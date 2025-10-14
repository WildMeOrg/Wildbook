package org.ecocean.api;

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
import org.apache.logging.log4j.ThreadContext;
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

        long startTime = System.currentTimeMillis();
        String requestId = java.util.UUID.randomUUID().toString();
        String context = ServletUtilities.getContext(request);
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        // Set thread context for all logs in this request
        ThreadContext.put("request_id", requestId);
        ThreadContext.put("endpoint", "/api/login");
        ThreadContext.put("http_method", request.getMethod());
        ThreadContext.put("client_ip", clientIp);
        ThreadContext.put("domain", context);

        JSONObject loginData = null;
        String username = null;
        boolean success = false;
        User user = null;
        JSONObject results = new JSONObject();
        Shepherd myShepherd = null;

        try {
            logger.info(new MapMessage()
                    .with("action", "login_started")
                    .with("client_ip", clientIp)
                    .with("user_agent", userAgent));

            loginData = ServletUtilities.jsonFromHttpServletRequest(request);

            if (loginData == null || loginData.isEmpty()) {
                logger.warn(new MapMessage()
                        .with("action", "login_failed")
                        .with("error_type", "empty_payload")
                        .with("client_ip", clientIp));
                results.put("error", "login_empty_data");
                results.put("success", false);
                response.setStatus(400);
                return;
            }

            username = loginData.optString("username", null);
            String password = loginData.optString("password", null);

            // Validate input
            if (username == null || username.trim().isEmpty()) {
                logger.warn(new MapMessage()
                        .with("action", "login_failed")
                        .with("error_type", "missing_username")
                        .with("client_ip", clientIp));
                results.put("error", "missing_username");
                results.put("success", false);
                response.setStatus(400);
                return;
            }

            if (password == null || password.isEmpty()) {
                logger.warn(new MapMessage()
                        .with("action", "login_failed")
                        .with("username", username)
                        .with("error_type", "missing_password")
                        .with("client_ip", clientIp));
                results.put("error", "missing_password");
                results.put("success", false);
                response.setStatus(400);
                return;
            }

            ThreadContext.put("username", username);

            myShepherd = new Shepherd(context);
            myShepherd.setAction("api.Login");
            myShepherd.beginDBTransaction();
            logger.debug(new MapMessage()
                    .with("action", "login_fetching_user")
                    .with("username", username));

            try {
                user = myShepherd.getUser(username);

                if (user == null) {
                    logger.warn(new MapMessage()
                            .with("action", "login_failed")
                            .with("username", username)
                            .with("error_type", "user_not_found")
                            .with("client_ip", clientIp));
                    results.put("error", "invalid_credentials");
                    results.put("success", false);
                    response.setStatus(401);
                    return;
                }

                String salt = user.getSalt();
                String hashedPassword = ServletUtilities.hashAndSaltPassword(password, salt);
                UsernamePasswordToken token = new UsernamePasswordToken(username, hashedPassword);


                // Get the subject and attempt login
                Subject subject = SecurityUtils.getSubject();
                Session session = subject.getSession();
                String sessionId = session.getId().toString();

                ThreadContext.put("session_id", sessionId);
                ThreadContext.put("user_id", user.getUsername());

                logger.debug(new MapMessage()
                        .with("action", "login_attempting_authentication")
                        .with("username", username)
                        .with("session_id", sessionId));

                subject.login(token);

                // Login successful
                user.setLastLogin((new Date()).getTime());
                myShepherd.commitDBTransaction();
                token.clear();

                success = true;
                results = user.infoJSONObject(myShepherd, true);
                results.put("success", true);

                // Check for redirect URL
                SavedRequest saved = WebUtils.getAndClearSavedRequest(request);
                String redirectUrl = null;
                if (saved != null) {
                    redirectUrl = saved.getRequestUrl();
                    results.put("redirectUrl", redirectUrl);
                }

                long duration = System.currentTimeMillis() - startTime;
                logger.info(new MapMessage()
                        .with("action", "login_success")
                        .with("username", username)
                        .with("user_id", user.getUsername())
                        .with("session_id", sessionId)
                        .with("client_ip", clientIp)
                        .with("has_redirect", redirectUrl != null)
                        .with("duration_ms", duration));
            } catch (UnknownAccountException ex) {
                logger.warn(new MapMessage()
                        .with("action", "login_failed")
                        .with("username", username)
                        .with("error_type", "unknown_account")
                        .with("client_ip", clientIp)
                        .with("duration_ms", System.currentTimeMillis() - startTime), ex);
                results.put("error", "invalid_credentials");
                results.put("success", false);

            } catch (IncorrectCredentialsException ex) {
                logger.warn(new MapMessage()
                        .with("action", "login_failed")
                        .with("username", username)
                        .with("error_type", "incorrect_password")
                        .with("client_ip", clientIp)
                        .with("duration_ms", System.currentTimeMillis() - startTime), ex);
                results.put("error", "invalid_credentials");
                results.put("success", false);

            } catch (Exception ex) {
                logger.error(new MapMessage()
                        .with("action", "login_failed")
                        .with("username", username)
                        .with("error_type", ex.getClass().getSimpleName())
                        .with("error_message", ex.getMessage())
                        .with("client_ip", clientIp)
                        .with("duration_ms", System.currentTimeMillis() - startTime), ex);
                results.put("error", "authentication_error");
                results.put("success", false);

            } finally {
                if (myShepherd != null) {
                    if (!success && myShepherd.isDBTransactionActive()) {
                        myShepherd.rollbackDBTransaction();
                    }
                    myShepherd.closeDBTransaction();
                }
            }
        } finally {
            // Final response
            int statusCode = success ? 200 : (results.has("error") ? 401 : 500);
            response.setStatus(statusCode);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(results.toString());

            // Final log with summary
            long totalDuration = System.currentTimeMillis() - startTime;
            if (!success && username != null) {
                logger.info(new MapMessage()
                        .with("action", "login_completed")
                        .with("username", username)
                        .with("success", false)
                        .with("status_code", statusCode)
                        .with("duration_ms", totalDuration));
            }
            // Clear thread context
            ThreadContext.clearAll();
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // If multiple IPs in X-Forwarded-For, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
