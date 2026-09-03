/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link DarkClientImpl}.
 */
public class DarkClientImplTest {

    @Test
    public void testMinterArkUsesMinterApiForm() {
        assertEquals("ark:12345/2000000002g", DarkClientImpl.minterArk("ark:/12345/2000000002g"));
        assertEquals("ark:12345/2000000002g", DarkClientImpl.minterArk("ark:12345/2000000002g"));
    }
}