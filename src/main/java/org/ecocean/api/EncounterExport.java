package org.ecocean.api;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.*;
import org.ecocean.export.EncounterAnnotationExportFile;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.joda.time.Instant;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

public class EncounterExport extends ApiBase {
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
            "attachment;filename=encounter_export_" + Instant.now().getMillis() + ".zip");

        myShepherd.beginDBTransaction();
        try (ZipOutputStream outputStream = new ZipOutputStream(response.getOutputStream())) {
            writeMetadataFile(request, myShepherd, outputStream);

            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd,
                request, "year descending, month descending, day descending");
            List<Encounter> encounters = queryResult.getResult();

            // Build map of encounter ID -> individual using join table relationship
            Map<String,
                MarkedIndividual> encounterToIndividual = buildEncounterIndividualMap(myShepherd,
                encounters);
            for (Encounter e : encounters) {
                int annotatationIdx = 0;
                for (Annotation a : e.getAnnotations()) {
                    MediaAsset ma = a.getMediaAsset();
                    if (!a.getMatchAgainst() || StringUtils.isBlank(a.getViewpoint()) ||
                        ma.webURL() == null) {
                        System.out.printf("Skipping annotation %s%n", a.getId());
                        continue;
                    }
                    try {
                        String displayName = getDisplayName(e, encounterToIndividual);
                        URI imageUrl = ma.webURL().toURI();
                        System.out.printf("Writing image [%s] %s%n", displayName, imageUrl);

                        ZipEntry croppedImageEntry = new ZipEntry(String.format(
                            "images/%s/%s_%s.jpg", displayName, e.getID(), annotatationIdx++));
                        outputStream.putNextEntry(croppedImageEntry);
                        BufferedImage originalImage = ImageIO.read(imageUrl.toURL());
                        BufferedImage croppedImage;
                        int[] bbox = a.getBbox();
                        if (bbox == null || bbox.length < 4 || bbox[2] == 0 || bbox[3] == 0) {
                            croppedImage = originalImage;
                        } else {
                            croppedImage = originalImage.getSubimage(bbox[0], bbox[1], bbox[2],
                                bbox[3]);
                            System.out.printf("  Cropped to [%d,%d,%d,%d] %n", bbox[0], bbox[1],
                                bbox[2], bbox[3]);
                        }
                        ImageIO.write(croppedImage, "jpg", outputStream);
                        outputStream.closeEntry();
                    } catch (Exception ex) {
                        throw new RuntimeException("Unable to process annotation " + a.getId() +
                                " for encounter " + e.getId(), ex);
                    }
                }
            }
        } catch (Exception e) {
            // todo: make this more specific
            e.printStackTrace();
            throw new RuntimeException("Unable to export data", e);
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    /**
     * Builds a map of encounter catalog number to MarkedIndividual.
     * This handles both the old direct foreign key and new join table relationships
     * in a single efficient query.
     */
    private static Map<String, MarkedIndividual> buildEncounterIndividualMap(Shepherd myShepherd,
        List<Encounter> encounters) {
        Map<String, MarkedIndividual> map = new HashMap<>();

        if (encounters.isEmpty()) {
            return map;
        }
        // First, handle direct relationships (e.getIndividual() != null)
        for (Encounter e : encounters) {
            if (e.getIndividual() != null) {
                map.put(e.getCatalogNumber(), e.getIndividual());
            }
        }
        // Build list of encounter IDs that still need individuals
        List<String> encountersNeedingIndividuals = new ArrayList<>();
        for (Encounter e : encounters) {
            if (!map.containsKey(e.getCatalogNumber())) {
                encountersNeedingIndividuals.add(e.getCatalogNumber());
            }
        }
        if (encountersNeedingIndividuals.isEmpty()) {
            return map;
        }
        // Query MarkedIndividuals that have these encounters (via join table)
        // Using fetch groups to eagerly load encounters and names
        PersistenceManager pm = myShepherd.getPM();
        PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();

        javax.jdo.FetchGroup indvGrp = pmf.getFetchGroup(MarkedIndividual.class,
            "individualWithEncounters");
        indvGrp.addMember("individualID").addMember("names").addMember("encounters");
        pm.getFetchPlan().addGroup("individualWithEncounters");

        Query query = pm.newQuery(MarkedIndividual.class);
        query.setFilter("encounters.contains(enc) && :catalogNumbers.contains(enc.catalogNumber)");
        query.declareVariables("org.ecocean.Encounter enc");

        @SuppressWarnings("unchecked") List<MarkedIndividual> individuals =
            (List<MarkedIndividual>)query.execute(encountersNeedingIndividuals);
        // Map encounters to individuals
        for (MarkedIndividual individual : individuals) {
            for (Encounter enc : individual.getEncounters()) {
                if (encountersNeedingIndividuals.contains(enc.getCatalogNumber())) {
                    map.put(enc.getCatalogNumber(), individual);
                }
            }
        }
        return map;
    }

    private static String getDisplayName(Encounter e,
        Map<String, MarkedIndividual> encounterToIndividual) {
        String displayName = "unidentified_individual";

        // Try the map first (handles both old and new relationships)
        MarkedIndividual individual = encounterToIndividual.get(e.getCatalogNumber());

        if (individual != null) {
            MultiValue names = individual.getNames();
            List<String> nameLabels = names.getSortedKeys();
            if (!nameLabels.isEmpty()) {
                displayName = names.getValue(nameLabels.get(0));
            }
        }
        return displayName;
    }

    private static void writeMetadataFile(HttpServletRequest request, Shepherd myShepherd,
        ZipOutputStream outputStream)
    throws IOException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException,
        IllegalAccessException {
        EncounterAnnotationExportFile exportFile = new EncounterAnnotationExportFile(request,
            myShepherd);

        // Write Excel file to ByteArrayOutputStream first to avoid nested ZIP issues
        try (ByteArrayOutputStream excelBytes = new ByteArrayOutputStream()) {
            exportFile.writeToStream(excelBytes);

            // Now write the complete Excel file as a single ZIP entry
            ZipEntry metadataFile = new ZipEntry("metadata.xlsx");
            outputStream.putNextEntry(metadataFile);
            outputStream.write(excelBytes.toByteArray());
            outputStream.closeEntry();
        }
    }
}
