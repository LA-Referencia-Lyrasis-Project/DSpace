/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response returned by dARK ARK endpoints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DarkArkResponse {

    private String ark;
    private String state;
    private String target;

    @JsonProperty("metadata_cid")
    private String metadataCid;

    @JsonProperty("metadata_schema")
    private String metadataSchema;

    @JsonProperty("minimal_metadata")
    private Map<String, Object> minimalMetadata;

    @JsonProperty("level1_cid")
    private String level1Cid;

    @JsonProperty("level2_cid")
    private String level2Cid;

    @JsonProperty("client_item_id")
    private String clientItemId;

    public String getArk() {
        return ark;
    }

    public void setArk(String ark) {
        this.ark = ark;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getMetadataCid() {
        return metadataCid;
    }

    public void setMetadataCid(String metadataCid) {
        this.metadataCid = metadataCid;
    }

    public String getMetadataSchema() {
        return metadataSchema;
    }

    public void setMetadataSchema(String metadataSchema) {
        this.metadataSchema = metadataSchema;
    }

    public Map<String, Object> getMinimalMetadata() {
        return minimalMetadata;
    }

    public void setMinimalMetadata(Map<String, Object> minimalMetadata) {
        this.minimalMetadata = minimalMetadata;
    }

    public String getLevel1Cid() {
        return level1Cid;
    }

    public void setLevel1Cid(String level1Cid) {
        this.level1Cid = level1Cid;
    }

    public String getLevel2Cid() {
        return level2Cid;
    }

    public void setLevel2Cid(String level2Cid) {
        this.level2Cid = level2Cid;
    }

    public String getClientItemId() {
        return clientItemId;
    }

    public void setClientItemId(String clientItemId) {
        this.clientItemId = clientItemId;
    }
}
