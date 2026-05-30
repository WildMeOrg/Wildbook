package org.ecocean.api;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * POST /api/v3/auth/token
 * Gated by existing Shiro auth (authcBasicWildbook). Mints a short-lived
 * RS256 token carrying only the caller's identity (uuid + context). The
 * external scoped-access kernel validates it with the public key.
 */
public class AuthToken extends ApiBase {
    private static final long DEFAULT_TTL_MILLIS = 30L * 60L * 1000L; // 30 min

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.AuthToken.doPost");
        myShepherd.beginDBTransaction();
        try {
            User user = myShepherd.getUser(request);
            if (user == null) {
                writeError(response, 401, "unauthenticated");
                return;
            }
            // SECURITY (Codex High #1): pin the token context SERVER-SIDE. Do NOT
            // sign ServletUtilities.getContext(request) — it honors a caller's
            // ?context=, while Basic auth authenticates only against context0, so a
            // caller-influenced context claim would let a user mint a token for a
            // context they didn't authenticate in. v1 is single-context.
            String tokenContext = org.ecocean.CommonConfiguration.getProperty("jwtContext", context);
            if (!Util.stringExists(tokenContext)) tokenContext = "context0";
            JwtService jwt = JwtService.fromConfig(tokenContext);
            if (!jwt.isEnabled()) {
                writeError(response, 503, "token issuance not configured");
                return;
            }
            long ttl = ttlFromConfig(tokenContext);
            String token = jwt.sign(user.getId(), tokenContext, ttl);
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

    private long ttlFromConfig(String context) {
        String v = org.ecocean.CommonConfiguration.getProperty("jwtTtlSeconds", context);
        if (Util.stringExists(v)) {
            try {
                long secs = Long.parseLong(v.trim());
                // Codex Low: clamp to a sane range (1 min .. 24 h)
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
