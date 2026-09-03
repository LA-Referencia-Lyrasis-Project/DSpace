/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dspace.identifier.dark.DarkIdentifierException;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DarkServiceImpl}.
 */
public class DarkServiceImplTest {

    private DarkServiceImpl service;

    @Before
    public void setUp() {
        service = new DarkServiceImpl();
        service.configurationService = mock(ConfigurationService.class);
        when(service.configurationService.getProperty("identifier.dark.resolver-api-url", ""))
            .thenReturn("https://resolver.example/api/v1/");
    }

    @Test
    public void testFormatIdentifierAcceptsArk() throws Exception {
        assertEquals("ark:/12345/abc123", service.formatIdentifier("ark:/12345/abc123"));
    }

    @Test
    public void testFormatIdentifierNormalizesArkWithoutSlash() throws Exception {
        assertEquals("ark:/12345/2000000001x", service.formatIdentifier("ark:12345/2000000001x"));
    }

    @Test
    public void testFormatIdentifierAcceptsResolverUrl() throws Exception {
        assertEquals("ark:/12345/abc123",
                     service.formatIdentifier("https://resolver.example/api/v1/arks/ark:/12345/abc123"));
    }

    @Test(expected = DarkIdentifierException.class)
    public void testFormatIdentifierRejectsInvalidIdentifier() throws Exception {
        service.formatIdentifier("doi:10.5072/test");
    }

    @Test
    public void testExternalFormUsesConfiguredResolver() throws Exception {
        assertEquals("https://resolver.example/api/v1/arks/ark:/12345/abc123",
                     service.DARKToExternalForm("ark:/12345/abc123"));
    }

}
