/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary data with trend information aggregated by month.
 * Contains total counts and monthly breakdown of actions.
 */
public class SummaryWithTrendData {
    /**
     * Total number of submissions
     */
    @JsonProperty("submissions")
    private Integer submissions;

    /**
     * Total number of reviews (approvals + rejections + withdrawals)
     */
    @JsonProperty("reviews")
    private Integer reviews;

    /**
     * Total number of approvals
     */
    @JsonProperty("approvals")
    private Integer approvals;

    /**
     * Total number of rejections
     */
    @JsonProperty("rejections")
    private Integer rejections;

    /**
     * Total number of withdrawals
     */
    @JsonProperty("withdrawals")
    private Integer withdrawals;

    /**
     * Total number of unique users
     */
    @JsonProperty("totalUsers")
    private Integer totalUsers;

    /**
     * Trend data aggregated by month (YYYY-MM format)
     * Each month maps to action type counts
     * Example: {"2025-01": {"SUBMITTED": 5, "APPROVED": 3, "REJECTED": 1,
     * "WITHDRAWN": 0}, ...}
     */
    @JsonProperty("trendData")
    private Map<String, Map<String, Integer>> trendData;

    public SummaryWithTrendData() {
        this.trendData = new HashMap<>();
    }

    public SummaryWithTrendData(Integer submissions, Integer reviews, Integer approvals,
                                Integer rejections, Integer withdrawals, Integer totalUsers) {
        this();
        this.submissions = submissions;
        this.reviews = reviews;
        this.approvals = approvals;
        this.rejections = rejections;
        this.withdrawals = withdrawals;
        this.totalUsers = totalUsers;
    }

    // Getters and Setters
    public Integer getSubmissions() {
        return submissions;
    }

    public void setSubmissions(Integer submissions) {
        this.submissions = submissions;
    }

    public Integer getReviews() {
        return reviews;
    }

    public void setReviews(Integer reviews) {
        this.reviews = reviews;
    }

    public Integer getApprovals() {
        return approvals;
    }

    public void setApprovals(Integer approvals) {
        this.approvals = approvals;
    }

    public Integer getRejections() {
        return rejections;
    }

    public void setRejections(Integer rejections) {
        this.rejections = rejections;
    }

    public Integer getWithdrawals() {
        return withdrawals;
    }

    public void setWithdrawals(Integer withdrawals) {
        this.withdrawals = withdrawals;
    }

    public Integer getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Integer totalUsers) {
        this.totalUsers = totalUsers;
    }

    public Map<String, Map<String, Integer>> getTrendData() {
        return trendData;
    }

    public void setTrendData(Map<String, Map<String, Integer>> trendData) {
        this.trendData = trendData;
    }

    /**
     * Add trend data for a specific month and action type
     *
     * @param month      the month in YYYY-MM format
     * @param actionType the action type (SUBMITTED, APPROVED, etc.)
     * @param count      the count to add
     */
    public void addTrendData(String month, String actionType, Integer count) {
        Map<String, Integer> monthData = trendData.computeIfAbsent(month, k -> new HashMap<>());
        monthData.put(actionType, count);
    }

    /**
     * Get trend data for a specific month
     *
     * @param month the month in YYYY-MM format
     * @return map of action type to count, or empty map if month not found
     */
    public Map<String, Integer> getTrendDataForMonth(String month) {
        return trendData.getOrDefault(month, new HashMap<>());
    }
}
