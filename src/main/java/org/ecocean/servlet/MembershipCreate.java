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

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.URL;

import java.io.*;

public class MembershipCreate extends HttpServlet {


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
        myShepherd.setAction("MembershipCreate.java");

        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        System.out.println("==> Hit the MembershipCreate Servlet.. ");

        String miId = null;
        String groupName;
        String roleName;
        String startDate;
        String endDate;

        MarkedIndividual mi = null;
        try {
            if (myShepherd.isMarkedIndividual(miId)) {
                mi = myShepherd.getMarkedIndividual(miId);
            }
        
            miId = j.optString("miId");
            groupName = j.optString("groupName");
            roleName = j.optString("roleName");
            startDate = j.optString("startDate");
            endDate = j.optString("endDate");
            SocialUnit su = myShepherd.getSocialUnit(groupName);
            if (su==null) {
                su = new SocialUnit(groupName);
            }

            Membership membership = null;
            if (su.hasMarkedIndividualAsMember(mi)) {
                membership = su.getMembershipForMarkedIndividual(mi);
            } else {
                //membership = new Membership(mi, roleName, startDate, endDate);
                membership = new Membership(mi, roleName);
            }

        } catch (NullPointerException npe) {
            npe.printStackTrace();
        } catch (JSONException je) {
            je.printStackTrace();
        }

        System.out.println("prototype MembershipCreate servlet end");




        // membershipJSON[groupName] = $("#socialGroupName").value();
        // membershipJSON[roleName] = $("#socialRoleName").value();
        // membershipJSON[startDate] = $("#socialGroupMembershipStart").value();
        // membershipJSON[endDate] = $("#socialGroupMembershipEnd").value();
    

    }


}