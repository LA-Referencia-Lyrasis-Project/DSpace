/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.dark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.dspace.identifier.dark.DarkAuthenticationService;
import org.dspace.identifier.dark.DarkIdentifierException;
import org.junit.Test;

/** The CLI emits only the public key, never private data from nested exception diagnostics. */
public class DarkAuthTest {

    @Test
    public void successfulOutputContainsOnlyPublicKey() throws Exception {
        DarkAuthenticationService service = mock(DarkAuthenticationService.class);
        when(service.generate()).thenReturn("public-key-only");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(0, DarkAuth.generate(service, new PrintStream(output), new PrintStream(errors)));
        assertEquals("public-key-only" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
        assertEquals(0, errors.size());
    }

    @Test
    public void failedOutputNeverIncludesExceptionMaterial() throws Exception {
        DarkAuthenticationService service = mock(DarkAuthenticationService.class);
        when(service.generate()).thenThrow(new DarkIdentifierException("PRIVATE-MATERIAL-MUST-NOT-APPEAR"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(1, DarkAuth.generate(service, new PrintStream(output), new PrintStream(errors)));
        assertEquals(0, output.size());
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains("PRIVATE-MATERIAL"));
    }
}
