# dARK configuration guide for DSpace

This guide describes how to configure the dARK identifier provider, connect it to a dARK Minter and operate its administration screens. It applies to the code in this checkout; verify the deployed backend and Angular versions before applying it to another release. The dARK provider adds an identifier to an Item and retains its existing Handle.

## Before you start

Obtain the following from the Minter administrator:

- The Minter API base URL and its expected path, TLS certificate and network access requirements.
- The authority identifier and an authorized NAAN. Signed authentication requires the authority identifier to be a canonical UUID.
- Whether signed Minter authentication is enabled and whether an additional gateway bearer token is required. A gateway token is separate from the Ed25519 signature.
- The Minter's metadata requirements and a test Item/authority suitable for a controlled write.

Install compatible DSpace backend and Angular artifacts, apply the dARK database migration through the normal DSpace upgrade procedure, and verify that the configured Item metadata field exists in the metadata registry. The included Dublin Core registry defines `dc.identifier.dark`; an existing installation may need that field registered separately. Authentication itself adds no database migration or reindex requirement.

The source configuration is [`dspace/config/modules/dark.cfg`](../dspace/config/modules/dark.cfg); the installed module file is `[dspace.dir]/config/modules/dark.cfg`. Set deployment-specific values in the installed file or, preferably, `[dspace.dir]/config/local.cfg`, which overrides module configuration. System properties and environment variables can override both. Restart backend processes after changes and apply consistent configuration to CLI and server instances.

**Important:** this checkout's `dark.cfg` sets `identifier.dark.enabled = true`, although the Java provider uses `false` when the property is absent. Set it explicitly to `false` until the Minter, migration and authentication setup are ready. The included authority, NAAN and URL are examples, not production credentials.

## Basic configuration

For example, in the installed `local.cfg`:

```properties
# Enable only after the remaining setup and registration are complete.
identifier.dark.enabled = false
identifier.dark.minter-api-url = https://minter.example.org/api/v1
identifier.dark.authority-id = 11111111-1111-4111-8111-111111111111
identifier.dark.naan = 12345
identifier.dark.batch-size = 100
identifier.dark.metadata = dc.identifier.dark
identifier.dark.target-url-pattern = ${dspace.ui.url}/items/{uuid}
```

Replace the example URL, UUID and NAAN with the values assigned to your repository. When ready, set `identifier.dark.enabled = true` and restart. The backend's `DarkIdentifierProvider` is already registered in `config/spring/api/identifier-service.xml` with an `always_true_filter`; verify any local customization of the provider or filter when troubleshooting automatic assignment.

| Property | Purpose |
| --- | --- |
| `identifier.dark.enabled` | Enables minting/registration; set explicitly in each installation. |
| `identifier.dark.minter-api-url` | Base URL used to call the Minter. This checkout removes one trailing slash and appends `/api/v1` when the value does not already end in `/api/v1`. Set the full expected base path, and verify the destination actually exposed by the deployed Minter. A deployment that serves its API without `/api/v1` requires a compatible client or proxy; this checkout cannot use a suffix-free base as-is. |
| `identifier.dark.authority-id` | Minter authority. Signed mode requires a canonical UUID; the demo string in `dark.cfg` is not valid for signed authentication. |
| `identifier.dark.naan` | NAAN provisioned for the authority by the Minter operator. |
| `identifier.dark.batch-size` | Batch size for bulk reservation and local refresh processing; the shipped value is `100`. |
| `identifier.dark.metadata` | Item metadata field containing the compact identifier, such as `ark:12345/example`. It does not replace Handle metadata. |
| `identifier.dark.target-url-pattern` | Fallback Item URL when no Handle is found. `{uuid}` and `{handle}` are replaced; when a Handle exists its canonical URL takes precedence. Set `dspace.ui.url` to the public Angular URL. |
| `identifier.dark.connect-timeout-ms`, `identifier.dark.read-timeout-ms` | Connection and socket timeouts in milliseconds; defaults are 10000 and 90000 respectively. Non-positive values use the defaults. |
| `identifier.dark.api-token` | Optional bearer token for a separate gateway. Configure only when required and protect the configuration source; do not commit the token or place it in examples, logs or support bundles. It is not a substitute for signed authentication. |
| `identifier.dark.authority-header.enabled`, `identifier.dark.authority-header` | Optional legacy authority header in unsigned mode. The client also sends `X-DARK-Authority-Id` in unsigned mode. Signed modifications use the signing headers instead. |

For signed requests, use HTTPS with a trusted certificate. HTTP is allowed only for loopback development hosts. Configure the actual final API endpoint: the signed client does not follow redirects. A proxy must preserve the request path and query used by the signature. Keep clocks synchronized.

## Metadata mapping

The following settings use the source fields shipped in `dark.cfg`; change them to match your repository's metadata practices:

```properties
identifier.dark.metadata.title = dc.title
identifier.dark.metadata.creator = dc.creator, dc.contributor.author, dc.contributor
identifier.dark.metadata.date = dc.date, dc.date.issued
identifier.dark.metadata.publisher = dc.publisher
identifier.dark.metadata.type = dc.type
identifier.dark.metadata.language = dc.language.iso
identifier.dark.metadata.abstract = dc.description.abstract
identifier.dark.metadata.subject = dc.subject
identifier.dark.metadata.schema = oai_dc
identifier.dark.metadata.media-type = application/xml
```

For comma-separated fields, the builder reads values from each configured field in order. The Minter validates its required Level 1 metadata, which can include title, author and year. A rejected update is recorded locally for correction and retry; consult the Minter operator for its actual validation rules.

## Signed authentication and key registration

Signed authentication is optional in DSpace but must match the Minter's policy. Do not enable unsigned writes against a Minter that requires signatures. For the complete security model and REST contract, see [dARK Minter authentication](dark-minter-authentication.md).

1. Set the authority UUID and Minter URL obtained from the operator. Choose a private key directory that is an immediate child of `[dspace.dir]/keys`:

   ```properties
   identifier.dark.auth.enabled = true
   identifier.dark.auth.key-directory = ${dspace.dir}/keys/dark
   ```

2. Restart the backend. Generate a key pair as the DSpace service account, either from the administrator page at `/admin/dark-authentication` or from the installation directory:

   ```bash
   bin/dspace dark-auth --generate
   ```

   The page's generation action requires authentication to be enabled. The CLI can generate before enabling it. Both use the configured directory, refuse to overwrite existing keys and expose only the public key. The authentication route may be opened directly by a site administrator even when it is absent from the main administration menu.
3. Share only the displayed public key and fingerprint with the Minter administrator. Request registration for the exact authority and NAAN. Do not share or display the private key.
4. After the operator confirms registration, refresh the authentication page, verify the displayed key fingerprint, authority UUID and Minter URL, select the confirmation checkbox and confirm. This records a local attestation; it does not register the key remotely or verify remote write permission.
5. Test an authorized Item with `bin/dspace dark --mint-uuid <item-uuid>` and verify its state through the registry and Minter. Only then enable broader use.

The key is stored unencrypted for unattended signing. The implementation requires a POSIX filesystem, checks ownership/permissions and rejects symlinks and unsuitable paths. The key directory must be server-only and outside web content. Protect backups as secrets and never put private key bytes in configuration, logs or source control. Do not regenerate over an existing directory. See the [authentication guide](dark-minter-authentication.md#rotation-and-rollback) for key rotation and coordinated rollback.

Signed modifications remain paused until confirmation matches the current public-key fingerprint, authority and normalized API URL. Changing any of these invalidates the confirmation. The administration page reports remote operation as unverified; a successful read does not establish that a signed write will be accepted.

## Automatic assignment and administration

With the provider enabled, normal Item installation invokes identifier registration, and the dARK provider uses its configured filter. Confirm this with an authorized test submission, including any local customizations, workflows or supplied-Handle import paths; do not assume every installation path behaves identically. A failed Minter request can affect identifier registration, so check the DSpace logs and Item state before repeating it.

The administrator-only Angular registry is `/admin/darks`. It lists local associations, missing Items, errors and status; the associated REST endpoints are `/api/admin/darks` and `/api/admin/darks/export`. UI actions start the existing DSpace scripts as processes. The authentication page is `/admin/dark-authentication` and its endpoints require site-wide administrator access.

From `[dspace.dir]`, administrators can also run:

```bash
bin/dspace dark --mint-uuid <item-uuid>
bin/dspace dark --mint-all
bin/dspace dark --refresh-uuid <item-uuid>
bin/dspace dark --refresh-status
bin/dspace dark --count-local
```

`--mint-uuid` processes one Item; a previously reserved ARK with a recorded metadata error can be retried after correction. `--mint-all` processes Items without local associations in batches, so review the scope and test a single Item first. The refresh commands read remote status; `--count-local` only counts local associations. Minter publication may be asynchronous: `R`, `D`, `U`, `P` and `T` represent reserved, draft, update, published and tombstone. For details and retry behavior, see [dARK integration](dark-dspace-integration.md).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `TRANSPORT_NOT_HTTPS_OR_HAS_CREDENTIALS` | Signed requests require HTTPS except on loopback. Remove embedded URL credentials and fragments; verify the final Minter URL and trusted certificate. |
| `DARK_AUTHENTICATION_PAUSED` | Confirm that the current public key was registered remotely, then explicitly confirm the displayed fingerprint, authority UUID and normalized destination locally. Changing any binding pauses signed writes again. |
| HTTP 301/302 | Check the configured API base path and reverse proxy with the Minter operator. Signed modifications do not follow redirects. This checkout appends `/api/v1` when missing. |
| HTTP 401 / `SIGNED_AUTH_HEADERS_REQUIRED` | Check that DSpace signing is enabled, the matching public key is registered for the authority, and the request reaches the intended Minter. A separate gateway bearer token does not replace signing. |
| Key generation or loading fails | Check the configured path, service-account ownership, exact POSIX permissions and absence of symlinks. Existing key directories are not overwritten. Do not print or upload private key material. |
| Item has no dARK after submission | Check the provider enabled flag, Spring wiring/filter, installation path, Minter access, authentication confirmation and Item metadata. Check the registry and DSpace logs for a saved error before retrying the same Item. |
| Minter rejects metadata | Correct the configured Item fields and retry that Item. The Minter decides which fields are required. |

A signed POST or PUT that times out, loses its response or fails at transport level may have succeeded remotely. Reconcile the known ARK and Item UUID with the Minter before retrying; do not assume the write failed or automatically switch to unsigned mode.

## Related documentation

- [dARK integration and command details](dark-dspace-integration.md)
- [dARK Minter authentication, public API and key rotation](dark-minter-authentication.md)
- The matching Angular checkout: `dspace-angular/docs/dark-admin-ui.md`
