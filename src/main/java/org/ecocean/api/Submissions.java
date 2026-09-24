package org.ecocean.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.api.submission.*;
import org.ecocean.security.SubmissionAuthenticationFilter;
import org.ecocean.security.SubmissionAuthenticationFilter.Actor;
import org.json.JSONObject;

/** HTTP adapter for the gated, Bearer-only submission draft pilot. */
public class Submissions extends ApiBase {
    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        try {
            Actor actor = (Actor)request.getAttribute(SubmissionAuthenticationFilter.ACTOR);
            if (actor == null) throw new SubmissionException(401, "AUTHENTICATION_REQUIRED", "Authentication required");
            String path = request.getPathInfo();
            if (path == null || path.equals("/")) path = "";
            String method = request.getMethod();
            if (!method.equals("GET")) SubmissionPolicy.requireAdmission("context0", actor.id);
            SubmissionStore store = store();
            JSONObject result = null;
            if (path.isEmpty() && method.equals("POST")) {
                result = store.create("context0", actor.id, request.getHeader("Idempotency-Key"), body(request));
                response.setStatus(201);
                response.setHeader("Location", request.getContextPath() + "/api/v3/submissions/" + result.getString("id"));
            } else if (path.equals("/capabilities") && method.equals("GET")) {
                result = capabilities();
            } else {
                String[] parts = path.split("/", -1);
                if (parts.length < 2 || !org.ecocean.Util.isUUID(parts[1])) throw new SubmissionException(404, "NOT_FOUND", "Route not found");
                String id = parts[1];
                if (parts.length == 2 && method.equals("GET")) result = store.get("context0", actor.id, id, actor.admin, false);
                else if (parts.length == 2 && method.equals("DELETE")) {
                    store.cancel("context0", actor.id, id, actor.admin, revision(request));
                    response.setStatus(204); return;
                } else if (parts.length == 3 && parts[2].equals("rows") && method.equals("GET"))
                    result = store.get("context0", actor.id, id, actor.admin, true);
                else if (parts.length == 3 && parts[2].equals("rows") && method.equals("PUT"))
                    result = store.replaceRows("context0", actor.id, id, actor.admin, revision(request), body(request));
                else if (parts.length == 3 && parts[2].equals("files") && method.equals("GET"))
                    result = store.manifest("context0", actor.id, id, actor.admin);
                else if (parts.length == 3 && parts[2].equals("files") && method.equals("POST")) {
                    long rev = revision(request);
                    SubmissionFiles storage = new SubmissionFiles("context0", getServletContext());
                    result = store.upload("context0", actor.id, id, actor.admin, rev, storage, limit -> storage.receive(request, limit));
                } else if (parts.length == 3 && parts[2].equals("validate") && method.equals("POST")) {
                    long rev = revision(request); SubmissionJson.keys(body(request));
                    result = store.validate("context0", actor.id, id, actor.admin, rev, new SubmissionFiles("context0", getServletContext()));
                } else if (parts.length == 3 && parts[2].equals("commit") && method.equals("POST")) {
                    result = new SubmissionJobs("context0").enqueue("context0", actor.id, id, actor.admin,
                        revision(request), request.getHeader("Idempotency-Key"), body(request));
                    response.setStatus(202); response.setHeader("Location", request.getContextPath() + "/api/v3/submissions/" + id);
                } else if (parts.length == 3 && parts[2].equals("results") && method.equals("GET")) {
                    int offset = page(request.getParameter("cursor"), 0), limit = page(request.getParameter("limit"), 100);
                    result = new SubmissionJobs("context0").results("context0", actor.id, id, actor.admin, offset, limit);
                } else throw new SubmissionException(404, "NOT_FOUND", "Route not available");
            }
            String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
            if (result.has("statusUrl")) result.put("statusUrl", contextPath + result.getString("statusUrl"));
            if (result.has("links") && result.getJSONObject("links").has("importTask")) {
                JSONObject links = result.getJSONObject("links"); links.put("importTask", contextPath + links.getString("importTask"));
            }
            if (result.has("revision")) response.setHeader("ETag", "\"" + result.getLong("revision") + "\"");
            response.getWriter().write(result.toString());
        } catch (SubmissionException ex) { SubmissionAuthenticationFilter.error(response, ex); }
        catch (org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException | org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException ex) { SubmissionAuthenticationFilter.error(response, new SubmissionException(413, "LIMIT_EXCEEDED", "Multipart size limit exceeded")); }
        catch (org.json.JSONException ex) { SubmissionAuthenticationFilter.error(response, new SubmissionException(400, "BAD_REQUEST", "Invalid JSON")); }
        catch (Exception ex) {
            getServletContext().log("Submissions request failed", ex);
            SubmissionAuthenticationFilter.error(response, new SubmissionException(500, "INTERNAL_ERROR", "Submission operation failed"));
        }
    }
    private int page(String value, int fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,8}")) throw new SubmissionException(400, "BAD_REQUEST", "Invalid pagination value");
        return Integer.parseInt(value);
    }
    protected SubmissionStore store() { return new SubmissionStore("context0"); }
    private JSONObject body(HttpServletRequest request) throws IOException {
        if (request.getContentType() == null || !request.getContentType().split(";")[0].trim().equalsIgnoreCase("application/json"))
            throw new SubmissionException(400, "BAD_REQUEST", "application/json required");
        byte[] bytes = request.getInputStream().readNBytes(SubmissionPolicy.MAX_BODY_BYTES + 1);
        if (bytes.length > SubmissionPolicy.MAX_BODY_BYTES) throw new SubmissionException(413, "LIMIT_EXCEEDED", "Request body limit exceeded");
        return SubmissionJson.parse(bytes);
    }
    private long revision(HttpServletRequest request) {
        String value = request.getHeader("If-Match");
        if (value == null) throw new SubmissionException(428, "PRECONDITION_REQUIRED", "If-Match required");
        if (!value.matches("\"[0-9]{1,18}\"")) throw new SubmissionException(400, "BAD_REQUEST", "Invalid If-Match revision");
        return Long.parseLong(value.substring(1, value.length() - 1));
    }
    private JSONObject capabilities() {
        boolean staging = false;
        try { new SubmissionFiles("context0", getServletContext()); staging = true; }
        catch (RuntimeException unavailable) { /* discovery remains available before storage configuration */ }
        return new JSONObject().put("contractVersion", "1")
            .put("admissionEnabled", SubmissionPolicy.enabled("context0"))
            .put("stagingAvailable", staging)
            .put("commitEnabled", SubmissionPolicy.commitEnabled("context0")).put("authentication", new org.json.JSONArray().put("bearer"))
            .put("processingModes", new org.json.JSONArray().put("import-only"))
            .put("operations", new org.json.JSONArray().put("create").put("get").put("replace-rows").put("get-rows").put("cancel").put("upload").put("get-files").put("validate").put("commit").put("results"))
            .put("limits", new JSONObject().put("maxRows", SubmissionPolicy.MAX_ROWS)
                .put("maxFieldsPerRow", 256)
                .put("maxRequestBytes", SubmissionPolicy.MAX_BODY_BYTES).put("maxDraftsPerUser", 20).put("maxNewDraftsPerDay", 20)
                .put("maxFileBytes", SubmissionFiles.maxFileBytes("context0"))
                .put("maxDraftBytes", SubmissionFiles.MAX_DRAFT_BYTES)
                .put("maxMediaPerEncounter", Math.max(1, org.ecocean.CommonConfiguration.getMaxMediaCountEncounter("context0")))
                .put("maxActiveJobs", 1)
                .put("draftTtlSeconds", SubmissionPolicy.DRAFT_TTL_MILLIS / 1000)
                .put("idempotencyRetentionSeconds", SubmissionPolicy.DRAFT_TTL_MILLIS / 1000))
            .put("rowFields", new JSONObject().put("supported", new org.json.JSONArray(new java.util.TreeSet<>(SubmissionValidator.FIELDS)))
                .put("indexedMedia", "Encounter.mediaAsset0 through Encounter.mediaAsset199")
                .put("required", new org.json.JSONArray().put("Encounter.genus").put("Encounter.specificEpithet")
                    .put("Encounter.year").put("Encounter.locationID")))
            .put("uploadMediaTypes", new org.json.JSONArray().put("image/jpeg").put("image/png"))
            .put("maxImagePixels", SubmissionFiles.MAX_PIXELS).put("maxFiles", SubmissionFiles.MAX_FILES);
    }
}
