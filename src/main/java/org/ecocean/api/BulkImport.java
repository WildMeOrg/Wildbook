package org.ecocean.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.bulk.*;

import org.ecocean.Encounter;
import org.ecocean.ia.IA;
import org.ecocean.LocationID;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

import org.json.JSONArray;
import org.json.JSONObject;

public class BulkImport extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        int statusCode = 500;
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.Bulk.doGet");
        myShepherd.beginDBTransaction();
        JSONObject rtn = new JSONObject("{\"success\": false}");

        try {
            User currentUser = myShepherd.getUser(request);
            if (currentUser == null) {
                response.setStatus(401);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"success\": false}");
                return;
            }
            boolean isAdmin = currentUser.isAdmin(myShepherd); // only compute once
            String uri = request.getRequestURI();
            String[] args = uri.substring(8).split("/"); // args[0] == 'bulk-import'
            String bulkImportId = null;
            if (args.length == 1) {
                List<ImportTask> tasks = null;
                if (isAdmin) {
                    tasks = myShepherd.getImportTasks();
                } else {
                    tasks = myShepherd.getImportTasksForUser(currentUser);
                }
                JSONArray tasksArr = new JSONArray();
                for (ImportTask task : tasks) {
                    tasksArr.put(taskJson(task, false, myShepherd));
                }
                rtn.put("tasks", tasksArr);
                rtn.put("success", true);
                statusCode = 200;
            } else if (args.length < 2) { // i guess this means 0?
                throw new IOException("invalid api endpoint");
            } else {
                bulkImportId = args[1];
                if (!Util.isUUID(bulkImportId))
                    throw new IOException("invalid bulk import id passed");
                if (args.length == 2) { // dump info on single task
                    ImportTask task = myShepherd.getImportTask(bulkImportId);
                    if (task == null) {
                        statusCode = 404;
                    } else if (!isAdmin && !currentUser.equals(task.getCreator())) {
                        statusCode = 403;
                        rtn.put("message", "no access to task");
                    } else {
                        rtn.put("task", taskJson(task, true, myShepherd));
                        statusCode = 200;
                        rtn.put("success", true);
                    }
                } else if (args[2].equals("files")) { // list files uploaded for this id
                    File uploadDir = UploadedFiles.getUploadDir(request, bulkImportId, true);
                    if (!uploadDir.exists()) {
                        statusCode = 404;
                        throw new IOException("uploadDir " + uploadDir + " not found");
                    }
                    JSONArray farr = new JSONArray();
                    for (final File f : uploadDir.listFiles()) {
                        if (f.isDirectory()) continue;
                        JSONArray fa = new JSONArray();
                        fa.put(f.getName());
                        fa.put(f.length());
                        farr.put(fa);
                    }
                    statusCode = 200;
                    rtn.put("success", true);
                    rtn.put("files", farr);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            myShepherd.rollbackAndClose();
        }
        response.setStatus(statusCode);
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(rtn.toString());
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        int statusCode = 500;
        JSONObject rtn = new JSONObject();

        rtn.put("success", false);
        boolean validateOnly = false;
        boolean skipDetection = false;
        boolean skipIdentification = false;
        JSONObject matchingSetFilter = new JSONObject();
        JSONObject encAssets = null;
        String dupId = null; // gets set as bulkImporId to be used in finally block
        long startProcess = System.currentTimeMillis();
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.Bulk.doPost");
        myShepherd.beginDBTransaction();
        long startTime = System.currentTimeMillis();
        try {
            User currentUser = myShepherd.getUser(request);
            if (currentUser == null) {
                response.setStatus(401);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"success\": false}");
                return;
            }
            JSONObject payload = ServletUtilities.jsonFromHttpServletRequest(request);
            if (payload == null) throw new ServletException("empty payload");
            JSONArray rows = payload.optJSONArray("rows");
            if (rows == null) throw new ServletException("no rows in payload");
            String bulkImportId = payload.optString("bulkImportId", null);
            if (bulkImportId == null) throw new ServletException("bulkImportId is required");
            rtn.put("bulkImportId", bulkImportId);
            archiveBulkJson(payload, "payload");
            dupId = bulkImportId;

            JSONObject tolerance = payload.optJSONObject("tolerance");
            if (tolerance == null) tolerance = new JSONObject();
            // default failImportOnError behavior is true, means any single error bails on whole import
            boolean toleranceFailImportOnError = tolerance.optBoolean("failImportOnError", true);
            // if above false, any error in a row causes it to be skipped, unless this false
            // in that case, rather than skip the row, the bad field will be ignored
            // note: REQUIRED fields cannot be skipped and if missing will cause row to be skipped
            boolean toleranceSkipRowOnError = tolerance.optBoolean("skipRowOnError", true);
            // defaulting to true for this, so basically bad fieldnames (and their values) are skipped
            boolean toleranceBadFieldnamesAreWarnings = tolerance.optBoolean(
                "badFieldnamesAreWarnings", true);

            // dont create anything, just check data (always returns a 200,
            // but returned "success" (boolean) indicates total validity
            validateOnly = payload.optBoolean("validateOnly", false);
            if (validateOnly) {
                toleranceFailImportOnError = false;
                toleranceSkipRowOnError = false;
            }
            boolean verboseReturn = payload.optBoolean("verbose", false);
            // should this be on by default?
            boolean processInBackground = payload.optBoolean("processInBackground",
                false) && !validateOnly;
            List<File> files = UploadedFiles.findFiles(request, bulkImportId);
            JSONArray fieldNamesArr = payload.optJSONArray("fieldNames");
            Set<String> fieldNames = null;
            if (fieldNamesArr != null) {
                fieldNames = new LinkedHashSet<String>();
                for (int i = 0; i < fieldNamesArr.length(); i++) {
                    String fn = fieldNamesArr.optString(i, null);
                    if (fn == null)
                        throw new ServletException("could not find field name at i=" + i);
                    fieldNames.add(fn);
                }
                List<List<String> > syn = BulkValidator.findSynonyms(fieldNames);
                if (syn != null) {
                    JSONArray synErrs = new JSONArray();
                    for (List<String> syns : syn) {
                        JSONObject err = new JSONObject();
                        err.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                        err.put("details", "synonym columns: " + String.join(", ", syns));
                        err.put("fieldNames", syns);
                        synErrs.put(err);
                    }
                    throw new ApiException("duplicate columns due to synonyms in field names",
                            synErrs);
                }
            }
            // we might grow matchingSetFilter stuff later, but for now we only have locationIds from ui
            JSONArray mloc = payload.optJSONArray("matchingLocations");
            if (mloc != null) {
                JSONArray locArr = new JSONArray();
                for (int i = 0; i < mloc.length(); i++) {
                    String locId = mloc.optString(i, null);
                    if (locId == null) continue;
                    if (!LocationID.isValidLocationID(locId))
                        throw new ApiException("matchingLocations contains invalid id=" + locId,
                                ApiException.ERROR_RETURN_CODE_INVALID);
                    locArr.put(locId);
                }
                matchingSetFilter.put("locationIds", locArr);
            }
            skipDetection = payload.optBoolean("skipDetection", false);
            // if you skipDetection, you cant do identification, so:
            skipIdentification = skipDetection || payload.optBoolean("skipIdentification", false);
            Set<String> filenamesNeeded = new HashSet<String>();
            final List<Map<String, Object> > validatedRows = new ArrayList<Map<String, Object> >();
            BulkImporter.logProgress(bulkImportId,
                "doPost: pre-validated [" + rows.length() + " rows]", startTime);
            for (int i = 0; i < rows.length(); i++) {
                if (i % 200 == 1)
                    BulkImporter.logProgress(bulkImportId, "doPost: validating=" + i, startTime);
                Map<String, Object> vrow = null;
                if (fieldNames == null) {
                    JSONObject rowData = rows.optJSONObject(i);
                    if (rowData == null) {
                        System.out.println("null rowData on row " + i + " of bulk import");
                        continue;
                    }
                    vrow = BulkImportUtil.validateRow(rowData, myShepherd);
                } else {
                    JSONArray rowArr = rows.optJSONArray(i);
                    if (rowArr == null) {
                        System.out.println("null rowArr on row " + i + " of bulk import");
                        continue;
                    }
                    vrow = BulkImportUtil.validateRow(fieldNames, rowArr, myShepherd);
                }
                validatedRows.add(vrow);
                // now we check if we actually have all the mediaAssets we need
                for (String fieldName : vrow.keySet()) {
                    if (!BulkValidator.isValidFieldName(fieldName)) continue;
                    String prefix = BulkValidator.indexPrefixValue(fieldName);
                    if (!"Encounter.mediaAsset".equals(prefix)) continue;
                    if (vrow.get(fieldName) instanceof Exception) continue;
                    BulkValidator bv = (BulkValidator)vrow.get(fieldName);
                    if (bv.getValue() == null) continue;
                    String filename = bv.getValue().toString();
                    if (Util.containsFilename(files, filename)) {
                        filenamesNeeded.add(filename);
                    } else {
                        System.out.println(bv.getValue() + " not found in uploaded files");
                        vrow.put(fieldName,
                            new BulkValidatorException("file '" + bv.getValue().toString() +
                            "' not found in uploaded files",
                            ApiException.ERROR_RETURN_CODE_REQUIRED));
                    }
                }
            }
            BulkImporter.logProgress(bulkImportId, "doPost: post-validated", startTime);
            // related to how to set owner when none given in row
            boolean nullSubmitterIsPublic = payload.optBoolean("nullSubmitterIsPublic", false);
            String submitterIDFieldName = "Encounter.submitterID";
            BulkValidator defaultSubmitterIDBV = null;
            if (nullSubmitterIsPublic) {
                defaultSubmitterIDBV = new BulkValidator(submitterIDFieldName, "public",
                    myShepherd);
            } else {
                defaultSubmitterIDBV = new BulkValidator(submitterIDFieldName,
                    currentUser.getUsername(), myShepherd);
            }
            int numRowsValid = 0;
            List<BulkValidator> validFields = new ArrayList<BulkValidator>();
            JSONArray dataErrors = new JSONArray();
            JSONArray dataWarnings = new JSONArray();
            Set<String> warningDuplicate = new HashSet<String>();
            for (int rowNum = 0; rowNum < validatedRows.size(); rowNum++) {
                boolean rowValid = true;
                boolean hasSubmitter = false;
                Map<String, Object> rowResult = validatedRows.get(rowNum);
                for (String rowFieldName : rowResult.keySet()) {
                    Object fieldObj = rowResult.get(rowFieldName);
                    if (fieldObj instanceof BulkValidator) {
                        BulkValidator bv = (BulkValidator)fieldObj;
                        // either we have passed a (valid) owner, or we have the field, but no value
                        // in latter case we set to the default
                        if (rowFieldName.equals(submitterIDFieldName)) {
                            if (bv.getValue() == null) {
                                rowResult.put(submitterIDFieldName, defaultSubmitterIDBV);
                                bv = defaultSubmitterIDBV;
                            }
                            hasSubmitter = true;
                        }
                        validFields.add(bv);
                    } else if (fieldObj instanceof BulkValidatorException) {
                        JSONObject err = new JSONObject();
                        err.put("rowNumber", rowNum);
                        err.put("fieldName", rowFieldName);
                        BulkValidatorException bve = (BulkValidatorException)fieldObj;
                        err.put("type", bve.getType());
                        err.put("errors", bve.getErrors());
                        err.put("details", bve.toString());
                        if (bve.treatAsWarning(toleranceBadFieldnamesAreWarnings)) {
                            // hackily prevents multiple identical warnings (like bad fieldname x N rows)
                            if (!warningDuplicate.contains(bve.toString())) {
                                dataWarnings.put(err);
                                warningDuplicate.add(bve.toString());
                            }
                        } else {
                            dataErrors.put(err);
                            rowValid = false;
                        }
                        System.out.println("[INFO] rowResult[" + rowNum + ", " + rowFieldName +
                            "]: " + bve);
                    } else {
                        System.out.println(
                            "[ERROR] Non-bulk exception (or something weird) for rowNum=" + rowNum +
                            ", rowFieldName=" + rowFieldName + ": " + fieldObj);
                        Exception ex = (Exception)fieldObj;
                        ex.printStackTrace();
                        throw new ServletException("cannot process rowResult[" + rowNum + ", " +
                                rowFieldName + "]: " + fieldObj);
                    }
                }
                if (rowValid) {
                    numRowsValid++;
                    // case where we simply didnt have the column for submitter, we add it now
                    if (!hasSubmitter) rowResult.put(submitterIDFieldName, defaultSubmitterIDBV);
                }
            }
            rtn.put("numberRows", validatedRows.size());
            rtn.put("numberRowsValid", numRowsValid);
            rtn.put("numberFieldsWarning", dataWarnings.length());
            rtn.put("numberFieldsError", dataErrors.length());
            rtn.put("numberFieldsValid", validFields.size());
            if (dataErrors.length() > 0) rtn.put("errors", dataErrors);
            List<File> validFiles = new ArrayList<File>();
            // now we only do "lightweight" (fast) file validation, as the MediaAsset creation
            // takes too long, and therefore we do that later in the background (if applicable)
            if (!validateOnly && (dataErrors.length() == 0)) {
                for (File file : files) {
                    String filename = file.getName();
                    if (!filenamesNeeded.contains(filename)) continue; // uploaded, but not referenced :(
                    // this takes about 30 sec for 1000 images :(
                    // if (!AssetStore.isValidImage(file))
                    // ... but this new method is less than 1 sec :)
                    if (!Util.fastFileValidation(file)) {
                        JSONObject err = new JSONObject();
                        err.put("filename", filename);
                        err.put("error", filename + " is not a valid file");
                        dataErrors.put(err);
                        System.out.println("[ERROR] image validation failed on " + filename);
                        continue;
                    }
                    validFiles.add(file);
                }
            }
            rtn.put("numberFilenamesReferenced", filenamesNeeded.size());
            rtn.put("numberFilesUploaded", files.size());
            rtn.put("numberFilesValid", validFiles.size());
            if (dataWarnings.length() > 0) rtn.put("warnings", dataWarnings);
            if (validateOnly) {
                rtn.put("validateOnly", true);
                rtn.put("success", (dataErrors.length() == 0));
                statusCode = 200;
            } else if ((dataErrors.length() > 0) && toleranceFailImportOnError) {
                statusCode = 400;
                rtn.put("errors", dataErrors);
            } else {
                // if we get here, it means we should attempt to create and persist objects for real
                // (we may have some errors in rows depending on tolerance)
                Util.mark("BEGIN createImport() for " + bulkImportId, startProcess);

                BulkImporter.logProgress(bulkImportId, "doPost: fg/bg split", startTime);
                rtn.put("processInBackground", processInBackground);
                if (processInBackground) {
                    // these need to be final
                    final boolean bgToleranceFailImportOnError = toleranceFailImportOnError;
                    final boolean bgSkipDetection = skipDetection;
                    final boolean bgSkipIdentification = skipIdentification;
                    final String currentUsername = currentUser.getUsername();
                    Runnable r = new Runnable() {
                        public void run() {
                            // make our background thread safely use our own Shepherd
                            Shepherd bgShepherd = new Shepherd(myShepherd.getContext());
                            bgShepherd.setAction("api.Bulk.processBackground");
                            bgShepherd.beginDBTransaction();

                            JSONObject bgEncAssets = null;
                            boolean success = false;
                            try {
                                User bgUser = bgShepherd.getUser(currentUsername);
                                initializeImportTask(bulkImportId, bgUser, payload,
                                    "processing-background");
                                int numNewErrors = dataErrors.length();
                                BulkImporter.logProgress(bulkImportId,
                                    "doPost: bg pre-createMediaAssets()", startTime);
                                Map<String, MediaAsset> maMap = createMediaAssets(bulkImportId,
                                    validFiles, dataErrors, bgShepherd, startTime);
                                BulkImporter.logProgress(bulkImportId,
                                    "doPost: bg post-createMediaAssets()", startTime);
                                numNewErrors = dataErrors.length() - numNewErrors;
                                boolean blockedByMAErrors = false;
                                if (numNewErrors > 0) {
                                    System.out.println(bulkImportId +
                                        " background createMediaAssets() failed: " + dataErrors);
                                    rtn.put("errors", dataErrors);
                                    importTaskSet(bulkImportId, null, null, dataErrors);
                                    if (bgToleranceFailImportOnError) blockedByMAErrors = true;
                                }
                                rtn.put("numberMediaAssetsCreated", maMap.size());
                                BulkImporter importer = new BulkImporter(bulkImportId,
                                    validatedRows, maMap, bgUser, bgShepherd);
                                JSONObject results = null;
                                if (!blockedByMAErrors) {
                                    try {
                                        BulkImporter.logProgress(bulkImportId,
                                            "doPost: bg pre-createImport()", startTime);
                                        results = importer.createImport();
                                        BulkImporter.logProgress(bulkImportId,
                                            "doPost: bg post-createImport()", startTime);
                                        success = true;
                                    } catch (ServletException ex) {
                                        // this will overwrite existing errors, but likely we dont have any here?
                                        rtn.put("errors", ex.toString());
                                        System.out.println(
                                            "ERROR: background importer on ImportTask " +
                                            bulkImportId + "failed with: " + ex);
                                        ex.printStackTrace();
                                    }
                                    System.out.println(bulkImportId + " IMPORTER RESULTS => " +
                                        results);
                                }
                                if (results != null)
                                    for (String rkey : results.keySet()) {
                                        rtn.put(rkey, results.get(rkey));
                                    }
                                if (!verboseReturn) {
                                    rtn.remove("mediaAssets");
                                    rtn.remove("encounters");
                                    rtn.remove("sightings");
                                    rtn.remove("individuals");
                                }
                                rtn.put("success", success);
                                if (success) {
                                    // we must use our shepherd, as encounters are associated with it
                                    ImportTask itask = bgShepherd.getImportTask(bulkImportId);
                                    if (itask == null) {
                                        System.out.println(
                                            "[ERROR] successful bg import could not load ImportTask for "
                                            + bulkImportId);
                                    } else {
                                        Util.mark("success; writing final ImportTask update");
                                        itask.setProcessingProgress(1.0D);
                                        itask.setEncounters(importer.getEncounters());
                                        itask.addLog("import complete");
                                        if (bgSkipDetection) {
                                            itask.addLog("detection skipped; task complete");
                                            itask.setStatus("complete");
                                        } else {
                                            itask.setStatus("imported");
                                            bgEncAssets = generateEncAssets(
                                                importer.getEncounters());
                                        }
                                        bgShepherd.storeNewImportTask(itask);
                                    }
                                } else {
                                    // i think errors will be set on task at this point
                                    importTaskSet(bulkImportId, "failed", null, null);
                                }
                                // taskShepherd.storeNewImportTask(bgTask);
                            } catch (Exception ex) {
                                System.out.println(
                                    "ERROR: background importing process on ImportTask " +
                                    bulkImportId + "failed with: " + ex);
                                ex.printStackTrace();
                            } finally {
                                if (success) {
                                    bgShepherd.commitDBTransaction();
                                } else {
                                    bgShepherd.rollbackDBTransaction();
                                }
                                bgShepherd.closeDBTransaction();
                                if (success && !bgSkipDetection)
                                    initiateIA(bulkImportId, bgSkipIdentification, bgEncAssets,
                                        matchingSetFilter);
                            }
                            rtn.put("processingTime", System.currentTimeMillis() - startProcess);
                            archiveBulkJson(rtn,
                                "backgroundComplete_" + (success ? "success" : "failed"));
                            Util.mark("END [background] createImport() for " + bulkImportId,
                                startProcess);
                            BulkImporter.logProgress(bulkImportId, "doPost: bg DONE", startTime);
                        }
                    };
                    new Thread(r).start();
                    rtn.put("processingTime", System.currentTimeMillis() - startProcess);
                    rtn.put("backgrounded", true);
                    rtn.put("success", true);
                    statusCode = 200;
                } else {
                    // foreground processing
                    initializeImportTask(bulkImportId, currentUser, payload,
                        "processing-foreground");
                    int numNewErrors = dataErrors.length();
                    BulkImporter.logProgress(bulkImportId, "doPost: fg pre-createMediaAssets()",
                        startTime);
                    Map<String, MediaAsset> maMap = createMediaAssets(bulkImportId, validFiles,
                        dataErrors, myShepherd, startTime);
                    BulkImporter.logProgress(bulkImportId, "doPost: fg post-createMediaAssets()",
                        startTime);
                    numNewErrors = dataErrors.length() - numNewErrors;
                    boolean blockedByMAErrors = false;
                    if (numNewErrors > 0) {
                        System.out.println(bulkImportId +
                            " foreground createMediaAssets() failed: " + dataErrors);
                        rtn.put("errors", dataErrors);
                        if (toleranceFailImportOnError) blockedByMAErrors = true;
                    }
                    rtn.put("numberMediaAssetsCreated", maMap.size());
                    if (blockedByMAErrors) {
                        importTaskSet(bulkImportId, "failed", null, dataErrors);
                        rtn.put("success", false);
                        statusCode = 400;
                    } else {
                        BulkImporter importer = new BulkImporter(bulkImportId, validatedRows, maMap,
                            currentUser, myShepherd);
                        BulkImporter.logProgress(bulkImportId, "doPost: fg pre-createImport()",
                            startTime);
                        JSONObject results = importer.createImport();
                        BulkImporter.logProgress(bulkImportId, "doPost: fg post-createImport()",
                            startTime);
                        for (String rkey : results.keySet()) {
                            rtn.put(rkey, results.get(rkey));
                        }
                        if (!verboseReturn) {
                            rtn.remove("mediaAssets");
                            rtn.remove("encounters");
                            rtn.remove("sightings");
                            rtn.remove("individuals");
                        }
                        // we must use our shepherd, as encounters are associated with it
                        ImportTask itask = myShepherd.getImportTask(bulkImportId);
                        if (itask == null) {
                            System.out.println(
                                "[ERROR] successful fg import could not load ImportTask for " +
                                bulkImportId);
                        } else {
                            Util.mark("success; writing final ImportTask update");
                            itask.setProcessingProgress(1.0D);
                            itask.setEncounters(importer.getEncounters());
                            itask.addLog("import complete");
                            if (skipDetection) {
                                itask.addLog("detection skipped; task complete");
                                itask.setStatus("complete");
                            } else {
                                itask.setStatus("imported");
                                encAssets = generateEncAssets(importer.getEncounters());
                            }
                            myShepherd.storeNewImportTask(itask);
                        }
                        rtn.put("success", true);
                        statusCode = 200;
                    }
                    rtn.put("processingTime", System.currentTimeMillis() - startProcess);
                    Util.mark("END [foreground] createImport() for " + bulkImportId, startProcess);
                    BulkImporter.logProgress(bulkImportId, "doPost: fg DONE", startTime);
                }
            }
        } catch (ServletException ex) { // should just be thrown, not caught (below)
            System.out.println("BulkImport.doPost() threw " + ex);
            ex.printStackTrace();
            throw ex;
        } catch (ApiException apiEx) {
            statusCode = 400;
            System.out.println("BulkImport.doPost() returning 400 due to " + apiEx + " errors=" +
                apiEx.getErrors());
            rtn.put("errors", apiEx.getErrors());
        } catch (Exception ex) {
            rtn.put("error", ex.toString());
            statusCode = 500;
            ex.printStackTrace();
        } finally {
            if ((statusCode == 200) && !validateOnly) {
                myShepherd.commitDBTransaction();
            } else {
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
            if ((statusCode == 200) && !skipDetection)
                initiateIA(dupId, skipIdentification, encAssets, matchingSetFilter);
        }
        rtn.put("statusCode", statusCode);
        response.setStatus(statusCode);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(rtn.toString());
        archiveBulkJson(rtn, "return" + statusCode);
        BulkImporter.logProgress(rtn.optString("bulkImportId", "(unknown)"), "doPost: fg EXIT",
            startTime);
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        int statusCode = 500;
        JSONObject rtn = new JSONObject();

        rtn.put("success", false);
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.Bulk.doDelete");
        myShepherd.beginDBTransaction();
        try {
            User currentUser = myShepherd.getUser(request);
            if ((currentUser == null) || !currentUser.isAdmin(myShepherd)) {
                response.setStatus(401);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"success\": false}");
                return;
            }
            String uri = request.getRequestURI();
            String[] args = uri.substring(8).split("/");
            if (args.length < 2) throw new ServletException("bad api path");
            String bulkImportId = args[1];
            ImportTask.deleteWithRelated(bulkImportId, currentUser, myShepherd);
            // above may throw IOException
            statusCode = 204;
            rtn.put("success", true);
        } catch (IOException ex) {
            ex.printStackTrace();
            statusCode = 400;
            rtn.put("error", ex.toString());
        } catch (ServletException ex) { // should just be thrown, not caught (below)
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (statusCode == 204) {
                myShepherd.commitDBTransaction();
            } else {
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        }
        response.setStatus(statusCode);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(rtn.toString());
    }

    // files should already be validated and "needed"
    private Map<String, MediaAsset> createMediaAssets(String bulkImportId, List<File> files,
        JSONArray dataErrors, Shepherd myShepherd, long startTime) {
        Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
        int ct = 0;

        for (File file : files) {
            ct++;
            String filename = file.getName();
            try {
                MediaAsset ma = UploadedFiles.makeMediaAsset(bulkImportId, file, myShepherd);
                maMap.put(filename, ma);
            } catch (ApiException apiEx) {
                JSONObject err = new JSONObject();
                err.put("filename", filename);
                err.put("mediaAssetError", true);
                err.put("errors", apiEx.getErrors());
                err.put("details", apiEx.toString());
                dataErrors.put(err);
                System.out.println("[ERROR] " + filename + " MediaAsset creation: " + apiEx);
            }
            if (ct % 100 == 1)
                BulkImporter.logProgress(bulkImportId, "doPost: createMediaAssets() ct=" + ct,
                    startTime);
            // just to save a little db overhead, we only do this every 10 files
            if (ct % 10 == 0) {
                // this 20% has to be coordinated with BulkImporter values
                Double progress = 0.2D * new Double(ct) / new Double(files.size());
                importTaskSet(bulkImportId, null, progress, null);
            }
        }
        System.out.println("[INFO] created " + maMap.size() + " MediaAssets from " + files.size() +
            " files");
        return maMap;
    }

    private void initializeImportTask(String id, User passedUser, JSONObject payload,
        String status) {
        if ((passedUser == null) || (passedUser.getId() == null)) {
            System.out.println("[WARNING] initializeImportTask(" + id + ") got null user: " +
                passedUser);
            return;
        }
        Shepherd taskShepherd = new Shepherd("context0");

        taskShepherd.setAction("BulkImport.initializeImportTask");
        taskShepherd.beginDBTransaction();
        try {
            User user = taskShepherd.getUser(passedUser.getId()); // needs to be on our shepherd
            ImportTask itask = taskShepherd.getImportTask(id);
            if (itask != null) {
                itask.addLog(
                    "WARNING! BulkImport api POST passed EXISTING bulkImportId, reusing this ImportTask");
                System.out.println(
                    "WARNING: BulkImport api POST passed EXISTING bulkImportId, reusing this ImportTask ***************** "
                    + itask);
            } else {
                itask = new ImportTask(user, id);
            }
            itask.setIATask(null);
            itask.setProcessingProgress(0.0D);
            itask.setEncounters(null);
            itask.setErrors(null);
            itask.setStatus(status);
            JSONObject passedParams = new JSONObject();
            for (String k : payload.keySet()) {
                if (k.equals("rows") || k.equals("fieldNames")) continue; // skip the data, basically
                passedParams.put(k, payload.get(k));
            }
            itask.setPassedParameters(passedParams);
            taskShepherd.storeNewImportTask(itask);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            taskShepherd.commitDBTransaction();
            taskShepherd.closeDBTransaction();
        }
        Util.mark("initializeImportTask(" + id + ", " + status + ")");
    }

    // this assumes task was created! and: we always ignore nulls
    private void importTaskSet(String id, String status, Double progress, JSONArray errors) {
        if (id == null) return;
        Shepherd taskShepherd = new Shepherd("context0");
        taskShepherd.setAction("BulkImport.importTaskSet");
        taskShepherd.beginDBTransaction();
        try {
            ImportTask itask = taskShepherd.getImportTask(id);
            if (itask == null) return;
            if (status != null) {
                itask.setStatus(status);
                itask.addLog("status: " + status);
            }
            if (progress != null) itask.setProcessingProgress(progress);
            if (errors != null) {
                itask.setErrors(errors);
                itask.addLog("errors: " + errors);
            }
            taskShepherd.storeNewImportTask(itask);
        } finally {
            taskShepherd.commitDBTransaction();
            taskShepherd.closeDBTransaction();
        }
        Util.mark("importTaskSet(" + id + ": " + status + ", " + progress + "% [etc] )");
    }

    // based on behavior in sendToIA() from import.jsp
    private void initiateIA(String importId, boolean skipIdent, JSONObject encAssets,
        JSONObject matchingSetFilter) {
        Util.mark("[INFO] > > > > > > BulkImport.initiateIA(" + importId + ") with encAssets=" +
            encAssets);
        if ((importId == null) || (encAssets == null) || (encAssets.length() < 1)) return;
        String context = "context0";
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("BulkImport.initiateIA()");
        myShepherd.beginDBTransaction();
        try {
            ImportTask itask = myShepherd.getImportTask(importId);
            if (itask == null) throw new IOException("could not load ImportTask " + importId);
            itask.setStatus("processing-detection");
            myShepherd.getPM().makePersistent(itask);
            // build data to send to handleBulkImport()
            JSONObject taskParams = new JSONObject();
            taskParams.put("importTaskId", importId);
            taskParams.put("skipIdent", skipIdent);
            if (!skipIdent) taskParams.put("matchingSetFilter", matchingSetFilter);
            JSONObject data = new JSONObject();
            data.put("taskParameters", taskParams);
            data.put("bulkImport", encAssets);
            Util.mark("[INFO] > > > > > > data => " + data);
            JSONObject res = new JSONObject();
            res.put("success", false);
            JSONObject rtn = IAGateway.handleBulkImport(data, res, myShepherd, context,
                IA.getBaseURL(context));
            Util.mark("[INFO] > > > > > > rtn => " + rtn);
        } catch (Exception ex) {
            System.out.println("[ERROR] BulkImport.initiateIA(" + importId + ") failed with " + ex);
            ex.printStackTrace();
        } finally {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    private JSONObject generateEncAssets(List<Encounter> encs) {
        if (Util.collectionIsEmptyOrNull(encs)) return null;
        int numAssets = 0;
        JSONObject ea = new JSONObject();
        for (Encounter enc : encs) {
            JSONArray maIds = new JSONArray();
            for (MediaAsset ma : enc.getMedia()) {
                if ((ma != null) && (ma.getIdInt() > 0)) {
                    maIds.put(ma.getIdInt());
                    numAssets++;
                }
            }
            ea.put(enc.getId(), maIds);
        }
        if (numAssets < 1) return null;
        return ea;
    }

    private void archiveBulkJson(JSONObject payload, String suffix) {
        String path = BulkImportUtil.bulkImportArchiveFilepath(payload.optString("bulkImportId",
            "__FAIL__"), suffix);

        try {
            Util.writeToFile(payload.toString(4), path);
        } catch (java.io.FileNotFoundException ex) {}
    }

    private static JSONObject taskJson(ImportTask task, boolean detailed, Shepherd myShepherd) {
        JSONObject jt = new JSONObject();

        jt.put("id", task.getId());
        jt.put("creator",
            task.getCreator() ==
            null ? JSONObject.NULL : task.getCreator().infoJSONObject(myShepherd.getContext()));
        jt.put("dateCreated", task.getCreated());
        jt.put("sourceName", task.getSourceName());
        jt.put("legacy", task.isLegacy());
        jt.put("errors", task.getErrors());
        // "importPercent" was deemed more consistent and useful
        jt.put("importPercent", task.getProcessingProgress());
        jt.put("numberEncounters", task.numberEncounters());

        // FIXME - this status may get tweaked below. hacky. we should figure out a way to
        // set it properly when IA pipeline is finished
        String persistedStatus = task.getStatus();
        jt.put("status", persistedStatus);
        jt.put("_statusPersisted", persistedStatus);
        JSONObject iaSummary = task.iaSummaryJson();
        if (detailed) jt.put("iaSummary", iaSummary);
        if (iaSummary.optBoolean("pipelineStarted", false)) {
            if (iaSummary.optBoolean("pipelineComplete", false)) {
                jt.put("status", "complete");
            } else if ("complete".equals(persistedStatus)) {
                // i guess this means we dont trust this complete, so...
                jt.put("status", "processing-pipeline");
            }
        }
        Set<String> indivIds = new HashSet<String>();
        if (task.numberEncounters() > 0) {
            JSONArray encArr = new JSONArray();
            for (Encounter enc : task.getEncounters()) {
                JSONObject encj = new JSONObject();
                encj.put("id", enc.getId());
                if (detailed) {
                    encj.put("id", enc.getId());
                    encj.put("date", enc.getDate());
                    encj.put("occurrenceId", enc.getOccurrenceID());
                    if (enc.hasMarkedIndividual()) {
                        indivIds.add(enc.getIndividualID());
                        encj.put("individualId", enc.getIndividualID());
                    }
                    encj.put("numberMediaAssets", enc.numAnnotations());
                    User sub = enc.getSubmitterUser(myShepherd);
                    if (sub != null)
                        encj.put("submitter", sub.infoJSONObject(myShepherd.getContext()));
                }
                encArr.put(encj);
            }
            jt.put("encounters", encArr);
            jt.put("numberMarkedIndividuals", indivIds.size());
        }
        return jt;
    }
}
