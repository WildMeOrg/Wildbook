# Artifact B — Integration Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add the two Wildbook-side endpoints the external scoped-query kernel depends on: (B1) short-lived asymmetric-signed token issuance, and (B2) a live `canUserAccess` check computed from live Collaboration/role objects.

**Architecture:** A pure, unit-testable `JwtService` (RSA keypair load + sign + verify) underpins a thin `AuthToken` servlet (`POST /api/v3/auth/token`) gated by existing Shiro Basic auth that mints a token carrying only identity (uuid + context + iss/aud/jti/exp). A separate `CanUserAccess` servlet (`POST /api/v3/can-user-access`), admin-gated, resolves a target user UUID + encounter IDs and returns the subset that user may view via `Encounter.canUserView`. Both follow the existing manual-servlet + web.xml pattern.

**Tech Stack:** Java 17, Apache Shiro (existing), **jjwt 0.12.6** (new, locked), `java.security` RSA. JUnit5 + Mockito (mockito-inline present); JwtService gets pure unit tests, endpoints get Mockito unit tests.

**Branch/worktree:** `integration-token-canaccess` at `/mnt/c/Wildbook-integration-b` (off `main`). Independent of Artifact A.

**Design source:** `docs/superpowers/specs/2026-05-29-wildbook-acl-prereqs-design.md` (Artifact B section).

---

## Key facts (verified in this worktree)
- Endpoints are manual servlets extending `org.ecocean.api.ApiBase` (only dispatches PATCH; **no** response/error helpers — each endpoint defines its own `writeError`, see `MatchInspection.java:145-153`). URIs are parsed by hand from `request.getRequestURI().split("/")`.
- Registration: explicit `<servlet>` + `<servlet-mapping>` in `web.xml`. **A new path is OPEN unless added to the Shiro `[urls]` section** (`web.xml` ~lines 101-106). `authcBasicWildbook` = Basic auth; `authc` = session; `roles[...]` adds role gating.
- Current user inside an endpoint: `myShepherd.getUser(request)` → `User` or null (uses `request.getUserPrincipal()`).
- `Shepherd.getUserByUUID(String)` (Shepherd.java:1184), `Shepherd.getUser(String username)`, `Shepherd.getEncounter(String)` (Shepherd.java:495).
- `Encounter.canUserView(User, Shepherd)` (Encounter.java:3316) = `user != null && (user.isAdmin(shepherd) || canUserAccess(user, context))`.
- Config: `CommonConfiguration.getProperty(name, context)`. Per-deploy props in the Docker-mounted `commonConfiguration.properties`.
- No JWT lib in pom.xml. Tests: pure Mockito unit (`EncounterApiTest`) or Testcontainers+Jetty+RestAssured integration (`EncounterExportImagesTest`).

## File structure
- **Modify** `pom.xml` — add jjwt 0.12.6 (api/impl/jackson), locked.
- **Create** `src/main/java/org/ecocean/api/auth/JwtService.java` — keypair load (from config), `String sign(claims)`, `Jws<Claims> verify(token)`, helpers; isEnabled() when keys present.
- **Create** `src/main/java/org/ecocean/api/AuthToken.java` — B1 servlet.
- **Create** `src/main/java/org/ecocean/api/CanUserAccess.java` — B2 servlet.
- **Modify** `src/main/webapp/WEB-INF/web.xml` — 2 servlet defs + mappings + 2 Shiro url constraints.
- **Create** `src/test/java/org/ecocean/api/auth/JwtServiceTest.java` — pure unit tests.
- **Create** `src/test/java/org/ecocean/api/AuthTokenTest.java`, `CanUserAccessTest.java` — Mockito unit tests.
- **Create** `docs/superpowers/runbooks/jwt-keypair-setup.md` — keypair generation + config.
- **Modify** `src/test/resources/shiro.ini` — add test auth rules for the two paths.

---

## Task 1: Add jjwt dependency

**Files:** Modify `pom.xml`

- [ ] **Step 1: Add the three jjwt artifacts (locked 0.12.6)**

In `pom.xml` `<dependencies>`, add:
```xml
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
```
(Match the file's existing indentation/version-property conventions; if the project pins versions in `<properties>`, follow that convention with a `jjwt.version` property instead.)

- [ ] **Step 2: Resolve dependencies**

Run: `cd /mnt/c/Wildbook-integration-b && mvn -q -DskipTests dependency:resolve 2>&1 | tail -5; mvn -q -DskipTests compile`
Expected: BUILD SUCCESS (jjwt downloaded; project still compiles).

- [ ] **Step 3: Commit**

```bash
git -C /mnt/c/Wildbook-integration-b add pom.xml
git -C /mnt/c/Wildbook-integration-b commit -m "build: add jjwt 0.12.6 (JWT signing for scoped-access tokens)"
```

---

## Task 2: JwtService (keypair load + sign + verify) — pure unit-tested core

**Files:** Create `src/main/java/org/ecocean/api/auth/JwtService.java`; Test `src/test/java/org/ecocean/api/auth/JwtServiceTest.java`

The JwtService is the security-critical, fully unit-testable core. Keys are RSA. Private key signs (Wildbook = issuer); public key verifies (the external kernel holds the public key; JwtService.verify exists for round-trip testing and any future Wildbook-side verification).

- [ ] **Step 1: Write failing tests (round-trip, expiry, tamper, wrong aud/iss, disabled)**

Create `JwtServiceTest.java`:
```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /mnt/c/Wildbook-integration-b && mvn test -Dtest=JwtServiceTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — `JwtService` does not exist.

- [ ] **Step 3: Implement JwtService**

Create `src/main/java/org/ecocean/api/auth/JwtService.java`:
```java
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
        return Jwts.parser()
            .requireIssuer(issuer)
            .requireAudience(audience)
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token);
    }
}
```
(Note: this uses the jjwt 0.12.x builder API — `audience().add(...).and()`, `Jwts.SIG.RS256`, `parseSignedClaims`. If the resolved jjwt version's API differs, adapt to that version's signatures and report.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /mnt/c/Wildbook-integration-b && mvn test -Dtest=JwtServiceTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/api/auth/JwtService.java src/test/java/org/ecocean/api/auth/JwtServiceTest.java
git -C /mnt/c/Wildbook-integration-b add src/main/java/org/ecocean/api/auth/JwtService.java src/test/java/org/ecocean/api/auth/JwtServiceTest.java
git -C /mnt/c/Wildbook-integration-b commit -m "feat(api): JwtService — RS256 sign/verify for scoped-access tokens"
```

---

## Task 3: AuthToken endpoint (B1)

**Files:** Create `src/main/java/org/ecocean/api/AuthToken.java`; Modify `web.xml`; Test `src/test/java/org/ecocean/api/AuthTokenTest.java`

- [ ] **Step 1: Implement the servlet**

Create `src/main/java/org/ecocean/api/AuthToken.java`:
```java
package org.ecocean.api;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * POST /api/v3/auth/token
 * Gated by existing Shiro auth (authcBasicWildbook). Mints a short-lived
 * RS256 token carrying only the caller's identity (uuid + context). The
 * external scoped-access kernel validates it with the public key.
 */
public class AuthToken extends ApiBase {
    private static final long DEFAULT_TTL_MILLIS = 30L * 60L * 1000L; // 30 min

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.AuthToken.doPost");
        myShepherd.beginDBTransaction();
        try {
            User user = myShepherd.getUser(request);
            if (user == null) {
                writeError(response, 401, "unauthenticated");
                return;
            }
            // SECURITY (Codex High #1): pin the token context SERVER-SIDE. Do NOT
            // sign ServletUtilities.getContext(request) — it honors a caller's
            // ?context=, while Basic auth authenticates only against context0, so a
            // caller-influenced context claim would let a user mint a token for a
            // context they didn't authenticate in. v1 is single-context.
            String tokenContext = org.ecocean.CommonConfiguration.getProperty("jwtContext", context);
            if (!Util.stringExists(tokenContext)) tokenContext = "context0";
            JwtService jwt = JwtService.fromConfig(tokenContext);
            if (!jwt.isEnabled()) {
                writeError(response, 503, "token issuance not configured");
                return;
            }
            long ttl = ttlFromConfig(tokenContext);
            String token = jwt.sign(user.getId(), tokenContext, ttl);
            JSONObject out = new JSONObject();
            out.put("token", token);
            out.put("tokenType", "Bearer");
            out.put("expiresInSeconds", ttl / 1000L);
            response.setStatus(200);
            response.setContentType("application/json");
            response.getWriter().write(out.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            writeError(response, 500, "token issuance failed");
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private long ttlFromConfig(String context) {
        String v = org.ecocean.CommonConfiguration.getProperty("jwtTtlSeconds", context);
        if (Util.stringExists(v)) {
            try {
                long secs = Long.parseLong(v.trim());
                // Codex Low: clamp to a sane range (1 min .. 24 h)
                secs = Math.max(60L, Math.min(secs, 24L * 3600L));
                return secs * 1000L;
            } catch (NumberFormatException ignore) {}
        }
        return DEFAULT_TTL_MILLIS;
    }

    private void writeError(HttpServletResponse response, int code, String message)
        throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.getWriter().write(new JSONObject().put("success", false)
            .put("error", message).toString());
    }
}
```
(Confirm `ServletUtilities.getContext(request)` exists — used by MatchInspection; if the helper signature differs, match the real one. Confirm `User.getId()` returns the UUID.)

- [ ] **Step 2: Register + protect in web.xml**

In `web.xml`, add a `<servlet>` + `<servlet-mapping>` (next to the other `/api/v3` servlets):
```xml
  <servlet>
    <servlet-name>AuthToken</servlet-name>
    <servlet-class>org.ecocean.api.AuthToken</servlet-class>
  </servlet>
  <servlet-mapping>
    <servlet-name>AuthToken</servlet-name>
    <url-pattern>/api/v3/auth/token</url-pattern>
  </servlet-mapping>
```
And in the Shiro `[urls]` section (near line 101-106), add (so it is NOT open):
```
/api/v3/auth/token = authcBasicWildbook
```
(Basic auth so non-browser clients can bootstrap username/password → token. Read the existing `[urls]` entries to match exact filter names available in this web.xml.)

- [ ] **Step 3: Write a Mockito unit test**

Create `src/test/java/org/ecocean/api/AuthTokenTest.java` following the `EncounterApiTest` MockedConstruction idiom: mock `Shepherd` so `getUser(request)` returns null → assert 401 (capture response via a mocked `HttpServletResponse` + `StringWriter`). (Full token-mint path needs keys/config; cover the 401 unauthenticated path and the 503 disabled path here — the happy path is covered by JwtServiceTest + the integration smoke in Task 5. Read EncounterApiTest for the exact mocking setup.)

- [ ] **Step 4: Run test + compile**

Run: `cd /mnt/c/Wildbook-integration-b && mvn test -Dtest=AuthTokenTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 5: Commit** (normalize LF on all touched files first)

```bash
git -C /mnt/c/Wildbook-integration-b add src/main/java/org/ecocean/api/AuthToken.java src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/AuthTokenTest.java
git -C /mnt/c/Wildbook-integration-b commit -m "feat(api): POST /api/v3/auth/token — short-lived scoped-access token issuance"
```

---

## Task 4: CanUserAccess endpoint (B2)

**Files:** Create `src/main/java/org/ecocean/api/CanUserAccess.java`; Modify `web.xml`; Test `src/test/java/org/ecocean/api/CanUserAccessTest.java`

- [ ] **Step 1: Implement the servlet**

Create `src/main/java/org/ecocean/api/CanUserAccess.java`:
```java
package org.ecocean.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * POST /api/v3/can-user-access
 * Admin/service-scoped. Body: {"userUuid": "...", "encounterIds": ["...", ...]}.
 * Returns {"accessible": ["...", ...]} — the subset of encounterIds the target
 * user may VIEW, computed from LIVE Collaboration/role objects (not the indexed
 * viewUsers). Defense-in-depth backstop for the scoped-query kernel.
 */
public class CanUserAccess extends ApiBase {
    private static final int MAX_IDS = 200;
    private static final long MAX_BODY_BYTES = 64L * 1024L; // cap request body (Codex Medium)

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.CanUserAccess.doPost");
        myShepherd.beginDBTransaction();
        try {
            User caller = myShepherd.getUser(request);
            if (caller == null) {
                writeError(response, 401, "unauthenticated");
                return;
            }
            if (!caller.isAdmin(myShepherd)) {
                writeError(response, 403, "admin required");
                return;
            }
            // Codex Medium: validate content-type + cap body size BEFORE reading/parsing.
            String ctype = request.getContentType();
            if (ctype == null || !ctype.toLowerCase().contains("application/json")) {
                writeError(response, 415, "Content-Type must be application/json");
                return;
            }
            if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                writeError(response, 413, "request body too large");
                return;
            }
            JSONObject body = readBody(request);
            if (body == null) { writeError(response, 400, "invalid JSON body"); return; }
            String userUuid = body.optString("userUuid", null);
            JSONArray idsArr = body.optJSONArray("encounterIds");
            if (!Util.stringExists(userUuid) || idsArr == null) {
                writeError(response, 400, "userUuid and encounterIds required");
                return;
            }
            if (idsArr.length() > MAX_IDS) {
                writeError(response, 400, "too many encounterIds (max " + MAX_IDS + ")");
                return;
            }
            User target = myShepherd.getUserByUUID(userUuid);
            JSONArray accessible = new JSONArray();
            // target==null -> empty accessible set (unknown user sees nothing)
            if (target != null) {
                for (int i = 0; i < idsArr.length(); i++) {
                    String encId = idsArr.optString(i, null);
                    if (!Util.stringExists(encId)) continue;
                    Encounter enc = myShepherd.getEncounter(encId);
                    if (enc != null && enc.canUserView(target, myShepherd)) {
                        accessible.put(encId);
                    }
                }
            }
            JSONObject out = new JSONObject();
            out.put("accessible", accessible);
            response.setStatus(200);
            response.setContentType("application/json");
            response.getWriter().write(out.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            writeError(response, 500, "canUserAccess failed");
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private JSONObject readBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = request.getReader();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
                if (sb.length() > MAX_BODY_BYTES) return null; // cap even if Content-Length lied
            }
            return new JSONObject(sb.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int code, String message)
        throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.getWriter().write(new JSONObject().put("success", false)
            .put("error", message).toString());
    }
}
```

- [ ] **Step 2: Register + protect in web.xml**

Add servlet + mapping:
```xml
  <servlet>
    <servlet-name>CanUserAccess</servlet-name>
    <servlet-class>org.ecocean.api.CanUserAccess</servlet-class>
  </servlet>
  <servlet-mapping>
    <servlet-name>CanUserAccess</servlet-name>
    <url-pattern>/api/v3/can-user-access</url-pattern>
  </servlet-mapping>
```
Shiro `[urls]` (admin-gated — read the file to confirm the exact filter name for admin role; pattern is `authcBasicWildbook, roles[admin]`):
```
/api/v3/can-user-access = authcBasicWildbook, roles[admin]
```
(The in-code `caller.isAdmin` check is belt-and-suspenders alongside the role constraint.)

- [ ] **Step 3: Mockito unit tests**

Create `CanUserAccessTest.java`: (a) non-admin caller → 403; (b) admin caller + a target user where one encounter `canUserView`==true and another ==false → `accessible` contains only the true one; (c) unknown target uuid → empty accessible. Mock `Shepherd`, `User` (caller `isAdmin`→true/false; target), `Encounter` (`canUserView`→true/false). Follow EncounterApiTest mocking; capture the written JSON via a mocked response + StringWriter and parse it.

- [ ] **Step 4: Run + compile**

Run: `cd /mnt/c/Wildbook-integration-b && mvn test -Dtest=CanUserAccessTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 5: Commit** (LF-normalize first)

```bash
git -C /mnt/c/Wildbook-integration-b add src/main/java/org/ecocean/api/CanUserAccess.java src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/CanUserAccessTest.java
git -C /mnt/c/Wildbook-integration-b commit -m "feat(api): POST /api/v3/can-user-access — live per-user encounter access check"
```

---

## Task 5: Keypair runbook + test shiro.ini + config docs

**Files:** Create `docs/superpowers/runbooks/jwt-keypair-setup.md`; Modify `src/test/resources/shiro.ini`

- [ ] **Step 1: Write the keypair/config runbook**

Create `docs/superpowers/runbooks/jwt-keypair-setup.md` documenting:
- Generate an RSA-2048 keypair and the Base64 (PKCS8 private / X.509 public) forms, e.g.:
  ```
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt_private.pem
  openssl pkcs8 -topk8 -nocrypt -in jwt_private.pem -outform DER | base64 -w0   # -> jwtPrivateKeyBase64
  openssl rsa -in jwt_private.pem -pubout -outform DER | base64 -w0             # -> jwtPublicKeyBase64
  ```
- The `commonConfiguration.properties` keys: `jwtPrivateKeyBase64`, `jwtPublicKeyBase64`, `jwtIssuer` (default `wildbook`), `jwtAudience` (default `wildbook-scoped-api`), `jwtTtlSeconds` (default 1800, clamped to [60, 86400]), `jwtKeyId` (optional, sets the JWT `kid` header for rotation), `jwtContext` (optional, default `context0` — the single context tokens are pinned to; do NOT derive from the request).
- Wildbook needs the PRIVATE key (to sign); the external kernel is given the PUBLIC key only. If `jwtPrivateKeyBase64` is unset, `/api/v3/auth/token` returns 503.
- **Key rotation:** set a distinct `jwtKeyId` per key. To rotate: deploy the new private key + bump `jwtKeyId`; the kernel keeps BOTH old and new public keys (selected by `kid`) for at least one TTL so in-flight tokens keep verifying, then drops the old one.
- **Secret hygiene (Codex Low):** the private key (and any `.pem`/Base64 form) must NEVER be committed to git or written to logs. If stored as a file, restrict perms (`chmod 600`). The Base64 value lives only in the Docker-mounted `commonConfiguration.properties`. Never log token strings.

- [ ] **Step 2: Add test auth rules**

In `src/test/resources/shiro.ini` `[urls]`, add these — **and they MUST be inserted BEFORE the `/** = anon` catch-all** (Shiro `[urls]` is first-match-wins; rules placed after `/**` are dead, Codex Medium):
```
/api/v3/auth/token = authcBasicWildbook
/api/v3/can-user-access = authcBasicWildbook, roles[admin]
```
Read the existing test shiro.ini to (a) use the exact filter names defined there (if `authcBasicWildbook` isn't defined in the test ini, use the test ini's available auth filter equivalent), and (b) place these lines physically above the `/** = anon` line. After editing, eyeball the ordering to confirm the catch-all is last.

- [ ] **Step 3: Normalize + commit**

```bash
perl -i -pe 's/\r\n/\n/g' docs/superpowers/runbooks/jwt-keypair-setup.md src/test/resources/shiro.ini
git -C /mnt/c/Wildbook-integration-b add docs/superpowers/runbooks/jwt-keypair-setup.md src/test/resources/shiro.ini
git -C /mnt/c/Wildbook-integration-b commit -m "docs(api): JWT keypair setup runbook + test auth rules"
```

---

## Task 6: Verification + final review

- [ ] **Step 1: Unit tests**

Run: `cd /mnt/c/Wildbook-integration-b && mvn test -Dtest=JwtServiceTest,AuthTokenTest,CanUserAccessTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: all pass.

- [ ] **Step 2: Full compile**

Run: `cd /mnt/c/Wildbook-integration-b && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: CRLF check on branch files**

Run: `cd /mnt/c/Wildbook-integration-b && for f in $(git diff --name-only main); do n=$(grep -cP '\r$' "$f" 2>/dev/null||echo 0); [ "$n" != "0" ] && echo "CRLF: $f"; done; echo done`
Expected: only "done".

- [ ] **Step 4: Wiring-assertion test (Codex High #2 — Mockito can't prove the gate)**

Because new paths are OPEN unless matched by a Shiro `[urls]` rule, and the unit tests do not load `web.xml`, add a guard test `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java` that reads `src/main/webapp/WEB-INF/web.xml` and asserts: (a) both servlets + mappings exist for `/api/v3/auth/token` and `/api/v3/can-user-access`; (b) the Shiro `[urls]` section contains a non-`anon` constraint for each, with `roles[admin]` on can-user-access, positioned before any `/** ` catch-all. This is a cheap regression guard against the open-by-default footgun. (The PRIMARY enforcement is the in-code check in each endpoint — B1 returns 401 when `getUser(request)==null`, B2 returns 403 unless `caller.isAdmin` — so the endpoints are self-protecting even if Shiro is misconfigured; this test guards the defense-in-depth layer.)

Run: `cd /mnt/c/Wildbook-integration-b && mvn test -Dtest=EndpointAuthWiringTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 5: Confirm no stray files staged across commits**

Run: `cd /mnt/c/Wildbook-integration-b && git diff --name-only main..HEAD`
Expected: only the intended source/test/doc/pom/web.xml files; NO logs/ or runtime artifacts.

---

## Notes for the implementer
- jjwt 0.12.x API specifics (builder `.audience().add().and()`, `Jwts.SIG.RS256`, `.parseSignedClaims`, `.verifyWith`) — if the resolved version's API differs, adapt and report which version/API you used.
- `writeError` is defined per-endpoint (no ApiBase helper) — matches `MatchInspection`.
- Always LF-normalize after each edit (`perl -i -pe 's/\r\n/\n/g'`); never stage `logs/application.json` — add only explicit paths.
- Do NOT log token values or private keys anywhere.
- Security stance: the token carries NO admin/role claims; the kernel resolves privileges fresh. B2 requires the CALLER to be admin and computes access for the TARGET user — never trust a caller-supplied "isAdmin".

## Open questions for review
1. jjwt 0.12.6 vs an alternative (nimbus-jose-jwt) — any packaging/transitive-dep concern in this WAR? (Dependabot/system-test per repo policy.)
2. B2 caller gating: full `admin` role for v1 vs a dedicated narrow service role (`rest`?) — admin is simplest now; dedicated service role is a follow-up.
3. Public-key distribution to the kernel: out-of-band config for v1 vs a `GET /api/v3/auth/jwks` endpoint (follow-up).
4. Key storage: Base64-in-properties (this plan) vs file-path vs a secrets manager — properties is simplest for the Docker-mounted config; revisit for production secret hygiene.
