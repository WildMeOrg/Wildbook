package org.ecocean.servlet.importer;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.identity.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;


import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;

import com.healthmarketscience.jackcess.*;

public class AccessImport extends HttpServlet {
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static PrintWriter out;
  private static String context;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    context = ServletUtilities.getContext(request);
    out = response.getWriter();

    Shepherd myShepherd = new Shepherd(context);

    myShepherd.beginDBTransaction();
    myShepherd.setAction("AccessImport.class");
    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      out.println("--Wildbook not initialized. Starting Wildbook. --");
      StartupWildbook.initializeWildbook(request, myShepherd);
    }
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false"));
    out.println("***** Beginning Access Database Import. *****");
    
    Database db = DatabaseBuilder.open(new File("mydb.mdb"));
  } 
}
  
  
  
  
  
  