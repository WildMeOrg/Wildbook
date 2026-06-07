# Token-Authenticated Scoped Search (Artifact D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Java the hard permission boundary for user-facing scoped encounter search — an external client presents a Bearer JWT (issued by Artifact B) to `/api/v3/search/encounter`, and Wildbook verifies it, resolves the user, and runs a permission-filtered search whose totals, pagination, and hits are all scoped to what that user may see.

**Architecture:** A new Shiro `OncePerRequestFilter` (`WildbookTokenAuthenticationFilter`) verifies the JWT (reusing B's `JwtService`), rejects context drift, resolves the user by UUID, and **wraps the request** so `Shepherd.getUser(request)` returns the token user — **without** calling `subject.login` (no session is minted, so a token can never be parlayed into a write). `SearchApi` then, on the token path, enforces encounter-only index gating + stored-query ownership and **injects a real ACL pre-filter** (`publiclyReadable OR submitterUserId==uuid OR viewUsers==uuid`) into the OpenSearch query *before execution*. Admins get no pre-filter. The session/browser path is untouched.

**Tech Stack:** Java 17, Apache Shiro (`org.apache.shiro.web.servlet.OncePerRequestFilter`), jjwt 0.12.6 (via B's `JwtService`), `org.json` (JSONObject/JSONArray), DataNucleus JDO (`Shepherd`), JUnit 5 + Mockito 5 (unit), embedded Jetty + Testcontainers OpenSearch (integration). Build: Maven (`mvn test`).

**Branch / worktree:** `token-auth-scoped-search` at `/mnt/c/Wildbook-token-auth` (off the A+B integration base; B's `JwtService`/`AuthToken` are present). All paths below are relative to that worktree.

**Spec:** `docs/superpowers/specs/2026-06-06-token-auth-scoped-search-design.md` (Codex-converged, 2 rounds).

---

## File Structure

**Create:**
- `src/main/java/org/ecocean/security/WildbookTokenAuthenticationFilter.java` — the Shiro filter (verify → context checks → wrap request → mark token-auth → forward). The only new auth code.
- `src/test/java/org/ecocean/security/WildbookTokenAuthenticationFilterTest.java` — filter unit tests (Mockito).
- `src/test/java/org/ecocean/OpenSearchAclFilterTest.java` — pure unit tests for the ACL-filter builder.

**Modify:**
- `src/main/java/org/ecocean/api/auth/JwtService.java` — add a tiny `canVerify()` accessor (public-key presence).
- `src/main/java/org/ecocean/OpenSearch.java` — add `applyEncounterAclFilter(query, userId)` (pure query rewriter).
- `src/main/java/org/ecocean/api/SearchApi.java` — token-path enforcement: fail-closed-on-unmarked-Bearer, encounter-only index gate, stored-query owner check, ACL injection after sanitize.
- `src/test/java/org/ecocean/api/SearchApiTokenAuthTest.java` — **create** (new file) for SearchApi token-path unit tests.
- `src/main/webapp/WEB-INF/web.xml` — register the filter in Shiro `[main]` and map `/api/v3/search/**` to it in `[urls]`.
- `src/test/java/org/ecocean/api/EncounterExportImagesTest.java` (or a sibling) — add a token-scoped enforcement integration test (real OpenSearch).
- `src/test/.../EndpointAuthWiringTest.java` (B's wiring guard, if present) — assert the new search rule.

**Key facts locked in (verified against current source on this branch):**
- `SearchApi` is mapped to `/api/v3/search/*` in `web.xml`; `doGet` delegates to `doPost`.
- `SearchApi:31` resolves the user via `myShepherd.getUser(request)`; `SearchApi:34` self-401s when that user is null.
- `Shepherd.getUser(request)` → `getUsername(request)` → `request.getUserPrincipal().toString()` → `getUser(username)` (DB lookup **by username**). So the wrapper's `getUserPrincipal().toString()` must return the **username**, not the UUID.
- `User.getId()` and `User.getUUID()` both return the `uuid` field (`User.java:400-401`) — interchangeable for the owner/ACL comparisons.
- `OpenSearch.querySanitize` (`OpenSearch:918-923`) is a **no-op**; `sanitizeDoc` (`OpenSearch:926-970`) post-sanitizes encounter hits but leaks metadata as `access:"none"` and does NOT scope totals — hence D injects a real pre-filter instead of relying on it.
- `SearchApi` query lifecycle: parse/`queryLoad` → (stored) re-read `indexName` + `queryScrubStored` (`SearchApi:81-82`) → `querySanitize` (`:84`, no-op) → `os.queryPit(indexName, query, ...)` (`:91-92`). `queryPit` mutates the passed `query` JSONObject in place (adds `from`/`size`/`sort`/`pit`) and POSTs it.
- `Encounter.opensearchAccess` (`Encounter.java:4753-4765`) grants on: `publiclyReadable` OR `submitterUserId==user.getId()` OR `user.isAdmin()` OR `viewUsers[]` contains `user.getId()`. `editUsers` is NOT consulted; orgAdmin access is carried through `viewUsers`. → the 3-term pre-filter (+ admin bypass) is complete.
- `JwtService.verify(token)` returns `Jws<Claims>`, enforces iss/aud/exp + pins RS256, throws `JwtException` on bad token and `IllegalStateException` if no public key. It does **not** check the `context` claim — D's filter does. Read claims via `jws.getPayload()` → `c.getSubject()`, `c.get("context", String.class)`.
- Shiro is configured via embedded `IniShiroFilter` in `web.xml`; existing filter `authcBasicWildbook = org.ecocean.security.WildbookBasicHttpAuthenticationFilter` is declared in `[main]`. `/api/v3/search/*` currently has **no** `[urls]` rule.

---

### Task 1: ACL-filter query builder (`OpenSearch.applyEncounterAclFilter`)

A pure function that wraps a search query's top-level `query` clause in a `bool` whose `filter` is the encounter ACL predicate, so OpenSearch scopes totals + pagination + hits. No I/O — fully unit-testable.

**Files:**
- Modify: `src/main/java/org/ecocean/OpenSearch.java`
- Test: `src/test/java/org/ecocean/OpenSearchAclFilterTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/OpenSearchAclFilterTest.java`:

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OpenSearchAclFilterTest {

    @Test void wrapsInnerQueryWithAclFilter() throws Exception {
        JSONObject original = new JSONObject("{\"query\":{\"match\":{\"name\":\"x\"}}}");
        JSONObject out = OpenSearch.applyEncounterAclFilter(original, "user-uuid-1");

        JSONObject bool = out.getJSONObject("query").getJSONObject("bool");
        // original inner query preserved as a must clause
        JSONArray must = bool.getJSONArray("must");
        assertEquals(1, must.length(), "one must clause (the original query)");
        assertTrue(must.getJSONObject(0).has("match"), "original match preserved");

        // ACL is a filter clause: should[publiclyReadable, submitterUserId, viewUsers], msm=1
        JSONArray filter = bool.getJSONArray("filter");
        JSONObject aclBool = filter.getJSONObject(0).getJSONObject("bool");
        assertEquals(1, aclBool.getInt("minimum_should_match"), "minimum_should_match=1");
        JSONArray should = aclBool.getJSONArray("should");
        assertEquals(3, should.length(), "three should clauses");
        assertTrue(should.getJSONObject(0).getJSONObject("term").getBoolean("publiclyReadable"),
            "publiclyReadable term");
        assertEquals("user-uuid-1",
            should.getJSONObject(1).getJSONObject("term").getString("submitterUserId"),
            "submitterUserId term = uuid");
        assertEquals("user-uuid-1",
            should.getJSONObject(2).getJSONObject("term").getString("viewUsers"),
            "viewUsers term = uuid");
    }

    @Test void filterOnlyWhenNoInnerQuery() throws Exception {
        JSONObject original = new JSONObject("{}"); // no "query" field
        JSONObject out = OpenSearch.applyEncounterAclFilter(original, "user-uuid-1");
        JSONObject bool = out.getJSONObject("query").getJSONObject("bool");
        assertFalse(bool.has("must"), "no must clause when there was no inner query");
        assertTrue(bool.has("filter"), "ACL filter still applied (fail-closed scope)");
    }

    @Test void rejectsNullUser() {
        JSONObject original = new JSONObject("{\"query\":{\"match_all\":{}}}");
        assertThrows(java.io.IOException.class,
            () -> OpenSearch.applyEncounterAclFilter(original, null),
            "null/empty userId must throw (fail closed)");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=OpenSearchAclFilterTest`
Expected: FAIL — `applyEncounterAclFilter` does not exist (compile error / method not found).

- [ ] **Step 3: Add the implementation to `OpenSearch.java`**

Insert this method immediately after `querySanitize` (after `OpenSearch.java:923`). Use the existing `org.json` imports already in the file:

```java
    /**
     * Wrap a search query's top-level "query" clause in a bool whose filter enforces the
     * encounter ACL (mirrors Encounter.opensearchAccess for a non-admin user). Applied on the
     * token-authenticated path BEFORE execution so totals, pagination, and hits are all scoped.
     * Admins are not passed through here (caller skips the call for admins).
     *
     * Decision (documented, safe): a request with no inner "query" yields a filter-only bool,
     * i.e. "all encounters this user may see" — still fully scoped, never a bypass. A truly
     * malformed (non-JSON) body fails earlier in ServletUtilities.jsonFromHttpServletRequest,
     * before reaching this method, so the spec's "fail closed on malformed" is satisfied upstream.
     */
    public static JSONObject applyEncounterAclFilter(JSONObject query, String userId)
    throws IOException {
        if ((query == null) || !Util.stringExists(userId))
            throw new IOException("applyEncounterAclFilter: null query or userId");
        JSONArray should = new JSONArray();
        should.put(new JSONObject().put("term", new JSONObject().put("publiclyReadable", true)));
        should.put(new JSONObject().put("term", new JSONObject().put("submitterUserId", userId)));
        should.put(new JSONObject().put("term", new JSONObject().put("viewUsers", userId)));
        JSONObject aclBool = new JSONObject();
        aclBool.put("should", should);
        aclBool.put("minimum_should_match", 1);
        JSONObject acl = new JSONObject().put("bool", aclBool);

        JSONObject wrapBool = new JSONObject();
        JSONObject inner = query.optJSONObject("query");
        if (inner != null) {
            JSONArray must = new JSONArray();
            must.put(inner);
            wrapBool.put("must", must);
        }
        wrapBool.put("filter", new JSONArray().put(acl));

        JSONObject out = new JSONObject(query.toString()); // shallow copy via re-parse
        out.put("query", new JSONObject().put("bool", wrapBool));
        return out;
    }
```

Confirm `org.json.JSONArray` is imported at the top of `OpenSearch.java` (it is used elsewhere in the file). `Util.stringExists` is already used throughout `OpenSearch.java`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=OpenSearchAclFilterTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Normalize line endings and commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchAclFilterTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchAclFilterTest.java
git commit -m "feat(search): add encounter ACL pre-filter builder for token-scoped search"
```

---

### Task 2: `JwtService.canVerify()` accessor

The filter must distinguish "bad token → 401" from "token auth not configured on this server → 503". `verify()` throws `IllegalStateException` when there's no public key; expose a clean predicate instead of catching that.

**Files:**
- Modify: `src/main/java/org/ecocean/api/auth/JwtService.java`
- Test: `src/test/java/org/ecocean/api/auth/JwtServiceTest.java` (existing)

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/org/ecocean/api/auth/JwtServiceTest.java` (mirror the existing key-setup helper `serviceWithFreshKeys()` already in that file):

```java
    @Test void canVerify_trueWhenPublicKeyPresent() throws Exception {
        JwtService svc = serviceWithFreshKeys();
        assertTrue(svc.canVerify(), "service built with a keypair can verify");
    }

    @Test void canVerify_falseWhenNoKeys() {
        JwtService svc = JwtService.fromBase64Keys(null, null, "wildbook", "wildbook-scoped-api");
        assertFalse(svc.canVerify(), "service with no public key cannot verify");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=JwtServiceTest#canVerify_trueWhenPublicKeyPresent+canVerify_falseWhenNoKeys`
Expected: FAIL — `canVerify` not defined.

- [ ] **Step 3: Add the accessor**

In `src/main/java/org/ecocean/api/auth/JwtService.java`, immediately after `isEnabled()` (after line 92):

```java
    public boolean canVerify() {
        return publicKey != null;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=JwtServiceTest`
Expected: PASS (all existing JwtService tests + the 2 new ones).

- [ ] **Step 5: Normalize and commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/api/auth/JwtService.java src/test/java/org/ecocean/api/auth/JwtServiceTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/api/auth/JwtService.java src/test/java/org/ecocean/api/auth/JwtServiceTest.java
git commit -m "feat(auth): add JwtService.canVerify() (public-key presence)"
```

---

### Task 3: `WildbookTokenAuthenticationFilter`

A Shiro `OncePerRequestFilter` that, on a Bearer-bearing request: verifies the JWT, enforces the context claim and rejects request-context drift, resolves the user by UUID, wraps the request so `getUserPrincipal().toString()` returns the **username**, marks the request token-authenticated, and forwards. No Bearer → pass through unchanged (session/anon path intact). Bad token → 401. Not configured → 503. The filter exposes protected seams (`expectedContext`, `requestContext`, `jwtService`, `lookupUsername`) so unit tests inject fakes without static mocking.

**Files:**
- Create: `src/main/java/org/ecocean/security/WildbookTokenAuthenticationFilter.java`
- Test: `src/test/java/org/ecocean/security/WildbookTokenAuthenticationFilterTest.java` (create)

- [ ] **Step 1: Write the filter (no test-first here — the test in Step 2 needs the protected seams to exist; write the class, then the test drives behavior)**

Create `src/main/java/org/ecocean/security/WildbookTokenAuthenticationFilter.java`:

```java
package org.ecocean.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import java.io.IOException;
import java.security.Principal;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.web.servlet.OncePerRequestFilter;
import org.ecocean.CommonConfiguration;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Token-authenticated read path for /api/v3/search/* (encounter index only; gated in SearchApi).
 *
 * Verifies a B-issued RS256 JWT, enforces the context claim + rejects request-context drift,
 * resolves the user by UUID, and WRAPS the request so Shepherd.getUser(request) returns the
 * token user. It deliberately does NOT call subject.login: Wildbook is session-based, and a
 * login on a bearer request could mint a session reusable on write endpoints. No Bearer => the
 * chain proceeds unchanged (browser/session path intact).
 */
public class WildbookTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_AUTH_ATTR = "org.ecocean.tokenAuth";

    @Override
    protected void doFilterInternal(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String authz = request.getHeader("Authorization");
        // HTTP auth scheme tokens are case-insensitive: accept "Bearer"/"bearer"/etc.
        if ((authz == null) || !authz.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response); // no token: leave session/anon path intact
            return;
        }
        // method allowlist (defence in depth): only safe read methods reach search
        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            deny(response, 405, "method not allowed");
            return;
        }
        String token = authz.substring(7).trim();
        String expected = expectedContext();

        JwtService jwt = jwtService(expected);
        if (!jwt.canVerify()) {
            deny(response, 503, "token auth not configured");
            return;
        }
        Claims claims;
        try {
            Jws<Claims> jws = jwt.verify(token);
            claims = jws.getPayload();
        } catch (JwtException | IllegalStateException ex) {
            deny(response, 401, "invalid token");
            return;
        }
        // context claim must equal server context, AND request-resolved context must not drift
        String tokenContext = claims.get("context", String.class);
        String reqContext = requestContext(request);
        if (!expected.equals(tokenContext) || !expected.equals(reqContext)) {
            deny(response, 401, "context mismatch");
            return;
        }
        String uuid = claims.getSubject();
        if (!Util.stringExists(uuid)) {
            deny(response, 401, "invalid token subject");
            return;
        }
        final String username = lookupUsername(expected, uuid);
        if (username == null) {
            deny(response, 401, "unknown user");
            return;
        }
        // wrap so getUser(request) -> getUsername(request) -> getUserPrincipal().toString() == username
        final Principal principal = new Principal() {
            public String getName() {
                return username;
            }

            public String toString() {
                return username;
            }
        };
        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() {
                return principal;
            }

            @Override public String getRemoteUser() {
                return username;
            }
        };
        wrapped.setAttribute(TOKEN_AUTH_ATTR, Boolean.TRUE);
        chain.doFilter(wrapped, response);
    }

    // ----- protected seams (overridden in unit tests) -----

    protected String expectedContext() {
        String c = CommonConfiguration.getProperty("jwtContext", "context0");
        return Util.stringExists(c) ? c : "context0";
    }

    protected String requestContext(HttpServletRequest request) {
        return ServletUtilities.getContext(request);
    }

    protected JwtService jwtService(String context) {
        return JwtService.fromConfig(context);
    }

    protected String lookupUsername(String context, String uuid) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("WildbookTokenAuthenticationFilter");
        myShepherd.beginDBTransaction();
        try {
            User user = myShepherd.getUserByUUID(uuid);
            return (user != null) ? user.getUsername() : null;
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private void deny(HttpServletResponse response, int status, String message)
    throws IOException {
        response.setStatus(status);
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject err = new JSONObject();
        err.put("success", false);
        err.put("error", message);
        response.getWriter().write(err.toString());
        response.getWriter().close();
    }
}
```

- [ ] **Step 2: Write the failing unit test**

Create `src/test/java/org/ecocean/security/WildbookTokenAuthenticationFilterTest.java`. It subclasses the filter to inject a real `JwtService` built from fresh test keys (so verification is genuine) and a canned username, and uses Mockito mocks for request/response/chain:

```java
package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.util.Base64;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.api.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WildbookTokenAuthenticationFilterTest {

    private JwtService realService; // genuine RS256 keypair; sign + verify both work
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter out;

    /** Filter with seams overridden: fixed context, real JwtService, canned username. */
    private WildbookTokenAuthenticationFilter filterFor(String requestContext,
        String resolvedUsername) {
        return new WildbookTokenAuthenticationFilter() {
            @Override protected String expectedContext() {
                return "context0";
            }
            @Override protected String requestContext(HttpServletRequest r) {
                return requestContext;
            }
            @Override protected JwtService jwtService(String context) {
                return realService;
            }
            @Override protected String lookupUsername(String context, String uuid) {
                return resolvedUsername;
            }
        };
    }

    @BeforeEach void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        realService = JwtService.fromBase64Keys(priv, pub, "wildbook", "wildbook-scoped-api");

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
    }

    @Test void noBearer_passesThroughUnchanged() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response); // original request, untouched
        verify(response, never()).setStatus(anyInt());
    }

    @Test void validBearer_wrapsRequestWithUsernamePrincipal() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");

        filterFor("context0", "alice").doFilterInternal(request, response, chain);

        ArgumentCaptor<javax.servlet.ServletRequest> cap =
            ArgumentCaptor.forClass(javax.servlet.ServletRequest.class);
        verify(chain).doFilter(cap.capture(), eq(response));
        HttpServletRequest wrapped = (HttpServletRequest) cap.getValue();
        Principal p = wrapped.getUserPrincipal();
        assertEquals("alice", p.toString(), "principal.toString() must yield the username");
        assertEquals(Boolean.TRUE, wrapped.getAttribute(
            WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR), "token-auth marker set");
    }

    @Test void expiredToken_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", -1_000L); // already expired
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void wrongContextClaim_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "contextX", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void requestContextDrift_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        // token says context0, but the resolved request context is context1 (e.g. ?context=, cookie, host)
        filterFor("context1", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void unknownUser_returns401() throws Exception {
        String token = realService.sign("ghost-uuid", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", null).doFilterInternal(request, response, chain); // lookup -> null
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void disallowedMethod_returns405() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("DELETE");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(405);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void tamperedToken_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L) + "tamper";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void lowercaseBearerScheme_stillAuthenticates() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("bearer " + token); // lowercase scheme
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(chain).doFilter(any(), eq(response)); // wrapped + forwarded, not treated as no-token
        verify(response, never()).setStatus(anyInt());
    }

    @Test void notConfigured_returns503() throws Exception {
        JwtService noKeys = JwtService.fromBase64Keys(null, null, "wildbook", "wildbook-scoped-api");
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        WildbookTokenAuthenticationFilter f = new WildbookTokenAuthenticationFilter() {
            @Override protected String expectedContext() {
                return "context0";
            }
            @Override protected String requestContext(HttpServletRequest r) {
                return "context0";
            }
            @Override protected JwtService jwtService(String context) {
                return noKeys; // canVerify() == false
            }
            @Override protected String lookupUsername(String context, String uuid) {
                return "alice";
            }
        };
        f.doFilterInternal(request, response, chain);
        verify(response).setStatus(503);
        verify(chain, never()).doFilter(any(), any());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails (then passes)**

Run: `mvn test -Dtest=WildbookTokenAuthenticationFilterTest`
Expected first run: the test compiles against the Step-1 class. If Step 1 was committed, this should PASS directly. If any assertion fails, fix the filter (not the test) until all tests pass.
Expected: PASS (10 tests).

> Note: `doFilterInternal` is `protected`; the test is in the same package (`org.ecocean.security`), so it can call it directly.

- [ ] **Step 4: Normalize and commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/security/WildbookTokenAuthenticationFilter.java src/test/java/org/ecocean/security/WildbookTokenAuthenticationFilterTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/security/WildbookTokenAuthenticationFilter.java src/test/java/org/ecocean/security/WildbookTokenAuthenticationFilterTest.java
git commit -m "feat(auth): add WildbookTokenAuthenticationFilter (verify JWT, wrap request, no session)"
```

---

### Task 4: `SearchApi` token-path enforcement

On the token path (request marked by the filter), `SearchApi` must: (a) fail closed if a Bearer header arrived without the marker (filter misconfig); (b) gate to the `encounter` index only — for both direct and stored-query paths; (c) require stored-query ownership (admin bypasses); (d) inject the ACL pre-filter after sanitize for non-admins. The session path is untouched.

**Files:**
- Modify: `src/main/java/org/ecocean/api/SearchApi.java`
- Test: `src/test/java/org/ecocean/api/SearchApiTokenAuthTest.java` (create)

- [ ] **Step 1: Read the current `doPost` ladder**

Open `src/main/java/org/ecocean/api/SearchApi.java` and confirm the structure around lines 31–92 matches the facts in the File Structure section (user resolution at :31, 401 at :34, index parse at :43, `queryLoad` at :48, the guard ladder at :49–59, the execute block from :60, `querySanitize` at :84, `queryPit` at :91). The edits below target those exact points.

- [ ] **Step 2: Add the token marker + fail-closed-unmarked guard + index/owner guards**

Edit `SearchApi.java`. First, just after the user is resolved and the `JSONObject res = new JSONObject();` line (around `:32`), add the marker read:

```java
        User currentUser = myShepherd.getUser(request);
        JSONObject res = new JSONObject();
        boolean tokenAuth = Boolean.TRUE.equals(
            request.getAttribute(org.ecocean.security.WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR));
        String authzHeader = request.getHeader("Authorization");
        // case-insensitive scheme match (matches the filter); avoids fall-through on "bearer ..."
        boolean bearerPresent = (authzHeader != null)
            && authzHeader.regionMatches(true, 0, "Bearer ", 0, 7);
```

Then change the top-level auth branch (currently `if ((currentUser == null) || (currentUser.getId() == null)) { 401 } else { ... }` at `:33`) to fail closed when a Bearer arrived unmarked:

```java
        if ((currentUser == null) || (currentUser.getId() == null)) {
            response.setStatus(401);
            res.put("error", 401);
        } else if (bearerPresent && !tokenAuth) {
            // a Bearer reached SearchApi without the token filter marking it -> filter misconfig
            response.setStatus(401);
            res.put("error", "token auth misconfiguration");
        } else {
```

Next, inside the `else` block, add the token-path guards as new `else if` branches in the existing guard ladder. **Order matters (Codex):** the method allowlist must come **first** — before the invalid-id (404), index (403), and owner (403) checks — so a wrong-method request returns 405 and never leaks via a 403/404 whether a stored query exists or who owns it. Insert these branches **immediately after** the top-level `if/else if (bearerPresent && !tokenAuth)` opening and the existing `(searchQueryId != null) && (query == null)` invalid-id branch is *re-sequenced after* the method gates. Concretely, replace the head of the guard ladder (the branches from the invalid-id 404 down to the existing 405) so it reads:

```java
            // --- token method allowlist FIRST (no existence/ownership leak via 403/404) ---
            } else if (tokenAuth && (searchQueryId != null)
                && !"GET".equals(request.getMethod())) {
                // stored-query replay is GET-only on the token path
                response.setStatus(405);
                res.put("error", "method not allowed");
            } else if (tokenAuth && (searchQueryId == null)
                && !"POST".equals(request.getMethod())) {
                // direct index search is POST-only on the token path
                response.setStatus(405);
                res.put("error", "method not allowed");
            // --- existing validity checks ---
            } else if ((searchQueryId != null) && (query == null)) {
                response.setStatus(404);
                res.put("error", "invalid searchQueryId " + searchQueryId);
            } else if ((searchQueryId == null) && !OpenSearch.isValidIndexName(indexName)) {
                response.setStatus(404);
                res.put("error", "unknown index");
            } else if ("annotation".equals(indexName) && !currentUser.isAdmin(myShepherd)) {
                response.setStatus(403);
                res.put("error", 403);
            // --- token encounter-only index gate + stored-query owner check ---
            } else if (tokenAuth && !"encounter".equals(
                (searchQueryId != null) ? (query != null ? query.optString("indexName", null) : null)
                                        : indexName)) {
                // covers stored queries whose real index is read from the stored doc, not the URL
                response.setStatus(403);
                res.put("error", "token search is limited to the encounter index");
            } else if (tokenAuth && (searchQueryId != null) && (query != null)
                && !currentUser.isAdmin(myShepherd)
                && !currentUser.getId().equals(query.optString("creator", null))) {
                // replaying someone else's stored query is not allowed (admin bypasses)
                response.setStatus(403);
                res.put("error", "not the owner of this stored query");
            } else if ((query == null) && !"POST".equals(request.getMethod())) {
                response.setStatus(405);
                res.put("error", "method not allowed");
            } else {
                // ... existing execute block (query parse/store/scrub, sanitize, ACL inject, queryPit) ...
            }
```

This re-sequences the *existing* invalid-id / unknown-index / annotation-admin / 405 branches (do not duplicate them elsewhere) and inserts the four new token branches in the shown order. The method gates fire first; the index/owner gates fire before execution; the execute block is unchanged except for Step 3's ACL injection.

- [ ] **Step 3: Inject the ACL pre-filter after sanitize**

Still in `SearchApi.java`, find the `querySanitize` call (`:84`):

```java
                query = OpenSearch.querySanitize(query, currentUser, myShepherd);
```

Immediately after it, add:

```java
                if (tokenAuth && !currentUser.isAdmin(myShepherd)) {
                    // Java is the hard boundary: scope totals + pagination + hits before execution
                    query = OpenSearch.applyEncounterAclFilter(query, currentUser.getId());
                }
```

This runs after `queryScrubStored` (so stored queries are scoped too) and before `os.queryPit(...)` — the only place that scopes totals.

- [ ] **Step 4: Write the failing unit test**

Create `src/test/java/org/ecocean/api/SearchApiTokenAuthTest.java`. It uses the repo's dominant pattern (Mockito mocks + `mockConstruction<Shepherd>`), and `mockConstruction<OpenSearch>` to capture the query handed to `queryPit` so the ACL injection is asserted without a live cluster:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.OpenSearch;
import org.ecocean.User;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class SearchApiTokenAuthTest {

    HttpServletRequest request;
    HttpServletResponse response;
    StringWriter out;
    // getContext() on a Mockito request would NPE (reads serverName/cookies/context props);
    // stub it for ALL tests. jsonFromHttpServletRequest is stubbed per-test as needed.
    MockedStatic<ServletUtilities> su;

    @BeforeEach void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(request.getServletContext()).thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter(anyString())).thenReturn(null);
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(Boolean.TRUE); // default: token request (override per-test where needed)
        su = mockStatic(ServletUtilities.class);
        su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
        su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
            .thenReturn(new JSONObject("{\"query\":{\"match_all\":{}}}"));
    }

    @AfterEach void tearDown() {
        su.close();
    }

    private User mockUser(String id, boolean admin) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getUUID()).thenReturn(id); // queryStore reads getUUID(); == getId() in source
        return u;
    }

    /** Shepherd mock with the standard tx stubs + a resolved user. */
    private MockedConstruction<Shepherd> shepherdReturning(User user, boolean admin) {
        return mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(user.isAdmin(m)).thenReturn(admin);
        });
    }

    private static final JSONObject EMPTY_HITS =
        new JSONObject("{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}");

    @Test void tokenRequest_nonEncounterIndex_returns403() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/individual");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
    }

    @Test void bearerWithoutMarker_returns401() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null); // filter did NOT mark it
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(401);
    }

    @Test void lowercaseBearerWithoutMarker_returns401() throws Exception {
        // case-insensitive bearerPresent: "bearer ..." with no marker must still fail closed
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("bearer x");
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null);
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(401);
    }

    @Test void tokenEncounterSearch_injectsAclFilterBeforeExecution() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        JSONObject bool = q.getJSONObject("query").getJSONObject("bool");
                        assertTrue(bool.has("filter"), "ACL filter injected before queryPit");
                        return EMPTY_HITS;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    @Test void adminTokenEncounterSearch_doesNotInjectAclFilter() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("admin1", true);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, true);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        JSONObject inner = q.getJSONObject("query");
                        assertFalse(inner.has("bool") && inner.getJSONObject("bool").has("filter"),
                            "admin token must NOT have an injected ACL filter");
                        return EMPTY_HITS;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    // ---- stored-query gating (token path) ----

    /** Stub OpenSearch.queryLoad to return a stored-query doc (with indexName + creator). */
    private MockedStatic<OpenSearch> storedQuery(String indexName, String creator) {
        MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class);
        osStatic.when(() -> OpenSearch.queryLoad(anyString())).thenReturn(
            new JSONObject().put("indexName", indexName).put("creator", creator)
                .put("query", new JSONObject().put("match_all", new JSONObject())));
        osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
        return osStatic;
    }

    @Test void storedQuery_otherOwner_returns403() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "someoneElse")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
    }

    @Test void storedQuery_nonEncounterIndex_returns403() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("individual", "u1")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403); // own query, but wrong index
    }

    @Test void storedQuery_postMethod_returns405() throws Exception {
        // token stored-query replay is GET-only
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "u1")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(405);
    }

    @Test void storedQuery_postOtherOwner_is405NotOwnership403() throws Exception {
        // method gate fires FIRST: wrong method must not leak ownership via a 403
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "someoneElse")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(405);
        verify(response, never()).setStatus(403);
    }

    @Test void directIndex_getMethod_returns405() throws Exception {
        // token direct index search is POST-only; a GET to /encounter must be 405
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(405);
    }

    @Test void storedQuery_ownEncounter_succeeds() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "u1");
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        assertTrue(q.getJSONObject("query").getJSONObject("bool").has("filter"),
                            "own stored encounter query is ACL-scoped before execution");
                        return EMPTY_HITS;
                    });
            })) {
            // queryScrubStored is static too; stub it to extract the inner query
            osStatic.when(() -> OpenSearch.queryScrubStored(any())).thenAnswer(inv -> {
                JSONObject stored = inv.getArgument(0);
                return new JSONObject().put("query", stored.optJSONObject("query"));
            });
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.applyEncounterAclFilter(any(), anyString()))
                .thenCallRealMethod();
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }
}
```

> Note on combining `mockStatic(OpenSearch)` + `mockConstruction(OpenSearch)`: Mockito supports both on one class concurrently (static-method mock vs constructed-instance mock are independent). In `storedQuery_ownEncounter_succeeds`, the static mock must explicitly stub `queryScrubStored`/`querySanitize`/`applyEncounterAclFilter` (they're static and otherwise return null/Mockito-default when the class is statically mocked). For the direct-query tests (no `mockStatic(OpenSearch)`), `queryStore`/`querySanitize`/`applyEncounterAclFilter` run for real — `queryStore` writes to `/tmp` (writable in the test env) and `applyEncounterAclFilter` is the real builder, which is what the assertion captures.

- [ ] **Step 5: Run the test**

Run: `mvn test -Dtest=SearchApiTokenAuthTest`
Expected: PASS (11 tests: direct non-encounter 403, unmarked Bearer 401, lowercase-Bearer-unmarked 401, ACL injection, admin no-injection, stored other-owner GET 403, stored non-encounter GET 403, stored POST 405, stored POST other-owner 405-not-403, direct GET 405, stored own-encounter scoped 200). Iterate on the SearchApi edits (not the tests) until green. Also re-run any existing SearchApi test to confirm no regression: `mvn test -Dtest=SearchApi*`.

- [ ] **Step 6: Normalize and commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/api/SearchApi.java src/test/java/org/ecocean/api/SearchApiTokenAuthTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/api/SearchApi.java src/test/java/org/ecocean/api/SearchApiTokenAuthTest.java
git commit -m "feat(search): enforce encounter-only token scope + ACL pre-filter in SearchApi"
```

---

### Task 5: Shiro `web.xml` wiring

Register the filter in Shiro `[main]` and map `/api/v3/search/**` to it — token filter only, no `authc`/`roles` chained.

**Files:**
- Modify: `src/main/webapp/WEB-INF/web.xml`
- Test: `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java` (B's wiring-guard test, if present)

- [ ] **Step 1: Add the filter to `[main]`**

In `web.xml`, in the Shiro `config` `[main]` block, after the existing line `authcBasicWildbook = org.ecocean.security.WildbookBasicHttpAuthenticationFilter`, add:

```
            tokenAuthSearch = org.ecocean.security.WildbookTokenAuthenticationFilter
```

- [ ] **Step 2: Add the `[urls]` rule**

In the `[urls]` section, in the `# ===== v3 API Security =====` group (after `/api/v3/match-inspection/** = authc`, around line 109), add:

```
            /api/v3/search/** = tokenAuthSearch
```

This is the only rule for the search path; no `authc`/`roles` is chained (the filter itself handles auth, and a no-Bearer request passes through to `SearchApi`, which self-401s if there is no session principal). Rules are first-match-wins and processed in order — confirm no earlier rule already matches `/api/v3/search/**`.

- [ ] **Step 3: Update / add the wiring-guard test**

If `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java` exists (added by Artifact B to assert `web.xml` Shiro rules), add an assertion that the search rule is present and maps only to `tokenAuthSearch`. Mirror the existing assertion style in that file — it exposes the `web.xml` contents via a helper named **`fullText()`** (`EndpointAuthWiringTest.java:35-36`), so use that (not a new `readWebXml()`):

```java
    @Test void searchPath_wiredToTokenFilterOnly() throws Exception {
        String webXml = fullText(); // existing helper in EndpointAuthWiringTest
        assertTrue(webXml.contains("tokenAuthSearch = org.ecocean.security.WildbookTokenAuthenticationFilter"),
            "token filter declared in [main]");
        assertTrue(webXml.contains("/api/v3/search/** = tokenAuthSearch"),
            "search path mapped to token filter only");
        assertFalse(webXml.replaceAll("\\s+", " ")
            .contains("/api/v3/search/** = tokenAuthSearch, "),
            "search path must NOT chain authc/roles after the token filter");
    }
```

If `EndpointAuthWiringTest` does not exist (verify first with a quick `ls src/test/java/org/ecocean/api/`), create `src/test/java/org/ecocean/api/SearchWiringTest.java` with the same assertions and a local `fullText()` helper that reads `src/main/webapp/WEB-INF/web.xml` from the project basedir:

```java
    private String fullText() throws Exception {
        return new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/main/webapp/WEB-INF/web.xml")),
            java.nio.charset.StandardCharsets.UTF_8);
    }
```

- [ ] **Step 4: Run the wiring test**

Run: `mvn test -Dtest=EndpointAuthWiringTest` (or `SearchWiringTest`)
Expected: PASS.

- [ ] **Step 5: Normalize and commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/webapp/WEB-INF/web.xml | xargs -r sed -i 's/\r$//'
git add src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/
git commit -m "feat(search): wire WildbookTokenAuthenticationFilter onto /api/v3/search/**"
```

---

### Task 6: End-to-end enforcement integration test (real OpenSearch)

Prove the scoping for real: a token-authenticated `/api/v3/search/encounter` returns only the token user's permitted encounters AND the reported `total` reflects the scoped set (guards against the post-sanitize leak). Reuse the embedded-Jetty + Testcontainers harness in `EncounterExportImagesTest`, which already registers `SearchApi` at `/api/v3/search/*` and `IniShiroFilter` from `classpath:shiro.ini`.

**Files:**
- Modify: `src/test/resources/shiro.ini` — add the same `[main]`/`[urls]` token-filter wiring as `web.xml`.
- Modify/Create: `src/test/java/org/ecocean/api/SearchTokenScopeTest.java` (new, or a method group in `EncounterExportImagesTest`).

- [ ] **Step 1: Mirror the Shiro wiring into the test `shiro.ini`**

In `src/test/resources/shiro.ini`, add to `[main]`:

```
tokenAuthSearch = org.ecocean.security.WildbookTokenAuthenticationFilter
```

and to `[urls]` (before any catch-all):

```
/api/v3/search/** = tokenAuthSearch
```

- [ ] **Step 2: Write the integration test**

Create `src/test/java/org/ecocean/api/SearchTokenScopeTest.java` modeled on `EncounterExportImagesTest`'s `@BeforeAll` server/Testcontainers setup (copy its server bring-up: `Server`, `ServletContextHandler`, `IniShiroFilter` from `classpath:shiro.ini`, register `new SearchApi()` at `/api/v3/search/*`, REST Assured base URI/port). Seed data + token, then assert scoping:

```java
    @Test void tokenSearch_scopesToPermittedEncountersAndTotals() throws Exception {
        // Arrange (in setUp/seed): index three encounters in the test OpenSearch:
        //   encA: submitterUserId = aliceUuid                  (alice can see)
        //   encB: viewUsers = [aliceUuid]                      (alice can see via collaboration)
        //   encC: submitterUserId = bobUuid, no viewUsers      (alice must NOT see)
        // Mint a real token for alice via the test JwtService (same keypair the filter loads).
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);

        io.restassured.response.Response r = given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when()
            .post("/api/v3/search/encounter")
            .then()
            .statusCode(200)
            .extract().response();

        // hits contain only encA + encB; encC absent
        org.json.JSONObject body = new org.json.JSONObject(r.asString());
        org.json.JSONArray hits = body.getJSONArray("hits");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < hits.length(); i++) ids.add(hits.getJSONObject(i).optString("id"));
        assertTrue(ids.contains(encAId), "alice sees her own encounter");
        assertTrue(ids.contains(encBId), "alice sees the collaboration encounter");
        assertFalse(ids.contains(encCId), "alice must NOT see bob's private encounter");

        // total is scoped (not the raw 3) — guards against the post-sanitize leak
        assertEquals("2", r.header("X-Wildbook-Total-Hits"), "total reflects scoped set");
    }

    @Test void adminTokenSearch_seesAll() throws Exception {
        String token = testJwtService.sign(adminUuid, "context0", 60_000L);
        io.restassured.response.Response r = given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().statusCode(200).extract().response();
        assertEquals("3", r.header("X-Wildbook-Total-Hits"), "admin token is unscoped");
    }

    @Test void tokenSearch_nonEncounterIndex_403() throws Exception {
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        given().header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/individual")
            .then().statusCode(403);
    }

    // Real-stack context-drift: token says context0, but the wildbookContext cookie aims
    // elsewhere. This drives the REAL ServletUtilities.getContext (the cookie branch returns
    // the cookie value unconditionally — ServletUtilities.java:786-794), so it reliably forces
    // a non-context0 resolved context without needing context1 configured in the test env.
    // (The ?context= branch only honors *configured* contexts, so the cookie is the robust
    // trigger; query-param/host drift logic is also covered by the filter seam unit tests.)
    @Test void tokenSearch_contextDriftViaCookie_401() throws Exception {
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        given().header("Authorization", "Bearer " + token)
            .cookie("wildbookContext", "context1")
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().statusCode(401);
    }

    // Spec Critical: a token request must NOT mint a session, so it can never be parlayed
    // into a write. Assert no JSESSIONID is set, and a follow-up no-Bearer request on the
    // same client is unauthenticated (does not inherit the token user).
    @Test void tokenSearch_mintsNoSession() throws Exception {
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        io.restassured.response.Response r = given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().statusCode(200)
            .extract().response();

        // no session cookie minted
        String setCookie = r.header("Set-Cookie");
        assertFalse((setCookie != null) && setCookie.contains("JSESSIONID"),
            "token search must not mint a JSESSIONID");
        assertTrue(r.getCookies() == null || r.getCookie("JSESSIONID") == null,
            "no JSESSIONID cookie returned");

        // a follow-up request with no Bearer (and any cookies the prior response set) is anon:
        // SearchApi self-401s because there is no session principal
        given()
            .cookies(r.getCookies() == null ? new java.util.HashMap<>() : r.getCookies())
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().statusCode(401);
    }
```

The JwtService keypair must match between the filter (loads from `CommonConfiguration` `jwtPublicKeyBase64`) and the test signer. Set the test config keys (`jwtPrivateKeyBase64`/`jwtPublicKeyBase64`/`jwtIssuer`/`jwtAudience`) to a generated test keypair in `@BeforeAll` (the harness already loads a test `commonConfiguration.properties`; write the test keys there or via the same mechanism `AuthTokenTest`/the B integration tests use). Reuse the keypair-generation snippet from `JwtServiceTest`/Task 3.

- [ ] **Step 3: Run the integration test**

Run: `mvn test -Dtest=SearchTokenScopeTest`
Expected: PASS (5 tests: scoped non-admin, admin unscoped, non-encounter 403, context-drift-via-cookie 401, no-session-minted). This pulls the Testcontainers OpenSearch image; allow time on first run. The `*Test` suffix means a plain `mvn test` also runs it (matching the repo's `EncounterExportImagesTest` convention). If the environment cannot run Testcontainers, mark this class `@org.junit.jupiter.api.Disabled("requires Docker/Testcontainers")` with a comment and rely on Tasks 1/3/4 unit tests + a manual run note — but do not delete it.

- [ ] **Step 4: Normalize and commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/test/resources/shiro.ini src/test/java/org/ecocean/api/SearchTokenScopeTest.java | xargs -r sed -i 's/\r$//'
git add src/test/resources/shiro.ini src/test/java/org/ecocean/api/SearchTokenScopeTest.java
git commit -m "test(search): end-to-end token-scoped encounter search enforcement"
```

---

### Task 7: Full build, self-review, and Codex code review

- [ ] **Step 1: Compile and run the full affected test set**

Run:
```bash
cd /mnt/c/Wildbook-token-auth
mvn -q -DskipTests compile
mvn test -Dtest=OpenSearchAclFilterTest,JwtServiceTest,WildbookTokenAuthenticationFilterTest,SearchApiTokenAuthTest,EndpointAuthWiringTest
```
Expected: BUILD SUCCESS; all tests green. (Run `SearchTokenScopeTest` separately if Docker is available.)

- [ ] **Step 2: Verify no CRLF crept in across all touched files**

Run:
```bash
cd /mnt/c/Wildbook-token-auth
git diff --name-only origin/main | xargs -r grep -lU $'\r' || echo "LF-clean"
```
Expected: `LF-clean`.

- [ ] **Step 3: Self-review against the spec**

Re-read `docs/superpowers/specs/2026-06-06-token-auth-scoped-search-design.md` components 1–8 and confirm each maps to a task: token verify + context check (Task 3), request wrapper not subject.login (Task 3), web.xml scope (Task 5), context-drift (Task 3), ACL pre-filter + exact injection point (Tasks 1+4), stored-query + index gating (Task 4), fail-closed-unmarked (Task 4), method allowlist (Task 3). Confirm the two flagged follow-ups (global SearchApi metadata leak; orgAdmin `viewUsers` coverage) are NOT implemented here (out of scope) and are noted in the PR description.

- [ ] **Step 4: Codex code review (per project convention — see memory `feedback-codex-review`, `feedback-codex-takeseriously`)**

Produce the full diff and run a read-only Codex review:
```bash
cd /mnt/c/Wildbook-token-auth
git diff origin/main > /tmp/artifact-d-diff.patch
cat > /tmp/codex-d-code-review.txt <<'PROMPT'
Adversarial code review of Artifact D (token-authenticated scoped encounter search) for Wildbook.
Read /tmp/artifact-d-diff.patch and verify against real source in /mnt/c/Wildbook-token-auth.
Focus: (1) can a token request reach queryPit unscoped (fail-open) for a non-admin? (2) is the
ACL predicate complete vs Encounter.opensearchAccess (publiclyReadable/submitterUserId/viewUsers,
admin bypass; editUsers correctly omitted)? (3) does the wrapper truly make getUser(request)
resolve the token user, and is there any SecurityUtils.getSubject() path it misses? (4) does the
filter avoid minting a session (no subject.login, no Set-Cookie)? (5) stored-query index+owner
gating for both direct and {uuid} paths; (6) context-drift coverage; (7) any Shiro [urls]
ordering issue. Take Low/Medium findings seriously. Write findings to /tmp/codex-d-code-review-out.md
with Severity | Finding | Evidence (file:line) | Fix, then a verdict: SAFE TO MERGE or FIX-FIRST.
PROMPT
codex exec --sandbox read-only -c approval_policy="never" - < /tmp/codex-d-code-review.txt > /tmp/codex-d-code-review-console.log 2>&1; echo "EXIT=$?"
```
Read the findings, fold in any High/Medium (and seriously weigh Low), commit fixes, and re-review until the verdict is SAFE TO MERGE.

- [ ] **Step 5: Final commit + push decision**

Once Codex is SAFE TO MERGE, stop and report to the user: summarize the diff, test counts, the Codex verdict, and the two flagged follow-ups. Do NOT push or open a PR without explicit user go-ahead (project convention). The PR depends on Artifact B (#1594) — note the stack/branch decision (open question 4 in the spec) for the user.

---

## Self-Review (completed by plan author)

**Spec coverage:** Components 1–8 each map to a task (see Task 7 Step 3). Scope (encounter-only) → Tasks 4+5. Security section (read-only, stateless, Java-enforces, context isolation) → Tasks 3+4. Testing section → Tasks 1,3,4,6. Kernel-retirement is a separate tracked task in the `wildbook-coscientist` repo (not this plan).

**Placeholder scan:** No TBD/TODO/"add validation"-style placeholders; every code step shows complete code. The one conditional note (Task 6 `@Disabled` if no Docker) is an explicit, justified fallback, not a placeholder.

**Type/name consistency:** `TOKEN_AUTH_ATTR` constant referenced identically in the filter (Task 3) and `SearchApi`/tests (Task 4). `applyEncounterAclFilter(JSONObject, String)` signature identical in Task 1 definition and Task 4 call. Filter protected seams (`expectedContext`/`requestContext`/`jwtService`/`lookupUsername`) defined in Task 3 and overridden in the Task 3 test. `User.getId()` used consistently for ACL value + owner check (== `getUUID()`, verified). `JwtService.canVerify()` defined in Task 2, used in Task 3.
