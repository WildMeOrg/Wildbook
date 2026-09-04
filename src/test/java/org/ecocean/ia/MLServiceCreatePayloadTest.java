package org.ecocean.ia;

import java.nio.file.Paths;

import org.ecocean.Annotation;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MLServiceCreatePayloadTest {
    static Annotation annotationWithPath(String assetPath) {
        LocalAssetStore store = new LocalAssetStore("test-store",
            Paths.get("/usr/local/tomcat/webapps/wildbook_data_dir"),
            "http://localhost/wildbook_data_dir", false);
        // construct with a valid path, then swap in the target path afterward: the
        // constructor itself resolves the path (store.hashCode()), so a stale
        // absolute path can only exist on assets hydrated from the database --
        // which is exactly the state this mimics.
        MediaAsset ma = new MediaAsset(store, new JSONObject().put("path", "placeholder.jpg"));
        ma.setParameters(new JSONObject().put("path", assetPath));
        JSONObject fparams = new JSONObject().put("x", 0).put("y", 0).put("width", 10).put("height",
            10).put("theta", 0.0d);
        Feature ft = new Feature(new FeatureType("org.ecocean.boundingBox"), fparams);

        ma.addFeature(ft);
        return new Annotation("Testus testus", ft, "test");
    }

    // historic installs stored absolute asset paths; after a server move they fall
    // outside the current store root and webURL() throws IllegalArgumentException
    // ("Path not under given root") from LocalAssetStore.checkPath(). createPayload()
    // must convert that to IAException so batch callers (Embedding.catchUpEmbeddings)
    // record and skip the annotation instead of aborting the whole sweep.
    @Test void createPayloadStaleAbsolutePathThrowsIAException() {
        Annotation ann = annotationWithPath(
            "/var/lib/tomcat7/webapps/wildbook_data_dir/8/9/some-uuid/frame00484.jpg");
        MLService mls = new MLService();
        JSONObject conf = new JSONObject().put("api_endpoint", "http://ml.example.com").put(
            "model_id", "test-v1");
        IAException ex = assertThrows(IAException.class, () -> mls.createPayload(ann, conf));

        assertTrue(ex.getMessage().contains("cannot resolve image"),
            "message should explain the unresolvable image: " + ex.getMessage());
    }

    @Test void createPayloadRelativePathBuildsPayload() throws IAException {
        Annotation ann = annotationWithPath("8/9/some-uuid/frame00484.jpg");
        MLService mls = new MLService();
        JSONObject conf = new JSONObject().put("api_endpoint", "http://ml.example.com").put(
            "model_id", "test-v1");
        JSONObject payload = mls.createPayload(ann, conf);

        assertEquals("http://localhost/wildbook_data_dir/8/9/some-uuid/frame00484.jpg",
            payload.get("image_uri").toString());
        assertFalse(payload.has("api_endpoint"), "api_endpoint must be stripped from payload");
        assertEquals("test-v1", payload.getString("model_id"));
    }
}
