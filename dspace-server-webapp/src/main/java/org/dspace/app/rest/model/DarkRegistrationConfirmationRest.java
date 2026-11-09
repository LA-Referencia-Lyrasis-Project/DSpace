/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Explicit administrator confirmation that the displayed public identity was registered with the dARK Minter.
 * It carries only public values; the service re-checks each of them against the current server state.
 *
 * @param keyId        lowercase SHA-256 hex of the raw public key, as displayed by the administration interface
 * @param authorityId  authority UUID, as displayed
 * @param minterApiUrl Minter API destination, as displayed
 * @param confirmed    must be {@code true}; anything else is refused
 */
public record DarkRegistrationConfirmationRest(
    @NotNull @Pattern(regexp = DarkRegistrationConfirmationRest.KEY_ID_REGEX) String keyId,
    @NotNull @Size(max = DarkRegistrationConfirmationRest.MAX_AUTHORITY_LENGTH)
    @Pattern(regexp = DarkRegistrationConfirmationRest.UUID_REGEX) String authorityId,
    @NotNull @Size(max = DarkRegistrationConfirmationRest.MAX_URL_LENGTH)
    @Pattern(regexp = DarkRegistrationConfirmationRest.HTTP_URL_REGEX) String minterApiUrl,
    boolean confirmed) {

    /** SHA-256 rendered as 64 lowercase hexadecimal characters. */
    public static final String KEY_ID_REGEX = "^[0-9a-f]{64}$";
    public static final String UUID_REGEX =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    /** Scheme and host are required; whitespace is never valid in a destination. */
    public static final String HTTP_URL_REGEX = "^https?://\\S+$";
    /** Same limit the Minter applies to X-DARK-Authority-Id. */
    public static final int MAX_AUTHORITY_LENGTH = 255;
    public static final int MAX_URL_LENGTH = 2048;
}
