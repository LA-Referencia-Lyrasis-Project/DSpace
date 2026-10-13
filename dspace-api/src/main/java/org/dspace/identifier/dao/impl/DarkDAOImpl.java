/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dao.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;
import org.dspace.identifier.DARK;
import org.dspace.identifier.DARK_;
import org.dspace.identifier.dao.DarkDAO;

/**
 * Hibernate implementation of the dARK DAO.
 */
public class DarkDAOImpl extends AbstractHibernateDAO<DARK> implements DarkDAO {

    protected DarkDAOImpl() {
        super();
    }

    @Override
    public DARK findByArk(Context context, String ark) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, DARK.class);
        Root<DARK> darkRoot = criteriaQuery.from(DARK.class);
        criteriaQuery.select(darkRoot);
        criteriaQuery.where(criteriaBuilder.equal(darkRoot.get(DARK_.ark), ark));
        return uniqueResult(context, criteriaQuery, false, DARK.class);
    }

    @Override
    public DARK findDARKByDSpaceObject(Context context, DSpaceObject dso) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, DARK.class);
        Root<DARK> darkRoot = criteriaQuery.from(DARK.class);
        criteriaQuery.select(darkRoot);
        criteriaQuery.where(criteriaBuilder.equal(darkRoot.get(DARK_.dSpaceObject), dso));
        return singleResult(context, criteriaQuery);
    }

    @Override
    public List<UUID> findItemIdsWithoutDARK(Context context) throws SQLException {
        String query = "SELECT i.id FROM " + Item.class.getSimpleName() + " i WHERE NOT EXISTS " +
            "(SELECT d.id FROM " + DARK.class.getSimpleName() + " d WHERE d.dSpaceObject = i)";
        return getHibernateSession(context).createQuery(query, UUID.class).getResultList();
    }

    @Override
    public long countItemsWithDARK(Context context) throws SQLException {
        String query = "SELECT COUNT(i.id) FROM " + Item.class.getSimpleName() + " i WHERE EXISTS " +
            "(SELECT d.id FROM " + DARK.class.getSimpleName() + " d WHERE d.dSpaceObject = i)";
        return getHibernateSession(context).createQuery(query, Long.class).getSingleResult();
    }

    @Override
    public List<DARK> findAll(Context context, String ark, String status, UUID itemId, boolean errorsOnly,
                              int limit, int offset, String sort, boolean ascending) throws SQLException {
        StringBuilder query = new StringBuilder("SELECT d FROM ").append(DARK.class.getSimpleName()).append(" d");
        appendFilters(query, ark, status, itemId, errorsOnly);
        appendSort(query, sort, ascending, "d.updatedAt");
        var hibernateQuery = getHibernateSession(context).createQuery(query.toString(), DARK.class);
        setFilters(hibernateQuery, ark, status, itemId, errorsOnly);
        return hibernateQuery.setFirstResult(offset).setMaxResults(limit)
                             .getResultList();
    }

    @Override
    public long count(Context context, String ark, String status, UUID itemId, boolean errorsOnly) throws SQLException {
        StringBuilder query = new StringBuilder("SELECT COUNT(d.id) FROM ").append(DARK.class.getSimpleName())
            .append(" d");
        appendFilters(query, ark, status, itemId, errorsOnly);
        var hibernateQuery = getHibernateSession(context).createQuery(query.toString(), Long.class);
        setFilters(hibernateQuery, ark, status, itemId, errorsOnly);
        return hibernateQuery.getSingleResult();
    }

    @Override
    public List<Item> findItemsWithoutDARK(Context context, UUID itemId, int limit, int offset) throws SQLException {
        String query = "SELECT i FROM " + Item.class.getSimpleName() + " i WHERE NOT EXISTS " +
            "(SELECT d.id FROM " + DARK.class.getSimpleName() + " d WHERE d.dSpaceObject = i)" +
            (itemId == null ? "" : " AND i.id = :itemId") + " ORDER BY i.lastModified DESC";
        var hibernateQuery = getHibernateSession(context).createQuery(query, Item.class);
        if (itemId != null) {
            hibernateQuery.setParameter("itemId", itemId);
        }
        return hibernateQuery.setFirstResult(offset).setMaxResults(limit)
                             .getResultList();
    }

    @Override
    public long countItemsWithoutDARK(Context context, UUID itemId) throws SQLException {
        String query = "SELECT COUNT(i.id) FROM " + Item.class.getSimpleName() + " i WHERE NOT EXISTS " +
            "(SELECT d.id FROM " + DARK.class.getSimpleName() + " d WHERE d.dSpaceObject = i)" +
            (itemId == null ? "" : " AND i.id = :itemId");
        var hibernateQuery = getHibernateSession(context).createQuery(query, Long.class);
        if (itemId != null) {
            hibernateQuery.setParameter("itemId", itemId);
        }
        return hibernateQuery.getSingleResult();
    }

    private void appendFilters(StringBuilder query, String ark, String status, UUID itemId, boolean errorsOnly) {
        boolean hasFilter = ark != null || status != null || itemId != null || errorsOnly;
        if (!hasFilter) {
            return;
        }
        query.append(" WHERE ");
        boolean needsAnd = false;
        if (ark != null) {
            query.append("LOWER(d.ark) LIKE :ark");
            needsAnd = true;
        }
        if (status != null) {
            if (needsAnd) {
                query.append(" AND ");
            }
            query.append("d.status = :status");
            needsAnd = true;
        }
        if (itemId != null) {
            if (needsAnd) {
                query.append(" AND ");
            }
            query.append("d.dSpaceObject.id = :itemId");
            needsAnd = true;
        }
        if (errorsOnly) {
            if (needsAnd) {
                query.append(" AND ");
            }
            query.append("d.lastError IS NOT NULL");
        }
    }

    private void setFilters(org.hibernate.query.Query<?> query, String ark, String status, UUID itemId,
                            boolean errorsOnly) {
        if (ark != null) {
            query.setParameter("ark", "%" + ark.toLowerCase() + "%");
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        if (itemId != null) {
            query.setParameter("itemId", itemId);
        }
    }

    private void appendSort(StringBuilder query, String sort, boolean ascending, String defaultSort) {
        query.append(" ORDER BY ");
        String property = "createdAt".equals(sort) ? "d.createdAt" : defaultSort;
        query.append(property).append(ascending ? " ASC" : " DESC");
    }

}
