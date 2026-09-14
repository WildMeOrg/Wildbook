package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live end-to-end wire-contract probe for {@link MlServiceClient}.
 *
 * <p>Opt-in only. The JUnit condition skips the entire class unless
 * {@code ML_SERVICE_BASE_URL} is set. Each individual test also throws
 * {@link IllegalStateException} at runtime if neither image-source env
 * var is provided. CI does not set any of these, so the test never
 * fires by accident. Run locally with:</p>
 *
 * <pre>
 * # Required:
 * ML_SERVICE_BASE_URL=http://&lt;ml-service-host&gt;:&lt;port&gt; \
 * # One of:
 * ML_SERVICE_TEST_IMAGE_URI=&lt;url ml-service can fetch&gt; \
 * #   OR
 * ML_SERVICE_TEST_IMAGE_FILE=&lt;local image path&gt; \
 *     mvn test -Dtest=MlServiceLiveIntegrationTest -DargLine="-Xmx2g"
 * </pre>
 *
 * <p>What this exercises (and what's NOT covered by the 107 WireMock unit
 * tests):</p>
 * <ul>
 *   <li>Real HTTP round-trip from {@code MlServiceClient} to a live
 *       ml-service deployment, including timeouts and TLS.</li>
 *   <li>The actual ml-service response shape matches what
 *       {@code validatePipelineResponse} / {@code validateExtractResponse}
 *       expect: top-level {@code success:true}, top-level {@code results}
 *       array, per-result {@code embedding} / {@code embedding_model_id} /
 *       {@code embedding_model_version}.</li>
 *   <li>The image_uri-fetch path inside ml-service works for the chosen
 *       test image (no auth, no firewall surprises).</li>
 *   <li>Round-trip latency is within the 30s connect / 120s read budgets
 *       MlServiceClient enforces by default.</li>
 * </ul>
 *
 * <p>What this does NOT cover:</p>
 * <ul>
 *   <li>JDO/OpenSearch/FileQueue handoff (those still need a Tomcat
 *       smoke test on a dev deployment).</li>
 *   <li>Phase 4 idempotency (no Annotation rows persisted here).</li>
 *   <li>WBIA registration polling.</li>
 * </ul>
 *
 * <p>Test image requirements: the {@code /extract/} test uses a fixed
 * bbox at {@code (100, 100, 400, 400)}, so the chosen image must be at
 * least 500&times;500 pixels for the bbox to land inside bounds.</p>
 *
 * <p>Failure modes worth understanding:</p>
 * <ul>
 *   <li>{@code IAException(code=INVALID, "/pipeline/ response missing
 *       'results' array")} → ml-service is not running the v2-contract
 *       fixes; pull the latest ml-service repo and redeploy.</li>
 *   <li>{@code IAException(code=TIMEOUT)} → network path is too slow OR
 *       ml-service is loading models on cold start. Re-run; should be
 *       fast on the second call.</li>
 *   <li>{@code IAException(code=SUCCESS_FALSE)} → ml-service returned
 *       {@code success: false}; check ml-service logs.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "ML_SERVICE_BASE_URL", matches = ".+",
    disabledReason = "Set ML_SERVICE_BASE_URL to enable live ml-service integration test")
class MlServiceLiveIntegrationTest {

    /** Default model triple. Override via the env vars below if the live
     *  ml-service deployment uses different model_ids. */
    private static final String DEFAULT_PREDICT_MODEL  = "msv3";
    private static final String DEFAULT_CLASSIFY_MODEL = "efficientnet-classifier";
    private static final String DEFAULT_EXTRACT_MODEL  = "miewid-msv4.1";

    private static String baseUrl() {
        return System.getenv("ML_SERVICE_BASE_URL");
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static String predictModel()  { return envOr("ML_SERVICE_PREDICT_MODEL",  DEFAULT_PREDICT_MODEL);  }
    private static String classifyModel() { return envOr("ML_SERVICE_CLASSIFY_MODEL", DEFAULT_CLASSIFY_MODEL); }
    private static String extractModel()  { return envOr("ML_SERVICE_EXTRACT_MODEL",  DEFAULT_EXTRACT_MODEL);  }

    /**
     * Resolve the image to send. One of {@code ML_SERVICE_TEST_IMAGE_URI}
     * (a URL the ml-service can fetch) or {@code ML_SERVICE_TEST_IMAGE_FILE}
     * (a local path that gets inlined as a base64 {@code data:} URI) must
     * be set. There is no built-in default URL: any hardcoded URL would
     * either leak deployment-specific information into the repo or fail
     * silently at runtime (e.g. Wikimedia 403s default httpx UAs).
     */
    private static String imageUri() {
        String uri = System.getenv("ML_SERVICE_TEST_IMAGE_URI");
        if (uri != null && !uri.isEmpty()) return uri;
        String file = System.getenv("ML_SERVICE_TEST_IMAGE_FILE");
        if (file != null && !file.isEmpty()) return imageFileAsDataUri(file);
        throw new IllegalStateException(
            "Set ML_SERVICE_TEST_IMAGE_URI=<url> or ML_SERVICE_TEST_IMAGE_FILE=<local path> "
            + "to run this test. The image must be reachable by the ml-service (or inlined "
            + "via _FILE) and >=500x500 pixels for the fixed /extract/ bbox.");
    }

    private static String imageFileAsDataUri(String path) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
            String mime = mimeForPath(path);
            return "data:" + mime + ";base64," +
                java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed reading test image at " + path + ": " + e, e);
        }
    }

    private static String mimeForPath(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        if (lower.endsWith(".gif")) return "image/gif";
        // Fall back to JPEG rather than throw; an operator pointing at an
        // unsupported file will see a clearer error from the ml-service
        // (e.g. "could not decode image") than from this helper.
        return "image/jpeg";
    }

    /**
     * Redact a possibly-huge {@code data:} URI before logging. base64 of a
     * 1 MB image is ~1.4 MB of text — emitting it to stdout pollutes the
     * Surefire report and leaks the test image's bytes into log files.
     */
    private static String forLog(String uri) {
        if (uri == null) return "(null)";
        if (!uri.startsWith("data:")) return uri;
        int comma = uri.indexOf(',');
        String header = (comma > 0) ? uri.substring(0, comma + 1) : "data:";
        return header + "[" + uri.length() + " chars, redacted]";
    }

    private static JSONObject zebraConfig() {
        return new JSONObject()
            .put("api_endpoint", baseUrl())
            .put("predict_model_id", predictModel())
            .put("classify_model_id", classifyModel())
            .put("extract_model_id", extractModel());
    }

    @Test
    @DisplayName("/pipeline/ round-trip: response satisfies v2 contract")
    void pipelineRoundTrip() throws Exception {
        // Resolve image once: imageFileAsDataUri reads + base64-encodes
        // the file each call, so caching also avoids redundant I/O.
        String image = imageUri();
        MlServiceClient client = new MlServiceClient();
        long t0 = System.currentTimeMillis();
        JSONObject response;
        try {
            response = client.pipeline(baseUrl(), image, zebraConfig());
        } catch (IAException ex) {
            fail("MlServiceClient.pipeline threw " + ex.getCode() + ": " + ex.getMessage(), ex);
            return;
        }
        long elapsedMs = System.currentTimeMillis() - t0;

        // The validator inside MlServiceClient.pipeline already checked the
        // structural contract. If we got here, success/results/each-result
        // shape is good. Do a few extra reality checks + print a summary so
        // operators running this locally can see what came back.

        assertTrue(response.optBoolean("success", false),
            "expected success=true; got: " + response);

        JSONArray results = response.optJSONArray("results");
        assertNotNull(results, "expected results array");

        System.out.println("---- /pipeline/ response summary ----");
        System.out.println("  base_url      : " + baseUrl());
        System.out.println("  image_uri     : " + forLog(image));
        System.out.println("  models        : " + response.optJSONObject("models_used"));
        System.out.println("  round-trip    : " + elapsedMs + " ms");
        System.out.println("  results count : " + results.length());

        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            JSONArray emb = r.optJSONArray("embedding");
            assertNotNull(emb, "results[" + i + "].embedding missing");
            assertTrue(emb.length() > 0, "results[" + i + "].embedding empty");
            String modelId = r.optString("embedding_model_id", "");
            String modelVer = r.optString("embedding_model_version", "");
            assertTrue(!modelId.isEmpty(),
                "results[" + i + "].embedding_model_id missing or empty");
            assertTrue(!modelVer.isEmpty(),
                "results[" + i + "].embedding_model_version missing or empty");
            System.out.println(String.format(
                "  [%d] bbox=%s theta=%s embed_dim=%d model=%s/%s",
                i, r.optJSONArray("bbox"), r.opt("theta"), emb.length(),
                modelId, modelVer));
        }
    }

    @Test
    @DisplayName("/extract/ round-trip: response satisfies v2 contract")
    void extractRoundTrip() throws Exception {
        // Hardcoded bbox at (100,100,400,400). The test image must be at
        // least 500x500 for this to land inside bounds (this is also
        // documented in the class javadoc). Smaller test images would
        // need ML_SERVICE_TEST_BBOX support (not yet implemented).
        double[] bbox = new double[] { 100.0, 100.0, 400.0, 400.0 };
        double theta = 0.0;

        String image = imageUri();
        MlServiceClient client = new MlServiceClient();
        long t0 = System.currentTimeMillis();
        JSONObject response;
        try {
            response = client.extract(baseUrl(), image, bbox, theta, zebraConfig());
        } catch (IAException ex) {
            fail("MlServiceClient.extract threw " + ex.getCode() + ": " + ex.getMessage(), ex);
            return;
        }
        long elapsedMs = System.currentTimeMillis() - t0;

        // Validator already checked top-level embedding / success / model id /
        // model version presence. Echo a summary for visual verification.

        JSONArray emb = response.optJSONArray("embedding");
        assertNotNull(emb, "expected embedding array");
        assertTrue(emb.length() > 0, "embedding array is empty");

        System.out.println("---- /extract/ response summary ----");
        System.out.println("  base_url      : " + baseUrl());
        System.out.println("  image_uri     : " + forLog(image));
        System.out.println("  bbox          : " + java.util.Arrays.toString(bbox));
        System.out.println("  theta         : " + theta);
        System.out.println("  round-trip    : " + elapsedMs + " ms");
        System.out.println("  embedding_dim : " + emb.length());
        System.out.println("  model         : " +
            response.optString("embedding_model_id", "?") + "/" +
            response.optString("embedding_model_version", "?"));
    }

    @Test
    @DisplayName("dimension stability: two /extract/ calls return same embedding dim")
    void dimensionStability() throws Exception {
        double[] bbox = new double[] { 100.0, 100.0, 400.0, 400.0 };
        String image = imageUri();
        MlServiceClient client = new MlServiceClient();
        JSONObject r1 = client.extract(baseUrl(), image, bbox, 0.0, zebraConfig());
        JSONObject r2 = client.extract(baseUrl(), image, bbox, 0.0, zebraConfig());
        int dim1 = r1.optJSONArray("embedding").length();
        int dim2 = r2.optJSONArray("embedding").length();
        assertEquals(dim1, dim2,
            "embedding dimension changed between calls: " + dim1 + " vs " + dim2);
        System.out.println("---- /extract/ dim stability ----");
        System.out.println("  embedding_dim consistent across two calls: " + dim1);
        System.out.println("  NOTE: this number should be set as embedding_dimension in");
        System.out.println("  Equus.quagga._mlservice_conf[0] so validatePipelineResponse");
        System.out.println("  can enforce length matches at runtime.");
    }
}
