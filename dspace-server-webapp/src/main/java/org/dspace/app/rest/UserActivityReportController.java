/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.dspace.app.reporting.model.UserAction;
import org.dspace.app.reporting.model.UserActivityStats;
import org.dspace.app.reporting.service.UserActivityReportService;
import org.dspace.app.rest.model.UserActionRest;
import org.dspace.app.rest.model.UserActivityReportRest;
import org.dspace.app.rest.model.UserActivityStatsRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for user activity reporting
 * Provides endpoints to retrieve statistics about user submissions and reviews
 */
@RestController
@RequestMapping("/api/reporting/user-activity")
public class UserActivityReportController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(UserActivityReportController.class);

    @Autowired
    private UserActivityReportService userActivityReportService;

    /**
     * Get full user activity report
     *
     * @param request HTTP request
     * @return UserActivityReportRest with all statistics
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/report")
    public ResponseEntity<?> getFullReport(HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            // Get all statistics
            Map<String, UserActivityStats> userStats = userActivityReportService.getUserStatistics(context);
            Map<String, Integer> totals = userActivityReportService.getTotalStatistics(context);

            // Build response
            UserActivityReportRest report = new UserActivityReportRest();
            report.totalUsers = totals.getOrDefault("totalUsers", 0);
            report.totalSubmissions = totals.getOrDefault("submissions", 0);
            report.totalApprovals = totals.getOrDefault("approvals", 0);
            report.totalRejections = totals.getOrDefault("rejections", 0);
            report.totalWithdrawals = totals.getOrDefault("withdrawals", 0);

            // Convert each user stat to REST model
            for (UserActivityStats stats : userStats.values()) {
                UserActivityStatsRest statsRest = convertToRest(stats);
                report.userStats.add(statsRest);
            }

            context.complete();
            return new ResponseEntity<>(report, HttpStatus.OK);

        } catch (SQLException e) {
            log.error("Error generating user activity report", e);
            return new ResponseEntity<>("Error generating report: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get statistics for a specific user by email
     *
     * @param email   the email of the user
     * @param request HTTP request
     * @return UserActivityStatsRest for the user
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/user/{email}")
    public ResponseEntity<?> getUserReport(@PathVariable String email, HttpServletRequest request) {
        try {
            Context context = ContextUtil.obtainContext(request);

            UserActivityStats userStats = userActivityReportService.getUserStatistics(context, email);

            if (userStats == null) {
                context.complete();
                return new ResponseEntity<>("User not found or has no submissions/reviews",
                        HttpStatus.NOT_FOUND);
            }

            UserActivityStatsRest statsRest = convertToRest(userStats);
            context.complete();

            return new ResponseEntity<>(statsRest, HttpStatus.OK);

        } catch (SQLException e) {
            log.error("Error retrieving user statistics for email: " + email, e);
            return new ResponseEntity<>("Error retrieving user statistics: " + e.getMessage(),
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

            // Convert to REST models
            List<UserActionRest> actionsRest = new java.util.ArrayList<>();
            for (UserAction action : actions) {
                UserActionRest actionRest = new UserActionRest();
                actionRest.actionType = action.getActionType();
                actionRest.userName = action.getUserName();
                actionRest.email = action.getEmail();
                actionRest.itemUUID = action.getItemUUID();
                actionRest.details = action.getDetails();

                if (action.getActionDate() != null) {
                    actionRest.actionDate = action.getActionDate().toString();
                }

                actionsRest.add(actionRest);
            }

            context.complete();
            return new ResponseEntity<>(actionsRest, HttpStatus.OK);

        } catch (SQLException e) {
            log.error("Error retrieving actions", e);
            return new ResponseEntity<>("Error retrieving actions: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Convert UserActivityStats to REST model
     */
    private UserActivityStatsRest convertToRest(UserActivityStats stats) {
        UserActivityStatsRest rest = new UserActivityStatsRest();
        rest.userName = stats.getUserName();
        rest.email = stats.getEmail();
        rest.totalSubmissions = stats.getTotalSubmissions();
        rest.totalApprovals = stats.getTotalApprovals();
        rest.totalRejections = stats.getTotalRejections();
        rest.totalWithdrawals = stats.getTotalWithdrawals();

        for (UserAction action : stats.getActions()) {
            UserActionRest actionRest = new UserActionRest();
            actionRest.actionType = action.getActionType();
            actionRest.userName = action.getUserName();
            actionRest.email = action.getEmail();
            actionRest.itemUUID = action.getItemUUID();
            actionRest.details = action.getDetails();

            if (action.getActionDate() != null) {
                actionRest.actionDate = action.getActionDate().toString();
            }

            rest.actions.add(actionRest);
        }

        return rest;
    }
}
