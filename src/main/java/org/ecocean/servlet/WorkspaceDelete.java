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



public class WorkspaceDelete extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(WorkspaceDelete.class);

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
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}

    boolean isOwner = true;


    // ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
    myShepherd.beginDBTransaction();

    String owner="";
    String id="";
    String originalWorkspaceQuery="";

    try {

      owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "testUser";
      id = request.getParameter("id");
      Workspace work = myShepherd.getWorkspaceForUser(id, owner);

      if (work!=null) {
        originalWorkspaceQuery=work.getArgs();
        myShepherd.beginDBTransaction();
        myShepherd.throwAwayWorkspace(work);
      }

    } catch (Exception edel) {
      locked = true;
      log.warn("Failed to serialize encounter: " + request.getParameter("id"), edel);
      edel.printStackTrace();
      myShepherd.rollbackDBTransaction();

    }


    if (!locked) {
      myShepherd.commitDBTransaction();

      //log it
      Logger log = LoggerFactory.getLogger(EncounterDelete.class);

      out.println("{status: deleted, originalWorkspaceQuery: "+originalWorkspaceQuery+"}");

    }

    out.close();
    myShepherd.closeDBTransaction();
  }
}
