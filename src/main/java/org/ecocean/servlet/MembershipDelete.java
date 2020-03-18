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

        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MembershipDelete.java");

        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        System.out.println("==> Hit the MembershipDelete Servlet.. ");

        String groupName = j.optString("groupName");
        String individualId = j.optString("individualId");

        JSONObject res = new JSONObject();
        try {
            MarkedIndividual mi = myShepherd.getMarkedIndividual(individualId);
            if (mi==null) {
                res.put("success", "false"); 
            }
            SocialUnit su = myShepherd.getSocialUnit(groupName);

            myShepherd.beginDBTransaction();
            if (su.hasMarkedIndividualAsMember(mi)) {
                su.removeMember(mi);
            }
            myShepherd.commitDBTransaction();

        } catch (Exception e) {
            myShepherd.rollbackAndClose();
            e.printStackTrace();
        } finally {
            myShepherd.closeDBTransaction();
        }

        System.out.println("prototype MembershipDelete servlet end");
    
    }


}