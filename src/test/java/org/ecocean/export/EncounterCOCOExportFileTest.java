package org.ecocean.export;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncounterCOCOExportFileTest {

    @Test
    void testBuildsCOCOStructure() throws Exception {
        // Create mock objects
        Shepherd shepherd = mock(Shepherd.class);

        // Create a mock MediaAsset
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getUUID()).thenReturn("test-media-uuid");
        when(ma.getWidth()).thenReturn(800.0);
        when(ma.getHeight()).thenReturn(600.0);
        when(ma.webURL()).thenReturn(null); // Skip actual image download in test

        // Create a mock Feature with bbox
        Feature feature = mock(Feature.class);
        JSONObject params = new JSONObject();
        params.put("x", 100);
        params.put("y", 200);
        params.put("width", 300);
        params.put("height", 400);
        params.put("theta", 0.5);
        when(feature.getParameters()).thenReturn(params);

        // Create a mock Annotation
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn("test-ann-uuid");
        when(ann.getIAClass()).thenReturn("whale_shark");
        when(ann.getBbox()).thenReturn(new int[]{100, 200, 300, 400});
        when(ann.getMediaAsset()).thenReturn(ma);
        when(ann.getViewpoint()).thenReturn("left");
        when(ann.getTheta()).thenReturn(0.5);

        // Create a mock MarkedIndividual
        MarkedIndividual ind = mock(MarkedIndividual.class);
        when(ind.getId()).thenReturn("test-individual-uuid");
        when(ind.getDisplayName()).thenReturn("Stumpy");

        // Create a mock Encounter
        Encounter enc = mock(Encounter.class);
        ArrayList<Annotation> annotations = new ArrayList<>();
        annotations.add(ann);
        when(enc.getAnnotations()).thenReturn(annotations);
        when(enc.getIndividual()).thenReturn(ind);

        List<Encounter> encounters = new ArrayList<>();
        encounters.add(enc);

        // Run export
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, shepherd);
        exportFile.writeTo(baos);

        // Parse ZIP and extract annotations.json
        byte[] zipBytes = baos.toByteArray();
        assertTrue(zipBytes.length > 0, "Export should produce output");

        String jsonContent = extractJsonFromZip(zipBytes);
        assertNotNull(jsonContent, "Should contain annotations.json");

        // Verify JSON structure
        JSONObject coco = new JSONObject(jsonContent);
        assertTrue(coco.has("info"));
        assertTrue(coco.has("licenses"));
        assertTrue(coco.has("categories"));
        assertTrue(coco.has("images"));
        assertTrue(coco.has("annotations"));

        // Verify categories
        JSONArray categories = coco.getJSONArray("categories");
        assertEquals(1, categories.length());
        assertEquals("whale_shark", categories.getJSONObject(0).getString("name"));

        // Verify annotations
        JSONArray anns = coco.getJSONArray("annotations");
        assertEquals(1, anns.length());
        JSONObject annJson = anns.getJSONObject(0);
        assertEquals("left", annJson.getString("viewpoint"));
        assertEquals(0.5, annJson.getDouble("theta"), 0.001);
        assertEquals("test-individual-uuid", annJson.getString("individual_uuid"));
        assertEquals("Stumpy", annJson.getString("name"));

        // Verify individual_id_mapping in info
        JSONObject info = coco.getJSONObject("info");
        assertTrue(info.has("individual_id_mapping"));
        JSONObject mapping = info.getJSONObject("individual_id_mapping");
        assertTrue(mapping.has("test-individual-uuid"));
    }

    @Test
    void testSkipsAnnotationsWithoutIaClass() throws Exception {
        Shepherd shepherd = mock(Shepherd.class);

        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getUUID()).thenReturn("test-media-uuid");
        when(ma.getWidth()).thenReturn(800.0);
        when(ma.getHeight()).thenReturn(600.0);

        // Annotation without iaClass
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn("test-ann-uuid");
        when(ann.getIAClass()).thenReturn(null); // No iaClass
        when(ann.getBbox()).thenReturn(new int[]{100, 200, 300, 400});
        when(ann.getMediaAsset()).thenReturn(ma);

        Encounter enc = mock(Encounter.class);
        ArrayList<Annotation> annotations = new ArrayList<>();
        annotations.add(ann);
        when(enc.getAnnotations()).thenReturn(annotations);

        List<Encounter> encounters = new ArrayList<>();
        encounters.add(enc);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, shepherd);
        exportFile.writeTo(baos);

        // Extract and verify
        String jsonContent = extractJsonFromZip(baos.toByteArray());
        JSONObject coco = new JSONObject(jsonContent);

        // Should have no annotations since iaClass was null
        assertEquals(0, coco.getJSONArray("annotations").length());
        assertEquals(0, coco.getJSONArray("categories").length());
    }

    private String extractJsonFromZip(byte[] zipBytes) throws Exception {
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.getName().endsWith("instances.json")) {
                    return new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
