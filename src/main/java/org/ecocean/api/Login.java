package org.ecocean.api;

// generally built from servlet/LoginUser.java

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.util.Date;
import org.json.JSONObject;

import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;

public class Login extends ApiBase {
    public Login() {
        super();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        JSONObject loginData = ServletUtilities.jsonFromHttpServletRequest(request);
        boolean success = false;
        User user = null;
        JSONObject results = new JSONObject();

        results.put("error", "login_empty_data");
        results.put("success", false);
        if (loginData != null) {
            String username = loginData.optString("username", null);
            String password = loginData.optString("password", null);
            String salt = null;
            String context = ServletUtilities.getContext(request);
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("api.Login");
            myShepherd.beginDBTransaction();
            try {
                user = myShepherd.getUser(username);
                salt = user.getSalt();
                // user.setAcceptedUserAgreement(true);
                // myShepherd.commitDBTransaction();
            } catch (Exception ex) {
                myShepherd.rollbackDBTransaction();
                results.put("error", "invalid_credentials");
            }
            if (user != null) {
                String hashedPassword = ServletUtilities.hashAndSaltPassword(password, salt);
                UsernamePasswordToken token = new UsernamePasswordToken(username, hashedPassword);
                try {
                    // get the user (aka subject) associated with this request.
                    Subject subject = SecurityUtils.getSubject();
                    Session session = subject.getSession();
                    subject.login(token);
                    user.setLastLogin((new Date()).getTime());
                    myShepherd.commitDBTransaction();
                    token.clear();
                    success = true;
                    results = user.infoJSONObject(context, true);
                    results.put("success", true);
                } catch (UnknownAccountException ex) {
                    // username not found
                    ex.printStackTrace();
                    // results.put("error", ex.getMessage());
                    results.put("error", "invalid_credentials");
                } catch (IncorrectCredentialsException ex) {
                    // wrong password
                    ex.printStackTrace();
                    // results.put("error", ex.getMessage());
                    results.put("error", "invalid_credentials");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // results.put("error", "unknown error");
                    results.put("error", "invalid_credentials");
                } finally {
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                }
            }
            if (myShepherd.isDBTransactionActive()) myShepherd.rollbackAndClose();
        }
        if (success) {
            response.setStatus(200);
        } else {
            response.setStatus(401);
        }
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }
}
