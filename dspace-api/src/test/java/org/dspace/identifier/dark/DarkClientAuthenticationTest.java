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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.apache.http.HttpVersion;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.dspace.app.client.DSpaceHttpClientFactory;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Signed and legacy HTTP integration without a live Minter. */
public class DarkClientAuthenticationTest {

    private static final String AUTHORITY = "11111111-1111-4111-8111-111111111111";
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private ConfigurationService configuration;
    private DarkAuthenticationService authentication;
    private DarkClientImpl dark;
    private CloseableHttpClient http;
    private CloseableHttpResponse response;
    private DSpaceHttpClientFactory factory;

    @Before
    public void setUp() throws Exception {
        configuration = mock(ConfigurationService.class);
        when(configuration.getProperty("dspace.dir")).thenReturn(temporary.getRoot().toPath().toRealPath().toString());
        when(configuration.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(configuration.getProperty("identifier.dark.authority-id")).thenReturn(AUTHORITY);
        when(configuration.getProperty(DarkClientImpl.CFG_MINTER_API_URL))
            .thenReturn("https://example.org/prefix/api/v1");
        when(configuration.getBooleanProperty(DarkAuthenticationService.CFG_ENABLED, false)).thenReturn(true);
        authentication = new DarkAuthenticationService();
        ReflectionTestUtils.setField(authentication, "configurationService", configuration);
        authentication.generate();
        authentication.confirmRegistration(authentication.status().keyId(), AUTHORITY,
            "https://example.org/prefix/api/v1", true);
        factory = mock(DSpaceHttpClientFactory.class);
        http = mock(CloseableHttpClient.class);
        response = mock(CloseableHttpResponse.class);
        when(factory.build(any(Consumer.class))).thenReturn(http);
        when(factory.build()).thenReturn(http);
        when(http.execute(any(HttpUriRequest.class))).thenReturn(response);
        reply(200, "{\"results\":[{\"ark\":\"ark:12345/test\"}]}");
        dark = new DarkClientImpl();
        dark.configurationService = configuration;
        dark.authenticationService = authentication;
        dark.httpClientFactory = factory;
    }

    @Test
    public void signedBatchAndMetadataPreserveWireBodyAndDisableResends() throws Exception {
        dark.reserveARKs(AUTHORITY, "12345", List.of("stable-item-id"));
        ArgumentCaptor<HttpUriRequest> requests = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(http).execute(requests.capture());
        HttpUriRequest request = requests.getValue();
        assertEquals("/prefix/api/v1/arks/batch", request.getURI().getRawPath());
        byte[] bytes = EntityUtils.toByteArray(((HttpEntityEnclosingRequestBase) request).getEntity());
        assertEquals(DarkRequestSigner.sha256(bytes), request.getFirstHeader("X-DARK-Content-SHA256").getValue());
        assertEquals(AUTHORITY, request.getFirstHeader("X-DARK-Authority-Id").getValue());
        assertEquals(authentication.status().keyId(), request.getFirstHeader("X-DARK-Key-Id").getValue());
        assertEquals(1, request.getHeaders("X-DARK-Signature").length);
        assertNull(request.getFirstHeader("X-Authority-Id"));
        ArgumentCaptor<Consumer<HttpClientBuilder>> customize = ArgumentCaptor.forClass(Consumer.class);
        verify(factory).build(customize.capture());
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_SELF);
        customize.getValue().accept(builder);
        verify(builder).disableAutomaticRetries();
        verify(builder).disableRedirectHandling();
        verify(builder).setDefaultRequestConfig(any());

        reply(200, "{\"ark\":\"ark:12345/test\",\"status\":\"D\"}");
        DarkMetadataRequest metadata = new DarkMetadataRequest();
        metadata.setAuthorityId(AUTHORITY);
        dark.updateMetadata("ark:/12345/test", metadata);
        verify(http, times(2)).execute(requests.capture());
        HttpUriRequest update = requests.getValue();
        assertEquals("PUT", update.getMethod());
        assertTrue(update.containsHeader("X-DARK-Signature"));
        assertEquals("/prefix/api/v1/arks/ark:12345/test", update.getURI().getRawPath());
    }

    @Test
    public void signedRequestsUseOnlyTheConfiguredBaseUrl() throws Exception {
        assertConfiguredBaseUrls(true);
    }

    @Test
    public void unsignedRequestsUseOnlyTheConfiguredBaseUrl() throws Exception {
        assertConfiguredBaseUrls(false);
    }

    private void assertConfiguredBaseUrls(boolean signed) throws Exception {
        when(configuration.getBooleanProperty(DarkAuthenticationService.CFG_ENABLED, false)).thenReturn(signed);
        for (String base : List.of("https://example.org", "https://example.org/",
            "https://example.org/api/v1/", "https://example.org/gateway/minter/")) {
            clearInvocations(http);
            when(configuration.getProperty(DarkClientImpl.CFG_MINTER_API_URL)).thenReturn(base);
            String normalized = org.apache.commons.lang3.StringUtils.removeEnd(base, "/");
            if (signed) {
                authentication.confirmRegistration(authentication.status().keyId(), AUTHORITY, normalized, true);
            }
            reply(200, "{\"results\":[{\"ark\":\"ark:12345/test\"}]}");
            dark.reserveARKs(AUTHORITY, "12345", List.of("item"));
            reply(200, "{\"ark\":\"ark:12345/test\",\"status\":\"D\"}");
            dark.getARK("ark:12345/test");
            DarkMetadataRequest metadata = new DarkMetadataRequest();
            metadata.setAuthorityId(AUTHORITY);
            dark.updateMetadata("ark:12345/test", metadata);

            ArgumentCaptor<HttpUriRequest> requests = ArgumentCaptor.forClass(HttpUriRequest.class);
            verify(http, times(3)).execute(requests.capture());
            List<HttpUriRequest> sent = requests.getAllValues();
            assertEquals(normalized + "/arks/batch", sent.get(0).getURI().toString());
            assertEquals(normalized + "/arks/ark:12345/test", sent.get(1).getURI().toString());
            assertEquals(normalized + "/arks/ark:12345/test", sent.get(2).getURI().toString());
            assertEquals(signed, sent.get(0).containsHeader("X-DARK-Signature"));
            assertFalse(sent.get(1).containsHeader("X-DARK-Signature"));
            assertEquals(signed, sent.get(2).containsHeader("X-DARK-Signature"));
        }
    }

    @Test
    public void everyRequestHasBoundedConfigurableTimeouts() throws Exception {
        dark.reserveARKs(AUTHORITY, "12345", List.of("item"));
        RequestConfig defaults = capturedRequestConfig();
        assertEquals(10_000, defaults.getConnectTimeout());
        assertEquals(10_000, defaults.getConnectionRequestTimeout());
        assertEquals(90_000, defaults.getSocketTimeout());
        assertFalse(defaults.isNormalizeUri());

        when(configuration.getIntProperty(DarkClientImpl.CFG_CONNECT_TIMEOUT_MS, 10_000)).thenReturn(2_000);
        when(configuration.getIntProperty(DarkClientImpl.CFG_READ_TIMEOUT_MS, 90_000)).thenReturn(5_000);
        dark.reserveARKs(AUTHORITY, "12345", List.of("item"));
        RequestConfig configured = capturedRequestConfig();
        assertEquals(2_000, configured.getConnectTimeout());
        assertEquals(5_000, configured.getSocketTimeout());

        // Non-positive values would mean "infinite"; they fall back to the safe defaults.
        when(configuration.getIntProperty(DarkClientImpl.CFG_READ_TIMEOUT_MS, 90_000)).thenReturn(0);
        dark.reserveARKs(AUTHORITY, "12345", List.of("item"));
        assertEquals(90_000, capturedRequestConfig().getSocketTimeout());
    }

    @SuppressWarnings("unchecked")
    private RequestConfig capturedRequestConfig() {
        ArgumentCaptor<Consumer<HttpClientBuilder>> customize = ArgumentCaptor.forClass(Consumer.class);
        verify(factory, atLeastOnce()).build(customize.capture());
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_SELF);
        customize.getValue().accept(builder);
        ArgumentCaptor<RequestConfig> config = ArgumentCaptor.forClass(RequestConfig.class);
        verify(builder).setDefaultRequestConfig(config.capture());
        return config.getValue();
    }

    @Test
    public void signedFailuresNeverExposeResponseOrRetry() throws Exception {
        for (int code : new int[] {401, 403, 409, 302, 503}) {
            reply(code, "UNTRUSTED-SECRET-RESPONSE");
            DarkIdentifierException error = assertThrows(DarkIdentifierException.class,
                () -> dark.reserveARKs(AUTHORITY, "12345", List.of("item")));
            assertFalse(error.toString().contains("UNTRUSTED-SECRET"));
            assertNull(error.getCause());
            if (code == 401 || code == 403) {
                assertEquals(DarkIdentifierException.AUTHENTICATION_ERROR, error.getCode());
            }
        }
        verify(http, times(5)).execute(any(HttpUriRequest.class));
    }

    @Test
    public void emptyNullAndMalformedSignedSuccessRequireReconciliation() throws Exception {
        for (String body : new String[] {"", "null", "INVALID-SECRET-RESPONSE"}) {
            reply(200, body);
            DarkIdentifierException error = assertThrows(DarkIdentifierException.class,
                () -> dark.reserveARKs(AUTHORITY, "12345", List.of("item")));
            assertTrue(error.getMessage().contains("reconcile"));
            assertFalse(error.toString().contains("INVALID-SECRET"));
            assertNull(error.getCause());
        }
        verify(http, times(3)).execute(any(HttpUriRequest.class));
    }

    @Test
    public void lostResponseRequiresReconciliationAndIsSentOnce() throws Exception {
        when(http.execute(any(HttpUriRequest.class))).thenThrow(new IOException("UNTRUSTED-SECRET"));
        DarkIdentifierException error = assertThrows(DarkIdentifierException.class,
            () -> dark.reserveARKs(AUTHORITY, "12345", List.of("item")));
        assertTrue(error.getMessage().contains("outcome is unknown"));
        assertNull(error.getCause());
        verify(http).execute(any(HttpUriRequest.class));
    }

    @Test
    public void readsStayUnsignedEvenWithoutSigningKey() throws Exception {
        when(configuration.getProperty("dspace.dir")).thenReturn(Path.of("/unavailable").toString());
        reply(200, "{\"ark\":\"ark:12345/test\",\"status\":\"P\"}");
        dark.getARK("ark:12345/test");
        ArgumentCaptor<HttpUriRequest> requests = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(http).execute(requests.capture());
        assertFalse(requests.getValue().containsHeader("X-DARK-Signature"));
    }

    @Test
    public void invalidSigningKeyStopsBeforeHttpAndLegacyRemainsAvailable() throws Exception {
        when(configuration.getProperty("dspace.dir")).thenReturn("/unavailable");
        assertThrows(DarkIdentifierException.class,
            () -> dark.reserveARKs(AUTHORITY, "12345", List.of("item")));
        verify(http, times(0)).execute(any(HttpUriRequest.class));
        when(configuration.getBooleanProperty(DarkAuthenticationService.CFG_ENABLED, false)).thenReturn(false);
        when(configuration.getBooleanProperty(DarkClientImpl.CFG_AUTHORITY_HEADER_ENABLED, true)).thenReturn(true);
        dark.reserveARKs(AUTHORITY, "12345", List.of("item"));
        ArgumentCaptor<HttpUriRequest> requests = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(http).execute(requests.capture());
        assertEquals(AUTHORITY, requests.getValue().getFirstHeader("X-Authority-Id").getValue());
        assertEquals(AUTHORITY, requests.getValue().getFirstHeader("X-DARK-Authority-Id").getValue());
        assertFalse(requests.getValue().containsHeader("X-DARK-Signature"));
    }

    private void reply(int code, String body) {
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, code, "response"));
        when(response.getEntity()).thenReturn(new StringEntity(body, ContentType.APPLICATION_JSON));
    }
}
