package org.ecocean.api;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.http.*;
import org.ecocean.*;
import org.ecocean.export.MobilePackJobs;
import org.ecocean.export.MobilePackConfiguration;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/** Explicit administrator publication of selected data, independent of ordinary download exports. */
public class MobilePackExport extends ApiBase {
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        // A same-origin custom header prevents cross-site form submissions from publishing data.
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.sendError(403); return;
        }
        String context = ServletUtilities.getContext(request);
        Shepherd shepherd = new Shepherd(context);
        shepherd.beginDBTransaction();
        try {
            User user = shepherd.getUser(request);
            if (user == null || !user.isAdmin(shepherd)) { response.sendError(403); return; }
            if (!MobilePackJobs.enabled(context)) { response.sendError(503, "Publishing is not configured"); return; }
            byte[] bytes = request.getInputStream().readNBytes(4097);
            if (bytes.length > 4096) { response.sendError(413); return; }
            String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            JSONObject body = new JSONObject(raw);
            if (!body.optBoolean("confirmPublic", false)) { response.sendError(400, "Confirm public publication"); return; }
            String query = body.getString("searchQueryId");
            String taxonomy = body.getString("taxonomy").trim();
            String feature = body.getString("featureClass");
            String display = body.getString("displayName").trim();
            String packId = body.optString("packId", "pk-" + UUID.randomUUID());
            if (query.isBlank() || query.length() > 100 || taxonomy.isEmpty() || taxonomy.length() > 200 ||
                feature.isBlank() || feature.length() > 200 ||
                display.isEmpty() || display.length() > 200 || !packId.matches("pk-[a-zA-Z0-9_-]{1,37}")) {
                response.sendError(400); return;
            }
            String instance = CommonConfiguration.getProperty("mobilePackInstanceUrl", context);
            MobilePackConfiguration.https(instance);
            JSONObject job = new JSONObject().put("owner", user.getUsername()).put("searchQueryId", query)
                .put("taxonomy", taxonomy).put("featureClass", feature).put("displayName", display)
                .put("packId", packId).put("instanceUrl", instance);
            String id = MobilePackJobs.submit(context, job);
            response.setStatus(202);
            response.getWriter().write(new JSONObject().put("jobId", id).put("packId", packId).toString());
        } catch (IllegalArgumentException | org.json.JSONException ex) {
            response.sendError(400, "Invalid mobile pack configuration or request");
        } finally { shepherd.rollbackAndClose(); }
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        String context = ServletUtilities.getContext(request);
        Shepherd shepherd = new Shepherd(context);
        shepherd.beginDBTransaction();
        try {
            User user = shepherd.getUser(request);
            if (user == null) { response.sendError(403); return; }
            String id = request.getParameter("jobId");
            if (id == null) {
                if (!user.isAdmin(shepherd) || !MobilePackJobs.enabled(context)) {
                    response.getWriter().write("{\"enabled\":false}"); return;
                }
                org.json.JSONArray profiles = new org.json.JSONArray();
                IAJsonProperties ia = IAJsonProperties.iaConfig();
                if (ia != null) for (String taxonomy : ia.getAllTaxonomyStrings()) {
                    Object value = ia.get(taxonomy.replace(' ', '.') + "._mobile_packs");
                    if (!(value instanceof JSONObject)) continue;
                    JSONObject configured = (JSONObject)value;
                    for (String featureClass : configured.keySet()) {
                        profiles.put(new JSONObject().put("taxonomy", taxonomy).put("featureClass", featureClass)
                            .put("label", taxonomy + " — " + featureClass));
                    }
                }
                response.getWriter().write(new JSONObject().put("enabled", true).put("profiles", profiles).toString());
                return;
            }
            if (!id.matches("[a-f0-9-]{36}")) { response.sendError(400); return; }
            JSONObject job = MobilePackJobs.read(context, id);
            if (!user.getUsername().equals(job.getString("owner"))) { response.sendError(403); return; }
            JSONObject status = new JSONObject().put("jobId", id).put("packId", job.getString("packId"))
                .put("status", job.getString("status")).put("skippedCount", job.optInt("skippedCount"));
            if (job.has("message")) status.put("message", job.getString("message"));
            if (job.has("publication")) status.put("publication", job.getJSONObject("publication"));
            response.getWriter().write(status.toString());
        } catch (java.nio.file.NoSuchFileException ex) { response.sendError(404); }
        finally { shepherd.rollbackAndClose(); }
    }
}
