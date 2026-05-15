/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Converts text queries into Solr KNN queries for semantic search.
 */
public class SolrSemanticSearchPlugin implements SolrServiceSearchPlugin {

    private static final Logger log = LogManager.getLogger(SolrSemanticSearchPlugin.class);

    private static final String SEARCH_TYPE_PROPERTY = "searchType";
    private static final String SEARCH_TYPE_SEMANTIC = "semantic";
    private static final String SEMANTIC_SEARCH_ENABLED_PROPERTY = "semantic.search.enabled";
    private static final String VECTOR_FIELD_PROPERTY = "embeddings.search.vectorField";
    private static final String DEFAULT_VECTOR_FIELD = "vector";
    private static final String SCORE_FIELD = "score";
    private static final String QUERY_PARSER_KNN = "knn";
    private static final String QUERY_PARSER_VECTOR_SIMILARITY = "vectorSimilarity";

    @Autowired(required = true)
    private EmbeddingService embeddingService;

    @Autowired(required = true)
    private ConfigurationService configurationService;

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
            throws SearchServiceException {

        if (!configurationService.getBooleanProperty(SEMANTIC_SEARCH_ENABLED_PROPERTY, false)) {
            return;
        }

        String searchType = getPropertyValue(discoveryQuery, SEARCH_TYPE_PROPERTY);
        if (!SEARCH_TYPE_SEMANTIC.equalsIgnoreCase(searchType)) {
            return;
        }

        String textQuery = solrQuery.getQuery();
        if (StringUtils.isBlank(textQuery) || "*:*".equals(textQuery)) {
            return;
        }

        try {
            List<Float> vector = embeddingService.getVectorFromAPIForSearch(textQuery);
            if (vector.isEmpty()) {
                return;
            }

            String queryParser = configurationService
                    .getProperty("embeddings.search.queryParser", QUERY_PARSER_KNN);
            String effectiveQueryParser = resolveQueryParser(queryParser);
            String vectorField = configurationService
                    .getProperty(VECTOR_FIELD_PROPERTY, DEFAULT_VECTOR_FIELD);
            int topK = configurationService.getIntProperty("embeddings.search.topK", 10);
            double minReturn = configurationService.getPropertyAsType(
                    "embeddings.search.minReturn", 0.7d);
            String vectorPayload = vector.stream().map(String::valueOf).collect(Collectors.joining(", "));
            String vectorQuery = buildVectorQuery(effectiveQueryParser, vectorField, topK, minReturn, vectorPayload);

            log.info("Executing semantic search using query parser '{}'", effectiveQueryParser);

            if (!discoveryQuery.getSearchFields().contains(SCORE_FIELD)) {
                discoveryQuery.addSearchField(SCORE_FIELD);
            }
            solrQuery.addField(SCORE_FIELD);

            solrQuery.setQuery(vectorQuery);
        } catch (Exception e) {
            // Keep lexical search resilient when embeddings are unavailable.
            log.error("Error while converting text query to semantic vector query", e);
        }
    }

    private String buildVectorQuery(String queryParser, String vectorField, int topK,
                                    double minReturn, String vectorPayload) {
        if (QUERY_PARSER_VECTOR_SIMILARITY.equalsIgnoreCase(queryParser)) {
            return "{!vectorSimilarity f=" + vectorField + " minReturn=" + minReturn + "}["
                    + vectorPayload + "]";
        }

        return "{!knn f=" + vectorField + " topK=" + topK + "}[" + vectorPayload + "]";
    }

    private String resolveQueryParser(String queryParser) {
        if (QUERY_PARSER_VECTOR_SIMILARITY.equalsIgnoreCase(queryParser)) {
            return QUERY_PARSER_VECTOR_SIMILARITY;
        }

        if (QUERY_PARSER_KNN.equalsIgnoreCase(queryParser)) {
            return QUERY_PARSER_KNN;
        }

        log.warn("Missing/Invalid embeddings.search.queryParser, falling back to '{}'", QUERY_PARSER_KNN);

        return QUERY_PARSER_KNN;
    }

    private String getPropertyValue(DiscoverQuery discoverQuery, String property) {
        List<String> values = discoverQuery.getProperties().get(property);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
