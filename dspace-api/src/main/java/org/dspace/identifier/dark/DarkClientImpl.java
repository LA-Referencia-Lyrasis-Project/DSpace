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
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
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

    private static final String API_V1_SUFFIX = "/api/v1";
    private static final String DEFAULT_AUTHORITY_HEADER = "X-Authority-Id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    @Autowired(required = true)
    protected DSpaceHttpClientFactory httpClientFactory;

    @Override
    public DarkArkResponse reserveARK(String authorityId, String naan, String clientItemId)
        throws DarkIdentifierException {
        Map<String, Object> item = new HashMap<>();
        item.put("client_item_id", clientItemId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("authority_id", authorityId);
        payload.put("naan", naan);
        payload.put("items", Collections.singletonList(item));

        HttpPost post = new HttpPost(minterApiUrl() + "/arks/batch");
        post.setEntity(jsonEntity(payload));
        applyHeaders(post, authorityId);

        DarkBatchResponse response = execute(post, DarkBatchResponse.class);
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            throw new DarkIdentifierException("dARK reservation failed: " + response.getErrors().get(0).getError(),
                                              DarkIdentifierException.BAD_REQUEST);
        }
        if (response.getResults() == null || response.getResults().isEmpty()) {
            throw new DarkIdentifierException("dARK reservation response did not contain any result.",
                                              DarkIdentifierException.BAD_ANSWER);
        }
        return response.getResults().get(0);
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

    @Override
    public void tombstoneARK(String ark, String authorityId) throws DarkIdentifierException {
        HttpDelete delete = new HttpDelete(minterApiUrl() + "/arks/" + minterArk(ark));
        applyHeaders(delete, authorityId);
        execute(delete, Void.class);
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

    private void applyHeaders(HttpUriRequest request, String authorityId) {
        request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        if (request instanceof HttpPost || request instanceof HttpPut) {
            request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        }

        String token = configurationService.getProperty(CFG_API_TOKEN);
        if (StringUtils.isNotBlank(token)) {
            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }

        boolean authorityHeaderEnabled = configurationService.getBooleanProperty(CFG_AUTHORITY_HEADER_ENABLED, true);
        if (authorityHeaderEnabled && StringUtils.isNotBlank(authorityId)) {
            String authorityHeader = configurationService.getProperty(CFG_AUTHORITY_HEADER, DEFAULT_AUTHORITY_HEADER);
            request.setHeader(authorityHeader, authorityId);
        }
    }

    private StringEntity jsonEntity(Object payload) throws DarkIdentifierException {
        try {
            return new StringEntity(objectMapper.writeValueAsString(payload), ContentType.APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            throw new DarkIdentifierException("Unable to serialize dARK request.", e,
                                              DarkIdentifierException.CONVERSION_ERROR);
        }
    }

    private <T> T execute(HttpUriRequest request, Class<T> responseType) throws DarkIdentifierException {
        try (CloseableHttpClient client = httpClientFactory.build();
             CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String content = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity(), "UTF-8");
            if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
                throw mapError(statusCode, content);
            }
            if (Void.class.equals(responseType)) {
                return null;
            }
            if (StringUtils.isBlank(content)) {
                throw new DarkIdentifierException("dARK API returned an empty response.",
                                                  DarkIdentifierException.BAD_ANSWER);
            }
            return objectMapper.readValue(content, responseType);
        } catch (IOException e) {
            throw new DarkIdentifierException("Unable to communicate with dARK API.", e,
                                              DarkIdentifierException.INTERNAL_ERROR);
        }
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
