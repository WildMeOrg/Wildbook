package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import org.ecocean.media.*;
import java.util.ArrayList;
import java.util.HashMap;

import java.io.*;

public class MediaAssetAttach extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      ServletUtilities.doOptions(request, response);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");

    if (request.getParameter("MediaAssetID") == null || request.getParameter("EncounterID") == null) throw new IOException("MediaAssetAttach servlet requires both a \"MediaAssetID\" and an \"EncounterID\" argument.");

  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");
    JSONObject res = new JSONObject();


    String encID = request.getParameter("EncounterID");
    String maID = request.getParameter("MediaAssetID");

    if (encID == null || maID == null) throw new IOException("MediaAssetAttach servlet requires both a \"MediaAssetID\" and an \"EncounterID\" argument.");

    String context = "context0";
    context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    PrintWriter out = response.getWriter();

    try {

      myShepherd.beginDBTransaction();

    MediaAsset ma = myShepherd.getMediaAsset(maID);
    if (ma == null) throw new ServletException("No MediaAsset with id "+maID+" found in database.");

    Encounter enc = myShepherd.getEncounter(encID);
    if (enc == null) throw new ServletException("No Encounter with id "+encID+" found in database.");

    boolean alreadyAttached = enc.hasTopLevelMediaAsset(ma.getId());
    res.put("alreadyAttached", alreadyAttached);

    // ATTACH MEDIAASSET TO ENCOUNTER
    if (request.getParameter("attach")!=null && request.getParameter("attach").equals("true")) {
      if (!alreadyAttached) {
        enc.addMediaAsset(ma, context);
        res.put("action","attach");
        res.put("success",true);
      }
      else {
        throw new ServletException("Cannot attach MediaAsset "+maID+" to Encounter "+encID+". They were already attached.");
      }
    }

    else if (request.getParameter("detach")!=null && request.getParameter("detach").equals("true")) {
      if (alreadyAttached) {
        enc.removeMediaAsset(ma);
        res.put("action","detach");
        res.put("success",true);
      }
      else {
        throw new ServletException("Cannot remove MediaAsset "+maID+" from Encounter "+encID+". They were not attached in the first place.");
      }
    } else {
      res.put("success",false);
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
