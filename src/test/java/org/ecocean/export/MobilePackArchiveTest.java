package org.ecocean.export;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilePackArchiveTest {
    @TempDir Path directory;
    static JSONObject profile() {
        JSONObject artifact = new JSONObject().put("downloadUrl", "https://assets.example.org/model.onnx")
            .put("sha256", "a".repeat(64)).put("sizeBytes", 100);
        JSONObject model = new JSONObject(artifact.toString()).put("name", "miewid")
            .put("version", "4.1.0").put("method", "miewid-msv4.1").put("methodVersion", "raw")
            .put("format", "onnx").put("embeddingDim", 2152)
            .put("inputSize", new JSONArray("[440,440]"))
            .put("normalize", new JSONObject("{\"mean\":[0,0,0],\"std\":[1,1,1]}"));
        JSONObject detector = new JSONObject(artifact.toString()).put("id", "horse-face").put("version", "1.0.0")
            .put("configUrl", "https://assets.example.org/detector.json").put("configSha256", "b".repeat(64));
        return new JSONObject().put("species", "horse").put("featureClass", "horse+face")
            .put("mobileDetector", detector).put("embeddingModel", model);
    }

    private Path config() throws Exception {
        JSONObject config = new JSONObject().put("modelFile", "detector.onnx").put("architecture", "yolo11")
            .put("outputFormat", "yolo").put("inputChannels", 3).put("channelOrder", "RGB")
            .put("classLabels", new JSONArray().put("horse")).put("inputSize", new JSONArray().put(640).put(640))
            .put("normalize", new JSONObject().put("mean", new JSONArray().put(0).put(0).put(0))
                .put("std", new JSONArray().put(1).put(1).put(1)).put("scale", 1.0 / 255))
            .put("confidenceThreshold", 0.25).put("nmsThreshold", 0.45).put("maxDetections", 100)
            .put("outputSpec", new JSONObject().put("layout", "batch_attributes_detections")
                .put("boxFormat", "cxcywh").put("coordinateType", "absolute"));
        return Files.writeString(directory.resolve("detector.json"), config.toString());
    }

    @Test void detectorRejectsUnsupportedPreprocessingBeforePublication() throws Exception {
        JSONObject detector = new JSONObject(Files.readString(config()));
        MobilePackArtifacts.validateDetector(detector);
        detector.getJSONObject("normalize").put("std", new JSONArray().put(1).put(0).put(1));
        assertThrows(IllegalArgumentException.class, () -> MobilePackArtifacts.validateDetector(detector));
    }

    @Test void archiveUsesLittleEndianVectorsAndExactOffsetsAndPinnedDependencies() throws Exception {
        Path detector = Files.write(directory.resolve("detector.onnx"), new byte[] {1, 2, 3});
        List<MobilePackArchive.Individual> individuals = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MobilePackArchive.Individual individual = new MobilePackArchive.Individual("animal-" + i, "Example");
            float[] vector = new float[2152]; vector[0] = i + 1.25f;
            individual.embeddings.add(vector);
            individual.photos.add(Files.write(directory.resolve("photo-" + i), new byte[] {4, 5}));
            individuals.add(individual);
        }
        Path output = directory.resolve("pack.zip");
        MobilePackArchive.write(output, new MobilePackConfiguration(profile()), "Example",
            "https://wildbook.example.org", individuals, detector, config());
        try (ZipFile zip = new ZipFile(output.toFile())) {
            byte[] binary = zip.getInputStream(zip.getEntry("embeddings/embeddings.bin")).readAllBytes();
            assertEquals(2 * 2152 * 4, binary.length);
            assertEquals(1.25f, ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).getFloat());
            assertEquals(2.25f, ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).getFloat(2152 * 4));
            JSONObject index = new JSONObject(new String(zip.getInputStream(zip.getEntry("embeddings/index.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(1, index.getJSONArray("individuals").getJSONObject(1).getInt("embeddingOffset"));
            JSONObject manifest = new JSONObject(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("a".repeat(64), manifest.getJSONObject("embeddingModel").getString("sha256"));
            assertFalse(manifest.has("exportedBy")); assertFalse(manifest.has("searchQuery"));
            assertNotNull(zip.getEntry("reference_photos/animal-0/ref_0.jpg"));
        }
    }

    @Test void rejectsWrongDimensionsAndUnsafeIdsAndNonMobileModels() {
        assertThrows(IllegalArgumentException.class, () -> new MobilePackArchive.Individual("../unsafe", "x"));
        JSONObject wrong = profile(); wrong.getJSONObject("embeddingModel").put("embeddingDim", 1536);
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(wrong));
        JSONObject insecure = profile(); insecure.getJSONObject("mobileDetector").put("downloadUrl", "http://example.org/model");
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(insecure));
    }

    @Test void rejectsEmptyPacks() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> MobilePackArchive.write(directory.resolve("empty.zip"),
            new MobilePackConfiguration(profile()), "Example", "https://wildbook.example.org", Collections.emptyList(),
            directory.resolve("model"), directory.resolve("config")));
    }

    @Test void identitiesAreStableAndScopedToSource() throws Exception {
        String one = MobilePackJobs.scopedIdentity("https://one.example", "A-001");
        assertEquals(one, MobilePackJobs.scopedIdentity("https://one.example", "A-001"));
        assertNotEquals(one, MobilePackJobs.scopedIdentity("https://two.example", "A-001"));
        assertTrue(one.matches("wb-[a-f0-9]{64}"));
    }
}
