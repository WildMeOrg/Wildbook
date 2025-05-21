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

import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

/*
   import org.ecocean.Annotation;
   import org.ecocean.CommonConfiguration;
   import org.ecocean.ContextConfiguration;
   import org.ecocean.IAJsonProperties;
   import org.ecocean.Keyword;
   import org.ecocean.LabeledKeyword;
   import org.ecocean.LocationID;
   import org.ecocean.Organization;
   import org.ecocean.Project;
   import org.ecocean.servlet.ReCAPTCHA;
   import org.ecocean.Setting;
   import org.ecocean.shepherd.core.ShepherdProperties;
   import org.ecocean.Util;
   import org.ecocean.Util.MeasurementDesc;
 */

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
            String uri = request.getRequestURI();
            String[] args = uri.substring(8).split("/");
            if (args.length < 2) throw new IOException("invalid api endpoint");
            String bulkImportId = args[1];
            if (!Util.isUUID(bulkImportId)) throw new IOException("invalid bulk import id passed");
            if (args.length == 2) {
                rtn.put("message", "not yet implemented");
            } else if (args[2].equals("files")) {
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
            Set<String> filenamesNeeded = new HashSet<String>();
            List<Map<String, Object> > validatedRows = new ArrayList<Map<String, Object> >();
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
            Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
            if (!validateOnly && (dataErrors.length() == 0)) {
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
            }
            rtn.put("numberMediaAssetsCreated", maMap.size());
            rtn.put("numberFilenamesReferenced", filenamesNeeded.size());
            rtn.put("numberFilesUploaded", files.size());
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
                System.out.println("================= about to createImport for " + bulkImportId +
                    " =================");
                BulkImporter importer = new BulkImporter(validatedRows, maMap, currentUser,
                    myShepherd);
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
                rtn.put("success", true);
                rtn.put("note", "INCOMPLETE IMPORT CREATION; in development");
                statusCode = 200;
            }
            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());
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
}
