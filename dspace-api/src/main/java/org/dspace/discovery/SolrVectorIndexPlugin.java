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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
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

    private static final String DEFAULT_VECTOR_TITLE_FIELD = "dc.title";
    private static final String[] DEFAULT_VECTOR_ADDITIONAL_FIELDS =
        new String[] {"dc.description.abstract"};

    private static final String VECTOR_SOURCE_TITLE_FIELD_PROPERTY = "embeddings.indexing.title.field";
    private static final String VECTOR_SOURCE_ADDITIONAL_FIELDS_PROPERTY = "embeddings.indexing.additional.fields";

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
        if (!configurationService.getBooleanProperty(VectorConstants.SEMANTIC_SEARCH_ENABLED_PROPERTY , false) && 
        !configurationService.getBooleanProperty(VectorConstants.HYBRID_SEARCH_ENABLED_PROPERTY , false)) {
            return;
        }

        if (!(indexableObject instanceof IndexableItem)) {
            return;
        }

        Item item = ((IndexableItem) indexableObject).getIndexedObject();
        String titleField = configurationService.getProperty(VECTOR_SOURCE_TITLE_FIELD_PROPERTY,
                DEFAULT_VECTOR_TITLE_FIELD);

        String title = getMetadataValue(item, titleField, VECTOR_SOURCE_TITLE_FIELD_PROPERTY,
                DEFAULT_VECTOR_TITLE_FIELD);
        if (StringUtils.isBlank(title)) {
            return;
        }

        try {
            String apiUrl = configurationService.getProperty(VectorConstants.API_URL_INDEXING_PROPERTY);
            String model = configurationService.getProperty(VectorConstants.MODEL_PROPERTY);
            String apiKey = configurationService.getProperty(VectorConstants.API_KEY_INDEXING_PROPERTY);
            boolean solrMultiVectors = configurationService
                    .getBooleanProperty(VectorConstants.SOLR_MULTI_VECTORS_PROPERTY, false);

            log.info("Indexing solr multi vector: {}", solrMultiVectors);

            String vectorField = VectorConstants.resolveVectorField(solrMultiVectors);

            if (!solrMultiVectors) {
                List<Float> vector = embeddingService.embed(chunkingService.normalizeText(title), apiUrl, model,
                    apiKey);
                if (vector.isEmpty()) {
                    return;
                }

                document.setField(vectorField, vector);
            } else {
                Set<String> textsToVectorize = new LinkedHashSet<>();
                textsToVectorize.add(chunkingService.normalizeText(title));

                for (String metadataField : getAdditionalMetadataFields()) {
                    textsToVectorize.addAll(getTextsToVectorize(item, metadataField, title));
                }

                if (textsToVectorize.isEmpty()) {
                    return;
                }

                List<List<Float>> vectors = embeddingService.embed(new ArrayList<>(textsToVectorize), apiUrl, model,
                    apiKey);
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

    private List<String> getAdditionalMetadataFields() {
        String[] configuredFields = configurationService.getArrayProperty(
            VECTOR_SOURCE_ADDITIONAL_FIELDS_PROPERTY,
            DEFAULT_VECTOR_ADDITIONAL_FIELDS);

        if (configuredFields == null || configuredFields.length == 0) {
            return Arrays.asList(DEFAULT_VECTOR_ADDITIONAL_FIELDS);
        }

        return Arrays.stream(configuredFields)
            .map(StringUtils::trim)
            .filter(StringUtils::isNotBlank)
            .toList();
    }

    private List<String> getTextsToVectorize(Item item, String metadataField, String title) {
        List<MetadataValue> metadataValues = itemService.getMetadataByMetadataString(item, metadataField);
        if (metadataValues == null || metadataValues.isEmpty()) {
            return List.of();
        }

        StringBuilder concatenatedValues = new StringBuilder();
        for (MetadataValue metadataValue : metadataValues) {
            String value = metadataValue.getValue();
            if (StringUtils.isBlank(value)) {
                continue;
            }

            if (concatenatedValues.length() > 0) {
                concatenatedValues.append(' ');
            }
            concatenatedValues.append(value);
        }

        if (concatenatedValues.length() == 0) {
            return List.of();
        }

        String fieldContent = concatenatedValues.toString();
        if (chunkingService.exceedsMaxSegmentSize(fieldContent)) {
            return chunkingService.chunkText(fieldContent, title);
        }

        return List.of(chunkingService.normalizeText(fieldContent));
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
