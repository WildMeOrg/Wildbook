package org.ecocean.api.submission;

import java.util.*;
import org.ecocean.*;
import org.ecocean.api.bulk.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;

/** Strict new-encounter boundary around the existing bulk field validators. */
public class SubmissionValidator {
    public static final Set<String> FIELDS = Set.of("Encounter.genus", "Encounter.specificEpithet",
        "Encounter.year", "Encounter.month", "Encounter.day", "Encounter.hour", "Encounter.minutes",
        "Encounter.locationID", "Encounter.decimalLatitude", "Encounter.decimalLongitude",
        "Encounter.sex", "Encounter.lifeStage", "Encounter.livingStatus", "Encounter.behavior",
        "Encounter.verbatimLocality", "Encounter.researcherComments");
    public static boolean supported(String field) {
        return FIELDS.contains(field) || field.matches("Encounter\\.mediaAsset(?:[0-9]|[1-9][0-9]|1[0-9]{2})");
    }
    public static JSONObject configuration(String context) {
        return new JSONObject().put("validatorVersion", 1).put("locations", LocationID.getLocationIDStructure())
            .put("maxMediaPerEncounter", Math.max(1, CommonConfiguration.getMaxMediaCountEncounter(context)))
            .put("maxFileBytes", SubmissionFiles.maxFileBytes(context)).put("maxPixels", SubmissionFiles.MAX_PIXELS);
    }
    public static boolean configuredLocation(JSONObject tree, String id) {
        if (id != null && !id.isEmpty() && id.equals(tree.optString("id", null))) return true;
        JSONArray children = tree.optJSONArray("locationID");
        if (children != null) for (int i = 0; i < children.length(); i++)
            if (children.optJSONObject(i) != null && configuredLocation(children.getJSONObject(i), id)) return true;
        return false;
    }
    public JSONObject validate(Submission draft, Shepherd sh, SubmissionFiles storage) {
        JSONObject config = configuration(draft.getContext());
        JSONArray rows = new JSONArray(draft.getRowsJson()), files = new JSONArray(draft.getFilesJson());
        JSONArray errors = new JSONArray(), normalized = new JSONArray();
        Set<String> present = new HashSet<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            try { storage.verify(file); present.add(file.getString("name")); }
            catch (Exception ex) { issue(errors, null, -1, null, "INVALID_MEDIA", "Staged image unavailable or invalid: " + file.getString("name")); }
        }
        if (rows.length() == 0) issue(errors, null, -1, null, "REQUIRED_VALUE", "At least one row required");
        Set<String> allMedia = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i), fields = row.getJSONObject("fields"); String source = row.getString("clientRowId");
            JSONObject copied = new JSONObject(fields.toString()), values = new JSONObject();
            Set<String> media = new HashSet<>();
            for (String field : fields.keySet()) {
                if (!supported(field)) { issue(errors, source, i, field, "UNSUPPORTED_FIELD", "Field is not supported by this pilot"); copied.remove(field); }
                if (supported(field) && field.startsWith("Encounter.mediaAsset")) {
                    Object raw = fields.get(field);
                    if (!(raw instanceof String) || !present.contains(raw)) issue(errors, source, i, field, "MISSING_MEDIA", "Reference must exactly match a completed image filename");
                    else {
                        if (!media.add((String)raw)) issue(errors, source, i, field, "DUPLICATE_MEDIA", "Image is repeated in this row");
                        if (!allMedia.add((String)raw)) issue(errors, source, i, field, "DUPLICATE_MEDIA", "Use each image in one row only");
                    }
                }
            }
            // No explicit encounter IDs are accepted: the importer creates one encounter per row.
            if (media.isEmpty()) issue(errors, source, i, "Encounter.mediaAsset0", "REQUIRED_VALUE", "At least one image required");
            if (media.size() > config.getInt("maxMediaPerEncounter")) issue(errors, source, i, null, "LIMIT_EXCEEDED", "Too many images for one encounter");
            if (!(fields.opt("Encounter.locationID") instanceof String) || !configuredLocation(config.getJSONObject("locations"), fields.optString("Encounter.locationID", null)))
                issue(errors, source, i, "Encounter.locationID", "INVALID_LOCATION", "A configured location ID is required");
            Map<String, Object> checked = BulkImportUtil.validateRow(copied, sh);
            for (Map.Entry<String, Object> entry : checked.entrySet()) {
                if (entry.getValue() instanceof BulkValidator) {
                    Object value = ((BulkValidator)entry.getValue()).getValue();
                    if (value != null) values.put(entry.getKey(), value);
                    else issue(errors, source, i, entry.getKey(), "INVALID_VALUE", "Provided value cannot be empty or unparseable");
                } else issue(errors, source, i, entry.getKey(), "INVALID_VALUE", "Value failed bulk-import validation");
            }
            normalized.put(new JSONObject().put("clientRowId", source).put("fields", values));
        }
        return new JSONObject().put("id", UUID.randomUUID().toString()).put("submissionId", draft.getId())
            .put("revision", draft.getRevision()).put("valid", errors.length() == 0)
            .put("configDigest", SubmissionJson.hash(SubmissionJson.canonical(config)))
            .put("manifestDigest", SubmissionJson.hash(SubmissionJson.canonical(files)))
            .put("errors", errors).put("warnings", new JSONArray()).put("normalizedRows", normalized)
            .put("effectiveOwnerId", draft.getOwnerId()).put("processing", new JSONObject().put("mode", draft.getProcessingMode()));
    }
    private static void issue(JSONArray issues, String source, int row, String field, String code, String message) {
        JSONObject issue = new JSONObject().put("code", code).put("message", message);
        if (source != null) issue.put("clientRowId", source).put("rowIndex", row);
        if (field != null) issue.put("field", field);
        issues.put(issue);
    }
}
