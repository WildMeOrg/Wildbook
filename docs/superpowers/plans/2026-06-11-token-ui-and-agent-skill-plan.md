# Token-Issuance UI + Served Agent Skill — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a logged-in user mint a short-lived bearer token from the Wildbook UI (with a server-verified step-up password), and serve an anonymous agent-skill document at `GET /api/v3/agent-skill` that teaches a user's AI agent the token-scoped API, schema, how to get a token, and to never accept the user's credentials.

**Architecture:** Component A — harden the existing `AuthToken` mint to verify a *fresh* Basic credential server-side (a session cookie must not suffice), then a React `/api-access` page that collects the password and calls it via a cookie-less `fetch`. Component B — an `AgentSkill` servlet streaming a curated, version-controlled markdown resource, anon-gated.

**Tech Stack:** Java 17 servlets (`javax.servlet`, `Shepherd`/JDO), org.json, JUnit 5 + Mockito; React (react-bootstrap, react-query), Jest/RTL; Shiro (`web.xml [urls]`).

**Spec:** `docs/superpowers/specs/2026-06-11-token-ui-and-agent-skill-design.md` (Codex-reviewed).
**Branch:** `token-auth-scoped-search` (PR #1613). Do NOT push/merge — the user does that.

**Repo conventions (read before starting):**
- JUnit 5 assertions put the message LAST: `assertEquals(expected, actual, "msg")`.
- Normalize line endings before every commit: `grep -c $'\r' <file>` must be 0; else `sed -i 's/\r$//' <file>`.
- Java test run: `mvn test -Dtest=<Class> -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"`
- Frontend test run: `cd frontend && npx jest <path>` (CI is continue-on-error for jest, but tests must pass locally).
- Commit messages end with: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

**Verified facts this plan relies on:**
- `ServletUtilities.hashAndSaltPassword(String clearText, String salt)` (ServletUtilities.java:752) produces the stored hash; `User.getSalt()`, `User.getPassword()`, `User.getUsername()`, `User.getId()` exist.
- `AuthToken extends ApiBase` and currently mints for `myShepherd.getUser(request)` (session/Basic via Shiro) — this is what we harden.
- `/api/v3/user` (`UserInfo`) exposes `username`. `/api/*` is mapped to `WildbookApi`, so a new **exact** servlet mapping `/api/v3/agent-skill` wins.
- `SearchApi` token allowlist = `encounter`, `annotation`, `individual` (others 403) — the drift-guard test pins the skill to this.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/org/ecocean/User.java` (**modify**) | Add `checkPassword(String clearText)` — verify against stored salted hash. |
| `src/main/java/org/ecocean/api/AuthToken.java` (**modify**) | Require + verify a fresh Basic credential; reject session-only; `no-store`; audit log. |
| `src/main/java/org/ecocean/api/AgentSkill.java` (**create**) | Stream the curated skill markdown as `text/markdown`. |
| `src/main/resources/agent-skill.md` (**create**) | The curated agent skill content. |
| `src/main/webapp/WEB-INF/web.xml` (**modify**) | Register `AgentSkill` servlet + `/api/v3/agent-skill` mapping + `anon` Shiro rule. |
| `src/test/java/org/ecocean/api/AuthTokenTest.java` (**modify/create**) | Step-up enforcement cases. |
| `src/test/java/org/ecocean/api/AgentSkillTest.java` (**create**) | Serving + content-anchor + drift-guard tests. |
| `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java` (**modify**) | Assert servlet + url-pattern + `anon` rule. |
| `frontend/src/models/auth/useMintToken.js` (**create**) | Cookie-less mint hook (raw fetch, Basic header). |
| `frontend/src/pages/ApiAccess/ApiAccessPage.jsx` (**create**) | Page + password modal + token display. |
| `frontend/src/AuthenticatedSwitch.jsx` (**modify**) | `/api-access` route. |
| `frontend/src/components/header/AvatarAndUserProfile.jsx` (**modify**) | "API Access" menu item. |
| `frontend/src/__tests__/...` (**create**) | Hook + page + menu tests. |

---

## Task 1: `User.checkPassword` helper

**Files:**
- Modify: `src/main/java/org/ecocean/User.java`
- Test: `src/test/java/org/ecocean/UserCheckPasswordTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/UserCheckPasswordTest.java`:

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UserCheckPasswordTest {

    private static final String SALT = "0123456789abcdef";

    private User userWith(String clear) {
        // IMPORTANT: User.setPassword(String) only ASSIGNS the field — it does NOT hash. Production
        // stores an already-hashed value alongside a matching salt, so the test must do the same.
        User u = new User();
        u.setUsername("alice");
        u.setSalt(SALT);
        u.setPassword(org.ecocean.servlet.ServletUtilities.hashAndSaltPassword(clear, SALT));
        return u;
    }

    @Test void checkPassword_trueForCorrect_falseForWrong() {
        User u = userWith("s3cr3t!");
        assertTrue(u.checkPassword("s3cr3t!"), "correct password verifies");
        assertFalse(u.checkPassword("nope"), "wrong password rejected");
    }

    @Test void checkPassword_falseOnNullOrNoStoredPassword() {
        User u = new User();
        u.setUsername("bob");              // no password/salt set
        assertFalse(u.checkPassword("anything"), "no stored password -> false");
        User u2 = userWith("pw");
        assertFalse(u2.checkPassword(null), "null candidate -> false");
        assertFalse(u2.checkPassword(""), "empty candidate -> false");
    }
}
```

Note: `User` has a no-arg constructor, `setSalt(String)`, and `setPassword(String)` that **only
assigns** (does not hash). `checkPassword` (Step 3) hashes the candidate with the stored salt and
compares to the stored hash — exactly mirroring `WildbookBasicHttpAuthenticationFilter`. If any setter
name differs, adapt the setup but keep the "store a real hash + matching salt" shape and the assertions.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=UserCheckPasswordTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — `checkPassword` undefined (compile error).

- [ ] **Step 3: Implement**

Add to `src/main/java/org/ecocean/User.java` (near `getPassword()`):

```java
    /**
     * Verify a clear-text password against this user's stored salted hash, using the same hashing
     * as login (ServletUtilities.hashAndSaltPassword). Constant-time comparison. Returns false if
     * this user has no stored password or the candidate is blank.
     */
    public boolean checkPassword(String clearText) {
        if ((clearText == null) || clearText.isEmpty()) return false;
        String stored = this.getPassword();
        String salt = this.getSalt();
        if ((stored == null) || stored.isEmpty() || (salt == null)) return false;
        String hashed = org.ecocean.servlet.ServletUtilities.hashAndSaltPassword(clearText, salt);
        if (hashed == null) return false;
        return java.security.MessageDigest.isEqual(
            hashed.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
```

- [ ] **Step 4: Run test to verify it passes** (same command) — Expected: PASS (2 tests).

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/User.java src/test/java/org/ecocean/UserCheckPasswordTest.java
git add src/main/java/org/ecocean/User.java src/test/java/org/ecocean/UserCheckPasswordTest.java
git commit -m "User: add checkPassword(clearText) for fresh credential verification

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: `AuthToken` step-up enforcement

Harden the mint so a session cookie alone can NEVER mint — a fresh, valid `Authorization: Basic` credential is required and verified server-side. (Codex High.)

**Files:**
- Modify: `src/main/java/org/ecocean/api/AuthToken.java`
- Test: `src/test/java/org/ecocean/api/AuthTokenStepUpTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/api/AuthTokenStepUpTest.java`:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class AuthTokenStepUpTest {

    private HttpServletResponse resp(StringWriter out) throws Exception {
        HttpServletResponse r = mock(HttpServletResponse.class);
        when(r.getWriter()).thenReturn(new PrintWriter(out));
        return r;
    }

    private String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes());
    }

    @Test void noBasicHeader_sessionOnly_is401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null); // session-only
        StringWriter out = new StringWriter();
        HttpServletResponse r = resp(out);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
        })) {
            new AuthToken().doPostForTest(req, r);
        }
        verify(r).setStatus(401);
    }

    // Servlet-level check: a present-but-wrong Basic credential is rejected by THIS servlet
    // regardless of any session. (The full filter+session end-to-end — "authenticated session +
    // wrong Basic still 401" — depends on Shiro and is covered by the live smoke in the spec.)
    @Test void wrongPassword_servletRejects_401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(basic("alice", "WRONG"));
        StringWriter out = new StringWriter();
        HttpServletResponse r = resp(out);
        User alice = mock(User.class);
        when(alice.checkPassword("WRONG")).thenReturn(false);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser("alice")).thenReturn(alice);
        })) {
            new AuthToken().doPostForTest(req, r);
        }
        verify(r).setStatus(401);
    }

    @Test void correctPassword_mints200() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(basic("alice", "right"));
        StringWriter out = new StringWriter();
        HttpServletResponse r = resp(out);
        User alice = mock(User.class);
        when(alice.checkPassword("right")).thenReturn(true);
        when(alice.getId()).thenReturn("uuid-alice");
        when(alice.getUsername()).thenReturn("alice");
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser("alice")).thenReturn(alice);
        })) {
            new AuthToken().doPostForTest(req, r);
        }
        // 200 only if JWT issuance is configured in the test env; otherwise 503.
        // Assert we did NOT 401 (the credential was accepted) and set no-store.
        verify(r, never()).setStatus(401);
        verify(r).setHeader("Cache-Control", "no-store");
    }
}
```

Note: the `correctPassword_mints200` test asserts "not 401" + the `no-store` header rather than a hard 200, because JWT signing may be unconfigured in the unit env (yielding 503). The point is the credential path, not the signer.

- [ ] **Step 2: Run test to verify it fails** (`mvn test -Dtest=AuthTokenStepUpTest ...`) — FAIL (`doPostForTest` undefined / logic absent).

- [ ] **Step 3: Implement**

Rewrite `AuthToken.doPost` to parse + verify a fresh Basic credential. Replace the current `doPost` body and add a `doPostForTest` shim + a parse helper. Full new `AuthToken.java`:

```java
package org.ecocean.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * POST /api/v3/auth/token
 * Mints a short-lived RS256 token. STEP-UP: requires a fresh, valid HTTP Basic credential and
 * verifies it server-side (a Shiro session alone is NOT sufficient) — so a stolen/unlocked session
 * or same-origin script cannot mint without the password.
 */
public class AuthToken extends ApiBase {
    private static final long DEFAULT_TTL_MILLIS = 30L * 60L * 1000L; // 30 min

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle(request, response);
    }

    // package-visible test entry point
    void doPostForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        final String context = "context0"; // Basic auth is pinned to context0 (see filter)
        String clientIp = request.getRemoteAddr();

        String[] cred = parseBasic(request.getHeader("Authorization"));
        if (cred == null) {
            // No fresh Basic credential -> session alone is not sufficient.
            System.out.println("AuthToken mint DENIED (no Basic credential) ip=" + clientIp);
            writeError(response, 401, "basic credentials required");
            return;
        }
        String username = cred[0];
        String password = cred[1];

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.AuthToken.doPost");
        myShepherd.beginDBTransaction();
        try {
            User user = (Util.stringExists(username)) ? myShepherd.getUser(username) : null;
            if ((user == null) || !user.checkPassword(password)) {
                System.out.println("AuthToken mint DENIED (bad credentials) user=" + username
                    + " ip=" + clientIp);
                writeError(response, 401, "invalid credentials");
                return;
            }
            String tokenContext = org.ecocean.CommonConfiguration.getProperty("jwtContext", context);
            if (!Util.stringExists(tokenContext)) tokenContext = "context0";
            JwtService jwt = JwtService.fromConfig(tokenContext);
            if (!jwt.isEnabled()) {
                writeError(response, 503, "token issuance not configured");
                return;
            }
            long ttl = ttlFromConfig(tokenContext);
            String token = jwt.sign(user.getId(), tokenContext, ttl);
            System.out.println("AuthToken mint OK user=" + username + " ip=" + clientIp);
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

    /** Parse an HTTP Basic Authorization header into [username, password], or null if absent/invalid. */
    private static String[] parseBasic(String header) {
        if (header == null) return null;
        if (!header.regionMatches(true, 0, "Basic ", 0, 6)) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                StandardCharsets.UTF_8);
            int i = decoded.indexOf(':');
            if (i < 0) return null;
            return new String[] { decoded.substring(0, i), decoded.substring(i + 1) };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long ttlFromConfig(String context) {
        String v = org.ecocean.CommonConfiguration.getProperty("jwtTtlSeconds", context);
        if (Util.stringExists(v)) {
            try {
                long secs = Long.parseLong(v.trim());
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

Key changes vs the old version: token is minted for the **Basic-verified** user (`getUser(username)` + `checkPassword`), never `getUser(request)` (which would honor the session); `no-store` header; audit `println`s without password/token. The Shiro `authcBasicWildbook` filter stays on the route (unchanged) — this servlet-level check is the authoritative step-up.

- [ ] **Step 4: Run the new test** (`mvn test -Dtest=AuthTokenStepUpTest ...`) — Expected: PASS (3 tests).

- [ ] **Step 4b: Update the EXISTING `AuthTokenTest` for the new contract (Codex High)**

The rewrite changes behavior, so `src/test/java/org/ecocean/api/AuthTokenTest.java` will regress — a
request with **no** Basic header now returns `401` *before* any Shepherd/JWT setup. Read that file and
update each case to the new contract:
- Any "no credentials / session-only" case → expect `401` ("basic credentials required").
- The JWT-disabled (`503`) and context-pin cases → must now supply a **valid** Basic header
  (`Authorization: Basic base64("alice:right")`) and mock `Shepherd.getUser("alice")` → a `User` mock
  whose `checkPassword("right")` returns `true` (mirror `AuthTokenStepUpTest`), so execution reaches
  the JWT/issuance logic those cases exercise.
Run `mvn test -Dtest=AuthTokenTest ...` → all green. Do NOT delete coverage — adapt it.

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/AuthToken.java src/test/java/org/ecocean/api/AuthTokenStepUpTest.java src/test/java/org/ecocean/api/AuthTokenTest.java
git add src/main/java/org/ecocean/api/AuthToken.java src/test/java/org/ecocean/api/AuthTokenStepUpTest.java src/test/java/org/ecocean/api/AuthTokenTest.java
git commit -m "AuthToken: server-side step-up — require+verify fresh Basic, reject session-only

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: The curated agent-skill markdown

**Files:**
- Create: `src/main/resources/agent-skill.md`

- [ ] **Step 1: Create the content**

Create `src/main/resources/agent-skill.md` with the following (keep it self-contained, agent-agnostic, and free of internal ACL field names / deployment details):

````markdown
# Wildbook Token-Scoped API — Agent Skill

You are an AI agent operating Wildbook's **read-only** API on behalf of a human user. You see exactly
what that user is permitted to see (everything is access-controlled to their account).

## Security — read first
- **Never ask for, accept, or store the user's Wildbook username or password.** You do not need them.
- The user generates a short-lived **bearer token** in Wildbook's UI (Account menu → **API Access**)
  and pastes **only the token** to you.
- Treat the token as a secret: never log or persist it, never send it anywhere except Wildbook over
  HTTPS. It expires (typically ~30 minutes). When it expires, ask the user to paste a fresh one.

## Authentication
Send the token as a bearer header on every request:
```
Authorization: Bearer <token>
```
The token response includes `expiresInSeconds`. An admin user's token sees all data; a normal user's
token is filtered to their own accessible records. The server also enforces internal access-control
fields, which are never returned to you.

## Endpoints

### Search — `POST /api/v3/search/{index}`
`{index}` is one of: `encounter`, `individual`, `annotation`. (`occurrence` and `media_asset` return
`403` for token callers.) Body is an OpenSearch query, e.g.:
```json
{ "query": { "term": { "taxonomy": "Salamandra salamandra" } } }
```
Pagination via `?from=&size=` query params; total hits in the `X-Wildbook-Total-Hits` response header.
Non-admin `individual` search may only query/sort identity fields. Aggregations, scripted queries, and
cross-index term lookups are rejected.

### Media resolve — `POST /api/v3/media/resolve`
Resolve up to 100 annotation IDs you are allowed to see into displayable image references:
```json
{ "annotationIds": ["<uuid>", "<uuid>"] }
```
Returns an array of `{ id, imageUrl, imageWidth, imageHeight, bbox: [x,y,w,h], theta, viewpoint,
encounterId, individualId, methodVersion }`. The `bbox` is in the `imageWidth`×`imageHeight`
coordinate space. **Fetch `imageUrl`, read its real pixel dimensions, and scale `bbox` by
`realW/imageWidth`, `realH/imageHeight` before cropping** (usually a no-op). IDs you can't see (or that
don't exist) are simply absent — the response never reveals which.

## OpenSearch schema (token-exposed fields)
See the field reference for full descriptions. Key indices/fields:
- **encounter** — `id`, `taxonomy`, `locationId`/`locationName`, `date`/`dateMillis`, `individualId`,
  `sex`, `lifeStage`, `livingStatus`, `country`, `behavior`, ...
- **individual** — `id`, `displayName`, `names`/`nameMap`, `sex`, `taxonomy`, `timeOfBirth`/`timeOfDeath`.
- **annotation** — `id`, `encounterId`, `viewpoint`, `iaClass`, `matchAgainst`, `mediaAssetId`, and
  `embeddings` (nested: `method`, `methodVersion`, and the MiewID `vector`).

Access-control fields exist server-side but are **never** returned.

## Worked examples

**Find an individual's salamander encounters, then view two annotations:**
1. `POST /api/v3/search/encounter` with `{"query":{"term":{"taxonomy":"Salamandra salamandra"}}}`.
2. Collect annotation IDs (search `annotation`, or via the encounters), then
   `POST /api/v3/media/resolve` with those IDs.
3. For each result, fetch `imageUrl`, scale `bbox` to the fetched pixels, crop, and present
   side-by-side.

**Comparing embeddings for missed matches:** only compare embeddings within the **same `viewpoint` and
same `methodVersion`** — different viewpoints/versions live in different latent spaces and are not
directly comparable. Calibrate similarity against known same-individual pairs before trusting a score.
````

- [ ] **Step 2: Commit** (no test yet; Task 4 wires + tests it)

```bash
grep -c $'\r' src/main/resources/agent-skill.md
git add src/main/resources/agent-skill.md
git commit -m "media/agent-skill: add curated agent-skill markdown resource

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: `AgentSkill` servlet + wiring + tests

**Files:**
- Create: `src/main/java/org/ecocean/api/AgentSkill.java`
- Modify: `src/main/webapp/WEB-INF/web.xml`
- Test: `src/test/java/org/ecocean/api/AgentSkillTest.java`
- Modify: `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java`
- Modify: `src/main/java/org/ecocean/api/SearchApi.java` (extract the allowlist constant)

- [ ] **Step 1: Extract the token allowlist constant in `SearchApi` (so the drift-guard is real)**

In `src/main/java/org/ecocean/api/SearchApi.java`, add a package-visible constant near the top of the
class:
```java
    /** Indices a token caller may search; everything else is 403. The agent-skill drift-guard test pins to this. */
    static final java.util.Set<String> TOKEN_ALLOWED_INDICES =
        java.util.Set.of("encounter", "annotation", "individual");
```
Then replace the inline allowlist check (currently `tokenAuth && !"encounter".equals(effectiveIndex)
&& !"annotation".equals(effectiveIndex) && !"individual".equals(effectiveIndex)`) with:
```java
                } else if (tokenAuth && !TOKEN_ALLOWED_INDICES.contains(effectiveIndex)) {
```
This is a behavior-preserving refactor; if `SearchApiTokenAuthTest`/`SearchApiChildIndexTest` exist,
re-run them to confirm no regression. Commit this small refactor with the Task 4 work.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/org/ecocean/api/AgentSkillTest.java`:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class AgentSkillTest {

    private String body(boolean[] statusOk) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        new AgentSkill().doGetForTest(req, resp);
        verify(resp).setStatus(200);
        verify(resp).setContentType("text/markdown; charset=UTF-8");
        return out.toString();
    }

    @Test void serves_markdown_with_key_anchors() throws Exception {
        String md = body(null);
        assertFalse(md.isEmpty(), "skill body must be non-empty");
        assertTrue(md.contains("Authorization: Bearer"), "documents bearer auth");
        assertTrue(md.contains("/api/v3/media/resolve"), "documents media resolve");
        assertTrue(md.contains("/api/v3/search/"), "documents search");
        for (String idx : new String[] {"encounter", "individual", "annotation"})
            assertTrue(md.contains(idx), "mentions index " + idx);
        assertTrue(md.toLowerCase().contains("never ask for")
            || md.toLowerCase().contains("never give"),
            "contains the never-share-credentials guidance");
    }

    // Drift-guard: the skill's claimed allowed indices must match SearchApi's REAL token allowlist
    // constant (not a hand-copied list) so the doc fails the build if the allowlist changes.
    @Test void skill_index_claims_match_search_allowlist() throws Exception {
        String md = body(null);
        for (String idx : SearchApi.TOKEN_ALLOWED_INDICES)
            assertTrue(md.contains(idx), "skill must list allowed index " + idx);
        // denied indices must be named + described as 403 so agents don't try them
        assertTrue(md.contains("occurrence") && md.contains("media_asset"),
            "skill must name the denied indices");
        assertTrue(md.contains("403"), "skill must state denied indices return 403");
        // and any index the skill says is denied must NOT be in the allowlist (no contradiction)
        for (String denied : new String[] {"occurrence", "media_asset"})
            assertFalse(SearchApi.TOKEN_ALLOWED_INDICES.contains(denied),
                "denied index " + denied + " must not be in the allowlist");
    }

    // Internal ACL field names must NOT leak into the public skill (Codex Low).
    @Test void skill_does_not_leak_internal_acl_field_names() throws Exception {
        String md = body(null);
        for (String acl : new String[] {
                "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"})
            assertFalse(md.contains(acl), "skill must not expose internal ACL field name " + acl);
    }
}
```

- [ ] **Step 3: Run test to verify it fails** (`mvn test -Dtest=AgentSkillTest ...`) — FAIL (`AgentSkill` undefined).

- [ ] **Step 4: Implement the servlet**

Create `src/main/java/org/ecocean/api/AgentSkill.java`:

```java
package org.ecocean.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * GET /api/v3/agent-skill
 * Serves the curated, version-controlled agent-skill markdown (classpath resource). Anonymous: a
 * how-to-authenticate doc cannot itself require auth, and it contains no secrets — only API docs.
 */
public class AgentSkill extends ApiBase {
    private static final String RESOURCE = "/agent-skill.md";

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle(request, response);
    }

    void doGetForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String md = readResource();
        if (md == null) {
            response.setStatus(500);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("agent skill unavailable");
            return;
        }
        response.setStatus(200);
        response.setContentType("text/markdown; charset=UTF-8");
        response.getWriter().write(md);
    }

    private String readResource() throws IOException {
        try (InputStream in = AgentSkill.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 5: Wire web.xml**

In `src/main/webapp/WEB-INF/web.xml`:

(a) Shiro `[urls]` block — add near the other `/api/v3/*` rules (anonymous):
```
				/api/v3/agent-skill = anon
```

(b) Servlet registration (near other `org.ecocean.api.*` servlets):
```xml
	<servlet>
		<servlet-name>AgentSkill</servlet-name>
		<servlet-class>org.ecocean.api.AgentSkill</servlet-class>
	</servlet>
	<servlet-mapping>
		<servlet-name>AgentSkill</servlet-name>
		<url-pattern>/api/v3/agent-skill</url-pattern>
	</servlet-mapping>
```
(An exact `<url-pattern>` wins over the `/api/*` → `WildbookApi` mapping.)

- [ ] **Step 6: Add wiring assertions to `EndpointAuthWiringTest.java`**

```java
    @Test
    void agentSkill_servletClassIsRegistered() {
        assertTrue(fullText().contains("<servlet-class>org.ecocean.api.AgentSkill</servlet-class>"),
                "web.xml must register the AgentSkill servlet");
    }

    @Test
    void agentSkill_urlPatternIsRegistered() {
        assertTrue(fullText().contains("<url-pattern>/api/v3/agent-skill</url-pattern>"),
                "web.xml must map /api/v3/agent-skill");
    }

    @Test
    void agentSkill_shiroRuleIsAnon() {
        String ruleLine = lines.stream()
                .filter(l -> {
                    String t = l.stripLeading();
                    return !t.startsWith("#") && t.contains("/api/v3/agent-skill");
                })
                .findFirst().orElse(null);
        assertNotNull(ruleLine, "Shiro [urls] must contain a rule for /api/v3/agent-skill");
        String value = ruleLine.substring(
                ruleLine.indexOf("/api/v3/agent-skill") + "/api/v3/agent-skill".length()).trim();
        if (value.startsWith("=")) value = value.substring(1).trim();
        assertEquals("anon", value,
                "the agent-skill doc must be anon (a how-to-auth doc can't require auth); was: '" + value + "'");
    }
```
(If `EndpointAuthWiringTest` exposes helpers under different names than `fullText()`/`lines`, mirror that file's existing assertions instead.)

- [ ] **Step 7: Run tests to verify they pass**

`mvn test -Dtest=AgentSkillTest,EndpointAuthWiringTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"`
Expected: PASS (AgentSkillTest 3 + EndpointAuthWiringTest existing + 3 new).

- [ ] **Step 8: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/SearchApi.java src/main/java/org/ecocean/api/AgentSkill.java src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/AgentSkillTest.java src/test/java/org/ecocean/api/EndpointAuthWiringTest.java
git add src/main/java/org/ecocean/api/SearchApi.java src/main/java/org/ecocean/api/AgentSkill.java src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/AgentSkillTest.java src/test/java/org/ecocean/api/EndpointAuthWiringTest.java
git commit -m "agent-skill: serve curated skill at GET /api/v3/agent-skill (anon) + drift-guard tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: `useMintToken` frontend hook (cookie-less Basic)

**Files:**
- Create: `frontend/src/models/auth/useMintToken.js`
- Test: `frontend/src/__tests__/models/useMintToken.test.js`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/models/useMintToken.test.js`:

```javascript
import { mintToken } from "../../models/auth/useMintToken";

describe("mintToken", () => {
  let fetchMock;
  beforeEach(() => { fetchMock = jest.fn(); global.fetch = fetchMock; }); // jsdom has no fetch by default
  afterEach(() => { jest.resetAllMocks(); });

  it("POSTs with a cookie-less Basic header and returns the token on 200", async () => {
    fetchMock.mockResolvedValue({
      status: 200,
      json: async () => ({ token: "tok123", tokenType: "Bearer", expiresInSeconds: 1800 }),
    });
    const res = await mintToken("alice", "s3cr3t");
    const [url, opts] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/v3/auth/token");
    expect(opts.method).toBe("POST");
    expect(opts.credentials).toBe("omit");                       // no session cookie
    expect(opts.headers.Authorization).toBe("Basic " + btoa("alice:s3cr3t"));
    expect(res.token).toBe("tok123");
    expect(res.expiresInSeconds).toBe(1800);
  });

  it("throws a typed error with the status on non-200", async () => {
    fetchMock.mockResolvedValue({ status: 401, json: async () => ({ error: "invalid credentials" }) });
    await expect(mintToken("alice", "wrong")).rejects.toMatchObject({ status: 401 });
  });
});
```

- [ ] **Step 2: Run to verify it fails**: `cd frontend && npx jest src/__tests__/models/useMintToken.test.js` — FAIL (module missing).

- [ ] **Step 3: Implement**

Create `frontend/src/models/auth/useMintToken.js`:

```javascript
// Mint a short-lived API token via step-up Basic auth.
// IMPORTANT: uses a raw fetch with credentials:"omit" so NO session cookie is sent — the server
// must verify the supplied password fresh (a session alone cannot mint).
export async function mintToken(username, password) {
  const resp = await fetch("/api/v3/auth/token", {
    method: "POST",
    credentials: "omit",
    headers: {
      Authorization: "Basic " + btoa(`${username}:${password}`),
    },
  });
  let data = null;
  try { data = await resp.json(); } catch (_e) { /* non-JSON body */ }
  if (resp.status !== 200 || !data || !data.token) {
    const err = new Error((data && data.error) || `mint failed (${resp.status})`);
    err.status = resp.status;
    throw err;
  }
  return data; // { token, tokenType, expiresInSeconds }
}

// Thin hook wrapper for components (keeps call sites declarative).
import { useState, useCallback } from "react";
export default function useMintToken() {
  const [loading, setLoading] = useState(false);
  const mint = useCallback(async (username, password) => {
    setLoading(true);
    try { return await mintToken(username, password); }
    finally { setLoading(false); }
  }, []);
  return { mint, loading };
}
```

- [ ] **Step 4: Run to verify it passes** (same jest command) — PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
grep -c $'\r' frontend/src/models/auth/useMintToken.js frontend/src/__tests__/models/useMintToken.test.js
git add frontend/src/models/auth/useMintToken.js frontend/src/__tests__/models/useMintToken.test.js
git commit -m "frontend: useMintToken hook (cookie-less step-up Basic mint)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: `ApiAccessPage` (page + password modal + token display)

**Files:**
- Create: `frontend/src/pages/ApiAccess/ApiAccessPage.jsx`
- Test: `frontend/src/__tests__/pages/ApiAccessPage.test.jsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/pages/ApiAccessPage.test.jsx`:

```javascript
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ApiAccessPage from "../../pages/ApiAccess/ApiAccessPage";

jest.mock("../../models/auth/users/useGetMe", () => () => ({
  data: { username: "alice" },
}));
const mockMint = jest.fn();
jest.mock("../../models/auth/useMintToken", () => () => ({ mint: mockMint, loading: false }));

describe("ApiAccessPage", () => {
  beforeEach(() => { mockMint.mockReset(); });

  it("mints and shows the token on success", async () => {
    mockMint.mockResolvedValue({ token: "tok-xyz", expiresInSeconds: 1800 });
    render(<ApiAccessPage />);
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "s3cr3t" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/tok-xyz/)).toBeInTheDocument());
    expect(mockMint).toHaveBeenCalledWith("alice", "s3cr3t");
  });

  it("shows an inline error on 401", async () => {
    mockMint.mockRejectedValue(Object.assign(new Error("invalid credentials"), { status: 401 }));
    render(<ApiAccessPage />);
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/incorrect password/i)).toBeInTheDocument());
    expect(screen.queryByText(/tok-/)).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run to verify it fails**: `cd frontend && npx jest src/__tests__/pages/ApiAccessPage.test.jsx` — FAIL (module missing).

- [ ] **Step 3: Implement**

Create `frontend/src/pages/ApiAccess/ApiAccessPage.jsx`:

```jsx
import React, { useState } from "react";
import { Button, Modal, Form, Alert } from "react-bootstrap";
import useGetMe from "../../models/auth/users/useGetMe";
import useMintToken from "../../models/auth/useMintToken";

const SKILL_URL = "/api/v3/agent-skill";

export default function ApiAccessPage() {
  const me = useGetMe();
  const username = me?.data?.username || "";
  const { mint, loading } = useMintToken();

  const [showModal, setShowModal] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [token, setToken] = useState(null);
  const [expiresIn, setExpiresIn] = useState(null);

  const openModal = () => { setError(null); setPassword(""); setShowModal(true); };

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      const res = await mint(username, password);
      setToken(res.token);
      setExpiresIn(res.expiresInSeconds);
      setPassword("");
      setShowModal(false);
    } catch (err) {
      if (err.status === 401) setError("Incorrect password. If your account uses single sign-on, API tokens aren't available yet.");
      else if (err.status === 503) setError("Token issuance isn't enabled on this server.");
      else setError("Couldn't generate a token. Please try again.");
    }
  };

  return (
    <div className="container" style={{ maxWidth: 720, padding: "2rem 1rem" }}>
      <h2>API Access</h2>
      <p>
        Generate a short-lived token so an AI agent or script can act with <strong>your</strong>{" "}
        Wildbook access. Treat it like a password.
      </p>
      <Alert variant="warning">
        Do <strong>not</strong> give your agent your username/password — paste it only a token.
      </Alert>
      <p>
        Your agent can learn this API here:{" "}
        <code>{SKILL_URL}</code>{" "}
        <Button size="sm" variant="link" onClick={() => navigator.clipboard?.writeText(window.location.origin + SKILL_URL)}>
          Copy link
        </Button>
      </p>

      <Button onClick={openModal}>Generate API token</Button>

      {token && (
        <div style={{ marginTop: "1.5rem" }}>
          <Alert variant="success">
            Copy this token now — it won't be shown again
            {expiresIn ? ` and expires in ~${Math.round(expiresIn / 60)} min` : ""}.
          </Alert>
          <div style={{ display: "flex", gap: 8 }}>
            <code style={{ wordBreak: "break-all", flex: 1 }}>{token}</code>
            <Button size="sm" onClick={() => navigator.clipboard?.writeText(token)}>Copy</Button>
          </div>
        </div>
      )}

      <Modal show={showModal} onHide={() => setShowModal(false)}>
        <Form onSubmit={submit}>
          <Modal.Header closeButton><Modal.Title>Confirm your password</Modal.Title></Modal.Header>
          <Modal.Body>
            <p>Re-enter your password to mint a token for <strong>{username}</strong>.</p>
            {error && <Alert variant="danger">{error}</Alert>}
            <Form.Group>
              <Form.Label htmlFor="apitoken-pw">Password</Form.Label>
              <Form.Control id="apitoken-pw" type="password" value={password}
                onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={() => setShowModal(false)}>Cancel</Button>
            <Button type="submit" disabled={loading || !password}>Confirm</Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </div>
  );
}
```

Notes:
- **Verify `useGetMe`'s return shape** before relying on `me?.data?.username`. `useGetMe` wraps
  `useFetch` (react-query) whose `dataAccessor` is `result?.data?.data`; confirm where `username`
  lands (it may be `me.data.username` or nested differently) and adjust the accessor to match the
  real hook. The test mocks `useGetMe` as `{ data: { username: "alice" } }`, so the page's accessor
  and that mock must agree.
- The 401 path keeps the modal open — `submit` only calls `setShowModal(false)` on success (the catch
  block does not close it). The success test asserts the token appears; the 401 test asserts the
  inline error appears with the modal still open.
- The token display uses a static "expires in ~N min" message (computed from `expiresInSeconds`), not
  a live ticking countdown — a live countdown is unnecessary for copy-now UX (YAGNI).

- [ ] **Step 4: Run to verify it passes** (same jest command) — PASS (2 tests). Fix selector/label mismatches if RTL can't find elements (keep `htmlFor`/`id` paired).

- [ ] **Step 5: Commit**

```bash
grep -c $'\r' frontend/src/pages/ApiAccess/ApiAccessPage.jsx frontend/src/__tests__/pages/ApiAccessPage.test.jsx
git add frontend/src/pages/ApiAccess/ApiAccessPage.jsx frontend/src/__tests__/pages/ApiAccessPage.test.jsx
git commit -m "frontend: ApiAccessPage — step-up modal + one-time token display

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Route + avatar menu item

**Files:**
- Modify: `frontend/src/AuthenticatedSwitch.jsx`
- Modify: `frontend/src/components/header/AvatarAndUserProfile.jsx`
- Test: `frontend/src/__tests__/components/AvatarApiAccessLink.test.jsx`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/components/AvatarApiAccessLink.test.jsx`:

```javascript
import React from "react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../utils/utils";
import AvatarAndUserProfile from "../../components/header/AvatarAndUserProfile";
import AuthContext from "../../AuthProvider";
import LocaleContext from "../../IntlProvider";

// AvatarAndUserProfile uses useNavigate + AuthContext + i18n, so render via the repo's standard
// renderWithProviders (router + intl), wrapped in the same contexts the header tests use
// (mirror frontend/src/__tests__/components/header/AuthenticatedHeader.test.js).
describe("AvatarAndUserProfile", () => {
  it("includes an API Access link to /api-access", () => {
    renderWithProviders(
      <AuthContext.Provider value={{ count: 0, mergeData: [], getAllNotifications: jest.fn() }}>
        <LocaleContext.Provider value={{ onLocaleChange: jest.fn() }}>
          <AvatarAndUserProfile avatar={"test-avatar"} />
        </LocaleContext.Provider>
      </AuthContext.Provider>,
    );
    const link = screen.getByText(/api access/i).closest("a");
    expect(link).toHaveAttribute("href", expect.stringContaining("/api-access"));
  });
});
```

- [ ] **Step 2: Run to verify it fails**: `cd frontend && npx jest src/__tests__/components/AvatarApiAccessLink.test.jsx` — FAIL (no such link). If the component needs providers/router to render, wrap with the same test utilities other component tests in `frontend/src/__tests__/components` use (mirror an existing one).

- [ ] **Step 3: Implement**

(a) `frontend/src/AuthenticatedSwitch.jsx` — add the import + route alongside the others (near line 115):
```jsx
import ApiAccessPage from "./pages/ApiAccess/ApiAccessPage";
```
```jsx
            <Route path="/api-access" element={<ApiAccessPage />} />
```

(b) `frontend/src/components/header/AvatarAndUserProfile.jsx` — add a dropdown item before Logout:
```jsx
        <NavDropdown.Item href={`${process.env.PUBLIC_URL}/api-access`} style={{ color: "black" }}>
          API Access
        </NavDropdown.Item>
```
(Match the existing `NavDropdown.Item` style used by the sibling items.)

- [ ] **Step 4: Run to verify it passes** (same jest command) — PASS. Run the page + hook tests too to confirm no breakage.

- [ ] **Step 5: Commit**

```bash
grep -c $'\r' frontend/src/AuthenticatedSwitch.jsx frontend/src/components/header/AvatarAndUserProfile.jsx frontend/src/__tests__/components/AvatarApiAccessLink.test.jsx
git add frontend/src/AuthenticatedSwitch.jsx frontend/src/components/header/AvatarAndUserProfile.jsx frontend/src/__tests__/components/AvatarApiAccessLink.test.jsx
git commit -m "frontend: route /api-access + API Access avatar menu item

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification (after all tasks)

- [ ] Backend suite (includes the **existing** `AuthTokenTest` updated in Task 2 Step 4b, plus the
  SearchApi token tests to confirm the allowlist-constant refactor didn't regress):
  `mvn test -Dtest=UserCheckPasswordTest,AuthTokenTest,AuthTokenStepUpTest,AgentSkillTest,EndpointAuthWiringTest,SearchApiTokenAuthTest,SearchApiChildIndexTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"` → all green. (Drop any class name that doesn't exist in this tree.)
- [ ] Frontend suite: `cd frontend && npx jest src/__tests__/models/useMintToken.test.js src/__tests__/pages/ApiAccessPage.test.jsx src/__tests__/components/AvatarApiAccessLink.test.jsx` → all green.
- [ ] `git log --oneline` shows the focused commits.
- [ ] Hand to Codex for a final code review (per the user's standing rule) before any PR/merge.
- [ ] Do NOT push — the user does that. (Live smoke per the spec runs after deploy: anon `GET /api/v3/agent-skill`; UI mint flow; session+wrong-password→401.)
