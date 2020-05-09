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

public class MembershipCreate extends HttpServlet {

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
            JSONObject res = new JSONObject();
            res.put("success","false");
            miId = j.optString("miId", "").trim();
            System.out.println("miID: "+miId);
            if (myShepherd.isMarkedIndividual(miId)) {
                mi = myShepherd.getMarkedIndividual(miId);
                groupName = j.optString("groupName", "").trim();
                roleName = j.optString("roleName", "").trim();
                startDate = j.optString("startDate", null);
                endDate = j.optString("endDate", null);
                SocialUnit su = myShepherd.getSocialUnit(groupName);
                boolean isNew = false;
                if (su==null) {
                    isNew = true;
                    su = new SocialUnit(groupName);
                    myShepherd.storeNewSocialUnit(su);
                }
                Membership membership = null;
                if (su.hasMarkedIndividualAsMember(mi)) {
                    membership = su.getMembershipForMarkedIndividual(mi);
                } else {
                    System.out.println("New membership!");
                    Long startLong = null;
                    Long endLong = null;
                    DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    
                    if (startDate!=null&&!"".equals(startDate)) {
                        DateTime startDT = DateTime.parse(startDate, formatter); 
                        startLong = startDT.getMillis();   
                    } 
                    if (endDate!=null&&!"".equals(endDate)) {
                        DateTime endDT = DateTime.parse(endDate, formatter);
                        endLong = endDT.getMillis(); 
                    }
                    // yer one of us now, mate
                    membership = new Membership(mi, roleName, startLong, endLong);
                    myShepherd.storeNewMembership(membership);
                    su.addMember(membership);
                }
    
                response.setContentType("text/plain");
                res.put("isNewSocialUnit",isNew);
                res.put("membershipId",membership.getId());
                res.put("role", roleName);
                res.put("groupName", groupName);
                res.put("startDate", startDate);
                res.put("endDate", endDate);
                res.put("success","true");
            } 

            PrintWriter out = response.getWriter();
            out.println(res);
            out.close();

        } catch (NullPointerException npe) {
            npe.printStackTrace();
        } catch (JSONException je) {
            je.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        }

        System.out.println("prototype MembershipCreate servlet end");
    
    }


}