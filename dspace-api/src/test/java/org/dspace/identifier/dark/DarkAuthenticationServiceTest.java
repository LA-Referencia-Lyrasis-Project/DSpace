/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

/** Protocol-vector, key-custody and public-only status tests. */
public class DarkAuthenticationServiceTest {

    private static final String AUTHORITY = "11111111-1111-4111-8111-111111111111";
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private DarkAuthenticationService service;
    private ConfigurationService configuration;
    private Path root;

    @Before
    public void setUp() throws Exception {
        root = temporary.getRoot().toPath().toRealPath();
        configuration = mock(ConfigurationService.class);
        when(configuration.getProperty("dspace.dir")).thenReturn(root.toString());
        when(configuration.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(configuration.getProperty("identifier.dark.authority-id")).thenReturn(AUTHORITY);
        when(configuration.getProperty(DarkClientImpl.CFG_MINTER_API_URL)).thenReturn("https://example.org");
        service = new DarkAuthenticationService();
        ReflectionTestUtils.setField(service, "configurationService", configuration);
    }

    @Test
    public void publishedInteroperabilityVector() throws Exception {
        // Public demonstration fixture from guide section 25; never register this key.
        byte[] fixture = HexFormat.of().parseHex("302e020100300506032b657004220420"
            + "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(fixture));
        byte[] body = ("{\"authority_id\":\"" + AUTHORITY + "\",\"naan\":\"12345\"}")
            .getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = DarkRequestSigner.sign(key,
            "56475aa75463474c0285df5dbf2bcab73da651358839e9b77481b2eab107708c",
            "POST", URI.create("https://example.org/api/v1/arks"), AUTHORITY, body,
            1700000000, UUID.fromString("22222222-2222-4222-8222-222222222222"));
        assertEquals("b4625ce6bb01deb70ccc17b509765e81040ba2307c8921fecce86ca53e2f9cc0",
            headers.get("X-DARK-Content-SHA256"));
        assertEquals("6sEEbLK0VBs69+R4WrWzK4SXickUEYqAtreVoigl1Clq4g2SgAgOGPKBn0vLS/tqm8MpHyPpCILspzRF9hlIAQ==",
            headers.get("X-DARK-Signature"));
        assertEquals(6, headers.size());
    }

    @Test
    public void generationIsSecureAndPublicOnly() throws Exception {
        String publicKey = service.generate();
        assertEquals(32, Base64.getDecoder().decode(publicKey).length);
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(keys()));
        assertEquals(PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(keys().resolve("private.pk8")));
        assertEquals(publicKey, service.status().publicKey());
        assertTrue(service.status().configured());
        assertFalse(service.status().enabled());
        assertEquals("unverified", service.status().operationalStatus());
        String json = new ObjectMapper().writeValueAsString(service.status());
        String encodedPrivate = Base64.getEncoder().encodeToString(Files.readAllBytes(keys().resolve("private.pk8")));
        assertFalse(json.contains(encodedPrivate));
        assertFalse(json.toLowerCase().contains("private"));
        assertFalse(json.contains(root.toString()));
    }

    @Test
    public void neverOverwritesExistingKeys() throws Exception {
        String publicKey = service.generate();
        assertThrows(DarkIdentifierException.class, service::generate);
        assertEquals(publicKey, service.status().publicKey());
    }

    @Test
    public void missingInvalidAndRelaxedKeysFailClosedWithoutParserDetails() throws Exception {
        assertFalse(service.status().configured());
        assertSigningFails();
        service.generate();
        Path privateFile = keys().resolve("private.pk8");
        Files.setPosixFilePermissions(privateFile, PosixFilePermissions.fromString("rw-r--r--"));
        assertFalse(service.status().configured());
        assertSigningFails();
        Files.setPosixFilePermissions(privateFile, PosixFilePermissions.fromString("rw-------"));
        Files.writeString(privateFile, "INVALID-SECRET-MUST-NOT-APPEAR");
        assertSigningFails();
    }

    @Test
    public void rejectsMismatchedPairAndSymbolicLinks() throws Exception {
        service.generate();
        var other = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.write(keys().resolve("public.der"), other.getPublic().getEncoded());
        assertSigningFails();
        Path privateFile = keys().resolve("private.pk8");
        Path target = keys().resolve("saved.pk8");
        Files.move(privateFile, target);
        Files.createSymbolicLink(privateFile, target);
        assertSigningFails();
    }

    @Test
    public void rawPathQueryExactBodyFreshNonceAndSignatureVerify() throws Exception {
        service.generate();
        service.confirmRegistration(service.status().keyId(), AUTHORITY, "https://example.org/api/v1", true);
        URI uri = URI.create("https://example.org/api/v1/arks?a=1&a=2&label=hello%20world");
        byte[] body = "{\"title\":\"Ciência\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> first = service.headers("PUT", uri, AUTHORITY, body);
        Map<String, String> second = service.headers("PUT", uri, AUTHORITY, body);
        assertFalse(first.get("X-DARK-Nonce").equals(second.get("X-DARK-Nonce")));
        String message = String.join("\n", "DARK2", "PUT", uri.getRawPath() + "?" + uri.getRawQuery(),
            AUTHORITY, first.get("X-DARK-Key-Id"), first.get("X-DARK-Timestamp"), first.get("X-DARK-Nonce"),
            DarkRequestSigner.sha256(body));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
            new X509EncodedKeySpec(Files.readAllBytes(keys().resolve("public.der")))));
        verifier.update(message.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(first.get("X-DARK-Signature"))));
        verifier.update((message + "\n").getBytes(StandardCharsets.UTF_8));
        assertFalse(verifier.verify(Base64.getDecoder().decode(first.get("X-DARK-Signature"))));
    }

    @Test
    public void rejectsInvalidAuthorityInsecureRemoteTransportAndEscapingDirectory() throws Exception {
        service.generate();
        assertThrows(DarkIdentifierException.class, () -> service.headers("POST",
            URI.create("http://example.org/api/v1/arks"), AUTHORITY, new byte[0]));
        assertThrows(DarkIdentifierException.class, () -> service.headers("POST",
            URI.create("https://example.org/api/v1/arks"), "invalid-authority", new byte[0]));
        when(configuration.getProperty(org.mockito.ArgumentMatchers.eq(DarkAuthenticationService.CFG_DIRECTORY),
            anyString())).thenReturn(root.resolve("escape").toString());
        assertSigningFails();
        assertThrows(DarkIdentifierException.class, service::generate);
    }

    @Test
    public void registrationBindsKeyAuthorityAndDestinationAndSurvivesRestart() throws Exception {
        when(configuration.getBooleanProperty(DarkAuthenticationService.CFG_ENABLED, false)).thenReturn(true);
        service.generate();
        assertEquals("pending_registration", service.status().state());
        assertSigningFails();
        assertThrows(DarkIdentifierException.class, () -> service.confirmRegistration(
            "stale-key-id", AUTHORITY, "https://example.org/api/v1", true));
        assertThrows(DarkIdentifierException.class, () -> service.confirmRegistration(
            service.status().keyId(), AUTHORITY, "https://example.org/api/v1", false));
        service.confirmRegistration(service.status().keyId(), AUTHORITY, "https://example.org/api/v1", true);
        assertEquals("configured", service.status().state());
        service = new DarkAuthenticationService();
        ReflectionTestUtils.setField(service, "configurationService", configuration);
        assertEquals("configured", service.status().state());
        when(configuration.getProperty(DarkClientImpl.CFG_MINTER_API_URL)).thenReturn("https://other.example.org");
        assertEquals("pending_registration", service.status().state());
        assertSigningFails();
    }

    private Path keys() {
        return root.resolve("keys/dark");
    }

    private void assertSigningFails() {
        DarkIdentifierException error = assertThrows(DarkIdentifierException.class, () -> service.headers("POST",
            URI.create("https://example.org/api/v1/arks"), AUTHORITY, new byte[0]));
        assertEquals(DarkIdentifierException.AUTHENTICATION_ERROR, error.getCode());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("INVALID-SECRET"));
    }
}
