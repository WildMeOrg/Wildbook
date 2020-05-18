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
import org.joda.time.format.ISODateTimeFormat;

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

        response.setContentType("application/json");
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        String context= ServletUtilities.getContext(request);
        PrintWriter out = response.getWriter();

        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        System.out.println("==> Hit the MembershipCreate Servlet.. ");

        String miId = null;
        String groupName;
        String roleName;
        String startDate;
        String endDate;

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MembershipCreate.java");
        myShepherd.beginDBTransaction();
        
        JSONObject res = new JSONObject();
        MarkedIndividual mi = null;
        try {            
            
            res.put("success","false");
            miId = j.optString("miId", "").trim();
            System.out.println("miID: "+miId);
            if (myShepherd.isMarkedIndividual(miId)) {
                mi = myShepherd.getMarkedIndividual(miId);
                groupName = j.optString("groupName", "").trim();
                roleName = j.optString("roleName", "").trim();
                startDate = j.optString("startDate", null);
                System.out.println("miID: "+miId);
                endDate = j.optString("endDate", null);
                
                //we only need the year-month-day
                if(startDate !=null && startDate.indexOf("T")!=-1) {
                  startDate=startDate.substring(0,startDate.indexOf("T"));
                }
                if(endDate != null && endDate.indexOf("T")!=-1) {
                  endDate=endDate.substring(0,endDate.indexOf("T"));
                }
                
                System.out.println("startDate: "+startDate);
                System.out.println("endDate: "+endDate);
                
                SocialUnit su = myShepherd.getSocialUnit(groupName);
                boolean isNew = false;
                if (su==null) {
                    isNew = true;
                    su = new SocialUnit(groupName);
                    myShepherd.storeNewSocialUnit(su);
                    myShepherd.updateDBTransaction();
                }
                //DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                DateTimeFormatter formatter = ISODateTimeFormat.dateOptionalTimeParser();
                
                Membership membership = null;
                if (su.hasMarkedIndividualAsMember(mi)) {
                    membership = su.getMembershipForMarkedIndividual(mi);
                } else {
                    System.out.println("New membership!");
             
                    // yer one of us now, mate
                    membership = new Membership(mi);
                    myShepherd.storeNewMembership(membership);
                    su.addMember(membership);
                    myShepherd.updateDBTransaction();
                }
                
                if (startDate!=null&&!"".equals(startDate)) {
                  DateTime startDT = DateTime.parse(startDate, formatter); 
                  System.out.println("StartDate parsed: "+startDT.toString());
                  Long startLong = startDT.getMillis();   
                  membership.setStartDate(startLong.longValue());
                } 
                if (endDate!=null&&!"".equals(endDate)) {
                  DateTime endDT = DateTime.parse(endDate, formatter);
                  System.out.println("EndDate parsed: "+endDT.toString());
                  Long endLong = endDT.getMillis(); 
                  membership.setEndDate(endLong.longValue());
                }
                if (roleName!=null&&!"".equals(roleName)) { 
                  membership.setRole(roleName);
                }
                
                myShepherd.updateDBTransaction();
                
    
                response.setContentType("text/plain");
                response.setStatus(HttpServletResponse.SC_OK);
                res.put("isNewSocialUnit",isNew);
                res.put("membershipId",membership.getId());
                res.put("role", roleName);
                res.put("groupName", groupName);
                res.put("startDate", startDate);
                res.put("endDate", endDate);
                res.put("success","true");
            } 
            //don't know which MarkedIndividual this is
            else {
              
              res.put("success","false");
              res.put("error","Unknown MarkedIndividual.");
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
              
            }
            
            

            out.println(res);
            out.close();

        } catch (NullPointerException npe) {
            npe.printStackTrace();
            res.put("success","false");
            res.put("error","NullPointerException npe");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (JSONException je) {
          je.printStackTrace();
          res.put("success","false");
          res.put("error","JSONException je");
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("success","false");
            res.put("error","Exception e");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(res);
        }

        //System.out.println("prototype MembershipCreate servlet end");
    
    }


}