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
}
