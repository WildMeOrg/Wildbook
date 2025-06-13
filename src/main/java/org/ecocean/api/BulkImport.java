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
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.importer.ImportTask;
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
        boolean validateOnly = false;
        long startProcess = System.currentTimeMillis();
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.Bulk.doPost");
        myShepherd.beginDBTransaction();
        try {
            User currentUser = myShepherd.getUser(request);
            if (currentUser == null) {
                response.setStatus(401);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"success\": false}");
                return;
            }
            JSONObject rtn = new JSONObject();
            rtn.put("success", false);

            JSONObject payload = ServletUtilities.jsonFromHttpServletRequest(request);
            if (payload == null) throw new ServletException("empty payload");
            JSONArray rows = payload.optJSONArray("rows");
            if (rows == null) throw new ServletException("no rows in payload");
            String bulkImportId = payload.optString("bulkImportId", null);
            if (bulkImportId == null) throw new ServletException("bulkImportId is required");
            rtn.put("bulkImportId", bulkImportId);
            archiveBulkJson(payload, "payload");

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
            }
            // we might grow matching filter stuff later, but for now we only have locationIds from ui
            Set<String> matchingLocations = new HashSet<String>();
            JSONArray mloc = payload.optJSONArray("matchingLocations");
            if (mloc != null) {
                for (int i = 0; i < mloc.length(); i++) {
                    String locId = mloc.optString(i, null);
                    if (locId == null) continue;
                    // VALIDATE locId  FIXME
                    matchingLocations.add(locId);
                }
            }
            boolean skipDetection = payload.optBoolean("skipDetection", false);
            // if you skipDetection, you cant do identification, so:
            boolean skipIdentification = skipDetection || payload.optBoolean("skipIdentification",
                false);
            Set<String> filenamesNeeded = new HashSet<String>();
            final List<Map<String, Object> > validatedRows = new ArrayList<Map<String, Object> >();
            for (int i = 0; i < rows.length(); i++) {
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
/*
                for (File file : files) {
                    String filename = file.getName();
                    if (!filenamesNeeded.contains(filename)) continue; // uploaded, but not referenced :(
                    try {
                        MediaAsset ma = UploadedFiles.makeMediaAsset(bulkImportId, file,
                            myShepherd);
                        maMap.put(filename, ma);
                    } catch (ApiException apiEx) {
                        JSONObject err = new JSONObject();
                        err.put("filename", filename);
                        err.put("mediaAssetError", true);
                        err.put("errors", apiEx.getErrors());
                        err.put("details", apiEx.toString());
                        dataErrors.put(err);
                        System.out.println("[ERROR] " + filename + " MediaAsset creation: " +
                            apiEx);
                    }
                }
                System.out.println("[INFO] created " + maMap.size() + " MediaAssets from " +
                    files.size() + " files");
 */
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

                rtn.put("processInBackground", processInBackground);
                if (processInBackground) {
                    final boolean bgToleranceFailImportOnError = toleranceFailImportOnError; // needs to be final
                    Runnable r = new Runnable() {
                        public void run() {
                            // make our background thread safely use our own Shepherd
                            Shepherd bgShepherd = new Shepherd(myShepherd.getContext());
                            bgShepherd.setAction("api.Bulk.processBackground");
                            bgShepherd.beginDBTransaction();
                            // task gets own shepherd as it needs to write even if we rollback bgShepherd
                            Shepherd taskShepherd = new Shepherd(myShepherd.getContext());
                            taskShepherd.setAction("api.Bulk.taskBackground");
                            taskShepherd.beginDBTransaction();

                            boolean success = false;
                            try {
                                User bgUser = bgShepherd.getUser(currentUser.getUsername());
                                ImportTask bgTask = getOrCreateImportTask(taskShepherd, bgUser,
                                    bulkImportId, payload);
                                bgTask.setStatus("processing-background");
                                taskShepherd.storeNewImportTask(bgTask);
                                taskShepherd.commitDBTransaction();
                                taskShepherd.beginDBTransaction();
                                int numNewErrors = dataErrors.length();
                                Map<String, MediaAsset> maMap = createMediaAssets(bulkImportId,
                                    validFiles, dataErrors, bgShepherd);
                                numNewErrors = dataErrors.length() - numNewErrors;
                                boolean blockedByMAErrors = false;
                                if (numNewErrors > 0) {
                                    System.out.println(bulkImportId +
                                        " background createMediaAssets() failed: " + dataErrors);
                                    rtn.put("errors", dataErrors);
                                    bgTask.setErrors(dataErrors);
                                    if (bgToleranceFailImportOnError) blockedByMAErrors = true;
                                }
                                rtn.put("numberMediaAssetsCreated", maMap.size());
                                BulkImporter importer = new BulkImporter(bulkImportId,
                                    validatedRows, maMap, bgUser, bgShepherd);
                                JSONObject results = null;
                                if (!blockedByMAErrors) {
                                    try {
                                        results = importer.createImport();
                                        success = true;
                                    } catch (ServletException ex) {
                                        // this will overwrite existing errors, but likely we dont have any here?
                                        rtn.put("errors", ex.toString());
                                        System.out.println(
                                            "ERROR: background importer on Import Task " +
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
                                    bgTask.setEncounters(importer.getEncounters());
                                    bgTask.setStatus("imported");
                                    bgTask.setProcessingProgress(1.0D);
                                    bgTask.addLog("import complete");
                                } else {
                                    bgTask.setStatus("failed");
                                    bgTask.addLog("errors: " + rtn.get("errors"));
                                }
                                taskShepherd.storeNewImportTask(bgTask);
                            } catch (Exception ex) {
                                System.out.println(
                                    "ERROR: background importing process on Import Task " +
                                    bulkImportId + "failed with: " + ex);
                                ex.printStackTrace();
                            } finally {
                                taskShepherd.commitDBTransaction();
                                taskShepherd.closeDBTransaction();
                                if (success) {
                                    bgShepherd.commitDBTransaction();
                                } else {
                                    bgShepherd.rollbackDBTransaction();
                                }
                                bgShepherd.closeDBTransaction();
                            }
                            rtn.put("processingTime", System.currentTimeMillis() - startProcess);
                            archiveBulkJson(rtn,
                                "backgroundComplete_" + (success ? "success" : "failed"));
                            Util.mark("END [background] createImport() for " + bulkImportId,
                                startProcess);
                        }
                    };
                    new Thread(r).start();
                    rtn.put("backgrounded", true);
                    rtn.put("success", true);
                    statusCode = 200;
                } else {
                    // foreground processing

                    // we need to use our own shepherd here as we rollback the main one, sigh
                    Shepherd taskShepherd = new Shepherd(myShepherd.getContext());
                    taskShepherd.beginDBTransaction();
                    ImportTask itask = getOrCreateImportTask(taskShepherd, currentUser,
                        bulkImportId, payload);
                    itask.setStatus("processing-foreground");
                    taskShepherd.storeNewImportTask(itask);
                    taskShepherd.commitDBTransaction();
                    taskShepherd.beginDBTransaction();

                    int numNewErrors = dataErrors.length();
                    Map<String, MediaAsset> maMap = createMediaAssets(bulkImportId, validFiles,
                        dataErrors, myShepherd);
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
                        itask.setStatus("failed");
                        itask.setErrors(dataErrors);
                        itask.addLog("errors: " + dataErrors);
                        rtn.put("success", false);
                        statusCode = 400;
                    } else {
                        BulkImporter importer = new BulkImporter(bulkImportId, validatedRows, maMap,
                            currentUser, myShepherd);
                        JSONObject results = importer.createImport();
                        for (String rkey : results.keySet()) {
                            rtn.put(rkey, results.get(rkey));
                        }
                        if (!verboseReturn) {
                            rtn.remove("mediaAssets");
                            rtn.remove("encounters");
                            rtn.remove("sightings");
                            rtn.remove("individuals");
                        }
                        itask.setEncounters(importer.getEncounters());
                        itask.setStatus("imported");
                        itask.setProcessingProgress(1.0D);
                        itask.addLog("import complete");
                        rtn.put("success", true);
                        statusCode = 200;
                    }
                    taskShepherd.storeNewImportTask(itask);
                    taskShepherd.commitDBTransaction();
                    taskShepherd.closeDBTransaction();
                    rtn.put("processingTime", System.currentTimeMillis() - startProcess);
                    Util.mark("END [foreground] createImport() for " + bulkImportId, startProcess);
                }
            }
            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());
            archiveBulkJson(rtn, "return" + statusCode);
        } catch (ServletException ex) { // should just be thrown, not caught (below)
            throw ex;
        } catch (Exception ex) {
            statusCode = 500;
            ex.printStackTrace();
        } finally {
            if ((statusCode == 200) && !validateOnly) {
                myShepherd.commitDBTransaction();
            } else {
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        }
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        int statusCode = 500;
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
            JSONObject rtn = new JSONObject();
            String uri = request.getRequestURI();
            String[] args = uri.substring(22).split("/");
            if (args.length < 2) throw new ServletException("Bad path");
            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());
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
    }

    // files should already be validated and "needed"
    private Map<String, MediaAsset> createMediaAssets(String bulkImportId, List<File> files,
        JSONArray dataErrors, Shepherd myShepherd) {
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
            // just to save a little db overhead, we only do this every 10 files
            if (ct % 10 == 0) {
                // we want this to be its own shepherd for the sake of committing without affecting main shepherd
                Shepherd taskShepherd = new Shepherd(myShepherd.getContext());
                taskShepherd.setAction("BulkImport.createMediaAssets");
                taskShepherd.beginDBTransaction();
                try {
                    ImportTask itask = taskShepherd.getImportTask(bulkImportId);
                    if (itask != null) {
                        // this 20% has to be coordinated with BulkImporter values
                        Double progress = 0.2D * new Double(ct) / new Double(files.size());
                        itask.setProcessingProgress(progress);
                        taskShepherd.storeNewImportTask(itask);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    taskShepherd.commitDBTransaction();
                    taskShepherd.closeDBTransaction();
                }
            }
        }
        System.out.println("[INFO] created " + maMap.size() + " MediaAssets from " + files.size() +
            " files");
        return maMap;
    }

    private ImportTask getOrCreateImportTask(Shepherd myShepherd, User user, String id,
        JSONObject payload) {
        ImportTask itask = myShepherd.getImportTask(id);

        if (itask != null) {
            itask.addLog(
                "WARNING! BulkImport api POST passed EXISTING bulkImportId, reusing this ImportTask");
            System.out.println(
                "WARNING: BulkImport api POST passed EXISTING bulkImportId, reusing this ImportTask ***************** "
                + itask);
        } else {
            itask = new ImportTask(user, id);
        }
        itask.setProcessingProgress(0.0D);
        itask.setEncounters(null);
        JSONObject passedParams = new JSONObject();
        for (String k : payload.keySet()) {
            if (k.equals("rows") || k.equals("fieldNames")) continue; // skip the data, basically
            passedParams.put(k, payload.get(k));
        }
        itask.setPassedParameters(passedParams);
        return itask;
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
        jt.put("status", task.getStatus());
        jt.put("errors", task.getErrors());
        jt.put("processingProgress", task.getProcessingProgress());
        jt.put("numberEncounters", task.numberEncounters());
        if (detailed) jt.put("iaSummary", task.iaSummaryJson());
/*  THIS SHOULD BE REPLACED BY iaSummary ABOVE
        List<MediaAsset> mas = task.getMediaAssets();
        int numMA = 0;
        int numAnn = 0;
        int numAcm = 0;
        if (mas != null)
            for (MediaAsset ma : mas) {
                numMA++;
                numAnn += ma.numAnnotations();
                if (ma.getAcmId() != null) numAcm++;
            }
        jt.put("numberMediaAssets", numMA);
        jt.put("numberAnnotations", numAnn);
        jt.put("numberMediaAssetACMIds", numAcm);
        // not sure what "valid for image analysis" means FIXME
        jt.put("numberMediaAssetValidIA", numMA);
 */

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
