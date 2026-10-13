/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.identifier.DARK;
import org.dspace.identifier.service.DarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative read API for the local dARK registry.
 *
 * Minting is intentionally performed through the existing {@code dark} script,
 * which creates a DSpace process and keeps long-running work out of HTTP requests.
 */
@RestController
@RequestMapping("/api/admin/darks")
@PreAuthorize("hasAuthority('ADMIN')")
public class DarkAdminRestController {

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private DarkService darkService;

    @GetMapping
    public DarkPage list(@RequestParam(defaultValue = "assigned") String view,
                         @RequestParam(required = false) String ark,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String itemId,
                         @RequestParam(defaultValue = "false") boolean errorsOnly,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         @RequestParam(defaultValue = "updatedAt") String sort,
                         @RequestParam(defaultValue = "DESC") String direction,
                         HttpServletRequest request) throws SQLException {
        Context context = ContextUtil.obtainContext(request);
        UUID uuid = parseUuid(itemId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by("ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC,
                "createdAt".equals(sort) ? "createdAt" : "updatedAt"));

        if ("missing".equals(view)) {
            List<Item> items = darkService.findItemsWithoutDARK(context, uuid, pageable.getPageSize(),
                (int) pageable.getOffset());
            return new DarkPage(items.stream().map(this::toMissingRow).toList(),
                darkService.countItemsWithoutDARK(context, uuid), pageable.getPageNumber(), pageable.getPageSize());
        }

        String normalizedArk = StringUtils.trimToNull(ark);
        String normalizedStatus = StringUtils.upperCase(StringUtils.trimToNull(status));
        List<DARK> darks = darkService.findAll(context, normalizedArk, normalizedStatus, uuid, errorsOnly,
            pageable.getPageSize(), (int) pageable.getOffset(), sort, "ASC".equalsIgnoreCase(direction));
        return new DarkPage(darks.stream().map(this::toDarkRow).toList(),
            darkService.count(context, normalizedArk, normalizedStatus, uuid, errorsOnly),
            pageable.getPageNumber(), pageable.getPageSize());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public void export(@RequestParam(defaultValue = "assigned") String view,
                       @RequestParam(required = false) String ark,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String itemId,
                       @RequestParam(defaultValue = "false") boolean errorsOnly,
                       HttpServletRequest request, HttpServletResponse response) throws SQLException, IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dark-registry.csv");
        Context context = ContextUtil.obtainContext(request);
        UUID uuid = parseUuid(itemId);
        var writer = response.getWriter();
        if ("missing".equals(view)) {
            writer.println("item_id,title");
            exportMissing(context, uuid, writer);
        } else {
            writer.println("ark,item_id,status,target,last_error,created_at,updated_at");
            exportAssigned(context, StringUtils.trimToNull(ark), StringUtils.upperCase(StringUtils.trimToNull(status)),
                uuid, errorsOnly, writer);
        }
    }

    private void exportAssigned(Context context, String ark, String status, UUID itemId, boolean errorsOnly,
                                java.io.PrintWriter writer) throws SQLException {
        long total = darkService.count(context, ark, status, itemId, errorsOnly);
        for (int page = 0; page * MAX_PAGE_SIZE < total; page++) {
            for (DARK dark : darkService.findAll(context, ark, status, itemId, errorsOnly,
                MAX_PAGE_SIZE, page * MAX_PAGE_SIZE, "updatedAt", false)) {
                DarkRow row = toDarkRow(dark);
                writer.println(csv(row.ark()) + ',' + csv(row.itemId()) + ',' + csv(row.status()) + ',' +
                    csv(row.target()) + ',' + csv(row.lastError()) + ',' + csv(row.createdAt()) + ',' +
                    csv(row.updatedAt()));
            }
        }
    }

    private void exportMissing(Context context, UUID itemId, java.io.PrintWriter writer) throws SQLException {
        long total = darkService.countItemsWithoutDARK(context, itemId);
        for (int page = 0; page * MAX_PAGE_SIZE < total; page++) {
            for (Item item : darkService.findItemsWithoutDARK(context, itemId, MAX_PAGE_SIZE,
                page * MAX_PAGE_SIZE)) {
                MissingRow row = toMissingRow(item);
                writer.println(csv(row.itemId()) + ',' + csv(row.title()));
            }
        }
    }

    private DarkRow toDarkRow(DARK dark) {
        return new DarkRow(dark.getArk(), dark.getDSpaceObject().getID().toString(), dark.getStatus(),
            dark.getTarget(), dark.getLastError(), dark.getCreatedAt(), dark.getUpdatedAt());
    }

    private MissingRow toMissingRow(Item item) {
        return new MissingRow(item.getID().toString(), item.getName());
    }

    private UUID parseUuid(String itemId) {
        return StringUtils.isBlank(itemId) ? null : UUID.fromString(itemId);
    }

    private String csv(Object value) {
        return '"' + StringUtils.defaultString(value == null ? null : value.toString()).replace("\"", "\"\"") + '"';
    }

    public record DarkPage(List<?> content, long totalElements, int page, int size) { }

    public record DarkRow(String ark, String itemId, String status, String target,
                          String lastError, Instant createdAt, Instant updatedAt) { }

    public record MissingRow(String itemId, String title) { }
}
