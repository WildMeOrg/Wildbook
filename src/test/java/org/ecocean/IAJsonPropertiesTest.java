package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Tests for the v2 ml-service migration accessors on IAJsonProperties.
 * Uses {@link org.ecocean.JsonProperties#setJson(JSONObject)} to inject
 * synthetic config trees rather than load from disk.
 *
 * Routing invariant under test: a species routes to ml-service iff its
 * `_id_conf.default.pipeline_root == "vector"` AND its `_mlservice_conf`
 * array is populated.
 */
class IAJsonPropertiesTest {

    private static final Taxonomy TAXY = new Taxonomy("Rhincodon typus");

    private IAJsonProperties iaConfigWith(JSONObject json) {
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac, "IAJsonProperties.iaConfig() must not be null in tests");
        iac.setJson(json);
        return iac;
    }

    private JSONObject vectorDefaultConfig() {
        return new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_mlservice_conf", new JSONArray()
                        .put(new JSONObject()
                            .put("predict_model_id", "msv3")
                            .put("extract_model_id", "miewid-msv4.1")
                            .put("embedding_dimension", 2152)
                            .put("api_endpoint", "https://ml-service.example.org")))
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject()
                                .put("default", true)
                                .put("method", "miewid")
                                .put("version", "4.1")
                                .put("pipeline_root", "vector"))
                            .put(new JSONObject()
                                .put("available_on_request", true)
                                .put("pipeline_root", "HotSpotter"))))));
    }

    private JSONObject hotspotterDefaultConfig() {
        return new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_mlservice_conf", new JSONArray()
                        .put(new JSONObject().put("predict_model_id", "msv3")))
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject()
                                .put("default", true)
                                .put("pipeline_root", "HotSpotter"))
                            .put(new JSONObject()
                                .put("available_on_request", true)
                                .put("method", "miewid")
                                .put("version", "4.1")
                                .put("pipeline_root", "vector"))))));
    }

    @Test void getPipelineRoot_returnsVectorWhenDefaultIsVector() {
        IAJsonProperties iac = iaConfigWith(vectorDefaultConfig());
        assertEquals("vector", iac.getPipelineRoot(TAXY));
    }

    @Test void getPipelineRoot_returnsHotspotterWhenDefaultIsHotspotter() {
        IAJsonProperties iac = iaConfigWith(hotspotterDefaultConfig());
        assertEquals("HotSpotter", iac.getPipelineRoot(TAXY));
    }

    @Test void getPipelineRoot_returnsNullWhenNoDefaultEntry() {
        // Same shape but no entry has default:true.
        JSONObject json = new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject().put("pipeline_root", "vector"))
                            .put(new JSONObject().put("pipeline_root", "HotSpotter"))))));
        IAJsonProperties iac = iaConfigWith(json);
        assertNull(iac.getPipelineRoot(TAXY));
    }

    @Test void getPipelineRoot_returnsNullForNullTaxonomy() {
        IAJsonProperties iac = iaConfigWith(vectorDefaultConfig());
        assertNull(iac.getPipelineRoot(null));
    }

    @Test void getActiveMlServiceConfigs_returnsConfigsWhenDefaultIsVector() {
        IAJsonProperties iac = iaConfigWith(vectorDefaultConfig());
        JSONArray confs = iac.getActiveMlServiceConfigs(TAXY);
        assertNotNull(confs);
        assertEquals(1, confs.length());
        assertEquals("msv3", confs.getJSONObject(0).getString("predict_model_id"));
    }

    @Test void getActiveMlServiceConfigs_returnsNullWhenDefaultIsHotspotter() {
        // Even though _mlservice_conf is populated, vector is not the default;
        // the strict invariant says do not route to ml-service.
        IAJsonProperties iac = iaConfigWith(hotspotterDefaultConfig());
        assertNull(iac.getActiveMlServiceConfigs(TAXY));
    }

    @Test void getActiveMlServiceConfigs_returnsNullWhenMlServiceConfMissing() {
        JSONObject json = new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject()
                                .put("default", true)
                                .put("pipeline_root", "vector"))))));
        IAJsonProperties iac = iaConfigWith(json);
        assertNull(iac.getActiveMlServiceConfigs(TAXY));
    }

    @Test void getActiveMlServiceConfigs_returnsNullForEmptyArray() {
        JSONObject json = new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_mlservice_conf", new JSONArray())  // empty
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject()
                                .put("default", true)
                                .put("pipeline_root", "vector"))))));
        IAJsonProperties iac = iaConfigWith(json);
        assertNull(iac.getActiveMlServiceConfigs(TAXY));
    }

    @Test void getActiveMlServiceConfigs_returnsNullForNullTaxonomy() {
        IAJsonProperties iac = iaConfigWith(vectorDefaultConfig());
        assertNull(iac.getActiveMlServiceConfigs(null));
    }

    @Test void getPipelineRoot_returnsNullWhenDefaultEntryHasNoPipelineRoot() {
        // The default-marked entry exists but doesn't set pipeline_root.
        // Per contract: returns null. Caller (the routing layer) treats null
        // the same as "no vector matching" and falls through.
        JSONObject json = new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject()
                                .put("default", true)
                                .put("method", "miewid"))))));  // no pipeline_root
        IAJsonProperties iac = iaConfigWith(json);
        assertNull(iac.getPipelineRoot(TAXY));
    }

    @Test void getActiveMlServiceConfigs_returnsNullWhenMlServiceConfNotAnArray() {
        // _mlservice_conf is present but malformed (object, not array).
        // Defensive: return null rather than throw.
        JSONObject json = new JSONObject()
            .put("Rhincodon", new JSONObject()
                .put("typus", new JSONObject()
                    .put("_mlservice_conf", new JSONObject().put("oops", "not_an_array"))
                    .put("_default", new JSONObject()
                        .put("_id_conf", new JSONArray()
                            .put(new JSONObject()
                                .put("default", true)
                                .put("pipeline_root", "vector"))))));
        IAJsonProperties iac = iaConfigWith(json);
        assertNull(iac.getActiveMlServiceConfigs(TAXY));
    }
}
