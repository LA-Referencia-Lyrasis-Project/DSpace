/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.embedding;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * OpenAI-compatible embeddings client for semantic indexing/search.
 */
@Service
public class EmbeddingService {

    @Autowired(required = true)
    private EmbeddingApiClient embeddingApiClient;

    public List<Float> embed(String text, String apiUrlProperty, String modelProperty, String apiKeyProperty)
            throws IOException, InterruptedException {
        List<List<Float>> embeddings = embeddingApiClient.callEmbeddingAPI(
                List.of(text),
                apiUrlProperty,
                modelProperty,
                apiKeyProperty);

        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }

        return embeddings.get(0);

    }

    public List<List<Float>> embed(List<String> texts, String apiUrlProperty, String modelProperty,
            String apiKeyProperty)
            throws IOException, InterruptedException {
        return embeddingApiClient.callEmbeddingAPI(texts, apiUrlProperty, modelProperty, apiKeyProperty);
    }

}
