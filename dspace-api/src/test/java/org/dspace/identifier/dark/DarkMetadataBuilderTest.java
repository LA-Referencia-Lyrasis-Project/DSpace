/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DarkMetadataBuilder}.
 */
public class DarkMetadataBuilderTest {

    private DarkMetadataBuilder builder;
    private Item item;
    private ItemService itemService;
    private UUID itemId;

    @Before
    public void setUp() throws Exception {
        builder = new DarkMetadataBuilder();
        builder.configurationService = mock(ConfigurationService.class);
        builder.itemService = mock(ItemService.class);
        builder.handleService = mock(HandleService.class);

        itemId = UUID.randomUUID();
        item = mock(Item.class);
        itemService = builder.itemService;

        when(item.getID()).thenReturn(itemId);
        when(builder.configurationService.getProperty(DarkMetadataBuilder.CFG_TARGET_URL_PATTERN))
            .thenReturn("https://repository.example.org/items/{uuid}");
        when(builder.configurationService.getProperty(DarkMetadataBuilder.CFG_METADATA_SCHEMA, "oai_dc"))
            .thenReturn("oai_dc");
        when(builder.configurationService.getProperty(DarkMetadataBuilder.CFG_METADATA_MEDIA_TYPE, "application/xml"))
            .thenReturn("application/xml");
        metadataFields(DarkMetadataBuilder.CFG_TITLE_METADATA, "dc.title");
        metadataFields(DarkMetadataBuilder.CFG_CREATOR_METADATA, "dc.contributor.author");
        metadataFields(DarkMetadataBuilder.CFG_DATE_METADATA, "dc.date.issued");
        metadataFields(DarkMetadataBuilder.CFG_PUBLISHER_METADATA, "dc.publisher");
        metadataFields(DarkMetadataBuilder.CFG_TYPE_METADATA, "dc.type");
        metadataFields(DarkMetadataBuilder.CFG_LANGUAGE_METADATA, "dc.language.iso");
        metadataFields(DarkMetadataBuilder.CFG_ABSTRACT_METADATA, "dc.description.abstract");
        metadataFields(DarkMetadataBuilder.CFG_SUBJECT_METADATA, "dc.subject");
    }

    @Test
    public void testBuildMapsItemMetadataToDarkPayload() throws Exception {
        Context context = mock(Context.class);
        List<MetadataValue> titles = List.of(metadata("A title & a subtitle"));
        List<MetadataValue> creators = List.of(metadata("Alice"), metadata("Bob"));
        List<MetadataValue> dates = List.of(metadata("2026-05-21"));
        List<MetadataValue> publishers = List.of(metadata("Repository"));
        List<MetadataValue> types = List.of(metadata("Article"));
        List<MetadataValue> languages = List.of(metadata("en"));
        List<MetadataValue> abstracts = List.of(metadata("An abstract"));
        List<MetadataValue> subjects = List.of(metadata("identifiers"));

        when(builder.handleService.findHandle(context, item)).thenReturn(null);
        when(itemService.getMetadataByMetadataString(item, "dc.title")).thenReturn(titles);
        when(itemService.getMetadataByMetadataString(item, "dc.contributor.author")).thenReturn(creators);
        when(itemService.getMetadataByMetadataString(item, "dc.date.issued")).thenReturn(dates);
        when(itemService.getMetadataByMetadataString(item, "dc.publisher")).thenReturn(publishers);
        when(itemService.getMetadataByMetadataString(item, "dc.type")).thenReturn(types);
        when(itemService.getMetadataByMetadataString(item, "dc.language.iso")).thenReturn(languages);
        when(itemService.getMetadataByMetadataString(item, "dc.description.abstract")).thenReturn(abstracts);
        when(itemService.getMetadataByMetadataString(item, "dc.subject")).thenReturn(subjects);

        DarkMetadataRequest request = builder.build(context, item, "platform-demo", "ark:/12345/abc123");

        assertEquals("platform-demo", request.getAuthorityId());
        assertEquals("https://repository.example.org/items/" + itemId, request.getTarget());
        assertEquals("oai_dc", request.getMetadataSchema());
        assertEquals("application/xml", request.getMetadataMediaType());
        assertEquals("A title & a subtitle", request.getMinimalMetadata().get("title"));
        assertEquals("2026", request.getMinimalMetadata().get("year"));
        assertEquals(List.of("Alice", "Bob"), request.getMinimalMetadata().get("authors"));
        assertTrue(request.getOriginalMetadata().contains("<dc:title>A title &amp; a subtitle</dc:title>"));
        assertTrue(((List<Map<String, String>>) request.getMinimalMetadata().get("alternate_identifiers")).get(0)
            .containsValue(itemId.toString()));
    }

    @Test
    public void testBuildCombinesCommaSeparatedMetadataFields() throws Exception {
        Context context = mock(Context.class);
        metadataFields(DarkMetadataBuilder.CFG_CREATOR_METADATA, "dc.contributor", "dc.creator");
        when(builder.handleService.findHandle(context, item)).thenReturn(null);
        when(itemService.getMetadataByMetadataString(item, "dc.contributor"))
            .thenReturn(List.of(metadata("Contributor Author")));
        when(itemService.getMetadataByMetadataString(item, "dc.creator"))
            .thenReturn(List.of(metadata("Creator Author")));

        DarkMetadataRequest request = builder.build(context, item, "platform-demo", "ark:/12345/abc123");

        assertEquals(List.of("Contributor Author", "Creator Author"), request.getMinimalMetadata().get("authors"));
    }

    @Test
    public void testMissingRequiredMetadataUsesAllConfiguredFallbackFields() {
        metadataFields(DarkMetadataBuilder.CFG_CREATOR_METADATA,
                       "dc.creator", "dc.contributor.author", "dc.contributor");
        metadataFields(DarkMetadataBuilder.CFG_DATE_METADATA, "dc.date", "dc.date.issued");
        when(itemService.getMetadataByMetadataString(item, "dc.contributor.author"))
            .thenReturn(List.of(metadata("Fallback Author")));
        when(itemService.getMetadataByMetadataString(item, "dc.date.issued"))
            .thenReturn(List.of(metadata("2020-08-05")));

        assertTrue(builder.missingRequiredMetadata(item).isEmpty());
    }

    private void metadataFields(String key, String... fields) {
        when(builder.configurationService.getArrayProperty(key, new String[] {fields[0]})).thenReturn(fields);
    }

    private MetadataValue metadata(String value) {
        MetadataValue metadataValue = mock(MetadataValue.class);
        when(metadataValue.getValue()).thenReturn(value);
        return metadataValue;
    }
}
