package org.ecocean.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * POST /api/v3/auth/token
 * Mints a short-lived RS256 token. STEP-UP: requires a fresh, valid HTTP Basic credential and
 * verifies it server-side (a Shiro session alone is NOT sufficient) — so a stolen/unlocked session
 * or same-origin script cannot mint without the password.
 */
public class AuthToken extends ApiBase {
    private static final long DEFAULT_TTL_MILLIS = 30L * 60L * 1000L; // 30 min

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle(request, response);
    }

    // package-visible test entry point
    void doPostForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        final String context = "context0"; // Basic auth is pinned to context0 (see filter)
        String clientIp = request.getRemoteAddr();

        String[] cred = parseBasic(request.getHeader("Authorization"));
        if (cred == null) {
            System.out.println("AuthToken mint DENIED (no Basic credential) ip=" + clientIp);
            writeError(response, 401, "basic credentials required");
            return;
        }
        String username = cred[0];
        String password = cred[1];

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.AuthToken.doPost");
        myShepherd.beginDBTransaction();
        try {
            User user = (Util.stringExists(username)) ? myShepherd.getUser(username) : null;
            if ((user == null) || !user.checkPassword(password)) {
                System.out.println("AuthToken mint DENIED (bad credentials) user=" + username
                    + " ip=" + clientIp);
                writeError(response, 401, "invalid credentials");
                return;
            }
            String tokenContext = org.ecocean.CommonConfiguration.getProperty("jwtContext", context);
            if (!Util.stringExists(tokenContext)) tokenContext = "context0";
            JwtService jwt = JwtService.fromConfig(tokenContext);
            if (!jwt.isEnabled()) {
                writeError(response, 503, "token issuance not configured");
                return;
            }
            long ttl = ttlFromConfig(tokenContext);
            String token = jwt.sign(user.getId(), tokenContext, ttl);
            System.out.println("AuthToken mint OK user=" + username + " ip=" + clientIp);
            JSONObject out = new JSONObject();
            out.put("token", token);
            out.put("tokenType", "Bearer");
            out.put("expiresInSeconds", ttl / 1000L);
            response.setStatus(200);
            response.setContentType("application/json");
            response.getWriter().write(out.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            writeError(response, 500, "token issuance failed");
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    /** Parse an HTTP Basic Authorization header into [username, password], or null if absent/invalid. */
    private static String[] parseBasic(String header) {
        if (header == null) return null;
        if (!header.regionMatches(true, 0, "Basic ", 0, 6)) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                StandardCharsets.UTF_8);
            int i = decoded.indexOf(':');
            if (i < 0) return null;
            return new String[] { decoded.substring(0, i), decoded.substring(i + 1) };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long ttlFromConfig(String context) {
        String v = org.ecocean.CommonConfiguration.getProperty("jwtTtlSeconds", context);
        if (Util.stringExists(v)) {
            try {
                long secs = Long.parseLong(v.trim());
                // clamp to a sane range (1 min .. 24 h)
                secs = Math.max(60L, Math.min(secs, 24L * 3600L));
                return secs * 1000L;
            } catch (NumberFormatException ignore) {}
        }
        return DEFAULT_TTL_MILLIS;
    }

    private void writeError(HttpServletResponse response, int code, String message)
        throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.getWriter().write(new JSONObject().put("success", false)
            .put("error", message).toString());
    }
}
