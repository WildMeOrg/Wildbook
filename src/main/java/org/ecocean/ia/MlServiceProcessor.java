package org.ecocean.ia;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Single-job orchestrator for ml-service v2 queue payloads.
 *
 * <p>Owns Shepherd transaction boundaries: load/revalidate, release DB while
 * the network call runs, then reopen for idempotent persistence and matching.
 * The dispatcher branch that routes {@code mlServiceV2:true} payloads lives in
 * a later commit.</p>
 */
public class MlServiceProcessor {
    private static final String ACTION_PREFIX = "MlServiceProcessor.";
    private static final String BOUNDING_BOX_FEATURE = "org.ecocean.boundingBox";

    private final String context;
    private final MlServiceClient client;
    private final MatchVisibilityGate visibilityGate;
    private final DeferredMatchPublisher deferredPublisher;

    public MlServiceProcessor(String context) {
        this(context, new MlServiceClient(),
            new MatchVisibilityGateImpl(context),
            new IAGatewayDeferredMatchPublisher());
    }

    public MlServiceProcessor(String context, MlServiceClient client) {
        this(context, client, new MatchVisibilityGateImpl(context),
            new IAGatewayDeferredMatchPublisher());
    }

    /**
     * Test-friendly constructor that accepts injected
     * {@link MatchVisibilityGate} and {@link DeferredMatchPublisher}.
     * Production code should use the no-arg or single-arg constructor
     * above. (Empty-match-prospects design Track 2 C11 testability
     * seam — Codex round-4 Medium.)
     */
    MlServiceProcessor(String context, MlServiceClient client,
        MatchVisibilityGate visibilityGate,
        DeferredMatchPublisher deferredPublisher) {
        this.context = context;
        this.client = client;
        this.visibilityGate = visibilityGate;
        this.deferredPublisher = deferredPublisher;
    }

    /** Process one ml-service queue job. Returns the outcome. */
    public MlServiceJobOutcome process(JSONObject jobData) {
        if (jobData == null) {
            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
        }
        if (jobData.optBoolean("deferredMatch", false)) {
            return runDeferredMatch(jobData);
        }

        String taxonomyString = jobData.optString("taxonomyString", null);
        String taskId = jobData.optString("taskId", null);
        String encounterId = jobData.optString("encounterId", null);

        if (jobData.has("mediaAssetId")) {
            String maId = String.valueOf(jobData.opt("mediaAssetId"));
            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
        }
        if (jobData.has("annotationId")) {
            String annId = jobData.optString("annotationId", null);
            return processExtraction(jobData, taxonomyString, taskId, annId);
        }
        return MlServiceJobOutcome.validationError("INVALID_PAYLOAD",
            "neither mediaAssetId nor annotationId in payload");
    }

    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
        String taskId, String encounterId, String maId) {
        DetectionContext det = null;

        try {
            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (det.outcome != null) return det.outcome;

        JSONObject response;
        try {
            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
        } catch (IAException ex) {
            if (ex.shouldRequeue()) {
                IAGateway.requeueJob(jobData, ex.shouldIncrement());
                return MlServiceJobOutcome.requeue();
            }
            markTaskError(taskId, ex.getCode(), ex.getMessage());
            return mapNonRetryableError(ex);
        }

        JSONArray results = response.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return finalizeZeroDetections(maId, taskId);
        }

        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
        if (persisted.outcome != null) return persisted.outcome;

        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
            det.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
        String taskId, String annId) {
        ExtractionContext ext = null;

        try {
            ext = loadExtractionContext(taxonomyString, taskId, annId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (ext.outcome != null) return ext.outcome;

        JSONObject response;
        try {
            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
                ext.mlConfig);
        } catch (IAException ex) {
            if (ex.shouldRequeue()) {
                IAGateway.requeueJob(jobData, ex.shouldIncrement());
                return MlServiceJobOutcome.requeue();
            }
            markTaskError(taskId, ex.getCode(), ex.getMessage());
            return mapNonRetryableError(ex);
        }

        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
        if (persisted.outcome != null) return persisted.outcome;

        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
        String encounterId, String maId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
        try {
            FeatureType.initAll(shep);
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
            Task task = Task.load(taskId, shep);

            String staleReason = detectionStaleReason(ma, enc, encounterId);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
            }

            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
            if (configs == null) {
                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
            }

            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
                markTaskError(task, "INVALID",
                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
                    "_mlservice_conf missing predict_model_id"));
            }

            URL webUrl = ma.webURL();
            if (webUrl == null) {
                markTaskError(task, "INVALID_IMAGE_URI",
                    "MediaAsset " + maId + " has no webURL");
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError(
                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
            }

            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
            shep.commitDBTransaction();
            return new DetectionContext(webUrl.toString(),
                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
                configs.matchConfig);
        } finally {
            shep.rollbackAndClose();
        }
    }

    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
        String annId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "loadExtractionContext");
        try {
            shep.beginDBTransaction();
            Annotation ann = shep.getAnnotation(annId);
            Task task = Task.load(taskId, shep);
            if (ann == null) {
                markTaskDroppedStale(shep, task, "annotation missing");
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.stale("annotation missing"));
            }
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) {
                markTaskDroppedStale(shep, task, "annotation media asset missing");
                shep.commitDBTransaction();
                return ExtractionContext.done(
                    MlServiceJobOutcome.stale("annotation media asset missing"));
            }

            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString,
                ann.findEncounter(shep));
            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
            if (configs == null) {
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.stale("pending-species"));
            }
            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
            }

            URL webUrl = ma.webURL();
            if (webUrl == null) {
                markTaskError(task, "INVALID_IMAGE_URI",
                    "Annotation " + annId + " media asset has no webURL");
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.validationError(
                    "INVALID_IMAGE_URI", "Annotation " + annId + " media asset has no webURL"));
            }
            int[] bbox = ann.getBbox();
            if (bbox == null || bbox.length != 4) {
                markTaskError(task, "INVALID_BBOX", "Annotation " + annId + " has no bbox");
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
                    "Annotation " + annId + " has no bbox"));
            }

            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
            shep.commitDBTransaction();
            return new ExtractionContext(webUrl.toString(),
                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
        } finally {
            shep.rollbackAndClose();
        }
    }

    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
        try {
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Task task = Task.load(taskId, shep);
            String staleReason = detectionStaleReason(ma, null, null);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return MlServiceJobOutcome.stale(staleReason);
            }
            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            markTaskCompleted(task);
            shep.commitDBTransaction();
            return MlServiceJobOutcome.okZeroDetections();
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "zero-detection finalize failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        } finally {
            shep.rollbackAndClose();
        }
    }

    private PersistResult persistDetections(String maId, String encounterId, String taskId,
        DetectionContext det, JSONArray results) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "persistDetections");
        List<String> annotationIds = new ArrayList<String>();

        try {
            FeatureType.initAll(shep);
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
            Task task = Task.load(taskId, shep);
            String staleReason = detectionStaleReason(ma, enc, encounterId);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return PersistResult.done(MlServiceJobOutcome.stale(staleReason));
            }

            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                double[] bbox = parseBbox(result.getJSONArray("bbox"));
                double theta = result.getDouble("theta");
                String bboxKey = bboxKey(bbox);
                String thetaKey = thetaKey(theta);
                String predictModelId = result.optString("predict_model_id",
                    det.mlConfig.optString("predict_model_id", null));
                Annotation existing = findExistingAnnotation(ma, predictModelId, bboxKey,
                    thetaKey);
                if (existing != null) {
                    annotationIds.add(existing.getId());
                    continue;
                }

                JSONObject featureParams = featureParams(bbox, theta,
                    result.optString("viewpoint", null));
                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
                String iaClass = result.optString("iaClass",
                    result.optString("class_name", result.optString("class", null)));
                Annotation ann = new Annotation(null, feature, iaClass);
                ann.__setMediaAsset(ma);
                ann.setAcmId(ann.getId());
                ann.setMatchAgainst(true);
                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
                ann.setPredictModelId(predictModelId);
                ann.setBboxKey(bboxKey);
                ann.setThetaKey(thetaKey);
                ann.setWbiaRegistered(Boolean.FALSE);
                ann.setWbiaRegisterAttempts(0);
                ann.setViewpoint(result.optString("viewpoint", null));
                ann.setQuality(optionalFiniteDouble(result, "score",
                    optionalFiniteDouble(result, "confidence", null)));

                // Bidirectional linkage:
                //   MediaAsset.addFeature sets Feature.asset
                //   Feature.setAnnotation sets the OWNING side of the
                //     Annotation.features collection (which is mapped-by
                //     "annotation"). Without explicitly setting this, the
                //     ANNOTATION_FEATURES join row depends on DataNucleus
                //     relationship management — fragile.
                //   Annotation.addFeature puts the feature in the in-memory
                //     list so reload returns it as expected.
                ma.addFeature(feature);
                feature.setAnnotation(ann);
                ann.addFeature(feature);
                if (enc != null) enc.addAnnotation(ann);
                shep.getPM().makePersistent(feature);
                shep.getPM().makePersistent(ann);

                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
                    result.getString("embedding_model_version"), result.getJSONArray("embedding"));
                shep.getPM().makePersistent(emb);
                annotationIds.add(ann.getId());
            }

            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            markTaskCompleted(task);
            shep.commitDBTransaction();
            return PersistResult.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "detection persist failed: " + ex.getMessage());
            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
                ex.getMessage()));
        } finally {
            shep.rollbackAndClose();
        }
    }

    private PersistResult persistExtraction(String annId, String taskId, ExtractionContext ext,
        JSONObject response) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "persistExtraction");
        List<String> annotationIds = new ArrayList<String>();

        try {
            shep.beginDBTransaction();
            Annotation ann = shep.getAnnotation(annId);
            Task task = Task.load(taskId, shep);
            if (ann == null || ann.getMediaAsset() == null) {
                markTaskDroppedStale(shep, task, "annotation missing");
                shep.commitDBTransaction();
                return PersistResult.done(MlServiceJobOutcome.stale("annotation missing"));
            }
            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
                markTaskCompleted(task);
                shep.commitDBTransaction();
                annotationIds.add(ann.getId());
                return PersistResult.ok(annotationIds);
            }

            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
                response.getString("embedding_model_version"), response.getJSONArray("embedding"));
            shep.getPM().makePersistent(emb);
            markTaskCompleted(task);
            annotationIds.add(ann.getId());
            shep.commitDBTransaction();
            return PersistResult.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "extraction persist failed: " + ex.getMessage());
            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
                ex.getMessage()));
        } finally {
            shep.rollbackAndClose();
        }
    }

    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        // Initial invocation: attempt=1, firstDeferredAt=null (the
        // gate stamps `now` so age-out is measured from this first
        // call, not from later re-fires).
        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
    }

    /**
     * Shared body for the initial {@link #waitAndRunMatch} call and
     * the re-gated {@link #runDeferredMatch} path. Drives the
     * {@link MatchVisibilityGate}: READY → run match; DEFER → publish
     * a deferred-match job through the publisher; GIVE_UP → log WARN
     * and run match against whatever is visible (partial results are
     * better than silently no match task; Codex round-2 #2).
     *
     * <p>(Empty-match-prospects design Track 2 C11.)</p>
     */
    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
        MatchVisibilityGate.GateOutcome gate = visibilityGate.gateForBatch(
            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
        switch (gate.kind) {
          case READY:
            return runMatchProspects(annotationIds, taskId, matchConfig);
          case DEFER:
            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
            return MlServiceJobOutcome.ok(annotationIds);
          case GIVE_UP:
          default:
            System.out.println(
                "WARN: MatchVisibilityGate aged out for task " + taskId +
                " after attempt=" + gate.attempt + " elapsed=" +
                gate.elapsedMillis + "ms reason=" + gate.reason +
                "; running match against current visible corpus");
            return runMatchProspects(annotationIds, taskId, matchConfig);
        }
    }

    public MlServiceJobOutcome runDeferredMatch(JSONObject jobData) {
        if (jobData == null) {
            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
        }
        List<String> annotationIds = jsonArrayToStringList(jobData.optJSONArray("annotationIds"));
        String taskId = jobData.optString("taskId", null);
        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
        // Carry forward attempt + firstDeferredAt so age-out is
        // measured by elapsed wall-clock from the original DEFER, not
        // by attempt count (Codex round-4 OQ #1).
        int attempt = jobData.optInt("attempt", 2);
        Long firstDeferredAt = jobData.has("firstDeferredAt")
            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
        // Re-gate; deferred match earns the same protection as the
        // initial call (Codex round-2 Major: don't degrade back to
        // today's bug on the first deferral).
        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
            attempt, firstDeferredAt);
    }

    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            markTaskCompleted(taskId);
            return MlServiceJobOutcome.ok(new ArrayList<String>());
        }

        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "runMatchProspects");
        try {
            shep.beginDBTransaction();
            List<Annotation> anns = new ArrayList<Annotation>();
            for (String annId : annotationIds) {
                Annotation ann = shep.getAnnotation(annId);
                if (ann != null) anns.add(ann);
            }
            if (anns.isEmpty()) {
                Task task = Task.load(taskId, shep);
                markTaskDroppedStale(shep, task, "annotations missing");
                shep.commitDBTransaction();
                return MlServiceJobOutcome.stale("annotations missing");
            }

            Task parent = Task.load(taskId, shep);
            Task matchTask = (parent == null) ? new Task() : new Task(parent);
            matchTask.setObjectAnnotations(anns);
            matchTask.addParameter("mlServiceV2Match", true);
            shep.getPM().makePersistent(matchTask);
            // findMatchProspects returns false when the match config is not
            // a vector config or matchConfig is null. Don't leave the match
            // task without a terminal status — mark the parent task error.
            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
            if (!ran) {
                matchTask.setStatus("error");
                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
                    "findMatchProspects rejected match config: " +
                    (matchConfig == null ? "null" : matchConfig.toString()));
                matchTask.setCompletionDateInMilliseconds();
                // Update the parent task in this same transaction (parent is
                // already loaded above) so the two updates commit atomically.
                // Splitting across transactions risks leaving the parent
                // "completed" if the second commit fails or the JVM dies.
                if (parent != null) {
                    markTaskError(parent, "INVALID_MATCH_CONFIG",
                        "no usable vector match config");
                }
                shep.commitDBTransaction();
                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
                    "no usable vector match config");
            }
            String matchTaskId = matchTask.getId();
            shep.commitDBTransaction();
            shep.rollbackAndClose();  // close BEFORE PairX enrichment (Track 2 C13)
            // Phase 4 (C13): PairX inspection-image enrichment. The
            // MatchResult + prospects are already persisted with
            // null inspection MediaAssets; the enricher fills them in
            // out-of-transaction via a Phase A/B/C flow per prospect.
            // Per-prospect failure is non-blocking — UI render works
            // either way, just without the inspection image.
            enrichPairxAssetsForMatchTask(matchTaskId);
            return MlServiceJobOutcome.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
        } finally {
            shep.rollbackAndClose();
        }
    }

    /**
     * Phase 4: drive {@link MatchInspectionPairxEnricher} for every
     * MatchResult attached to a child of {@code matchTaskId}. Runs
     * after the main runMatchProspects transaction has closed, so the
     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
     * design Track 2 C13.)
     */
    void enrichPairxAssetsForMatchTask(String matchTaskId) {
        if (matchTaskId == null) return;
        List<String> mrIds = collectMatchResultIds(matchTaskId);
        if (mrIds.isEmpty()) return;
        MatchInspectionPairxEnricher enricher =
            new MatchInspectionPairxEnricher(context);
        for (String mrId : mrIds) {
            try {
                enricher.enrichMatchResult(mrId);
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MlServiceProcessor.enrichPairxAssetsForMatchTask " +
                    "mr=" + mrId + " failed (non-blocking): " + ex);
            }
        }
    }

    /**
     * Open a short Shepherd, list MatchResult IDs attached to children
     * of {@code matchTaskId}, close. Returns scalar IDs only so
     * subsequent enrichment runs without DB state.
     */
    private List<String> collectMatchResultIds(String matchTaskId) {
        List<String> out = new ArrayList<String>();
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
        try {
            shep.beginDBTransaction();
            Task matchTask = Task.load(matchTaskId, shep);
            if (matchTask == null) {
                shep.commitDBTransaction();
                return out;
            }
            List<Task> children = matchTask.getChildren();
            if (children != null) {
                for (Task child : children) {
                    if (child == null) continue;
                    List<MatchResult> mrs = shep.getMatchResults(child);
                    if (mrs == null) continue;
                    for (MatchResult mr : mrs) {
                        if (mr != null && mr.getId() != null) {
                            out.add(mr.getId());
                        }
                    }
                }
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            System.out.println(
                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
                matchTaskId + ": " + ex);
        } finally {
            shep.closeDBTransaction();
        }
        return out;
    }

    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
        String code = ex == null ? null : ex.getCode();
        String message = ex == null ? null : ex.getMessage();
        if ("INVALID".equals(code) || "SUCCESS_FALSE".equals(code)) {
            return MlServiceJobOutcome.validationError(code, message);
        }
        if ("TIMEOUT".equals(code) || "NETWORK".equals(code) || "RATE_LIMITED".equals(code)
            || "SERVER_ERROR".equals(code) || "CLIENT_ERROR".equals(code)) {
            return MlServiceJobOutcome.networkError(code, message);
        }
        return MlServiceJobOutcome.networkError("UNKNOWN", message);
    }

    static String bboxKey(double[] bbox) {
        if (bbox == null || bbox.length != 4) return null;
        return Math.round(bbox[0]) + ":" + Math.round(bbox[1]) + ":" + Math.round(bbox[2]) +
            ":" + Math.round(bbox[3]);
    }

    static String thetaKey(double theta) {
        return String.format(Locale.US, "%.4f", theta);
    }

    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
        String bboxKey, String thetaKey) {
        if (ma == null) return null;
        for (Annotation ann : ma.getAnnotations()) {
            if (ann == null) continue;
            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
            if (!sameString(bboxKey, ann.getBboxKey())) continue;
            if (!sameString(thetaKey, ann.getThetaKey())) continue;
            return ann;
        }
        return null;
    }

    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
        if (!Util.stringExists(taxonomyString)) return null;
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        if (iac == null) return null;
        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
        if (configs == null || configs.length() == 0) return null;
        JSONObject mlConfig = configs.optJSONObject(0);
        if (mlConfig == null) return null;
        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
        return new ConfigPair(mlConfig, matchConfig);
    }

    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
        JSONObject mlConfig) {
        JSONObject matchConfig = null;
        JSONArray identConfigs = iac.getIdentConfig(taxy);
        if (identConfigs != null) {
            for (int i = 0; i < identConfigs.length(); i++) {
                JSONObject entry = identConfigs.optJSONObject(i);
                if (entry == null) continue;
                if (entry.optBoolean("default", false)
                    && "vector".equals(entry.optString("pipeline_root", null))) {
                    matchConfig = new JSONObject(entry.toString());
                    break;
                }
            }
        }
        if (matchConfig == null) matchConfig = new JSONObject();
        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
        }
        return matchConfig;
    }

    private JSONObject inferMatchConfig(List<String> annotationIds) {
        if (annotationIds == null || annotationIds.isEmpty()) return null;
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
        try {
            shep.beginDBTransaction();
            for (String annId : annotationIds) {
                Annotation ann = shep.getAnnotation(annId);
                if (ann == null) continue;
                Embedding emb = ann.getAnEmbedding();
                if (emb != null) {
                    JSONObject config = new JSONObject();
                    config.put("method", emb.getMethod());
                    config.put("version", emb.getMethodVersion());
                    config.put("pipeline_root", "vector");
                    return config;
                }
            }
            return null;
        } finally {
            shep.rollbackAndClose();
        }
    }

    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
        JSONObject mlConfig) {
        JSONObject config = (matchConfig == null) ? new JSONObject()
            : new JSONObject(matchConfig.toString());
        if (embeddingSource != null) {
            if (!Util.stringExists(config.optString("method", null))
                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
                config.put("method", embeddingSource.optString("embedding_model_id"));
            }
            if (!Util.stringExists(config.optString("version", null))
                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
                config.put("version", embeddingSource.optString("embedding_model_version"));
            }
        }
        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
        }
        return config;
    }

    private String detectionStaleReason(MediaAsset ma, Encounter enc, String encounterId) {
        if (ma == null) return "media asset missing";
        if (Util.stringExists(encounterId) && enc == null) return "encounter missing";
        String status = ma.getDetectionStatus();
        if (IBEISIA.STATUS_COMPLETE_MLSERVICE.equals(status)) return "detection already complete";
        if (IBEISIA.STATUS_COMPLETE.equals(status)) return "detection already complete";
        return null;
    }

    private String effectiveTaxonomyString(String taxonomyString, Encounter enc) {
        if (Util.stringExists(taxonomyString)) return taxonomyString;
        if (enc != null) return enc.getTaxonomyString();
        return null;
    }

    private void markTaskError(String taskId, String code, String message) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "markTaskError");
        try {
            shep.beginDBTransaction();
            Task task = Task.load(taskId, shep);
            markTaskError(task, code, message);
            shep.commitDBTransaction();
        } finally {
            shep.rollbackAndClose();
        }
    }

    private void markTaskCompleted(String taskId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "markTaskCompleted");
        try {
            shep.beginDBTransaction();
            markTaskCompleted(Task.load(taskId, shep));
            shep.commitDBTransaction();
        } finally {
            shep.rollbackAndClose();
        }
    }

    private void markTaskError(Task task, String code, String message) {
        if (task == null) return;
        task.setStatus(IBEISIA.STATUS_ERROR);
        task.setStatusDetailsAddError(code == null ? "UNKNOWN" : code, message);
        task.setCompletionDateInMilliseconds();
    }

    private void markTaskCompleted(Task task) {
        if (task == null) return;
        task.setStatus("completed");
        task.setCompletionDateInMilliseconds();
    }

    private void markTaskDroppedStale(Shepherd shep, Task task, String reason) {
        if (task == null) return;
        task.setStatus(IBEISIA.STATUS_DROPPED_STALE);
        task.setStatusDetailsAddLog(reason);
        task.setCompletionDateInMilliseconds();
    }

    /**
     * Build and publish a deferred-match payload via the injected
     * {@link DeferredMatchPublisher}. The real publisher wraps
     * {@link IAGateway#requeueJob} with {@code increment=true} so the
     * 30s fixed delay applies (Codex round-4 Blocker: setting
     * {@code __queueRetries} alone does not create the delay).
     *
     * <p>Routing flags: {@code mlServiceV2: true} (IAGateway v2
     * dispatch) AND {@code deferredMatch: true} (MlServiceProcessor
     * deferred branch). Both required — Codex round-5 Blocker
     * documented the dispatch contract.</p>
     *
     * <p>Gate metadata on the payload: {@code attempt} (incremented
     * per DEFER), {@code firstDeferredAt} (epoch-ms of the first
     * DEFER, preserved across re-fires for elapsed-time age-out),
     * {@code lastGateReason} (Codex round-2 #6 diagnostic).</p>
     */
    private void enqueueDeferredMatch(List<String> annotationIds, String parentTaskId,
        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
        JSONObject payload = new JSONObject();
        // Routing flags — both required for the dispatcher to land
        // the requeue back on MlServiceProcessor's deferred entry
        // point (Codex round-5 Blocker).
        payload.put("mlServiceV2", true);
        payload.put("deferredMatch", true);
        // Diagnostic marker — not the routing contract.
        payload.put("mlServiceV2DeferredMatch", true);
        payload.put("annotationIds", new JSONArray(annotationIds));
        if (Util.stringExists(parentTaskId)) payload.put("taskId", parentTaskId);
        if (matchConfig != null) payload.put("matchConfig", matchConfig);
        // Carry __context in the payload so the dispatcher's
        // jobj.optString("__context", "context0") fallback at
        // IAGateway.java doesn't silently route the deferred-match
        // into context0 when this processor is running in a non-default
        // context.
        payload.put("__context", context);
        // Gate metadata — incremented for next attempt; firstDeferredAt
        // preserved across re-fires (Codex round-4 OQ #1).
        payload.put("attempt", gate.attempt + 1);
        payload.put("firstDeferredAt", gate.firstDeferredAt);
        if (gate.reason != null) payload.put("lastGateReason", gate.reason);
        try {
            deferredPublisher.publish(payload);
        } catch (Exception ex) {
            // requeueJob doesn't throw declared exceptions, but a future
            // publisher impl might. Don't let publish-failure leak past
            // the orchestrator.
            System.out.println("MlServiceProcessor.enqueueDeferredMatch failed: " + ex);
        }
    }

    private static JSONObject featureParams(double[] bbox, double theta, String viewpoint) {
        JSONObject params = new JSONObject();
        params.put("x", bbox[0]);
        params.put("y", bbox[1]);
        params.put("width", bbox[2]);
        params.put("height", bbox[3]);
        params.put("theta", theta);
        params.put("viewpoint", viewpoint);
        return params;
    }

    private static double[] parseBbox(JSONArray bbox) {
        return new double[] {
            bbox.getDouble(0), bbox.getDouble(1), bbox.getDouble(2), bbox.getDouble(3)
        };
    }

    private static double[] toDoubleArray(int[] bbox) {
        return new double[] { bbox[0], bbox[1], bbox[2], bbox[3] };
    }

    private static List<String> jsonArrayToStringList(JSONArray array) {
        List<String> values = new ArrayList<String>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, null);
            if (Util.stringExists(value)) values.add(value);
        }
        return values;
    }

    private static Double optionalFiniteDouble(JSONObject obj, String key, Double fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        double value = obj.optDouble(key, Double.NaN);
        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return value;
    }

    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
        if (ann == null || ann.numberEmbeddings() < 1) return false;
        if (matchConfig == null) return ann.numberEmbeddings() > 0;
        String method = matchConfig.optString("method", null);
        String version = matchConfig.optString("version", null);
        if (!Util.stringExists(method)) return ann.numberEmbeddings() > 0;
        return ann.getEmbeddingByMethod(method, version) != null;
    }

    private static boolean sameString(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static final class ConfigPair {
        final JSONObject mlConfig;
        final JSONObject matchConfig;

        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
        }
    }

    private static final class DetectionContext {
        final String imageUri;
        final String apiEndpoint;
        final JSONObject mlConfig;
        final JSONObject matchConfig;
        final MlServiceJobOutcome outcome;

        DetectionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
            JSONObject matchConfig) {
            this.imageUri = imageUri;
            this.apiEndpoint = apiEndpoint;
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
            this.outcome = null;
        }

        private DetectionContext(MlServiceJobOutcome outcome) {
            this.imageUri = null;
            this.apiEndpoint = null;
            this.mlConfig = null;
            this.matchConfig = null;
            this.outcome = outcome;
        }

        static DetectionContext done(MlServiceJobOutcome outcome) {
            return new DetectionContext(outcome);
        }
    }

    private static final class ExtractionContext {
        final String imageUri;
        final String apiEndpoint;
        final JSONObject mlConfig;
        final JSONObject matchConfig;
        final double[] bbox;
        final double theta;
        final MlServiceJobOutcome outcome;

        ExtractionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
            JSONObject matchConfig, double[] bbox, double theta) {
            this.imageUri = imageUri;
            this.apiEndpoint = apiEndpoint;
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
            this.bbox = bbox;
            this.theta = theta;
            this.outcome = null;
        }

        private ExtractionContext(MlServiceJobOutcome outcome) {
            this.imageUri = null;
            this.apiEndpoint = null;
            this.mlConfig = null;
            this.matchConfig = null;
            this.bbox = null;
            this.theta = 0.0d;
            this.outcome = outcome;
        }

        static ExtractionContext done(MlServiceJobOutcome outcome) {
            return new ExtractionContext(outcome);
        }
    }

    private static final class PersistResult {
        final List<String> annotationIds;
        final MlServiceJobOutcome outcome;

        private PersistResult(List<String> annotationIds, MlServiceJobOutcome outcome) {
            this.annotationIds = annotationIds;
            this.outcome = outcome;
        }

        static PersistResult ok(List<String> annotationIds) {
            return new PersistResult(annotationIds, null);
        }

        static PersistResult done(MlServiceJobOutcome outcome) {
            return new PersistResult(null, outcome);
        }
    }
}
