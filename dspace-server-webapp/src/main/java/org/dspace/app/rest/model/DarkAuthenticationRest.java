/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import org.dspace.identifier.dark.DarkAuthenticationService;

/** Explicit public-only whitelist for administrative authentication responses. */
public record DarkAuthenticationRest(boolean enabled, boolean configured, String publicKey, String keyId,
                                     String state, String operationalStatus, String authorityId, String minterApiUrl) {

    /** Converts local status without exposing server credential objects, files or diagnostics. */
    public static DarkAuthenticationRest from(DarkAuthenticationService.Status status) {
        return new DarkAuthenticationRest(status.enabled(), status.configured(), status.publicKey(), status.keyId(),
            status.state(), status.operationalStatus(), status.authorityId(), status.minterApiUrl());
    }
}
