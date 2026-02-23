/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.dspace.app.reporting.model.UserAction;
import org.junit.Test;

/**
 * Unit tests for {@link ProvenanceParser}
 *
 * @author Jesiel Viana
 */
public class ProvenanceParserTest {

    @Test
    public void testParseSubmission() {
        String line = "Submitted by User One (user1@example.com) on 2024-01-15T12:00:00Z workflow start=STEP1";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(line);

        assertEquals(1, actions.size());
        UserAction action = actions.get(0);
        assertEquals("SUBMITTED", action.getActionType());
        assertEquals("User One", action.getUserName());
        assertEquals("user1@example.com", action.getEmail());
        assertNotNull(action.getActionDate());
        assertEquals("workflow start=STEP1", action.getDetails());
    }

    @Test
    public void testParseApproval() {
        String line = "Approved for entry into archive by Admin User (admin@example.com) on 2024-01-16T10:30:00Z";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(line);

        assertEquals(1, actions.size());
        UserAction action = actions.get(0);
        assertEquals("APPROVED", action.getActionType());
        assertEquals("Admin User", action.getUserName());
        assertEquals("admin@example.com", action.getEmail());
        assertEquals("Approved for entry into archive", action.getDetails());
    }

    @Test
    public void testParseEditstepApproval() {
        String line = "Step: editstep - action:editaction Approved for entry into archive by "
                + "Admin User (admin@example.com) on 2024-01-17T10:30:00Z";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(line);

        assertEquals(1, actions.size());
        UserAction action = actions.get(0);
        assertEquals("APPROVED", action.getActionType());
        assertEquals("Admin User", action.getUserName());
        assertEquals("Approved for entry into archive (editstep)", action.getDetails());
    }

    @Test
    public void testParseRejection() {
        String line = "Rejected by Admin User (admin@example.com), reason: Oops on 2024-01-18T10:30:00Z";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(line);

        assertEquals(1, actions.size());
        UserAction action = actions.get(0);
        assertEquals("REJECTED", action.getActionType());
        assertEquals("Rejected: Oops", action.getDetails());
    }

    @Test
    public void testParseEditstepRejection() {
        String line = "Step: editstep - action:editaction Rejected by Admin User (admin@example.com), "
                + "reason: Missing metadata on 2024-01-19T10:30:00Z";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(line);

        assertEquals(1, actions.size());
        UserAction action = actions.get(0);
        assertEquals("REJECTED", action.getActionType());
        assertEquals("Rejected: Missing metadata", action.getDetails());
    }

    @Test
    public void testParseWithdrawn() {
        String line = "Item withdrawn by User One (user1@example.com) on 2024-01-20T12:00:00Z";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(line);

        assertEquals(1, actions.size());
        UserAction action = actions.get(0);
        assertEquals("WITHDRAWN", action.getActionType());
        assertEquals("Item withdrawn", action.getDetails());
    }

    @Test
    public void testMultiLineParsing() {
        String provenance = "Submitted by User One (user1@example.com) on 2024-01-15T12:00:00Z\n"
                + "Approved for entry into archive by Admin User (admin@example.com) on 2024-01-16T10:30:00Z";
        List<UserAction> actions = ProvenanceParser.parseProvenanceText(provenance);

        assertEquals(2, actions.size());
        assertEquals("SUBMITTED", actions.get(0).getActionType());
        assertEquals("APPROVED", actions.get(1).getActionType());
    }

    @Test
    public void testDateParsingFormats() {
        // ISO format
        String line1 = "Item withdrawn by U (e) on 2024-01-20T12:00:00Z";
        assertEquals(1, ProvenanceParser.parseProvenanceText(line1).size());

        // GMT format
        String line2 = "Item withdrawn by U (e) on 2024-01-20T12:00:00 (GMT)";
        assertEquals(1, ProvenanceParser.parseProvenanceText(line2).size());
    }

    @Test
    public void testEdgeCases() {
        assertTrue(ProvenanceParser.parseProvenanceText(null).isEmpty());
        assertTrue(ProvenanceParser.parseProvenanceText("").isEmpty());
        assertTrue(ProvenanceParser.parseProvenanceText("   ").isEmpty());
        assertTrue(ProvenanceParser.parseProvenanceText("Invalid line").isEmpty());
    }
}
