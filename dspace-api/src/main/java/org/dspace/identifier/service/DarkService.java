/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.identifier.DARK;
import org.dspace.identifier.dark.DarkIdentifierException;

/**
 * Service interface for {@link DARK} identifiers.
 */
public interface DarkService {

    void update(Context context, DARK dark) throws SQLException;

    DARK create(Context context) throws SQLException;

    void delete(Context context, DARK dark) throws SQLException;

    List<DARK> findAll(Context context) throws SQLException;

    DARK findByArk(Context context, String ark) throws SQLException;

    DARK findDARKByDSpaceObject(Context context, DSpaceObject dso) throws SQLException;

    List<UUID> findItemIdsWithoutDARK(Context context) throws SQLException;

    long countItemsWithDARK(Context context) throws SQLException;

    String formatIdentifier(String identifier) throws DarkIdentifierException;
}
