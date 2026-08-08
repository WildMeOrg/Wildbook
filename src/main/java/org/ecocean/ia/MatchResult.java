package org.ecocean.ia;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.api.UploadedFiles;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.MLService;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.URLAssetStore;
import org.ecocean.MarkedIndividual;
import org.ecocean.RestClient;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class MatchResult implements java.io.Serializable {
    private String id;
    private long created;
    private Task task;
    private Set<MatchResultProspect> prospects;
    private Annotation queryAnnotation;
    private int numberCandidates = 0;
    // we store *actual* count here, but they may not all exist
    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
    private int numberProspects = 0;
    // not sure we really *need* true fk link to these annots
    // they might be gone now and will we ever use this?
    // so for now we just populate numberCandidates
    private Set<Annotation> candidates;
    // fallback number to cutoff number of prospects to return
    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
    // number of MatchResultProspects [per type] to actually store (hotspotter
    // results can produce thousands, but storing them all is excessive)
    public static final int MAXIMUM_PROSPECTS_STORED = 500;

    public MatchResult() {
        id = Util.generateUUID();
        created = System.currentTimeMillis();
    }

    public MatchResult(Task task) {
        this();
        this.task = task;
    }

    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
    throws IOException {
        this();
        this.createFromIdentityServiceLog(isLog, myShepherd);
    }

    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
    throws IOException {
        this();
        this.task = task;
        this.createFromJsonResult(jsonResult, myShepherd);
    }

    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
        Shepherd myShepherd)
    throws IOException {
        this();
        this.task = task;
        this.numberCandidates = numberCandidates;
        this.setQueryAnnotationFromTask();
        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
        this.populateProspects(annots, false, myShepherd);
        this.populateProspects(annots, true, myShepherd);
    }

    public int getNumberCandidates() {
        return numberCandidates;
    }

    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
    throws IOException {
        if (isLog == null) throw new IOException("log passed is null");
        String taskId = isLog.getTaskID();
        this.task = myShepherd.getTask(taskId);
        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
        JSONObject res = isLog.getJsonResult();
        if (res == null) {
            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
                isLog.getStatusJson());
            throw new IOException("could not get json result");
        }
        createFromJsonResult(res, myShepherd);
    }

    public Annotation setQueryAnnotationFromTask()
    throws IOException {
        if (this.task == null)
            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
        int numAnns = this.task.countObjectAnnotations();
        if (numAnns < 1)
            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
        if (numAnns > 1)
            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
                " annotations; using first");
        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
        return this.queryAnnotation;
    }

    // json_result section should be passed here
    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
    throws IOException {
        if (res == null) throw new IOException("null json_result passed");
        if (res.optJSONArray("query_annot_uuid_list") == null)
            throw new IOException("no query annot list");
        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
            throw new IOException("empty query annot list");
        // for now we are assuming a single query annot. sorrynotsorry.
        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
            "query_annot_uuid_list").optJSONObject(0));
        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
        if (this.queryAnnotation == null)
            throw new IOException("failed to load query annot from id=" + queryAnnotId);
        if (res.optJSONObject("cm_dict") == null)
            throw new IOException("no cm_dict found in " + res);
        // results is the real scores (etc) we are looking for.... finally!
        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
        if (results == null) throw new IOException("no actual results found");
        // see note at top about true annot list of candidates vs number
        if (res.optJSONArray("database_annot_uuid_list") != null)
            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
/*
        annot_score_list <=> dannot_uuid_list
        score_list is for indiv scores but on dannot_uuid_list (same length)
        name_score_list <=> unique_name_uuid_list ???
 */
        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        System.out.println("[DEBUG] createFromJsonResult() created " + this);
    }

    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
        JSONArray externs, String externRef, Shepherd myShepherd)
    throws IOException {
        if ((annotIds == null) || (scores == null))
            throw new IOException("null annotIds or scores");
        if (annotIds.length() != scores.length())
            throw new IOException("mismatch in size of annotIds/scores");
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
        this.numberProspects += annotIds.length(); // true number of prospects

        // Sort (index, score) pairs by score DESC, ties broken by original
        // index ASC, before applying the MAXIMUM_PROSPECTS_STORED cap.
        // WBIA's response (dannot_uuid_list, score_list, annot_score_list)
        // is NOT ordered by score, so iterating the first 500 and breaking
        // would drop the actual top-K matches when the strongest are past
        // index 500. WBIA also serializes float('-inf') as the literal
        // string "-Infinity" which is not parseable as a JSON number;
        // parseScore() handles that and missing/non-numeric values by
        // returning Double.NEGATIVE_INFINITY so they sort to the bottom.
        int n = annotIds.length();
        Integer[] order = new Integer[n];
        double[] parsed = new double[n];
        for (int i = 0; i < n; i++) {
            order[i] = Integer.valueOf(i);
            parsed[i] = parseScore(scores.opt(i));
        }
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                int c = Double.compare(parsed[b.intValue()], parsed[a.intValue()]);
                if (c != 0) return c;
                return Integer.compare(a.intValue(), b.intValue());
            }
        });

        int num = 0;
        for (int k = 0; k < n; k++) {
            int i = order[k].intValue();
            double score = parsed[i];
            // Skip non-finite scores: WBIA's "-Infinity" / "Infinity" /
            // "NaN" signal "no valid score for this candidate", and
            // org.json refuses to serialize non-finite numbers later
            // when prospects render as JSON. Drop them outright so they
            // never reach MatchResultProspect, regardless of where they
            // landed in the sort (NaN coerced to -Infinity above, but
            // raw +/-Infinity can land anywhere).
            if (!Double.isFinite(score)) continue;
            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
            if (ann == null) {
                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
                    "; skipping; score=" + score);
                continue;
            }
            MediaAsset ma = null;
            // we only try if we have a true value in externs[i]
            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
            num++;
            if (num >= MAXIMUM_PROSPECTS_STORED) {
                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
                    ") number storable prospects on " + this);
                break;
            }
        }
        return num;
    }

    /**
     * Parse a score value from WBIA's response that may be a JSON number,
     * a JSON null, or the literal string "-Infinity" / "Infinity" / "NaN"
     * (Python's default serialization for non-finite floats). Missing,
     * null, unparseable, or NaN values map to
     * {@link Double#NEGATIVE_INFINITY} so they sort to the bottom of the
     * prospect list. NaN must be coerced explicitly because
     * {@link Double#compare} sorts NaN ABOVE finite values, which would
     * push invalid scores to the top of a DESC sort.
     */
    public static double parseScore(Object raw) {
        double v;
        if (raw == null || raw == JSONObject.NULL) {
            v = Double.NEGATIVE_INFINITY;
        } else if (raw instanceof Number) {
            v = ((Number) raw).doubleValue();
        } else if (raw instanceof String) {
            try {
                v = Double.parseDouble((String) raw);
            } catch (NumberFormatException ex) {
                v = Double.NEGATIVE_INFINITY;
            }
        } else {
            v = Double.NEGATIVE_INFINITY;
        }
        return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : v;
    }

    // we just have a list of annots which matched (e.g. via vectors in opensearch)
    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
    //
    // Empty-match-prospects design Track 2 C13: prospects are created with
    // {@code asset=null}. The PairX inspection image is populated later by
    // {@link MatchInspectionPairxEnricher} in a Phase A/B/C flow so the
    // outer Shepherd is never held across the PairX HTTP call. Holding a
    // Shepherd across that ~10-30s POST would risk connection-pool
    // exhaustion under load (Codex C12 review High).
    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
        Shepherd myShepherd)
    throws IOException {
        if (Util.collectionIsEmptyOrNull(annots)) return 0;
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
        if (scoreByIndividual) {
            // C19: per-individual scores are now the highest per-annotation
            // OS knn score within the group (same scale as the annot tab),
            // and un-ID'd candidates are emitted as singletons.
            _populateProspectsByIndividual(annots, myShepherd);
        } else {
            // these scores are direct from opensearch
            for (Annotation ann : annots) {
                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
                    null));
            }
        }
        this.numberProspects = this.prospects.size();
        return this.numberProspects;
    }

    /**
     * Build indiv-tab prospects (scoreType "indiv") from the knn
     * candidate annotations. C19 changes from the prior count-based
     * scoring of identified-only individuals to a uniform highest-score
     * (within group) scoring that also surfaces un-ID'd candidates as
     * singleton
     * "individuals" — matching the legacy WBIA HotSpotter behavior of
     * assigning placeholder name {@code "____"} to un-ID'd
     * annotations.
     *
     * <p>For each MarkedIndividual that owns one or more candidate
     * annotations, the prospect carries the highest-scoring annotation
     * within that group and score = its OpenSearch Lucene knn
     * cosinesimil score {@code (1+cos)/2} in [0, 1] (which matches
     * WBIA-MiewID's stored score scale for cross-pipeline parity).
     * For each candidate whose encounter has no MarkedIndividual, a
     * singleton prospect carries that annotation and its own score.
     * All entries sort by score descending — the indiv tab and the
     * image tab use the same scoring scale.</p>
     */
    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
        // Key by individual ID (String), NOT by MarkedIndividual object.
        // Base.equals() compares by id but Base does not override
        // hashCode(), so two distinct MarkedIndividual instances with
        // the same id would hash to different buckets and emit
        // duplicate indiv prospects. Keying by id avoids that
        // (Codex C19 review Medium).
        Map<String, List<Annotation> > tally =
            new HashMap<String, List<Annotation> >();
        List<Annotation> singletons = new ArrayList<Annotation>();

        for (Annotation ann : annots) {
            Encounter enc = ann.findEncounter(myShepherd);
            // No encounter at all: skip (no individual axis possible).
            if (enc == null) continue;
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv == null || indiv.getId() == null) {
                // Un-ID'd (no MarkedIndividual or its id is null):
                // treat as a singleton "individual" so the indiv tab
                // still shows it, matching legacy WBIA behavior (C19).
                // The annotation is the singleton's own representative;
                // the frontend renders these as "potential new
                // individual" rows since the annotation.encounter.
                // individual link is null.
                singletons.add(ann);
            } else {
                String key = indiv.getId();
                if (!tally.containsKey(key))
                    tally.put(key, new ArrayList<Annotation>());
                tally.get(key).add(ann);
            }
        }
        if (tally.isEmpty() && singletons.isEmpty()) return;

        // For each ID'd individual: pick the highest-scoring annotation
        // within its candidate group. That becomes the rep prospect.
        // Multi-annotation individuals no longer get a count-based
        // boost — score is the per-annotation OS knn score (same scale
        // as the image tab and as WBIA-MiewID's stored score).
        // prospectsSorted(...) handles final ordering, so we don't
        // pre-sort here.
        for (Map.Entry<String, List<Annotation> > ent : tally.entrySet()) {
            Annotation best = null;
            double bestScore = -Double.MAX_VALUE;
            for (Annotation cand : ent.getValue()) {
                double s = cand.getOpensearchScore();
                if (best == null || s > bestScore) {
                    best = cand;
                    bestScore = s;
                }
            }
            if (best != null) {
                this.prospects.add(new MatchResultProspect(best, bestScore, "indiv", null));
            }
        }
        // Singletons: one prospect per un-ID'd annotation, scored by
        // its own OS knn score (same scale as the annot tab).
        for (Annotation ann : singletons) {
            this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(),
                "indiv", null));
        }
    }

    /**
     * Public read-only view of the prospects collection so the
     * {@link MatchInspectionPairxEnricher} can iterate them in Phase A
     * and Phase C without reaching into private state. Returns the
     * underlying Set; callers must not mutate.
     */
    public Set<MatchResultProspect> getProspects() {
        return this.prospects;
    }

    /**
     * Public accessor for the queryAnnotation field. Returns whatever
     * value was set by {@link #setQueryAnnotationFromTask()} or
     * {@link #createFromJsonResult(JSONObject, Shepherd)} — may be null
     * if neither has run.
     */
    public Annotation getQueryAnnotation() {
        return this.queryAnnotation;
    }

    /** Public accessor for the JDO primary key. */
    public String getId() {
        return this.id;
    }

    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
        if (acmId == null) return null;
        Annotation found = findAcmIdInTaskAnnotations(acmId);
        if (found != null) return found;
        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
        if ((anns == null) || (anns.size() < 1)) return null;
        return anns.get(0);
    }

    private Annotation findAcmIdInTaskAnnotations(String acmId) {
        if ((this.task == null) || (acmId == null)) return null;
        if (!this.task.hasObjectAnnotations()) return null;
        for (Annotation ann : this.task.getObjectAnnotations()) {
            if (acmId.equals(ann.getAcmId())) return ann;
        }
        return null;
    }

    // if it exists, we just return the thing, other wise we attempt to create it
    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
        Shepherd myShepherd) {
        if (externRef == null) return null;
        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
        url += "&database_annot_uuid=" + annotId;
        url += "&version=heatmask";
        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
        MediaAsset ma = null;
        try {
            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
            ma.addLabel("matchInspectionHeatmap");
            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
                " and created " + ma);
            tmpFile.delete();
        } catch (Exception ex) {
            System.out.println(
                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
                " => " + tmpFile + ": " + ex);
            ex.printStackTrace();
        }
        return ma;
    }

/*
   notes on pairx payload:
   - image1_uris / image2_uris accept URLs or local file paths (as seen by the server)
   - If you provide 1 image1 and N image2s, it compares that single image1 against each image2 (1-to-many)
   - If you provide N of each, they're compared pairwise (N-to-N, max 16 pairs)
   - bb1/bb2 are bounding boxes as [x, y, width, height]
   - visualization_type options: "lines_and_colors", "only_lines", "only_colors"
   - layer_key controls feature depth — earlier layers (e.g. backbone.blocks.1) give point-specific matches, later layers
    (e.g. backbone.blocks.5) give broader region matches
 */
    public MediaAsset createInspectionPairxAsset(Annotation ann1, Annotation ann2,
        Shepherd myShepherd) {
        if ((ann1 == null) || (ann2 == null)) return null;
        MediaAsset ma1 = ann1.getMediaAsset();
        MediaAsset ma2 = ann2.getMediaAsset();
        if ((ma1 == null) || (ma2 == null)) return null;
        // we need this to find MLService endpoint
        Encounter enc = ann1.findEncounter(myShepherd);
        if (enc == null) return null;
        JSONObject payload = new JSONObject();
        payload.put("algorithm", "pairx");
        payload.put("visualization_type", "only_colors");
        payload.put("k_colors", 5);
        // payload.put("k_lines", 20);
        payload.put("model_id", "miewid-msv4.1");
        payload.put("crop_bbox", false);
        payload.put("layer_key", "backbone.blocks.3");
        payload.put("image1_uris", new JSONArray(new String[] { ma1.webURL().toString() }));
        payload.put("image2_uris", new JSONArray(new String[] { ma2.webURL().toString() }));
        payload.put("theta1", new JSONArray(new Double[] { ann1.getTheta() }));
        payload.put("theta2", new JSONArray(new Double[] { ann2.getTheta() }));
        // bb1 / bb2 payload construction. See addBboxPayload Javadoc for
        // the two bugs this fixes (shared-array + negative-bbox-rejection,
        // empty-match-prospects design Track 2 C12). If either clamped
        // bbox has zero width or height, skip the POST entirely — PairX
        // also rejects degenerate boxes.
        int[] clamped1 = clampBbox(ann1.getBbox());
        int[] clamped2 = clampBbox(ann2.getBbox());
        if (isDegenerateBbox(clamped1) || isDegenerateBbox(clamped2)) {
            System.out.println(
                "[INFO] createInspectionPairxAsset() skipping PairX for ann1=" +
                ann1.getId() + " ann2=" + ann2.getId() +
                ": degenerate clamped bbox " +
                java.util.Arrays.toString(clamped1) + " / " +
                java.util.Arrays.toString(clamped2));
            return null;
        }
        addBboxPayload(payload, clamped1, clamped2);

        // get the image data from pairx endpoint
        JSONObject res = null;
        URL pairxUrl = null;
        try {
            pairxUrl = _getPairxUrl(enc.getTaxonomyString());
            if (pairxUrl == null) return null;
            res = RestClient.postJSON(pairxUrl, payload, null);
        } catch (Exception ex) {
            System.out.println("[ERROR] createInspectionPairxAsset() POST to " + pairxUrl +
                " failed: " + ex + "; payload=" + payload);
            ex.printStackTrace();
        }
        if (res == null) return null;
        JSONArray imgs = res.optJSONArray("images");
        if ((imgs == null) || (imgs.length() < 1)) return null;
        String b64 = imgs.optString(0, null);
        if (b64 == null) return null;
        // create the asset from base64 data
        System.out.println("[DEBUG] createInspectionPairxAsset() POST to " + pairxUrl +
            " got image data length=" + b64.length());
        try {
            AssetStore store = AssetStore.getDefault(myShepherd);
            JSONObject params = store.createParameters(new File(Util.hashDirectories(this.id) +
                "/pairx-" + this.id + "-" + ann1.getId() + "-" + ann2.getId() + ".png"));
            MediaAsset ma = store.create(params);
            ma.copyInBase64(b64);
            ma.addLabel("matchInspectionPairx");
            System.out.println("[INFO] createInspectionPairxAsset() created " + ma);
            myShepherd.getPM().makePersistent(ma);
            return ma;
        } catch (Exception ex) {
            System.out.println(
                "[ERROR] createInspectionPairxAsset() failed to create MediaAsset: " + ex);
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Clamp negative bbox values to the in-image portion. ml-service
     * detections sometimes produce bboxes whose top-left extends past
     * the image edge (e.g., {@code [-80, 42, 1786, 2228]}); the PairX
     * {@code /explain/} endpoint rejects those with HTTP 400. Shifting
     * x or y to 0 alone would translate the box; we also shrink the
     * dimension by the same amount so the result covers the same in-
     * image pixels the embedding model actually consumed after
     * edge-cropping.
     *
     * <p>Package-visible for unit testing. (Empty-match-prospects
     * design Track 2 C12.)</p>
     */
    static int[] clampBbox(int[] bbox) {
        if (bbox == null || bbox.length < 4) return bbox;
        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
        if (x < 0) {
            w = Math.max(0, w + x);
            x = 0;
        }
        if (y < 0) {
            h = Math.max(0, h + y);
            y = 0;
        }
        return new int[] { x, y, w, h };
    }

    /**
     * Convert an int[] bbox to a JSONArray of ints. {@code JSONArray.put(Object)}
     * doesn't auto-convert int[] reliably across org.json versions, so we
     * box explicitly.
     */
    static JSONArray bboxToJsonArray(int[] bbox) {
        JSONArray arr = new JSONArray();
        if (bbox == null) return arr;
        for (int v : bbox) arr.put(v);
        return arr;
    }

    /**
     * True when a bbox has zero or negative width/height (the typical
     * shape after {@link #clampBbox} on a box that lies entirely off-
     * image). PairX rejects such boxes the same way it rejects negative
     * x/y, so callers should skip the POST entirely.
     */
    static boolean isDegenerateBbox(int[] bbox) {
        if (bbox == null || bbox.length < 4) return true;
        return bbox[2] <= 0 || bbox[3] <= 0;
    }

    /**
     * Build the bb1/bb2 payload for {@code /explain/}: each key gets
     * its own outer JSONArray of one [x, y, w, h] inner array.
     *
     * <p>Two bugs in the previous implementation are addressed
     * together (empty-match-prospects design Track 2 C12):</p>
     * <ol>
     *   <li>The previous code reused one tmpArr for both keys, so
     *       {@code tmpArr.put(0, ann2)} after {@code payload.put("bb1", tmpArr)}
     *       mutated the shared array and made {@code bb2 == bb1}.
     *       Building two outer arrays here keeps the references
     *       distinct.</li>
     *   <li>{@link #clampBbox} (called by the production entry point
     *       before this method) prevents negative x/y from being
     *       sent to PairX, which would return HTTP 400
     *       "Bounding box values should be positive".</li>
     * </ol>
     *
     * <p>Package-visible so {@code MatchResultPairxPayloadTest} can
     * assert the JSON shape without spinning up a real Annotation.</p>
     */
    static void addBboxPayload(JSONObject payload, int[] bbox1, int[] bbox2) {
        payload.put("bb1", new JSONArray().put(bboxToJsonArray(bbox1)));
        payload.put("bb2", new JSONArray().put(bboxToJsonArray(bbox2)));
    }

    public static URL _getPairxUrl(String txStr)
    throws IOException {
        if (txStr == null) throw new IOException("passed null taxonomy");
        String urlStr = null;
        try {
            MLService mls = new MLService();
            List<JSONObject> confs = mls.getConfigs(txStr);
            if (confs.size() < 1) throw new IOException("empty MLService configs for tx=" + txStr);
            urlStr = confs.get(0).optString("api_endpoint", null);
        } catch (IAException ex) {
            throw new IOException(ex);
        }
        if (urlStr == null) return null;
        return new URL(urlStr + "/explain/");
    }

    public JSONObject getTaskParameters() {
        if (task == null) return null;
        return task.getParameters();
    }

    public JSONObject getTaskMatchingSetFilter() {
        if (task == null) return null;
        JSONObject params = task.getParameters();
        if (params == null) return null;
        return params.optJSONObject("matchingSetFilter");
    }

/*
    see note at top about candidates vs numberCandidates
    public int numberCandidates() {
        return Util.collectionSize(candidates);
    }
 */
    public int numberProspects() {
        return this.numberProspects;
    }

    public Set<String> prospectScoreTypes() {
        Set<String> types = new HashSet<String>();

        if (numberProspects() == 0) return types;
        for (MatchResultProspect mrp : prospects) {
            types.add(mrp.getType());
        }
        return types;
    }

    // if cutoff < 0 then it will not be truncated at all
    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
        Set<String> projectIds, Shepherd myShepherd) {
        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();

        if (numberProspects() == 0) return pros;
        for (MatchResultProspect mrp : prospects) {
            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
        }
        Collections.sort(pros);
        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
        return pros;
    }

    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject sj = new JSONObject();

        for (String type : prospectScoreTypes()) {
            JSONArray jarr = new JSONArray();
            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
                jarr.put(mrp.jsonForApiGet(myShepherd));
            }
            sj.put(type, jarr);
        }
        return sj;
    }

    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", id);
        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
        rtn.put("numberTotalProspects", numberProspects());
        rtn.put("numberCandidates", getNumberCandidates());
        rtn.put("created", Util.millisToISO8601String(created));
        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
        rtn.put("projectIds", projectIds);
        return rtn;
    }

    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
        JSONObject aj = new JSONObject();

        if (ann == null) return aj;
        MediaAsset ma = ann.getMediaAsset();
        // populate bounding box stuff (note: it may reset aj so must be done first)
        if (ann.getFeatures() != null) {
            for (Feature ft : ann.getFeatures()) {
                if (ft.isUnity()) {
                    aj.put("trivial", true);
                    aj.put("x", 0);
                    aj.put("y", 0);
                    // would be weird to be null, but.....
                    if (ma != null) {
                        aj.put("width", (int)ma.getWidth());
                        aj.put("height", (int)ma.getHeight());
                    }
                } else {
                    // basically if we have more than one feature, only one wins
                    if (ft.getParameters() != null) aj = ft.getParameters();
                }
            }
        }
        if (ma != null) {
            JSONObject mj = ma.toSimpleJSONObject();
            mj.put("rotationInfo", ma.getRotationInfo());
            aj.put("asset", mj);
        }
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc != null) {
            JSONObject ej = new JSONObject();
            // TODO add "access" permission value if needed?
            ej.put("id", enc.getId());
            ej.put("taxonomy", enc.getTaxonomyString());
            ej.put("locationId", enc.getLocationID());
            aj.put("encounter", ej);
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv != null) {
                JSONObject ij = new JSONObject();
                ij.put("id", indiv.getId());
                ij.put("taxonomy", indiv.getTaxonomyString());
                ij.put("displayName", indiv.getDisplayName());
                ij.put("nickname", indiv.getNickName());
                ij.put("sex", indiv.getSex());
                ij.put("numberEncounters", indiv.getNumEncounters());
                aj.put("individual", ij);
            }
        }
        aj.put("id", ann.getId());
        // ml-service migration v2 §commit #11: surface WBIA registration
        // state so the frontend can disable the "Match with HotSpotter"
        // button until WBIA has acknowledged the annotation. tri-state:
        // null = legacy or not-yet-pending; false = pending registration;
        // true = WBIA acknowledged. Frontend treats anything non-true as
        // "HotSpotter not available yet" with a tooltip.
        Boolean wbiaReg = ann.getWbiaRegistered();
        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
        return aj;
    }

    public String toString() {
        String s = "MatchResult " + id;

        s += " [" + Util.millisToISO8601String(created) + "]";
        s += " query " + queryAnnotation;
        s += "; numCandidates=" + this.getNumberCandidates();
        s += "; numProspects=" + this.numberProspects();
        s += "; task=" + (task == null ? "null" : task.getId());
        return s;
    }
}
