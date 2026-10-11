package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

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
}
