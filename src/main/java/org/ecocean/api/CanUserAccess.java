package org.ecocean.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
            // Codex Medium: validate content-type + cap body size BEFORE reading/parsing.
            String ctype = request.getContentType();
            if (ctype == null || !ctype.toLowerCase().contains("application/json")) {
                writeError(response, 415, "Content-Type must be application/json");
                return;
            }
            if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                writeError(response, 413, "request body too large");
                return;
            }
            JSONObject body = readBody(request);
            if (body == null) { writeError(response, 400, "invalid JSON body"); return; }
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

    private JSONObject readBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = request.getReader();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
                if (sb.length() > MAX_BODY_BYTES) return null; // cap even if Content-Length lied
            }
            return new JSONObject(sb.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int code, String message)
        throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.getWriter().write(new JSONObject().put("success", false)
            .put("error", message).toString());
    }
}
