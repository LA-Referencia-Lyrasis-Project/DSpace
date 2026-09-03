/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for dARK metadata submission.
 */
public class DarkMetadataRequest {

    @JsonProperty("authority_id")
    private String authorityId;

    private String target;

    @JsonProperty("minimal_metadata")
    private Map<String, Object> minimalMetadata;

    @JsonProperty("original_metadata")
    private String originalMetadata;

    @JsonProperty("metadata_schema")
    private String metadataSchema;

    @JsonProperty("metadata_media_type")
    private String metadataMediaType;

    public DarkMetadataRequest() {
    }

    public DarkMetadataRequest(String authorityId, String target, Map<String, Object> minimalMetadata,
                               String originalMetadata, String metadataSchema, String metadataMediaType) {
        this.authorityId = authorityId;
        this.target = target;
        this.minimalMetadata = minimalMetadata;
        this.originalMetadata = originalMetadata;
        this.metadataSchema = metadataSchema;
        this.metadataMediaType = metadataMediaType;
    }

    public String getAuthorityId() {
        return authorityId;
    }

    public void setAuthorityId(String authorityId) {
        this.authorityId = authorityId;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Map<String, Object> getMinimalMetadata() {
        return minimalMetadata;
    }

    public void setMinimalMetadata(Map<String, Object> minimalMetadata) {
        this.minimalMetadata = minimalMetadata;
    }

    public String getOriginalMetadata() {
        return originalMetadata;
    }

    public void setOriginalMetadata(String originalMetadata) {
        this.originalMetadata = originalMetadata;
    }

    public String getMetadataSchema() {
        return metadataSchema;
    }

    public void setMetadataSchema(String metadataSchema) {
        this.metadataSchema = metadataSchema;
    }

    public String getMetadataMediaType() {
        return metadataMediaType;
    }

    public void setMetadataMediaType(String metadataMediaType) {
        this.metadataMediaType = metadataMediaType;
    }
}
