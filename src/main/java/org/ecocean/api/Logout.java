package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletException;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import org.ecocean.servlet.ServletUtilities;

public class Logout extends ApiBase {
    public Logout() {
        super();
    }

    private static final Logger logger = LogManager.getLogger(Logout.class);

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

        // Set thread context
        ThreadContext.put("request_id", requestId);
        ThreadContext.put("endpoint", "/api/logout");
        ThreadContext.put("http_method", request.getMethod());
        ThreadContext.put("client_ip", clientIp);
        ThreadContext.put("domain", context);

        String username = "unknown";
        int statusCode = 500; // Default to error
        boolean success = false;

        try {
            Subject subject = SecurityUtils.getSubject();

            if (subject != null && subject.isAuthenticated()) {
                Object principal = subject.getPrincipal();
                if (principal != null) {
                    username = principal.toString();
                    ThreadContext.put("username", username);
                }

                Session session = subject.getSession(false);
                if (session != null) {
                    ThreadContext.put("session_id", session.getId().toString());
                }

                ThreadContext.put("action", "logout_started");
                logger.info("Logout started");

                // Perform logout
                subject.logout();

                ThreadContext.put("duration_ms", String.valueOf(System.currentTimeMillis() - startTime));
                ThreadContext.put("action", "logout_success");
                logger.info("Logout successful");

            } else {
                ThreadContext.put("action", "logout_no_active_session");
                logger.debug("Logout attempt without active session");
            }

            // Invalidate HTTP session
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.invalidate();
                ThreadContext.put("action", "logout_http_session_invalidated");
                logger.debug("Logout attempt with http session invalidated");
            }

            statusCode = 200;
            success = true;
            response.setStatus(statusCode);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": true}");

        } catch (Exception ex) {
            ThreadContext.put("error_type", ex.getClass().getSimpleName());
            ThreadContext.put("error_message", ex.getMessage());
            ThreadContext.put("action", "logout_error");
            logger.error("Logout error");

            statusCode = 500;
            success = false;
            response.setStatus(statusCode);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false, \"error\": \"logout_error\"}");

        } finally {
            long totalDuration = System.currentTimeMillis() - startTime;
            ThreadContext.put("duration_ms", String.valueOf(totalDuration));
            ThreadContext.put("status_code", String.valueOf(statusCode));
            ThreadContext.put("action", "api_request_completed");

            if (success) {
                logger.info("API request completed");
            } else {
                logger.warn("API request completed");
            }

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
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
