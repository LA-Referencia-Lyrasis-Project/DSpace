/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * REST representation of a user action (submission or review)
 */
public class UserActionRest {
  @JsonProperty("actionType")
  public String actionType;

  @JsonProperty("userName")
  public String userName;

  @JsonProperty("email")
  public String email;

  @JsonProperty("actionDate")
  public String actionDate;

  @JsonProperty("itemUUID")
  public String itemUUID;

  @JsonProperty("details")
  public String details;

  public UserActionRest() {
  }

  public UserActionRest(String actionType, String userName, String email, String actionDate, String itemUUID) {
    this.actionType = actionType;
    this.userName = userName;
    this.email = email;
    this.actionDate = actionDate;
    this.itemUUID = itemUUID;
  }
}
