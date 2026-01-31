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
}
