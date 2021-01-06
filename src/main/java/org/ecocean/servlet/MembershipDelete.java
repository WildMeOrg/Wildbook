package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.ia.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.ecocean.media.*;
import org.ecocean.social.Membership;
import org.ecocean.social.SocialUnit;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.net.URL;

import java.io.*;

public class MembershipDelete extends HttpServlet {

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
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MembershipDelete.java");
        myShepherd.beginDBTransaction();


        JSONObject j = null;
        try {
            j = ServletUtilities.jsonFromHttpServletRequest(request);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject res = new JSONObject();
        res.put("success", "false"); 
        System.out.println("==> Hit the MembershipDelete Servlet.. ");
        if (j!=null) {
            String groupName = j.optString("groupName");
            String individualId = j.optString("miId");
            // keep these around in response for usage if error
            res.put("groupName", groupName);
            res.put("miId", individualId);
            try {
                MarkedIndividual mi = myShepherd.getMarkedIndividual(individualId);
                if (mi!=null) {
                    SocialUnit su = myShepherd.getSocialUnit(groupName);
        
                    myShepherd.beginDBTransaction();
                    if (su.hasMarkedIndividualAsMember(mi)) {
                        su.removeMember(mi, myShepherd);
                        res.put("success", "true"); 
                    }
                    myShepherd.commitDBTransaction();
                    response.setStatus(HttpServletResponse.SC_OK);
                    System.out.println("I think I removed the indy from the SocialUnit: "+(!su.hasMarkedIndividualAsMember(mi)));
                    
                    if (su.getMarkedIndividuals().size()<1) {
                        System.out.println("[INFO]: No more members left in SocialUnit "+su.getSocialUnitName()+", deleting.");
                        myShepherd.throwAwaySocialUnit(su);
                    }

                } else {
                    res.put("success","false");
                    res.put("error","Unknown MarkedIndividual.");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                
            } catch (Exception e) {
                myShepherd.rollbackAndClose();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                e.printStackTrace();
            } finally {
                myShepherd.closeDBTransaction();
            }
        }

        PrintWriter out = response.getWriter();
        out.println(res);
    
    }


}