package org.ecocean.api.auth;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.ecocean.CommonConfiguration;
import org.ecocean.Util;

/**
 * Issues and verifies short-lived RS256 JWTs that carry ONLY identity
 * (subject = user UUID, context). No admin/role claims — the consumer
 * resolves privileges fresh. Wildbook holds the private (signing) key;
 * the external scoped-access kernel holds the public key.
 *
 * Keys are RSA, supplied as Base64 of the encoded key bytes (private = PKCS8,
 * public = X.509), via commonConfiguration.properties:
 *   jwtPrivateKeyBase64=...   (signing; required to mint tokens)
 *   jwtPublicKeyBase64=...    (verify; optional Wildbook-side)
 *   jwtIssuer=wildbook
 *   jwtAudience=wildbook-scoped-api
 * If the private key is absent, the service is "disabled" and the token
 * endpoint returns 503.
 */
public class JwtService {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final String keyId; // JWT header 'kid' for key rotation (Codex Medium); may be null

    private JwtService(PrivateKey priv, PublicKey pub, String issuer, String audience,
        String keyId) {
        this.privateKey = priv;
        this.publicKey = pub;
        this.issuer = issuer;
        this.audience = audience;
        this.keyId = keyId;
    }

    // test/explicit overload without a keyId
    public static JwtService fromBase64Keys(String privB64, String pubB64, String issuer,
        String audience) {
        return fromBase64Keys(privB64, pubB64, issuer, audience, null);
    }

    public static JwtService fromBase64Keys(String privB64, String pubB64, String issuer,
        String audience, String keyId) {
        PrivateKey priv = null;
        PublicKey pub = null;
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (Util.stringExists(privB64)) {
                priv = kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privB64.trim())));
            }
            if (Util.stringExists(pubB64)) {
                pub = kf.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64.trim())));
            }
        } catch (Exception ex) {
            System.out.println("JwtService: failed to load keys: " + ex);
        }
        return new JwtService(priv, pub, issuer, audience, keyId);
    }

    public static JwtService fromConfig(String context) {
        return fromBase64Keys(
            CommonConfiguration.getProperty("jwtPrivateKeyBase64", context),
            CommonConfiguration.getProperty("jwtPublicKeyBase64", context),
            orDefault(CommonConfiguration.getProperty("jwtIssuer", context), "wildbook"),
            orDefault(CommonConfiguration.getProperty("jwtAudience", context),
                "wildbook-scoped-api"),
            CommonConfiguration.getProperty("jwtKeyId", context)); // may be null
    }

    private static String orDefault(String v, String d) {
        return Util.stringExists(v) ? v : d;
    }

    public boolean isEnabled() {
        return privateKey != null;
    }

    public String sign(String userUuid, String context, long ttlMillis) {
        if (!isEnabled()) throw new IllegalStateException("JwtService not enabled (no private key)");
        long now = System.currentTimeMillis();
        io.jsonwebtoken.JwtBuilder b = Jwts.builder()
            .issuer(issuer)
            .audience().add(audience).and()
            .subject(userUuid)
            .claim("context", context)
            .id(Util.generateUUID())
            .issuedAt(new Date(now))
            .expiration(new Date(now + ttlMillis));
        if (Util.stringExists(keyId)) b.header().keyId(keyId).and(); // 'kid' for rotation
        return b.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    public Jws<Claims> verify(String token) {
        if (publicKey == null) throw new IllegalStateException("JwtService cannot verify (no public key)");
        Jws<Claims> jws = Jwts.parser()
            .requireIssuer(issuer)
            .requireAudience(audience)
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token);
        // Pin to RS256: reject same-key tokens signed with a different RSA/PSS variant.
        // jjwt 0.12.6's sig().clear().add() API throws IAE before build(), so we check
        // the algorithm header explicitly after a successful signature verification.
        String alg = jws.getHeader().getAlgorithm();
        if (!Jwts.SIG.RS256.getId().equals(alg)) {
            throw new JwtException("JWT algorithm '" + alg + "' is not accepted; only RS256 is allowed");
        }
        return jws;
    }
}
