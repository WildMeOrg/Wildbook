OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3ce2-df95-7233-875c-e067b85326e1
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

# Codex code-review: Track 1 C3 — iaImageIdsStrict variant

Empty-match-prospects design Track 1 C3 (per locked design's
"Codex follow-ups" item 2): add a strict `iaImageIdsStrict(context)`
variant mirroring the existing `iaAnnotationIdsStrict`. Shared
15-min QueryCache under key `"iaImageIds"`. Used by the new Phase 0
of `registerOneByDto` (C6) to detect "is this image already
registered with WBIA?" without silently treating a network failure
as "go ahead and POST".

## Diff

diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index a2175e72b..fd1efdc59 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -552,6 +552,86 @@ public class WildbookIAM extends IAPlugin {
         return ids;
     }
 
+    /**
+     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
+     * failure rather than returning an empty list. The new Phase 0 of
+     * the v2 WBIA registration polling thread needs this so a network
+     * failure during the "is the image already registered with WBIA?"
+     * check is not silently treated as "go ahead and POST".
+     *
+     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
+     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
+     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
+     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
+     */
+    public static List<String> iaImageIdsStrict(String context) throws IOException {
+        String cacheName = "iaImageIds";
+        // QueryCacheFactory.getQueryCache(context) can return null on a
+        // context that has never been initialized; treat that as "no cache"
+        // rather than NPE-ing out and aborting the poll cycle.
+        QueryCache qc = null;
+        try {
+            qc = QueryCacheFactory.getQueryCache(context);
+        } catch (Exception ex) {
+            // Defensive: cache factory init can fail; degrade to no-cache.
+        }
+        if (qc != null && qc.getQueryByName(cacheName) != null &&
+            System.currentTimeMillis() <
+            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
+            try {
+                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
+                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
+                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
+                return parseImageIdsArrayStrict(cached);
+            } catch (Exception ex) {
+                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
+                    + ex.getMessage());
+            }
+        }
+        JSONArray jids;
+        try {
+            jids = apiGetJSONArray("/api/image/json/", context);
+        } catch (Exception ex) {
+            throw new IOException("WBIA /api/image/json/ fetch failed: " + ex.getMessage(), ex);
+        }
+        if (jids == null) throw new IOException("WBIA /api/image/json/ returned null");
+        if (qc != null) {
+            try {
+                org.datanucleus.api.rest.orgjson.JSONObject jobj =
+                    new org.datanucleus.api.rest.orgjson.JSONObject();
+                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
+                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
+                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
+                qc.addCachedQuery(cq);
+            } catch (Exception cacheEx) {
+                // Cache store failure is non-fatal; we still have the ids.
+            }
+        }
+        return parseImageIdsArrayStrict(jids);
+    }
+
+    /**
+     * Strict element parser: throws IOException if any element is not a
+     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
+     * a future commit (C4) extracts the common
+     * {@code parseFancyUuidArrayStrict(JSONArray, String)} body, but
+     * keeping the two named entry points preserves grep-friendly call sites.
+     */
+    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
+        List<String> ids = new ArrayList<String>();
+        if (jids == null) return ids;
+        for (int i = 0; i < jids.length(); i++) {
+            JSONObject jo = jids.optJSONObject(i);
+            if (jo == null)
+                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
+            String decoded = fromFancyUUID(jo);
+            if (decoded == null)
+                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
+            ids.add(decoded);
+        }
+        return ids;
+    }
+
     /**
      * Build the forced-id POST body for a single DTO. Pure function;
      * factored out so unit tests can verify the request shape without


## New test file:

```java
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
```


## Review scope

- Does the new method exactly mirror `iaAnnotationIdsStrict` in
  cache/error semantics?
- Is the cache key `"iaImageIds"` the right pattern (matches what
  `iaAnnotationIdsStrict` uses for its key `"iaAnnotationIds"`,
  shared with the lenient counterpart even though the lenient
  variant doesn't currently use a cache)?
- Phase 0 will call `safeInvalidate(context, "iaImageIds")` after
  a successful image POST. Confirm that's the right invalidation
  target.
- The parser is intentionally duplicated for now; C4 will extract
  the shared helper per your round-1 C2 review recommendation
  (`parseFancyUuidArrayStrict(JSONArray, String label)`).
- Test coverage gaps?

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this against the existing `iaAnnotationIdsStrict` implementation and surrounding cache/test patterns, without making any file changes.
exec
/bin/bash -lc "rg -n \"iaAnnotationIdsStrict|iaAnnotationIds\\(|iaImageIds\\(|QueryCacheFactory|getQueryByName|safeInvalidate|class CachedQuery|fromFancyUUID\" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java src/test/java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '480,620p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git branch --show-current' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '620,760p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 251ms:
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
                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
                return parseAnnotationIdsArrayStrict(cached);
            } catch (Exception ex) {
                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
                    + ex.getMessage());
            }
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
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo == null)
                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
            String decoded = fromFancyUUID(jo);
            if (decoded == null)
                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
            ids.add(decoded);
        }
        return ids;
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
     * a future commit (C4) extracts the common
     * {@code parseFancyUuidArrayStrict(JSONArray, String)} body, but
     * keeping the two named entry points preserves grep-friendly call sites.
     */
    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {

 succeeded in 294ms:
    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
        List<String> ids = new ArrayList<String>();
        if (jids == null) return ids;
        for (int i = 0; i < jids.length(); i++) {
            JSONObject jo = jids.optJSONObject(i);
            if (jo == null)
                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
            String decoded = fromFancyUUID(jo);
            if (decoded == null)
                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
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
        return map;
    }

    /**
     * Validate a forced-id response. Throws on any contract violation
     * (length mismatch, missing entry, id mismatch). Pure function.
     */
    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
        if (resp == null) throw new IOException("null forced-id response");
        if (resp.has("status")) {
            JSONObject status = resp.optJSONObject("status");
            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
                throw new IOException("forced-id response status.success=false: " + resp);
            }
        }
        JSONArray respArr = resp.optJSONArray("response");
        if (respArr == null) throw new IOException("no response array: " + resp);
        if (respArr.length() != 1)
            throw new IOException("expected response array length 1, got " + respArr.length());
        JSONObject jid = respArr.optJSONObject(0);
        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
        String respId = fromFancyUUID(jid);
        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
        if (!respId.equals(sentAnnotId))
            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
    }

    /**
     * Phase B entry point. Does the already-present check, builds the
     * forced-id POST, fires it, and classifies the outcome. Does NOT
     * touch any Shepherd or JDO state; callers must hand it a DTO that
     * was pre-validated and detached in Phase A.
     */
    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
        if (u == null) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        List<String> known;
        try {
            known = iaAnnotationIdsStrict(context);
        } catch (IOException ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
                ex.getMessage());
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
        // only check the annotation's id and acmId here. Comparing against
        // the media-asset's acmId is wrong - that would compare an image
        // identifier against a list of annotation identifiers.
        if (known.contains(dto.annotationId) ||
            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
        }
        URL url;
        try {
            url = new URL(u);
        } catch (MalformedURLException ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
        JSONObject rtn;
        try {
            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        } catch (Exception ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        try {
            validateForcedResponse(dto.annotationId, rtn);
        } catch (IOException ex) {
            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
            return WbiaRegisterOutcome.RESPONSE_BAD;
        }
        return WbiaRegisterOutcome.REGISTERED_OK;
    }

    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
    throws IOException {
        if ((sentIds == null) || (respArr == null))
            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
        if (sentIds.size() != respArr.length())
            throw new IOException("args diff length: " + sentIds.size() + " != " +
                    respArr.length());
        for (int i = 0; i < sentIds.size(); i++) {
            String sentId = fromFancyUUID(sentIds.get(i));
            if (sentId == null)
                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
            JSONObject jid = respArr.optJSONObject(i);
            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
            String respId = fromFancyUUID(jid);
            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
            if (!respId.equals(sentId))
                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
                        "; respId=" + respId);
        }
    }

    public static List<String> acmIdsFromResponse(JSONObject rtn) {
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
            if (rtn.getJSONArray("response").optJSONObject(i) == null) {

 succeeded in 929ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:17:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:278:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:359:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:469:     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:490:            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:493:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:537:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:550:            if (jo != null) ids.add(fromFancyUUID(jo));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556:     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:563:     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:564:     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:569:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:574:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:578:        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:580:            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:583:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:627:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:676:        String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:697:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:743:            String sentId = fromFancyUUID(sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:748:            String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:772:    public List<String> iaAnnotationIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:773:        return iaAnnotationIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:777:    public static List<String> iaAnnotationIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:783:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:784:            if (qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:786:                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:788:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:803:            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:810:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:814:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:825:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:840:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:872:    public static String fromFancyUUID(JSONObject u) {
src/main/java/org/ecocean/cache/CachedQuery.java:26:public class CachedQuery {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:28:    public static void safeInvalidate(String context, String cacheName) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/identity/IBEISIA.java:800:                    String uuid = fromFancyUUID(list.getJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:813:                        String acmId = fromFancyUUID(list.getJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:1086:    public static String fromFancyUUID(JSONObject u) {
src/main/java/org/ecocean/identity/IBEISIA.java:1271:        ann.setAcmId(fromFancyUUID(iaResult.optJSONObject("uuid")));
src/main/java/org/ecocean/identity/IBEISIA.java:1594:                    String iuuid = fromFancyUUID(jiuuid);
src/main/java/org/ecocean/identity/IBEISIA.java:1833:                String acmId = fromFancyUUID(rlist.getJSONObject(i).getJSONObject("annot_uuid_1")); // gets not opts here... so ungraceful fail possible
src/main/java/org/ecocean/identity/IBEISIA.java:1946:        String ms = getIdentificationMatchingState(fromFancyUUID(rlist.getJSONObject(
src/main/java/org/ecocean/identity/IBEISIA.java:1948:            fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")), context);
src/main/java/org/ecocean/identity/IBEISIA.java:2122:            String imageUUID = fromFancyUUID(rtn.getJSONArray("response").getJSONObject(0));
src/main/java/org/ecocean/identity/IBEISIA.java:2282:            origAnnUUIDs.add(fromFancyUUID(auuids.optJSONObject(j)));
src/main/java/org/ecocean/identity/IBEISIA.java:2291:            String n = fromFancyUUID(nameUUIDs.optJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:2320:                String u = fromFancyUUID(auuidSet.optJSONObject(j));
src/main/java/org/ecocean/identity/IBEISIA.java:2841:            map.put(fromFancyUUID(uuids.optJSONObject(i)), names.optString(i, null));
src/main/java/org/ecocean/identity/IBEISIA.java:2855:        return fromFancyUUID(rtn.getJSONArray("response").optJSONObject(0));
src/main/java/org/ecocean/identity/IBEISIA.java:3242:            qid = fromFancyUUID(rlist.getJSONObject(0).optJSONObject("annot_uuid_key"));
src/main/java/org/ecocean/identity/IBEISIA.java:3247:            if (!qid.equals(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_key"))))
src/main/java/org/ecocean/identity/IBEISIA.java:3250:            s.put(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")));
src/main/java/org/ecocean/identity/IBEISIA.java:3704:        // List<String> iaAnnotIds = plugin.iaAnnotationIds();
src/main/java/org/ecocean/identity/IBEISIA.java:3705:        HashSet<String> iaAnnotIds = new HashSet(plugin.iaAnnotationIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3710:        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
src/main/java/org/ecocean/identity/IBEISIA.java:3719:            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3766:        List<String> iaIds = org.ecocean.ia.plugin.WildbookIAM.iaAnnotationIds(context);
src/main/java/org/ecocean/identity/IAQueryCache.java:8:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/identity/IAQueryCache.java:58:        QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/identity/IAQueryCache.java:59:        CachedQuery q = qc.getQueryByName(qname);
src/main/java/org/ecocean/identity/IAQueryCache.java:82:        QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/identity/IAQueryCache.java:83:        CachedQuery q = qc.getQueryByName(qname);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:9:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:44:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:45:            CachedQuery existingCache = qc.getQueryByName(cacheName);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:68:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/WbiaQueueUtil.java:69:            CachedQuery existingCache = qc.getQueryByName(cacheName);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:54:        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:55:        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:173:     * Builds a "fancy UUID" wrapper that {@link WildbookIAM#fromFancyUUID}
src/main/java/org/ecocean/ia/MatchResult.java:131:        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
src/main/java/org/ecocean/ia/MatchResult.java:171:            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:56:        // Empty JSONObject with no __UUID__ field — fromFancyUUID returns null.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:17:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:278:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:359:        if (checkFirst) iaAnnotIds = iaAnnotationIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:469:     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:477:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:479:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:484:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:490:            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:493:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:497:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:537:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:550:            if (jo != null) ids.add(fromFancyUUID(jo));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556:     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:563:     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:564:     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:569:        // QueryCacheFactory.getQueryCache(context) can return null on a
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:574:            qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:578:        if (qc != null && qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:580:            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:583:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:627:            String decoded = fromFancyUUID(jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:676:        String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:697:            known = iaAnnotationIdsStrict(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:743:            String sentId = fromFancyUUID(sentIds.get(i));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:748:            String respId = fromFancyUUID(jid);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:764:                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:772:    public List<String> iaAnnotationIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:773:        return iaAnnotationIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:777:    public static List<String> iaAnnotationIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:783:            QueryCache qc = QueryCacheFactory.getQueryCache(context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:784:            if (qc.getQueryByName(cacheName) != null &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:786:                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:788:                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:803:            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:810:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:814:                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:825:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:840:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:872:    public static String fromFancyUUID(JSONObject u) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:47:import org.ecocean.cache.QueryCacheFactory;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4390:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4394:                if (qc.getQueryByName(("numRecentEncounters_" + thisUser)) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4395:                    CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + thisUser));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4426:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4431:            if (qc.getQueryByName(("numRecentEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4432:                CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + user.getUUID()));
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4467:        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4472:            if (qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID())) != null) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4473:                CachedQuery cq = qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID()));
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:13:class QueryCacheFactorySafeInvalidateTest {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:17:    @Test void safeInvalidate_invokesInvalidateByName_whenCacheNotNull()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:21:        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:22:            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:23:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:24:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:29:    @Test void safeInvalidate_swallowsNull_whenGetQueryCacheReturnsNull() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:30:        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:31:            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(null);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:32:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:34:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:38:    @Test void safeInvalidate_swallowsIoException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:43:        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:44:            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:45:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:47:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:52:    @Test void safeInvalidate_swallowsRuntimeException_fromGetQueryCache() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:53:        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:54:            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX))
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:56:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:58:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:62:    @Test void safeInvalidate_swallowsRuntimeException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:70:        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:71:            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:72:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:73:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/main/java/org/ecocean/servlet/importer/ImportIA.java:121:            String occID = IBEISIA.fromFancyUUID(fancyID);
src/main/java/org/ecocean/servlet/importer/ImportIA.java:126:            List<String> annotUUIDs = fromFancyUUIDList(annotFancyUUIDs);
src/main/java/org/ecocean/servlet/importer/ImportIA.java:369:    private List<String> fromFancyUUIDList(JSONArray fancyUUIDs) {
src/main/java/org/ecocean/servlet/importer/ImportIA.java:373:            ids.add(IBEISIA.fromFancyUUID(fancyUUIDs.getJSONObject(j)));

codex
I found the proposed implementation is already present in the working tree, so I’m comparing it directly against the existing strict annotation path and the cache utilities rather than relying only on the pasted diff.
 succeeded in 11295ms:
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
?? src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java
migrate-ml-service-v2

exec
/bin/bash -lc "sed -n '1,120p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' src/main/java/org/ecocean/cache/QueryCacheFactory.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '760,860p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' src/main/java/org/ecocean/cache/QueryCache.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,100p' src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
 succeeded in 313ms:
package org.ecocean.cache;

public class QueryCacheFactory {
    private static QueryCache qc;

    public synchronized static QueryCache getQueryCache(String context) {
        try {
            if (qc == null) {
                qc = new QueryCache(context);
                qc.loadQueries();
            }
            return qc;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println("I couldn't instantiate a QueryCache.");
            return null;
        }
    }

    /**
     * Invalidate a named cached query without throwing. Both
     * {@link #getQueryCache(String)} (which can return null on
     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
     * (which declares {@code throws IOException}) are wrapped so callers
     * doing best-effort cache busting don't have to repeat the same
     * defensive plumbing.
     */
    public static void safeInvalidate(String context, String cacheName) {
        try {
            QueryCache cache = getQueryCache(context);
            if (cache != null) cache.invalidateByName(cacheName);
        } catch (Exception ex) {
            System.out.println(
                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
        }
    }
}

 succeeded in 318ms:
            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
                ids.add(null);
            } else {
                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
            }
        }
        System.out.println("fromResponse ---> " + ids);
        return ids;
    }

    // instance version of below (since context is known)
    public List<String> iaAnnotationIds() {
        return iaAnnotationIds(this.context);
    }

    // this fails "gracefully" with empty list if network fubar.  bad decision?
    public static List<String> iaAnnotationIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;
        String cacheName = "iaAnnotationIds";

        try {
            QueryCache qc = QueryCacheFactory.getQueryCache(context);
            if (qc.getQueryByName(cacheName) != null &&
                System.currentTimeMillis() <
                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
            } else {
                jids = apiGetJSONArray("/api/annot/json/", context);
                if (jids != null) {
                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
                        new org.datanucleus.api.rest.orgjson.JSONObject();
                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
                    qc.addCachedQuery(cq);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
                ex.toString());
        }
        if (jids != null) {
            try {
                for (int i = 0; i < jids.length(); i++) {
                    if (jids.optJSONObject(i) != null)
                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
            }
        }
        return ids;
    }

    // as above, but images
    public List<String> iaImageIds() {
        return iaImageIds(this.context);
    }

    public static List<String> iaImageIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;

        try {
            jids = apiGetJSONArray("/api/image/json/", context);
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
                ex.toString());
        }
        if (jids != null) {
            try {
                for (int i = 0; i < jids.length(); i++) {
                    if (jids.optJSONObject(i) != null)
                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
            }
        }
        return ids;
    }

    public JSONArray apiGetJSONArray(String urlSuffix)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return apiGetJSONArray(urlSuffix, this.context);
    }

    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        URL u = IBEISIA.iaURL(context, urlSuffix);
        JSONObject rtn = RestClient.get(u);

 succeeded in 325ms:
package org.ecocean.ia.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.servlet.ServletContextEvent;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.acm.AcmUtil;
import org.ecocean.Annotation;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.media.*;
import org.ecocean.RestClient;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

// NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
import org.ecocean.identity.IBEISIA;

/*
    Wildbook Image Analysis Module (IAM)
    Initial stab at "plugin architecture" for "Image Analysis"
 */
public class WildbookIAM extends IAPlugin {
    private String context = null;

    public WildbookIAM() {
        super();
    }
    public WildbookIAM(String context) {
        super(context);
        this.context = context;
    }

    @Override public boolean isEnabled() {
        return true; // FIXME
    }

    @Override public boolean init(String context) {
        this.context = context;
        IA.log("WildbookIAM init() called on context " + context);
        return true;
    }

    @Override public void startup(ServletContextEvent sce) {
        // if we dont need identificaiton, no need to prime
        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context,
            "IBEISIADisableIdentification"));

        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
    }

    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
        final Task parentTask) {
        return null;
    }

    @Override public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
        final Task parentTask) {
        return null;
    }

    // for now "primed" is stored in IBEISIA still.  <scratches head>
    public boolean isPrimed() {
        return IBEISIA.isIAPrimed();
    }

    public void prime() {
        IA.log("INFO: WildbookIAM.prime(" + this.context +
            ") called - NOTE this is deprecated and does nothing now.");
        IBEISIA.setIAPrimed(true);
    }

/*
    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
    this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId values.  if we
 * timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab the image over the network and
       generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
 */
    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");

        if (u == null)
            throw new MalformedURLException(
                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int batchSize = 30;
        int numBatches = Math.round(mas.size() / batchSize + 1);

        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaImageIds = new ArrayList<String>();
        if (checkFirst) iaImageIds = iaImageIds();
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("image_unixtime_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());
        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
        int batchCt = 1;
        JSONObject allRtn = new JSONObject();
        allRtn.put("_batchSize", batchSize);
        allRtn.put("_totalSize", mas.size());
        JSONArray bres = new JSONArray();
        for (int i = 0; i < mas.size(); i++) {
            MediaAsset ma = mas.get(i);
            if (iaImageIds.contains(ma.getAcmId())) continue;

 succeeded in 335ms:
package org.ecocean.cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

// A singleton responsible for storing, retrieving, and eventually executing queries that are desired for caching. It will have all necessary getters
// and setters for dealing with the cache.
public class QueryCache {
    private Map<String, CachedQuery> cachedQueries;
    private String context = null;

    public QueryCache() {}
    public QueryCache(String context) {
        this.context = context;
    }

    public CachedQuery getQueryByName(String name) {
        if (cachedQueries == null) loadQueries();
        return cachedQueries.get(name);
    }

    public Map<String, CachedQuery> cachedQueries() { return cachedQueries; }

    public void loadQueries() {
        if (context == null)
            throw new RuntimeException("QueryCache.loadQueries() called with context null");
        cachedQueries = new HashMap<String, CachedQuery>();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();

        List<StoredQuery> queries = myShepherd.getAllStoredQueries();
        int numQueries = queries.size();
        for (int i = 0; i < numQueries; i++) {
            StoredQuery sq = queries.get(i);
            cachedQueries.put(sq.getName(), sq.getCachedQueryCopy());
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }

    public void invalidateByName(String name)
    throws IOException {
        if (cachedQueries.containsKey(name)) {
            cachedQueries.get(name).invalidate();
        }
    }

    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
        Shepherd myShepherd) {
        // create CachedQuery object
        CachedQuery cq = new CachedQuery(name, jsonobj, persistAsStoredQuery, myShepherd);

        // put on HashMap
        cachedQueries.put(name, cq);
    }

    public void addCachedQuery(CachedQuery cq) {
        cachedQueries.put(cq.getName(), cq);
    }
}

 succeeded in 265ms:
package org.ecocean.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.jdo.Query;

import org.apache.commons.io.IOUtils;
import org.datanucleus.api.jdo.JDOPersistenceManager;
import org.datanucleus.api.rest.RESTUtils;
import org.datanucleus.ExecutionContext;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

// A non-persistent object representing a single StoredQuery.
public class CachedQuery {
    private StoredQuery storedQuery = null;
    public static final String STATUS_PENDING = "pending"; // pending review (needs action by user)
    public static final String CACHE_PROPERTIES_PROPFILE = "cache.properties";
    public static final String CACHE_PROPERTIES_ROOTDIR = "cacheRootDirectory";

    public CachedQuery(StoredQuery sq) {
        this.storedQuery = sq;
        this.uuid = sq.getUUID();
        this.queryString = sq.getQueryString();
        this.name = sq.getName();
        this.correspondingIACacheName = sq.getCorrespondingIACacheName();
        this.expirationTimeoutDuration = sq.getExpirationTimeoutDuration();
        this.nextExpirationTimeout = sq.getNextExpirationTimeoutDuration();
    }

    public CachedQuery(String name, String queryString, long expirationTimeoutDuration) {
        this.queryString = queryString;
        this.name = name;
        this.expirationTimeoutDuration = expirationTimeoutDuration;
    }

    public CachedQuery(String name, JSONObject jsonSerializedQueryResult,
        boolean persistAsStoredQuery, Shepherd myShepherd) {
        this.name = name;
        this.jsonSerializedQueryResult = jsonSerializedQueryResult;
        if (persistAsStoredQuery) {
            try {
                // OK, so we need to serialize out the result
                Util.writeToFile(jsonSerializedQueryResult.toString(),
                    getCacheFile().getAbsolutePath());

                StoredQuery sq = new StoredQuery(name);
                myShepherd.getPM().makePersistent(sq);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public CachedQuery(String name, JSONObject jsonSerializedQueryResult) {
        this.name = name;
        this.jsonSerializedQueryResult = jsonSerializedQueryResult;
    }

    // primary key, persistent, String, not null
    private String uuid;

    // The JDOQL representation of the query, persistent, String, not null
    private String queryString;

    // a human-readable name for the query, persistent, String, not null, unique
    private String name;

    // if this query matches an IA cache this field in the name of the cache, String, persistent
    private String correspondingIACacheName;

    // The time duration (diff) between create time and this queries expiration time in milliseconds, requiring a refresh of cached items.
    public long expirationTimeoutDuration = -1;

    // the next time this cache expires
    public long nextExpirationTimeout = -1;

    public JSONObject jsonSerializedQueryResult;
    public Integer collectionQueryCount;

    public String getName() { return name; }

    public String getUUID() { return uuid; }

    public String getQueryString() { return queryString; }

    public String getCorrespondingIACacheName() { return correspondingIACacheName; }

 succeeded in 313ms:
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * ml-service migration v2 §commit #11 fix-pass. Pure-function coverage of
 * the no-Shepherd WBIA registration helpers introduced in
 * {@link org.ecocean.ia.plugin.WildbookIAM}. Network-bound behavior of
 * {@code registerOneByDto} itself is exercised end-to-end by the
 * polling thread integration in a dev deployment; here we cover the
 * pieces that can be tested without WireMock/Tomcat.
 */
class WildbookIAMRegisterTest {

    private static WbiaRegisterRequest sampleDto() {
        return new WbiaRegisterRequest(
            "ann-uuid-1", "ann-acm-1", "ma-acm-1",
            new int[] { 10, 20, 100, 200 },
            0.0d, "right_dorsalfin", "indiv-1");
    }

    // --- buildForcedRequestMap -------------------------------------------

    @Test void buildForcedRequestMapPopulatesAllLists() {
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
        assertEquals(1, map.get("image_uuid_list").size());
        assertEquals(1, map.get("annot_uuid_list").size());
        assertEquals(1, map.get("annot_species_list").size());
        assertEquals(1, map.get("annot_bbox_list").size());
        assertEquals(1, map.get("annot_name_list").size());
        assertEquals(1, map.get("annot_theta_list").size());
        assertEquals("right_dorsalfin", map.get("annot_species_list").get(0));
        assertEquals("indiv-1", map.get("annot_name_list").get(0));
    }

    @Test void buildForcedRequestMapWrapsUuidsInFancyForm() {
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
        JSONObject annUuid = (JSONObject) map.get("annot_uuid_list").get(0);
        JSONObject imgUuid = (JSONObject) map.get("image_uuid_list").get(0);
        assertEquals("ann-uuid-1", WildbookIAM.fromFancyUUID(annUuid));
        assertEquals("ma-acm-1",   WildbookIAM.fromFancyUUID(imgUuid));
    }

    @Test void buildForcedRequestMapNullIndividualSerializesUnderscores() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-2", "ann-acm-2", "ma-2", new int[] { 0, 0, 1, 1 },
            1.5d, "iaClass", null);
        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(dto);
        assertEquals("____", map.get("annot_name_list").get(0));
    }

    // --- validateForcedResponse ------------------------------------------

    @Test void validateForcedResponseAcceptsMatchingId() throws IOException {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("ann-uuid-1")));
        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    }

    @Test void validateForcedResponseRejectsNull() {
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("x", null));
    }

    @Test void validateForcedResponseRejectsMissingArray() {
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("x", new JSONObject()));
    }

    @Test void validateForcedResponseRejectsWrongLength() {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("a")).put(makeFancy("b")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("a", resp));
    }

    @Test void validateForcedResponseRejectsIdMismatch() {
        JSONObject resp = new JSONObject().put("response",
            new JSONArray().put(makeFancy("other-id")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    }

    @Test void validateForcedResponseRejectsStatusSuccessFalse() {
        JSONObject resp = new JSONObject()
            .put("status", new JSONObject().put("success", false))
            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
        assertThrows(IOException.class,
            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
    }

    @Test void validateForcedResponseTreatsStatusSuccessTrueAsOK() throws IOException {
        JSONObject resp = new JSONObject()
            .put("status", new JSONObject().put("success", true))
            .put("response", new JSONArray().put(makeFancy("ann-uuid-1")));
        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
    }

    // --- parseAnnotationIdsArray -----------------------------------------

    @Test void parseAnnotationIdsArrayReturnsEmptyOnNull() {
        assertTrue(WildbookIAM.parseAnnotationIdsArray(null).isEmpty());
    }

    @Test void parseAnnotationIdsArrayExtractsUuids() {
        JSONArray jids = new JSONArray()

exec
/bin/bash -lc "sed -n '120,260p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 273ms:
        JSONArray jids = new JSONArray()
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArraySkipsNonObjectEntries() {
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
    }
}

exec
/bin/bash -lc "sed -n '860,910p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 272ms:
        JSONObject rtn = RestClient.get(u);

        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
            (rtn.optJSONArray("response") == null) ||
            !rtn.getJSONObject("status").optBoolean("success", false)) {
            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
                rtn);
            return null;
        }
        return rtn.getJSONArray("response");
    }

    public static String fromFancyUUID(JSONObject u) {
        if (u == null) return null;
        return u.optString("__UUID__", null);
    }

    public static JSONObject toFancyUUID(String u) {
        JSONObject j = new JSONObject();

        j.put("__UUID__", u);
        return j;
    }

    /**
     * Build the URL string WBIA expects in {@code image_uri_list}. The
     * double-encoded "?" pattern preserves filenames that contain "?" so
     * WBIA's HTTP layer doesn't truncate them at the query boundary.
     *
     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
     * {@code null}. Promoted from {@code private Object} to
     * {@code public String} (and the leading-NPE on {@code curl.toString()}
     * tightened) so the ml-service v2 WBIA registration polling thread
     * can call it from Phase A while building the {@link WbiaRegisterRequest}
     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
     */
    public static String mediaAssetToUri(MediaAsset ma) {
        if (ma == null) return null;
        URL curl = ma.webURL();
        if (curl == null) return null;
        String urlStr = curl.toString();
        if (urlStr == null) return null;
        // THIS WILL BREAK if you need to append a query to the filename...
        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
        return urlStr.replaceAll("\\?", "%3F");
    }

    // basically "should we send to IA?"
    public static boolean validMediaAsset(MediaAsset ma) {
        if (ma == null) return false;
        if (!ma.isMimeTypeMajor("image")) return false;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '468,635p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '688,715p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"safeInvalidate\\(.*iaImageIds|invalidate\\(context, \\\"iaImageIds\\\"|iaImageIdsStrict|iaImageIds\" src/main/java src/test/java docs/plans/2026-05-09-ml-service-migration-v2.md" in /mnt/c/Wildbook-clean2
 succeeded in 231ms:
     1	package org.ecocean.ia.plugin;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertThrows;
     5	
     6	import java.io.IOException;
     7	import java.util.List;
     8	
     9	import org.json.JSONArray;
    10	import org.json.JSONObject;
    11	import org.junit.jupiter.api.Test;
    12	
    13	/**
    14	 * Pure-function coverage of {@link WildbookIAM#parseImageIdsArrayStrict}.
    15	 * Network-bound behavior of {@link WildbookIAM#iaImageIdsStrict} itself
    16	 * is exercised end-to-end by Phase 0 of the WBIA registration polling
    17	 * thread in a dev deployment; here we cover the pieces that can be tested
    18	 * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
    19	 */
    20	class WildbookIAMImageIdsStrictTest {
    21	
    22	    private static JSONObject fancyUuid(String uuid) {
    23	        JSONObject jo = new JSONObject();
    24	        jo.put("__UUID__", uuid);
    25	        return jo;
    26	    }
    27	
    28	    @Test void parseImageIdsArrayStrict_returnsEmptyList_whenInputIsNull()
    29	    throws IOException {
    30	        List<String> out = WildbookIAM.parseImageIdsArrayStrict(null);
    31	        assertEquals(0, out.size());
    32	    }
    33	
    34	    @Test void parseImageIdsArrayStrict_decodesValidFancyUuids()
    35	    throws IOException {
    36	        JSONArray jids = new JSONArray();
    37	        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
    38	        jids.put(fancyUuid("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb"));
    39	        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
    40	        assertEquals(2, out.size());
    41	        assertEquals("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa", out.get(0));
    42	        assertEquals("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb", out.get(1));
    43	    }
    44	
    45	    @Test void parseImageIdsArrayStrict_throwsIoException_onNonJsonObjectEntry() {
    46	        JSONArray jids = new JSONArray();
    47	        jids.put(fancyUuid("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
    48	        jids.put("not-an-object");  // string entry, not JSONObject
    49	        IOException ex = assertThrows(IOException.class,
    50	            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
    51	        assertEquals(true, ex.getMessage().contains("iaImageIds entry 1"));
    52	    }
    53	
    54	    @Test void parseImageIdsArrayStrict_throwsIoException_onUndecodableEntry() {
    55	        JSONArray jids = new JSONArray();
    56	        // Empty JSONObject with no __UUID__ field — fromFancyUUID returns null.
    57	        jids.put(new JSONObject());
    58	        IOException ex = assertThrows(IOException.class,
    59	            () -> WildbookIAM.parseImageIdsArrayStrict(jids));
    60	        assertEquals(true, ex.getMessage().contains("could not be decoded"));
    61	    }
    62	
    63	    @Test void parseImageIdsArrayStrict_preservesOrder()
    64	    throws IOException {
    65	        JSONArray jids = new JSONArray();
    66	        jids.put(fancyUuid("00000000-0000-4000-8000-000000000003"));
    67	        jids.put(fancyUuid("00000000-0000-4000-8000-000000000001"));
    68	        jids.put(fancyUuid("00000000-0000-4000-8000-000000000002"));
    69	        List<String> out = WildbookIAM.parseImageIdsArrayStrict(jids);
    70	        assertEquals("00000000-0000-4000-8000-000000000003", out.get(0));
    71	        assertEquals("00000000-0000-4000-8000-000000000001", out.get(1));
    72	        assertEquals("00000000-0000-4000-8000-000000000002", out.get(2));
    73	    }
    74	}

 succeeded in 241ms:
   468	    /**
   469	     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
   470	     * fetch failure rather than returning an empty list. Phase B needs
   471	     * this so a network failure during the already-present check is
   472	     * not silently treated as "go ahead and POST".
   473	     *
   474	     * <p>Honors the 15-minute QueryCache the same way the lenient
   475	     * variant does, so a cache hit avoids the network entirely.</p>
   476	     */
   477	    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
   478	        String cacheName = "iaAnnotationIds";
   479	        // QueryCacheFactory.getQueryCache(context) can return null on a
   480	        // context that has never been initialized; treat that as "no cache"
   481	        // rather than NPE-ing out and aborting the poll cycle.
   482	        QueryCache qc = null;
   483	        try {
   484	            qc = QueryCacheFactory.getQueryCache(context);
   485	        } catch (Exception ex) {
   486	            // Defensive: cache factory init can fail; degrade to no-cache.
   487	        }
   488	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   489	            System.currentTimeMillis() <
   490	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   491	            try {
   492	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   493	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   494	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   495	                return parseAnnotationIdsArrayStrict(cached);
   496	            } catch (Exception ex) {
   497	                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
   498	                    + ex.getMessage());
   499	            }
   500	        }
   501	        JSONArray jids;
   502	        try {
   503	            jids = apiGetJSONArray("/api/annot/json/", context);
   504	        } catch (Exception ex) {
   505	            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
   506	        }
   507	        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
   508	        if (qc != null) {
   509	            try {
   510	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   511	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   512	                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   513	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   514	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   515	                qc.addCachedQuery(cq);
   516	            } catch (Exception cacheEx) {
   517	                // Cache store failure is non-fatal; we still have the ids.
   518	            }
   519	        }
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
   531	        List<String> ids = new ArrayList<String>();
   532	        if (jids == null) return ids;
   533	        for (int i = 0; i < jids.length(); i++) {
   534	            JSONObject jo = jids.optJSONObject(i);
   535	            if (jo == null)
   536	                throw new IOException("iaAnnotationIds entry " + i + " is not a JSONObject");
   537	            String decoded = fromFancyUUID(jo);
   538	            if (decoded == null)
   539	                throw new IOException("iaAnnotationIds entry " + i + " could not be decoded: " + jo);
   540	            ids.add(decoded);
   541	        }
   542	        return ids;
   543	    }
   544	
   545	    static List<String> parseAnnotationIdsArray(JSONArray jids) {
   546	        List<String> ids = new ArrayList<String>();
   547	        if (jids == null) return ids;
   548	        for (int i = 0; i < jids.length(); i++) {
   549	            JSONObject jo = jids.optJSONObject(i);
   550	            if (jo != null) ids.add(fromFancyUUID(jo));
   551	        }
   552	        return ids;
   553	    }
   554	
   555	    /**
   556	     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
   557	     * failure rather than returning an empty list. The new Phase 0 of
   558	     * the v2 WBIA registration polling thread needs this so a network
   559	     * failure during the "is the image already registered with WBIA?"
   560	     * check is not silently treated as "go ahead and POST".
   561	     *
   562	     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
   563	     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
   564	     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
   565	     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
   566	     */
   567	    public static List<String> iaImageIdsStrict(String context) throws IOException {
   568	        String cacheName = "iaImageIds";
   569	        // QueryCacheFactory.getQueryCache(context) can return null on a
   570	        // context that has never been initialized; treat that as "no cache"
   571	        // rather than NPE-ing out and aborting the poll cycle.
   572	        QueryCache qc = null;
   573	        try {
   574	            qc = QueryCacheFactory.getQueryCache(context);
   575	        } catch (Exception ex) {
   576	            // Defensive: cache factory init can fail; degrade to no-cache.
   577	        }
   578	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   579	            System.currentTimeMillis() <
   580	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   581	            try {
   582	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   583	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   584	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
   585	                return parseImageIdsArrayStrict(cached);
   586	            } catch (Exception ex) {
   587	                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
   588	                    + ex.getMessage());
   589	            }
   590	        }
   591	        JSONArray jids;
   592	        try {
   593	            jids = apiGetJSONArray("/api/image/json/", context);
   594	        } catch (Exception ex) {
   595	            throw new IOException("WBIA /api/image/json/ fetch failed: " + ex.getMessage(), ex);
   596	        }
   597	        if (jids == null) throw new IOException("WBIA /api/image/json/ returned null");
   598	        if (qc != null) {
   599	            try {
   600	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   601	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   602	                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
   603	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   604	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   605	                qc.addCachedQuery(cq);
   606	            } catch (Exception cacheEx) {
   607	                // Cache store failure is non-fatal; we still have the ids.
   608	            }
   609	        }
   610	        return parseImageIdsArrayStrict(jids);
   611	    }
   612	
   613	    /**
   614	     * Strict element parser: throws IOException if any element is not a
   615	     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
   616	     * a future commit (C4) extracts the common
   617	     * {@code parseFancyUuidArrayStrict(JSONArray, String)} body, but
   618	     * keeping the two named entry points preserves grep-friendly call sites.
   619	     */
   620	    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
   621	        List<String> ids = new ArrayList<String>();
   622	        if (jids == null) return ids;
   623	        for (int i = 0; i < jids.length(); i++) {
   624	            JSONObject jo = jids.optJSONObject(i);
   625	            if (jo == null)
   626	                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
   627	            String decoded = fromFancyUUID(jo);
   628	            if (decoded == null)
   629	                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
   630	            ids.add(decoded);
   631	        }
   632	        return ids;
   633	    }
   634	
   635	    /**

 succeeded in 262ms:
   688	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
   689	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
   690	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   691	        if (u == null) {
   692	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
   693	            return WbiaRegisterOutcome.NETWORK_FAIL;
   694	        }
   695	        List<String> known;
   696	        try {
   697	            known = iaAnnotationIdsStrict(context);
   698	        } catch (IOException ex) {
   699	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
   700	                ex.getMessage());
   701	            return WbiaRegisterOutcome.NETWORK_FAIL;
   702	        }
   703	        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
   704	        // only check the annotation's id and acmId here. Comparing against
   705	        // the media-asset's acmId is wrong - that would compare an image
   706	        // identifier against a list of annotation identifiers.
   707	        if (known.contains(dto.annotationId) ||
   708	            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
   709	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
   710	        }
   711	        URL url;
   712	        try {
   713	            url = new URL(u);
   714	        } catch (MalformedURLException ex) {
   715	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);

 succeeded in 777ms:
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:15:    private static final String NAME = "iaImageIds";
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:15: * Network-bound behavior of {@link WildbookIAM#iaImageIdsStrict} itself
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:51:        assertEquals(true, ex.getMessage().contains("iaImageIds entry 1"));
src/main/java/org/ecocean/identity/IBEISIA.java:3710:        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
src/main/java/org/ecocean/identity/IBEISIA.java:3711:        HashSet<String> iaImageIds = null;
src/main/java/org/ecocean/identity/IBEISIA.java:3718:            // get iaImageIds only if we need it
src/main/java/org/ecocean/identity/IBEISIA.java:3719:            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
src/main/java/org/ecocean/identity/IBEISIA.java:3720:            if (iaImageIds.isEmpty())
src/main/java/org/ecocean/identity/IBEISIA.java:3721:                throw new RuntimeException("iaImageIds is empty; possible IA problems");
src/main/java/org/ecocean/identity/IBEISIA.java:3722:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556:     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:562:     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:564:     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:567:    public static List<String> iaImageIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:568:        String cacheName = "iaImageIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:584:                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:587:                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:602:                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:626:                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:825:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());

2026-05-18T21:00:21.490740Z ERROR codex_core::tools::router: error=write_stdin failed: stdin is closed for this session; rerun exec_command with tty=true to keep stdin open
codex
The key behavior does match the annotation strict method structurally: cache lookup, strict parse on hit, fetch and throw on API failure/null, best-effort cache store, strict parse on fresh result. I’m checking the Phase 0 call shape next because the cache only helps if the subsequent POST invalidates the same name.
exec
/bin/bash -lc 'rg -n "Phase 0|iaImageIdsStrict|safeInvalidate|iaImageIds|REGISTERED_IMAGE|image already|already registered|WbiaRegisterOutcome" docs/plans/2026-05-09-ml-service-migration-v2.md docs/plans/2026-05-18-wbia-image-registration-design.md src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/ia' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,160p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "registerOneByDto|WbiaRegisterRequest|mediaAssetAcmId|REGISTERED_ALREADY_PRESENT|NETWORK_FAIL|RESPONSE_BAD" src/main/java/org/ecocean/ia src/test/java/org/ecocean/ia/plugin' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '160,360p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
 succeeded in 209ms:
# Design: image-then-annotation WBIA registration in polling thread

## Context

The v2 WBIA registration polling thread (commit `c6ffe5d20` and follow-ups)
calls `/api/annot/json/` to register each ml-service-created annotation
with WBIA. The thread is meant to keep WBIA in sync so HotSpotter remains
available as a fallback identifier.

What works today:
- The polling JDOQL picks pending annotations (`wbiaRegistered == false
  && wbiaRegisterAttempts < 10`).
- Phase A loads a detached DTO under a short Shepherd transaction.
- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
- Phase C persists the outcome in a fresh Shepherd.

What's broken in production: the legacy v2 routing path
(`IA.intakeMediaAssetsOneSpeciesMlService`) **never tells WBIA about the
image** — it skips the legacy `WBIA.sendMediaAssets()` call because
ml-service does its own detection. By the time the annotation-registration
polling thread fires `/api/annot/json/`, WBIA has no record of the image
uuid the annotation references. WBIA returns HTTP 500 with `ValueError:
The input list image_uuid_list has invalid values (index, value): [(0,
None)]`. Annotations get marked failed and retry until `MAX_ATTEMPTS=10`,
then park.

## Goal

Make the polling thread register the image first when needed, then the
annotation. Keep it non-blocking (no foreground caller is gated on WBIA),
keep one retry counter, keep one polling thread.

## Non-goals

- Modifying `IA.intakeMediaAssetsOneSpeciesMlService` to do image
  registration at intake time. The polling thread can handle it
  retroactively, which keeps the intake fast path simple.
- Adding a parallel image-registration polling thread. Doubles the
  background-thread inventory for no benefit; the single annotation
  thread already iterates pending work at a sensible cadence.
- Adding a new JDO column for image-registration state. The existing
  `wbiaRegistered` / `wbiaRegisterAttempts` columns govern both phases
  atomically.

## Audit: what already exists

| Helper | Status | Reused as-is |
|---|---|---|
| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
| `WildbookIAM.sendMediaAssetsForceId(ArrayList<MediaAsset>, boolean)` | exists, batch POST to `/api/image/json/`, sends our acmId via `image_uuid_list` | The POST body shape and `toFancyUUID` wrapping logic lift line-for-line into the new DTO-based variant |
| `WildbookIAM.mediaAssetToUri(MediaAsset)` | exists but is **`private`** at line 804. Returns the double-encoded web URL string. Note: `ma.webURL()` can return null and the existing helper dereferences without checking — a long-standing latent NPE. | Promote to `public static String` (signature change from `Object` return; tighten the null-check). Phase A then calls it directly to capture `imageUri` into the DTO. |
| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
| `AcmUtil` | exists | Not relevant — its `rectify*` utilities are for syncing acmId values, but v2 owns the acmId so no rectification needed |

## Design

### DTO extension

Extend `WildbookIAM.WbiaRegisterRequest` with four image-side fields,
populated in Phase A:

```java
public final String imageUri;             // mediaAssetToUri(ma) result
public final Double imageLatitude;        // ma.getLatitude(), nullable
public final Double imageLongitude;       // ma.getLongitude(), nullable
public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
```

`mediaAssetUuid` is not needed as a separate field because v2's
convention is `MediaAsset.acmId == MediaAsset.uuid` (commit `2a3eab63a`);
`dto.mediaAssetAcmId` already carries the value `sendMediaAssetsForceId`
puts in `image_uuid_list`.

### Phase A additions

`StartupWildbook.loadWbiaRegisterDto` (added in commit `c6ffe5d20`) already
captures the annotation-side eligibility (`mediaAsset != null &&
acmId != null && validForIdentification`). Add TWO image-side eligibility
checks in the same block (mirroring what `sendMediaAssetsForceId` does
at line 121-130, in the same order):

```java
if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
    reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
} else if (!WildbookIAM.validMediaAsset(ma)) {
    reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
}
```

If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
behavior — keeps the ineligible-park path consistent).

Then capture the image fields into the DTO:

```java
String imageUri = (String) WildbookIAM.mediaAssetToUri(ma);  // method returns Object; cast
DateTime dt = ma.getDateTime();
Long dateTimeMillis = (dt == null) ? null : dt.getMillis();
WbiaRegisterRequest dto = new WbiaRegisterRequest(
    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy, ann.getTheta(),
    ann.getIAClass(), name,
    imageUri, ma.getLatitude(), ma.getLongitude(), dateTimeMillis  // NEW
);
```

The c11 fix-pass refactor of Phase A is preserved (still short DB tx, no
network).

### New `iaImageIdsStrict(context)` in `WildbookIAM`

Strict variant mirroring `iaAnnotationIdsStrict` (added in c11 fix-pass).
Same shape:

- Honors the existing 15-min `QueryCache`.
- Throws `IOException` on fetch failure (vs. lenient variant which
  swallows and returns empty list).
- Uses a strict element parser (no silently-skipped malformed entries).

Roughly 40 lines, parallel to the annotation version. Reuses the same
`apiGetJSONArray` and `parseAnnotationIdsArrayStrict`-style helpers; the
parser helper is duplicated rather than generalized because the cache key
and endpoint path differ.

### New `sendMediaAssetByDto(WbiaRegisterRequest dto)` in `WildbookIAM`

Single-image variant of `sendMediaAssetsForceId`, DTO-only (no Shepherd
parameter, no MediaAsset entity):

```java
public boolean sendMediaAssetByDto(WbiaRegisterRequest dto) {
    String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
    if (u == null) return false;
    URL url;
    try { url = new URL(u); } catch (MalformedURLException ex) { return false; }
    HashMap<String, ArrayList> map = buildSingleImageRequestMap(dto);
    try {
        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        // Validate status.success; the legacy WBIA wrapper returns
        // {"status": {"success": true/false, ...}, "response": ...}
        if (!isStatusSuccess(rtn)) return false;
        return true;
    } catch (Exception ex) {
        IA.log("WARNING: sendMediaAssetByDto() POST failed: " + ex.getMessage());
        return false;
    }
}

static HashMap<String, ArrayList> buildSingleImageRequestMap(WbiaRegisterRequest dto) {
    HashMap<String, ArrayList> map = new HashMap<>();
    map.put("image_uri_list", new ArrayList<String>());
    map.put("image_uuid_list", new ArrayList<JSONObject>());
    map.put("image_unixtime_list", new ArrayList<Integer>());
    map.put("image_gps_lat_list", new ArrayList<Double>());
    map.put("image_gps_lon_list", new ArrayList<Double>());
    map.get("image_uri_list").add(dto.imageUri);
    map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
    map.get("image_unixtime_list").add(
        dto.imageDateTimeMillis == null ? null
                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
    map.get("image_gps_lat_list").add(dto.imageLatitude);

 succeeded in 219ms:
    map.get("image_gps_lat_list").add(dto.imageLatitude);
    map.get("image_gps_lon_list").add(dto.imageLongitude);
    return map;
}
```

The pure helpers (`buildSingleImageRequestMap`, `isStatusSuccess`) are
extracted so unit tests can verify the request shape without a network
round-trip.

### Phase B `registerOneByDto` modification

Add Phase 0 at the entry; existing Phase 1 (annotation registration)
moves down unchanged:

```java
public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;

    // ---- Phase 0: ensure WBIA knows about the image ----
    List<String> knownImages;
    try { knownImages = iaImageIdsStrict(context); }
    catch (IOException ex) {
        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
        return WbiaRegisterOutcome.NETWORK_FAIL;
    }
    if (!knownImages.contains(dto.mediaAssetAcmId)) {
        if (!sendMediaAssetByDto(dto)) {
            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
        }
        // Invalidate the image-ids cache so the next annotation on this
        // image sees the updated list — without this, the 15-min cache
        // would still report the image as absent and we'd re-POST it
        // every poll cycle.
        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
    }

    // ---- Phase 1: existing annotation registration logic ----
    List<String> knownAnnots;
    try { knownAnnots = iaAnnotationIdsStrict(context); }
    catch (IOException ex) { return NETWORK_FAIL; }
    // ... rest of existing logic unchanged ...
}
```

### Outcome semantics

No new `WbiaRegisterOutcome` value. The 4 existing values handle every
case cleanly:

- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).

Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
the annotation parks after 10 attempts — same as today, just with image
registration also having been attempted along the way.

### Cache invalidation note

After a successful Phase 0 POST, we must invalidate the `iaImageIds`
QueryCache entry. Otherwise the next annotation on the same image (within
15 minutes) would still see the cached image-ids list (which didn't
include this image), trigger Phase 0 again, attempt to register the
already-registered image (skip-if-present inside `sendMediaAssetsForceId`
would no-op, but it's wasted work), and worse — *if a different
annotation needs the same image*, we'd repeat the dance per annotation.

Verify QueryCache has an invalidation API. If not, fall back to direct
removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
Worst case: skip invalidation and accept that the next ~30 polling ticks
per image will see stale cache. Trade-off is minor since the cache TTL is
only 15 minutes and `sendMediaAssetByDto` is a single fast POST.

## Tests

Layer 1 — pure-function (`WildbookIAMRegisterTest` additions):

- `buildSingleImageRequestMapPopulatesAllLists` — verifies the 5 list
  shapes (uri, uuid, unixtime, gps_lat, gps_lon).
- `buildSingleImageRequestMapHandlesNullDatetime` — `null` datetime → 
  `null` in unixtime list (not 0 or omitted).
- `buildSingleImageRequestMapWrapsUuidInFancyForm` — verifies `toFancyUUID`
  is called on `mediaAssetAcmId`.
- `parseImageIdsArrayStrict` — paralleling
  `parseAnnotationIdsArrayStrict`: null array returns empty; well-formed
  returns list; non-object entry raises IOException; undecodable raises.
- `isStatusSuccess` — accepts `status.success=true`, rejects `false`,
  rejects missing `status`.

Layer 2 — flow (mocked HTTP):

- `registerOneByDto_image_already_present_skips_phase0_post` — mock
  iaImageIdsStrict to return list including dto's acmId; verify no
  sendMediaAssetByDto call.
- `registerOneByDto_image_absent_triggers_phase0_post` — mock empty
  image-ids list; verify sendMediaAssetByDto called; verify
  iaAnnotationIdsStrict called after (Phase 1 reached).
- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
  throw IOException; verify NETWORK_FAIL returned without any POST.
- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
  succeed → REGISTERED_OK.

Layer 3 — Phase A + outcome integration (new test class
`WbiaRegistrationPhaseAImageFieldsTest` or appended to existing): verify
the DTO captures `imageUri`, lat, lon, datetimeMillis correctly from a
mock MediaAsset; verify `validMediaAsset` failure parks the annotation.

## Failure modes

| Scenario | Outcome | Why |
|---|---|---|
| MediaAsset has null acmId at Phase A | Park (MAX_ATTEMPTS) | Already handled by existing eligibility check |
| MediaAsset fails validMediaAsset at Phase A | Park (MAX_ATTEMPTS) | New eligibility check added |
| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
| Image POST fails | NETWORK_FAIL | Retry next tick |
| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
| Image POST succeeds + cache invalidation not supported | Functional but mildly wasteful | Per-annotation re-check until cache TTL expires |

## Codex review gates

Per the locked-in workflow:

1. Design review: this document. Codex green-lights before any code.
2. Code review: single implementation commit (DTO additions + new
   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
   before merge.
3. Post-commit verify: if Codex finds issues, fix-pass commit + re-review.

## Open questions for Codex

1. **QueryCache invalidation API**: does `QueryCacheFactory.getQueryCache`
   expose a way to remove a single named cached query? If not, the
   "minor staleness" trade-off above is acceptable. Worth verifying
   before implementation.
2. **`isStatusSuccess` placement**: this is a generic WBIA-response
   helper. Should it live in `WildbookIAM` (alongside the other request/
   response helpers) or in `IBEISIA` (which has historical knowledge of
   the WBIA wrapper shape)? Current draft says `WildbookIAM` for locality;
   open to moving it.
3. **Strict-parser duplication**: `parseAnnotationIdsArrayStrict` already
   exists from c11 fix-pass. The new `parseImageIdsArrayStrict` would be
   almost identical. Worth factoring into a shared private helper? Or
   accept duplication for grep-readability (image-ids and annotation-ids
   conceptually different, even if mechanically identical today).
4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
   Phase A's eligibility check rather than Phase B. Acceptable, or
   should Phase B also defensively check (in case state changed between
   Phase A and Phase B)? The window is short (Phase A commits, Phase B
   immediately calls HTTP), but the strict no-trust-managed-state c12
   pattern suggests Phase B could re-check via DTO field. My judgment is
   it's not worth the field — the MA's validity is set at construction
   time and rarely changes.

 succeeded in 354ms:
docs/plans/2026-05-09-ml-service-migration-v2.md:103:     backfills them with wbiaRegistered=true (they were already registered
docs/plans/2026-05-18-wbia-image-registration-design.md:49:| `WildbookIAM.iaImageIds(context)` | exists, lenient (swallows errors → empty list). **No QueryCache**, unlike `iaAnnotationIds`. | Yes, indirectly — we add a strict variant alongside it, AND introduce caching in the strict variant (since the lenient variant doesn't have it today) |
docs/plans/2026-05-18-wbia-image-registration-design.md:109:### New `iaImageIdsStrict(context)` in `WildbookIAM`
docs/plans/2026-05-18-wbia-image-registration-design.md:172:Add Phase 0 at the entry; existing Phase 1 (annotation registration)
docs/plans/2026-05-18-wbia-image-registration-design.md:176:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-wbia-image-registration-design.md:177:    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-wbia-image-registration-design.md:179:    // ---- Phase 0: ensure WBIA knows about the image ----
docs/plans/2026-05-18-wbia-image-registration-design.md:181:    try { knownImages = iaImageIdsStrict(context); }
docs/plans/2026-05-18-wbia-image-registration-design.md:183:        IA.log("WARNING: iaImageIdsStrict failed: " + ex.getMessage());
docs/plans/2026-05-18-wbia-image-registration-design.md:184:        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-wbia-image-registration-design.md:188:            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-wbia-image-registration-design.md:207:No new `WbiaRegisterOutcome` value. The 4 existing values handle every
docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-wbia-image-registration-design.md:217:Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-wbia-image-registration-design.md:227:include this image), trigger Phase 0 again, attempt to register the
docs/plans/2026-05-18-wbia-image-registration-design.md:233:removal via `qc.removeCachedQueryByName("iaImageIds")` or equivalent.
docs/plans/2026-05-18-wbia-image-registration-design.md:257:  iaImageIdsStrict to return list including dto's acmId; verify no
docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556:     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:557:     * failure rather than returning an empty list. The new Phase 0 of
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:559:     * failure during the "is the image already registered with WBIA?"
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:562:     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:564:     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:567:    public static List<String> iaImageIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:568:        String cacheName = "iaImageIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:584:                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:587:                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:602:                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:626:                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:825:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:104:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:105:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:191:        List<String> iaImageIds = new ArrayList<String>();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:192:        if (checkFirst) iaImageIds = iaImageIds();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556:     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:557:     * failure rather than returning an empty list. The new Phase 0 of
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:559:     * failure during the "is the image already registered with WBIA?"
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:562:     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:564:     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:567:    public static List<String> iaImageIdsStrict(String context) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:568:        String cacheName = "iaImageIds";
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:584:                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:587:                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:602:                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:626:                throw new IOException("iaImageIds entry " + i + " is not a JSONObject");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:                throw new IOException("iaImageIds entry " + i + " could not be decoded: " + jo);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821:    public List<String> iaImageIds() {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:        return iaImageIds(this.context);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:825:    public static List<String> iaImageIds(String context) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:833:            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());

 succeeded in 440ms:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:449:        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:456:            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:460:            this.mediaAssetAcmId = mediaAssetAcmId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:640:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:648:        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:893:     * can call it from Phase A while building the {@link WbiaRegisterRequest}
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(

exec
/bin/bash -lc "rg -n \"void invalidate|invalidate\\(\" src/main/java/org/ecocean/cache/CachedQuery.java src/main/java/org/ecocean/cache -C 3" in /mnt/c/Wildbook-clean2
 succeeded in 422ms:
src/main/java/org/ecocean/cache/CachedQuery.java-203-        return collectionQueryCount;
src/main/java/org/ecocean/cache/CachedQuery.java-204-    }
src/main/java/org/ecocean/cache/CachedQuery.java-205-
src/main/java/org/ecocean/cache/CachedQuery.java:206:    public synchronized void invalidate()
src/main/java/org/ecocean/cache/CachedQuery.java-207-    throws IOException {
src/main/java/org/ecocean/cache/CachedQuery.java-208-        collectionQueryCount = null;
src/main/java/org/ecocean/cache/CachedQuery.java-209-        jsonSerializedQueryResult = null;
--
src/main/java/org/ecocean/cache/QueryCache.java-43-        myShepherd.closeDBTransaction();
src/main/java/org/ecocean/cache/QueryCache.java-44-    }
src/main/java/org/ecocean/cache/QueryCache.java-45-
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java-47-    throws IOException {
src/main/java/org/ecocean/cache/QueryCache.java-48-        if (cachedQueries.containsKey(name)) {
src/main/java/org/ecocean/cache/QueryCache.java:49:            cachedQueries.get(name).invalidate();
src/main/java/org/ecocean/cache/QueryCache.java-50-        }
src/main/java/org/ecocean/cache/QueryCache.java-51-    }
src/main/java/org/ecocean/cache/QueryCache.java-52-
--
src/main/java/org/ecocean/cache/CachedQuery.java-203-        return collectionQueryCount;
src/main/java/org/ecocean/cache/CachedQuery.java-204-    }
src/main/java/org/ecocean/cache/CachedQuery.java-205-
src/main/java/org/ecocean/cache/CachedQuery.java:206:    public synchronized void invalidate()
src/main/java/org/ecocean/cache/CachedQuery.java-207-    throws IOException {
src/main/java/org/ecocean/cache/CachedQuery.java-208-        collectionQueryCount = null;
src/main/java/org/ecocean/cache/CachedQuery.java-209-        jsonSerializedQueryResult = null;

exec
/bin/bash -lc "sed -n '190,220p' src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
 succeeded in 264ms:
       }
     */
    public Integer executeCountQuery(Shepherd myShepherd) {
        if ((collectionQueryCount == null) ||
            ((expirationTimeoutDuration > -1) &&
            (System.currentTimeMillis() > nextExpirationTimeout))) {
            try {
                // System.out.println("Executing executeCountQuery");
                List<Object> c = executeQuery(myShepherd);
                collectionQueryCount = new Integer(c.size());
                nextExpirationTimeout = System.currentTimeMillis() + expirationTimeoutDuration;
            } catch (Exception e) { e.printStackTrace(); }
        }
        return collectionQueryCount;
    }

    public synchronized void invalidate()
    throws IOException {
        collectionQueryCount = null;
        jsonSerializedQueryResult = null;

        // delete the serialized JSON
        getCacheFile().delete();
    }

    public synchronized JSONObject getJSONSerializedQueryResult() {
        return jsonSerializedQueryResult;
    }

    public synchronized void setJSONSerializedQueryResult(JSONObject jsonSerializedQueryResult,
        boolean serialize) {

exec
/bin/bash -lc "sed -n '1,100p' src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 254ms:
package org.ecocean.cache;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class QueryCacheFactorySafeInvalidateTest {
    private static final String CTX = "context0";
    private static final String NAME = "iaImageIds";

    @Test void safeInvalidate_invokesInvalidateByName_whenCacheNotNull()
    throws Exception {
        QueryCache cache = mock(QueryCache.class);
        doNothing().when(cache).invalidateByName(NAME);
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
        verify(cache).invalidateByName(NAME);
    }

    @Test void safeInvalidate_swallowsNull_whenGetQueryCacheReturnsNull() {
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(null);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            // Must not throw.
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
    }

    @Test void safeInvalidate_swallowsIoException_fromInvalidateByName()
    throws Exception {
        QueryCache cache = mock(QueryCache.class);
        doThrow(new IOException("simulated cache failure"))
            .when(cache).invalidateByName(NAME);
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            // Must not throw; exception is logged and swallowed.
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
        verify(cache).invalidateByName(NAME);
    }

    @Test void safeInvalidate_swallowsRuntimeException_fromGetQueryCache() {
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX))
                .thenThrow(new RuntimeException("simulated factory failure"));
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            // Must not propagate.
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
    }

    @Test void safeInvalidate_swallowsRuntimeException_fromInvalidateByName()
    throws Exception {
        // The shape of a poisoned/half-initialized singleton: getQueryCache
        // returns a cache instance, but invalidateByName throws a runtime
        // failure (e.g., NPE from a partially-loaded internal map).
        QueryCache cache = mock(QueryCache.class);
        doThrow(new RuntimeException("simulated runtime failure"))
            .when(cache).invalidateByName(NAME);
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
        verify(cache).invalidateByName(NAME);
    }
}

exec
/bin/bash -lc "rg -n \"wbiaRegistered|WbiaRegisterOutcome|REGISTERED_OK|REGISTERED_ALREADY_PRESENT|RESPONSE_BAD|NETWORK_FAIL|registerOneByDto|loadWbiaRegisterDto|MAX_ATTEMPTS\" src/main/java/org/ecocean/StartupWildbook.java src/main/java/org/ecocean/ia src/main/java/org/ecocean -g'*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '420,466p'" in /mnt/c/Wildbook-clean2
 succeeded in 257ms:
   420	    // ------------------------------------------------------------------
   421	
   422	    /**
   423	     * Outcome of a Phase-B WBIA registration attempt.
   424	     * REGISTERED_OK              - POST succeeded, ids match.
   425	     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
   426	     * NETWORK_FAIL               - GET or POST threw / non-2xx.
   427	     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
   428	     *                              (id mismatch, length mismatch, missing field).
   429	     */
   430	    public enum WbiaRegisterOutcome {
   431	        REGISTERED_OK,
   432	        REGISTERED_ALREADY_PRESENT,
   433	        NETWORK_FAIL,
   434	        RESPONSE_BAD,
   435	    }
   436	
   437	    /**
   438	     * Plain-data DTO that holds everything Phase B needs about one
   439	     * Annotation. Built under a Shepherd transaction in Phase A, then
   440	     * passed across the close/open boundary into Phase B.
   441	     *
   442	     * <p>Phase A is responsible for pre-validating that all required
   443	     * fields are populated; Phase B treats the DTO as opaque and does
   444	     * not re-touch any JDO-managed state.</p>
   445	     */
   446	    public static final class WbiaRegisterRequest {
   447	        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
   448	        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
   449	        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
   450	        public final int[]  bbox;               // x,y,w,h
   451	        public final double theta;
   452	        public final String iaClass;            // species/class string
   453	        public final String individualName;     // "____" if absent
   454	
   455	        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
   456	            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
   457	            String individualName) {
   458	            this.annotationId    = annotationId;
   459	            this.annotationAcmId = annotationAcmId;
   460	            this.mediaAssetAcmId = mediaAssetAcmId;
   461	            this.bbox            = bbox;
   462	            this.theta           = theta;
   463	            this.iaClass         = iaClass;
   464	            this.individualName  = individualName;
   465	        }
   466	    }

 succeeded in 726ms:
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:445:    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:495:                + WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
src/main/java/org/ecocean/StartupWildbook.java:706:            loadWbiaRegisterDto(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
src/main/java/org/ecocean/StartupWildbook.java:748:        loadWbiaRegisterDto(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:762:            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:778:                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:795:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
src/main/java/org/ecocean/StartupWildbook.java:827:                case REGISTERED_OK:
src/main/java/org/ecocean/StartupWildbook.java:828:                case REGISTERED_ALREADY_PRESENT:
src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
src/main/java/org/ecocean/StartupWildbook.java:835:                case RESPONSE_BAD:
src/main/java/org/ecocean/StartupWildbook.java:837:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:842:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:844:                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:445:    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:495:                + WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
src/main/java/org/ecocean/StartupWildbook.java:706:            loadWbiaRegisterDto(context, annId);
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
src/main/java/org/ecocean/StartupWildbook.java:748:        loadWbiaRegisterDto(String context, String annId) {
src/main/java/org/ecocean/StartupWildbook.java:762:            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:778:                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:795:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
src/main/java/org/ecocean/StartupWildbook.java:827:                case REGISTERED_OK:
src/main/java/org/ecocean/StartupWildbook.java:828:                case REGISTERED_ALREADY_PRESENT:
src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
src/main/java/org/ecocean/StartupWildbook.java:835:                case RESPONSE_BAD:
src/main/java/org/ecocean/StartupWildbook.java:837:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:842:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:844:                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:80:    protected Boolean wbiaRegistered;
src/main/java/org/ecocean/Annotation.java:336:    public Boolean getWbiaRegistered() { return wbiaRegistered; }
src/main/java/org/ecocean/Annotation.java:337:    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:341:    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:424:     * REGISTERED_OK              - POST succeeded, ids match.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:431:        REGISTERED_OK,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/ia/MatchResult.java:522:        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:424:     * REGISTERED_OK              - POST succeeded, ids match.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:431:        REGISTERED_OK,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '688,850p'" in /mnt/c/Wildbook-clean2
 succeeded in 250ms:
   688	        }
   689	    }
   690	
   691	    /**
   692	     * Phase A/B/C split per Codex c11 fix-review.
   693	     * <ul>
   694	     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
   695	     *   <li>Phase B: no Shepherd held; WBIA HTTP via
   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
   697	     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
   698	     * </ul>
   699	     * Ineligible annotations (missing media asset, missing acmId, fails
   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
   701	     * fall out of the polling query.
   702	     */
   703	    private static void registerOneAnnotationWithWbia(String context, String annId) {
   704	        // ---- Phase A: load DTO under a short transaction. ----
   705	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   706	            loadWbiaRegisterDto(context, annId);
   707	        if (dto == null) return;  // ineligible / already registered / parked
   708	
   709	        // Bail out before starting the non-interruptible HTTP call if
   710	        // shutdown was requested while Phase A was running. Otherwise we
   711	        // would start a 300s WBIA POST that contextDestroyed can't cancel.
   712	        if (Thread.currentThread().isInterrupted() ||
   713	            wbiaRegisterExecutor == null) {
   714	            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
   715	                " (shutdown requested)");
   716	            return;
   717	        }
   718	
   719	        // ---- Phase B: no Shepherd held; call WBIA. ----
   720	        org.ecocean.ia.plugin.WildbookIAM iam =
   721	            new org.ecocean.ia.plugin.WildbookIAM(context);
   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
   723	            iam.registerOneByDto(dto);
   724	
   725	        // Skip Phase C if shutdown has been requested while Phase B ran.
   726	        // RestClient is not interruptible mid-IO, so Phase B can outlive
   727	        // awaitTermination; this prevents Phase C from racing the rest of
   728	        // contextDestroyed's cleanup (Shepherd / IndexingManager / etc.).
   729	        if (Thread.currentThread().isInterrupted() ||
   730	            wbiaRegisterExecutor == null) {
   731	            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
   732	                " (shutdown requested)");
   733	            return;
   734	        }
   735	
   736	        // ---- Phase C: persist outcome under a short transaction. ----
   737	        persistWbiaRegisterResult(context, annId, outcome);
   738	    }
   739	
   740	    /**
   741	     * Phase A. Returns a detached DTO ready for Phase B, or null if the
   742	     * annotation does not need (or cannot get) a Phase-B network call.
   743	     * Null cases: missing annotation, already registered, parked at max
   744	     * attempts, or ineligible (missing media asset / acmId / bbox / etc.).
   745	     * Ineligible annotations are parked here so they stop being polled.
   746	     */
   747	    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
   748	        loadWbiaRegisterDto(String context, String annId) {
   749	        Shepherd shep = new Shepherd(context);
   750	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
   751	        shep.beginDBTransaction();
   752	        try {
   753	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   754	            if (ann == null) {
   755	                shep.commitDBTransaction();
   756	                return null;
   757	            }
   758	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   759	                shep.commitDBTransaction();
   760	                return null;
   761	            }
   762	            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   763	                shep.commitDBTransaction();
   764	                return null;
   765	            }
   766	            // Eligibility checks. Any failure here is permanent for this
   767	            // annotation under its current state, so park it.
   768	            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
   769	            String reason = null;
   770	            if (ma == null) reason = "missing media asset";
   771	            else if (!Util.stringExists(ma.getAcmId())) reason = "media asset has no acmId";
   772	            else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
   773	            else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
   774	                reason = "validForIdentification returned false (bbox/iaClass/etc.)";
   775	            if (reason != null) {
   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
   777	                    " (ineligible: " + reason + ")");
   778	                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
   779	                shep.commitDBTransaction();
   780	                return null;
   781	            }
   782	            // Resolve the individual name now while the Shepherd is open;
   783	            // Phase B has no DB access.
   784	            String name = ann.findIndividualId(shep);
   785	            // Copy bbox into a fresh array so the DTO is fully detached.
   786	            int[] bb = ann.getBbox();
   787	            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
   788	            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   789	                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
   790	                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
   791	                    ann.getTheta(), ann.getIAClass(), name);
   792	            shep.commitDBTransaction();
   793	            return dto;
   794	        } catch (Exception ex) {
   795	            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
   796	                annId + ": " + ex);
   797	            shep.rollbackDBTransaction();
   798	            return null;
   799	        } finally {
   800	            shep.closeDBTransaction();
   801	        }
   802	    }
   803	
   804	    /**
   805	     * Phase C. Re-loads the annotation and writes the outcome of the
   806	     * Phase-B network call. On terminal-success outcomes the annotation
   807	     * is marked registered; on retryable outcomes the attempts counter
   808	     * is bumped and we WARN-log when we hit the abandonment threshold.
   809	     */
   810	    private static void persistWbiaRegisterResult(String context, String annId,
   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
   812	        Shepherd shep = new Shepherd(context);
   813	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
   814	        shep.beginDBTransaction();
   815	        try {
   816	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   817	            if (ann == null) {
   818	                shep.commitDBTransaction();
   819	                return;
   820	            }
   821	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   822	                // Some other path flipped it while Phase B ran; respect that.
   823	                shep.commitDBTransaction();
   824	                return;
   825	            }
   826	            switch (outcome) {
   827	                case REGISTERED_OK:
   828	                case REGISTERED_ALREADY_PRESENT:
   829	                    // Always honor a success outcome even if the row was
   830	                    // parked by a racing poller: stuck-at-attempts==MAX
   831	                    // would otherwise become permanent.
   832	                    ann.setWbiaRegistered(Boolean.TRUE);
   833	                    break;
   834	                case NETWORK_FAIL:
   835	                case RESPONSE_BAD:
   836	                default:
   837	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   838	                        // Already parked by another path; do not increment past MAX.
   839	                        break;
   840	                    }
   841	                    ann.incrementWbiaRegisterAttempts();
   842	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   843	                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
   844	                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
   845	                            " attempts (last outcome=" + outcome + "); will not retry");
   846	                    }
   847	                    break;
   848	            }
   849	            shep.commitDBTransaction();
   850	        } catch (Exception ex) {

codex
**Findings**

No blocking correctness issues in C3. `iaImageIdsStrict` mirrors `iaAnnotationIdsStrict` closely: same cache-hit path, same strict parse behavior, same fetch-failure wrapping, same null-response rejection, and same best-effort cache store. See [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:567).

One C6 integration caveat: `safeInvalidate(context, "iaImageIds")` is the right target, but `CachedQuery.invalidate()` leaves the named cache entry present with `jsonSerializedQueryResult = null`. The next `iaImageIdsStrict` call will treat it as a valid unexpired cache entry, log `cache parse failed; refetching`, then recover by refetching and replacing it. Functional, but bulk imports could produce expected WARN noise after successful image POSTs. Consider either accepting that, treating null cached JSON as a quiet cache miss, or adding true removal/expiration. Relevant paths: [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:578), [QueryCache.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCache.java:46), [CachedQuery.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/CachedQuery.java:206).

**Open Questions**

`QueryCache` invalidation exists. Use `QueryCacheFactory.safeInvalidate(context, "iaImageIds")`, not the design-doc pseudocode `invalidate(...)`. The cache name matches the C3 method’s `cacheName` and JSON payload field.

`"iaImageIds"` is the right cache key pattern. It matches the established `"iaAnnotationIds"` convention. Caveat: `QueryCacheFactory` is a singleton, so this inherits the repo’s existing non-context-namespaced cache behavior.

Parser duplication is acceptable for this commit. C4 extracting `parseFancyUuidArrayStrict(JSONArray, String label)` is still the right cleanup.

For Phase A/B: keep `validMediaAsset` in Phase A. Phase B should not re-touch JDO state. A small defensive DTO scalar check in Phase B is reasonable, but not a DB re-check.

**Test Gaps**

The new tests cover the parser well, but not the main C3 contract: cache/error semantics. I’d add mocked unit coverage for cache hit, API exception wrapping, null API response, cache parse miss/refetch, and successful fetch storing under `"iaImageIds"`.

I did not run tests because this was review-only and test execution would write build output.
tokens used
97,062
**Findings**

No blocking correctness issues in C3. `iaImageIdsStrict` mirrors `iaAnnotationIdsStrict` closely: same cache-hit path, same strict parse behavior, same fetch-failure wrapping, same null-response rejection, and same best-effort cache store. See [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:567).

One C6 integration caveat: `safeInvalidate(context, "iaImageIds")` is the right target, but `CachedQuery.invalidate()` leaves the named cache entry present with `jsonSerializedQueryResult = null`. The next `iaImageIdsStrict` call will treat it as a valid unexpired cache entry, log `cache parse failed; refetching`, then recover by refetching and replacing it. Functional, but bulk imports could produce expected WARN noise after successful image POSTs. Consider either accepting that, treating null cached JSON as a quiet cache miss, or adding true removal/expiration. Relevant paths: [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:578), [QueryCache.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/QueryCache.java:46), [CachedQuery.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/cache/CachedQuery.java:206).

**Open Questions**

`QueryCache` invalidation exists. Use `QueryCacheFactory.safeInvalidate(context, "iaImageIds")`, not the design-doc pseudocode `invalidate(...)`. The cache name matches the C3 method’s `cacheName` and JSON payload field.

`"iaImageIds"` is the right cache key pattern. It matches the established `"iaAnnotationIds"` convention. Caveat: `QueryCacheFactory` is a singleton, so this inherits the repo’s existing non-context-namespaced cache behavior.

Parser duplication is acceptable for this commit. C4 extracting `parseFancyUuidArrayStrict(JSONArray, String label)` is still the right cleanup.

For Phase A/B: keep `validMediaAsset` in Phase A. Phase B should not re-touch JDO state. A small defensive DTO scalar check in Phase B is reasonable, but not a DB re-check.

**Test Gaps**

The new tests cover the parser well, but not the main C3 contract: cache/error semantics. I’d add mocked unit coverage for cache hit, API exception wrapping, null API response, cache parse miss/refetch, and successful fetch storing under `"iaImageIds"`.

I did not run tests because this was review-only and test execution would write build output.
