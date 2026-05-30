package org.ecocean.api;

import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * POST /api/v3/can-user-access
 * Admin/service-scoped. Body: {"userUuid": "...", "encounterIds": ["...", ...]}.
 * Returns {"accessible": ["...", ...]} — the subset of encounterIds the target
 * user may VIEW, computed from LIVE Collaboration/role objects (not the indexed
 * viewUsers). Defense-in-depth backstop for the scoped-query kernel.
 */
public class CanUserAccess extends ApiBase {
    private static final int MAX_IDS = 200;
    private static final long MAX_BODY_BYTES = 64L * 1024L; // cap request body (Codex Medium)

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.CanUserAccess.doPost");
        myShepherd.beginDBTransaction();
        try {
            User caller = myShepherd.getUser(request);
            if (caller == null) {
                writeError(response, 401, "unauthenticated");
                return;
            }
            if (!caller.isAdmin(myShepherd)) {
                writeError(response, 403, "admin required");
                return;
            }
            // Fix 3 (Low): parse media type before ';' for an exact match — reject
            // types like "text/application/json" or "application/json-patch+json".
            String ctype = request.getContentType();
            if (ctype == null) {
                writeError(response, 415, "Content-Type must be application/json");
                return;
            }
            String mediaType = ctype.split(";")[0].trim().toLowerCase();
            if (!mediaType.equals("application/json")) {
                writeError(response, 415, "Content-Type must be application/json");
                return;
            }
            // Fix 2 (Medium): pre-flight Content-Length guard.
            if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                writeError(response, 413, "request body too large");
                return;
            }
            JSONObject body;
            try {
                body = readBody(request);
            } catch (BodyTooLargeException ex) {
                writeError(response, 413, "request body too large");
                return;
            } catch (Exception ex) {
                writeError(response, 400, "invalid JSON body");
                return;
            }
            String userUuid = body.optString("userUuid", null);
            JSONArray idsArr = body.optJSONArray("encounterIds");
            if (!Util.stringExists(userUuid) || idsArr == null) {
                writeError(response, 400, "userUuid and encounterIds required");
                return;
            }
            if (idsArr.length() > MAX_IDS) {
                writeError(response, 400, "too many encounterIds (max " + MAX_IDS + ")");
                return;
            }
            User target = myShepherd.getUserByUUID(userUuid);
            JSONArray accessible = new JSONArray();
            // target==null -> empty accessible set (unknown user sees nothing)
            if (target != null) {
                for (int i = 0; i < idsArr.length(); i++) {
                    String encId = idsArr.optString(i, null);
                    if (!Util.stringExists(encId)) continue;
                    Encounter enc = myShepherd.getEncounter(encId);
                    if (enc != null && enc.canUserView(target, myShepherd)) {
                        accessible.put(encId);
                    }
                }
            }
            JSONObject out = new JSONObject();
            out.put("accessible", accessible);
            response.setStatus(200);
            response.setContentType("application/json");
            response.getWriter().write(out.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            writeError(response, 500, "canUserAccess failed");
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    /** Thrown by readBody when the request body exceeds MAX_BODY_BYTES. */
    private static final class BodyTooLargeException extends Exception {
        BodyTooLargeException() { super("request body too large"); }
    }

    /**
     * Fix 2 (Medium): read body in chunks, counting ALL bytes read (not just
     * newline-stripped line lengths). Throws BodyTooLargeException on overflow
     * (maps to 413) and JSONException on bad JSON (maps to 400).
     */
    private JSONObject readBody(HttpServletRequest request) throws IOException, BodyTooLargeException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = request.getReader();
        char[] buf = new char[8192];
        long total = 0;
        int n;
        while ((n = r.read(buf)) != -1) {
            total += n;
            if (total > MAX_BODY_BYTES) throw new BodyTooLargeException();
            sb.append(buf, 0, n);
        }
        return new JSONObject(sb.toString()); // throws JSONException on invalid input
    }

    private void writeError(HttpServletResponse response, int code, String message)
        throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.getWriter().write(new JSONObject().put("success", false)
            .put("error", message).toString());
    }
}
