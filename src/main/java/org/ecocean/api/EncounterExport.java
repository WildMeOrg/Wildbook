package org.ecocean.api;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.*;
import org.ecocean.export.EncounterAnnotationExportFile;
import org.ecocean.export.EncounterImageExportFile;
import org.ecocean.security.HiddenEncReporter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.joda.time.Instant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
            if (Objects.equals(request.getParameter("includeMetadata"), "true")) {
                writeMetadataFile(request, myShepherd, outputStream);
            }
            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd,
                request, "year descending, month descending, day descending");
            List<Encounter> encounters = queryResult.getResult();

            // Build map of encounter ID -> individual using join table relationship
            Map<String,
                MarkedIndividual> encounterToIndividual = buildEncounterIndividualMap(myShepherd,
                encounters);
            EnumSet<EncounterImageExportFile.ExportOptions> flags = EnumSet.noneOf(
                EncounterImageExportFile.ExportOptions.class);
            if (Objects.equals(request.getParameter("unidentifiedEncounters"), "true")) {
                flags = EnumSet.of(
                    EncounterImageExportFile.ExportOptions.IncludeUnidentifiedEncounters);
            }
            int numAnnotationsPerId = -1;
            if (StringUtils.isNumeric(request.getParameter("numAnnotationsPerId"))) {
                numAnnotationsPerId = Integer.parseInt(request.getParameter("numAnnotationsPerId"));
            }
            EncounterImageExportFile imagesExport = new EncounterImageExportFile(encounters,
                encounterToIndividual, numAnnotationsPerId, flags);

            imagesExport.writeTo(outputStream);
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
        List<Encounter> encounters)
    throws Exception {
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
        try {
            query.setFilter(
                "encounters.contains(enc) && :catalogNumbers.contains(enc.catalogNumber)");
            query.declareVariables("org.ecocean.Encounter enc");

            @SuppressWarnings("unchecked") List<MarkedIndividual> individuals = (List<MarkedIndividual>)query.execute(encountersNeedingIndividuals);
            // Map encounters to individuals
            for (MarkedIndividual individual : individuals) {
                for (Encounter enc : individual.getEncounters()) {
                    if (encountersNeedingIndividuals.contains(enc.getCatalogNumber())) {
                        map.put(enc.getCatalogNumber(), individual);
                    }
                }
            }
            return map;
        } finally {
            query.closeAll();
        }
    }

    private static void writeMetadataFile(HttpServletRequest request, Shepherd myShepherd,
        ZipOutputStream outputStream)
    throws IOException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException,
        IllegalAccessException {
        EncounterAnnotationExportFile exportFile = new EncounterAnnotationExportFile(request,
            myShepherd);
        HiddenEncReporter hiddenData;

        // Write CSV file to ByteArrayOutputStream first to avoid nested ZIP issues
        try (ByteArrayOutputStream metadataBytes = new ByteArrayOutputStream()) {
            hiddenData = exportFile.writeToStream(metadataBytes);

            // Now write the complete CSV file as a single ZIP entry
            ZipEntry metadataFile = new ZipEntry("metadata.csv");
            outputStream.putNextEntry(metadataFile);
            outputStream.write(metadataBytes.toByteArray());
            outputStream.closeEntry();
        }

        // Security: log the hidden data report in CSV so the user can request collaborations with owners of hidden data
        try (ByteArrayOutputStream hiddenDataBytes = new ByteArrayOutputStream()) {
            hiddenData.writeHiddenDataReport(hiddenDataBytes);

            ZipEntry hiddenDataFile = new ZipEntry("hidden_data.csv");
            outputStream.putNextEntry(hiddenDataFile);
            outputStream.write(hiddenDataBytes.toByteArray());
            outputStream.closeEntry();
        }
    }
}
