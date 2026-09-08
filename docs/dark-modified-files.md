# dARK Integration File Inventory

This document lists the files created or modified for the dARK integration in
DSpace. It covers the functional implementation, configuration, database, tests,
and documentation. Generated installation files, such as `compiled/`, are not
part of the versioned source code.

## Model, persistence, and database

| File | Action | Change |
| --- | --- | --- |
| `dspace-api/src/main/java/org/dspace/identifier/DARK.java` | Added | Hibernate entity representing the local association between a dARK and a DSpace object. Stores the ARK, Item, state, target, and CIDs returned by the API. |
| `dspace-api/src/main/java/org/dspace/identifier/service/DarkService.java` | Added | Service contract for creating, locating, and updating dARK identifiers. |
| `dspace-api/src/main/java/org/dspace/identifier/DarkServiceImpl.java` | Added | Service implementation; normalizes `ark:/...` and `ark:...` into the DSpace canonical form. |
| `dspace-api/src/main/java/org/dspace/identifier/dao/DarkDAO.java` | Added | DAO contract for lookups by ARK and DSpace object. |
| `dspace-api/src/main/java/org/dspace/identifier/dao/impl/DarkDAOImpl.java` | Added | Hibernate implementation of the dARK DAO. |
| `dspace-api/src/main/resources/org/dspace/storage/rdbms/sqlmigration/postgres/V11.0_2026.09.03__dark_identifier.sql` | Added | Creates the sequence, `dark` table, uniqueness constraints, and indexes for PostgreSQL. |
| `dspace-api/src/main/resources/org/dspace/storage/rdbms/sqlmigration/h2/V11.0_2026.09.03__dark_identifier.sql` | Added | H2 equivalent of the migration, used for tests and development. |
| `dspace/config/hibernate.cfg.xml` | Modified | Registers the `org.dspace.identifier.DARK` Hibernate entity. |
| `dspace/config/spring/api/core-dao-services.xml` | Modified | Registers `DarkDAOImpl` as a Spring-managed DAO. |
| `dspace/config/spring/api/core-services.xml` | Modified | Registers `DarkServiceImpl`, `DarkClientImpl`, and `DarkMetadataBuilder` as Spring services. |

## Provider and communication with the dARK platform

| File | Action | Change |
| --- | --- | --- |
| `dspace-api/src/main/java/org/dspace/identifier/DarkIdentifierProvider.java` | Added and modified | `IdentifierService` provider that reserves, registers, looks up, updates, and removes dARKs; persists Item metadata, optionally updates the public URI, and exposes metadata preflight to the CLI. Later changes include Minter response normalization. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkClient.java` | Added | HTTP client interface to reserve, look up, update metadata for, and tombstone ARKs. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkClientImpl.java` | Added | Apache HTTP client for the Minter API; applies optional authentication and authority header, and converts `ark:/...` to `ark:...` only in Minter URLs. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkArkResponse.java` | Added | DTO for an ARK response returned by the API. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkBatchResponse.java` | Added | DTO for a batch reservation response. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkBatchError.java` | Added | DTO for per-Item errors returned by batch reservation. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkIdentifierException.java` | Added | Specific exception and error codes for dARK operations. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkMetadataRequest.java` | Added | DTO for the metadata payload sent to the Minter. |
| `dspace-api/src/main/java/org/dspace/identifier/dark/DarkMetadataBuilder.java` | Added and modified | Builds Level 1 and OAI-DC Level 2 metadata. A later change replaced `getProperty()` with `getArrayProperty()` so every comma-separated field is read and reported during preflight. |
| `dspace/config/spring/api/identifier-service.xml` | Modified | Registers `DarkIdentifierProvider` with `IdentifierService`, using an always-true filter and `dark.cfg` configuration. |

## Administrative CLI

| File | Action | Change |
| --- | --- | --- |
| `dspace-api/src/main/java/org/dspace/app/dark/DarkMint.java` | Added | Implements `dark-mint --uuid` and `dark-mint --all`; verifies that the provider is enabled, avoids duplicates, performs preflight before reserving an ARK, and counts batch results. |
| `dspace-api/src/main/java/org/dspace/app/dark/DarkMintScriptConfiguration.java` | Added | Declares the script's `--uuid`, `--all`, and `--help` options. |
| `dspace/config/spring/api/scripts.xml` | Modified | Registers the `dark-mint` command with the `bin/dspace` launcher. |

## Configuration and metadata

| File | Action | Change |
| --- | --- | --- |
| `dspace/config/modules/dark.cfg` | Added and modified | Configures enablement, URLs, authority, NAAN, authentication, primary URI, dARK metadata field, and field mapping. The current author and date fallbacks are `dc.creator, dc.contributor.author, dc.contributor` and `dc.date, dc.date.issued`. |
| `dspace/config/dspace.cfg` | Modified | Includes the `modules/dark.cfg` module in the main configuration. |
| `dspace/config/registries/dublin-core-types.xml` | Modified | Registers the `dc.identifier.dark` Dublin Core field used to expose the identifier on the Item. |

## Tests

| File | Action | Change |
| --- | --- | --- |
| `dspace-api/src/test/java/org/dspace/identifier/DarkIdentifierProviderTest.java` | Added | Tests provider operations and rules, including dARK registration and local handling. |
| `dspace-api/src/test/java/org/dspace/identifier/DarkServiceImplTest.java` | Added | Tests dARK service normalization and behavior. |
| `dspace-api/src/test/java/org/dspace/identifier/dark/DarkClientImplTest.java` | Added | Tests the ARK format conversion used by the Minter. |
| `dspace-api/src/test/java/org/dspace/identifier/dark/DarkMetadataBuilderTest.java` | Added and modified | Tests payload creation, metadata combination, and preflight with author/date fallbacks. |

## Documentation

| File | Action | Change |
| --- | --- | --- |
| `docs/dark-dspace-integration.md` | Added | Guide to architecture, configuration, automatic flow, CLI, persistence, deployment, and safe operation. |
| `docs/dark-modified-files.md` | Added | This complete file inventory. |

## Non-functional artifact

| File | Observed action | Recommendation |
| --- | --- | --- |
| `dspace-api/javac.20260903_132954.args` | Added | This is a temporary Java compiler argument file and is not part of the dARK functionality. Remove it from the Git index before committing unless there is an external reason to retain it. |

## Files outside this inventory

The dARK project itself was not modified. Changes in installation or build
directories, such as `compiled/`, are local deployment artifacts and do not
replace the source files listed above.
