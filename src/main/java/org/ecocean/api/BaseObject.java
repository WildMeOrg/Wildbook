package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Base;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Project;
import org.ecocean.resumableupload.UploadServlet;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

// note: this is for use on any Base object (MarkedIndividual, Encounter, Occurrence)
public class BaseObject extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String uri = request.getRequestURI();
        String[] args = uri.substring(8).split("/");
        if (args.length < 1) throw new ServletException("Bad path");
        // System.out.println("args => " + java.util.Arrays.toString(args));

        JSONObject payload = null;
        String requestMethod = request.getMethod();
        if (requestMethod.equals("POST")) {
            payload = ServletUtilities.jsonFromHttpServletRequest(request);
        }

        if (!ReCAPTCHA.sessionIsHuman(request)) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            return;
        }

/*
        if (!(args[0].equals("encounters") || args[0].equals("individuals") || args[0].equals("occurrences")))
            throw new ServletException("Bad class");
*/
        payload.put("_class", args[0]);

        JSONObject rtn = null;
        if (requestMethod.equals("POST")) {
            rtn = processPost(request, args, payload);
        } else if (requestMethod.equals("GET")) {
            rtn = processGet(request, args);
        } else {
            throw new ServletException("Invalid method");
        }
        int statusCode = rtn.optInt("statusCode", 200);

        response.setStatus(statusCode);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(rtn.toString());
    }

    protected JSONObject processPost(HttpServletRequest request, String[] args, JSONObject payload)
    throws ServletException, IOException {
        if (payload == null) throw new ServletException("empty payload");
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        List<File> files = findFiles(request, payload);
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.BaseObject.processPost");
        myShepherd.beginDBTransaction();
        User currentUser = myShepherd.getUser(request);

        Base obj = null;
        try {
            String cls = payload.optString("_class");
            switch (cls) {
            case "encounters":
                obj = Encounter.createFromApi(payload, files);
                break;
            case "occurrences":
                obj = Occurrence.createFromApi(payload, files);
                break;
            case "individuals":
                obj = MarkedIndividual.createFromApi(payload, files);
                break;
            default:
                throw new ApiException("bad class");
            }
            rtn.put("id", obj.getId());
            rtn.put("class", cls);
            rtn.put("success", true);

        } catch (ApiException apiEx) {
            rtn.put("statusCode", 400);
            rtn.put("errors", apiEx.getErrors());
        }

        if ((obj != null) && (rtn.optInt("statusCode", 0) == 200)) {
            myShepherd.commitDBTransaction();
        } else {
            myShepherd.rollbackDBTransaction();
        }
        myShepherd.closeDBTransaction();
        return rtn;
    }

    protected JSONObject processGet(HttpServletRequest request, String[] args)
    throws ServletException, IOException {
        JSONObject rtn = new JSONObject();
        return rtn;
    }


    private List<File> findFiles(HttpServletRequest request, JSONObject payload)
    throws IOException {
        List<File> files = new ArrayList<File>();
        if (payload == null) return files;
        String submissionId = payload.optString("submissionId", null);
        if (submissionId == null) {
            System.out.println("WARNING: submissionId required; no files possible");
            return files;
        }

        Map<String, String> values = new HashMap<String, String>();
        values.put("submissionId", submissionId);
        File uploadDir = new File(UploadServlet.getUploadDir(request, values));
        System.out.println("findFiles() uploadDir=" + uploadDir);
        if (!uploadDir.exists()) throw new IOException("uploadDir for submissionId=" + submissionId + " does not exist");

        List<String> filenames = new ArrayList<String>();
        JSONArray fnArr = payload.optJSONArray("assetFilenames");
        if (fnArr != null) {
            for (int i = 0 ; i < fnArr.length() ; i++) {
                String fn = fnArr.optString(i, null);
                if (fn != null) filenames.add(fn);
            }
        } else {
            for (File f : uploadDir.listFiles()) {
                filenames.add(f.getName());
            }
        }
        if (filenames.size() < 1) return files;
        for (String fname : filenames) {
            File file = new File(uploadDir, fname);
            if (!file.exists()) throw new IOException(file + " does not exist in uploadDir");
            files.add(file);
        }
        System.out.println("findFiles(): files=" + files);
        return files;
    }
}
