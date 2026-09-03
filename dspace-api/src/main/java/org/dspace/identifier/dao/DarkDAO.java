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

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;
import org.dspace.identifier.DARK;

/**
 * Database access object interface for {@link DARK}.
 */
public interface DarkDAO extends GenericDAO<DARK> {

    DARK findByArk(Context context, String ark) throws SQLException;

    DARK findDARKByDSpaceObject(Context context, DSpaceObject dso) throws SQLException;

    DARK findDARKByDSpaceObject(Context context, DSpaceObject dso, List<Integer> statusToExclude)
        throws SQLException;
}
