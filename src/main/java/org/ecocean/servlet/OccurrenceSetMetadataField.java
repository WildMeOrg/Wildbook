package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.json.JSONObject;

public class OccurrenceSetMetadataField extends HttpServlet {

    private static final long serialVersionUID = 1L;

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
        response.setContentType("application/json");
        response.setHeader("Access-Control-Allow-Origin", "*");

        //String context= ServletUtilities.getContext(request);
        PrintWriter out = response.getWriter();

        Shepherd myShepherd = new Shepherd(request);

        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        JSONObject resp = new JSONObject();
        resp.put("success", "false");

        String occId = j.getString("occId");
        if (occId!=null&&!"".equals(occId)&&myShepherd.isOccurrence(occId)) {

            // add as many other basic setter/gettter fields as you want to this simple servlet. 

            Occurrence occ = myShepherd.getOccurrence(occId);

            if (j.has("visibilityIndex")) {
                String val = j.getString("visibilityIndex");
                System.out.println("got "+val+" for visibilityIndex submitted to OccurrenceSetMetadataField");
    
                try {
                    Double vi = Double.parseDouble(val);
                    myShepherd.beginDBTransaction();
                    occ.setVisibilityIndex(vi);
                    myShepherd.commitDBTransaction();
                    resp.put("success", "true");
                    resp.put("visibilityIndex", occ.getVisibilityIndex());
                } catch (Exception e ) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.put("success", "false");
                    myShepherd.rollbackDBTransaction();
                    e.printStackTrace();
                }
            }

            if (j.has("groupComposition")) {
                String val = j.getString("groupComposition");
                System.out.println("got "+val+" for groupComposition submitted to OccurrenceSetMetadataField");
                try {
                    myShepherd.beginDBTransaction();
                    if (val!=null&&!"null".equals(val)) {
                        occ.setGroupComposition(val);
                    } else {
                        occ.setGroupComposition("");
                    }
                    myShepherd.commitDBTransaction();
                    resp.put("success", "true");
                    resp.put("groupComposition", occ.getGroupComposition());
                } catch (Exception e ) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.put("success", "false");
                    myShepherd.rollbackDBTransaction();
                    e.printStackTrace();
                }
            }

            if ("true".equals(resp.getString("success"))) {
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
            } 

            out.print(resp);
            out.close();
            myShepherd.closeDBTransaction();

        }

    }
    
}


