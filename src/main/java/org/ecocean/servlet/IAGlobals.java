package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.concurrent.ThreadPoolExecutor;

import org.json.JSONObject;
import org.json.JSONException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IAGlobals extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(IAGlobals.class);

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IAGlobals.class");

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

      if (request.getParameter("species")!=null) {
        String fullSpecies = CommonConfiguration.getProperty("genus",context)+" "+ CommonConfiguration.getProperty("species",context);
        res.put("species",fullSpecies);
        res.put("success",true);
      }

    } catch (Exception edel) {
      locked = true;
      log.warn("Failed to get globals! Error. ", edel);
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
