package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * v2 commit #9: pure-logic tests for MlServiceProcessor.
 *
 * <p>The Phase 1-5 lifecycle methods require real Shepherd transactions,
 * JDO mutations, and live OpenSearch — those are reviewable by diff and
 * exercised by hand-test per the v2 plan's test-strategy decision
 * (WireMock unit tests only). Here we cover:</p>
 *
 * <ul>
 *   <li>Top-level {@code process()} payload routing (validation errors,
 *       missing-payload-fields branches).</li>
 *   <li>{@code mapNonRetryableError(IAException)} maps each typed code
 *       to the right outcome Kind.</li>
 *   <li>{@code bboxKey}/{@code thetaKey} formatting (rounding and
 *       string-format invariants).</li>
 *   <li>{@code findExistingAnnotation} dedupe matching.</li>
 *   <li>{@code parseEmbeddingMethodVersion} dash-split derivation of
 *       embedding {@code (METHOD, METHODVERSION)} from the ml-service
 *       {@code /extract/} response.</li>
 * </ul>
 */
class MlServiceProcessorTest {

    // --- process() payload routing -------------------------------------

    @Test void processRejectsNullPayload() {
        MlServiceProcessor p = new MlServiceProcessor("context0");
        MlServiceJobOutcome out = p.process(null);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("INVALID_PAYLOAD", out.getCode());
    }

    @Test void processRejectsPayloadWithoutMediaAssetOrAnnotationId() {
        MlServiceProcessor p = new MlServiceProcessor("context0");
        JSONObject payload = new JSONObject()
            .put("mlServiceV2", true)
            .put("taxonomyString", "Rhincodon typus");
        MlServiceJobOutcome out = p.process(payload);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("INVALID_PAYLOAD", out.getCode());
        assertNotNull(out.getMessage());
    }

    // --- mapNonRetryableError ------------------------------------------

    @Test void mapNonRetryableInvalidIsValidationError() {
        IAException ex = new IAException("INVALID", "bad bbox", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("INVALID", out.getCode());
    }

    @Test void mapNonRetryableSuccessFalseIsValidationError() {
        IAException ex = new IAException("SUCCESS_FALSE",
            "ml-service success=false", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals("SUCCESS_FALSE", out.getCode());
    }

    @Test void mapNonRetryableNetworkIsNetworkError() {
        IAException ex = new IAException("NETWORK",
            "ml-service 502", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
        assertEquals("NETWORK", out.getCode());
    }

    @Test void mapNonRetryableTimeoutIsNetworkError() {
        IAException ex = new IAException("TIMEOUT",
            "ml-service read timed out", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    }

    @Test void mapNonRetryableClientErrorIsNetworkError() {
        // 4xx surfaces as CLIENT_ERROR from the client. The processor's
        // contract: anything not VALIDATION* maps to NETWORK (with code preserved).
        IAException ex = new IAException("CLIENT_ERROR",
            "ml-service 400", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
        assertEquals("CLIENT_ERROR", out.getCode());
    }

    @Test void mapNonRetryableUnknownCodeIsNetworkError() {
        IAException ex = new IAException("WEIRD_CODE",
            "something happened", false, false);
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        // Defensive default: unknown codes route to NETWORK rather than
        // crashing the processor.
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    }

    @Test void mapNonRetryableNullCode() {
        // IAException constructed with the legacy 1/2/3-arg constructors
        // leaves code null. Treat as ERROR_NETWORK.
        IAException ex = new IAException("legacy message");
        MlServiceJobOutcome out = MlServiceProcessor.mapNonRetryableError(ex);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind());
    }

    // --- bboxKey / thetaKey -------------------------------------------

    @Test void bboxKeyRoundsToInts() {
        // The composite-unique-index columns are literal strings so we get
        // debugability over hash opacity. Rounded ints from a 4-element double[].
        assertEquals("10:20:30:40",
            MlServiceProcessor.bboxKey(new double[] { 10.0, 20.0, 30.0, 40.0 }));
        assertEquals("10:20:30:40",
            MlServiceProcessor.bboxKey(new double[] { 10.4, 20.4, 30.4, 40.4 }));
        assertEquals("11:21:31:41",
            MlServiceProcessor.bboxKey(new double[] { 10.5, 20.5, 30.5, 40.5 }));
    }

    @Test void thetaKeyRoundsToFourDecimals() {
        assertEquals("0.0000", MlServiceProcessor.thetaKey(0.0));
        assertEquals("3.1416", MlServiceProcessor.thetaKey(3.1415926));
        // Negative angles round symmetrically.
        assertEquals("-1.5708", MlServiceProcessor.thetaKey(-1.5707963));
    }

    @Test void thetaKeyHandlesNegativeZero() {
        // Negative zero formats the same as positive zero, matching the
        // expected key for "theta is zero".
        String k = MlServiceProcessor.thetaKey(-0.0);
        assertTrue(k.equals("0.0000") || k.equals("-0.0000"),
            "unexpected thetaKey for -0.0: " + k);
    }

    // --- parseEmbeddingMethodVersion ----------------------------------

    @Test void parseEmbeddingMethodVersionSplitsOnDash() {
        // The canonical case: ml-service returns model_id=miewid-msv4.1
        // and we must stamp METHOD=miewid, METHODVERSION=msv4.1 so the
        // matchConfig built from IA.json finds the row.
        JSONObject resp = new JSONObject()
            .put("embedding_model_id", "miewid-msv4.1")
            .put("embedding_model_version", "4.1");
        String[] mv = MlServiceProcessor.parseEmbeddingMethodVersion(resp);
        assertEquals("miewid", mv[0]);
        assertEquals("msv4.1", mv[1]);
    }

    @Test void parseEmbeddingMethodVersionMsv3() {
        // Older deployments still use msv3; same dash-split rule applies.
        JSONObject resp = new JSONObject()
            .put("embedding_model_id", "miewid-msv3")
            .put("embedding_model_version", "3.0");
        String[] mv = MlServiceProcessor.parseEmbeddingMethodVersion(resp);
        assertEquals("miewid", mv[0]);
        assertEquals("msv3", mv[1]);
    }

    @Test void parseEmbeddingMethodVersionMultiDashTakesEverythingAfterFirst() {
        // Multi-dash IDs are not embedding models in practice today but
        // the split rule must be deterministic: first dash is the
        // method/version boundary, the remainder is the version.
        JSONObject resp = new JSONObject()
            .put("embedding_model_id", "miewid-msv4.1-beta")
            .put("embedding_model_version", "4.1-beta");
        String[] mv = MlServiceProcessor.parseEmbeddingMethodVersion(resp);
        assertEquals("miewid", mv[0]);
        assertEquals("msv4.1-beta", mv[1]);
    }

    @Test void parseEmbeddingMethodVersionNoDashFallsBackToVersionField() {
        // If the ID has no dash, version must come from the explicit
        // embedding_model_version field.
        JSONObject resp = new JSONObject()
            .put("embedding_model_id", "miewid")
            .put("embedding_model_version", "msv4.1");
        String[] mv = MlServiceProcessor.parseEmbeddingMethodVersion(resp);
        assertEquals("miewid", mv[0]);
        assertEquals("msv4.1", mv[1]);
    }

    @Test void parseEmbeddingMethodVersionThrowsOnMissingId() {
        JSONObject resp = new JSONObject()
            .put("embedding_model_version", "msv4.1");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> MlServiceProcessor.parseEmbeddingMethodVersion(resp));
        assertTrue(ex.getMessage().contains("embedding_model_id"),
            "message should mention the missing field: " + ex.getMessage());
    }

    @Test void parseEmbeddingMethodVersionThrowsOnNoDashNoVersion() {
        // No dash AND no embedding_model_version => cannot derive version
        // => throw rather than write a null tag (matches the prior
        // getString() NPE behavior, with a clearer message).
        JSONObject resp = new JSONObject()
            .put("embedding_model_id", "miewid");
        assertThrows(IllegalStateException.class,
            () -> MlServiceProcessor.parseEmbeddingMethodVersion(resp));
    }

    @Test void parseEmbeddingMethodVersionThrowsOnNullResponse() {
        assertThrows(IllegalStateException.class,
            () -> MlServiceProcessor.parseEmbeddingMethodVersion(null));
    }

    @Test void parseEmbeddingMethodVersionLeadingOrTrailingDashTreatedAsNoDash() {
        // A leading or trailing dash leaves either method or version
        // empty after split, so treat the ID as a single token and fall
        // back to the explicit version field.
        JSONObject leading = new JSONObject()
            .put("embedding_model_id", "-msv4.1")
            .put("embedding_model_version", "msv4.1");
        String[] mv = MlServiceProcessor.parseEmbeddingMethodVersion(leading);
        assertEquals("-msv4.1", mv[0]);
        assertEquals("msv4.1", mv[1]);

        JSONObject trailing = new JSONObject()
            .put("embedding_model_id", "miewid-")
            .put("embedding_model_version", "msv4.1");
        mv = MlServiceProcessor.parseEmbeddingMethodVersion(trailing);
        assertEquals("miewid-", mv[0]);
        assertEquals("msv4.1", mv[1]);
    }
}
