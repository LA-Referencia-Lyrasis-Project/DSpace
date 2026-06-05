/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.util.Utils;
import org.dspace.core.Context;
import org.dspace.discovery.embedding.ChunkingService;
import org.dspace.discovery.embedding.EmbeddingService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Converts text queries into Solr combined lexical/vector queries using RRF.
 */
public class SolrHybridSearchPlugin implements SolrServiceSearchPlugin {

    private static final Logger log = LogManager.getLogger(SolrHybridSearchPlugin.class);

    public static final String HYBRID_SEARCH_REQUEST_PARAM = "dspace.hybrid.search";
    public static final String HYBRID_SEARCH_JSON_PARAM = "dspace.hybrid.search.json";

    private static final String SOLR_DEFAULT_VECTOR_FIELD = "vector";
    private static final String SEARCH_TYPE_PROPERTY = "searchType";
    private static final String SEARCH_TYPE_HYBRID = "hybrid";
    private static final String SCORE_FIELD = "score";
    private static final String HIGHLIGHT_QUERY_PARAM = "hl.q";
    private static final String PARENT_WHICH_QUERY = "*:* -_nest_path_:*";

    private static final String SEMANTIC_SEARCH_ENABLED_PROPERTY = "semantic.search.enabled";
    private static final String SOLR_MULTI_VECTORS_PROPERTY = "embeddings.solr.multi.vectors";
    private static final String SOLR_VECTOR_FIELD_PROPERTY = "embeddings.solr.vector.field";
    private static final String API_URL_SEARCH_PROPERTY = "embeddings.api.url.search";
    private static final String MODEL_SEARCH_PROPERTY = "embeddings.model.search";

    @Autowired(required = true)
    private EmbeddingService embeddingService;

    @Autowired(required = true)
    private ConfigurationService configurationService;

    @Autowired(required = true)
    private ChunkingService chunkingService;

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
            throws SearchServiceException {

        if (!configurationService.getBooleanProperty(SEMANTIC_SEARCH_ENABLED_PROPERTY, false)) {
            return;
        }

        String searchType = getPropertyValue(discoveryQuery, SEARCH_TYPE_PROPERTY);
        if (!SEARCH_TYPE_HYBRID.equalsIgnoreCase(searchType)) {
            return;
        }

        String textQuery = chunkingService.normalizeText(solrQuery.getQuery());
        if (StringUtils.isBlank(textQuery) || "*:*".equals(textQuery)) {
            return;
        }

        try {
            String apiUrl = configurationService.getProperty(API_URL_SEARCH_PROPERTY);
            String model = configurationService.getProperty(MODEL_SEARCH_PROPERTY);

            List<Float> vector = embeddingService.embed(textQuery, apiUrl, model);
            if (vector.isEmpty()) {
                return;
            }

            boolean solrMultiVectors = configurationService.getBooleanProperty(SOLR_MULTI_VECTORS_PROPERTY, false);
            String vectorField = configurationService.getProperty(SOLR_VECTOR_FIELD_PROPERTY,
                    SOLR_DEFAULT_VECTOR_FIELD);
            int topK = configurationService.getIntProperty("embeddings.hybrid.topK",
                    configurationService.getIntProperty("embeddings.search.topK", 10));
            int rrfK = configurationService.getIntProperty("embeddings.hybrid.rrf.k", 60);

            String vectorPayload = vector.stream().map(String::valueOf).collect(Collectors.joining(","));
            Map<String, Object> vectorQuery = solrMultiVectors
                    ? buildNestedMultiVectorQuery(vectorField, topK, vectorPayload)
                    : buildVectorQuery(vectorField, topK, vectorPayload);

            Map<String, Object> combinedQuery = buildCombinedQuery(solrQuery, vectorQuery, rrfK);

            log.info("Executing hybrid search using RRF with topK {}, rrf.k {}, solr multi vectors {}",
                    topK, rrfK, solrMultiVectors);

            if (!discoveryQuery.getSearchFields().contains(SCORE_FIELD)) {
                discoveryQuery.addSearchField(SCORE_FIELD);
            }
            solrQuery.addField(SCORE_FIELD);
            solrQuery.set(HIGHLIGHT_QUERY_PARAM, textQuery);
            solrQuery.set(HYBRID_SEARCH_REQUEST_PARAM, Boolean.TRUE.toString());
            solrQuery.set(HYBRID_SEARCH_JSON_PARAM, Utils.toJSONString(combinedQuery));

            solrQuery.set("debugQuery", "on");
            solrQuery.set("debug", "results");
        } catch (Exception e) {
            // Keep lexical search resilient when embeddings or the combined query cannot be
            // built.
            log.error("Error while converting text query to hybrid vector query", e);
        }
    }

    private Map<String, Object> buildCombinedQuery(SolrQuery solrQuery, Map<String, Object> vectorQuery,
            int rrfK) {
        Map<String, Object> lexicalQuery = new LinkedHashMap<>();
        lexicalQuery.put("lucene", Map.of("query", solrQuery.getQuery()));

        Map<String, Object> queries = new LinkedHashMap<>();
        queries.put("lexical", lexicalQuery);
        queries.put("vector", vectorQuery);

        Map<String, Object> combinerParams = new LinkedHashMap<>();
        combinerParams.put("combiner", true);
        combinerParams.put("combiner.query", Arrays.asList("lexical", "vector"));
        combinerParams.put("combiner.algorithm", "rrf");
        combinerParams.put("combiner.rrf.k", rrfK);

        Map<String, Object> combinedQuery = new LinkedHashMap<>();
        combinedQuery.put("queries", queries);
        combinedQuery.put("limit", solrQuery.getRows());
        combinedQuery.put("offset", solrQuery.getStart());
        combinedQuery.put("fields", getFields(solrQuery));
        combinedQuery.put("params", combinerParams);

        String[] filters = solrQuery.getFilterQueries();
        if (filters != null && filters.length > 0) {
            combinedQuery.put("filter", Arrays.asList(filters));
        }

        return combinedQuery;
    }

    private Map<String, Object> buildVectorQuery(String vectorField, int topK, String vectorPayload) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("f", vectorField);
        knn.put("topK", topK);
        knn.put("filteredSearchThreshold", "60");
        knn.put("query", "[" + vectorPayload + "]");

        return Map.of("knn", knn);
    }

    private Map<String, Object> buildNestedMultiVectorQuery(String vectorField, int topK, String vectorPayload) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("f", vectorField);
        knn.put("topK", topK);
        knn.put("filteredSearchThreshold", "60");
        knn.put("query", "[" + vectorPayload + "]");
        knn.put("childrenOf", PARENT_WHICH_QUERY);

        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("which", PARENT_WHICH_QUERY);
        parent.put("score", "max");
        parent.put("query", Map.of("knn", knn));

        return Map.of("parent", parent);
    }

    private List<String> getFields(SolrQuery solrQuery) {
        List<String> fields = new ArrayList<>();
        String[] configuredFields = solrQuery.getParams("fl");
        if (configuredFields != null) {
            for (String fieldList : configuredFields) {
                fields.addAll(Arrays.stream(fieldList.split(","))
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList()));
            }
        }
        if (fields.isEmpty()) {
            fields.add("*");
        }
        if (!fields.contains(SCORE_FIELD)) {
            fields.add(SCORE_FIELD);
        }
        return fields;
    }

    private String getPropertyValue(DiscoverQuery discoverQuery, String property) {
        List<String> values = discoverQuery.getProperties().get(property);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
