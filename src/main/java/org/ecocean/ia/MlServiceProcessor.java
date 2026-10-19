package org.ecocean.ia;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * {@code IAGateway.processQueueMessage} routes {@code mlServiceV2:true}
 * payloads here.</p>
 *
 * <p>A species whose active {@code _mlservice_conf} has several entries runs
 * every pipeline and embeds/matches each class in its own space; see
 * docs/plans/2026-10-16-mlservice-multi-config.md.</p>
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

        JSONArray results;
        // Multi-pipeline only: producedBy.get(i) is the _mlservice_conf entry
        // whose /pipeline/ call returned results[i]. Null for single-pipeline.
        List<JSONObject> producedBy = null;
        if (!det.multiPipeline) {
            JSONObject response;
            try {
                response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
            } catch (IAException ex) {
                return detectionCallFailed(jobData, maId, taskId, ex);
            }
            results = response.optJSONArray("results");
        } else {
            PipelineRun run;
            try {
                run = runPipelines(client, det.pipelines, det.imageUri);
            } catch (IAException ex) {
                return detectionCallFailed(jobData, maId, taskId, ex);
            }
            results = run.results;
            producedBy = run.producedBy;
        }
        if (results == null || results.length() == 0) {
            return finalizeZeroDetections(maId, taskId);
        }

        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results,
            producedBy);
        if (persisted.outcome != null) return persisted.outcome;

        if (det.multiPipeline) {
            return runMultiPipelineMatches(persisted.detected, taskId,
                det.effectiveTaxonomyString);
        }
        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
            det.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    /**
     * Call {@code /pipeline/} once per entry, in IA.json order, each on its
     * own {@code api_endpoint}, and concatenate the results. Runs every
     * pipeline before anything is persisted: the first failure propagates,
     * so the caller fails or requeues the whole job and the MediaAsset never
     * completes with one class silently missing. A requeue re-runs every
     * pipeline; detection dedup keeps that idempotent.
     */
    static PipelineRun runPipelines(MlServiceClient client, List<JSONObject> pipelines,
        String imageUri)
    throws IAException {
        PipelineRun run = new PipelineRun();
        for (JSONObject pipeline : pipelines) {
            JSONObject response = client.pipeline(pipeline.optString("api_endpoint", null),
                imageUri, pipeline);
            JSONArray pipelineResults = (response == null) ? null
                : response.optJSONArray("results");
            if (pipelineResults == null) continue;
            for (int i = 0; i < pipelineResults.length(); i++) {
                run.results.put(pipelineResults.get(i));
                run.producedBy.add(pipeline);
            }
        }
        return run;
    }

    /**
     * Every path names an embedding space by splitting extract_model_id on
     * its first dash; a model id without one (e.g. "msv3") would be named
     * differently by the legacy extraction path, so multi-pipeline species
     * reject it outright.
     */
    static final String MULTI_PIPELINE_EXTRACT_ID_RULE =
        "multi-pipeline _mlservice_conf entries need an extract_model_id of the form " +
        "<method>-<version>";

    /**
     * Why a multi-pipeline species' entries cannot run, or null when every
     * entry is an object with a predict_model_id and a splittable
     * extract_model_id.
     */
    static String invalidMultiPipelineEntry(List<JSONObject> pipelines) {
        for (int i = 0; i < pipelines.size(); i++) {
            JSONObject pipeline = pipelines.get(i);
            if ((pipeline == null) ||
                !Util.stringExists(pipeline.optString("predict_model_id", null)))
                return "_mlservice_conf[" + i + "] missing predict_model_id";
            if (extractorIdentity(pipeline) == null)
                return "_mlservice_conf[" + i + "]: " + MULTI_PIPELINE_EXTRACT_ID_RULE;
        }
        return null;
    }

    /** Requeue a retryable /pipeline/ failure, else fail the detection. */
    private MlServiceJobOutcome detectionCallFailed(JSONObject jobData, String maId,
        String taskId, IAException ex) {
        if (ex.shouldRequeue()) {
            IAGateway.requeueJob(jobData, ex.shouldIncrement());
            return MlServiceJobOutcome.requeue();
        }
        markDetectionFailure(maId, taskId, ex.getCode(), ex.getMessage());
        return mapNonRetryableError(ex);
    }

    /**
     * Multi-pipeline species: match each detected annotation in the embedding
     * space it was given, one {@link #waitAndRunMatch} per resolved match
     * config. Every group is attempted; the outcome is the first error group
     * outcome, else ok with every annotation id.
     */
    private MlServiceJobOutcome runMultiPipelineMatches(List<DetectedAnnotation> detected,
        String taskId, String taxonomyString) {
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        Taxonomy taxy = Util.stringExists(taxonomyString) ? new Taxonomy(taxonomyString) : null;
        List<String> allIds = new ArrayList<String>();
        MlServiceJobOutcome firstError = null;
        for (MatchGroup group : groupByMatchConfig(iac, taxy, detected)) {
            allIds.addAll(group.annotationIds);
            MlServiceJobOutcome outcome;
            try {
                outcome = waitAndRunMatch(group.annotationIds, taskId, group.matchConfig, true);
            } catch (RuntimeException ex) {
                // e.g. a failing error-marker inside runMatchProspects' catch;
                // never let one group stop the others from being matched.
                System.out.println("[ERROR] MlServiceProcessor: match group " +
                    group.annotationIds + " for task " + taskId + " threw " + ex);
                outcome = MlServiceJobOutcome.persistError("MATCH", String.valueOf(ex));
            }
            if ((firstError == null) && (outcome != null) && outcome.isError()) {
                firstError = outcome;
            }
        }
        if (firstError != null) return firstError;
        return MlServiceJobOutcome.ok(allIds);
    }

    /**
     * Group detected annotations by the match config each resolves to
     * ({@link #resolveMultiPipelineMatchConfig}), in first-seen order. An
     * annotation id appears in at most one group.
     */
    static List<MatchGroup> groupByMatchConfig(IAJsonProperties iac, Taxonomy taxy,
        List<DetectedAnnotation> detected) {
        Map<String, MatchGroup> groups = new LinkedHashMap<String, MatchGroup>();
        Set<String> seen = new HashSet<String>();
        if (detected == null) return new ArrayList<MatchGroup>();
        for (DetectedAnnotation d : detected) {
            if ((d == null) || !Util.stringExists(d.annotationId) || !seen.add(d.annotationId))
                continue;
            JSONObject config = resolveMultiPipelineMatchConfig(iac, taxy, d.iaClass,
                d.embeddingIdentity, d.producedBy);
            String key = canonicalJson(config);
            MatchGroup group = groups.get(key);
            if (group == null) {
                group = new MatchGroup(config);
                groups.put(key, group);
            }
            group.annotationIds.add(d.annotationId);
        }
        return new ArrayList<MatchGroup>(groups.values());
    }

    /**
     * Match config for one annotation of a multi-pipeline species: the
     * iaClass's default vector {@code _id_conf} entry, else {@code _default}'s.
     * If that entry names a different (method, version) than the embedding
     * the annotation actually has, it would match against the wrong (or an
     * empty) corpus, so fall back to a config derived from the embedding
     * itself and log a WARN. {@code api_endpoint} defaults to the producing
     * pipeline's.
     */
    static JSONObject resolveMultiPipelineMatchConfig(IAJsonProperties iac, Taxonomy taxy,
        String iaClass, String[] embeddingIdentity, JSONObject producedBy) {
        JSONObject config = null;
        if ((iac != null) && (taxy != null)) {
            config = iac.getDefaultVectorIdentConfig(taxy, iaClass);
            if (config == null) config = iac.getDefaultVectorIdentConfig(taxy, "_default");
        }
        if ((embeddingIdentity != null) && !identityMatches(config, embeddingIdentity)) {
            System.out.println("[WARN] MlServiceProcessor: match config " + config +
                " for iaClass " + iaClass + " (" + taxy + ") does not match embedding " +
                embeddingIdentity[0] + "-" + embeddingIdentity[1] +
                "; matching in the embedding's own space instead");
            config = new JSONObject();
            config.put("pipeline_root", "vector");
            config.put("method", embeddingIdentity[0]);
            config.put("version", embeddingIdentity[1]);
            String desc = (producedBy == null) ? null : producedBy.optString("description", null);
            config.put("description",
                Util.stringExists(desc) ? desc : "ml-service vector matching");
        }
        if (config == null) config = new JSONObject();
        if (!Util.stringExists(config.optString("api_endpoint", null)) && (producedBy != null) &&
            Util.stringExists(producedBy.optString("api_endpoint", null))) {
            config.put("api_endpoint", producedBy.optString("api_endpoint"));
        }
        return config;
    }

    /**
     * Key-sorted JSON text, so two configs with the same content give the
     * same string whatever order their keys were inserted in.
     */
    static String canonicalJson(Object value) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            List<String> keys = new ArrayList<String>(obj.keySet());
            java.util.Collections.sort(keys);
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(JSONObject.quote(keys.get(i))).append(':')
                    .append(canonicalJson(obj.opt(keys.get(i))));
            }
            return sb.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) sb.append(',');
                sb.append(canonicalJson(arr.opt(i)));
            }
            return sb.append(']').toString();
        }
        if (value instanceof String) return JSONObject.quote((String) value);
        return String.valueOf(value);
    }

    private static boolean identityMatches(JSONObject config, String[] identity) {
        if ((config == null) || (identity == null)) return false;
        return identity[0].equals(config.optString("method", null)) &&
               identity[1].equals(config.optString("version", null));
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

        JSONObject matchConfig;
        if (ext.multiPipeline) {
            // Resolve against the embedding just stored (the response's
            // identity), not the configured one. persistExtraction already
            // parsed it successfully, so this cannot throw here.
            matchConfig = resolveMultiPipelineMatchConfig(IAJsonProperties.iaConfig(),
                new Taxonomy(ext.taxonomyString), ext.iaClass,
                parseEmbeddingMethodVersion(response), ext.mlConfig);
        } else {
            matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
        }
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
            // Multi-pipeline: every entry must be runnable, or none run.
            String invalid = configs.multiPipeline
                ? invalidMultiPipelineEntry(configs.pipelines) : null;
            if (invalid != null) {
                markTaskError(task, "INVALID", invalid + " for " + effectiveTaxonomy);
                ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
                    invalid));
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
                configs.matchConfig, effectiveTaxonomy, configs.pipelines,
                configs.multiPipeline);
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
            JSONObject mlConfig = configs.mlConfig;
            if (configs.multiPipeline) {
                // One extractor per annotation, chosen by its iaClass; "already
                // extracted" means an embedding from THAT extractor, so an old
                // embedding from another model never suppresses extraction.
                IAJsonProperties iac = IAJsonProperties.iaConfig();
                Taxonomy taxy = shep.getOrCreateTaxonomy(effectiveTaxonomy, false);
                mlConfig = iac.selectExtractionConfig(taxy, ann.getIAClass());
                String[] extractMv = extractorIdentity(mlConfig);
                if (extractMv == null) {
                    markTaskError(task, "INVALID", MULTI_PIPELINE_EXTRACT_ID_RULE + "; got " +
                        mlConfig + " for " + effectiveTaxonomy);
                    shep.commitDBTransaction();
                    return ExtractionContext.done(MlServiceJobOutcome.validationError(
                        "INVALID", MULTI_PIPELINE_EXTRACT_ID_RULE));
                }
                if (ann.getEmbeddingByMethod(extractMv[0], extractMv[1]) != null) {
                    markTaskCompleted(task);
                    shep.commitDBTransaction();
                    return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
                }
            } else if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
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
                mlConfig.optString("api_endpoint", null), mlConfig,
                configs.matchConfig, toDoubleArray(bbox), ann.getTheta(),
                configs.multiPipeline, effectiveTaxonomy, ann.getIAClass());
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
        DetectionContext det, JSONArray results, List<JSONObject> producedBy) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "persistDetections");
        List<String> annotationIds = new ArrayList<String>();
        // Multi-pipeline only: what each annotation id was detected as, so the
        // match step can pick a match config per class/embedding space.
        List<DetectedAnnotation> detected = new ArrayList<DetectedAnnotation>();

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
                // Multi-pipeline: two pipelines can return the same rounded
                // geometry for different classes (body vs part); only a box of
                // the same resolved class is a duplicate.
                Annotation existing = det.multiPipeline
                    ? findExistingAnnotation(ma, bbox, theta, iaClass)
                    : findExistingAnnotation(ma, bbox, theta);
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
                    if (det.multiPipeline)
                        detected.add(new DetectedAnnotation(existing.getId(),
                            existing.getIAClass(), mv, producedBy.get(i)));
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
                if (det.multiPipeline)
                    detected.add(new DetectedAnnotation(ann.getId(), iaClass, mv,
                        producedBy.get(i)));
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
            return PersistResult.ok(annotationIds, detected);
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
            String[] mv;
            boolean exists;
            if (ext.multiPipeline) {
                // Dedup on the embedding the service actually returned.
                mv = parseEmbeddingMethodVersion(response);
                exists = ann.getEmbeddingByMethod(mv[0], mv[1]) != null;
            } else {
                mv = null;
                exists = hasEmbeddingForMatchConfig(ann,
                    ensureMatchConfig(ext.matchConfig, response, ext.mlConfig));
            }
            if (exists) {
                // loadExtractionContext set processing-mlservice against the
                // selected extractor; if the response named another model
                // that is already stored, close the annotation out here.
                if (ext.multiPipeline)
                    ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
                markTaskCompleted(task);
                shep.commitDBTransaction();
                annotationIds.add(ann.getId());
                return PersistResult.ok(annotationIds);
            }

            ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
            if (mv == null) mv = parseEmbeddingMethodVersion(response);
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
        return waitAndRunMatch(annotationIds, taskId, matchConfig, false);
    }

    /**
     * @param matchGroup true when this is one of several match groups that
     *        share {@code taskId} (multi-pipeline detection). A group then
     *        never writes a stale status onto the shared parent task, so it
     *        cannot overwrite a sibling group's error or mask its success.
     */
    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
        JSONObject matchConfig, boolean matchGroup) {
        // Initial invocation: attempt=1, firstDeferredAt=null (the
        // gate stamps `now` so age-out is measured from this first
        // call, not from later re-fires).
        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null,
            matchGroup);
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
        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt,
        boolean matchGroup) {
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
            return runMatchProspects(annotationIds, taskId, matchConfig, matchGroup);
          case DEFER:
            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate, matchGroup);
            return MlServiceJobOutcome.ok(annotationIds);
          case GIVE_UP:
          default:
            System.out.println(
                "WARN: MatchVisibilityGate aged out for task " + taskId +
                " after attempt=" + gate.attempt + " elapsed=" +
                gate.elapsedMillis + "ms reason=" + gate.reason +
                "; running match against current visible corpus");
            return runMatchProspects(annotationIds, taskId, matchConfig, matchGroup);
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
            attempt, firstDeferredAt, jobData.optBoolean("matchGroup", false));
    }

    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        return runMatchProspects(annotationIds, taskId, matchConfig, false);
    }

    MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig, boolean matchGroup) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            if (!matchGroup) markTaskCompleted(taskId);
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
                // A match group's staleness stays local: the shared parent
                // already holds the detection's terminal status and any
                // sibling group's error.
                if (matchGroup) return MlServiceJobOutcome.stale("annotations missing");
                Task task = Task.load(taskId, shep);
                markTaskDroppedStale(shep, task, "annotations missing");
                shep.commitDBTransaction();
                return MlServiceJobOutcome.stale("annotations missing");
            }

            Task parent = Task.load(taskId, shep);
            Task matchTask = (parent == null) ? new Task() : new Task(parent);
            // new Task(parent) copies the parent's parameters. The v2 detection
            // task is tagged ibeis.detection (IA.enqueueOneAssetForMlService) so
            // the /metrics detection gauges count it; strip that flag here so it
            // does NOT propagate onto this match task or the per-annotation
            // subtasks Embedding.findMatchProspects creates from it below
            // (new Task(matchTask)). Otherwise the detection gauges would
            // miscount match/subtasks as detections, and the existential child
            // clause in MetricsBot would stop counting the parent detection
            // task. Mirror of IBEISIA's params.remove("ibeis.detection").
            JSONObject inheritedParams = matchTask.getParameters();
            if ((inheritedParams != null) && inheritedParams.has("ibeis.detection")) {
                inheritedParams.remove("ibeis.detection");
                matchTask.setParameters(inheritedParams);
            }
            matchTask.setObjectAnnotations(anns);
            matchTask.addParameter("mlServiceV2Match", true);
            // Stamp the chosen _id_conf entry's description into an
            // ibeis.identification block so Task.getIdentificationMethodInfo
            // surfaces a method/description for the React match-results page
            // header. Pure-v2 imports (no legacy WBIA in the chain) leave the
            // task tree without any ibeis.identification anywhere, which
            // causes matchResultsJson to omit `method` from the API tree —
            // the React header reads `method?.name ?? method?.description`
            // and renders blank. Stamp unconditionally — even the error
            // branch below (matchConfig == null / findMatchProspects rejects)
            // persists this task and the user can hit its match-results page.
            String desc = (matchConfig == null) ? null
                : matchConfig.optString("description", null);
            JSONObject identBlock = new JSONObject();
            identBlock.put("description",
                (desc == null || desc.isEmpty()) ? "ml-service vector matching" : desc);
            // Record the embedding space the match ran in. The legacy ident
            // path already carries method/version here (the whole _id_conf
            // entry); PairX reads them to explain the match with the same
            // model. Additive: getIdentificationMethodInfo reads only
            // api_endpoint/model_id/description/query_config_dict, and
            // api_endpoint is deliberately NOT copied (it would switch the
            // header to the legacy "Vector embedding match" label).
            if (matchConfig != null) {
                String method = matchConfig.optString("method", null);
                String version = matchConfig.optString("version", null);
                if (Util.stringExists(method) && Util.stringExists(version)) {
                    identBlock.put("method", method);
                    identBlock.put("version", version);
                }
            }
            matchTask.addParameter("ibeis.identification", identBlock);
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
            shep.commitDBTransaction();
        } catch (Exception ex) {
            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
        } finally {
            shep.rollbackAndClose();
        }
        // PairX inspection-image generation is intentionally OFF the
        // synchronous match path. The UI lazy-fetches each prospect's
        // inspection asset via POST /api/v3/match-inspection/{mrId}/{annId}
        // when the inspector opens — see api/MatchInspection and the
        // lazy-fetch in MatchProspectTable.jsx.
        return MlServiceJobOutcome.ok(annotationIds);
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

    /**
     * Multi-pipeline variant of {@link #findExistingAnnotation(MediaAsset,
     * double[], double)}: same geometry test, but only an annotation whose
     * iaClass equals {@code iaClass} (both null counts as equal) is a
     * duplicate.
     */
    static Annotation findExistingAnnotation(MediaAsset ma, double[] bbox, double theta,
        String iaClass) {
        if (ma == null || bbox == null || bbox.length != 4) return null;
        List<Feature> fts = ma.getFeatures();
        if (fts == null) return null;
        for (Feature ft : fts) {
            if (ft == null || !ft.isType(BOUNDING_BOX_FEATURE)) continue;
            Annotation ann = ft.getAnnotation();
            if (ann == null || !Objects.equals(ann.getIAClass(), iaClass)) continue;
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
        // Every entry, in IA.json order; a non-object entry stays as null so
        // loadDetectionContext rejects it rather than silently skipping it.
        List<JSONObject> pipelines = new ArrayList<JSONObject>();
        for (int i = 0; i < configs.length(); i++) {
            pipelines.add(configs.optJSONObject(i));
        }
        return new ConfigPair(mlConfig, matchConfig, pipelines, configs.length() > 1);
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
        String[] split = splitModelId(embeddingModelId);
        if (split != null) {
            method = split[0];
            version = split[1];
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

    /**
     * Split an ml-service model id on its FIRST dash into
     * {@code {method, version}} ({@code "miewid-pet-v1"} → {@code {"miewid",
     * "pet-v1"}}), or null when there is no dash with text on both sides.
     * The one rule every multi-pipeline path uses to name an embedding space.
     */
    public static String[] splitModelId(String modelId) {
        if (!Util.stringExists(modelId)) return null;
        int dash = modelId.indexOf('-');
        if (dash > 0 && dash < modelId.length() - 1) {
            return new String[] { modelId.substring(0, dash), modelId.substring(dash + 1) };
        }
        return null;
    }

    /**
     * {@code {method, version}} of the embedding an ml-service config's
     * {@code extract_model_id} produces, or null when it has none or it
     * cannot be split.
     */
    public static String[] extractorIdentity(JSONObject mlConfig) {
        if (mlConfig == null) return null;
        return splitModelId(mlConfig.optString("extract_model_id", null));
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
        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate, boolean matchGroup) {
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
        // Only multi-pipeline groups carry this, so single-pipeline payloads
        // are unchanged; runDeferredMatch restores it on re-fire.
        if (matchGroup) payload.put("matchGroup", true);
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
        // all species-level _mlservice_conf entries (entry 0 == mlConfig)
        final List<JSONObject> pipelines;
        final boolean multiPipeline;

        ConfigPair(JSONObject mlConfig, JSONObject matchConfig, List<JSONObject> pipelines,
            boolean multiPipeline) {
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
            this.pipelines = pipelines;
            this.multiPipeline = multiPipeline;
        }
    }

    /** One annotation persisted by a multi-pipeline detection. */
    static final class DetectedAnnotation {
        final String annotationId;
        final String iaClass;
        // {method, version} of the embedding stored with it
        final String[] embeddingIdentity;
        // the _mlservice_conf entry whose /pipeline/ call returned it
        final JSONObject producedBy;

        DetectedAnnotation(String annotationId, String iaClass, String[] embeddingIdentity,
            JSONObject producedBy) {
            this.annotationId = annotationId;
            this.iaClass = iaClass;
            this.embeddingIdentity = embeddingIdentity;
            this.producedBy = producedBy;
        }
    }

    /** Concatenated results of several /pipeline/ calls. */
    static final class PipelineRun {
        final JSONArray results = new JSONArray();
        // producedBy.get(i) is the pipeline entry that returned results[i]
        final List<JSONObject> producedBy = new ArrayList<JSONObject>();
    }

    /** Annotation ids that share one match config. */
    static final class MatchGroup {
        final JSONObject matchConfig;
        final List<String> annotationIds = new ArrayList<String>();

        MatchGroup(JSONObject matchConfig) {
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
        // all species-level pipelines; only iterated when multiPipeline
        final List<JSONObject> pipelines;
        final boolean multiPipeline;
        final MlServiceJobOutcome outcome;

        DetectionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
            JSONObject matchConfig, String effectiveTaxonomyString, List<JSONObject> pipelines,
            boolean multiPipeline) {
            this.imageUri = imageUri;
            this.apiEndpoint = apiEndpoint;
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
            this.effectiveTaxonomyString = effectiveTaxonomyString;
            this.pipelines = pipelines;
            this.multiPipeline = multiPipeline;
            this.outcome = null;
        }

        private DetectionContext(MlServiceJobOutcome outcome) {
            this.imageUri = null;
            this.apiEndpoint = null;
            this.mlConfig = null;
            this.matchConfig = null;
            this.effectiveTaxonomyString = null;
            this.pipelines = null;
            this.multiPipeline = false;
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
        final boolean multiPipeline;
        final String taxonomyString;
        final String iaClass;
        final MlServiceJobOutcome outcome;

        ExtractionContext(String imageUri, String apiEndpoint, JSONObject mlConfig,
            JSONObject matchConfig, double[] bbox, double theta, boolean multiPipeline,
            String taxonomyString, String iaClass) {
            this.imageUri = imageUri;
            this.apiEndpoint = apiEndpoint;
            this.mlConfig = mlConfig;
            this.matchConfig = matchConfig;
            this.bbox = bbox;
            this.theta = theta;
            this.multiPipeline = multiPipeline;
            this.taxonomyString = taxonomyString;
            this.iaClass = iaClass;
            this.outcome = null;
        }

        private ExtractionContext(MlServiceJobOutcome outcome) {
            this.imageUri = null;
            this.apiEndpoint = null;
            this.mlConfig = null;
            this.matchConfig = null;
            this.bbox = null;
            this.theta = 0.0d;
            this.multiPipeline = false;
            this.taxonomyString = null;
            this.iaClass = null;
            this.outcome = outcome;
        }

        static ExtractionContext done(MlServiceJobOutcome outcome) {
            return new ExtractionContext(outcome);
        }
    }

    private static final class PersistResult {
        final List<String> annotationIds;
        // multi-pipeline detection only; empty otherwise
        final List<DetectedAnnotation> detected;
        final MlServiceJobOutcome outcome;

        private PersistResult(List<String> annotationIds, List<DetectedAnnotation> detected,
            MlServiceJobOutcome outcome) {
            this.annotationIds = annotationIds;
            this.detected = detected;
            this.outcome = outcome;
        }

        static PersistResult ok(List<String> annotationIds) {
            return new PersistResult(annotationIds, new ArrayList<DetectedAnnotation>(), null);
        }

        static PersistResult ok(List<String> annotationIds, List<DetectedAnnotation> detected) {
            return new PersistResult(annotationIds, detected, null);
        }

        static PersistResult done(MlServiceJobOutcome outcome) {
            return new PersistResult(null, null, outcome);
        }
    }
}
