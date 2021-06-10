
package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Route;
import org.ecocean.Shepherd;
import org.ecocean.media.Feature;
import org.ecocean.movement.Path;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

public class RouteList extends HttpServlet {
  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  public void doGet(final HttpServletRequest request,
      final HttpServletResponse response) throws ServletException, IOException {
    System.out.println("------------------- get " + request.getParameter("action"));
    doPost(request, response);
  }

  @Override
  public void doPost(final HttpServletRequest request,
      final HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");
    System.out.println("------------------- post " + request.getParameter("action"));
    // init variables
    PrintWriter out = response.getWriter();
    String context = ServletUtilities.getContext(request);
    JSONObject rtn = new JSONObject();
    Shepherd myShepherd = new Shepherd(context);
    
    
    myShepherd.beginDBTransaction();
    if(request.getParameter("action").equals("getList")){
      Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Route");
      q.setOrdering("startTime");
      Collection c = (Collection) (q.execute());
      JSONArray jarr = new JSONArray();
      JSONObject jrt = null;
      for (Route rt : new ArrayList<Route>(c)) {
        jrt = new JSONObject();
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
      rtn.put("data", jarr);
    } else if (request.getParameter("action").equals("delete")){
      String routeId = request.getParameter("id");
      System.out.println("-- " + routeId);
      Route route = (Route) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Route.class, routeId), true));
      System.out.println("-- after" + route.getId());
      myShepherd.getPM().deletePersistent(route);
    } else if (request.getParameter("action").equals("save")) {
      System.out.println("action " + request.getParameter("name"));
      System.out.println("locationId " + request.getParameter("locationId"));
      System.out.println("startTime " + request.getParameter("startTime"));
      System.out.println("endTime " + request.getParameter("endTime"));
      System.out.println("path " + request.getParameter("path"));
      
        JSONObject jsonIn = ServletUtilities.jsonFromHttpServletRequest(request);
        myShepherd.setAction("routeCreator-save");
        myShepherd.beginDBTransaction();
        Route route = new Route();
        route.setName(jsonIn.optString("name", null));
        route.setStartTime(new DateTime(jsonIn.optString("startTime", null)));
        route.setEndTime(new DateTime(jsonIn.optString("endTime", null)));
        route.setLocationId(jsonIn.optString("locationId", null));
        route.setPath(Path.fromJSONArray(jsonIn.optJSONArray("path")));
        myShepherd.getPM().makePersistent(route);
        rtn.put("success", true);
        rtn.put("routeId", route.getId());
        System.out.println("=========== " + rtn.toString(4));
        out.println(rtn.toString(4));
    }
    rtn.put("success", true);
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    response.setContentType("text/json");
    out.println(rtn.toString(4));
    out.close();
  }

}
