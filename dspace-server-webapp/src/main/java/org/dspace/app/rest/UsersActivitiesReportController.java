/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.dspace.app.reporting.model.PaginatedUserActionsResponse;
import org.dspace.app.reporting.model.SummaryWithTrendData;
import org.dspace.app.reporting.model.UserActivityStats;
import org.dspace.app.reporting.service.UsersActivitiesReportService;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for user activity reporting
 * Provides endpoints to retrieve statistics about user submissions and reviews
 */
@RestController
@RequestMapping("/api/reports/users-activities")
public class UsersActivitiesReportController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(UsersActivitiesReportController.class);

    @Autowired
    private UsersActivitiesReportService usersActivitiesReportService;

    /**
     * Get users activity report
     *
     * @param request HTTP request
     * @return UserActivityReportRest with all statistics
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping()
    public ResponseEntity<?> getUsersActivitiesReport(HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            // Get all statistics
            Map<String, UserActivityStats> userStats = usersActivitiesReportService
                    .getUsersActivitiesStatistics(context);

            List<UserActivityStats> userActivityStats = new ArrayList<>(userStats.values());

            context.complete();
            return new ResponseEntity<>(userActivityStats, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error generating user activity report", e);
            return new ResponseEntity<>("Error generating report: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get total statistics (summary counts)
     *
     * @param request HTTP request
     * @return map with total counts
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            Map<String, Integer> totals = usersActivitiesReportService.getTotalStatistics(context);

            context.complete();
            return new ResponseEntity<>(totals, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error generating summary statistics", e);
            return new ResponseEntity<>("Error generating summary: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all actions (submissions and reviews) with optional filtering and
     * pagination.
     *
     * @param request    HTTP request
     * @param page       page number (0-based), default 0
     * @param size       page size, default 100
     * @param itemId     optional item id filter
     * @param actionType optional action type filter
     * @param userEmail  optional user email filter
     * @param userName   optional user name filter
     * @return paginated response with actions and page metadata
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/actions")
    public ResponseEntity<?> getAllActions(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String userName) {
        try {
            Context context = ContextUtil.obtainContext(request);

            PaginatedUserActionsResponse actions = usersActivitiesReportService.getActions(
                    context,
                    page,
                    size,
                    itemId,
                    actionType,
                    userEmail,
                    userName);

            context.complete();
            return new ResponseEntity<>(actions, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error retrieving actions", e);
            return new ResponseEntity<>("Error retrieving actions: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get total statistics with trend data aggregated by month
     *
     * @param request HTTP request
     * @return SummaryWithTrendData with totals and monthly trends
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/summary-with-trends")
    public ResponseEntity<?> getSummaryWithTrends(HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            SummaryWithTrendData summary = usersActivitiesReportService.getTotalStatisticsWithTrends(context);

            context.complete();
            return new ResponseEntity<>(summary, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error generating summary with trends", e);
            return new ResponseEntity<>("Error generating summary with trends: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
