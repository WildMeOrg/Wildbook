package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * v2 commit #8: payload/validation/classification tests for MlServiceClient.
 * Network round-trips are exercised end-to-end via MlServiceProcessor's
 * tests in a later commit; here we cover the pure-function pieces.
 */
class MlServiceClientTest {

    // --- joinEndpoint -----------------------------------------------------

    @Test void joinEndpointHandlesTrailingSlashes() {
        assertEquals("https://ml/pipeline/",
            MlServiceClient.joinEndpoint("https://ml/", "/pipeline/"));
        assertEquals("https://ml/pipeline/",
            MlServiceClient.joinEndpoint("https://ml///", "/pipeline/"));
        assertEquals("https://ml/pipeline/",
            MlServiceClient.joinEndpoint("https://ml", "/pipeline/"));
    }

    // --- buildPipelinePayload --------------------------------------------

    @Test void buildPipelinePayloadIncludesModelIds() {
        JSONObject conf = new JSONObject()
            .put("predict_model_id", "msv3")
            .put("classify_model_id", "efnet")
            .put("extract_model_id", "miewid-4.1")
            .put("orientation_model_id", "densenet");
        JSONObject p = MlServiceClient.buildPipelinePayload("http://img/a.jpg", conf);
        assertEquals("http://img/a.jpg", p.getString("image_uri"));
        assertEquals("msv3", p.getString("predict_model_id"));
        assertEquals("efnet", p.getString("classify_model_id"));
        assertEquals("miewid-4.1", p.getString("extract_model_id"));
        assertEquals("densenet", p.getString("orientation_model_id"));
    }

    @Test void buildPipelinePayloadOmitsMissingFields() {
        JSONObject conf = new JSONObject().put("predict_model_id", "msv3");
        JSONObject p = MlServiceClient.buildPipelinePayload("uri", conf);
        assertEquals("uri", p.getString("image_uri"));
        assertEquals("msv3", p.getString("predict_model_id"));
        assertFalse(p.has("classify_model_id"));
        assertFalse(p.has("orientation_model_id"));
    }

    // --- buildExtractPayload --------------------------------------------

    @Test void buildExtractPayloadIncludesBboxAndTheta() {
        JSONObject conf = new JSONObject().put("extract_model_id", "miewid-4.1");
        JSONObject p = MlServiceClient.buildExtractPayload("uri",
            new double[] { 10, 20, 30, 40 }, 0.5, conf);
        assertEquals("uri", p.getString("image_uri"));
        assertEquals("miewid-4.1", p.getString("extract_model_id"));
        assertEquals(0.5, p.getDouble("theta"));
        JSONArray bbox = p.getJSONArray("bbox");
        assertEquals(4, bbox.length());
        assertEquals(10.0, bbox.getDouble(0));
        assertEquals(40.0, bbox.getDouble(3));
    }

    // --- validatePipelineResponse ----------------------------------------

    private JSONObject validResult(int dim) {
        JSONArray bbox = new JSONArray().put(0).put(0).put(100).put(100);
        JSONArray emb = new JSONArray();
        for (int i = 0; i < dim; i++) emb.put(0.5);
        return new JSONObject()
            .put("bbox", bbox)
            .put("theta", 0.0)
            .put("embedding", emb)
            .put("embedding_model_id", "miewid")
            .put("embedding_model_version", "4.1");
    }

    @Test void validatePipelineResponseAcceptsZeroResults() throws Exception {
        JSONObject r = new JSONObject().put("success", true).put("results", new JSONArray());
        MlServiceClient.validatePipelineResponse(r, 2152);
    }

    @Test void validatePipelineResponseAcceptsValidResult() throws Exception {
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(validResult(2152)));
        MlServiceClient.validatePipelineResponse(r, 2152);
    }

    @Test void validatePipelineResponseRejectsSuccessFalse() {
        JSONObject r = new JSONObject().put("success", false);
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 2152));
        assertEquals("SUCCESS_FALSE", ex.getCode());
        assertFalse(ex.shouldRequeue());
    }

    @Test void validatePipelineResponseRejectsMissingResults() {
        JSONObject r = new JSONObject().put("success", true);
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 2152));
        assertEquals("INVALID", ex.getCode());
    }

    @Test void validatePipelineResponseRejectsNonArrayBbox() {
        JSONObject result = validResult(2152);
        result.put("bbox", new JSONArray().put(1).put(2).put(3));   // only 3 elements
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 2152));
        assertEquals("INVALID", ex.getCode());
    }

    @Test void validatePipelineResponseRejectsZeroWidthBbox() {
        JSONObject result = validResult(2152);
        result.put("bbox", new JSONArray().put(0).put(0).put(0).put(100));
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 2152));
        assertEquals("INVALID", ex.getCode());
    }

    @Test void validatePipelineResponseRejectsNonNumericBbox() {
        // org.json rejects literal NaN/Infinity at insertion time. The
        // attainable non-finite path: a non-numeric string where a number
        // should be. optDouble returns NaN as the default for non-coercible
        // entries, which is what validateBbox is checking against.
        JSONObject result = validResult(2152);
        result.put("bbox", new JSONArray().put(0).put(0).put("not-a-number").put(100));
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 2152));
    }

    @Test void validatePipelineResponseRejectsEmbeddingLengthMismatch() {
        JSONObject result = validResult(100);
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 2152));
        assertEquals("INVALID", ex.getCode());
        assertTrue(ex.getMessage().contains("length 100 != expected 2152"));
    }

    @Test void validatePipelineResponseAcceptsAnyLengthWhenExpectedDimZero() throws Exception {
        // expectedDim <= 0 means "unknown, skip length check" (per design).
        JSONObject result = validResult(100);
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        MlServiceClient.validatePipelineResponse(r, 0);
    }

    @Test void validatePipelineResponseRejectsMissingTheta() {
        // theta default-on-missing was a defect: a malformed result could be
        // persisted with a fabricated theta=0.0. Require presence.
        JSONObject result = validResult(100);
        result.remove("theta");
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 100));
        assertEquals("INVALID", ex.getCode());
        assertTrue(ex.getMessage().contains("missing theta"));
    }

    @Test void validatePipelineResponseRejectsNonNumericTheta() {
        JSONObject result = validResult(100);
        result.put("theta", "not-a-number");
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        IAException ex = assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 100));
        assertEquals("INVALID", ex.getCode());
    }

    @Test void validatePipelineResponseRejectsNonNumericInEmbedding() {
        // Same constraint as bbox: org.json rejects NaN insertion. A non-
        // numeric string at any embedding index returns NaN from optDouble,
        // which the validator must reject.
        JSONArray emb = new JSONArray();
        for (int i = 0; i < 100; i++) emb.put(0.5);
        emb.put(50, "not-a-number");
        JSONObject result = validResult(100);
        result.put("embedding", emb);
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 100));
    }

    @Test void validatePipelineResponseRejectsMissingModelId() {
        JSONObject result = validResult(100);
        result.remove("embedding_model_id");
        JSONObject r = new JSONObject().put("success", true)
            .put("results", new JSONArray().put(result));
        assertThrows(IAException.class,
            () -> MlServiceClient.validatePipelineResponse(r, 100));
    }

    // --- validateExtractResponse -----------------------------------------

    @Test void validateExtractResponseAcceptsValid() throws Exception {
        JSONArray emb = new JSONArray();
        for (int i = 0; i < 100; i++) emb.put(0.5);
        JSONObject r = new JSONObject()
            .put("success", true)
            .put("embedding", emb)
            .put("embedding_model_id", "miewid")
            .put("embedding_model_version", "4.1");
        MlServiceClient.validateExtractResponse(r, 100);
    }

    @Test void validateExtractResponseRejectsEmptyEmbedding() {
        JSONObject r = new JSONObject()
            .put("success", true)
            .put("embedding", new JSONArray())
            .put("embedding_model_id", "miewid")
            .put("embedding_model_version", "4.1");
        assertThrows(IAException.class,
            () -> MlServiceClient.validateExtractResponse(r, 0));
    }

    // --- classifyHttpFailure ---------------------------------------------

    @Test void classifyTimeoutBySocketTimeoutExceptionType() {
        Exception ex = new IOException("inner", new SocketTimeoutException("Read timed out"));
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("TIMEOUT", out.getCode());
        assertTrue(out.shouldRequeue());
        assertFalse(out.shouldIncrement());   // timeout doesn't increment retry count
    }

    @Test void classifyTimeoutByMessageFallback() {
        Exception ex = new IOException("connect timed out");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("TIMEOUT", out.getCode());
        assertTrue(out.shouldRequeue());
    }

    @Test void classifyConnectionRefused() {
        Exception ex = new IOException("Connection refused");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("NETWORK", out.getCode());
        assertTrue(out.shouldRequeue());
        assertTrue(out.shouldIncrement());
    }

    @Test void classify502EqualsSign() {
        Exception ex = new IOException("HTTP error code = 502");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("NETWORK", out.getCode());
        assertTrue(out.shouldRequeue());
        assertTrue(out.shouldIncrement());
    }

    @Test void classify503ColonSpelling() {
        // Defensive: accept both "= " and ": " spellings of HTTP error code.
        Exception ex = new IOException("HTTP error code : 503");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("NETWORK", out.getCode());
        assertTrue(out.shouldRequeue());
    }

    @Test void classify429RateLimit() {
        Exception ex = new IOException("HTTP error code = 429");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("RATE_LIMITED", out.getCode());
        assertTrue(out.shouldRequeue());
        assertTrue(out.shouldIncrement());
    }

    @Test void classify500GenericServer() {
        Exception ex = new IOException("HTTP error code = 500");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("SERVER_ERROR", out.getCode());
        assertTrue(out.shouldRequeue());
    }

    @Test void classify400ClientErrorNotRetryable() {
        Exception ex = new IOException("HTTP error code = 400");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("CLIENT_ERROR", out.getCode());
        assertFalse(out.shouldRequeue());
    }

    @Test void classifyUnrecognizedNotRetryable() {
        Exception ex = new IOException("totally unknown failure");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertFalse(out.shouldRequeue(),
            "Unknown failures must not retry-loop");
    }

    @Test void classifyConnectionReset() {
        // Common transient peer-side condition; treat like Connection refused.
        Exception ex = new IOException("Connection reset");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("NETWORK", out.getCode());
        assertTrue(out.shouldRequeue());
        assertTrue(out.shouldIncrement());
    }

    @Test void classify408RequestTimeout() {
        // Proxy/LB-emitted timeout; treat like SocketTimeoutException.
        Exception ex = new IOException("HTTP error code = 408");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("TIMEOUT", out.getCode());
        assertTrue(out.shouldRequeue());
        assertFalse(out.shouldIncrement());
    }

    @Test void classifyParseFailureAsInvalid() {
        // 200 OK with non-JSON body throws "could not convert postRaw() to
        // JSONObject" from RestClient.postJSON. That's a contract violation
        // by ml-service, not a network issue — classify INVALID, non-retryable.
        Exception ex = new IOException("could not convert postRaw() to JSONObject: <html>...");
        IAException out = MlServiceClient.classifyHttpFailure(ex, "http://ml/x");
        assertEquals("INVALID", out.getCode());
        assertFalse(out.shouldRequeue());
    }
}
