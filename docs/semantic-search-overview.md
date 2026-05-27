# Semantic Search Feature Overview (DSpace Backend)

## Scope

This document summarizes semantic-search changes in branch `semantic-search` compared to `main`.

## Feature Summary

Semantic search adds embedding-based indexing and querying on top of Discovery.

1. A new REST query parameter `searchType` is supported (`lexical` or `semantic`).
2. Semantic mode generates embeddings and executes vector queries in Solr.
3. Discovery results expose `score` in REST so UI can sort/render relevance.
4. Two runtime modes are supported:
   - Single vector per item: `embeddings.solrMultiVectors=false`
   - MultiVectors (nested child vectors): `embeddings.solrMultiVectors=true`

## Backend Runtime Flow

1. `searchType=semantic` is passed from REST to `DiscoverQuery` properties.
2. `SolrSemanticSearchPlugin` detects semantic mode and replaces text query with vector query.
3. `SolrVectorIndexPlugin` generates and stores vectors during indexing.
4. `EmbeddingApiClient` calls OpenAI-compatible embedding APIs with retry/backoff.
5. `DiscoverResultConverter` maps Solr `score` into REST `SearchResultEntryRest.score`.

### Mode-specific behavior

1. Single-vector mode (`embeddings.solrMultiVectors=false`)
   - Indexes only title text.
   - Stores one vector in `embeddings.solrVectorField`.
   - Searches in `embeddings.solrVectorField`.

2. MultiVectors mode (`embeddings.solrMultiVectors=true`)
   - Indexes title plus abstract chunks.
   - Stores vectors in `embeddings.solrVectorFieldMultiValued`.
   - Searches child vectors and returns parents through Solr parent/child query.

## Configuration Keys

Defined in `dspace/config/modules/embeddings.cfg`:

1. `semantic.search.enabled`
2. `embeddings.api.url.indexing`
3. `embeddings.model.indexing`
4. `embeddings.indexing.titleField`
5. `embeddings.indexing.descriptionField`
6. `embeddings.api.url.search`
7. `embeddings.model.search`
8. `embeddings.api.url` (legacy fallback)
9. `embeddings.api.key`
10. `embeddings.model` (legacy fallback)
11. `embeddings.encoding_format`
12. `embeddings.max.segment.size.tokens`
13. `embeddings.max.overlap.size.tokens`
14. `embeddings.max.chunks.size`
15. `embeddings.vector.dimension`
16. `embeddings.api.timeout.ms`
17. `embeddings.api.retry.maxAttempts`
18. `embeddings.api.retry.delay.ms`
19. `embeddings.search.topK` (used by `knn`)
20. `embeddings.solrVectorField`
21. `embeddings.solrVectorFieldMultiValued`
22. `embeddings.solrMultiVectors`
23. `embeddings.search.queryParser` (`knn` or `vectorSimilarity`)
24. `embeddings.search.minReturn` (used by `vectorSimilarity`)

Additional config wiring:

1. `rest.properties.exposed = semantic.search.enabled` in `dspace/config/modules/rest.cfg`
2. `semantic.search.enabled = ${semantic.search.enabled}` in `dspace/config/dspace.cfg`

## Solr Requirements

See [Solr Schema Changes for Multi-Valued Dense Vectors](Solr%20Schema%20Changes%20for%20Multi-Valued%20Dense%20Vectors.md).

Always required:

1. `DenseVectorField` type (`knn_vector`)
2. Single-vector field `vector`

Required only when `embeddings.solrMultiVectors=true`:

1. Nested support (`_nest_path_` and `_root_`)
2. Multi-valued vector field (`vector_multivalued`)
3. Identity fields (`search.resourceid`, `search.resourcetype`, `search.uniqueid`) must not be required

## Files Created in Branch

1. `docs/solr-issues.md`
2. `dspace-api/src/main/java/org/dspace/discovery/SolrSemanticSearchPlugin.java`
3. `dspace-api/src/main/java/org/dspace/discovery/SolrVectorIndexPlugin.java`
4. `dspace-api/src/main/java/org/dspace/discovery/embedding/ChunkingService.java`
5. `dspace-api/src/main/java/org/dspace/discovery/embedding/CustomTokenCountEstimator.java`
6. `dspace-api/src/main/java/org/dspace/discovery/embedding/EmbeddingApiClient.java`
7. `dspace-api/src/main/java/org/dspace/discovery/embedding/EmbeddingService.java`
8. `dspace-api/src/main/java/org/dspace/discovery/embedding/models/EmbeddingData.java`
9. `dspace-api/src/main/java/org/dspace/discovery/embedding/models/EmbeddingRequest.java`
10. `dspace-api/src/main/java/org/dspace/discovery/embedding/models/EmbeddingResponse.java`
11. `dspace-api/src/main/java/org/dspace/discovery/embedding/models/Usage.java`
12. `dspace/config/modules/embeddings.cfg`

## Files Modified in Branch

1. `dspace-api/pom.xml`
2. `dspace-server-webapp/src/main/java/org/dspace/app/rest/DiscoveryRestController.java`
3. `dspace-server-webapp/src/main/java/org/dspace/app/rest/converter/DiscoverResultConverter.java`
4. `dspace-server-webapp/src/main/java/org/dspace/app/rest/link/search/DiscoveryRestHalLinkFactory.java`
5. `dspace-server-webapp/src/main/java/org/dspace/app/rest/link/search/SearchConfigurationResourceHalLinkFactory.java`
6. `dspace-server-webapp/src/main/java/org/dspace/app/rest/link/search/SearchFacetEntryHalLinkFactory.java`
7. `dspace-server-webapp/src/main/java/org/dspace/app/rest/model/SearchResultEntryRest.java`
8. `dspace-server-webapp/src/main/java/org/dspace/app/rest/repository/DiscoveryRestRepository.java`
9. `dspace-server-webapp/src/main/java/org/dspace/app/rest/utils/RestDiscoverQueryBuilder.java`
10. `dspace-server-webapp/src/test/data/dspaceFolder/config/spring/api/test-discovery.xml`
11. `dspace/config/dspace.cfg`
12. `dspace/config/modules/rest.cfg`
13. `dspace/config/registries/local-types.xml`
14. `dspace/config/spring/api/discovery.xml`
15. `dspace/solr/search/conf/schema.xml`
16. `dspace/solr/search/conf/solrconfig.xml`

## Branch Review Notes

Non-feature artifacts present in the branch diff:

1. `dspace-api/javac.20260526_173744.args`
2. `hs_err_pid152131.log`

Recommendation: remove these files from the branch before opening/finalizing PR.
