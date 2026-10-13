/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;
import org.dspace.identifier.DARK;

/**
 * Database access object interface for {@link DARK}.
 */
public interface DarkDAO extends GenericDAO<DARK> {

    DARK findByArk(Context context, String ark) throws SQLException;

    DARK findDARKByDSpaceObject(Context context, DSpaceObject dso) throws SQLException;

    List<UUID> findItemIdsWithoutDARK(Context context) throws SQLException;

    long countItemsWithDARK(Context context) throws SQLException;

    List<DARK> findAll(Context context, String ark, String status, UUID itemId, boolean errorsOnly,
                       int limit, int offset, String sort, boolean ascending) throws SQLException;

    long count(Context context, String ark, String status, UUID itemId, boolean errorsOnly) throws SQLException;

    List<Item> findItemsWithoutDARK(Context context, UUID itemId, int limit, int offset) throws SQLException;

    long countItemsWithoutDARK(Context context, UUID itemId) throws SQLException;

}
