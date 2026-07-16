/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.discovery.embedding.models.EmbeddingData;
import org.dspace.discovery.embedding.models.EmbeddingRequest;
import org.dspace.discovery.embedding.models.EmbeddingResponse;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Low-level API client for embedding generation with retry/backoff behavior.
 */
@Service
public class EmbeddingApiClient implements InitializingBean {

    private static final Logger log = LogManager.getLogger(EmbeddingApiClient.class);

    private static final String MODEL = "embeddings.model";
    private static final String ENCODING_FORMAT = "embeddings.encoding_format";
    private static final String TIMEOUT_MS = "embeddings.api.timeout.ms";
    private static final String RETRY_MAX_ATTEMPTS = "embeddings.api.retry.max.attempts";
    private static final String RETRY_DELAY_MS = "embeddings.api.retry.delay.ms";
    private static final String VECTOR_DIMENSION = "embeddings.vector.dimension";

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_RETRY_DELAY_MS = 500;
    private static final int DEFAULT_VECTOR_DIMENSION = 1024;
    private static final String DEFAULT_ENCODING_FORMAT = "float";

    @Autowired(required = true)
    private ConfigurationService configurationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    @Override
    public void afterPropertiesSet() {
        int timeoutMs = configurationService.getIntProperty(
                TIMEOUT_MS,
                DEFAULT_TIMEOUT_MS);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public List<List<Float>> callEmbeddingAPI(List<String> texts, String apiUrl, String model, String apiKey)
            throws IOException, InterruptedException {

        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        if (StringUtils.isBlank(model)) {
            model = configurationService.getProperty(MODEL);
        }

        int expectedDimension = configurationService.getIntProperty(
                VECTOR_DIMENSION,
                DEFAULT_VECTOR_DIMENSION);
        String encodingFormat = configurationService.getProperty(
                ENCODING_FORMAT,
                DEFAULT_ENCODING_FORMAT);

        if (StringUtils.isBlank(apiUrl) || StringUtils.isBlank(model)) {
            return Collections.emptyList();
        }

        int timeoutMs = configurationService.getIntProperty(
                TIMEOUT_MS,
                DEFAULT_TIMEOUT_MS);
        int retryMaxAttempts = Math.max(1, configurationService.getIntProperty(
                RETRY_MAX_ATTEMPTS,
                DEFAULT_RETRY_MAX_ATTEMPTS));
        int retryDelayMs = Math.max(0, configurationService.getIntProperty(
                RETRY_DELAY_MS,
                DEFAULT_RETRY_DELAY_MS));

        EmbeddingRequest requestPayload = new EmbeddingRequest(
                texts,
                model,
                expectedDimension,
                encodingFormat);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(requestPayload)));

        if (StringUtils.isNotBlank(apiKey)) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = null;
        IOException lastIOException = null;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException ioException) {
                lastIOException = ioException;
                if (attempt >= retryMaxAttempts) {
                    throw ioException;
                }
                log.warn("Embedding API IO failure on attempt {}/{}. Retrying in {} ms.",
                        attempt, retryMaxAttempts, getBackoffDelayMs(retryDelayMs, attempt), ioException);
                sleepBeforeRetry(retryDelayMs, attempt);
                continue;
            }

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                break;
            }

            if (isRetryableStatus(statusCode) && attempt < retryMaxAttempts) {
                log.warn("Embedding API returned status {} on attempt {}/{}. Retrying in {} ms.",
                        statusCode, attempt, retryMaxAttempts, getBackoffDelayMs(retryDelayMs, attempt));
                sleepBeforeRetry(retryDelayMs, attempt);
                continue;
            }

            throw new IOException("""
                    Embedding API request failed.
                    Status: %d
                    Response: %s
                    """.formatted(
                    statusCode,
                    response.body()));
        }

        if (response == null) {
            throw new IOException("Embedding API request failed after retries.", lastIOException);
        }

        EmbeddingResponse embeddingResponse = objectMapper.readValue(
                response.body(),
                EmbeddingResponse.class);

        List<EmbeddingData> data = embeddingResponse.data();

        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Float>> vectors = new ArrayList<>(data.size());

        for (EmbeddingData item : data) {

            List<Float> vector = item.embedding();

            if (vector == null || vector.isEmpty()) {
                throw new IOException("Embedding vector is empty.");
            }

            if (expectedDimension > 0 &&
                    vector.size() != expectedDimension) {

                throw new IOException("""
                        Embedding dimension mismatch.
                        Expected: %d
                        Received: %d
                        """.formatted(
                        expectedDimension,
                        vector.size()));
            }

            vectors.add(List.copyOf(vector));
        }

        return List.copyOf(vectors);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode >= 404;
    }

    private void sleepBeforeRetry(int baseDelayMs, int attempt) throws InterruptedException {
        long delayMs = getBackoffDelayMs(baseDelayMs, attempt);
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    private long getBackoffDelayMs(int baseDelayMs, int attempt) {
        long multiplier = 1L << Math.max(0, attempt - 1);
        long delayMs = (long) baseDelayMs * multiplier;
        return Math.min(delayMs, 30_000L);
    }
}