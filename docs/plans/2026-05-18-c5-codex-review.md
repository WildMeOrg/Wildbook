cat: /tmp/codex-context-bundle.md: No such file or directory
OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3e34-f0d6-7113-9ea0-ebd576c87763
--------
user
# Codex code-review: Track 1 C5 — WbiaRegisterRequest image fields + Phase A capture

Empty-match-prospects design Track 1 C5 (locked design Track 1
items 3 + 7): add four image-side fields to WbiaRegisterRequest
(imageUri, imageLatitude, imageLongitude, imageDateTimeMillis),
captured under Shepherd in Phase A of the WBIA registration
polling thread, plus extend Phase A eligibility with
`ma.isValidImageForIA()` and `WildbookIAM.validMediaAsset(ma)`
checks (same order as `sendMediaAssetsForceId` at
WildbookIAM.java:121-130).

Backward compatibility: the existing 7-arg constructor stays as
a thin delegate to the new 11-arg form with image fields defaulted
to null, so existing test fixtures don't need to change.

## Diff

diff --git a/src/main/java/org/ecocean/StartupWildbook.java b/src/main/java/org/ecocean/StartupWildbook.java
index 58092808b..ce0cc4b2a 100644
--- a/src/main/java/org/ecocean/StartupWildbook.java
+++ b/src/main/java/org/ecocean/StartupWildbook.java
@@ -772,6 +772,17 @@ public class StartupWildbook implements ServletContextListener {
             else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
             else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
                 reason = "validForIdentification returned false (bbox/iaClass/etc.)";
+            // Image-side eligibility for the new Phase 0 image-registration
+            // path. Order mirrors sendMediaAssetsForceId at
+            // WildbookIAM.java:121-130 so the polling thread parks the same
+            // media assets the legacy batch path would skip. Phase B trusts
+            // Phase A's verdict — these are not re-checked against the DB
+            // after the Shepherd closes. (Empty-match-prospects design
+            // Track 1 C5: WBIA Phase 0 eligibility extension.)
+            else if (ma.isValidImageForIA() != null && !ma.isValidImageForIA())
+                reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
+            else if (!org.ecocean.ia.plugin.WildbookIAM.validMediaAsset(ma))
+                reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
             if (reason != null) {
                 System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
                     " (ineligible: " + reason + ")");
@@ -785,10 +796,20 @@ public class StartupWildbook implements ServletContextListener {
             // Copy bbox into a fresh array so the DTO is fully detached.
             int[] bb = ann.getBbox();
             int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
+            // Capture image-side fields for Phase 0 (image registration).
+            // mediaAssetToUri returns null on null webURL — Phase A's
+            // validMediaAsset check above already rejected that case, so a
+            // null here would be a contract violation we'd want to see.
+            String imageUri = org.ecocean.ia.plugin.WildbookIAM.mediaAssetToUri(ma);
+            Double imageLatitude = ma.getLatitude();
+            Double imageLongitude = ma.getLongitude();
+            org.joda.time.DateTime dt = ma.getDateTime();
+            Long imageDateTimeMillis = (dt == null) ? null : dt.getMillis();
             org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
                 new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
                     ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
-                    ann.getTheta(), ann.getIAClass(), name);
+                    ann.getTheta(), ann.getIAClass(), name,
+                    imageUri, imageLatitude, imageLongitude, imageDateTimeMillis);
             shep.commitDBTransaction();
             return dto;
         } catch (Exception ex) {
diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index ea3dd5619..a59fc15d0 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -452,16 +452,44 @@ public class WildbookIAM extends IAPlugin {
         public final String iaClass;            // species/class string
         public final String individualName;     // "____" if absent
 
+        // Image-side fields. Phase 0 (image registration) sends these into
+        // WBIA's /api/image/json/ payload when the image isn't already
+        // known to WBIA. Captured under Shepherd in Phase A so Phase B has
+        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
+        public final String imageUri;            // double-encoded URL string; from mediaAssetToUri(ma)
+        public final Double imageLatitude;       // nullable; ma.getLatitude()
+        public final Double imageLongitude;      // nullable; ma.getLongitude()
+        public final Long   imageDateTimeMillis; // nullable epoch-ms; ma.getDateTime().getMillis()
+
+        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
+            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
+            String individualName, String imageUri, Double imageLatitude,
+            Double imageLongitude, Long imageDateTimeMillis) {
+            this.annotationId        = annotationId;
+            this.annotationAcmId     = annotationAcmId;
+            this.mediaAssetAcmId     = mediaAssetAcmId;
+            this.bbox                = bbox;
+            this.theta               = theta;
+            this.iaClass             = iaClass;
+            this.individualName      = individualName;
+            this.imageUri            = imageUri;
+            this.imageLatitude       = imageLatitude;
+            this.imageLongitude      = imageLongitude;
+            this.imageDateTimeMillis = imageDateTimeMillis;
+        }
+
+        /**
+         * Pre-C5 constructor preserved for backward-compatibility with
+         * test fixtures that don't exercise the Phase 0 image-registration
+         * path. Equivalent to the 11-arg constructor with all four
+         * image fields null. New production callers should use the
+         * 11-arg form.
+         */
         public WbiaRegisterRequest(String annotationId, String annotationAcmId,
             String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
             String individualName) {
-            this.annotationId    = annotationId;
-            this.annotationAcmId = annotationAcmId;
-            this.mediaAssetAcmId = mediaAssetAcmId;
-            this.bbox            = bbox;
-            this.theta           = theta;
-            this.iaClass         = iaClass;
-            this.individualName  = individualName;
+            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
+                iaClass, individualName, null, null, null, null);
         }
     }
 


## New test file:

```java
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
```


## Validation

- WbiaRegisterRequestImageFieldsTest: 3/3 (new)
- WildbookIAMRegisterTest: 17/17 (unchanged; still uses 7-arg constructor)
- LF line-endings verified on all touched files.

## Review scope

- Is the order of the new Phase A eligibility checks correct vs
  `sendMediaAssetsForceId`?
- Is Phase B's "trust Phase A's verdict" stance applied correctly
  (i.e., Phase B uses DTO scalars only, never re-touches `MediaAsset`)?
  Phase B implementation comes in C6; for now, this commit only
  affects Phase A.
- Constructor compatibility: did I miss any other call site of
  the 7-arg form that should migrate to the 11-arg form?
- DTO field ordering / immutability — anything off?
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll review this against the actual code paths, focusing on the eligibility order, DTO capture boundary, and constructor call sites. I’ll keep this read-only.
exec
/bin/bash -lc "sed -n '90,155p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '730,835p' src/main/java/org/ecocean/StartupWildbook.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '430,500p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff -- src/main/java/org/ecocean/StartupWildbook.java src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 539ms:
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

 succeeded in 538ms:
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

 succeeded in 539ms:
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
            // path. Order mirrors sendMediaAssetsForceId at
            // WildbookIAM.java:121-130 so the polling thread parks the same
            // media assets the legacy batch path would skip. Phase B trusts
            // Phase A's verdict — these are not re-checked against the DB
            // after the Shepherd closes. (Empty-match-prospects design
            // Track 1 C5: WBIA Phase 0 eligibility extension.)
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

 succeeded in 1153ms:
diff --git a/src/main/java/org/ecocean/StartupWildbook.java b/src/main/java/org/ecocean/StartupWildbook.java
index 58092808b..ce0cc4b2a 100644
--- a/src/main/java/org/ecocean/StartupWildbook.java
+++ b/src/main/java/org/ecocean/StartupWildbook.java
@@ -772,6 +772,17 @@ public class StartupWildbook implements ServletContextListener {
             else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
             else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
                 reason = "validForIdentification returned false (bbox/iaClass/etc.)";
+            // Image-side eligibility for the new Phase 0 image-registration
+            // path. Order mirrors sendMediaAssetsForceId at
+            // WildbookIAM.java:121-130 so the polling thread parks the same
+            // media assets the legacy batch path would skip. Phase B trusts
+            // Phase A's verdict — these are not re-checked against the DB
+            // after the Shepherd closes. (Empty-match-prospects design
+            // Track 1 C5: WBIA Phase 0 eligibility extension.)
+            else if (ma.isValidImageForIA() != null && !ma.isValidImageForIA())
+                reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
+            else if (!org.ecocean.ia.plugin.WildbookIAM.validMediaAsset(ma))
+                reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
             if (reason != null) {
                 System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
                     " (ineligible: " + reason + ")");
@@ -785,10 +796,20 @@ public class StartupWildbook implements ServletContextListener {
             // Copy bbox into a fresh array so the DTO is fully detached.
             int[] bb = ann.getBbox();
             int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
+            // Capture image-side fields for Phase 0 (image registration).
+            // mediaAssetToUri returns null on null webURL — Phase A's
+            // validMediaAsset check above already rejected that case, so a
+            // null here would be a contract violation we'd want to see.
+            String imageUri = org.ecocean.ia.plugin.WildbookIAM.mediaAssetToUri(ma);
+            Double imageLatitude = ma.getLatitude();
+            Double imageLongitude = ma.getLongitude();
+            org.joda.time.DateTime dt = ma.getDateTime();
+            Long imageDateTimeMillis = (dt == null) ? null : dt.getMillis();
             org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
                 new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
                     ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
-                    ann.getTheta(), ann.getIAClass(), name);
+                    ann.getTheta(), ann.getIAClass(), name,
+                    imageUri, imageLatitude, imageLongitude, imageDateTimeMillis);
             shep.commitDBTransaction();
             return dto;
         } catch (Exception ex) {
diff --git a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
index ea3dd5619..a59fc15d0 100644
--- a/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
+++ b/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java
@@ -452,16 +452,44 @@ public class WildbookIAM extends IAPlugin {
         public final String iaClass;            // species/class string
         public final String individualName;     // "____" if absent
 
+        // Image-side fields. Phase 0 (image registration) sends these into
+        // WBIA's /api/image/json/ payload when the image isn't already
+        // known to WBIA. Captured under Shepherd in Phase A so Phase B has
+        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
+        public final String imageUri;            // double-encoded URL string; from mediaAssetToUri(ma)
+        public final Double imageLatitude;       // nullable; ma.getLatitude()
+        public final Double imageLongitude;      // nullable; ma.getLongitude()
+        public final Long   imageDateTimeMillis; // nullable epoch-ms; ma.getDateTime().getMillis()
+
+        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
+            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
+            String individualName, String imageUri, Double imageLatitude,
+            Double imageLongitude, Long imageDateTimeMillis) {
+            this.annotationId        = annotationId;
+            this.annotationAcmId     = annotationAcmId;
+            this.mediaAssetAcmId     = mediaAssetAcmId;
+            this.bbox                = bbox;
+            this.theta               = theta;
+            this.iaClass             = iaClass;
+            this.individualName      = individualName;
+            this.imageUri            = imageUri;
+            this.imageLatitude       = imageLatitude;
+            this.imageLongitude      = imageLongitude;
+            this.imageDateTimeMillis = imageDateTimeMillis;
+        }
+
+        /**
+         * Pre-C5 constructor preserved for backward-compatibility with
+         * test fixtures that don't exercise the Phase 0 image-registration
+         * path. Equivalent to the 11-arg constructor with all four
+         * image fields null. New production callers should use the
+         * 11-arg form.
+         */
         public WbiaRegisterRequest(String annotationId, String annotationAcmId,
             String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
             String individualName) {
-            this.annotationId    = annotationId;
-            this.annotationAcmId = annotationAcmId;
-            this.mediaAssetAcmId = mediaAssetAcmId;
-            this.bbox            = bbox;
-            this.theta           = theta;
-            this.iaClass         = iaClass;
-            this.individualName  = individualName;
+            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
+                iaClass, individualName, null, null, null, null);
         }
     }
 

codex
The applied diff matches what you pasted. I’m checking the legacy image-send path and all DTO constructors now, because the main risks are ordering drift and missed call sites.
exec
/bin/bash -lc 'rg -n "mediaAssetToUri|image_unixtime_list|image_gps_lat_list|image_gps_lon_list|WbiaRegisterRequest" src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/StartupWildbook.java src/test/java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"sendMediaAssetsForceId|validMediaAsset\\(|isValidImageForIA\\(|new WbiaRegisterRequest|WbiaRegisterRequest\\(\" src/main/java src/test/java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '155,250p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '250,430p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
 succeeded in 477ms:
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

 succeeded in 490ms:
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

 succeeded in 849ms:
src/main/java/org/ecocean/StartupWildbook.java:705:        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:747:    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
src/main/java/org/ecocean/StartupWildbook.java:800:            // mediaAssetToUri returns null on null webURL — Phase A's
src/main/java/org/ecocean/StartupWildbook.java:803:            String imageUri = org.ecocean.ia.plugin.WildbookIAM.mediaAssetToUri(ma);
src/main/java/org/ecocean/StartupWildbook.java:808:            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
src/main/java/org/ecocean/StartupWildbook.java:809:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:109:        map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:110:        map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:111:        map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:134:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:135:            map.get("image_gps_lat_list").add(ma.getLatitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:136:            map.get("image_gps_lon_list").add(ma.getLongitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:139:                map.get("image_unixtime_list").add(null);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:141:                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:164:                    map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:165:                    map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:166:                    map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:196:        map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:197:        map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:198:        map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:217:            map.get("image_uri_list").add(mediaAssetToUri(ma));
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:219:            map.get("image_gps_lat_list").add(ma.getLatitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:220:            map.get("image_gps_lon_list").add(ma.getLongitude());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:223:                map.get("image_unixtime_list").add(null);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:225:                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000)); // IA wants seconds since epoch
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:250:                    map.put("image_unixtime_list", new ArrayList<Integer>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:251:                    map.put("image_gps_lat_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:252:                    map.put("image_gps_lon_list", new ArrayList<Double>());
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:446:    public static final class WbiaRegisterRequest {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:459:        public final String imageUri;            // double-encoded URL string; from mediaAssetToUri(ma)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:464:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:674:    static HashMap<String, ArrayList> buildForcedRequestMap(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:722:    public WbiaRegisterOutcome registerOneByDto(WbiaRegisterRequest dto) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:927:     * can call it from Phase A while building the {@link WbiaRegisterRequest}
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:930:    public static String mediaAssetToUri(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:946:        if (mediaAssetToUri(ma) == null) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:948:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:14: * Pure-function coverage of {@link WildbookIAM#mediaAssetToUri(MediaAsset)}
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:21:        assertNull(WildbookIAM.mediaAssetToUri(null));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:27:        assertNull(WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:35:            WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:43:            WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:51:            WildbookIAM.mediaAssetToUri(ma));
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:14:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:29:    private static WbiaRegisterRequest sampleDto() {
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:6:import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:10: * Coverage of the new image-side fields on {@link WbiaRegisterRequest}
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:16:class WbiaRegisterRequestImageFieldsTest {
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:19:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:37:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:49:        WbiaRegisterRequest dto = new WbiaRegisterRequest(

 succeeded in 1665ms:
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:30:        return new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java:59:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:19:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:37:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:49:        WbiaRegisterRequest dto = new WbiaRegisterRequest(
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:121:            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:127:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:178:    public JSONObject sendMediaAssetsForceId(ArrayList<MediaAsset> mas, boolean checkFirst)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:207:            if (ma.isValidImageForIA() != null && !ma.isValidImageForIA()) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:209:                    "WARNING: WildbookIAM.sendMediaAssetsForceId() found a corrupt or otherwise invalid MediaAsset with Id: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:213:            if (!validMediaAsset(ma)) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:214:                IA.log("WARNING: WildbookIAM.sendMediaAssetsForceId() skipping invalid " + ma);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:230:                    IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() is sending " + sendSize +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:234:                    System.out.println(batchCt + "]  sendMediaAssetsForceId() -> " + rtn);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:242:                        IA.log("INFO: WildbookIAM.sendMediaAssetsForceId() updated " + numChanged +
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:464:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:488:        public WbiaRegisterRequest(String annotationId, String annotationAcmId,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:942:    public static boolean validMediaAsset(MediaAsset ma) {
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:948:                "WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " +
src/main/java/org/ecocean/StartupWildbook.java:776:            // path. Order mirrors sendMediaAssetsForceId at
src/main/java/org/ecocean/StartupWildbook.java:782:            else if (ma.isValidImageForIA() != null && !ma.isValidImageForIA())
src/main/java/org/ecocean/StartupWildbook.java:783:                reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
src/main/java/org/ecocean/StartupWildbook.java:784:            else if (!org.ecocean.ia.plugin.WildbookIAM.validMediaAsset(ma))
src/main/java/org/ecocean/StartupWildbook.java:809:                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
src/main/java/org/ecocean/AcmIdBot.java:41:                    if (asset != null && asset.isValidImageForIA() == null) {
src/main/java/org/ecocean/AcmIdBot.java:45:                        if (!asset.isValidImageForIA()) numInvalidForIA++;
src/main/java/org/ecocean/AcmIdBot.java:48:                    if (asset != null && asset.isValidImageForIA()) {
src/main/java/org/ecocean/media/MediaAsset.java:1514:    public Boolean isValidImageForIA() {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:652:            if (ma.isValidImageForIA() != null) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:653:                if (ma.isValidImageForIA().booleanValue()) numAllowedIA++;
src/main/java/org/ecocean/servlet/importer/ImportTask.java:658:                if ((ma.isValidImageForIA() == null) || !ma.isValidImageForIA().booleanValue()) {

exec
/bin/bash -lc "sed -n '760,830p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '640,760p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '910,970p' src/main/java/org/ecocean/ia/plugin/WildbookIAM.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,110p' src/test/java/org/ecocean/ia/plugin/WildbookIAMRegisterTest.java && sed -n '1,90p' src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 515ms:
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

 succeeded in 513ms:
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

 succeeded in 479ms:

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

 succeeded in 472ms:
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

codex
So far the image-specific check order is consistent with `sendMediaAssetsForceId`: `isValidImageForIA()==false` is checked before `validMediaAsset(ma)`. I’m looking for secondary issues around the Phase A boundary and test coverage.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '196,222p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/StartupWildbook.java | sed -n '760,815p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"Integer getWidth|int getWidth|getWidth\\(|Integer getHeight|int getHeight|getHeight\\(|webURL\\(|getDateTime\\(|getLatitude\\(|getLongitude\\(\" src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/plugin/WildbookIAM.java | sed -n '446,492p'" in /mnt/c/Wildbook-clean2
 succeeded in 473ms:
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
   776	            // path. Order mirrors sendMediaAssetsForceId at
   777	            // WildbookIAM.java:121-130 so the polling thread parks the same
   778	            // media assets the legacy batch path would skip. Phase B trusts
   779	            // Phase A's verdict — these are not re-checked against the DB
   780	            // after the Shepherd closes. (Empty-match-prospects design
   781	            // Track 1 C5: WBIA Phase 0 eligibility extension.)
   782	            else if (ma.isValidImageForIA() != null && !ma.isValidImageForIA())
   783	                reason = "MediaAsset.isValidImageForIA() == false (corrupt/invalid)";
   784	            else if (!org.ecocean.ia.plugin.WildbookIAM.validMediaAsset(ma))
   785	                reason = "MediaAsset failed validMediaAsset (mime/dims/url)";
   786	            if (reason != null) {
   787	                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
   788	                    " (ineligible: " + reason + ")");
   789	                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
   790	                shep.commitDBTransaction();
   791	                return null;
   792	            }
   793	            // Resolve the individual name now while the Shepherd is open;
   794	            // Phase B has no DB access.
   795	            String name = ann.findIndividualId(shep);
   796	            // Copy bbox into a fresh array so the DTO is fully detached.
   797	            int[] bb = ann.getBbox();
   798	            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
   799	            // Capture image-side fields for Phase 0 (image registration).
   800	            // mediaAssetToUri returns null on null webURL — Phase A's
   801	            // validMediaAsset check above already rejected that case, so a
   802	            // null here would be a contract violation we'd want to see.
   803	            String imageUri = org.ecocean.ia.plugin.WildbookIAM.mediaAssetToUri(ma);
   804	            Double imageLatitude = ma.getLatitude();
   805	            Double imageLongitude = ma.getLongitude();
   806	            org.joda.time.DateTime dt = ma.getDateTime();
   807	            Long imageDateTimeMillis = (dt == null) ? null : dt.getMillis();
   808	            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
   809	                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
   810	                    ann.getId(), ann.getAcmId(), ma.getAcmId(), bbCopy,
   811	                    ann.getTheta(), ann.getIAClass(), name,
   812	                    imageUri, imageLatitude, imageLongitude, imageDateTimeMillis);
   813	            shep.commitDBTransaction();
   814	            return dto;
   815	        } catch (Exception ex) {

 succeeded in 478ms:
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

 succeeded in 479ms:
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

 succeeded in 521ms:
516:    public double getWidth() {
520:        return iattr.getWidth();
523:    public double getHeight() {
527:        return iattr.getHeight();
539:    public DateTime getDateTime() {
542:            DateTime dt = this.store.getDateTime(this);
549:        return getMetadata().getDateTime();
561:       like getDateTime() this is considered "definitive" -- so it must resolve differences in metadata vs other (e.g. encounter etc) values
567:    public Double getLatitude() {
572:        return getMetadata().getLatitude();
583:    public Double getLongitude() {
588:        return getMetadata().getLongitude();
694:    public URL webURL() {
707:        return store.webURL(this);
716:        return ma.webURL();
755:                return new URL(store.webURL(this).getProtocol(), containerName, 80,
756:                        store.webURL(this).getFile());
926:        DateTime dt = getDateTime();
945:            jobj.put("userLatitude", this.getLatitude());
946:            jobj.put("userLongitude", this.getLongitude());
1598:            DateTime dt = this.getDateTime();

exec
/bin/bash -lc "sed -n '500,590p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 444ms:
        return store.cacheLocal(this, force);
    }

    // indisputable attributes about the image (e.g. type, dimensions, colorspaces etc)
    // this is (seemingly?) always derived from MediaAssetMetadata, so .. yeah. make sure that is set (see note by getMetadata() )
    public ImageAttributes getImageAttributes() {
        if ((metadata == null) || (metadata.getData() == null)) return null;
        JSONObject attr = metadata.getData().optJSONObject("attributes");
        if (attr == null) return null;
        double w = attr.optDouble("width", -1);
        double h = attr.optDouble("height", -1);
        String type = attr.optString("contentType");
        if ((w < 1) || (h < 1)) return null;
        return new ImageAttributes(w, h, type);
    }

    public double getWidth() {
        ImageAttributes iattr = getImageAttributes();

        if (iattr == null) return 0;
        return iattr.getWidth();
    }

    public double getHeight() {
        ImageAttributes iattr = getImageAttributes();

        if (iattr == null) return 0;
        return iattr.getHeight();
    }

    public void addToMetadata(String key, String value) {
        if (metadata == null) metadata = new MediaAssetMetadata();
        metadata.addDatum(key, value);
    }

    /**
       this function resolves (how???) various difference in "when" this image was taken.  it might use different metadata (in EXIF etc) and/or human-input
       FOR NOW: we rely first on (a) metadata.attributes.dateTime (as iso8601 string), then (b) crawl metadata.exif for something date-y
     */
    public DateTime getDateTime() {
        if (this.userDateTime != null) return this.userDateTime;
        if (this.store != null) {
            DateTime dt = this.store.getDateTime(this);
            if (dt != null) return dt;
        }
        if (getMetadata() == null) return null;
        String adt = getMetadata().getAttributes().optString("dateTime", null);
        if (adt != null) return DateTime.parse(adt); // lets hope it is in iso8601 format like it should be!
        // meh, gotta find it the hard way then...
        return getMetadata().getDateTime();
    }

    public void setUserDateTime(DateTime dt) {
        this.userDateTime = dt;
    }

    public DateTime getUserDateTime() {
        return this.userDateTime;
    }

    /**
       like getDateTime() this is considered "definitive" -- so it must resolve differences in metadata vs other (e.g. encounter etc) values
     */
    public Double getUserLatitude() {
        return this.userLatitude;
    }

    public Double getLatitude() {
        if (this.userLatitude != null) return this.userLatitude;
        if (getMetadata() == null) return null;
        double lat = getMetadata().getAttributes().optDouble("latitude");
        if (!Double.isNaN(lat)) return lat;
        return getMetadata().getLatitude();
    }

    public void setUserLatitude(Double lat) {
        this.userLatitude = lat;
    }

    public Double getUserLongitude() {
        return this.userLongitude;
    }

    public Double getLongitude() {
        if (this.userLongitude != null) return this.userLongitude;
        if (getMetadata() == null) return null;
        double lon = getMetadata().getAttributes().optDouble("longitude");
        if (!Double.isNaN(lon)) return lon;
        return getMetadata().getLongitude();
    }


exec
/bin/bash -lc "sed -n '1,90p' src/main/java/org/ecocean/AcmIdBot.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"setAcmId\\(|getAcmId\\(\\).*getUUID|getUUID\\(\\).*acm|mediaAssetAcmId|MediaAsset.*acmId\" src/main/java/org/ecocean src/test/java/org/ecocean | head -n 80" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"class Annotation|String getAcmId|setAcmId|String getUUID|getUUID\\(\" src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 408ms:
package org.ecocean;

import java.lang.Runnable;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.List;
import javax.jdo.Query;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;

/*
 * Wildbook requires shared UUIDs (a.k.a. acmID) between MediaAsset objects in the Wildbook database and images in WBIA. AcmIDs are a prerequisite for
 * detection and therefore can be a blocker in the IA pipeline if for any reason WBIA times out or is otherwise unavailable to provide an acmId to
 * Wildbook when new data is submitted. This bot provides some automated backend healing to get images registered if for any reason acmId registration
 * fails. It first checks bulk ImportTasks for appropriate images that may be missing an acmId, and then it checks Encounters submitted within the
 * past 24 hours.
 *
 */
public class AcmIdBot {
    static String context = "context0";

    private static void fixFeats(List<Feature> feats, Shepherd myShepherd, String summaryMessage,
                                 int maxFixes) {
        if (feats != null && feats.size() > 0) {
            int numRecommended = feats.size();
            int numValidIAFixes = 0;
            int numAcmIdFixesSent = 0;
            int numAcmIdFixesSuccessful = 0;
            int numInvalidForIA = 0;
            for (Feature feat : feats) {
                MediaAsset asset = feat.getMediaAsset();
                myShepherd.setAction("AcmIDBot_" + summaryMessage + "_asset_" + asset.getId());
                try {
                    // is this an appropriate image type for acm ID registration?
                    if (asset != null && asset.isValidImageForIA() == null) {
                        asset.validateSourceImage();
                        myShepherd.updateDBTransaction();
                        numValidIAFixes++;
                        if (!asset.isValidImageForIA()) numInvalidForIA++;
                    }
                    // if appropriate let's send it
                    if (asset != null && asset.isValidImageForIA()) {
                        // let's check for child media assets - lack of these could impact acmID registration
                        if (!asset.hasFamily(myShepherd)) asset.updateStandardChildren();
                        ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
                        fixMe.add(asset);
                        IBEISIA.sendMediaAssetsNew(fixMe, context);
                        numAcmIdFixesSent++;
                        if (asset.getAcmId() != null) {
                            numAcmIdFixesSuccessful++;
                            // allow the bot to determine how many fixes it wants the logic to consider before exiting
                            // helps keep the bots attention back on newer data
                            if (numAcmIdFixesSuccessful >= maxFixes) break;
                        }
                    }
                } catch (Exception ec) {
                    System.out.println("Exception in AcmIdBot.fixFeats");
                    ec.printStackTrace();
                    // as of now we don't know of a commonality that would suggest a fix
                    if (ec.toString().contains("HTTP error code : 500")) {
                        asset.setIsValidImageForIA(false);
                        myShepherd.updateDBTransaction();
                        numValidIAFixes--;
                        numInvalidForIA++;
                    }
                }
            }
            System.out.println(summaryMessage);
            System.out.println("...candidate fixes: " + numRecommended);
            System.out.println("......num valid for IA checks performed: " + numValidIAFixes);
            System.out.println(".........num ultimately invalid for IA: " + numInvalidForIA);
            System.out.println("......num media assets sent for ACM ID fixing: " +
                numAcmIdFixesSent);
            System.out.println("......num media assets successfully updated with Acm ID: " +
                numAcmIdFixesSuccessful);
        }
    }

    // background workers
    public static boolean startServices(String context) {
        startCollector(context);
        return true;
    }


 succeeded in 573ms:
src/main/java/org/ecocean/Annotation.java:29:public class Annotation extends Base implements java.io.Serializable {
src/main/java/org/ecocean/Annotation.java:312:    public void setAcmId(String id) {
src/main/java/org/ecocean/Annotation.java:317:    public String getAcmId() {
src/main/java/org/ecocean/Annotation.java:387:    public String getUUID() {
src/main/java/org/ecocean/media/MediaAsset.java:117:        if (this.acmId == null) this.acmId = this.getUUID();
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/media/MediaAsset.java:136:    public String getAcmId() {
src/main/java/org/ecocean/media/MediaAsset.java:235:    public String getUUID() {
src/main/java/org/ecocean/media/MediaAsset.java:665:    class AnnotationPositionalComparator implements Comparator<Annotation> {
src/main/java/org/ecocean/media/MediaAsset.java:696:            System.out.println("MediaAsset " + this.getUUID() + " has no store!");
src/main/java/org/ecocean/media/MediaAsset.java:998:        j.put("uuid", getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1694:        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
src/main/java/org/ecocean/media/MediaAsset.java:1695:        return this.uuid.equals(two.getUUID());
src/main/java/org/ecocean/media/MediaAsset.java:1727:        jgen.writeStringField("uuid", this.getUUID());

 succeeded in 1493ms:
src/main/java/org/ecocean/acm/AcmUtil.java:13:    public static int rectifyMediaAssetIds(List<MediaAsset> mas, List<String> acmIds) {
src/main/java/org/ecocean/acm/AcmUtil.java:25:                IA.log("INFO: AcmUtil.rectifyMediaAssetIds() has null acmId response for " +
src/main/java/org/ecocean/acm/AcmUtil.java:28:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:31:                IA.log("ERROR: AcmUtil.rectifyMediaAssetIds() changing acmId from " +
src/main/java/org/ecocean/acm/AcmUtil.java:33:                mas.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:56:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/acm/AcmUtil.java:61:                anns.get(i).setAcmId(acmIds.get(i));
src/main/java/org/ecocean/media/MediaAsset.java:132:    public void setAcmId(String id) {
src/main/java/org/ecocean/identity/IBEISIA.java:146:            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/identity/IBEISIA.java:1271:        ann.setAcmId(fromFancyUUID(iaResult.optJSONObject("uuid")));
src/main/java/org/ecocean/identity/IBEISIA.java:2155:            ann.setAcmId(acmId);
src/main/java/org/ecocean/identity/IBEISIA.java:2202:        ma.setAcmId(maUUID);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:132:            String uuidToSend = (ma.getAcmId() != null) ? ma.getAcmId() : ma.getUUID();
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:153:                            "WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:156:                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:158:                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:238:                            "WARNING: WildbookIAM.sendMediaAssetsForceId() could not get list of acmIds from response: "
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:241:                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:243:                            " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:449:        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:465:            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:470:            this.mediaAssetAcmId     = mediaAssetAcmId;
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:489:            String mediaAssetAcmId, int[] bbox, double theta, String iaClass,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:491:            this(annotationId, annotationAcmId, mediaAssetAcmId, bbox, theta,
src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:682:        map.get("image_uuid_list").add(toFancyUUID(dto.mediaAssetAcmId));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:331:                ann.setAcmId(ann.getId());
src/main/java/org/ecocean/shepherd/core/Shepherd.java:4547:    public ArrayList<MediaAsset> getMediaAssetsWithACMId(String acmId) {
src/main/java/org/ecocean/Annotation.java:312:    public void setAcmId(String id) {
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:26:        assertEquals("ma-acm-1", dto.mediaAssetAcmId);
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:61:        assertEquals("ma-acm-3", dto.mediaAssetAcmId);

exec
/bin/bash -lc "sed -n '95,145p' src/main/java/org/ecocean/media/MediaAsset.java && sed -n '220,245p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 448ms:

    /**
     * To be called by AssetStore factory method.
     */

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
        // OpenSearch reindexer picks up detection-status changes and so the
        // stale-job reconciler in commit #12 has a real "when did this
        // detectionStatus change" timestamp via REVISION.
        this.setRevision();
    }

    public String getIdentificationStatus() {
        return this.identificationStatus;
    }

    public void setIdentificationStatus(String status) {
        this.identificationStatus = status;
    }

    // this is for Annotation mostly?  provides are reproducible uuid based on the MediaAsset id
    public String getUUID() {
        if (uuid != null) return uuid;
        // UUID v3 seems to take an arbitrary bytearray in, so we construct one that is basically "Ma____" where "____" is the int id
        return generateUUIDFromId();
    }

    public void setUUID(String u) {
        uuid = u;
    }

    /* note: this is used for *new* MediaAssets (via constructor), so we want it to *always* give us something.

exec
/bin/bash -lc "rg -n \"boolean isMimeTypeMajor|isMimeTypeMajor\\(\" src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 478ms:
1366:    public boolean isMimeTypeMajor(String type) {

exec
/bin/bash -lc "sed -n '1348,1375p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 446ms:
            if (keywords.contains(k)) keywords.remove(k);
        }
    }

    // if we dont have the Annotation... which kinda sucks but okay
    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd) {
        return toHtmlElement(request, myShepherd, null);
    }

    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd, Annotation ann) {
        if (store == null)
            return "<!-- ERROR: MediaAsset.toHtmlElement() has no .store value for " +
                       this.toString() + " -->";
        return store.mediaAssetToHtmlElement(this, request, myShepherd, ann);
    }

    // piggybacks off metadata, so that must be set first, otherwise it matches corresponding mime type (major/minor), case-insensitive
    // note: for return values, we standardize on all-lowercase. so there.
    public boolean isMimeTypeMajor(String type) {
        if (type == null) return false;
        return type.toLowerCase().equals(this.getMimeTypeMajor());
    }

    public boolean isMimeTypeMinor(String type) {
        if (type == null) return false;
        return type.toLowerCase().equals(this.getMimeTypeMinor());
    }


exec
/bin/bash -lc "rg -n \"String getMimeTypeMajor|String getMimeTypeMinor|getMimeTypeMajor\\(\" src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 479ms:
1368:        return type.toLowerCase().equals(this.getMimeTypeMajor());
1376:    public String getMimeTypeMajor() {
1383:    public String getMimeTypeMinor() {

exec
/bin/bash -lc "sed -n '1375,1395p' src/main/java/org/ecocean/media/MediaAsset.java" in /mnt/c/Wildbook-clean2
 succeeded in 441ms:

    public String getMimeTypeMajor() {
        String[] mt = this.getMimeType();

        if ((mt == null) || (mt.length < 1)) return null;
        return mt[0];
    }

    public String getMimeTypeMinor() {
        String[] mt = this.getMimeType();

        if ((mt == null) || (mt.length < 2)) return null;
        return mt[1];
    }

    public String[] getMimeType() {
        if (this.metadata == null) return null;
        String mt = this.metadata.getAttributes().optString("contentType", null); // note: getAttributes always  returns a JSONObject (even if empty)
        if (mt == null) return null;
        return mt.toLowerCase().split("/");
    }

exec
/bin/bash -lc 'rg -n "Empty-match-prospects|Phase 0|Track 1 C5|imageDateTimeMillis|image registration" .' in /mnt/c/Wildbook-clean2
 succeeded in 4973ms:
./docs/plans/2026-05-18-wbia-image-registration-design.md:66:public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
./docs/plans/2026-05-18-wbia-image-registration-design.md:158:        dto.imageDateTimeMillis == null ? null
./docs/plans/2026-05-18-wbia-image-registration-design.md:159:                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
./docs/plans/2026-05-18-wbia-image-registration-design.md:172:Add Phase 0 at the entry; existing Phase 1 (annotation registration)
./docs/plans/2026-05-18-wbia-image-registration-design.md:179:    // ---- Phase 0: ensure WBIA knows about the image ----
./docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
./docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
./docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
./docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
./docs/plans/2026-05-18-wbia-image-registration-design.md:217:Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
./docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-wbia-image-registration-design.md:227:include this image), trigger Phase 0 again, attempt to register the
./docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
./docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
./docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-empty-match-prospects-design.md:180:- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-design.md:239:   distinguishes phase via existing log string (Phase 0 / 1 / 2).
./docs/plans/2026-05-18-empty-match-prospects-design.md:247:public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-design.md:285:- After successful image POST in Phase 0:
./docs/plans/2026-05-18-empty-match-prospects-design.md:874:`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:224:- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:289:   distinguishing the phase via the existing log string (Phase 0 vs
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:298:public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:479:gains coverage for the Phase 0 image POST, the cache invalidation
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:697:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:707:docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6599:/bin/bash -lc 'rg -n "cache|invalidate|iaImageIds|iaAnnotationIds|Phase 0|Phase B" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-18-empty-match-prospects-design.md' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6609:docs/plans/2026-05-18-empty-match-prospects-design.md:114:   distinguishing the phase via the existing log string (Phase 0 vs
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6610:docs/plans/2026-05-18-empty-match-prospects-design.md:304:gains coverage for the Phase 0 image POST, the cache invalidation
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6617:docs/plans/2026-05-18-wbia-image-registration-design.md:172:Add Phase 0 at the entry; existing Phase 1 (annotation registration)
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6618:docs/plans/2026-05-18-wbia-image-registration-design.md:179:    // ---- Phase 0: ensure WBIA knows about the image ----
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6625:docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6626:docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6627:docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6628:docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6629:docs/plans/2026-05-18-wbia-image-registration-design.md:217:Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6630:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6632:docs/plans/2026-05-18-wbia-image-registration-design.md:227:include this image), trigger Phase 0 again, attempt to register the
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6638:docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6640:docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
./docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6642:docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:335:- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:394:   distinguishes phase via existing log string (Phase 0 / 1 / 2).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:402:public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:440:- After successful image POST in Phase 0:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:1012:`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:6852:/bin/bash -lc "rg -n \"WbiaRegister|wbiaRegister|iaAnnotationIdsStrict|sendAnnotations|park|NETWORK_FAIL|Phase 0|Phase 1|Phase 2|mediaAssetToUri|parseImage\" src/main/java/org/ecocean/ia src/main/java/org/ecocean/ia/plugin src/main/java/org/ecocean -g '*.java'" in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7035:    66	public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7127:   158	        dto.imageDateTimeMillis == null ? null
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7128:   159	                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7141:   172	Add Phase 0 at the entry; existing Phase 1 (annotation registration)
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7148:   179	    // ---- Phase 0: ensure WBIA knows about the image ----
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7179:   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7180:   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7181:   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7182:   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v5.md:7186:   217	Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:297:- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:356:   distinguishes phase via existing log string (Phase 0 / 1 / 2).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:364:public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:402:- After successful image POST in Phase 0:
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v4.md:868:`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:258:- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:317:   distinguishes phase via existing log string (Phase 0 / 1 / 2).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:325:public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:345:- After successful image POST in Phase 0, call
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:748:`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:930:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1262:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6667:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1376:docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9448:   224	After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1517:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:697:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1662:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6599:/bin/bash -lc 'rg -n "cache|invalidate|iaImageIds|iaAnnotationIds|Phase 0|Phase B" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-18-empty-match-prospects-design.md' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v3.md:1675:docs/plans/2026-05-18-empty-match-prospects-codex-review.md:6630:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:247:- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:306:   distinguishes phase via existing log string (Phase 0 / 1 / 2).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:314:public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:326:- After successful image POST in Phase 0, call
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:589:`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6667:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:6679:docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8926:    72	- Detection-time WBIA image registration. Same rationale as the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8985:   131	   distinguishes phase via existing log string (Phase 0 / 1 / 2).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:8993:   139	public final Long imageDateTimeMillis;
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9005:   151	- After successful image POST in Phase 0, call
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9288:   414	`WildbookIAMRegisterTest` gains 8–10 cases covering Phase 0
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9434:   210	- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9435:   211	- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9436:   212	- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9437:   213	- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9441:   217	Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9448:   224	After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-empty-match-prospects-codex-review-v2.md:9451:   227	include this image), trigger Phase 0 again, attempt to register the
./docs/plans/2026-05-18-c4-codex-review.md:178:Empty-match-prospects design Track 1 C4 (per your round-1 C2 review
./docs/plans/2026-05-18-c4-codex-review.md:232:+     * <p>(Empty-match-prospects design Track 1 C4: extracted from
./docs/plans/2026-05-18-c4-codex-review.md:279: * report which WBIA response was malformed. (Empty-match-prospects
./docs/plans/2026-05-18-c4-codex-review.md:393:     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c4-codex-review.md:401:     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
./docs/plans/2026-05-18-c4-codex-review.md:467:     * <p>(Empty-match-prospects design Track 1 C4: extracted from
./docs/plans/2026-05-18-c4-codex-review.md:974: * report which WBIA response was malformed. (Empty-match-prospects
./docs/plans/2026-05-18-c4-codex-review.md:1030: * is exercised end-to-end by Phase 0 of the WBIA registration polling
./docs/plans/2026-05-18-c4-codex-review.md:1032: * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
./docs/plans/2026-05-18-c4-codex-review.md:1173:    22	 * report which WBIA response was malformed. (Empty-match-prospects
./docs/plans/2026-05-18-c4-codex-review.md:1244:   900	     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./docs/plans/2026-05-18-c4-codex-review.md:1273:   546	     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c4-codex-review.md:1281:   554	     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
./docs/plans/2026-05-18-c4-codex-review.md:1347:   620	     * <p>(Empty-match-prospects design Track 1 C4: extracted from
./docs/plans/2026-05-18-c3-codex-review.md:178:Empty-match-prospects design Track 1 C3 (per locked design's
./docs/plans/2026-05-18-c3-codex-review.md:181:15-min QueryCache under key `"iaImageIds"`. Used by the new Phase 0
./docs/plans/2026-05-18-c3-codex-review.md:198:+     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c3-codex-review.md:206:+     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
./docs/plans/2026-05-18-c3-codex-review.md:299: * is exercised end-to-end by Phase 0 of the WBIA registration polling
./docs/plans/2026-05-18-c3-codex-review.md:301: * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
./docs/plans/2026-05-18-c3-codex-review.md:369:- Phase 0 will call `safeInvalidate(context, "iaImageIds")` after
./docs/plans/2026-05-18-c3-codex-review.md:468:     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c3-codex-review.md:476:     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
./docs/plans/2026-05-18-c3-codex-review.md:1905:     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./docs/plans/2026-05-18-c3-codex-review.md:1947:    16	 * is exercised end-to-end by Phase 0 of the WBIA registration polling
./docs/plans/2026-05-18-c3-codex-review.md:1949:    18	 * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
./docs/plans/2026-05-18-c3-codex-review.md:2097:   557	     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c3-codex-review.md:2105:   565	     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
./docs/plans/2026-05-18-c3-codex-review.md:2242:The key behavior does match the annotation strict method structurally: cache lookup, strict parse on hit, fetch and throw on API failure/null, best-effort cache store, strict parse on fresh result. I’m checking the Phase 0 call shape next because the cache only helps if the subsequent POST invalidates the same name.
./docs/plans/2026-05-18-c3-codex-review.md:2244:/bin/bash -lc 'rg -n "Phase 0|iaImageIdsStrict|safeInvalidate|iaImageIds|REGISTERED_IMAGE|image already|already registered|WbiaRegisterOutcome" docs/plans/2026-05-09-ml-service-migration-v2.md docs/plans/2026-05-18-wbia-image-registration-design.md src/main/java/org/ecocean/ia/plugin/WildbookIAM.java src/main/java/org/ecocean/ia' in /mnt/c/Wildbook-clean2
./docs/plans/2026-05-18-c3-codex-review.md:2317:public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
./docs/plans/2026-05-18-c3-codex-review.md:2409:        dto.imageDateTimeMillis == null ? null
./docs/plans/2026-05-18-c3-codex-review.md:2410:                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
./docs/plans/2026-05-18-c3-codex-review.md:2426:Add Phase 0 at the entry; existing Phase 1 (annotation registration)
./docs/plans/2026-05-18-c3-codex-review.md:2433:    // ---- Phase 0: ensure WBIA knows about the image ----
./docs/plans/2026-05-18-c3-codex-review.md:2464:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
./docs/plans/2026-05-18-c3-codex-review.md:2465:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
./docs/plans/2026-05-18-c3-codex-review.md:2466:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
./docs/plans/2026-05-18-c3-codex-review.md:2467:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
./docs/plans/2026-05-18-c3-codex-review.md:2471:Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
./docs/plans/2026-05-18-c3-codex-review.md:2478:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-c3-codex-review.md:2481:include this image), trigger Phase 0 again, attempt to register the
./docs/plans/2026-05-18-c3-codex-review.md:2536:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
./docs/plans/2026-05-18-c3-codex-review.md:2545:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
./docs/plans/2026-05-18-c3-codex-review.md:2565:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-c3-codex-review.md:2578:docs/plans/2026-05-18-wbia-image-registration-design.md:172:Add Phase 0 at the entry; existing Phase 1 (annotation registration)
./docs/plans/2026-05-18-c3-codex-review.md:2581:docs/plans/2026-05-18-wbia-image-registration-design.md:179:    // ---- Phase 0: ensure WBIA knows about the image ----
./docs/plans/2026-05-18-c3-codex-review.md:2588:docs/plans/2026-05-18-wbia-image-registration-design.md:210:- Phase 0 GET fails → `NETWORK_FAIL` (Phase C increments attempts).
./docs/plans/2026-05-18-c3-codex-review.md:2589:docs/plans/2026-05-18-wbia-image-registration-design.md:211:- Phase 0 POST (image registration) fails → `NETWORK_FAIL`.
./docs/plans/2026-05-18-c3-codex-review.md:2590:docs/plans/2026-05-18-wbia-image-registration-design.md:212:- Phase 0 succeeds + Phase 1 succeeds → `REGISTERED_OK`.
./docs/plans/2026-05-18-c3-codex-review.md:2591:docs/plans/2026-05-18-wbia-image-registration-design.md:213:- Phase 0 succeeds + Phase 1 fails network → `NETWORK_FAIL`.
./docs/plans/2026-05-18-c3-codex-review.md:2592:docs/plans/2026-05-18-wbia-image-registration-design.md:217:Phase 0 retries are bounded by the same `MAX_ATTEMPTS=10` because the
./docs/plans/2026-05-18-c3-codex-review.md:2593:docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
./docs/plans/2026-05-18-c3-codex-review.md:2594:docs/plans/2026-05-18-wbia-image-registration-design.md:227:include this image), trigger Phase 0 again, attempt to register the
./docs/plans/2026-05-18-c3-codex-review.md:2598:docs/plans/2026-05-18-wbia-image-registration-design.md:282:| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
./docs/plans/2026-05-18-c3-codex-review.md:2599:docs/plans/2026-05-18-wbia-image-registration-design.md:291:   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
./docs/plans/2026-05-18-c3-codex-review.md:2600:docs/plans/2026-05-18-wbia-image-registration-design.md:311:4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-c3-codex-review.md:2609:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:557:     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c3-codex-review.md:2642:src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:557:     * failure rather than returning an empty list. The new Phase 0 of
./docs/plans/2026-05-18-c2-codex-review.md:178:Empty-match-prospects design Track 1 C2 (per locked design's "Codex
./docs/plans/2026-05-18-c2-codex-review.md:1854:+     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./docs/plans/2026-05-18-c2-codex-review.md:1918: * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
./docs/plans/2026-05-18-c2-codex-review.md:2428:   814	     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./docs/plans/2026-05-18-c2-codex-review.md:2489:    16	 * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
./docs/plans/2026-05-18-c2-codex-review.md:2548:+     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./docs/plans/2026-05-18-c2-codex-review.md:4342:+     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./docs/plans/2026-05-18-c2-codex-review.md:4558:    66	public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
./docs/plans/2026-05-18-c2-codex-review.md:4599:   158	        dto.imageDateTimeMillis == null ? null
./docs/plans/2026-05-18-c2-codex-review.md:4600:   159	                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
./docs/plans/2026-05-18-c2-codex-review.md:4670:   282	| Image POST succeeds but annotation POST fails | NETWORK_FAIL | Retry; on next tick image is already registered so Phase 0 is fast |
./docs/plans/2026-05-18-c2-codex-review.md:4679:   291	   helpers + Phase 0 in registerOneByDto + tests). Codex reviews diff
./docs/plans/2026-05-18-c2-codex-review.md:4699:   311	4. **Phase 0 + ineligibility in Phase A**: I moved `validMediaAsset` into
./docs/plans/2026-05-18-c2-codex-review.md:5164:- QueryCache invalidation: yes, this branch has `QueryCache.invalidateByName` and `QueryCacheFactory.safeInvalidate`. Use `safeInvalidate(context, "iaImageIds")` after successful image registration. The strict cache readers should remain robust because invalidation leaves a cache object but clears serialized JSON.
./docs/plans/2026-05-18-c2-codex-review.md:5189:- QueryCache invalidation: yes, this branch has `QueryCache.invalidateByName` and `QueryCacheFactory.safeInvalidate`. Use `safeInvalidate(context, "iaImageIds")` after successful image registration. The strict cache readers should remain robust because invalidation leaves a cache object but clears serialized JSON.
./src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:16: * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
./src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:16: * is exercised end-to-end by Phase 0 of the WBIA registration polling
./src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:18: * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
./src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:22: * report which WBIA response was malformed. (Empty-match-prospects
./src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:12: * {@code imageLongitude}, {@code imageDateTimeMillis}) and the
./src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:14: * to {@code null}. (Empty-match-prospects design Track 1 C5.)
./src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:33:        assertEquals(Long.valueOf(1700000000000L), dto.imageDateTimeMillis);
./src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:45:        assertNull(dto.imageDateTimeMillis);
./src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:58:        assertNull(dto.imageDateTimeMillis);
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:455:        // Image-side fields. Phase 0 (image registration) sends these into
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:458:        // no JDO touchpoints. (Empty-match-prospects design Track 1 C5.)
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:462:        public final Long   imageDateTimeMillis; // nullable epoch-ms; ma.getDateTime().getMillis()
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:467:            Double imageLongitude, Long imageDateTimeMillis) {
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:478:            this.imageDateTimeMillis = imageDateTimeMillis;
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:483:         * test fixtures that don't exercise the Phase 0 image-registration
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:574:     * failure rather than returning an empty list. The new Phase 0 of
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:582:     * variant remains cache-free. (Empty-match-prospects design Track 1 C3.)</p>
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:648:     * <p>(Empty-match-prospects design Track 1 C4: extracted from
./src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:928:     * DTO. (Empty-match-prospects design Track 1 C2.)</p>
./src/main/java/org/ecocean/StartupWildbook.java:775:            // Image-side eligibility for the new Phase 0 image-registration
./src/main/java/org/ecocean/StartupWildbook.java:780:            // after the Shepherd closes. (Empty-match-prospects design
./src/main/java/org/ecocean/StartupWildbook.java:781:            // Track 1 C5: WBIA Phase 0 eligibility extension.)
./src/main/java/org/ecocean/StartupWildbook.java:799:            // Capture image-side fields for Phase 0 (image registration).
./src/main/java/org/ecocean/StartupWildbook.java:807:            Long imageDateTimeMillis = (dt == null) ? null : dt.getMillis();
./src/main/java/org/ecocean/StartupWildbook.java:812:                    imageUri, imageLatitude, imageLongitude, imageDateTimeMillis);

exec
/bin/bash -lc "sed -n '300,320p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '145,190p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '35,85p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
 succeeded in 428ms:
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

 succeeded in 436ms:
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

 succeeded in 550ms:
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

exec
/bin/bash -lc "sed -n '85,125p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
 succeeded in 454ms:
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


exec
/bin/bash -lc 'rg -n "DateTime|dateTime|invalid date|date" docs/plans/2026-05-18-wbia-image-registration-design.md docs/plans/2026-05-18-empty-match-prospects-design.md' in /mnt/c/Wildbook-clean2
 succeeded in 480ms:
docs/plans/2026-05-18-wbia-image-registration-design.md:66:public final Long imageDateTimeMillis;    // ma.getDateTime().getMillis(), nullable
docs/plans/2026-05-18-wbia-image-registration-design.md:97:DateTime dt = ma.getDateTime();
docs/plans/2026-05-18-wbia-image-registration-design.md:98:Long dateTimeMillis = (dt == null) ? null : dt.getMillis();
docs/plans/2026-05-18-wbia-image-registration-design.md:102:    imageUri, ma.getLatitude(), ma.getLongitude(), dateTimeMillis  // NEW
docs/plans/2026-05-18-wbia-image-registration-design.md:138:        // Validate status.success; the legacy WBIA wrapper returns
docs/plans/2026-05-18-wbia-image-registration-design.md:158:        dto.imageDateTimeMillis == null ? null
docs/plans/2026-05-18-wbia-image-registration-design.md:159:                                        : (int) Math.floor(dto.imageDateTimeMillis / 1000.0));
docs/plans/2026-05-18-wbia-image-registration-design.md:190:        // Invalidate the image-ids cache so the next annotation on this
docs/plans/2026-05-18-wbia-image-registration-design.md:191:        // image sees the updated list — without this, the 15-min cache
docs/plans/2026-05-18-wbia-image-registration-design.md:194:        QueryCacheFactory.getQueryCache(context).invalidate("iaImageIds");
docs/plans/2026-05-18-wbia-image-registration-design.md:224:After a successful Phase 0 POST, we must invalidate the `iaImageIds`
docs/plans/2026-05-18-wbia-image-registration-design.md:244:- `buildSingleImageRequestMapHandlesNullDatetime` — `null` datetime → 
docs/plans/2026-05-18-wbia-image-registration-design.md:271:the DTO captures `imageUri`, lat, lon, datetimeMillis correctly from a
docs/plans/2026-05-18-empty-match-prospects-design.md:10:at match-task time the candidate pool is incomplete.
docs/plans/2026-05-18-empty-match-prospects-design.md:29:   against a candidate pool that doesn't yet include the rest of
docs/plans/2026-05-18-empty-match-prospects-design.md:53:- Codex round-5 explicitly confirmed: `safeInvalidate` placement
docs/plans/2026-05-18-empty-match-prospects-design.md:87:- **`safeInvalidate(context, name)` lives on `QueryCacheFactory`**
docs/plans/2026-05-18-empty-match-prospects-design.md:128:- **Phase 2 prose** updated to reference both `STATUS_COMPLETE`
docs/plans/2026-05-18-empty-match-prospects-design.md:132:  `QueryCacheFactory.getQueryCache(context).invalidateByName(name)`
docs/plans/2026-05-18-empty-match-prospects-design.md:133:  in a small `safeInvalidate(context, name)` helper because the
docs/plans/2026-05-18-empty-match-prospects-design.md:166:  the shared key. The WBIA doc's stale `invalidate("iaImageIds")`
docs/plans/2026-05-18-empty-match-prospects-design.md:175:visually) plus any pre-existing candidates from the corpus. WBIA
docs/plans/2026-05-18-empty-match-prospects-design.md:204:| `QueryCache.invalidateByName(name)` | `QueryCache.java:46` | Right API for cache invalidation (not "invalidate"). |
docs/plans/2026-05-18-empty-match-prospects-design.md:230:4. Phase B does NOT re-validate MA eligibility. Documented as an
docs/plans/2026-05-18-empty-match-prospects-design.md:247:public final Long imageDateTimeMillis;
docs/plans/2026-05-18-empty-match-prospects-design.md:271:private static void safeInvalidate(String context, String cacheName) {
docs/plans/2026-05-18-empty-match-prospects-design.md:274:        if (qc != null) qc.invalidateByName(cacheName);
docs/plans/2026-05-18-empty-match-prospects-design.md:278:            "WARN: QueryCache invalidate " + cacheName + " failed: " + ex);
docs/plans/2026-05-18-empty-match-prospects-design.md:286:  `safeInvalidate(context, "iaImageIds")`.
docs/plans/2026-05-18-empty-match-prospects-design.md:288:  `safeInvalidate(context, "iaAnnotationIds")` (prevents
docs/plans/2026-05-18-empty-match-prospects-design.md:566:// embedding metadata, the match will just return zero candidates,
docs/plans/2026-05-18-empty-match-prospects-design.md:586:problem (no candidates returned), not a stuck wait.
docs/plans/2026-05-18-empty-match-prospects-design.md:880:New unit tests in `MatchVisibilityGateImplTest` and updates to
docs/plans/2026-05-18-empty-match-prospects-design.md:974:- `safeInvalidate_swallowsNull_fromQueryCacheFactory`
docs/plans/2026-05-18-empty-match-prospects-design.md:975:- `safeInvalidate_swallowsException_fromInvalidateByName`
docs/plans/2026-05-18-empty-match-prospects-design.md:979:includes the other 3 as candidates. Same fire-salamander test
docs/plans/2026-05-18-empty-match-prospects-design.md:1028:- `safeInvalidate(context, name)` lives on `QueryCacheFactory`
docs/plans/2026-05-18-empty-match-prospects-design.md:1061:  - `QueryCache.invalidateByName` —

exec
/bin/bash -lc "sed -n '236,274p' docs/plans/2026-05-18-wbia-image-registration-design.md" in /mnt/c/Wildbook-clean2
 succeeded in 447ms:
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

codex
No findings.

The new Phase A checks in [StartupWildbook.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:782) match `sendMediaAssetsForceId` order: `isValidImageForIA()==false` first, then `validMediaAsset(ma)`, as seen in [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:207).

Phase A captures the image fields while Shepherd is open and passes only scalars into the DTO at [StartupWildbook.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:803). Current Phase B still uses DTO data only; no `MediaAsset`/JDO touchpoint is introduced.

Constructor compatibility looks intact. I found only the intended production migration to the 11-arg constructor, with existing test fixtures still using the 7-arg delegate. Field ordering and final-field immutability are fine for the new fields; the existing `bbox` array remains shallow-mutable, but Phase A already defensively copies it before DTO creation.

Non-blocking note: the comment saying `WildbookIAM.java:121-130` is stale in the current file; `sendMediaAssetsForceId` is now around lines 207-214. I did not rerun tests to honor the review-only/no-write constraint.
tokens used
94,280
No findings.

The new Phase A checks in [StartupWildbook.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:782) match `sendMediaAssetsForceId` order: `isValidImageForIA()==false` first, then `validMediaAsset(ma)`, as seen in [WildbookIAM.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/plugin/WildbookIAM.java:207).

Phase A captures the image fields while Shepherd is open and passes only scalars into the DTO at [StartupWildbook.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/StartupWildbook.java:803). Current Phase B still uses DTO data only; no `MediaAsset`/JDO touchpoint is introduced.

Constructor compatibility looks intact. I found only the intended production migration to the 11-arg constructor, with existing test fixtures still using the 7-arg delegate. Field ordering and final-field immutability are fine for the new fields; the existing `bbox` array remains shallow-mutable, but Phase A already defensively copies it before DTO creation.

Non-blocking note: the comment saying `WildbookIAM.java:121-130` is stale in the current file; `sendMediaAssetsForceId` is now around lines 207-214. I did not rerun tests to honor the review-only/no-write constraint.
