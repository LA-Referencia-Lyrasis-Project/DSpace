/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.reporting.model.UserAction;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.CacheManager;

/**
 * Integration tests for {@link UsersActivitiesActionsCacheService}
 *
 * @author Jesiel Viana
 */
public class UsersActivitiesActionsCacheServiceIT extends AbstractIntegrationTestWithDatabase {

        private UsersActivitiesActionsCacheService usersActivitiesActionsCacheService = DSpaceServicesFactory
                        .getInstance().getServiceManager()
                        .getServiceByName(null, UsersActivitiesActionsCacheService.class);

        private Collection col;

        @Before
        @Override
        public void setUp() throws Exception {
                super.setUp();
                // Clear cache before each test
                CacheManager cacheManager = DSpaceServicesFactory.getInstance().getServiceManager()
                                .getServiceByName(null, CacheManager.class);
                if (cacheManager != null && cacheManager.getCache("usersActivities.allActions") != null) {
                        cacheManager.getCache("usersActivities.allActions").clear();
                }

                context.turnOffAuthorisationSystem();
                parentCommunity = CommunityBuilder.createCommunity(context)
                                .withName("Parent Community")
                                .build();
                col = CollectionBuilder.createCollection(context, parentCommunity)
                                .withName("Test Collection")
                                .build();
                context.restoreAuthSystemState();
        }

        @Test
        public void testGetAllActions() throws Exception {
                context.turnOffAuthorisationSystem();

                String provenance = "Submitted by User One (user1@example.com) on 2024-01-15T12:00:00Z "
                                + "workflow start=STEP1\n"
                                + "Approved for entry into archive by Admin User (admin@example.com) on "
                                + "2024-01-16T10:30:00Z";

                Item item = ItemBuilder.createItem(context, col)
                                .withTitle("Test Item")
                                .withProvenanceData(provenance)
                                .build();

                context.restoreAuthSystemState();

                List<UserAction> actions = usersActivitiesActionsCacheService.getAllActions(context);

                assertThat(actions, hasSize(2));
                assertThat(actions.get(0).getEmail(), is("admin@example.com"));
                assertThat(actions.get(0).getActionType(), is("APPROVED"));
                assertThat(actions.get(1).getEmail(), is("user1@example.com"));
                assertThat(actions.get(1).getActionType(), is("SUBMITTED"));
                assertThat(actions.get(0).getItemUUID(), is(item.getID().toString()));
                assertThat(actions.get(1).getItemUUID(), is(item.getID().toString()));
        }
}
