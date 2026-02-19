/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.service;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.Logger;
import org.dspace.app.reporting.model.SummaryWithTrendData;
import org.dspace.app.reporting.model.UserAction;
import org.dspace.app.reporting.model.UserActivityStats;
import org.dspace.app.reporting.utils.ProvenanceParser;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service implementation for generating user activity reports
 * by parsing provenance metadata.
 */
@Service
public class UserActivityReportServiceImpl implements UserActivityReportService {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(UserActivityReportServiceImpl.class);

    @Autowired
    private MetadataFieldService metadataFieldService;

    @Autowired
    private MetadataValueService metadataValueService;

    @Autowired
    private ItemService itemService;

    @Override
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

    @Override
    public Map<String, UserActivityStats> getUserStatistics(Context context) throws SQLException {
        Map<String, UserActivityStats> stats = new TreeMap<>();

        try {
            List<UserAction> allActions = getAllActions(context);

            for (UserAction action : allActions) {
                String email = action.getEmail();

                // Get or create stats for this user
                UserActivityStats userStats = stats.computeIfAbsent(
                        email,
                        k -> new UserActivityStats(action.getUserName(), email));

                // Update user name if we have a new one
                if (action.getUserName() != null && !action.getUserName().isEmpty()) {
                    userStats.setUserName(action.getUserName());
                }

                userStats.countAction(action.getActionType());
            }

            log.info("Generated statistics for " + stats.size() + " unique users");

        } catch (Exception e) {
            log.error("Error generating user statistics: " + e.getMessage(), e);
            throw new SQLException("Error generating user statistics", e);
        }

        return stats;
    }

    @Override
    public UserActivityStats getUserStatistics(Context context, String email) throws SQLException {
        try {
            Map<String, UserActivityStats> allStats = getUserStatistics(context);
            return allStats.get(email);
        } catch (Exception e) {
            log.error("Error retrieving statistics for user " + email + ": " + e.getMessage(), e);
            throw new SQLException("Error retrieving user statistics", e);
        }
    }

    @Override
    public Map<String, Integer> getTotalStatistics(Context context) throws SQLException {
        Map<String, Integer> totals = new HashMap<>();

        try {
            List<UserAction> allActions = getAllActions(context);

            int submissions = 0;
            int reviews = 0;
            int approvals = 0;
            int rejections = 0;
            int withdrawals = 0;

            for (UserAction action : allActions) {
                String actionType = action.getActionType();
                if ("SUBMITTED".equals(actionType)) {
                    submissions++;
                } else if ("APPROVED".equals(actionType)) {
                    approvals++;
                    reviews++;
                } else if ("REJECTED".equals(actionType)) {
                    rejections++;
                    reviews++;
                } else if ("WITHDRAWN".equals(actionType)) {
                    withdrawals++;
                    reviews++;
                }
            }

            totals.put("submissions", submissions);
            totals.put("reviews", reviews);
            totals.put("approvals", approvals);
            totals.put("rejections", rejections);
            totals.put("withdrawals", withdrawals);
            totals.put("totalUsers", 0);

            // Count unique users
            Map<String, UserActivityStats> userStats = getUserStatistics(context);
            totals.put("totalUsers", userStats.size());

            log.info("Total statistics: " + submissions + " submissions, " + reviews + " reviews, "
                    + approvals + " approvals, " + rejections + " rejections, " + withdrawals
                    + " withdrawals by " + userStats.size() + " users");

        } catch (Exception e) {
            log.error("Error generating total statistics: " + e.getMessage(), e);
            throw new SQLException("Error generating total statistics", e);
        }

        return totals;
    }

    @Override
    public SummaryWithTrendData getTotalStatisticsWithTrends(Context context) throws SQLException {
        try {
            List<UserAction> allActions = getAllActions(context);

            int submissions = 0;
            int reviews = 0;
            int approvals = 0;
            int rejections = 0;
            int withdrawals = 0;

            // Trend data: Map of month (YYYY-MM) to action type counts
            Map<String, Map<String, Integer>> trendData = new TreeMap<>();

            for (UserAction action : allActions) {
                String actionType = action.getActionType();

                // Count totals
                if ("SUBMITTED".equals(actionType)) {
                    submissions++;
                } else if ("APPROVED".equals(actionType)) {
                    approvals++;
                    reviews++;
                } else if ("REJECTED".equals(actionType)) {
                    rejections++;
                    reviews++;
                } else if ("WITHDRAWN".equals(actionType)) {
                    withdrawals++;
                    reviews++;
                }

                // Aggregate by month
                if (action.getActionDate() != null) {
                    YearMonth month = YearMonth.from(action.getActionDate());
                    String monthKey = month.toString(); // Format: YYYY-MM

                    Map<String, Integer> monthData = trendData.computeIfAbsent(monthKey, k -> new HashMap<>());

                    // Initialize action type counters if not present
                    monthData.putIfAbsent("SUBMITTED", 0);
                    monthData.putIfAbsent("APPROVED", 0);
                    monthData.putIfAbsent("REJECTED", 0);
                    monthData.putIfAbsent("WITHDRAWN", 0);

                    // Increment the appropriate counter
                    int currentCount = monthData.get(actionType);
                    monthData.put(actionType, currentCount + 1);
                }
            }

            // Create summary with trends
            SummaryWithTrendData summary = new SummaryWithTrendData(
                    submissions,
                    reviews,
                    approvals,
                    rejections,
                    withdrawals,
                    getUserStatistics(context).size());

            summary.setTrendData(trendData);

            log.info("Generated statistics with trends: " + submissions + " submissions, " + reviews
                    + " reviews with " + trendData.size() + " months of trend data");

            return summary;

        } catch (Exception e) {
            log.error("Error generating total statistics with trends: " + e.getMessage(), e);
            throw new SQLException("Error generating total statistics with trends", e);
        }
    }
}
