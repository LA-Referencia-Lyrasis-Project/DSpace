/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.model;


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents aggregated statistics for a user's activity.
 */
public class UserActivityStats {
    /**
     * Full name of the user
     */
    @JsonProperty("userName")
    private String userName;

    /**
     * Email address of the user
     */
    @JsonProperty("email")
    private String email;

    /**
     * Total number of submissions
     */
    @JsonProperty("totalSubmissions")
    private int totalSubmissions;

    /**
     * Total number of approvals
     */
    @JsonProperty("totalApprovals")
    private int totalApprovals;

    /**
     * Total number of reviews
     */
    @JsonProperty("totalReviews")
    private int totalReviews;

    /**
     * Total number of rejections
     */
    @JsonProperty("totalRejections")
    private int totalRejections;

    /**
     * Total number of withdrawals
     */
    @JsonProperty("totalWithdrawals")
    private int totalWithdrawals;



    public UserActivityStats(String userName, String email) {
        this.userName = userName;
        this.email = email;
        this.totalSubmissions = 0;
        this.totalApprovals = 0;
        this.totalRejections = 0;
        this.totalWithdrawals = 0;
        this.totalReviews = 0;
    }

    // Getters and Setters
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(int totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }

    public int getTotalApprovals() {
        return totalApprovals;
    }

    public void setTotalApprovals(int totalApprovals) {
        this.totalApprovals = totalApprovals;
    }

    public int getTotalRejections() {
        return totalRejections;
    }

    public void setTotalRejections(int totalRejections) {
        this.totalRejections = totalRejections;
    }

    public int getTotalWithdrawals() {
        return totalWithdrawals;
    }

    public void setTotalWithdrawals(int totalWithdrawals) {
        this.totalWithdrawals = totalWithdrawals;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(int totalReviews) {
        this.totalReviews = totalReviews;
    }

    public void countAction(String actionType) {
        if ("SUBMITTED".equals(actionType)) {
            this.totalSubmissions++;
        } else if ("APPROVED".equals(actionType)) {
            this.totalApprovals++;
            this.totalReviews++;
        } else if ("REJECTED".equals(actionType)) {
            this.totalRejections++;
            this.totalReviews++;
        } else if ("WITHDRAWN".equals(actionType)) {
            this.totalWithdrawals++;
            this.totalReviews++;
        }
    }

    @Override
    public String toString() {
        return "UserActivityStats{" +
                "userName='" + userName + '\'' +
                ", email='" + email + '\'' +
                ", totalSubmissions=" + totalSubmissions +
                ", totalReviews=" + totalReviews +
                ", totalApprovals=" + totalApprovals +
                ", totalRejections=" + totalRejections +
                ", totalWithdrawals=" + totalWithdrawals +
                '}';
    }
}
