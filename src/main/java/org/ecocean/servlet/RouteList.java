
package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Shepherd;
import org.ecocean.Decision;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.movement.Path;
import org.ecocean.Encounter;
import org.ecocean.Route;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.AccessControl;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RouteList extends HttpServlet {
  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  public void doGet(final HttpServletRequest request,
      final HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  @Override
  public void doPost(final HttpServletRequest request,
      final HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");
    PrintWriter out = response.getWriter();
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();

    JSONObject rtn = new JSONObject("{\"success\": true}");
    Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Route");
    q.setOrdering("startTime");
    Collection c = (Collection) (q.execute());
    JSONArray jarr = new JSONArray();
    for (Route rt : new ArrayList<Route>(c)) {
      JSONObject jrt = new JSONObject();
      jrt.put("id", rt.getId());
      jrt.put("name", rt.getName());
      jrt.put("locationId", rt.getLocationId());
      jrt.put("startTime", rt.getStartTime());
      jrt.put("endTime", rt.getEndTime());
      Path path = rt.getPath();
      if (path != null) {
        JSONArray pts = Path.toJSONArray(path.getPointLocations());
        jrt.put("path", pts);
      }
      jarr.put(jrt);
    }
    q.closeAll();
    rtn.put("routes", jarr);
    System.out.println(rtn.toString());
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    response.setContentType("text/json");
    out.println(rtn.toString(4));
    out.close();
  }

}
