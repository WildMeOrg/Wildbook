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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Async COCO export servlet. Three actions:
 *   ?action=start  — kicks off background export, returns JSON {"jobId":"..."}
 *   ?action=status&jobId=...  — returns JSON with progress/completion
 *   ?action=download&jobId=...  — streams the completed ZIP file
 *
 * Legacy (no action param) falls back to synchronous export for backwards compatibility.
 */
public class EncounterSearchExportCOCO extends HttpServlet {

    private static final long MAX_JOB_AGE_MS = 60 * 60 * 1000; // 1 hour

    private static final Map<String, ExportJob> jobs = new ConcurrentHashMap<>();

    static class ExportJob {
        final String jobId;
        final long createdAt = System.currentTimeMillis();
        volatile String status = "running"; // running | complete | error
        volatile String errorMessage;
        volatile File tempFile;
        volatile EncounterCOCOExportFile exportFile;

        ExportJob(String jobId) { this.jobId = jobId; }
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        if ("start".equals(action)) {
            handleStart(request, response);
        } else if ("status".equals(action)) {
            handleStatus(request, response);
        } else if ("download".equals(action)) {
            handleDownload(request, response);
        } else {
            handleSynchronous(request, response);
        }
    }

    private void handleStart(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        purgeStaleJobs();

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSearchExportCOCO.start");
        myShepherd.beginDBTransaction();

        // Collect encounter IDs on the request thread (JDO objects stay local)
        List<String> encounterIds;
        try {
            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(
                myShepherd, request, "year descending, month descending, day descending");
            Vector<?> rEncounters = queryResult.getResult();
            HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);

            encounterIds = new ArrayList<>();
            for (Object obj : rEncounters) {
                Encounter enc = (Encounter) obj;
                if (!hiddenData.contains(enc)) {
                    encounterIds.add(enc.getCatalogNumber());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(response, 500, "{\"error\":\"Query failed: " +
                escapeJson(e.getMessage()) + "\"}");
            return;
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }

        String jobId = UUID.randomUUID().toString();
        ExportJob job = new ExportJob(jobId);
        jobs.put(jobId, job);

        // Background thread opens its own Shepherd and re-fetches encounters
        List<String> encIds = encounterIds;
        Thread exportThread = new Thread(() -> {
            Shepherd bgShepherd = new Shepherd(context);
            bgShepherd.setAction("EncounterSearchExportCOCO.export-" + jobId);
            bgShepherd.beginDBTransaction();
            try {
                List<Encounter> encounters = new ArrayList<>();
                for (String id : encIds) {
                    Encounter enc = bgShepherd.getEncounter(id);
                    if (enc != null) encounters.add(enc);
                }
                job.exportFile = new EncounterCOCOExportFile(encounters, bgShepherd);

                File tmpDir = new File(CommonConfiguration.getUploadTmpDir(context));
                if (!tmpDir.exists()) tmpDir.mkdirs();
                File tempFile = File.createTempFile("wildbook-coco-export-", ".zip", tmpDir);
                tempFile.deleteOnExit();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    job.exportFile.writeTo(fos);
                }
                job.tempFile = tempFile;
                job.status = "complete";
            } catch (Exception e) {
                e.printStackTrace();
                job.status = "error";
                job.errorMessage = e.getMessage();
            } finally {
                bgShepherd.rollbackDBTransaction();
                bgShepherd.closeDBTransaction();
            }
        }, "coco-export-" + jobId);
        exportThread.setDaemon(true);
        exportThread.start();

        sendJson(response, 200, "{\"jobId\":\"" + jobId + "\"}");
    }

    private void handleStatus(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String jobId = request.getParameter("jobId");
        ExportJob job = (jobId != null) ? jobs.get(jobId) : null;
        if (job == null) {
            sendJson(response, 404, "{\"error\":\"Job not found\"}");
            return;
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"jobId\":\"").append(job.jobId).append("\"");
        json.append(",\"status\":\"").append(job.status).append("\"");
        if (job.exportFile != null) {
            json.append(",\"totalImages\":").append(job.exportFile.getTotalImages());
            json.append(",\"processedImages\":").append(job.exportFile.getProcessedImages());
            json.append(",\"failedImages\":").append(job.exportFile.getFailedImages());
        }
        if (job.errorMessage != null) {
            json.append(",\"error\":\"").append(escapeJson(job.errorMessage)).append("\"");
        }
        json.append("}");
        sendJson(response, 200, json.toString());
    }

    private void handleDownload(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String jobId = request.getParameter("jobId");
        // Atomic remove prevents concurrent downloads of the same job
        ExportJob job = (jobId != null) ? jobs.remove(jobId) : null;
        if (job == null) {
            sendJson(response, 404, "{\"error\":\"Job not found\"}");
            return;
        }
        if (!"complete".equals(job.status) || job.tempFile == null || !job.tempFile.exists()) {
            sendJson(response, 400, "{\"error\":\"Export not ready\"}");
            return;
        }

        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"wildbook-coco-export.zip\"");
            response.setContentLengthLong(job.tempFile.length());
            OutputStream out = response.getOutputStream();
            Files.copy(job.tempFile.toPath(), out);
            out.flush();
        } finally {
            job.tempFile.delete();
        }
    }

    /** Synchronous fallback for legacy/non-JS callers. */
    private void handleSynchronous(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSearchExportCOCO.class");
        myShepherd.beginDBTransaction();

        File tempFile = null;
        try {
            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(
                myShepherd, request, "year descending, month descending, day descending");
            Vector<?> rEncounters = queryResult.getResult();
            HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);
            List<Encounter> encounters = new ArrayList<>();
            for (Object obj : rEncounters) {
                Encounter enc = (Encounter) obj;
                if (!hiddenData.contains(enc)) {
                    encounters.add(enc);
                }
            }

            File tmpDir = new File(CommonConfiguration.getUploadTmpDir(context));
            if (!tmpDir.exists()) tmpDir.mkdirs();
            tempFile = File.createTempFile("wildbook-coco-export-", ".zip", tmpDir);
            tempFile.deleteOnExit();
            EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, myShepherd);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                exportFile.writeTo(fos);
            }

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"wildbook-coco-export.zip\"");
            response.setContentLengthLong(tempFile.length());
            OutputStream out = response.getOutputStream();
            Files.copy(tempFile.toPath(), out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
            if (!response.isCommitted()) {
                sendJson(response, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    private void sendJson(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private void purgeStaleJobs() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ExportJob>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            ExportJob job = it.next().getValue();
            if ((now - job.createdAt) > MAX_JOB_AGE_MS) {
                if (job.tempFile != null && job.tempFile.exists()) {
                    job.tempFile.delete();
                }
                it.remove();
            }
        }
    }
}
