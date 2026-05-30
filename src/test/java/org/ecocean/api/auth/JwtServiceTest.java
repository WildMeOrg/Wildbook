package org.ecocean.api.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService serviceWithFreshKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()); // PKCS8
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());   // X.509
        return JwtService.fromBase64Keys(privB64, pubB64, "wildbook", "wildbook-scoped-api");
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
        String token = svc.sign("u", "context0", -1000L); // already expired
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
}
