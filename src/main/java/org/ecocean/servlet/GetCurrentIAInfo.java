package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;

import java.io.*;

public class GetCurrentIAInfo extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        System.out.println("==> In GetCurrentIAInfo Servlet ");

        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("GetCurrentIAInfo.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        String action = j.optString("action", null);
        String onlyIdentifiable = j.optString("onlyIdentifiable", null);
        try {
            res.put("success","false");
            if ("getIAInfoForEncounter".equals(action)) {
                String encNum = j.optString("encounterId", null);
                if (Util.stringExists(encNum)&&myShepherd.isEncounter(encNum)) {
                    Encounter enc = myShepherd.getEncounter(encNum);
                    ArrayList<Annotation> anns = enc.getAnnotations();
                    if (anns!=null&&!anns.isEmpty()) {
                        JSONArray resArr = new JSONArray();
                        for (Annotation ann : anns) {
                            if ("true".equals(onlyIdentifiable)&&!IBEISIA.validForIdentification(ann, context)) continue; 
                            JSONObject annIAJSON = getIAJSONForAnnotation(myShepherd, ann);
                            resArr.put(annIAJSON);
                        }
                        res.put("IAInfo", resArr); 
                    }
                    res.put("success","true");
                    response.setStatus(HttpServletResponse.SC_OK);
                }
            }

            out.println(res);
            out.close();

        } catch (NullPointerException npe) {
            npe.printStackTrace();
            addErrorMessage(res, "NullPointerException npe");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (JSONException je) {
            je.printStackTrace();
            addErrorMessage(res, "JSONException je");
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            addErrorMessage(res, "Exception e");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(res);
        }
    }

    private JSONObject getIAJSONForAnnotation(Shepherd myShepherd, Annotation ann) {
        JSONObject annIA = new JSONObject();
        try {
            annIA.put("id", ann.getId());
            annIA.put("iaClass", ann.getIAClass());
            annIA.put("identificationStatus", ann.getIdentificationStatus());
            
            MediaAsset ma = ann.getMediaAsset();
            annIA.put("assetId", ma.getId());
            annIA.put("assetDetectionStatus", ma.getDetectionStatus());
            annIA.put("assetWebURL", Util.scrubURL(ma.webURL()));
            
            String lastTask = "";
            List<Task> rootTasks = ann.getRootIATasks(myShepherd);
            if (rootTasks!=null&&!rootTasks.isEmpty()) {
                annIA.put("lastTaskId", rootTasks.get(0).getId());
            }
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
        return annIA;
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }


}
