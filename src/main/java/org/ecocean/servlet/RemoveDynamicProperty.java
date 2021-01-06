package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.json.JSONObject;

public class RemoveDynamicProperty extends HttpServlet {

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

        // can make usable for Encounter and Occurrence, it's the same operation

        //String occId = j.getString("occId");
        String encId = j.getString("encId");
        String dPropKey = j.getString("dPropKey");

        //if (occId!=null&&!"".equals(occId)&&myShepherd.isOccurrence(occId)) {
            // nothin yet
        //}

        if (encId!=null&&!"".equals(encId)&&myShepherd.isEncounter(encId)) {
            try {
                Encounter enc = myShepherd.getEncounter(encId);
                if (enc!=null) {
                    System.out.println("trying to remove this DP: "+dPropKey);
                    myShepherd.beginDBTransaction();
                    enc.removeDynamicProperty(dPropKey); 
                    myShepherd.commitDBTransaction();
                }
                if (!enc.hasDynamicProperty(dPropKey)) {
                    resp.put("success", "true");
                }
            } catch (Exception e) {
                myShepherd.rollbackDBTransaction();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                e.printStackTrace();
            }
            
        }

        out.print(resp);
        out.close();
        myShepherd.closeDBTransaction();


    }
    
}


