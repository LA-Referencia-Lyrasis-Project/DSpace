/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST representation of user activity report
 */
public class UserActivityReportRest {
    @JsonProperty("totalUsers")
    public int totalUsers;

    @JsonProperty("totalSubmissions")
    public int totalSubmissions;

    @JsonProperty("totalApprovals")
    public int totalApprovals;

    @JsonProperty("totalRejections")
    public int totalRejections;

    @JsonProperty("totalWithdrawals")
    public int totalWithdrawals;

    @JsonProperty("userStats")
    public List<UserActivityStatsRest> userStats;

    public UserActivityReportRest() {
        this.userStats = new ArrayList<>();
    }
}
