package org.ecocean.servlet;


import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONArray;

public class MultipleSubmitAPI extends HttpServlet {
    static final long serialVersionUID = 1L;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        System.out.println("Sent GET to MultipleSubmitAPI");
        String context = ServletUtilities.getContext(request);
        PrintWriter out = response.getWriter();
        
        response.setContentType("application/json");
        //response.setCharacterEncoding("UTF-8");

        JSONObject rtn = new JSONObject();
        try {
            String getLocations = request.getParameter("getLocations");
            System.out.println("getLocations param from query: "+getLocations);
            if (getLocations!=null&&"true".equals(getLocations)) {
                System.out.println("Trying to get locations for dropdown...");
                JSONArray rtnArr = new JSONArray();
                List<String> locs = new ArrayList<>();
                if (CommonConfiguration.getIndexedPropertyValues("locationID", context).size()>0) {
                    System.out.println("Gonna try and return "+CommonConfiguration.getIndexedPropertyValues("locationID", context).size()+" location Ids.");
                    locs = CommonConfiguration.getIndexedPropertyValues("locationID", context);
                    for (String loc : locs) {
                        rtnArr.put(loc);
                    }
                    System.out.println("Final rtnArr: "+rtnArr.toString());
                    rtn.put("locationIds", rtnArr);
                    out.println(rtn);
                    out.close();
                    return;
                }      
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        System.out.println("Sent POST to MultipleSubmitAPI");

        if (ServletUtilities.captchaIsValid(request)) {
            // only need this for POST
        }
    }
}