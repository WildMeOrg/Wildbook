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
 * <p>Opt-in only. Runs only when the env var {@code ML_SERVICE_BASE_URL}
 * is set. CI does not set it, so this test never fires by accident. Run
 * locally with:</p>
 *
 * <pre>
 * ML_SERVICE_BASE_URL=http://52.146.95.168:6050 \
 * ML_SERVICE_TEST_IMAGE_URI=https://upload.wikimedia.org/wikipedia/commons/9/9e/Plains_Zebra_Equus_quagga.jpg \
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
 * <p>Failure modes worth understanding:</p>
 * <ul>
 *   <li>{@code IAException(code=INVALID, "/pipeline/ response missing
 *       'results' array")} → ml-service is not running the v2-contract
 *       fixes (commit {@code 3fbed82} or later in {@code /mnt/c/ml-service}).</li>
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

    /**
     * Default test image. Public-domain plains zebra photo from Wikimedia
     * Commons. Override via {@code ML_SERVICE_TEST_IMAGE_URI} env var if
     * the deployment can't reach Wikimedia (e.g., firewalled environment).
     */
    private static final String DEFAULT_IMAGE_URI =
        "https://upload.wikimedia.org/wikipedia/commons/9/9e/Plains_Zebra_Equus_quagga.jpg";

    /** Zebra model triple. Matches what zebra.wildme.org's IA.json has under
     *  {@code Equus.quagga._mlservice_conf[0]}. */
    private static final String PREDICT_MODEL  = "msv3";
    private static final String CLASSIFY_MODEL = "efficientnet-classifier";
    private static final String EXTRACT_MODEL  = "miewid-msv4.1";

    private static String baseUrl() {
        return System.getenv("ML_SERVICE_BASE_URL");
    }

    private static String imageUri() {
        String v = System.getenv("ML_SERVICE_TEST_IMAGE_URI");
        return (v == null || v.isEmpty()) ? DEFAULT_IMAGE_URI : v;
    }

    private static JSONObject zebraConfig() {
        return new JSONObject()
            .put("api_endpoint", baseUrl())
            .put("predict_model_id", PREDICT_MODEL)
            .put("classify_model_id", CLASSIFY_MODEL)
            .put("extract_model_id", EXTRACT_MODEL);
    }

    @Test
    @DisplayName("/pipeline/ round-trip: response satisfies v2 contract")
    void pipelineRoundTrip() throws Exception {
        MlServiceClient client = new MlServiceClient();
        long t0 = System.currentTimeMillis();
        JSONObject response;
        try {
            response = client.pipeline(baseUrl(), imageUri(), zebraConfig());
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
        System.out.println("  image_uri     : " + imageUri());
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
        // Use a small fixed bbox in the upper-left quadrant of the image.
        // The /extract/ endpoint accepts either ints or doubles; we send
        // doubles to mirror what MlServiceClient does in production.
        double[] bbox = new double[] { 100.0, 100.0, 400.0, 400.0 };
        double theta = 0.0;

        MlServiceClient client = new MlServiceClient();
        long t0 = System.currentTimeMillis();
        JSONObject response;
        try {
            response = client.extract(baseUrl(), imageUri(), bbox, theta, zebraConfig());
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
        System.out.println("  image_uri     : " + imageUri());
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
        MlServiceClient client = new MlServiceClient();
        JSONObject r1 = client.extract(baseUrl(), imageUri(), bbox, 0.0, zebraConfig());
        JSONObject r2 = client.extract(baseUrl(), imageUri(), bbox, 0.0, zebraConfig());
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
