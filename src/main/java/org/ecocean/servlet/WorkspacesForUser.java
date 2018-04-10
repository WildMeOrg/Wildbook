package org.ecocean.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;

import java.io.*;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;

import java.util.Date;
import java.util.ArrayList;

import org.ecocean.*;

public class WorkspacesForUser extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      ServletUtilities.doOptions(request, response);
  }

  /**
   * retrieves a workspace object
   * @requestParameter id identifies the workspace
   * @returns the output of the query described by that workspace (a MediaAsset JSONArray)
   **/
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  /**
   * Creates & persists a new workspace object
   * @requestParameter id the name of the new workspace
   * @requestParameter args a JSON object containing the named parameters that would be sent to TranslateQuery servlet
   * @requestParameter overwrite (optional) if "true", will overwrite an existing workspace
   * e.g. http://52.36.191.214/WorkspaceServer?id=testServer&args={class:org.ecocean.MarkedIndividual, range:3}&overwrite=true
   **/
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    PrintWriter out = response.getWriter();

    ArrayList<String> wSpaceIDs = new ArrayList<String>();

    String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "testUser";

    try {
      // args is either a) a singleton argument that's a stringified JSONObject, or b) constructed with requestParamsToJSON

      //ArrayList<Workspace> wSpaces = myShepherd.getWorkspacesForUser(owner);

      ArrayList<Workspace> wSpaces = new ArrayList<Workspace>();
      if (request.getParameter("isImageSet")!=null) {
        boolean isImageSet = Boolean.parseBoolean(request.getParameter("isImageSet"));
        wSpaces = myShepherd.getWorkspacesForUser(owner, isImageSet);
      } else {
        wSpaces = myShepherd.getWorkspacesForUser(owner);
      }


      int maxSpaces = 1000;
      for (int i=0;i<wSpaces.size() && i<maxSpaces;i++) {
        wSpaceIDs.add(wSpaces.get(i).getName());
      }

      out.println(wSpaceIDs.toString());
      System.out.println("WorkspacesForUser for user "+owner+" is returning workspace IDs: "+wSpaceIDs.toString());

    } catch(Exception e) {
      //throw new IOException(e.toString());
      e.printStackTrace(out);
    } finally {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
  }
}
