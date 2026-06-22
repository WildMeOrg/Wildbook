package org.ecocean.export;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EncounterCOCOExportFile {
    private static final Logger log = Logger.getLogger(EncounterCOCOExportFile.class.getName());
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;

    private final List<Encounter> encounters;
    private final Shepherd shepherd;

    public EncounterCOCOExportFile(List<Encounter> encounters, Shepherd shepherd) {
        this.encounters = encounters;
        this.shepherd = shepherd;
    }

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

        // Write ZIP
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            // Write annotations.json
            log.info("COCO Export: Writing annotations JSON...");
            byte[] jsonBytes = coco.toString(2).getBytes(StandardCharsets.UTF_8);
            ZipEntry jsonEntry = new ZipEntry("coco/annotations/instances.json");
            zipOut.putNextEntry(jsonEntry);
            zipOut.write(jsonBytes);
            zipOut.closeEntry();
            zipOut.flush();

            // Write images with streaming to minimize memory usage
            int totalImages = mediaAssetMap.size();
            int processedImages = 0;
            int failedImages = 0;
            log.info("COCO Export: Starting export of " + totalImages + " images...");

            for (Map.Entry<String, MediaAsset> entry : mediaAssetMap.entrySet()) {
                MediaAsset ma = entry.getValue();
                processedImages++;
                try {
                    boolean success = writeImageToZip(ma, zipOut);
                    if (!success) {
                        failedImages++;
                    }
                    // Progress logging every 100 images
                    if (processedImages % 100 == 0) {
                        log.info("COCO Export: Processed " + processedImages + "/" + totalImages +
                                 " images (" + failedImages + " failed)");
                    }
                } catch (Exception e) {
                    failedImages++;
                    log.warning("COCO Export: Failed to export image " + ma.getUUID() + ": " + e.getMessage());
                }
            }

            zipOut.finish();
            log.info("COCO Export: Completed. " + (processedImages - failedImages) + "/" + totalImages +
                     " images exported successfully, " + failedImages + " failed.");
        }
    }

    /**
     * Writes an image directly to the ZipOutputStream using streaming.
     * This avoids loading entire images into memory, critical for large exports.
     *
     * @return true if image was written successfully, false if skipped
     */
    private boolean writeImageToZip(MediaAsset ma, ZipOutputStream zipOut) throws IOException {
        URL url = ma.webURL();
        if (url == null) {
            log.fine("COCO Export: Skipping image " + ma.getUUID() + " - no URL available");
            return false;
        }

        String contentType = detectContentType(url);
        boolean isJpeg = isJpegContentType(contentType);

        ZipEntry imgEntry = new ZipEntry("coco/images/" + ma.getUUID() + ".jpg");
        zipOut.putNextEntry(imgEntry);

        try {
            if (isJpeg) {
                // Stream JPEG directly - most memory efficient
                streamImageDirectly(url, zipOut);
            } else {
                // Non-JPEG: must decode and re-encode (uses more memory but unavoidable)
                convertAndWriteImage(url, zipOut);
            }
        } finally {
            zipOut.closeEntry();
            zipOut.flush(); // Keep data flowing to client
        }

        return true;
    }

    /**
     * Detects the content type of a URL, handling redirects.
     */
    private String detectContentType(URL url) {
        try {
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).setRequestMethod("HEAD");
                ((HttpURLConnection) conn).setInstanceFollowRedirects(true);
            }

            String contentType = conn.getContentType();

            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }

            return contentType;
        } catch (Exception e) {
            // Fall back to checking file extension
            String path = url.getPath().toLowerCase();
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            return null;
        }
    }

    /**
     * Checks if content type indicates JPEG.
     */
    private boolean isJpegContentType(String contentType) {
        if (contentType == null) return false;
        contentType = contentType.toLowerCase();
        return contentType.contains("image/jpeg") || contentType.contains("image/jpg");
    }

    /**
     * Streams image bytes directly from URL to ZipOutputStream without decoding.
     * This is the most memory-efficient method for JPEG images.
     */
    private void streamImageDirectly(URL url, ZipOutputStream zipOut) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        if (conn instanceof HttpURLConnection) {
            ((HttpURLConnection) conn).setInstanceFollowRedirects(true);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                zipOut.write(buffer, 0, bytesRead);
            }
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

    /**
     * Converts a non-JPEG image to JPEG and writes to ZipOutputStream.
     * This requires loading the image into memory but handles PNG, GIF, etc.
     */
    private void convertAndWriteImage(URL url, ZipOutputStream zipOut) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        if (conn instanceof HttpURLConnection) {
            ((HttpURLConnection) conn).setInstanceFollowRedirects(true);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("Failed to decode image");
            }

            // For images with alpha channel (PNG), convert to RGB for JPEG
            if (image.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgbImage.createGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);
                image = rgbImage;
            }

            // Write directly to zip stream
            ImageIO.write(image, "jpg", zipOut);
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

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

        // Always include all fields - use null for missing values so every record
        // has the same schema (enables NaN counting in EDA instead of probing for missing keys)
        img.put("date_captured", ma.getDateTime() != null ? ma.getDateTime().toString() : JSONObject.NULL);
        Double lat = ma.getLatitude();
        Double lon = ma.getLongitude();
        img.put("gps_lat_captured", lat != null ? lat : JSONObject.NULL);
        img.put("gps_lon_captured", lon != null ? lon : JSONObject.NULL);
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
        annJson.put("viewpoint", viewpoint != null ? viewpoint : JSONObject.NULL);

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

        // Encounter info
        String locationId = enc.getLocationID();
        annJson.put("location_id", locationId != null ? locationId : JSONObject.NULL);
        annJson.put("encounter_id", enc.getCatalogNumber());

        return annJson;
    }
}
