package org.ecocean.ia;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.RestClient;
import org.ecocean.Util;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Phase A/B/C orchestrator that enriches a persisted
 * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
 * Replaces the previous in-{@link MatchResult}-constructor PairX calls
 * that violated the "never hold a Shepherd across HTTP" convention.
 *
 * <p>Per-prospect flow:</p>
 * <ol>
 *   <li><b>Phase A (Shepherd open, short tx):</b>
 *       {@link #loadDtos(String)} loads the MatchResult, walks its
 *       prospects, and builds a list of {@link PairxDto} carrying every
 *       scalar Phase B and Phase C need. Shepherd closes before any HTTP.</li>
 *   <li><b>Phase B (no Shepherd):</b>
 *       {@link #postPairxAndExtractBase64(PairxDto)} runs the
 *       {@code /explain/} HTTP POST. No JDO state held.</li>
 *   <li><b>Phase C (fresh Shepherd, per prospect):</b>
 *       {@link #persistInspectionAsset(PairxDto, String)} opens a fresh
 *       short-lived Shepherd, creates a MediaAsset from the base64
 *       payload, attaches it to the prospect, commits.</li>
 * </ol>
 *
 * <p>Per-prospect failure is non-blocking: an HTTP timeout or persist
 * error on one prospect logs and continues; other prospects in the
 * same MatchResult still get processed.</p>
 *
 * <p>(Empty-match-prospects design Track 2 C13.)</p>
 */
public final class MatchInspectionPairxEnricher {

    private final String context;

    public MatchInspectionPairxEnricher(String context) {
        this.context = context;
    }

    /**
     * For each prospect of the named MatchResult that lacks an
     * inspection MediaAsset, run PairX out-of-transaction and attach
     * the resulting image.
     *
     * <p>Returns the number of prospects that received a new inspection
     * MediaAsset.</p>
     */
    public int enrichMatchResult(String matchResultId) {
        if (matchResultId == null) return 0;
        List<PairxDto> dtos;
        try {
            dtos = loadDtos(matchResultId);
        } catch (Exception ex) {
            System.out.println(
                "[WARN] MatchInspectionPairxEnricher.loadDtos failed for mr=" +
                matchResultId + ": " + ex);
            return 0;
        }
        int enriched = 0;
        for (PairxDto dto : dtos) {
            String b64;
            try {
                b64 = postPairxAndExtractBase64(dto);
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MatchInspectionPairxEnricher Phase B HTTP failed for ann=" +
                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
                continue;
            }
            if (b64 == null) continue;
            try {
                if (persistInspectionAsset(dto, b64)) enriched++;
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MatchInspectionPairxEnricher Phase C persist failed for ann=" +
                    dto.prospectAnnotationId + " mr=" + matchResultId + ": " + ex);
            }
        }
        System.out.println("[INFO] MatchInspectionPairxEnricher enriched " + enriched +
            "/" + dtos.size() + " prospects on mr=" + matchResultId);
        return enriched;
    }

    /**
     * Phase A: load all PairxDtos for the given MatchResult under one
     * short Shepherd transaction. Returns detached, scalar-only DTOs.
     */
    List<PairxDto> loadDtos(String matchResultId) {
        List<PairxDto> out = new ArrayList<PairxDto>();
        Shepherd shep = new Shepherd(context);
        shep.setAction("PairxEnricher.loadDtos." + matchResultId);
        try {
            shep.beginDBTransaction();
            MatchResult mr = shep.getMatchResult(matchResultId);
            if (mr == null) {
                shep.commitDBTransaction();
                return out;
            }
            Annotation queryAnn = mr.getQueryAnnotation();
            if (queryAnn == null) {
                shep.commitDBTransaction();
                return out;
            }
            String taxonomy = null;
            Encounter qEnc = queryAnn.findEncounter(shep);
            if (qEnc != null) taxonomy = qEnc.getTaxonomyString();
            String queryImageUri = imageUriOf(queryAnn);
            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
            double queryTheta = queryAnn.getTheta();
            if (mr.getProspects() != null) {
                for (MatchResultProspect prospect : mr.getProspects()) {
                    // Skip prospects that already have an inspection image
                    // (idempotent retry — Phase C may run twice on the
                    // same MatchResult under operator-driven re-fire).
                    if (prospect.getAsset() != null) continue;
                    Annotation pAnn = prospect.getAnnotation();
                    if (pAnn == null) continue;
                    String prospectImageUri = imageUriOf(pAnn);
                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
                    double prospectTheta = pAnn.getTheta();
                    out.add(new PairxDto(
                        matchResultId, pAnn.getId(), prospect.getType(),
                        taxonomy, queryImageUri, prospectImageUri,
                        queryBbox, prospectBbox, queryTheta, prospectTheta));
                }
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            throw new RuntimeException(ex);
        } finally {
            shep.closeDBTransaction();
        }
        return out;
    }

    private static String imageUriOf(Annotation ann) {
        if (ann == null) return null;
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) return null;
        URL url = ma.webURL();
        return (url == null) ? null : url.toString();
    }

    /**
     * Phase B: POST to {@code /explain/} and extract the base64 image.
     * No Shepherd held. Returns null on any non-fatal condition (degenerate
     * bbox, missing URL, empty response). Throws on HTTP failure so the
     * caller can log per-prospect.
     */
    String postPairxAndExtractBase64(PairxDto dto) throws IOException {
        if (dto == null) return null;
        if (!Util.stringExists(dto.queryImageUri) ||
            !Util.stringExists(dto.prospectImageUri)) return null;
        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
            System.out.println(
                "[INFO] PairxEnricher skipping degenerate bbox for ann=" +
                dto.prospectAnnotationId);
            return null;
        }
        if (!Util.stringExists(dto.taxonomyString)) return null;
        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
        if (pairxUrl == null) return null;
        JSONObject payload = buildPayload(dto);
        JSONObject res = RestClient.postJSON(pairxUrl, payload, null);
        if (res == null) return null;
        JSONArray imgs = res.optJSONArray("images");
        if ((imgs == null) || (imgs.length() < 1)) return null;
        String b64 = imgs.optString(0, null);
        if (!Util.stringExists(b64)) return null;
        return b64;
    }

    /**
     * Build the {@code /explain/} POST body. Pure function; package-
     * visible for unit-testing. Mirrors the body the legacy
     * {@code MatchResult.createInspectionPairxAsset} sent, with the
     * C12 clampBbox and addBboxPayload fixes baked in.
     */
    static JSONObject buildPayload(PairxDto dto) {
        JSONObject payload = new JSONObject();
        payload.put("algorithm", "pairx");
        payload.put("visualization_type", "only_colors");
        payload.put("k_colors", 5);
        payload.put("model_id", "miewid-msv4.1");
        payload.put("crop_bbox", false);
        payload.put("layer_key", "backbone.blocks.3");
        payload.put("image1_uris", new JSONArray().put(dto.queryImageUri));
        payload.put("image2_uris", new JSONArray().put(dto.prospectImageUri));
        payload.put("theta1", new JSONArray().put(dto.queryTheta));
        payload.put("theta2", new JSONArray().put(dto.prospectTheta));
        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
        return payload;
    }

    /**
     * Phase C: persist a new MediaAsset under a fresh Shepherd and
     * attach it to the prospect identified by (annotationId, scoreType)
     * within the MatchResult. Returns true on successful attach, false
     * if the prospect couldn't be located.
     */
    boolean persistInspectionAsset(PairxDto dto, String b64) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("PairxEnricher.persist." + dto.matchResultId + "." +
            dto.prospectAnnotationId);
        try {
            shep.beginDBTransaction();
            MatchResult mr = shep.getMatchResult(dto.matchResultId);
            if (mr == null) {
                shep.rollbackDBTransaction();
                return false;
            }
            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
                dto.scoreType);
            if (target == null) {
                shep.rollbackDBTransaction();
                return false;
            }
            // Idempotency guard: if a parallel Phase C already attached an
            // asset to this prospect, don't double-attach.
            if (target.getAsset() != null) {
                shep.rollbackDBTransaction();
                return false;
            }
            AssetStore store = AssetStore.getDefault(shep);
            JSONObject params = store.createParameters(new File(
                Util.hashDirectories(dto.matchResultId) +
                "/pairx-" + dto.matchResultId + "-" +
                dto.prospectAnnotationId + "-" + dto.scoreType + ".png"));
            MediaAsset ma = store.create(params);
            ma.copyInBase64(b64);
            ma.addLabel("matchInspectionPairx");
            shep.getPM().makePersistent(ma);
            target.setAsset(ma);
            shep.commitDBTransaction();
            return true;
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            throw new RuntimeException(ex);
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Find a prospect in the given MatchResult by (annotationId, scoreType).
     * Package-visible for tests.
     */
    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
        String scoreType) {
        if (mr == null || mr.getProspects() == null) return null;
        for (MatchResultProspect p : mr.getProspects()) {
            Annotation a = p.getAnnotation();
            if (a == null) continue;
            if (!annotationId.equals(a.getId())) continue;
            String t = p.getType();
            if (scoreType == null) {
                if (t == null) return p;
            } else if (scoreType.equals(t)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Outcome of a single-prospect enrichment attempt. Used by the
     * on-demand REST endpoint (see {@link org.ecocean.api.MatchInspection})
     * to map enrichment results to HTTP status codes. The existing
     * batch path ({@link #enrichMatchResult(String)}) still uses the
     * legacy log-and-skip pattern.
     */
    public static enum ProspectEnrichmentResult {
        /** prospect already had an inspection asset (idempotent hit). */
        EXISTS,
        /** new inspection asset persisted and attached. */
        CREATED,
        /** the prospectAnnotationId is not on the named match result. */
        NOT_ASSOCIATED,
        /** degenerate bbox, missing URLs, or empty pairx response. */
        UNSUPPORTED,
        /** pairx HTTP call failed (network / 5xx / parse error). */
        PAIRX_FAILED
    }

    /**
     * Result + optional asset JSON + optional human-readable detail.
     *
     * <p>The asset is carried as a pre-serialized {@code JSONObject}
     * (not a {@link MediaAsset}) so the servlet caller can write it
     * directly without re-touching the persistence layer. MediaAsset
     * lazy fields (e.g. webURL, attributes) can NPE when accessed
     * after the Shepherd that loaded them has closed; serializing
     * inside the Shepherd's lifetime sidesteps that.</p>
     */
    public static final class ProspectEnrichmentOutcome {
        public final ProspectEnrichmentResult result;
        public final JSONObject assetJson;   // non-null only for EXISTS / CREATED
        public final String message;         // optional diagnostic detail

        private ProspectEnrichmentOutcome(ProspectEnrichmentResult result,
            JSONObject assetJson, String message) {
            this.result = result;
            this.assetJson = assetJson;
            this.message = message;
        }

        public static ProspectEnrichmentOutcome exists(JSONObject assetJson) {
            return new ProspectEnrichmentOutcome(
                ProspectEnrichmentResult.EXISTS, assetJson, null);
        }

        public static ProspectEnrichmentOutcome created(JSONObject assetJson) {
            return new ProspectEnrichmentOutcome(
                ProspectEnrichmentResult.CREATED, assetJson, null);
        }

        public static ProspectEnrichmentOutcome notAssociated() {
            return new ProspectEnrichmentOutcome(
                ProspectEnrichmentResult.NOT_ASSOCIATED, null, null);
        }

        public static ProspectEnrichmentOutcome unsupported(String message) {
            return new ProspectEnrichmentOutcome(
                ProspectEnrichmentResult.UNSUPPORTED, null, message);
        }

        public static ProspectEnrichmentOutcome pairxFailed(String message) {
            return new ProspectEnrichmentOutcome(
                ProspectEnrichmentResult.PAIRX_FAILED, null, message);
        }
    }

    /**
     * On-demand enrichment for a single prospect. Used by the REST
     * endpoint that lazy-generates inspection images on the user's
     * first inspector-open. Distinct from {@link #enrichMatchResult}
     * which is the batch / eager path.
     *
     * <p>Concurrency: relies on the existing idempotency guard inside
     * {@link #persistInspectionAsset} — a second concurrent call that
     * loses the race re-loads the winning asset and returns EXISTS.
     * Two parallel POSTs for the same prospect both reach pairx (no
     * pre-pairx coordination), but only one MediaAsset gets persisted.</p>
     */
    public ProspectEnrichmentOutcome enrichOneProspect(String matchResultId,
        String prospectAnnotationId) {
        if ((matchResultId == null) || (prospectAnnotationId == null)) {
            return ProspectEnrichmentOutcome.notAssociated();
        }
        List<PairxDto> dtos;
        try {
            dtos = loadDtos(matchResultId);
        } catch (Exception ex) {
            return ProspectEnrichmentOutcome.unsupported(
                "loadDtos failed: " + ex.getMessage());
        }
        PairxDto target = null;
        for (PairxDto dto : dtos) {
            if (prospectAnnotationId.equals(dto.prospectAnnotationId)) {
                target = dto;
                break;
            }
        }
        if (target == null) {
            // Two distinct cases collapse here: (a) the prospect doesn't
            // belong to this MatchResult, or (b) the prospect already has
            // an asset and loadDtos skipped it. Disambiguate with a short
            // read so the caller can return 200/EXISTS rather than 404.
            // Read failures propagate as RuntimeException — the servlet
            // catch maps them to 500 (Codex C7a Minor #2: a DB read
            // failure shouldn't masquerade as 404 not-found).
            JSONObject existing = readExistingAssetJson(matchResultId, prospectAnnotationId);
            if (existing != null) return ProspectEnrichmentOutcome.exists(existing);
            return ProspectEnrichmentOutcome.notAssociated();
        }
        String b64;
        try {
            b64 = postPairxAndExtractBase64(target);
        } catch (Exception ex) {
            return ProspectEnrichmentOutcome.pairxFailed(ex.getMessage());
        }
        if (b64 == null) {
            return ProspectEnrichmentOutcome.unsupported(
                "pairx returned no image (degenerate bbox / missing URL / empty response)");
        }
        boolean persisted;
        try {
            persisted = persistInspectionAsset(target, b64);
        } catch (Exception ex) {
            return ProspectEnrichmentOutcome.unsupported(
                "Phase C persist threw: " + ex.getMessage());
        }
        JSONObject attachedJson = readExistingAssetJson(matchResultId, prospectAnnotationId);
        if (persisted && attachedJson != null) {
            return ProspectEnrichmentOutcome.created(attachedJson);
        }
        if (attachedJson != null) {
            // persistInspectionAsset returned false because another
            // concurrent call won the idempotency guard. Their asset
            // is now attached — return EXISTS rather than misreporting.
            return ProspectEnrichmentOutcome.exists(attachedJson);
        }
        return ProspectEnrichmentOutcome.unsupported(
            "persist returned false and no asset is attached");
    }

    /**
     * Read the inspection MediaAsset currently attached to the named
     * prospect and serialize it to JSON INSIDE the read transaction.
     * Returns null if MatchResult, prospect, or asset is missing.
     *
     * <p>Serializing in-transaction avoids touching MediaAsset lazy
     * fields (webURL, attributes) after the loading Shepherd has
     * closed — those calls can NPE on a detached object
     * (Codex C7a round-1 Major).</p>
     *
     * <p>Throws {@link RuntimeException} on DB failures so the servlet
     * layer can map them to 500 rather than silently 404
     * (Codex C7a round-1 Minor #2).</p>
     */
    private JSONObject readExistingAssetJson(String matchResultId,
        String prospectAnnotationId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("PairxEnricher.readExistingAssetJson." + matchResultId + "." +
            prospectAnnotationId);
        try {
            shep.beginDBTransaction();
            org.ecocean.ia.MatchResult mr = shep.getMatchResult(matchResultId);
            if (mr == null) {
                shep.commitDBTransaction();
                return null;
            }
            MatchResultProspect p = null;
            if (mr.getProspects() != null) {
                for (MatchResultProspect candidate : mr.getProspects()) {
                    Annotation a = (candidate == null) ? null : candidate.getAnnotation();
                    if ((a != null) && prospectAnnotationId.equals(a.getId())) {
                        p = candidate;
                        break;
                    }
                }
            }
            MediaAsset asset = (p == null) ? null : p.getAsset();
            if (asset == null) {
                shep.commitDBTransaction();
                return null;
            }
            JSONObject aj = asset.toSimpleJSONObject();
            URL webUrl = asset.webURL();
            aj.put("url", webUrl == null ? null : webUrl.toString());
            shep.commitDBTransaction();
            return aj;
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            throw new RuntimeException(
                "readExistingAssetJson failed for mr=" + matchResultId +
                " prospectAnn=" + prospectAnnotationId, ex);
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Plain-data carrier for one prospect's PairX inputs. Captured under
     * Shepherd in Phase A, immutable through Phase B + C.
     */
    static final class PairxDto {
        final String matchResultId;
        final String prospectAnnotationId;
        final String scoreType;             // "annot" or "indiv"
        final String taxonomyString;
        final String queryImageUri;
        final String prospectImageUri;
        final int[] queryBbox;              // pre-clamped
        final int[] prospectBbox;           // pre-clamped
        final double queryTheta;
        final double prospectTheta;

        PairxDto(String matchResultId, String prospectAnnotationId, String scoreType,
            String taxonomyString, String queryImageUri, String prospectImageUri,
            int[] queryBbox, int[] prospectBbox,
            double queryTheta, double prospectTheta) {
            this.matchResultId = matchResultId;
            this.prospectAnnotationId = prospectAnnotationId;
            this.scoreType = scoreType;
            this.taxonomyString = taxonomyString;
            this.queryImageUri = queryImageUri;
            this.prospectImageUri = prospectImageUri;
            this.queryBbox = queryBbox;
            this.prospectBbox = prospectBbox;
            this.queryTheta = queryTheta;
            this.prospectTheta = prospectTheta;
        }
    }
}
