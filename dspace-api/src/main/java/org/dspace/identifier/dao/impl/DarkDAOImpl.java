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

}
