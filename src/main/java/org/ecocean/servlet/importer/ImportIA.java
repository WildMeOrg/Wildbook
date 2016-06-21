package org.ecocean.servlet.importer;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.identity.IBEISIA;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;

public class ImportIA extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

/*
    out.println("\n\nStarting ImportIA servlet...");

    JSONObject imageSetRes = IBEISIA.iaURL(context, "/api/imageset/json/");
    out.println(imageSetRes);
*/


  }
}
