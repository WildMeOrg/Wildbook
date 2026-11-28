package org.ecocean.auth;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceKeyCredentialTest {

    @Test void issueRoundTrips() {
        ServiceKeyCredential.Issued issued = ServiceKeyCredential.issue();
        Optional<String[]> parsed = ServiceKeyCredential.parse(issued.plaintext);
        assertTrue(parsed.isPresent(), "issued credential should parse");
        assertEquals(issued.keyId, parsed.get()[0], "key id round-trips");
        assertTrue(ServiceKeyCredential.matches(parsed.get()[1], issued.secretHash),
            "secret should match stored hash");
        assertEquals(64, issued.secretHash.length(), "hash is 64 hex chars");
        assertEquals(issued.secretHash.toLowerCase(), issued.secretHash, "hash is lowercase");
    }

    @Test void plaintextShape() {
        ServiceKeyCredential.Issued a = ServiceKeyCredential.issue();
        ServiceKeyCredential.Issued b = ServiceKeyCredential.issue();
        assertTrue(a.plaintext.startsWith("wbsvc_"), "prefix");
        String secret = a.plaintext.substring(a.plaintext.indexOf('.') + 1);
        assertEquals(32, Base64.getUrlDecoder().decode(secret).length, "secret is 32 bytes");
        assertFalse(secret.contains("="), "no padding");
        assertNotEquals(a.plaintext, b.plaintext, "two issues differ");
    }

    @Test void malformedCredentialsAre401() {
        String id = UUID.randomUUID().toString();
        String[] bad = { null, "", "wbsvc_", "wbsvc_abc", "wbsvc_.x", "wbsvc_" + id + ".",
            "wbsvc_" + id + ".!!!", "Bearer x", "wbsvc_" + id + ".AAAA" };
        for (String s : bad) {
            assertFalse(ServiceKeyCredential.parse(s).isPresent(), "should reject: " + s);
        }
    }

    @Test void wrongSecretDoesNotMatch() {
        ServiceKeyCredential.Issued a = ServiceKeyCredential.issue();
        ServiceKeyCredential.Issued b = ServiceKeyCredential.issue();
        String otherSecret = ServiceKeyCredential.parse(b.plaintext).get()[1];
        assertFalse(ServiceKeyCredential.matches(otherSecret, a.secretHash), "wrong secret");
        assertFalse(ServiceKeyCredential.matches(null, a.secretHash), "null secret");
        assertFalse(ServiceKeyCredential.matches(otherSecret, null), "null hash");
    }
}
