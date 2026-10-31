package org.ecocean.export;

import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;

/** An explicitly released mobile pipeline, selected by taxonomy and feature class in IA.json. */
public final class MobilePackConfiguration {
    private final JSONObject value;

    public MobilePackConfiguration(JSONObject source) {
        value = new JSONObject(source.toString());
        required(value, "species");
        required(value, "featureClass");
        JSONObject detector = value.getJSONObject("mobileDetector");
        required(detector, "id");
        required(detector, "version");
        artifact(detector);
        https(detector.getString("configUrl"));
        hash(detector.getString("configSha256"));
        JSONObject model = value.getJSONObject("embeddingModel");
        artifact(model);
        required(model, "name");
        required(model, "method");
        required(model, "methodVersion");
        if (!model.getString("version").matches("v?[0-9]+\\.[0-9]+(?:\\.[0-9]+)?") ||
            !"onnx".equals(model.getString("format")) || model.getInt("embeddingDim") != 2152)
            throw new IllegalArgumentException("Unsupported mobile embedding model");
        JSONArray size = model.getJSONArray("inputSize");
        if (size.length() != 2 || size.getInt(0) != 440 || size.getInt(1) != 440)
            throw new IllegalArgumentException("Mobile MiewID requires 440 x 440 input");
        JSONObject norm = model.getJSONObject("normalize");
        for (String key : new String[] {"mean", "std"}) {
            JSONArray values = norm.getJSONArray(key);
            if (values.length() != 3) throw new IllegalArgumentException("Invalid normalization");
            for (int i = 0; i < 3; i++) {
                double n = values.getDouble(i);
                if (!Double.isFinite(n) || ("std".equals(key) && n <= 0))
                    throw new IllegalArgumentException("Invalid normalization");
            }
        }
    }

    public JSONObject json() { return new JSONObject(value.toString()); }
    public JSONObject model() { return json().getJSONObject("embeddingModel"); }
    public JSONObject detector() { return json().getJSONObject("mobileDetector"); }
    public String species() { return value.getString("species"); }
    public String featureClass() { return value.getString("featureClass"); }

    public static void artifact(JSONObject artifact) {
        https(artifact.getString("downloadUrl"));
        hash(artifact.getString("sha256"));
        if (artifact.getLong("sizeBytes") <= 0)
            throw new IllegalArgumentException("Artifact size must be positive");
    }

    public static URI https(String text) {
        URI uri = URI.create(text);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null ||
            uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
            throw new IllegalArgumentException("Configure a permanent HTTPS artifact URL");
        return uri;
    }

    public static void hash(String hash) {
        if (!hash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Expected lowercase SHA-256");
    }

    private static void required(JSONObject value, String key) {
        if (value.getString(key).trim().isEmpty()) throw new IllegalArgumentException("Missing " + key);
    }
}
