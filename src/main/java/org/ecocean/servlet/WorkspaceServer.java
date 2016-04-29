package org.ecocean.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Workspace;

public class WorkspaceServer extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  /**
   * retrieves a workspace object
   * @requestParameter id identifies the workspace
   * @returns the output of the query described by that workspace
   **/
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    String getOut = "";

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");

    try {
      Workspace wSpace = myShepherd.getWorkspace(request.getParameter("id"));

    }
    catch (Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      res.put("error", sw.toString());
      out.println(res.toString());
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    finally {
      myShepherd.rollbackDBTransaction();
      myShepherd.commitDBTransaction();
    }
  }

  /**
   * Creates & persists a new workspace object
   **/
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
    
    if (request.getParameter("id")==null) throw new IOException("WorkspaceServer requires an \"id\" argument");

  }


}
