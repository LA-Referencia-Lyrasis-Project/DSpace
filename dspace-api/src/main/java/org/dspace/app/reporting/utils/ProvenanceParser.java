/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reporting.utils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.reporting.model.UserAction;

/**
 * Utility class to parse provenance metadata and extract user actions.
 */
public class ProvenanceParser {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ProvenanceParser.class);

    private ProvenanceParser() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    // Pattern to match "Submitted by Name (email) on Date ..."
    private static final Pattern SUBMIT_PATTERN = Pattern.compile(
            "Submitted by\\s+([^(]+?)\\s+\\(([^)]+)\\)\\s+on\\s+([^\\s]+)",
            Pattern.CASE_INSENSITIVE);

    // Pattern to match "Approved for entry into archive by Name (email) on Date
    // ..."
    private static final Pattern APPROVAL_PATTERN = Pattern.compile(
            "Approved for entry into archive by\\s+([^(]+?)\\s+\\(([^)]+)\\)\\s+on\\s+([^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    // Pattern to match "Step: editstep - action:editaction Approved for entry into
    // archive by Name (email) on Date ..."
    private static final Pattern EDITSTEP_APPROVAL_PATTERN = Pattern.compile(
            "Step:\\s+editstep\\s+-\\s+action:editaction\\s+" +
                    "Approved for entry into archive by\\s+([^(]+?)\\s+\\(([^)]+)\\)\\s+on\\s+([^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    // Pattern to match "Step: editstep - action:editaction Rejected by Name
    // (email), reason: ... on Date ..."
    private static final Pattern EDITSTEP_REJECTION_PATTERN = Pattern.compile(
            "Step:\\s+editstep\\s+-\\s+action:editaction\\s+" +
                    "Rejected by\\s+([^(]+?)\\s+\\(([^)]+)\\),\\s+reason:.*?\\s+on\\s+([^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    // Pattern to match "Rejected by Name (email), reason: ... on Date ..."
    private static final Pattern REJECTION_PATTERN = Pattern.compile(
            "Rejected by\\s+([^(]+?)\\s+\\(([^)]+)\\),\\s+reason:.*?\\s+on\\s+([^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    // Pattern to match "Item withdrawn by Name (email) on Date"
    private static final Pattern WITHDRAWN_PATTERN = Pattern.compile(
            "Item withdrawn by\\s+([^(]+?)\\s+\\(([^)]+)\\)\\s+on\\s+([^\\s]+)",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Parse provenance text and extract user actions
     *
     * @param provenanceText the full provenance text value from metadata
     * @return list of UserAction objects extracted from the text
     */
    public static List<UserAction> parseProvenanceText(String provenanceText) {
        List<UserAction> actions = new ArrayList<>();

        if (StringUtils.isBlank(provenanceText)) {
            return actions;
        }

        // Split the text into lines for individual processing
        String[] lines = provenanceText.split("\n");

        for (String line : lines) {
            line = line.trim();

            // Try to parse as submission
            UserAction submitAction = parseSubmissionLine(line);
            if (submitAction != null) {
                actions.add(submitAction);
                continue;
            }

            // Try to parse as editstep approval (with Step: prefix)
            UserAction editstepApprovalAction = parseEditstepApprovalLine(line);
            if (editstepApprovalAction != null) {
                actions.add(editstepApprovalAction);
                continue;
            }

            // Try to parse as editstep rejection (with Step: prefix)
            UserAction editstepRejectionAction = parseEditstepRejectionLine(line);
            if (editstepRejectionAction != null) {
                actions.add(editstepRejectionAction);
                continue;
            }

            // Try to parse as regular approval (without Step: prefix)
            UserAction approvalAction = parseApprovalLine(line);
            if (approvalAction != null) {
                actions.add(approvalAction);
                continue;
            }

            // Try to parse as regular rejection (without Step: prefix)
            UserAction rejectionAction = parseRejectionLine(line);
            if (rejectionAction != null) {
                actions.add(rejectionAction);
                continue;
            }

            // Try to parse as withdrawn
            UserAction withdrawnAction = parseWithdrawnLine(line);
            if (withdrawnAction != null) {
                actions.add(withdrawnAction);
                continue;
            }
        }

        return actions;
    }

    /**
     * Parse a submission line
     * Format: "Submitted by Name (email) on Date ..."
     */
    private static UserAction parseSubmissionLine(String line) {
        Matcher matcher = SUBMIT_PATTERN.matcher(line);
        if (matcher.find()) {
            String userName = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            String dateStr = matcher.group(3).trim();

            try {
                ZonedDateTime actionDate = parseDate(dateStr);
                UserAction action = new UserAction();
                action.setActionType("SUBMITTED");
                action.setUserName(userName);
                action.setEmail(email);
                action.setActionDate(actionDate);

                // Extract details from the line (workflow start info)
                if (line.contains("workflow start")) {
                    String details = line.substring(line.indexOf("workflow start"));
                    action.setDetails(details);
                }

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse submission line: " + line, e);
            }
        }
        return null;
    }

    /**
     * Parse an approval/review line (without Step: prefix)
     * Format: "Approved for entry into archive by Name (email) on Date ..."
     */
    private static UserAction parseApprovalLine(String line) {
        // Skip if it's an editstep line (will be handled by editstep parser)
        if (line.contains("Step:") && line.contains("action:editaction")) {
            return null;
        }

        Matcher matcher = APPROVAL_PATTERN.matcher(line);
        if (matcher.find()) {
            String userName = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            String dateStr = matcher.group(3).trim();

            try {
                ZonedDateTime actionDate = parseDate(dateStr);
                UserAction action = new UserAction();
                action.setActionType("APPROVED");
                action.setUserName(userName);
                action.setEmail(email);
                action.setActionDate(actionDate);
                action.setDetails("Approved for entry into archive");

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse approval line: " + line, e);
            }
        }
        return null;
    }

    /**
     * Parse an editstep approval line (with Step: prefix)
     * Format: "Step: editstep - action:editaction Approved for entry into archive
     * by Name (email) on Date ..."
     */
    private static UserAction parseEditstepApprovalLine(String line) {
        Matcher matcher = EDITSTEP_APPROVAL_PATTERN.matcher(line);
        if (matcher.find()) {
            String userName = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            String dateStr = matcher.group(3).trim();

            try {
                ZonedDateTime actionDate = parseDate(dateStr);
                UserAction action = new UserAction();
                action.setActionType("APPROVED");
                action.setUserName(userName);
                action.setEmail(email);
                action.setActionDate(actionDate);
                action.setDetails("Approved for entry into archive (editstep)");

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse editstep approval line: " + line, e);
            }
        }
        return null;
    }

    /**
     * Parse an editstep rejection line (with Step: prefix)
     * Format: "Step: editstep - action:editaction Rejected by Name (email), reason:
     * ... on Date ..."
     */
    private static UserAction parseEditstepRejectionLine(String line) {
        Matcher matcher = EDITSTEP_REJECTION_PATTERN.matcher(line);
        if (matcher.find()) {
            String userName = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            String dateStr = matcher.group(3).trim();

            try {
                ZonedDateTime actionDate = parseDate(dateStr);
                UserAction action = new UserAction();
                action.setActionType("REJECTED");
                action.setUserName(userName);
                action.setEmail(email);
                action.setActionDate(actionDate);

                // Extract reason if available
                int reasonStart = line.indexOf("reason:");
                int onIndex = line.indexOf(" on " + dateStr);
                if (reasonStart != -1 && onIndex != -1) {
                    String reason = line.substring(reasonStart + 7, onIndex).trim();
                    action.setDetails("Rejected: " + reason);
                } else {
                    action.setDetails("Rejected (editstep)");
                }

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse editstep rejection line: " + line, e);
            }
        }
        return null;
    }

    /**
     * Parse a rejection line (without Step: prefix)
     * Format: "Rejected by Name (email), reason: ... on Date ..."
     */
    private static UserAction parseRejectionLine(String line) {
        // Skip if it's an editstep line (will be handled by editstep parser)
        if (line.contains("Step:") && line.contains("action:editaction")) {
            return null;
        }

        Matcher matcher = REJECTION_PATTERN.matcher(line);
        if (matcher.find()) {
            String userName = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            String dateStr = matcher.group(3).trim();

            try {
                ZonedDateTime actionDate = parseDate(dateStr);
                UserAction action = new UserAction();
                action.setActionType("REJECTED");
                action.setUserName(userName);
                action.setEmail(email);
                action.setActionDate(actionDate);

                // Extract reason if available
                int reasonStart = line.indexOf("reason:");
                int onIndex = line.indexOf(" on " + dateStr);
                if (reasonStart != -1 && onIndex != -1) {
                    String reason = line.substring(reasonStart + 7, onIndex).trim();
                    action.setDetails("Rejected: " + reason);
                } else {
                    action.setDetails("Rejected");
                }

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse rejection line: " + line, e);
            }
        }
        return null;
    }

    /**
     * Parse a withdrawn line
     * Format: "Item withdrawn by Name (email) on Date"
     */
    private static UserAction parseWithdrawnLine(String line) {
        Matcher matcher = WITHDRAWN_PATTERN.matcher(line);
        if (matcher.find()) {
            String userName = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            String dateStr = matcher.group(3).trim();

            try {
                ZonedDateTime actionDate = parseDate(dateStr);
                UserAction action = new UserAction();
                action.setActionType("WITHDRAWN");
                action.setUserName(userName);
                action.setEmail(email);
                action.setActionDate(actionDate);
                action.setDetails("Item withdrawn");

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse withdrawn line: " + line, e);
            }
        }
        return null;
    }

    /**
     * Parse ISO 8601 date string
     */
    private static ZonedDateTime parseDate(String dateStr) {
        try {
            // Try ISO format with Z suffix
            if (dateStr.endsWith("Z")) {
                return ZonedDateTime.parse(dateStr, ISO_FORMATTER);
            }

            // Try with GMT suffix
            if (dateStr.contains("GMT")) {
                String cleanDate = dateStr.replace("(GMT)", "").trim();
                return ZonedDateTime.parse(cleanDate, ISO_FORMATTER);
            }

            // Try parsing as ISO format
            return ZonedDateTime.parse(dateStr, ISO_FORMATTER);
        } catch (Exception e) {
            log.debug("Failed to parse date: " + dateStr, e);
            return null;
        }
    }
}
