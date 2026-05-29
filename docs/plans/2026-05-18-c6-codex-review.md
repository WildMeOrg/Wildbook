OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3e4b-fdfc-75e1-aab7-deadfaba58f7
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing code on the `migrate-ml-service-v2` branch of the
Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you the
project conventions, repo gotchas, and current architecture that the
code under review assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (3.1 on the live amphibian-reptile deployment),
  React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has the 20 v2 commits plus the Track 1 empty-match-prospects work
  in progress. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md` and
  `docs/plans/2026-05-18-empty-match-prospects-design.md`.

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

`rollbackAndClose` is idempotent — safe after commit and safe after
early return.

**Critical gotcha:** never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the HTTP
work without Shepherd in Phase B, persist outcome in a fresh Shepherd
in Phase C.

## JDO naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes. The
`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
direct FK, not a JDO-generated join).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh
boundary; **does not** drain the Wildbook IndexingManager queue. If
you need "after this write the doc must be searchable" semantics,
use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
in c7, commit `f429c5bf8`).

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",   // "vector" = ml-service v2
        "method": "miewid-msv4.1",   // embedding model id
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
`isVectorConfig = method != null || api_endpoint != null` — both
nullable independently.

## v1 antipatterns to avoid

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.

## CRLF/LF gotcha on this Windows-mounted repo

The Edit tool sometimes flips LF files to CRLF when editing on
`/mnt/c/Wildbook-clean2`. Reviewers should call this out if they
see `git ls-files --eol` reporting `i/lf w/crlf`.

## What we want from this review

Code-review the diff below. Focus on:
- Correctness given the Wildbook conventions above.
- Whether the implementation matches the locked design.
- Test coverage and gaps.
- Anything else.

**Do not write to any file.** Review-only.

---

# Codex code-review: Track 1 C6 — registerOneByDto Phase 0 image POST

Empty-match-prospects design Track 1 C6 (final Track 1 commit):
add Phase 0 (image registration) to `registerOneByDto`. This is
the load-bearing fix for the recurring HTTP 500
`image_uuid_list has invalid values [(0, None)]` errors observed
on amphibian-reptile.wildbook.org.

Flow before Phase 1's annotation POST:
1. Property check `IBEISIARestUrlAddImages`.
2. `iaImageIdsStrict(context)` — GET WBIA's known image-ids.
3. If `dto.mediaAssetAcmId` already in the list → return
   REGISTERED_ALREADY_PRESENT (no POST).
4. Otherwise POST to `/api/image/json/` with one image entry.
5. Validate response, `safeInvalidate("iaImageIds")` on success.

Also: `safeInvalidate("iaAnnotationIds")` after successful Phase 1
annotation POST (Codex round-1 C6 follow-up: prevents stale-cache
duplicate POSTs on retry races).

No new outcome enum (Codex round-2 #6). Image-side failures reuse
NETWORK_FAIL (network/property/POST error) and RESPONSE_BAD
(unexpected response shape).

## Diff

diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index a59fc15d0..f6eebb4d4 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -714,13 +714,38 @@ public class WildbookIAM extends IAPlugin {
     }
 
     /**
-     * Phase B entry point. Does the already-present check, builds the
-     * forced-id POST, fires it, and classifies the outcome. Does NOT
-     * touch any Shepherd or JDO state; callers must hand it a DTO that
-     * was pre-validated and detached in Phase A.
+     * Phase B entry point. Sequence:
+     * <ol>
+     *   <li><b>Phase 0</b> (image registration, new in C6): GET the
+     *       WBIA-known image-ids; if the DTO's mediaAssetAcmId isn't
+     *       in the list, POST the image to /api/image/json/ and
+     *       invalidate the {@code "iaImageIds"} cache on success.
+     *       Without this, the legacy v2 routing path that skips
+     *       sendMediaAssets leaves WBIA unaware of the image, and
+     *       Phase 1's annotation POST returns HTTP 500 with
+     *       {@code image_uuid_list has invalid values [(0, None)]}.</li>
+     *   <li><b>Phase 1</b> (annotation registration, existing): the
+     *       already-present check, the forced-id POST, classification.</li>
+     * </ol>
+     *
+     * Does NOT touch any Shepherd or JDO state; callers must hand it
+     * a DTO that was pre-validated and detached in Phase A.
+     * (Empty-match-prospects design Track 1 C6.)
      */
     public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
         if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
+        // Phase 0: image registration. If the image isn't already at
+        // WBIA, POST it before attempting the annotation POST.
+        WbiaRegisterOutcome phase0 = registerImageIfMissing(dto);
+        if (phase0 != null && phase0 != WbiaRegisterOutcome.REGISTERED_OK &&
+            phase0 != WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT) {
+            // NETWORK_FAIL or RESPONSE_BAD; propagate so the polling
+            // thread retries / parks (Codex round-2 #6: no new outcome
+            // enum needed — Phase C log line distinguishes phase).
+            return phase0;
+        }
+        // Phase 1: annotation registration. Property check first since
+        // a missing property is a config error, not a network error.
         String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
         if (u == null) {
             IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
@@ -763,9 +788,148 @@ public class WildbookIAM extends IAPlugin {
             IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
             return WbiaRegisterOutcome.RESPONSE_BAD;
         }
+        // Annotation POST succeeded — invalidate the iaAnnotationIds cache
+        // so the next polling iteration sees this new annotation as already
+        // present and doesn't re-POST it (Codex round-1 C6 follow-up).
+        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
         return WbiaRegisterOutcome.REGISTERED_OK;
     }
 
+    /**
+     * Phase 0 of registerOneByDto: ensure the image referenced by
+     * {@code dto.mediaAssetAcmId} is registered with WBIA before the
+     * Phase 1 annotation POST.
+     *
+     * <p>Returns:</p>
+     * <ul>
+     *   <li>{@link WbiaRegisterOutcome#REGISTERED_ALREADY_PRESENT} if the
+     *       image is already in WBIA's id list (no POST done).</li>
+     *   <li>{@link WbiaRegisterOutcome#REGISTERED_OK} if the image was
+     *       not present and the POST succeeded. Also invalidates the
+     *       {@code "iaImageIds"} cache so the next caller sees the
+     *       image as already present.</li>
+     *   <li>{@link WbiaRegisterOutcome#NETWORK_FAIL} on missing
+     *       {@code IBEISIARestUrlAddImages} property, fetch failure,
+     *       or POST exception.</li>
+     *   <li>{@link WbiaRegisterOutcome#RESPONSE_BAD} if the POST
+     *       returned an unexpected response shape.</li>
+     * </ul>
+     *
+     * Package-visible so unit tests can cover it without going
+     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
+     */
+    WbiaRegisterOutcome registerImageIfMissing(WbiaRegisterRequest dto) {
+        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
+        if (!Util.stringExists(dto.mediaAssetAcmId)) {
+            // Phase A required mediaAssetAcmId; a null here is a contract
+            // bug, not a state we should silently work around.
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null mediaAssetAcmId in DTO");
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        if (!Util.stringExists(dto.imageUri)) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null/empty imageUri in DTO");
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        String urlProp = IA.getProperty(context, "IBEISIARestUrlAddImages");
+        if (urlProp == null) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() property IBEISIARestUrlAddImages not set");
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        List<String> knownImages;
+        try {
+            knownImages = iaImageIdsStrict(context);
+        } catch (IOException ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() iaImageIds fetch failed: " +
+                ex.getMessage());
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        if (knownImages.contains(dto.mediaAssetAcmId)) {
+            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
+        }
+        URL url;
+        try {
+            url = new URL(urlProp);
+        } catch (MalformedURLException ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() malformed URL " + urlProp);
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        HashMap<String, ArrayList> map = buildImageRequestMap(dto);
+        JSONObject rtn;
+        try {
+            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+        } catch (Exception ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() POST failed: " + ex.getMessage());
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        try {
+            validateImageResponse(dto.mediaAssetAcmId, rtn);
+        } catch (IOException ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() response invalid: " +
+                ex.getMessage());
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        // Image POST succeeded — invalidate so the next iteration sees
+        // this image as already present.
+        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
+        return WbiaRegisterOutcome.REGISTERED_OK;
+    }
+
+    /**
+     * Build the WBIA /api/image/json/ POST body for a single DTO.
+     * Pure function; factored out so unit tests can verify the request
+     * shape without a network round trip. Mirrors the same field-set
+     * {@link #sendMediaAssetsForceId} populates per asset (uri, fancy
+     * uuid, unix-seconds time, lat, lon).
+     */
+    static HashMap<String, ArrayList> buildImageRequestMap(WbiaRegisterRequest dto) {
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uri_list", new ArrayList<String>());
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
+        map.put("image_unixtime_list", new ArrayList<Integer>());
+        map.put("image_gps_lat_list", new ArrayList<Double>());
+        map.put("image_gps_lon_list", new ArrayList<Double>());
+        map.get("image_uri_list").add(dto.imageUri);
+        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
+        if (dto.imageDateTimeMillis == null) {
+            map.get("image_unixtime_list").add(null);
+        } else {
+            // IA expects seconds since epoch, not milliseconds.
+            map.get("image_unixtime_list").add(
+                (int)Math.floor(dto.imageDateTimeMillis / 1000));
+        }
+        map.get("image_gps_lat_list").add(dto.imageLatitude);
+        map.get("image_gps_lon_list").add(dto.imageLongitude);
+        return map;
+    }
+
+    /**
+     * Validate a /api/image/json/ response. Mirrors
+     * {@link #validateForcedResponse} in shape: expect a length-1
+     * response array whose first element decodes via fromFancyUUID to
+     * exactly the {@code sentImageUuid} we sent.
+     */
+    static void validateImageResponse(String sentImageUuid, JSONObject resp)
+    throws IOException {
+        if (resp == null) throw new IOException("null image response");
+        if (resp.has("status")) {
+            JSONObject status = resp.optJSONObject("status");
+            if (status != null && status.has("success") &&
+                !status.optBoolean("success", true)) {
+                throw new IOException("image response status.success=false: " + resp);
+            }
+        }
+        JSONArray respArr = resp.optJSONArray("response");
+        if (respArr == null) throw new IOException("no response array: " + resp);
+        if (respArr.length() != 1)
+            throw new IOException("expected response array length 1, got " + respArr.length());
+        JSONObject jid = respArr.optJSONObject(0);
+        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
+        String respId = fromFancyUUID(jid);
+        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
+        if (!respId.equals(sentImageUuid))
+            throw new IOException("image-id mismatch: sent=" + sentImageUuid + " got=" + respId);
+    }
+
     private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
     throws IOException {
         if ((sentIds == null) || (respArr == null))


## New test file:

```java
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of Phase 0 helpers introduced in C6:
 * {@link WildbookIAM#buildImageRequestMap} and
 * {@link WildbookIAM#validateImageResponse}. The network-bound
 * {@code registerImageIfMissing} entry point is exercised end-to-end
 * by the WBIA registration polling thread in a dev deployment.
 * (Empty-match-prospects design Track 1 C6.)
 */
class WildbookIAMImagePhase0Test {

    private static WbiaRegisterRequest sampleDtoWithImage() {
        return new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            12.34d, -56.78d, 1700000000000L);
    }

    // --- buildImageRequestMap --------------------------------------------

    @Test void buildImageRequestMapHasAllFiveLists() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        assertNotNull(map.get("image_uri_list"));
        assertNotNull(map.get("image_uuid_list"));
        assertNotNull(map.get("image_unixtime_list"));
        assertNotNull(map.get("image_gps_lat_list"));
        assertNotNull(map.get("image_gps_lon_list"));
        assertEquals(1, map.get("image_uri_list").size());
    }

    @Test void buildImageRequestMapPopulatesScalarFields() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        assertEquals("https://example.com/img.jpg",
            map.get("image_uri_list").get(0));
        assertEquals(12.34d, map.get("image_gps_lat_list").get(0));
        assertEquals(-56.78d, map.get("image_gps_lon_list").get(0));
    }

    @Test void buildImageRequestMapWrapsImageUuidInFancyForm() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        Object wrapped = map.get("image_uuid_list").get(0);
        assertTrue(wrapped instanceof JSONObject,
            "expected JSONObject fancy-uuid wrapper, got " +
            (wrapped == null ? "null" : wrapped.getClass().getName()));
        assertEquals("ma-acm-uuid-1",
            ((JSONObject) wrapped).optString("__UUID__"));
    }

    @Test void buildImageRequestMapConvertsMillisToUnixSeconds() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        // 1700000000000ms = 1700000000s
        assertEquals(1700000000, map.get("image_unixtime_list").get(0));
    }

    @Test void buildImageRequestMapPassesNullForNullDateTime() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            12.34d, -56.78d, null);
        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
        assertNull(map.get("image_unixtime_list").get(0));
    }

    @Test void buildImageRequestMapPassesNullsForOptionalGps() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            null, null, 1700000000000L);
        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
        assertNull(map.get("image_gps_lat_list").get(0));
        assertNull(map.get("image_gps_lon_list").get(0));
    }

    // --- validateImageResponse -------------------------------------------

    private static JSONObject okResponse(String returnedUuid) {
        JSONObject jo = new JSONObject();
        JSONObject status = new JSONObject();
        status.put("success", true);
        jo.put("status", status);
        JSONArray arr = new JSONArray();
        JSONObject fancy = new JSONObject();
        fancy.put("__UUID__", returnedUuid);
        arr.put(fancy);
        jo.put("response", arr);
        return jo;
    }

    @Test void validateImageResponse_acceptsMatchingFancyUuid()
    throws IOException {
        WildbookIAM.validateImageResponse("ma-acm-uuid-1",
            okResponse("ma-acm-uuid-1"));
    }

    @Test void validateImageResponse_throwsOnNullResponse() {
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", null));
        assertTrue(ex.getMessage().contains("null"),
            "message should mention null: " + ex.getMessage());
    }

    @Test void validateImageResponse_throwsOnStatusSuccessFalse() {
        JSONObject resp = okResponse("ma-acm-uuid-1");
        resp.getJSONObject("status").put("success", false);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("success=false"));
    }

    @Test void validateImageResponse_throwsOnMissingResponseArray() {
        JSONObject resp = new JSONObject();
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("no response array"));
    }

    @Test void validateImageResponse_throwsOnArrayLengthMismatch() {
        JSONObject resp = okResponse("ma-acm-uuid-1");
        resp.getJSONArray("response").put(new JSONObject().put("__UUID__", "x"));
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("length 1"));
    }

    @Test void validateImageResponse_throwsOnUuidMismatch() {
        JSONObject resp = okResponse("different-uuid");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("mismatch"));
        assertTrue(ex.getMessage().contains("ma-acm-uuid-1"));
        assertTrue(ex.getMessage().contains("different-uuid"));
    }

    @Test void validateImageResponse_throwsOnUndecodableResponseElement() {
        JSONObject resp = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("not_uuid_key", "x"));  // no __UUID__
        resp.put("response", arr);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("could not be decoded"));
    }
}
```


## Validation

- WildbookIAMImagePhase0Test: 13/13 (new — covers buildImageRequestMap +
  validateImageResponse pure functions).
- Full project test suite: 310 tests, 0 failures, 7 pre-existing skips.
- LF line-endings verified on all touched files.
- Phase 0 sequencing: property check first, then `iaImageIdsStrict`,
  then conditional POST. Matches the `iaAnnotationIdsStrict` Phase 1
  pattern (Codex's c11 precedent).

## Review scope

- Phase 0 placement: at the top of `registerOneByDto`, before
  Phase 1's annotation POST. Pseudocode review confirmed this is
  the right composition.
- Outcome semantics: if Phase 0 returns NETWORK_FAIL or RESPONSE_BAD,
  the function returns that outcome immediately and does NOT proceed
  to Phase 1. REGISTERED_ALREADY_PRESENT and REGISTERED_OK both
  proceed to Phase 1.
- `Util.stringExists(dto.imageUri)` and `Util.stringExists(dto.mediaAssetAcmId)`
  short-circuit to RESPONSE_BAD if the DTO is missing required
  fields — Phase A is responsible for not handing us a bad DTO
  (locked design Track 1 item 4: Phase B trusts Phase A's verdict),
  but we treat a DTO contract violation as a fatal classification
  rather than a network retry.
- Cache invalidation uses the C1 `safeInvalidate` null-safe helper.
- The package-visible `registerImageIfMissing` exists for testability.

Anything else (especially: have I introduced any new way to hot-loop
or get stuck retrying)?

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this read-only and check the live surrounding code, not just the pasted diff, so the findings are grounded in the actual contracts and retry path.
exec
/bin/bash -lc 'git status --short' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "enum WbiaRegisterOutcome|WbiaRegisterOutcome|registerOneByDto|registerImageIfMissing|iaImageIdsStrict|iaAnnotationIdsStrict|sendMediaAssetsForceId|validateForcedResponse|toFancyUUID|fromFancyUUID" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
 succeeded in 552ms:
133:            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
178:    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
209:                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
214:                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
218:            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
230:                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
234:                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
242:                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
305:            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
379:            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
387:            JSONObject aid = toFancyUUID(ann.getId());
430:    public enum WbiaRegisterOutcome {
505:    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
525:                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
567:            if (jo != null) ids.add(fromFancyUUID(jo));
580:     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
584:    public static List<String> iaImageIdsStrict(String context) throws IOException {
604:                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
661:            String decoded = fromFancyUUID(jo);
682:        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
683:        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
696:    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
710:        String respId = fromFancyUUID(jid);
735:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
736:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
739:        WbiaRegisterOutcome phase0 = registerImageIfMissing(dto);
740:        if (phase0 != null && phase0 != WbiaRegisterOutcome.REGISTERED_OK &&
741:            phase0 != WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT) {
751:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
752:            return WbiaRegisterOutcome.NETWORK_FAIL;
756:            known = iaAnnotationIdsStrict(context);
758:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
760:            return WbiaRegisterOutcome.NETWORK_FAIL;
768:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
774:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
775:            return WbiaRegisterOutcome.NETWORK_FAIL;
782:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
783:            return WbiaRegisterOutcome.NETWORK_FAIL;
786:            validateForcedResponse(dto.annotationId, rtn);
788:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
789:            return WbiaRegisterOutcome.RESPONSE_BAD;
795:        return WbiaRegisterOutcome.REGISTERED_OK;
799:     * Phase 0 of registerOneByDto: ensure the image referenced by
805:     *   <li>{@link WbiaRegisterOutcome#REGISTERED_ALREADY_PRESENT} if the
807:     *   <li>{@link WbiaRegisterOutcome#REGISTERED_OK} if the image was
811:     *   <li>{@link WbiaRegisterOutcome#NETWORK_FAIL} on missing
814:     *   <li>{@link WbiaRegisterOutcome#RESPONSE_BAD} if the POST
819:     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
821:    WbiaRegisterOutcome registerImageIfMissing(WbiaRegisterRequest dto) {
822:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
826:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null mediaAssetAcmId in DTO");
827:            return WbiaRegisterOutcome.RESPONSE_BAD;
830:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null/empty imageUri in DTO");
831:            return WbiaRegisterOutcome.RESPONSE_BAD;
835:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() property IBEISIARestUrlAddImages not set");
836:            return WbiaRegisterOutcome.NETWORK_FAIL;
840:            knownImages = iaImageIdsStrict(context);
842:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() iaImageIds fetch failed: " +
844:            return WbiaRegisterOutcome.NETWORK_FAIL;
847:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
853:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() malformed URL " + urlProp);
854:            return WbiaRegisterOutcome.NETWORK_FAIL;
861:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() POST failed: " + ex.getMessage());
862:            return WbiaRegisterOutcome.NETWORK_FAIL;
867:            IA.log("WARNING: WildbookIAM.registerImageIfMissing() response invalid: " +
869:            return WbiaRegisterOutcome.RESPONSE_BAD;
874:        return WbiaRegisterOutcome.REGISTERED_OK;
881:     * {@link #sendMediaAssetsForceId} populates per asset (uri, fancy
892:        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
907:     * {@link #validateForcedResponse} in shape: expect a length-1
908:     * response array whose first element decodes via fromFancyUUID to
927:        String respId = fromFancyUUID(jid);
941:            String sentId = fromFancyUUID(sentIds.get(i));
946:            String respId = fromFancyUUID(jid);
962:                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
1008:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
1038:                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
1070:    public static String fromFancyUUID(JSONObject u) {
1075:    public static JSONObject toFancyUUID(String u) {

 succeeded in 1034ms:
diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index a59fc15d0..f6eebb4d4 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -714,13 +714,38 @@ public class WildbookIAM extends IAPlugin {
     }
 
     /**
-     * Phase B entry point. Does the already-present check, builds the
-     * forced-id POST, fires it, and classifies the outcome. Does NOT
-     * touch any Shepherd or JDO state; callers must hand it a DTO that
-     * was pre-validated and detached in Phase A.
+     * Phase B entry point. Sequence:
+     * <ol>
+     *   <li><b>Phase 0</b> (image registration, new in C6): GET the
+     *       WBIA-known image-ids; if the DTO's mediaAssetAcmId isn't
+     *       in the list, POST the image to /api/image/json/ and
+     *       invalidate the {@code "iaImageIds"} cache on success.
+     *       Without this, the legacy v2 routing path that skips
+     *       sendMediaAssets leaves WBIA unaware of the image, and
+     *       Phase 1's annotation POST returns HTTP 500 with
+     *       {@code image_uuid_list has invalid values [(0, None)]}.</li>
+     *   <li><b>Phase 1</b> (annotation registration, existing): the
+     *       already-present check, the forced-id POST, classification.</li>
+     * </ol>
+     *
+     * Does NOT touch any Shepherd or JDO state; callers must hand it
+     * a DTO that was pre-validated and detached in Phase A.
+     * (Empty-match-prospects design Track 1 C6.)
      */
     public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
         if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
+        // Phase 0: image registration. If the image isn't already at
+        // WBIA, POST it before attempting the annotation POST.
+        WbiaRegisterOutcome phase0 = registerImageIfMissing(dto);
+        if (phase0 != null && phase0 != WbiaRegisterOutcome.REGISTERED_OK &&
+            phase0 != WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT) {
+            // NETWORK_FAIL or RESPONSE_BAD; propagate so the polling
+            // thread retries / parks (Codex round-2 #6: no new outcome
+            // enum needed — Phase C log line distinguishes phase).
+            return phase0;
+        }
+        // Phase 1: annotation registration. Property check first since
+        // a missing property is a config error, not a network error.
         String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
         if (u == null) {
             IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
@@ -763,9 +788,148 @@ public class WildbookIAM extends IAPlugin {
             IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
             return WbiaRegisterOutcome.RESPONSE_BAD;
         }
+        // Annotation POST succeeded — invalidate the iaAnnotationIds cache
+        // so the next polling iteration sees this new annotation as already
+        // present and doesn't re-POST it (Codex round-1 C6 follow-up).
+        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
         return WbiaRegisterOutcome.REGISTERED_OK;
     }
 
+    /**
+     * Phase 0 of registerOneByDto: ensure the image referenced by
+     * {@code dto.mediaAssetAcmId} is registered with WBIA before the
+     * Phase 1 annotation POST.
+     *
+     * <p>Returns:</p>
+     * <ul>
+     *   <li>{@link WbiaRegisterOutcome#REGISTERED_ALREADY_PRESENT} if the
+     *       image is already in WBIA's id list (no POST done).</li>
+     *   <li>{@link WbiaRegisterOutcome#REGISTERED_OK} if the image was
+     *       not present and the POST succeeded. Also invalidates the
+     *       {@code "iaImageIds"} cache so the next caller sees the
+     *       image as already present.</li>
+     *   <li>{@link WbiaRegisterOutcome#NETWORK_FAIL} on missing
+     *       {@code IBEISIARestUrlAddImages} property, fetch failure,
+     *       or POST exception.</li>
+     *   <li>{@link WbiaRegisterOutcome#RESPONSE_BAD} if the POST
+     *       returned an unexpected response shape.</li>
+     * </ul>
+     *
+     * Package-visible so unit tests can cover it without going
+     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
+     */
+    WbiaRegisterOutcome registerImageIfMissing(WbiaRegisterRequest dto) {
+        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
+        if (!Util.stringExists(dto.mediaAssetAcmId)) {
+            // Phase A required mediaAssetAcmId; a null here is a contract
+            // bug, not a state we should silently work around.
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null mediaAssetAcmId in DTO");
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        if (!Util.stringExists(dto.imageUri)) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null/empty imageUri in DTO");
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        String urlProp = IA.getProperty(context, "IBEISIARestUrlAddImages");
+        if (urlProp == null) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() property IBEISIARestUrlAddImages not set");
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        List<String> knownImages;
+        try {
+            knownImages = iaImageIdsStrict(context);
+        } catch (IOException ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() iaImageIds fetch failed: " +
+                ex.getMessage());
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        if (knownImages.contains(dto.mediaAssetAcmId)) {
+            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
+        }
+        URL url;
+        try {
+            url = new URL(urlProp);
+        } catch (MalformedURLException ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() malformed URL " + urlProp);
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        HashMap<String, ArrayList> map = buildImageRequestMap(dto);
+        JSONObject rtn;
+        try {
+            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
+        } catch (Exception ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() POST failed: " + ex.getMessage());
+            return WbiaRegisterOutcome.NETWORK_FAIL;
+        }
+        try {
+            validateImageResponse(dto.mediaAssetAcmId, rtn);
+        } catch (IOException ex) {
+            IA.log("WARNING: WildbookIAM.registerImageIfMissing() response invalid: " +
+                ex.getMessage());
+            return WbiaRegisterOutcome.RESPONSE_BAD;
+        }
+        // Image POST succeeded — invalidate so the next iteration sees
+        // this image as already present.
+        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
+        return WbiaRegisterOutcome.REGISTERED_OK;
+    }
+
+    /**
+     * Build the WBIA /api/image/json/ POST body for a single DTO.
+     * Pure function; factored out so unit tests can verify the request
+     * shape without a network round trip. Mirrors the same field-set
+     * {@link #sendMediaAssetsForceId} populates per asset (uri, fancy
+     * uuid, unix-seconds time, lat, lon).
+     */
+    static HashMap<String, ArrayList> buildImageRequestMap(WbiaRegisterRequest dto) {
+        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
+        map.put("image_uri_list", new ArrayList<String>());
+        map.put("image_uuid_list", new ArrayList<JSONObject>());
+        map.put("image_unixtime_list", new ArrayList<Integer>());
+        map.put("image_gps_lat_list", new ArrayList<Double>());
+        map.put("image_gps_lon_list", new ArrayList<Double>());
+        map.get("image_uri_list").add(dto.imageUri);
+        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
+        if (dto.imageDateTimeMillis == null) {
+            map.get("image_unixtime_list").add(null);
+        } else {
+            // IA expects seconds since epoch, not milliseconds.
+            map.get("image_unixtime_list").add(
+                (int)Math.floor(dto.imageDateTimeMillis / 1000));
+        }
+        map.get("image_gps_lat_list").add(dto.imageLatitude);
+        map.get("image_gps_lon_list").add(dto.imageLongitude);
+        return map;
+    }
+
+    /**
+     * Validate a /api/image/json/ response. Mirrors
+     * {@link #validateForcedResponse} in shape: expect a length-1
+     * response array whose first element decodes via fromFancyUUID to
+     * exactly the {@code sentImageUuid} we sent.
+     */
+    static void validateImageResponse(String sentImageUuid, JSONObject resp)
+    throws IOException {
+        if (resp == null) throw new IOException("null image response");
+        if (resp.has("status")) {
+            JSONObject status = resp.optJSONObject("status");
+            if (status != null && status.has("success") &&
+                !status.optBoolean("success", true)) {
+                throw new IOException("image response status.success=false: " + resp);
+            }
+        }
+        JSONArray respArr = resp.optJSONArray("response");
+        if (respArr == null) throw new IOException("no response array: " + resp);
+        if (respArr.length() != 1)
+            throw new IOException("expected response array length 1, got " + respArr.length());
+        JSONObject jid = respArr.optJSONObject(0);
+        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
+        String respId = fromFancyUUID(jid);
+        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
+        if (!respId.equals(sentImageUuid))
+            throw new IOException("image-id mismatch: sent=" + sentImageUuid + " got=" + respId);
+    }
+
     private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
     throws IOException {
         if ((sentIds == null) || (respArr == null))

codex
I’ve confirmed the working diff matches the pasted change. Next I’m checking the surrounding WBIA helper conventions and the polling outcome handling, since that’s where hot-loop or stuck-retry behavior would show up.
exec
/bin/bash -lc "sed -n '430,960p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '960,1095p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,260p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 364ms:
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

 succeeded in 375ms:
    public enum WbiaRegisterOutcome {
        REGISTERED_OK,
        REGISTERED_ALREADY_PRESENT,
        NETWORK_FAIL,
        RESPONSE_BAD,
    }

    /**
     * Plain-data DTO that holds everything Phase B needs about one
     * Annotation. Built under a Shepherd transaction in Phase A, then
     * passed across the close/open boundary into Phase B.
     *
     * <p>Phase A is responsible for pre-validating that all required
     * fields are populated; Phase B treats the DTO as opaque and does
     * not re-touch any JDO-managed state.</p>
     */
    public static final class WbiaRegisterRequest {
        public final String annotationId;       // Annotation.id (the WBIA annot id we send)
        public final String annotationAcmId;    // Annotation.acmId, may differ from id on legacy rows
        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
        public final int[]  bbox;               // x,y,w,h
        public final double theta;
        public final String iaClass;            // species/class string
        public final String individualName;     // "____" if absent

        // Image-side fields. Phase 0 (image registration) sends these into
        // WBIA's /api/image/json/ payload when the image isn't already
        // known to WBIA. Captured under Shepherd in Phase A so Phase B has
        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
        public final String imageUri;            // double-encoded URL string; from mediaAssetToUri(ma)
        public final Double imageLatitude;       // nullable; ma.getLatitude()
        public final Double imageLongitude;      // nullable; ma.getLongitude()
        public final Long   imageDateTimeMillis; // nullable epoch-ms; ma.getDateTime().getMillis()

        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
            String individualName, String imageUri, Double imageLatitude,
            Double imageLongitude, Long imageDateTimeMillis) {
            this.annotationId        = annotationId;
            this.annotationAcmId     = annotationAcmId;
            this.mediaAssetAcmId     = mediaAssetAcmId;
            this.bbox                = bbox;
            this.theta               = theta;
            this.iaClass             = iaClass;
            this.individualName      = individualName;
            this.imageUri            = imageUri;
            this.imageLatitude       = imageLatitude;
            this.imageLongitude      = imageLongitude;
            this.imageDateTimeMillis = imageDateTimeMillis;
        }

        /**
         * Pre-C5 constructor preserved for backward-compatibility with
         * test fixtures that don't exercise the Phase 0 image-registration
         * path. Equivalent to the 11-arg constructor with all four
         * image fields null. New production callers should use the
         * 11-arg form.
         */
        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
            String individualName) {
            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
                iaClass, individualName, null, null, null, null);
        }
    }

    /**
     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
     * fetch failure rather than returning an empty list. Phase B needs
     * this so a network failure during the already-present check is
     * not silently treated as "go ahead and POST".
     *
     * <p>Honors the 15-minute QueryCache the same way the lenient
     * variant does, so a cache hit avoids the network entirely.</p>
     */
    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
        String cacheName = "iaAnnotationIds";
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
     * Phase B entry point. Sequence:
     * <ol>
     *   <li><b>Phase 0</b> (image registration, new in C6): GET the
     *       WBIA-known image-ids; if the DTO's mediaAssetAcmId isn't
     *       in the list, POST the image to /api/image/json/ and
     *       invalidate the {@code "iaImageIds"} cache on success.
     *       Without this, the legacy v2 routing path that skips
     *       sendMediaAssets leaves WBIA unaware of the image, and
     *       Phase 1's annotation POST returns HTTP 500 with
     *       {@code image_uuid_list has invalid values [(0, None)]}.</li>
     *   <li><b>Phase 1</b> (annotation registration, existing): the
     *       already-present check, the forced-id POST, classification.</li>
     * </ol>
     *
     * Does NOT touch any Shepherd or JDO state; callers must hand it
     * a DTO that was pre-validated and detached in Phase A.
     * (Empty-match-prospects design Track 1 C6.)
     */
    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
        // Phase 0: image registration. If the image isn't already at
        // WBIA, POST it before attempting the annotation POST.
        WbiaRegisterOutcome phase0 = registerImageIfMissing(dto);
        if (phase0 != null && phase0 != WbiaRegisterOutcome.REGISTERED_OK &&
            phase0 != WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT) {
            // NETWORK_FAIL or RESPONSE_BAD; propagate so the polling
            // thread retries / parks (Codex round-2 #6: no new outcome
            // enum needed — Phase C log line distinguishes phase).
            return phase0;
        }
        // Phase 1: annotation registration. Property check first since
        // a missing property is a config error, not a network error.
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
        // Annotation POST succeeded — invalidate the iaAnnotationIds cache
        // so the next polling iteration sees this new annotation as already
        // present and doesn't re-POST it (Codex round-1 C6 follow-up).
        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
        return WbiaRegisterOutcome.REGISTERED_OK;
    }

    /**
     * Phase 0 of registerOneByDto: ensure the image referenced by
     * {@code dto.mediaAssetAcmId} is registered with WBIA before the
     * Phase 1 annotation POST.
     *
     * <p>Returns:</p>
     * <ul>
     *   <li>{@link WbiaRegisterOutcome#REGISTERED_ALREADY_PRESENT} if the
     *       image is already in WBIA's id list (no POST done).</li>
     *   <li>{@link WbiaRegisterOutcome#REGISTERED_OK} if the image was
     *       not present and the POST succeeded. Also invalidates the
     *       {@code "iaImageIds"} cache so the next caller sees the
     *       image as already present.</li>
     *   <li>{@link WbiaRegisterOutcome#NETWORK_FAIL} on missing
     *       {@code IBEISIARestUrlAddImages} property, fetch failure,
     *       or POST exception.</li>
     *   <li>{@link WbiaRegisterOutcome#RESPONSE_BAD} if the POST
     *       returned an unexpected response shape.</li>
     * </ul>
     *
     * Package-visible so unit tests can cover it without going
     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
     */
    WbiaRegisterOutcome registerImageIfMissing(WbiaRegisterRequest dto) {
        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
        if (!Util.stringExists(dto.mediaAssetAcmId)) {
            // Phase A required mediaAssetAcmId; a null here is a contract
            // bug, not a state we should silently work around.
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null mediaAssetAcmId in DTO");
            return WbiaRegisterOutcome.RESPONSE_BAD;
        }
        if (!Util.stringExists(dto.imageUri)) {
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null/empty imageUri in DTO");
            return WbiaRegisterOutcome.RESPONSE_BAD;
        }
        String urlProp = IA.getProperty(context, "IBEISIARestUrlAddImages");
        if (urlProp == null) {
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() property IBEISIARestUrlAddImages not set");
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        List<String> knownImages;
        try {
            knownImages = iaImageIdsStrict(context);
        } catch (IOException ex) {
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() iaImageIds fetch failed: " +
                ex.getMessage());
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        if (knownImages.contains(dto.mediaAssetAcmId)) {
            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
        }
        URL url;
        try {
            url = new URL(urlProp);
        } catch (MalformedURLException ex) {
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() malformed URL " + urlProp);
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        HashMap<String, ArrayList> map = buildImageRequestMap(dto);
        JSONObject rtn;
        try {
            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        } catch (Exception ex) {
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() POST failed: " + ex.getMessage());
            return WbiaRegisterOutcome.NETWORK_FAIL;
        }
        try {
            validateImageResponse(dto.mediaAssetAcmId, rtn);
        } catch (IOException ex) {
            IA.log("WARNING: WildbookIAM.registerImageIfMissing() response invalid: " +
                ex.getMessage());
            return WbiaRegisterOutcome.RESPONSE_BAD;
        }
        // Image POST succeeded — invalidate so the next iteration sees
        // this image as already present.
        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
        return WbiaRegisterOutcome.REGISTERED_OK;
    }

    /**
     * Build the WBIA /api/image/json/ POST body for a single DTO.
     * Pure function; factored out so unit tests can verify the request
     * shape without a network round trip. Mirrors the same field-set
     * {@link #sendMediaAssetsForceId} populates per asset (uri, fancy
     * uuid, unix-seconds time, lat, lon).
     */
    static HashMap<String, ArrayList> buildImageRequestMap(WbiaRegisterRequest dto) {
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uri_list", new ArrayList<String>());
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("image_unixtime_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());
        map.get("image_uri_list").add(dto.imageUri);
        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
        if (dto.imageDateTimeMillis == null) {
            map.get("image_unixtime_list").add(null);
        } else {
            // IA expects seconds since epoch, not milliseconds.
            map.get("image_unixtime_list").add(
                (int)Math.floor(dto.imageDateTimeMillis / 1000));
        }
        map.get("image_gps_lat_list").add(dto.imageLatitude);
        map.get("image_gps_lon_list").add(dto.imageLongitude);
        return map;
    }

    /**
     * Validate a /api/image/json/ response. Mirrors
     * {@link #validateForcedResponse} in shape: expect a length-1
     * response array whose first element decodes via fromFancyUUID to
     * exactly the {@code sentImageUuid} we sent.
     */
    static void validateImageResponse(String sentImageUuid, JSONObject resp)
    throws IOException {
        if (resp == null) throw new IOException("null image response");
        if (resp.has("status")) {
            JSONObject status = resp.optJSONObject("status");
            if (status != null && status.has("success") &&
                !status.optBoolean("success", true)) {
                throw new IOException("image response status.success=false: " + resp);
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
        if (!respId.equals(sentImageUuid))
            throw new IOException("image-id mismatch: sent=" + sentImageUuid + " got=" + respId);
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
                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
                ids.add(null);

 succeeded in 427ms:
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
            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
                IA.log(
                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
                    + ma.getId());
                continue;
            }
            if (!validMediaAsset(ma)) {
                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
                continue;
            }
            acmList.add(ma);
            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
            map.get("image_uri_list").add(mediaAssetToUri(ma));
            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());
            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_unixtime_list").add(null);
            } else {
                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
            }
            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
                if (acmList.size() > 0) {
                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
                        " batches)");
                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
                    List<String> acmIds = acmIdsFromResponse(rtn);
                    if (acmIds == null) {
                        IA.log(
                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
                            + rtn);
                    } else {
                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
                    }
                    bres.put(rtn);
                    // initialize for next batch (if any)
                    map.put("image_uri_list", new ArrayList<JSONObject>());
                    map.put("image_uuid_list", new ArrayList<JSONObject>());
                    map.put("image_unixtime_list", new ArrayList<Integer>());
                    map.put("image_gps_lat_list", new ArrayList<Double>());
                    map.put("image_gps_lon_list", new ArrayList<Double>());
                    acmList = new ArrayList<MediaAsset>();
                } else {
                    bres.put("EMPTY BATCH");
                }
                batchCt++;
            }
        }
        allRtn.put("batchResults", bres);
        return allRtn;
    }

    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
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
        int batchCt = 1;
        JSONObject allRtn = new JSONObject();
        allRtn.put("_batchSize", batchSize);
        allRtn.put("_totalSize", mas.size());
        JSONArray bres = new JSONArray();
        for (int i = 0; i < mas.size(); i++) {
            MediaAsset ma = mas.get(i);
            if (iaImageIds.contains(ma.getAcmId())) continue;
            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
                IA.log(
                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
                    + ma.getId());
                continue;
            }
            if (!validMediaAsset(ma)) {
                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
                continue;
            }
            map.get("image_uri_list").add(mediaAssetToUri(ma));
            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());
            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_unixtime_list").add(null);
            } else {
                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
            }
            int sendSize = map.get("image_uri_list").size();
            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
                if (sendSize > 0) {
                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
                        " batches)");
                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
/*
                    if (acmIds == null) {
                        IA.log(
                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
 + rtn);
                    } else {
                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
                    }
 */
                    bres.put(rtn);
                    // initialize for next batch (if any)
                    map.put("image_uri_list", new ArrayList<JSONObject>());
                    map.put("image_uuid_list", new ArrayList<JSONObject>());
                    map.put("image_unixtime_list", new ArrayList<Integer>());
                    map.put("image_gps_lat_list", new ArrayList<Double>());
                    map.put("image_gps_lon_list", new ArrayList<Double>());
                    // acmList = new ArrayList<MediaAsset>();
                } else {
                    bres.put("EMPTY BATCH");
                }
                batchCt++;
            }
        }
        allRtn.put("batchResults", bres);

 succeeded in 17354ms:
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
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
?? src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java

exec
/bin/bash -lc 'rg -n "registerOneByDto|WbiaRegisterOutcome|REGISTERED_OK|REGISTERED_ALREADY_PRESENT|NETWORK_FAIL|RESPONSE_BAD|park|poll" src/main/java docs/plans src/test/java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "class QueryCacheFactory|safeInvalidate" src/main/java/org/ecocean/cache src/test/java/org/ecocean/cache src/main/java src/test/java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '260,430p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1095,1165p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 479ms:
        allRtn.put("batchResults", bres);
        return allRtn;
    }

    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
        Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");

        if (u == null)
            throw new MalformedURLException(
                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);
        int ct = 0;
        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaAnnotIds = new ArrayList<String>();
        if (checkFirst) iaAnnotIds = iaAnnotationIds();
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        map.put("annot_theta_list", new ArrayList<Double>());

        List<Annotation> acmList = new ArrayList<Annotation>(); // for rectifyAnnotationIds below
        for (Annotation ann : anns) {
            if (iaAnnotIds.contains(ann.getAcmId())) continue;
            if (iaAnnotIds.contains(ann.getId())) continue;
            if (ann.getMediaAsset() == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
                    "; skipping!");
                continue;
            }
            if (ann.getMediaAsset().getAcmId() == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
                    " (MediaAsset id=" + ann.getMediaAsset().getId() +
                    " not added to IA?); skipping!");
                continue;
            }
            if (!IBEISIA.validForIdentification(ann)) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
                continue;
            }
            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
            if (iid == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
                    ann.getMediaAsset() + " on " + ann + "; skipping!");
                continue;
            }
            acmList.add(ann);
            map.get("image_uuid_list").add(iid);
            int[] bbox = ann.getBbox();
            map.get("annot_bbox_list").add(bbox);
            // yuck - IA class is not species
            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
            // better
            map.get("annot_species_list").add(ann.getIAClass());

            map.get("annot_theta_list").add(ann.getTheta());
            String name = ann.findIndividualId(myShepherd);
            map.get("annot_name_list").add((name == null) ? "____" : name);
            ct++;
        }
        // myShepherd.rollbackDBTransaction();

        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
        if (ct < 1) return null; // null for "none to send" ?  is this cool?
        System.out.println("sendAnnotations(): data -->\n" + map);
        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        System.out.println("sendAnnotations() -> " + rtn);
        List<String> acmIds = acmIdsFromResponse(rtn);
        if (acmIds == null) {
            IA.log(
                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
                + rtn);
        } else {
            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds, myShepherd);
            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
                " Annotation(s) acmId(s) via rectifyAnnotationIds()");
        }
        return rtn;
    }

    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
        Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");

        if (u == null)
            throw new MalformedURLException(
                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);
        int ct = 0;
        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaAnnotIds = new ArrayList<String>();
        if (checkFirst) iaAnnotIds = iaAnnotationIds();
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        map.put("annot_theta_list", new ArrayList<Double>());
        for (Annotation ann : anns) {
            if (iaAnnotIds.contains(ann.getAcmId())) continue;
            if (iaAnnotIds.contains(ann.getId())) continue;
            if (ann.getMediaAsset() == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
                    ann + "; skipping!");
                continue;
            }
            if (!IBEISIA.validForIdentification(ann)) {
                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
                continue;
            }
            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
            if (iid == null) {
                IA.log(
                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
                    + ann.getMediaAsset() + " on " + ann + "; skipping!");
                continue;
            }
            map.get("image_uuid_list").add(iid);
            JSONObject aid = toFancyUUID(ann.getId());
            map.get("annot_uuid_list").add(aid);
            int[] bbox = ann.getBbox();
            map.get("annot_bbox_list").add(bbox);
            // yuck - IA class is not species
            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
            // better
            map.get("annot_species_list").add(ann.getIAClass());

            map.get("annot_theta_list").add(ann.getTheta());
            String name = ann.findIndividualId(myShepherd);
            map.get("annot_name_list").add((name == null) ? "____" : name);
            ct++;
        }
        // myShepherd.rollbackDBTransaction();

        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
        if (ct < 1) return null; // null for "none to send" ?  is this cool?
        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
        System.out.println("sendAnnotationsForceId() -> " + rtn);
        checkForcedIds(map.get("annot_uuid_list"), rtn.optJSONArray("response"));
        return rtn;
    }

    // ------------------------------------------------------------------
    // ml-service migration v2: no-Shepherd WBIA registration helpers.
    //
    // The polling thread in StartupWildbook splits the work into:
    //   Phase A (write tx) - load DTO + close.
    //   Phase B (no DB)    - call into the helpers below.
    //   Phase C (write tx) - persist result.
    // Phase B must not hold a Shepherd transaction across the WBIA call.
    // ------------------------------------------------------------------

    /**
     * Outcome of a Phase-B WBIA registration attempt.
     * REGISTERED_OK              - POST succeeded, ids match.
     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
     * NETWORK_FAIL               - GET or POST threw / non-2xx.
     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
     *                              (id mismatch, length mismatch, missing field).
     */
    public enum WbiaRegisterOutcome {

 succeeded in 625ms:
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
        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
        if (mediaAssetToUri(ma) == null) {
            System.out.println(
                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
                ma);
            return false;
        }
        return true;
    }

    // this is used to give a string to IA for annot_species_list specifially
    // hence the term "IASpecies"
    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
        // NOTE: returning null here is probably "bad" btw....
        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
        if (enc == null) return null;
        String ts = enc.getTaxonomyString();
        if (ts == null) return null;
        return ts.replaceAll(" ", "_");
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("WildbookIAM IA Plugin")
                   .toString();
    }
}

 succeeded in 2330ms:
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:73:    //           IBEISIA flow"). Excluded from polling.
src/main/java/org/ecocean/Annotation.java:83:    // chronically-failing rows park rather than spin forever.
src/main/java/org/ecocean/Embedding.java:402:        // polling/UI semantics.
src/main/java/org/ecocean/api/UserInfo.java:15:    // for polling we do a simple HEAD response
docs/plans/2026-05-18-wbia-image-registration-design.md:1:# Design: image-then-annotation WBIA registration in polling thread
docs/plans/2026-05-18-wbia-image-registration-design.md:5:The v2 WBIA registration polling thread (commit `c6ffe5d20` and follow-ups)
docs/plans/2026-05-18-wbia-image-registration-design.md:11:- The polling JDOQL picks pending annotations (`wbiaRegistered == false
docs/plans/2026-05-18-wbia-image-registration-design.md:14:- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
docs/plans/2026-05-18-wbia-image-registration-design.md:21:polling thread fires `/api/annot/json/`, WBIA has no record of the image
docs/plans/2026-05-18-wbia-image-registration-design.md:25:then park.
docs/plans/2026-05-18-wbia-image-registration-design.md:29:Make the polling thread register the image first when needed, then the
docs/plans/2026-05-18-wbia-image-registration-design.md:31:keep one retry counter, keep one polling thread.
docs/plans/2026-05-18-wbia-image-registration-design.md:36:  registration at intake time. The polling thread can handle it
docs/plans/2026-05-18-wbia-image-registration-design.md:38:- Adding a parallel image-registration polling thread. Doubles the
docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-wbia-image-registration-design.md:90:If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
docs/plans/2026-05-18-wbia-image-registration-design.md:91:behavior — keeps the ineligible-park path consistent).
docs/plans/2026-05-18-wbia-image-registration-design.md:170:### Phase B `registerOneByDto` modification
docs/plans/2026-05-18-wbia-image-registration-design.md:176:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-wbia-image-registration-design.md:177:    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-wbia-image-registration-design.md:184:        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-wbia-image-registration-design.md:188:            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-wbia-image-registration-design.md:193:        // every poll cycle.
docs/plans/2026-05-18-wbia-image-registration-design.md:200:    catch (IOException ex) { return NETWORK_FAIL; }
docs/plans/2026-05-18-wbia-image-registration-design.md:207:No new `WbiaRegisterOutcome` value. The 4 existing values handle every
docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-wbia-image-registration-design.md:214:- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
docs/plans/2026-05-18-wbia-image-registration-design.md:215:  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).
docs/plans/2026-05-18-wbia-image-registration-design.md:218:counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
docs/plans/2026-05-18-wbia-image-registration-design.md:219:the annotation parks after 10 attempts — same as today, just with image
docs/plans/2026-05-18-wbia-image-registration-design.md:234:Worst case: skip invalidation and accept that the next ~30 polling ticks
docs/plans/2026-05-18-wbia-image-registration-design.md:256:- `registerOneByDto_image_already_present_skips_phase0_post` — mock
docs/plans/2026-05-18-wbia-image-registration-design.md:259:- `registerOneByDto_image_absent_triggers_phase0_post` — mock empty
docs/plans/2026-05-18-wbia-image-registration-design.md:262:- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
docs/plans/2026-05-18-wbia-image-registration-design.md:263:  throw IOException; verify NETWORK_FAIL returned without any POST.
docs/plans/2026-05-18-wbia-image-registration-design.md:264:- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
docs/plans/2026-05-18-wbia-image-registration-design.md:265:  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
docs/plans/2026-05-18-wbia-image-registration-design.md:266:- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
docs/plans/2026-05-18-wbia-image-registration-design.md:267:  succeed → REGISTERED_OK.
docs/plans/2026-05-18-wbia-image-registration-design.md:272:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-wbia-image-registration-design.md:281:| Image POST fails | NETWORK_FAIL | Retry next tick |
docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-empty-match-prospects-design.md:19:   polling thread fires `/api/annot/json/` against WBIA without WBIA
docs/plans/2026-05-18-empty-match-prospects-design.md:149:  round-2 Major). Phase 3 polls with the matchingSetQuery's
docs/plans/2026-05-18-empty-match-prospects-design.md:155:  scope which **closes** before Phase 3's OpenSearch poll. No
docs/plans/2026-05-18-empty-match-prospects-design.md:181:  paused WBIA design — handling retroactively in the polling
docs/plans/2026-05-18-empty-match-prospects-design.md:195:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-design.md:228:   `WildbookIAM.java:121-130`). If either fails, park at
docs/plans/2026-05-18-empty-match-prospects-design.md:238:   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-design.md:276:        // Cache invalidation must not abort the polling cycle; just log.
docs/plans/2026-05-18-empty-match-prospects-design.md:489:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-design.md:546:Same `_refresh`-on-entry + exponential-backoff polling pattern as
docs/plans/2026-05-18-empty-match-prospects-design.md:789:No DB transaction is held during the OS poll. Matches the
docs/plans/2026-05-18-empty-match-prospects-design.md:1051:- WBIA registration polling thread origin: `c6ffe5d20` (c11).
src/main/java/org/ecocean/AcmIdBot.java:91:    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:197:   without WBIA's help), so when the annotation-registration polling
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:225:  paused WBIA design — handling it retroactively in the polling thread
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:277:   `WildbookIAM.java`). If either fails, Phase A parks the
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:278:   annotation at `MAX_ATTEMPTS` (existing park behavior).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:288:   existing `NETWORK_FAIL` outcome, with the Phase C log line
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:414:  is `_count`-based polling, bounded by `VISIBILITY_TIMEOUT_MS`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:416:  poll cycle, regardless of how many ids are in the request.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:548:- WBIA registration polling thread origin: `c6ffe5d20` (c11)
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:578:docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:628:src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:654:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:676:docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:692:docs/plans/2026-05-18-wbia-image-registration-design.md:176:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:703:docs/plans/2026-05-18-wbia-image-registration-design.md:272:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:705:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:756:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:938: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1837:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1846:   476	    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1857:   487	    //     every poll return zero hits regardless of how long we wait.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1859:   489	    // Then polls a _count eligibility query with exponential backoff (start
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1886:   516	                "— every poll will return zero hits regardless of wait time.");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4992:    67	    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:4998:    73	    //           IBEISIA flow"). Excluded from polling.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5008:    83	    // chronically-failing rows park rather than spin forever.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5393:src/main/java/org/ecocean/StartupWildbook.java:870:        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5689:   481	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5736:   528	     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5816:   608	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5817:   609	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5820:   612	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5821:   613	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5827:   619	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5829:   621	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5837:   629	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5843:   635	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5844:   636	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5975:   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5979:   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5980:   701	     * fall out of the polling query.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:5986:   707	        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6001:   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6002:   723	            iam.registerOneByDto(dto);
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6022:   743	     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6024:   745	     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6046:   767	            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6055:   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6090:   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6255:   643	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6256:   644	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6261:   649	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6262:   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6264:   652	        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6350:   827	                case REGISTERED_OK:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6351:   828	                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6353:   830	                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6357:   834	                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6358:   835	                case RESPONSE_BAD:
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6361:   838	                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6392:   869	        // Stop the WBIA poller first so it does not race teardown of
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6393:   870	        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6611:docs/plans/2026-05-18-wbia-image-registration-design.md:14:- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6616:docs/plans/2026-05-18-wbia-image-registration-design.md:170:### Phase B `registerOneByDto` modification
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6625:docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6626:docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6627:docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6628:docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6637:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6638:docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6640:docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java:41:    // polling heartbeat thread
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:194:   polling thread fires `/api/annot/json/` against WBIA without WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:304:  round-2 Major). Phase 3 polls with the matchingSetQuery's
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:310:  scope which **closes** before Phase 3's OpenSearch poll. No
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:336:  paused WBIA design — handling retroactively in the polling
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:350:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:383:   `WildbookIAM.java:121-130`). If either fails, park at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:393:   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:431:        // Cache invalidation must not abort the polling cycle; just log.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:634:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:691:Same `_refresh`-on-entry + exponential-backoff polling pattern as
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:927:No DB transaction is held during the OS poll. Matches the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1183:- WBIA registration polling thread origin: `c6ffe5d20` (c11).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1227:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1243:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1505: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2108:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2117:   476	    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2128:   487	    //     every poll return zero hits regardless of how long we wait.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2130:   489	    // Then polls a _count eligibility query with exponential backoff (start
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2157:   516	                "— every poll will return zero hits regardless of wait time.");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:2461:   402	        // polling/UI semantics.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:3632:   481	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4033:    67	    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4039:    73	    //           IBEISIA flow"). Excluded from polling.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:4049:    83	    // chronically-failing rows park rather than spin forever.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6852:/bin/bash -lc "rg -n \"WbiaRegister|wbiaRegister|iaAnnotationIdsStrict|sendAnnotations|park|NETWORK_FAIL|Phase 0|Phase 1|Phase 2|mediaAssetToUri|parseImage\" src/main/java/org/ecocean/ia src/main/java/org/ecocean/ia/plugin src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6970:     1	# Design: image-then-annotation WBIA registration in polling thread
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6974:     5	The v2 WBIA registration polling thread (commit `c6ffe5d20` and follow-ups)
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6980:    11	- The polling JDOQL picks pending annotations (`wbiaRegistered == false
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6983:    14	- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6990:    21	polling thread fires `/api/annot/json/`, WBIA has no record of the image
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6994:    25	then park.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6998:    29	Make the polling thread register the image first when needed, then the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7000:    31	keep one retry counter, keep one polling thread.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7005:    36	  registration at intake time. The polling thread can handle it
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7007:    38	- Adding a parallel image-registration polling thread. Doubles the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7021:    52	| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7059:    90	If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7060:    91	behavior — keeps the ineligible-park path consistent).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7139:   170	### Phase B `registerOneByDto` modification
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7145:   176	public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7146:   177	    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7153:   184	        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7157:   188	            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7162:   193	        // every poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7169:   200	    catch (IOException ex) { return NETWORK_FAIL; }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7176:   207	No new `WbiaRegisterOutcome` value. The 4 existing values handle every
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7179:   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7180:   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7181:   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7182:   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7183:   214	- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7184:   215	  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7187:   218	counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7188:   219	the annotation parks after 10 attempts — same as today, just with image
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7193:src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7201:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7204:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:528:     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7206:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7207:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7208:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:612:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7209:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7210:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7211:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7212:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7213:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:635:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7214:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7215:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:643:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7216:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7217:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:649:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7218:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7219:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7242:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7243:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7244:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7250:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7251:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7252:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7254:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7255:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7256:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7257:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7258:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7259:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7283:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7284:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7285:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7291:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7292:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7293:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7295:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7296:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7297:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7298:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7299:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7300:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7324:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7325:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7326:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7332:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7333:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7334:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7336:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7337:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7338:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7339:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7340:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7341:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7355:src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7358:src/main/java/org/ecocean/Annotation.java:83:    // chronically-failing rows park rather than spin forever.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7390:src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7407:src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7410:src/main/java/org/ecocean/StartupWildbook.java:707:        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7412:src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7415:src/main/java/org/ecocean/StartupWildbook.java:743:     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7416:src/main/java/org/ecocean/StartupWildbook.java:745:     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7421:src/main/java/org/ecocean/StartupWildbook.java:767:            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7422:src/main/java/org/ecocean/StartupWildbook.java:776:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7428:src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7430:src/main/java/org/ecocean/StartupWildbook.java:830:                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7432:src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7434:src/main/java/org/ecocean/StartupWildbook.java:838:                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7478:   450	        // Refuse to start a second poller if one is already running; this
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7590:   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7594:   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7595:   701	     * fall out of the polling query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7601:   707	        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7616:   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7617:   723	            iam.registerOneByDto(dto);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7637:   743	     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7639:   745	     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7661:   767	            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7670:   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7705:   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7721:   827	                case REGISTERED_OK:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7722:   828	                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7724:   830	                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7728:   834	                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7729:   835	                case RESPONSE_BAD:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7732:   838	                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7753:   424	     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7754:   425	     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7755:   426	     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7756:   427	     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7759:   430	    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7760:   431	        REGISTERED_OK,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7761:   432	        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7762:   433	        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7763:   434	        RESPONSE_BAD,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7849:   608	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7850:   609	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7853:   612	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7854:   613	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7860:   619	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7862:   621	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7870:   629	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7876:   635	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7877:   636	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7884:   643	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7885:   644	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7890:   649	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7891:   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7893:   652	        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7921:    23	 * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7922:    24	 * polling thread integration in a dev deployment; here we cover the
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:415:    // The polling thread in StartupWildbook splits the work into:
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:424:     * REGISTERED_OK              - POST succeeded, ids match.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:431:        REGISTERED_OK,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:509:        // rather than NPE-ing out and aborting the poll cycle.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:556:     * registered" in the polling thread's already-present check.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:575:     * the v2 WBIA registration polling thread needs this so a network
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:588:        // rather than NPE-ing out and aborting the poll cycle.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:735:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:736:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:739:        WbiaRegisterOutcome phase0 = registerImageIfMissing(dto);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:740:        if (phase0 != null && phase0 != WbiaRegisterOutcome.REGISTERED_OK &&
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:741:            phase0 != WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:742:            // NETWORK_FAIL or RESPONSE_BAD; propagate so the polling
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:743:            // thread retries / parks (Codex round-2 #6: no new outcome
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:751:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:752:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:758:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:760:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:768:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:774:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:775:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:782:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:783:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:788:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:789:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:792:        // so the next polling iteration sees this new annotation as already
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:795:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:799:     * Phase 0 of registerOneByDto: ensure the image referenced by
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:805:     *   <li>{@link WbiaRegisterOutcome#REGISTERED_ALREADY_PRESENT} if the
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:807:     *   <li>{@link WbiaRegisterOutcome#REGISTERED_OK} if the image was
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:811:     *   <li>{@link WbiaRegisterOutcome#NETWORK_FAIL} on missing
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:814:     *   <li>{@link WbiaRegisterOutcome#RESPONSE_BAD} if the POST
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:819:     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:821:    WbiaRegisterOutcome registerImageIfMissing(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:822:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:827:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:831:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:836:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:844:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:847:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:854:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:862:            return WbiaRegisterOutcome.NETWORK_FAIL;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:869:            return WbiaRegisterOutcome.RESPONSE_BAD;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:874:        return WbiaRegisterOutcome.REGISTERED_OK;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1090:     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:194:   polling thread fires `/api/annot/json/` against WBIA without WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:266:  round-2 Major). Phase 3 polls with the matchingSetQuery's
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:272:  scope which **closes** before Phase 3's OpenSearch poll. No
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:298:  paused WBIA design — handling retroactively in the polling
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:312:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:345:   `WildbookIAM.java:121-130`). If either fails, park at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:355:   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:393:        // Cache invalidation must not abort the polling cycle; just log.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:546:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:603:Same `_refresh`-on-entry + exponential-backoff polling pattern as
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:785:No DB transaction is held during the OS poll. Matches the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1026:- WBIA registration polling thread origin: `c6ffe5d20` (c11).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1093:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1164:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1173:    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1184:    //     every poll return zero hits regardless of how long we wait.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1186:    // Then polls a _count eligibility query with exponential backoff (start
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1213:                "— every poll will return zero hits regardless of wait time.");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:1971: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:3763:        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:3810:     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4649: * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4907:     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4987:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4988:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4991:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4992:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:4998:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5000:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5008:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5014:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5015:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5022:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5023:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5028:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5029:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5031:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5107:/bin/bash -lc 'rg -n "WbiaRegister|registerOne|wbia|WbiaRegisterOutcome|Park|park|wbiaRegister" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5137: * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5138: * polling thread integration in a dev deployment; here we cover the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5300:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5304:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5305:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5306:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:612:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5307:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5308:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5309:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5310:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5311:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:635:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5312:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5313:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:643:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5314:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5315:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:649:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5316:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5317:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5319:src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5323:src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5326:src/main/java/org/ecocean/Annotation.java:83:    // chronically-failing rows park rather than spin forever.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5345:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5349:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5350:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5351:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:612:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5352:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5353:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5354:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5355:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5356:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:635:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5357:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5358:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:643:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5359:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5360:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:649:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5361:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5362:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5378:src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5392:src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5393:src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5397:src/main/java/org/ecocean/StartupWildbook.java:707:        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5399:src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5400:src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5403:src/main/java/org/ecocean/StartupWildbook.java:743:     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5404:src/main/java/org/ecocean/StartupWildbook.java:745:     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5409:src/main/java/org/ecocean/StartupWildbook.java:767:            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5410:src/main/java/org/ecocean/StartupWildbook.java:776:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5416:src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5418:src/main/java/org/ecocean/StartupWildbook.java:830:                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5421:src/main/java/org/ecocean/StartupWildbook.java:838:                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5433:src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5445:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5457:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5458:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5459:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5460:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5461:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5462:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5463:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5464:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5465:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5486:src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5505:src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5562:     * ml-service migration v2 §commit #11. Background polling thread that
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5586:        // Refuse to start a second poller if one is already running; this
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5695:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5699:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5700:     * fall out of the polling query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5706:        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5721:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5722:            iam.registerOneByDto(dto);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5742:     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5744:     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5766:            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5775:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5810:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5826:                case REGISTERED_OK:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5827:                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5829:                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5833:                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5834:                case RESPONSE_BAD:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5837:                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5907:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5913:    //           IBEISIA flow"). Excluded from polling.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:5923:    // chronically-failing rows park rather than spin forever.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6639:--     flow. Mark them as registered so the new background-polling thread
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6651:-- (5) Partial index for the WBIA-registration polling thread (commit #11
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6652:--     fix-pass). The poller's JDOQL filter is
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6657:--     The predicate matches the poller's filter exactly (also excluding
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:6658:--     parked rows at attempts == MAX_ATTEMPTS, so abandoned rows never
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:7710:        // polling/UI semantics.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:8529:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:24: * polling thread integration in a dev deployment; here we cover the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:194:   polling thread fires `/api/annot/json/` against WBIA without WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:227:  round-2 Major). Phase 3 polls with the matchingSetQuery's
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:233:  scope which **closes** before Phase 3's OpenSearch poll. No
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:259:  paused WBIA design — handling retroactively in the polling
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:273:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:306:   `WildbookIAM.java:121-130`). If either fails, park at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:316:   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:452:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:492:`_refresh`-on-entry + exponential-backoff polling pattern.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:665:No DB transaction is held during the OS poll. Matches the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:889:- WBIA registration polling thread origin: `c6ffe5d20` (c11).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:934:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:947:docs/plans/2026-05-18-empty-match-prospects-design.md:98:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:978:docs/plans/2026-05-18-empty-match-prospects-design.md:277:`waitForVisibility` today polls `_count` with an `ids` query
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1044:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1056:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1124:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1172:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1220:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5334:    17	 * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1266:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6676:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1321:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1378:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1382:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1390:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1398:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:241:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1449:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:573:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1454:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:578:docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1458:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:582:docs/plans/2026-05-18-empty-match-prospects-design.md:66:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. Returns `boolean`. `_refresh` on entry, then bounded `_count` poll. Already wired into `MlServiceProcessor.waitAndRunMatch`. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1491:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:628:src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1502:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:654:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1521:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:705:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1566:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:1837:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1679:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6637:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1700:docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1710:docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1724:src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2009: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:2993:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:3002:   476	    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:3013:   487	    //     every poll return zero hits regardless of how long we wait.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:3015:   489	    // Then polls a _count eligibility query with exponential backoff (start
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:3042:   516	                "— every poll will return zero hits regardless of wait time.");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6349:    66	--     flow. Mark them as registered so the new background-polling thread
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6980:    67	    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6986:    73	    //           IBEISIA flow"). Excluded from polling.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:6996:    83	    // chronically-failing rows park rather than spin forever.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7291:/bin/bash -lc 'rg -n "WbiaRegister|wbiaRegister|iaAnnotationIdsStrict|parseAnnotationIdsArrayStrict|sendMediaAssetsForceId|mediaAssetToUri|validMediaAsset|iaImageIds|QueryCacheFactory|invalidateByName|NETWORK_FAIL|MAX_ATTEMPTS" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7371:   481	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7419:   608	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7420:   609	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7423:   612	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7424:   613	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7430:   619	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7432:   621	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7440:   629	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7446:   635	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7447:   636	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7454:   643	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7455:   644	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7460:   649	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7461:   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7566:    23	 * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7567:    24	 * polling thread integration in a dev deployment; here we cover the
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7746:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7747:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7748:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7759:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7760:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7761:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7763:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7764:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7765:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7766:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7767:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7768:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7793:src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7807:src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7811:src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7823:src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7826:src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7861:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7862:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7863:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7874:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7875:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:609:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7876:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:613:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7878:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:621:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7879:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:629:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7880:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:636:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7881:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:644:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7882:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:650:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7883:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:652:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7911:src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7931:   430	    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7932:   431	        REGISTERED_OK,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7933:   432	        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7934:   433	        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7935:   434	        RESPONSE_BAD,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:7975:   528	     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8048:   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8050:   652	        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8171:   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8175:   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8176:   701	     * fall out of the polling query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8182:   707	        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8197:   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8198:   723	            iam.registerOneByDto(dto);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8218:   743	     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8220:   745	     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8242:   767	            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8251:   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8286:   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8302:   827	                case REGISTERED_OK:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8303:   828	                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8305:   830	                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8309:   834	                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8310:   835	                case RESPONSE_BAD:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8313:   838	                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8348:   450	        // Refuse to start a second poller if one is already running; this
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:8642:    17	 * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9008:src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:9205:   402	        // polling/UI semantics.
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:23: * by the WBIA registration polling thread in a dev deployment.
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:54: *   <li>WBIA registration polling.</li>
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:141:     * 1 MB image is ~1.4 MB of text — emitting it to stdout pollutes the
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:16: * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:194:   polling thread fires `/api/annot/json/` against WBIA without WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:248:  paused WBIA design — handling retroactively in the polling
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:262:| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:295:   `WildbookIAM.java:121-130`). If either fails, park at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:305:   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:475:// last ~5min, matching the WBIA register polling thread's cadence.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:675:- WBIA registration polling thread origin: `c6ffe5d20` (c11).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1172:   467	    // Callers (typically waitForVisibility) follow with a visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1181:   476	    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1192:   487	    //     every poll return zero hits regardless of how long we wait.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1194:   489	    // Then polls a _count eligibility query with exponential backoff (start
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1221:   516	                "— every poll will return zero hits regardless of wait time.");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:1431: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:3704:   402	        // polling/UI semantics.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:5334:    17	 * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6652:docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6674:docs/plans/2026-05-18-wbia-image-registration-design.md:272:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6676:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6726:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:619:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7122:   481	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7169:   528	     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7388:   426	     * ml-service migration v2 §commit #11. Background polling thread that
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7412:   450	        // Refuse to start a second poller if one is already running; this
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7486:src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7519:src/main/java/org/ecocean/StartupWildbook.java:776:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7532:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:608:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7537:src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7574:   415	    // The polling thread in StartupWildbook splits the work into:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7583:   424	     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7584:   425	     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7585:   426	     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7586:   427	     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7589:   430	    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7590:   431	        REGISTERED_OK,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7591:   432	        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7592:   433	        NETWORK_FAIL,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7593:   434	        RESPONSE_BAD,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7660:   608	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7661:   609	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7664:   612	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7665:   613	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7671:   619	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7673:   621	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7681:   629	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7687:   635	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7688:   636	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7695:   643	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7696:   644	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7701:   649	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7702:   650	            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7704:   652	        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7732:   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7733:   701	     * fall out of the polling query.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7739:   707	        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7754:   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7755:   723	            iam.registerOneByDto(dto);
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7775:   743	     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7777:   745	     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7799:   767	            // annotation under its current state, so park it.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7808:   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7843:   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7859:   827	                case REGISTERED_OK:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7860:   828	                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7862:   830	                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7866:   834	                case NETWORK_FAIL:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7867:   835	                case RESPONSE_BAD:
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:7870:   838	                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8873:    19	   polling thread fires `/api/annot/json/` against WBIA without WBIA
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8927:    73	  paused WBIA design — handling retroactively in the polling
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8941:    87	| `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` | `OpenSearch.java:498` | Works. `_refresh` + bounded `_count` poll. |
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8974:   120	   `WildbookIAM.java:121-130`). If either fails, park at
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8984:   130	   the existing `NETWORK_FAIL` outcome; Phase C log line
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9174:   300	// last ~5min, matching the WBIA register polling thread's cadence.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9379:   500	- WBIA registration polling thread origin: `c6ffe5d20` (c11).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9408:   184	        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9412:   188	            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9417:   193	        // every poll cycle.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9424:   200	    catch (IOException ex) { return NETWORK_FAIL; }
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9431:   207	No new `WbiaRegisterOutcome` value. The 4 existing values handle every
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9434:   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9435:   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9436:   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9437:   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9438:   214	- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9439:   215	  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9442:   218	counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9443:   219	the annotation parks after 10 attempts — same as today, just with image
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9458:   234	Worst case: skip invalidation and accept that the next ~30 polling ticks
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9470:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9472:- **Major:** make the Shepherd boundary explicit in `MatchVisibilityGateImpl`. It should load task/status/eligible IDs under Shepherd, close it, then call OpenSearch. Do not hold a DB transaction through the visibility poll.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9504:- **Major:** `_id` visibility is weaker than “matchable visibility.” `waitForVisibility` only counts IDs ([OpenSearch.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/OpenSearch.java:521>)), while matching requires nested `embeddings.method` / `methodVersion` ([Annotation.java](</mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Annotation.java:1194>)). Add a visibility poll that checks IDs plus `matchAgainst`, `acmId`, and nested embedding method/version, or stale docs can pass the gate.
docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9506:- **Major:** make the Shepherd boundary explicit in `MatchVisibilityGateImpl`. It should load task/status/eligible IDs under Shepherd, close it, then call OpenSearch. Do not hold a DB transaction through the visibility poll.
src/test/java/org/ecocean/OpenSearchVisibilityTest.java:17: * The full poll-and-wait behavior of waitForVisibility requires a real
docs/plans/2026-05-18-c5-codex-review.md:20:polling thread, plus extend Phase A eligibility with
docs/plans/2026-05-18-c5-codex-review.md:41:+            // WildbookIAM.java:121-130 so the polling thread parks the same
docs/plans/2026-05-18-c5-codex-review.md:51:                 System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-c5-codex-review.md:303:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c5-codex-review.md:304:        REGISTERED_OK,
docs/plans/2026-05-18-c5-codex-review.md:305:        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c5-codex-review.md:306:        NETWORK_FAIL,
docs/plans/2026-05-18-c5-codex-review.md:307:        RESPONSE_BAD,
docs/plans/2026-05-18-c5-codex-review.md:389:     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-c5-codex-review.md:391:     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-c5-codex-review.md:413:            // annotation under its current state, so park it.
docs/plans/2026-05-18-c5-codex-review.md:423:            // WildbookIAM.java:121-130 so the polling thread parks the same
docs/plans/2026-05-18-c5-codex-review.md:433:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-c5-codex-review.md:478:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-c5-codex-review.md:494:+            // WildbookIAM.java:121-130 so the polling thread parks the same
docs/plans/2026-05-18-c5-codex-review.md:504:                 System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-c5-codex-review.md:761:    // The polling thread in StartupWildbook splits the work into:
docs/plans/2026-05-18-c5-codex-review.md:770:     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c5-codex-review.md:771:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c5-codex-review.md:772:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c5-codex-review.md:773:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c5-codex-review.md:776:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c5-codex-review.md:910:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:722:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c5-codex-review.md:978:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c5-codex-review.md:979:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c5-codex-review.md:981:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c5-codex-review.md:1130:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c5-codex-review.md:1131:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c5-codex-review.md:1134:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c5-codex-review.md:1135:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c5-codex-review.md:1141:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c5-codex-review.md:1143:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c5-codex-review.md:1151:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c5-codex-review.md:1157:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c5-codex-review.md:1158:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c5-codex-review.md:1165:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c5-codex-review.md:1166:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c5-codex-review.md:1187:     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c5-codex-review.md:1256: * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-c5-codex-review.md:1257: * polling thread integration in a dev deployment; here we cover the
docs/plans/2026-05-18-c5-codex-review.md:1426:   767	            // annotation under its current state, so park it.
docs/plans/2026-05-18-c5-codex-review.md:1436:   777	            // WildbookIAM.java:121-130 so the polling thread parks the same
docs/plans/2026-05-18-c5-codex-review.md:1446:   787	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-c5-codex-review.md:1976:./docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c5-codex-review.md:1977:./docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:1978:./docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c5-codex-review.md:1979:./docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:1983:./docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c5-codex-review.md:1984:./docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c5-codex-review.md:2002:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6625:docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c5-codex-review.md:2003:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6626:docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2004:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6627:docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c5-codex-review.md:2005:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6628:docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2009:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6638:docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c5-codex-review.md:2010:./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6640:docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c5-codex-review.md:2017:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6852:/bin/bash -lc "rg -n \"WbiaRegister|wbiaRegister|iaAnnotationIdsStrict|sendAnnotations|park|NETWORK_FAIL|Phase 0|Phase 1|Phase 2|mediaAssetToUri|parseImage\" src/main/java/org/ecocean/ia src/main/java/org/ecocean/ia/plugin src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c5-codex-review.md:2023:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7179:   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c5-codex-review.md:2024:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7180:   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2025:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7181:   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c5-codex-review.md:2026:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7182:   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2056:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9434:   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c5-codex-review.md:2057:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9435:   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2058:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9436:   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c5-codex-review.md:2059:./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9437:   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2070:./docs/plans/2026-05-18-c4-codex-review.md:1030: * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c5-codex-review.md:2081:./docs/plans/2026-05-18-c3-codex-review.md:299: * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c5-codex-review.md:2087:./docs/plans/2026-05-18-c3-codex-review.md:1947:    16	 * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c5-codex-review.md:2092:./docs/plans/2026-05-18-c3-codex-review.md:2244:/bin/bash -lc 'rg -n "Phase 0|iaImageIdsStrict|safeInvalidate|iaImageIds|REGISTERED_IMAGE|image already|already registered|WbiaRegisterOutcome" docs/plans/2026-05-09-ml-service-migration-v2.md docs/plans/2026-05-18-wbia-image-registration-design.md src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/ia' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c5-codex-review.md:2098:./docs/plans/2026-05-18-c3-codex-review.md:2464:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c5-codex-review.md:2099:./docs/plans/2026-05-18-c3-codex-review.md:2465:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2100:./docs/plans/2026-05-18-c3-codex-review.md:2466:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c5-codex-review.md:2101:./docs/plans/2026-05-18-c3-codex-review.md:2467:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2105:./docs/plans/2026-05-18-c3-codex-review.md:2536:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c5-codex-review.md:2106:./docs/plans/2026-05-18-c3-codex-review.md:2545:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c5-codex-review.md:2110:./docs/plans/2026-05-18-c3-codex-review.md:2588:docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c5-codex-review.md:2111:./docs/plans/2026-05-18-c3-codex-review.md:2589:docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2112:./docs/plans/2026-05-18-c3-codex-review.md:2590:docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c5-codex-review.md:2113:./docs/plans/2026-05-18-c3-codex-review.md:2591:docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c5-codex-review.md:2117:./docs/plans/2026-05-18-c3-codex-review.md:2598:docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c5-codex-review.md:2118:./docs/plans/2026-05-18-c3-codex-review.md:2599:docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c5-codex-review.md:2132:./docs/plans/2026-05-18-c2-codex-review.md:4670:   282	| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c5-codex-review.md:2133:./docs/plans/2026-05-18-c2-codex-review.md:4679:   291	   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c5-codex-review.md:2138:./src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:16: * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c5-codex-review.md:2216:### Phase B `registerOneByDto` modification
docs/plans/2026-05-18-c5-codex-review.md:2222:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c5-codex-review.md:2223:    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c5-codex-review.md:2230:        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c5-codex-review.md:2234:            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-c5-codex-review.md:2240:  registration at intake time. The polling thread can handle it
docs/plans/2026-05-18-c5-codex-review.md:2242:- Adding a parallel image-registration polling thread. Doubles the
docs/plans/2026-05-18-c5-codex-review.md:2256:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-c5-codex-review.md:2299:If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
docs/plans/2026-05-18-c5-codex-review.md:2300:behavior — keeps the ineligible-park path consistent).
docs/plans/2026-05-18-c5-codex-review.md:2401:- `registerOneByDto_image_already_present_skips_phase0_post` — mock
docs/plans/2026-05-18-c5-codex-review.md:2404:- `registerOneByDto_image_absent_triggers_phase0_post` — mock empty
docs/plans/2026-05-18-c5-codex-review.md:2407:- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
docs/plans/2026-05-18-c5-codex-review.md:2408:  throw IOException; verify NETWORK_FAIL returned without any POST.
docs/plans/2026-05-18-c5-codex-review.md:2409:- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
docs/plans/2026-05-18-c5-codex-review.md:2410:  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
docs/plans/2026-05-18-c5-codex-review.md:2411:- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
docs/plans/2026-05-18-c5-codex-review.md:2412:  succeed → REGISTERED_OK.
docs/plans/2026-05-18-c5-codex-review.md:2417:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
src/main/java/org/ecocean/StartupWildbook.java:46:    // poller so contextDestroyed can shut it down cleanly. Without this the
src/main/java/org/ecocean/StartupWildbook.java:47:    // executor leaks across redeploys and a new poll thread starts on top
src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
src/main/java/org/ecocean/StartupWildbook.java:203:        // FileQueue with manual reconcile servlet. The polling thread reads
src/main/java/org/ecocean/StartupWildbook.java:426:     * ml-service migration v2 §commit #11. Background polling thread that
src/main/java/org/ecocean/StartupWildbook.java:450:        // Refuse to start a second poller if one is already running; this
src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
src/main/java/org/ecocean/StartupWildbook.java:701:     * fall out of the polling query.
src/main/java/org/ecocean/StartupWildbook.java:707:        if (dto == null) return;  // ineligible / already registered / parked
src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
src/main/java/org/ecocean/StartupWildbook.java:743:     * Null cases: missing annotation, already registered, parked at max
src/main/java/org/ecocean/StartupWildbook.java:745:     * Ineligible annotations are parked here so they stop being polled.
src/main/java/org/ecocean/StartupWildbook.java:767:            // annotation under its current state, so park it.
src/main/java/org/ecocean/StartupWildbook.java:778:            // the polling thread parks the same media assets the legacy
src/main/java/org/ecocean/StartupWildbook.java:788:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
src/main/java/org/ecocean/StartupWildbook.java:833:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
src/main/java/org/ecocean/StartupWildbook.java:849:                case REGISTERED_OK:
src/main/java/org/ecocean/StartupWildbook.java:850:                case REGISTERED_ALREADY_PRESENT:
src/main/java/org/ecocean/StartupWildbook.java:852:                    // parked by a racing poller: stuck-at-attempts==MAX
src/main/java/org/ecocean/StartupWildbook.java:856:                case NETWORK_FAIL:
src/main/java/org/ecocean/StartupWildbook.java:857:                case RESPONSE_BAD:
src/main/java/org/ecocean/StartupWildbook.java:860:                        // Already parked by another path; do not increment past MAX.
src/main/java/org/ecocean/StartupWildbook.java:891:        // Stop the WBIA poller first so it does not race teardown of
src/main/java/org/ecocean/StartupWildbook.java:892:        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
src/main/java/org/ecocean/StartupWildbook.java:896:        // The poll loop's interrupt/null checks make subsequent work bail.
src/main/java/org/ecocean/StartupWildbook.java:905:    // ml-service migration v2 §commit #11 fix-pass. The polling executor
src/main/java/org/ecocean/StartupWildbook.java:919:            // poller handle that case by bailing out before doing more DB
docs/plans/2026-05-09-ml-service-migration-v2.md:36:| Synchronous `OpenSearch._refresh` after persist; matching ran before reindex completed | Real bounded poll-and-wait on knn-eligibility; deferred-match fallback on timeout |
docs/plans/2026-05-09-ml-service-migration-v2.md:41:| `wbiaRegister` queue with FileQueue at-most-once + manual reconcile servlet | DB-backed via `Annotation.wbiaRegistered`; periodic background thread does the polling and registration; no separate FileQueue |
docs/plans/2026-05-09-ml-service-migration-v2.md:71:        │       status = complete-mlservice; wbiaRegistered = false (so background poll picks it up)
docs/plans/2026-05-09-ml-service-migration-v2.md:78:        │   refresh index; poll knn-eligibility query for ids; bounded
docs/plans/2026-05-09-ml-service-migration-v2.md:98:                    until MAX_ATTEMPTS, then row is excluded from polling
docs/plans/2026-05-09-ml-service-migration-v2.md:102:  → Legacy annotations are excluded from polling because the JDO migration
docs/plans/2026-05-09-ml-service-migration-v2.md:114:| `org.ecocean.OpenSearch` (modified) | New `waitForVisibility(indexName, ids, timeoutMs)` that refreshes + polls knn-eligibility. Documents that it does NOT drain the IndexingManager queue. | +50 |
docs/plans/2026-05-09-ml-service-migration-v2.md:117:| `org.ecocean.Annotation` (modified) | New fields: `predictModelId`, `bboxKey`, `thetaKey`, `wbiaRegistered`, `wbiaRegisterAttempts`. Literal `bboxKey`/`thetaKey`, not hashed. `wbiaRegistered` Boolean (null on legacy → backfilled to true; false on new ml-service → polled by background thread; true on success — terminal). `wbiaRegisterAttempts` int counts failed attempts; ≥ MAX cuts off polling. | +80 |
docs/plans/2026-05-09-ml-service-migration-v2.md:121:| `org.ecocean.StartupWildbook` (modified) | New scheduled background thread polls `Annotation.wbiaRegistered = false AND wbiaRegisterAttempts < MAX` for WBIA registration. | +60 |
docs/plans/2026-05-09-ml-service-migration-v2.md:123:| `frontend/src/pages/Encounter/pollingHelpers.js` (modified) | `isTerminalDetectionStatus` recognizes `complete-mlservice`. | +5 |
docs/plans/2026-05-09-ml-service-migration-v2.md:167:- `frontend/src/pages/Encounter/pollingHelpers.js`: `isTerminalDetectionStatus` recognizes `complete-mlservice`.
docs/plans/2026-05-09-ml-service-migration-v2.md:221:| `WBIAREGISTERED` | boolean, nullable | true = WBIA acknowledged (terminal success). false = needs registration / transient failure (polled by background thread). null = legacy annotation, pre-existing-and-already-registered (excluded from polling; backfilled to true via DDL migration when the column is added). |
docs/plans/2026-05-09-ml-service-migration-v2.md:222:| `WBIAREGISTERATTEMPTS` | integer, default 0 | Number of registration attempts. Background poll filters `< MAX_ATTEMPTS` (e.g. 10). After cutoff, row is parked until operator resets. |
docs/plans/2026-05-09-ml-service-migration-v2.md:235:-- background-polling thread does NOT re-register them.
docs/plans/2026-05-09-ml-service-migration-v2.md:253:This polls knn visibility *only*. It deliberately does NOT try to drain the
docs/plans/2026-05-09-ml-service-migration-v2.md:309:| 5 | State-semantics propagation: backend (`complete-mlservice`/`pending-species`/`dropped-stale` in terminality checks; `MediaAsset.setDetectionStatus` bumps `revision`); frontend (`pollingHelpers.js`, `EncounterStore.js`, `ImageCard.jsx`, `ImageModalStore.js` recognize `complete-mlservice`) | ~60 | #4 |
docs/plans/2026-05-09-ml-service-migration-v2.md:313:| 9 | `MlServiceProcessor` (lifecycle) — persist creates Annotation with `wbiaRegistered = false` and `wbiaRegisterAttempts = 0`; the background poll picks it up. No inline WBIA call. | ~250 | #2 #3 #4 #5 #7 #8 |
docs/plans/2026-05-09-ml-service-migration-v2.md:316:| 11 | DB-backed WBIA registration: `Annotation.wbiaRegistered` + `wbiaRegisterAttempts` background polling thread in `StartupWildbook`; `MatchResult.annotationDetails` exposes `wbiaRegistered` | ~120 | #4 #9 |
docs/plans/2026-05-09-ml-service-migration-v2.md:377:- `wbiaRegister` background thread (#11): poll picks up annotations with `wbiaRegistered=false`; success sets true; transient WBIA failure increments `wbiaRegisterAttempts` (still false); next cycle retries; rows with `attempts >= MAX` are excluded from polling.
docs/plans/2026-05-09-ml-service-migration-v2.md:401:- `OpenSearch.waitForVisibility` documented as polling visibility only, not draining the IndexingManager queue.
docs/plans/2026-05-09-ml-service-migration-v2.md:404:- `wbiaRegister` simplified to DB-backed polling (was FileQueue + reconcile servlet).
docs/plans/2026-05-09-ml-service-migration-v2.md:406:- Frontend `pollingHelpers.js` updates included in commit #5 (was missing).
docs/plans/2026-05-09-ml-service-migration-v2.md:413:- WBIA registration state machine reworked: `wbiaRegistered` is now a real boolean, not tri-state. Backfill UPDATE marks legacy annotations true so the polling query doesn't sweep them. New `wbiaRegisterAttempts` column counts failed attempts; polling filters `< MAX_ATTEMPTS`. Failed registrations are rescheduled by the next poll cycle without flipping the registration flag.
docs/plans/2026-05-09-ml-service-migration-v2.md:415:- Frontend status consumers expanded: `EncounterStore.js`, `ImageCard.jsx`, and `ImageModalStore.js` are added to commit #5 alongside `pollingHelpers.js`.
docs/plans/2026-05-18-c4-codex-review.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-c4-codex-review.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-c4-codex-review.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-c4-codex-review.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-c4-codex-review.md:191:      * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c4-codex-review.md:375:     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c4-codex-review.md:394:     * the v2 WBIA registration polling thread needs this so a network
docs/plans/2026-05-18-c4-codex-review.md:407:        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c4-codex-review.md:730: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-c4-codex-review.md:1030: * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c4-codex-review.md:1242:   898	     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c4-codex-review.md:1255:   528	     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c4-codex-review.md:1274:   547	     * the v2 WBIA registration polling thread needs this so a network
docs/plans/2026-05-18-c4-codex-review.md:1287:   560	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-c3-codex-review.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-c3-codex-review.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-c3-codex-review.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-c3-codex-review.md:182:of `registerOneByDto` (C6) to detect "is this image already
docs/plans/2026-05-18-c3-codex-review.md:199:+     * the v2 WBIA registration polling thread needs this so a network
docs/plans/2026-05-18-c3-codex-review.md:212:+        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:299: * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c3-codex-review.md:392:        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:439:     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c3-codex-review.md:469:     * the v2 WBIA registration polling thread needs this so a network
docs/plans/2026-05-18-c3-codex-review.md:482:        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:602:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:603:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:606:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c3-codex-review.md:607:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:613:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c3-codex-review.md:615:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:623:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:629:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c3-codex-review.md:630:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:637:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:638:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:643:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:644:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:646:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c3-codex-review.md:1017: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-c3-codex-review.md:1702: * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-c3-codex-review.md:1703: * polling thread integration in a dev deployment; here we cover the
docs/plans/2026-05-18-c3-codex-review.md:1903:     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c3-codex-review.md:1947:    16	 * is exercised end-to-end by Phase 0 of the WBIA registration polling
docs/plans/2026-05-18-c3-codex-review.md:2021:   481	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:2068:   528	     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c3-codex-review.md:2098:   558	     * the v2 WBIA registration polling thread needs this so a network
docs/plans/2026-05-18-c3-codex-review.md:2111:   571	        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:2178:   688	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2179:   689	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2182:   692	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c3-codex-review.md:2183:   693	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2189:   699	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c3-codex-review.md:2191:   701	            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2199:   709	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:2205:   715	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c3-codex-review.md:2244:/bin/bash -lc 'rg -n "Phase 0|iaImageIdsStrict|safeInvalidate|iaImageIds|REGISTERED_IMAGE|image already|already registered|WbiaRegisterOutcome" docs/plans/2026-05-09-ml-service-migration-v2.md docs/plans/2026-05-18-wbia-image-registration-design.md src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/ia' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c3-codex-review.md:2248:/bin/bash -lc 'rg -n "registerOneByDto|WbiaRegisterRequest|mediaAssetAcmId|REGISTERED_ALREADY_PRESENT|NETWORK_FAIL|RESPONSE_BAD" src/main/java/org/ecocean/ia src/test/java/org/ecocean/ia/plugin' in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c3-codex-review.md:2252:# Design: image-then-annotation WBIA registration in polling thread
docs/plans/2026-05-18-c3-codex-review.md:2256:The v2 WBIA registration polling thread (commit `c6ffe5d20` and follow-ups)
docs/plans/2026-05-18-c3-codex-review.md:2262:- The polling JDOQL picks pending annotations (`wbiaRegistered == false
docs/plans/2026-05-18-c3-codex-review.md:2265:- Phase B calls `WildbookIAM.registerOneByDto(dto)` (no Shepherd held).
docs/plans/2026-05-18-c3-codex-review.md:2272:polling thread fires `/api/annot/json/`, WBIA has no record of the image
docs/plans/2026-05-18-c3-codex-review.md:2276:then park.
docs/plans/2026-05-18-c3-codex-review.md:2280:Make the polling thread register the image first when needed, then the
docs/plans/2026-05-18-c3-codex-review.md:2282:keep one retry counter, keep one polling thread.
docs/plans/2026-05-18-c3-codex-review.md:2287:  registration at intake time. The polling thread can handle it
docs/plans/2026-05-18-c3-codex-review.md:2289:- Adding a parallel image-registration polling thread. Doubles the
docs/plans/2026-05-18-c3-codex-review.md:2303:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-c3-codex-review.md:2341:If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
docs/plans/2026-05-18-c3-codex-review.md:2342:behavior — keeps the ineligible-park path consistent).
docs/plans/2026-05-18-c3-codex-review.md:2424:### Phase B `registerOneByDto` modification
docs/plans/2026-05-18-c3-codex-review.md:2430:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2431:    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2438:        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2442:            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-c3-codex-review.md:2447:        // every poll cycle.
docs/plans/2026-05-18-c3-codex-review.md:2454:    catch (IOException ex) { return NETWORK_FAIL; }
docs/plans/2026-05-18-c3-codex-review.md:2461:No new `WbiaRegisterOutcome` value. The 4 existing values handle every
docs/plans/2026-05-18-c3-codex-review.md:2464:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c3-codex-review.md:2465:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c3-codex-review.md:2466:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c3-codex-review.md:2467:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c3-codex-review.md:2468:- Image was already in WBIA + Phase 1 succeeds → `REGISTERED_OK` or
docs/plans/2026-05-18-c3-codex-review.md:2469:  `REGISTERED_ALREADY_PRESENT` (depending on the annotation side).
docs/plans/2026-05-18-c3-codex-review.md:2472:counter increments on each NETWORK_FAIL. If WBIA can't be reached at all,
docs/plans/2026-05-18-c3-codex-review.md:2473:the annotation parks after 10 attempts — same as today, just with image
docs/plans/2026-05-18-c3-codex-review.md:2488:Worst case: skip invalidation and accept that the next ~30 polling ticks
docs/plans/2026-05-18-c3-codex-review.md:2510:- `registerOneByDto_image_already_present_skips_phase0_post` — mock
docs/plans/2026-05-18-c3-codex-review.md:2513:- `registerOneByDto_image_absent_triggers_phase0_post` — mock empty
docs/plans/2026-05-18-c3-codex-review.md:2516:- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
docs/plans/2026-05-18-c3-codex-review.md:2517:  throw IOException; verify NETWORK_FAIL returned without any POST.
docs/plans/2026-05-18-c3-codex-review.md:2518:- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
docs/plans/2026-05-18-c3-codex-review.md:2519:  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
docs/plans/2026-05-18-c3-codex-review.md:2520:- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
docs/plans/2026-05-18-c3-codex-review.md:2521:  succeed → REGISTERED_OK.
docs/plans/2026-05-18-c3-codex-review.md:2526:mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-c3-codex-review.md:2534:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-c3-codex-review.md:2535:| Image POST fails | NETWORK_FAIL | Retry next tick |
docs/plans/2026-05-18-c3-codex-review.md:2536:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c3-codex-review.md:2545:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c3-codex-review.md:2579:docs/plans/2026-05-18-wbia-image-registration-design.md:176:public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2580:docs/plans/2026-05-18-wbia-image-registration-design.md:177:    if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2584:docs/plans/2026-05-18-wbia-image-registration-design.md:184:        return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2585:docs/plans/2026-05-18-wbia-image-registration-design.md:188:            return WbiaRegisterOutcome.NETWORK_FAIL;  // retry next tick
docs/plans/2026-05-18-c3-codex-review.md:2587:docs/plans/2026-05-18-wbia-image-registration-design.md:207:No new `WbiaRegisterOutcome` value. The 4 existing values handle every
docs/plans/2026-05-18-c3-codex-review.md:2588:docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
docs/plans/2026-05-18-c3-codex-review.md:2589:docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
docs/plans/2026-05-18-c3-codex-review.md:2590:docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
docs/plans/2026-05-18-c3-codex-review.md:2591:docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
docs/plans/2026-05-18-c3-codex-review.md:2597:docs/plans/2026-05-18-wbia-image-registration-design.md:280:| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-c3-codex-review.md:2598:docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c3-codex-review.md:2599:docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c3-codex-review.md:2607:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c3-codex-review.md:2620:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2621:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2622:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2623:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2624:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:2625:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2626:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2627:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2628:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c3-codex-review.md:2640:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c3-codex-review.md:2653:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2654:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2655:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2656:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2657:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:2658:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2659:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2660:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2661:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c3-codex-review.md:2669:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c3-codex-review.md:2670:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c3-codex-review.md:2671:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c3-codex-review.md:2672:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c3-codex-review.md:2673:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-c3-codex-review.md:2674:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
docs/plans/2026-05-18-c3-codex-review.md:2682:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2683:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2684:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c3-codex-review.md:2685:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2686:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c3-codex-review.md:2687:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2688:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:2689:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c3-codex-review.md:2690:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2691:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:2692:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2693:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:2694:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2697:src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
docs/plans/2026-05-18-c3-codex-review.md:2849:/bin/bash -lc "rg -n \"wbiaRegistered|WbiaRegisterOutcome|REGISTERED_OK|REGISTERED_ALREADY_PRESENT|RESPONSE_BAD|NETWORK_FAIL|registerOneByDto|loadWbiaRegisterDto|MAX_ATTEMPTS\" src/main/java/org/ecocean/StartupWildbook.java src/main/java/org/ecocean/ia src/main/java/org/ecocean -g'*.java'" in /mnt/c/Wildbook-clean2
docs/plans/2026-05-18-c3-codex-review.md:2857:   424	     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c3-codex-review.md:2858:   425	     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c3-codex-review.md:2859:   426	     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c3-codex-review.md:2860:   427	     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c3-codex-review.md:2863:   430	    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c3-codex-review.md:2864:   431	        REGISTERED_OK,
docs/plans/2026-05-18-c3-codex-review.md:2865:   432	        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c3-codex-review.md:2866:   433	        NETWORK_FAIL,
docs/plans/2026-05-18-c3-codex-review.md:2867:   434	        RESPONSE_BAD,
docs/plans/2026-05-18-c3-codex-review.md:2910:src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-c3-codex-review.md:2911:src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-c3-codex-review.md:2913:src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-c3-codex-review.md:2914:src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
docs/plans/2026-05-18-c3-codex-review.md:2919:src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-c3-codex-review.md:2920:src/main/java/org/ecocean/StartupWildbook.java:827:                case REGISTERED_OK:
docs/plans/2026-05-18-c3-codex-review.md:2921:src/main/java/org/ecocean/StartupWildbook.java:828:                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-c3-codex-review.md:2922:src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
docs/plans/2026-05-18-c3-codex-review.md:2923:src/main/java/org/ecocean/StartupWildbook.java:835:                case RESPONSE_BAD:
docs/plans/2026-05-18-c3-codex-review.md:2935:src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-c3-codex-review.md:2936:src/main/java/org/ecocean/StartupWildbook.java:700:     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-c3-codex-review.md:2938:src/main/java/org/ecocean/StartupWildbook.java:722:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-c3-codex-review.md:2939:src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);
docs/plans/2026-05-18-c3-codex-review.md:2944:src/main/java/org/ecocean/StartupWildbook.java:811:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-c3-codex-review.md:2945:src/main/java/org/ecocean/StartupWildbook.java:827:                case REGISTERED_OK:
docs/plans/2026-05-18-c3-codex-review.md:2946:src/main/java/org/ecocean/StartupWildbook.java:828:                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-c3-codex-review.md:2947:src/main/java/org/ecocean/StartupWildbook.java:834:                case NETWORK_FAIL:
docs/plans/2026-05-18-c3-codex-review.md:2948:src/main/java/org/ecocean/StartupWildbook.java:835:                case RESPONSE_BAD:
docs/plans/2026-05-18-c3-codex-review.md:2952:src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
docs/plans/2026-05-18-c3-codex-review.md:2957:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:424:     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c3-codex-review.md:2958:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c3-codex-review.md:2959:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c3-codex-review.md:2960:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c3-codex-review.md:2961:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c3-codex-review.md:2962:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:431:        REGISTERED_OK,
docs/plans/2026-05-18-c3-codex-review.md:2963:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c3-codex-review.md:2964:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-c3-codex-review.md:2965:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
docs/plans/2026-05-18-c3-codex-review.md:2966:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2967:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2968:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c3-codex-review.md:2969:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2970:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c3-codex-review.md:2971:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2972:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:2973:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c3-codex-review.md:2974:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2975:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:2976:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2977:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:2978:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2979:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c3-codex-review.md:2982:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:424:     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c3-codex-review.md:2983:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:425:     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c3-codex-review.md:2984:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:426:     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c3-codex-review.md:2985:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:427:     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c3-codex-review.md:2986:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:430:    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c3-codex-review.md:2987:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:431:        REGISTERED_OK,
docs/plans/2026-05-18-c3-codex-review.md:2988:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:432:        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c3-codex-review.md:2989:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:433:        NETWORK_FAIL,
docs/plans/2026-05-18-c3-codex-review.md:2990:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:434:        RESPONSE_BAD,
docs/plans/2026-05-18-c3-codex-review.md:2991:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:688:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c3-codex-review.md:2992:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:689:        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:2993:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:692:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c3-codex-review.md:2994:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:693:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2995:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:699:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c3-codex-review.md:2996:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:701:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:2997:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:709:            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c3-codex-review.md:2998:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:715:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c3-codex-review.md:2999:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:716:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:3000:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:723:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:3001:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:724:            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c3-codex-review.md:3002:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:729:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c3-codex-review.md:3003:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:730:            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c3-codex-review.md:3004:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:732:        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c3-codex-review.md:3017:   696	     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
docs/plans/2026-05-18-c3-codex-review.md:3021:   700	     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
docs/plans/2026-05-18-c3-codex-review.md:3022:   701	     * fall out of the polling query.
docs/plans/2026-05-18-c3-codex-review.md:3028:   707	        if (dto == null) return;  // ineligible / already registered / parked
docs/plans/2026-05-18-c3-codex-review.md:3043:   722	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
docs/plans/2026-05-18-c3-codex-review.md:3044:   723	            iam.registerOneByDto(dto);
docs/plans/2026-05-18-c3-codex-review.md:3064:   743	     * Null cases: missing annotation, already registered, parked at max
docs/plans/2026-05-18-c3-codex-review.md:3066:   745	     * Ineligible annotations are parked here so they stop being polled.
docs/plans/2026-05-18-c3-codex-review.md:3088:   767	            // annotation under its current state, so park it.
docs/plans/2026-05-18-c3-codex-review.md:3097:   776	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
docs/plans/2026-05-18-c3-codex-review.md:3132:   811	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
docs/plans/2026-05-18-c3-codex-review.md:3148:   827	                case REGISTERED_OK:
docs/plans/2026-05-18-c3-codex-review.md:3149:   828	                case REGISTERED_ALREADY_PRESENT:
docs/plans/2026-05-18-c3-codex-review.md:3151:   830	                    // parked by a racing poller: stuck-at-attempts==MAX
docs/plans/2026-05-18-c3-codex-review.md:3155:   834	                case NETWORK_FAIL:
docs/plans/2026-05-18-c3-codex-review.md:3156:   835	                case RESPONSE_BAD:
docs/plans/2026-05-18-c3-codex-review.md:3159:   838	                        // Already parked by another path; do not increment past MAX.
docs/plans/2026-05-18-c2-codex-review.md:55:The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
docs/plans/2026-05-18-c2-codex-review.md:92:combines `_refresh` with a `_count` poll on an `ids` query.
docs/plans/2026-05-18-c2-codex-review.md:99:every `waitForVisibility` poll return zero — there's a diagnostic
docs/plans/2026-05-18-c2-codex-review.md:146:3. **Don't park silently.** Every parked annotation logs why with
docs/plans/2026-05-18-c2-codex-review.md:604:-    // The polling thread in StartupWildbook splits the work into:
docs/plans/2026-05-18-c2-codex-review.md:613:-     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c2-codex-review.md:614:-     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c2-codex-review.md:615:-     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c2-codex-review.md:616:-     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c2-codex-review.md:619:-    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c2-codex-review.md:620:-        REGISTERED_OK,
docs/plans/2026-05-18-c2-codex-review.md:621:-        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c2-codex-review.md:622:-        NETWORK_FAIL,
docs/plans/2026-05-18-c2-codex-review.md:623:-        RESPONSE_BAD,
docs/plans/2026-05-18-c2-codex-review.md:670:-        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c2-codex-review.md:717:-     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c2-codex-review.md:797:-    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c2-codex-review.md:798:-        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c2-codex-review.md:801:-            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c2-codex-review.md:802:-            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:808:-            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c2-codex-review.md:810:-            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:818:-            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c2-codex-review.md:824:-            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c2-codex-review.md:825:-            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:832:-            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c2-codex-review.md:833:-            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:838:-            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c2-codex-review.md:839:-            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c2-codex-review.md:841:-        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c2-codex-review.md:1455:+    // The polling thread in StartupWildbook splits the work into:
docs/plans/2026-05-18-c2-codex-review.md:1464:+     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c2-codex-review.md:1465:+     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c2-codex-review.md:1466:+     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c2-codex-review.md:1467:+     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c2-codex-review.md:1470:+    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c2-codex-review.md:1471:+        REGISTERED_OK,
docs/plans/2026-05-18-c2-codex-review.md:1472:+        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c2-codex-review.md:1473:+        NETWORK_FAIL,
docs/plans/2026-05-18-c2-codex-review.md:1474:+        RESPONSE_BAD,
docs/plans/2026-05-18-c2-codex-review.md:1521:+        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c2-codex-review.md:1568:+     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c2-codex-review.md:1648:+    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c2-codex-review.md:1649:+        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c2-codex-review.md:1652:+            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c2-codex-review.md:1653:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:1659:+            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c2-codex-review.md:1661:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:1669:+            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c2-codex-review.md:1675:+            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c2-codex-review.md:1676:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:1683:+            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c2-codex-review.md:1684:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:1689:+            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c2-codex-review.md:1690:+            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c2-codex-review.md:1692:+        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c2-codex-review.md:1852:+     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c2-codex-review.md:2191: M frontend/src/pages/Encounter/pollingHelpers.js
docs/plans/2026-05-18-c2-codex-review.md:2426:   812	     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c2-codex-review.md:2546:+     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c2-codex-review.md:2576:c6ffe5d20 fix(ia): WBIA registration poller c11 review follow-ups
docs/plans/2026-05-18-c2-codex-review.md:2644:docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-c2-codex-review.md:3544:+    // The polling thread in StartupWildbook splits the work into:
docs/plans/2026-05-18-c2-codex-review.md:3562:+     * REGISTERED_OK              - POST succeeded, ids match.
docs/plans/2026-05-18-c2-codex-review.md:3564:+     * REGISTERED_ALREADY_PRESENT - WBIA already knew the annotation; no POST.
docs/plans/2026-05-18-c2-codex-review.md:3566:+     * NETWORK_FAIL               - GET or POST threw / non-2xx.
docs/plans/2026-05-18-c2-codex-review.md:3568:+     * RESPONSE_BAD               - POST returned 200 but body shape was wrong
docs/plans/2026-05-18-c2-codex-review.md:3574:+    public enum WbiaRegisterOutcome {
docs/plans/2026-05-18-c2-codex-review.md:3576:+        REGISTERED_OK,
docs/plans/2026-05-18-c2-codex-review.md:3578:+        REGISTERED_ALREADY_PRESENT,
docs/plans/2026-05-18-c2-codex-review.md:3580:+        NETWORK_FAIL,
docs/plans/2026-05-18-c2-codex-review.md:3582:+        RESPONSE_BAD,
docs/plans/2026-05-18-c2-codex-review.md:3676:+        // rather than NPE-ing out and aborting the poll cycle.
docs/plans/2026-05-18-c2-codex-review.md:3770:+     * registered" in the polling thread's already-present check.
docs/plans/2026-05-18-c2-codex-review.md:3930:+    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
docs/plans/2026-05-18-c2-codex-review.md:3932:+        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c2-codex-review.md:3938:+            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
docs/plans/2026-05-18-c2-codex-review.md:3940:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:3952:+            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
docs/plans/2026-05-18-c2-codex-review.md:3956:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:3972:+            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
docs/plans/2026-05-18-c2-codex-review.md:3984:+            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
docs/plans/2026-05-18-c2-codex-review.md:3986:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:4000:+            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
docs/plans/2026-05-18-c2-codex-review.md:4002:+            return WbiaRegisterOutcome.NETWORK_FAIL;
docs/plans/2026-05-18-c2-codex-review.md:4012:+            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
docs/plans/2026-05-18-c2-codex-review.md:4014:+            return WbiaRegisterOutcome.RESPONSE_BAD;
docs/plans/2026-05-18-c2-codex-review.md:4018:+        return WbiaRegisterOutcome.REGISTERED_OK;
docs/plans/2026-05-18-c2-codex-review.md:4338:+     * tightened) so the ml-service v2 WBIA registration polling thread
docs/plans/2026-05-18-c2-codex-review.md:4544:    52	| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-c2-codex-review.md:4565:    90	If `reason != null`, park the annotation at `MAX_ATTEMPTS` (existing
docs/plans/2026-05-18-c2-codex-review.md:4566:    91	behavior — keeps the ineligible-park path consistent).
docs/plans/2026-05-18-c2-codex-review.md:4640:docs/plans/2026-05-18-wbia-image-registration-design.md:52:| `WildbookIAM.validMediaAsset(MediaAsset)` | exists, public; checks MIME, dims, and that `mediaAssetToUri` is non-null | Called in Phase A's eligibility check; if false, Phase A parks the annotation |
docs/plans/2026-05-18-c2-codex-review.md:4650:   262	- `registerOneByDto_phase0_get_fails_returns_network_fail` — mock GET to
docs/plans/2026-05-18-c2-codex-review.md:4651:   263	  throw IOException; verify NETWORK_FAIL returned without any POST.
docs/plans/2026-05-18-c2-codex-review.md:4652:   264	- `registerOneByDto_phase0_post_fails_returns_network_fail` — mock POST
docs/plans/2026-05-18-c2-codex-review.md:4653:   265	  to throw; verify NETWORK_FAIL; verify no annotation POST attempted.
docs/plans/2026-05-18-c2-codex-review.md:4654:   266	- `registerOneByDto_phase0_then_phase1_success_full_sequence` — both
docs/plans/2026-05-18-c2-codex-review.md:4655:   267	  succeed → REGISTERED_OK.
docs/plans/2026-05-18-c2-codex-review.md:4660:   272	mock MediaAsset; verify `validMediaAsset` failure parks the annotation.
docs/plans/2026-05-18-c2-codex-review.md:4668:   280	| `iaImageIdsStrict` GET fails | NETWORK_FAIL | Phase C increments attempts; retry |
docs/plans/2026-05-18-c2-codex-review.md:4669:   281	| Image POST fails | NETWORK_FAIL | Retry next tick |
docs/plans/2026-05-18-c2-codex-review.md:4670:   282	| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
docs/plans/2026-05-18-c2-codex-review.md:4679:   291	   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
docs/plans/2026-05-18-c2-codex-review.md:5150:- `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1` - The file was converted from LF to CRLF. `git ls-files --eol` shows `i/lf w/crlf`, and `git diff --check` reports trailing whitespace on every line. That is the main blocker: it pollutes the commit and may fail whitespace checks. Re-normalize this file to LF and keep only the semantic method diff.
docs/plans/2026-05-18-c2-codex-review.md:5175:- `src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:1` - The file was converted from LF to CRLF. `git ls-files --eol` shows `i/lf w/crlf`, and `git diff --check` reports trailing whitespace on every line. That is the main blocker: it pollutes the commit and may fail whitespace checks. Re-normalize this file to LF and keep only the semantic method diff.
src/main/java/org/ecocean/OpenSearch.java:467:    // Callers (typically waitForVisibility) follow with a visibility poll.
src/main/java/org/ecocean/OpenSearch.java:476:    // ml-service migration v2 (commit #7): bounded poll-and-wait until OpenSearch
src/main/java/org/ecocean/OpenSearch.java:487:    //     every poll return zero hits regardless of how long we wait.
src/main/java/org/ecocean/OpenSearch.java:489:    // Then polls a _count eligibility query with exponential backoff (start
src/main/java/org/ecocean/OpenSearch.java:516:                "— every poll will return zero hits regardless of wait time.");
src/main/java/org/ecocean/MetricsBot.java:59:    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
src/main/java/org/ecocean/MetricsBot.java:184:            // Taxonomy has to be treated differently because of past data pollution from Spotter app

 succeeded in 2287ms:
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:28:    public static void safeInvalidate(String context, String cacheName) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:13:class QueryCacheFactorySafeInvalidateTest {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:17:    @Test void safeInvalidate_invokesInvalidateByName_whenCacheNotNull()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:23:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:24:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:29:    @Test void safeInvalidate_swallowsNull_whenGetQueryCacheReturnsNull() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:32:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:34:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:38:    @Test void safeInvalidate_swallowsIoException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:45:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:47:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:52:    @Test void safeInvalidate_swallowsRuntimeException_fromGetQueryCache() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:56:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:58:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:62:    @Test void safeInvalidate_swallowsRuntimeException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:72:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:73:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:28:    public static void safeInvalidate(String context, String cacheName) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:34:                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:794:        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:873:        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:13:class QueryCacheFactorySafeInvalidateTest {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:17:    @Test void safeInvalidate_invokesInvalidateByName_whenCacheNotNull()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:23:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:24:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:29:    @Test void safeInvalidate_swallowsNull_whenGetQueryCacheReturnsNull() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:32:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:34:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:38:    @Test void safeInvalidate_swallowsIoException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:45:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:47:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:52:    @Test void safeInvalidate_swallowsRuntimeException_fromGetQueryCache() {
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:56:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:58:            QueryCacheFactory.safeInvalidate(CTX, NAME);
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:62:    @Test void safeInvalidate_swallowsRuntimeException_fromInvalidateByName()
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:72:            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
src/test/java/org/ecocean/cache/QueryCacheFactorySafeInvalidateTest.java:73:            QueryCacheFactory.safeInvalidate(CTX, NAME);

exec
/bin/bash -lc "sed -n '680,850p' src/main/java/org/ecocean/StartupWildbook.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,70p' src/main/java/org/ecocean/cache/QueryCacheFactory.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,240p' src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java" in /mnt/c/Wildbook-clean2
 succeeded in 379ms:
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

 succeeded in 394ms:
            shep.commitDBTransaction();
            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
        } catch (Exception ex) {
            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
                maId + ": " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Phase A/B/C split per Codex c11 fix-review.
     * <ul>
     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
     *   <li>Phase B: no Shepherd held; WBIA HTTP via
     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
     * </ul>
     * Ineligible annotations (missing media asset, missing acmId, fails
     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
     * fall out of the polling query.
     */
    private static void registerOneAnnotationWithWbia(String context, String annId) {
        // ---- Phase A: load DTO under a short transaction. ----
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
            loadWbiaRegisterDto(context, annId);
        if (dto == null) return;  // ineligible / already registered / parked

        // Bail out before starting the non-interruptible HTTP call if
        // shutdown was requested while Phase A was running. Otherwise we
        // would start a 300s WBIA POST that contextDestroyed can't cancel.
        if (Thread.currentThread().isInterrupted() ||
            wbiaRegisterExecutor == null) {
            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
                " (shutdown requested)");
            return;
        }

        // ---- Phase B: no Shepherd held; call WBIA. ----
        org.ecocean.ia.plugin.WildbookIAM iam =
            new org.ecocean.ia.plugin.WildbookIAM(context);
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
            iam.registerOneByDto(dto);

        // Skip Phase C if shutdown has been requested while Phase B ran.
        // RestClient is not interruptible mid-IO, so Phase B can outlive
        // awaitTermination; this prevents Phase C from racing the rest of
        // contextDestroyed's cleanup (Shepherd / IndexingManager / etc.).
        if (Thread.currentThread().isInterrupted() ||
            wbiaRegisterExecutor == null) {
            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
                " (shutdown requested)");
            return;
        }

        // ---- Phase C: persist outcome under a short transaction. ----
        persistWbiaRegisterResult(context, annId, outcome);
    }

    /**
     * Phase A. Returns a detached DTO ready for Phase B, or null if the
     * annotation does not need (or cannot get) a Phase-B network call.
     * Null cases: missing annotation, already registered, parked at max
     * attempts, or ineligible (missing media asset / acmId / bbox / etc.).
     * Ineligible annotations are parked here so they stop being polled.
     */
    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
        loadWbiaRegisterDto(String context, String annId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
        shep.beginDBTransaction();
        try {
            org.ecocean.Annotation ann = shep.getAnnotation(annId);
            if (ann == null) {
                shep.commitDBTransaction();
                return null;
            }
            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
                shep.commitDBTransaction();
                return null;
            }
            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                shep.commitDBTransaction();
                return null;
            }
            // Eligibility checks. Any failure here is permanent for this
            // annotation under its current state, so park it.
            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
            String reason = null;
            if (ma == null) reason = "missing media asset";
            else if (!Util.stringExists(ma.getAcmId())) reason = "media asset has no acmId";
            else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
            else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
                reason = "validForIdentification returned false (bbox/iaClass/etc.)";
            // Image-side eligibility for the new Phase 0 image-registration
            // path. Order mirrors sendMediaAssetsForceId in WildbookIAM (the
            // isValidImageForIA + validMediaAsset pair, in that order), so
            // the polling thread parks the same media assets the legacy
            // batch path would skip. Phase B trusts Phase A's verdict —
            // these are not re-checked against the DB after the Shepherd
            // closes. (Empty-match-prospects design Track 1 C5: WBIA
            // Phase 0 eligibility extension.)
            else if (ma.isValidImageForIA() != null && !ma.isValidImageForIA())
                reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
            else if (!org.ecocean.ia.plugin.WildbookIAM.validMediaAsset(ma))
                reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
            if (reason != null) {
                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
                    " (ineligible: " + reason + ")");
                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
                shep.commitDBTransaction();
                return null;
            }
            // Resolve the individual name now while the Shepherd is open;
            // Phase B has no DB access.
            String name = ann.findIndividualId(shep);
            // Copy bbox into a fresh array so the DTO is fully detached.
            int[] bb = ann.getBbox();
            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
            // Capture image-side fields for Phase 0 (image registration).
            // mediaAssetToUri returns null on null webURL — Phase A's
            // validMediaAsset check above already rejected that case, so a
            // null here would be a contract violation we'd want to see.
            String imageUri = org.ecocean.ia.plugin.WildbookIAM.mediaAssetToUri(ma);
            Double imageLatitude = ma.getLatitude();
            Double imageLongitude = ma.getLongitude();
            org.joda.time.DateTime dt = ma.getDateTime();
            Long imageDateTimeMillis = (dt == null) ? null : dt.getMillis();
            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
                    ann.getTheta(), ann.getIAClass(), name,
                    imageUri, imageLatitude, imageLongitude, imageDateTimeMillis);
            shep.commitDBTransaction();
            return dto;
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
                annId + ": " + ex);
            shep.rollbackDBTransaction();
            return null;
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Phase C. Re-loads the annotation and writes the outcome of the
     * Phase-B network call. On terminal-success outcomes the annotation
     * is marked registered; on retryable outcomes the attempts counter
     * is bumped and we WARN-log when we hit the abandonment threshold.
     */
    private static void persistWbiaRegisterResult(String context, String annId,
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
        shep.beginDBTransaction();
        try {
            org.ecocean.Annotation ann = shep.getAnnotation(annId);
            if (ann == null) {
                shep.commitDBTransaction();
                return;
            }
            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
                // Some other path flipped it while Phase B ran; respect that.
                shep.commitDBTransaction();
                return;
            }
            switch (outcome) {
                case REGISTERED_OK:
                case REGISTERED_ALREADY_PRESENT:

 succeeded in 491ms:
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of Phase 0 helpers introduced in C6:
 * {@link WildbookIAM#buildImageRequestMap} and
 * {@link WildbookIAM#validateImageResponse}. The network-bound
 * {@code registerImageIfMissing} entry point is exercised end-to-end
 * by the WBIA registration polling thread in a dev deployment.
 * (Empty-match-prospects design Track 1 C6.)
 */
class WildbookIAMImagePhase0Test {

    private static WbiaRegisterRequest sampleDtoWithImage() {
        return new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            12.34d, -56.78d, 1700000000000L);
    }

    // --- buildImageRequestMap --------------------------------------------

    @Test void buildImageRequestMapHasAllFiveLists() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        assertNotNull(map.get("image_uri_list"));
        assertNotNull(map.get("image_uuid_list"));
        assertNotNull(map.get("image_unixtime_list"));
        assertNotNull(map.get("image_gps_lat_list"));
        assertNotNull(map.get("image_gps_lon_list"));
        assertEquals(1, map.get("image_uri_list").size());
    }

    @Test void buildImageRequestMapPopulatesScalarFields() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        assertEquals("https://example.com/img.jpg",
            map.get("image_uri_list").get(0));
        assertEquals(12.34d, map.get("image_gps_lat_list").get(0));
        assertEquals(-56.78d, map.get("image_gps_lon_list").get(0));
    }

    @Test void buildImageRequestMapWrapsImageUuidInFancyForm() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        Object wrapped = map.get("image_uuid_list").get(0);
        assertTrue(wrapped instanceof JSONObject,
            "expected JSONObject fancy-uuid wrapper, got " +
            (wrapped == null ? "null" : wrapped.getClass().getName()));
        assertEquals("ma-acm-uuid-1",
            ((JSONObject) wrapped).optString("__UUID__"));
    }

    @Test void buildImageRequestMapConvertsMillisToUnixSeconds() {
        HashMap<String, ArrayList> map =
            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
        // 1700000000000ms = 1700000000s
        assertEquals(1700000000, map.get("image_unixtime_list").get(0));
    }

    @Test void buildImageRequestMapPassesNullForNullDateTime() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            12.34d, -56.78d, null);
        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
        assertNull(map.get("image_unixtime_list").get(0));
    }

    @Test void buildImageRequestMapPassesNullsForOptionalGps() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
            new int[] { 0, 0, 100, 100 },
            0.0d, "iaClass", "____",
            "https://example.com/img.jpg",
            null, null, 1700000000000L);
        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
        assertNull(map.get("image_gps_lat_list").get(0));
        assertNull(map.get("image_gps_lon_list").get(0));
    }

    // --- validateImageResponse -------------------------------------------

    private static JSONObject okResponse(String returnedUuid) {
        JSONObject jo = new JSONObject();
        JSONObject status = new JSONObject();
        status.put("success", true);
        jo.put("status", status);
        JSONArray arr = new JSONArray();
        JSONObject fancy = new JSONObject();
        fancy.put("__UUID__", returnedUuid);
        arr.put(fancy);
        jo.put("response", arr);
        return jo;
    }

    @Test void validateImageResponse_acceptsMatchingFancyUuid()
    throws IOException {
        WildbookIAM.validateImageResponse("ma-acm-uuid-1",
            okResponse("ma-acm-uuid-1"));
    }

    @Test void validateImageResponse_throwsOnNullResponse() {
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", null));
        assertTrue(ex.getMessage().contains("null"),
            "message should mention null: " + ex.getMessage());
    }

    @Test void validateImageResponse_throwsOnStatusSuccessFalse() {
        JSONObject resp = okResponse("ma-acm-uuid-1");
        resp.getJSONObject("status").put("success", false);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("success=false"));
    }

    @Test void validateImageResponse_throwsOnMissingResponseArray() {
        JSONObject resp = new JSONObject();
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("no response array"));
    }

    @Test void validateImageResponse_throwsOnArrayLengthMismatch() {
        JSONObject resp = okResponse("ma-acm-uuid-1");
        resp.getJSONArray("response").put(new JSONObject().put("__UUID__", "x"));
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("length 1"));
    }

    @Test void validateImageResponse_throwsOnUuidMismatch() {
        JSONObject resp = okResponse("different-uuid");
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("mismatch"));
        assertTrue(ex.getMessage().contains("ma-acm-uuid-1"));
        assertTrue(ex.getMessage().contains("different-uuid"));
    }

    @Test void validateImageResponse_throwsOnUndecodableResponseElement() {
        JSONObject resp = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("not_uuid_key", "x"));  // no __UUID__
        resp.put("response", arr);
        IOException ex = assertThrows(IOException.class,
            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
        assertTrue(ex.getMessage().contains("could not be decoded"));
    }
}

exec
/bin/bash -lc "sed -n '150,235p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '850,930p' src/main/java/org/ecocean/StartupWildbook.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "WBIA_REGISTER|wbiaRegister|WbiaRegistrationPoll" src/main/java/org/ecocean/StartupWildbook.java src/main/java/org/ecocean/Annotation.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '235,292p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
 succeeded in 490ms:
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

 succeeded in 465ms:
                case REGISTERED_ALREADY_PRESENT:
                    // Always honor a success outcome even if the row was
                    // parked by a racing poller: stuck-at-attempts==MAX
                    // would otherwise become permanent.
                    ann.setWbiaRegistered(Boolean.TRUE);
                    break;
                case NETWORK_FAIL:
                case RESPONSE_BAD:
                default:
                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                        // Already parked by another path; do not increment past MAX.
                        break;
                    }
                    ann.incrementWbiaRegisterAttempts();
                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
                            " attempts (last outcome=" + outcome + "); will not retry");
                    }
                    break;
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
                annId + ": " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext sContext = sce.getServletContext();
        String context = "context0";

        System.out.println("* StartupWildbook destroyed called for: " +
            servletContextInfo(sContext));

        if (CommonConfiguration.useSpotPatternRecognition(context)) {
            saveMatchGraph(sContext, context);
        }
        // Stop the WBIA poller first so it does not race teardown of
        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
        // flight. shutdownWbiaRegisterExecutor signals shutdown by
        // nulling the executor handle and waits up to 15s for in-flight
        // ticks; any tick still running after that gets shutdownNow().
        // The poll loop's interrupt/null checks make subsequent work bail.
        shutdownWbiaRegisterExecutor();
        AnnotationLite.cleanup(sContext, context);
        QueueUtil.cleanup();
        MetricsBot.cleanup();
        AcmIdBot.cleanup();
        IndexingManagerFactory.getIndexingManager().shutdown();
    }

    // ml-service migration v2 §commit #11 fix-pass. The polling executor
    // was previously held only in a local variable, which meant redeploys
    // could leak a zombie thread that re-armed on next contextInitialized.
    private static void shutdownWbiaRegisterExecutor() {
        ScheduledExecutorService ses = wbiaRegisterExecutor;
        if (ses == null) return;
        wbiaRegisterExecutor = null;
        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
        ses.shutdown();
        try {
            // 15s gives a healthy Phase B WBIA call time to finish so we
            // do not skip its Phase C unnecessarily. RestClient HTTP isn't
            // truly interruptible, so a hung call will still outlive this
            // wait; the per-id and pre-Phase-C interrupt checks in the
            // poller handle that case by bailing out before doing more DB
            // work.
            if (!ses.awaitTermination(15L, TimeUnit.SECONDS)) ses.shutdownNow();
        } catch (InterruptedException ie) {
            ses.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void createMatchGraph() {
        System.out.println("Entering createMatchGraph StartupWildbook method.");
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();

 succeeded in 480ms:
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

 succeeded in 546ms:
src/main/java/org/ecocean/StartupWildbook.java:49:    private static volatile ScheduledExecutorService wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:202:        // polling. Replaces v1's plan to use a separate "wbiaRegister"
src/main/java/org/ecocean/StartupWildbook.java:204:        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
src/main/java/org/ecocean/StartupWildbook.java:207:            startWbiaRegistrationPollingThread(context);
src/main/java/org/ecocean/StartupWildbook.java:429:     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
src/main/java/org/ecocean/StartupWildbook.java:433:     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
src/main/java/org/ecocean/StartupWildbook.java:438:     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
src/main/java/org/ecocean/StartupWildbook.java:439:     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
src/main/java/org/ecocean/StartupWildbook.java:443:     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
src/main/java/org/ecocean/StartupWildbook.java:445:    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
src/main/java/org/ecocean/StartupWildbook.java:446:    private static final int WBIA_REGISTER_BATCH_LIMIT = 50;
src/main/java/org/ecocean/StartupWildbook.java:447:    private static final long WBIA_REGISTER_POLL_SECONDS = 30L;
src/main/java/org/ecocean/StartupWildbook.java:449:    private static void startWbiaRegistrationPollingThread(final String context) {
src/main/java/org/ecocean/StartupWildbook.java:453:        if (wbiaRegisterExecutor != null) {
src/main/java/org/ecocean/StartupWildbook.java:455:                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
src/main/java/org/ecocean/StartupWildbook.java:458:        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
src/main/java/org/ecocean/StartupWildbook.java:462:                    Thread t = new Thread(r, "WbiaRegistrationPoll");
src/main/java/org/ecocean/StartupWildbook.java:470:                    runWbiaRegistrationPoll(context);
src/main/java/org/ecocean/StartupWildbook.java:476:                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
src/main/java/org/ecocean/StartupWildbook.java:480:        }, WBIA_REGISTER_POLL_SECONDS, WBIA_REGISTER_POLL_SECONDS, TimeUnit.SECONDS);
src/main/java/org/ecocean/StartupWildbook.java:481:        wbiaRegisterExecutor = ses;
src/main/java/org/ecocean/StartupWildbook.java:484:    private static void runWbiaRegistrationPoll(String context) {
src/main/java/org/ecocean/StartupWildbook.java:489:        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
src/main/java/org/ecocean/StartupWildbook.java:494:                "wbiaRegistered == false && wbiaRegisterAttempts < "
src/main/java/org/ecocean/StartupWildbook.java:495:                + WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:496:            q.setOrdering("wbiaRegisterAttempts ascending");
src/main/java/org/ecocean/StartupWildbook.java:497:            q.setRange(0, WBIA_REGISTER_BATCH_LIMIT);
src/main/java/org/ecocean/StartupWildbook.java:507:            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
src/main/java/org/ecocean/StartupWildbook.java:514:        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");
src/main/java/org/ecocean/StartupWildbook.java:523:                wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:524:                System.out.println("[INFO] WbiaRegistrationPoll: stopping mid-batch (interrupted)");
src/main/java/org/ecocean/StartupWildbook.java:713:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:714:            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase B for " + annId +
src/main/java/org/ecocean/StartupWildbook.java:730:            wbiaRegisterExecutor == null) {
src/main/java/org/ecocean/StartupWildbook.java:731:            System.out.println("[INFO] WbiaRegistrationPoll: skipping Phase C for " + annId +
src/main/java/org/ecocean/StartupWildbook.java:750:        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
src/main/java/org/ecocean/StartupWildbook.java:762:            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:788:                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
src/main/java/org/ecocean/StartupWildbook.java:790:                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
src/main/java/org/ecocean/StartupWildbook.java:817:            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
src/main/java/org/ecocean/StartupWildbook.java:835:        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
src/main/java/org/ecocean/StartupWildbook.java:859:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:864:                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
src/main/java/org/ecocean/StartupWildbook.java:865:                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
src/main/java/org/ecocean/StartupWildbook.java:866:                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
src/main/java/org/ecocean/StartupWildbook.java:873:            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
src/main/java/org/ecocean/StartupWildbook.java:909:        ScheduledExecutorService ses = wbiaRegisterExecutor;
src/main/java/org/ecocean/StartupWildbook.java:911:        wbiaRegisterExecutor = null;
src/main/java/org/ecocean/StartupWildbook.java:912:        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
src/main/java/org/ecocean/Annotation.java:67:    // wbiaRegistered drives the DB-backed background poller that tells WBIA
src/main/java/org/ecocean/Annotation.java:80:    protected Boolean wbiaRegistered;
src/main/java/org/ecocean/Annotation.java:82:    // Failed-attempt counter. Polling filters wbiaRegisterAttempts < MAX so
src/main/java/org/ecocean/Annotation.java:84:    protected int wbiaRegisterAttempts = 0;
src/main/java/org/ecocean/Annotation.java:336:    public Boolean getWbiaRegistered() { return wbiaRegistered; }
src/main/java/org/ecocean/Annotation.java:337:    public void setWbiaRegistered(Boolean b) { this.wbiaRegistered = b; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:341:    public boolean isWbiaRegistered() { return Boolean.TRUE.equals(this.wbiaRegistered); }
src/main/java/org/ecocean/Annotation.java:343:    public int getWbiaRegisterAttempts() { return wbiaRegisterAttempts; }
src/main/java/org/ecocean/Annotation.java:344:    public void setWbiaRegisterAttempts(int n) { this.wbiaRegisterAttempts = n; this.setVersion(); }
src/main/java/org/ecocean/Annotation.java:346:        this.wbiaRegisterAttempts++;

codex
The Phase A/B/C separation still holds: Phase B is network-only and Phase C is bounded by the existing 10-attempt counter. I’m now checking one subtle contract: which image identifier WBIA receives in legacy image registration versus the new DTO path.
exec
/bin/bash -lc 'nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getAcmId\\(|setAcmId\\(|getUUID\\(|setUUID\\(|protected .*acm|protected .*uuid|String acm|String uuid|class MediaAsset\" src/main/java/org/ecocean/media src/main/java/org/ecocean/acm src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/identity/IBEISIA.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'nl -ba src/main/java/org/ecocean/StartupWildbook.java' in /mnt/c/Wildbook-clean2
 succeeded in 415ms:
     1	package org.ecocean.ia.plugin;
     2	
     3	import java.io.IOException;
     4	import java.net.MalformedURLException;
     5	import java.net.URL;
     6	import java.security.InvalidKeyException;
     7	import java.security.NoSuchAlgorithmException;
     8	import java.util.ArrayList;
     9	import java.util.HashMap;
    10	import java.util.List;
    11	import javax.servlet.ServletContextEvent;
    12	import org.apache.commons.lang3.builder.ToStringBuilder;
    13	import org.ecocean.acm.AcmUtil;
    14	import org.ecocean.Annotation;
    15	import org.ecocean.cache.CachedQuery;
    16	import org.ecocean.cache.QueryCache;
    17	import org.ecocean.cache.QueryCacheFactory;
    18	import org.ecocean.ia.IA;
    19	import org.ecocean.ia.Task;
    20	import org.ecocean.media.*;
    21	import org.ecocean.RestClient;
    22	import org.ecocean.shepherd.core.Shepherd;
    23	import org.ecocean.Util;
    24	import org.joda.time.DateTime;
    25	import org.json.JSONArray;
    26	import org.json.JSONObject;
    27	
    28	// NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
    29	import org.ecocean.identity.IBEISIA;
    30	
    31	/*
    32	    Wildbook Image Analysis Module (IAM)
    33	    Initial stab at "plugin architecture" for "Image Analysis"
    34	 */
    35	public class WildbookIAM extends IAPlugin {
    36	    private String context = null;
    37	
    38	    public WildbookIAM() {
    39	        super();
    40	    }
    41	    public WildbookIAM(String context) {
    42	        super(context);
    43	        this.context = context;
    44	    }
    45	
    46	    @Override public boolean isEnabled() {
    47	        return true; // FIXME
    48	    }
    49	
    50	    @Override public boolean init(String context) {
    51	        this.context = context;
    52	        IA.log("WildbookIAM init() called on context " + context);
    53	        return true;
    54	    }
    55	
    56	    @Override public void startup(ServletContextEvent sce) {
    57	        // if we dont need identificaiton, no need to prime
    58	        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context,
    59	            "IBEISIADisableIdentification"));
    60	
    61	        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
    62	    }
    63	
    64	    @Override public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
    65	        final Task parentTask) {
    66	        return null;
    67	    }
    68	
    69	    @Override public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
    70	        final Task parentTask) {
    71	        return null;
    72	    }
    73	
    74	    // for now "primed" is stored in IBEISIA still.  <scratches head>
    75	    public boolean isPrimed() {
    76	        return IBEISIA.isIAPrimed();
    77	    }
    78	
    79	    public void prime() {
    80	        IA.log("INFO: WildbookIAM.prime(" + this.context +
    81	            ") called - NOTE this is deprecated and does nothing now.");
    82	        IBEISIA.setIAPrimed(true);
    83	    }
    84	
    85	/*
    86	    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
    87	    this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId values.  if we
    88	 * timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab the image over the network and
    89	       generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
    90	 */
    91	    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst)
    92	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
    93	        InvalidKeyException {
    94	        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
    95	
    96	        if (u == null)
    97	            throw new MalformedURLException(
    98	                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
    99	        URL url = new URL(u);
   100	        int batchSize = 30;
   101	        int numBatches = Math.round(mas.size() / batchSize + 1);
   102	
   103	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   104	        List<String> iaImageIds = new ArrayList<String>();
   105	        if (checkFirst) iaImageIds = iaImageIds();
   106	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   107	        map.put("image_uri_list", new ArrayList<JSONObject>());
   108	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   109	        map.put("image_unixtime_list", new ArrayList<Integer>());
   110	        map.put("image_gps_lat_list", new ArrayList<Double>());
   111	        map.put("image_gps_lon_list", new ArrayList<Double>());
   112	        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); // for rectifyMediaAssetIds below
   113	        int batchCt = 1;
   114	        JSONObject allRtn = new JSONObject();
   115	        allRtn.put("_batchSize", batchSize);
   116	        allRtn.put("_totalSize", mas.size());
   117	        JSONArray bres = new JSONArray();
   118	        for (int i = 0; i < mas.size(); i++) {
   119	            MediaAsset ma = mas.get(i);
   120	            if (iaImageIds.contains(ma.getAcmId())) continue;
   121	            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
   122	                IA.log(
   123	                    "WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: "
   124	                    + ma.getId());
   125	                continue;
   126	            }
   127	            if (!validMediaAsset(ma)) {
   128	                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
   129	                continue;
   130	            }
   131	            acmList.add(ma);
   132	            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
   133	            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
   134	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   135	            map.get("image_gps_lat_list").add(ma.getLatitude());
   136	            map.get("image_gps_lon_list").add(ma.getLongitude());
   137	            DateTime t = ma.getDateTime();
   138	            if (t == null) {
   139	                map.get("image_unixtime_list").add(null);
   140	            } else {
   141	                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
   142	            }
   143	            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
   144	                if (acmList.size() > 0) {
   145	                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() +
   146	                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
   147	                        " batches)");
   148	                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   149	                    System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
   150	                    List<String> acmIds = acmIdsFromResponse(rtn);
   151	                    if (acmIds == null) {
   152	                        IA.log(
   153	                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
   154	                            + rtn);
   155	                    } else {
   156	                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
   157	                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged +
   158	                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
   159	                    }
   160	                    bres.put(rtn);
   161	                    // initialize for next batch (if any)
   162	                    map.put("image_uri_list", new ArrayList<JSONObject>());
   163	                    map.put("image_uuid_list", new ArrayList<JSONObject>());
   164	                    map.put("image_unixtime_list", new ArrayList<Integer>());
   165	                    map.put("image_gps_lat_list", new ArrayList<Double>());
   166	                    map.put("image_gps_lon_list", new ArrayList<Double>());
   167	                    acmList = new ArrayList<MediaAsset>();
   168	                } else {
   169	                    bres.put("EMPTY BATCH");
   170	                }
   171	                batchCt++;
   172	            }
   173	        }
   174	        allRtn.put("batchResults", bres);
   175	        return allRtn;
   176	    }
   177	
   178	    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
   179	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   180	        InvalidKeyException {
   181	        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
   182	
   183	        if (u == null)
   184	            throw new MalformedURLException(
   185	                      "WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
   186	        URL url = new URL(u);
   187	        int batchSize = 30;
   188	        int numBatches = Math.round(mas.size() / batchSize + 1);
   189	
   190	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   191	        List<String> iaImageIds = new ArrayList<String>();
   192	        if (checkFirst) iaImageIds = iaImageIds();
   193	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   194	        map.put("image_uri_list", new ArrayList<JSONObject>());
   195	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   196	        map.put("image_unixtime_list", new ArrayList<Integer>());
   197	        map.put("image_gps_lat_list", new ArrayList<Double>());
   198	        map.put("image_gps_lon_list", new ArrayList<Double>());
   199	        int batchCt = 1;
   200	        JSONObject allRtn = new JSONObject();
   201	        allRtn.put("_batchSize", batchSize);
   202	        allRtn.put("_totalSize", mas.size());
   203	        JSONArray bres = new JSONArray();
   204	        for (int i = 0; i < mas.size(); i++) {
   205	            MediaAsset ma = mas.get(i);
   206	            if (iaImageIds.contains(ma.getAcmId())) continue;
   207	            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
   208	                IA.log(
   209	                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
   210	                    + ma.getId());
   211	                continue;
   212	            }
   213	            if (!validMediaAsset(ma)) {
   214	                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
   215	                continue;
   216	            }
   217	            map.get("image_uri_list").add(mediaAssetToUri(ma));
   218	            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
   219	            map.get("image_gps_lat_list").add(ma.getLatitude());
   220	            map.get("image_gps_lon_list").add(ma.getLongitude());
   221	            DateTime t = ma.getDateTime();
   222	            if (t == null) {
   223	                map.get("image_unixtime_list").add(null);
   224	            } else {
   225	                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
   226	            }
   227	            int sendSize = map.get("image_uri_list").size();
   228	            if ((i == (mas.size() - 1)) || ((i > 0) && (i % batchSize == 0))) { // end of all; or end of a batch
   229	                if (sendSize > 0) {
   230	                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
   231	                        " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches +
   232	                        " batches)");
   233	                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   234	                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
   235	/*
   236	                    if (acmIds == null) {
   237	                        IA.log(
   238	                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
   239	 + rtn);
   240	                    } else {
   241	                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
   242	                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
   243	                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
   244	                    }
   245	 */
   246	                    bres.put(rtn);
   247	                    // initialize for next batch (if any)
   248	                    map.put("image_uri_list", new ArrayList<JSONObject>());
   249	                    map.put("image_uuid_list", new ArrayList<JSONObject>());
   250	                    map.put("image_unixtime_list", new ArrayList<Integer>());
   251	                    map.put("image_gps_lat_list", new ArrayList<Double>());
   252	                    map.put("image_gps_lon_list", new ArrayList<Double>());
   253	                    // acmList = new ArrayList<MediaAsset>();
   254	                } else {
   255	                    bres.put("EMPTY BATCH");
   256	                }
   257	                batchCt++;
   258	            }
   259	        }
   260	        allRtn.put("batchResults", bres);
   261	        return allRtn;
   262	    }
   263	
   264	    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst,
   265	        Shepherd myShepherd)
   266	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   267	        InvalidKeyException {
   268	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   269	
   270	        if (u == null)
   271	            throw new MalformedURLException(
   272	                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
   273	        URL url = new URL(u);
   274	        int ct = 0;
   275	        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
   276	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   277	        List<String> iaAnnotIds = new ArrayList<String>();
   278	        if (checkFirst) iaAnnotIds = iaAnnotationIds();
   279	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   280	        map.put("image_uuid_list", new ArrayList<String>());
   281	        map.put("annot_species_list", new ArrayList<String>());
   282	        map.put("annot_bbox_list", new ArrayList<int[]>());
   283	        map.put("annot_name_list", new ArrayList<String>());
   284	        map.put("annot_theta_list", new ArrayList<Double>());
   285	
   286	        List<Annotation> acmList = new ArrayList<Annotation>(); // for rectifyAnnotationIds below
   287	        for (Annotation ann : anns) {
   288	            if (iaAnnotIds.contains(ann.getAcmId())) continue;
   289	            if (iaAnnotIds.contains(ann.getId())) continue;
   290	            if (ann.getMediaAsset() == null) {
   291	                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann +
   292	                    "; skipping!");
   293	                continue;
   294	            }
   295	            if (ann.getMediaAsset().getAcmId() == null) {
   296	                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann +
   297	                    " (MediaAsset id=" + ann.getMediaAsset().getId() +
   298	                    " not added to IA?); skipping!");
   299	                continue;
   300	            }
   301	            if (!IBEISIA.validForIdentification(ann)) {
   302	                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
   303	                continue;
   304	            }
   305	            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
   306	            if (iid == null) {
   307	                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " +
   308	                    ann.getMediaAsset() + " on " + ann + "; skipping!");
   309	                continue;
   310	            }
   311	            acmList.add(ann);
   312	            map.get("image_uuid_list").add(iid);
   313	            int[] bbox = ann.getBbox();
   314	            map.get("annot_bbox_list").add(bbox);
   315	            // yuck - IA class is not species
   316	            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
   317	            // better
   318	            map.get("annot_species_list").add(ann.getIAClass());
   319	
   320	            map.get("annot_theta_list").add(ann.getTheta());
   321	            String name = ann.findIndividualId(myShepherd);
   322	            map.get("annot_name_list").add((name == null) ? "____" : name);
   323	            ct++;
   324	        }
   325	        // myShepherd.rollbackDBTransaction();
   326	
   327	        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
   328	        if (ct < 1) return null; // null for "none to send" ?  is this cool?
   329	        System.out.println("sendAnnotations(): data -->\n" + map);
   330	        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   331	        System.out.println("sendAnnotations() -> " + rtn);
   332	        List<String> acmIds = acmIdsFromResponse(rtn);
   333	        if (acmIds == null) {
   334	            IA.log(
   335	                "WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: "
   336	                + rtn);
   337	        } else {
   338	            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds, myShepherd);
   339	            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged +
   340	                " Annotation(s) acmId(s) via rectifyAnnotationIds()");
   341	        }
   342	        return rtn;
   343	    }
   344	
   345	    public JSONObject sendAnnotationsForceId(ArrayList<Annotation> anns, boolean checkFirst,
   346	        Shepherd myShepherd)
   347	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
   348	        InvalidKeyException {
   349	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   350	
   351	        if (u == null)
   352	            throw new MalformedURLException(
   353	                      "WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
   354	        URL url = new URL(u);
   355	        int ct = 0;
   356	        // may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
   357	        // sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
   358	        List<String> iaAnnotIds = new ArrayList<String>();
   359	        if (checkFirst) iaAnnotIds = iaAnnotationIds();
   360	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   361	        map.put("image_uuid_list", new ArrayList<String>());
   362	        map.put("annot_uuid_list", new ArrayList<String>());
   363	        map.put("annot_species_list", new ArrayList<String>());
   364	        map.put("annot_bbox_list", new ArrayList<int[]>());
   365	        map.put("annot_name_list", new ArrayList<String>());
   366	        map.put("annot_theta_list", new ArrayList<Double>());
   367	        for (Annotation ann : anns) {
   368	            if (iaAnnotIds.contains(ann.getAcmId())) continue;
   369	            if (iaAnnotIds.contains(ann.getId())) continue;
   370	            if (ann.getMediaAsset() == null) {
   371	                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset for " +
   372	                    ann + "; skipping!");
   373	                continue;
   374	            }
   375	            if (!IBEISIA.validForIdentification(ann)) {
   376	                IA.log("WARNING: WildbookIAM.sendAnnotationsForceId() skipping invalid " + ann);
   377	                continue;
   378	            }
   379	            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
   380	            if (iid == null) {
   381	                IA.log(
   382	                    "WARNING: WildbookIAM.sendAnnotationsForceId() unable to find asset.acmId for "
   383	                    + ann.getMediaAsset() + " on " + ann + "; skipping!");
   384	                continue;
   385	            }
   386	            map.get("image_uuid_list").add(iid);
   387	            JSONObject aid = toFancyUUID(ann.getId());
   388	            map.get("annot_uuid_list").add(aid);
   389	            int[] bbox = ann.getBbox();
   390	            map.get("annot_bbox_list").add(bbox);
   391	            // yuck - IA class is not species
   392	            // map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
   393	            // better
   394	            map.get("annot_species_list").add(ann.getIAClass());
   395	
   396	            map.get("annot_theta_list").add(ann.getTheta());
   397	            String name = ann.findIndividualId(myShepherd);
   398	            map.get("annot_name_list").add((name == null) ? "____" : name);
   399	            ct++;
   400	        }
   401	        // myShepherd.rollbackDBTransaction();
   402	
   403	        IA.log("INFO: WildbookIAM.sendAnnotationsForceId() is sending " + ct);
   404	        if (ct < 1) return null; // null for "none to send" ?  is this cool?
   405	        System.out.println("sendAnnotationsForceId(): data -->\n" + map);
   406	        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   407	        System.out.println("sendAnnotationsForceId() -> " + rtn);
   408	        checkForcedIds(map.get("annot_uuid_list"), rtn.optJSONArray("response"));
   409	        return rtn;
   410	    }
   411	
   412	    // ------------------------------------------------------------------
   413	    // ml-service migration v2: no-Shepherd WBIA registration helpers.
   414	    //
   415	    // The polling thread in StartupWildbook splits the work into:
   416	    //   Phase A (write tx) - load DTO + close.
   417	    //   Phase B (no DB)    - call into the helpers below.
   418	    //   Phase C (write tx) - persist result.
   419	    // Phase B must not hold a Shepherd transaction across the WBIA call.
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
   455	        // Image-side fields. Phase 0 (image registration) sends these into
   456	        // WBIA's /api/image/json/ payload when the image isn't already
   457	        // known to WBIA. Captured under Shepherd in Phase A so Phase B has
   458	        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
   459	        public final String imageUri;            // double-encoded URL string; from mediaAssetToUri(ma)
   460	        public final Double imageLatitude;       // nullable; ma.getLatitude()
   461	        public final Double imageLongitude;      // nullable; ma.getLongitude()
   462	        public final Long   imageDateTimeMillis; // nullable epoch-ms; ma.getDateTime().getMillis()
   463	
   464	        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
   465	            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
   466	            String individualName, String imageUri, Double imageLatitude,
   467	            Double imageLongitude, Long imageDateTimeMillis) {
   468	            this.annotationId        = annotationId;
   469	            this.annotationAcmId     = annotationAcmId;
   470	            this.mediaAssetAcmId     = mediaAssetAcmId;
   471	            this.bbox                = bbox;
   472	            this.theta               = theta;
   473	            this.iaClass             = iaClass;
   474	            this.individualName      = individualName;
   475	            this.imageUri            = imageUri;
   476	            this.imageLatitude       = imageLatitude;
   477	            this.imageLongitude      = imageLongitude;
   478	            this.imageDateTimeMillis = imageDateTimeMillis;
   479	        }
   480	
   481	        /**
   482	         * Pre-C5 constructor preserved for backward-compatibility with
   483	         * test fixtures that don't exercise the Phase 0 image-registration
   484	         * path. Equivalent to the 11-arg constructor with all four
   485	         * image fields null. New production callers should use the
   486	         * 11-arg form.
   487	         */
   488	        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
   489	            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
   490	            String individualName) {
   491	            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
   492	                iaClass, individualName, null, null, null, null);
   493	        }
   494	    }
   495	
   496	    /**
   497	     * Strict variant of {@link #iaAnnotationIds(String)}: throws on
   498	     * fetch failure rather than returning an empty list. Phase B needs
   499	     * this so a network failure during the already-present check is
   500	     * not silently treated as "go ahead and POST".
   501	     *
   502	     * <p>Honors the 15-minute QueryCache the same way the lenient
   503	     * variant does, so a cache hit avoids the network entirely.</p>
   504	     */
   505	    public static List<String> iaAnnotationIdsStrict(String context) throws IOException {
   506	        String cacheName = "iaAnnotationIds";
   507	        // QueryCacheFactory.getQueryCache(context) can return null on a
   508	        // context that has never been initialized; treat that as "no cache"
   509	        // rather than NPE-ing out and aborting the poll cycle.
   510	        QueryCache qc = null;
   511	        try {
   512	            qc = QueryCacheFactory.getQueryCache(context);
   513	        } catch (Exception ex) {
   514	            // Defensive: cache factory init can fail; degrade to no-cache.
   515	        }
   516	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   517	            System.currentTimeMillis() <
   518	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   519	            try {
   520	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   521	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   522	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   523	                return parseAnnotationIdsArrayStrict(cached);
   524	            } catch (Exception ex) {
   525	                IA.log("WARNING: WildbookIAM.iaAnnotationIdsStrict() cache parse failed; refetching: "
   526	                    + ex.getMessage());
   527	            }
   528	        }
   529	        JSONArray jids;
   530	        try {
   531	            jids = apiGetJSONArray("/api/annot/json/", context);
   532	        } catch (Exception ex) {
   533	            throw new IOException("WBIA /api/annot/json/ fetch failed: " + ex.getMessage(), ex);
   534	        }
   535	        if (jids == null) throw new IOException("WBIA /api/annot/json/ returned null");
   536	        if (qc != null) {
   537	            try {
   538	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   539	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   540	                jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   541	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   542	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   543	                qc.addCachedQuery(cq);
   544	            } catch (Exception cacheEx) {
   545	                // Cache store failure is non-fatal; we still have the ids.
   546	            }
   547	        }
   548	        return parseAnnotationIdsArrayStrict(jids);
   549	    }
   550	
   551	    /**
   552	     * Strict element parser: throws IOException if any element is not a
   553	     * decodable fancy-UUID. The non-strict {@link #parseAnnotationIdsArray}
   554	     * skips/null-pads malformed entries, which is fine for legacy paths but
   555	     * would let a corrupt response masquerade as "annotation not yet
   556	     * registered" in the polling thread's already-present check.
   557	     */
   558	    static List<String> parseAnnotationIdsArrayStrict(JSONArray jids) throws IOException {
   559	        return parseFancyUuidArrayStrict(jids, "iaAnnotationIds");
   560	    }
   561	
   562	    static List<String> parseAnnotationIdsArray(JSONArray jids) {
   563	        List<String> ids = new ArrayList<String>();
   564	        if (jids == null) return ids;
   565	        for (int i = 0; i < jids.length(); i++) {
   566	            JSONObject jo = jids.optJSONObject(i);
   567	            if (jo != null) ids.add(fromFancyUUID(jo));
   568	        }
   569	        return ids;
   570	    }
   571	
   572	    /**
   573	     * Strict variant of {@link #iaImageIds(String)}: throws on fetch
   574	     * failure rather than returning an empty list. The new Phase 0 of
   575	     * the v2 WBIA registration polling thread needs this so a network
   576	     * failure during the "is the image already registered with WBIA?"
   577	     * check is not silently treated as "go ahead and POST".
   578	     *
   579	     * <p>Honors a 15-minute QueryCache under the key {@code "iaImageIds"}
   580	     * (same pattern as {@link #iaAnnotationIdsStrict(String)} sharing
   581	     * {@code "iaAnnotationIds"}). The lenient {@link #iaImageIds(String)}
   582	     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
   583	     */
   584	    public static List<String> iaImageIdsStrict(String context) throws IOException {
   585	        String cacheName = "iaImageIds";
   586	        // QueryCacheFactory.getQueryCache(context) can return null on a
   587	        // context that has never been initialized; treat that as "no cache"
   588	        // rather than NPE-ing out and aborting the poll cycle.
   589	        QueryCache qc = null;
   590	        try {
   591	            qc = QueryCacheFactory.getQueryCache(context);
   592	        } catch (Exception ex) {
   593	            // Defensive: cache factory init can fail; degrade to no-cache.
   594	        }
   595	        if (qc != null && qc.getQueryByName(cacheName) != null &&
   596	            System.currentTimeMillis() <
   597	            qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   598	            try {
   599	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   600	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   601	                JSONArray cached = Util.toggleJSONArray(jobj.getJSONArray("iaImageIds"));
   602	                return parseImageIdsArrayStrict(cached);
   603	            } catch (Exception ex) {
   604	                IA.log("WARNING: WildbookIAM.iaImageIdsStrict() cache parse failed; refetching: "
   605	                    + ex.getMessage());
   606	            }
   607	        }
   608	        JSONArray jids;
   609	        try {
   610	            jids = apiGetJSONArray("/api/image/json/", context);
   611	        } catch (Exception ex) {
   612	            throw new IOException("WBIA /api/image/json/ fetch failed: " + ex.getMessage(), ex);
   613	        }
   614	        if (jids == null) throw new IOException("WBIA /api/image/json/ returned null");
   615	        if (qc != null) {
   616	            try {
   617	                org.datanucleus.api.rest.orgjson.JSONObject jobj =
   618	                    new org.datanucleus.api.rest.orgjson.JSONObject();
   619	                jobj.put("iaImageIds", Util.toggleJSONArray(jids));
   620	                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   621	                cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   622	                qc.addCachedQuery(cq);
   623	            } catch (Exception cacheEx) {
   624	                // Cache store failure is non-fatal; we still have the ids.
   625	            }
   626	        }
   627	        return parseImageIdsArrayStrict(jids);
   628	    }
   629	
   630	    /**
   631	     * Strict element parser: throws IOException if any element is not a
   632	     * decodable fancy-UUID. Symmetric with {@link #parseAnnotationIdsArrayStrict};
   633	     * both delegate to {@link #parseFancyUuidArrayStrict(JSONArray, String)}
   634	     * with the appropriate label for error-message clarity.
   635	     */
   636	    static List<String> parseImageIdsArrayStrict(JSONArray jids) throws IOException {
   637	        return parseFancyUuidArrayStrict(jids, "iaImageIds");
   638	    }
   639	
   640	    /**
   641	     * Shared body for {@link #parseAnnotationIdsArrayStrict} and
   642	     * {@link #parseImageIdsArrayStrict}. The {@code label} is the
   643	     * source-array name (e.g. {@code "iaAnnotationIds"},
   644	     * {@code "iaImageIds"}); it appears in IOException messages so
   645	     * operators can tell which WBIA endpoint a malformed response
   646	     * came from.
   647	     *
   648	     * <p>(Empty-match-prospects design Track 1 C4: extracted from
   649	     * duplicated parser bodies on Codex's round-1 C2 review
   650	     * recommendation; the two named entry points stay so call sites
   651	     * grep cleanly.)</p>
   652	     */
   653	    static List<String> parseFancyUuidArrayStrict(JSONArray jids, String label)
   654	    throws IOException {
   655	        List<String> ids = new ArrayList<String>();
   656	        if (jids == null) return ids;
   657	        for (int i = 0; i < jids.length(); i++) {
   658	            JSONObject jo = jids.optJSONObject(i);
   659	            if (jo == null)
   660	                throw new IOException(label + " entry " + i + " is not a JSONObject");
   661	            String decoded = fromFancyUUID(jo);
   662	            if (decoded == null)
   663	                throw new IOException(label + " entry " + i + " could not be decoded: " + jo);
   664	            ids.add(decoded);
   665	        }
   666	        return ids;
   667	    }
   668	
   669	    /**
   670	     * Build the forced-id POST body for a single DTO. Pure function;
   671	     * factored out so unit tests can verify the request shape without
   672	     * a network round trip.
   673	     */
   674	    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
   675	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   676	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   677	        map.put("annot_uuid_list", new ArrayList<JSONObject>());
   678	        map.put("annot_species_list", new ArrayList<String>());
   679	        map.put("annot_bbox_list", new ArrayList<int[]>());
   680	        map.put("annot_name_list", new ArrayList<String>());
   681	        map.put("annot_theta_list", new ArrayList<Double>());
   682	        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
   683	        map.get("annot_uuid_list").add(toFancyUUID(dto.annotationId));
   684	        map.get("annot_species_list").add(dto.iaClass);
   685	        map.get("annot_bbox_list").add(dto.bbox);
   686	        map.get("annot_name_list").add(
   687	            (dto.individualName == null) ? "____" : dto.individualName);
   688	        map.get("annot_theta_list").add(dto.theta);
   689	        return map;
   690	    }
   691	
   692	    /**
   693	     * Validate a forced-id response. Throws on any contract violation
   694	     * (length mismatch, missing entry, id mismatch). Pure function.
   695	     */
   696	    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
   697	        if (resp == null) throw new IOException("null forced-id response");
   698	        if (resp.has("status")) {
   699	            JSONObject status = resp.optJSONObject("status");
   700	            if (status != null && status.has("success") && !status.optBoolean("success", true)) {
   701	                throw new IOException("forced-id response status.success=false: " + resp);
   702	            }
   703	        }
   704	        JSONArray respArr = resp.optJSONArray("response");
   705	        if (respArr == null) throw new IOException("no response array: " + resp);
   706	        if (respArr.length() != 1)
   707	            throw new IOException("expected response array length 1, got " + respArr.length());
   708	        JSONObject jid = respArr.optJSONObject(0);
   709	        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
   710	        String respId = fromFancyUUID(jid);
   711	        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
   712	        if (!respId.equals(sentAnnotId))
   713	            throw new IOException("forced-id mismatch: sent=" + sentAnnotId + " got=" + respId);
   714	    }
   715	
   716	    /**
   717	     * Phase B entry point. Sequence:
   718	     * <ol>
   719	     *   <li><b>Phase 0</b> (image registration, new in C6): GET the
   720	     *       WBIA-known image-ids; if the DTO's mediaAssetAcmId isn't
   721	     *       in the list, POST the image to /api/image/json/ and
   722	     *       invalidate the {@code "iaImageIds"} cache on success.
   723	     *       Without this, the legacy v2 routing path that skips
   724	     *       sendMediaAssets leaves WBIA unaware of the image, and
   725	     *       Phase 1's annotation POST returns HTTP 500 with
   726	     *       {@code image_uuid_list has invalid values [(0, None)]}.</li>
   727	     *   <li><b>Phase 1</b> (annotation registration, existing): the
   728	     *       already-present check, the forced-id POST, classification.</li>
   729	     * </ol>
   730	     *
   731	     * Does NOT touch any Shepherd or JDO state; callers must hand it
   732	     * a DTO that was pre-validated and detached in Phase A.
   733	     * (Empty-match-prospects design Track 1 C6.)
   734	     */
   735	    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
   736	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
   737	        // Phase 0: image registration. If the image isn't already at
   738	        // WBIA, POST it before attempting the annotation POST.
   739	        WbiaRegisterOutcome phase0 = registerImageIfMissing(dto);
   740	        if (phase0 != null && phase0 != WbiaRegisterOutcome.REGISTERED_OK &&
   741	            phase0 != WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT) {
   742	            // NETWORK_FAIL or RESPONSE_BAD; propagate so the polling
   743	            // thread retries / parks (Codex round-2 #6: no new outcome
   744	            // enum needed — Phase C log line distinguishes phase).
   745	            return phase0;
   746	        }
   747	        // Phase 1: annotation registration. Property check first since
   748	        // a missing property is a config error, not a network error.
   749	        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
   750	        if (u == null) {
   751	            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
   752	            return WbiaRegisterOutcome.NETWORK_FAIL;
   753	        }
   754	        List<String> known;
   755	        try {
   756	            known = iaAnnotationIdsStrict(context);
   757	        } catch (IOException ex) {
   758	            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
   759	                ex.getMessage());
   760	            return WbiaRegisterOutcome.NETWORK_FAIL;
   761	        }
   762	        // iaAnnotationIds returns ANNOTATION uuids (not image uuids), so
   763	        // only check the annotation's id and acmId here. Comparing against
   764	        // the media-asset's acmId is wrong - that would compare an image
   765	        // identifier against a list of annotation identifiers.
   766	        if (known.contains(dto.annotationId) ||
   767	            (Util.stringExists(dto.annotationAcmId) && known.contains(dto.annotationAcmId))) {
   768	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
   769	        }
   770	        URL url;
   771	        try {
   772	            url = new URL(u);
   773	        } catch (MalformedURLException ex) {
   774	            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
   775	            return WbiaRegisterOutcome.NETWORK_FAIL;
   776	        }
   777	        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
   778	        JSONObject rtn;
   779	        try {
   780	            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   781	        } catch (Exception ex) {
   782	            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
   783	            return WbiaRegisterOutcome.NETWORK_FAIL;
   784	        }
   785	        try {
   786	            validateForcedResponse(dto.annotationId, rtn);
   787	        } catch (IOException ex) {
   788	            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
   789	            return WbiaRegisterOutcome.RESPONSE_BAD;
   790	        }
   791	        // Annotation POST succeeded — invalidate the iaAnnotationIds cache
   792	        // so the next polling iteration sees this new annotation as already
   793	        // present and doesn't re-POST it (Codex round-1 C6 follow-up).
   794	        QueryCacheFactory.safeInvalidate(context, "iaAnnotationIds");
   795	        return WbiaRegisterOutcome.REGISTERED_OK;
   796	    }
   797	
   798	    /**
   799	     * Phase 0 of registerOneByDto: ensure the image referenced by
   800	     * {@code dto.mediaAssetAcmId} is registered with WBIA before the
   801	     * Phase 1 annotation POST.
   802	     *
   803	     * <p>Returns:</p>
   804	     * <ul>
   805	     *   <li>{@link WbiaRegisterOutcome#REGISTERED_ALREADY_PRESENT} if the
   806	     *       image is already in WBIA's id list (no POST done).</li>
   807	     *   <li>{@link WbiaRegisterOutcome#REGISTERED_OK} if the image was
   808	     *       not present and the POST succeeded. Also invalidates the
   809	     *       {@code "iaImageIds"} cache so the next caller sees the
   810	     *       image as already present.</li>
   811	     *   <li>{@link WbiaRegisterOutcome#NETWORK_FAIL} on missing
   812	     *       {@code IBEISIARestUrlAddImages} property, fetch failure,
   813	     *       or POST exception.</li>
   814	     *   <li>{@link WbiaRegisterOutcome#RESPONSE_BAD} if the POST
   815	     *       returned an unexpected response shape.</li>
   816	     * </ul>
   817	     *
   818	     * Package-visible so unit tests can cover it without going
   819	     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
   820	     */
   821	    WbiaRegisterOutcome registerImageIfMissing(WbiaRegisterRequest dto) {
   822	        if (dto == null) return WbiaRegisterOutcome.RESPONSE_BAD;
   823	        if (!Util.stringExists(dto.mediaAssetAcmId)) {
   824	            // Phase A required mediaAssetAcmId; a null here is a contract
   825	            // bug, not a state we should silently work around.
   826	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null mediaAssetAcmId in DTO");
   827	            return WbiaRegisterOutcome.RESPONSE_BAD;
   828	        }
   829	        if (!Util.stringExists(dto.imageUri)) {
   830	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() null/empty imageUri in DTO");
   831	            return WbiaRegisterOutcome.RESPONSE_BAD;
   832	        }
   833	        String urlProp = IA.getProperty(context, "IBEISIARestUrlAddImages");
   834	        if (urlProp == null) {
   835	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() property IBEISIARestUrlAddImages not set");
   836	            return WbiaRegisterOutcome.NETWORK_FAIL;
   837	        }
   838	        List<String> knownImages;
   839	        try {
   840	            knownImages = iaImageIdsStrict(context);
   841	        } catch (IOException ex) {
   842	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() iaImageIds fetch failed: " +
   843	                ex.getMessage());
   844	            return WbiaRegisterOutcome.NETWORK_FAIL;
   845	        }
   846	        if (knownImages.contains(dto.mediaAssetAcmId)) {
   847	            return WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT;
   848	        }
   849	        URL url;
   850	        try {
   851	            url = new URL(urlProp);
   852	        } catch (MalformedURLException ex) {
   853	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() malformed URL " + urlProp);
   854	            return WbiaRegisterOutcome.NETWORK_FAIL;
   855	        }
   856	        HashMap<String, ArrayList> map = buildImageRequestMap(dto);
   857	        JSONObject rtn;
   858	        try {
   859	            rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
   860	        } catch (Exception ex) {
   861	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() POST failed: " + ex.getMessage());
   862	            return WbiaRegisterOutcome.NETWORK_FAIL;
   863	        }
   864	        try {
   865	            validateImageResponse(dto.mediaAssetAcmId, rtn);
   866	        } catch (IOException ex) {
   867	            IA.log("WARNING: WildbookIAM.registerImageIfMissing() response invalid: " +
   868	                ex.getMessage());
   869	            return WbiaRegisterOutcome.RESPONSE_BAD;
   870	        }
   871	        // Image POST succeeded — invalidate so the next iteration sees
   872	        // this image as already present.
   873	        QueryCacheFactory.safeInvalidate(context, "iaImageIds");
   874	        return WbiaRegisterOutcome.REGISTERED_OK;
   875	    }
   876	
   877	    /**
   878	     * Build the WBIA /api/image/json/ POST body for a single DTO.
   879	     * Pure function; factored out so unit tests can verify the request
   880	     * shape without a network round trip. Mirrors the same field-set
   881	     * {@link #sendMediaAssetsForceId} populates per asset (uri, fancy
   882	     * uuid, unix-seconds time, lat, lon).
   883	     */
   884	    static HashMap<String, ArrayList> buildImageRequestMap(WbiaRegisterRequest dto) {
   885	        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
   886	        map.put("image_uri_list", new ArrayList<String>());
   887	        map.put("image_uuid_list", new ArrayList<JSONObject>());
   888	        map.put("image_unixtime_list", new ArrayList<Integer>());
   889	        map.put("image_gps_lat_list", new ArrayList<Double>());
   890	        map.put("image_gps_lon_list", new ArrayList<Double>());
   891	        map.get("image_uri_list").add(dto.imageUri);
   892	        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
   893	        if (dto.imageDateTimeMillis == null) {
   894	            map.get("image_unixtime_list").add(null);
   895	        } else {
   896	            // IA expects seconds since epoch, not milliseconds.
   897	            map.get("image_unixtime_list").add(
   898	                (int)Math.floor(dto.imageDateTimeMillis / 1000));
   899	        }
   900	        map.get("image_gps_lat_list").add(dto.imageLatitude);
   901	        map.get("image_gps_lon_list").add(dto.imageLongitude);
   902	        return map;
   903	    }
   904	
   905	    /**
   906	     * Validate a /api/image/json/ response. Mirrors
   907	     * {@link #validateForcedResponse} in shape: expect a length-1
   908	     * response array whose first element decodes via fromFancyUUID to
   909	     * exactly the {@code sentImageUuid} we sent.
   910	     */
   911	    static void validateImageResponse(String sentImageUuid, JSONObject resp)
   912	    throws IOException {
   913	        if (resp == null) throw new IOException("null image response");
   914	        if (resp.has("status")) {
   915	            JSONObject status = resp.optJSONObject("status");
   916	            if (status != null && status.has("success") &&
   917	                !status.optBoolean("success", true)) {
   918	                throw new IOException("image response status.success=false: " + resp);
   919	            }
   920	        }
   921	        JSONArray respArr = resp.optJSONArray("response");
   922	        if (respArr == null) throw new IOException("no response array: " + resp);
   923	        if (respArr.length() != 1)
   924	            throw new IOException("expected response array length 1, got " + respArr.length());
   925	        JSONObject jid = respArr.optJSONObject(0);
   926	        if (jid == null) throw new IOException("response[0] is not a JSONObject: " + respArr);
   927	        String respId = fromFancyUUID(jid);
   928	        if (respId == null) throw new IOException("response[0] could not be decoded: " + jid);
   929	        if (!respId.equals(sentImageUuid))
   930	            throw new IOException("image-id mismatch: sent=" + sentImageUuid + " got=" + respId);
   931	    }
   932	
   933	    private static void checkForcedIds(List<JSONObject> sentIds, JSONArray respArr)
   934	    throws IOException {
   935	        if ((sentIds == null) || (respArr == null))
   936	            throw new IOException("null arg(s) passed: " + sentIds + ", " + respArr);
   937	        if (sentIds.size() != respArr.length())
   938	            throw new IOException("args diff length: " + sentIds.size() + " != " +
   939	                    respArr.length());
   940	        for (int i = 0; i < sentIds.size(); i++) {
   941	            String sentId = fromFancyUUID(sentIds.get(i));
   942	            if (sentId == null)
   943	                throw new IOException("bad sentId at i=" + i + "; sentIds.get=" + sentIds.get(i));
   944	            JSONObject jid = respArr.optJSONObject(i);
   945	            if (jid == null) throw new IOException("no JSONObject at respArr[" + i + "]");
   946	            String respId = fromFancyUUID(jid);
   947	            if (respId == null) throw new IOException("bad respId at i=" + i + "; jid=" + jid);
   948	            if (!respId.equals(sentId))
   949	                throw new IOException("mismatch of ids at i=" + i + ": sentId=" + sentId +
   950	                        "; respId=" + respId);
   951	        }
   952	    }
   953	
   954	    public static List<String> acmIdsFromResponse(JSONObject rtn) {
   955	        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
   956	        List<String> ids = new ArrayList<String>();
   957	        for (int i = 0; i < rtn.getJSONArray("response").length(); i++) {
   958	            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
   959	                // IA returns null when it cant localize/etc, so we need to add this to keep array length the same
   960	                ids.add(null);
   961	            } else {
   962	                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
   963	            }
   964	        }
   965	        System.out.println("fromResponse ---> " + ids);
   966	        return ids;
   967	    }
   968	
   969	    // instance version of below (since context is known)
   970	    public List<String> iaAnnotationIds() {
   971	        return iaAnnotationIds(this.context);
   972	    }
   973	
   974	    // this fails "gracefully" with empty list if network fubar.  bad decision?
   975	    public static List<String> iaAnnotationIds(String context) {
   976	        List<String> ids = new ArrayList<String>();
   977	        JSONArray jids = null;
   978	        String cacheName = "iaAnnotationIds";
   979	
   980	        try {
   981	            QueryCache qc = QueryCacheFactory.getQueryCache(context);
   982	            if (qc.getQueryByName(cacheName) != null &&
   983	                System.currentTimeMillis() <
   984	                qc.getQueryByName(cacheName).getNextExpirationTimeout()) {
   985	                org.datanucleus.api.rest.orgjson.JSONObject jobj = Util.toggleJSONObject(
   986	                    qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
   987	                jids = Util.toggleJSONArray(jobj.getJSONArray("iaAnnotationIds"));
   988	            } else {
   989	                jids = apiGetJSONArray("/api/annot/json/", context);
   990	                if (jids != null) {
   991	                    org.datanucleus.api.rest.orgjson.JSONObject jobj =
   992	                        new org.datanucleus.api.rest.orgjson.JSONObject();
   993	                    jobj.put("iaAnnotationIds", Util.toggleJSONArray(jids));
   994	                    CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(jobj));
   995	                    cq.nextExpirationTimeout = System.currentTimeMillis() + (15 * 60 * 1000);
   996	                    qc.addCachedQuery(cq);
   997	                }
   998	            }
   999	        } catch (Exception ex) {
  1000	            ex.printStackTrace();
  1001	            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " +
  1002	                ex.toString());
  1003	        }
  1004	        if (jids != null) {
  1005	            try {
  1006	                for (int i = 0; i < jids.length(); i++) {
  1007	                    if (jids.optJSONObject(i) != null)
  1008	                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
  1009	                }
  1010	            } catch (Exception ex) {
  1011	                ex.printStackTrace();
  1012	                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
  1013	            }
  1014	        }
  1015	        return ids;
  1016	    }
  1017	
  1018	    // as above, but images
  1019	    public List<String> iaImageIds() {
  1020	        return iaImageIds(this.context);
  1021	    }
  1022	
  1023	    public static List<String> iaImageIds(String context) {
  1024	        List<String> ids = new ArrayList<String>();
  1025	        JSONArray jids = null;
  1026	
  1027	        try {
  1028	            jids = apiGetJSONArray("/api/image/json/", context);
  1029	        } catch (Exception ex) {
  1030	            ex.printStackTrace();
  1031	            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " +
  1032	                ex.toString());
  1033	        }
  1034	        if (jids != null) {
  1035	            try {
  1036	                for (int i = 0; i < jids.length(); i++) {
  1037	                    if (jids.optJSONObject(i) != null)
  1038	                        ids.add(fromFancyUUID(jids.getJSONObject(i)));
  1039	                }
  1040	            } catch (Exception ex) {
  1041	                ex.printStackTrace();
  1042	                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
  1043	            }
  1044	        }
  1045	        return ids;
  1046	    }
  1047	
  1048	    public JSONArray apiGetJSONArray(String urlSuffix)
  1049	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
  1050	        InvalidKeyException {
  1051	        return apiGetJSONArray(urlSuffix, this.context);
  1052	    }
  1053	
  1054	    public static JSONArray apiGetJSONArray(String urlSuffix, String context)
  1055	    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
  1056	        InvalidKeyException {
  1057	        URL u = IBEISIA.iaURL(context, urlSuffix);
  1058	        JSONObject rtn = RestClient.get(u);
  1059	
  1060	        if ((rtn == null) || (rtn.optJSONObject("status") == null) ||
  1061	            (rtn.optJSONArray("response") == null) ||
  1062	            !rtn.getJSONObject("status").optBoolean("success", false)) {
  1063	            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " +
  1064	                rtn);
  1065	            return null;
  1066	        }
  1067	        return rtn.getJSONArray("response");
  1068	    }
  1069	
  1070	    public static String fromFancyUUID(JSONObject u) {
  1071	        if (u == null) return null;
  1072	        return u.optString("__UUID__", null);
  1073	    }
  1074	
  1075	    public static JSONObject toFancyUUID(String u) {
  1076	        JSONObject j = new JSONObject();
  1077	
  1078	        j.put("__UUID__", u);
  1079	        return j;
  1080	    }
  1081	
  1082	    /**
  1083	     * Build the URL string WBIA expects in {@code image_uri_list}. The
  1084	     * double-encoded "?" pattern preserves filenames that contain "?" so
  1085	     * WBIA's HTTP layer doesn't truncate them at the query boundary.
  1086	     *
  1087	     * <p>Returns {@code null} when {@link MediaAsset#webURL()} returns
  1088	     * {@code null}. Promoted from {@code private Object} to
  1089	     * {@code public String} (and the leading-NPE on {@code curl.toString()}
  1090	     * tightened) so the ml-service v2 WBIA registration polling thread
  1091	     * can call it from Phase A while building the {@link WbiaRegisterRequest}
  1092	     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
  1093	     */
  1094	    public static String mediaAssetToUri(MediaAsset ma) {
  1095	        if (ma == null) return null;
  1096	        URL curl = ma.webURL();
  1097	        if (curl == null) return null;
  1098	        String urlStr = curl.toString();
  1099	        if (urlStr == null) return null;
  1100	        // THIS WILL BREAK if you need to append a query to the filename...
  1101	        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
  1102	        return urlStr.replaceAll("\\?", "%3F");
  1103	    }
  1104	
  1105	    // basically "should we send to IA?"
  1106	    public static boolean validMediaAsset(MediaAsset ma) {
  1107	        if (ma == null) return false;
  1108	        if (!ma.isMimeTypeMajor("image")) return false;
  1109	        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
  1110	        if (mediaAssetToUri(ma) == null) {
  1111	            System.out.println(
  1112	                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
  1113	                ma);
  1114	            return false;
  1115	        }
  1116	        return true;
  1117	    }
  1118	
  1119	    // this is used to give a string to IA for annot_species_list specifially
  1120	    // hence the term "IASpecies"
  1121	    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
  1122	        // NOTE: returning null here is probably "bad" btw....
  1123	        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
  1124	        if (enc == null) return null;
  1125	        String ts = enc.getTaxonomyString();
  1126	        if (ts == null) return null;
  1127	        return ts.replaceAll(" ", "_");
  1128	    }
  1129	
  1130	    public String toString() {
  1131	        return new ToStringBuilder(this)
  1132	                   .append("WildbookIAM IA Plugin")
  1133	                   .toString();
  1134	    }
  1135	}

 succeeded in 403ms:
     1	package org.ecocean;
     2	
     3	import java.io.File;
     4	import java.util.ArrayList;
     5	import java.util.List;
     6	import java.util.Properties;
     7	import java.net.URL;
     8	import javax.servlet.http.HttpServletRequest;
     9	import javax.servlet.ServletContext;
    10	import javax.servlet.ServletContextEvent;
    11	import javax.servlet.ServletContextListener;
    12	
    13	import org.ecocean.grid.GridManager;
    14	import org.ecocean.grid.MatchGraphCreationThread;
    15	import org.ecocean.grid.SharkGridThreadExecutorService;
    16	import org.ecocean.ia.IAPluginManager;
    17	import org.ecocean.identity.IBEISIA;
    18	import org.ecocean.media.AssetStore;
    19	import org.ecocean.media.AssetStoreConfig;
    20	import org.ecocean.media.LocalAssetStore;
    21	
    22	import org.ecocean.media.MediaAsset;
    23	
    24	import org.ecocean.queue.*;
    25	import org.ecocean.scheduled.WildbookScheduledTask;
    26	import org.ecocean.servlet.IAGateway;
    27	import org.ecocean.servlet.ServletUtilities;
    28	
    29	import java.util.concurrent.ThreadPoolExecutor;
    30	
    31	import org.ecocean.shepherd.core.Shepherd;
    32	import org.ecocean.shepherd.core.ShepherdProperties;
    33	import org.json.JSONObject;
    34	
    35	import java.io.IOException;
    36	import java.lang.Runnable;
    37	import java.util.concurrent.Executors;
    38	import java.util.concurrent.ScheduledExecutorService;
    39	import java.util.concurrent.TimeUnit;
    40	
    41	// This little collection of functions will be called on webapp start. static Its main purpose is to check that certain
    42	// global variables are initialized, and do so if necessary.
    43	
    44	public class StartupWildbook implements ServletContextListener {
    45	    // ml-service migration v2 §commit #11: handle to the WBIA-registration
    46	    // poller so contextDestroyed can shut it down cleanly. Without this the
    47	    // executor leaks across redeploys and a new poll thread starts on top
    48	    // of any zombie that survived undeploy.
    49	    private static volatile ScheduledExecutorService wbiaRegisterExecutor;
    50	
    51	    // this function is automatically run on webapp init
    52	    // it is attached via web.xml's <listener></listener>
    53	    public static void initializeWildbook(HttpServletRequest request, Shepherd myShepherd) {
    54	        ensureTomcatUserExists(myShepherd);
    55	        ensureAssetStoreExists(request, myShepherd);
    56	        ensureServerInfo(myShepherd);
    57	        ensureProfilePhotoKeywordExists(myShepherd);
    58	    }
    59	
    60	    /*
    61	        right now this *only* uses SERVER_URL env variable TODO: make this work in the more general case where it isnt e.g.
    62	           CommonConfiguration.checkServerInfo(myShepherd, request)
    63	     */
    64	    public static void ensureServerInfo(Shepherd myShepherd) {
    65	        String urlString = System.getenv("SERVER_URL");
    66	
    67	        if (urlString == null) return;
    68	        URL url = null;
    69	        try {
    70	            url = new URL(urlString);
    71	        } catch (java.net.MalformedURLException mal) {
    72	            System.out.println("StartupWildbook.ensureServerInfo failed on " + urlString + ": " +
    73	                mal.toString());
    74	            return;
    75	        }
    76	        JSONObject info = new JSONObject();
    77	        info.put("scheme", url.getProtocol());
    78	        info.put("serverName", url.getHost());
    79	        int port = url.getPort();
    80	        if (port > 0) info.put("serverPort", port);
    81	        info.put("contextPath", url.getFile());
    82	        // if (!isValidServerName(req.getServerName())) return false;  //dont update if we got wonky name like "localhost"
    83	        info.put("timestamp", System.currentTimeMillis());
    84	        info.put("context", myShepherd.getContext());
    85	        CommonConfiguration.setServerInfo(myShepherd, info);
    86	        System.out.println("StartupWildbook.ensureServerInfo updated server info to: " +
    87	            info.toString());
    88	        updateAssetStore(myShepherd); // piggyback here, thus we ensure we have a *good* SERVER_URL
    89	    }
    90	
    91	    // note: this (currently) is ONLY for docker-based deployment (hence reliance on SERVER_URL)
    92	    public static void updateAssetStore(Shepherd myShepherd) {
    93	        String urlString = System.getenv("SERVER_URL");
    94	
    95	        if (urlString == null) return;
    96	        AssetStore as = AssetStore.getDefault(myShepherd); // should exist either (or both) cuz of ensureAssetStore and/or sql
    97	        if (as == null) return;
    98	        AssetStoreConfig newConfig = new AssetStoreConfig();
    99	        newConfig.put("root", "/usr/local/tomcat/webapps/wildbook_data_dir"); // docker-specific
   100	        newConfig.put("webroot", urlString + "/wildbook_data_dir");
   101	        System.out.println("StartupWildbook.updateAssetStore() changing " + as + " config from [" +
   102	            as.getConfig() + "] to [" + newConfig + "]");
   103	        as.setConfig(newConfig);
   104	    }
   105	
   106	    public static void ensureTomcatUserExists(Shepherd myShepherd) {
   107	        List<User> users = myShepherd.getAllUsers();
   108	
   109	        if (users.size() == 0) {
   110	            System.out.println("");
   111	            String salt = ServletUtilities.getSalt().toHex();
   112	            String hashedPassword = ServletUtilities.hashAndSaltPassword("tomcat123", salt);
   113	            User newUser = new User("tomcat", hashedPassword, salt);
   114	            myShepherd.getPM().makePersistent(newUser);
   115	            System.out.println(
   116	                "StartupWildbook: No users found on Wildbook. Creating tomcat user account...");
   117	            myShepherd.commitDBTransaction();
   118	            List<Role> roles = myShepherd.getAllRoles();
   119	            if (roles.size() == 0) {
   120	                myShepherd.beginDBTransaction();
   121	                System.out.println("Creating tomcat roles...");
   122	
   123	                Role newRole1 = new Role("tomcat", "admin");
   124	                newRole1.setContext("context0");
   125	                myShepherd.getPM().makePersistent(newRole1);
   126	                Role newRole1a = new Role("tomcat", "orgAdmin");
   127	                newRole1a.setContext("context0");
   128	                myShepherd.getPM().makePersistent(newRole1a);
   129	                Role newRole2 = new Role("tomcat", "researcher");
   130	                newRole2.setContext("context0");
   131	                myShepherd.getPM().makePersistent(newRole2);
   132	                Role newRole3 = new Role("tomcat", "machinelearning");
   133	                newRole3.setContext("context0");
   134	                myShepherd.getPM().makePersistent(newRole3);
   135	                Role newRole5 = new Role("tomcat", "rest");
   136	                newRole5.setContext("context0");
   137	                myShepherd.getPM().makePersistent(newRole5);
   138	                myShepherd.commitDBTransaction();
   139	                System.out.println("Creating tomcat user account...");
   140	            }
   141	        }
   142	    }
   143	
   144	    public static void ensureAssetStoreExists(HttpServletRequest request, Shepherd myShepherd) {
   145	        String rootDir = request.getSession().getServletContext().getRealPath("/");
   146	        String dataDir = ServletUtilities.dataDir("context0", rootDir);
   147	        String urlLoc = request.getScheme() + "://" + CommonConfiguration.getURLLocation(request);
   148	        String dataUrl = urlLoc + "/wildbook_data_dir";
   149	
   150	        myShepherd.beginDBTransaction();
   151	        LocalAssetStore as = new LocalAssetStore("Default Local AssetStore",
   152	            new File(dataDir).toPath(), dataUrl, true);
   153	        myShepherd.getPM().makePersistent(as);
   154	        myShepherd.commitDBTransaction();
   155	    }
   156	
   157	    public static void ensureProfilePhotoKeywordExists(Shepherd myShepherd) {
   158	        int numKeywords = myShepherd.getNumKeywords();
   159	
   160	        if (numKeywords == 0) {
   161	            String readableName = "ProfilePhoto";
   162	            Keyword newword = new Keyword(readableName);
   163	            myShepherd.storeNewKeyword(newword);
   164	        }
   165	    }
   166	
   167	    // these get run with each tomcat startup/shutdown, if web.xml is configured accordingly.  see, e.g. https://stackoverflow.com/a/785802
   168	    public void contextInitialized(ServletContextEvent sce) {
   169	        ServletContext sContext = sce.getServletContext();
   170	        String context = "context0";
   171	
   172	        System.out.println(new org.joda.time.DateTime() + " ### StartupWildbook initialized for: " +
   173	            servletContextInfo(sContext));
   174	        if (skipInit(sce, null)) {
   175	            System.out.println("- SKIPPED initialization due to skipInit()");
   176	            return;
   177	        }
   178	        Setting.initialize(context);
   179	        // initialize the plugin (instances)
   180	        IAPluginManager.initPlugins(context);
   181	        // this should be handling all plugin startups
   182	        IAPluginManager.startup(sce);
   183	        // NOTE! this is whaleshark-specific (and maybe other spot-matchers?) ... should be off on any other trees
   184	        if (CommonConfiguration.useSpotPatternRecognition(context)) {
   185	            loadMatchGraphOrRebuild(sContext, context);
   186	        }
   187	        // TODO: set strategy for the following (genericize starting "all" consumers, make configurable, move to WildbookIAM.startup, move to plugins, or other)
   188	        startIAQueues(context);
   189	        MetricsBot.startServices(context);
   190	        AcmIdBot.startServices(context);
   191	        AnnotationLite.startup(sContext, context);
   192	        OpenSearch.unsetActiveIndexingBackground(); // since tomcat is just starting, these reset to false
   193	        OpenSearch.unsetActiveIndexingForeground();
   194	        OpenSearch.backgroundStartup(context);
   195	
   196	        try {
   197	            startWildbookScheduledTaskThread(context);
   198	        } catch (Exception e) {
   199	            e.printStackTrace();
   200	        }
   201	        // ml-service migration v2 §commit #11: DB-backed WBIA registration
   202	        // polling. Replaces v1's plan to use a separate "wbiaRegister"
   203	        // FileQueue with manual reconcile servlet. The polling thread reads
   204	        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
   205	        // survives JVM restarts without queue infrastructure.
   206	        try {
   207	            startWbiaRegistrationPollingThread(context);
   208	        } catch (Exception e) {
   209	            e.printStackTrace();
   210	        }
   211	        // ml-service migration v2 §commit #12: at-most-once delivery on the
   212	        // FileQueue means a JVM crash mid-detection can leave a MediaAsset
   213	        // in processing-mlservice forever. Once at startup, walk assets
   214	        // stuck past a threshold and re-enqueue them.
   215	        try {
   216	            runStaleMlServiceReconciliation(context);
   217	        } catch (Exception e) {
   218	            e.printStackTrace();
   219	        }
   220	        // initialize the MarkedIndividual names cache
   221	        // moved initNamesCache here
   222	        Shepherd myShepherd = new Shepherd(context);
   223	        myShepherd.setAction("MarkedIndividual.initNamesCache");
   224	        myShepherd.beginDBTransaction();
   225	        try {
   226	            boolean cached = org.ecocean.MarkedIndividual.initNamesCache(myShepherd);
   227	        } catch (Exception f) {
   228	            f.printStackTrace();
   229	        } finally { myShepherd.rollbackAndClose(); }
   230	    }
   231	
   232	    private void startIAQueues(String context) {
   233	        class IAMessageHandler extends QueueMessageHandler {
   234	            public boolean handler(String msg) {
   235	                try {
   236	                    org.ecocean.servlet.IAGateway.processQueueMessage(msg); // yeah we need to move this somewhere else...
   237	                } catch (Exception ex) {
   238	                    System.out.println("WARNING: IAMessageHandler processQueueMessage() threw " +
   239	                        ex.toString());
   240	                    ex.printStackTrace();
   241	                }
   242	                return true;
   243	            }
   244	        }
   245	        
   246	        //instructions on what to do if a message is published to the acmid queue
   247	        //which handles ACM ID registration for MediaAssets
   248	        class AcmIdMessageHandler extends QueueMessageHandler {
   249	            public boolean handler(String mediaAssetID) {
   250	            	Shepherd myShepherd = new Shepherd(context);
   251	            	myShepherd.setAction("AcmIdMessageHandler.handler."+mediaAssetID);
   252	            	myShepherd.beginDBTransaction();
   253	            	try {
   254	            		MediaAsset asset=myShepherd.getMediaAsset(mediaAssetID);
   255	            		if(asset!=null) {
   256			                ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
   257		            		fixMe.add(asset);
   258			                IBEISIA.sendMediaAssetsNew(fixMe, context);
   259			                myShepherd.updateDBTransaction();
   260	            		}
   261	            	}
   262	            	//RuntimeExceptions include an array of timeout and connectivitivity issues
   263	            	//indicating WBIA may be overloaded or restarting
   264	            	//therefore this exception includes a simple sleep function to pause ACM ID registration
   265	            	//to give WBIA time to restart or be less busy.
   266	            	//This implementation is temporary until ACM ID registration is removed entirely
   267	                catch (java.lang.RuntimeException ex) {
   268	                    System.out.println("\r\n\r\nWARNING: AcmIdMessageHandler processQueueMessage() threw " +
   269	                        ex.toString()+"\r\n\r\n");
   270	                    ex.printStackTrace();
   271	                    
   272	                    long timeoutMilliseconds=60000;
   273	                    Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
   274	                    if(props!=null && props.getProperty("timeoutMilliseconds")!=null) {
   275	                    	String millis = props.getProperty("timeoutMilliseconds");
   276	                    	Long millisAsLong = Long.getLong(millis);
   277	                    	if(millisAsLong!=null)timeoutMilliseconds=millisAsLong.longValue();
   278	                    }
   279	                    
   280	                    try {
   281	                    	Thread.sleep(timeoutMilliseconds);
   282	                    	Queue acmIdQueue=IAGateway.getAcmIdQueue(context);
   283	                    	acmIdQueue.publish(mediaAssetID);
   284	                    }
   285	                    catch(Exception ioe) {
   286	                    	ioe.printStackTrace();
   287	                    }
   288	                    return false;
   289	                }
   290	                catch (Exception ex) {
   291	                    System.out.println("\r\n\r\nWARNING: AcmIdMessageHandler processQueueMessage() threw " +
   292	                        ex.toString()+"\r\n\r\n");
   293	                    ex.printStackTrace();
   294	                    
   295	                    try {
   296	                    	Queue acmIdQueue=IAGateway.getAcmIdQueue(context);
   297	                    	acmIdQueue.publish(mediaAssetID);
   298	                    }
   299	                    catch(Exception ioe) {
   300	                    	ioe.printStackTrace();
   301	                    }
   302	                    return false;
   303	                }
   304	            	finally {
   305	            		myShepherd.rollbackAndClose();
   306	            	}
   307	                return true;
   308	            }
   309	        }
   310	
   311	        class IACallbackMessageHandler extends QueueMessageHandler {
   312	            public boolean handler(String msg) {
   313	                try {
   314	                    org.ecocean.servlet.IAGateway.processCallbackQueueMessage(msg); // yeah we need to move this somewhere else...
   315	                } catch (Exception ex) {
   316	                    System.out.println(
   317	                        "WARNING: IACallbackMessageHandler processCallbackQueueMessage() threw " +
   318	                        ex.toString());
   319	                    ex.printStackTrace();
   320	                }
   321	                return true;
   322	            }
   323	        }
   324	        if (!IBEISIA.iaEnabled()) {
   325	            System.out.println("+ INFO: IA not enabled; IA queue service not started");
   326	            return;
   327	        }
   328	        Queue queue = null;
   329	        try {
   330	            queue = QueueUtil.getBest(context, "IA");
   331	        } catch (IOException ex) {
   332	            System.out.println("+ ERROR: IA queue startup exception: " + ex.toString());
   333	        }
   334	        Queue queueCallback = null;
   335	        try {
   336	            queueCallback = QueueUtil.getBest(context, "IACallback");
   337	        } catch (IOException ex) {
   338	            System.out.println("+ ERROR: IACallback queue startup exception: " + ex.toString());
   339	        }
   340	        Queue detectionQ = null;
   341	        try {
   342	            detectionQ = QueueUtil.getBest(context, "detection");
   343	        } catch (IOException ex) {
   344	            System.out.println("+ ERROR: detection queue startup exception: " + ex.toString());
   345	        }
   346	        //MediaAsset ACM ID registration queue
   347	        Queue acmidQ = null;
   348	        try {
   349	            acmidQ = QueueUtil.getBest(context, "acmid");
   350	        } catch (IOException ex) {
   351	            System.out.println("+ ERROR: acmid queue startup exception: " + ex.toString());
   352	        }
   353	        if ((queue == null) || (queueCallback == null) || (detectionQ == null) || (acmidQ == null)) {
   354	            System.out.println("+ WARNING: IA queue service(s) NOT started");
   355	            return;
   356	        }
   357	        IAMessageHandler qh = new IAMessageHandler();
   358	        try {
   359	            queue.consume(qh);
   360	            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() started on " +
   361	                queue.toString());
   362	        } catch (IOException iox) {
   363	            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() FAILED on " +
   364	                queue.toString() + ": " + iox.toString());
   365	        }
   366	        IACallbackMessageHandler qh2 = new IACallbackMessageHandler();
   367	        try {
   368	            queueCallback.consume(qh2);
   369	            System.out.println(
   370	                "+ StartupWildbook.startIAQueues() queueCallback.consume() started on " +
   371	                queueCallback.toString());
   372	        } catch (IOException iox) {
   373	            System.out.println(
   374	                "+ StartupWildbook.startIAQueues() queueCallback.consume() FAILED on " +
   375	                queueCallback.toString() + ": " + iox.toString());
   376	        }
   377	        IAMessageHandler qh3 = new IAMessageHandler();
   378	        try {
   379	            detectionQ.consume(qh3);
   380	            System.out.println(
   381	                "+ StartupWildbook.startIAQueues() detectionQ.consume() started on " +
   382	                detectionQ.toString());
   383	        } catch (IOException iox) {
   384	            System.out.println("+ StartupWildbook.startIAQueues() detectionQ.consume() FAILED on " +
   385	                detectionQ.toString() + ": " + iox.toString());
   386	        }
   387	        //ACM ID queue handler
   388	        AcmIdMessageHandler qh4 = new AcmIdMessageHandler();
   389	        try {
   390	            acmidQ.consume(qh4);
   391	            System.out.println(
   392	                "+ StartupWildbook.startIAQueues() acmidQ.consume() started on " +
   393	                acmidQ.toString());
   394	        } catch (IOException iox) {
   395	            System.out.println("+ StartupWildbook.startIAQueues() acmidQ.consume() FAILED on " +
   396	                acmidQ.toString() + ": " + iox.toString());
   397	        }
   398	    }
   399	
   400	    private static void startWildbookScheduledTaskThread(String context) {
   401	        System.out.println("STARTING: StartupWildbook.startWildbookScheduledTaskThread()");
   402	        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
   403	        ses.scheduleAtFixedRate(new Runnable() {
   404	            @Override public void run() {
   405	                System.out.println("[INFO]: checking for scheduled tasks to execute...");
   406	                Shepherd myShepherd = new Shepherd(context);
   407	                myShepherd.setAction("WildbookScheduledTaskThread");
   408	                try {
   409	                    ArrayList<WildbookScheduledTask> scheduledTasks =
   410	                    myShepherd.getAllIncompleteWildbookScheduledTasks();
   411	                    for (WildbookScheduledTask scheduledTask : scheduledTasks) {
   412	                        if (scheduledTask.isTaskEligibleForExecution()) {
   413	                            scheduledTask.execute(myShepherd);
   414	                        }
   415	                    }
   416	                } catch (Exception e) {
   417	                    myShepherd.rollbackAndClose();
   418	                    e.printStackTrace();
   419	                }
   420	                myShepherd.closeDBTransaction();
   421	            }
   422	        }, 0, 1, TimeUnit.HOURS);
   423	    }
   424	
   425	    /**
   426	     * ml-service migration v2 §commit #11. Background polling thread that
   427	     * registers ml-service-created annotations with WBIA so HotSpotter is
   428	     * available on demand for them. State is on the Annotation row itself
   429	     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
   430	     * queue or reconcile servlet is needed.
   431	     *
   432	     * <p>Per cycle (~30s): query annotations with
   433	     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
   434	     * up to a small batch limit. For each, call
   435	     * {@link org.ecocean.ia.plugin.WildbookIAM#sendAnnotationsForceId} in a
   436	     * per-annotation Shepherd transaction (so one slow WBIA call blocks
   437	     * only one slot, not the entire batch). On success: set
   438	     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
   439	     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
   440	     *
   441	     * <p>Legacy annotations are excluded from the query because the DDL
   442	     * migration in {@code archive/sql/ml_service_idempotency.sql} backfills
   443	     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
   444	     */
   445	    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
   446	    private static final int WBIA_REGISTER_BATCH_LIMIT = 50;
   447	    private static final long WBIA_REGISTER_POLL_SECONDS = 30L;
   448	
   449	    private static void startWbiaRegistrationPollingThread(final String context) {
   450	        // Refuse to start a second poller if one is already running; this
   451	        // also matters when contextInitialized fires more than once for
   452	        // the same JVM (e.g., context reload).
   453	        if (wbiaRegisterExecutor != null) {
   454	            System.out.println(
   455	                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
   456	            return;
   457	        }
   458	        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
   459	        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
   460	            new java.util.concurrent.ThreadFactory() {
   461	                @Override public Thread newThread(Runnable r) {
   462	                    Thread t = new Thread(r, "WbiaRegistrationPoll");
   463	                    t.setDaemon(true);
   464	                    return t;
   465	                }
   466	            });
   467	        ses.scheduleAtFixedRate(new Runnable() {
   468	            @Override public void run() {
   469	                try {
   470	                    runWbiaRegistrationPoll(context);
   471	                } catch (Throwable t) {
   472	                    // Catch Throwable here: ScheduledExecutorService silently
   473	                    // stops re-firing the task on any uncaught exception.
   474	                    // We want the thread to keep ticking through transient
   475	                    // failures.
   476	                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
   477	                    t.printStackTrace();
   478	                }
   479	            }
   480	        }, WBIA_REGISTER_POLL_SECONDS, WBIA_REGISTER_POLL_SECONDS, TimeUnit.SECONDS);
   481	        wbiaRegisterExecutor = ses;
   482	    }
   483	
   484	    private static void runWbiaRegistrationPoll(String context) {
   485	        // Phase 1: query the pending list (Shepherd open, no network). Capture
   486	        // annotation IDs and release before any WBIA calls.
   487	        java.util.List<String> pendingIds = new ArrayList<String>();
   488	        Shepherd shep = new Shepherd(context);
   489	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
   490	        shep.beginDBTransaction();
   491	        try {
   492	            javax.jdo.Query q = shep.getPM().newQuery(
   493	                org.ecocean.Annotation.class,
   494	                "wbiaRegistered == false && wbiaRegisterAttempts < "
   495	                + WBIA_REGISTER_MAX_ATTEMPTS);
   496	            q.setOrdering("wbiaRegisterAttempts ascending");
   497	            q.setRange(0, WBIA_REGISTER_BATCH_LIMIT);
   498	            @SuppressWarnings("unchecked")
   499	            java.util.List<org.ecocean.Annotation> pending =
   500	                (java.util.List<org.ecocean.Annotation>) q.execute();
   501	            if (pending != null) {
   502	                for (org.ecocean.Annotation a : pending) pendingIds.add(a.getId());
   503	            }
   504	            q.closeAll();
   505	            shep.commitDBTransaction();
   506	        } catch (Exception ex) {
   507	            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
   508	            shep.rollbackDBTransaction();
   509	        } finally {
   510	            shep.closeDBTransaction();
   511	        }
   512	
   513	        if (pendingIds.isEmpty()) return;
   514	        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");
   515	
   516	        // Phase 2: per-annotation registration. Each runs in its own short
   517	        // Shepherd tx so a slow WBIA call blocks only that one slot.
   518	        // The interrupted-check makes shutdownNow() effective at bounding
   519	        // the per-tick work even if the in-flight Phase B HTTP call ran
   520	        // past awaitTermination.
   521	        for (String annId : pendingIds) {
   522	            if (Thread.currentThread().isInterrupted() ||
   523	                wbiaRegisterExecutor == null) {
   524	                System.out.println("[INFO] WbiaRegistrationPoll: stopping mid-batch (interrupted)");
   525	                return;
   526	            }
   527	            registerOneAnnotationWithWbia(context, annId);
   528	        }
   529	    }
   530	
   531	    /**
   532	     * ml-service migration v2 §commit #12. Once-at-startup pass that
   533	     * detects MediaAssets stuck in {@code processing-mlservice} past a
   534	     * threshold (worker presumably died mid-detection due to the
   535	     * at-most-once FileQueue semantics) and re-enqueues them through
   536	     * the normal routing layer.
   537	     *
   538	     * <p>Safe under any active worker because:</p>
   539	     * <ul>
   540	     *   <li>The re-check inside reconcileOneStaleAsset uses the fresh
   541	     *       Shepherd's current state; if another worker has already
   542	     *       progressed the asset, the status will no longer be
   543	     *       {@code processing-mlservice} and the reconciler skips.</li>
   544	     *   <li>MlServiceProcessor's Phase 4 idempotency check (composite of
   545	     *       mediaAsset + predictModelId + bboxKey + thetaKey) prevents
   546	     *       duplicate annotation creation if the dead worker had already
   547	     *       persisted some results.</li>
   548	     *   <li>The reconciler intentionally does NOT bump REVISION after a
   549	     *       successful re-enqueue, because doing so from the stale
   550	     *       managed MediaAsset instance could overwrite progress made by
   551	     *       a fast queue consumer between enqueue and commit. REVISION
   552	     *       advances naturally when MlServiceProcessor's Phase 1 calls
   553	     *       setDetectionStatus on the picked-up job. A restart that
   554	     *       happens between enqueue and consumer pickup can re-enqueue
   555	     *       a duplicate job; Phase 4 idempotency (see previous bullet)
   556	     *       bounds the impact to wasted work, not data corruption.</li>
   557	     * </ul>
   558	     *
   559	     * <p>Threshold default: 1 hour. Longer than any healthy detection
   560	     * job's worst-case duration; short enough that operators don't wait
   561	     * days for recovery.</p>
   562	     */
   563	    private static final long STALE_MLSERVICE_THRESHOLD_MS = 60L * 60L * 1000L;
   564	
   565	    private static void runStaleMlServiceReconciliation(String context) {
   566	        System.out.println(
   567	            "STARTING: StartupWildbook.runStaleMlServiceReconciliation()");
   568	        long revisionCutoff = System.currentTimeMillis() - STALE_MLSERVICE_THRESHOLD_MS;
   569	        java.util.List<String> staleIds = fetchStaleMlServiceAssetIds(context, revisionCutoff);
   570	        if (staleIds.isEmpty()) {
   571	            System.out.println(
   572	                "[INFO] StaleMlServiceReconciliation: no stuck assets older than threshold");
   573	            return;
   574	        }
   575	        System.out.println("[INFO] StaleMlServiceReconciliation: " + staleIds.size() +
   576	            " stuck assets older than " + STALE_MLSERVICE_THRESHOLD_MS + "ms");
   577	        for (String maId : staleIds) {
   578	            reconcileOneStaleAsset(context, maId);
   579	        }
   580	    }
   581	
   582	    private static java.util.List<String> fetchStaleMlServiceAssetIds(String context,
   583	        long revisionCutoff) {
   584	        java.util.List<String> ids = new ArrayList<String>();
   585	        Shepherd shep = new Shepherd(context);
   586	        shep.setAction("StartupWildbook.StaleMlServiceReconciliation.fetch");
   587	        shep.beginDBTransaction();
   588	        try {
   589	            javax.jdo.Query q = shep.getPM().newQuery(
   590	                org.ecocean.media.MediaAsset.class,
   591	                "detectionStatus == 'processing-mlservice' && revision < "
   592	                + revisionCutoff);
   593	            // Oldest-first so the most-stuck assets get recovered before
   594	            // newer ones in the same batch.
   595	            q.setOrdering("revision ascending");
   596	            @SuppressWarnings("unchecked")
   597	            java.util.List<org.ecocean.media.MediaAsset> stale =
   598	                (java.util.List<org.ecocean.media.MediaAsset>) q.execute();
   599	            if (stale != null) {
   600	                for (org.ecocean.media.MediaAsset ma : stale) {
   601	                    ids.add(String.valueOf(ma.getId()));
   602	                }
   603	            }
   604	            q.closeAll();
   605	            shep.commitDBTransaction();
   606	        } catch (Exception ex) {
   607	            System.out.println(
   608	                "WARN: StaleMlServiceReconciliation fetch failed: " + ex);
   609	            shep.rollbackDBTransaction();
   610	        } finally {
   611	            shep.closeDBTransaction();
   612	        }
   613	        return ids;
   614	    }
   615	
   616	    private static void reconcileOneStaleAsset(String context, String maId) {
   617	        Shepherd shep = new Shepherd(context);
   618	        shep.setAction("StartupWildbook.StaleMlServiceReconciliation." + maId);
   619	        shep.beginDBTransaction();
   620	        try {
   621	            org.ecocean.media.MediaAsset ma = shep.getMediaAsset(maId);
   622	            if (ma == null) {
   623	                shep.commitDBTransaction();
   624	                return;
   625	            }
   626	            // Re-check: another worker may have progressed it since fetch.
   627	            if (!org.ecocean.identity.IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(
   628	                    ma.getDetectionStatus())) {
   629	                shep.commitDBTransaction();
   630	                return;
   631	            }
   632	            // Derive taxonomy.
   633	            java.util.List<org.ecocean.Taxonomy> taxies = ma.getTaxonomies(shep);
   634	            org.ecocean.Taxonomy taxy = null;
   635	            if (taxies != null && !taxies.isEmpty()) taxy = taxies.get(0);
   636	
   637	            org.ecocean.IAJsonProperties iac = org.ecocean.IAJsonProperties.iaConfig();
   638	            boolean stillVectorRouted = iac != null && taxy != null
   639	                && iac.getActiveMlServiceConfigs(taxy) != null;
   640	            if (!stillVectorRouted) {
   641	                // Species is no longer configured for ml-service (or no taxy).
   642	                // Flip to error so the operator sees it; don't re-enqueue.
   643	                ma.setDetectionStatus(org.ecocean.identity.IBEISIA.STATUS_ERROR);
   644	                System.out.println("[INFO] StaleMlServiceReconciliation: " + maId +
   645	                    " no longer vector-routed; marking error");
   646	                shep.commitDBTransaction();
   647	                return;
   648	            }
   649	            // Call the per-asset enqueue helper directly. This bypasses
   650	            // handleMissingAcmids (which would otherwise fire WBIA HTTP
   651	            // inside this Shepherd transaction) and passes null for
   652	            // topTask so the helper creates a root task internally. The
   653	            // reconciler doesn't need an aggregator parent.
   654	            //
   655	            // The helper internally calls storeNewTask, which commits the
   656	            // surrounding transaction. So when we get here, either:
   657	            //   - enqueue succeeded: the child Task + queue file are durable;
   658	            //   - enqueue failed: the child Task IS still persisted (orphan,
   659	            //     unreachable without a queued job) but the asset remains
   660	            //     in processing-mlservice for next-startup retry.
   661	            boolean enqueued = org.ecocean.ia.IA.enqueueOneAssetForMlService(
   662	                shep, ma, taxy, /* topTask */ null, context, /* baseUrl */ null);
   663	            if (!enqueued) {
   664	                System.out.println("WARN: StaleMlServiceReconciliation: enqueue FAILED for " +
   665	                    maId + "; leaving processing-mlservice intact for next-startup retry");
   666	                shep.rollbackDBTransaction();
   667	                return;
   668	            }
   669	            // No status update after a successful enqueue. The queued
   670	            // MlServiceProcessor job will set processing-mlservice itself
   671	            // (bumping REVISION) when its Phase 1 picks the work up. We
   672	            // intentionally do NOT mutate ma here: a fast queue consumer
   673	            // could already have advanced detectionStatus to
   674	            // complete-mlservice before our commit lands, and writing
   675	            // back from this stale managed instance would overwrite it.
   676	            //
   677	            // We still need a successful commit so the storeNewTask done
   678	            // inside the helper is finalized (it is already committed via
   679	            // updateDBTransaction, so this commit is essentially a no-op).
   680	            shep.commitDBTransaction();
   681	            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
   682	        } catch (Exception ex) {
   683	            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
   684	                maId + ": " + ex);
   685	            shep.rollbackDBTransaction();
   686	        } finally {
   687	            shep.closeDBTransaction();
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
   775	            // Image-side eligibility for the new Phase 0 image-registration
   776	            // path. Order mirrors sendMediaAssetsForceId in WildbookIAM (the
   777	            // isValidImageForIA + validMediaAsset pair, in that order), so
   778	            // the polling thread parks the same media assets the legacy
   779	            // batch path would skip. Phase B trusts Phase A's verdict —
   780	            // these are not re-checked against the DB after the Shepherd
   781	            // closes. (Empty-match-prospects design Track 1 C5: WBIA
   782	            // Phase 0 eligibility extension.)
   783	            else if (ma.isValidImageForIA() != null && !ma.isValidImageForIA())
   784	                reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
   785	            else if (!org.ecocean.ia.plugin.WildbookIAM.validMediaAsset(ma))
   786	                reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
   787	            if (reason != null) {
   788	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
   789	                    " (ineligible: " + reason + ")");
   790	                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
   791	                shep.commitDBTransaction();
   792	                return null;
   793	            }
   794	            // Resolve the individual name now while the Shepherd is open;
   795	            // Phase B has no DB access.
   796	            String name = ann.findIndividualId(shep);
   797	            // Copy bbox into a fresh array so the DTO is fully detached.
   798	            int[] bb = ann.getBbox();
   799	            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
   800	            // Capture image-side fields for Phase 0 (image registration).
   801	            // mediaAssetToUri returns null on null webURL — Phase A's
   802	            // validMediaAsset check above already rejected that case, so a
   803	            // null here would be a contract violation we'd want to see.
   804	            String imageUri = org.ecocean.ia.plugin.WildbookIAM.mediaAssetToUri(ma);
   805	            Double imageLatitude = ma.getLatitude();
   806	            Double imageLongitude = ma.getLongitude();
   807	            org.joda.time.DateTime dt = ma.getDateTime();
   808	            Long imageDateTimeMillis = (dt == null) ? null : dt.getMillis();
   809	            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   810	                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
   811	                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
   812	                    ann.getTheta(), ann.getIAClass(), name,
   813	                    imageUri, imageLatitude, imageLongitude, imageDateTimeMillis);
   814	            shep.commitDBTransaction();
   815	            return dto;
   816	        } catch (Exception ex) {
   817	            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
   818	                annId + ": " + ex);
   819	            shep.rollbackDBTransaction();
   820	            return null;
   821	        } finally {
   822	            shep.closeDBTransaction();
   823	        }
   824	    }
   825	
   826	    /**
   827	     * Phase C. Re-loads the annotation and writes the outcome of the
   828	     * Phase-B network call. On terminal-success outcomes the annotation
   829	     * is marked registered; on retryable outcomes the attempts counter
   830	     * is bumped and we WARN-log when we hit the abandonment threshold.
   831	     */
   832	    private static void persistWbiaRegisterResult(String context, String annId,
   833	        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
   834	        Shepherd shep = new Shepherd(context);
   835	        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
   836	        shep.beginDBTransaction();
   837	        try {
   838	            org.ecocean.Annotation ann = shep.getAnnotation(annId);
   839	            if (ann == null) {
   840	                shep.commitDBTransaction();
   841	                return;
   842	            }
   843	            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
   844	                // Some other path flipped it while Phase B ran; respect that.
   845	                shep.commitDBTransaction();
   846	                return;
   847	            }
   848	            switch (outcome) {
   849	                case REGISTERED_OK:
   850	                case REGISTERED_ALREADY_PRESENT:
   851	                    // Always honor a success outcome even if the row was
   852	                    // parked by a racing poller: stuck-at-attempts==MAX
   853	                    // would otherwise become permanent.
   854	                    ann.setWbiaRegistered(Boolean.TRUE);
   855	                    break;
   856	                case NETWORK_FAIL:
   857	                case RESPONSE_BAD:
   858	                default:
   859	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   860	                        // Already parked by another path; do not increment past MAX.
   861	                        break;
   862	                    }
   863	                    ann.incrementWbiaRegisterAttempts();
   864	                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
   865	                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
   866	                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
   867	                            " attempts (last outcome=" + outcome + "); will not retry");
   868	                    }
   869	                    break;
   870	            }
   871	            shep.commitDBTransaction();
   872	        } catch (Exception ex) {
   873	            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
   874	                annId + ": " + ex);
   875	            shep.rollbackDBTransaction();
   876	        } finally {
   877	            shep.closeDBTransaction();
   878	        }
   879	    }
   880	
   881	    public void contextDestroyed(ServletContextEvent sce) {
   882	        ServletContext sContext = sce.getServletContext();
   883	        String context = "context0";
   884	
   885	        System.out.println("* StartupWildbook destroyed called for: " +
   886	            servletContextInfo(sContext));
   887	
   888	        if (CommonConfiguration.useSpotPatternRecognition(context)) {
   889	            saveMatchGraph(sContext, context);
   890	        }
   891	        // Stop the WBIA poller first so it does not race teardown of
   892	        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
   893	        // flight. shutdownWbiaRegisterExecutor signals shutdown by
   894	        // nulling the executor handle and waits up to 15s for in-flight
   895	        // ticks; any tick still running after that gets shutdownNow().
   896	        // The poll loop's interrupt/null checks make subsequent work bail.
   897	        shutdownWbiaRegisterExecutor();
   898	        AnnotationLite.cleanup(sContext, context);
   899	        QueueUtil.cleanup();
   900	        MetricsBot.cleanup();
   901	        AcmIdBot.cleanup();
   902	        IndexingManagerFactory.getIndexingManager().shutdown();
   903	    }
   904	
   905	    // ml-service migration v2 §commit #11 fix-pass. The polling executor
   906	    // was previously held only in a local variable, which meant redeploys
   907	    // could leak a zombie thread that re-armed on next contextInitialized.
   908	    private static void shutdownWbiaRegisterExecutor() {
   909	        ScheduledExecutorService ses = wbiaRegisterExecutor;
   910	        if (ses == null) return;
   911	        wbiaRegisterExecutor = null;
   912	        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
   913	        ses.shutdown();
   914	        try {
   915	            // 15s gives a healthy Phase B WBIA call time to finish so we
   916	            // do not skip its Phase C unnecessarily. RestClient HTTP isn't
   917	            // truly interruptible, so a hung call will still outlive this
   918	            // wait; the per-id and pre-Phase-C interrupt checks in the
   919	            // poller handle that case by bailing out before doing more DB
   920	            // work.
   921	            if (!ses.awaitTermination(15L, TimeUnit.SECONDS)) ses.shutdownNow();
   922	        } catch (InterruptedException ie) {
   923	            ses.shutdownNow();
   924	            Thread.currentThread().interrupt();
   925	        }
   926	    }
   927	
   928	    public static void createMatchGraph() {
   929	        System.out.println("Entering createMatchGraph StartupWildbook method.");
   930	        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
   931	        es.execute(new MatchGraphCreationThread());
   932	    }
   933	
   934	    /**
   935	     * Try loading the matchGraph from disk cache. If the cache exists and loads
   936	     * successfully, use it directly. Otherwise fall back to the full DB rebuild
   937	     * via MatchGraphCreationThread.
   938	     */
   939	    public static void loadMatchGraphOrRebuild(ServletContext sContext, String context) {
   940	        try {
   941	            String dataDir = CommonConfiguration.getDataDirectory(sContext, context).getAbsolutePath();
   942	            String cacheFile = GridManager.getCacheFilePath(dataDir);
   943	            if (new File(cacheFile).exists() && GridManager.cacheRead(cacheFile)) {
   944	                System.out.println("INFO: matchGraph loaded from cache.");
   945	                return;
   946	            }
   947	        } catch (Exception e) {
   948	            System.out.println("WARNING: Could not load matchGraph cache, rebuilding from DB: " +
   949	                e.getMessage());
   950	        }
   951	        createMatchGraph();
   952	    }
   953	
   954	    public static void saveMatchGraph(ServletContext sContext, String context) {
   955	        try {
   956	            String dataDir = CommonConfiguration.getDataDirectory(sContext, context).getAbsolutePath();
   957	            String cacheFile = GridManager.getCacheFilePath(dataDir);
   958	            GridManager.cacheWrite(cacheFile);
   959	        } catch (Exception e) {
   960	            System.out.println("WARNING: Could not save matchGraph cache: " + e.getMessage());
   961	        }
   962	    }
   963	
   964	    public static boolean skipInit(ServletContextEvent sce, String extra) {
   965	        ServletContext sc = sce.getServletContext();
   966	
   967	/*   WARNING!  this bad hackery to try to work around "double deployment" ... yuck!
   968	     see:  https://octopus.com/blog/defining-tomcat-context-paths
   969	 */
   970	        String fname = "/tmp/WB_SKIP_INIT" + ((extra == null) ? "" : "_" + extra);
   971	        boolean skip = new File(fname).exists();
   972	
   973	        System.out.println("++ StartupWildbook.skipInit() test on " + extra + " [" + fname +
   974	            "] --> " + skip);
   975	        return skip;
   976	    }
   977	
   978	    public static String servletContextInfo(ServletContext sc) {
   979	        if (sc == null) return null;
   980	        try {
   981	            return sc.getServletContextName() + " [" + sc.getContextPath() + " via " +
   982	                       sc.getRealPath("/") + "]";
   983	        } catch (Exception ex) {
   984	            System.out.println("WARNING: StartupWildbook.servletContextInfo() threw " +
   985	                ex.toString());
   986	            return "<unknown>";
   987	        }
   988	    }
   989	    
   990	    
   991	    
   992	}

 succeeded in 608ms:
src/main/java/org/ecocean/Annotation.java:53:    protected String acmId;
src/main/java/org/ecocean/Annotation.java:213:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/Annotation.java:312:    public void setAcmId(String id) {
src/main/java/org/ecocean/Annotation.java:317:    public String getAcmId() {
src/main/java/org/ecocean/Annotation.java:387:    public String getUUID() {
src/main/java/org/ecocean/Annotation.java:590:       JSONObject iaViewpointFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException,
src/main/java/org/ecocean/Annotation.java:1890:            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:120:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:132:            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:206:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:218:            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:288:            if (iaAnnotIds.contains(ann.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:295:            if (ann.getMediaAsset().getAcmId() == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:305:            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:368:            if (iaAnnotIds.contains(ann.getAcmId())) continue;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:379:            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
src/main/java/org/ecocean/identity/IBEISIA.java:146:            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/identity/IBEISIA.java:206:            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
src/main/java/org/ecocean/identity/IBEISIA.java:207:            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
src/main/java/org/ecocean/identity/IBEISIA.java:280:            qlist.add(toFancyUUID(ann.getAcmId()));
src/main/java/org/ecocean/identity/IBEISIA.java:311:                // Util.mark(ct + "]  sib-1 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
src/main/java/org/ecocean/identity/IBEISIA.java:317:                // Util.mark("      sib-2 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
src/main/java/org/ecocean/identity/IBEISIA.java:319:                tlist.add(toFancyUUID(ann.getAcmId()));
src/main/java/org/ecocean/identity/IBEISIA.java:418:            if (ma.getAcmId() != null) uuidList.put(toFancyUUID(ma.getAcmId()));
src/main/java/org/ecocean/identity/IBEISIA.java:430:            if (ma.getAcmId() == null) { // usually this means it was not able to be added to IA (e.g. a video etc)
src/main/java/org/ecocean/identity/IBEISIA.java:436:            malist.add(toFancyUUID(ma.getAcmId()));
src/main/java/org/ecocean/identity/IBEISIA.java:800:                    String uuid = fromFancyUUID(list.getJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:813:                        String acmId = fromFancyUUID(list.getJSONObject(i));
src/main/java/org/ecocean/identity/IBEISIA.java:1169:            uuids.add(ann.getAcmId());
src/main/java/org/ecocean/identity/IBEISIA.java:1192:            if ((enc == null) || (ann.getAcmId() == null)) continue;
src/main/java/org/ecocean/identity/IBEISIA.java:1197:            uuids.add(ann.getAcmId());
src/main/java/org/ecocean/identity/IBEISIA.java:1271:        ann.setAcmId(fromFancyUUID(iaResult.optJSONObject("uuid")));
src/main/java/org/ecocean/identity/IBEISIA.java:1597:                        if (ma.getAcmId() == null) continue; // was likely an asset rejected (e.g. video)
src/main/java/org/ecocean/identity/IBEISIA.java:1598:                        if (ma.getAcmId().equals(iuuid) &&
src/main/java/org/ecocean/identity/IBEISIA.java:1786:                    anns.put((ann.getAcmId() != null) ? ann.getAcmId() : ann.getId(), ann);
src/main/java/org/ecocean/identity/IBEISIA.java:1833:                String acmId = fromFancyUUID(rlist.getJSONObject(i).getJSONObject("annot_uuid_1")); // gets not opts here... so ungraceful fail possible
src/main/java/org/ecocean/identity/IBEISIA.java:2112:    public static Annotation getAnnotationFromIA(String acmId, Shepherd myShepherd) {
src/main/java/org/ecocean/identity/IBEISIA.java:2155:            ann.setAcmId(acmId);
src/main/java/org/ecocean/identity/IBEISIA.java:2202:        ma.setAcmId(maUUID);
src/main/java/org/ecocean/identity/IBEISIA.java:2204:        // ma.setUUID(maUUID);
src/main/java/org/ecocean/identity/IBEISIA.java:2859:    public static int iaImageSetIdFromUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2872:    public static DateTime iaDateTimeFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2886:    public static Boolean iaIsOfInterestFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2899:    public static Double[] iaLatLonFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2913:    public static Double iaThetaFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2925:    public static Double[] iaLatLonFromImageUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2946:    public static DateTime iaDateTimeFromImageUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2962:    public static String iaSexFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:2986:    public static JSONObject iaViewpointFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:3072:    public static String iaFilepathFromImageUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:3085:    public static Double iaAgeFromAnnotUUID(String uuid, String context)
src/main/java/org/ecocean/identity/IBEISIA.java:3110:    public static JSONObject iaSetViewpointForAnnotUUID(String uuid, String viewpoint,
src/main/java/org/ecocean/identity/IBEISIA.java:3127:        for (String uuid : uuids) {
src/main/java/org/ecocean/identity/IBEISIA.java:3130:        String uuidList = Util.joinStrings(fancyUUIDs, ",");
src/main/java/org/ecocean/identity/IBEISIA.java:3267:        AnnotationLite annl = AnnotationLite.getCache(ann.getAcmId());
src/main/java/org/ecocean/identity/IBEISIA.java:3279:        AnnotationLite.setCache(ann.getAcmId(), annl);
src/main/java/org/ecocean/identity/IBEISIA.java:3587:        String acmId = ann.getAcmId();
src/main/java/org/ecocean/identity/IBEISIA.java:3714:            if (iaAnnotIds.contains(ann.getAcmId())) continue;
src/main/java/org/ecocean/identity/IBEISIA.java:3722:            if (iaImageIds.contains(ma.getAcmId())) continue;
src/main/java/org/ecocean/identity/IBEISIA.java:3752:            String acmId = (String)row[0];
src/main/java/org/ecocean/media/MediaAssetSet.java:10:public class MediaAssetSet implements java.io.Serializable {
src/main/java/org/ecocean/media/YouTubeAssetStore.java:255:        sp.put("key", parent.getUUID() + "/" + f.getName());
src/main/java/org/ecocean/media/YouTubeAssetStore.java:280:        sp.put("key", parent.getUUID() + "/" + f.getName());
src/main/java/org/ecocean/media/YouTubeAssetStore.java:316:        sp.put("key", parent.getUUID() + "/" + f.getName());
src/main/java/org/ecocean/media/MediaAssetFactory.java:10:public class MediaAssetFactory {
src/main/java/org/ecocean/media/MediaAssetFactory.java:32:    public static MediaAsset loadByUuid(final String uuid, Shepherd myShepherd) {
src/main/java/org/ecocean/media/MediaAsset.java:48:public class MediaAsset extends Base implements java.io.Serializable {
src/main/java/org/ecocean/media/MediaAsset.java:53:    protected String uuid = null;
src/main/java/org/ecocean/media/MediaAsset.java:92:    private String acmId;
src/main/java/org/ecocean/media/MediaAsset.java:111:        this.setUUID();
src/main/java/org/ecocean/media/MediaAsset.java:117:        if (this.acmId == null) this.acmId = this.getUUID();
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/media/MediaAsset.java:136:    public String getAcmId() {
src/main/java/org/ecocean/media/MediaAsset.java:235:    public String getUUID() {
src/main/java/org/ecocean/media/MediaAsset.java:241:    public void setUUID(String u) {
src/main/java/org/ecocean/media/MediaAsset.java:248:    private void setUUID() {
src/main/java/org/ecocean/media/MediaAsset.java:696:            System.out.println("MediaAsset " + this.getUUID() + " has no store!");
src/main/java/org/ecocean/media/MediaAsset.java:861:        jobj.put("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:895:                    jf.put("annotationAcmId", ann.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:998:        j.put("uuid", getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1694:        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
src/main/java/org/ecocean/media/MediaAsset.java:1695:        return this.uuid.equals(two.getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1726:        jgen.writeStringField("acmId", this.getAcmId());
src/main/java/org/ecocean/media/MediaAsset.java:1727:        jgen.writeStringField("uuid", this.getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1745:            jgen.writeStringField("acmId", ann.getAcmId());
src/main/java/org/ecocean/acm/AcmUtil.java:27:            } else if (mas.get(i).getAcmId() == null) {
src/main/java/org/ecocean/acm/AcmUtil.java:28:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:30:            } else if (!mas.get(i).getAcmId().equals(acmIds.get(i))) {
src/main/java/org/ecocean/acm/AcmUtil.java:32:                    mas.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + mas.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:33:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:55:            } else if (anns.get(i).getAcmId() == null) {
src/main/java/org/ecocean/acm/AcmUtil.java:56:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:58:            } else if (!anns.get(i).getAcmId().equals(acmIds.get(i))) {
src/main/java/org/ecocean/acm/AcmUtil.java:60:                    anns.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + anns.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:61:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/media/MediaAssetMetadata.java:17:public class MediaAssetMetadata implements java.io.Serializable {

 succeeded in 748ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/plugin/WildbookIAM.java

exec
/bin/bash -lc 'git ls-files --eol -- src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'nl -ba src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short --untracked-files=all -- src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '100,150p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 397ms:
     1	package org.ecocean.ia.plugin;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNotNull;
     5	import static org.junit.jupiter.api.Assertions.assertNull;
     6	import static org.junit.jupiter.api.Assertions.assertThrows;
     7	import static org.junit.jupiter.api.Assertions.assertTrue;
     8	
     9	import java.io.IOException;
    10	import java.util.ArrayList;
    11	import java.util.HashMap;
    12	
    13	import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
    14	import org.json.JSONArray;
    15	import org.json.JSONObject;
    16	import org.junit.jupiter.api.Test;
    17	
    18	/**
    19	 * Pure-function coverage of Phase 0 helpers introduced in C6:
    20	 * {@link WildbookIAM#buildImageRequestMap} and
    21	 * {@link WildbookIAM#validateImageResponse}. The network-bound
    22	 * {@code registerImageIfMissing} entry point is exercised end-to-end
    23	 * by the WBIA registration polling thread in a dev deployment.
    24	 * (Empty-match-prospects design Track 1 C6.)
    25	 */
    26	class WildbookIAMImagePhase0Test {
    27	
    28	    private static WbiaRegisterRequest sampleDtoWithImage() {
    29	        return new WbiaRegisterRequest(
    30	            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
    31	            new int[] { 0, 0, 100, 100 },
    32	            0.0d, "iaClass", "____",
    33	            "https://example.com/img.jpg",
    34	            12.34d, -56.78d, 1700000000000L);
    35	    }
    36	
    37	    // --- buildImageRequestMap --------------------------------------------
    38	
    39	    @Test void buildImageRequestMapHasAllFiveLists() {
    40	        HashMap<String, ArrayList> map =
    41	            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
    42	        assertNotNull(map.get("image_uri_list"));
    43	        assertNotNull(map.get("image_uuid_list"));
    44	        assertNotNull(map.get("image_unixtime_list"));
    45	        assertNotNull(map.get("image_gps_lat_list"));
    46	        assertNotNull(map.get("image_gps_lon_list"));
    47	        assertEquals(1, map.get("image_uri_list").size());
    48	    }
    49	
    50	    @Test void buildImageRequestMapPopulatesScalarFields() {
    51	        HashMap<String, ArrayList> map =
    52	            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
    53	        assertEquals("https://example.com/img.jpg",
    54	            map.get("image_uri_list").get(0));
    55	        assertEquals(12.34d, map.get("image_gps_lat_list").get(0));
    56	        assertEquals(-56.78d, map.get("image_gps_lon_list").get(0));
    57	    }
    58	
    59	    @Test void buildImageRequestMapWrapsImageUuidInFancyForm() {
    60	        HashMap<String, ArrayList> map =
    61	            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
    62	        Object wrapped = map.get("image_uuid_list").get(0);
    63	        assertTrue(wrapped instanceof JSONObject,
    64	            "expected JSONObject fancy-uuid wrapper, got " +
    65	            (wrapped == null ? "null" : wrapped.getClass().getName()));
    66	        assertEquals("ma-acm-uuid-1",
    67	            ((JSONObject) wrapped).optString("__UUID__"));
    68	    }
    69	
    70	    @Test void buildImageRequestMapConvertsMillisToUnixSeconds() {
    71	        HashMap<String, ArrayList> map =
    72	            WildbookIAM.buildImageRequestMap(sampleDtoWithImage());
    73	        // 1700000000000ms = 1700000000s
    74	        assertEquals(1700000000, map.get("image_unixtime_list").get(0));
    75	    }
    76	
    77	    @Test void buildImageRequestMapPassesNullForNullDateTime() {
    78	        WbiaRegisterRequest dto = new WbiaRegisterRequest(
    79	            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
    80	            new int[] { 0, 0, 100, 100 },
    81	            0.0d, "iaClass", "____",
    82	            "https://example.com/img.jpg",
    83	            12.34d, -56.78d, null);
    84	        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
    85	        assertNull(map.get("image_unixtime_list").get(0));
    86	    }
    87	
    88	    @Test void buildImageRequestMapPassesNullsForOptionalGps() {
    89	        WbiaRegisterRequest dto = new WbiaRegisterRequest(
    90	            "ann-1", "ann-acm-1", "ma-acm-uuid-1",
    91	            new int[] { 0, 0, 100, 100 },
    92	            0.0d, "iaClass", "____",
    93	            "https://example.com/img.jpg",
    94	            null, null, 1700000000000L);
    95	        HashMap<String, ArrayList> map = WildbookIAM.buildImageRequestMap(dto);
    96	        assertNull(map.get("image_gps_lat_list").get(0));
    97	        assertNull(map.get("image_gps_lon_list").get(0));
    98	    }
    99	
   100	    // --- validateImageResponse -------------------------------------------
   101	
   102	    private static JSONObject okResponse(String returnedUuid) {
   103	        JSONObject jo = new JSONObject();
   104	        JSONObject status = new JSONObject();
   105	        status.put("success", true);
   106	        jo.put("status", status);
   107	        JSONArray arr = new JSONArray();
   108	        JSONObject fancy = new JSONObject();
   109	        fancy.put("__UUID__", returnedUuid);
   110	        arr.put(fancy);
   111	        jo.put("response", arr);
   112	        return jo;
   113	    }
   114	
   115	    @Test void validateImageResponse_acceptsMatchingFancyUuid()
   116	    throws IOException {
   117	        WildbookIAM.validateImageResponse("ma-acm-uuid-1",
   118	            okResponse("ma-acm-uuid-1"));
   119	    }
   120	
   121	    @Test void validateImageResponse_throwsOnNullResponse() {
   122	        IOException ex = assertThrows(IOException.class,
   123	            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", null));
   124	        assertTrue(ex.getMessage().contains("null"),
   125	            "message should mention null: " + ex.getMessage());
   126	    }
   127	
   128	    @Test void validateImageResponse_throwsOnStatusSuccessFalse() {
   129	        JSONObject resp = okResponse("ma-acm-uuid-1");
   130	        resp.getJSONObject("status").put("success", false);
   131	        IOException ex = assertThrows(IOException.class,
   132	            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
   133	        assertTrue(ex.getMessage().contains("success=false"));
   134	    }
   135	
   136	    @Test void validateImageResponse_throwsOnMissingResponseArray() {
   137	        JSONObject resp = new JSONObject();
   138	        IOException ex = assertThrows(IOException.class,
   139	            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
   140	        assertTrue(ex.getMessage().contains("no response array"));
   141	    }
   142	
   143	    @Test void validateImageResponse_throwsOnArrayLengthMismatch() {
   144	        JSONObject resp = okResponse("ma-acm-uuid-1");
   145	        resp.getJSONArray("response").put(new JSONObject().put("__UUID__", "x"));
   146	        IOException ex = assertThrows(IOException.class,
   147	            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
   148	        assertTrue(ex.getMessage().contains("length 1"));
   149	    }
   150	
   151	    @Test void validateImageResponse_throwsOnUuidMismatch() {
   152	        JSONObject resp = okResponse("different-uuid");
   153	        IOException ex = assertThrows(IOException.class,
   154	            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
   155	        assertTrue(ex.getMessage().contains("mismatch"));
   156	        assertTrue(ex.getMessage().contains("ma-acm-uuid-1"));
   157	        assertTrue(ex.getMessage().contains("different-uuid"));
   158	    }
   159	
   160	    @Test void validateImageResponse_throwsOnUndecodableResponseElement() {
   161	        JSONObject resp = new JSONObject();
   162	        JSONArray arr = new JSONArray();
   163	        arr.put(new JSONObject().put("not_uuid_key", "x"));  // no __UUID__
   164	        resp.put("response", arr);
   165	        IOException ex = assertThrows(IOException.class,
   166	            () -> WildbookIAM.validateImageResponse("ma-acm-uuid-1", resp));
   167	        assertTrue(ex.getMessage().contains("could not be decoded"));
   168	    }
   169	}

 succeeded in 425ms:
 succeeded in 509ms:
    public MediaAsset() {
        this(MediaAssetFactory.NOT_SAVED, null, null);
    }

    public MediaAsset(final AssetStore store, final JSONObject params) {
        // this(store, params, null);
        this(MediaAssetFactory.NOT_SAVED, store, params);
    }

    public MediaAsset(final int id, final AssetStore store, final JSONObject params) {
        this.id = id;
        this.setUUID();
        this.store = store;
        this.parameters = params;
        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
        this.setHashCode();
        if (this.acmId == null) this.acmId = this.getUUID();
    }

    public AccessControl getAccessControl() {
        return accessControl;
    }

    public void setAccessControl(AccessControl ac) {
        accessControl = ac;
    }

    public void setAccessControl(HttpServletRequest request) {
        this.setAccessControl(new AccessControl(request));
    }

    public void setAcmId(String id) {
        this.acmId = id;
    }

    public String getAcmId() {
        return this.acmId;
    }

    public boolean hasAcmId() {
        return (null != this.acmId);
    }

    private URL getUrl(final AssetStore store, final Path path) {
        if (store == null) {
            return null;
        }
        return null; // store.webPath(path);
    }


 succeeded in 947ms:
 M src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
?? src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java

exec
/bin/bash -lc "sed -n '429,530p' src/main/java/org/ecocean/StartupWildbook.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '130,230p' src/main/java/org/ecocean/identity/IBEISIA.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '2160,2220p' src/main/java/org/ecocean/identity/IBEISIA.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,80p' src/main/java/org/ecocean/acm/AcmUtil.java" in /mnt/c/Wildbook-clean2
 succeeded in 440ms:
     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
     * queue or reconcile servlet is needed.
     *
     * <p>Per cycle (~30s): query annotations with
     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
     * up to a small batch limit. For each, call
     * {@link org.ecocean.ia.plugin.WildbookIAM#sendAnnotationsForceId} in a
     * per-annotation Shepherd transaction (so one slow WBIA call blocks
     * only one slot, not the entire batch). On success: set
     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
     *
     * <p>Legacy annotations are excluded from the query because the DDL
     * migration in {@code archive/sql/ml_service_idempotency.sql} backfills
     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
     */
    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
    private static final int WBIA_REGISTER_BATCH_LIMIT = 50;
    private static final long WBIA_REGISTER_POLL_SECONDS = 30L;

    private static void startWbiaRegistrationPollingThread(final String context) {
        // Refuse to start a second poller if one is already running; this
        // also matters when contextInitialized fires more than once for
        // the same JVM (e.g., context reload).
        if (wbiaRegisterExecutor != null) {
            System.out.println(
                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
            return;
        }
        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "WbiaRegistrationPoll");
                    t.setDaemon(true);
                    return t;
                }
            });
        ses.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    runWbiaRegistrationPoll(context);
                } catch (Throwable t) {
                    // Catch Throwable here: ScheduledExecutorService silently
                    // stops re-firing the task on any uncaught exception.
                    // We want the thread to keep ticking through transient
                    // failures.
                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
                    t.printStackTrace();
                }
            }
        }, WBIA_REGISTER_POLL_SECONDS, WBIA_REGISTER_POLL_SECONDS, TimeUnit.SECONDS);
        wbiaRegisterExecutor = ses;
    }

    private static void runWbiaRegistrationPoll(String context) {
        // Phase 1: query the pending list (Shepherd open, no network). Capture
        // annotation IDs and release before any WBIA calls.
        java.util.List<String> pendingIds = new ArrayList<String>();
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
        shep.beginDBTransaction();
        try {
            javax.jdo.Query q = shep.getPM().newQuery(
                org.ecocean.Annotation.class,
                "wbiaRegistered == false && wbiaRegisterAttempts < "
                + WBIA_REGISTER_MAX_ATTEMPTS);
            q.setOrdering("wbiaRegisterAttempts ascending");
            q.setRange(0, WBIA_REGISTER_BATCH_LIMIT);
            @SuppressWarnings("unchecked")
            java.util.List<org.ecocean.Annotation> pending =
                (java.util.List<org.ecocean.Annotation>) q.execute();
            if (pending != null) {
                for (org.ecocean.Annotation a : pending) pendingIds.add(a.getId());
            }
            q.closeAll();
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }

        if (pendingIds.isEmpty()) return;
        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");

        // Phase 2: per-annotation registration. Each runs in its own short
        // Shepherd tx so a slow WBIA call blocks only that one slot.
        // The interrupted-check makes shutdownNow() effective at bounding
        // the per-tick work even if the in-flight Phase B HTTP call ran
        // past awaitTermination.
        for (String annId : pendingIds) {
            if (Thread.currentThread().isInterrupted() ||
                wbiaRegisterExecutor == null) {
                System.out.println("[INFO] WbiaRegistrationPoll: stopping mid-batch (interrupted)");
                return;
            }
            registerOneAnnotationWithWbia(context, annId);
        }
    }


 succeeded in 464ms:
            ImageAttributes iatt = ma.getImageAttributes();
            int w = 0;
            int h = 0;
            if (iatt != null) {
                w = (int)iatt.getWidth();
                h = (int)iatt.getHeight();
            }
            // we are *required* to have a width/height to pass to IA, so lets skip...
            if ((w < 1) || (h < 1)) {
                System.out.println("WARNING: IBEISIA.sendMediaAssets() skipping " + ma.toString() +
                    " - unable to find width/height");
                continue;
            }
            map.get("image_width_list").add(w);
            map.get("image_height_list").add(h);

            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
            map.get("image_uuid_list").add(toFancyUUID(uuidToSend));
            map.get("image_uri_list").add(mediaAssetToUri(ma));

            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());

            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_time_posix_list").add(0);
            } else {
                map.get("image_time_posix_list").add((int)Math.floor(t.getMillis() / 1000)); // IBIES-IA wants seconds since epoch
            }
            markSent(ma);
            ct++;
        }
        System.out.println("sendMediaAssets(): sending " + ct);
        if (ct < 1) return null; // null for "none to send" ?  is this cool?
        return RestClient.post(url, hashMapToJSONObject(map));
    }

    public static JSONObject __sendAnnotations(ArrayList<Annotation> anns, String context,
        Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (!isIAPrimed())
            System.out.println("WARNING: sendAnnotations() called without IA primed");
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
        if (u == null)
            throw new MalformedURLException(
                      "configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);
        int ct = 0;
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        for (Annotation ann : anns) {
            if (!needToSend(ann)) continue;
            if (!validForIdentification(ann, context)) {
                System.out.println("WARNING: IBEISIA.sendAnnotations() skipping invalid " + ann);
                continue;
            }
            // Try and get an iaClass from the  annotation. If detection ran correctly.. it should be there.
            // I guess fall back on the species from ann if you don't find anything? Maybe you shouldn't... because detect shouldn't have anything to
            // do
            // with the human friendly "species", just ia class. Oh well, doing it anyway for now.. FIGHT ME ABOUT IT
            String iaClass = null;
            if (Util.stringExists(ann.getIAClass())) {
                iaClass = ann.getIAClass();
                System.out.println("iaClass set from Annotation.");
            } else {
                System.out.println(
                    "===> CRITICAL ERROR: Annotation did not have a useable class candidate to send to identification for iaClass. ");
                continue;
            }
            int[] bbox = ann.getBbox();
            map.get("annot_bbox_list").add(bbox);
            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
            map.get("annot_species_list").add(iaClass);
            String name = ann.findIndividualId(myShepherd);
            map.get("annot_name_list").add((name == null) ? "____" : name);
            markSent(ann);
            ct++;
        }
        System.out.println("sendAnnotations(): sending " + ct);
        if (ct < 1) return null;
        // this should only be checking for missing images, i guess?
        boolean tryAgain = true;
        JSONObject res = null;
        while (tryAgain) {
            res = RestClient.post(url, hashMapToJSONObject(map));
            tryAgain = iaCheckMissing(res, context, myShepherd);
        }
        return res;
    }

    // note: if tanns here is null, then it is exemplar for this species
    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns,
        JSONObject queryConfigDict, JSONObject userConfidence, String baseUrl, String context,
        String taskId, boolean fastlane)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,

 succeeded in 433ms:
package org.ecocean.acm;

import java.util.List;
import org.ecocean.Annotation;
import org.ecocean.ia.IA;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;

public class AcmUtil {
    // these take a list of objects and a parallel list of acmIds to assign
    // returns number actually changed
    // it should "handle weirdness" whatever that may mean?
    public static int rectifyMediaAssetIds(List<MediaAsset> mas, List<String> acmIds) {
        if ((mas == null) || (acmIds == null) || (mas.size() != acmIds.size())) {
            IA.log("ERROR: AcmUtil.rectifyMediaAssetIds() has invalid lists passed; failing");
            return -1;
        }
        int numChanged = 0;
        for (int i = 0; i < mas.size(); i++) {
            if (mas.get(i) == null) {
                IA.log(
                    "WARNING: bizarre! AcmUtil.rectifyMediaAssetIds() has null MediaAsset at i=" +
                    i + "; skipping");
            } else if (acmIds.get(i) == null) {
                IA.log("INFO: AcmUtil.rectifyMediaAssetIds() has null acmId response for " +
                    mas.get(i) + "; skipping");
            } else if (mas.get(i).getAcmId() == null) {
                mas.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            } else if (!mas.get(i).getAcmId().equals(acmIds.get(i))) {
                IA.log("ERROR: AcmUtil.rectifyMediaAssetIds() changing acmId from " +
                    mas.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + mas.get(i));
                mas.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            }
        }
        return numChanged;
    }

    public static int rectifyAnnotationIds(List<Annotation> anns, List<String> acmIds,
        Shepherd myShepherd) {
        if ((anns == null) || (acmIds == null) || (anns.size() != acmIds.size())) {
            IA.log("ERROR: AcmUtil.rectifyAnnotationIds() has invalid lists passed; failing");
            return -1;
        }
        int numChanged = 0;
        for (int i = 0; i < anns.size(); i++) {
            if (anns.get(i) == null) {
                IA.log(
                    "WARNING: bizarre! AcmUtil.rectifyAnnotationIds() has null Annotation at i=" +
                    i + "; skipping");
            } else if (acmIds.get(i) == null) {
                IA.log("INFO: AcmUtil.rectifyAnnotationIds() has null acmId response for " +
                    anns.get(i) + "; skipping");
            } else if (anns.get(i).getAcmId() == null) {
                anns.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            } else if (!anns.get(i).getAcmId().equals(acmIds.get(i))) {
                IA.log("WARNING: AcmUtil.rectifyAnnotationIds() changing acmId from " +
                    anns.get(i).getAcmId() + " to " + acmIds.get(i) + " on " + anns.get(i));
                anns.get(i).setAcmId(acmIds.get(i));
                numChanged++;
            }
        }
        // persist this
        if (numChanged > 0) myShepherd.updateDBTransaction();
        return numChanged;
    }
}

 succeeded in 482ms:
            }
            Boolean aoi = iaIsOfInterestFromAnnotUUID(acmId, context);
            ann.setIsOfInterest(aoi);
            ann.setMatchAgainst(true); // kosher?
            ann.setViewpointFromIA(context); // note: can block ... but wygd
            System.out.println("INFO: " + ann + " pulled from IA");
            return ann;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("getAnnotationFromIA(" + acmId + ") error " + ex.toString());
        }
    }

    public static MediaAsset grabMediaAsset(String maUUID, Shepherd myShepherd) {
        // note: there may be more than one acmId with this value, but for this case we dont (cant?) care...
        MediaAsset ma = MediaAssetFactory.loadByAcmId(maUUID, myShepherd);

        if (ma != null) return ma;
        return getMediaAssetFromIA(maUUID, myShepherd);
    }

    // http://52.37.240.178:5000/api/image/src/json/cb2e67a4-7094-d971-c5c6-3b5bed251fec/
    // making a decision to persist these upon creation... there was a conflict cuz loadByUuid above failed on subsequent
    // iterations and this was created multiple times before saving
    public static MediaAsset getMediaAssetFromIA(String maUUID, Shepherd myShepherd) {
        String context = myShepherd.getContext();
        String filename = maUUID + ".jpg"; // hopefully will be updated with real filename!
        String filepath = null;

        try {
            filepath = iaFilepathFromImageUUID(maUUID, context);
            filename = new File(filepath).getName();
        } catch (Exception ex) {
            System.out.println("WARNING: failed to get iaFilepath of " + maUUID + ": " +
                ex.toString());
        }
        // note: we add /fakedir/ cuz the file doesnt need to exist there; we just want to force a hashed subdir to be created in params
        File file = new File("/fakedir/" + filename);
        AssetStore astore = AssetStore.getDefault(myShepherd);
        JSONObject params = astore.createParameters(file);
        if (filepath != null) params.put("iaOriginalFilepath", filepath);
        MediaAsset ma = new MediaAsset(astore, params);
        ma.setAcmId(maUUID);
        // similarly, do we want to set uuid on ma based on acmId???
        // ma.setUUID(maUUID);
        try {
            // grab the url to our localPath for convenience (e.g. child assets to be created from)
            file = ma.localPath().toFile();
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            // we actually need to handle bad maUUID better.  :( (returns
            RestClient.writeToFile(iaURL(context, "/api/image/src/json/" + maUUID + "/"), file);
            ma.copyIn(file);
            ma.addDerivationMethod("pulledFromIA", System.currentTimeMillis());
            ma.updateMetadata();
            MediaAssetFactory.save(ma, myShepherd);
            ma.updateStandardChildren(myShepherd);
        } catch (IOException ioe) {
            throw new RuntimeException("ERROR: getMediaAssetFromIA " + ioe.toString());
        }
        ma.addLabel("_original");

exec
/bin/bash -lc 'rg -n "class RestClient|static JSONObject post|static JSONObject get|HTTP|status" src/main/java/org/ecocean/RestClient.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,220p' src/main/java/org/ecocean/RestClient.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '220,420p' src/main/java/org/ecocean/RestClient.java" in /mnt/c/Wildbook-clean2
 succeeded in 399ms:
                System.out.println("caught exception on key " + key + ": " + uee.toString());
            }
        }
//////System.out.println("------- getPostDataString=(\n" + result.toString() + "\n)--------\n");
        return result.toString();
    }

    public static void writeToFile(URL url, File file)
    throws IOException {
        if (url == null) throw new IOException("RestClient.writeToFile() got null url");
        InputStream is = url.openStream();
        OutputStream os = new FileOutputStream(file);
        byte[] b = new byte[2048];
        int length;
        while ((length = is.read(b)) != -1) {
            os.write(b, 0, length);
        }
        is.close();
        os.close();
    }

    /***********************************************************************************************
       TODO: evaluate strategy, rewrite as needed and define future actions that need to be taken to truly update
       some attempts to *truly* genericize these....

       rather than authUsername/authPassword, we might want to have our own credential class that allows us to set headers etc accordingly!

       note also that data passed in (for post) is a string, so encode it accordingly first one annoying thing we attempt to handle now is that there
          may be plain-text returned (not always json)
     ***********************************************************************************************/
    public static JSONObject getJSONObject(URL url, String authUsername, String authPassword)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String res = get(url, authUsername, authPassword);

        if (res == null) return null;
        return new JSONObject(res);
    }

    public static String get(URL url, String authUsername, String authPassword)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethodGeneric("GET", url, authUsername, authPassword, null);
    }

    // you might want to use getPostDataString() to get input data string here
    private static String anyMethodGeneric(String method, URL url, String authUsername,
        String authPassword, String data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethodGeneric(method, url, authUsername, authPassword, data, CONNECTION_TIMEOUT);
    }

    private static String anyMethodGeneric(String method, URL url, String authUsername,
        String authPassword, String data, int ctimeout)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        System.out.println("TRYING anyMethodGeneric(" + method + ") url -> " + url);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setReadTimeout(ctimeout);
        conn.setConnectTimeout(ctimeout);
        conn.setDoOutput((data != null));
        conn.setDoInput(true);
        conn.setRequestMethod(method);
        if ((authUsername != null) && (authPassword != null)) {
            byte[] authBytes = (authUsername + ":" + authPassword).getBytes("UTF-8");
            String authEncoded = javax.xml.bind.DatatypeConverter.printBase64Binary(authBytes);
            conn.setRequestProperty("Authorization", "Basic " + authEncoded);
        }
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        boolean success = true;
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            success = false;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        if (!success) {
            System.out.println("========= anyMethod failed with code=" + conn.getResponseCode() +
                "\n" + jtext + "\n============");
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }
        if (jtext.equals("")) return null;
        return jtext;
    }

    // much more generic form...
    public static String postRaw(URL url, String data, Map<String, String> headers)
    throws IOException, java.net.ProtocolException {
        return postRaw(url, data, headers, CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
    }

    // ml-service migration v2 (commit #6): overload with separate connect/read
    // timeouts. The shared CONNECTION_TIMEOUT (5 minutes) is too long for the
    // ml-service path — a hung request would stall the single-consumer
    // detection queue. ml-service callers pass shorter values (e.g. 30s
    // connect / 120s read). Per HttpURLConnection.setConnectTimeout /
    // setReadTimeout: 0 means infinite; negative values throw
    // IllegalArgumentException. Callers are responsible for passing positive
    // values or 0 — this method does not clamp.
    public static String postRaw(URL url, String data, Map<String, String> headers,
        int connectTimeoutMs, int readTimeoutMs)
    throws IOException, java.net.ProtocolException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        conn.setReadTimeout(readTimeoutMs);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setDoOutput((data != null));
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        if (headers != null) {
            for (String hkey : headers.keySet()) {
                conn.setRequestProperty(hkey, headers.get(hkey));
            }
        }
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        boolean success = true;
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) success = false;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        if (!success) {
            System.out.println("WARNING: postRaw() on " + url + " failed with code=" +
                conn.getResponseCode() + "\n" + jtext + "\n============");
            throw new IOException("HTTP error code = " + conn.getResponseCode());
        }
        if (jtext.equals("")) return null;
        return jtext;
    }

    // JSON-friendly generic  (can pass null for headers and it will get set)
    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers)
    throws IOException, java.net.ProtocolException {
        return postJSON(url, data, headers, CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
    }

    // ml-service migration v2 (commit #6): timeout-aware overload.
    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers,
        int connectTimeoutMs, int readTimeoutMs)
    throws IOException, java.net.ProtocolException {
        if (headers == null) headers = new HashMap<String, String>();
        if (headers.get("Content-type") == null) headers.put("Content-type", "application/json");
        String rtn = postRaw(url, (data == null) ? (String)null : data.toString(), headers,
            connectTimeoutMs, readTimeoutMs);
        JSONObject jrtn = Util.stringToJSONObject(rtn);
        if (jrtn == null)
            throw new IOException("could not convert postRaw() to JSONObject: " + rtn);
        return jrtn;
    }
}

 succeeded in 399ms:
package org.ecocean;
/*  NOTE: due to the authentication header stuff, this is effectively IBEIS-specific but starting to break this out into generic. Still a work-in-progress.... see methods at the end of the file which will hopefully allow control over auth stuff better
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;

/*
   javax.ws.rs.core.UriBuilder https://stackoverflow.com/a/29053050/1525311
 */

public class RestClient {
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final int CONNECTION_TIMEOUT = 300000; // maybe this should be service-specific?

    public static JSONObject post(URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("POST", url, data);
    }

    public static JSONObject put(URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("PUT", url, data);
    }

    public static JSONObject get(URL url)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("GET", url, null);
    }

    public static JSONObject get(URL url, int timeout)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("GET", url, null, timeout);
    }

    public static JSONObject get(URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("GET", url, data);
    }

    // IBEIS-specifically, data gets posted as name-value pairs where name comes from the keys
    private static JSONObject anyMethod(String method, URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod(method, url, data, CONNECTION_TIMEOUT);
    }

    private static JSONObject anyMethod(String method, URL url, JSONObject data, int ctimeout)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        System.out.println("TRYING anyMethod(" + method + ") url -> " + url);
        // System.setProperty("http.keepAlive", "false");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setReadTimeout(ctimeout);
        conn.setConnectTimeout(ctimeout);
        conn.setDoOutput((data != null));
        conn.setDoInput(true);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Authorization", getAuthorizationHeader(url.toString()));
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(getPostDataString(data).getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        boolean success = true;
        // the 600 response here is IBEIS-specific, so we need to genericize this
        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK) &&
            (conn.getResponseCode() != 600)) {
            // conn.disconnnect();
            System.out.println("!!!!!!!!!!!!!!!!!!! [url = " + url.toString() +
                "] bad response code = " + conn.getResponseCode());
            success = false;
        }
        BufferedReader br = null;

        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        // conn.disconnect();
        if (!success) {
            System.out.println("========= anyMethod failed with code=" + conn.getResponseCode() +
                "\n" + jtext + "\n============");
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }
        if (jtext.equals("")) return null;
        return new JSONObject(jtext);

    }

    public static JSONObject postStream(URL url, InputStream in)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        conn.setReadTimeout(CONNECTION_TIMEOUT);
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream os = conn.getOutputStream();
        byte[] buffer = new byte[10240];
        int len;
// System.out.println("OK, begin<");
        while ((len = in.read(buffer)) != -1) {
            os.write(buffer, 0, len);
// System.out.write(buffer, 0, len);
        }
        in.close();
        os.flush();
        os.close();
        conn.connect();

        boolean success = true;
        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK)) {
            System.out.println("!!!!!!!!!!!!!!!!!!! bad response code = " + conn.getResponseCode());
            success = false;
        }
        if (!success) {
            JSONObject rtn = new JSONObject();
            rtn.put("error", conn.getResponseCode());
            return rtn;
        }

        BufferedReader br = null;

        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            success = false;
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        
        if (jtext.equals("")) return null;
        System.out.println("======================== postStream -> " + jtext);
        return new JSONObject(jtext);
    }

    // this chunk below is IBEIS-specific -- need to generalize for RestClient to be universal

    private static String getSignature(String key, byte[] messageToSendBytes)
    throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec keyHmac = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);

        mac.init(keyHmac);
        return new String(Base64.encodeBase64(mac.doFinal(messageToSendBytes)));
    }

    public static String getAuthorizationHeader(String url)
    throws NoSuchAlgorithmException, InvalidKeyException {
        String appName = "IBEIS";
        String appSecret = "CB73808F-A6F6-094B-5FCD-385EBAFF8FC0";

        return appName + ":" + getSignature(appSecret, url.getBytes());
    }

    // end IBEIS-specific

    private static String getPostDataString(JSONObject obj) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        Iterator<?> keys = obj.keys();

        while (keys.hasNext()) {
            String key = (String)keys.next();
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            try {
                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(obj.get(key).toString(), "UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                System.out.println("caught exception on key " + key + ": " + uee.toString());

 succeeded in 415ms:
32:public class RestClient {
36:    public static JSONObject post(URL url, JSONObject data)
48:    public static JSONObject get(URL url)
54:    public static JSONObject get(URL url, int timeout)
60:    public static JSONObject get(URL url, JSONObject data)
96:        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK) &&
120:            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
127:    public static JSONObject postStream(URL url, InputStream in)
152:        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK)) {
250:    public static JSONObject getJSONObject(URL url, String authUsername, String authPassword)
298:        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
316:            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
360:        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) success = false;
376:            throw new IOException("HTTP error code = " + conn.getResponseCode());
383:    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers)
389:    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers,

exec
/bin/bash -lc "rg -n \"WbiaRegisterRequest\\(\" src/test/java/org/ecocean/ia/plugin src/test/java src/main/java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "new WbiaRegisterRequest|registerOneByDto|buildForcedRequestMap|validateForcedResponse|WildbookIAMRegister" src/test/java src/main/java' in /mnt/c/Wildbook-clean2
 succeeded in 1994ms:
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:23: * {@code registerOneByDto} itself is exercised end-to-end by the
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:27:class WildbookIAMRegisterTest {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:36:    // --- buildForcedRequestMap -------------------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:38:    @Test void buildForcedRequestMapPopulatesAllLists() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:39:        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:50:    @Test void buildForcedRequestMapWrapsUuidsInFancyForm() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:51:        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(sampleDto());
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:58:    @Test void buildForcedRequestMapNullIndividualSerializesUnderscores() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:62:        HashMap<String, ArrayList> map = WildbookIAM.buildForcedRequestMap(dto);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:66:    // --- validateForcedResponse ------------------------------------------
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:68:    @Test void validateForcedResponseAcceptsMatchingId() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:71:        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:74:    @Test void validateForcedResponseRejectsNull() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:76:            () -> WildbookIAM.validateForcedResponse("x", null));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:79:    @Test void validateForcedResponseRejectsMissingArray() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:81:            () -> WildbookIAM.validateForcedResponse("x", new JSONObject()));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:84:    @Test void validateForcedResponseRejectsWrongLength() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:88:            () -> WildbookIAM.validateForcedResponse("a", resp));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:91:    @Test void validateForcedResponseRejectsIdMismatch() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:95:            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:98:    @Test void validateForcedResponseRejectsStatusSuccessFalse() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:103:            () -> WildbookIAM.validateForcedResponse("ann-uuid-1", resp));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:106:    @Test void validateForcedResponseTreatsStatusSuccessTrueAsOK() throws IOException {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:110:        WildbookIAM.validateForcedResponse("ann-uuid-1", resp);
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:19:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:37:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:49:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:29:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:78:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:89:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:674:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:696:    static void validateForcedResponse(String sentAnnotId, JSONObject resp) throws IOException {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:735:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:751:            IA.log("WARNING: WildbookIAM.registerOneByDto() property IBEISIARestUrlAddAnnotations not set");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:758:            IA.log("WARNING: WildbookIAM.registerOneByDto() iaAnnotationIds fetch failed: " +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:774:            IA.log("WARNING: WildbookIAM.registerOneByDto() malformed URL " + u);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:777:        HashMap<String, ArrayList> map = buildForcedRequestMap(dto);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:782:            IA.log("WARNING: WildbookIAM.registerOneByDto() POST failed: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:786:            validateForcedResponse(dto.annotationId, rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:788:            IA.log("WARNING: WildbookIAM.registerOneByDto() response invalid: " + ex.getMessage());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:799:     * Phase 0 of registerOneByDto: ensure the image referenced by
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:819:     * through {@link #registerOneByDto(WbiaRegisterRequest)}.
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:907:     * {@link #validateForcedResponse} in shape: expect a length-1
src/main/java/org/ecocean/StartupWildbook.java:696:     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
src/main/java/org/ecocean/StartupWildbook.java:723:            iam.registerOneByDto(dto);

 succeeded in 2038ms:
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:29:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:78:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:89:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:19:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:37:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:49:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:19:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:37:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:49:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:29:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:78:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:89:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:464:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/StartupWildbook.java:810:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(

exec
/bin/bash -lc "sed -n '1,110p' src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,130p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 361ms:
package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the new image-side fields on {@link WbiaRegisterRequest}
 * added in C5 ({@code imageUri}, {@code imageLatitude},
 * {@code imageLongitude}, {@code imageDateTimeMillis}) and the
 * backward-compat 7-arg delegating constructor that defaults all four
 * to {@code null}. (Empty-match-prospects design Track 1 C5.)
 */
class WbiaRegisterRequestImageFieldsTest {

    @Test void elevenArgConstructorAssignsAllFields() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-1",
            new int[] { 10, 20, 100, 200 },
            0.5d, "salamander_fire_adult", "indiv-1",
            "https://example.com/img.jpg", 12.34d, -56.78d, 1700000000000L);
        assertEquals("ann-1", dto.annotationId);
        assertEquals("ann-acm-1", dto.annotationAcmId);
        assertEquals("ma-acm-1", dto.mediaAssetAcmId);
        assertEquals(0.5d, dto.theta, 0.0);
        assertEquals("salamander_fire_adult", dto.iaClass);
        assertEquals("indiv-1", dto.individualName);
        assertEquals("https://example.com/img.jpg", dto.imageUri);
        assertEquals(12.34d, dto.imageLatitude, 0.0);
        assertEquals(-56.78d, dto.imageLongitude, 0.0);
        assertEquals(Long.valueOf(1700000000000L), dto.imageDateTimeMillis);
    }

    @Test void elevenArgConstructorAcceptsNullsForOptionalImageFields() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-2", "ann-acm-2", "ma-acm-2",
            new int[] { 0, 0, 1, 1 },
            0.0d, "iaClass", "____",
            "https://example.com/2.jpg", null, null, null);
        assertEquals("https://example.com/2.jpg", dto.imageUri);
        assertNull(dto.imageLatitude);
        assertNull(dto.imageLongitude);
        assertNull(dto.imageDateTimeMillis);
    }

    @Test void sevenArgConstructorDefaultsAllImageFieldsToNull() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-3", "ann-acm-3", "ma-acm-3",
            new int[] { 1, 2, 3, 4 },
            0.0d, "iaClass", "____");
        // Backward-compat path; image fields default to null so legacy
        // test fixtures don't need to know about the C5 additions.
        assertNull(dto.imageUri);
        assertNull(dto.imageLatitude);
        assertNull(dto.imageLongitude);
        assertNull(dto.imageDateTimeMillis);
        // Annotation-side fields are still populated.
        assertEquals("ann-3", dto.annotationId);
        assertEquals("ma-acm-3", dto.mediaAssetAcmId);
    }
}

 succeeded in 365ms:
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
            .put(makeFancy("u1"))
            .put(makeFancy("u2"));
        List<String> ids = WildbookIAM.parseAnnotationIdsArray(jids);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("u1"));
        assertTrue(ids.contains("u2"));
    }

    @Test void parseAnnotationIdsArraySkipsNonObjectEntries() {
        JSONArray jids = new JSONArray()

exec
/bin/bash -lc 'rg -n "class QueryCache|invalidateByName|addCachedQuery|getQueryByName|remove" src/main/java/org/ecocean/cache/QueryCache.java src/main/java/org/ecocean/cache' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,180p' src/main/java/org/ecocean/cache/QueryCache.java" in /mnt/c/Wildbook-clean2
 succeeded in 584ms:
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

 succeeded in 816ms:
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java:53:    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
src/main/java/org/ecocean/cache/QueryCache.java:62:    public void addCachedQuery(CachedQuery cq) {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:3:public class QueryCacheFactory {
src/main/java/org/ecocean/cache/QueryCacheFactory.java:23:     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
src/main/java/org/ecocean/cache/QueryCacheFactory.java:31:            if (cache != null) cache.invalidateByName(cacheName);
src/main/java/org/ecocean/cache/QueryCache.java:13:public class QueryCache {
src/main/java/org/ecocean/cache/QueryCache.java:22:    public CachedQuery getQueryByName(String name) {
src/main/java/org/ecocean/cache/QueryCache.java:46:    public void invalidateByName(String name)
src/main/java/org/ecocean/cache/QueryCache.java:53:    public void addCachedQuery(JSONObject jsonobj, String name, boolean persistAsStoredQuery,
src/main/java/org/ecocean/cache/QueryCache.java:62:    public void addCachedQuery(CachedQuery cq) {

exec
/bin/bash -lc "sed -n '1,140p' src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
 succeeded in 428ms:
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

    public long getExpirationTimeoutDuration() { return expirationTimeoutDuration; }

    public long getNextExpirationTimeout() { return nextExpirationTimeout; }

    public void refreshValues(String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.beginDBTransaction();
        StoredQuery sq = myShepherd.getStoredQuery(uuid);
        this.uuid = sq.getUUID();
        this.queryString = sq.getQueryString();
        this.name = sq.getName();
        this.correspondingIACacheName = sq.getCorrespondingIACacheName();
        this.expirationTimeoutDuration = sq.getExpirationTimeoutDuration();
        this.nextExpirationTimeout = sq.getNextExpirationTimeoutDuration();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }

    public JSONObject executeCollectionQuery(Shepherd myShepherd, boolean useSerializedJSONCache)
    throws IOException {
        // first, can we use serialized cache and if so, does it exist
        if (useSerializedJSONCache) {
            long time = System.currentTimeMillis();
            if ((jsonSerializedQueryResult == null) ||
                ((expirationTimeoutDuration > -1) && (time > nextExpirationTimeout))) {
                // System.out.println("*****Status 1");
                // check if we have a serialized cache
                // first if the cache is null but not expired, then just load it.
                // ((expirationTimeoutDuration==-1)||(((expirationTimeoutDuration>-1)&&(time<nextExpirationTimeout))))
                if ((jsonSerializedQueryResult == null) && getCacheFile().exists()) {
                    // load the cache file and return the JSONObject
                    // System.out.println("*****Status 1a");
                    nextExpirationTimeout = time + expirationTimeoutDuration;
                    return loadCachedJSON();
                }
                // gotta regen the cache
                else {
                    // System.out.println("cached file does NOT exist or has expired!");

exec
/bin/bash -lc "rg -n \"void invalidate|invalidate\\(\" src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
 succeeded in 471ms:
206:    public synchronized void invalidate()

exec
/bin/bash -lc "sed -n '196,220p' src/main/java/org/ecocean/cache/CachedQuery.java" in /mnt/c/Wildbook-clean2
 succeeded in 468ms:
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

codex
**Findings**

1. [WildbookIAM.java:864](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:864) - If the image POST reaches WBIA but `validateImageResponse` rejects the body, `iaImageIds` is not invalidated. Since `iaImageIdsStrict` may have just cached the pre-POST “image absent” list at [WildbookIAM.java:840](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:840), the next retry can re-use stale cache and POST the same image again until cache expiry or max attempts. This is bounded, not an infinite hot-loop, but it is a new duplicate-POST retry path. Safer pattern: invalidate `iaImageIds` after any POST returns a response, then classify the response.

2. [StartupWildbook.java:865](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:865) - The final abandon log still only says `last outcome=NETWORK_FAIL/RESPONSE_BAD`; it does not distinguish Phase 0 vs Phase 1. The Phase B warnings are phase-specific, but they do not include `annotationId`, so ops has to correlate logs by timing. That weakens the “parked annotation logs why” convention. Consider including `dto.annotationId` in Phase 0/Phase 1 warning logs, or otherwise carrying a phase label into the abandon log.

**Coverage Gaps**

The pure helper tests are useful, but the load-bearing behavior is still untested in-unit: `registerImageIfMissing` and `registerOneByDto` sequencing, no annotation POST after Phase 0 failure, both cache invalidations, and already-present skip behavior. Also, in this checkout the new test file is currently untracked, so make sure it is added before commit.

I did not find a Shepherd-across-network regression. The retry path remains bounded by the existing 10-attempt counter.
tokens used
122,243
**Findings**

1. [WildbookIAM.java:864](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:864) - If the image POST reaches WBIA but `validateImageResponse` rejects the body, `iaImageIds` is not invalidated. Since `iaImageIdsStrict` may have just cached the pre-POST “image absent” list at [WildbookIAM.java:840](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:840), the next retry can re-use stale cache and POST the same image again until cache expiry or max attempts. This is bounded, not an infinite hot-loop, but it is a new duplicate-POST retry path. Safer pattern: invalidate `iaImageIds` after any POST returns a response, then classify the response.

2. [StartupWildbook.java:865](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:865) - The final abandon log still only says `last outcome=NETWORK_FAIL/RESPONSE_BAD`; it does not distinguish Phase 0 vs Phase 1. The Phase B warnings are phase-specific, but they do not include `annotationId`, so ops has to correlate logs by timing. That weakens the “parked annotation logs why” convention. Consider including `dto.annotationId` in Phase 0/Phase 1 warning logs, or otherwise carrying a phase label into the abandon log.

**Coverage Gaps**

The pure helper tests are useful, but the load-bearing behavior is still untested in-unit: `registerImageIfMissing` and `registerOneByDto` sequencing, no annotation POST after Phase 0 failure, both cache invalidations, and already-present skip behavior. Also, in this checkout the new test file is currently untracked, so make sure it is added before commit.

I did not find a Shepherd-across-network regression. The retry path remains bounded by the existing 10-attempt counter.
