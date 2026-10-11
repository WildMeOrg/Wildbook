package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.ecocean.Annotation;
import org.ecocean.User;
import org.ecocean.ia.Task;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Issue #1787 spec §4.0: the /ia gateway resolves the request root before parameters are written. */
class IAGatewayRequestRootTest {

    @Test void umbrellaIdIsReplacedAndTheNewIdReachesPayloadAndResponse() {
        Task umbrella = new Task("u-1");
        umbrella.addParameter(Task.PARAM_RUN_ID, "u-1");
        JSONObject j = new JSONObject().put("taskId", "u-1");
        JSONObject res = new JSONObject().put("taskId", "u-1");
        Task root = IAGateway.resolveGatewayRoot(umbrella, "u-1", j, res);
        assertNotSame(umbrella, root);
        assertNotEquals(umbrella.getId(), root.getId(), "the replacement carries a fresh id, not the umbrella's");
        assertEquals(root.getId(), j.getString("taskId"), "queued payload names the fresh root");
        assertEquals(root.getId(), res.getString("taskId"), "response names the fresh root");
        assertEquals("u-1", umbrella.getParameters().getString(Task.PARAM_RUN_ID),
            "the umbrella's identity is untouched");
    }

    @Test void mediaAssetOnlyRootIsReusedUnchanged() {
        Task loaded = new Task("m-1");
        loaded.addObject(mock(MediaAsset.class));
        JSONObject j = new JSONObject().put("taskId", "m-1");
        JSONObject res = new JSONObject().put("taskId", "m-1");
        assertSame(loaded, IAGateway.resolveGatewayRoot(loaded, "m-1", j, res));
        assertEquals("m-1", j.getString("taskId"));
        assertEquals("m-1", res.getString("taskId"));
    }

    @Test void unknownIdCreatesARootWithThatId() {
        JSONObject j = new JSONObject().put("taskId", "req-9");
        JSONObject res = new JSONObject().put("taskId", "req-9");
        assertEquals("req-9", IAGateway.resolveGatewayRoot(null, "req-9", j, res).getId());
        assertEquals("req-9", j.getString("taskId"));
        assertEquals("req-9", res.getString("taskId"));
    }
    // ---- final fix 3: client-supplied run keys never reach the stored root ----

    static JSONObject requestWithRunKeys() {
        return new JSONObject().put("taskId", "req-1").put("annotationIds", new org.json.JSONArray().put("A1"))
            .put("taskParameters", new JSONObject()
                .put(Task.PARAM_RUN_ID, "OLD-RUN").put(Task.PARAM_OPTION, true)
                .put(Task.PARAM_V2_MATCH, true).put("ibeis.identification", new JSONObject().put("x", 1))
                .put("matchingSetFilter", new JSONObject().put("k", 1)).put("userId", "spoofed"));
    }

    @Test void gatewayTaskParamsNeverCarryTheRunKeys() {
        JSONObject j = requestWithRunKeys();
        JSONObject p = IAGateway.gatewayTaskParams(j, null);
        assertFalse(p.has(Task.PARAM_RUN_ID));
        assertFalse(p.has(Task.PARAM_OPTION));
        assertFalse(p.has(Task.PARAM_V2_MATCH));
        assertFalse(p.has("ibeis.identification"));
        assertFalse(p.has("userId"), "anonymous: the client's user id is dropped as before");
        assertEquals(1, p.getJSONObject("matchingSetFilter").getInt("k"));

        User u = mock(User.class);
        org.mockito.Mockito.when(u.getUUID()).thenReturn("uuid-1");
        org.mockito.Mockito.when(u.getUsername()).thenReturn("someone");
        JSONObject pu = IAGateway.gatewayTaskParams(requestWithRunKeys(), u);
        assertEquals("uuid-1", pu.getString("userId"));
        assertEquals("someone", pu.getString("username"));
        assertFalse(pu.has(Task.PARAM_RUN_ID));
        assertEquals(0, IAGateway.gatewayTaskParams(new JSONObject(), null).length());
    }

    @Test void storedGatewayRootIsNotReplacedAgainByHandleRest() {
        // a fresh replacement root (the loaded task was an umbrella) and a reused media-asset root
        Task umbrella = new Task("u-2");
        umbrella.addParameter(Task.PARAM_RUN_ID, "u-2");
        Task media = new Task("m-2");
        media.addObject(mock(MediaAsset.class));
        for (Task loaded : new Task[] { umbrella, media, null }) {
            String taskId = (loaded == null) ? "req-1" : loaded.getId();
            JSONObject j = requestWithRunKeys().put("taskId", taskId);
            JSONObject res = new JSONObject().put("taskId", taskId);
            Task root = IAGateway.resolveGatewayRoot(loaded, taskId, j, res);
            root.setParameters(IAGateway.gatewayTaskParams(j, null));
            assertFalse(org.ecocean.ia.IA.mustReplaceRequestRoot(root),
                "handleRest keeps the root the response named: " + root.getParameters());
            assertEquals(root.getId(), res.getString("taskId"));
        }
    }

    // ---- final fix 4a: a legacy identify request keeps its annotation-holding task ----

    static Task annotationTask(String id, String runId) {
        Task t = new Task(id);
        Annotation a = new Annotation();
        a.setId("ann-" + id);
        t.addObject(a);
        if (runId != null) t.addParameter(Task.PARAM_RUN_ID, runId);
        return t;
    }

    static JSONObject identifyRequest(String taskId) {
        return new JSONObject().put("taskId", taskId)
            .put("identify", new JSONObject().put("annotationIds", new org.json.JSONArray().put("A1")));
    }

    @Test void annotationTaskWithoutARunIsReusedForAnIdentifyRequest() {
        Task loaded = annotationTask("t-1", null);
        JSONObject j = identifyRequest("t-1");
        JSONObject res = new JSONObject().put("taskId", "t-1");
        assertSame(loaded, IAGateway.resolveGatewayRoot(loaded, "t-1", j, res));
        assertEquals("t-1", j.getString("taskId"));
        assertEquals("t-1", res.getString("taskId"));
    }

    @Test void annotationTaskWithoutARunIsReplacedForANonIdentifyRequest() {
        Task loaded = annotationTask("t-2", null);
        JSONObject j = new JSONObject().put("taskId", "t-2").put("annotationIds", new org.json.JSONArray().put("A1"));
        JSONObject res = new JSONObject().put("taskId", "t-2");
        Task root = IAGateway.resolveGatewayRoot(loaded, "t-2", j, res);
        assertNotSame(loaded, root);
        assertEquals(root.getId(), j.getString("taskId"));
    }

    @Test void runTaskIsReplacedForIdentifyAndNonIdentifyRequests() {
        Task forIdentify = annotationTask("t-3", "RUN-3");
        JSONObject j = identifyRequest("t-3");
        Task root = IAGateway.resolveGatewayRoot(forIdentify, "t-3", j, new JSONObject().put("taskId", "t-3"));
        assertNotSame(forIdentify, root);
        assertEquals(root.getId(), j.getString("taskId"));

        Task forOther = annotationTask("t-4", "RUN-4");
        JSONObject j2 = new JSONObject().put("taskId", "t-4").put("annotationIds", new org.json.JSONArray().put("A1"));
        assertNotSame(forOther, IAGateway.resolveGatewayRoot(forOther, "t-4", j2, new JSONObject().put("taskId", "t-4")));

        Task runNoAnns = new Task("t-5");
        runNoAnns.addParameter(Task.PARAM_RUN_ID, "RUN-5");
        assertNotSame(runNoAnns, IAGateway.resolveGatewayRoot(runNoAnns, "t-5", identifyRequest("t-5"),
            new JSONObject().put("taskId", "t-5")));
    }
}
