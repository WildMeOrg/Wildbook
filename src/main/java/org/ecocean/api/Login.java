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
            ThreadContext.put("action", "login_started");
            ThreadContext.put("user_agent", userAgent);
            logger.info("Login started");

            loginData = ServletUtilities.jsonFromHttpServletRequest(request);

            if (loginData == null || loginData.isEmpty()) {
                ThreadContext.put("action", "login_failed");
                ThreadContext.put("error_type", "empty_payload");
                logger.warn("Login attempt with empty payload");
                results.put("error", "login_empty_data");
                results.put("success", false);
                response.setStatus(400);
                return;
            }

            username = loginData.optString("username", null);
            ThreadContext.put("username", username != null ? username : "unknown");
            String password = loginData.optString("password", null);

            // Validate input
            if (username == null || username.trim().isEmpty()) {
                ThreadContext.put("action", "login_failed");
                ThreadContext.put("error_type", "missing_username");
                logger.warn("Login attempt with missing username");
                results.put("error", "missing_username");
                results.put("success", false);
                response.setStatus(400);
                return;
            }

            if (password == null || password.isEmpty()) {
                ThreadContext.put("action", "login_failed");
                ThreadContext.put("error_type", "missing_password");
                logger.warn("Login attempt with missing password");
                results.put("error", "missing_password");
                results.put("success", false);
                response.setStatus(400);
                return;
            }

            ThreadContext.put("username", username);
            myShepherd = new Shepherd(context);
            myShepherd.setAction("api.Login");
            myShepherd.beginDBTransaction();
            ThreadContext.put("action", "login_fetching_user");
            logger.debug("Fetching user");

            try {
                user = myShepherd.getUser(username);

                if (user == null) {
                    ThreadContext.put("action", "login_failed");
                    ThreadContext.put("error_type", "invalid_credentials");
                    logger.warn("Login attempt - invalid credentials");
                    results.put("error", "invalid_credentials");
                    results.put("success", false);
                    response.setStatus(401);
                    return;
                }

                String salt = user.getSalt();
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
                    results = user.infoJSONObject(myShepherd, true, false);
                    results.put("success", true);

                    // check for redirect URL
                    SavedRequest saved = WebUtils.getAndClearSavedRequest(request);
                    if (saved != null) {
                        results.put("redirectUrl", saved.getRequestUrl());
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

            // Log one final "completed" event
            long totalDuration = System.currentTimeMillis() - startTime;
            ThreadContext.put("duration_ms", String.valueOf(totalDuration));
            ThreadContext.put("status_code", String.valueOf(statusCode));
            ThreadContext.put("action", "api_request_completed");

            if (success) {
                logger.info("API request completed");
            } else {
                // Already logged a specific failure, but this ensures duration/status are captured
                logger.warn("API request completed");
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
