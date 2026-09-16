OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3cef-9544-7b60-8e37-7440c6d43672
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing a design doc on the `migrate-ml-service-v2` branch of
the Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you
the project conventions, repo gotchas, and current architecture that
the design assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (now 3.1 on this deployment), React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has 20 commits, all Codex-reviewed at design + code. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md`.

## Shepherd pattern

```java
Shepherd shep = new Shepherd(context);
shep.setAction(ACTION_PREFIX + "methodName");
try {
    shep.beginDBTransaction();
    // ... JDO operations ...
    shep.commitDBTransaction();
} catch (Exception ex) {
    // log
} finally {
    shep.rollbackAndClose();
}
```

`rollbackAndClose` is idempotent — safe after commit (rollback on
inactive tx is a no-op) and safe after early return.

Critical gotcha: never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the
HTTP work without Shepherd in Phase B, persist outcome in a fresh
Shepherd in Phase C. This pattern is the reference for any code
that mixes DB + HTTP.

## JDO column naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes — e.g.
`ENCOUNTER_ANNOTATIONS.CATALOGNUMBER_OID` references
`ENCOUNTER.CATALOGNUMBER`; `ENCOUNTER_ANNOTATIONS.ID_EID` references
`ANNOTATION.ID`. The `EMBEDDING` table uses `ANNOTATION_ID` (no
`_OID` suffix — it's a direct FK, not a JDO-generated join).

## ANNOTATION FK constraint cleanup

Direct SQL DELETE on `ANNOTATION` is dangerous. Several tables hold
FKs with `allows-null=false`:

- `MATCHRESULT.QUERYANNOTATION_ID_OID` — row must be deleted, not
  nulled.
- `MATCHRESULTPROSPECT.ANNOTATION_ID_OID` — row must be deleted.
- `EMBEDDING.ANNOTATION_ID` — `Annotation.embeddings` is
  `dependent-element=false`, so JDO does not cascade. Row must be
  deleted explicitly.
- `TASK_OBJECTANNOTATIONS` join — clean up via the join row.
- `ANNOTATION_FEATURES` — `dependent-element=true`, JDO cascades.
- `ENCOUNTER_ANNOTATIONS` — caller's responsibility (`enc.removeAnnotation(ann)`).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh boundary;
**does not** drain the Wildbook IndexingManager queue. If you need
"after this write the doc must be searchable" semantics, use
`OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` which
combines `_refresh` with a `_count` poll on an `ids` query.

`waitForVisibility` was added in commit `f429c5bf8` (c7 of v2) as
a counter-measure for race conditions between JDO write,
IndexingManager queue, and OS refresh cadence.

`skipAutoIndexing` (controlled by `/tmp/skipAutoIndexing`) makes
every `waitForVisibility` poll return zero — there's a diagnostic
WARN at entry to alert anyone running with the flag set.

## `/tmp/skipAutoIndexing` and test mode

If `/tmp/skipAutoIndexing` exists, IndexingManager skips OS indexing
entirely. This is a developer convenience for fast local iteration
but breaks any code that depends on `_id` being findable in OS. The
v2 work checks for this file and warns.

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",      // "vector" = ml-service v2
        "method": "miewid-msv4.1",      // embedding model id
        "version": "4.1",
        "embedding_dimension": 2152,
        // legacy entries have api_endpoint instead of method/version
      }
    },
    "_mlservice_conf": {
      "default": {
        "base_url": "https://ml-service.example.com:8008",
        "detection_endpoint": "/pipeline/",
        "extraction_endpoint": "/extract/",
        "model_id": "...",
        "match_against_species": [...]
      }
    }
  }
}
```

`Embedding.findMatchProspects` gates entry on
`isVectorConfig = method != null || api_endpoint != null` (the
either-or check covers the v2-vs-legacy contract).

## v1 antipatterns to avoid (carry-over from earlier review rounds)

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.
5. **Don't trust the cache without a strict variant.** Lenient
   `iaImageIds` swallows errors; strict variant raises so calls
   that need accuracy can opt in.

## File path conventions

- Java: `src/main/java/org/ecocean/...`
- Tests: `src/test/java/org/ecocean/...`
- Design docs: `docs/plans/YYYY-MM-DD-<topic>.md`
- React: `frontend/src/...`

## What we want from this review

The doc below proposes fixing two linked bugs causing empty match
prospects on bulk imports. Both are in scope. Your job: independent
read on whether the design is right, where it's wrong, what it
missed, and any sequencing/test gaps.

**Specifically requested feedback in the doc's "Open questions"
section.** Address those, plus anything else you'd raise.

**Do not write to any file.** Review-only.

---

# Codex code-review: Track 1 C4 — parseFancyUuidArrayStrict shared helper

Empty-match-prospects design Track 1 C4 (per your round-1 C2 review
recommendation and locked design item 5): extract the shared parser
body from `parseAnnotationIdsArrayStrict` and `parseImageIdsArrayStrict`
into a private `parseFancyUuidArrayStrict(JSONArray, String label)`.
Both named entry points stay so call sites grep cleanly.

## Diff

diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index fd1efdc59..ea3dd5619 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -528,18 +528,7 @@ public class WildbookIAM extends IAPlugin {
      * registered" in the polling thread's already-present check.
      */
     static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
-        List<String> ids = new ArrayList<String>();
-        if (jids == null) return ids;
-        for (int i = 0; i < jids.length(); i++) {
-            JSONObject jo = jids.optJSONObject(i);
-            if (jo == null)
-                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
-            String decoded = fromFancyUUID(jo);
-            if (decoded == null)
-                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
-            ids.add(decoded);
-        }
-        return ids;
+        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
     }
 
     static List<String> parseAnnotationIdsArray(JSONArray jids) {
@@ -613,20 +602,37 @@ public class WildbookIAM extends IAPlugin {
     /**
      * Strict element parser: throws IOException if any element is not a
      * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
-     * a future commit (C4) extracts the common
-     * {@code parseFancyUuidArrayStrict(JSONArray, String)} body, but
-     * keeping the two named entry points preserves grep-friendly call sites.
+     * both delegate to {@link #parseFancyUuidArrayStrict(JSONArray, String)}
+     * with the appropriate label for error-message clarity.
      */
     static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
+        return parseFancyUuidArrayStrict(jids, "iaImageIds");
+    }
+
+    /**
+     * Shared body for {@link #parseAnnotationIdsArrayStrict} and
+     * {@link #parseImageIdsArrayStrict}. The {@code label} is the
+     * source-array name (e.g. {@code "iaAnnotationIds"},
+     * {@code "iaImageIds"}); it appears in IOException messages so
+     * operators can tell which WBIA endpoint a malformed response
+     * came from.
+     *
+     * <p>(Empty-match-prospects design Track 1 C4: extracted from
+     * duplicated parser bodies on Codex's round-1 C2 review
+     * recommendation; the two named entry points stay so call sites
+     * grep cleanly.)</p>
+     */
+    static List<String> parseFancyUuidArrayStrict(JSONArray jids, String label)
+    throws IOException {
         List<String> ids = new ArrayList<String>();
         if (jids == null) return ids;
         for (int i = 0; i < jids.length(); i++) {
             JSONObject jo = jids.optJSONObject(i);
             if (jo == null)
-                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
+                throw new IOException(label + " entry " + i + " is not a JSONObject");
             String decoded = fromFancyUUID(jo);
             if (decoded == null)
-                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
+                throw new IOException(label + " entry " + i + " could not be decoded: " + jo);
             ids.add(decoded);
         }
         return ids;


## New test file:

```java
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage of the shared
 * {@link WildbookIAM#parseFancyUuidArrayStrict(JSONArray, String)} body.
 * The two named entry points
 * ({@link WildbookIAM#parseAnnotationIdsArrayStrict} and
 * {@link WildbookIAM#parseImageIdsArrayStrict}) have their own tests
 * for grep-friendly call coverage; this class verifies the shared
 * body's label propagation into error messages so both endpoints
 * report which WBIA response was malformed. (Empty-match-prospects
 * design Track 1 C4.)
 */
class WildbookIAMFancyUuidArrayStrictTest {

    private static JSONObject fancyUuid(String uuid) {
        JSONObject jo = new JSONObject();
        jo.put("__UUID__", uuid);
        return jo;
    }

    @Test void labelAppearsInIoMessage_forNonJsonObjectEntry() {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put("not-an-object");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "customLabel"));
        assertTrue(ex.getMessage().startsWith("customLabel entry 1"),
            "label was not propagated: " + ex.getMessage());
    }

    @Test void labelAppearsInIoMessage_forUndecodableEntry() {
        JSONArray jids = new JSONArray();
        jids.put(new JSONObject());  // no __UUID__ — fromFancyUUID returns null
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "iaCustomIds"));
        assertTrue(ex.getMessage().startsWith("iaCustomIds entry 0"),
            "label was not propagated: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("could not be decoded"));
    }

    @Test void emptyInputReturnsEmptyList_forAnyLabel()
    throws IOException {
        List<String> out = WildbookIAM.parseFancyUuidArrayStrict(null, "anything");
        assertEquals(0, out.size());
        out = WildbookIAM.parseFancyUuidArrayStrict(new JSONArray(), "anything");
        assertEquals(0, out.size());
    }
}
```


## Validation

All three relevant test classes still pass:
- WildbookIAMFancyUuidArrayStrictTest: 3/3
- WildbookIAMImageIdsStrictTest: 5/5 (unchanged, delegates through wrapper)
- WildbookIAMRegisterTest: 17/17 (includes parseAnnotationIdsArrayStrict cases)

## Review scope

- Is `parseFancyUuidArrayStrict(JSONArray, String label)` the right
  signature? Visibility is package-private (no modifier), matching
  the existing parsers.
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this as a code-review pass, using the local file only for surrounding context and line references. I won’t modify files or run anything that writes build output.
exec
/bin/bash -lc 'git status --short --branch' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "parseAnnotationIdsArrayStrict|parseImageIdsArrayStrict|parseFancyUuidArrayStrict|fromFancyUUID" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '500,660p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 289ms:
        }
        JSONArray jids;
        try {
            jids = apiGetJSONArray("/api/annot/json/", context);
        } catch (Exception ex) {
            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
        }
        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
        if (qc != null) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj =
                    new org.datanucleus.api.rest.orgjson.JSONObject();
                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                qc.addCachedQuery(cq);
            } catch (Exception cacheEx) {
                // Cache store failure is non-fatal; we still have the ids.
            }
        }
        return parseAnnotationIdsArrayStrict(jids);
    }

    /**
     * Strict element parser: throws IOException if any element is not a
     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
     * skips/null-pads malformed entries, which is fine for legacy paths but
     * would let a corrupt response masquerade as "annotation not yet
     * registered" in the polling thread's already-present check.
     */
    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
    }

    static List<String> parseAnnotationIdsArray(JSONArray jids) {
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo != null) ids.add(fromFancyUUID(jo));
        }
        return ids;
    }

    /**
     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
     * failure rather than returning an empty list. The new Phase 0 of
     * the v2 WBIA registration polling thread needs this so a network
     * failure during the "is the image already registered with WBIA?"
     * check is not silently treated as "go ahead and POST".
     *
     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
     */
    public static List<String> iaImageIdsStrict(String context) throws IOException {
        String cacheName = "iaImageIds";
        // QueryCacheFactory.getQueryCache(context) can return null on a
        // context that has never been initialized; treat that as "no cache"
        // rather than NPE-ing out and aborting the poll cycle.
        QueryCache qc = null;
        try {
            qc = QueryCacheFactory.getQueryCache(context);
        } catch (Exception ex) {
            // Defensive: cache factory init can fail; degrade to no-cache.
        }
        if (qc != null && qc.getQueryByName(cacheName) != null &&
            System.currentTimeMillis() <
            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
                return parseImageIdsArrayStrict(cached);
            } catch (Exception ex) {
                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
                    + ex.getMessage());
            }
        }
        JSONArray jids;
        try {
            jids = apiGetJSONArray("/api/image/json/", context);
        } catch (Exception ex) {
            throw new IOException("WBIA /api/image/json/ fetch failed: " + ex.getMessage(), ex);
        }
        if (jids == null) throw new IOException("WBIA /api/image/json/ returned null");
        if (qc != null) {
            try {
                org.datanucleus.api.rest.orgjson.JSONObject jobj =
                    new org.datanucleus.api.rest.orgjson.JSONObject();
                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                qc.addCachedQuery(cq);
            } catch (Exception cacheEx) {
                // Cache store failure is non-fatal; we still have the ids.
            }
        }
        return parseImageIdsArrayStrict(jids);
    }

    /**
     * Strict element parser: throws IOException if any element is not a
     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
     * both delegate to {@link #parseFancyUuidArrayStrict(JSONArray, String)}
     * with the appropriate label for error-message clarity.
     */
    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
        return parseFancyUuidArrayStrict(jids, "iaImageIds");
    }

    /**
     * Shared body for {@link #parseAnnotationIdsArrayStrict} and
     * {@link #parseImageIdsArrayStrict}. The {@code label} is the
     * source-array name (e.g. {@code "iaAnnotationIds"},
     * {@code "iaImageIds"}); it appears in IOException messages so
     * operators can tell which WBIA endpoint a malformed response
     * came from.
     *
     * <p>(Empty-match-prospects design Track 1 C4: extracted from
     * duplicated parser bodies on Codex's round-1 C2 review
     * recommendation; the two named entry points stay so call sites
     * grep cleanly.)</p>
     */
    static List<String> parseFancyUuidArrayStrict(JSONArray jids, String label)
    throws IOException {
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo == null)
                throw new IOException(label + " entry " + i + " is not a JSONObject");
            String decoded = fromFancyUUID(jo);
            if (decoded == null)
                throw new IOException(label + " entry " + i + " could not be decoded: " + jo);
            ids.add(decoded);
        }
        return ids;
    }

    /**
     * Build the forced-id POST body for a single DTO. Pure function;
     * factored out so unit tests can verify the request shape without
     * a network round trip.
     */
    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("annot_uuid_list", new ArrayList<JSONObject>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        map.put("annot_theta_list", new ArrayList<Double>());
        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
        map.get("annot_species_list").add(dto.iaClass);
        map.get("annot_bbox_list").add(dto.bbox);
        map.get("annot_name_list").add(
            (dto.individualName == null) ? "____" : dto.individualName);
        map.get("annot_theta_list").add(dto.theta);

 succeeded in 518ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:495:                return parseAnnotationIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:520:        return parseAnnotationIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530:    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:531:        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:539:            if (jo != null) ids.add(fromFancyUUID(jo));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:574:                return parseImageIdsArrayStrict(cached);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:599:        return parseImageIdsArrayStrict(jids);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:604:     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:605:     * both delegate to {@link #parseFancyUuidArrayStrict(JSONArray, String)}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        return parseFancyUuidArrayStrict(jids, "iaImageIds");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:     * Shared body for {@link #parseAnnotationIdsArrayStrict} and
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:614:     * {@link #parseImageIdsArrayStrict}. The {@code label} is the
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:625:    static List<String> parseFancyUuidArrayStrict(JSONArray jids, String label)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:633:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:682:        String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:749:            String sentId = fromFancyUUID(sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:754:            String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:770:                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:816:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:846:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:878:    public static String fromFancyUUID(JSONObject u) {
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:16: * {@link WildbookIAM#parseFancyUuidArrayStrict(JSONArray, String)} body.
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:18: * ({@link WildbookIAM#parseAnnotationIdsArrayStrict} and
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:19: * {@link WildbookIAM#parseImageIdsArrayStrict}) have their own tests
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:38:            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "customLabel"));
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:45:        jids.put(new JSONObject());  // no __UUID__ — fromFancyUUID returns null
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:47:            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "iaCustomIds"));
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:55:        List<String> out = WildbookIAM.parseFancyUuidArrayStrict(null, "anything");
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:57:        out = WildbookIAM.parseFancyUuidArrayStrict(new JSONArray(), "anything");
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:14: * Pure-function coverage of {@link WildbookIAM#parseImageIdsArrayStrict}.
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:28:    @Test void parseImageIdsArrayStrict_returnsEmptyList_whenInputIsNull()
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:30:        List<String> out = WildbookIAM.parseImageIdsArrayStrict(null);
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:34:    @Test void parseImageIdsArrayStrict_decodesValidFancyUuids()
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:39:        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:45:    @Test void parseImageIdsArrayStrict_throwsIoException_onNonJsonObjectEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:50:            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:54:    @Test void parseImageIdsArrayStrict_throwsIoException_onUndecodableEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:56:        // Empty JSONObject with no __UUID__ field — fromFancyUUID returns null.
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:59:            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:63:    @Test void parseImageIdsArrayStrict_preservesOrder()
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:69:        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:54:        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:55:        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:138:    // --- parseAnnotationIdsArrayStrict -----------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:140:    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:141:        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:144:    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:148:        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:154:    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:159:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:162:    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:167:            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:173:     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}

codex
I’ve confirmed the branch already contains the proposed helper and tests. I’m checking the call-site expectations now, especially whether widening the helper’s visibility or label contract creates any unintended surface.
 succeeded in 11212ms:
## migrate-ml-service-v2...origin/migrate-ml-service-v2 [ahead 5]
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/OpenSearch.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/StartupWildbook.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MatchResult.java
 M src/main/java/org/ecocean/ia/MatchResultProspect.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/ia/MlServiceProcessor.java
 M src/main/java/org/ecocean/ia/Task.java
 M src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/WEB-INF/web.xml
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
?? src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java

exec
/bin/bash -lc "sed -n '1,110p' src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,90p' src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '130,180p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 169ms:
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage of the shared
 * {@link WildbookIAM#parseFancyUuidArrayStrict(JSONArray, String)} body.
 * The two named entry points
 * ({@link WildbookIAM#parseAnnotationIdsArrayStrict} and
 * {@link WildbookIAM#parseImageIdsArrayStrict}) have their own tests
 * for grep-friendly call coverage; this class verifies the shared
 * body's label propagation into error messages so both endpoints
 * report which WBIA response was malformed. (Empty-match-prospects
 * design Track 1 C4.)
 */
class WildbookIAMFancyUuidArrayStrictTest {

    private static JSONObject fancyUuid(String uuid) {
        JSONObject jo = new JSONObject();
        jo.put("__UUID__", uuid);
        return jo;
    }

    @Test void labelAppearsInIoMessage_forNonJsonObjectEntry() {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put("not-an-object");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "customLabel"));
        assertTrue(ex.getMessage().startsWith("customLabel entry 1"),
            "label was not propagated: " + ex.getMessage());
    }

    @Test void labelAppearsInIoMessage_forUndecodableEntry() {
        JSONArray jids = new JSONArray();
        jids.put(new JSONObject());  // no __UUID__ — fromFancyUUID returns null
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "iaCustomIds"));
        assertTrue(ex.getMessage().startsWith("iaCustomIds entry 0"),
            "label was not propagated: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("could not be decoded"));
    }

    @Test void emptyInputReturnsEmptyList_forAnyLabel()
    throws IOException {
        List<String> out = WildbookIAM.parseFancyUuidArrayStrict(null, "anything");
        assertEquals(0, out.size());
        out = WildbookIAM.parseFancyUuidArrayStrict(new JSONArray(), "anything");
        assertEquals(0, out.size());
    }
}

 succeeded in 175ms:
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of {@link WildbookIAM#parseImageIdsArrayStrict}.
 * Network-bound behavior of {@link WildbookIAM#iaImageIdsStrict} itself
 * is exercised end-to-end by Phase 0 of the WBIA registration polling
 * thread in a dev deployment; here we cover the pieces that can be tested
 * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
 */
class WildbookIAMImageIdsStrictTest {

    private static JSONObject fancyUuid(String uuid) {
        JSONObject jo = new JSONObject();
        jo.put("__UUID__", uuid);
        return jo;
    }

    @Test void parseImageIdsArrayStrict_returnsEmptyList_whenInputIsNull()
    throws IOException {
        List<String> out = WildbookIAM.parseImageIdsArrayStrict(null);
        assertEquals(0, out.size());
    }

    @Test void parseImageIdsArrayStrict_decodesValidFancyUuids()
    throws IOException {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put(fancyUuid("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb"));
        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
        assertEquals(2, out.size());
        assertEquals("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa", out.get(0));
        assertEquals("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb", out.get(1));
    }

    @Test void parseImageIdsArrayStrict_throwsIoException_onNonJsonObjectEntry() {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        jids.put("not-an-object");  // string entry, not JSONObject
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
        assertEquals(true, ex.getMessage().contains("iaImageIds entry 1"));
    }

    @Test void parseImageIdsArrayStrict_throwsIoException_onUndecodableEntry() {
        JSONArray jids = new JSONArray();
        // Empty JSONObject with no __UUID__ field — fromFancyUUID returns null.
        jids.put(new JSONObject());
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
        assertEquals(true, ex.getMessage().contains("could not be decoded"));
    }

    @Test void parseImageIdsArrayStrict_preservesOrder()
    throws IOException {
        JSONArray jids = new JSONArray();
        jids.put(fancyUuid("00000000-0000-4000-8000-000000000003"));
        jids.put(fancyUuid("00000000-0000-4000-8000-000000000001"));
        jids.put(fancyUuid("00000000-0000-4000-8000-000000000002"));
        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
        assertEquals("00000000-0000-4000-8000-000000000003", out.get(0));
        assertEquals("00000000-0000-4000-8000-000000000001", out.get(1));
        assertEquals("00000000-0000-4000-8000-000000000002", out.get(2));
    }
}

 succeeded in 223ms:
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put("not-an-object");
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(1, ids.size());
        assertEquals("u1", ids.get(0));
    }

    // --- parseAnnotationIdsArrayStrict -----------------------------------

    @Test void parseAnnotationIdsArrayStrictReturnsEmptyOnNull() throws IOException {
        assertTrue(WildbookIAM.parseAnnotationIdsArrayStrict(null).isEmpty());
    }

    @Test void parseAnnotationIdsArrayStrictExtractsWhenWellFormed() throws IOException {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArrayStrict(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArrayStrictThrowsOnNonObjectEntry() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put("not-an-object");
        assertThrows(IOException.class,
            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
    }

    @Test void parseAnnotationIdsArrayStrictThrowsOnUndecodableEntry() {
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(new JSONObject().put("not_uuid_key", "x"));
        assertThrows(IOException.class,
            () -> WildbookIAM.parseAnnotationIdsArrayStrict(jids));
    }

    // --- helpers ---------------------------------------------------------

    /**
     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}
     * decodes back to the raw string. The wire format ({@code __UUID__})
     * is opaque to this test, so go through the public factory.
     */
    private static JSONObject makeFancy(String raw) {
        JSONObject jo = WildbookIAM.toFancyUUID(raw);
        assertNotNull(jo, "toFancyUUID returned null for " + raw);
        return jo;

codex
The helper is a straight behavioral extraction in the current file: both wrappers still supply fixed labels, and the existing strict parser tests exercise the wrappers. I’m doing one more pass for edge cases around malformed JSON elements and test value.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '520,640p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java | sed -n '1,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '870,900p'" in /mnt/c/Wildbook-clean2
 succeeded in 213ms:
     1	package org.ecocean.ia.plugin;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertThrows;
     5	import static org.junit.jupiter.api.Assertions.assertTrue;
     6	
     7	import java.io.IOException;
     8	import java.util.List;
     9	
    10	import org.json.JSONArray;
    11	import org.json.JSONObject;
    12	import org.junit.jupiter.api.Test;
    13	
    14	/**
    15	 * Direct coverage of the shared
    16	 * {@link WildbookIAM#parseFancyUuidArrayStrict(JSONArray, String)} body.
    17	 * The two named entry points
    18	 * ({@link WildbookIAM#parseAnnotationIdsArrayStrict} and
    19	 * {@link WildbookIAM#parseImageIdsArrayStrict}) have their own tests
    20	 * for grep-friendly call coverage; this class verifies the shared
    21	 * body's label propagation into error messages so both endpoints
    22	 * report which WBIA response was malformed. (Empty-match-prospects
    23	 * design Track 1 C4.)
    24	 */
    25	class WildbookIAMFancyUuidArrayStrictTest {
    26	
    27	    private static JSONObject fancyUuid(String uuid) {
    28	        JSONObject jo = new JSONObject();
    29	        jo.put("__UUID__", uuid);
    30	        return jo;
    31	    }
    32	
    33	    @Test void labelAppearsInIoMessage_forNonJsonObjectEntry() {
    34	        JSONArray jids = new JSONArray();
    35	        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
    36	        jids.put("not-an-object");
    37	        IOException ex = assertThrows(IOException.class,
    38	            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "customLabel"));
    39	        assertTrue(ex.getMessage().startsWith("customLabel entry 1"),
    40	            "label was not propagated: " + ex.getMessage());
    41	    }
    42	
    43	    @Test void labelAppearsInIoMessage_forUndecodableEntry() {
    44	        JSONArray jids = new JSONArray();
    45	        jids.put(new JSONObject());  // no __UUID__ — fromFancyUUID returns null
    46	        IOException ex = assertThrows(IOException.class,
    47	            () -> WildbookIAM.parseFancyUuidArrayStrict(jids, "iaCustomIds"));
    48	        assertTrue(ex.getMessage().startsWith("iaCustomIds entry 0"),
    49	            "label was not propagated: " + ex.getMessage());
    50	        assertTrue(ex.getMessage().contains("could not be decoded"));
    51	    }
    52	
    53	    @Test void emptyInputReturnsEmptyList_forAnyLabel()
    54	    throws IOException {
    55	        List<String> out = WildbookIAM.parseFancyUuidArrayStrict(null, "anything");
    56	        assertEquals(0, out.size());
    57	        out = WildbookIAM.parseFancyUuidArrayStrict(new JSONArray(), "anything");
    58	        assertEquals(0, out.size());
    59	    }
    60	}

 succeeded in 220ms:
   870	            !rtn.getJSONObject("status").optBoolean("success", false)) {
   871	            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
   872	                rtn);
   873	            return null;
   874	        }
   875	        return rtn.getJSONArray("response");
   876	    }
   877	
   878	    public static String fromFancyUUID(JSONObject u) {
   879	        if (u == null) return null;
   880	        return u.optString("__UUID__", null);
   881	    }
   882	
   883	    public static JSONObject toFancyUUID(String u) {
   884	        JSONObject j = new JSONObject();
   885	
   886	        j.put("__UUID__", u);
   887	        return j;
   888	    }
   889	
   890	    /**
   891	     * Build the URL string WBIA expects in {@code image_uri_list}. The
   892	     * double-encoded "?" pattern preserves filenames that contain "?" so
   893	     * WBIA's HTTP layer doesn't truncate them at the query boundary.
   894	     *
   895	     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
   896	     * {@code null}. Promoted from {@code private Object} to
   897	     * {@code public String} (and the leading-NPE on {@code curl.toString()}
   898	     * tightened) so the ml-service v2 WBIA registration polling thread
   899	     * can call it from Phase A while building the {@link WbiaRegisterRequest}
   900	     * DTO. (Empty-match-prospects design Track 1 C2.)</p>

 succeeded in 290ms:
   520	        return parseAnnotationIdsArrayStrict(jids);
   521	    }
   522	
   523	    /**
   524	     * Strict element parser: throws IOException if any element is not a
   525	     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
   526	     * skips/null-pads malformed entries, which is fine for legacy paths but
   527	     * would let a corrupt response masquerade as "annotation not yet
   528	     * registered" in the polling thread's already-present check.
   529	     */
   530	    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
   531	        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
   532	    }
   533	
   534	    static List<String> parseAnnotationIdsArray(JSONArray jids) {
   535	        List<String> ids = new ArrayList<String>();
   536	        if (jids == null) return ids;
   537	        for (int i = 0; i < jids.length(); i++) {
   538	            JSONObject jo = jids.optJSONObject(i);
   539	            if (jo != null) ids.add(fromFancyUUID(jo));
   540	        }
   541	        return ids;
   542	    }
   543	
   544	    /**
   545	     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
   546	     * failure rather than returning an empty list. The new Phase 0 of
   547	     * the v2 WBIA registration polling thread needs this so a network
   548	     * failure during the "is the image already registered with WBIA?"
   549	     * check is not silently treated as "go ahead and POST".
   550	     *
   551	     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
   552	     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
   553	     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
   554	     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
   555	     */
   556	    public static List<String> iaImageIdsStrict(String context) throws IOException {
   557	        String cacheName = "iaImageIds";
   558	        // QueryCacheFactory.getQueryCache(context) can return null on a
   559	        // context that has never been initialized; treat that as "no cache"
   560	        // rather than NPE-ing out and aborting the poll cycle.
   561	        QueryCache qc = null;
   562	        try {
   563	            qc = QueryCacheFactory.getQueryCache(context);
   564	        } catch (Exception ex) {
   565	            // Defensive: cache factory init can fail; degrade to no-cache.
   566	        }
   567	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   568	            System.currentTimeMillis() <
   569	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   570	            try {
   571	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   572	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   573	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
   574	                return parseImageIdsArrayStrict(cached);
   575	            } catch (Exception ex) {
   576	                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
   577	                    + ex.getMessage());
   578	            }
   579	        }
   580	        JSONArray jids;
   581	        try {
   582	            jids = apiGetJSONArray("/api/image/json/", context);
   583	        } catch (Exception ex) {
   584	            throw new IOException("WBIA /api/image/json/ fetch failed: " + ex.getMessage(), ex);
   585	        }
   586	        if (jids == null) throw new IOException("WBIA /api/image/json/ returned null");
   587	        if (qc != null) {
   588	            try {
   589	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   590	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   591	                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
   592	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   593	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   594	                qc.addCachedQuery(cq);
   595	            } catch (Exception cacheEx) {
   596	                // Cache store failure is non-fatal; we still have the ids.
   597	            }
   598	        }
   599	        return parseImageIdsArrayStrict(jids);
   600	    }
   601	
   602	    /**
   603	     * Strict element parser: throws IOException if any element is not a
   604	     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
   605	     * both delegate to {@link #parseFancyUuidArrayStrict(JSONArray, String)}
   606	     * with the appropriate label for error-message clarity.
   607	     */
   608	    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
   609	        return parseFancyUuidArrayStrict(jids, "iaImageIds");
   610	    }
   611	
   612	    /**
   613	     * Shared body for {@link #parseAnnotationIdsArrayStrict} and
   614	     * {@link #parseImageIdsArrayStrict}. The {@code label} is the
   615	     * source-array name (e.g. {@code "iaAnnotationIds"},
   616	     * {@code "iaImageIds"}); it appears in IOException messages so
   617	     * operators can tell which WBIA endpoint a malformed response
   618	     * came from.
   619	     *
   620	     * <p>(Empty-match-prospects design Track 1 C4: extracted from
   621	     * duplicated parser bodies on Codex's round-1 C2 review
   622	     * recommendation; the two named entry points stay so call sites
   623	     * grep cleanly.)</p>
   624	     */
   625	    static List<String> parseFancyUuidArrayStrict(JSONArray jids, String label)
   626	    throws IOException {
   627	        List<String> ids = new ArrayList<String>();
   628	        if (jids == null) return ids;
   629	        for (int i = 0; i < jids.length(); i++) {
   630	            JSONObject jo = jids.optJSONObject(i);
   631	            if (jo == null)
   632	                throw new IOException(label + " entry " + i + " is not a JSONObject");
   633	            String decoded = fromFancyUUID(jo);
   634	            if (decoded == null)
   635	                throw new IOException(label + " entry " + i + " could not be decoded: " + jo);
   636	            ids.add(decoded);
   637	        }
   638	        return ids;
   639	    }
   640	

codex
No blocking findings.

The signature `parseFancyUuidArrayStrict(JSONArray jids, String label)` is appropriate for this extraction. The label is only diagnostic text, so a `String` is enough; an enum or endpoint-specific type would add noise without improving safety here. Behavior is preserved: both wrappers still supply fixed labels at [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530) and [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608), and the helper keeps the prior null-input, non-object, undecodable-entry, and ordering behavior at [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:625).

Package-private visibility is acceptable if the repo convention is to test these parser helpers directly from package tests. If the design wording still says “private helper,” I’d update that wording or make the helper private and cover it only through the two named wrappers. I would not block on package-private here.

The only test gap I’d consider is small: add one assertion that `parseAnnotationIdsArrayStrict` emits `iaAnnotationIds` in the exception message, parallel to the existing image-id assertion. The new helper test covers arbitrary label propagation at [WildbookIAMFancyUuidArrayStrictTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:33), but wrapper-label wiring is what protects against an accidental copy/paste label swap.

I did not rerun tests because this was review-only and I avoided commands that would write build output.
tokens used
31,055
No blocking findings.

The signature `parseFancyUuidArrayStrict(JSONArray jids, String label)` is appropriate for this extraction. The label is only diagnostic text, so a `String` is enough; an enum or endpoint-specific type would add noise without improving safety here. Behavior is preserved: both wrappers still supply fixed labels at [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:530) and [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608), and the helper keeps the prior null-input, non-object, undecodable-entry, and ordering behavior at [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:625).

Package-private visibility is acceptable if the repo convention is to test these parser helpers directly from package tests. If the design wording still says “private helper,” I’d update that wording or make the helper private and cover it only through the two named wrappers. I would not block on package-private here.

The only test gap I’d consider is small: add one assertion that `parseAnnotationIdsArrayStrict` emits `iaAnnotationIds` in the exception message, parallel to the existing image-id assertion. The new helper test covers arbitrary label propagation at [WildbookIAMFancyUuidArrayStrictTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:33), but wrapper-label wiring is what protects against an accidental copy/paste label swap.

I did not rerun tests because this was review-only and I avoided commands that would write build output.
