/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.embedding.models;

public record EmbeddingRequest(
        Object input,
        String model,
        Integer dimensions,
        String encoding_format) {
}
