/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.reporting.model.SummaryWithTrendData;
import org.dspace.app.reporting.model.UserActivityStats;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.CacheManager;

/**
 * Integration tests for {@link UsersActivitiesReportServiceImpl}
 *
 * @author Jesiel Viana
 */
public class UsersActivitiesReportServiceIT extends AbstractIntegrationTestWithDatabase {

        private UsersActivitiesReportService usersActivitiesReportService = DSpaceServicesFactory.getInstance()
                        .getServiceManager()
                        .getServiceByName(null, UsersActivitiesReportService.class);

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
        public void testGetUsersActivitiesStatistics() throws Exception {
                context.turnOffAuthorisationSystem();

                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on 2024-01-15T12:00:00Z")
                                .build();

                ItemBuilder.createItem(context, col)
                                .withTitle("Item 2")
                                .withProvenanceData(
                                                "Submitted by User One (user1@example.com) on 2024-02-15T12:00:00Z\n" +
                                                                "Approved for entry into archive by Admin User"
                                                                + " (admin@example.com) on 2024-02-16T10:30:00Z")
                                .build();

                context.restoreAuthSystemState();

                Map<String, UserActivityStats> stats = usersActivitiesReportService
                                .getUsersActivitiesStatistics(context);

                assertThat(stats.get("user1@example.com"), notNullValue());
                assertThat(stats.get("user1@example.com").getTotalSubmissions(), is(2));
                assertThat(stats.get("admin@example.com").getTotalApprovals(), is(1));
        }

        @Test
        public void testGetTotalStatistics() throws Exception {
                context.turnOffAuthorisationSystem();

                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on 2024-01-15T12:00:00Z")
                                .build();

                ItemBuilder.createItem(context, col)
                                .withTitle("Item 2")
                                .withProvenanceData(
                                                "Rejected by Admin User (admin@example.com), reason: Typo on "
                                                                + "2024-01-16T10:30:00Z")
                                .build();

                context.restoreAuthSystemState();

                Map<String, Integer> totals = usersActivitiesReportService.getTotalStatistics(context);

                assertThat(totals.get("submissions"), is(1));
                assertThat(totals.get("rejections"), is(1));
                assertThat(totals.get("reviews"), is(1));
                assertThat(totals.get("totalUsers"), is(2));
        }

        @Test
        public void testGetTotalStatisticsWithTrends() throws Exception {
                context.turnOffAuthorisationSystem();

                ItemBuilder.createItem(context, col)
                                .withTitle("Item 1")
                                .withProvenanceData("Submitted by User One (user1@example.com) on 2024-01-15T12:00:00Z")
                                .build();

                ItemBuilder.createItem(context, col)
                                .withTitle("Item 2")
                                .withProvenanceData("Submitted by User Two (user2@example.com) on 2024-02-15T12:00:00Z")
                                .build();

                context.restoreAuthSystemState();

                SummaryWithTrendData summary = usersActivitiesReportService.getTotalStatisticsWithTrends(context);

                assertThat(summary.getSubmissions(), is(2));
                assertThat(summary.getTrendData().size(), is(2));
                assertThat(summary.getTrendData().get("2024-01").get("SUBMITTED"), is(1));
                assertThat(summary.getTrendData().get("2024-02").get("SUBMITTED"), is(1));
        }
}
