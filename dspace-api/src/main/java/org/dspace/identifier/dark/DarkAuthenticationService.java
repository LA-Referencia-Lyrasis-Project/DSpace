/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Server-only Ed25519 custody and signing, following the dARK Java implementation reference.
 * No method returns private material, and failures deliberately discard parser exception details.
 */
public class DarkAuthenticationService {

    public static final String CFG_ENABLED = "identifier.dark.auth.enabled";
    public static final String CFG_DIRECTORY = "identifier.dark.auth.key-directory";
    private static final Logger log = LogManager.getLogger();

    private static final String KEY_ALGORITHM = "Ed25519";
    private static final String PRIVATE_FILE = "private.pk8";
    private static final String PUBLIC_FILE = "public.der";
    private static final String REGISTRATION_FILE = "registration.txt";
    private static final String GENERATION_LOCK_FILE = ".dark-generation.lock";
    private static final String GENERATION_DIRECTORY_PREFIX = ".dark-generating-";
    private static final String REGISTRATION_TEMP_PREFIX = ".registration-";
    private static final String REGISTRATION_TEMP_SUFFIX = ".tmp";
    private static final String KEYS_BOUNDARY_DIRECTORY = "keys";
    private static final String DEFAULT_KEY_DIRECTORY = "dark";
    private static final String PAIR_CHALLENGE = "dARK credential pair verification";

    /** Owner-only access for credential directories and files; no group or other bits are ever accepted. */
    private static final String DIRECTORY_PERMISSIONS = "rwx------";
    private static final String FILE_PERMISSIONS = "rw-------";

    /** Credential files are small (a PKCS#8 or SPKI Ed25519 key); anything larger is not ours. */
    private static final int MAX_CREDENTIAL_FILE_BYTES = 4096;
    /** Longest accepted X-DARK-Authority-Id value (guide §8). */
    private static final int MAX_AUTHORITY_LENGTH = 255;
    /** Length in bytes of a raw Ed25519 public key. */
    private static final int RAW_PUBLIC_KEY_BYTES = 32;
    /** DER SubjectPublicKeyInfo header of an Ed25519 public key, followed by the raw 32 bytes. */
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    @Autowired
    private ConfigurationService configurationService;

    /** Whether DARK2 signing is explicitly enabled; legacy installations default to false. */
    public boolean isEnabled() {
        return configurationService.getBooleanProperty(CFG_ENABLED, false);
    }

    /**
     * Generates a dedicated pair. Directory creation is exclusive: existing or incomplete
     * credentials are never overwritten, including concurrent CLI invocations.
     * Only the raw-base64 PUBLIC key is returned, in the Minter registration format.
     */
    public synchronized String generate() throws DarkIdentifierException {
        try {
            Path directory = keyDirectory();
            Path boundary = directory.getParent();
            if (!Files.exists(boundary, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(boundary, directoryAttributes());
            }
            checkDirectory(boundary);
            // A process-wide filesystem lock makes exclusive generation safe across CLI invocations.
            Path lockPath = boundary.resolve(GENERATION_LOCK_FILE);
            if (!Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createFile(lockPath, fileAttributes());
                } catch (FileAlreadyExistsException e) {
                    // Another generator created the shared lock; validate it below.
                }
            }
            checkFile(lockPath);
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw new Failure("GENERATION_LOCK_HELD");
                }
                if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw new Failure("KEY_DIRECTORY_ALREADY_EXISTS");
                }
                Path temporary = Files.createTempDirectory(boundary, GENERATION_DIRECTORY_PREFIX,
                    directoryAttributes());
                try {
                    KeyPair pair = KeyPairGenerator.getInstance(KEY_ALGORITHM).generateKeyPair();
                    writeNew(temporary.resolve(PRIVATE_FILE), pair.getPrivate().getEncoded());
                    writeNew(temporary.resolve(PUBLIC_FILE), pair.getPublic().getEncoded());
                    Files.move(temporary, directory, StandardCopyOption.ATOMIC_MOVE);
                    // Re-load and verify before reporting successful generation.
                    return publicKey(load());
                } finally {
                    if (Files.isDirectory(temporary, LinkOption.NOFOLLOW_LINKS)) {
                        Files.deleteIfExists(temporary.resolve(PRIVATE_FILE));
                        Files.deleteIfExists(temporary.resolve(PUBLIC_FILE));
                        Files.deleteIfExists(temporary);
                    }
                }
            }
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw authenticationError("generate",
                "Unable to generate dARK keys: use a new directory with secure POSIX permissions.", e);
        }
    }

    /** Local configuration and registration health; this never performs a remote modification. */
    public synchronized Status status() {
        String publicKey = null;
        String keyId = null;
        try {
            KeyPair pair = load();
            publicKey = publicKey(pair);
            keyId = keyId(pair);
            String authority = authorityId();
            String url = minterUrl();
            boolean registered = registrationMatches(keyId, authority, url);
            return new Status(isEnabled(), true, publicKey, keyId,
                isEnabled() ? (registered ? "configured" : "pending_registration") : "disabled", "unverified",
                authority, url);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            logFailure("status", e);
            return new Status(isEnabled(), false, publicKey, keyId,
                isEnabled() ? "invalid_configuration" : "disabled", "unverified", null, null);
        }
    }

    /**
     * Records explicit out-of-band registration confirmation for this key, identity and destination.
     * Configuration changes or a new key directory invalidate the confirmation automatically.
     */
    public synchronized Status confirmRegistration(String keyId, String authority, String url, boolean confirmed)
        throws DarkIdentifierException {
        try {
            KeyPair pair = load();
            if (!confirmed) {
                throw new Failure("CONFIRMATION_NOT_EXPLICIT");
            }
            if (!Objects.equals(keyId(pair), keyId) || !Objects.equals(authorityId(), authority)
                || !Objects.equals(minterUrl(), url)) {
                throw new Failure("CONFIRMATION_STALE_IDENTITY");
            }
            Path directory = keyDirectory();
            Path registration = directory.resolve(REGISTRATION_FILE);
            if (Files.isSymbolicLink(registration)) {
                throw new Failure("REGISTRATION_FILE_IS_SYMLINK");
            }
            Path temporary = Files.createTempFile(directory, REGISTRATION_TEMP_PREFIX, REGISTRATION_TEMP_SUFFIX,
                fileAttributes());
            try {
                Files.writeString(temporary, registrationValue(keyId, authority, url), StandardCharsets.UTF_8);
                Files.move(temporary, registration, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return status();
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw authenticationError("confirmRegistration",
                "Unable to confirm dARK registration; refresh and confirm the current key.", e);
        }
    }

    private boolean registrationMatches(String keyId, String authority, String url) throws IOException {
        Path file = keyDirectory().resolve(REGISTRATION_FILE);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        checkFile(file);
        return registrationValue(keyId, authority, url).equals(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static String registrationValue(String keyId, String authority, String url) {
        return String.join("\n", keyId, authority, url);
    }

    private String authorityId() {
        String authority = configurationService.getProperty("identifier.dark.authority-id");
        if (authority == null || authority.length() > MAX_AUTHORITY_LENGTH) {
            throw new Failure("AUTHORITY_ID_MISSING_OR_TOO_LONG");
        }
        try {
            if (!UUID.fromString(authority).toString().equalsIgnoreCase(authority)) {
                throw new Failure("AUTHORITY_ID_NOT_CANONICAL_UUID");
            }
        } catch (IllegalArgumentException e) {
            throw new Failure("AUTHORITY_ID_NOT_A_UUID");
        }
        return authority;
    }

    private String minterUrl() {
        String configured = configurationService.getProperty(DarkClientImpl.CFG_MINTER_API_URL);
        if (configured == null) {
            throw new Failure("MINTER_URL_MISSING");
        }
        String url = StringUtils.removeEnd(configured.trim(), "/");
        URI uri = URI.create(url);
        validateTransport(uri);
        if (uri.getRawQuery() != null) {
            throw new Failure("MINTER_URL_HAS_QUERY");
        }
        return url;
    }

    /** Signs a modification, refusing invalid identity, transport, key or permissions. */
    public synchronized Map<String, String> headers(String method, URI uri, String authority, byte[] body)
        throws DarkIdentifierException {
        try {
            String url = minterUrl();
            if (!Objects.equals(authorityId(), authority)) {
                throw new Failure("AUTHORITY_CONFIGURATION_CHANGED");
            }
            if (!uri.toString().startsWith(url + "/")) {
                throw new Failure("DESTINATION_OUTSIDE_CONFIGURED_MINTER");
            }
            validateTransport(uri);
            KeyPair pair = load();
            if (!registrationMatches(keyId(pair), authority, url)) {
                log.warn("dARK signing refused: DARK_AUTHENTICATION_PAUSED (registration not confirmed).");
                throw authenticationError(
                    "dARK modifications are paused: confirm the current key, authority and destination registration.");
            }
            return DarkRequestSigner.sign(pair.getPrivate(), keyId(pair), method, uri, authority, body,
                Instant.now().getEpochSecond(), UUID.randomUUID());
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw authenticationError("headers",
                "dARK signing configuration is unavailable or invalid; check identity, URL and key permissions.", e);
        }
    }

    private static void validateTransport(URI uri) {
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
            || "[::1]".equals(uri.getHost());
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
            || !("https".equalsIgnoreCase(uri.getScheme())
                || (local && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new Failure("TRANSPORT_NOT_HTTPS_OR_HAS_CREDENTIALS");
        }
    }

    private Path keyDirectory() throws IOException {
        Path installation = Path.of(configurationService.getProperty("dspace.dir")).toRealPath();
        Path boundary = installation.resolve(KEYS_BOUNDARY_DIRECTORY);
        Path directory = Path.of(configurationService.getProperty(CFG_DIRECTORY,
            boundary.resolve(DEFAULT_KEY_DIRECTORY).toString())).toAbsolutePath().normalize();
        // One private directory inside the installation's server-only keys boundary.
        if (!boundary.equals(directory.getParent())) {
            throw new Failure("KEY_DIRECTORY_OUTSIDE_KEYS_BOUNDARY");
        }
        for (Path current = directory; current != null && current.startsWith(installation);
             current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new Failure("SYMLINK_IN_KEY_PATH");
            }
        }
        return directory;
    }

    private KeyPair load() throws IOException, GeneralSecurityException {
        Path directory = keyDirectory();
        checkDirectory(directory.getParent());
        checkDirectory(directory);
        Path privateFile = directory.resolve(PRIVATE_FILE);
        Path publicFile = directory.resolve(PUBLIC_FILE);
        checkFile(privateFile);
        checkFile(publicFile);
        byte[] encoded = Files.readAllBytes(privateFile);
        try {
            KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);
            KeyPair pair = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile))),
                factory.generatePrivate(new PKCS8EncodedKeySpec(encoded)));
            rawPublicKey(pair);
            byte[] challenge = PAIR_CHALLENGE.getBytes(StandardCharsets.UTF_8);
            Signature signature = Signature.getInstance(KEY_ALGORITHM);
            signature.initSign(pair.getPrivate());
            signature.update(challenge);
            byte[] signed = signature.sign();
            signature.initVerify(pair.getPublic());
            signature.update(challenge);
            if (!signature.verify(signed)) {
                throw new Failure("KEY_PAIR_MISMATCH");
            }
            return pair;
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static byte[] rawPublicKey(KeyPair pair) throws GeneralSecurityException {
        byte[] encoded = pair.getPublic().getEncoded();
        if (encoded.length != SPKI_PREFIX.length + RAW_PUBLIC_KEY_BYTES
            || !Arrays.equals(SPKI_PREFIX, Arrays.copyOf(encoded, SPKI_PREFIX.length))) {
            throw new Failure("PUBLIC_KEY_NOT_ED25519_SPKI");
        }
        return Arrays.copyOfRange(encoded, SPKI_PREFIX.length, encoded.length);
    }

    private static String publicKey(KeyPair pair) throws GeneralSecurityException {
        return Base64.getEncoder().encodeToString(rawPublicKey(pair));
    }

    private static String keyId(KeyPair pair) throws GeneralSecurityException {
        return DarkRequestSigner.sha256(rawPublicKey(pair));
    }

    private static void checkDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new Failure("NOT_A_DIRECTORY_OR_IS_SYMLINK");
        }
        checkPermissions(path, DIRECTORY_PERMISSIONS);
    }

    private static void checkFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new Failure("NOT_A_REGULAR_FILE");
        }
        if (Files.size(path) > MAX_CREDENTIAL_FILE_BYTES) {
            throw new Failure("CREDENTIAL_FILE_TOO_LARGE");
        }
        checkPermissions(path, FILE_PERMISSIONS);
    }

    private static void checkPermissions(Path path, String permissions) throws IOException {
        if (!Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
            .equals(PosixFilePermissions.fromString(permissions))) {
            throw new Failure("PERMISSIONS_NOT_OWNER_ONLY");
        }
        // The server process must own its credentials: the CLI and the web application share one OS user.
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(
            path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name")))) {
            throw new Failure("OWNER_IS_NOT_PROCESS_USER");
        }
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        try {
            Files.createFile(path, fileAttributes());
            Files.write(path, bytes, StandardOpenOption.WRITE);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static FileAttribute<Set<PosixFilePermission>> directoryAttributes() {
        return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(DIRECTORY_PERMISSIONS));
    }

    private static FileAttribute<Set<PosixFilePermission>> fileAttributes() {
        return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(FILE_PERMISSIONS));
    }

    /**
     * Logs only a fixed reason code or the exception class. Parser and provider messages are never logged
     * because they could echo key material or filesystem layout.
     */
    private static void logFailure(String operation, Exception cause) {
        String reason = cause instanceof Failure failure ? failure.reason() : cause.getClass().getSimpleName();
        log.warn("dARK authentication operation '{}' failed: {}", operation, reason);
    }

    private static DarkIdentifierException authenticationError(String operation, String message, Exception cause) {
        logFailure(operation, cause);
        return new DarkIdentifierException(message, DarkIdentifierException.AUTHENTICATION_ERROR);
    }

    private static DarkIdentifierException authenticationError(String message) {
        return new DarkIdentifierException(message, DarkIdentifierException.AUTHENTICATION_ERROR);
    }

    /** Internal credential-policy violation identified by a secret-free reason code; its text is log-only. */
    private static final class Failure extends RuntimeException {
        private final String reason;

        Failure(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    /** Public-only administrative view. Operational status remains unverified until a controlled remote write. */
    public record Status(boolean enabled, boolean configured, String publicKey, String keyId,
                         String state, String operationalStatus, String authorityId, String minterApiUrl) { }
}
