package org.ecocean.api;

import org.ecocean.export.EncounterAnnotationExportFile;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.joda.time.DateTime;
import org.joda.time.Instant;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EncounterExport extends ApiBase{
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment;filename=encounter_export_"+ Instant.now().getMillis() +".zip");

        myShepherd.beginDBTransaction();
        try (ZipOutputStream outputStream = new ZipOutputStream(response.getOutputStream())) {
            writeMetadataFile(request, myShepherd, outputStream);
        }
        catch (Exception e){
            // todo: make this more specific
            e.printStackTrace();
            throw new RuntimeException("Unable to export data", e);
        }
        finally{
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    private static void writeMetadataFile(HttpServletRequest request, Shepherd myShepherd, ZipOutputStream outputStream) throws IOException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        EncounterAnnotationExportFile exportFile = new EncounterAnnotationExportFile(request, myShepherd);

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
