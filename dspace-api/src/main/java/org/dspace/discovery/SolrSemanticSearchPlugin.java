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
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Converts text queries into Solr KNN queries for semantic search.
 */
public class SolrSemanticSearchPlugin implements SolrServiceSearchPlugin {

  private static final Logger log = LogManager.getLogger(SolrSemanticSearchPlugin.class);

  private static final String SEARCH_TYPE_PROPERTY = "searchType";
  private static final String SEARCH_TYPE_SEMANTIC = "semantic";
  private static final String VECTOR_FIELD = "vector";

  @Autowired(required = true)
  private EmbeddingService embeddingService;

  @Autowired(required = true)
  private ConfigurationService configurationService;

  @Override
  public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
      throws SearchServiceException {

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

      int topK = configurationService.getIntProperty("embeddings.search.topK", 10);
      String vectorPayload = vector.stream().map(String::valueOf).collect(Collectors.joining(", "));
      String knnQuery = "{!knn f=" + VECTOR_FIELD + " topK=" + topK + "}[" + vectorPayload + "]";

      solrQuery.setQuery(knnQuery);
    } catch (Exception e) {
      // Keep lexical search resilient when embeddings are unavailable.
      log.error("Error while converting text query to semantic KNN query", e);
    }
  }

  private String getPropertyValue(DiscoverQuery discoverQuery, String property) {
    List<String> values = discoverQuery.getProperties().get(property);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.get(0);
  }
}
