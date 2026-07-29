/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
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
import org.dspace.discovery.embedding.ChunkingService;
import org.dspace.discovery.embedding.EmbeddingService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Converts text queries into Solr KNN queries for semantic search.
 */
public class SolrSemanticSearchPlugin implements SolrServiceSearchPlugin {

    private static final Logger log = LogManager.getLogger(SolrSemanticSearchPlugin.class);

    private static final String SEARCH_TYPE_SEMANTIC = "semantic";
    private static final String QUERY_PARSER_KNN = "knn";
    private static final String QUERY_PARSER_VECTOR_SIMILARITY = "vectorSimilarity";
    private static final String ALL_PARENTS_PARAM = "allParents";
    private static final String CHILDREN_QUERY_PARAM = "children.q";

    @Autowired(required = true)
    private EmbeddingService embeddingService;

    @Autowired(required = true)
    private ConfigurationService configurationService;

    @Autowired(required = true)
    private ChunkingService chunkingService;

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
            throws SearchServiceException {

        if (!configurationService.getBooleanProperty(VectorConstants.SEMANTIC_SEARCH_ENABLED_PROPERTY, false)) {
            return;
        }

        String searchType = getPropertyValue(discoveryQuery, VectorConstants.SEARCH_TYPE_PROPERTY);
        if (!SEARCH_TYPE_SEMANTIC.equalsIgnoreCase(searchType)) {
            return;
        }

        String textQuery = chunkingService.normalizeText(solrQuery.getQuery());
        if (StringUtils.isBlank(textQuery) || "*:*".equals(textQuery)) {
            return;
        }

        try {
            String apiUrl = configurationService.getProperty(VectorConstants.API_URL_SEARCH_PROPERTY);
            String model = configurationService.getProperty(VectorConstants.MODEL_PROPERTY);
            String apiKey = configurationService.getProperty(VectorConstants.API_KEY_SEARCH_PROPERTY);

            List<Float> vector = embeddingService.embed(textQuery, apiUrl, model, apiKey);
            if (vector.isEmpty()) {
                return;
            }

            String queryParser = configurationService
                    .getProperty("embeddings.search.query.parser", QUERY_PARSER_KNN);
            String effectiveQueryParser = resolveQueryParser(queryParser);
            boolean solrMultiVectors = configurationService
                    .getBooleanProperty(VectorConstants.SOLR_MULTI_VECTORS_PROPERTY, false);
            String vectorField = VectorConstants.resolveVectorField(solrMultiVectors);
            int topK = configurationService.getIntProperty(VectorConstants.SEARCH_TOP_K_PROPERTY, 10);
            double minReturn = configurationService.getPropertyAsType(
                    "embeddings.search.min.return", 0.7d);
            String vectorPayload = vector.stream().map(String::valueOf).collect(Collectors.joining(","));
            String vectorQuery = solrMultiVectors
                    ? buildNestedMultiVectorQuery(solrQuery, effectiveQueryParser, vectorField, topK, minReturn,
                            vectorPayload)
                    : buildVectorQuery(effectiveQueryParser, vectorField, topK, minReturn, vectorPayload);

            log.info("Executing semantic search using query parser '{}' for solr multi vectors {}",
                    effectiveQueryParser, solrMultiVectors);

            if (!discoveryQuery.getSearchFields().contains(VectorConstants.SCORE_FIELD)) {
                discoveryQuery.addSearchField(VectorConstants.SCORE_FIELD);
            }
            solrQuery.addField(VectorConstants.SCORE_FIELD);
            solrQuery.set(VectorConstants.HIGHLIGHT_QUERY_PARAM, textQuery);

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

    private String buildNestedMultiVectorQuery(SolrQuery solrQuery, String queryParser, String vectorField, int topK,
            double minReturn, String vectorPayload) {
        solrQuery.set(ALL_PARENTS_PARAM, VectorConstants.PARENT_WHICH_QUERY);

        String childVectorQuery;
        if (QUERY_PARSER_VECTOR_SIMILARITY.equalsIgnoreCase(queryParser)) {
            childVectorQuery = buildVectorQuery(QUERY_PARSER_VECTOR_SIMILARITY, vectorField, topK, minReturn,
                    vectorPayload);
        } else {
            childVectorQuery = "{!knn f=" + vectorField + " topK=" + topK + " childrenOf=$" + ALL_PARENTS_PARAM
                    + "}[" + vectorPayload + "]";
        }

        solrQuery.set(CHILDREN_QUERY_PARAM, childVectorQuery);
        return "{!parent which=$" + ALL_PARENTS_PARAM + " score=max v=$" + CHILDREN_QUERY_PARAM + "}";
    }

    private String resolveQueryParser(String queryParser) {
        if (QUERY_PARSER_VECTOR_SIMILARITY.equalsIgnoreCase(queryParser)) {
            return QUERY_PARSER_VECTOR_SIMILARITY;
        }

        if (QUERY_PARSER_KNN.equalsIgnoreCase(queryParser)) {
            return QUERY_PARSER_KNN;
        }

        log.warn("Missing/Invalid embeddings.search.query.parser, falling back to '{}'", QUERY_PARSER_KNN);

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
