package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of
 * {@link MatchInspectionPairxEnricher#buildPayload} and
 * {@link MatchInspectionPairxEnricher#findProspect}. Phase A/C paths
 * that require Shepherd are exercised by integration on the live
 * deployment — same precedent as the C6/C9 paths. (Empty-match-
 * prospects design Track 2 C13.)
 */
class MatchInspectionPairxEnricherTest {

    private static MatchInspectionPairxEnricher.PairxDto sampleDto() {
        return new MatchInspectionPairxEnricher.PairxDto(
            "mr-1", "ann-2", "annot",
            "Salamandra salamandra",
            "https://example.com/img1.jpg",
            "https://example.com/img2.jpg",
            new int[] { 10, 20, 100, 200 },
            new int[] { 30, 40, 150, 300 },
            0.0d, 0.5d);
    }

    // --- buildPayload ---------------------------------------------------

    @Test void buildPayload_usesSuppliedModelId() {
        // Registries differ per installation: kaiju loads miewid-msv4_v3,
        // not the historic miewid-msv4.1. Sending an id the target has not
        // loaded produced an HTTP 500 that Wildbook retried indefinitely.
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto(), "miewid-msv4_v3", "backbone.blocks.3");
        assertEquals("miewid-msv4_v3", p.getString("model_id"));
    }

    @Test void buildPayload_defaultOverloadUsesDefaultModelId() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        assertEquals(MatchResult.DEFAULT_PAIRX_MODEL_ID, p.getString("model_id"));
    }

    @Test void buildPayload_modelIdDoesNotDisturbOtherKeys() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto(), "anything", "backbone.blocks.9");
        assertEquals("pairx", p.getString("algorithm"));
        assertEquals("backbone.blocks.9", p.getString("layer_key"));
        assertEquals(5, p.getInt("k_colors"));
    }

    @Test void buildPayload_usesSuppliedLayerKey() {
        // layer_key is model-architecture specific: a configured model is
        // not guaranteed to expose backbone.blocks.3.
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(
            sampleDto(), "m", "backbone.blocks.1");
        assertEquals("backbone.blocks.1", p.getString("layer_key"));
    }

    @Test void buildPayload_nullOrBlankArgsNormaliseToDefaults() {
        // The payload must never carry a null model_id.
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto(), null, null);
        assertEquals(MatchResult.DEFAULT_PAIRX_MODEL_ID, p.getString("model_id"));
        assertEquals(MatchResult.DEFAULT_PAIRX_LAYER_KEY, p.getString("layer_key"));
        JSONObject q = MatchInspectionPairxEnricher.buildPayload(sampleDto(), "  ", "");
        assertEquals(MatchResult.DEFAULT_PAIRX_MODEL_ID, q.getString("model_id"));
        assertEquals(MatchResult.DEFAULT_PAIRX_LAYER_KEY, q.getString("layer_key"));
    }

    @Test void pairxModelIdFromConfig_precedence() {
        JSONObject both = new JSONObject()
            .put("pairx_model_id", "explicit").put("extract_model_id", "extract");
        assertEquals("explicit", MatchResult._pairxModelIdFromConfig(both));
        JSONObject extractOnly = new JSONObject().put("extract_model_id", "miewid-msv4_v3");
        assertEquals("miewid-msv4_v3", MatchResult._pairxModelIdFromConfig(extractOnly));
        assertEquals(MatchResult.DEFAULT_PAIRX_MODEL_ID,
            MatchResult._pairxModelIdFromConfig(new JSONObject()));
        assertEquals(MatchResult.DEFAULT_PAIRX_MODEL_ID,
            MatchResult._pairxModelIdFromConfig(null));
    }

    @Test void pairxModelIdFromConfig_blankValuesFallThrough() {
        // optString returns the blank, not the default: a blank
        // pairx_model_id must fall through to extract_model_id, and a
        // blank extract_model_id to the default -- never reach the wire.
        JSONObject blankExplicit = new JSONObject()
            .put("pairx_model_id", "   ").put("extract_model_id", "miewid-msv4_v3");
        assertEquals("miewid-msv4_v3", MatchResult._pairxModelIdFromConfig(blankExplicit));
        JSONObject bothBlank = new JSONObject()
            .put("pairx_model_id", "").put("extract_model_id", "  ");
        assertEquals(MatchResult.DEFAULT_PAIRX_MODEL_ID,
            MatchResult._pairxModelIdFromConfig(bothBlank));
    }

    @Test void pairxLayerKeyFromConfig_blankFallsBackToDefault() {
        assertEquals(MatchResult.DEFAULT_PAIRX_LAYER_KEY,
            MatchResult._pairxLayerKeyFromConfig(new JSONObject().put("pairx_layer_key", "  ")));
    }

    @Test void pairxLayerKeyFromConfig_precedence() {
        assertEquals("backbone.blocks.1", MatchResult._pairxLayerKeyFromConfig(
            new JSONObject().put("pairx_layer_key", "backbone.blocks.1")));
        assertEquals(MatchResult.DEFAULT_PAIRX_LAYER_KEY,
            MatchResult._pairxLayerKeyFromConfig(new JSONObject()));
        assertEquals(MatchResult.DEFAULT_PAIRX_LAYER_KEY,
            MatchResult._pairxLayerKeyFromConfig(null));
    }

    @Test void pairxUrlFromConfig_nullConfigYieldsNullSoPairxIsSkipped() throws Exception {
        // A config read that failed must skip PairX entirely rather than
        // POST a default model id the deployment may not have loaded.
        assertNull(MatchResult._pairxUrlFromConfig(null));
        assertNull(MatchResult._pairxUrlFromConfig(new JSONObject()));
    }

    @Test void buildPayload_setsAllFixedKeys() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        assertEquals("pairx", p.getString("algorithm"));
        assertEquals("only_colors", p.getString("visualization_type"));
        assertEquals(5, p.getInt("k_colors"));
        assertEquals("miewid-msv4.1", p.getString("model_id"));
        assertEquals(false, p.getBoolean("crop_bbox"));
        assertEquals("backbone.blocks.3", p.getString("layer_key"));
    }

    @Test void buildPayload_setsImageUrisAsSeparateArrays() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        JSONArray image1 = p.getJSONArray("image1_uris");
        JSONArray image2 = p.getJSONArray("image2_uris");
        assertEquals("https://example.com/img1.jpg", image1.getString(0));
        assertEquals("https://example.com/img2.jpg", image2.getString(0));
    }

    @Test void buildPayload_setsThetaArrays() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        assertEquals(0.0d, p.getJSONArray("theta1").getDouble(0));
        assertEquals(0.5d, p.getJSONArray("theta2").getDouble(0));
    }

    @Test void buildPayload_bb1AndBb2AreDistinctReferences() {
        // C12 regression guard: previous implementation reused one
        // JSONArray for both keys; this test fails if the enricher
        // reintroduces that bug.
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        JSONArray bb1 = p.getJSONArray("bb1");
        JSONArray bb2 = p.getJSONArray("bb2");
        assertNotSame(bb1, bb2);
        // Confirm bb1 and bb2 contain the DTO's distinct bboxes.
        assertEquals(10, bb1.getJSONArray(0).getInt(0));
        assertEquals(30, bb2.getJSONArray(0).getInt(0));
    }

    @Test void buildPayload_doubleArrayShape() {
        JSONObject p = MatchInspectionPairxEnricher.buildPayload(sampleDto());
        // PairX expects [[x, y, w, h]] for each bbox key.
        JSONArray bb1Outer = p.getJSONArray("bb1");
        assertEquals(1, bb1Outer.length());
        JSONArray inner = bb1Outer.getJSONArray(0);
        assertEquals(4, inner.length());
    }

    // --- findProspect ---------------------------------------------------

    @Test void findProspect_returnsNullForNullMatchResult() {
        assertNull(MatchInspectionPairxEnricher.findProspect(null, "x", "annot"));
    }

    @Test void findProspect_returnsNullWhenProspectsNull() {
        MatchResult mr = new MatchResult();
        // prospects field is null by default
        assertNull(MatchInspectionPairxEnricher.findProspect(mr, "x", "annot"));
    }

    @Test void findProspect_matchesByAnnotationIdAndScoreType() {
        MatchResult mr = new MatchResult();
        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
        a1.setId("ann-1");
        org.ecocean.Annotation a2 = new org.ecocean.Annotation();
        a2.setId("ann-2");
        MatchResultProspect p1 = new MatchResultProspect(a1, 1.0d, "annot", null);
        MatchResultProspect p2 = new MatchResultProspect(a2, 0.5d, "annot", null);
        // Inject via the populateProspects loop is private; we exercise
        // findProspect by directly constructing a MatchResult and
        // adding via reflection on the prospects field.
        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
        set.add(p1);
        set.add(p2);
        injectProspects(mr, set);
        MatchResultProspect found = MatchInspectionPairxEnricher.findProspect(
            mr, "ann-2", "annot");
        assertNotNull(found);
        assertEquals(a2, found.getAnnotation());
    }

    @Test void findProspect_distinguishesByScoreType() {
        MatchResult mr = new MatchResult();
        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
        a1.setId("ann-1");
        MatchResultProspect annotP = new MatchResultProspect(a1, 1.0d, "annot", null);
        MatchResultProspect indivP = new MatchResultProspect(a1, 0.8d, "indiv", null);
        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
        set.add(annotP);
        set.add(indivP);
        injectProspects(mr, set);
        MatchResultProspect foundAnnot = MatchInspectionPairxEnricher.findProspect(
            mr, "ann-1", "annot");
        MatchResultProspect foundIndiv = MatchInspectionPairxEnricher.findProspect(
            mr, "ann-1", "indiv");
        assertNotNull(foundAnnot);
        assertNotNull(foundIndiv);
        assertTrue(foundAnnot.isType("annot"));
        assertTrue(foundIndiv.isType("indiv"));
        assertNotSame(foundAnnot, foundIndiv);
    }

    @Test void findProspect_returnsNullWhenAnnotationIdMissing() {
        MatchResult mr = new MatchResult();
        org.ecocean.Annotation a1 = new org.ecocean.Annotation();
        a1.setId("ann-1");
        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
        set.add(new MatchResultProspect(a1, 1.0d, "annot", null));
        injectProspects(mr, set);
        assertNull(MatchInspectionPairxEnricher.findProspect(mr, "ann-MISSING", "annot"));
    }

    @SuppressWarnings("unchecked")
    private static void injectProspects(MatchResult mr,
        java.util.HashSet<MatchResultProspect> prospects) {
        try {
            java.lang.reflect.Field f = MatchResult.class.getDeclaredField("prospects");
            f.setAccessible(true);
            f.set(mr, prospects);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
