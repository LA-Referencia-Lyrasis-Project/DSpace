/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.embedding.ChunkingService;
import org.dspace.discovery.embedding.EmbeddingService;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Adds dense-vector embeddings to Solr documents during discovery indexing.
 */
public class SolrVectorIndexPlugin implements SolrServiceIndexPlugin {

    private static final Logger log = LogManager.getLogger(SolrVectorIndexPlugin.class);

    private static final String DEFAULT_SOLR_VECTOR_FIELD = "vector";
    private static final String DEFAULT_VECTOR_TITLE_FIELD = "dc.title";
    private static final String DEFAULT_VECTOR_DESCRIPTION_FIELD = "dc.description.abstract";

    private static final String SEMANTIC_SEARCH_ENABLED_PROPERTY = "semantic.search.enabled";
    private static final String SOLR_MULTI_VECTORS_PROPERTY = "embeddings.solr.multi.vectors";
    private static final String SOLR_VECTOR_FIELD = "embeddings.solr.vector.field";
    private static final String VECTOR_SOURCE_TITLE_FIELD_PROPERTY = "embeddings.indexing.title.field";
    private static final String VECTOR_SOURCE_DESCRIPTION_FIELD_PROPERTY = "embeddings.indexing.description.field";
    private static final String API_URL_INDEXING_PROPERTY = "embeddings.api.url.indexing";
    private static final String MODEL_INDEXING_PROPERTY = "embeddings.model.indexing";

    @Autowired(required = true)
    private ItemService itemService;

    @Autowired(required = true)
    private ConfigurationService configurationService;

    @Autowired(required = true)
    private EmbeddingService embeddingService;

    @Autowired(required = true)
    private ChunkingService chunkingService;

    @Override
    @SuppressWarnings("rawtypes")
    public void additionalIndex(Context context, IndexableObject indexableObject, SolrInputDocument document) {
        log.info("Processing {} for vector indexing", indexableObject.getID());
        if (!configurationService.getBooleanProperty(SEMANTIC_SEARCH_ENABLED_PROPERTY, false)) {
            return;
        }

        if (!(indexableObject instanceof IndexableItem)) {
            return;
        }

        Item item = ((IndexableItem) indexableObject).getIndexedObject();
        String titleField = configurationService.getProperty(VECTOR_SOURCE_TITLE_FIELD_PROPERTY,
                DEFAULT_VECTOR_TITLE_FIELD);
        String descriptionField = configurationService.getProperty(VECTOR_SOURCE_DESCRIPTION_FIELD_PROPERTY,
                DEFAULT_VECTOR_DESCRIPTION_FIELD);

        String title = getMetadataValue(item, titleField, VECTOR_SOURCE_TITLE_FIELD_PROPERTY,
                DEFAULT_VECTOR_TITLE_FIELD);
        if (StringUtils.isBlank(title)) {
            return;
        }

        try {
            String apiUrl = configurationService.getProperty(API_URL_INDEXING_PROPERTY);
            String model = configurationService.getProperty(MODEL_INDEXING_PROPERTY);
            boolean solrMultiVectors = configurationService.getBooleanProperty(SOLR_MULTI_VECTORS_PROPERTY, false);

            log.info("Indexing solr multi vector: {}", solrMultiVectors);

            String vectorField = configurationService.getProperty(SOLR_VECTOR_FIELD, DEFAULT_SOLR_VECTOR_FIELD);

            if (!solrMultiVectors) {
                List<Float> vector = embeddingService.embed(chunkingService.normalizeText(title), apiUrl, model);
                if (vector.isEmpty()) {
                    return;
                }

                document.setField(vectorField, vector);
            } else {
                List<String> textsToVectorize = new ArrayList<>();
                textsToVectorize.add(chunkingService.normalizeText(title));

                String description = getMetadataValue(item, descriptionField,
                        VECTOR_SOURCE_DESCRIPTION_FIELD_PROPERTY, DEFAULT_VECTOR_DESCRIPTION_FIELD);
                if (StringUtils.isNotBlank(description)) {
                    textsToVectorize.addAll(chunkingService.chunkTitleAndAbstract(title, description));
                }

                List<List<Float>> vectors = embeddingService.embed(textsToVectorize, apiUrl, model);
                if (vectors.isEmpty()) {
                    return;
                }
                document.setField(vectorField, vectors);
            }
        } catch (Exception e) {
            // Keep lexical indexing resilient when embeddings are unavailable.
            log.error("Error while generating embedding for item {}", item.getID(), e);
        }
    }

    private String getMetadataValue(Item item, String metadataField, String propertyName, String defaultField) {
        String[] parts = StringUtils.defaultString(metadataField).split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            log.warn("Invalid {} value '{}', falling back to {}", propertyName, metadataField, defaultField);
            parts = defaultField.split("\\.");
            if (parts.length < 2 || parts.length > 3) {
                return null;
            }
        }

        String schema = parts[0];
        String element = parts[1];
        String qualifier = parts.length == 3 ? parts[2] : null;

        return itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY);
    }
}
