# Token-Scoped Media Resolve Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a token-gated `POST /api/v3/media/resolve` that turns a batch of annotation IDs the caller may already see into `{imageUrl, imageWidth, imageHeight, bbox (in the returned image's pixel space), theta, viewpoint, encounterId, individualId, methodVersion}`, so a token-only agent can render side-by-side annotation crops for missed-match verification.

**Architecture:** A new `MediaResolveApi extends ApiBase` servlet. Visibility reuses Spec A's exact ACL gate (`OpenSearch.applyAclFilter` over an `ids` query) as the single source of truth — admins bypass it. For each visible annotation it selects a safe derivative (`_master`→`_mid`, rejecting `_original`/`URLAssetStore`/unknown), reads the source-asset and derivative dimensions, and scales+clamps the bbox into the derivative's pixel space. Non-visible/unresolvable IDs are silently absent (no existence oracle). No new index fields, no image proxy, no UI.

**Tech Stack:** Java 17, servlet (`javax.servlet`), DataNucleus JDO (`Shepherd`), OpenSearch, org.json, JUnit 5 + Mockito (`MockedConstruction`/`MockedStatic`), Maven/surefire.

**Spec:** `docs/superpowers/specs/2026-06-08-token-scoped-media-resolve-design.md` (Codex-reviewed).

**Branch:** this Spec A worktree branch (`token-auth-scoped-search`). Do NOT push or open a PR — the user merges/pushes explicitly.

**Repo conventions (read before starting):**
- JUnit 5 assertions put the message LAST: `assertEquals(expected, actual, "msg")`, `assertTrue(cond, "msg")`.
- Normalize line endings before every commit: `grep -c $'\r' <file>` must be 0; if not, `sed -i 's/\r$//' <file>`.
- Run one test class:
  ```
  mvn test -Dtest=MediaResolveApiTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"
  ```
- Commit each task; end every commit message with the trailer:
  ```
  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  ```

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/org/ecocean/api/MediaResolveApi.java` (**create**) | The endpoint + two static helpers (`scaleBbox`, `selectSafeDerivative`) + per-ID `resolveOne` + the visibility gate. |
| `src/main/java/org/ecocean/OpenSearch.java` (**modify**) | Widen `buildIdEligibilityQuery` from package-private to `public` so the `api` package can reuse it. |
| `src/main/webapp/WEB-INF/web.xml` (**modify**) | Register the servlet + url mapping; add the Shiro token-filter rule for `/api/v3/media/**`. |
| `src/test/java/org/ecocean/api/MediaResolveApiTest.java` (**create**) | Unit + servlet-level tests (helpers, gate, payload, omission, no-oracle, dedup, size guard, validation). |
| `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java` (**modify**) | Add assertions that the media endpoint is registered + token-gated in web.xml. |
| `docs/superpowers/runbooks/childindex-acl-reindex.md` (**modify**) | Append the live smoke-test for resolve. |

Reused as-is (do not modify): `OpenSearch.applyAclFilter`, `OpenSearch.queryPit`/`deletePit`, `WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR`/`TOKEN_CONTEXT_ATTR`, `Annotation.getBbox`/`getTheta`/`getViewpoint`/`findEncounter`/`findIndividualId`/`getMediaAsset`/`getEmbeddings`/`getId`, `MediaAsset.findChildrenByLabel`/`getWidth`/`getHeight`/`getStore`/`hasLabel`/`webURL`, `Shepherd.getAnnotation`/`getUser`, `ServletUtilities.jsonFromHttpServletRequest`/`getContext`.

---

## Task 1: `scaleBbox` — pure bbox→derivative-space scaling helper

This is the High-severity Codex fix: `getBbox()` is in the source asset's pixel space, but the served derivative is resized. Pure function → test it directly, no mocks.

**Files:**
- Create: `src/main/java/org/ecocean/api/MediaResolveApi.java`
- Test: `src/test/java/org/ecocean/api/MediaResolveApiTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/api/MediaResolveApiTest.java`:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MediaResolveApiTest {

    @Test void scaleBbox_identityWhenSameDims() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {10, 20, 30, 40}, 100, 200, 100, 200);
        assertArrayEquals(new int[] {10, 20, 30, 40}, out, "same src/dst dims must be identity");
    }

    @Test void scaleBbox_halfScaleDownscales() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {100, 200, 300, 400}, 1000, 1000, 500, 500);
        assertArrayEquals(new int[] {50, 100, 150, 200}, out, "0.5x scale halves every component");
    }

    @Test void scaleBbox_clampsOverflowToDerivative() {
        // box runs to x+w=1000 in src; at 0.5x that's 500 == dst width, fine; push past:
        int[] out = MediaResolveApi.scaleBbox(new int[] {900, 0, 400, 100}, 1000, 1000, 500, 500);
        // x=450, w would be 200 -> 450+200=650 > 500, clamp w to 50
        assertArrayEquals(new int[] {450, 0, 50, 50}, out, "overflow width/height clamped to derivative bounds");
    }

    @Test void scaleBbox_nullOnBadInput() {
        assertNull(MediaResolveApi.scaleBbox(null, 100, 100, 50, 50), "null src -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {1, 2, 3}, 100, 100, 50, 50), "short src -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {0, 0, 10, 10}, 0, 100, 50, 50), "zero src dim -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {0, 0, 10, 10}, 100, 100, 0, 50), "zero dst dim -> null");
    }

    @Test void scaleBbox_negativeOriginClampsCornerAndShrinksWidth() {
        // src bbox starts at x=-10; clamping the LEFT corner to 0 must shrink width to 10, not keep 20.
        int[] out = MediaResolveApi.scaleBbox(new int[] {-10, 0, 20, 20}, 100, 100, 100, 100);
        assertArrayEquals(new int[] {0, 0, 10, 20}, out, "negative origin clamps corner and shrinks width");
    }

    @Test void scaleBbox_fullyOutsideReturnsNull() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {-50, -50, 20, 20}, 100, 100, 100, 100);
        assertNull(out, "a box entirely outside the image collapses to <1px -> null (omit)");
    }

    @Test void scaleBbox_nullWhenScaledRegionVanishes() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {0, 0, 1, 1}, 10000, 10000, 5, 5);
        assertNull(out, "a region that rounds to <1px in the derivative -> null (omit)");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MediaResolveApiTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — `MediaResolveApi` does not exist / `scaleBbox` not found (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/org/ecocean/api/MediaResolveApi.java`:

```java
package org.ecocean.api;

public class MediaResolveApi extends ApiBase {

    /** Max annotation IDs accepted per request. */
    static final int MAX_IDS = 100;

    /**
     * Scale an axis-aligned bbox from the source asset's pixel space into the returned
     * derivative's pixel space, then clamp to the derivative bounds.
     * Returns null if inputs are invalid or the scaled region is empty (<1px) — caller omits the entry.
     *
     * @param src  [x, y, width, height] in source-asset pixels
     */
    static int[] scaleBbox(int[] src, double srcW, double srcH, double dstW, double dstH) {
        if ((src == null) || (src.length < 4)) return null;
        if ((srcW <= 0) || (srcH <= 0) || (dstW <= 0) || (dstH <= 0)) return null;
        double sx = dstW / srcW;
        double sy = dstH / srcH;
        int maxW = (int) Math.floor(dstW);
        int maxH = (int) Math.floor(dstH);
        // Scale BOTH corners, clamp each corner to the derivative bounds, THEN derive w/h.
        // (Clamping only the origin and keeping the scaled w/h would mis-size a negative-origin box.)
        long x1 = clamp(Math.round(src[0] * sx), 0, maxW);
        long y1 = clamp(Math.round(src[1] * sy), 0, maxH);
        long x2 = clamp(Math.round(((long) src[0] + src[2]) * sx), 0, maxW);
        long y2 = clamp(Math.round(((long) src[1] + src[3]) * sy), 0, maxH);
        int w = (int) (x2 - x1);
        int h = (int) (y2 - y1);
        if ((w < 1) || (h < 1)) return null;
        return new int[] {(int) x1, (int) y1, w, h};
    }

    private static long clamp(long v, long lo, long hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: PASS (7 tests).

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
# if any nonzero: sed -i 's/\r$//' <that file>
git add src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git commit -m "media-resolve: bbox scaling helper into derivative pixel space

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: `selectSafeDerivative` — explicit `_master`→`_mid` selection

Codex High/Medium fix: do NOT use `safeURL`/`bestSafeAsset` (returns originals for `URLAssetStore`, returns `this` when self-labeled, and does not fall back master→mid). Select explicitly.

**Files:**
- Modify: `src/main/java/org/ecocean/api/MediaResolveApi.java`
- Test: `src/test/java/org/ecocean/api/MediaResolveApiTest.java`

- [ ] **Step 1: Write the failing test**

Add these imports to the test file (top, after the existing imports):

```java
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.URLAssetStore;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.shepherd.core.Shepherd;
```

Add these tests to the class:

```java
    private MediaAsset child(String label, boolean urlStore) {
        MediaAsset ma = mock(MediaAsset.class);
        ArrayList<String> labels = new ArrayList<>();
        labels.add(label);
        when(ma.getLabels()).thenReturn(labels);
        when(ma.hasLabel(label)).thenReturn(true);
        when(ma.getStore()).thenReturn(urlStore ? mock(URLAssetStore.class) : mock(LocalAssetStore.class));
        return ma;
    }

    private Annotation annWithSource(MediaAsset src) {
        Annotation ann = mock(Annotation.class);
        when(ann.getMediaAsset()).thenReturn(src);
        return ann;
    }

    @Test void selectSafeDerivative_prefersMaster() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset master = child("_master", false);
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(master);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset out = MediaResolveApi.selectSafeDerivative(annWithSource(src), sh);
        assertSame(master, out, "a _master child must be selected first");
    }

    @Test void selectSafeDerivative_fallsBackToMidWhenNoMaster() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(null);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        MediaAsset out = MediaResolveApi.selectSafeDerivative(annWithSource(src), sh);
        assertSame(mid, out, "must fall back to _mid when _master is absent (bestSafeAsset bug bypassed)");
    }

    @Test void selectSafeDerivative_nullWhenNoSafeDerivative() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(null);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(null);
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "no _master/_mid -> null (omit)");
    }

    @Test void selectSafeDerivative_rejectsUrlAssetStoreSource() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(URLAssetStore.class));
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "URLAssetStore source (external original) must be rejected");
    }

    @Test void selectSafeDerivative_skipsUrlStoreChildren() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset urlChild = child("_master", true); // _master label but URLAssetStore
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(urlChild);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(null);
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a non-local (URLAssetStore) child must be skipped");
    }

    @Test void selectSafeDerivative_rejectsYouTubeStoreSource() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(YouTubeAssetStore.class));
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "YouTubeAssetStore source (webURL is a watch page, not image bytes) must be rejected");
    }

    @Test void selectSafeDerivative_skipsMasterAlsoLabeledOriginal_fallsBackToMid() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset masterButOriginal = child("_master", false);
        when(masterButOriginal.hasLabel("_original")).thenReturn(true); // also carries _original
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(masterButOriginal);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        assertSame(mid, MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a _master child also labeled _original is skipped; falls back to _mid");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: FAIL — `selectSafeDerivative` not defined (compile error).

- [ ] **Step 3: Write minimal implementation**

Add to `MediaResolveApi.java` (inside the class). Add imports at the top of the file:

```java
import java.util.ArrayList;

import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.shepherd.core.Shepherd;
```

Method:

```java
    /**
     * Select the safe derivative to serve for an annotation's region: a child of the source asset
     * labeled _master (preferred) or _mid. Both the source and the chosen derivative must be backed
     * by a LocalAssetStore — an ALLOWLIST, not a denylist: this rejects URLAssetStore (external/public
     * originals) AND YouTubeAssetStore (webURL is a watch page, not cropable image bytes). Also skips
     * any child carrying _original. Returns null if none qualifies (caller omits).
     * Deliberately does NOT use MediaAsset.safeURL/bestSafeAsset, which can return originals for
     * URLAssetStore and does not fall back from a missing _master to _mid.
     */
    static MediaAsset selectSafeDerivative(Annotation ann, Shepherd myShepherd) {
        if (ann == null) return null;
        MediaAsset src = ann.getMediaAsset();
        if (src == null) return null;
        if (!(src.getStore() instanceof LocalAssetStore)) return null;
        for (String label : new String[] {"_master", "_mid"}) {
            ArrayList<MediaAsset> kids = src.findChildrenByLabel(myShepherd, label);
            if (kids == null) continue;
            for (MediaAsset kid : kids) {
                if (kid == null) continue;
                if (!(kid.getStore() instanceof LocalAssetStore)) continue;
                if (kid.hasLabel("_original")) continue;
                return kid;
            }
        }
        return null;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: PASS (14 tests total).

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git add src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git commit -m "media-resolve: explicit local-store _master/_mid derivative selection

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Widen `buildIdEligibilityQuery` to public

The visibility gate reuses `OpenSearch.buildIdEligibilityQuery(Set<String>)` (builds `{"query":{"ids":{"values":[...]}}}`). It is currently package-private in `org.ecocean`; `MediaResolveApi` is in `org.ecocean.api`, so it must be `public`.

**Files:**
- Modify: `src/main/java/org/ecocean/OpenSearch.java` (the `static JSONObject buildIdEligibilityQuery` declaration)

- [ ] **Step 1: Make the change**

Find:

```java
    // Package-visible for testing. Returns the _count-shaped query body that
    // filters on _id ∈ ids, using OpenSearch's idiomatic `ids` query.
    static JSONObject buildIdEligibilityQuery(Set<String> ids) {
```

Replace the comment + signature with:

```java
    // Returns a query body that filters on _id ∈ ids, using OpenSearch's idiomatic `ids` query.
    // Public so the media-resolve endpoint (org.ecocean.api) can reuse the exact id-eligibility shape.
    public static JSONObject buildIdEligibilityQuery(Set<String> ids) {
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q -o test-compile -DargLine="-Xmx2g" 2>&1 | tail -5` (offline compile; if `-o` fails due to missing deps, drop `-o`).
Expected: BUILD SUCCESS (no compile errors).

- [ ] **Step 3: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/OpenSearch.java
git add src/main/java/org/ecocean/OpenSearch.java
git commit -m "OpenSearch: make buildIdEligibilityQuery public for media-resolve reuse

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Endpoint skeleton — token-path enforcement + body validation + JSON writer

Codex Low fix: require `TOKEN_AUTH_ATTR`, use the filter-verified context, never fall back to a session. Validate the body before any DB/OpenSearch work.

**Files:**
- Modify: `src/main/java/org/ecocean/api/MediaResolveApi.java`
- Test: `src/test/java/org/ecocean/api/MediaResolveApiTest.java`

- [ ] **Step 1: Write the failing test**

Add imports to the test file:

```java
import static org.mockito.ArgumentMatchers.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.User;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
```

Add a nested helper + tests (these exercise `doPost`):

```java
    /** Build a token-auth request mock; body comes from the ServletUtilities static mock. */
    private HttpServletRequest tokenRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.TRUE);
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR)).thenReturn("context0");
        return req;
    }

    private User mockUser(String id, boolean admin) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    @Test void doPost_non_token_request_401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.FALSE);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        new MediaResolveApi().doPostForTest(req, resp);
        verify(resp).setStatus(401);
    }

    @Test void doPost_token_but_missing_context_401_noShepherd() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.TRUE);
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR)).thenReturn(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd may be constructed without a verified context");
        }
        verify(resp).setStatus(401);
    }

    @Test void doPost_empty_ids_400_noShepherd() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject("{\"annotationIds\":[]}"));
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "body validation must happen before any Shepherd/DB work");
        }
        verify(resp).setStatus(400);
    }

    @Test void doPost_over_max_ids_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        JSONArray big = new JSONArray();
        for (int i = 0; i < MediaResolveApi.MAX_IDS + 1; i++) big.put("id-" + i);
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", big));
            new MediaResolveApi().doPostForTest(req, resp);
        }
        verify(resp).setStatus(400);
    }

    @Test void doPost_malformed_body_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenThrow(new org.json.JSONException("bad json"));
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "malformed body must be rejected before Shepherd construction");
        }
        verify(resp).setStatus(400);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: FAIL — `doPostForTest` not defined / `doPost` logic absent.

- [ ] **Step 3: Write minimal implementation**

Add imports to `MediaResolveApi.java`:

```java
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
```

Add the servlet methods. `doPostForTest` is a thin package-visible delegate so tests can invoke the logic directly (mirrors how the servlet container would call `doPost`):

```java
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        handle(request, response);
    }

    // package-visible entry point for unit tests
    void doPostForTest(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        boolean tokenAuth = Boolean.TRUE.equals(
            request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR));
        if (!tokenAuth) {
            writeError(response, 401, "token auth required");
            return;
        }
        // Verified context ONLY — never fall back to ServletUtilities.getContext() (no session on
        // this endpoint, so a caller must not be able to steer context via ?context=/cookie/host).
        String context = (String) request.getAttribute(
            WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR);
        if (!Util.stringExists(context)) {
            writeError(response, 401, "unauthorized");
            return;
        }
        // Parse + validate the body BEFORE constructing a Shepherd or touching the DB/OpenSearch.
        // Malformed JSON throws here -> 400 (not the 500 a later broad catch would give).
        Set<String> ids;
        try {
            JSONObject body = ServletUtilities.jsonFromHttpServletRequest(request);
            JSONArray idArr = (body != null) ? body.optJSONArray("annotationIds") : null;
            if ((idArr == null) || (idArr.length() == 0) || (idArr.length() > MAX_IDS)) {
                writeError(response, 400, "annotationIds must be a non-empty array of <= " + MAX_IDS);
                return;
            }
            ids = new LinkedHashSet<>();
            for (int i = 0; i < idArr.length(); i++) {
                String s = idArr.optString(i, null);
                if (Util.stringExists(s)) ids.add(s);
            }
            if (ids.isEmpty()) {
                writeError(response, 400, "annotationIds must contain at least one valid id");
                return;
            }
        } catch (Exception ex) {
            writeError(response, 400, "malformed request body");
            return;
        }

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.MediaResolveApi.POST");
        myShepherd.beginDBTransaction();
        try {
            User currentUser = myShepherd.getUser(request);
            if ((currentUser == null) || (currentUser.getId() == null)) {
                writeError(response, 401, "unauthorized");
                return;
            }
            boolean isAdmin = currentUser.isAdmin(myShepherd);
            Set<String> visible = isAdmin ? ids : gatedVisibleIds(ids, currentUser.getId());
            JSONArray results = new JSONArray();
            for (String id : visible) {
                JSONObject entry = resolveOne(id, myShepherd);
                if (entry != null) results.put(entry);
            }
            response.setStatus(200);
            writeBody(response, results.toString());
        } catch (Exception ex) {
            response.setStatus(500);
            writeBody(response, new JSONObject().put("error", "resolve failed").toString());
            ex.printStackTrace();
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private void writeError(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        writeBody(response, new JSONObject().put("error", msg).toString());
    }

    private void writeBody(HttpServletResponse response, String s) throws IOException {
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(s);
        response.getWriter().close();
    }
```

This references `gatedVisibleIds` and `resolveOne`, added in Tasks 5–6. To compile now, add temporary stubs at the end of the class (they will be replaced):

```java
    private Set<String> gatedVisibleIds(Set<String> ids, String userId) throws IOException {
        return ids; // TEMP — replaced in Task 5
    }

    private JSONObject resolveOne(String annotationId, Shepherd myShepherd) {
        return null; // TEMP — replaced in Task 6
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: PASS (19 tests total). The validation/auth tests pass; the resolve loop is a no-op (stubs) for now.

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git add src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git commit -m "media-resolve: endpoint skeleton, token-path enforcement, body validation

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Visibility gate — reuse Spec A ACL filter, sized to ID count

Codex Medium fix: set query `size` to the de-duplicated ID count (SearchApi's default 10 would silently drop visible IDs beyond 10). Admin bypasses the gate (handled in Task 4).

**Files:**
- Modify: `src/main/java/org/ecocean/api/MediaResolveApi.java` (replace the `gatedVisibleIds` stub)
- Test: `src/test/java/org/ecocean/api/MediaResolveApiTest.java`

- [ ] **Step 1: Write the failing test**

Add to the test file. This mocks the constructed `OpenSearch` so no live cluster is needed, and proves the size guard (>10 ids) and that the visible subset is taken from the ACL-gated hits:

```java
    private JSONObject hitsFor(String... idsThatPass) {
        JSONArray hits = new JSONArray();
        for (String id : idsThatPass) hits.put(new JSONObject().put("_id", id));
        return new JSONObject().put("hits",
            new JSONObject().put("total", new JSONObject().put("value", idsThatPass.length))
                            .put("hits", hits));
    }

    @Test void gate_returns_only_acl_passing_ids_and_sizes_to_id_count() throws Exception {
        // 12 requested ids; OpenSearch (mocked) returns all 12 as visible -> proves size>=12, not 10.
        JSONArray req = new JSONArray();
        String[] all = new String[12];
        for (int i = 0; i < 12; i++) { all[i] = "ann-" + i; req.put(all[i]); }

        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        final int[] capturedSize = {-1};
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer", false);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 // resolveOne loads each annotation: return null so the loop is a no-op here
                 when(m.getAnnotation(anyString())).thenReturn(null);
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenAnswer(inv -> { capturedSize[0] = inv.getArgument(3); return hitsFor(all); });
                 })) {
            su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", req));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(12, capturedSize[0], "gate query size must equal the de-duplicated id count, not the default 10");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: FAIL — `capturedSize` stays -1 because the stub `gatedVisibleIds` never calls OpenSearch.

- [ ] **Step 3: Write minimal implementation**

Add import to `MediaResolveApi.java`:

```java
import org.ecocean.OpenSearch;
```

Replace the temporary `gatedVisibleIds` stub with:

```java
    /**
     * Visibility gate (non-admin): reuse Spec A's exact annotation ACL filter over an ids query.
     * Returns the subset of requested ids the token may see. Query size is set to the id count so
     * none are dropped by the default page size.
     */
    private Set<String> gatedVisibleIds(Set<String> ids, String userId) throws IOException {
        JSONObject query = OpenSearch.buildIdEligibilityQuery(ids);
        query = OpenSearch.applyAclFilter(query, userId, "annotation");
        OpenSearch os = new OpenSearch();
        os.deletePit("annotation");
        JSONObject res = os.queryPit("annotation", query, 0, ids.size(), null, null);
        Set<String> visible = new LinkedHashSet<>();
        JSONObject outer = (res != null) ? res.optJSONObject("hits") : null;
        JSONArray hits = (outer != null) ? outer.optJSONArray("hits") : null;
        if (hits != null) {
            for (int i = 0; i < hits.length(); i++) {
                JSONObject h = hits.optJSONObject(i);
                if (h == null) continue;
                String hid = h.optString("_id", null);
                if (Util.stringExists(hid)) visible.add(hid);
            }
        }
        return visible;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: PASS (14 tests total).

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git add src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git commit -m "media-resolve: ACL visibility gate reusing Spec A filter, sized to id count

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Per-ID resolution + response assembly + omission

Wires the helpers (`selectSafeDerivative`, `scaleBbox`) and metadata into one response entry; fail-closed omission on any missing piece. Codex Medium: `encounterId`/`individualId` use `findEncounter`'s first parent (documented multi-parent behavior).

**Files:**
- Modify: `src/main/java/org/ecocean/api/MediaResolveApi.java` (replace the `resolveOne` stub)
- Test: `src/test/java/org/ecocean/api/MediaResolveApiTest.java`

- [ ] **Step 1: Write the failing test**

Add imports to the test file:

```java
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.media.LocalAssetStore;
```

Add tests:

```java
    /** A fully-populated annotation whose 1000x1000 source has a 500x500 _master derivative. */
    private Annotation fullAnnotation(String id, String encId, String indId) throws Exception {
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.getWidth()).thenReturn(1000.0);
        when(src.getHeight()).thenReturn(1000.0);

        MediaAsset master = mock(MediaAsset.class);
        when(master.getStore()).thenReturn(mock(LocalAssetStore.class));
        ArrayList<String> labels = new ArrayList<>(); labels.add("_master");
        when(master.getLabels()).thenReturn(labels);
        when(master.hasLabel("_master")).thenReturn(true);
        when(master.hasLabel("_original")).thenReturn(false);
        when(master.getWidth()).thenReturn(500.0);
        when(master.getHeight()).thenReturn(500.0);
        when(master.webURL()).thenReturn(new URL("https://h/wildbook_data_dir/x/" + id + "-master.jpg"));
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(master);

        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn(id);
        when(ann.getMediaAsset()).thenReturn(src);
        when(src.findChildrenByLabel(any(Shepherd.class), eq("_master"))).thenReturn(masters);
        when(ann.getBbox()).thenReturn(new int[] {100, 200, 300, 400});
        when(ann.getTheta()).thenReturn(0.0);
        when(ann.getViewpoint()).thenReturn("up");
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn(encId);
        when(ann.findEncounter(any(Shepherd.class))).thenReturn(enc);
        when(ann.findIndividualId(any(Shepherd.class))).thenReturn(indId);
        when(ann.getEmbeddings()).thenReturn(null);
        return ann;
    }

    @Test void resolve_payload_scaledBbox_and_derivativeUrl() throws Exception {
        Annotation ann = fullAnnotation("ann-A", "enc-A", "ind-A");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin", true);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true); // admin path: no OpenSearch gate
                 when(m.getAnnotation("ann-A")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-A")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "one entry resolved");
        JSONObject e = arr.getJSONObject(0);
        assertEquals("ann-A", e.getString("id"), "id echoed");
        assertTrue(e.getString("imageUrl").endsWith("-master.jpg"), "serves the _master derivative url");
        assertEquals(500, e.getInt("imageWidth"), "imageWidth is the derivative width");
        assertEquals(500, e.getInt("imageHeight"), "imageHeight is the derivative height");
        // src 1000x1000 -> dst 500x500 is 0.5x: [100,200,300,400] -> [50,100,150,200]
        assertEquals("[50,100,150,200]", e.getJSONArray("bbox").toString(),
            "bbox scaled into derivative pixel space, not raw source coords");
        assertEquals("up", e.getString("viewpoint"), "viewpoint passed through");
        assertEquals("enc-A", e.getString("encounterId"), "first-parent encounter id");
        assertEquals("ind-A", e.getString("individualId"), "individual id");
    }

    @Test void resolve_omits_unresolvable_and_is_not_an_existence_oracle() throws Exception {
        Annotation good = fullAnnotation("ann-good", "enc-1", "ind-1");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin", true);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-good")).thenReturn(good);
                 when(m.getAnnotation("ann-garbage")).thenReturn(null); // nonexistent
             })) {
            su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds",
                  new JSONArray().put("ann-good").put("ann-garbage")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "garbage/nonexistent id is silently absent (no existence oracle)");
        assertEquals("ann-good", arr.getJSONObject(0).getString("id"), "only the resolvable id returned");
    }

    @Test void resolve_dedups_repeated_ids() throws Exception {
        Annotation good = fullAnnotation("ann-d", "enc-d", "ind-d");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin", true);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-d")).thenReturn(good);
             })) {
            su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds",
                  new JSONArray().put("ann-d").put("ann-d").put("ann-d")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "duplicate ids collapse to a single entry");
    }

    @Test void resolve_nonAdmin_onlyGateVisibleResolved_noOracle_andNoLoadForHidden() throws Exception {
        // Non-admin: OpenSearch gate returns ONLY "ann-vis". Hidden (real-but-invisible) and garbage
        // ids must be absent AND must never trigger a Shepherd.getAnnotation load.
        Annotation vis = fullAnnotation("ann-vis", "enc-v", "ind-v");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer", false);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 when(m.getAnnotation("ann-vis")).thenReturn(vis);
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenReturn(hitsFor("ann-vis")); // only ann-vis passes the ACL gate
                 })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds",
                  new JSONArray().put("ann-vis").put("ann-hidden").put("ann-garbage")));
            new MediaResolveApi().doPostForTest(request, resp);
            Shepherd constructed = sh.constructed().get(0);
            verify(constructed, never()).getAnnotation("ann-hidden");
            verify(constructed, never()).getAnnotation("ann-garbage");
        }
        verify(resp).setStatus(200);
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "non-admin sees only gate-passed ids; hidden/garbage absent (no oracle)");
        assertEquals("ann-vis", arr.getJSONObject(0).getString("id"), "only the visible id resolved");
    }

    @Test void resolve_nonAdmin_moreThan10VisibleAllResolve() throws Exception {
        // 12 visible ids -> all 12 must appear in the response (proves no default-10 truncation end-to-end).
        JSONArray reqIds = new JSONArray();
        String[] all = new String[12];
        for (int i = 0; i < 12; i++) { all[i] = "ann-" + i; reqIds.put(all[i]); }
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer", false);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 when(m.getAnnotation(anyString())).thenAnswer(inv -> {
                     String id = inv.getArgument(0);
                     return fullAnnotation(id, "enc-" + id, "ind-" + id);
                 });
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenReturn(hitsFor(all));
                 })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", reqIds));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(12, new JSONArray(out.toString()).length(), "all 12 visible ids resolve (no default-10 truncation)");
    }

    @Test void resolve_thetaDefault_and_nullViewpoint() throws Exception {
        Annotation ann = fullAnnotation("ann-t", "enc-t", "ind-t");
        when(ann.getViewpoint()).thenReturn(null);
        when(ann.getTheta()).thenReturn(0.0);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin", true);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-t")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-t")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONObject e = new JSONArray(out.toString()).getJSONObject(0);
        assertTrue(e.isNull("viewpoint"), "null viewpoint serialized as JSON null");
        assertEquals(0.0, e.getDouble("theta"), 0.0001, "theta defaults to 0.0");
    }

    @Test void resolve_methodVersion_dedupAndOrder() throws Exception {
        Annotation ann = fullAnnotation("ann-m", "enc-m", "ind-m");
        Embedding e1 = mock(Embedding.class); when(e1.getMethodVersion()).thenReturn("msv4.1");
        Embedding e2 = mock(Embedding.class); when(e2.getMethodVersion()).thenReturn("msv4.1");
        Embedding e3 = mock(Embedding.class); when(e3.getMethodVersion()).thenReturn("msv3");
        Set<Embedding> embs = new LinkedHashSet<>(); embs.add(e1); embs.add(e2); embs.add(e3);
        when(ann.getEmbeddings()).thenReturn(embs);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin", true);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-m")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-m")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONArray mvs = new JSONArray(out.toString()).getJSONObject(0).getJSONArray("methodVersion");
        assertEquals("[\"msv4.1\",\"msv3\"]", mvs.toString(), "method versions de-duplicated, first-seen order");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: FAIL — `resolveOne` stub returns null, so `resolve_payload_*` and the new resolution tests get empty arrays.

- [ ] **Step 3: Write minimal implementation**

Add imports to `MediaResolveApi.java`:

```java
import java.net.URL;

import org.ecocean.Embedding;
import org.ecocean.Encounter;
```

Replace the temporary `resolveOne` stub with:

```java
    /**
     * Resolve one visible annotation into a response entry, or null to omit (fail-closed) when any
     * required piece is missing: annotation, source asset, dimensions, safe derivative, bbox, or url.
     * encounterId/individualId use findEncounter's first parent (documented multi-parent behavior;
     * multi-parent annotations are admin-only in the index so a non-admin never reaches here for them).
     */
    private JSONObject resolveOne(String annotationId, Shepherd myShepherd) {
        Annotation ann = myShepherd.getAnnotation(annotationId);
        if (ann == null) return null;
        MediaAsset src = ann.getMediaAsset();
        if (src == null) return null;
        double srcW = src.getWidth();
        double srcH = src.getHeight();
        if ((srcW <= 0) || (srcH <= 0)) return null;
        MediaAsset deriv = selectSafeDerivative(ann, myShepherd);
        if (deriv == null) return null;
        double dstW = deriv.getWidth();
        double dstH = deriv.getHeight();
        if ((dstW <= 0) || (dstH <= 0)) return null;
        int[] scaled = scaleBbox(ann.getBbox(), srcW, srcH, dstW, dstH);
        if (scaled == null) return null;
        URL url = deriv.webURL();
        if (url == null) return null;

        JSONObject e = new JSONObject();
        e.put("id", ann.getId());
        e.put("imageUrl", url.toString());
        e.put("imageWidth", (int) dstW);
        e.put("imageHeight", (int) dstH);
        JSONArray bb = new JSONArray();
        for (int v : scaled) bb.put(v);
        e.put("bbox", bb);
        e.put("theta", ann.getTheta());
        String vp = ann.getViewpoint();
        e.put("viewpoint", (vp != null) ? vp : JSONObject.NULL);
        Encounter enc = ann.findEncounter(myShepherd);
        e.put("encounterId", (enc != null) ? enc.getId() : JSONObject.NULL);
        String indId = ann.findIndividualId(myShepherd);
        e.put("individualId",
            (Util.stringExists(indId) && !"None".equalsIgnoreCase(indId)) ? indId : JSONObject.NULL);
        JSONArray mvs = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (ann.getEmbeddings() != null) {
            for (Embedding emb : ann.getEmbeddings()) {
                String mv = (emb != null) ? emb.getMethodVersion() : null;
                if (Util.stringExists(mv) && seen.add(mv)) mvs.put(mv);
            }
        }
        e.put("methodVersion", mvs);
        return e;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run the `mvn test -Dtest=MediaResolveApiTest ...` command.
Expected: PASS (27 tests total).

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git add src/main/java/org/ecocean/api/MediaResolveApi.java src/test/java/org/ecocean/api/MediaResolveApiTest.java
git commit -m "media-resolve: per-id resolution, scaled-bbox payload, fail-closed omission

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Register the servlet + Shiro token rule in web.xml (+ wiring test)

**Files:**
- Modify: `src/main/webapp/WEB-INF/web.xml`
- Modify: `src/test/java/org/ecocean/api/EndpointAuthWiringTest.java`

- [ ] **Step 1: Write the failing test**

Add to `EndpointAuthWiringTest.java` (mirror the existing `authToken_*` assertions — they assert against the raw web.xml text via the test's existing `fullText()` helper):

```java
    @Test
    void mediaResolve_servletClassIsRegistered() {
        assertTrue(fullText().contains("<servlet-class>org.ecocean.api.MediaResolveApi</servlet-class>"),
                "web.xml must register the MediaResolveApi servlet");
    }

    @Test
    void mediaResolve_urlPatternIsRegistered() {
        assertTrue(fullText().contains("<url-pattern>/api/v3/media/resolve</url-pattern>"),
                "web.xml must map /api/v3/media/resolve");
    }

    @Test
    void mediaResolve_shiroRuleIsTokenFilterOnly() {
        // Mirror searchPath_wiredToTokenFilterOnly: exact value equality, not a loose contains().
        String ruleLine = lines.stream()
                .filter(l -> {
                    String t = l.stripLeading();
                    return !t.startsWith("#") && t.contains("/api/v3/media/**");
                })
                .findFirst().orElse(null);
        assertNotNull(ruleLine, "Shiro [urls] must contain a rule for /api/v3/media/**");
        String value = ruleLine.substring(
                ruleLine.indexOf("/api/v3/media/**") + "/api/v3/media/**".length()).trim();
        if (value.startsWith("=")) value = value.substring(1).trim();
        assertEquals("tokenAuthSearch", value,
                "media path must map to tokenAuthSearch ONLY (no authc/roles chained); was: '" + value + "'");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EndpointAuthWiringTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"`
Expected: FAIL (3 new assertions) — web.xml has no media entries yet.

- [ ] **Step 3: Make the web.xml changes**

(a) In the Shiro `[urls]` block, immediately after the line `/api/v3/search/** = tokenAuthSearch`, add:

```
				/api/v3/media/** = tokenAuthSearch
```

(b) In the servlet-registration section (near the other `org.ecocean.api.*` servlets, e.g. after the `ApiUserInfo` servlet+mapping), add:

```xml
	<servlet>
		<servlet-name>MediaResolveApi</servlet-name>
		<servlet-class>org.ecocean.api.MediaResolveApi</servlet-class>
	</servlet>
	<servlet-mapping>
		<servlet-name>MediaResolveApi</servlet-name>
		<url-pattern>/api/v3/media/resolve</url-pattern>
	</servlet-mapping>
```

- [ ] **Step 4: Run test to verify it passes**

Run the `mvn test -Dtest=EndpointAuthWiringTest ...` command.
Expected: PASS (existing + 3 new).

- [ ] **Step 5: Normalize + commit**

```bash
grep -c $'\r' src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/EndpointAuthWiringTest.java
git add src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/EndpointAuthWiringTest.java
git commit -m "media-resolve: register servlet + token-gated shiro rule for /api/v3/media

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Runbook smoke-test addendum

**Files:**
- Modify: `docs/superpowers/runbooks/childindex-acl-reindex.md`

- [ ] **Step 1: Append the smoke test**

Add this section to the end of `docs/superpowers/runbooks/childindex-acl-reindex.md`:

````markdown
---

## 6. Media-resolve smoke test (Spec: token-scoped media resolve)

No reindex is needed for `POST /api/v3/media/resolve` (no new index fields). After deploying the
endpoint, verify with tokens (see `jwt-keypair-setup.md` + `POST /api/v3/auth/token`):

```bash
# admin token: resolve the two salamander missed-match candidate annotations -> image url + scaled bbox
curl -s -X POST "$HOST/api/v3/media/resolve" -H "Authorization: Bearer $ADMIN_TOK" \
  -H 'Content-Type: application/json' \
  -d '{"annotationIds":["<ann-BGBI_22-168>","<ann-BGBI_23-2716>"]}'
# -> 200, JSON array with 2 entries: imageUrl (…-master.jpg or …-mid.jpg), imageWidth/Height,
#    bbox in that image's pixel space, theta, viewpoint, encounterId, individualId, methodVersion.
# Fetch each imageUrl and confirm it returns image bytes (HTTP 200).

# non-admin token: an annotation whose parent encounter the user cannot see resolves to empty
curl -s -X POST "$HOST/api/v3/media/resolve" -H "Authorization: Bearer $RESEARCHER_TOK" \
  -H 'Content-Type: application/json' -d '{"annotationIds":["<private-annotation-id>"]}'
# -> 200 with [] (silently absent; indistinguishable from a nonexistent id — no existence oracle)

# no token -> 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$HOST/api/v3/media/resolve" \
  -H 'Content-Type: application/json' -d '{"annotationIds":["x"]}'
# -> 401
```

Sanity-check the bbox: crop the returned `imageUrl` to `bbox` — it should frame the animal region the
embedding was computed from. The bbox is already in the returned image's pixel space (no client
scaling needed); `imageWidth`/`imageHeight` are provided for verification.
````

- [ ] **Step 2: Normalize + commit**

```bash
grep -c $'\r' docs/superpowers/runbooks/childindex-acl-reindex.md
git add docs/superpowers/runbooks/childindex-acl-reindex.md
git commit -m "media-resolve: add live smoke-test to childindex-acl-reindex runbook

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification (after all tasks)

- [ ] Run the full new test class once more, plus the wiring test:
  ```
  mvn test -Dtest=MediaResolveApiTest,EndpointAuthWiringTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -Xmx2g"
  ```
  Expected: all green.
- [ ] `git log --oneline` shows 8 focused commits.
- [ ] Confirm no temporary stubs remain in `MediaResolveApi.java` (grep for `TEMP`).
- [ ] Hand off to Codex code review (per the user's standing rule) before any PR/merge.
- [ ] Do NOT push or open a PR — the user does that explicitly.
```
