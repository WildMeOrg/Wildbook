# AcmIdBot Reconciliation Sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace AcmIdBot's 24-hour Query 2 with a cursor-paged sweep of all matchable MediaAssets that probes WBIA for unknown acmIds and heals them.

**Architecture:** Three-phase sweep inside the existing 15-minute AcmIdBot scheduler: (1) read phase collects one page of distinct asset ids/acmIds from a JDOQL query over `matchAgainst==true` annotations, (2) probe phase asks WBIA `GET /api/image/rowid/uuid/` in 50-UUID chunks which acmIds it doesn't know, (3) heal phase re-registers missing assets via the existing `sendMediaAssets` (which sends Wildbook-assigned `image_uuid_list`) with explicit transaction commits. An in-memory cursor pages ~10k assets per run and wraps around.

**Tech Stack:** Java 11, DataNucleus JDO (JDOQL string queries), org.json, JUnit 5 (Jupiter), Maven surefire.

**Spec:** `docs/superpowers/specs/2026-07-01-acmidbot-reconciliation-sweep-design.md` (rev 2 — read it before starting any task).

## Global Constraints

- Working directory: `/mnt/c/Wildbook-clean2/.claude/worktrees/acmidbot-sweep` (git worktree, branch `feature/acmidbot-reconciliation-sweep`). NEVER run `git checkout`, `git branch`, `git reset`, or `git stash`.
- Constants (exact values from spec): `SWEEP_PAGE_SIZE = 10000`, `PROBE_CHUNK_SIZE = 50`, `PAGE_FAIL_LIMIT = 3`, `maxFixes = 500` (existing local in `fixAcmIds`).
- Query 1 (ImportTask fast-path) in `AcmIdBot.fixAcmIds()` stays untouched.
- JUnit 5 assertion message goes LAST: `assertTrue(cond, "msg")`.
- Before every commit: `grep -c $'\r' <file>` must print `0` for each staged file (CRLF check); if not, `sed -i 's/\r$//' <file>`.
- Compile check command (backend only, ~2–4 min on WSL): `mvn -q compile 2>&1 | tail -20` — expect no `ERROR` lines.
- Single-class test command: `mvn test -Dtest=<ClassName> 2>&1 | tail -25` (pom's surefire argLine already carries the needed `--add-opens`).
- Match surrounding code style: `System.out.println` + `IA.log` logging, C-style Java, no lambdas in AcmIdBot (keep its existing idiom), generics OK.

---

### Task 1: WBIA probe pure helpers (`chunkList`, `parseRowidProbeResponse`)

**Files:**
- Modify: `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java` (add two package-visible static helpers + one constant, near `parseImageIdsArrayStrict` ~line 637)
- Test: `src/test/java/org/ecocean/ia/plugin/WildbookIAMRowidProbeParseTest.java` (create)

**Interfaces:**
- Consumes: nothing new (org.json only).
- Produces: `static <T> List<List<T>> WildbookIAM.chunkList(List<T> items, int size)`; `static List<String> WildbookIAM.parseRowidProbeResponse(List<String> chunkAcmIds, JSONArray response) throws IOException`; `static final int WildbookIAM.PROBE_CHUNK_SIZE = 50`. Task 2 calls all three.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/ia/plugin/WildbookIAMRowidProbeParseTest.java`:

```java
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the pure helpers behind
 * {@link WildbookIAM#iaMissingImageIds(java.util.List, String)}:
 * {@link WildbookIAM#chunkList(List, int)} and
 * {@link WildbookIAM#parseRowidProbeResponse(List, JSONArray)}.
 * The WBIA endpoint /api/image/rowid/uuid/ returns a rowid per requested
 * image UUID, with JSON null where the UUID is unknown; null entries mark
 * the acmId as missing from WBIA. (AcmIdBot reconciliation sweep spec §3.)
 */
class WildbookIAMRowidProbeParseTest {

    // ---------- chunkList ----------

    @Test void chunkListSplitsAtExactBoundary() {
        List<String> in = new ArrayList<String>();
        for (int i = 0; i < 100; i++) in.add("u" + i);
        List<List<String>> out = WildbookIAM.chunkList(in, 50);
        assertEquals(2, out.size());
        assertEquals(50, out.get(0).size());
        assertEquals(50, out.get(1).size());
        assertEquals("u0", out.get(0).get(0));
        assertEquals("u99", out.get(1).get(49));
    }

    @Test void chunkListHandlesRemainderAndSmallInput() {
        List<List<String>> out = WildbookIAM.chunkList(Arrays.asList("a", "b", "c"), 2);
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).size());
        assertEquals(1, out.get(1).size());
        out = WildbookIAM.chunkList(Arrays.asList("solo"), 50);
        assertEquals(1, out.size());
        assertEquals("solo", out.get(0).get(0));
    }

    @Test void chunkListEmptyOrNullOrBadSizeReturnsEmpty() {
        assertEquals(0, WildbookIAM.chunkList(new ArrayList<String>(), 50).size());
        assertEquals(0, WildbookIAM.chunkList(null, 50).size());
        assertEquals(0, WildbookIAM.chunkList(Arrays.asList("a"), 0).size());
    }

    // ---------- parseRowidProbeResponse ----------

    @Test void nullRowidEntriesAreMissing() throws IOException {
        List<String> chunk = Arrays.asList("aaa", "bbb", "ccc");
        JSONArray resp = new JSONArray();
        resp.put(101);
        resp.put(org.json.JSONObject.NULL);
        resp.put(303);
        List<String> missing = WildbookIAM.parseRowidProbeResponse(chunk, resp);
        assertEquals(1, missing.size());
        assertEquals("bbb", missing.get(0));
    }

    @Test void allKnownReturnsEmpty() throws IOException {
        List<String> chunk = Arrays.asList("aaa", "bbb");
        JSONArray resp = new JSONArray();
        resp.put(1);
        resp.put(2);
        assertEquals(0, WildbookIAM.parseRowidProbeResponse(chunk, resp).size());
    }

    @Test void lengthMismatchThrows() {
        List<String> chunk = Arrays.asList("aaa", "bbb");
        JSONArray resp = new JSONArray();
        resp.put(1);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseRowidProbeResponse(chunk, resp));
        assertTrue(ex.getMessage().contains("length"),
            "message should mention length: " + ex.getMessage());
    }

    @Test void nullResponseThrows() {
        List<String> chunk = Arrays.asList("aaa");
        assertThrows(IOException.class,
            () -> WildbookIAM.parseRowidProbeResponse(chunk, null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WildbookIAMRowidProbeParseTest 2>&1 | tail -25`
Expected: COMPILATION ERROR — `cannot find symbol: method chunkList` / `parseRowidProbeResponse`.

- [ ] **Step 3: Write minimal implementation**

In `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java`, directly after the `parseImageIdsArrayStrict` method (~line 638), add:

```java
    /**
     * Chunk size for the /api/image/rowid/uuid/ existence probe. 50 fancy
     * UUIDs is ~3.5 KB of query string — safely under common proxy URL
     * limits. (AcmIdBot reconciliation sweep spec §3/§6.)
     */
    static final int PROBE_CHUNK_SIZE = 50;

    /**
     * Split a list into consecutive sublists of at most {@code size}
     * elements, preserving order. Returns an empty list for null/empty
     * input or a non-positive size.
     */
    static <T> java.util.List<java.util.List<T>> chunkList(java.util.List<T> items, int size) {
        java.util.List<java.util.List<T>> out = new ArrayList<java.util.List<T>>();

        if ((items == null) || items.isEmpty() || (size < 1)) return out;
        for (int i = 0; i < items.size(); i += size) {
            out.add(new ArrayList<T>(items.subList(i, Math.min(items.size(), i + size))));
        }
        return out;
    }

    /**
     * Interpret one /api/image/rowid/uuid/ response chunk. WBIA returns a
     * rowid per requested UUID, with JSON null where the UUID is unknown;
     * those unknown acmIds are returned. Throws IOException on a null
     * response or a request/response length mismatch so a malformed reply
     * is treated as a failed probe, never as "all present". (AcmIdBot
     * reconciliation sweep spec §3.)
     */
    static List<String> parseRowidProbeResponse(List<String> chunkAcmIds, JSONArray response)
    throws IOException {
        if (response == null)
            throw new IOException("rowid probe returned null response for chunk of " +
                    chunkAcmIds.size());
        if (response.length() != chunkAcmIds.size())
            throw new IOException("rowid probe response length " + response.length() +
                    " != request length " + chunkAcmIds.size());
        List<String> missing = new ArrayList<String>();
        for (int i = 0; i < response.length(); i++) {
            if (response.isNull(i)) missing.add(chunkAcmIds.get(i));
        }
        return missing;
    }
```

Note: `WildbookIAM.java` already imports `java.io.IOException`, `java.util.ArrayList`, `java.util.List`, and `org.json.JSONArray` — verify with `grep -n "^import" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java` and add any of those that are absent.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WildbookIAMRowidProbeParseTest 2>&1 | tail -25`
Expected: `Tests run: 7, Failures: 0, Errors: 0` — BUILD SUCCESS.

- [ ] **Step 5: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMRowidProbeParseTest.java
# each line must end ":0"; if not: sed -i 's/\r$//' <file>
git add src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMRowidProbeParseTest.java
git commit -m "feat: pure helpers for WBIA image-uuid existence probe

chunkList + parseRowidProbeResponse interpret /api/image/rowid/uuid/
responses (null rowid = unknown acmId); malformed replies throw rather
than reading as all-present. Sweep spec §3.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: `iaMissingImageIds` probe method

**Files:**
- Modify: `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java` (add one method right after `parseRowidProbeResponse` from Task 1)

**Interfaces:**
- Consumes: `chunkList`, `parseRowidProbeResponse`, `PROBE_CHUNK_SIZE` (Task 1); existing `apiGetJSONArray(String, String)` and `toFancyUUID(String)`.
- Produces: `public static List<String> WildbookIAM.iaMissingImageIds(List<String> acmIds, String context) throws IOException` — Task 6 calls this. Skips null entries; returns the subset of acmIds WBIA does not know; throws IOException if any chunk fails (caller must treat the whole probe as failed).

No new unit test: the branching logic lives in the Task 1 helpers (already tested); this method is HTTP orchestration following the existing `iaImageIds()` idiom, verified by compile + review.

- [ ] **Step 1: Write the implementation**

Directly after `parseRowidProbeResponse` in `WildbookIAM.java`, add:

```java
    /**
     * Ask WBIA which of the given image acmIds it does NOT have, via
     * GET /api/image/rowid/uuid/ in {@link #PROBE_CHUNK_SIZE} chunks
     * (a null rowid in the response marks that UUID unknown — see
     * wildbook-ia get_image_gids_from_uuid). Null acmIds are skipped
     * (callers treat those assets as heal candidates without probing).
     * Throws IOException if any chunk fails so callers never mistake a
     * failed probe for "all present". (AcmIdBot sweep spec §3.)
     */
    public static List<String> iaMissingImageIds(List<String> acmIds, String context)
    throws IOException {
        List<String> missing = new ArrayList<String>();

        if (acmIds == null) return missing;
        List<String> probeable = new ArrayList<String>();
        for (String acmId : acmIds) {
            if (acmId != null) probeable.add(acmId);
        }
        for (List<String> chunk : chunkList(probeable, PROBE_CHUNK_SIZE)) {
            StringBuilder sb = new StringBuilder();
            for (String acmId : chunk) {
                if (sb.length() > 0) sb.append(",");
                sb.append(toFancyUUID(acmId).toString());
            }
            JSONArray resp = null;
            try {
                resp = apiGetJSONArray("/api/image/rowid/uuid/?uuid_list=[" + sb.toString() +
                    "]", context);
            } catch (Exception ex) {
                throw new IOException("WBIA /api/image/rowid/uuid/ probe failed: " +
                        ex.getMessage(), ex);
            }
            // apiGetJSONArray returns null when status.success is false or
            // the payload is unparseable; parseRowidProbeResponse throws on it
            missing.addAll(parseRowidProbeResponse(chunk, resp));
        }
        return missing;
    }
```

- [ ] **Step 2: Compile and re-run Task 1 tests (regression)**

Run: `mvn test -Dtest=WildbookIAMRowidProbeParseTest 2>&1 | tail -15`
Expected: compiles, `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 3: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java   # expect 0
git add src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
git commit -m "feat: iaMissingImageIds — batch WBIA existence probe by acmId

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: `IBEISIA.sendMediaAssetsNew` checkFirst overload

**Files:**
- Modify: `src/main/java/org/ecocean/identity/IBEISIA.java:3677-3683` (the existing 2-arg `sendMediaAssetsNew`)

**Interfaces:**
- Consumes: existing `getPluginInstance(context)` (IBEISIA.java:3671) and `WildbookIAM.sendMediaAssets(ArrayList<MediaAsset>, boolean)`.
- Produces: `public static JSONObject IBEISIA.sendMediaAssetsNew(ArrayList<MediaAsset> mas, String context, boolean checkFirst)` — Task 6's heal loop calls it with `checkFirst=false`. The 2-arg form keeps its exact current behavior (`checkFirst=true`).

- [ ] **Step 1: Replace the 2-arg method with a delegating pair**

Current code at IBEISIA.java:3677:

```java
    public static JSONObject sendMediaAssetsNew(ArrayList<MediaAsset> mas, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        WildbookIAM plugin = getPluginInstance(context);

        return plugin.sendMediaAssets(mas, true);
    }
```

Replace with:

```java
    public static JSONObject sendMediaAssetsNew(ArrayList<MediaAsset> mas, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return sendMediaAssetsNew(mas, context, true);
    }

    // checkFirst=false skips the full iaImageIds() existence pre-check; use when
    // the caller has already established the assets are missing from WBIA
    // (e.g. AcmIdBot sweep probe). (AcmIdBot sweep spec §4.)
    public static JSONObject sendMediaAssetsNew(ArrayList<MediaAsset> mas, String context,
        boolean checkFirst)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        WildbookIAM plugin = getPluginInstance(context);

        return plugin.sendMediaAssets(mas, checkFirst);
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile 2>&1 | tail -20`
Expected: no `ERROR` lines (BUILD SUCCESS or silent).

- [ ] **Step 3: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/identity/IBEISIA.java   # expect 0
git add src/main/java/org/ecocean/identity/IBEISIA.java
git commit -m "feat: sendMediaAssetsNew overload with explicit checkFirst

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Commit healed acmIds in `fixFeats` (Critical rollback bug)

**Files:**
- Modify: `src/main/java/org/ecocean/AcmIdBot.java:53-60` (inside `fixFeats`)

**Interfaces:**
- Consumes: existing `Shepherd.updateDBTransaction()` (= commit + begin, Shepherd.java:3388).
- Produces: no new API. Behavior fix only: acmIds written by `AcmUtil.rectifyMediaAssetIds` during a Query-1 heal now survive the `rollbackAndClose()` at the end of `fixAcmIds`.

Background (spec "Verified facts"): `fixAcmIds()` ends with `myShepherd.rollbackAndClose()`; `fixFeats` only calls `updateDBTransaction()` in its validate branch. An asset healed without passing through that branch gets its new acmId rolled back. No practical unit test without a DB — this is a reviewed one-line transactional fix.

- [ ] **Step 1: Add the commit after a successful send**

In `fixFeats`, current code (AcmIdBot.java:53-60):

```java
                        IBEISIA.sendMediaAssetsNew(fixMe, context);
                        numAcmIdFixesSent++;
                        if (asset.getAcmId() != null) {
                            numAcmIdFixesSuccessful++;
```

Replace with:

```java
                        IBEISIA.sendMediaAssetsNew(fixMe, context);
                        numAcmIdFixesSent++;
                        // commit now: rectifyMediaAssetIds set acmId on this asset, and
                        // fixAcmIds() ends with rollbackAndClose() which would discard it
                        myShepherd.updateDBTransaction();
                        if (asset.getAcmId() != null) {
                            numAcmIdFixesSuccessful++;
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile 2>&1 | tail -20`
Expected: no `ERROR` lines.

- [ ] **Step 3: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/AcmIdBot.java   # expect 0
git add src/main/java/org/ecocean/AcmIdBot.java
git commit -m "fix: commit healed acmIds in AcmIdBot.fixFeats

fixAcmIds() ends with rollbackAndClose(); without an explicit commit
after sendMediaAssetsNew, the acmId written by rectifyMediaAssetIds
could be silently rolled back. Sweep spec Critical finding #1.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Sweep page collection + cursor pure helpers

**Files:**
- Modify: `src/main/java/org/ecocean/AcmIdBot.java` (add constants, static state, `SweepPage` inner class, three package-visible static helpers — place them between `fixFeats` and `startServices`)
- Test: `src/test/java/org/ecocean/AcmIdBotSweepTest.java` (create)

**Interfaces:**
- Consumes: `MediaAsset` (constructed in tests as `new MediaAsset(id, null, null)` — existing test precedent constructs MediaAssets directly; note this constructor defaults `acmId = getUUID()`, so tests use `setAcmId(null)` to simulate legacy rows, `setIsValidImageForIA(false)` for known-invalid).
- Produces (Task 6 consumes all of these):
  - `static final int AcmIdBot.SWEEP_PAGE_SIZE = 10000`
  - `static final int AcmIdBot.PAGE_FAIL_LIMIT = 3`
  - `static int AcmIdBot.sweepCursor` / `static int AcmIdBot.sweepFailCount` (package-visible mutable state)
  - `static class AcmIdBot.SweepPage { List<Integer> nullAcmAssetIds; LinkedHashMap<String,Integer> acmIdToAssetId; boolean rawExhausted; int lastAssetId; }`
  - `static SweepPage AcmIdBot.collectSweepPage(Iterator<MediaAsset> assetsInIdOrder, int pageSize)`
  - `static int AcmIdBot.nextCursorAfterSuccess(SweepPage page, boolean maxFixesHit, int resumeAssetId)`
  - `static boolean AcmIdBot.shouldSkipPoisonedPage(int consecutiveFailures)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/AcmIdBotSweepTest.java`:

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage of the AcmIdBot reconciliation sweep helpers
 * (spec docs/superpowers/specs/2026-07-01-acmidbot-reconciliation-sweep-design.md):
 * page collection (dedup, bucketing, exhaustion), cursor advancement
 * (wrap-around, maxFixes clamp), and the poisoned-page skip threshold.
 */
class AcmIdBotSweepTest {

    private static MediaAsset asset(int id) {
        // constructor defaults acmId to the asset's own UUID
        return new MediaAsset(id, null, null);
    }

    private static MediaAsset nullAcmAsset(int id) {
        MediaAsset ma = asset(id);

        ma.setAcmId(null); // legacy row: never assigned
        return ma;
    }

    private static MediaAsset invalidAsset(int id) {
        MediaAsset ma = asset(id);

        ma.setIsValidImageForIA(false);
        return ma;
    }

    // ---------- collectSweepPage ----------

    @Test void bucketsNullAcmSeparatelyFromProbeable() {
        List<MediaAsset> in = Arrays.asList(asset(1), nullAcmAsset(2), asset(3));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(2, page.acmIdToAssetId.size());
        assertEquals(1, page.nullAcmAssetIds.size());
        assertEquals(Integer.valueOf(2), page.nullAcmAssetIds.get(0));
        assertTrue(page.rawExhausted, "short input should exhaust");
        assertEquals(3, page.lastAssetId);
    }

    @Test void dedupesRepeatedAssets() {
        MediaAsset one = asset(1);
        List<MediaAsset> in = Arrays.asList(one, one, asset(2), one);
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(2, page.acmIdToAssetId.size());
        assertTrue(page.rawExhausted);
    }

    @Test void skipsKnownInvalidButStillCountsThemTowardPage() {
        List<MediaAsset> in = Arrays.asList(invalidAsset(1), asset(2));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAssetId.size());
        assertEquals(0, page.nullAcmAssetIds.size());
        assertEquals(2, page.lastAssetId);
    }

    @Test void skipsNullAssetsFromDanglingFeatures() {
        List<MediaAsset> in = Arrays.asList(null, asset(2));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAssetId.size());
        assertTrue(page.rawExhausted);
    }

    @Test void pageLimitStopsCollectionAndMarksNotExhausted() {
        List<MediaAsset> in = new ArrayList<MediaAsset>();
        for (int i = 1; i <= 5; i++) in.add(asset(i));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 3);

        assertEquals(3, page.acmIdToAssetId.size());
        assertFalse(page.rawExhausted, "limit hit before input end: not exhausted");
        assertEquals(3, page.lastAssetId);
    }

    @Test void exactlyFullPageWithNoMoreInputIsExhausted() {
        List<MediaAsset> in = Arrays.asList(asset(1), asset(2), asset(3));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 3);

        assertEquals(3, page.acmIdToAssetId.size());
        assertTrue(page.rawExhausted, "input ended exactly at limit: exhausted");
    }

    @Test void emptyInputIsExhaustedWithSentinelLastId() {
        AcmIdBot.SweepPage page =
            AcmIdBot.collectSweepPage(new ArrayList<MediaAsset>().iterator(), 10);

        assertTrue(page.rawExhausted);
        assertEquals(-1, page.lastAssetId);
        assertEquals(0, page.acmIdToAssetId.size());
        assertEquals(0, page.nullAcmAssetIds.size());
    }

    // ---------- nextCursorAfterSuccess ----------

    @Test void normalPageAdvancesToLastAssetId() {
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(
            Arrays.asList(asset(7), asset(9)).iterator(), 1); // limit -> not exhausted
        assertFalse(page.rawExhausted);
        assertEquals(7, AcmIdBot.nextCursorAfterSuccess(page, false, page.lastAssetId));
    }

    @Test void exhaustionWrapsCursorToZero() {
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(
            Arrays.asList(asset(7)).iterator(), 10);
        assertTrue(page.rawExhausted);
        assertEquals(0, AcmIdBot.nextCursorAfterSuccess(page, false, page.lastAssetId));
    }

    @Test void maxFixesClampBeatsExhaustionWrap() {
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(
            Arrays.asList(asset(7), asset(9)).iterator(), 10);
        assertTrue(page.rawExhausted);
        // cap hit while healing asset 7: resume from 7, do NOT wrap
        assertEquals(7, AcmIdBot.nextCursorAfterSuccess(page, true, 7));
    }

    // ---------- shouldSkipPoisonedPage ----------

    @Test void skipsOnlyAtFailLimit() {
        assertFalse(AcmIdBot.shouldSkipPoisonedPage(1));
        assertFalse(AcmIdBot.shouldSkipPoisonedPage(2));
        assertTrue(AcmIdBot.shouldSkipPoisonedPage(3));
        assertTrue(AcmIdBot.shouldSkipPoisonedPage(4));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AcmIdBotSweepTest 2>&1 | tail -25`
Expected: COMPILATION ERROR — `cannot find symbol: class SweepPage` etc.

- [ ] **Step 3: Write minimal implementation**

In `src/main/java/org/ecocean/AcmIdBot.java`, after the closing brace of `fixFeats` (line 83) and before `startServices`, add:

```java
    // ------- reconciliation sweep (spec: 2026-07-01-acmidbot-reconciliation-sweep-design.md) -------

    static final int SWEEP_PAGE_SIZE = 10000; // distinct assets examined per 15-minute run
    static final int PAGE_FAIL_LIMIT = 3; // failed runs on one page before skipping it
    // in-memory sweep state; a restart just restarts the sweep (probes are cheap)
    static int sweepCursor = 0; // highest asset id processed
    static int sweepFailCount = 0; // consecutive failures at the current cursor

    // one page of sweep candidates, reduced to primitives so no JDO object
    // outlives the read transaction
    static class SweepPage {
        final List<Integer> nullAcmAssetIds = new ArrayList<Integer>();
        final java.util.LinkedHashMap<String, Integer> acmIdToAssetId =
            new java.util.LinkedHashMap<String, Integer>();
        boolean rawExhausted = false;
        int lastAssetId = -1; // -1 = empty page
    }

    // walk assets (ascending id order, may contain duplicates from multiple
    // Features per asset) collecting up to pageSize distinct assets.
    // rawExhausted is true only when the input ran out — never inferred from
    // a post-dedup count (spec §1/§2).
    static SweepPage collectSweepPage(java.util.Iterator<MediaAsset> assetsInIdOrder,
        int pageSize) {
        SweepPage page = new SweepPage();
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();

        page.rawExhausted = true;
        while (assetsInIdOrder.hasNext()) {
            MediaAsset asset = assetsInIdOrder.next();
            if (asset == null) continue;
            if (seen.contains(asset.getId())) continue;
            if (seen.size() >= pageSize) {
                page.rawExhausted = false;
                return page;
            }
            seen.add(asset.getId());
            page.lastAssetId = asset.getId();
            // known-invalid assets are unhealable: counted as processed, never probed
            if (asset.isValidImageForIA() != null && !asset.isValidImageForIA()) continue;
            if (asset.getAcmId() == null) {
                page.nullAcmAssetIds.add(asset.getId());
            } else {
                page.acmIdToAssetId.put(asset.getAcmId(), asset.getId());
            }
        }
        return page;
    }

    // cursor policy (spec §2): maxFixes clamp wins (resume mid-page next run),
    // else wrap to 0 on true exhaustion, else advance past the page
    static int nextCursorAfterSuccess(SweepPage page, boolean maxFixesHit, int resumeAssetId) {
        if (maxFixesHit) return resumeAssetId;
        if (page.rawExhausted) return 0;
        return page.lastAssetId;
    }

    // poisoned-page guard (spec §5): after PAGE_FAIL_LIMIT consecutive failures
    // on the same page, skip it so one bad page cannot stall the sweep forever
    static boolean shouldSkipPoisonedPage(int consecutiveFailures) {
        return consecutiveFailures >= PAGE_FAIL_LIMIT;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AcmIdBotSweepTest 2>&1 | tail -25`
Expected: `Tests run: 11, Failures: 0, Errors: 0` — BUILD SUCCESS.

- [ ] **Step 5: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/AcmIdBot.java src/test/java/org/ecocean/AcmIdBotSweepTest.java   # expect 0 each
git add src/main/java/org/ecocean/AcmIdBot.java src/test/java/org/ecocean/AcmIdBotSweepTest.java
git commit -m "feat: sweep page collection + cursor helpers for AcmIdBot

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Wire `sweepMatchableAssets` into `fixAcmIds`, remove Query 2

**Files:**
- Modify: `src/main/java/org/ecocean/AcmIdBot.java` (replace the Query-2/filter3 block in `fixAcmIds` lines 154-171; add `sweepMatchableAssets`; update the class comment lines 17-24; add imports)

**Interfaces:**
- Consumes: `SweepPage`/`collectSweepPage`/`nextCursorAfterSuccess`/`shouldSkipPoisonedPage`/`sweepCursor`/`sweepFailCount`/`SWEEP_PAGE_SIZE` (Task 5); `WildbookIAM.iaMissingImageIds(List, String)` (Task 2); `IBEISIA.sendMediaAssetsNew(mas, context, false)` (Task 3); existing `MediaAssetFactory.load(int, Shepherd)` (MediaAssetFactory.java:21), `Shepherd.updateDBTransaction()`.
- Produces: `static void AcmIdBot.sweepMatchableAssets(String context, int maxFixes)` — called from `fixAcmIds` after the Query-1 heal.

- [ ] **Step 1: Replace the Query-2 block in `fixAcmIds`**

Current code (AcmIdBot.java, after the `fixFeats(feats, ...)` Query-1 call — the block from `// check recent Encounter submissions in last 24 hours...` through `fixFeats(feats2, ...)`, lines 154-171) and the `query3` machinery around it. Replace the whole method body with:

```java
    public static void fixAcmIds(String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("AcmIdBot.java");
        myShepherd.beginDBTransaction();

        // number of fixes to consider before finishing and letting a new round of work restart the effort
        int maxFixes = 500;
        Query query2 = null;
        try {
            System.out.println(
                "Looking for complete import tasks with media assets with missing acmIds");

            String filter2 =
                "select from org.ecocean.media.Feature where itask.status == 'complete' && itask.encounters.contains(enc) && enc.annotations.contains(annot) && annot.features.contains(this) && asset.acmId == null VARIABLES org.ecocean.Encounter enc;org.ecocean.servlet.importer.ImportTask itask;org.ecocean.Annotation annot";
            query2 = myShepherd.getPM().newQuery(filter2);
            query2.setOrdering("revision desc");
            Collection c2 = (Collection)(query2.execute());
            List<Feature> feats = new ArrayList<Feature>(c2);
            query2.closeAll();
            query2 = null;  // Mark as closed

            fixFeats(feats, myShepherd, "ACM ID ImportTask fixing summary", maxFixes);
        } catch (Exception f) {
            System.out.println("Exception in AcmIdBot!");
            f.printStackTrace();
        } finally {
            if (query2 != null) query2.closeAll();
            myShepherd.rollbackAndClose();
        }

        // full reconciliation sweep of the matchable set (replaces the old
        // 24-hour Encounter query); manages its own transactions
        sweepMatchableAssets(context, maxFixes);
    }
```

- [ ] **Step 2: Add `sweepMatchableAssets` after `fixAcmIds`**

```java
    /**
     * One 15-minute bite of the reconciliation sweep (spec
     * docs/superpowers/specs/2026-07-01-acmidbot-reconciliation-sweep-design.md).
     * Phase-separated: (1) read one page of distinct matchable assets inside a
     * short transaction, reduced to primitives; (2) probe WBIA for unknown
     * acmIds over HTTP with no transaction open; (3) heal missing assets in a
     * fresh transaction with explicit commits so rectified acmIds survive
     * rollbackAndClose.
     */
    static void sweepMatchableAssets(String context, int maxFixes) {
        // ---- read phase ----
        SweepPage page = null;
        Shepherd readShepherd = new Shepherd(context);

        readShepherd.setAction("AcmIdBot.sweepRead");
        readShepherd.beginDBTransaction();
        Query query = null;
        try {
            System.out.println("AcmIdBot sweep: reading matchable assets from cursor " +
                sweepCursor);
            String filter =
                "select from org.ecocean.media.Feature where annot.matchAgainst == true && annot.features.contains(this) && asset.id > "
                + sweepCursor + " VARIABLES org.ecocean.Annotation annot";
            query = readShepherd.getPM().newQuery(filter);
            query.setOrdering("asset.id ascending");
            Collection c = (Collection)(query.execute());
            final java.util.Iterator featIter = c.iterator();
            java.util.Iterator<MediaAsset> assetIter = new java.util.Iterator<MediaAsset>() {
                public boolean hasNext() {
                    return featIter.hasNext();
                }
                public MediaAsset next() {
                    return ((Feature)featIter.next()).getMediaAsset();
                }
            };
            page = collectSweepPage(assetIter, SWEEP_PAGE_SIZE);
        } catch (Exception ex) {
            System.out.println("Exception in AcmIdBot.sweepMatchableAssets read phase!");
            ex.printStackTrace();
        } finally {
            if (query != null) query.closeAll();
            readShepherd.rollbackAndClose();
        }
        if (page == null) return; // read failed; cursor unchanged, retry next run

        // ---- probe phase (no transaction open) ----
        List<String> missingAcmIds = null;
        try {
            missingAcmIds = org.ecocean.ia.plugin.WildbookIAM.iaMissingImageIds(
                new ArrayList<String>(page.acmIdToAssetId.keySet()), context);
        } catch (java.io.IOException ex) {
            sweepFailCount++;
            System.out.println("WARNING: AcmIdBot sweep probe failed (attempt " +
                sweepFailCount + " of " + PAGE_FAIL_LIMIT + " at cursor " + sweepCursor +
                "): " + ex.toString());
            if (shouldSkipPoisonedPage(sweepFailCount)) {
                System.out.println(
                    "WARNING: AcmIdBot sweep SKIPPING page after repeated failures; cursor " +
                    sweepCursor + " -> " + page.lastAssetId);
                if (page.lastAssetId >= 0) sweepCursor = page.lastAssetId;
                sweepFailCount = 0;
            }
            return; // cursor otherwise unchanged: same page retried next run
        }

        // ---- heal phase (own transaction, explicit commits) ----
        List<Integer> candidateIds = new ArrayList<Integer>(page.nullAcmAssetIds);
        for (String acmId : missingAcmIds) {
            Integer assetId = page.acmIdToAssetId.get(acmId);
            if (assetId != null) candidateIds.add(assetId);
        }
        java.util.Collections.sort(candidateIds); // ascending id = cursor order
        int healedCount = 0;
        int sentCount = 0;
        boolean maxFixesHit = false;
        int resumeAssetId = page.lastAssetId;
        if (candidateIds.size() > 0) {
            Shepherd healShepherd = new Shepherd(context);
            healShepherd.setAction("AcmIdBot.sweepHeal");
            healShepherd.beginDBTransaction();
            try {
                for (Integer assetId : candidateIds) {
                    healShepherd.setAction("AcmIdBot.sweepHeal_asset_" + assetId);
                    try {
                        MediaAsset asset = org.ecocean.media.MediaAssetFactory.load(
                            assetId.intValue(), healShepherd);
                        if (asset == null) continue;
                        if (asset.isValidImageForIA() == null) {
                            asset.validateSourceImage();
                            healShepherd.updateDBTransaction();
                        }
                        if (!asset.isValidImageForIA()) continue;
                        if (!asset.hasFamily(healShepherd)) asset.updateStandardChildren();
                        // legacy rows: adopt the constructor convention before sending
                        if (asset.getAcmId() == null) asset.setAcmId(asset.getUUID());
                        ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
                        fixMe.add(asset);
                        // checkFirst=false: the probe already established absence
                        IBEISIA.sendMediaAssetsNew(fixMe, context, false);
                        sentCount++;
                        // commit so the (possibly rectified) acmId survives rollbackAndClose
                        healShepherd.updateDBTransaction();
                        if (asset.getAcmId() != null) {
                            healedCount++;
                            if (healedCount >= maxFixes) {
                                maxFixesHit = true;
                                resumeAssetId = assetId.intValue();
                                break;
                            }
                        }
                    } catch (Exception ec) {
                        System.out.println("Exception in AcmIdBot sweep heal for asset " +
                            assetId);
                        ec.printStackTrace();
                        // mirror fixFeats: a 500 from WBIA marks the image invalid for IA
                        if (ec.toString().contains("HTTP error code : 500")) {
                            try {
                                MediaAsset asset = org.ecocean.media.MediaAssetFactory.load(
                                    assetId.intValue(), healShepherd);
                                if (asset != null) {
                                    asset.setIsValidImageForIA(false);
                                    healShepherd.updateDBTransaction();
                                }
                            } catch (Exception inner) {
                                inner.printStackTrace();
                            }
                        }
                    }
                }
            } finally {
                healShepherd.rollbackAndClose();
            }
        }
        sweepCursor = nextCursorAfterSuccess(page, maxFixesHit, resumeAssetId);
        sweepFailCount = 0;
        System.out.println("AcmIdBot Reconciliation Sweep Summary");
        System.out.println("...page: " + (page.acmIdToAssetId.size() +
            page.nullAcmAssetIds.size()) + " candidates (" + page.acmIdToAssetId.size() +
            " probed, " + page.nullAcmAssetIds.size() + " null acmId)");
        System.out.println("......missing from WBIA: " + missingAcmIds.size());
        System.out.println("......heals sent: " + sentCount + ", heals successful: " +
            healedCount + (maxFixesHit ? " (maxFixes cap hit)" : ""));
        System.out.println("......cursor now " + sweepCursor +
            (page.rawExhausted && !maxFixesHit ? " (sweep complete, wrapped)" : ""));
    }
```

- [ ] **Step 3: Update the class comment (AcmIdBot.java lines 17-24)**

Replace the last sentence of the class comment:

```java
 * fails. It first checks bulk ImportTasks for appropriate images that may be missing an acmId, and then it checks Encounters submitted within the
 * past 24 hours.
```

with:

```java
 * fails. It first checks bulk ImportTasks for appropriate images that may be missing an acmId (fast path for fresh imports), and then runs one
 * page of a continuous reconciliation sweep: every MediaAsset backing a matchAgainst annotation is eventually probed against WBIA
 * (/api/image/rowid/uuid/) and re-registered if WBIA does not know its acmId.
```

- [ ] **Step 4: Compile and run both new test classes**

Run: `mvn -q compile 2>&1 | tail -20` — expect no `ERROR` lines.
Run: `mvn test -Dtest='AcmIdBotSweepTest,WildbookIAMRowidProbeParseTest' 2>&1 | tail -20`
Expected: `Tests run: 18, Failures: 0, Errors: 0`.

- [ ] **Step 5: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/AcmIdBot.java   # expect 0
git add src/main/java/org/ecocean/AcmIdBot.java
git commit -m "feat: replace AcmIdBot 24h query with matchable-set reconciliation sweep

Cursor-paged sweep (10k assets/run) over matchAgainst annotations:
probe WBIA for unknown acmIds, heal via sendMediaAssetsNew with
checkFirst=false, phase-separated transactions with explicit commits.
Poisoned-page 3-strike skip; maxFixes clamps the cursor mid-page.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Scheduler hardening + final verification

**Files:**
- Modify: `src/main/java/org/ecocean/AcmIdBot.java` (the contiguous `startServices` / `startCollector` / `cleanup` block — line numbers will have shifted after Tasks 5–6; anchor by method names)

**Interfaces:**
- Consumes: nothing new.
- Produces: no API change (`startServices(String)` / `cleanup()` signatures unchanged — StartupWildbook.java:190/899 keep working). Behavior: double `startServices` no longer spawns a second executor; `cleanup()` actually shuts the executor down.

- [ ] **Step 1: Replace the scheduler block**

Current code (AcmIdBot.java lines 85-127: `startServices` through `cleanup`). Replace with:

```java
    // background workers
    private static ScheduledExecutorService schedExec = null;

    public static synchronized boolean startServices(String context) {
        if ((schedExec != null) && !schedExec.isShutdown()) {
            System.out.println("WARNING: AcmIdBot.startServices(" + context +
                ") called but collector already running; ignoring");
            return false;
        }
        startCollector(context);
        return true;
    }

    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { // throws IOException {
        long interval = 15; // number minutes between runs
        long initialDelay = 1; // number minutes before first execution occurs

        System.out.println("+ AcmIdBot.startCollector(" + context + ") starting.");
        schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledExecutorService execRef = schedExec;
        final ScheduledFuture schedFuture = execRef.scheduleWithFixedDelay(new Runnable() {
            // DO WORK HERE
            public void run() {
                if (new java.io.File("/tmp/WB_AcmIdBot_SHUTDOWN").exists()) {
                    System.out.println("INFO: AcmIdBot.startCollection(" + context +
                    ") shutting down due to file signal");
                    execRef.shutdown();
                    return;
                }
                fixAcmIds(context);
            }
        }, initialDelay, // initial delay
            interval, // period delay *after* execution finishes
            TimeUnit.MINUTES); // unit of delays above

        System.out.println("Let's get AcmIdBot's time running.");
        try {
            execRef.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: AcmIdBot.startCollector(" + context + ") interrupted: " +
                ex.toString());
        }
        System.out.println("+ AcmIdBot.startCollector(" + context + ") backgrounded");
    }

    // called from StartupWildbook contextDestroyed
    public static synchronized void cleanup() {
        if (schedExec != null) {
            schedExec.shutdown();
            schedExec = null;
        }
        System.out.println(
            "================ = = = = = = ===================== AcmIdBot.cleanup() finished.");
    }
```

- [ ] **Step 2: Full verification pass**

```bash
mvn -q compile 2>&1 | tail -20                    # expect no ERROR lines
mvn test -Dtest='AcmIdBotSweepTest,WildbookIAMRowidProbeParseTest,WildbookIAMImageIdsStrictTest,WildbookIAMFancyUuidArrayStrictTest' 2>&1 | tail -20
```
Expected: all listed classes pass (18 new tests + existing strict-parse tests as regression guard).

- [ ] **Step 3: CRLF check and commit**

```bash
grep -c $'\r' src/main/java/org/ecocean/AcmIdBot.java   # expect 0
git add src/main/java/org/ecocean/AcmIdBot.java
git commit -m "fix: guard AcmIdBot against double start; shut executor down in cleanup

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Post-plan checklist (for the coordinating session, not a task)

- Codex adversarial review of the full diff (`feedback_codex_review` standing preference), iterate to convergence.
- Push branch + open PR off `main` referencing the spec.
