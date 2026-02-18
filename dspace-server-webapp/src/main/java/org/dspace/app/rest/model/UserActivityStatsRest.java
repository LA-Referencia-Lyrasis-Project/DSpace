/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST representation of user activity statistics
 */
public class UserActivityStatsRest {
    @JsonProperty("userName")
    public String userName;

    @JsonProperty("email")
    public String email;

    @JsonProperty("totalSubmissions")
    public int totalSubmissions;

    @JsonProperty("totalApprovals")
    public int totalApprovals;

    @JsonProperty("totalRejections")
    public int totalRejections;

    @JsonProperty("totalWithdrawals")
    public int totalWithdrawals;

    @JsonProperty("actions")
    public List<UserActionRest> actions;

    public UserActivityStatsRest() {
        this.actions = new ArrayList<>();
    }

    public UserActivityStatsRest(String userName, String email) {
        this.userName = userName;
        this.email = email;
        this.actions = new ArrayList<>();
        this.totalSubmissions = 0;
        this.totalApprovals = 0;
        this.totalRejections = 0;
        this.totalWithdrawals = 0;
    }
}
