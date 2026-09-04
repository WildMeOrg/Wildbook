package org.ecocean.export;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.MultiValue;
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

/**
 * Generates a COCO-format ZIP export from a list of encounters.
 *
 * Design: all JDO/database data is eagerly extracted into plain data structures
 * during {@link #extractData()}, so the caller can close the DB transaction
 * before calling {@link #writeTo(OutputStream)} which does long-running HTTP
 * image downloads.
 */
public class EncounterCOCOExportFile {
    private static final Logger log = Logger.getLogger(EncounterCOCOExportFile.class.getName());
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;

    // Progress tracking for async exports
    private volatile int totalImages;
    private volatile int processedImages;
    private volatile int failedImages;
    private volatile String phase = "preparing";

    // --- Plain data extracted from JDO objects ---
    // Populated by extractData(), consumed by writeTo()
    private Map<String, URL> mediaAssetUrls;           // uuid -> webURL
    private Map<String, Boolean> mediaAssetIsJpeg;      // uuid -> true if JPEG
    private Map<String, ImageMeta> mediaAssetMeta;      // uuid -> metadata for JSON
    private Map<String, Integer> mediaAssetToImageId;   // uuid -> sequential image ID
    private Map<String, Integer> categoryMap;            // iaClass -> category ID
    private Map<String, Integer> individualIdMap;        // individual ID -> sequential int
    private List<AnnotationData> annotationDataList;     // flattened annotation records

    /** Immutable snapshot of image metadata needed for the COCO JSON. */
    static class ImageMeta {
        final String uuid;
        final int width;
        final int height;
        final String dateTime; // nullable
        final Double latitude; // nullable
        final Double longitude; // nullable

        ImageMeta(MediaAsset ma) {
            this.uuid = ma.getUUID();
            this.width = (int) ma.getWidth();
            this.height = (int) ma.getHeight();
            this.dateTime = ma.getDateTime() != null ? ma.getDateTime().toString() : null;
            this.latitude = ma.getLatitude();
            this.longitude = ma.getLongitude();
        }
    }

    /** Immutable snapshot of one annotation's data needed for the COCO JSON. */
    static class AnnotationData {
        final String annotationId;
        final String mediaAssetUuid;
        final String iaClass;
        final int[] bbox;
        final String viewpoint; // nullable
        final double theta;
        final String individualId; // nullable
        final String individualDisplayName; // nullable
        final List<String[]> allNames; // list of [key, value] pairs
        final String locationId; // nullable
        final String encounterId;

        AnnotationData(Annotation ann, Encounter enc) {
            this.annotationId = ann.getId();
            this.mediaAssetUuid = ann.getMediaAsset().getUUID();
            this.iaClass = ann.getIAClass().trim();
            this.bbox = ann.getBbox();
            this.viewpoint = ann.getViewpoint();
            this.theta = ann.getTheta();
            this.locationId = enc.getLocationID();
            this.encounterId = enc.getCatalogNumber();

            MarkedIndividual ind = enc.getIndividual();
            if (ind != null && ind.getId() != null) {
                this.individualId = ind.getId();
                this.individualDisplayName = ind.getDisplayName();
                this.allNames = extractNames(ind);
            } else {
                this.individualId = null;
                this.individualDisplayName = null;
                this.allNames = Collections.emptyList();
            }
        }

        private static List<String[]> extractNames(MarkedIndividual ind) {
            List<String[]> result = new ArrayList<>();
            MultiValue names = ind.getNames();
            if (names == null) return result;
            Set<String> keys = names.getKeys();
            if (keys == null) return result;
            for (String key : keys) {
                List<String> vals = names.getValuesByKey(key);
                if (vals == null) continue;
                for (String val : vals) {
                    result.add(new String[]{key, val});
                }
            }
            return result;
        }
    }

    /**
     * Constructs the export file and eagerly extracts all data from JDO objects.
     * After this constructor returns, the caller may close the DB transaction.
     */
    public EncounterCOCOExportFile(List<Encounter> encounters, Shepherd shepherd) {
        extractData(encounters);
    }

    public int getTotalImages() { return totalImages; }
    public int getProcessedImages() { return processedImages; }
    public int getFailedImages() { return failedImages; }
    public String getPhase() { return phase; }

    /**
     * Eagerly extracts all needed data from JDO-managed objects into plain
     * data structures. Must be called while the DB transaction is still active.
     */
    private void extractData(List<Encounter> encounters) {
        // Collect unique media assets and their metadata/URLs/content types
        mediaAssetUrls = new LinkedHashMap<>();
        mediaAssetIsJpeg = new LinkedHashMap<>();
        mediaAssetMeta = new LinkedHashMap<>();
        for (Encounter enc : encounters) {
            if (enc.getAnnotations() == null) continue;
            for (Annotation ann : enc.getAnnotations()) {
                if (!isValidAnnotation(ann)) continue;
                MediaAsset ma = ann.getMediaAsset();
                String uuid = ma.getUUID();
                if (!mediaAssetUrls.containsKey(uuid)) {
                    URL url = ma.webURL();
                    mediaAssetUrls.put(uuid, url);
                    mediaAssetMeta.put(uuid, new ImageMeta(ma));
                    mediaAssetIsJpeg.put(uuid, detectIsJpeg(ma, url));
                }
            }
        }

        // Assign sequential image IDs
        mediaAssetToImageId = new LinkedHashMap<>();
        int imageId = 1;
        for (String uuid : mediaAssetUrls.keySet()) {
            mediaAssetToImageId.put(uuid, imageId++);
        }

        // Build category map
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
        categoryMap = new LinkedHashMap<>();
        int catId = 1;
        for (String iaClass : iaClasses) {
            categoryMap.put(iaClass, catId++);
        }

        // Build individual ID map
        Set<String> individualIds = new TreeSet<>();
        for (Encounter enc : encounters) {
            MarkedIndividual ind = enc.getIndividual();
            if (ind != null && ind.getId() != null) {
                individualIds.add(ind.getId());
            }
        }
        individualIdMap = new LinkedHashMap<>();
        int idx = 0;
        for (String indId : individualIds) {
            individualIdMap.put(indId, idx++);
        }

        // Extract all annotation data
        annotationDataList = new ArrayList<>();
        for (Encounter enc : encounters) {
            if (enc.getAnnotations() == null) continue;
            for (Annotation ann : enc.getAnnotations()) {
                if (!isValidAnnotation(ann)) continue;
                annotationDataList.add(new AnnotationData(ann, enc));
            }
        }
    }

    /**
     * Writes the COCO ZIP to the given output stream. This method performs
     * long-running HTTP image downloads and does NOT require a DB transaction.
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        // Write ZIP: images first, then JSON manifest so it only references
        // images that were actually written successfully.
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            totalImages = mediaAssetUrls.size();
            processedImages = 0;
            failedImages = 0;
            phase = "images";
            Set<String> exportedUuids = new LinkedHashSet<>();
            log.info("COCO Export: Starting export of " + totalImages + " images...");

            for (Map.Entry<String, URL> entry : mediaAssetUrls.entrySet()) {
                String uuid = entry.getKey();
                URL url = entry.getValue();
                try {
                    boolean success = writeImageToZip(uuid, url, zipOut);
                    if (success) {
                        exportedUuids.add(uuid);
                    } else {
                        failedImages++;
                    }
                } catch (Exception e) {
                    failedImages++;
                    log.warning("COCO Export: Failed to export image " + uuid +
                        ": " + e.getMessage());
                }
                processedImages++;
                if (processedImages % 100 == 0) {
                    log.info("COCO Export: Processed " + processedImages + "/" + totalImages +
                             " images (" + failedImages + " failed)");
                }
            }

            // Build JSON arrays using only successfully exported images
            phase = "manifest";
            JSONArray imagesArray = new JSONArray();
            for (String uuid : exportedUuids) {
                ImageMeta meta = mediaAssetMeta.get(uuid);
                int imgId = mediaAssetToImageId.get(uuid);
                imagesArray.put(buildImageObject(meta, imgId));
            }

            JSONArray annotationsArray = new JSONArray();
            int annotationId = 1;
            for (AnnotationData ad : annotationDataList) {
                if (!exportedUuids.contains(ad.mediaAssetUuid)) continue;
                int imgId = mediaAssetToImageId.get(ad.mediaAssetUuid);
                annotationsArray.put(buildAnnotationObject(ad, annotationId++, imgId));
            }

            JSONObject coco = new JSONObject();
            coco.put("info", buildInfo());
            coco.put("licenses", buildLicenses());
            coco.put("categories", buildCategories());
            coco.put("images", imagesArray);
            coco.put("annotations", annotationsArray);

            // Write annotations.json as the last entry
            phase = "packaging";
            log.info("COCO Export: Writing annotations JSON...");
            byte[] jsonBytes = coco.toString(2).getBytes(StandardCharsets.UTF_8);
            ZipEntry jsonEntry = new ZipEntry("coco/annotations/instances.json");
            zipOut.putNextEntry(jsonEntry);
            zipOut.write(jsonBytes);
            zipOut.closeEntry();

            zipOut.finish();
            log.info("COCO Export: Completed. " + exportedUuids.size() + "/" + totalImages +
                     " images exported successfully, " + failedImages + " failed.");
        }
    }

    /**
     * Writes an image directly to the ZipOutputStream using streaming.
     *
     * @return true if image was written successfully, false if skipped
     */
    private boolean writeImageToZip(String uuid, URL url, ZipOutputStream zipOut) throws IOException {
        if (url == null) {
            log.fine("COCO Export: Skipping image " + uuid + " - no URL available");
            return false;
        }

        boolean isJpeg = Boolean.TRUE.equals(mediaAssetIsJpeg.get(uuid));

        ZipEntry imgEntry = new ZipEntry("coco/images/" + uuid + ".jpg");
        zipOut.putNextEntry(imgEntry);

        try {
            if (isJpeg) {
                streamImageDirectly(url, zipOut);
            } else {
                convertAndWriteImage(url, zipOut);
            }
        } finally {
            zipOut.closeEntry();
            zipOut.flush();
        }

        return true;
    }

    /**
     * Determines if a MediaAsset is JPEG using DB metadata (no HTTP request).
     * Falls back to URL path extension if metadata is unavailable.
     */
    private static boolean detectIsJpeg(MediaAsset ma, URL url) {
        String[] mimeType = ma.getMimeType();
        if (mimeType != null && mimeType.length >= 2) {
            String minor = mimeType[1].split(";")[0].trim();
            return "jpeg".equals(minor) || "jpg".equals(minor);
        }
        // Fall back to URL path extension
        if (url != null) {
            String path = url.getPath().toLowerCase();
            return path.endsWith(".jpg") || path.endsWith(".jpeg");
        }
        return false;
    }

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

            if (image.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2d = rgbImage.createGraphics();
                try {
                    g2d.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
                } finally {
                    g2d.dispose();
                }
                image = rgbImage;
            }

            java.io.ByteArrayOutputStream imgBuf = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "jpg", imgBuf);
            zipOut.write(imgBuf.toByteArray());
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

    private boolean isValidAnnotation(Annotation ann) {
        if (ann == null) return false;
        String iaClass = ann.getIAClass();
        if (iaClass == null || iaClass.trim().isEmpty()) return false;
        int[] bbox = ann.getBbox();
        if (bbox == null || bbox.length < 4) return false;
        if (bbox[2] <= 0 || bbox[3] <= 0) return false;
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null || ma.getUUID() == null) return false;
        return true;
    }

    // --- JSON builders using plain data (no JDO access) ---

    private JSONObject buildImageObject(ImageMeta meta, int imageId) {
        JSONObject img = new JSONObject();
        img.put("id", imageId);
        img.put("file_name", meta.uuid + ".jpg");
        img.put("width", meta.width);
        img.put("height", meta.height);
        img.put("uuid", meta.uuid);
        img.put("date_captured", meta.dateTime != null ? meta.dateTime : JSONObject.NULL);
        img.put("gps_lat_captured", meta.latitude != null ? meta.latitude : JSONObject.NULL);
        img.put("gps_lon_captured", meta.longitude != null ? meta.longitude : JSONObject.NULL);
        return img;
    }

    private JSONObject buildAnnotationObject(AnnotationData ad, int annotationId, int imageId) {
        JSONObject annJson = new JSONObject();
        annJson.put("id", annotationId);
        annJson.put("image_id", imageId);
        annJson.put("category_id", categoryMap.get(ad.iaClass));

        int[] bbox = ad.bbox;
        JSONArray bboxArray = new JSONArray();
        bboxArray.put(bbox[0]);
        bboxArray.put(bbox[1]);
        bboxArray.put(bbox[2]);
        bboxArray.put(bbox[3]);
        annJson.put("bbox", bboxArray);

        annJson.put("area", bbox[2] * bbox[3]);

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
        annJson.put("uuid", ad.annotationId);
        annJson.put("viewpoint", ad.viewpoint != null ? ad.viewpoint : JSONObject.NULL);
        annJson.put("theta", ad.theta);

        JSONArray individualIds = new JSONArray();
        if (ad.individualId != null && individualIdMap.containsKey(ad.individualId)) {
            individualIds.put(individualIdMap.get(ad.individualId));
            annJson.put("individual_uuid", ad.individualId);
            annJson.put("name", ad.individualDisplayName != null ? ad.individualDisplayName : JSONObject.NULL);
            JSONArray allNames = new JSONArray();
            for (String[] kv : ad.allNames) {
                JSONObject entry = new JSONObject();
                entry.put("key", kv[0]);
                entry.put("value", kv[1]);
                allNames.put(entry);
            }
            annJson.put("all_names", allNames);
        } else {
            annJson.put("individual_uuid", JSONObject.NULL);
            annJson.put("name", JSONObject.NULL);
            annJson.put("all_names", new JSONArray());
        }
        annJson.put("individual_ids", individualIds);

        annJson.put("location_id", ad.locationId != null ? ad.locationId : JSONObject.NULL);
        annJson.put("encounter_id", ad.encounterId);

        return annJson;
    }

    private JSONObject buildInfo() {
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

    private JSONArray buildLicenses() {
        JSONArray licenses = new JSONArray();
        JSONObject license = new JSONObject();
        license.put("id", 1);
        license.put("name", "See Wildbook Terms");
        license.put("url", "");
        licenses.put(license);
        return licenses;
    }

    private JSONArray buildCategories() {
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
}
