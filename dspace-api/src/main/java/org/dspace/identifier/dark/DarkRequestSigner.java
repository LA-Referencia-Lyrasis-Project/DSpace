/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.identifier.dark;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** DARK2 wire encoding, adapted from the dARK Java reference's Dark2Signer. */
final class DarkRequestSigner {

    private DarkRequestSigner() { }

    /** Signs the exact body and raw path/query with ordinary Ed25519 (no prehash). */
    static Map<String, String> sign(PrivateKey key, String keyId, String method, URI uri,
                                  String authority, byte[] body, long timestamp, UUID nonce)
        throws GeneralSecurityException {
        String path = uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            path += "?" + uri.getRawQuery();
        }
        String digest = sha256(body);
        String[] lines = {"DARK2", method.toUpperCase(Locale.ROOT), path, authority, keyId,
            Long.toString(timestamp), nonce.toString(), digest};
        for (String line : lines) {
            if (line == null || line.isBlank() || line.contains("\r") || line.contains("\n")
                || !line.equals(line.trim())) {
                throw new IllegalArgumentException("Invalid DARK2 canonical field");
            }
        }
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return Map.of("X-DARK-Authority-Id", authority, "X-DARK-Key-Id", keyId,
            "X-DARK-Timestamp", Long.toString(timestamp), "X-DARK-Nonce", nonce.toString(),
            "X-DARK-Content-SHA256", digest,
            "X-DARK-Signature", Base64.getEncoder().encodeToString(signature.sign()));
    }

    /** Lowercase hexadecimal SHA-256. */
    static String sha256(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
