/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.app.reporting.model.UserAction;
import org.dspace.app.reporting.utils.ProvenanceParser;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Cached access to all provenance-based user actions.
 */
@Service
public class UsersActivitiesActionsCacheService {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(UsersActivitiesActionsCacheService.class);

    @Autowired
    private MetadataFieldService metadataFieldService;

    @Autowired
    private MetadataValueService metadataValueService;

    @Autowired
    private ItemService itemService;

    /**
     * Get all actions (submissions and reviews) from provenance metadata.
     * Cached for 1 hour via Ehcache configuration.
     *
     * @param context the DSpace context
     * @return list of all user actions extracted from provenance metadata
     * @throws SQLException if database error occurs
     */
    @Cacheable(cacheNames = "usersActivities.allActions", key = "'all'")
    public List<UserAction> getAllActions(Context context) throws SQLException {
        List<UserAction> allActions = new ArrayList<>();

        try {
            // Find the provenance metadata field (dc.description.provenance)
            MetadataField provenanceField = metadataFieldService.findByElement(
                    context,
                    "dc",
                    "description",
                    "provenance");

            if (provenanceField == null) {
                log.warn("Provenance metadata field (dc.description.provenance) not found");
                return allActions;
            }

            // Get all provenance metadata values
            List<MetadataValue> provenanceValues = metadataValueService.findByField(context, provenanceField);

            for (MetadataValue metadataValue : provenanceValues) {
                String provenanceText = metadataValue.getValue();

                if (provenanceText != null && !provenanceText.isEmpty()) {
                    List<UserAction> actions = ProvenanceParser.parseProvenanceText(provenanceText);

                    // Add item UUID to each action
                    if (metadataValue.getDSpaceObject() instanceof Item) {
                        Item item = (Item) metadataValue.getDSpaceObject();
                        String itemUUID = item.getID().toString();

                        for (UserAction action : actions) {
                            action.setItemUUID(itemUUID);
                        }
                    }

                    allActions.addAll(actions);
                }
            }

            log.info("Extracted " + allActions.size() + " total actions from provenance metadata");

        } catch (Exception e) {
            log.error("Error retrieving provenance metadata: " + e.getMessage(), e);
            throw new SQLException("Error retrieving provenance metadata", e);
        }

        return allActions;
    }
}
