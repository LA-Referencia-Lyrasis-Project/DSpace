/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Adds dense-vector embeddings to Solr documents during discovery indexing.
 */
public class SolrVectorIndexPlugin implements SolrServiceIndexPlugin {

    private static final Logger log = LogManager.getLogger(SolrVectorIndexPlugin.class);

    private static final String VECTOR_FIELD_PROPERTY = "embeddings.search.vectorField";
    private static final String DEFAULT_VECTOR_FIELD = "vector";
    private static final String VECTOR_SOURCE_METADATA_FIELD_PROPERTY = "embeddings.indexing.metadataField";
    private static final String DEFAULT_VECTOR_SOURCE_METADATA_FIELD = "dc.title";
    private static final String SEMANTIC_SEARCH_ENABLED_PROPERTY = "semantic.search.enabled";

    @Autowired(required = true)
    private ItemService itemService;

    @Autowired(required = true)
    private ConfigurationService configurationService;

    @Autowired(required = true)
    private EmbeddingService embeddingService;

    @Override
    @SuppressWarnings("rawtypes")
    public void additionalIndex(Context context, IndexableObject indexableObject, SolrInputDocument document) {
        if (!configurationService.getBooleanProperty(SEMANTIC_SEARCH_ENABLED_PROPERTY, false)) {
            return;
        }

        if (!(indexableObject instanceof IndexableItem)) {
            return;
        }

        Item item = ((IndexableItem) indexableObject).getIndexedObject();
        String sourceMetadataField = configurationService.getProperty(
                VECTOR_SOURCE_METADATA_FIELD_PROPERTY,
                DEFAULT_VECTOR_SOURCE_METADATA_FIELD);
        String textToVectorize = getMetadataValue(item, sourceMetadataField);
        if (StringUtils.isBlank(textToVectorize)) {
            return;
        }

        try {
            List<Float> vector = embeddingService.getVectorFromAPIForIndexing(textToVectorize);
            if (vector.isEmpty()) {
                return;
            }

            String vectorField = configurationService.getProperty(VECTOR_FIELD_PROPERTY, DEFAULT_VECTOR_FIELD);

            for (Float value : vector) {
                document.addField(vectorField, value);
            }
        } catch (Exception e) {
            // Keep lexical indexing resilient when embeddings are unavailable.
            log.error("Error while generating embedding for item {}", item.getID(), e);
        }
    }

    private String getMetadataValue(Item item, String metadataField) {
        String[] parts = StringUtils.defaultString(metadataField).split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            log.warn("Invalid {} value '{}', falling back to {}", VECTOR_SOURCE_METADATA_FIELD_PROPERTY,
                    metadataField, DEFAULT_VECTOR_SOURCE_METADATA_FIELD);
            return itemService.getMetadataFirstValue(item, "dc", "title", null, Item.ANY);
        }

        String schema = parts[0];
        String element = parts[1];
        String qualifier = parts.length == 3 ? parts[2] : null;

        return itemService.getMetadataFirstValue(item, schema, element, qualifier, Item.ANY);
    }
}
