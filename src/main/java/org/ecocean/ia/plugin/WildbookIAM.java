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
        public final String mediaAssetAcmId;    // MediaAsset.acmId (the WBIA image id we send)
        public final int[]  bbox;               // x,y,w,h
        public final double theta;
        public final String iaClass;            // species/class string
        public final String individualName;     // "____" if absent

        public WbiaRegisterRequest(String annotationId, String mediaAssetAcmId,
            int[] bbox, double theta, String iaClass, String individualName) {
            this.annotationId    = annotationId;
            this.mediaAssetAcmId = mediaAssetAcmId;
            this.bbox            = bbox;
            this.theta           = theta;
            this.iaClass         = iaClass;
            this.individualName  = individualName;
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
                return parseAnnotationIdsArray(cached);
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
        return parseAnnotationIdsArray(jids);
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
        if (known.contains(dto.annotationId) || known.contains(dto.mediaAssetAcmId)) {
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

    private static Object mediaAssetToUri(MediaAsset ma) {
        URL curl = ma.webURL();
        String urlStr = curl.toString();

        // THIS WILL BREAK if you need to append a query to the filename...
        // we are double encoding the '?' in order to allow filenames that contain it to go to IA
        if (urlStr != null) {
            urlStr = urlStr.replaceAll("\\?", "%3F");
            if (ma.getStore() instanceof LocalAssetStore) {
                return urlStr;
            } else {
                return urlStr;
            }
        }
        return null;
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
