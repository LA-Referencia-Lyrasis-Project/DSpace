/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.identifier.dao.DarkDAO;
import org.dspace.identifier.dark.DarkIdentifierException;
import org.dspace.identifier.service.DarkService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service implementation for {@link DARK}.
 */
public class DarkServiceImpl implements DarkService {

    private static final Pattern ARK_PATTERN = Pattern.compile("^ark:[0-9]{5,}/[^\\s]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARK_WITH_SLASH_PATTERN =
        Pattern.compile("^ark:/[0-9]{5,}/[^\\s]+$", Pattern.CASE_INSENSITIVE);

    @Autowired(required = true)
    protected DarkDAO darkDAO;

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    protected DarkServiceImpl() {
    }

    @Override
    public void update(Context context, DARK dark) throws SQLException {
        darkDAO.save(context, dark);
    }

    @Override
    public DARK create(Context context) throws SQLException {
        return darkDAO.create(context, new DARK());
    }

    @Override
    public void delete(Context context, DARK dark) throws SQLException {
        darkDAO.delete(context, dark);
    }

    @Override
    public List<DARK> findAll(Context context) throws SQLException {
        return darkDAO.findAll(context, DARK.class);
    }

    @Override
    public DARK findByArk(Context context, String ark) throws SQLException {
        return darkDAO.findByArk(context, ark);
    }

    @Override
    public DARK findDARKByDSpaceObject(Context context, DSpaceObject dso) throws SQLException {
        return darkDAO.findDARKByDSpaceObject(context, dso);
    }

    @Override
    public List<UUID> findItemIdsWithoutDARK(Context context) throws SQLException {
        return darkDAO.findItemIdsWithoutDARK(context);
    }

    @Override
    public long countItemsWithDARK(Context context) throws SQLException {
        return darkDAO.countItemsWithDARK(context);
    }

    @Override
    public List<DARK> findAll(Context context, String ark, String status, UUID itemId, boolean errorsOnly,
                              int limit, int offset, String sort, boolean ascending) throws SQLException {
        return darkDAO.findAll(context, ark, status, itemId, errorsOnly, limit, offset, sort, ascending);
    }

    @Override
    public long count(Context context, String ark, String status, UUID itemId, boolean errorsOnly) throws SQLException {
        return darkDAO.count(context, ark, status, itemId, errorsOnly);
    }

    @Override
    public List<org.dspace.content.Item> findItemsWithoutDARK(Context context, UUID itemId, int limit, int offset)
        throws SQLException {
        return darkDAO.findItemsWithoutDARK(context, itemId, limit, offset);
    }

    @Override
    public long countItemsWithoutDARK(Context context, UUID itemId) throws SQLException {
        return darkDAO.countItemsWithoutDARK(context, itemId);
    }

    @Override
    public String formatIdentifier(String identifier) throws DarkIdentifierException {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier is null.", new NullPointerException());
        }

        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("Cannot format an empty identifier.");
        }

        String trimmedIdentifier = identifier.trim();
        if (ARK_PATTERN.matcher(trimmedIdentifier).matches()) {
            return trimmedIdentifier;
        }
        if (ARK_WITH_SLASH_PATTERN.matcher(trimmedIdentifier).matches()) {
            return "ark:" + trimmedIdentifier.substring("ark:/".length());
        }

        throw new DarkIdentifierException("Cannot recognize dARK identifier: " + identifier,
                                          DarkIdentifierException.UNRECOGNIZED);
    }
}
