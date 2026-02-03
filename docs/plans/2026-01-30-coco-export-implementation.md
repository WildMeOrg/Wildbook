# COCO Export Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Export Wildbook encounter search results to COCO format (tar.gz with images + annotations.json) for MIEW-ID training.

**Architecture:** Servlet receives encounter search query, delegates to export file class which iterates encounters/annotations, builds COCO JSON structure, and streams tar.gz with full original images and annotations.json.

**Tech Stack:** Java servlet, org.json for JSON, Apache Commons Compress for tar.gz, existing Wildbook patterns (Shepherd, EncounterQueryProcessor, MediaAsset).

---

## Task 1: Create EncounterCOCOExportFile Core Class

**Files:**
- Create: `src/main/java/org/ecocean/export/EncounterCOCOExportFile.java`

**Step 1: Create the skeleton class with constructor**

```java
package org.ecocean.export;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EncounterCOCOExportFile {
    private final List<Encounter> encounters;
    private final Shepherd shepherd;

    public EncounterCOCOExportFile(List<Encounter> encounters, Shepherd shepherd) {
        this.encounters = encounters;
        this.shepherd = shepherd;
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        // TODO: implement
    }
}
```

**Step 2: Verify file compiles**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile -q -pl . -am 2>&1 | head -30`
Expected: BUILD SUCCESS (or no errors related to this file)

**Step 3: Commit**

```bash
git add src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
git commit -m "feat: add EncounterCOCOExportFile skeleton"
```

---

## Task 2: Implement Data Collection Methods

**Files:**
- Modify: `src/main/java/org/ecocean/export/EncounterCOCOExportFile.java`

**Step 1: Add helper methods to collect unique MediaAssets and build category/individual maps**

Add these methods to the class:

```java
    /**
     * Collects all unique MediaAssets from valid annotations.
     * An annotation is valid if it has a non-null/non-empty iaClass and a valid bbox.
     */
    private Map<String, MediaAsset> collectUniqueMediaAssets() {
        Map<String, MediaAsset> mediaAssetMap = new LinkedHashMap<>();
        for (Encounter enc : encounters) {
            if (enc.getAnnotations() == null) continue;
            for (Annotation ann : enc.getAnnotations()) {
                if (!isValidAnnotation(ann)) continue;
                MediaAsset ma = ann.getMediaAsset();
                if (ma != null && ma.getUUID() != null) {
                    mediaAssetMap.putIfAbsent(ma.getUUID(), ma);
                }
            }
        }
        return mediaAssetMap;
    }

    /**
     * Builds a map from iaClass to sequential category ID.
     */
    private Map<String, Integer> buildCategoryMap() {
        Set<String> iaClasses = new LinkedHashSet<>();
        for (Encounter enc : encounters) {
            if (enc.getAnnotations() == null) continue;
            for (Annotation ann : enc.getAnnotations()) {
                String iaClass = ann.getIAClass();
                if (iaClass != null && !iaClass.trim().isEmpty()) {
                    iaClasses.add(iaClass.trim());
                }
            }
        }
        Map<String, Integer> categoryMap = new LinkedHashMap<>();
        int id = 1;
        for (String iaClass : iaClasses) {
            categoryMap.put(iaClass, id++);
        }
        return categoryMap;
    }

    /**
     * Builds a map from MarkedIndividual UUID to sequential integer ID.
     */
    private Map<String, Integer> buildIndividualIdMap() {
        Set<String> individualIds = new TreeSet<>(); // TreeSet for sorted, deterministic order
        for (Encounter enc : encounters) {
            MarkedIndividual ind = enc.getIndividual();
            if (ind != null && ind.getId() != null) {
                individualIds.add(ind.getId());
            }
        }
        Map<String, Integer> idMap = new LinkedHashMap<>();
        int idx = 0;
        for (String indId : individualIds) {
            idMap.put(indId, idx++);
        }
        return idMap;
    }

    /**
     * Checks if an annotation is valid for COCO export.
     */
    private boolean isValidAnnotation(Annotation ann) {
        if (ann == null) return false;
        String iaClass = ann.getIAClass();
        if (iaClass == null || iaClass.trim().isEmpty()) return false;
        int[] bbox = ann.getBbox();
        if (bbox == null || bbox.length < 4) return false;
        if (bbox[2] <= 0 || bbox[3] <= 0) return false; // width/height must be positive
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null || ma.getUUID() == null) return false;
        return true;
    }
```

**Step 2: Verify file compiles**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile -q -pl . -am 2>&1 | head -30`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
git commit -m "feat: add data collection methods for COCO export"
```

---

## Task 3: Implement JSON Building Methods

**Files:**
- Modify: `src/main/java/org/ecocean/export/EncounterCOCOExportFile.java`

**Step 1: Add methods to build COCO JSON structures**

Add these methods:

```java
    /**
     * Builds the "info" section of COCO JSON.
     */
    private JSONObject buildInfo(Map<String, Integer> individualIdMap) {
        JSONObject info = new JSONObject();
        info.put("description", "Wildbook COCO Export");
        info.put("version", "1.0");
        info.put("date_created", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));
        info.put("contributor", "Wildbook");

        JSONObject idMapping = new JSONObject();
        for (Map.Entry<String, Integer> entry : individualIdMap.entrySet()) {
            idMapping.put(entry.getKey(), entry.getValue());
        }
        info.put("individual_id_mapping", idMapping);
        return info;
    }

    /**
     * Builds the "licenses" section of COCO JSON.
     */
    private JSONArray buildLicenses() {
        JSONArray licenses = new JSONArray();
        JSONObject license = new JSONObject();
        license.put("id", 1);
        license.put("name", "See Wildbook Terms");
        license.put("url", "");
        licenses.put(license);
        return licenses;
    }

    /**
     * Builds the "categories" section of COCO JSON.
     */
    private JSONArray buildCategories(Map<String, Integer> categoryMap) {
        JSONArray categories = new JSONArray();
        for (Map.Entry<String, Integer> entry : categoryMap.entrySet()) {
            JSONObject cat = new JSONObject();
            cat.put("id", entry.getValue());
            cat.put("name", entry.getKey());
            cat.put("supercategory", "animal");
            categories.put(cat);
        }
        return categories;
    }

    /**
     * Builds an image object for COCO JSON.
     */
    private JSONObject buildImageObject(MediaAsset ma, int imageId) {
        JSONObject img = new JSONObject();
        img.put("id", imageId);
        img.put("file_name", ma.getUUID() + ".jpg");
        img.put("width", (int) ma.getWidth());
        img.put("height", (int) ma.getHeight());
        img.put("uuid", ma.getUUID());

        // Optional fields
        if (ma.getDateTime() != null) {
            img.put("date_captured", ma.getDateTime().toString());
        }
        Double lat = ma.getLatitude();
        Double lon = ma.getLongitude();
        if (lat != null) {
            img.put("gps_lat_captured", lat);
        }
        if (lon != null) {
            img.put("gps_lon_captured", lon);
        }
        return img;
    }

    /**
     * Builds an annotation object for COCO JSON.
     */
    private JSONObject buildAnnotationObject(Annotation ann, int annotationId, int imageId,
                                              Map<String, Integer> categoryMap,
                                              Map<String, Integer> individualIdMap,
                                              Encounter enc) {
        JSONObject annJson = new JSONObject();
        annJson.put("id", annotationId);
        annJson.put("image_id", imageId);

        String iaClass = ann.getIAClass().trim();
        annJson.put("category_id", categoryMap.get(iaClass));

        int[] bbox = ann.getBbox();
        JSONArray bboxArray = new JSONArray();
        bboxArray.put(bbox[0]); // x
        bboxArray.put(bbox[1]); // y
        bboxArray.put(bbox[2]); // width
        bboxArray.put(bbox[3]); // height
        annJson.put("bbox", bboxArray);

        int area = bbox[2] * bbox[3];
        annJson.put("area", area);

        // Segmentation: rectangle polygon from bbox
        JSONArray segmentation = new JSONArray();
        JSONArray polygon = new JSONArray();
        int x = bbox[0], y = bbox[1], w = bbox[2], h = bbox[3];
        polygon.put(x);     polygon.put(y);
        polygon.put(x + w); polygon.put(y);
        polygon.put(x + w); polygon.put(y + h);
        polygon.put(x);     polygon.put(y + h);
        segmentation.put(polygon);
        annJson.put("segmentation", segmentation);

        annJson.put("iscrowd", 0);

        // Custom fields
        annJson.put("uuid", ann.getId());

        String viewpoint = ann.getViewpoint();
        if (viewpoint != null) {
            annJson.put("viewpoint", viewpoint);
        }

        annJson.put("theta", ann.getTheta());

        // Individual info
        MarkedIndividual ind = enc.getIndividual();
        JSONArray individualIds = new JSONArray();
        if (ind != null && ind.getId() != null && individualIdMap.containsKey(ind.getId())) {
            individualIds.put(individualIdMap.get(ind.getId()));
            annJson.put("individual_uuid", ind.getId());
            String displayName = ind.getDisplayName();
            if (displayName != null) {
                annJson.put("name", displayName);
            } else {
                annJson.put("name", JSONObject.NULL);
            }
        } else {
            annJson.put("individual_uuid", JSONObject.NULL);
            annJson.put("name", JSONObject.NULL);
        }
        annJson.put("individual_ids", individualIds);

        return annJson;
    }
```

**Step 2: Verify file compiles**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile -q -pl . -am 2>&1 | head -30`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
git commit -m "feat: add COCO JSON building methods"
```

---

## Task 4: Implement writeTo Method

**Files:**
- Modify: `src/main/java/org/ecocean/export/EncounterCOCOExportFile.java`

**Step 1: Implement the main writeTo method**

Replace the TODO writeTo method with:

```java
    public void writeTo(OutputStream outputStream) throws IOException {
        // Collect data
        Map<String, MediaAsset> mediaAssetMap = collectUniqueMediaAssets();
        Map<String, Integer> categoryMap = buildCategoryMap();
        Map<String, Integer> individualIdMap = buildIndividualIdMap();

        // Assign image IDs
        Map<String, Integer> mediaAssetToImageId = new LinkedHashMap<>();
        int imageId = 1;
        for (String uuid : mediaAssetMap.keySet()) {
            mediaAssetToImageId.put(uuid, imageId++);
        }

        // Build JSON arrays
        JSONArray imagesArray = new JSONArray();
        for (Map.Entry<String, MediaAsset> entry : mediaAssetMap.entrySet()) {
            MediaAsset ma = entry.getValue();
            int imgId = mediaAssetToImageId.get(entry.getKey());
            imagesArray.put(buildImageObject(ma, imgId));
        }

        JSONArray annotationsArray = new JSONArray();
        int annotationId = 1;
        for (Encounter enc : encounters) {
            if (enc.getAnnotations() == null) continue;
            for (Annotation ann : enc.getAnnotations()) {
                if (!isValidAnnotation(ann)) continue;
                MediaAsset ma = ann.getMediaAsset();
                int imgId = mediaAssetToImageId.get(ma.getUUID());
                annotationsArray.put(buildAnnotationObject(ann, annotationId++, imgId,
                    categoryMap, individualIdMap, enc));
            }
        }

        // Build complete COCO JSON
        JSONObject coco = new JSONObject();
        coco.put("info", buildInfo(individualIdMap));
        coco.put("licenses", buildLicenses());
        coco.put("categories", buildCategories(categoryMap));
        coco.put("images", imagesArray);
        coco.put("annotations", annotationsArray);

        // Write tar.gz
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(outputStream);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {

            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            // Write annotations.json
            byte[] jsonBytes = coco.toString(2).getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry jsonEntry = new TarArchiveEntry("coco-export/annotations.json");
            jsonEntry.setSize(jsonBytes.length);
            tarOut.putArchiveEntry(jsonEntry);
            tarOut.write(jsonBytes);
            tarOut.closeArchiveEntry();

            // Write images
            for (Map.Entry<String, MediaAsset> entry : mediaAssetMap.entrySet()) {
                MediaAsset ma = entry.getValue();
                try {
                    byte[] imageBytes = getImageBytes(ma);
                    if (imageBytes != null) {
                        TarArchiveEntry imgEntry = new TarArchiveEntry(
                            "coco-export/images/" + ma.getUUID() + ".jpg");
                        imgEntry.setSize(imageBytes.length);
                        tarOut.putArchiveEntry(imgEntry);
                        tarOut.write(imageBytes);
                        tarOut.closeArchiveEntry();
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to export image " + ma.getUUID() + ": " + e.getMessage());
                }
            }

            tarOut.finish();
        }
    }

    /**
     * Retrieves image bytes from a MediaAsset.
     */
    private byte[] getImageBytes(MediaAsset ma) throws IOException {
        URL url = ma.webURL();
        if (url == null) return null;

        BufferedImage image = ImageIO.read(url);
        if (image == null) return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
```

**Step 2: Verify file compiles**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile -q -pl . -am 2>&1 | head -30`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
git commit -m "feat: implement writeTo method for COCO tar.gz export"
```

---

## Task 5: Create EncounterSearchExportCOCO Servlet

**Files:**
- Create: `src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java`

**Step 1: Create the servlet class**

```java
package org.ecocean.servlet.export;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.EncounterQueryProcessor;
import org.ecocean.EncounterQueryResult;
import org.ecocean.export.EncounterCOCOExportFile;
import org.ecocean.security.HiddenEncReporter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class EncounterSearchExportCOCO extends HttpServlet {

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSearchExportCOCO.class");

        myShepherd.beginDBTransaction();

        try {
            // Process query
            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(
                myShepherd, request, "year descending, month descending, day descending");
            Vector<?> rEncounters = queryResult.getResult();

            // Filter hidden encounters
            HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);

            // Convert to list, excluding hidden
            List<Encounter> encounters = new ArrayList<>();
            for (Object obj : rEncounters) {
                Encounter enc = (Encounter) obj;
                if (!hiddenData.contains(enc)) {
                    encounters.add(enc);
                }
            }

            // Set response headers
            response.setContentType("application/gzip");
            response.setHeader("Content-Disposition", "attachment; filename=\"wildbook-coco-export.tar.gz\"");

            // Write export
            OutputStream out = response.getOutputStream();
            EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, myShepherd);
            exportFile.writeTo(out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(ServletUtilities.getHeader(request));
            out.println("<html><body><p><strong>Error encountered</strong></p>");
            out.println("<p>Error: " + e.getMessage() + "</p>");
            out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportCOCO servlet</p></body></html>");
            out.println(ServletUtilities.getFooter(context));
            out.close();
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }
}
```

**Step 2: Verify file compiles**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile -q -pl . -am 2>&1 | head -30`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
git commit -m "feat: add EncounterSearchExportCOCO servlet"
```

---

## Task 6: Register Servlet in web.xml

**Files:**
- Modify: `src/main/webapp/WEB-INF/web.xml`

**Step 1: Add security rule for the export endpoint**

Find the "Export Operations" section (around line 127) and add after the last export entry (around line 146):

```xml
				/EncounterSearchExportCOCO = authc, roles[researcher]
```

**Step 2: Add servlet definition and mapping**

Find the servlet definitions section (around line 1665 where EncounterSearchExportExcelFile is defined) and add nearby:

```xml
	<servlet>
		<servlet-name>EncounterSearchExportCOCO</servlet-name>
		<servlet-class>org.ecocean.servlet.export.EncounterSearchExportCOCO</servlet-class>
	</servlet>
	<servlet-mapping>
		<servlet-name>EncounterSearchExportCOCO</servlet-name>
		<url-pattern>/EncounterSearchExportCOCO</url-pattern>
	</servlet-mapping>
```

**Step 3: Verify XML is valid**

Run: `cd /mnt/c/Wildbook-clean2 && mvn validate -q 2>&1 | head -10`
Expected: No XML parsing errors

**Step 4: Commit**

```bash
git add src/main/webapp/WEB-INF/web.xml
git commit -m "feat: register COCO export servlet in web.xml"
```

---

## Task 7: Add Export Link to UI

**Files:**
- Modify: `src/main/webapp/encounters/exportSearchResults.jsp`
- Modify: `src/main/resources/bundles/en/exportSearchResults.properties`

**Step 1: Add the COCO export link to exportSearchResults.jsp**

Find line 154-155 (after the Encounter Annotation Export link) and add after it:

```jsp

<p><br>COCO Format Export (for AI training) <a href="<%=serverUrl%>/EncounterSearchExportCOCO?<%=request.getQueryString()%>"><%=map_props.getProperty("clickHere")%></a>
</p>
```

**Step 2: Commit**

```bash
git add src/main/webapp/encounters/exportSearchResults.jsp
git commit -m "feat: add COCO export link to search results page"
```

---

## Task 8: Verify Full Build

**Files:** None (verification only)

**Step 1: Run full Maven build**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

**Step 2: Check for any warnings**

Run: `cd /mnt/c/Wildbook-clean2 && mvn compile 2>&1 | grep -i "error\|warning" | head -20`
Expected: No critical errors

---

## Task 9: Write Unit Test for EncounterCOCOExportFile

**Files:**
- Create: `src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java`

**Step 1: Create test class**

```java
package org.ecocean.export;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(ann);
        when(enc.getAnnotations()).thenReturn(annotations);
        when(enc.getIndividual()).thenReturn(ind);

        List<Encounter> encounters = new ArrayList<>();
        encounters.add(enc);

        // Run export
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, shepherd);
        exportFile.writeTo(baos);

        // Parse tar.gz and extract annotations.json
        byte[] tarGzBytes = baos.toByteArray();
        assertTrue(tarGzBytes.length > 0, "Export should produce output");

        String jsonContent = null;
        try (GzipCompressorInputStream gzIn = new GzipCompressorInputStream(
                new ByteArrayInputStream(tarGzBytes));
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (entry.getName().endsWith("annotations.json")) {
                    byte[] content = tarIn.readAllBytes();
                    jsonContent = new String(content, StandardCharsets.UTF_8);
                    break;
                }
            }
        }

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
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(ann);
        when(enc.getAnnotations()).thenReturn(annotations);

        List<Encounter> encounters = new ArrayList<>();
        encounters.add(enc);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, shepherd);
        exportFile.writeTo(baos);

        // Extract and verify
        String jsonContent = extractJsonFromTarGz(baos.toByteArray());
        JSONObject coco = new JSONObject(jsonContent);

        // Should have no annotations since iaClass was null
        assertEquals(0, coco.getJSONArray("annotations").length());
        assertEquals(0, coco.getJSONArray("categories").length());
    }

    private String extractJsonFromTarGz(byte[] tarGzBytes) throws Exception {
        try (GzipCompressorInputStream gzIn = new GzipCompressorInputStream(
                new ByteArrayInputStream(tarGzBytes));
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (entry.getName().endsWith("annotations.json")) {
                    return new String(tarIn.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
```

**Step 2: Verify test compiles**

Run: `cd /mnt/c/Wildbook-clean2 && mvn test-compile -q 2>&1 | tail -10`
Expected: BUILD SUCCESS

**Step 3: Run the test**

Run: `cd /mnt/c/Wildbook-clean2 && mvn test -Dtest=EncounterCOCOExportFileTest -q 2>&1 | tail -20`
Expected: Tests pass

**Step 4: Commit**

```bash
git add src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
git commit -m "test: add unit tests for EncounterCOCOExportFile"
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Create skeleton class | EncounterCOCOExportFile.java |
| 2 | Data collection methods | EncounterCOCOExportFile.java |
| 3 | JSON building methods | EncounterCOCOExportFile.java |
| 4 | Implement writeTo | EncounterCOCOExportFile.java |
| 5 | Create servlet | EncounterSearchExportCOCO.java |
| 6 | Register in web.xml | web.xml |
| 7 | Add UI link | exportSearchResults.jsp |
| 8 | Verify build | (verification) |
| 9 | Unit tests | EncounterCOCOExportFileTest.java |
