package org.ecocean.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.web.servlet.OncePerRequestFilter;
import org.ecocean.CommonConfiguration;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Token-authenticated read path for /api/v3/search/* (encounter index only; gated in SearchApi).
 *
 * Verifies a B-issued RS256 JWT, enforces the context claim + rejects request-context drift,
 * resolves the user by UUID, and WRAPS the request so Shepherd.getUser(request) returns the
 * token user. It deliberately does NOT call subject.login: Wildbook is session-based, and a
 * login on a bearer request could mint a session reusable on write endpoints. No Bearer => the
 * chain proceeds unchanged (browser/session path intact).
 */
public class WildbookTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_AUTH_ATTR = "org.ecocean.tokenAuth";
    /** Filter-verified context, passed to SearchApi so it does not re-resolve from caller-controlled inputs. */
    public static final String TOKEN_CONTEXT_ATTR = "org.ecocean.tokenAuthContext";

    @Override
    protected void doFilterInternal(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String authz = request.getHeader("Authorization");
        // HTTP auth scheme tokens are case-insensitive: accept "Bearer"/"bearer"/etc.
        if ((authz == null) || !authz.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response); // no token: leave session/anon path intact
            return;
        }
        // method allowlist (defence in depth): only safe read methods reach search
        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            deny(response, 405, "method not allowed");
            return;
        }
        String token = authz.substring(7).trim();
        String expected = expectedContext();

        JwtService jwt = jwtService(expected);
        if (!jwt.canVerify()) {
            deny(response, 503, "token auth not configured");
            return;
        }
        // Extract claims INSIDE the catch: jjwt typed-claim access (get(..., String.class),
        // getSubject) can throw JwtException subclasses for ill-typed claims -> treat as 401,
        // never let it escape as a 500.
        String tokenContext;
        String uuid;
        try {
            Jws<Claims> jws = jwt.verify(token);
            Claims claims = jws.getPayload();
            tokenContext = claims.get("context", String.class);
            uuid = claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            deny(response, 401, "invalid token");
            return;
        }
        // context claim must equal server context, AND request-resolved context must not drift
        String reqContext = requestContext(request);
        if (!expected.equals(tokenContext) || !expected.equals(reqContext)) {
            deny(response, 401, "context mismatch");
            return;
        }
        if (!Util.stringExists(uuid)) {
            deny(response, 401, "invalid token subject");
            return;
        }
        final String username = lookupUsername(expected, uuid);
        if (username == null) {
            deny(response, 401, "unknown user");
            return;
        }
        // wrap so getUser(request) -> getUsername(request) -> getUserPrincipal().toString() == username
        final Principal principal = new Principal() {
            public String getName() {
                return username;
            }

            public String toString() {
                return username;
            }
        };
        // Local attribute store: keeps wrapper-set attrs isolated from the underlying session
        // request (important in tests and in production to avoid bleeding auth markers into
        // the original request, and ensures getAttribute(TOKEN_AUTH_ATTR) is visible on the
        // wrapped object passed downstream — HttpServletRequestWrapper.setAttribute delegates
        // to the underlying request, which would be invisible to callers of the wrapper).
        final Map<String, Object> localAttrs = new HashMap<>();
        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() {
                return principal;
            }

            @Override public String getRemoteUser() {
                return username;
            }

            @Override public void setAttribute(String name, Object value) {
                localAttrs.put(name, value);
            }

            @Override public Object getAttribute(String name) {
                if (localAttrs.containsKey(name)) return localAttrs.get(name);
                return super.getAttribute(name);
            }
        };
        wrapped.setAttribute(TOKEN_AUTH_ATTR, Boolean.TRUE);
        wrapped.setAttribute(TOKEN_CONTEXT_ATTR, expected);
        chain.doFilter(wrapped, response);
    }

    // ----- protected seams (overridden in unit tests) -----

    protected String expectedContext() {
        String c = CommonConfiguration.getApiAccessProperty("jwtContext", "context0");
        return Util.stringExists(c) ? c : "context0";
    }

    protected String requestContext(HttpServletRequest request) {
        return ServletUtilities.getContext(request);
    }

    protected JwtService jwtService(String context) {
        return JwtService.fromConfig(context);
    }

    protected String lookupUsername(String context, String uuid) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("WildbookTokenAuthenticationFilter");
        myShepherd.beginDBTransaction();
        try {
            User user = myShepherd.getUserByUUID(uuid);
            return (user != null) ? user.getUsername() : null;
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private void deny(HttpServletResponse response, int status, String message)
    throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        JSONObject err = new JSONObject();
        err.put("success", false);
        err.put("error", message);
        java.io.PrintWriter w = response.getWriter();
        w.write(err.toString());
        w.close();
    }
}
