package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

import org.json.JSONObject;

public class UserCheck extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String context = "context0";
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserCheck.class");

        // set up for response
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();

        // check for an existing user
        JSONObject returnJson = new JSONObject();
        returnJson.put("success", false);
        JSONObject jsonRes = new JSONObject();
        myShepherd.beginDBTransaction();
        try {
            jsonRes = ServletUtilities.jsonFromHttpServletRequest(request);
        } catch (Exception e) {
            System.out.println("Failed to get json from the http request for UserCheck.");
            e.printStackTrace();
        }
        JSONObject existingUserResultsJson = new JSONObject();
        existingUserResultsJson.put("success", false);
        existingUserResultsJson.put("doesUserExistAlready", false);
        try {
            Boolean checkForExistingUsernameDesired = jsonRes.optBoolean(
                "checkForExistingUsernameDesired", false);
            String targetUsername = jsonRes.optString("username", null);
            if (checkForExistingUsernameDesired && targetUsername != null &&
                !targetUsername.equals("")) {
                User targetUser = myShepherd.getUser(targetUsername);
                if (targetUser != null) {
                    existingUserResultsJson.put("doesUserExistAlready", true);
                }
            }
            existingUserResultsJson.put("success", true);
        } catch (Exception e) {
            System.out.println("userCheck exception while checking for an existing user.");
            e.printStackTrace();
            addErrorMessage(returnJson,
                "userCheck: Exception while checking for an existing user.");
        } finally {
            System.out.println(
                "userCheck closing ajax call for user checking for an existing user....");
            myShepherd.rollbackAndClose();
            returnJson.put("success", true);
            returnJson.put("existingUserResultsJson", existingUserResultsJson);
        }
        // check for an existing email address
        returnJson.put("success", false);
        JSONObject existingEmailAddressResultsJson = new JSONObject();
        existingEmailAddressResultsJson.put("success", false);
        existingEmailAddressResultsJson.put("doesEmailAddressExistAlready", false);
        myShepherd.beginDBTransaction();
        try {
            Boolean checkForExistingEmailDesired = jsonRes.optBoolean(
                "checkForExistingEmailDesired", false);
            String targetEmailAddress = jsonRes.optString("emailAddress", null);
            if (checkForExistingEmailDesired && targetEmailAddress != null &&
                !targetEmailAddress.equals("")) {
                User targetUserForEmail = myShepherd.getUserByEmailAddress(targetEmailAddress);
                if (targetUserForEmail != null) {
                    existingEmailAddressResultsJson.put("doesEmailAddressExistAlready", true);
                }
            }
            existingEmailAddressResultsJson.put("success", true);
        } catch (Exception e) {
            System.out.println("userCheck exception while checking for an existing user.");
            e.printStackTrace();
            addErrorMessage(returnJson,
                "userCheck: Exception while checking for an existing user.");
        } finally {
            System.out.println(
                "userCheck closing ajax call for user checking for an existing user....");
            myShepherd.rollbackAndClose();
            returnJson.put("success", true);
            returnJson.put("existingEmailAddressResultsJson", existingEmailAddressResultsJson);
            if (out != null) {
                out.println(returnJson);
                out.close();
            }
        }
    }
}
