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
import org.ecocean.RestClient;
import org.ecocean.media.*;
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

import java.io.InputStream;

public class IAGateway extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
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
            getOut = "<div class=\"response-error\">invalid job ID " + jobID + "</div>";
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
            try {
                url += "callback_url=" + CommonConfiguration.getServerURL(request, request.getContextPath()) + "/ia%3FdetectionReviewPost&callback_method=POST";
System.out.println("url --> " + url);
                URL u = new URL(url);
                JSONObject rtn = RestClient.get(u);
                if ((rtn.optString("response", null) == null) || (rtn.optJSONObject("status") == null) ||
                    !rtn.getJSONObject("status").optBoolean("success", false)) {
                    getOut = "<div class=\"response-error\">invalid response: <xmp>" + rtn.toString() + "</xmp></div>";
                } else {
                    getOut = rtn.getString("response");
                    if (request.getParameter("test") != null) {
                        getOut = "<html><head><script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.2.0/jquery.min.js\"></script></head><body>" + getOut + "</body></html>";
                    }
                }
            } catch (Exception ex) {
                getOut = "<div class=\"response-error\">Error: " + ex.toString() + "</div>";
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
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    String qstr = request.getQueryString();
    if ("detectionReviewPost".equals(qstr)) {
        String url = CommonConfiguration.getProperty("IBEISIARestUrlDetectReview", "context0");
        if (url == null) throw new IOException("IBEISIARestUrlDetectionReview url not set");
System.out.println("attempting passthru to " + url);
        URL u = new URL(url);
        JSONObject rtn = new JSONObject("{\"success\": false}");
/*
InputStream is = request.getInputStream();
byte buffer[] = new byte[10240];
int i;
System.out.println("before....");
while ((i = is.read(buffer)) > 0) {
    System.out.write(buffer, 0, i);
}
System.out.println("....after");
*/
        try {
            rtn = RestClient.postStream(u, request.getInputStream());
        } catch (Exception ex) {
            rtn.put("error", ex.toString());
        }
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
        return;
    }


    String context = ServletUtilities.getContext(request);  //note! this *must* be run after postStream stuff above
    Shepherd myShepherd = new Shepherd(context);

    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();

    JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
    JSONObject res = new JSONObject();
    String taskId = Util.generateUUID();
    res.put("taskId", taskId);

    if (j.optJSONArray("detect") != null) {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        JSONArray ids = j.getJSONArray("detect");
        ArrayList<String> validIds = new ArrayList<String>();
        for (int i = 0 ; i < ids.length() ; i++) {
            int id = ids.optInt(i, 0);
System.out.println(id);
            if (id < 1) continue;
            MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
            if (ma != null) {
                mas.add(ma);
                validIds.add(Integer.toString(id));
            }
        }
        if (mas.size() > 0) {
            try {
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                res.put("sendMediaAssets", IBEISIA.sendMediaAssets(mas));
                JSONObject sent = IBEISIA.sendDetect(mas, baseUrl);
                res.put("sendDetect", sent);
                String jobId = null;
                if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false))
                    jobId = sent.optString("response", null);
                res.put("jobId", jobId);
                IBEISIA.log(taskId, validIds.toArray(new String[validIds.size()]), jobId, new JSONObject("{\"_action\": \"initDetect\"}"), context);
            } catch (Exception ex) {
                throw new IOException(ex.toString());
            }
        }
    }

    res.put("_in", j);
    res.put("success", true);

    out.println(res.toString());
    out.close();
    //myShepherd.closeDBTransaction();
  }

}
  

