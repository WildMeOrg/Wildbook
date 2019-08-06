package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;
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
    } catch (JSONException e){
      // urgh... depending on if POSTing from Postman or $.ajax, parameters must be handled differently.
      args.put("attach",request.getParameter("attach"));
      args.put("detach",request.getParameter("detach"));
      args.put("EncounterID", request.getParameter("EncounterID"));
      args.put("MediaAssetID", request.getParameter("MediaAssetID"));
      //leave this print in case of shenanigans even though we have alternate behavior
      e.printStackTrace();
    }

    //String encID = request.getParameter("EncounterID");
    //String maID = request.getParameter("MediaAssetID");
    String encID = args.optString("EncounterID");

    //resolve all asset ids into one list
    List<String> maIds = new ArrayList<String>();
    /*
    ok, leaving this for prosperity. this FAILS on the .getString() for reasons i cant explain when passed an int (non-string)
    however, the conditional still evaluates as TRUE !!!!   good luck on this one.
        see also:   https://github.com/stleary/JSON-java/issues/472
    if (args.optString("MediaAssetID", null) != null) maIds.add(args.getString("MediaAssetID"));
    */
    if (args.optString("MediaAssetID", null) != null) maIds.add(args.optString("MediaAssetID", null));
    JSONArray jarr = args.optJSONArray("mediaAssetIds");
    if (jarr != null) for (int i = 0 ; i < jarr.length() ; i++) {
        String arrId = jarr.optString(i, null);
        if ((arrId != null) && !maIds.contains(arrId)) maIds.add(arrId);
    }

    System.out.println("Servlet received maIds="+maIds+" and encID="+encID);

    if (encID == null || maIds.size() < 1) {
      throw new IOException("MediaAssetAttach servlet requires both a \"MediaAssetID\" and an \"EncounterID\" argument. Servlet received maIds="+maIds+" and encID="+encID);
    }

    res.put("maIds", new JSONArray(maIds));

    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("MediaAssetAttach.class");
    PrintWriter out = response.getWriter();

    JSONArray alreadyAttachedIds = new JSONArray();
    List<MediaAsset> alreadyAttached = new ArrayList<MediaAsset>();

    try {
      myShepherd.beginDBTransaction();

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
        for (MediaAsset ma : mas) {
            enc.addMediaAsset(ma);
        }
        res.put("action","attach");
        res.put("success",true);
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
 
            String undoLink = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/MediaAssetAttach?attach=true&EncounterID="+encID+"&MediaAssetID="+ma.getId();
            String comments = "Detached MediaAsset " + ma.getId() + ". To undo this action, visit " + undoLink;
            enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + comments + " </p>");
            success = true;
        }
        res.put("action","detach");
        res.put("success", success);

    } else {
      res.put("args.optString",false);
        res.put("error", "unknown command");
        res.put("success", false);
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