package org.ecocean.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.web.servlet.OncePerRequestFilter;
import org.ecocean.User;
import org.ecocean.api.auth.JwtService;
import org.ecocean.api.submission.SubmissionException;
import org.ecocean.api.submission.SubmissionPolicy;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/** Bearer-only pilot; no login, cookie fallback, or inherited session roles. */
public class SubmissionAuthenticationFilter extends OncePerRequestFilter {
    public static final String ACTOR = "org.ecocean.submission.actor";
    public static final class Actor {
        public final String id;
        public final boolean admin;
        public Actor(String id, boolean admin) { this.id = id; this.admin = admin; }
    }
    @Override protected void doFilterInternal(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        response.setHeader("Cache-Control", "no-store");
        HttpServletRequest authenticated;
        try {
            String method = request.getMethod();
            if (!Set.of("GET", "POST", "PUT", "DELETE").contains(method)) {
                response.setHeader("Allow", "GET, POST, PUT, DELETE");
                throw new SubmissionException(405, "BAD_REQUEST", "Method not supported");
            }
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7))
                throw new SubmissionException(401, "AUTHENTICATION_REQUIRED", "Submission Bearer token required");
            JwtService jwt = jwtService();
            if (!jwt.canVerify()) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Token verification unavailable");
            Claims claims;
            String id, context, scope;
            try {
                claims = jwt.verifySubmission(authorization.substring(7).trim()).getPayload();
                id = claims.getSubject(); context = claims.get("context", String.class);
                scope = claims.get("submissionScope", String.class);
            } catch (JwtException | IllegalArgumentException ex) {
                throw new SubmissionException(401, "AUTHENTICATION_REQUIRED", "Invalid token");
            }
            if (!"context0".equals(context) || !"context0".equals(requestContext(request)) || id == null)
                throw new SubmissionException(401, "AUTHENTICATION_REQUIRED", "Token context or identity invalid");
            if (!SubmissionPolicy.READ.equals(scope) && !SubmissionPolicy.WRITE.equals(scope))
                throw new SubmissionException(403, "ACCESS_DENIED", "Submission capability required");
            Actor actor = lookup(id);
            if (actor == null) throw new SubmissionException(401, "AUTHENTICATION_REQUIRED", "Account unavailable");
            if (!"GET".equals(method)) {
                if (!SubmissionPolicy.WRITE.equals(scope)) throw new SubmissionException(403, "ACCESS_DENIED", "Write capability required");
                admission(id);
            }
            HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
                @Override public Object getAttribute(String name) { return ACTOR.equals(name) ? actor : super.getAttribute(name); }
                @Override public boolean isUserInRole(String role) { return "admin".equals(role) && actor.admin; }
                @Override public java.security.Principal getUserPrincipal() { return () -> actor.id; }
                @Override public String getRemoteUser() { return actor.id; }
            };
            authenticated = wrapped;
        } catch (SubmissionException ex) { error(response, ex); return; }
        catch (Exception ex) {
            System.err.println("Submission authentication failed: " + ex.getClass().getSimpleName());
            error(response, new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Submission authentication unavailable"));
            return;
        }
        chain.doFilter(authenticated, response);
    }
    protected JwtService jwtService() { return JwtService.fromConfig("context0"); }
    protected String requestContext(HttpServletRequest request) { return org.ecocean.servlet.ServletUtilities.getContext(request); }
    protected void admission(String id) { SubmissionPolicy.requireAdmission("context0", id); }
    protected Actor lookup(String id) {
        Shepherd sh = new Shepherd("context0");
        try {
            sh.beginDBTransaction();
            User user = sh.getUserByUUID(id);
            return user == null ? null : new Actor(user.getId(), user.isAdmin(sh));
        } finally { sh.rollbackAndClose(); }
    }
    public static void error(HttpServletResponse response, SubmissionException ex) throws IOException {
        response.setStatus(ex.status);
        if (ex.status == 429 && ex.getMessage().contains("five seconds")) response.setHeader("Retry-After", "5");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(new JSONObject().put("code", ex.code).put("message", ex.getMessage())
            .put("requestId", java.util.UUID.randomUUID().toString()).toString());
    }
}
