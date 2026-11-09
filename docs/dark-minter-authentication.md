# dARK Minter authentication

This adds optional Ed25519 authentication to the existing dARK client. It leaves
identifier persistence, migrations, minting, batch correlation, metadata, Handle
preservation and refresh commands in place. No database migration or reindex is
needed. Java 21 supplies the cryptography; no dependency was added.

The authoritative references are the [client guide](https://github.com/LA-Referencia-IOI/dark-deployer/blob/80876e2960948b1a690cc6e4bfb8320c6d78ba30/docs/minter-auth/minter-client-authentication-guide.md),
[Java reference](https://github.com/LA-Referencia-IOI/dark-deployer/blob/80876e2960948b1a690cc6e4bfb8320c6d78ba30/docs/minter-auth/java-implementation-reference.md)
and [Java examples](https://github.com/LA-Referencia-IOI/dark-deployer/tree/80876e2960948b1a690cc6e4bfb8320c6d78ba30/docs/minter-auth/examples/java).
The signer reproduces guide section 25's published deterministic test vector.

## Install and generate keys

Build/install the backend and rebuild the corresponding `dspace-angular` checkout.
The source configuration is `dspace/config/modules/dark.cfg`; edit the installed
configuration or `local.cfg`, not a private key value in configuration.

Run as the same operating-system account that runs DSpace:

```bash
bin/dspace dark-auth --generate
```

Successful command output is **only the public key**, as standard base64 of the
32 raw Ed25519 public bytes. Submit that complete value to the Minter administrator.
The command remains registered in `launcher.xml`, outside the REST script/process
registry. Administrators can alternatively use **Generate key** on the authentication
page when `identifier.dark.auth.enabled = true`. The backend invokes the same
`DarkAuthenticationService.generate()` routine as the command; it does not start a
shell or expose a command runner. Only public configuration is returned, and no
private material is written to process output.

Default layout:

```text
[dspace.dir]/keys/                       0700
  .dark-generation.lock                 0600
  dark/                                 0700
    private.pk8                         0600 (binary PKCS#8 DER, private)
    public.der                          0600 (SubjectPublicKeyInfo DER, public)
    registration.txt                    0600 (created on registration confirmation)
```

Generation locks across CLI and server processes and commits a fully written credential
directory with an atomic move. It refuses an existing directory, including an
incomplete one. Keys are never overwritten; there is no `--force` option.
A crash can leave a private `.dark-generating-*` directory, which is never selected
for signing; the operator should inspect/remove such orphaned directories during
maintenance without displaying their contents.

The private key is unencrypted for unattended service use, as permitted by guide
section 4.3. Custody relies on the operating system: exact POSIX permissions,
service-account ownership, a deployment storage boundary, and no symbolic links.
Every use rechecks both files and verifies that they form a real Ed25519 pair.
The implementation fails closed on filesystems without these POSIX facilities;
Windows ACL storage and encrypted/OpenSSH key import are not provided.
Keep the entire `keys` tree outside static web mappings, images, source control,
process attachments and diagnostic bundles. Protect backups and service-account
access as secrets. Never paste, print, or upload `private.pk8`.

## Configure, register and confirm

Existing installations continue unsigned by default:

```properties
identifier.dark.auth.enabled = false
identifier.dark.auth.key-directory = ${dspace.dir}/keys/dark
```

The key directory must be an immediate child of `[dspace.dir]/keys`. No private
key bytes, passwords, or signing seeds belong in configuration.

1. Obtain the exact authority UUID, authorized NAAN, HTTPS Minter URL and
   confirmation of `MINTER_SIGNED_AUTH_ENABLED` from the dARK operator.
2. Set the existing `identifier.dark.authority-id`, `identifier.dark.naan` and
   `identifier.dark.minter-api-url` to the operator's values. Include the complete
   API base path (for example `/api/v1`); no suffix is appended automatically.
   Set `identifier.dark.auth.enabled = true` and restart
   DSpace. Signed modifications are paused until key generation and registration
   confirmation are complete; reads still work.
3. Sign in as a site administrator and open **dARK authentication** in the admin
   sidebar (`/admin/dark-authentication`). Click **Generate key**. This action is
   available only with authentication enabled and without an already loaded public
   key; server-side storage checks still refuse any existing credential directory,
   even if the key is corrupt or unavailable. Generation displays the public key
   immediately and leaves registration pending. Alternatively, the operator can
   run `bin/dspace dark-auth --generate`, including before enabling authentication.
4. Copy the public key and share it and its SHA-256 fingerprint with the Minter
   administrator. A collection administrator or ordinary user cannot generate keys
   or access this API/page.
5. Wait for the operator to confirm registration of that public key against the
   authority and destination, including NAAN permissions.
6. Check the displayed public key/fingerprint, authority UUID and destination.
   Select the explicit registration confirmation checkbox and click
   **Confirm registration and allow signed modifications**.
7. Perform a controlled mint/update using an authorized test Item and follow
   publication using the existing refresh commands before production use.

The confirmation is persisted locally and survives restart. It binds the actual
key fingerprint, authority UUID and normalized API URL. Changing any of these
invalidates confirmation and pauses signed modifications. A stale or unchecked
confirmation is refused. This is an operator attestation, not a remote key
registration call or a guarantee of permission/publication.

`configured` means local credentials, configuration and registration confirmation
are valid. `pending_registration` means writes are paused awaiting confirmation;
`invalid_configuration` indicates invalid/missing credentials, identity, destination
or storage permissions. The API always reports `operationalStatus: unverified`:
an unsigned read cannot prove possession of the registered key, and this panel
does not mint identifiers as a connectivity test. The page provides loading/error,
refresh, clipboard-success and clipboard-failure states. Private material is never
part of its API model; status is not transferred through SSR caches.

Signed requests require HTTPS with normal certificate validation. HTTP is accepted
only for loopback development (`localhost`, `127.0.0.1`, `[::1]`). Proxies must
preserve the raw request path/query, including a configured path prefix. Correct
redirecting URLs rather than following redirects. Clock synchronization is required.

## Existing headers and wire behavior

dARK is per-request authentication, not a login, JWT, or bearer-token exchange.
The existing `identifier.dark.api-token` remains available for a separate gateway
HTTP authentication layer; it does not replace dARK.

The batch reservation POST and metadata PUT carry the six `X-DARK-*` headers.
The UTF-8 JSON body is serialized once, hashed and sent unchanged. Canonical
signatures contain eight LF-separated lines with no trailing LF, ordinary
Ed25519 and standard base64 signatures. Fresh UUID nonces and Unix-second
timestamps are generated for every signed attempt. Key IDs hash the raw public
key, not its SPKI wrapper. GET/refresh operations remain unsigned.

With authentication disabled, the client retains the configurable legacy
`X-Authority-Id` header and bearer behavior and also sends `X-DARK-Authority-Id`,
which the current Minter requires even when signature verification is disabled.
With authentication enabled, the legacy authority header is omitted.

Automatic retries, URI normalization and redirects are disabled for signed sends.
A transport failure, missing/malformed success body, replay or server error can
leave the modification outcome unknown. Reconcile using the known ARK and stable
Item UUID (`client_item_id`) before manually resubmitting. No failure falls back to
an unsigned modification. Error codes 401/403 map to authentication failure;
known HTTP error categories retain their dARK exception classification. A bounded,
validated `X-DARK-Error-Code` may appear in errors; raw signed error bodies and
crypto/parser exception causes are not propagated to logs or `dark.last_error`.

## Rotation and rollback

Stop/drain modifying jobs first. Use a **new** key-directory name under
`[dspace.dir]/keys`, generate the new pair, restart the server with that directory,
register its public key for the **same authority UUID**, then confirm the new
fingerprint/identity/destination in the admin panel. The new directory has no
registration confirmation, so signed writes remain paused until that step.
Coordinate the cutover across all backend/CLI replicas. Retire old keys through
the deployment's secret-retention procedure after operator confirmation.

The Minter caches registered keys; replacing the blockchain key does not guarantee
instant revocation of continuous old-key traffic. Coordinate traffic/cache handling
with the operator, particularly after compromise.

To roll back application changes, restore the previous backend/frontend artifacts
and keep the protected key directory. Unsigned mode is available by setting
`identifier.dark.auth.enabled = false` and restarting **only when the Minter
operator confirms unsigned writes are allowed**. Do not disable authentication to
work around an authentication failure on a signed-only Minter. No database rollback
or index rebuild is necessary.

## REST contract

All endpoints require site-wide `ADMIN` authority, normal DSpace authentication,
and `Cache-Control: no-store`; mutations also use DSpace's normal CSRF protection.

`GET /api/admin/darks/authentication` returns this public-only shape:

```json
{
  "enabled": true,
  "configured": true,
  "publicKey": "<base64 of 32 public bytes>",
  "keyId": "<lowercase SHA-256 hex>",
  "state": "pending_registration",
  "operationalStatus": "unverified",
  "authorityId": "<authority UUID>",
  "minterApiUrl": "https://minter.example.org/api/v1"
}
```

`POST /api/admin/darks/authentication/keys` generates a dedicated pair using the
same protected server routine as the CLI. No parameters or private material are
accepted; the key directory comes exclusively from server configuration. It requires
`identifier.dark.auth.enabled = true`. Success returns **201 Created** with the
same public-only status shape, normally in `pending_registration` state. Disabled
authentication or an existing/unwritable/invalid credential directory returns **409
Conflict** with a fixed diagnostic; no key is overwritten. A failed or repeated
request is not retried automatically. Refresh status after an uncertain response
before attempting generation again.

`POST /api/admin/darks/authentication/registration` accepts exactly the displayed
public binding and explicit confirmation:

```json
{
  "keyId": "<current fingerprint>",
  "authorityId": "<current authority UUID>",
  "minterApiUrl": "https://minter.example.org/api/v1",
  "confirmed": true
}
```

Success returns the refreshed public-only status. A stale/invalid binding returns
400 with a fixed diagnostic. Anonymous/ordinary users receive 401/403. Server-side
generation is allowed only through the protected operation above; no endpoint
uploads, downloads, reads or returns private key material.

## Validation

Focused tests cover the published vector, exact UTF-8 body/path/query signatures,
fresh nonces, invalid signatures, missing/corrupt/mismatched keys, symlinks,
permissions, overwrite refusal, persisted registration binding, safe CLI output,
public-only JSON, signed HTTP errors, no resends, unsigned reads and existing
dARK provider/service/metadata behavior. REST integration tests cover administrator,
ordinary-user and anonymous access, enabled/disabled generation, safe generation
failures and registration confirmation. Angular tests cover
public-key rendering/copy, manual-copy fallback, error/retry, SSR, the authenticated
API URL, key generation, duplicate-request/overwrite prevention and explicit confirmation.

Backend commands (see the implementation report for environment-specific results):

```bash
mvn -pl dspace-api -am test -DskipUnitTests=false \
  -Dtest=DarkAuthTest,DarkAuthenticationServiceTest,DarkClientAuthenticationTest,DarkClientImplTest,DarkIdentifierProviderTest,DarkServiceImplTest,DarkMetadataBuilderTest \
  -DfailIfNoTests=false
mvn -pl dspace,dspace-server-webapp -am verify -DskipIntegrationTests=false \
  -Dit.test=DarkAuthenticationRestControllerIT -DfailIfNoTests=false
```

Angular (in `dspace-angular`):

```bash
npm test -- --include='src/app/admin/admin-dark-auth/*.spec.ts' \
  --include='src/app/shared/menu/providers/dark-authentication.menu.spec.ts' \
  --browsers=ChromeHeadless
```

## Changed components

| Area | Files |
| --- | --- |
| Signing/custody/registration | `dspace-api/.../identifier/dark/DarkRequestSigner.java`, `DarkAuthenticationService.java` |
| Existing HTTP integration | `dspace-api/.../identifier/dark/DarkClientImpl.java` |
| Local CLI | `dspace-api/.../app/dark/DarkAuth.java`, `dspace/config/launcher.xml` |
| Configuration/wiring | `dspace/config/modules/dark.cfg`, `dspace/config/spring/api/core-services.xml` |
| Admin REST | `dspace-server-webapp/.../app/rest/DarkAdminRestController.java`, `.../rest/model/DarkAuthenticationRest.java` |
| Backend tests | `DarkAuthenticationServiceTest`, `DarkClientAuthenticationTest`, `DarkAuthTest`, `DarkAuthenticationRestControllerIT` |
| Angular panel/API/tests | `src/app/admin/admin-dark-auth/` in `dspace-angular` |
| Angular navigation | `src/app/admin/admin-routes.ts`, `src/app/app.menus.ts`, `src/app/shared/menu/providers/dark-authentication.menu*` |
| Translations | `src/assets/i18n/en.json5`, `pt-BR.json5`, `pt-PT.json5` |
| Documentation | This guide, `docs/dark-dspace-integration.md` and `docs/dark-modified-files.md` |

Pre-existing `.gitignore` edits in both repositories are outside this change.

## Verification notes (2026-11-06)

Final results: backend reactor `verify` succeeded with 34 focused unit tests and
5 REST integration tests passing. All 11 focused Angular tests passed. Focused
Java Checkstyle, frontend ESLint and application/SSR TypeScript checks passed.

The focused Java tests require Mockito's Byte Buddy agent to be loaded explicitly
in this sandbox; default self-attachment is unavailable. The successful backend
verification command is:

```bash
mvn -pl dspace,dspace-server-webapp -am verify \
  -DskipUnitTests=false -DskipIntegrationTests=false \
  -Dtest=DarkAuthTest,DarkAuthenticationServiceTest,DarkClientAuthenticationTest,DarkClientImplTest,DarkIdentifierProviderTest,DarkServiceImplTest,DarkMetadataBuilderTest \
  -Dit.test=DarkAuthenticationRestControllerIT \
  -DfailIfNoTests=false -Dfailsafe.failIfNoSpecifiedTests=false \
  -Dcheckstyle.skip=true -Dmdep.overWriteSnapshots=true \
  '-Dtest.argLine=-Xmx1024m -Dfile.encoding=UTF-8 -javaagent:/Users/jesiel/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.5/byte-buddy-agent-1.17.5.jar'
```

The machine-specific agent path is a local test-environment workaround, not a
runtime dependency or a change to repository build configuration. Include the
`dspace` assembly project so the isolated H2 integration-test home contains the
standard configuration. Omitting it initially caused a missing
`config/config-definition.xml` error before any REST assertions ran.

`mvn -pl dspace-api -am checkstyle:check` found pre-existing violations in
`DarkMint.java:115`, `DarkMint.java:331`, `DarkIdentifierProvider.java:692` and
`identifier/service/DarkService.java:15`. They were left outside this change.
All new/modified Java code was checked with:

```bash
mvn -pl dspace-server-webapp -am checkstyle:check \
  '-Dcheckstyle.includes=**/DarkAuth.java,**/DarkAuthTest.java,**/DarkAuthenticationService.java,**/DarkRequestSigner.java,**/DarkClientImpl.java,**/DarkAdminRestController.java,**/DarkAuthenticationRest.java,**/DarkAuthenticationRestControllerIT.java,**/DarkAuthenticationServiceTest.java,**/DarkClientAuthenticationTest.java'
```

Frontend verification used these commands in `dspace-angular`:

```bash
ESLINT_USE_FLAT_CONFIG=false ./node_modules/.bin/eslint \
  src/app/admin/admin-dark-auth \
  src/app/shared/menu/providers/dark-authentication.menu.ts \
  src/app/shared/menu/providers/dark-authentication.menu.spec.ts \
  src/app/admin/admin-routes.ts src/app/app.menus.ts
./node_modules/.bin/tsc --noEmit -p tsconfig.app.json
./node_modules/.bin/tsc --noEmit -p tsconfig.server.json
CHROME_BIN='/Applications/Brave Browser.app/Contents/MacOS/Brave Browser' \
  npm test -- --include='src/app/admin/admin-dark-auth/*.spec.ts' \
  --include='src/app/shared/menu/providers/dark-authentication.menu.spec.ts' \
  --browsers=ChromeHeadless
```

The initial root `tsconfig.json` probe included Cypress/tooling sources and failed
with 766 errors; the application and SSR project configurations both passed.
ESLint requires the existing legacy-config mode shown above. The first UI test
run caught a duplicated `/api` in the URL; this was fixed and the 11 UI tests passed.
No live Minter registration, TLS deployment, production key generation or remote
write/publication was performed. POSIX key custody and loopback HTTP are tested
locally; production operation still requires the operator-led registration and
controlled integration verification described above.


### Administration key generation (2026-11-07)

Added the **Generate key** action for installations with authentication enabled.
It shares the existing CLI custody implementation, preserves private-key isolation
and exclusive generation, and returns public-only configuration. The original CLI
continues to work with authentication disabled for initial provisioning. No schema
migration or reindex is needed. Deploy the corresponding backend and frontend
changes together; rolling back the button/API does not remove generated credentials.

Focused verification uses the same Maven/Angular commands above, plus actual
Angular template compilation (`./node_modules/.bin/ngc --noEmit -p tsconfig.app.json`).
No keys were generated for the real installation during implementation; filesystem
key generation is exercised only in isolated test directories.

Verification for this addition passed: backend reactor `verify` with 34 unit tests
and 10 REST integration tests; 18 focused Angular tests; Angular AOT template
compilation; frontend ESLint; and focused Checkstyle on `DarkAuth.java`,
`DarkAdminRestController.java` and `DarkAuthenticationRestControllerIT.java`.
The focused Checkstyle command was:

```bash
mvn -pl dspace-server-webapp -am checkstyle:check \
  '-Dcheckstyle.includes=**/DarkAuth.java,**/DarkAdminRestController.java,**/DarkAuthenticationRestControllerIT.java'
```

### Registration confirmation troubleshooting (2026-11-07)

A page showing the public key, authority and destination has loaded configuration.
A subsequent registration failure must be reported as a confirmation failure,
without replacing that status or claiming the configuration could not be loaded.
The panel now distinguishes expired sessions (401), refused confirmation (403),
network failures and other confirmation errors, using fixed localized messages.
Raw backend diagnostics are never rendered.

The administrator API service now waits for `GET /api/security/csrf` to return the
DSpace CSRF cookie/header before issuing the generation or registration POST.
Both requests use credentials and the existing DSpace XSRF interceptor. Neither
mutation is automatically retried, and a failed CSRF initialization stops the POST.
This follows DSpace's existing CSRF endpoint rather than bypassing CSRF protection.

If local status remains `pending_registration` after changing the authority UUID,
the persisted confirmation can still describe the previous authority, even when
its key fingerprint and Minter destination are unchanged. Register the displayed
public key for the current authority with the Minter administrator, refresh the
panel and explicitly confirm that current binding. Do not regenerate the key or
manually overwrite the registration file as a workaround. For HTTP 403, sign in
again as a site administrator and check browser cookies/access restrictions if
confirmation remains refused.

Verification of this correction passed with 25 focused Angular tests, application
AOT template compilation and ESLint. Commands were the existing focused
`npm test -- --include='src/app/admin/admin-dark-auth/*.spec.ts' --include='src/app/shared/menu/providers/dark-authentication.menu.spec.ts' --browsers=ChromeHeadless`
(using the installed Brave Chromium executable through `CHROME_BIN`),
`./node_modules/.bin/ngc --noEmit -p tsconfig.app.json` and
`ESLINT_USE_FLAT_CONFIG=false ./node_modules/.bin/eslint src/app/admin/admin-dark-auth --fix`.
The user's browser-session confirmation was not submitted by the agent; remote
registration and the explicit confirmation remain operator/administrator actions.


### Explicit Minter base URL (2026-11-09)

`identifier.dark.minter-api-url` now specifies the complete API base URL. Both the
HTTP client and authentication/registration binding use that configured URL, with
surrounding whitespace and one trailing slash removed, without appending `/api/v1`.
For the sandbox configure:

```properties
identifier.dark.minter-api-url = https://sandbox.dark-pid.net/api/v1
```

A root URL such as `https://example.org` now sends reservation requests to
`https://example.org/arks/batch`. A custom base such as
`https://example.org/gateway/minter` sends them to
`https://example.org/gateway/minter/arks/batch`. The same base applies to metadata
updates and reads.

Migration: before updating an installation that omitted `/api/v1` while relying
on automatic completion, add that prefix explicitly to its configured URL. This
preserves the effective destination and its existing registration confirmation.
An intentional change of the effective destination requires confirmation again;
keys do not need to be regenerated. No database migration or reindex is needed.
When rolling back, remember that the previous client automatically appended
`/api/v1` to URLs that did not already end with that suffix.


### Diagnosing SIGNED_AUTH_HEADERS_REQUIRED

This Minter response does not establish whether the client sent a signature:
the HTTP client maps all 401 responses through its sanitized authentication-error
handler, including responses to unsigned requests.

The client now snapshots the presence/basic shape of the six dARK headers before
sending, and includes a fixed diagnostic if the Minter returns
`X-DARK-Error-Code: SIGNED_AUTH_HEADERS_REQUIRED`:

- `The request was not signed`: check the backend's effective
  `identifier.dark.auth.enabled = true`, restart it, and confirm registration for
  the current key, authority and complete HTTPS Minter URL.
- `All six dARK headers were prepared locally`: check forwarding through any
  reverse proxy/gateway between DSpace and Minter, and verify the Minter's header
  requirements. Local preparation alone does not prove the headers arrived.
- Missing, empty, duplicate or oversized headers are identified by their fixed
  names only. No signature, nonce, authorization token, key value, raw response
  body or request body is included in the diagnostic.

No authentication rejection automatically resends a mutation or falls back to
an unsigned request. The diagnostic does not register a key or bypass the existing
registration check.

Validation of this diagnostic passed with 40 focused unit/regression tests,
including signed and unsigned `SIGNED_AUTH_HEADERS_REQUIRED` responses, and
Checkstyle for the changed client and tests. The existing focused Maven test
command above was used. Focused style validation was:

```bash
mvn -pl dspace-api -am checkstyle:check \
  '-Dcheckstyle.includes=**/DarkClientImpl.java,**/DarkClientAuthenticationTest.java'
```

These checks do not establish the remote server's effective configuration or
prove which headers a remote proxy forwarded. Confirm the effective setting on
that backend and use the fixed diagnostic from its next attempt to distinguish
those cases; do not capture or share raw authentication headers.
