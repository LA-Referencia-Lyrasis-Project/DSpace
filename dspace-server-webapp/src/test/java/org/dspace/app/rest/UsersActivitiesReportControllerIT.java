/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.CacheManager;

/**
 * Integration tests for {@link UsersActivitiesReportController}
 *
 * @author Jesiel Viana
 */
public class UsersActivitiesReportControllerIT extends AbstractControllerIntegrationTest {

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
        public void testGetUsersActivitiesReport() throws Exception {
                context.turnOffAuthorisationSystem();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on "
                                                + "2024-01-15T12:00:00Z workflow start=STEP1\n"
                                                + "Approved for entry into archive by Admin User"
                                                + " (admin@example.com) on 2024-01-16T10:30:00Z")
                                .build();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 2")
                                .withProvenanceData("Step: editstep - action:editaction Approved for "
                                                + "entry into archive by Admin User (admin@example.com) "
                                                + "on 2024-01-17T10:30:00Z")
                                .build();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 3")
                                .withProvenanceData(
                                                "Rejected by Admin User (admin@example.com), reason: Oops on "
                                                                + "2024-01-18T10:30:00Z")
                                .build();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 4")
                                .withProvenanceData("Step: editstep - action:editaction Rejected by "
                                                + "Admin User (admin@example.com), reason: Missing metadata "
                                                + "on 2024-01-19T10:30:00Z")
                                .build();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 5")
                                .withProvenanceData(
                                                "Item withdrawn by User One (user1@example.com) on "
                                                                + "2024-01-20T12:00:00Z")
                                .build();
                context.restoreAuthSystemState();

                String token = getAuthToken(admin.getEmail(), password);

                getClient(token).perform(get("/api/reports/users-activities"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", Matchers.hasSize(2))) // user1 and admin
                                .andExpect(jsonPath("$[?(@.email=='user1@example.com')].totalSubmissions",
                                                Matchers.contains(1)))
                                .andExpect(jsonPath("$[?(@.email=='user1@example.com')].totalWithdrawals",
                                                Matchers.contains(1)))
                                .andExpect(jsonPath("$[?(@.email=='admin@example.com')].totalApprovals",
                                                Matchers.contains(2)))
                                .andExpect(jsonPath("$[?(@.email=='admin@example.com')].totalRejections",
                                                Matchers.contains(2)));
        }

        @Test
        public void testGetSummary() throws Exception {
                context.turnOffAuthorisationSystem();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on "
                                                + "2024-01-15T12:00:00Z\n"
                                                + "Approved for entry into archive by Admin User"
                                                + " (admin@example.com) on 2024-01-16T10:30:00Z")
                                .build();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 2")
                                .withProvenanceData("Rejected by Admin User (admin@example.com), "
                                                + "reason: Outdated on 2024-01-17T10:30:00Z")
                                .build();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 3")
                                .withProvenanceData("Item withdrawn by User One (user1@example.com) "
                                                + "on 2024-01-18T12:00:00Z")
                                .build();
                context.restoreAuthSystemState();

                String token = getAuthToken(admin.getEmail(), password);

                getClient(token).perform(get("/api/reports/users-activities/summary"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.submissions", Matchers.is(1)))
                                .andExpect(jsonPath("$.approvals", Matchers.is(1)))
                                .andExpect(jsonPath("$.rejections", Matchers.is(1)))
                                .andExpect(jsonPath("$.withdrawals", Matchers.is(1)))
                                .andExpect(jsonPath("$.totalUsers", Matchers.is(2)));
        }

        @Test
        public void testGetAllActions() throws Exception {
                context.turnOffAuthorisationSystem();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on "
                                                + "2024-01-15T12:00:00Z\n"
                                                + "Step: editstep - action:editaction Approved for entry "
                                                + "into archive by Admin User (admin@example.com) on "
                                                + "2024-01-16T10:30:00Z")
                                .build();
                context.restoreAuthSystemState();

                String token = getAuthToken(admin.getEmail(), password);

                getClient(token).perform(get("/api/reports/users-activities/actions"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", Matchers.hasSize(2)))
                                .andExpect(jsonPath("$[?(@.email=='user1@example.com')].actionType",
                                                Matchers.contains("SUBMITTED")))
                                .andExpect(jsonPath("$[?(@.email=='admin@example.com')].actionType",
                                                Matchers.contains("APPROVED")));
        }

        @Test
        public void testGetSummaryWithTrends() throws Exception {
                context.turnOffAuthorisationSystem();
                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on "
                                                + "2024-01-15T12:00:00Z\n"
                                                + "Approved for entry into archive by Admin User"
                                                + " (admin@example.com) on 2024-02-15T12:00:00Z")
                                .build();
                context.restoreAuthSystemState();

                String token = getAuthToken(admin.getEmail(), password);

                getClient(token).perform(get("/api/reports/users-activities/summary-with-trends"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.submissions", Matchers.is(1)))
                                .andExpect(jsonPath("$.approvals", Matchers.is(1)))
                                .andExpect(jsonPath("$.trendData['2024-01'].SUBMITTED", Matchers.is(1)))
                                .andExpect(jsonPath("$.trendData['2024-02'].APPROVED", Matchers.is(1)));
        }

        @Test
        public void testUnauthorized() throws Exception {
                getClient().perform(get("/api/reports/users-activities"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        public void testForbiddenForNonAdmin() throws Exception {
                String token = getAuthToken(eperson.getEmail(), password);
                getClient(token).perform(get("/api/reports/users-activities"))
                                .andExpect(status().isForbidden());
        }
}
