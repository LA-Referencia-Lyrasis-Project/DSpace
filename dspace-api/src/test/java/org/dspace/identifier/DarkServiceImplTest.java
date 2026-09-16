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
    }

    @Test
    public void testFormatIdentifierAcceptsArk() throws Exception {
        assertEquals("ark:12345/abc123", service.formatIdentifier("ark:12345/abc123"));
    }

    @Test
    public void testFormatIdentifierNormalizesArkWithSlash() throws Exception {
        assertEquals("ark:12345/2000000001x", service.formatIdentifier("ark:/12345/2000000001x"));
    }

    @Test(expected = DarkIdentifierException.class)
    public void testFormatIdentifierRejectsInvalidIdentifier() throws Exception {
        service.formatIdentifier("doi:10.5072/test");
    }

}
