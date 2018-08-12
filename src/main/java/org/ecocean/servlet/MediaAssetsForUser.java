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
import org.ecocean.media.*;

public class MediaAssetsForUser extends HttpServlet {

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
    myShepherd.beginDBTransaction();
    myShepherd.setAction("MediaAssetsForUser.class");
    PrintWriter out = response.getWriter();

    JSONArray assets = new JSONArray();

    String owner = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;

    try {
      // args is either a) a singleton argument that's a stringified JSONObject, or b) constructed with requestParamsToJSON

      ArrayList<MediaAsset> mAssets = new ArrayList<MediaAsset>();

      if (request.getParameter("detectionStatus")!=null) {
        mAssets = myShepherd.getMediaAssetsForOwner(owner, request.getParameter("detectionStatus"));
      } else {
        mAssets = myShepherd.getMediaAssetsForOwner(owner);
      }
      
      String offsetStr = request.getParameter("offset");
      int offset = (offsetStr==null) ? 0 : Integer.parseInt(offsetStr);
      String rangeStr = request.getParameter("range");
      int range = (rangeStr==null) ? 1000 : Integer.parseInt(rangeStr);

      for (int i=offset;i<mAssets.size() && i<(offset+range);i++) {
        assets.put(mAssets.get(i).sanitizeJson(request, new JSONObject()));
      }

      out.println(assets.toString());

    } catch(Exception e) {
      //throw new IOException(e.toString());
      e.printStackTrace(out);
    } finally {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
  }
}
