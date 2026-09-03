/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.dspace.content.Item;
import org.dspace.content.logic.Filter;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.core.Constants;
import org.dspace.AbstractUnitTest;
import org.dspace.identifier.dark.DarkArkResponse;
import org.dspace.identifier.dark.DarkClient;
import org.dspace.identifier.dark.DarkMetadataBuilder;
import org.dspace.identifier.service.DarkService;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DarkIdentifierProvider}.
 */
public class DarkIdentifierProviderTest extends AbstractUnitTest {

    private DarkIdentifierProvider provider;
    private DarkService darkService;
    private DarkClient darkClient;
    private Item item;
    private AtomicReference<DARK> storedDARK;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        provider = new DarkIdentifierProvider();
        provider.setConfigurationService(mock(ConfigurationService.class));
        provider.contentServiceFactory = mock(org.dspace.content.factory.ContentServiceFactory.class);
        provider.metadataBuilder = mock(DarkMetadataBuilder.class);
        provider.itemService = mock(org.dspace.content.service.ItemService.class);
        provider.darkMetadataFieldName = new org.dspace.content.MetadataFieldName("dc.identifier.dark");

        darkService = mock(DarkService.class);
        darkClient = mock(DarkClient.class);
        provider.darkService = darkService;
        provider.darkClient = darkClient;

        item = mock(Item.class);
        UUID itemId = UUID.randomUUID();
        when(item.getID()).thenReturn(itemId);
        when(item.getType()).thenReturn(Constants.ITEM);

        DSpaceObjectService<Item> dsoService = mock(DSpaceObjectService.class);
        when(dsoService.getTypeText(item)).thenReturn("ITEM");
        when(provider.contentServiceFactory.getDSpaceObjectService(item)).thenReturn(dsoService);

        Filter filter = mock(Filter.class);
        when(filter.getResult(context, item)).thenReturn(true);
        provider.setFilter(filter);

        when(provider.configurationService.getProperty(DarkIdentifierProvider.CFG_AUTHORITY_ID))
            .thenReturn("platform-demo-1788435035");
        when(provider.configurationService.getProperty(DarkIdentifierProvider.CFG_NAAN)).thenReturn("12345");
        when(provider.configurationService.getBooleanProperty(DarkIdentifierProvider.CFG_ENABLED, false))
            .thenReturn(true);
        when(darkService.formatIdentifier("ark:/12345/abc123")).thenReturn("ark:/12345/abc123");
        when(darkService.formatIdentifier("ark:12345/abc123")).thenReturn("ark:/12345/abc123");

        DarkArkResponse response = new DarkArkResponse();
        response.setArk("ark:12345/abc123");
        response.setState("R");
        response.setClientItemId(itemId.toString());
        when(darkClient.reserveARK("platform-demo-1788435035", "12345", itemId.toString())).thenReturn(response);

        storedDARK = new AtomicReference<>();
        when(darkService.create(context)).thenAnswer((Answer<DARK>) invocation -> new DARK());
        when(darkService.findDARKByDSpaceObject(eq(context), eq(item), anyList()))
            .thenAnswer(invocation -> storedDARK.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            storedDARK.set(invocation.getArgument(1));
            return null;
        }).when(darkService).update(eq(context), any(DARK.class));
    }

    @Test
    public void testMintIsIdempotentForSameItem() throws Exception {
        assertEquals("ark:/12345/abc123", provider.mint(context, item));
        assertEquals("ark:/12345/abc123", provider.mint(context, item));

        verify(darkClient, times(1)).reserveARK("platform-demo-1788435035", "12345", item.getID().toString());
        assertEquals(DarkIdentifierProvider.RESERVED, storedDARK.get().getStatus());
    }

    @Test
    public void testMintDoesNothingWhenDisabled() throws Exception {
        when(provider.configurationService.getBooleanProperty(DarkIdentifierProvider.CFG_ENABLED, false))
            .thenReturn(false);

        assertEquals(null, provider.mint(context, item));

        verifyNoInteractions(darkClient, darkService);
    }

    @Test
    public void testSaveDARKReplacesPrimaryUriWhenConfigured() throws Exception {
        when(provider.configurationService.getBooleanProperty(DarkIdentifierProvider.CFG_PRIMARY_URI_ENABLED, false))
            .thenReturn(true);
        when(provider.configurationService.getProperty(DarkIdentifierProvider.CFG_PRIMARY_URI_METADATA,
                                                       "dc.identifier.uri"))
            .thenReturn("dc.identifier.uri");
        when(darkService.DARKToExternalForm("ark:/12345/abc123"))
            .thenReturn("http://localhost:8002/api/v1/arks/ark:/12345/abc123");
        when(provider.itemService.getMetadata(any(Item.class), eq("dc"), eq("identifier"), eq("dark"),
                                              org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(java.util.Collections.emptyList());

        provider.saveDARKToObject(context, item, "ark:/12345/abc123");

        verify(provider.itemService).clearMetadata(context, item, "dc", "identifier", "uri", null);
        verify(provider.itemService).addMetadata(context, item, "dc", "identifier", "uri", null,
                                                 "http://localhost:8002/api/v1/arks/ark:/12345/abc123");
    }
}
