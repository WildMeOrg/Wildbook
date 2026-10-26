package org.ecocean.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Credential format: wbsvc_&lt;keyId&gt;.&lt;secret&gt;; the secret is 32 random bytes, base64url. */
public final class ServiceKeyCredential {
    public static final String PREFIX = "wbsvc_";
    public static final Set<String> SCOPES = Set.of("data:read", "submissions:read", "submissions:write");
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ServiceKeyCredential() {}

    public static final class Issued {
        public final String keyId;
        public final String plaintext;
        public final String secretHash;

        public Issued(String keyId, String plaintext, String secretHash) {
            this.keyId = keyId; this.plaintext = plaintext; this.secretHash = secretHash;
        }
    }

    public static Issued issue() {
        byte[] raw = new byte[SECRET_BYTES];
        RANDOM.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String keyId = UUID.randomUUID().toString();
        return new Issued(keyId, PREFIX + keyId + "." + secret, hash(secret));
    }

    /** Returns [keyId, secret], or empty for anything malformed. Never throws. */
    public static Optional<String[]> parse(String credential) {
        try {
            if (credential == null || !credential.startsWith(PREFIX)) return Optional.empty();
            String rest = credential.substring(PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot <= 0 || dot == rest.length() - 1) return Optional.empty();
            String keyId = rest.substring(0, dot);
            String secret = rest.substring(dot + 1);
            if (!UUID.fromString(keyId).toString().equals(keyId.toLowerCase())) return Optional.empty();
            if (Base64.getUrlDecoder().decode(secret).length != SECRET_BYTES) return Optional.empty();
            return Optional.of(new String[] { keyId, secret });
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Constant-time comparison of SHA-256(secret) against the stored hex hash. */
    public static boolean matches(String secret, String storedHash) {
        if (secret == null || storedHash == null) return false;
        return MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.US_ASCII),
            storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    static String hash(String secret) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray();
            char[] out = new char[d.length * 2];
            for (int i = 0; i < d.length; i++) {
                out[i * 2] = hex[(d[i] >> 4) & 0xf];
                out[i * 2 + 1] = hex[d[i] & 0xf];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
