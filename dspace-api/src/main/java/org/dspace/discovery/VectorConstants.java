/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

/**
 * Shared constants for Solr vector indexing and search behavior.
 */
public final class VectorConstants {

    public static final String VECTOR_FIELD = "vector";
    public static final String VECTOR_MULTIVALUED_FIELD = "vector_multivalued";
    public static final String PARENT_WHICH_QUERY = "*:* -_nest_path_:*";
    public static final String SEARCH_TYPE_PROPERTY = "searchType";
    public static final String SEMANTIC_SEARCH_ENABLED_PROPERTY = "semantic.search.enabled";
    public static final String HYBRID_SEARCH_ENABLED_PROPERTY = "hybrid.search.enabled";
    public static final String SOLR_MULTI_VECTORS_PROPERTY = "embeddings.solr.multi.vectors";
    public static final String API_URL_INDEXING_PROPERTY = "embeddings.api.url.indexing";
    public static final String API_KEY_INDEXING_PROPERTY = "embeddings.api.key.indexing";
    public static final String API_URL_SEARCH_PROPERTY = "embeddings.api.url.search";
    public static final String API_KEY_SEARCH_PROPERTY = "embeddings.api.key.search";
    public static final String MODEL_PROPERTY = "embeddings.model";
    public static final String SEARCH_TOP_K_PROPERTY = "embeddings.search.topK";
    public static final String SCORE_FIELD = "score";
    public static final String HIGHLIGHT_QUERY_PARAM = "hl.q";

    private VectorConstants() {
    }

    public static String resolveVectorField(boolean solrMultiVectors) {
        return solrMultiVectors ? VECTOR_MULTIVALUED_FIELD : VECTOR_FIELD;
    }
}