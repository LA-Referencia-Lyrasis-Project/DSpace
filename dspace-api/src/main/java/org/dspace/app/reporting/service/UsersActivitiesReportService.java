/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.dspace.app.reporting.model.PaginatedUserActionsResponse;
import org.dspace.app.reporting.model.SummaryWithTrendData;
import org.dspace.app.reporting.model.UserAction;
import org.dspace.app.reporting.model.UserActivityStats;
import org.dspace.core.Context;

/**
 * Service for generating user activity reports.
 */
public interface UsersActivitiesReportService {

    /**
     * Get all actions (submissions and reviews) from provenance metadata.
     *
     * @param context the DSpace context
     * @return list of all user actions extracted from provenance metadata
     * @throws SQLException if database error occurs
     */
    List<UserAction> getAllActions(Context context) throws SQLException;

    /**
     * Get actions with optional filters and pagination.
     *
     * @param context    the DSpace context
     * @param page       page number (0-based)
     * @param size       page size
     * @param itemId     optional item id filter
     * @param actionType optional action type filter
     * @param userEmail  optional user email filter
     * @param userName   optional user name filter
     * @return paginated and filtered actions response
     * @throws SQLException if database error occurs
     */
    PaginatedUserActionsResponse getActions(Context context, int page, int size, String itemId,
            String actionType, String userEmail, String userName)
            throws SQLException;

    /**
     * Get aggregated statistics per user (submissions and reviews)
     *
     * @param context the DSpace context
     * @return map of user email to UserActivityStats
     * @throws SQLException if database error occurs
     */
    Map<String, UserActivityStats> getUsersActivitiesStatistics(Context context) throws SQLException;

    /**
     * Get total submissions and reviews count
     *
     * @param context the DSpace context
     * @return map with keys "submissions" and "reviews" and integer values
     * @throws SQLException if database error occurs
     */
    Map<String, Integer> getTotalStatistics(Context context) throws SQLException;

    /**
     * Get total statistics with trend data aggregated by month
     *
     * @param context the DSpace context
     * @return SummaryWithTrendData containing totals and monthly trends
     * @throws SQLException if database error occurs
     */
    SummaryWithTrendData getTotalStatisticsWithTrends(Context context) throws SQLException;
}
