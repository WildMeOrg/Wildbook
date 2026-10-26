package org.ecocean.api;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.api.auth.JwtService;
import org.ecocean.auth.ServiceKeyAuth;
import org.ecocean.auth.ServiceKeyCredential;
import org.ecocean.auth.ServiceKeyException;
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

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            handleServiceKey(request, response, authorization.substring(7).trim(), context, clientIp);
            return;
        }

        String[] cred = parseBasic(request.getHeader("Authorization"));
        if (cred == null) {
            System.out.println("AuthToken mint DENIED (no Basic credential) ip=" + clientIp);
            // challenge only when no credential was sent at all, for clients that wait for one;
            // other 401s carry no challenge so browsers never show a login dialog
            if (authorization == null) response.setHeader("WWW-Authenticate", "Basic realm=\"Wildbook\"");
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
            String tokenContext = org.ecocean.CommonConfiguration.getApiAccessProperty("jwtContext", context);
            if (!Util.stringExists(tokenContext)) tokenContext = "context0";
            JwtService jwt = JwtService.fromConfig(tokenContext);
            if (!jwt.isEnabled()) {
                writeError(response, 503, "token issuance not configured");
                return;
            }
            long ttl = ttlFromConfig(tokenContext);
            String scope = request.getParameter("scope");
            if (scope != null) {
                if (!org.ecocean.api.submission.SubmissionPolicy.READ.equals(scope)
                    && !org.ecocean.api.submission.SubmissionPolicy.WRITE.equals(scope)) {
                    writeError(response, 400, "unsupported scope");
                    return;
                }
                // Basic credentials are resolved in context0; do not grant a capability for another context.
                if (!context.equals(tokenContext)) {
                    writeError(response, 503, "submission token context unavailable");
                    return;
                }
                if (org.ecocean.api.submission.SubmissionPolicy.WRITE.equals(scope)) {
                    try {
                        org.ecocean.api.submission.SubmissionPolicy.requireAdmission(context, user.getId());
                    } catch (org.ecocean.api.submission.SubmissionException ex) {
                        writeError(response, ex.status, ex.getMessage());
                        return;
                    }
                }
            }
            String token = scope == null ? jwt.sign(user.getId(), tokenContext, ttl)
                : jwt.signSubmission(user.getId(), tokenContext, ttl, scope);
            System.out.println("AuthToken mint OK user=" + username + " ip=" + clientIp);
            JSONObject out = new JSONObject();
            out.put("token", token);
            out.put("tokenType", "Bearer");
            out.put("expiresInSeconds", ttl / 1000L);
            if (scope != null) out.put("scope", scope);
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

    /** Service key mode: exchange a wbsvc_ credential for a token that never outlives the key. */
    private void handleServiceKey(HttpServletRequest request, HttpServletResponse response,
        String credential, String context, String clientIp) throws IOException {
        if (!credential.startsWith(ServiceKeyCredential.PREFIX)) {
            System.out.println("AuthToken mint DENIED (not a service key) ip=" + clientIp);
            writeError(response, 401, "invalid service key");
            return;
        }
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.AuthToken.serviceKey");
        myShepherd.beginDBTransaction();
        try {
            long now = System.currentTimeMillis();
            ServiceKeyAuth.Grant grant;
            try {
                grant = ServiceKeyAuth.authenticate(myShepherd, credential, now);
            } catch (ServiceKeyException ex) {
                System.out.println("AuthToken mint DENIED (service key: " + ex.getMessage() + ") ip="
                    + clientIp);
                writeError(response, ex.status, ex.getMessage());
                return;
            }
            String tokenContext = org.ecocean.CommonConfiguration.getApiAccessProperty("jwtContext", context);
            if (!Util.stringExists(tokenContext)) tokenContext = "context0";
            // the service user and its key live in context0; never mint for another context
            if (!context.equals(tokenContext)) {
                writeError(response, 503, "service key token context unavailable");
                return;
            }
            JwtService jwt = JwtService.fromConfig(tokenContext);
            if (!jwt.isEnabled()) {
                writeError(response, 503, "token issuance not configured");
                return;
            }
            String scope = request.getParameter("scope");
            boolean allowed;
            if (scope == null) {
                allowed = grant.scopes.contains("data:read");
            } else if (org.ecocean.api.submission.SubmissionPolicy.READ.equals(scope)) {
                allowed = grant.scopes.contains(org.ecocean.api.submission.SubmissionPolicy.READ)
                    || grant.scopes.contains(org.ecocean.api.submission.SubmissionPolicy.WRITE);
            } else if (org.ecocean.api.submission.SubmissionPolicy.WRITE.equals(scope)) {
                allowed = grant.scopes.contains(org.ecocean.api.submission.SubmissionPolicy.WRITE);
            } else {
                writeError(response, 400, "unsupported scope");
                return;
            }
            if (!allowed) {
                System.out.println("AuthToken mint DENIED (scope not allowed) key=" + grant.keyId
                    + " org=" + grant.organizationId);
                writeError(response, 403, "scope not allowed for this key");
                return;
            }
            if (org.ecocean.api.submission.SubmissionPolicy.WRITE.equals(scope)) {
                try {
                    org.ecocean.api.submission.SubmissionPolicy.requireAdmission(context, grant.serviceUserId);
                } catch (org.ecocean.api.submission.SubmissionException ex) {
                    writeError(response, ex.status, ex.getMessage());
                    return;
                }
            }
            long ttl = ServiceKeyAuth.exchangeTtlMillis(ttlFromConfig(tokenContext), grant.keyExpiresAt, now);
            if (ttl <= 0) {
                writeError(response, 401, "service key expired");
                return;
            }
            String token = jwt.signUntil(grant.serviceUserId, "context0", ttl, scope, grant.keyExpiresAt);
            // persist lastUsedAt before handing out the token
            if (!myShepherd.commitDBTransactionWithStatus()) {
                writeError(response, 500, "token issuance failed");
                return;
            }
            System.out.println("AuthToken mint OK key=" + grant.keyId + " org=" + grant.organizationId);
            JSONObject out = new JSONObject();
            out.put("token", token);
            out.put("tokenType", "Bearer");
            out.put("expiresInSeconds", ttl / 1000L);
            if (scope != null) out.put("scope", scope);
            out.put("keyExpiresAt", grant.keyExpiresAt);
            response.setStatus(200);
            response.setContentType("application/json");
            response.getWriter().write(out.toString());
        } catch (Exception ex) {
            System.out.println("AuthToken service key exchange failed: " + ex.getClass().getName());
            writeError(response, 500, "token issuance failed");
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private long ttlFromConfig(String context) {
        String v = org.ecocean.CommonConfiguration.getApiAccessProperty("jwtTtlSeconds", context);
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
