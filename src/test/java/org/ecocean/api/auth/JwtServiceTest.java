package org.ecocean.api.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    /** Generates a fresh RSA-2048 keypair and returns a JwtService wired to it. */
    private JwtService serviceWithFreshKeys() throws Exception {
        return serviceWithFreshKeys("wildbook", "wildbook-scoped-api");
    }

    private JwtService serviceWithFreshKeys(String issuer, String audience) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()); // PKCS8
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());   // X.509
        return JwtService.fromBase64Keys(privB64, pubB64, issuer, audience);
    }

    /** Returns a fresh RSA-2048 KeyPair (used by algorithm-confusion tests). */
    private KeyPair freshKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    @Test void signThenVerify_roundTrip() throws Exception {
        JwtService svc = serviceWithFreshKeys();
        String token = svc.sign("user-uuid-123", "context0", 60_000L); // 60s TTL
        Jws<Claims> jws = svc.verify(token);
        Claims c = jws.getPayload();
        assertEquals("user-uuid-123", c.getSubject(), "subject = user uuid");
        assertEquals("context0", c.get("context", String.class), "context claim");
        assertEquals("wildbook", c.getIssuer(), "issuer");
        assertNotNull(c.getId(), "jti present");
        assertTrue(c.getExpiration().getTime() > System.currentTimeMillis(), "exp in future");
        assertNull(c.get("admin"), "no admin claim");
        assertNull(c.get("roles"), "no roles claim");
    }

    @Test void expiredToken_rejected() throws Exception {
        JwtService svc = serviceWithFreshKeys();
        String token = svc.sign("u", "context0", -60_000L); // expired 60s ago (avoids sub-second boundary flake)
        assertThrows(JwtException.class, () -> svc.verify(token), "expired token must be rejected");
    }

    @Test void tamperedToken_rejected() throws Exception {
        JwtService svc = serviceWithFreshKeys();
        String token = svc.sign("u", "context0", 60_000L);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "bb" : "aa");
        assertThrows(JwtException.class, () -> svc.verify(tampered), "bad signature must be rejected");
    }

    @Test void wrongAudienceOrIssuer_rejected() throws Exception {
        // SAME keypair (valid signature), but the verifier requires a different
        // issuer/audience — this genuinely exercises requireIssuer/requireAudience
        // (a different-key test would pass even if those checks were removed).
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        JwtService signer = JwtService.fromBase64Keys(privB64, pubB64, "wildbook", "wildbook-scoped-api");
        String token = signer.sign("u", "context0", 60_000L);

        JwtService wrongIssuer = JwtService.fromBase64Keys(privB64, pubB64, "evil-issuer", "wildbook-scoped-api");
        assertThrows(JwtException.class, () -> wrongIssuer.verify(token), "mismatched issuer must be rejected");

        JwtService wrongAudience = JwtService.fromBase64Keys(privB64, pubB64, "wildbook", "some-other-aud");
        assertThrows(JwtException.class, () -> wrongAudience.verify(token), "mismatched audience must be rejected");
    }

    @Test void disabledWhenKeysMissing() {
        JwtService svc = JwtService.fromBase64Keys(null, null, "wildbook", "wildbook-scoped-api");
        assertFalse(svc.isEnabled(), "service disabled without keys");
        assertThrows(IllegalStateException.class, () -> svc.sign("u", "context0", 1000L),
            "signing while disabled must throw");
    }

    // ------------------------------------------------------------------ //
    // Algorithm-confusion regression tests (defence-in-depth, Fix 1)      //
    // ------------------------------------------------------------------ //

    /**
     * alg=none UNSECURED JWT must be rejected.
     * jjwt 0.12.x will not emit alg=none via its builder, so we craft the
     * token string manually: base64url(header) + "." + base64url(payload) + "."
     * (empty signature part) — which is the canonical unsecured-JWT form.
     */
    @Test void algNone_rejected() throws Exception {
        JwtService svc = serviceWithFreshKeys();

        Base64.Encoder b64url = Base64.getUrlEncoder().withoutPadding();
        String header  = b64url.encodeToString(
            "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis() / 1000;
        String payload = b64url.encodeToString(
            ("{\"iss\":\"wildbook\",\"aud\":\"wildbook-scoped-api\","
                + "\"sub\":\"u\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}")
            .getBytes(StandardCharsets.UTF_8));
        // canonical unsecured JWT: header.payload. (empty signature)
        String unsecuredToken = header + "." + payload + ".";

        assertThrows(JwtException.class, () -> svc.verify(unsecuredToken),
            "alg=none token must be rejected");
    }

    /**
     * HS256 token signed with the RSA public key's raw bytes as the HMAC secret
     * must be rejected (algorithm-confusion / symmetric-with-asymmetric attack).
     */
    @Test void hs256ForgedWithPublicKey_rejected() throws Exception {
        KeyPair kp = freshKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pubB64  = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        JwtService svc = JwtService.fromBase64Keys(privB64, pubB64, "wildbook", "wildbook-scoped-api");

        // forge: sign with HS256 using the RSA public key bytes as HMAC secret
        SecretKeySpec hmacKey = new SecretKeySpec(kp.getPublic().getEncoded(), "HmacSHA256");
        long now = System.currentTimeMillis();
        String forged = Jwts.builder()
            .issuer("wildbook")
            .audience().add("wildbook-scoped-api").and()
            .subject("u")
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3_600_000L))
            .signWith(hmacKey, Jwts.SIG.HS256)
            .compact();

        assertThrows(JwtException.class, () -> svc.verify(forged),
            "HS256-with-RSA-public-key forged token must be rejected");
    }

    // ------------------------------------------------------------------ //
    // Key-ID (kid) header tests (key-rotation support, Fix 2)            //
    // ------------------------------------------------------------------ //

    /**
     * When a keyId is supplied via the 5-arg overload, the signed token must
     * carry a `kid` header that survives round-trip verification.
     */
    @Test void keyIdHeader_setWhenProvided() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pubB64  = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        JwtService svc = JwtService.fromBase64Keys(privB64, pubB64, "wildbook", "wildbook-scoped-api", "key-1");
        String token = svc.sign("kid-user", "context0", 60_000L);
        Jws<Claims> jws = svc.verify(token);

        // kid header must be propagated
        assertEquals("key-1", jws.getHeader().getKeyId(), "kid header must equal supplied keyId");

        // round-trip claims must still be correct
        Claims c = jws.getPayload();
        assertEquals("kid-user", c.getSubject(), "subject preserved with kid header");
        assertEquals("wildbook", c.getIssuer(), "issuer preserved with kid header");
    }

    /**
     * When no keyId is provided (4-arg overload), the `kid` header must be
     * absent (null) — confirming the header is not injected spuriously.
     */
    @Test void keyIdHeader_absentWhenNotProvided() throws Exception {
        JwtService svc = serviceWithFreshKeys();
        String token = svc.sign("no-kid-user", "context0", 60_000L);
        Jws<Claims> jws = svc.verify(token);
        assertNull(jws.getHeader().getKeyId(), "kid header must be absent when no keyId supplied");
    }

    // ------------------------------------------------------------------ //
    // canVerify() predicate tests (Task 2)                               //
    // ------------------------------------------------------------------ //

    @Test void canVerify_trueWhenPublicKeyPresent() throws Exception {
        JwtService svc = serviceWithFreshKeys();
        assertTrue(svc.canVerify(), "service built with a keypair can verify");
    }

    @Test void canVerify_falseWhenNoKeys() {
        JwtService svc = JwtService.fromBase64Keys(null, null, "wildbook", "wildbook-scoped-api");
        assertFalse(svc.canVerify(), "service with no public key cannot verify");
    }

    /**
     * A token signed with RS384 using the SAME RSA private key must be rejected
     * once the verifier is pinned to RS256.  (This is the test that should FAIL
     * before Fix 1 is applied and PASS after.)
     */
    @Test void rs384SameKey_rejected() throws Exception {
        KeyPair kp = freshKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pubB64  = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        JwtService svc = JwtService.fromBase64Keys(privB64, pubB64, "wildbook", "wildbook-scoped-api");

        // forge: sign with RS384 (stronger but wrong algorithm)
        long now = System.currentTimeMillis();
        String rs384Token = Jwts.builder()
            .issuer("wildbook")
            .audience().add("wildbook-scoped-api").and()
            .subject("u")
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3_600_000L))
            .signWith(kp.getPrivate(), Jwts.SIG.RS384)
            .compact();

        assertThrows(JwtException.class, () -> svc.verify(rs384Token),
            "RS384-signed token must be rejected when verifier is pinned to RS256");
    }
}
