/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery.embedding;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * Service responsible for normalizing and splitting textual metadata into
 * embedding-ready chunks.
 *
 * <p>
 * The resulting chunks follow a title + fragment template so each segment
 * preserves the original document context during vectorization.
 * </p>
 */
@Service
public class ChunkingService implements InitializingBean {
    private static final String MAX_SEGMENT_SIZE_TOKENS = "embeddings.max.segment.size.tokens";
    private static final String MAX_OVERLAP_SIZE_TOKENS = "embeddings.max.overlap.size.tokens";
    private static final String MAX_CHUNKS_SIZE = "embeddings.max.chunks.size";

    private static final int DEFAULT_MAX_SEGMENT_SIZE_TOKENS = 128;
    private static final int DEFAULT_MAX_OVERLAP_SIZE_TOKENS = 0;
    private static final int DEFAULT_MAX_CHUNKS_SIZE = 5;

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\n\\r]+");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern HYPHENATED_LINE_BREAK = Pattern.compile("-\\s*\\n\\s*");

    @Autowired(required = true)
    private ConfigurationService configurationService;

    private DocumentSplitter documentSplitter;
    private int maxChunksSize;

    @Override
    public void afterPropertiesSet() {
        int maxChunkSize = configurationService.getIntProperty(
                MAX_SEGMENT_SIZE_TOKENS,
                DEFAULT_MAX_SEGMENT_SIZE_TOKENS);

        int maxOverlapSize = configurationService.getIntProperty(
                MAX_OVERLAP_SIZE_TOKENS,
                DEFAULT_MAX_OVERLAP_SIZE_TOKENS);

        this.maxChunksSize = configurationService.getIntProperty(
                MAX_CHUNKS_SIZE,
                DEFAULT_MAX_CHUNKS_SIZE);

        CustomTokenCountEstimator customTokenCountEstimator = new CustomTokenCountEstimator();
        this.documentSplitter = DocumentSplitters.recursive(
                maxChunkSize,
                maxOverlapSize,
                customTokenCountEstimator);
    }

    /**
     * Applies lightweight text normalization suitable for chunking and
     * embedding.
     *
     * @param input input text, potentially null or blank
     * @return normalized text, or an empty string when the input is null/blank
     */
    public String normalizeText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = input.trim();

        // Join words split by hyphen + line break
        // Example: "multi-\nlingual" -> "multilingual"
        normalized = HYPHENATED_LINE_BREAK.matcher(normalized).replaceAll("");

        // Lowercase normalization
        normalized = normalized.toLowerCase(Locale.ROOT);

        // Replace remaining line breaks with spaces
        normalized = LINE_BREAKS.matcher(normalized).replaceAll(" ");

        // Collapse multiple spaces/tabs/newlines
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ");

        // Unicode normalization (ensures canonical representation)
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC);

        return normalized;
    }

    /**
     * Splits a document abstract into chunks and prefixes each fragment with the
     * normalized title.
     *
     * <p>
     * The splitter first tries to preserve larger structures (paragraphs and
     * lines) and recursively falls back to sentences, words, and characters when
     * needed to respect token limits.
     * </p>
     *
     * @param title        document title to prepend to each chunk
     * @param abstractText document abstract/body to split
     * @return a list of non-blank chunks formatted as title + newline + fragment,
     * limited by {@code maxChunksSize}; returns an empty list for null or
     * blank title/abstract inputs
     */
    public List<String> chunkTitleAndAbstract(String title, String abstractText) {
        String normalizedTitle = normalizeText(title);
        String normalizedAbstract = normalizeText(abstractText);

        if (normalizedTitle.isBlank() || normalizedAbstract.isBlank()) {
            return List.of();
        }

        List<TextSegment> segments = documentSplitter.split(Document.from(normalizedAbstract));

        return segments.stream()
                .map(TextSegment::text)
                .filter(fragment -> !fragment.isBlank())
                .limit(maxChunksSize)
                .map(fragment -> formatChunk(normalizedTitle, fragment))
                .toList();
    }

    /**
     * Formats a chunk payload with title context.
     *
     * @param title    normalized title
     * @param fragment split abstract fragment
     * @return chunk text in two lines: title and fragment
     */
    private static String formatChunk(String title, String fragment) {
        return String.join("\n", title, fragment);
    }

}
