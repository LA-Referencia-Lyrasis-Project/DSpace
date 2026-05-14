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
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Adds dense-vector embeddings to Solr documents during discovery indexing.
 */
public class SolrVectorIndexPlugin implements SolrServiceIndexPlugin {

  private static final Logger log = LogManager.getLogger(SolrVectorIndexPlugin.class);

  private static final String VECTOR_FIELD = "vector";

  @Autowired(required = true)
  private ItemService itemService;

  @Autowired(required = true)
  private EmbeddingService embeddingService;

  @Override
  @SuppressWarnings("rawtypes")
  public void additionalIndex(Context context, IndexableObject indexableObject, SolrInputDocument document) {
    if (!(indexableObject instanceof IndexableItem)) {
      return;
    }

    Item item = ((IndexableItem) indexableObject).getIndexedObject();
    String title = itemService.getMetadataFirstValue(item, "dc", "title", null, Item.ANY);
    if (StringUtils.isBlank(title)) {
      return;
    }

    try {
      List<Float> vector = embeddingService.getVectorFromAPIForIndexing(title);
      if (vector.isEmpty()) {
        return;
      }

      for (Float value : vector) {
        document.addField(VECTOR_FIELD, value);
      }
    } catch (Exception e) {
      // Keep lexical indexing resilient when embeddings are unavailable.
      log.error("Error while generating embedding for item {}", item.getID(), e);
    }
  }
}
