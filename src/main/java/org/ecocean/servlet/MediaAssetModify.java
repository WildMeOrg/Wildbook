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
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;

import org.json.JSONObject;
import org.json.JSONException;




public class MediaAssetModify extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(WorkspaceDelete.class);

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      response.setHeader("Access-Control-Allow-Origin", "*");
      response.setHeader("Access-Control-Allow-Methods", "GET, POST");
      if (request.getHeader("Access-Control-Request-Headers") != null) response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
      //response.setContentType("text/plain");
  }



  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("MediaAssetModify.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    JSONObject res = new JSONObject("{\"success\": \"false\"}");

    boolean isOwner = true;


    // ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
    myShepherd.beginDBTransaction();

    String id="";

    try {

      id = request.getParameter("id");
      if (id==null) {
        throw new IOException("MediaAssetModify servlet requires an 'id' argument.");
      }


      MediaAsset ma = myShepherd.getMediaAsset(id);

      if (ma==null) {
        throw new IOException("No MediaAsset in database with id "+request.getParameter("id"));
      } else {

        if (request.getParameter("lat")!=null) {
          ma.setUserLatitude(Double.valueOf(request.getParameter("lat")));
          res.put("setLatitude",Double.valueOf(request.getParameter("lat")));
        }

        if (request.getParameter("long")!=null) {
          ma.setUserLongitude(Double.valueOf(request.getParameter("long")));
          res.put("setLongitude",Double.valueOf(request.getParameter("long")));
        }

        if (request.getParameter("datetime")!=null) {
          ma.setUserDateTime(DateTime.parse(request.getParameter("datetime")));
          res.put("setDateTime",DateTime.parse(request.getParameter("datetime")).toString());
        }

      }

      res.put("success","true");
    } catch (Exception edel) {
      locked = true;
      log.warn("Failed to modify MediaAsset: " + request.getParameter("id"), edel);
      edel.printStackTrace();
      myShepherd.rollbackDBTransaction();
    }


    if (!locked) {
      myShepherd.commitDBTransaction();
    }

    out.println(res.toString());
    out.close();
    myShepherd.closeDBTransaction();
  }
}
