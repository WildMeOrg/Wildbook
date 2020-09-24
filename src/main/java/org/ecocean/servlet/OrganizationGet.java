package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;
import java.util.List;

public class OrganizationGet extends HttpServlet {

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
		response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        System.out.println("==> In ProjectGet Servlet ");

        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectGet.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        try {
            res.put("success",false);
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

            String action = "";
            action = j.optString("action", null);

            boolean complete = false;

            if ("getAllForUser".equals(action)||"getAll".equals(action)) {
                User user  = myShepherd.getUser(request);
                if (user!=null) {
                    JSONArray orgJSONArr = new JSONArray();
                    List<Organization> orgs = null;

                    if ("getAllForUser".equals(action)) {
                        orgs = myShepherd.getAllOrganizationsForUser(user);
                    } else if ("getAll".equals(action)) {
                        orgs = myShepherd.getAllOrganizations();
                    }

                    for (Organization org : orgs) {
                        JSONObject orgOb = new JSONObject();
                        List<User> userArr = org.getMembers();
                        JSONArray userJSONArr = new JSONArray();
                        for (User orgUser : userArr) {
                            JSONObject thisUserJSON = new JSONObject();
                            thisUserJSON.put("username", orgUser.getUsername());
                            thisUserJSON.put("id", orgUser.getId());
                            userJSONArr.put(thisUserJSON);
                        }
                        orgOb.put("users", userJSONArr);
                        orgOb.put("name", org.getName());
                        orgOb.put("id", org.getId());
                        orgJSONArr.put(orgOb);
                    }
                    res.put("organizations", orgJSONArr );
                    res.put("success", "true");
                    complete = true;
                }
            }

            out.println(res);
            out.close();

        } catch (NullPointerException npe) {
            npe.printStackTrace();
            addErrorMessage(res, "NullPointerException npe");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (JSONException je) {
            je.printStackTrace();
            addErrorMessage(res, "JSONException je");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            addErrorMessage(res, "Exception e");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(res);
        }
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

}
