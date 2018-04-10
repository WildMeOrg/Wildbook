package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;


public class MediaAssetContext extends HttpServlet {
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
    String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "testUser";
    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");
    String id = request.getParameter("id");
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("MediaAssetContext.class");
    PrintWriter out = response.getWriter();
    myShepherd.beginDBTransaction();


    try {

      MediaAsset mAsset = myShepherd.getMediaAsset(id);
      if (mAsset==null) {
        throw new IOException("No MediaAsset in DB with id="+id);
      } else {
        System.out.println("WorkspaceMetadata successfully grabbed MediaAsset with id="+id);
      }
  
      //
      JSONObject res = new JSONObject("{id:"+mAsset.getId()+"}");
  
      JSONObject encs = new JSONObject();
      HashSet<String> individualIDs = new HashSet<String>();
      HashSet<String> occurrenceIDs = new HashSet<String>();
      List<Encounter> encounters = Encounter.findAllByMediaAsset(mAsset,myShepherd);
      res.put("numEncs", encounters.size());
      for (Encounter enc: encounters) {
        JSONObject encJson = new JSONObject();
        encs.put(enc.getCatalogNumber(), enc.uiJson(request));
        if (enc.getIndividualID()!=null && !enc.getIndividualID().equals("")) {
          individualIDs.add(enc.getIndividualID());
        }
        if (enc.getOccurrenceID()!=null && !enc.getOccurrenceID().equals("")) {
          occurrenceIDs.add(enc.getOccurrenceID());
        }
  
      }
      res.put("Encounters", encs);
  
      JSONObject inds = new JSONObject();
      for (String indID : individualIDs) {
        MarkedIndividual indie = myShepherd.getMarkedIndividual(indID);
        if (indie!=null) {
          inds.put(indID, indie.uiJson(request));
        }
      }
      res.put("MarkedIndividuals", inds);
  
  
      JSONObject anns = new JSONObject();
      ArrayList<Annotation> annotations = mAsset.getAnnotations();
      for(Annotation ann : annotations) {
        anns.put(ann.getId(),ann.sanitizeJson(request));
      }
      // get attached Annotations
      res.put("Annotations", anns);
  
      JSONObject occs = new JSONObject();
      for (String occID : occurrenceIDs) {
        Occurrence occie = myShepherd.getOccurrence(occID);
        if (occie!=null) {
          occs.put(occID, occie.uiJson(request));
        }
      }
      res.put("Occurrences", occs);
  
      res.put("IAStatus", Util.toggleJSONObject(mAsset.getIAStatus()));
      out.println(res);

  } 
  catch (JSONException jsoe) {
    // curse datanucleus for demanding we handle this exception
  } 
  catch (Exception e) {
    out.println(e.getMessage());
  }
    finally{
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
    }

  }


}
