/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Builds dARK Level 1 and Level 2 metadata from a DSpace Item.
 */
public class DarkMetadataBuilder {

    public static final String CFG_TITLE_METADATA = "identifier.dark.metadata.title";
    public static final String CFG_CREATOR_METADATA = "identifier.dark.metadata.creator";
    public static final String CFG_DATE_METADATA = "identifier.dark.metadata.date";
    public static final String CFG_PUBLISHER_METADATA = "identifier.dark.metadata.publisher";
    public static final String CFG_TYPE_METADATA = "identifier.dark.metadata.type";
    public static final String CFG_LANGUAGE_METADATA = "identifier.dark.metadata.language";
    public static final String CFG_ABSTRACT_METADATA = "identifier.dark.metadata.abstract";
    public static final String CFG_SUBJECT_METADATA = "identifier.dark.metadata.subject";
    public static final String CFG_TARGET_URL_PATTERN = "identifier.dark.target-url-pattern";
    public static final String CFG_METADATA_SCHEMA = "identifier.dark.metadata.schema";
    public static final String CFG_METADATA_MEDIA_TYPE = "identifier.dark.metadata.media-type";

    private static final String DEFAULT_TITLE_METADATA = "dc.title";
    private static final String DEFAULT_CREATOR_METADATA = "dc.contributor.author";
    private static final String DEFAULT_DATE_METADATA = "dc.date.issued";
    private static final String DEFAULT_PUBLISHER_METADATA = "dc.publisher";
    private static final String DEFAULT_TYPE_METADATA = "dc.type";
    private static final String DEFAULT_LANGUAGE_METADATA = "dc.language.iso";
    private static final String DEFAULT_ABSTRACT_METADATA = "dc.description.abstract";
    private static final String DEFAULT_SUBJECT_METADATA = "dc.subject";
    private static final String DEFAULT_METADATA_SCHEMA = "oai_dc";
    private static final String DEFAULT_METADATA_MEDIA_TYPE = "application/xml";

    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    @Autowired(required = true)
    protected ItemService itemService;

    @Autowired(required = true)
    protected HandleService handleService;

    public DarkMetadataRequest build(Context context, Item item, String authorityId, String ark)
        throws SQLException {
        String target = buildTargetUrl(context, item);

        Map<String, Object> minimalMetadata = new LinkedHashMap<>();
        putIfPresent(minimalMetadata, "ark", ark);
        putIfPresent(minimalMetadata, "title", firstValue(item, CFG_TITLE_METADATA, DEFAULT_TITLE_METADATA));

        List<String> creators = values(item, CFG_CREATOR_METADATA, DEFAULT_CREATOR_METADATA);
        if (!creators.isEmpty()) {
            minimalMetadata.put("creator", creators);
            minimalMetadata.put("authors", creators);
        }

        String date = firstValue(item, CFG_DATE_METADATA, DEFAULT_DATE_METADATA);
        putIfPresent(minimalMetadata, "date", date);
        putIfPresent(minimalMetadata, "year", extractYear(date));
        putIfPresent(minimalMetadata, "publisher", firstValue(item, CFG_PUBLISHER_METADATA, DEFAULT_PUBLISHER_METADATA));
        putIfPresent(minimalMetadata, "resource_type", firstValue(item, CFG_TYPE_METADATA, DEFAULT_TYPE_METADATA));
        putIfPresent(minimalMetadata, "language", firstValue(item, CFG_LANGUAGE_METADATA, DEFAULT_LANGUAGE_METADATA));
        putIfPresent(minimalMetadata, "abstract", firstValue(item, CFG_ABSTRACT_METADATA, DEFAULT_ABSTRACT_METADATA));

        List<String> subjects = values(item, CFG_SUBJECT_METADATA, DEFAULT_SUBJECT_METADATA);
        if (!subjects.isEmpty()) {
            minimalMetadata.put("subjects", subjects);
        }

        minimalMetadata.put("alternate_identifiers", List.of(
            Map.of("schema", "dspace-item-uuid", "value", item.getID().toString())
        ));
        minimalMetadata.put("alternate_urls", List.of(target));
        Map<String, Object> originalMetadata = new LinkedHashMap<>();
        originalMetadata.put("schema", getMetadataSchema());
        originalMetadata.put("media_type", getMetadataMediaType());
        originalMetadata.put("cid", null);
        minimalMetadata.put("original_metadata", originalMetadata);

        return new DarkMetadataRequest(
            authorityId,
            target,
            minimalMetadata,
            buildOaiDc(item, target),
            getMetadataSchema(),
            getMetadataMediaType()
        );
    }

    /**
     * Returns the Level 1 fields required by dARK that are absent from an Item.
     *
     * @param item Item to validate
     * @return configured metadata fields that require a value before registration
     */
    public List<String> missingRequiredMetadata(Item item) {
        List<String> missing = new ArrayList<>();
        if (values(item, CFG_CREATOR_METADATA, DEFAULT_CREATOR_METADATA).isEmpty()) {
            missing.add(configuredMetadataFields(CFG_CREATOR_METADATA, DEFAULT_CREATOR_METADATA));
        }
        String date = firstValue(item, CFG_DATE_METADATA, DEFAULT_DATE_METADATA);
        if (extractYear(date) == null) {
            missing.add(configuredMetadataFields(CFG_DATE_METADATA, DEFAULT_DATE_METADATA));
        }
        return missing;
    }

    protected String buildTargetUrl(Context context, Item item) throws SQLException {
        String handle = handleService.findHandle(context, item);
        if (StringUtils.isNotBlank(handle)) {
            return handleService.getCanonicalForm(handle);
        }

        String pattern = configurationService.getProperty(CFG_TARGET_URL_PATTERN);
        if (StringUtils.isBlank(pattern)) {
            pattern = StringUtils.removeEnd(configurationService.getProperty("dspace.ui.url", ""), "/") +
                "/items/{uuid}";
        }

        return pattern
            .replace("{uuid}", item.getID().toString())
            .replace("{handle}", StringUtils.defaultString(handle));
    }

    private String getMetadataSchema() {
        return configurationService.getProperty(CFG_METADATA_SCHEMA, DEFAULT_METADATA_SCHEMA);
    }

    private String getMetadataMediaType() {
        return configurationService.getProperty(CFG_METADATA_MEDIA_TYPE, DEFAULT_METADATA_MEDIA_TYPE);
    }

    private String firstValue(Item item, String configKey, String defaultField) {
        List<String> values = values(item, configKey, defaultField);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<String> values(Item item, String configKey, String defaultField) {
        return Arrays.stream(configurationService.getArrayProperty(configKey, new String[] {defaultField}))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .flatMap(field -> itemService.getMetadataByMetadataString(item, field).stream())
            .map(MetadataValue::getValue)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    private String configuredMetadataFields(String configKey, String defaultField) {
        return String.join(", ", configurationService.getArrayProperty(configKey, new String[] {defaultField}));
    }

    private String extractYear(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(date);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String buildOaiDc(Item item, String target) {
        List<String> elements = new ArrayList<>();
        elements.add(xmlElement("dc:identifier", target));
        elements.add(xmlElement("dc:identifier", item.getID().toString()));
        addElements(elements, "dc:title", values(item, CFG_TITLE_METADATA, DEFAULT_TITLE_METADATA));
        addElements(elements, "dc:creator", values(item, CFG_CREATOR_METADATA, DEFAULT_CREATOR_METADATA));
        addElements(elements, "dc:date", values(item, CFG_DATE_METADATA, DEFAULT_DATE_METADATA));
        addElements(elements, "dc:publisher", values(item, CFG_PUBLISHER_METADATA, DEFAULT_PUBLISHER_METADATA));
        addElements(elements, "dc:type", values(item, CFG_TYPE_METADATA, DEFAULT_TYPE_METADATA));
        addElements(elements, "dc:language", values(item, CFG_LANGUAGE_METADATA, DEFAULT_LANGUAGE_METADATA));
        addElements(elements, "dc:description", values(item, CFG_ABSTRACT_METADATA, DEFAULT_ABSTRACT_METADATA));
        addElements(elements, "dc:subject", values(item, CFG_SUBJECT_METADATA, DEFAULT_SUBJECT_METADATA));

        return "<oai_dc:dc xmlns:oai_dc=\"http://www.openarchives.org/OAI/2.0/oai_dc/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n  " +
            elements.stream().filter(Objects::nonNull).collect(Collectors.joining("\n  ")) +
            "\n</oai_dc:dc>";
    }

    private void addElements(List<String> elements, String element, List<String> values) {
        values.stream()
            .map(value -> xmlElement(element, value))
            .forEach(elements::add);
    }

    private String xmlElement(String name, String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return "<" + name + ">" + escapeXml(value) + "</" + name + ">";
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value instanceof String && StringUtils.isBlank((String) value)) {
            return;
        }
        if (value != null) {
            map.put(key, value);
        }
    }
}
