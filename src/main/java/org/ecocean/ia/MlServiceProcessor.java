package org.ecocean.ia;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.Occurrence;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
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
            markDetectionFailure(maId, taskId, "PERSIST",
                "load/revalidate failed: " + ex.getMessage());
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
            markDetectionFailure(maId, taskId, ex.getCode(), ex.getMessage());
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
                // Mark the MA terminal so bulk-import polling completes. Note:
                // this case is an admin/config error (not a per-asset failure)
                // and ideally would have its own MediaAsset status. Using
                // STATUS_ERROR keeps the polling tally honest pending a richer
                // status model.
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
                    "_mlservice_conf missing predict_model_id"));
            }

            URL webUrl = ma.webURL();
            if (webUrl == null) {
                markTaskError(task, "INVALID_IMAGE_URI",
                    "MediaAsset " + maId + " has no webURL");
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError(
                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
            }

            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
            shep.commitDBTransaction();
            return new DetectionContext(webUrl.toString(),
                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
                configs.matchConfig, effectiveTaxonomy);
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
            // Best-effort rollback so markDetectionFailure (which opens a
            // second Shepherd on the same MA row) doesn't block on locks.
            // Swallow rollback exceptions — they must not shadow the
            // original failure outcome.
            try {
                shep.rollbackDBTransaction();
            } catch (Exception rbEx) {
                System.out.println(
                    "[WARN] MlServiceProcessor.finalizeZeroDetections rollback " +
                    "before cleanup failed: " + rbEx);
            }
            markDetectionFailure(maId, taskId, "PERSIST",
                "zero-detection finalize failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        } finally {
            // Guarded close — if rollbackAndClose throws here (e.g., the
            // PM is already closed from a deeper bug), the deliberate
            // persistError return above must not be shadowed.
            try {
                shep.rollbackAndClose();
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MlServiceProcessor.finalizeZeroDetections " +
                    "rollbackAndClose failed: " + ex);
            }
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

            // Resolve the _save_as remap inputs ONCE (not per-detection): the
            // IA.json config and the effective Taxonomy that selected the
            // detection configs. taxy stays null if no taxonomy is known, in
            // which case applySaveAs is a no-op. getOrCreateTaxonomy(..., false)
            // does not persist a new row.
            IAJsonProperties iaConf = IAJsonProperties.iaConfig();
            Taxonomy taxy = Util.stringExists(det.effectiveTaxonomyString)
                ? shep.getOrCreateTaxonomy(det.effectiveTaxonomyString, false) : null;

            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                double[] bbox = parseBbox(result.getJSONArray("bbox"));
                double theta = result.getDouble("theta");
                String[] mv = parseEmbeddingMethodVersion(result);
                // Resolve the iaClass up front, then the IA.json _save_as remap
                // (parity with legacy IBEISIA detection, IBEISIA.java:1234,2150)
                // so the annotation carries the catalog-stored class that
                // Annotation.getMatchingSetQuery filters match candidates on.
                // Prefer a true species-classifier's class (the pipeline puts it
                // on top-level "iaClass" via top_species), then legacy
                // "class_name"/"class".
                String rawIaClass = result.optString("iaClass",
                    result.optString("class_name", result.optString("class", null)));
                String iaClass = applySaveAs(iaConf, taxy, rawIaClass);
                // Fallback: when the classifier gave us nothing -- e.g. a
                // viewpoint-only classifier like whale shark's
                // msv2_multilabel_flip -- use the DETECTOR's own class
                // ("detection_class"), mirroring legacy WBIA which read
                // iaResult.optString("class"). Without a class the box is
                // stamped null and binAnnotsByIaClass (IA.java) drops it from
                // matching. Gate this fallback on validity so a misconfigured
                // detector's garbage class is NOT stamped where we'd otherwise
                // (correctly) leave null. NB: never read the nested
                // "classification.class" -- that is a viewpoint label
                // (e.g. "species:back"), not an iaClass.
                if (!Util.stringExists(iaClass)) {
                    String detRaw = result.optString("detection_class", null);
                    String detMapped = applySaveAs(iaConf, taxy, detRaw);
                    // Require a taxonomy we can validate against: if we cannot
                    // confirm the detector class is a real iaClass, leave null
                    // (the old, safe behavior) rather than trust a bare label.
                    if (Util.stringExists(detMapped) && (iaConf != null) && (taxy != null)
                        && iaConf.isValidIAClass(taxy, detMapped)) {
                        rawIaClass = detRaw;
                        iaClass = detMapped;
                    } else if (Util.stringExists(detRaw)) {
                        System.out.println(
                            "WARNING: MlServiceProcessor.persistDetections ignoring detection_class '" +
                            detRaw + "' (maps to '" + detMapped + "') -- not a valid iaClass for " +
                            "taxonomy " + taxy + "; leaving iaClass null.");
                    }
                }
                // WBIA parity (IBEISIA.convertAnnotation): warn on a class that
                // is not valid for this taxonomy but still persist it (non-fatal).
                if (Util.stringExists(iaClass) && (iaConf != null) && (taxy != null)
                    && !iaConf.isValidIAClass(taxy, iaClass)) {
                    System.out.println(
                        "WARNING: MlServiceProcessor.persistDetections resolved iaClass '" +
                        iaClass + "' not valid for taxonomy " + taxy + " (raw='" + rawIaClass +
                        "'); persisting anyway.");
                }
                Annotation existing = findExistingAnnotation(ma, bbox, theta);
                if (existing != null) {
                    // Dedup hit: an annotation with this bbox+theta already
                    // exists on the MediaAsset (idempotent retry, or another
                    // model produced an identical box). Reuse it. If it has no
                    // embedding for THIS (method, version), attach one so a
                    // later model run does not leave the box unmatchable for
                    // that method. Annotation.addEmbedding() bumps the parent's
                    // version, so the reconciler reindexes its nested embeddings
                    // into the document _source.
                    if (existing.getEmbeddingByMethod(mv[0], mv[1]) == null) {
                        Embedding emb = new Embedding(existing, mv[0], mv[1],
                            result.getJSONArray("embedding"));
                        shep.getPM().makePersistent(emb);
                    }
                    // Self-heal a reused annotation's iaClass when we now have a
                    // better value. Two cases, both guarded so a legitimately
                    // different stored class is never clobbered:
                    //  (a) it has NO class (e.g. persisted before the
                    //      detection_class fallback existed) -- stamp the freshly
                    //      resolved class so binAnnotsByIaClass stops dropping it;
                    //  (b) pre-_save_as row: stored class is exactly the raw
                    //      model class we now remap to something else.
                    // setIAClass() bumps VERSION (OpenSearch reindex).
                    if (Util.stringExists(iaClass) && !iaClass.equals(existing.getIAClass())) {
                        boolean existingHasNoClass = !Util.stringExists(existing.getIAClass());
                        boolean preSaveAsRow = Util.stringExists(rawIaClass)
                            && rawIaClass.equals(existing.getIAClass());
                        if (existingHasNoClass || preSaveAsRow) {
                            existing.setIAClass(iaClass);
                        }
                    }
                    annotationIds.add(existing.getId());
                    continue;
                }

                // Viewpoint: prefer an explicit result "viewpoint"; otherwise
                // fall back to the orientation model's label (e.g. whale shark's
                // whaleshark_v3 -> "left"/"right"). The pipeline only sets a
                // top-level "viewpoint" from a classifier 'viewpoint' field,
                // which viewpoint-less classifiers (msv2_multilabel_flip) do not
                // provide -- leaving it null. Whale-shark matching DOES filter
                // the candidate set on viewpoint (getMatchingSetQuery via
                // getViewpointAndNeighbors), and the catalog's viewpoints are
                // left/right, so without this the box matches across both sides.
                String viewpoint = result.optString("viewpoint", null);
                if (!Util.stringExists(viewpoint)) {
                    JSONObject orientation = result.optJSONObject("orientation");
                    if (orientation != null) {
                        String label = orientation.optString("label", null);
                        // Only adopt a fallback viewpoint Wildbook understands
                        // (Annotation.VALID_VIEWPOINTS); a stray orientation
                        // label would never match getViewpointAndNeighbors.
                        if (Util.stringExists(label) && Annotation.isValidViewpoint(label)) {
                            viewpoint = label;
                        }
                    }
                }
                JSONObject featureParams = featureParams(bbox, theta, viewpoint);
                Feature feature = new Feature(BOUNDING_BOX_FEATURE, featureParams);
                Annotation ann = new Annotation(null, feature, iaClass);
                ann.setAcmId(ann.getId());
                ann.setMatchAgainst(true);
                ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
                ann.setWbiaRegistered(Boolean.FALSE);
                ann.setWbiaRegisterAttempts(0);
                ann.setViewpoint(viewpoint);
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
                // Intentionally do NOT add ann to enc here. Encounter
                // linkage is deferred to assignEncounters() below so
                // it can clone the encounter per detection — the
                // "one Encounter = one animal" domain rule. Pre-
                // assigning all detections to enc here makes
                // assignEncounters' needsEncounter set empty and the
                // cloning skips. Mirrors legacy IBEISIA flow where
                // createAnnotationFromIAResult also creates the
                // Annotation "with no Encounter" (IBEISIA.java:1127).
                shep.getPM().makePersistent(feature);
                shep.getPM().makePersistent(ann);

                Embedding emb = new Embedding(ann, mv[0], mv[1],
                    result.getJSONArray("embedding"));
                shep.getPM().makePersistent(emb);
                annotationIds.add(ann.getId());
            }

            // Capture originating bulk-import context BEFORE assignEncounters
            // so cloned encounters inherit it. enc may be null (non-import
            // detection path); guard accordingly.
            ImportTask originatingTask = (enc != null) ? enc.getImportTask(shep) : null;
            Occurrence originatingOcc = (enc != null) ? shep.getOccurrence(enc) : null;

            // Mirror legacy IBEISIA detection-callback (IBEISIA.java:1671):
            // assignEncounters walks the MA's annotations and either replaces
            // trivial placeholders with detections, or clones the originating
            // encounter once per additional non-first detection. Without this,
            // ml-service detections of multiple animals on one image all land
            // on the same Encounter, violating "one Encounter = one animal".
            List<Encounter> assignedEncs = ma.assignEncounters(shep);

            // Wire any newly-cloned encounters into the same ImportTask and
            // Occurrence as the originating encounter (mirrors legacy
            // IBEISIA.java:1677-1700). Skipped when there is no originating
            // bulk-import context.
            if ((assignedEncs != null) && (enc != null)) {
                for (Encounter cloneOrTouched : assignedEncs) {
                    if (cloneOrTouched == null || cloneOrTouched == enc) continue;
                    if ((originatingTask != null) &&
                        (cloneOrTouched.getImportTask(shep) == null)) {
                        originatingTask.addEncounter(cloneOrTouched);
                    }
                    if ((originatingOcc != null) &&
                        (shep.getOccurrence(cloneOrTouched) == null)) {
                        originatingOcc.addEncounter(cloneOrTouched);
                        cloneOrTouched.setOccurrenceID(originatingOcc.getOccurrenceID());
                    }
                }
            }

            ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            markTaskCompleted(task);
            shep.commitDBTransaction();
            return PersistResult.ok(annotationIds);
        } catch (Exception ex) {
            // Release locks on the MA before markDetectionFailure (which
            // opens a second Shepherd on the same MA row) — swallow the
            // rollback exception so it can't shadow the original failure.
            try {
                shep.rollbackDBTransaction();
            } catch (Exception rbEx) {
                System.out.println(
                    "[WARN] MlServiceProcessor.persistDetections rollback before " +
                    "cleanup failed: " + rbEx);
            }
            markDetectionFailure(maId, taskId, "PERSIST",
                "detection persist failed: " + ex.getMessage());
            return PersistResult.done(MlServiceJobOutcome.persistError("PERSIST",
                ex.getMessage()));
        } finally {
            // Guarded close so the deliberate persistError return is
            // never shadowed by a rollbackAndClose throw.
            try {
                shep.rollbackAndClose();
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MlServiceProcessor.persistDetections " +
                    "rollbackAndClose failed: " + ex);
            }
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
            String[] mv = parseEmbeddingMethodVersion(response);
            Embedding emb = new Embedding(ann, mv[0], mv[1],
                response.getJSONArray("embedding"));
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
     * <p>Bulk-import "Skip identification" honored here, before the
     * gate runs. The flag is stamped onto the IA Task parameters by
     * {@link org.ecocean.api.BulkImport#initiateIA} and inherited by
     * each per-MA child Task ({@link Task#Task(Task)} copies
     * parameters). Reading it at this single chokepoint covers the
     * detection and extraction entry paths AND the deferred re-fire
     * path (which goes straight to this method via
     * {@link #runDeferredMatch}). The legacy WBIA path has its own
     * skipIdent gate in {@code IBEISIA.processCallback}.</p>
     *
     * <p>(Empty-match-prospects design Track 2 C11.)</p>
     */
    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
        if (readSkipIdent(taskId)) {
            System.out.println(
                "NOTICE: MlServiceProcessor skipped identification for task " + taskId +
                " (skipIdent=true); annotationIds=" + annotationIds);
            return MlServiceJobOutcome.ok(annotationIds);
        }
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
        // Honor skipIdent before inferMatchConfig, which opens a Shepherd
        // to inspect existing embeddings (matchConfig fallback path).
        // A skipIdent task should do no match-side DB work at all
        // (Codex code-review Low #1).
        if (readSkipIdent(taskId)) {
            System.out.println(
                "NOTICE: MlServiceProcessor.runDeferredMatch skipped identification for task " +
                taskId + " (skipIdent=true); annotationIds=" + annotationIds);
            return MlServiceJobOutcome.ok(annotationIds);
        }
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

    /** Package-private seam so tests can substitute a Shepherd whose checked commit fails. */
    Shepherd openShepherd(String action) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + action);
        return shep;
    }

    /** Spec §4.1 step 3: D's parameters minus the keys a run sets for itself. Pure. */
    static JSONObject matchIntakeParams(JSONObject parentParams) {
        JSONObject p = (parentParams == null) ? new JSONObject() : new JSONObject(parentParams.toString());
        p.remove("ibeis.detection");          // detection gauges must not count match tasks
        p.remove(Task.PARAM_RUN_ID);
        p.remove(Task.PARAM_V2_MATCH);
        p.remove(Task.PARAM_OPTION);
        p.remove("ibeis.identification");
        return p;
    }

    /** Spec §4.1 step 2: one group per encounter in first-seen order; no-encounter annotations form their own group. Pure. */
    static List<List<Annotation> > groupByEncounter(List<Annotation> anns, Map<String, String> annIdToEncId) {
        Map<String, List<Annotation> > groups = new LinkedHashMap<String, List<Annotation> >();
        for (Annotation ann : anns) {
            String enc = annIdToEncId.get(ann.getId());
            String key = (enc == null) ? "\u0000no-encounter" : enc;
            List<Annotation> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<Annotation>();
                groups.put(key, g);
            }
            g.add(ann);
        }
        return new ArrayList<List<Annotation> >(groups.values());
    }

    // per-group recovery record (spec §4.1 step 4)
    private static final class GroupRecord {
        final IA.IntakeOutcome outcome;
        boolean committed = false;
        final List<String> annotationIds = new ArrayList<String>();

        GroupRecord(IA.IntakeOutcome outcome, List<Annotation> group) {
            this.outcome = outcome;
            for (Annotation ann : group) annotationIds.add(ann.getId());
        }
    }

    private static String annotationIdList(List<Annotation> anns) {
        List<String> ids = new ArrayList<String>();
        for (Annotation a : anns) ids.add(a.getId());
        return ids.toString();
    }

    /**
     * Spec §4.1: every default identification option runs for each encounter's annotations
     * through the shared intake. {@code matchConfig} keeps its gate / embedding-presence roles
     * upstream (waitAndRunMatchInternal) and completes each vector option (method/version/
     * api_endpoint) where IA.json leaves them out; it no longer selects the algorithm.
     */
    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            markTaskCompleted(taskId);
            return MlServiceJobOutcome.ok(new ArrayList<String>());
        }
        List<GroupRecord> processed = new ArrayList<GroupRecord>();
        // Keep scalar inputs even if loading/grouping fails before any intake can begin.
        List<List<String>> groupsToRecover = new ArrayList<List<String>>();
        groupsToRecover.add(new ArrayList<String>(annotationIds));
        String inheritedParameters = null;
        String failureCode = null;
        String failureMessage = null;
        String parentId = taskId;
        MlServiceJobOutcome result = null;
        Shepherd shep = openShepherd("runMatchProspects");
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
            if (parent == null) {   // as before: an orphan root when the detection task is gone
                parent = (taskId == null) ? new Task() : new Task(taskId);
                shep.getPM().makePersistent(parent);
            }
            parentId = parent.getId();
            Map<String, String> annToEnc = new HashMap<String, String>();
            for (Annotation ann : anns) {
                Encounter enc = ann.findEncounter(shep);
                annToEnc.put(ann.getId(), (enc == null) ? null : enc.getCatalogNumber());
            }
            List<List<Annotation> > groups = groupByEncounter(anns, annToEnc);
            JSONObject inherited = matchIntakeParams(parent.getParameters());
            inheritedParameters = inherited.toString();
            groupsToRecover.clear();
            for (List<Annotation> group : groups) {
                List<String> ids = new ArrayList<String>();
                for (Annotation ann : group) ids.add(ann.getId());
                groupsToRecover.add(ids);
            }
            int noWorkGroups = 0;
            for (List<Annotation> group : groups) {
                IA.IntakeOutcome o = IA.intakeAnnotationsUnder(shep, group, parent, inherited, false,
                    matchConfig);
                if (o == null) {
                    failureCode = "MATCH_PERSIST";
                    failureMessage = "no identification run for annotations " + annotationIdList(group);
                    break;
                }
                GroupRecord rec = new GroupRecord(o, group);
                processed.add(rec);
                if (o.persistFailed) {
                    failureCode = "MATCH_PERSIST";
                    failureMessage = "could not persist the identification run for annotations " +
                        annotationIdList(group);
                    break;
                }
                if (o.noWork) noWorkGroups++;
                // one checked commit per group: a later group's failure never rolls back an
                // earlier group's vector leaves
                if (!shep.commitDBTransactionWithStatus()) {
                    failureCode = "MATCH_PERSIST";
                    failureMessage = "group commit failed for identification run " + o.umbrella.getId();
                    break;
                }
                rec.committed = true;
                shep.beginDBTransaction();
            }
            if (failureCode == null) {
                if (noWorkGroups == groups.size()) {
                    markTaskError(parent, "INVALID_MATCH_CONFIG", "no default identification options");
                    if (shep.commitDBTransactionWithStatus()) {
                        result = MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
                            "no default identification options");
                    } else {
                        failureCode = "MATCH_PERSIST";
                        failureMessage = "could not commit the INVALID_MATCH_CONFIG mark on " + parentId;
                    }
                } else {
                    result = MlServiceJobOutcome.ok(annotationIds);
                }
            }
        } catch (Exception ex) {
            failureCode = "MATCH";
            failureMessage = "identification intake failed: " + ex.getMessage();
            ex.printStackTrace();
        } finally {
            shep.rollbackAndClose();   // rolls back whatever the failed group left open
        }
        if (failureCode != null) {
            recoverMatchFailure(parentId, processed, groupsToRecover, inheritedParameters, failureCode, failureMessage);
            return MlServiceJobOutcome.persistError(failureCode, failureMessage);
        }
        // PairX inspection-image generation is intentionally OFF the synchronous match path.
        // The UI lazy-fetches each prospect's inspection asset via
        // POST /api/v3/match-inspection/{mrId}/{annId} when the inspector opens.
        return result;
    }

    /**
     * Spec §4.1 step 5. With a fresh Shepherd: for every processed group whose commit did not
     * succeed but whose run was persisted by the intake core, mark every option task whose job
     * was not successfully published (every vector option; HotSpotter options whose publication
     * failed) error MATCH_PERSIST, and the umbrella too unless it is itself a published option;
     * leave published HotSpotter options alone (their job is durable and will complete them).
     * Mark the detection/extraction task with {@code code}. Groups whose commit succeeded are
     * untouched. Groups with no durable run receive terminal error umbrellas holding their
     * annotations, so the import can finish. The recovery commit is checked; failures log ids.
     */
    private void recoverMatchFailure(String parentTaskId, List<GroupRecord> processed,
        List<List<String>> groups, String inheritedParameters, String code, String message) {
        Shepherd shep = openShepherd("runMatchProspects.recover");
        List<String> marked = new ArrayList<String>();
        try {
            shep.beginDBTransaction();
            java.util.Set<String> covered = new java.util.HashSet<String>();
            for (GroupRecord rec : processed) {
                if (rec.committed) {
                    covered.addAll(rec.annotationIds);
                    continue;
                }
                if (rec.outcome.persistFailed) continue;
                IA.IntakeOutcome o = rec.outcome;
                if (o.umbrella == null) continue;
                Task umbrella = Task.load(o.umbrella.getId(), shep);
                if (umbrella == null) continue;
                covered.addAll(rec.annotationIds);
                boolean umbrellaIsPublishedOption = false;
                for (IA.OptionResult r : o.options) {
                    if (r.published && !r.vector) {
                        if (r.task.getId().equals(umbrella.getId())) umbrellaIsPublishedOption = true;
                        continue;
                    }
                    Task opt = Task.load(r.task.getId(), shep);
                    if ((opt == null) || opt.statusInEndState()) continue;
                    markTaskError(opt, "MATCH_PERSIST", message);
                    marked.add(opt.getId());
                }
                if (!umbrellaIsPublishedOption && !umbrella.statusInEndState()) {
                    markTaskError(umbrella, "MATCH_PERSIST", message);
                    marked.add(umbrella.getId());
                }
            }
            Task parent = Task.load(parentTaskId, shep);
            if (parent == null) {
                parent = (parentTaskId == null) ? new Task() : new Task(parentTaskId);
                shep.getPM().makePersistent(parent);
            }
            JSONObject inherited = (inheritedParameters == null) ? matchIntakeParams(parent.getParameters()) :
                new JSONObject(inheritedParameters);
            for (List<String> group : groups) {
                List<Annotation> missing = new ArrayList<Annotation>();
                for (String id : group) {
                    if (covered.contains(id)) continue;
                    Annotation ann = shep.getAnnotation(id);
                    if (ann != null) missing.add(ann);
                }
                if (missing.isEmpty()) continue;
                // A failed/unattempted group still needs a durable run: the import cannot age
                // out annotations that have no tasks. Published options above remain untouched.
                Task failed = new Task();
                failed.setParameters(new JSONObject(inherited.toString()));
                failed.addParameter(Task.PARAM_RUN_ID, failed.getId());
                failed.setObjectAnnotations(missing);
                failed.setParent(parent);
                markTaskError(failed, code, message);
                shep.getPM().makePersistent(failed);
                marked.add(failed.getId());
            }
            markTaskError(parent, code, message);
            if (!shep.commitDBTransactionWithStatus()) {
                System.out.println("ERROR: MlServiceProcessor.runMatchProspects() recovery commit failed; " +
                    "tasks left pending: " + marked + "; parent " + parentTaskId + "; " + message);
            }
        } catch (Exception ex) {
            System.out.println("ERROR: MlServiceProcessor.runMatchProspects() recovery threw " + ex +
                "; tasks: " + marked + "; parent " + parentTaskId);
            ex.printStackTrace();
        } finally {
            shep.rollbackAndClose();
        }
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

    /**
     * Returns an existing bounding-box Annotation on this MediaAsset whose
     * geometry matches the given bbox (rounded to ints) and theta (to 4
     * decimals), else null. Feature-based — the authoritative
     * Annotation&#8596;MediaAsset link — mirroring the legacy
     * {@code IBEISIA.duplicateDetection} approach but also comparing theta.
     *
     * <p>Model-agnostic by design: we do not want a second annotation for the
     * same region regardless of which model (or legacy WBIA flow) produced it.
     * Missing theta on a legacy Feature is treated as 0.0 so legacy boxes still
     * dedup. The numeric theta comparison also normalizes {@code -0.0} vs
     * {@code 0.0}.</p>
     */
    static Annotation findExistingAnnotation(MediaAsset ma, double[] bbox, double theta) {
        if (ma == null || bbox == null || bbox.length != 4) return null;
        List<Feature> fts = ma.getFeatures();
        if (fts == null) return null;
        for (Feature ft : fts) {
            if (ft == null || !ft.isType(BOUNDING_BOX_FEATURE)) continue;
            Annotation ann = ft.getAnnotation();
            if (ann == null) continue;
            JSONObject p = ft.getParameters();
            if (p == null) continue;
            if (Math.round(p.optDouble("x", 0)) == Math.round(bbox[0])
                && Math.round(p.optDouble("y", 0)) == Math.round(bbox[1])
                && Math.round(p.optDouble("width", 0)) == Math.round(bbox[2])
                && Math.round(p.optDouble("height", 0)) == Math.round(bbox[3])
                && Math.abs(p.optDouble("theta", 0.0d) - theta) < 1.0e-4) {
                return ann;
            }
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

    /**
     * Derive the {@code (method, methodVersion)} pair that Wildbook stamps
     * on Embedding rows and uses in matchConfig from an ml-service
     * {@code /extract/} response.
     *
     * <p>Wildbook's vector-matching pipeline (configured via IA.json
     * {@code pipeline_root="vector"} entries) filters embeddings on
     * {@code METHOD}/{@code METHODVERSION}. IA.json sets these as
     * {@code method="miewid"}, {@code version="msv4.1"}, derived by
     * dash-splitting the ml-service {@code model_id}. The same
     * derivation is done by the legacy v1 path
     * ({@code MLService.getMethodValues}) so existing rows written by
     * {@code importMiewidEmbeddings.jsp} and the v1 send path use this
     * shape. Writing raw {@code embedding_model_id} /
     * {@code embedding_model_version} from the response instead would
     * stamp {@code ("miewid-msv4.1", "4.1")}, which never satisfies the
     * matchConfig filter and renders the embedding unfindable.</p>
     *
     * <p>Rules: read {@code embedding_model_id} (the v2 contract field).
     * If it contains a {@code -} with non-empty text on both sides, split
     * on the first dash and use the left side as method, the right side
     * as version. If there is no dash, or the dash is leading/trailing
     * (leaving one side empty), treat the ID as a single token and fall
     * back to the explicit {@code embedding_model_version} field for the
     * version. Throws {@link IllegalStateException} when either
     * component cannot be derived, mirroring the
     * "missing-required-field" failure mode of the previous
     * {@code getString} calls but with a clearer message.</p>
     */
    static String[] parseEmbeddingMethodVersion(JSONObject response) {
        if (response == null) {
            throw new IllegalStateException(
                "ml-service response missing: cannot derive (method, version)");
        }
        String embeddingModelId = response.optString("embedding_model_id", null);
        if (!Util.stringExists(embeddingModelId)) {
            throw new IllegalStateException(
                "ml-service response missing embedding_model_id");
        }
        String method;
        String version;
        int dash = embeddingModelId.indexOf('-');
        if (dash > 0 && dash < embeddingModelId.length() - 1) {
            method = embeddingModelId.substring(0, dash);
            version = embeddingModelId.substring(dash + 1);
        } else {
            method = embeddingModelId;
            version = response.optString("embedding_model_version", null);
        }
        if (!Util.stringExists(method) || !Util.stringExists(version)) {
            throw new IllegalStateException(
                "ml-service response yields incomplete (method=" + method
                    + ", version=" + version + ") from embedding_model_id="
                    + embeddingModelId + ", embedding_model_version="
                    + response.optString("embedding_model_version", null));
        }
        return new String[] { method, version };
    }

    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
        JSONObject mlConfig) {
        JSONObject config = (matchConfig == null) ? new JSONObject()
            : new JSONObject(matchConfig.toString());
        if (embeddingSource != null) {
            boolean needMethod = !Util.stringExists(config.optString("method", null));
            boolean needVersion = !Util.stringExists(config.optString("version", null));
            // Only parse if we'd actually consume the fallback. The parser
            // throws on malformed input; we don't want to surface that
            // when matchConfig is already fully populated from IA.json.
            if ((needMethod || needVersion)
                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
                String[] mv = parseEmbeddingMethodVersion(embeddingSource);
                if (needMethod) config.put("method", mv[0]);
                if (needVersion) config.put("version", mv[1]);
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

    /**
     * Apply the IA.json {@code _save_as} iaClass remap, restoring parity with
     * the legacy IBEISIA detection path ({@code IBEISIA.java:1234,2150}). The
     * ml-service returns a model-native class (e.g. {@code "zebra_grevys"});
     * {@code _save_as} maps it to the catalog-stored class (e.g. {@code
     * "zebra"}) so the new annotation shares the iaClass that {@link
     * org.ecocean.Annotation#getMatchingSetQuery} filters match candidates on.
     *
     * <p>Null-safe by design: if the config, taxonomy, or raw class is
     * missing, the raw class is returned unchanged. In particular a class-less
     * detection must NOT fall through to a {@code _default._save_as} default,
     * which would stamp a real catalog class onto an unclassified box.</p>
     *
     * <p>Package-visible + pure (config and taxonomy injected) so it unit-tests
     * without a deployment IA.json or a database.</p>
     */
    static String applySaveAs(IAJsonProperties iac, Taxonomy taxy, String rawIaClass) {
        if (iac == null || taxy == null || !Util.stringExists(rawIaClass)) return rawIaClass;
        return iac.convertIAClassForTaxonomy(rawIaClass, taxy);
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

    /**
     * Detection-only failure marker: transitions both the Task and the
     * MediaAsset to terminal error states in one short Shepherd
     * transaction. Use ONLY from detection paths; generic markTaskError
     * is the right helper for extraction/match paths that must leave
     * the MediaAsset alone (bulk-import polling reads MA detectionStatus
     * to decide when detection is done, and an extraction/match failure
     * must not retroactively flip a successfully-detected MA to error).
     *
     * <p>The MA transition uses the {@link IBEISIA#STATUS_ERROR}
     * terminal status. Caller must ensure any prior open transaction
     * on the same MA has been rolled back before invoking, to avoid
     * lock conflicts on the row.</p>
     *
     * <p>If the cleanup transaction itself fails (MA since deleted,
     * DB unavailable), the original failure outcome is preserved and
     * a WARN is logged — do NOT shadow the upstream error.</p>
     */
    private void markDetectionFailure(String maId, String taskId, String code, String message) {
        // Whole-helper try so that nothing in here can shadow the upstream
        // failure outcome — Shepherd construction, setAction, the
        // transaction body, AND the final rollbackAndClose are all guarded.
        Shepherd shep = null;
        try {
            shep = new Shepherd(context);
            shep.setAction(ACTION_PREFIX + "markDetectionFailure");
            shep.beginDBTransaction();
            Task task = Task.load(taskId, shep);
            markTaskError(task, code, message);
            if (Util.stringExists(maId)) {
                MediaAsset ma = shep.getMediaAsset(maId);
                if (ma != null) ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println(
                "[WARN] MlServiceProcessor.markDetectionFailure cleanup failed for " +
                "maId=" + maId + " taskId=" + taskId + ": " + ex);
        } finally {
            if (shep != null) {
                try {
                    shep.rollbackAndClose();
                } catch (Exception ex) {
                    System.out.println(
                        "[WARN] MlServiceProcessor.markDetectionFailure " +
                        "rollbackAndClose failed for maId=" + maId +
                        " taskId=" + taskId + ": " + ex);
                }
            }
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

    /**
     * Read {@code skipIdent} from the IA Task's parameters. Returns
     * {@code false} on any failure — null/blank task id, Task not
     * loadable, Shepherd construction failure, etc. — so the default
     * posture is always "run identification."
     *
     * <p>Package-private so tests can override via an anonymous
     * subclass without needing a live database. Production code should
     * not call this directly; gate from
     * {@link #waitAndRunMatchInternal}.</p>
     */
    boolean readSkipIdent(String taskId) {
        if (!Util.stringExists(taskId)) return false;
        Shepherd shep = null;
        try {
            shep = new Shepherd(context);
            shep.setAction(ACTION_PREFIX + "readSkipIdent");
            shep.beginDBTransaction();
            Task task = Task.load(taskId, shep);
            return IBEISIA.taskParametersSkipIdent(task);
        } catch (Exception ex) {
            System.out.println(
                "WARN: MlServiceProcessor.readSkipIdent failed for task " + taskId +
                ": " + ex + " — defaulting to skipIdent=false");
            return false;
        } finally {
            // Guarded close so a rollback/close throw cannot break this
            // helper's "false on any failure" contract (Codex code-review
            // Low #2).
            if (shep != null) {
                try {
                    shep.rollbackAndClose();
                } catch (Exception ex) {
                    System.out.println(
                        "WARN: MlServiceProcessor.readSkipIdent rollbackAndClose " +
                        "failed for task " + taskId + ": " + ex);
                }
            }
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
        // Effective taxonomy (request override or encounter fallback) used to
        // select the configs above; carried so persistDetections applies the
        // _save_as iaClass remap against the SAME taxonomy. String (not a JDO
        // Taxonomy) to avoid carrying a PM-bound object across transactions.
        final String effectiveTaxonomyString;
        final MlServiceJobOutcome outcome;

        DetectionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
            JSONObject matchConfig, String effectiveTaxonomyString) {
            this.imageUri = imageUri;
            this.apiEndpoint = apiEndpoint;
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
            this.effectiveTaxonomyString = effectiveTaxonomyString;
            this.outcome = null;
        }

        private DetectionContext(MlServiceJobOutcome outcome) {
            this.imageUri = null;
            this.apiEndpoint = null;
            this.mlConfig = null;
            this.matchConfig = null;
            this.effectiveTaxonomyString = null;
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
