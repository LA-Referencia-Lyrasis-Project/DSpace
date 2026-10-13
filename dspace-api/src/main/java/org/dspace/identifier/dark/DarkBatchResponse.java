/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response returned by the dARK batch reservation endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DarkBatchResponse {

    private List<DarkArkResponse> results;
    private List<DarkBatchError> errors;

    public List<DarkArkResponse> getResults() {
        return results;
    }

    public void setResults(List<DarkArkResponse> results) {
        this.results = results;
    }

    public List<DarkBatchError> getErrors() {
        return errors;
    }

    public void setErrors(List<DarkBatchError> errors) {
        this.errors = errors;
    }
}
