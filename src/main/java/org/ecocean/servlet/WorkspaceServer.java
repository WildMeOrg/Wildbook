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


import org.ecocean.*;

public class WorkspaceServer extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  /**
   * retrieves a workspace object
   * @requestParameter id identifies the workspace
   * @returns the output of the query described by that workspace (a MediaAsset JSONArray)
   **/
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    JSONObject res;
    try {
      res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    } catch (JSONException e) { //datanucleus JSONObject initialization requires explicit error handling
    }

    String getOut = "";

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");

    try {
      Workspace wSpace = myShepherd.getWorkspace(request.getParameter("id"));
      if (wSpace==null) {
        throw new IOException("No workspace in DB with id="+request.getParameter("id"));
      } else {
        System.out.println("doGet successfully grabbed workspace with id="+wSpace.id+" and queryArg="+wSpace.queryArg);
      }
      request.setAttribute("queryAsString", wSpace.queryAsString);
      request.setAttribute("workspaceID", wSpace.id);
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
   **/
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    PrintWriter out = response.getWriter();

    try {

      // args is either a) a singleton argument that's a stringified JSONObject, or b) constructed with requestParamsToJSON
      JSONObject args = ((request.getParameter("args"))!=null) ? new JSONObject(request.getParameter("args")) : Util.requestParamsToJSON(request);

      String id = request.getParameter("id");
      boolean overwrite = (request.getParameter("overwrite")!=null && request.getParameter("overwrite").equalsIgnoreCase("true"));
      boolean inDB = myShepherd.isWorkspace(id);

      if (inDB && !overwrite) throw new IOException("Workspace with id="+id+" already in database. Include request parameter overwrite=true if you wish to overwrite.");

      String isStored = "false";
      Workspace wSpace = (inDB && overwrite) ? myShepherd.getWorkspace(id) : new Workspace(id, args);
      if (overwrite) wSpace.setArg(args);

      out.println("initializing new workspace with args id="+id+" and args="+args.toString());

      out.println("workspace with id="+id+" already in database? "+inDB);
      if (!inDB) {
        isStored=myShepherd.storeNewWorkspace(wSpace);
      } else {
        isStored="overwritten";
      }
      out.println("workspace stored = "+isStored);



    } catch(Exception e) {
      throw new IOException(e.toString());
    } finally {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }



  }

  private static JSONObject getArgs (HttpServletRequest request) throws JSONException, IOException {
    JSONObject args = new JSONObject();

    String className = request.getParameter("className");
    return args;



  }


}
