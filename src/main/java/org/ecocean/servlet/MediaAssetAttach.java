package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;
import org.ecocean.media.*;
import java.util.List;
import java.util.ArrayList;

import java.io.*;

public class MediaAssetAttach extends HttpServlet {

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
    response.setHeader("Access-Control-Allow-Origin", "*");
    JSONObject res = new JSONObject();


    JSONObject args = new JSONObject();

    try {
      args = ServletUtilities.jsonFromHttpServletRequest(request);
    }

    catch (JSONException e){
      // urgh... depending on if POSTing from Postman or $.ajax, parameters must be handled differently.
      args.put("attach",request.getParameter("attach"));
      args.put("detach",request.getParameter("detach"));
      args.put("EncounterID", request.getParameter("EncounterID"));
      args.put("MediaAssetID", request.getParameter("MediaAssetID"));
    }

    //String encID = request.getParameter("EncounterID");
    //String maID = request.getParameter("MediaAssetID");
    String encID = args.optString("EncounterID");
    String maID = args.optString("MediaAssetID");

    System.out.println("Servlet received maID="+maID+" and encID="+encID);

    if (encID == null || maID == null) {
      throw new IOException("MediaAssetAttach servlet requires both a \"MediaAssetID\" and an \"EncounterID\" argument. Servlet received maID="+maID+" and encID="+encID);
    }

    String context = "context0";
    context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("MediaAssetAttach.class");
    PrintWriter out = response.getWriter();

    JSONArray alreadyAttachedIds = new JSONArray();
    List<MediaAsset> alreadyAttached = new ArrayList<MediaAsset>();

    try {

      myShepherd.beginDBTransaction();

    MediaAsset ma = myShepherd.getMediaAsset(maID);
    if (ma == null) throw new ServletException("No MediaAsset with id "+maID+" found in database.");

    Encounter enc = myShepherd.getEncounter(encID);
    if (enc == null) throw new ServletException("No Encounter with id "+encID+" found in database.");

        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (String maId : maIds) {
            MediaAsset ma = myShepherd.getMediaAsset(maId);
            if (ma == null) throw new ServletException("No MediaAsset with id "+maId+" found in database.");
            if (enc.hasTopLevelMediaAsset(ma.getId())) {
                alreadyAttachedIds.put(ma.getId());
                alreadyAttached.add(ma);
            } else {
                mas.add(ma);
            }
        }

    if (alreadyAttachedIds.length() > 0) res.put("alreadyAttached", alreadyAttachedIds);

    // ATTACH MEDIAASSET TO ENCOUNTER
    if (args.optString("attach")!=null && args.optString("attach").equals("true")) {
      if (!alreadyAttached) {
        enc.addMediaAsset(ma);
        res.put("action","attach");
        res.put("success",true);
      }
      else {
        throw new ServletException("Cannot attach MediaAsset "+maID+" to Encounter "+encID+". They were already attached.");
      }
    }

    // DETACH MEDIAASSET FROM ENCOUNTER
    else if (args.optString("detach")!=null && args.optString("detach").equals("true")) {
        boolean success = false;
        for (MediaAsset ma : alreadyAttached) {
System.out.println("DETACH: " + ma);
            // Set match against to false on the annotation(s) from this asset that were associated with the encounter. 
            for (Annotation ann : enc.getAnnotations()) {
                if (ann.getMediaAsset().getId() != ma.getId()) continue;
System.out.println("setting matchAgainst=F on " + ann);
                ann.setMatchAgainst(false);
            }
            enc.removeMediaAsset(ma);
 
        String undoLink = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/MediaAssetAttach?attach=true&EncounterID="+encID+"&MediaAssetID="+maID;
        String comments = "Detached MediaAsset " + maID + ". To undo this action, visit " + undoLink;
        enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + comments + " </p>");
        res.put("action","detach");
        res.put("success",true);
      }
      else {
        throw new ServletException("Cannot remove MediaAsset "+maID+" from Encounter "+encID+". They were not attached in the first place.");
      }
    } else {
      res.put("args.optString",false);
    }
    myShepherd.commitDBTransaction();

    // DETACH MEDIAASSET FROM ENCOUNTER
  } catch (Exception e) {
    e.printStackTrace(out);
    myShepherd.rollbackDBTransaction();
  }
  finally {
    myShepherd.closeDBTransaction();
  }

  out.println(res.toString());
  out.close();

  }
}
