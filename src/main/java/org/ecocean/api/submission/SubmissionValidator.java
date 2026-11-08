package org.ecocean.api.submission;

import java.util.*;
import org.ecocean.*;
import org.ecocean.api.bulk.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;

/** Strict new-encounter boundary around the existing bulk field validators. */
public class SubmissionValidator {
    private final java.time.Clock clock;
    public SubmissionValidator() { this(java.time.Clock.systemUTC()); }
    SubmissionValidator(java.time.Clock clock) { this.clock = clock; } // tests fix "today"
    public static final Set<String> FIELDS = Set.of("Encounter.genus", "Encounter.specificEpithet",
        "Encounter.year", "Encounter.month", "Encounter.day", "Encounter.hour", "Encounter.minutes",
        "Encounter.locationID", "Encounter.decimalLatitude", "Encounter.decimalLongitude",
        "Encounter.sex", "Encounter.lifeStage", "Encounter.livingStatus", "Encounter.behavior",
        "Encounter.verbatimLocality", "Encounter.researcherComments", "Encounter.sightingID");
    static final String SIGHTING_ID = "Encounter.sightingID";
    // becomes an Occurrence primary key used in page links and URLs, so keep it to URL-safe identifier characters
    static final java.util.regex.Pattern SIGHTING_ID_FORMAT = java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    static final String SLOT = "(?:[0-9]|[1-9][0-9]|1[0-9]{2})"; // slots 0-199
    // bulk-import boxes (spec 4.9): the five per-photo box columns and the detector score
    static final String BOX_SUFFIX = "(?:" + String.join("|", BulkValidator.BOX_SUFFIXES) + ")";
    public static boolean supported(String field) {
        return FIELDS.contains(field) || field.matches("Encounter\\.mediaAsset" + SLOT)
            || isBoxField(field)
            || field.matches("Encounter\\.quality" + SLOT);
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
        Map<String, int[]> present = new HashMap<>(); // filename -> header width/height (bulk-import boxes: box bounds)
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            try { present.put(file.getString("name"), storage.verify(file)); }
            catch (Exception ex) { issue(errors, null, -1, null, "INVALID_MEDIA", "Staged image unavailable or invalid: " + file.getString("name")); }
        }
        if (rows.length() == 0) issue(errors, null, -1, null, "REQUIRED_VALUE", "At least one row required");
        // bulk-import boxes (spec 4.9): every eligible photo use, by filename, for the cross-row pass below
        Map<String, List<Use>> uses = new LinkedHashMap<>();
        Map<String, Boolean> sightingExists = new HashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i), fields = row.getJSONObject("fields"); String source = row.getString("clientRowId");
            JSONObject copied = new JSONObject(fields.toString()), values = new JSONObject();
            Set<String> media = new HashSet<>();
            Set<String> inRowRepeats = new HashSet<>(); // bare fields already reported as "repeated in this row"
            List<String> eligible = new ArrayList<>();   // bare fields naming a present photo, first use in the row
            for (String field : fields.keySet()) {
                if (!supported(field)) { issue(errors, source, i, field, "UNSUPPORTED_FIELD", "Field is not supported by this pilot"); copied.remove(field); }
            }
            // bulk-import boxes (spec 4.9): photos are the bare numbered slots of the SUPPORTED fields (a
            // .bbox or .iaClass key is never a filename, and an unsupported huge index never reaches the
            // slot helper, which grows a list to the index)
            for (String field : BulkImportUtil.findIndexedFieldNames(copied.keySet(), "Encounter.mediaAsset")) {
                if (field == null) continue;
                Object raw = fields.get(field);
                if (!(raw instanceof String) || !present.containsKey(raw)) issue(errors, source, i, field, "MISSING_MEDIA", "Reference must exactly match a completed image filename");
                else {
                    if (!media.add((String)raw)) { issue(errors, source, i, field, "DUPLICATE_MEDIA", "Image is repeated in this row"); inRowRepeats.add(field); }
                    else eligible.add(field);
                }
            }
            // No explicit encounter IDs are accepted: the importer creates one encounter per row.
            // Rows sharing a sighting ID become one new sighting; an existing sighting is never joined.
            if (fields.has(SIGHTING_ID)) {
                Object raw = fields.get(SIGHTING_ID);
                if (!(raw instanceof String) || !SIGHTING_ID_FORMAT.matcher((String)raw).matches()) {
                    issue(errors, source, i, SIGHTING_ID, "INVALID_VALUE", "INVALID", "Use 1 to 100 letters, digits, '.', '_' or '-', starting with a letter or digit");
                    copied.remove(SIGHTING_ID);
                } else if (sightingExists.computeIfAbsent((String)raw, id -> sh.getOccurrence(id) != null)) {
                    issue(errors, source, i, SIGHTING_ID, "SIGHTING_EXISTS", "A sighting with this ID already exists; this API only creates new sightings");
                }
            }
            if (media.isEmpty()) issue(errors, source, i, "Encounter.mediaAsset0", "REQUIRED_VALUE", "At least one image required");
            if (media.size() > config.getInt("maxMediaPerEncounter")) issue(errors, source, i, null, "LIMIT_EXCEEDED", "Too many images for one encounter");
            boolean locationRejected = !(fields.opt("Encounter.locationID") instanceof String) || !configuredLocation(config.getJSONObject("locations"), fields.optString("Encounter.locationID", null));
            if (locationRejected) issue(errors, source, i, "Encounter.locationID", "INVALID_LOCATION", "A configured location ID is required");
            // captured before legacy validation so every date it judges future is also after this date
            java.time.LocalDate today = clock.instant().atOffset(Util.LATEST_CIVIL_OFFSET).toLocalDate();
            // the shared row rules; import-only skips identification, which widens the accepted box classes (decision 3)
            Map<String, Object> checked = BulkImportUtil.validateRow(copied, sh, !draft.requestsIdentification());
            for (Map.Entry<String, Object> entry : checked.entrySet()) {
                if (entry.getValue() instanceof BulkValidator) {
                    Object value = ((BulkValidator)entry.getValue()).getValue();
                    if (value != null) values.put(entry.getKey(), value);
                    // an empty box column is kept as "" (the column survives normalization, so the importer keeps
                    // exact-slot mode) without boxing the slot; every other field keeps the empty-value rule
                    else if (isBoxField(entry.getKey())) values.put(entry.getKey(), "");
                    else issue(errors, source, i, entry.getKey(), "INVALID_VALUE", "INVALID", "Provided value cannot be empty");
                } else if (inRowRepeats.contains(entry.getKey()) && entry.getValue() instanceof BulkValidatorException
                        && ((BulkValidatorException)entry.getValue()).isType(BulkValidatorException.TYPE_INVALID_DUPLICATE)) {
                    // the shared rules' INVALID_DUPLICATE for a photo this validator already reported as repeated in the row
                } else if (!(locationRejected && "Encounter.locationID".equals(entry.getKey()))) { // already INVALID_LOCATION
                    SubmissionValueIssues.Issue explained = SubmissionValueIssues.explain(entry.getKey(), (Exception)entry.getValue(), copied, checked, sh, today);
                    issue(errors, source, i, explained.field, "INVALID_VALUE", explained.reason, explained.message);
                }
            }
            for (String bare : eligible) {
                Use use = new Use(); use.row = i; use.source = source; use.bareField = bare; use.bboxField = bare + ".bbox";
                Object box = checked.get(use.bboxField);
                if (box instanceof Exception) use.boxed = true; // rejected box: counts as boxed, identity unknown
                else if (box instanceof BulkValidator && ((BulkValidator)box).getValue() != null) {
                    use.boxed = true;
                    String canonical = ((BulkValidator)box).getValueString();
                    Object thetaV = checked.get(bare + ".theta");
                    Object thetaValue = thetaV instanceof BulkValidator ? ((BulkValidator)thetaV).getValue() : null;
                    double theta = thetaValue instanceof Double ? (Double)thetaValue : 0.0;
                    if (theta == 0.0) theta = 0.0; // -0.0 and 0.0 are the same box
                    use.boxKey = canonical + "@" + theta;
                    int[] dims = present.get(fields.getString(bare));
                    try {
                        int[] b = BulkValidator.parseBbox(canonical, use.bboxField);
                        if (dims != null && ((long)b[0] + b[2] > dims[0] || (long)b[1] + b[3] > dims[1]))
                            issue(errors, source, i, use.bboxField, "INVALID_VALUE", "BOX_OUT_OF_BOUNDS",
                                "box " + canonical + " exceeds image " + dims[0] + "x" + dims[1]);
                    } catch (BulkValidatorException ex) { /* canonical: cannot fail */ }
                }
                uses.computeIfAbsent(fields.getString(bare), k -> new ArrayList<>()).add(use);
            }
            normalized.put(new JSONObject().put("clientRowId", source).put("fields", values));
        }
        // bulk-import boxes (spec 4.9): image reuse across rows. Un-boxed repeats stay DUPLICATE_MEDIA; a photo
        // boxed on one row and used un-boxed on another flags the un-boxed uses; identical boxes on one photo
        // are DUPLICATE_BOX; different boxes on one photo are allowed (one animal per row).
        for (Map.Entry<String, List<Use>> entry : uses.entrySet()) {
            List<Use> list = entry.getValue();
            if (list.size() < 2) continue;
            Use firstBoxed = null;
            for (Use u : list) if (u.boxed) { firstBoxed = u; break; }
            if (firstBoxed == null) {
                for (int n = 1; n < list.size(); n++)
                    issue(errors, list.get(n).source, list.get(n).row, list.get(n).bareField, "DUPLICATE_MEDIA", "Use each image in one row only");
                continue;
            }
            Map<String, Integer> seen = new HashMap<>();
            for (Use u : list) {
                if (!u.boxed) {
                    issue(errors, u.source, u.row, u.bareField, "DUPLICATE_MEDIA",
                        "photo " + entry.getKey() + " has a box on row " + firstBoxed.row + " but none here");
                } else if (u.boxKey != null) {
                    Integer earlier = seen.putIfAbsent(u.boxKey, u.row);
                    if (earlier != null) issue(errors, u.source, u.row, u.bboxField, "DUPLICATE_BOX", "Identical box already on row " + earlier);
                }
            }
        }
        return new JSONObject().put("id", UUID.randomUUID().toString()).put("submissionId", draft.getId())
            .put("revision", draft.getRevision()).put("valid", errors.length() == 0)
            .put("configDigest", SubmissionJson.hash(SubmissionJson.canonical(config)))
            .put("manifestDigest", SubmissionJson.hash(SubmissionJson.canonical(files)))
            .put("errors", errors).put("warnings", new JSONArray()).put("normalizedRows", normalized)
            .put("effectiveOwnerId", draft.getOwnerId()).put("processing", new JSONObject().put("mode", draft.getProcessingMode()));
    }
    private static final class Use { int row; String source, bareField, bboxField, boxKey; boolean boxed; }

    // a per-photo box column (Encounter.mediaAsset{N}.<one of BulkValidator.BOX_SUFFIXES>)
    static boolean isBoxField(String field) {
        return field.matches("Encounter\\.mediaAsset" + SLOT + "\\." + BOX_SUFFIX);
    }

    private static void issue(JSONArray issues, String source, int row, String field, String code, String message) {
        issue(issues, source, row, field, code, null, message);
    }
    private static void issue(JSONArray issues, String source, int row, String field, String code, String reason, String message) {
        JSONObject issue = new JSONObject().put("code", code).put("message", message);
        if (reason != null) issue.put("reason", reason);
        if (source != null) issue.put("clientRowId", source).put("rowIndex", row);
        if (field != null) issue.put("field", field);
        issues.put(issue);
    }
}
