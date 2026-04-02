/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.model;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an action (submission or review) by a user.
 */
public class UserAction {
    /**
     * Type of action: "SUBMITTED" or "REVIEWED"
     */
    @JsonProperty("actionType")
    private String actionType;

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
     * Date and time when the action was performed
     */
    @JsonProperty("actionDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
    private ZonedDateTime actionDate;

    /**
     * Item UUID that was submitted or reviewed
     */
    @JsonProperty("itemUUID")
    private String itemUUID;

    /**
     * Item identifier (same value as itemUUID for compatibility)
     */
    @JsonProperty("itemId")
    private String itemId;

    /**
     * Item title
     */
    @JsonProperty("itemTitle")
    private String itemTitle;

    /**
     * Additional details (e.g., "Approved for entry into archive")
     */
    @JsonProperty("details")
    private String details;

    public UserAction() {
    }

    public UserAction(String actionType, String userName, String email, ZonedDateTime actionDate, String itemUUID) {
        this.actionType = actionType;
        this.userName = userName;
        this.email = email;
        this.actionDate = actionDate;
        this.itemUUID = itemUUID;
        this.itemId = itemUUID;
    }

    // Getters and Setters
    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

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

    public ZonedDateTime getActionDate() {
        return actionDate;
    }

    public void setActionDate(ZonedDateTime actionDate) {
        this.actionDate = actionDate;
    }

    public String getItemUUID() {
        return itemUUID;
    }

    public void setItemUUID(String itemUUID) {
        this.itemUUID = itemUUID;
        this.itemId = itemUUID;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return "UserAction{" +
                "actionType='" + actionType + '\'' +
                ", userName='" + userName + '\'' +
                ", email='" + email + '\'' +
                ", actionDate=" + actionDate +
                ", itemUUID='" + itemUUID + '\'' +
                ", itemId='" + itemId + '\'' +
                ", itemTitle='" + itemTitle + '\'' +
                ", details='" + details + '\'' +
                '}';
    }
}
