package org.ecocean.servlet;

import org.ecocean.*;

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


import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;


public class WorkspaceMetadata extends HttpServlet {
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    try {
    //
    JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");

    JSONArray encs = new JSONArray();
    // get attached Encounters
    res.put("Encounters", encs);

    JSONArray inds = new JSONArray();
    // get attached MarkedIndividuals
    res.put("MarkedIndividuals", inds);

    JSONArray anns = new JSONArray();
    // get attached Annotations
    res.put("Annotations", anns);

  } catch (JSONException e) {
    // curse datanucleus for demanding we handle this exception
  }

  }


}
