/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.dspace.app.reporting.model.SummaryWithTrendData;
import org.dspace.app.reporting.model.UserAction;
import org.dspace.app.reporting.model.UserActivityStats;
import org.dspace.app.reporting.service.UsersActivityReportService;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for user activity reporting
 * Provides endpoints to retrieve statistics about user submissions and reviews
 */
@RestController
@RequestMapping("/api/reporting/user-activity")
public class UsersActivityReportController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(UsersActivityReportController.class);

    @Autowired
    private UsersActivityReportService userActivityReportService;

    /**
     * Get users activity report
     *
     * @param request HTTP request
     * @return UserActivityReportRest with all statistics
     */
    // @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<?> getUsersReport(HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            // Get all statistics
            Map<String, UserActivityStats> userStats = userActivityReportService.getUserStatistics(context);

            List<UserActivityStats> userActivityStats = new ArrayList<>(userStats.values());

            context.complete();
            return new ResponseEntity<>(userActivityStats, HttpStatus.OK);

        } catch (SQLException e) {
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

            Map<String, Integer> totals = userActivityReportService.getTotalStatistics(context);

            context.complete();
            return new ResponseEntity<>(totals, HttpStatus.OK);

        } catch (SQLException e) {
            log.error("Error generating summary statistics", e);
            return new ResponseEntity<>("Error generating summary: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all actions (submissions and reviews) without aggregation
     *
     * @param request HTTP request
     * @return list of all user actions
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/actions")
    public ResponseEntity<?> getAllActions(HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            List<UserAction> actions = userActivityReportService.getAllActions(context);

            context.complete();
            return new ResponseEntity<>(actions, HttpStatus.OK);

        } catch (SQLException e) {
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

            SummaryWithTrendData summary = userActivityReportService.getTotalStatisticsWithTrends(context);

            context.complete();
            return new ResponseEntity<>(summary, HttpStatus.OK);

        } catch (SQLException e) {
            log.error("Error generating summary with trends", e);
            return new ResponseEntity<>("Error generating summary with trends: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
