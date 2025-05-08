package org.ecocean.api;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.bulk.*;

import org.ecocean.media.MediaAsset;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.servlet.ServletUtilities;

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

/*
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
*/

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

            // dont create anything, just check data (always returns a 200,
            // but returned "success" (boolean) indicates total validity
            validateOnly = payload.optBoolean("validateOnly", false);
            if (validateOnly) {
                toleranceFailImportOnError = false;
                toleranceSkipRowOnError = false;
            }

            List<File> files = UploadedFiles.findFiles(request, bulkImportId);

            JSONArray fieldNamesArr = payload.optJSONArray("fieldNames");
            Set<String> fieldNames = null;
            if (fieldNamesArr != null) {
                fieldNames = new LinkedHashSet<String>();
                for (int i = 0 ; i < fieldNamesArr.length() ; i++) {
                    String fn = fieldNamesArr.optString(i, null);
                    if (fn == null) throw new ServletException("could not find field name at i=" + i);
                    fieldNames.add(fn);
                }
            }

            List<Map<String, Object>> validatedRows = new ArrayList<Map<String, Object>>();
            for (int i = 0 ; i < rows.length() ; i++) {
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

                //now we check if we actually have all the mediaAssets we need
                for (String fieldName : vrow.keySet()) {
                    if (!fieldName.startsWith("Encounter.mediaAsset")) continue;
                    if (vrow.get(fieldName) instanceof Exception) continue;
                    BulkValidator bv = (BulkValidator)vrow.get(fieldName);
                    if (bv.getValue() == null) continue;
                    if (!Util.containsFilename(files, bv.getValue().toString())) {
                        System.out.println(bv.getValue() + " not found in uploaded files");
                        vrow.put(fieldName, new BulkValidatorException("file '" + bv.getValue().toString() + "' not found in uploaded files", ApiException.ERROR_RETURN_CODE_REQUIRED));
                    }
                }
            }

            int numRowsValid = 0;
            List<BulkValidator> validFields = new ArrayList<BulkValidator>();
            JSONArray dataErrors = new JSONArray();
            for (int rowNum = 0 ; rowNum < validatedRows.size() ; rowNum++) {
                boolean rowValid = true;
                Map<String, Object> rowResult = validatedRows.get(rowNum);
                for (String rowFieldName : rowResult.keySet()) {
                    Object fieldObj = rowResult.get(rowFieldName);
                    if (fieldObj instanceof BulkValidator) {
                        validFields.add((BulkValidator)fieldObj);
                    } else if (fieldObj instanceof BulkValidatorException) {
                        JSONObject err = new JSONObject();
                        err.put("rowNumber", rowNum);
                        err.put("fieldName", rowFieldName);
                        BulkValidatorException bve = (BulkValidatorException)fieldObj;
                        err.put("errors", bve.getErrors());
                        err.put("details", bve.toString());
                        dataErrors.put(err);
                        System.out.println("[INFO] rowResult[" + rowNum + ", " + rowFieldName + "]: " + bve);
                        rowValid = false;
                    } else {
                        System.out.println("[ERROR] Non-bulk exception (or something weird) for rowNum=" + rowNum + ", rowFieldName=" + rowFieldName + ": " + fieldObj);
                        Exception ex = (Exception)fieldObj;
                        ex.printStackTrace();
                        throw new ServletException("cannot process rowResult[" + rowNum + ", " + rowFieldName + "]: " + fieldObj);
                    }
                }
                if (rowValid) numRowsValid++;
            }

            rtn.put("numberRows", validatedRows.size());
            rtn.put("numberRowsValid", numRowsValid);
            rtn.put("numberFieldsError", dataErrors.length());
            rtn.put("numberFieldsValid", validFields.size());
            if (dataErrors.length() > 0) rtn.put("errors", dataErrors);

            Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();
            if (!validateOnly && (dataErrors.length() == 0)) {
                for (File file : files) {
                    try {
                        MediaAsset ma = UploadedFiles.makeMediaAsset(bulkImportId, file, myShepherd);
                        maMap.put(file.getName(), ma);
                    } catch (ApiException apiEx) {
                        JSONObject err = new JSONObject();
                        err.put("filename", file.getName());
                        err.put("mediaAssetError", true);
                        err.put("errors", apiEx.getErrors());
                        err.put("details", apiEx.toString());
                        dataErrors.put(err);
                        System.out.println("[ERROR] " + file.getName() + " MediaAsset creation: " + apiEx);
                    }
                }
                System.out.println("[INFO] created " + maMap.size() + " MediaAssets from " + files.size() + " files");
            }

            if (validateOnly) {
                rtn.put("validateOnly", true);
                rtn.put("success", (dataErrors.length() == 0));

            } else if (dataErrors.length() > 0) {
                statusCode = 400;
                rtn.put("errors", dataErrors);

            } else {
                rtn.put("success", true);
                /// TODO FIXME try to do actual import and make data!  :o
                rtn.put("note", "FAKE SUCCESS; nothing actually created");
                statusCode = 200;
            }

            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());

        } catch (ServletException ex) {  // should just be thrown, not caught (below)
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

        } catch (ServletException ex) {  // should just be thrown, not caught (below)
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
