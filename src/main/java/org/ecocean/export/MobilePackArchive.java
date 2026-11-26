package org.ecocean.export;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Streams the existing mobile v1 ZIP format; contains no database or cloud dependencies. */
public final class MobilePackArchive {
    public static final class Individual {
        public final String id;
        public final String name;
        public final List<float[]> embeddings = new ArrayList<>();
        public final List<Path> photos = new ArrayList<>();
        public Individual(String id, String name) {
            if (!id.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Unsafe identity");
            this.id = id;
            this.name = name == null ? id : name;
        }
    }

    public static JSONObject write(Path output, MobilePackConfiguration profile, String displayName,
        String instanceUrl, List<Individual> individuals, Path detector, Path detectorConfig) throws Exception {
        if (individuals.isEmpty()) throw new IllegalArgumentException("No compatible identified annotations");
        JSONObject config = new JSONObject(Files.readString(detectorConfig));
        MobilePackArtifacts.validateDetector(config);
        JSONObject model = profile.model();
        model.remove("downloadUrl"); model.remove("sizeBytes"); model.remove("format");
        // Preserve the explicit source method/version alongside the mobile release version.
        JSONArray index = new JSONArray();
        int offset = 0;
        Set<String> identities = new HashSet<>();
        Path binary = Files.createTempFile(output.getParent(), "embeddings-", ".bin");
        try {
            try (OutputStream stream = Files.newOutputStream(binary)) {
                for (Individual individual : individuals) {
                    if (!identities.add(individual.id) || individual.embeddings.isEmpty() || individual.photos.isEmpty())
                        throw new IllegalArgumentException("Each unique individual needs embeddings and a reference photo");
                    JSONArray photos = new JSONArray();
                    for (int n = 0; n < individual.photos.size(); n++) photos.put("ref_" + n + ".jpg");
                    index.put(new JSONObject().put("id", individual.id).put("name", individual.name)
                        .put("embeddingOffset", offset).put("embeddingCount", individual.embeddings.size())
                        .put("referencePhotos", photos));
                    for (float[] vector : individual.embeddings) {
                        if (vector.length != 2152) throw new IllegalArgumentException("Wrong embedding dimension");
                        ByteBuffer bytes = ByteBuffer.allocate(2152 * 4).order(ByteOrder.LITTLE_ENDIAN);
                        double norm = 0;
                        for (float value : vector) {
                            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite embedding");
                            bytes.putFloat(value); norm += (double)value * value;
                        }
                        if (norm == 0) throw new IllegalArgumentException("Zero embedding");
                        stream.write(bytes.array());
                    }
                    offset += individual.embeddings.size();
                }
            }
            JSONObject checksums = new JSONObject().put("embeddings/embeddings.bin", MobilePackArtifacts.sha256(binary))
                .put("models/detector.onnx", MobilePackArtifacts.sha256(detector))
                .put("config/detector.json", MobilePackArtifacts.sha256(detectorConfig));
            JSONObject manifest = new JSONObject().put("formatVersion", "1.0")
                .put("species", profile.species()).put("featureClass", profile.featureClass())
                .put("displayName", displayName).put("wildbookInstanceUrl", instanceUrl)
                .put("exportDate", java.time.Instant.now().toString()).put("individualCount", individuals.size())
                .put("embeddingCount", offset).put("embeddingDim", 2152).put("embeddingModel", model)
                .put("detectorModel", new JSONObject().put("filename", "detector.onnx").put("configFile", "config/detector.json"))
                .put("checksums", checksums);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                json(zip, "manifest.json", manifest);
                json(zip, "embeddings/index.json", new JSONObject().put("formatVersion", "1.0")
                    .put("generatedWith", model.getString("name")).put("individuals", index));
                file(zip, "embeddings/embeddings.bin", binary);
                file(zip, "models/detector.onnx", detector);
                file(zip, "config/detector.json", detectorConfig);
                for (Individual individual : individuals) {
                    for (int n = 0; n < individual.photos.size(); n++)
                        file(zip, "reference_photos/" + individual.id + "/ref_" + n + ".jpg", individual.photos.get(n));
                }
            }
            return manifest;
        } finally { Files.deleteIfExists(binary); }
    }

    private static void json(ZipOutputStream zip, String name, JSONObject value) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static void file(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); Files.copy(file, zip); zip.closeEntry();
    }
}
