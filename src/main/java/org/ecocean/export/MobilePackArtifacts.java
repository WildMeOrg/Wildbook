package org.ecocean.export;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.file.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONObject;

/** Content-addressed cache for administrator-configured, immutable artifacts. */
public final class MobilePackArtifacts {
    private MobilePackArtifacts() {}
    public static String sha256(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return DigestUtils.sha256Hex(in);
        }
    }

    /** HEAD an administrator-released artifact and require the configured length before any expensive work. */
    public static void preflight(String url, long sizeBytes) {
        MobilePackConfiguration.https(url);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)java.net.URI.create(url).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() != sizeBytes)
                throw new IllegalArgumentException(
                    "Embedding model is not available at its download URL with the configured size");
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                "Embedding model is not available at its download URL with the configured size (" +
                ex.getClass().getSimpleName() + ")");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static Path fetch(Path cache, String url, String hash, long maxBytes) throws Exception {
        MobilePackConfiguration.hash(hash);
        MobilePackConfiguration.https(url);
        Files.createDirectories(cache);
        Path target = cache.resolve(hash);
        if (Files.isRegularFile(target) && Files.size(target) <= maxBytes && sha256(target).equals(hash)) return target;
        Path temporary = Files.createTempFile(cache, "download-", ".part");
        HttpURLConnection connection = (HttpURLConnection)java.net.URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(15);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Artifact download failed");
            try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temporary)) {
                byte[] bytes = new byte[65536];
                long total = 0;
                int n;
                while ((n = in.read(bytes)) != -1) {
                    total += n;
                    if (total > maxBytes || System.nanoTime() > deadline || Thread.currentThread().isInterrupted())
                        throw new IOException("Artifact download exceeded limits");
                    out.write(bytes, 0, n);
                }
            }
            if (!sha256(temporary).equals(hash)) throw new IOException("Artifact checksum mismatch");
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            connection.disconnect();
            Files.deleteIfExists(temporary);
        }
    }

    public static void validateDetector(JSONObject config) {
        if (!"detector.onnx".equals(config.getString("modelFile")) ||
            !"yolo".equals(config.getString("outputFormat")) ||
            !"batch_attributes_detections".equals(config.getJSONObject("outputSpec").getString("layout")) ||
            config.getInt("inputChannels") != 3 || config.getJSONArray("classLabels").length() == 0)
            throw new IllegalArgumentException("Detector does not match the mobile YOLO contract");
        if (!java.util.Arrays.asList("yolo11", "yolov8").contains(config.getString("architecture")))
            throw new IllegalArgumentException("Unsupported mobile detector architecture");
        JSONObject spec = config.getJSONObject("outputSpec");
        if (!java.util.Arrays.asList("xyxy", "xywh", "cxcywh").contains(spec.getString("boxFormat")) ||
            !java.util.Arrays.asList("normalized", "absolute").contains(spec.getString("coordinateType")) ||
            !java.util.Arrays.asList("RGB", "BGR").contains(config.getString("channelOrder")))
            throw new IllegalArgumentException("Unsupported detector preprocessing or coordinates");
        JSONObject normalization = config.getJSONObject("normalize");
        for (String field : java.util.Arrays.asList("mean", "std")) {
            org.json.JSONArray values = normalization.getJSONArray(field);
            if (values.length() != 3) throw new IllegalArgumentException("Detector normalization needs three channels");
            for (int i = 0; i < 3; i++) {
                double value = values.getDouble(i);
                if (!Double.isFinite(value) || ("std".equals(field) && value <= 0))
                    throw new IllegalArgumentException("Invalid detector normalization");
            }
        }
        double scale = normalization.getDouble("scale");
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid detector scale");
        for (String field : java.util.Arrays.asList("confidenceThreshold", "nmsThreshold")) {
            double value = config.getDouble(field);
            if (!Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Invalid detector threshold");
        }
        if (config.getInt("maxDetections") <= 0 || config.getInt("maxDetections") > 1000)
            throw new IllegalArgumentException("Invalid detector maximum");
        for (Object label : config.getJSONArray("classLabels"))
            if (!(label instanceof String) || ((String)label).isBlank())
                throw new IllegalArgumentException("Invalid detector class label");
        if (config.getJSONArray("inputSize").length() != 2 ||
            config.getJSONArray("inputSize").getInt(0) <= 0 || config.getJSONArray("inputSize").getInt(1) <= 0)
            throw new IllegalArgumentException("Invalid detector dimensions");
    }
}
