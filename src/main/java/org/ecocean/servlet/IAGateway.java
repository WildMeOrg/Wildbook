/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;


import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.media.*;
import org.ecocean.Annotation;
import org.ecocean.RestClient;
import org.ecocean.identity.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URL;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;


public class IAGateway extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String getOut = "";

    if (request.getParameter("getJobResult") != null) {
        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        try {
            res = IBEISIA.getJobResult(request.getParameter("getJobResult"));
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
        response.setContentType("text/plain");
        getOut = res.toString();

    } else if (request.getParameter("getDetectReviewHtml") != null) {
        String jobID = request.getParameter("getDetectReviewHtml");
        int offset = 0;
        if (request.getParameter("offset") != null) {
            try {
                offset = Integer.parseInt(request.getParameter("offset"));
            } catch (NumberFormatException ex) {}
        }
        JSONObject res = null;
        try {
            res = IBEISIA.getJobResult(jobID);
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
System.out.println("res(" + jobID + "[" + offset + "]) -> " + res);
        if ((res == null) || (res.optJSONObject("response") == null) || (res.getJSONObject("response").optJSONObject("json_result") == null) || (res.getJSONObject("response").getJSONObject("json_result").optJSONArray("results_list") == null) || (res.getJSONObject("response").getJSONObject("json_result").optJSONArray("image_uuid_list") == null)) {
            getOut = "<div>invalid job ID " + jobID + "</div>";
            System.out.println("ERROR: invalid jobid for res(" + jobID + "[" + offset + "]) -> " + res);
        } else {
            JSONArray rlist = res.getJSONObject("response").getJSONObject("json_result").getJSONArray("results_list");
            JSONArray ilist = res.getJSONObject("response").getJSONObject("json_result").getJSONArray("image_uuid_list");
            if ((offset > rlist.length() - 1) || (offset < 0)) offset = 0;
            if (offset > ilist.length() - 1) offset = 0;
            String url = CommonConfiguration.getProperty("IBEISIARestUrlDetectReview", "context0");
            if (url == null) throw new IOException("IBEISIARestUrlDetectionReview url not set");
            url += "?image_uuid=" + ilist.getJSONObject(offset).toString() + "&";
            url += "result_list=" + rlist.getJSONArray(offset).toString() + "&";
            url += "callback_url=" + "http://example.com/" + "&callback_method=POST";
            try {
System.out.println("url --> " + url);
                URL u = new URL(url);
                JSONObject rtn = RestClient.get(u);
                if ((rtn.optString("response", null) == null) || (rtn.optJSONObject("status") == null) ||
                    !rtn.getJSONObject("status").optBoolean("success", false)) {
                    getOut = "<div>invalid response: <xmp>" + rtn.toString() + "</xmp></div>";
                } else {
                    getOut = rtn.getString("response");
                    if (request.getParameter("test") != null) {
                        getOut = "<html><head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.2.0/jquery.min.js\"></script></head><body>" + getOut + "</body></html>";
                    }
                }
            } catch (Exception ex) {
                getOut = "<div>Error: " + ex.toString() + "</div>";
            }

/*
    public static JSONObject getJobResult(String jobID) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlGetJobResult", "context0");
            getOut = "(url -> " + url + ")";
        }
*/

        }
    }

    PrintWriter out = response.getWriter();
    out.println(getOut);
    out.close();
  }




  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();

    JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
    JSONObject res = new JSONObject();

    if (j.optJSONArray("detect") != null) {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        JSONArray ids = j.getJSONArray("detect");
        for (int i = 0 ; i < ids.length() ; i++) {
            int id = ids.optInt(i, 0);
System.out.println(id);
            if (id < 1) continue;
            MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
            if (ma != null) mas.add(ma);
        }
        if (mas.size() > 0) {
            try {
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                res.put("sendMediaAssets", IBEISIA.sendMediaAssets(mas));
                res.put("sendDetect", IBEISIA.sendDetect(mas, baseUrl));
            } catch (Exception ex) {
                throw new IOException(ex.toString());
            }
        }

    //right now we only take a single Annotation id and figure out which MediaAsset to use
    } else if ((j.optString("identify", null) != null) && (j.optString("species", null) != null) && (j.optString("genus", null) != null)) {
        try {
            Annotation qann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, j.getString("identify")), true)));
            String species = j.getString("species");
            String genus = j.getString("genus");
            if (qann == null) {
                res.put("error", "invalid Annotation id " + j.getString("identify"));

            } else {
                String taskID = Util.generateUUID();
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                ArrayList<Encounter> encs = myShepherd.getAllEncountersForSpecies(genus, species);
                JSONObject ires = IBEISIA.beginIdentify(qann, encs, myShepherd, Util.taxonomyString(genus, species), taskID, baseUrl, context);
                //res.put("beginIdentify", ires);  //too verbose!  lets skip it
                res.put("taskID", taskID);
                res.put("success", true);
            }

        } catch (Exception ex) {
                res.put("error", ex.toString());
        }

    } else if (j.optString("taskIds", null) != null) {  //pass annotation id
        res.put("taskIds", IBEISIA.findTaskIDsFromObjectID(j.getString("taskIds"), myShepherd));
        res.put("success", true);

    } else if (j.optJSONArray("taskSummary") != null) {  //pass annotation ids
        res.put("taskSummary", taskSummary(j.getJSONArray("taskSummary"), myShepherd));
        res.put("success", true);

    } else {
        res.put("error", "unknown");
    }

    res.put("_in", j);

    out.println(res.toString());
    out.close();
    //myShepherd.closeDBTransaction();
  }


    public static JSONObject expandAnnotation(String annID, Shepherd myShepherd, HttpServletRequest request) {
        if (annID == null) return null;
        JSONObject rtn = new JSONObject();
        Annotation ann = null;
        try {
            ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annID), true)));
        } catch (Exception ex) {}
        if (ann != null) {
            rtn.put("annotationID", annID);
            Encounter enc = Encounter.findByAnnotation(ann, myShepherd);
            if (enc != null) {
                JSONObject jenc = new JSONObject();
                jenc.put("catalogNumber", enc.getCatalogNumber());
                jenc.put("date", enc.getDate());
                jenc.put("sex", enc.getSex());
                jenc.put("verbatimLocality", enc.getVerbatimLocality());
                jenc.put("locationID", enc.getLocationID());
                jenc.put("individualID", enc.getIndividualID());
                rtn.put("encounter", jenc);
            }
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null) {
                try {
                    rtn.put("mediaAsset", new JSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject()).toString()));
                } catch (Exception ex) {}
            }
        }
        return rtn;
    }

    public static JSONObject taskSummary(JSONArray taskIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();
        if ((taskIds == null) || (taskIds.length() < 1)) return rtn;
        for (int i = 0 ; i < taskIds.length() ; i++) {
            String annId = taskIds.optString(i);
            if (annId == null) continue;
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.summaryForAnnotationId(annId, myShepherd);
            if ((logs != null) && (logs.size() > 0)) {
                JSONObject tasks = new JSONObject();
                for (IdentityServiceLog l : logs) {
                    if (l.getTaskID() == null) continue;
                    JSONObject t = new JSONObject();
                    if (l.getStatus() != null) t.put("status", new JSONObject(l.getStatus()));
                    t.put("timestamp", l.getTimestamp());
                    tasks.put(l.getTaskID(), t);
                }
                rtn.put(annId, tasks);
            }
        }
        return rtn;
    }

}
  

