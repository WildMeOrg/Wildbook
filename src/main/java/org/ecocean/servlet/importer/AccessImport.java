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

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;

import org.json.JSONArray;

import java.io.*;


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

    // Check if we have created and asset store yet, and if not create one.
    myShepherd.beginDBTransaction();
    myShepherd.setAction("AccessImport.class");
    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      out.println("--Wildbook not initialized. Starting Wildbook. --");
      StartupWildbook.initializeWildbook(request, myShepherd);
    }
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
      
    String dbName = "master.mdb";
    if (request.getParameter("file") != null) {
      dbName = request.getParameter("file");
    }
    Database db = DatabaseBuilder.open(new File("mydb.mdb"));
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false"));
    
    out.println("***** Beginning Access Database Import. *****");
    
    // Close that db so it don't leak or something.
    db.close();
  } 
}
  
  
  
  
  
  