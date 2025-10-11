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
import org.apache.shiro.web.util.SavedRequest;
import org.apache.shiro.web.util.WebUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MapMessage;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;

public class Login extends ApiBase {
    public Login() {
        super();
    }

    private static final Logger logger = LogManager.getLogger(Login.class);

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        logger.info(new MapMessage()
            .with("action", "login_dopost_started"));
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
            logger.info(new MapMessage()
                    .with("action", "login_dopost_getuser")
                    .with("username", username));
            try {
                user = myShepherd.getUser(username);
                salt = user.getSalt();
                // user.setAcceptedUserAgreement(true);
                // myShepherd.commitDBTransaction();
            } catch (Exception ex) {
                myShepherd.rollbackAndClose();
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
                    results = user.infoJSONObject(myShepherd, true);
                    results.put("success", true);

                    // check for redirect URL
                    SavedRequest saved = WebUtils.getAndClearSavedRequest(request);
                    if (saved != null) {
                        results.put("redirectUrl", saved.getRequestUrl());
                    }
                    logger.info(new MapMessage()
                            .with("action", "login_dopost_userauthenticated")
                            .with("username", username));
                } catch (UnknownAccountException ex) {
                    // username not found
                    // ex.printStackTrace();
                    // results.put("error", ex.getMessage());
                    logger.error(new MapMessage()
                            .with("action", "login_dopost_failed")
                            .with("username", username)
                            .with("error_type", ex.getClass().getSimpleName())
                            .with("error_message", ex.getMessage()), ex);
                    results.put("error", "invalid_credentials");
                } catch (IncorrectCredentialsException ex) {
                    // wrong password
                    // ex.printStackTrace();
                    // results.put("error", ex.getMessage());
                    logger.error(new MapMessage()
                            .with("action", "login_dopost_failed")
                            .with("username", username)
                            .with("error_type", ex.getClass().getSimpleName())
                            .with("error_message", ex.getMessage()), ex);
                    results.put("error", "invalid_credentials");
                } catch (Exception ex) {
                    // ex.printStackTrace();
                    // results.put("error", "unknown error");
                    logger.error(new MapMessage()
                            .with("action", "login_dopost_failed")
                            .with("username", username)
                            .with("error_type", ex.getClass().getSimpleName())
                            .with("error_message", ex.getMessage()), ex);
                    results.put("error", "invalid_credentials");
                } finally {
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                }
            }
            // log this !(user != null) condition?
            if (myShepherd.isDBTransactionActive()) myShepherd.rollbackAndClose();
            logger.info(new MapMessage()
                    .with("action", "login_dopost_setstausandreturn")
                    .with("username", username)
                    .with("status", success));
        }

        if (success) {
            response.setStatus(200);
        } else {
            response.setStatus(401);
        }
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }

    private String getEnvironment() {
        return System.getenv("ENVIRONMENT") != null ?
                System.getenv("ENVIRONMENT") : "production";
    }

    private String getDomainName() {
        return System.getenv("DOMAIN_NAME") != null ?
                System.getenv("DOMAIN_NAME") : "unknown";
    }
}
