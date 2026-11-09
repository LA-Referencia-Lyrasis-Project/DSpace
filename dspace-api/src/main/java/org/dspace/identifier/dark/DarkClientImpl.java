/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.dspace.app.client.DSpaceHttpClientFactory;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HTTP client for the dARK minter API.
 */
public class DarkClientImpl implements DarkClient {

    public static final String CFG_MINTER_API_URL = "identifier.dark.minter-api-url";
    public static final String CFG_API_TOKEN = "identifier.dark.api-token";
    public static final String CFG_AUTHORITY_HEADER = "identifier.dark.authority-header";
    public static final String CFG_AUTHORITY_HEADER_ENABLED = "identifier.dark.authority-header.enabled";
    public static final String CFG_CONNECT_TIMEOUT_MS = "identifier.dark.connect-timeout-ms";
    public static final String CFG_READ_TIMEOUT_MS = "identifier.dark.read-timeout-ms";

    /** Bounds connection establishment so an unreachable Minter cannot hold a harvest thread indefinitely. */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    /** Bounds the wait for a Minter response; reservations may be slow, but never unbounded (guide §20). */
    private static final int DEFAULT_READ_TIMEOUT_MS = 90_000;

    private static final String API_V1_SUFFIX = "/api/v1";
    private static final String DEFAULT_AUTHORITY_HEADER = "X-Authority-Id";
    private static final String AUTHORITY_ID_HEADER = "X-DARK-Authority-Id";
    private static final String SIGNATURE_HEADER = "X-DARK-Signature";
    private static final String ERROR_CODE_HEADER = "X-DARK-Error-Code";
    /** Machine-readable Minter error codes only; anything else is never echoed into exception messages. */
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    @Autowired(required = true)
    protected DSpaceHttpClientFactory httpClientFactory;

    @Autowired
    protected DarkAuthenticationService authenticationService;

    @Override
    public DarkArkResponse reserveARK(String authorityId, String naan, String clientItemId)
        throws DarkIdentifierException {
        DarkBatchResponse response = reserveARKs(authorityId, naan, Collections.singletonList(clientItemId));
        if (response.getResults() == null || response.getResults().isEmpty()) {
            throw new DarkIdentifierException("dARK reservation response did not contain any result.",
                                              DarkIdentifierException.BAD_ANSWER);
        }
        return response.getResults().get(0);
    }

    @Override
    public DarkBatchResponse reserveARKs(String authorityId, String naan, List<String> clientItemIds)
        throws DarkIdentifierException {
        List<Map<String, Object>> items = clientItemIds.stream()
            .map(clientItemId -> {
                Map<String, Object> item = new HashMap<>();
                item.put("client_item_id", clientItemId);
                return item;
            })
            .collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("authority_id", authorityId);
        payload.put("naan", naan);
        payload.put("items", items);

        HttpPost post = new HttpPost(minterApiUrl() + "/arks/batch");
        post.setEntity(jsonEntity(payload));
        applyHeaders(post, authorityId);

        DarkBatchResponse response = execute(post, DarkBatchResponse.class);
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            throw new DarkIdentifierException("dARK reservation failed: " + response.getErrors().get(0).getError(),
                                              DarkIdentifierException.BAD_REQUEST);
        }
        return response;
    }

    @Override
    public DarkArkResponse getARK(String ark) throws DarkIdentifierException {
        HttpGet get = new HttpGet(minterApiUrl() + "/arks/" + minterArk(ark));
        applyHeaders(get, null);
        return execute(get, DarkArkResponse.class);
    }

    @Override
    public DarkArkResponse updateMetadata(String ark, DarkMetadataRequest metadata)
        throws DarkIdentifierException {
        HttpPut put = new HttpPut(minterApiUrl() + "/arks/" + minterArk(ark));
        put.setEntity(jsonEntity(metadata));
        applyHeaders(put, metadata.getAuthorityId());
        return execute(put, DarkArkResponse.class);
    }

    static String minterArk(String ark) {
        return ark.startsWith("ark:/") ? "ark:" + ark.substring("ark:/".length()) : ark;
    }

    private String minterApiUrl() throws DarkIdentifierException {
        String url = configurationService.getProperty(CFG_MINTER_API_URL);
        if (StringUtils.isBlank(url)) {
            throw new DarkIdentifierException("Missing required configuration: " + CFG_MINTER_API_URL);
        }

        url = StringUtils.removeEnd(url.trim(), "/");
        if (!url.endsWith(API_V1_SUFFIX)) {
            url = url + API_V1_SUFFIX;
        }
        return url;
    }

    private void applyHeaders(HttpUriRequest request, String authorityId) throws DarkIdentifierException {
        request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        if (request instanceof HttpPost || request instanceof HttpPut) {
            request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        }

        String token = configurationService.getProperty(CFG_API_TOKEN);
        if (StringUtils.isNotBlank(token)) {
            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }

        if (authenticationService.isEnabled()) {
            if (request instanceof HttpEntityEnclosingRequest modification) {
                try {
                    byte[] body = EntityUtils.toByteArray(modification.getEntity());
                    authenticationService.headers(request.getMethod(), request.getURI(), authorityId, body)
                        .forEach(request::setHeader);
                } catch (IOException e) {
                    throw new DarkIdentifierException("Unable to prepare dARK signature.",
                        DarkIdentifierException.AUTHENTICATION_ERROR);
                }
            }
            return;
        }

        // The current Minter requires this identity even with signature verification disabled.
        // Retain the configurable legacy header as well for existing installations.
        if (StringUtils.isNotBlank(authorityId)) {
            request.setHeader(AUTHORITY_ID_HEADER, authorityId);
        }
        boolean authorityHeaderEnabled = configurationService.getBooleanProperty(CFG_AUTHORITY_HEADER_ENABLED, true);
        if (authorityHeaderEnabled && StringUtils.isNotBlank(authorityId)) {
            String authorityHeader = configurationService.getProperty(CFG_AUTHORITY_HEADER, DEFAULT_AUTHORITY_HEADER);
            request.setHeader(authorityHeader, authorityId);
        }
    }

    private ByteArrayEntity jsonEntity(Object payload) throws DarkIdentifierException {
        try {
            return new ByteArrayEntity(objectMapper.writeValueAsBytes(payload), ContentType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            throw new DarkIdentifierException("Unable to serialize dARK request.", e,
                                              DarkIdentifierException.CONVERSION_ERROR);
        }
    }

    private CloseableHttpClient buildClient(boolean signed) {
        RequestConfig.Builder requestConfig = RequestConfig.custom()
            .setConnectTimeout(timeoutMillis(CFG_CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS))
            .setConnectionRequestTimeout(timeoutMillis(CFG_CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS))
            .setSocketTimeout(timeoutMillis(CFG_READ_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS));
        if (signed) {
            requestConfig.setNormalizeUri(false);
        }
        return httpClientFactory.build(builder -> {
            // A signed request must reach the Minter byte-for-byte: no redirects, retries or compression.
            if (signed) {
                builder.disableAutomaticRetries().disableRedirectHandling().disableContentCompression();
            }
            builder.setDefaultRequestConfig(requestConfig.build());
        });
    }

    private <T> T execute(HttpUriRequest request, Class<T> responseType) throws DarkIdentifierException {
        boolean signed = request.containsHeader(SIGNATURE_HEADER);
        try (CloseableHttpClient client = buildClient(signed);
             CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String content = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity(), "UTF-8");
            if (statusCode < HttpStatus.SC_OK || statusCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
                // Authentication responses may come from an untrusted gateway: expose no raw body.
                if (signed || statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {
                    String diagnostic = statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR
                        || statusCode == HttpStatus.SC_CONFLICT
                        ? "Modification outcome may be unknown; reconcile before resending."
                        : "Check authentication, permissions and endpoint configuration.";
                    Header errorHeader = response.getFirstHeader(ERROR_CODE_HEADER);
                    if (errorHeader != null && ERROR_CODE_PATTERN.matcher(errorHeader.getValue()).matches()) {
                        diagnostic = errorHeader.getValue() + ". " + diagnostic;
                    }
                    throw mapError(statusCode, diagnostic);
                }
                throw mapError(statusCode, content);
            }
            if (StringUtils.isBlank(content)) {
                throw new DarkIdentifierException(signed
                    ? "dARK modification outcome is unknown: empty response; reconcile before resending."
                    : "dARK API returned an empty response.",
                                                  DarkIdentifierException.BAD_ANSWER);
            }
            T result = objectMapper.readValue(content, responseType);
            if (signed && result == null) {
                throw new DarkIdentifierException(
                    "dARK modification outcome is unknown: invalid response; reconcile before resending.",
                    DarkIdentifierException.BAD_ANSWER);
            }
            return result;
        } catch (IOException e) {
            throw new DarkIdentifierException(signed
                ? "dARK modification outcome is unknown; reconcile before resending."
                : "Unable to communicate with dARK API.",
                                              DarkIdentifierException.INTERNAL_ERROR);
        }
    }

    private int timeoutMillis(String property, int defaultValue) {
        int configured = configurationService.getIntProperty(property, defaultValue);
        return configured > 0 ? configured : defaultValue;
    }

    private DarkIdentifierException mapError(int statusCode, String content) {
        int code = DarkIdentifierException.INTERNAL_ERROR;
        if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {
            code = DarkIdentifierException.AUTHENTICATION_ERROR;
        } else if (statusCode == HttpStatus.SC_BAD_REQUEST || statusCode == 422) {
            code = DarkIdentifierException.BAD_REQUEST;
        } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
            code = DarkIdentifierException.DARK_DOES_NOT_EXIST;
        } else if (statusCode == HttpStatus.SC_CONFLICT) {
            code = DarkIdentifierException.DARK_ALREADY_EXISTS;
        }

        return new DarkIdentifierException("dARK API returned HTTP " + statusCode + ": " + content, code);
    }
}
