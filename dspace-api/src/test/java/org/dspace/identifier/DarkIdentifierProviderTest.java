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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.dspace.content.Item;
import org.dspace.content.logic.Filter;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.core.Context;
import org.dspace.identifier.dark.DarkArkResponse;
import org.dspace.identifier.dark.DarkBatchResponse;
import org.dspace.identifier.dark.DarkClient;
import org.dspace.identifier.dark.DarkMetadataBuilder;
import org.dspace.identifier.service.DarkService;
import org.dspace.servicemanager.DSpaceKernelImpl;
import org.dspace.servicemanager.DSpaceKernelInit;
import org.dspace.services.ConfigurationService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DarkIdentifierProvider}.
 */
public class DarkIdentifierProviderTest {

    private static DSpaceKernelImpl kernelImpl;

    private DarkIdentifierProvider provider;
    private DarkService darkService;
    private DarkClient darkClient;
    private Item item;
    private DSpaceObjectService<Item> dsoService;
    private Context context;
    private AtomicReference<DARK> storedDARK;
    private List<DARK> storedDARKs;

    @BeforeClass
    public static void initKernel() {
        String dspaceDir = System.getProperty("dspace.dir");
        if (dspaceDir == null || !Files.exists(Paths.get(dspaceDir).resolve("config/config-definition.xml"))) {
            Path moduleTestDspace = Paths.get("dspace");
            if (!Files.exists(moduleTestDspace.resolve("config/config-definition.xml"))) {
                moduleTestDspace = Paths.get("../dspace");
            }
            System.setProperty("dspace.dir", moduleTestDspace.toAbsolutePath().toString());
        }
        kernelImpl = DSpaceKernelInit.getKernel(null);
        if (!kernelImpl.isRunning()) {
            kernelImpl.start(System.getProperty("dspace.dir"));
        }
    }

    @AfterClass
    public static void destroyKernel() {
        if (kernelImpl != null) {
            kernelImpl.destroy();
        }
    }

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
        context = mock(Context.class);

        dsoService = mock(DSpaceObjectService.class);
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
        when(darkService.formatIdentifier("ark:/12345/abc123")).thenReturn("ark:12345/abc123");
        when(darkService.formatIdentifier("ark:12345/abc123")).thenReturn("ark:12345/abc123");

        DarkArkResponse response = new DarkArkResponse();
        response.setArk("ark:12345/abc123");
        response.setState("R");
        response.setClientItemId(itemId.toString());
        when(darkClient.reserveARK("platform-demo-1788435035", "12345", itemId.toString())).thenReturn(response);

        storedDARK = new AtomicReference<>();
        storedDARKs = new ArrayList<>();
        when(darkService.create(context)).thenAnswer((Answer<DARK>) invocation -> new DARK());
        when(darkService.findDARKByDSpaceObject(eq(context), eq(item), anyList()))
            .thenAnswer(invocation -> storedDARK.get());
        doAnswer(invocation -> {
            DARK dark = invocation.getArgument(1);
            storedDARK.set(dark);
            storedDARKs.add(dark);
            return null;
        }).when(darkService).update(eq(context), any(DARK.class));
    }

    @Test
    public void testMintIsIdempotentForSameItem() throws Exception {
        assertEquals("ark:12345/abc123", provider.mint(context, item));
        assertEquals("ark:12345/abc123", provider.mint(context, item));

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
    public void testSaveDARKStoresBareArkOnlyInConfiguredMetadata() throws Exception {
        when(provider.itemService.getMetadata(any(Item.class), eq("dc"), eq("identifier"), eq("dark"),
                                              org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(java.util.Collections.emptyList());

        provider.saveDARKToObject(context, item, "ark:/12345/abc123");

        verify(provider.itemService).addMetadata(context, item, "dc", "identifier", "dark", null,
                                                 "ark:12345/abc123");
        verify(provider.itemService, never()).clearMetadata(context, item, "dc", "identifier", "uri", null);
        verify(provider.itemService, never()).addMetadata(context, item, "dc", "identifier", "uri", null,
                                                          "ark:12345/abc123");
    }

    @Test
    public void testReserveBatchUsesSingleBatchReservationCall() throws Exception {
        Item secondItem = mock(Item.class);
        UUID secondItemId = UUID.randomUUID();
        when(secondItem.getID()).thenReturn(secondItemId);
        when(dsoService.getTypeText(secondItem)).thenReturn("ITEM");
        when(provider.contentServiceFactory.getDSpaceObjectService(secondItem)).thenReturn(dsoService);
        when(darkService.findDARKByDSpaceObject(eq(context), eq(secondItem), anyList())).thenReturn(null);
        when(darkService.formatIdentifier("ark:12345/def456")).thenReturn("ark:12345/def456");

        DarkArkResponse firstResponse = new DarkArkResponse();
        firstResponse.setArk("ark:12345/abc123");
        firstResponse.setState("R");
        firstResponse.setClientItemId(item.getID().toString());

        DarkArkResponse secondResponse = new DarkArkResponse();
        secondResponse.setArk("ark:12345/def456");
        secondResponse.setState("R");
        secondResponse.setClientItemId(secondItemId.toString());

        DarkBatchResponse response = new DarkBatchResponse();
        response.setResults(List.of(firstResponse, secondResponse));
        when(darkClient.reserveARKs("platform-demo-1788435035", "12345",
                                    List.of(item.getID().toString(), secondItemId.toString())))
            .thenReturn(response);

        Map<UUID, String> reserved = provider.reserveBatch(context, List.of(item, secondItem));

        assertEquals("ark:12345/abc123", reserved.get(item.getID()));
        assertEquals("ark:12345/def456", reserved.get(secondItemId));
        assertEquals(2, storedDARKs.size());
        verify(darkClient).reserveARKs("platform-demo-1788435035", "12345",
                                       List.of(item.getID().toString(), secondItemId.toString()));
        verify(darkClient, never()).reserveARK(any(), any(), any());
    }
}
