package org.ecocean.ia;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.RestClient;
import org.ecocean.Util;

/**
 * HTTP-only wrapper around ml-service ({@code /pipeline/} and {@code /extract/}
 * endpoints). Validates the response shape against the v2 contract. No
 * Shepherd, no DB; just HTTP + JSON validation.
 *
 * <p>Migration plan v2 §commit #8. Used by {@link
 * org.ecocean.ia.MlServiceProcessor} (commit #9). Tests directly via
 * {@code MlServiceClientTest}.</p>
 *
 * <h3>Retry classification (matches v2 plan §Failure ladder):</h3>
 * <ul>
 *   <li>{@link SocketTimeoutException} or message contains "timed out" →
 *       IAException retryable=true, increment=false (timeout doesn't imply
 *       overload).</li>
 *   <li>Connection refused / 502 / 503 / 504 / 5xx → retryable=true,
 *       increment=true.</li>
 *   <li>429 (rate-limited) → retryable=true, increment=true so the client
 *       backs off.</li>
 *   <li>Other 4xx, parse failure, {@code success=false} response → retryable
 *       =false; mark task error.</li>
 * </ul>
 *
 * <p>RestClient throws {@code "HTTP error code = NNN"} (literally with {@code
 * =}). The classifier accepts both {@code "= NNN"} and {@code ": NNN"}
 * spellings to be defensive against any future RestClient refactor.</p>
 */
public class MlServiceClient {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 120_000;

    // Matches "HTTP error code = 502" or "HTTP error code : 502", capturing
    // the status code as group 1.
    private static final Pattern HTTP_CODE_PATTERN =
        Pattern.compile("HTTP error code\\s*[=:]\\s*(\\d{3})");

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public MlServiceClient() {
        this(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public MlServiceClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * POSTs to {@code apiEndpoint/pipeline/} with the predict/classify/extract/
     * orientation model IDs from {@code config}. Returns the validated response.
     *
     * @param apiEndpoint base URL of ml-service (no trailing slash required)
     * @param imageUri    URL or local path of the image to process
     * @param config      a single {@code _mlservice_conf} entry from IA.json
     * @return validated response JSON ({@code success:true, results:[...]})
     * @throws IAException on network failure or response-validation failure;
     *         {@code shouldRequeue()} and {@code getCode()} carry the
     *         classification. Codes: {@code TIMEOUT}, {@code NETWORK},
     *         {@code SERVER_ERROR}, {@code RATE_LIMITED},
     *         {@code CLIENT_ERROR}, {@code SUCCESS_FALSE}, {@code INVALID}.
     */
    public JSONObject pipeline(String apiEndpoint, String imageUri, JSONObject config)
    throws IAException {
        JSONObject payload = buildPipelinePayload(imageUri, config);
        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/pipeline/"),
            payload);
        validatePipelineResponse(response, config.optInt("embedding_dimension", 0));
        return response;
    }

    /**
     * POSTs to {@code apiEndpoint/extract/}. Used for manual annotations
     * (user-drawn bbox; no detection step needed).
     *
     * @throws IAException same contract as {@link #pipeline}.
     */
    public JSONObject extract(String apiEndpoint, String imageUri, double[] bbox,
        double theta, JSONObject config)
    throws IAException {
        JSONObject payload = buildExtractPayload(imageUri, bbox, theta, config);
        JSONObject response = postWithClassification(joinEndpoint(apiEndpoint, "/extract/"),
            payload);
        validateExtractResponse(response, config.optInt("embedding_dimension", 0));
        return response;
    }

    // ---------------------------------------------------------------------
    // Internal helpers (package-visible for unit tests)
    // ---------------------------------------------------------------------

    static String joinEndpoint(String base, String path) {
        if (base == null) return path;
        String trimmed = base.replaceAll("/+$", "");
        return trimmed + path;
    }

    static JSONObject buildPipelinePayload(String imageUri, JSONObject config) {
        JSONObject p = new JSONObject();
        p.put("image_uri", imageUri);
        if (config != null) {
            if (config.has("predict_model_id"))
                p.put("predict_model_id", config.opt("predict_model_id"));
            if (config.has("classify_model_id"))
                p.put("classify_model_id", config.opt("classify_model_id"));
            if (config.has("extract_model_id"))
                p.put("extract_model_id", config.opt("extract_model_id"));
            if (config.has("orientation_model_id"))
                p.put("orientation_model_id", config.opt("orientation_model_id"));
        }
        return p;
    }

    static JSONObject buildExtractPayload(String imageUri, double[] bbox, double theta,
        JSONObject config) {
        JSONObject p = new JSONObject();
        p.put("image_uri", imageUri);
        if (config != null && config.has("extract_model_id")) {
            p.put("extract_model_id", config.opt("extract_model_id"));
        }
        if (bbox != null) {
            JSONArray b = new JSONArray();
            for (double v : bbox) b.put(v);
            p.put("bbox", b);
        }
        p.put("theta", theta);
        return p;
    }

    static void validatePipelineResponse(JSONObject response, int expectedDim)
    throws IAException {
        if (response == null)
            throw new IAException("INVALID", "/pipeline/ returned null", false, false);
        if (!response.optBoolean("success", false))
            throw new IAException("SUCCESS_FALSE",
                "/pipeline/ returned success=false: " + response, false, false);
        JSONArray results = response.optJSONArray("results");
        if (results == null)
            throw new IAException("INVALID",
                "/pipeline/ response missing 'results' array: " + response, false, false);
        // Zero detections is a valid response. Each present result must be
        // structurally complete; we reject the whole response on any partial
        // result rather than persist a subset.
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null)
                throw new IAException("INVALID",
                    "/pipeline/ results[" + i + "] is not an object", false, false);
            validateBbox(r.optJSONArray("bbox"), i);
            // theta must be present AND finite. Default-on-missing (e.g.
            // optDouble("theta", 0.0)) would accept a malformed result and
            // persist a fabricated orientation. Require presence.
            if (!r.has("theta"))
                throw new IAException("INVALID",
                    "/pipeline/ results[" + i + "] missing theta", false, false);
            double theta = r.optDouble("theta", Double.NaN);
            if (!isFiniteDouble(theta))
                throw new IAException("INVALID",
                    "/pipeline/ results[" + i + "] theta non-finite", false, false);
            validateEmbeddingField(r, "embedding", expectedDim, "results[" + i + "]");
        }
    }

    static void validateExtractResponse(JSONObject response, int expectedDim)
    throws IAException {
        if (response == null)
            throw new IAException("INVALID", "/extract/ returned null", false, false);
        if (!response.optBoolean("success", false))
            throw new IAException("SUCCESS_FALSE",
                "/extract/ returned success=false: " + response, false, false);
        validateEmbeddingField(response, "embedding", expectedDim, "response");
    }

    private static void validateBbox(JSONArray bbox, int idx)
    throws IAException {
        if (bbox == null || bbox.length() != 4)
            throw new IAException("INVALID",
                "/pipeline/ results[" + idx + "] bbox must be a 4-element array", false, false);
        for (int j = 0; j < 4; j++) {
            double v = bbox.optDouble(j, Double.NaN);
            if (!isFiniteDouble(v))
                throw new IAException("INVALID",
                    "/pipeline/ results[" + idx + "] bbox[" + j + "] non-finite", false, false);
        }
        if (bbox.optDouble(2) < 1.0 || bbox.optDouble(3) < 1.0)
            throw new IAException("INVALID",
                "/pipeline/ results[" + idx + "] bbox width/height must be >= 1", false, false);
    }

    private static void validateEmbeddingField(JSONObject parent, String fieldName,
        int expectedDim, String context)
    throws IAException {
        JSONArray emb = parent.optJSONArray(fieldName);
        if (emb == null)
            throw new IAException("INVALID",
                context + " missing '" + fieldName + "' array", false, false);
        if (expectedDim > 0 && emb.length() != expectedDim)
            throw new IAException("INVALID",
                context + " embedding length " + emb.length() + " != expected " + expectedDim,
                false, false);
        if (emb.length() == 0)
            throw new IAException("INVALID",
                context + " embedding array is empty", false, false);
        for (int j = 0; j < emb.length(); j++) {
            double v = emb.optDouble(j, Double.NaN);
            if (!isFiniteDouble(v))
                throw new IAException("INVALID",
                    context + " embedding[" + j + "] non-finite", false, false);
        }
        String modelId = parent.optString("embedding_model_id", null);
        String modelVer = parent.optString("embedding_model_version", null);
        if (!Util.stringExists(modelId) || !Util.stringExists(modelVer))
            throw new IAException("INVALID",
                context + " missing embedding_model_id or embedding_model_version",
                false, false);
    }

    private static boolean isFiniteDouble(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private JSONObject postWithClassification(String url, JSONObject payload)
    throws IAException {
        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException ex) {
            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
        }
        try {
            return RestClient.postJSON(u, payload, null, connectTimeoutMs, readTimeoutMs);
        } catch (Exception ex) {
            throw classifyHttpFailure(ex, url);
        }
    }

    /** Classify a RestClient throw into the v2 failure-ladder buckets. */
    static IAException classifyHttpFailure(Exception ex, String url) {
        // Detect timeout primarily by exception type; fall back to message
        // sniffing for environments where the cause chain is flattened.
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return new IAException("TIMEOUT",
                    "ml-service timeout on " + url + ": " + ex.getMessage(), true, false);
            }
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.contains("timed out")) {
            return new IAException("TIMEOUT",
                "ml-service timeout on " + url + ": " + msg, true, false);
        }
        // Connection refused and Connection reset are both transient peer-side
        // conditions; retry with increment so the back-off counter advances.
        if (msg.contains("Connection refused") || msg.contains("Connection reset")) {
            return new IAException("NETWORK",
                "ml-service connection error on " + url + ": " + msg, true, true);
        }
        // Parse failures from RestClient.postJSON: the response was a 200 OK
        // but the body wasn't valid JSON. That's a contract violation by
        // ml-service, not a network issue. Classify as INVALID, non-retryable.
        if (msg.contains("could not convert postRaw()")) {
            return new IAException("INVALID",
                "ml-service returned non-JSON 200 on " + url + ": " + msg, false, false);
        }
        Matcher m = HTTP_CODE_PATTERN.matcher(msg);
        if (m.find()) {
            int statusCode;
            try {
                statusCode = Integer.parseInt(m.group(1));
            } catch (NumberFormatException nfe) {
                statusCode = 0;
            }
            // 408 (Request Timeout) — typically emitted by a proxy/LB in front
            // of ml-service; treat like a normal timeout (retry, no increment).
            if (statusCode == 408) {
                return new IAException("TIMEOUT",
                    "ml-service 408 on " + url, true, false);
            }
            if (statusCode == 429) {
                return new IAException("RATE_LIMITED",
                    "ml-service rate-limited (429) on " + url, true, true);
            }
            if (statusCode == 502 || statusCode == 503 || statusCode == 504) {
                return new IAException("NETWORK",
                    "ml-service " + statusCode + " on " + url, true, true);
            }
            if (statusCode >= 500 && statusCode < 600) {
                return new IAException("SERVER_ERROR",
                    "ml-service " + statusCode + " on " + url, true, true);
            }
            if (statusCode >= 400 && statusCode < 500) {
                return new IAException("CLIENT_ERROR",
                    "ml-service " + statusCode + " on " + url + " (non-retryable)",
                    false, false);
            }
        }
        // Unrecognized; treat as non-retryable to avoid spinning.
        return new IAException("NETWORK",
            "ml-service request failed on " + url + ": " + msg, false, false);
    }
}
