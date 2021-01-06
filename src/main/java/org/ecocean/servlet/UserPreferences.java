package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;

public class UserPreferences extends HttpServlet {

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

        System.out.println("==> In UserPreferences Servlet ");

        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserPreferences.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        String action = j.optString("action", null);

        try {
            res.put("success","false");
            if (Util.stringExists(action)) {
                User user = myShepherd.getUser(request);
                if(user!=null){
                }else{
                }
                if ("setProjectContext".equals(action)) {
                    String defaultProjectId = j.optString("projectId", null);
                    if (Util.stringExists(defaultProjectId)) {
                        user.setProjectIdForPreferredContext(defaultProjectId);
                        myShepherd.updateDBTransaction();
                        setSuccess(res, response);
                    }
                }
                if("setUserConsolidationChoicesTrue".equals(action)){
                  user.setPreference("userConsolidationChoicesMade", "true");
                  myShepherd.updateDBTransaction();
                  setSuccess(res, response);
                }
                if("setUserConsolidationChoicesFalse".equals(action)){
                  user.setPreference("userConsolidationChoicesMade", "false");
                  myShepherd.updateDBTransaction();
                  setSuccess(res, response);
                }
                if("getUserConsolidationChoiceStatus".equals(action)){
                  String consolidationStatus = user.getPreference("userConsolidationChoicesMade");
                  if(Util.stringExists(consolidationStatus)){
                    res.put("userConsolidationChoicesMade", consolidationStatus);
                  } else{
                    res.put("userConsolidationChoicesMade", "false");
                  }
                  setSuccess(res, response);
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

    private void setSuccess(JSONObject res, HttpServletResponse response) {
        res.put("success","true");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
