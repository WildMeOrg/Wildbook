package org.ecocean.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.auth.ServiceKey;
import org.ecocean.auth.ServiceKeyCredential;
import org.ecocean.auth.ServiceKeyException;
import org.ecocean.auth.ServiceKeyStore;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Service key management:
 *   GET    /api/v3/service-keys/organizations              organizations the caller can manage
 *   GET    /api/v3/organizations/{orgId}/service-keys        list
 *   POST   /api/v3/organizations/{orgId}/service-keys        create {"label","scopes":[...],"days"}
 *   DELETE /api/v3/organizations/{orgId}/service-keys/{keyId} revoke one (idempotent)
 *   DELETE /api/v3/organizations/{orgId}/service-keys        revoke all
 * Every request needs a fresh HTTP Basic credential, checked here; a session cookie alone is never
 * enough (Shiro maps these paths to anon). Mutations refuse a foreign Origin.
 */
public class ServiceKeys extends ApiBase {
    private static final String CONTEXT = "context0";
    private static final int MAX_BODY_CHARS = 16 * 1024;

    /** A parsed request path. */
    private static final class Route {
        final boolean discovery;
        final String orgId;
        final String keyId;

        Route(boolean discovery, String orgId, String keyId) {
            this.discovery = discovery; this.orgId = orgId; this.keyId = keyId;
        }
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle("GET", request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle("POST", request, response);
    }

    @Override protected void doDelete(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle("DELETE", request, response);
    }

    /**
     * Whether {@code basicUser} may manage {@code org}'s service keys. PR-B: site admins only.
     * Task D.1 widens this; keep all key-management authorization here.
     */
    protected boolean canManage(Shepherd sh, User basicUser, Organization org) {
        return basicUser.isAdmin(sh);
    }

    private void handle(String method, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        response.setHeader("Cache-Control", "no-store");
        Route route = route(request.getServletPath(), request.getPathInfo());
        if (route == null) {
            writeError(response, 404, "not found");
            return;
        }
        boolean allowed = route.discovery ? "GET".equals(method)
            : route.keyId != null ? "DELETE".equals(method) : true;
        if (!allowed) {
            response.setHeader("Allow", route.discovery ? "GET" : route.keyId != null ? "DELETE" : "GET, POST, DELETE");
            writeError(response, 405, "method not allowed");
            return;
        }
        if (!"GET".equals(method) && !sameOrigin(request)) {
            System.out.println("ServiceKeys " + method + " DENIED (foreign origin) ip=" + request.getRemoteAddr());
            writeError(response, 403, "origin not allowed");
            return;
        }
        String[] cred = parseBasic(request.getHeader("Authorization"));
        if (cred == null) {
            writeError(response, 401, "basic credentials required");
            return;
        }

        Shepherd myShepherd = new Shepherd(CONTEXT);
        myShepherd.setAction("api.ServiceKeys." + method);
        myShepherd.beginDBTransaction();
        try {
            User user = Util.stringExists(cred[0]) ? myShepherd.getUser(cred[0]) : null;
            // service accounts can never manage keys, whatever checkPassword says
            if (user == null || user.isServiceAccount() || !user.checkPassword(cred[1])) {
                System.out.println("ServiceKeys DENIED (bad credentials) ip=" + request.getRemoteAddr());
                writeError(response, 401, "invalid credentials");
                return;
            }
            if (route.discovery) {
                JSONArray orgs = new JSONArray();
                for (Organization org : myShepherd.getAllOrganizationsStrict()) {
                    if (!canManage(myShepherd, user, org)) continue;
                    orgs.put(new JSONObject().put("id", org.getId())
                        .put("name", org.getName() == null ? JSONObject.NULL : org.getName()));
                }
                writeJson(response, 200, new JSONObject().put("organizations", orgs));
                return;
            }
            Organization org = ServiceKeyStore.organization(myShepherd, route.orgId);
            if (org == null) {
                writeError(response, 404, "organization not found");
                return;
            }
            if (!canManage(myShepherd, user, org)) {
                System.out.println("ServiceKeys " + method + " DENIED (not allowed) user=" + user.getId()
                    + " org=" + org.getId());
                writeError(response, 403, "not allowed to manage this organization's service keys");
                return;
            }
            // the acting admin's user id (fits the 36-char column whatever the username's length)
            String by = user.getId();
            if ("GET".equals(method)) {
                JSONArray keys = new JSONArray();
                for (ServiceKey key : ServiceKeyStore.list(myShepherd, org.getId()))
                    keys.put(ServiceKeyStore.toJson(myShepherd, key));
                writeJson(response, 200, new JSONObject().put("keys", keys));
            } else if ("POST".equals(method)) {
                create(request, response, myShepherd, org, user);
            } else if (route.keyId != null) {
                if (!ServiceKeyStore.revoke(myShepherd, org.getId(), route.keyId, by)) {
                    writeError(response, 404, "service key not found");
                    return;
                }
                if (!commit(myShepherd, response)) return;
                System.out.println("ServiceKeys revoked key=" + route.keyId + " org=" + org.getId()
                    + " by user=" + user.getId());
                writeJson(response, 200, new JSONObject().put("keyId", route.keyId).put("revoked", true));
            } else {
                int n = ServiceKeyStore.revokeAll(myShepherd, org.getId(), by);
                if (!commit(myShepherd, response)) return;
                System.out.println("ServiceKeys revoked all org=" + org.getId() + " count=" + n
                    + " by user=" + user.getId());
                writeJson(response, 200, new JSONObject().put("revoked", n));
            }
        } catch (ServiceKeyException ex) {
            writeError(response, ex.status, ex.getMessage());
        } catch (Exception ex) {
            System.out.println("ServiceKeys " + method + " failed: " + ex.getClass().getName());
            writeError(response, 500, "service key request failed");
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    private void create(HttpServletRequest request, HttpServletResponse response, Shepherd myShepherd,
        Organization org, User user) throws IOException {
        JSONObject body;
        try {
            String text = readBody(request);
            body = text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (JSONException ex) {
            writeError(response, 400, "request body must be a JSON object");
            return;
        }
        Object labelValue = body.opt("label");
        if (labelValue != null && labelValue != JSONObject.NULL && !(labelValue instanceof String)) {
            writeError(response, 400, "label must be a string");
            return;
        }
        String label = labelValue instanceof String ? (String)labelValue : null;
        Set<String> scopes = new LinkedHashSet<>();
        Object scopeValue = body.opt("scopes");
        if (scopeValue != null && scopeValue != JSONObject.NULL) {
            if (!(scopeValue instanceof JSONArray)) {
                writeError(response, 400, "scopes must be an array of strings");
                return;
            }
            JSONArray arr = (JSONArray)scopeValue;
            for (int i = 0; i < arr.length(); i++) {
                if (!(arr.get(i) instanceof String)) {
                    writeError(response, 400, "scopes must be an array of strings");
                    return;
                }
                scopes.add((String)arr.get(i));
            }
        }
        if (scopes.isEmpty()) {
            writeError(response, 400, "at least one scope is required");
            return;
        }
        int days = ServiceKeyStore.DEFAULT_DAYS;
        Object daysValue = body.opt("days");
        if (daysValue != null && daysValue != JSONObject.NULL) {
            if (!(daysValue instanceof Integer) && !(daysValue instanceof Long)) {
                writeError(response, 400, "days must be a whole number");
                return;
            }
            long d = ((Number)daysValue).longValue();
            if (d < ServiceKeyStore.MIN_DAYS || d > ServiceKeyStore.MAX_DAYS) {
                writeError(response, 400, "days must be between " + ServiceKeyStore.MIN_DAYS + " and "
                    + ServiceKeyStore.MAX_DAYS);
                return;
            }
            days = (int)d;
        }
        ServiceKeyCredential.Issued issued = ServiceKeyStore.create(myShepherd, org, label, scopes, days,
            user.getId());
        ServiceKey key = ServiceKeyStore.get(myShepherd, org.getId(), issued.keyId);
        if (key == null) throw new IllegalStateException("created key not readable");
        JSONObject out = ServiceKeyStore.toJson(myShepherd, key);
        out.put("key", issued.plaintext);
        out.put("organizationId", org.getId());
        if (!commit(myShepherd, response)) return;
        System.out.println("ServiceKeys created key=" + issued.keyId + " org=" + org.getId() + " by user="
            + user.getId());
        writeJson(response, 201, out);
    }

    private boolean commit(Shepherd myShepherd, HttpServletResponse response) throws IOException {
        if (myShepherd.commitDBTransactionWithStatus()) return true;
        writeError(response, 500, "service key change failed");
        return false;
    }

    /** null for any path this servlet does not serve. */
    private static Route route(String servletPath, String pathInfo) {
        if (servletPath == null || pathInfo == null) return null;
        String[] parts = pathInfo.split("/", -1);
        // parts[0] is the empty string before the leading slash
        if (parts.length < 2 || !parts[0].isEmpty()) return null;
        if (servletPath.equals("/api/v3/service-keys")) {
            return (parts.length == 2 && parts[1].equals("organizations")) ? new Route(true, null, null) : null;
        }
        if (!servletPath.equals("/api/v3/organizations")) return null;
        if (parts.length < 3 || parts[1].isEmpty() || !parts[2].equals("service-keys")) return null;
        if (parts.length == 3) return new Route(false, parts[1], null);
        if (parts.length == 4 && !parts[3].isEmpty()) return new Route(false, parts[1], parts[3]);
        return null;
    }

    /**
     * No Origin (curl, server-to-server) is allowed. Otherwise the Origin's host[:port] must equal the
     * request's Host header, both lowercased with a default port (80/443) dropped. The scheme is
     * ignored, so a TLS-terminating proxy in front of Tomcat does not turn same-site requests away.
     * "null", an unparseable Origin, or a missing Host header is refused.
     */
    static boolean sameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) return true;
        String host = request.getHeader("Host");
        if (host == null || host.trim().isEmpty()) return false;
        try {
            URI uri = new URI(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null) return false;
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) return false;
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
            String originHost = uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getPort() > 0) originHost += ":" + uri.getPort();
            return withoutDefaultPort(originHost).equals(withoutDefaultPort(host.trim().toLowerCase(Locale.ROOT)));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static String withoutDefaultPort(String hostPort) {
        if (hostPort.endsWith(":80")) return hostPort.substring(0, hostPort.length() - 3);
        if (hostPort.endsWith(":443")) return hostPort.substring(0, hostPort.length() - 4);
        return hostPort;
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        if (reader == null) return "";
        char[] buf = new char[2048];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
            if (sb.length() > MAX_BODY_CHARS) throw new ServiceKeyException(413, "request body too large");
        }
        return sb.toString();
    }

    private static void writeJson(HttpServletResponse response, int code, JSONObject body)
        throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.getWriter().write(body.toString());
    }

    private static void writeError(HttpServletResponse response, int code, String message)
        throws IOException {
        writeJson(response, code, new JSONObject().put("success", false).put("error", message));
    }
}
