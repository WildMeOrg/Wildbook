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
import org.ecocean.Annotation;
import org.ecocean.Occurrence;
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
import java.util.Collection;
import java.util.Iterator;

import javax.jdo.Query;

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
        getOut = _detectionHtmlFromResult(res, request, offset, null);

    } else if (request.getParameter("getDetectionReviewHtmlNext") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<MediaAsset> mas = mineNeedingDetectionReview(request, myShepherd);
        if ((mas == null) || (mas.size() < 1)) {
            getOut = "<div>no detections needing review</div>";
        } else {
            MediaAsset ma = mas.get((int)(Math.random() * mas.size()));
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", Integer.toString(ma.getId()), myShepherd);
            JSONObject res = null;
            for (IdentityServiceLog log : logs) {
                if ((log.getStatusJson() == null) || !log.getStatusJson().optString("_action", "FAIL").equals("getJobResult")) continue;
                res = log.getStatusJson().optJSONObject("_response");
                if (res != null) break;
            }
    System.out.println("res(" + ma.toString() + ") -> " + res);
            getOut = _detectionHtmlFromResult(res, request, -1, ma.getUUID());
        }

    //ugh, lets standardize on passing taskId, not jobid cuz jobid sucks
    } else if (request.getParameter("getIdentificationReviewHtml") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String taskId = request.getParameter("getIdentificationReviewHtml");
        int offset = 0;
        if (request.getParameter("offset") != null) {
            try {
                offset = Integer.parseInt(request.getParameter("offset"));
            } catch (NumberFormatException ex) {}
        }
        JSONObject res = null;
        try {
            res = IBEISIA.getTaskResults(taskId, myShepherd);
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
System.out.println("res(" + taskId + "[" + offset + "]) -> " + res);
        getOut = _identificationHtmlFromResult(res, request, offset, null);

    } else if (request.getParameter("getIdentificationReviewHtmlNext") != null) {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<Annotation> anns = mineNeedingIdentificationReview(request, myShepherd);
        if ((anns == null) || (anns.size() < 1)) {
            getOut = "<div>no identifications needing review</div>";
        } else {
            Annotation ann = anns.get((int)(Math.random() * anns.size()));
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", ann.getId(), myShepherd);
            JSONObject res = null;
            for (IdentityServiceLog log : logs) {
                if ((log.getStatusJson() == null) || !log.getStatusJson().optString("_action", "FAIL").equals("getJobResult")) continue;
                res = log.getStatusJson().optJSONObject("_response");
                if (res != null) break;
            }
            //this is to munge the format into that of getTaskResults() as above non-Next version
            if ((res != null) && (res.optJSONObject("results") == null) && (res.optJSONObject("response") != null))
                res.put("results", res.getJSONObject("response").optJSONObject("json_result"));
    System.out.println("res(" + ann.toString() + ") -> " + res);
            getOut = _identificationHtmlFromResult(res, request, -1, ann.getId());
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
        if (url == null) throw new IOException("IBEISIARestUrlDetectReview url not set");
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


    if ("identificationReviewPost".equals(qstr)) {
        String url = CommonConfiguration.getProperty("IBEISIARestUrlIdentifyReview", "context0");
        if (url == null) throw new IOException("IBEISIARestUrlIdentifyReview url not set");
System.out.println("attempting passthru to " + url);
        URL u = new URL(url);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        try {
            rtn = RestClient.postStream(u, request.getInputStream());
        } catch (Exception ex) {
            rtn.put("error", ex.toString());
        }
        if ((rtn.optJSONObject("status") != null) && rtn.getJSONObject("status").optBoolean("success", false)) {
            JSONArray match = rtn.optJSONArray("response");
            if ((match != null) && (match.optJSONObject(0) != null) && (match.optJSONObject(1) != null)) {
                String a1 = IBEISIA.fromFancyUUID(match.optJSONObject(0));
                String a2 = IBEISIA.fromFancyUUID(match.optJSONObject(1));
                IBEISIA.updateIdentificationMatchingState(a1, a2, match.optString(2, "UNKNOWN_MATCH_STATE"));
                checkIdentificationIterationStatus(a1);
            }
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
                ma.setDetectionStatus("processing");
                mas.add(ma);
                validIds.add(Integer.toString(id));
            }
        }
        if (mas.size() > 0) {
            boolean success = true;
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
                success = false;
                throw new IOException(ex.toString());
            }
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus("error");
                }
            }
        }
        res.put("success", true);

    } else if (j.optJSONObject("identify") != null) {
        ArrayList<Annotation> anns = new ArrayList<Annotation>();  //what we ultimately run on.  occurrences are irrelevant now right?
        ArrayList<String> validIds = new ArrayList<String>();
        int limitTargetSize = j.optInt("limitTargetSize", -1);  //really "only" for debugging/testing, so use if you know what you are doing

        //currently this implies each annotation should be sent one-at-a-time TODO later will be allow clumping (to be sent as multi-annotation
        //  query lists.... *when* that is supported by IA
        JSONArray alist = j.getJSONObject("identify").optJSONArray("annotationIds");
        if ((alist != null) && (alist.length() > 0)) {
            for (int i = 0 ; i < alist.length() ; i++) {
                String aid = alist.optString(i, null);
                if (aid == null) continue;
                Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
                if (ann == null) continue;
                anns.add(ann);
                validIds.add(aid);
            }
        }

        //i think that "in the future" co-occurring annotations should be sent together as one set of query list; but since we dont have support for that
        // now, we just send these all in one at a time.  hope that is good enough!   TODO
        JSONArray olist = j.getJSONObject("identify").optJSONArray("occurrenceIds");
        if ((olist != null) && (olist.length() > 0)) {
            for (int i = 0 ; i < olist.length() ; i++) {
                String oid = olist.optString(i, null);
                if (oid == null) continue;
                Occurrence occ = ((Occurrence) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Occurrence.class, oid), true)));
                if (occ == null) continue;
                ArrayList<MediaAsset> mas = occ.getAssets();
                if ((mas == null) || (mas.size() < 1)) continue;
                for (MediaAsset ma : mas) {
                    ArrayList<Annotation> maAnns = ma.getAnnotations();
                    if ((maAnns == null) || (maAnns.size() < 1)) continue;
                    for (Annotation ann : anns) {
                        if (validIds.contains(ann.getId())) continue;
                        anns.add(ann);
                        validIds.add(ann.getId());
                    }
                }
            }
        }

        JSONArray taskList = new JSONArray();
/* currently we are sending annotations one at a time (one per query list) but later we will have to support clumped sets...
   things to consider for that - we probably have to further subdivide by species ... other considerations?   */
        for (Annotation ann : anns) {
            String species = ann.getSpecies();
            if ((species == null) || (species.equals(""))) throw new IOException("species on Annotation " + ann + " invalid: " + species);
            boolean success = true;
            String annTaskId = Util.generateUUID();
            JSONObject taskRes = new JSONObject();
            taskRes.put("taskId", annTaskId);
            JSONArray jids = new JSONArray();
            jids.put(ann.getId());  //for now there is only one 
            taskRes.put("annotationIds", jids);
            try {
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                //TODO we might want to cache this examplars list (per species) yes?
                ArrayList<Annotation> exemplars = Annotation.getExemplars(species, myShepherd);
                if (limitTargetSize > -1) {
                    res.put("_limitTargetSize", limitTargetSize);
                    System.out.println("WARNING: limited identification exemplar list size from " + exemplars.size() + " to " + limitTargetSize);
                    exemplars = new ArrayList(exemplars.subList(0, limitTargetSize));
                }
                taskRes.put("exemplarsSize", exemplars.size());
                ArrayList<Annotation> qanns = new ArrayList<Annotation>();
                qanns.add(ann);
                JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, exemplars, myShepherd, species, annTaskId, baseUrl, context);
                taskRes.put("beginIdentify", sent);
                String jobId = null;
                if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false))
                    jobId = sent.optString("response", null);
                taskRes.put("jobId", jobId);
                //validIds.toArray(new String[validIds.size()])
                IBEISIA.log(annTaskId, ann.getId(), jobId, new JSONObject("{\"_action\": \"initIdentify\"}"), context);
            } catch (Exception ex) {
                success = false;
                throw new IOException(ex.toString());
            }
/* TODO ?????????
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus("error");
                }
            }
*/
            taskList.put(taskRes);
        }
        res.put("tasks", taskList);
        res.put("success", true);

    } else {
        res.put("error", "unknown POST command");
        res.put("success", false);
    }

    res.put("_in", j);

    out.println(res.toString());
    out.close();
    //myShepherd.closeDBTransaction();
  }


    private String _detectionHtmlFromResult(JSONObject res, HttpServletRequest request, int offset, String maUUID) throws IOException {
        String getOut = "";
        if ((res == null) || (res.optJSONObject("response") == null) || (res.getJSONObject("response").optJSONObject("json_result") == null) || (res.getJSONObject("response").getJSONObject("json_result").optJSONArray("results_list") == null) || (res.getJSONObject("response").getJSONObject("json_result").optJSONArray("image_uuid_list") == null)) {
            getOut = "<div class=\"response-error\">unable to obtain detection interface</div>";
            System.out.println("ERROR: invalid res for _detectionHtmlFromResult: " + res);
        } else {
            JSONArray rlist = res.getJSONObject("response").getJSONObject("json_result").getJSONArray("results_list");
            JSONArray ilist = res.getJSONObject("response").getJSONObject("json_result").getJSONArray("image_uuid_list");
            if (maUUID != null) {
                offset = -1;
                for (int i = 0 ; i < ilist.length() ; i++) {
                    if (maUUID.equals(IBEISIA.fromFancyUUID(ilist.getJSONObject(i)))) {
                        offset = i;
                        break;
                    }
                }
                if (offset < 0) {
                    System.out.println("ERROR: could not find uuid " + maUUID + " in res: " + res.toString());
                    return "<div class=\"response-error\">unable to find MediaAsset for detection</div>";
                }
            }
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
        }
        return getOut;
    }

    private String _identificationHtmlFromResult(JSONObject res, HttpServletRequest request, int offset, String annId) throws IOException {
        String getOut = "";
        if ((res == null) || (res.optJSONObject("results") == null) || (res.getJSONObject("results").optJSONObject("inference_dict") == null) ||
            (res.getJSONObject("results").getJSONObject("inference_dict").optJSONObject("annot_pair_dict") == null) ||
            (res.getJSONObject("results").getJSONObject("inference_dict").getJSONObject("annot_pair_dict").optJSONArray("review_pair_list") == null)) {
                System.out.println("ERROR: invalid res for _identificationHtmlFromResult: " + res);
                return "<div class=\"response-error\">unable to obtain identification interface</div>";
        }

        JSONArray rlist = res.getJSONObject("results").getJSONObject("inference_dict").getJSONObject("annot_pair_dict").getJSONArray("review_pair_list");
        JSONObject rpair = null;
        if (offset >= 0) {
            if (offset > rlist.length() - 1) offset = 0;
            rpair = rlist.optJSONObject(offset);
        } else {
            rpair = getAvailableIdentificationReviewPair(rlist, annId);
        }
        if (rpair == null) {
            System.out.println("ERROR: could not determine rpair from " + rlist.toString());
            return "<div class=\"response-error\">unable to obtain identification interface</div>";
        }

        String url = CommonConfiguration.getProperty("IBEISIARestUrlIdentifyReview", "context0");
        if (url == null) throw new IOException("IBEISIARestUrlIdentifyReview url not set");
        url += "?query_config_dict=" + res.getJSONObject("results").optJSONObject("query_config_dict").toString() + "&";
        url += "review_pair=" + rpair.toString() + "&";
        String quuid = IBEISIA.fromFancyUUID(rpair.optJSONObject("annot_uuid_1"));
        if (quuid == null) {
            getOut = "<div class=\"response-error\">unable to obtain identification interface</div>";
            System.out.println("ERROR: could not determine query annotation uuid for _identificationHtmlFromResult: " + res);
            return getOut;
        }
        if ((res.getJSONObject("results").optJSONObject("cm_dict") == null) || (res.getJSONObject("results").getJSONObject("cm_dict").optJSONObject(quuid) == null)) {
            getOut = "<div class=\"response-error\">unable to obtain identification interface</div>";
            System.out.println("ERROR: could not determine cm_dict for quuid=" + quuid + " for _identificationHtmlFromResult: " + res);
            return getOut;
        }
        url += "cm_dict=" + res.getJSONObject("results").getJSONObject("cm_dict").getJSONObject(quuid).toString() + "&";
        url += "view_orientation=horizontal&";  //TODO set how?
        url += "_internal_state=null&";  //"placeholder" according to docs

        try {
            url += "callback_url=" + CommonConfiguration.getServerURL(request, request.getContextPath()) + "/ia%3FidentificationReviewPost&callback_method=POST";
System.out.println("url --> " + url);
getOut = "(( " + url + " ))";
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

        return getOut;
    }

    private JSONObject getAvailableIdentificationReviewPair(JSONArray rlist, String annId) {
        if ((rlist == null) || (rlist.length() < 1)) return null;
        for (int i = 0 ; i < rlist.length() ; i++) {
            JSONObject rp = rlist.optJSONObject(i);
            if (rp == null) continue;
            String a1 = IBEISIA.fromFancyUUID(rp.optJSONObject("annot_uuid_1"));
            if (!annId.equals(a1)) continue;
            String a2 = IBEISIA.fromFancyUUID(rp.optJSONObject("annot_uuid_2"));
            if (IBEISIA.getIdentificationMatchingState(a1, a2) == null) return rp;
        }
        return null;
    }

    private ArrayList<MediaAsset> mineNeedingDetectionReview(HttpServletRequest request, Shepherd myShepherd) {
        String filter = "SELECT FROM org.ecocean.media.MediaAsset WHERE detectionStatus == \"pending\"";
        String username = ((request.getUserPrincipal() == null) ? null : request.getUserPrincipal().getName());
        if (username != null) {
            filter = "SELECT FROM org.ecocean.media.MediaAsset WHERE accessControl.username == \"" + username + "\" && detectionStatus == \"pending\"";
        }
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            mas.add((MediaAsset)it.next());
        }    
        query.closeAll();
        return mas;
    }

    private ArrayList<Annotation> mineNeedingIdentificationReview(HttpServletRequest request, Shepherd myShepherd) {
        String filter = "SELECT FROM org.ecocean.Annotation WHERE identificationStatus == \"pending\"";
/*
        String username = ((request.getUserPrincipal() == null) ? null : request.getUserPrincipal().getName());
        if (username != null) {
            filter = "SELECT FROM org.ecocean.media.MediaAsset WHERE accessControl.username == \"" + username + "\" && detectionStatus == \"pending\"";
        }
*/
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            anns.add((Annotation)it.next());
        }    
        query.closeAll();
        return anns;
    }

    private void checkIdentificationIterationStatus(String annId) {
        if (annId == null) return;
/*
        Shepherd myShepherd = new Shepherd("context0");
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", annId, myShepherd);
///////TODO once more on the rodeo?
*/
    }

}
  

