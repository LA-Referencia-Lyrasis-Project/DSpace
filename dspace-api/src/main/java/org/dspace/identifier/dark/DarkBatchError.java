/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error item returned by the dARK batch reservation endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DarkBatchError {

    @JsonProperty("client_item_id")
    private String clientItemId;

    private String error;
    private Integer index;

    public String getClientItemId() {
        return clientItemId;
    }

    public void setClientItemId(String clientItemId) {
        this.clientItemId = clientItemId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }
}
