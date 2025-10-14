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
import org.apache.logging.log4j.message.MapMessage;

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
        String sessionId = "unknown";
        boolean hadActiveSession = false;

        try {
            Subject subject = SecurityUtils.getSubject();

            if (subject != null && subject.isAuthenticated()) {
                hadActiveSession = true;
                Object principal = subject.getPrincipal();
                if (principal != null) {
                    username = principal.toString();
                    ThreadContext.put("username", username);
                }

                Session session = subject.getSession(false);
                if (session != null) {
                    sessionId = session.getId().toString();
                    ThreadContext.put("session_id", sessionId);
                }

                logger.info(new MapMessage()
                        .with("action", "logout_started")
                        .with("username", username)
                        .with("session_id", sessionId)
                        .with("client_ip", clientIp));

                // Perform logout
                subject.logout();

                logger.info(new MapMessage()
                        .with("action", "logout_success")
                        .with("username", username)
                        .with("session_id", sessionId)
                        .with("client_ip", clientIp)
                        .with("duration_ms", System.currentTimeMillis() - startTime));

            } else {
                logger.debug(new MapMessage()
                        .with("action", "logout_no_active_session")
                        .with("client_ip", clientIp));
            }

            // Invalidate HTTP session
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.invalidate();
                logger.debug(new MapMessage()
                        .with("action", "logout_http_session_invalidated")
                        .with("username", username));
            }

            response.setStatus(200);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": true}");

        } catch (Exception ex) {
            logger.error(new MapMessage()
                    .with("action", "logout_error")
                    .with("username", username)
                    .with("error_type", ex.getClass().getSimpleName())
                    .with("error_message", ex.getMessage())
                    .with("client_ip", clientIp)
                    .with("duration_ms", System.currentTimeMillis() - startTime), ex);

            response.setStatus(500);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false, \"error\": \"logout_error\"}");

        } finally {
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
