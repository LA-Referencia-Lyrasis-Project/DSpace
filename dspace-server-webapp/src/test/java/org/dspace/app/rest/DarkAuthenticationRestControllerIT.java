/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.identifier.dark.DarkAuthenticationService;
import org.dspace.identifier.dark.DarkIdentifierException;
import org.junit.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Authentication configuration is public-key-only and restricted to site administrators. */
public class DarkAuthenticationRestControllerIT extends AbstractControllerIntegrationTest {

    @MockitoBean
    private DarkAuthenticationService authenticationService;

    @Test
    public void administratorCanRetrievePublicKeyWithoutPrivateMaterialOrStoragePaths() throws Exception {
        when(authenticationService.status()).thenReturn(new DarkAuthenticationService.Status(
            true, true, "public-key-only", "fingerprint", "configured", "unverified",
            "authority", "https://example.org/api/v1"));
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(get("/api/admin/darks/authentication"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.publicKey").value("public-key-only"))
            .andExpect(jsonPath("$.keyId").value("fingerprint"))
            .andExpect(jsonPath("$.operationalStatus").value("unverified"))
            .andExpect(jsonPath("$.privateKey").doesNotExist())
            .andExpect(jsonPath("$.storageRoot").doesNotExist());
    }

    @Test
    public void anonymousCannotReadAuthenticationConfiguration() throws Exception {
        getClient().perform(get("/api/admin/darks/authentication")).andExpect(status().isUnauthorized());
    }

    @Test
    public void ordinaryUserCannotReadAuthenticationConfiguration() throws Exception {
        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/admin/darks/authentication")).andExpect(status().isForbidden());
    }
    private static final String KEY_ID = "a".repeat(64);
    private static final String AUTHORITY_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    public void administratorCanConfirmDisplayedRegistration() throws Exception {
        when(authenticationService.confirmRegistration(KEY_ID, AUTHORITY_ID, "https://example.org/api/v1", true))
            .thenReturn(new DarkAuthenticationService.Status(true, true, "public-key-only", KEY_ID,
                "configured", "unverified", AUTHORITY_ID, "https://example.org/api/v1"));
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(post("/api/admin/darks/authentication/registration")
                .contentType("application/json")
                .content("{\"keyId\":\"" + KEY_ID + "\",\"authorityId\":\"" + AUTHORITY_ID + "\","
                    + "\"minterApiUrl\":\"https://example.org/api/v1\",\"confirmed\":true}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.state").value("configured"))
            .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    @Test
    public void malformedRegistrationConfirmationIsRejectedBeforeReachingTheService() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);
        for (String body : new String[] {
            "{}",
            "{\"keyId\":\"fingerprint\",\"authorityId\":\"" + AUTHORITY_ID
                + "\",\"minterApiUrl\":\"https://example.org\",\"confirmed\":true}",
            "{\"keyId\":\"" + KEY_ID + "\",\"authorityId\":\"authority\","
                + "\"minterApiUrl\":\"https://example.org\",\"confirmed\":true}",
            "{\"keyId\":\"" + KEY_ID + "\",\"authorityId\":\"" + AUTHORITY_ID
                + "\",\"minterApiUrl\":\"ftp://example.org\",\"confirmed\":true}"}) {
            getClient(token).perform(post("/api/admin/darks/authentication/registration")
                    .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        verify(authenticationService, never()).confirmRegistration(any(), any(), any(), anyBoolean());
    }

    @Test
    public void ordinaryUserCannotConfirmRegistration() throws Exception {
        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(post("/api/admin/darks/authentication/registration")
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void administratorCanGenerateKeysWhenAuthenticationIsEnabled() throws Exception {
        when(authenticationService.isEnabled()).thenReturn(true);
        when(authenticationService.generate()).thenReturn("public-key-only");
        when(authenticationService.status()).thenReturn(new DarkAuthenticationService.Status(
            true, true, "public-key-only", "fingerprint", "pending_registration", "unverified",
            "authority", "https://example.org/api/v1"));
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(post("/api/admin/darks/authentication/keys"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.publicKey").value("public-key-only"))
            .andExpect(jsonPath("$.state").value("pending_registration"))
            .andExpect(jsonPath("$.privateKey").doesNotExist())
            .andExpect(jsonPath("$.storageRoot").doesNotExist());
        verify(authenticationService).generate();
    }

    @Test
    public void disabledAuthenticationRefusesGenerationEvenForAdministrator() throws Exception {
        when(authenticationService.isEnabled()).thenReturn(false);
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(post("/api/admin/darks/authentication/keys"))
            .andExpect(status().isConflict());
        verify(authenticationService, never()).generate();
    }

    @Test
    public void generationFailureDoesNotExposeExceptionDetailsOrOverwriteKeys() throws Exception {
        when(authenticationService.isEnabled()).thenReturn(true);
        when(authenticationService.generate())
            .thenThrow(new DarkIdentifierException("PRIVATE-MATERIAL-MUST-NOT-APPEAR"));
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(post("/api/admin/darks/authentication/keys"))
            .andExpect(status().isConflict())
            .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains("PRIVATE-MATERIAL")));
        verify(authenticationService).generate();
    }

    @Test
    public void anonymousCannotGenerateKeys() throws Exception {
        getClient().perform(post("/api/admin/darks/authentication/keys"))
            .andExpect(status().isUnauthorized());
        verify(authenticationService, never()).generate();
    }

    @Test
    public void ordinaryUserCannotGenerateKeys() throws Exception {
        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(post("/api/admin/darks/authentication/keys"))
            .andExpect(status().isForbidden());
        verify(authenticationService, never()).generate();
    }

}
