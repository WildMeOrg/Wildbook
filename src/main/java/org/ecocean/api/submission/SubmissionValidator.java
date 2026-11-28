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
        "Encounter.verbatimLocality", "Encounter.researcherComments", "Encounter.sightingID",
        GroupCollapse.GROUP_KEY);
    static final String SIGHTING_ID = "Encounter.sightingID";
    static final String LOCATION = "Encounter.locationID";
    static final String PHOTO = "Encounter.mediaAsset";
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

    /** The validation report and, when it is valid, the collapsed rows the importer consumes. */
    public static final class Checked {
        public final JSONObject report;  // exactly what validate(...) returns
        public final GroupedRows rows;   // null when the report is invalid
        Checked(JSONObject report, GroupedRows rows) { this.report = report; this.rows = rows; }
    }

    public JSONObject validate(Submission draft, Shepherd sh, SubmissionFiles storage) {
        return check(draft, sh, storage).report;
    }

    // one source row: its fields as sent, the supported copy handed to the collapse, and its own issues
    private static final class Source {
        String id, groupKey; // groupKey: as typed (trimmed); null when the row is ungrouped
        JSONObject fields, copied;
        JSONArray issues = new JSONArray();
        Set<String> media = new HashSet<>();
        Set<String> inRowRepeats = new HashSet<>(); // bare fields already reported as "repeated in this row"
        List<String> eligible = new ArrayList<>();   // bare fields naming a present photo, first use in the row
        JSONObject values;                            // normalized fields
        Set<String> reported = new HashSet<>();      // a grouped row's issues: each failure once per cell
    }

    public Checked check(Submission draft, Shepherd sh, SubmissionFiles storage) {
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
        // the collector named at create; the owner must still be a service account (403 otherwise)
        JSONObject collector = null;
        if (SubmissionAttribution.requested(draft) != null) {
            collector = SubmissionAttribution.resolve(sh, draft);
            if (collector == null) issue(errors, null, -1, SubmissionAttribution.FIELD, SubmissionAttribution.NOT_ALLOWED, SubmissionAttribution.NOT_ALLOWED_MESSAGE);
        }
        // captured before legacy validation so every date it judges future is also after this date
        java.time.LocalDate today = clock.instant().atOffset(Util.LATEST_CIVIL_OFFSET).toLocalDate();

        // 1. each source row's own cells, before the collapse: supported fields, photos, sighting ID format and,
        // on a grouped row, the location's type. A rejected sighting ID or location never reaches the collapse.
        List<Source> sources = new ArrayList<>();
        List<JSONObject> copiedRows = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            Source s = new Source();
            s.fields = row.getJSONObject("fields"); s.id = row.getString("clientRowId");
            s.copied = new JSONObject(s.fields.toString());
            s.groupKey = GroupCollapse.echoKey(s.fields.opt(GroupCollapse.GROUP_KEY));
            boolean grouped = s.groupKey != null;
            for (String field : s.fields.keySet()) {
                if (!supported(field)) { issue(s, i, field, "UNSUPPORTED_FIELD", null, "Field is not supported by this pilot"); s.copied.remove(field); }
            }
            if (!grouped) s.copied.remove(GroupCollapse.GROUP_KEY); // blank: never "cannot be empty"
            // bulk-import boxes (spec 4.9): photos are the bare numbered slots of the SUPPORTED fields (a
            // .bbox or .iaClass key is never a filename, and an unsupported huge index never reaches the
            // slot helper, which grows a list to the index)
            for (String field : BulkImportUtil.findIndexedFieldNames(s.copied.keySet(), PHOTO)) {
                if (field == null) continue;
                Object raw = s.fields.get(field);
                if (grouped && GroupCollapse.isAbsent(raw)) continue; // a blank cell of a grouped row abstains
                if (!(raw instanceof String) || !present.containsKey(raw)) issue(s, i, field, "MISSING_MEDIA", null, "Reference must exactly match a completed image filename");
                else if (!grouped) { // a grouped row's repeats are the collapse's
                    if (!s.media.add((String)raw)) { issue(s, i, field, "DUPLICATE_MEDIA", null, "Image is repeated in this row"); s.inRowRepeats.add(field); }
                    else s.eligible.add(field);
                }
            }
            // No explicit encounter IDs are accepted: the importer creates one encounter per row or group.
            // Rows sharing a sighting ID become one new sighting; an existing sighting is never joined.
            if (s.fields.has(SIGHTING_ID) && !(grouped && GroupCollapse.isAbsent(s.fields.opt(SIGHTING_ID)))) {
                Object raw = s.fields.get(SIGHTING_ID);
                if (!(raw instanceof String) || !SIGHTING_ID_FORMAT.matcher((String)raw).matches()) {
                    issue(s, i, SIGHTING_ID, "INVALID_VALUE", "INVALID", "Use 1 to 100 letters, digits, '.', '_' or '-', starting with a letter or digit");
                    s.copied.remove(SIGHTING_ID);
                }
            }
            // a grouped location must be text, so the order of the rows never decides which value is checked
            if (grouped && !GroupCollapse.isAbsent(s.fields.opt(LOCATION)) && !(s.fields.opt(LOCATION) instanceof String)) {
                issue(s, i, LOCATION, "INVALID_LOCATION", null, "A configured location ID is required");
                s.copied.remove(LOCATION);
            }
            sources.add(s);
            copiedRows.add(s.copied);
        }

        // 2. rows sharing a key become one merged row; every other row is validateRow() of itself
        GroupedRows g = GroupCollapse.collapse(copiedRows, sh, !draft.requestsIdentification(),
            Math.min(BulkValidator.MAX_INDEX + 1, config.getInt("maxMediaPerEncounter")));

        Map<String, Boolean> sightingExists = new HashMap<>();
        for (int m = 0; m < g.mergedRows().size(); m++) {
            if (g.isGrouped(m)) checkGroup(g, m, sources, config, sightingExists, sh, today);
            else checkUngrouped(g, m, sources.get(g.membersOf(m).get(0)), config, sightingExists, sh, today);
        }
        // source-only failures of grouped cells, each on its own source row
        for (GroupedRows.Issue gi : g.issues()) {
            Source s = sources.get(gi.row);
            BulkValidatorException ex = gi.error;
            if (ex.isType(BulkValidatorException.TYPE_GROUP_CONFLICT))
                issue(s, gi.row, gi.field, "GROUP_CONFLICT", null, ex.getMessage());
            else if (ex.isType(BulkValidatorException.TYPE_INVALID_DUPLICATE) && gi.field.matches("Encounter\\.mediaAsset\\d+"))
                issue(s, gi.row, gi.field, "DUPLICATE_MEDIA", null, ex.getMessage());
            else if (ex.isType(BulkValidatorException.TYPE_REQUIRED_VALUE) && (PHOTO + "0").equals(gi.field))
                issue(s, gi.row, gi.field, "REQUIRED_VALUE", null, "At least one image required");
            else if (LOCATION.equals(BulkValidator.canonicalFieldName(gi.field))) // as on an ungrouped row
                issue(s, gi.row, gi.field, "INVALID_LOCATION", null, "A configured location ID is required");
            else {
                SubmissionValueIssues.Issue explained = SubmissionValueIssues.explain(gi.field, ex, s.copied, Map.of(), sh, today);
                issue(s, gi.row, explained.field, "INVALID_VALUE", explained.reason, explained.message);
            }
        }

        // bulk-import boxes (spec 4.9): every photo use, by filename, in source order (rows, then slots)
        List<Use> all = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            int m = g.mergedIndexOf(i);
            if (g.isGrouped(m)) continue;
            Source s = sources.get(i);
            Map<String, Object> checked = g.mergedRows().get(m);
            for (String bare : s.eligible) {
                Use use = new Use(); use.row = i; use.slot = BulkImportUtil.slotOf(bare).index; use.source = s.id; use.bareField = bare; use.bboxField = bare + ".bbox";
                use.filename = s.fields.getString(bare);
                boxUse(use, checked.get(use.bboxField), checked.get(bare + ".theta"), present.get(use.filename), s);
                all.add(use);
            }
        }
        // a group's photos are its packed slots (P3 packs each filename once and none beyond the cap), each
        // traced to the source cell that supplied it
        for (int m : g.groupedMergedIndexes()) {
            Map<String, Object> checked = g.mergedRows().get(m);
            for (String packed : BulkImportUtil.findIndexedFieldNames(checked.keySet(), PHOTO)) {
                if (packed == null) continue;
                GroupedRows.Cell cell = g.contributorsOf(m, packed).get(0);
                Source s = sources.get(cell.row);
                Object filename = g.sourceValues(cell.row).get(cell.field);
                if (!(filename instanceof String) || !present.containsKey(filename)) continue; // MISSING_MEDIA already
                Use use = new Use(); use.row = cell.row; use.slot = BulkImportUtil.slotOf(cell.field).index; use.source = s.id;
                use.bareField = cell.field; use.groupKey = s.groupKey; use.filename = (String)filename;
                List<GroupedRows.Cell> boxCells = g.contributorsOf(m, packed + ".bbox");
                use.bboxField = boxCells.size() == 1 ? boxCells.get(0).field : cell.field + ".bbox";
                Object box = checked.get(packed + ".bbox");
                // a rejected box cell is a source issue and never packed: it still counts as boxed, identity unknown
                if (box == null && !GroupCollapse.isAbsent(s.copied.opt(cell.field + ".bbox"))) use.boxed = true;
                else {
                    boxUse(use, box, checked.get(packed + ".theta"), present.get(use.filename), s);
                    // a supplied angle that failed is a source issue and never packed: the box's identity is unknown
                    if (!GroupCollapse.isAbsent(s.copied.opt(cell.field + ".theta")) && !(checked.get(packed + ".theta") instanceof BulkValidator)) use.boxKey = null;
                }
                all.add(use);
            }
        }
        all.sort(Comparator.comparingInt((Use u) -> u.row).thenComparingInt(u -> u.slot));
        Map<String, List<Use>> uses = new LinkedHashMap<>();
        for (Use use : all) uses.computeIfAbsent(use.filename, k -> new ArrayList<>()).add(use);
        for (Source s : sources) {
            for (int n = 0; n < s.issues.length(); n++) errors.put(s.issues.get(n));
            normalized.put(new JSONObject().put("clientRowId", s.id).put("fields", s.values));
        }
        // bulk-import boxes (spec 4.9): image reuse across encounters. Un-boxed repeats stay DUPLICATE_MEDIA; a photo
        // boxed on one row and used un-boxed on another flags the un-boxed uses; identical boxes on one photo
        // are DUPLICATE_BOX; different boxes on one photo are allowed (one animal per encounter).
        for (Map.Entry<String, List<Use>> entry : uses.entrySet()) {
            List<Use> list = entry.getValue();
            if (list.size() < 2) continue;
            Use firstBoxed = null;
            for (Use u : list) if (u.boxed) { firstBoxed = u; break; }
            if (firstBoxed == null) {
                for (int n = 1; n < list.size(); n++)
                    issue(errors, list.get(n).source, list.get(n).row, list.get(n).bareField, "DUPLICATE_MEDIA", null, "Use each image in one row only", list.get(n).groupKey);
                continue;
            }
            Map<String, Integer> seen = new HashMap<>();
            for (Use u : list) {
                if (!u.boxed) {
                    issue(errors, u.source, u.row, u.bareField, "DUPLICATE_MEDIA", null,
                        "photo " + entry.getKey() + " has a box on row " + firstBoxed.row + " but none here", u.groupKey);
                } else if (u.boxKey != null) {
                    Integer earlier = seen.putIfAbsent(u.boxKey, u.row);
                    if (earlier != null) issue(errors, u.source, u.row, u.bboxField, "DUPLICATE_BOX", null, "Identical box already on row " + earlier, u.groupKey);
                }
            }
        }
        Object effectiveOwnerId = collector == null ? draft.getOwnerId()
            : collector.isNull("userId") ? SubmissionAttribution.PUBLIC : collector.get("userId");
        JSONObject report = new JSONObject().put("id", UUID.randomUUID().toString()).put("submissionId", draft.getId())
            .put("revision", draft.getRevision()).put("valid", errors.length() == 0)
            .put("configDigest", SubmissionJson.hash(SubmissionJson.canonical(config)))
            .put("manifestDigest", SubmissionJson.hash(SubmissionJson.canonical(files)))
            .put("errors", errors).put("warnings", new JSONArray()).put("normalizedRows", normalized)
            .put("effectiveOwnerId", effectiveOwnerId).put("draftOwnerId", draft.getOwnerId())
            .put("processing", new JSONObject().put("mode", draft.getProcessingMode()));
        if (collector != null) report.put("effectiveOwner", collector);
        return new Checked(report, errors.length() == 0 ? g : null);
    }

    // 3. an ungrouped row: the single-row rules exactly as before grouping existed
    private void checkUngrouped(GroupedRows g, int m, Source s, JSONObject config, Map<String, Boolean> sightingExists,
            Shepherd sh, java.time.LocalDate today) {
        int i = g.membersOf(m).get(0);
        JSONObject fields = s.fields, copied = s.copied, values = new JSONObject();
        if (copied.has(SIGHTING_ID) && sightingExists.computeIfAbsent(copied.getString(SIGHTING_ID), id -> sh.getOccurrence(id) != null))
            issue(s, i, SIGHTING_ID, "SIGHTING_EXISTS", null, "A sighting with this ID already exists; this API only creates new sightings");
        if (s.media.isEmpty()) issue(s, i, "Encounter.mediaAsset0", "REQUIRED_VALUE", null, "At least one image required");
        if (s.media.size() > config.getInt("maxMediaPerEncounter")) issue(s, i, null, "LIMIT_EXCEEDED", null, "Too many images for one encounter");
        boolean locationRejected = !(fields.opt(LOCATION) instanceof String) || !configuredLocation(config.getJSONObject("locations"), fields.optString(LOCATION, null));
        if (locationRejected) issue(s, i, LOCATION, "INVALID_LOCATION", null, "A configured location ID is required");
        // the shared row rules; import-only skips identification, which widens the accepted box classes (decision 3)
        Map<String, Object> checked = g.mergedRows().get(m);
        for (Map.Entry<String, Object> entry : checked.entrySet()) {
            if (entry.getValue() instanceof BulkValidator) {
                Object value = ((BulkValidator)entry.getValue()).getValue();
                if (value != null) values.put(entry.getKey(), value);
                // an empty box column is kept as "" (the column survives normalization, so the importer keeps
                // exact-slot mode) without boxing the slot; every other field keeps the empty-value rule
                else if (isBoxField(entry.getKey())) values.put(entry.getKey(), "");
                else issue(s, i, entry.getKey(), "INVALID_VALUE", "INVALID", "Provided value cannot be empty");
            } else if (s.inRowRepeats.contains(entry.getKey()) && entry.getValue() instanceof BulkValidatorException
                    && ((BulkValidatorException)entry.getValue()).isType(BulkValidatorException.TYPE_INVALID_DUPLICATE)) {
                // the shared rules' INVALID_DUPLICATE for a photo this validator already reported as repeated in the row
            } else if (!(locationRejected && LOCATION.equals(entry.getKey()))) { // already INVALID_LOCATION
                SubmissionValueIssues.Issue explained = SubmissionValueIssues.explain(entry.getKey(), (Exception)entry.getValue(), copied, checked, sh, today);
                issue(s, i, explained.field, "INVALID_VALUE", explained.reason, explained.message);
            }
        }
        s.values = values;
    }

    // 4-5. a group: sighting and location once on the agreed values, then each merged diagnostic once per
    // contributing source cell, under that cell's own column name
    private void checkGroup(GroupedRows g, int m, List<Source> sources, JSONObject config, Map<String, Boolean> sightingExists,
            Shepherd sh, java.time.LocalDate today) {
        Map<String, Object> merged = g.mergedRows().get(m);
        JSONObject raw = g.mergedRaw(m);
        for (int i : g.membersOf(m)) sources.get(i).values = new JSONObject(g.sourceValues(i));
        Object sighting = raw.opt(SIGHTING_ID);
        if (merged.get(SIGHTING_ID) instanceof BulkValidator && sighting instanceof String
                && sightingExists.computeIfAbsent((String)sighting, id -> sh.getOccurrence(id) != null))
            for (GroupedRows.Cell c : g.contributorsOf(m, SIGHTING_ID))
                issue(sources.get(c.row), c.row, c.field, "SIGHTING_EXISTS", null, "A sighting with this ID already exists; this API only creates new sightings");
        // a location cell the collapse already reported (a GROUP_CONFLICT, or a failed cell reported as
        // INVALID_LOCATION on its own row) explains why the group has none: no other row is blamed
        boolean locationIssue = false;
        for (GroupedRows.Issue gi : g.issues())
            if (g.mergedIndexOf(gi.row) == m && LOCATION.equals(BulkValidator.canonicalFieldName(gi.field))) locationIssue = true;
        // the missing-location fan-out is only for a group where no row supplied a location cell at all; a
        // supplied cell that failed (here or before the collapse) is already reported on its own row
        boolean supplied = false;
        for (int i : g.membersOf(m)) if (!GroupCollapse.isAbsent(sources.get(i).fields.opt(LOCATION))) supplied = true;
        boolean locationRejected = !supplied || (!locationIssue && raw.opt(LOCATION) instanceof String
            && !configuredLocation(config.getJSONObject("locations"), raw.optString(LOCATION, null)));
        if (locationRejected)
            for (GroupedRows.Cell c : g.cellsFor(m, LOCATION))
                issue(sources.get(c.row), c.row, c.field, "INVALID_LOCATION", null, "A configured location ID is required");
        for (Map.Entry<String, Object> entry : merged.entrySet()) {
            if (!(entry.getValue() instanceof Exception)) continue;
            String field = entry.getKey();
            if (locationRejected && LOCATION.equals(BulkValidator.canonicalFieldName(field))) continue; // already INVALID_LOCATION
            for (GroupedRows.Cell cell : g.cellsFor(m, field)) {
                // explained in source coordinates: the merged context, with this cell's own column holding the value
                JSONObject f = new JSONObject(raw.toMap());
                f.put(cell.field, raw.opt(field));
                SubmissionValueIssues.Issue explained = SubmissionValueIssues.explain(cell.field, (Exception)entry.getValue(), f, merged, sh, today);
                List<GroupedRows.Cell> targets = List.of(cell);
                String family = BulkValidator.canonicalFieldName(explained.field);
                if (!family.equals(BulkValidator.canonicalFieldName(cell.field))) {
                    // explained on another field (a future date names the month): blame the cells that supplied it
                    List<GroupedRows.Cell> other = g.contributorsOf(m, family);
                    targets = other.isEmpty() ? List.of(new GroupedRows.Cell(cell.row, explained.field)) : other;
                }
                for (GroupedRows.Cell t : targets)
                    issue(sources.get(t.row), t.row, t.field, "INVALID_VALUE", explained.reason, explained.message);
            }
        }
    }

    // a use's box identity and, against the staged file's header, its bounds
    private static void boxUse(Use use, Object box, Object thetaV, int[] dims, Source s) {
        if (box instanceof Exception) use.boxed = true; // rejected box: counts as boxed, identity unknown
        else if (box instanceof BulkValidator && ((BulkValidator)box).getValue() != null) {
            use.boxed = true;
            String canonical = ((BulkValidator)box).getValueString();
            Object thetaValue = thetaV instanceof BulkValidator ? ((BulkValidator)thetaV).getValue() : null;
            double theta = thetaValue instanceof Double ? (Double)thetaValue : 0.0;
            if (theta == 0.0) theta = 0.0; // -0.0 and 0.0 are the same box
            use.boxKey = canonical + "@" + theta;
            try {
                int[] b = BulkValidator.parseBbox(canonical, use.bboxField);
                if (dims != null && ((long)b[0] + b[2] > dims[0] || (long)b[1] + b[3] > dims[1]))
                    issue(s.issues, use.source, use.row, use.bboxField, "INVALID_VALUE", "BOX_OUT_OF_BOUNDS",
                        "box " + canonical + " exceeds image " + dims[0] + "x" + dims[1], use.groupKey);
            } catch (BulkValidatorException ex) { /* canonical: cannot fail */ }
        }
    }

    private static final class Use { int row, slot; String source, filename, bareField, bboxField, boxKey, groupKey; boolean boxed; }

    // a per-photo box column (Encounter.mediaAsset{N}.<one of BulkValidator.BOX_SUFFIXES>)
    static boolean isBoxField(String field) {
        return field.matches("Encounter\\.mediaAsset" + SLOT + "\\." + BOX_SUFFIX);
    }

    // an issue on a source row; a grouped row's issues carry its key as typed, and an identical one is reported once
    private static void issue(Source s, int row, String field, String code, String reason, String message) {
        if (s.groupKey != null && !s.reported.add(field + "\u0000" + code + "\u0000" + reason + "\u0000" + message)) return;
        issue(s.issues, s.id, row, field, code, reason, message, s.groupKey);
    }
    private static void issue(JSONArray issues, String source, int row, String field, String code, String message) {
        issue(issues, source, row, field, code, null, message, null);
    }
    private static void issue(JSONArray issues, String source, int row, String field, String code, String reason, String message, String groupKey) {
        JSONObject issue = new JSONObject().put("code", code).put("message", message);
        if (reason != null) issue.put("reason", reason);
        if (source != null) issue.put("clientRowId", source).put("rowIndex", row);
        if (field != null) issue.put("field", field);
        if (groupKey != null) issue.put("groupKey", groupKey);
        issues.put(issue);
    }
}
