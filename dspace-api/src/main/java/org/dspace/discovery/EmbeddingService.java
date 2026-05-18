/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * OpenAI-compatible embeddings client for semantic indexing/search.
 */
public class EmbeddingService implements InitializingBean {

    private static final Logger log = LogManager.getLogger(EmbeddingService.class);

    private static final String API_URL = "embeddings.api.url";
    private static final String API_URL_INDEXING = "embeddings.api.url.indexing";
    private static final String API_URL_SEARCH = "embeddings.api.url.search";
    private static final String API_KEY = "embeddings.api.key";
    private static final String MODEL = "embeddings.model";
    private static final String MODEL_INDEXING = "embeddings.model.indexing";
    private static final String MODEL_SEARCH = "embeddings.model.search";
    private static final String TIMEOUT_MS = "embeddings.api.timeout.ms";
    private static final String VECTOR_DIMENSION = "embeddings.vector.dimension";

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_VECTOR_DIMENSION = 1024;

    // Precompiling patterns saves CPU on bulk processing
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\n\\r]+");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    @Autowired(required = true)
    private ConfigurationService configurationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    @Override
    public void afterPropertiesSet() {
        int timeoutMs = configurationService.getIntProperty(TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    /**
     * Converts text into a dense vector using an OpenAI-compatible embeddings API.
     *
     * @param text input text
     * @return embedding vector or empty list when unavailable
     * @throws IOException          when IO/parsing fails
     * @throws InterruptedException when HTTP call is interrupted
     */
    public List<Float> getVectorFromAPI(String text) throws IOException, InterruptedException {
        return getVectorFromAPI(text, API_URL, MODEL);
    }

    /**
     * Converts text into a dense vector using the API configured for indexing.
     */
    public List<Float> getVectorFromAPIForIndexing(String text) throws IOException, InterruptedException {
        return getVectorFromAPI(text, API_URL_INDEXING, MODEL_INDEXING);
    }

    /**
     * Converts text into a dense vector using the API configured for search.
     */
    public List<Float> getVectorFromAPIForSearch(String text) throws IOException, InterruptedException {
        return getVectorFromAPI(text, API_URL_SEARCH, MODEL_SEARCH);
    }

    private List<Float> getVectorFromAPI(String text, String apiUrlProperty, String modelProperty)
            throws IOException, InterruptedException {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        text = normalize(text);

        String apiUrl = resolveApiUrl(apiUrlProperty);
        String model = resolveModel(modelProperty);
        if (StringUtils.isBlank(apiUrl) || StringUtils.isBlank(model)) {
            return List.of();
        }

        int timeoutMs = configurationService.getIntProperty(TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("input", text);
        payload.put("model", model);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

        String apiKey = configurationService.getProperty(API_KEY);
        if (StringUtils.isNotBlank(apiKey)) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Embedding API request failed with status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray() || dataArray.isEmpty()) {
            return List.of();
        }

        JsonNode embeddingNode = dataArray.get(0).path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            return List.of();
        }

        List<Float> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode value : embeddingNode) {
            vector.add(value.floatValue());
        }

        int expectedDimension = configurationService.getIntProperty(VECTOR_DIMENSION, DEFAULT_VECTOR_DIMENSION);
        if (expectedDimension > 0 && vector.size() != expectedDimension) {
            log.warn("Embedding size mismatch. Expected={}, got={}", expectedDimension, vector.size());
            return List.of();
        }

        return vector;
    }

    private String resolveApiUrl(String apiUrlProperty) {
        String apiUrl = configurationService.getProperty(apiUrlProperty);
        if (StringUtils.isNotBlank(apiUrl)) {
            return apiUrl;
        }
        return configurationService.getProperty(API_URL);
    }

    private String resolveModel(String modelProperty) {
        String model = configurationService.getProperty(modelProperty);
        if (StringUtils.isNotBlank(model)) {
            return model;
        }
        return configurationService.getProperty(MODEL);
    }

    private String normalize(String input) {
        if (input == null) {
            return input;
        }
        if (input.isBlank()) {
            return "";
        }

        String normalized = input.trim();

        normalized = normalized.toLowerCase(Locale.ROOT);

        // Replace line breaks with spaces
        normalized = LINE_BREAKS.matcher(normalized).replaceAll(" ");

        // Collapse multiple spaces
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ");

        // Unicode Normalization (NFC) - Guarantees "ç" vs "c + ̧"
        return Normalizer.normalize(normalized, Normalizer.Form.NFC);
    }
}
