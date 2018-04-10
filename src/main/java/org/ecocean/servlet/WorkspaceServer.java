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

import org.ecocean.*;


/**
 * A wildbook Workspace is a persisted/named TranslateQuery.
 * This servlet lets the user create (POST) workspaces, and
 * retrieve (GET) the MediaAssets contained in a workspace selected by name
 **/

public class WorkspaceServer extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      ServletUtilities.doOptions(request, response);
  }

  /**
   * retrieves an existing workspace object (or rather, its MediaAssets)
   * @requestParameter id identifies the workspace
   * @returns the output of the query described by that workspace (a MediaAsset JSONArray)
   **/
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
    JSONObject res;
    try {
      res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    } catch (JSONException e) { //datanucleus JSONObject initialization requires explicit error handling
    }

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    //String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
    String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "testUser";

    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");

    String id = request.getParameter("id");

    try {
      Workspace wSpace = myShepherd.getWorkspaceForUser(id, owner);
      //Workspace wSpace = myShepherd.getWorkspace(1);
      if (wSpace==null) {
        throw new IOException("No workspace in DB with id="+id+" for user "+owner);
      } else {
        System.out.println("doGet successfully grabbed workspace with id="+wSpace.getID()+" and queryArg="+wSpace.getArgs());
      }

      myShepherd.beginDBTransaction();
      wSpace.setAccessed(new Date());
      myShepherd.commitDBTransaction();

      request.setAttribute("queryAsString", wSpace.getArgs());
      request.setAttribute("workspaceID", wSpace.getArgs());
      RequestDispatcher rd=request.getRequestDispatcher("TranslateQuery");
      rd.forward(request, response);

    }
    catch (Exception e) {
      throw new IOException(e.toString());
    }
    finally {
      myShepherd.rollbackDBTransaction();
      myShepherd.commitDBTransaction();
    }
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

    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    PrintWriter out = response.getWriter();

    //String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
    String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "testUser";
    out.println("owner = "+owner);

    try {

      // args is either a) a singleton argument that's a stringified JSONObject, or b) constructed with requestParamsToJSON
      JSONObject args = ((request.getParameter("args"))!=null) ? new JSONObject(request.getParameter("args")) : Util.requestParamsToJSON(request);

      String id = request.getParameter("id");
      boolean overwrite = (request.getParameter("overwrite")!=null && request.getParameter("overwrite").equalsIgnoreCase("true"));

      Workspace wSpace = myShepherd.getWorkspaceForUser(id, owner);
      boolean inDB = (wSpace!=null);
      out.println("inDB = "+inDB);

      if (inDB && !overwrite) throw new IOException("Workspace with id="+id+" and owner="+owner+" already in database. Include request parameter overwrite=true if you wish to overwrite.");

      if (inDB) {
        wSpace.setArg(args);
        wSpace.setModified(new Date());
        out.println("modifying existing workspace with id="+wSpace.getID()+", name="+wSpace.getName()+", owner="+wSpace.getOwner()+", args="+wSpace.getArgs()+", created="+wSpace.getCreated()+", and modified ="+wSpace.getModified());
        System.out.println("modifying existing workspace with id="+wSpace.getID()+", name="+wSpace.getName()+", owner="+wSpace.getOwner()+", args="+wSpace.getArgs()+", created="+wSpace.getCreated()+", and modified ="+wSpace.getModified());
      } else {
        wSpace = new Workspace(id, owner, args);
        out.println("initializing new workspace with id="+wSpace.id+", name="+id+", owner="+wSpace.owner+", args="+args.toString()+", and created="+wSpace.created);
        System.out.println("initializing new workspace with id="+wSpace.id+", name="+id+", owner="+wSpace.owner+", args="+args.toString()+", and created="+wSpace.created);
      }

      String isStored = "false";
      if (!inDB) {
        isStored=myShepherd.storeNewWorkspace(wSpace);
      } else {
        isStored=String.valueOf(overwrite);
      }
      out.println("workspace stored = "+isStored);
      System.out.println("workspace stored = "+isStored);



    } catch(Exception e) {
      throw new IOException(e.toString());
    } finally {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
  }
}
