/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

/**
 * Client for the dARK minter API.
 */
public interface DarkClient {

    DarkArkResponse reserveARK(String authorityId, String naan, String clientItemId)
        throws DarkIdentifierException;

    DarkArkResponse getARK(String ark) throws DarkIdentifierException;

    DarkArkResponse updateMetadata(String ark, DarkMetadataRequest metadata)
        throws DarkIdentifierException;

    void tombstoneARK(String ark, String authorityId) throws DarkIdentifierException;
}
