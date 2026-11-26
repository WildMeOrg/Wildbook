package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.ecocean.api.ApiException;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;
import org.json.JSONObject;

// groups bulk-import rows that share an Encounter.groupKey into one encounter
public final class GroupCollapse {
    public static final String GROUP_KEY = "Encounter.groupKey";

    static final String REMARKS_HINT =
        "Put encounter remarks on one row of the group, or make them identical.";
    // field families (by compare key) whose conflict message ends with REMARKS_HINT
    static final Set<String> REMARKS_FAMILIES = Set.of("Encounter.researcherComments",
        "Encounter.identificationRemarks", "Encounter.sightingRemarks", "Sighting.comments");
    // date components that may not be combined with epoch millis within a group
    static final Set<String> CALENDAR_PARTS = Set.of("Encounter.month", "Encounter.day",
        "Encounter.hour", "Encounter.minutes");
    static final String MILLIS = "Encounter.dateInMilliseconds";
    static final String YEAR = "Encounter.year";
    // sample/tissue fields: compound (C4) data, not scalar families
    static final Set<String> SAMPLE_FIELDS = Set.of("TissueSample.sampleID",
        "TissueSample.tissueType", "MicrosatelliteMarkersAnalysis.analysisID",
        "MicrosatelliteMarkersAnalysis.alleleNames", "SexAnalysis.processingLabTaskID",
        "SexAnalysis.sex", "MitochondrialDNAAnalysis.haplotype");

    private GroupCollapse() {}

    // ASCII-only case fold: A-Z -> a-z on UTF-16 units; every other character is unchanged
    public static String foldKey(String trimmedKey) {
        StringBuilder sb = new StringBuilder(trimmedKey.length());
        for (int i = 0; i < trimmedKey.length(); i++) {
            char c = trimmedKey.charAt(i);
            sb.append((c >= 'A' && c <= 'Z') ? (char)(c + 32) : c);
        }
        return sb.toString();
    }

    // one validated, non-absent source cell
    private static final class Entry {
        final GroupedRows.Cell cell;
        final BulkValidator bv;
        final Object raw;
        String compared;

        Entry(GroupedRows.Cell cell, BulkValidator bv, Object raw) {
            this.cell = cell;
            this.bv = bv;
            this.raw = raw;
        }
    }

    // what collapse() accumulates across groups
    private static final class State {
        final List<JSONObject> rows;
        final Shepherd sh;
        final boolean identificationSkipped;
        final String[] keyAsTyped;
        final List<GroupedRows.Issue> issues = new ArrayList<GroupedRows.Issue>();
        final List<GroupedRows.Issue> warnings = new ArrayList<GroupedRows.Issue>();
        final List<Map<String, Object> > sourceValues;
        final Set<String> groupedFilenames = new LinkedHashSet<String>();

        State(List<JSONObject> rows, Shepherd sh, boolean identificationSkipped) {
            this.rows = rows;
            this.sh = sh;
            this.identificationSkipped = identificationSkipped;
            this.keyAsTyped = new String[rows.size()];
            this.sourceValues = new ArrayList<Map<String, Object> >(
                Collections.nCopies(rows.size(), (Map<String, Object>)null));
        }

        void issue(int row, String field, BulkValidatorException ex) {
            issues.add(new GroupedRows.Issue(row, field, ex, keyAsTyped[row]));
        }
    }

    /**
     * Collapses the rows of one upload: rows sharing a groupKey (ASCII case-folded) become one
     * merged row; every other row is validateRow() of itself. The input rows are not modified.
     * maxPhotosPerGroup is reserved for photo packing.
     */
    public static GroupedRows collapse(List<JSONObject> sourceRows, Shepherd sh,
        boolean identificationSkipped, int maxPhotosPerGroup) {
        State st = new State(sourceRows, sh, identificationSkipped);
        int n = sourceRows.size();
        int[] mergedIndexOf = new int[n];
        List<List<Integer> > members = new ArrayList<List<Integer> >();
        Set<Integer> grouped = new HashSet<Integer>();
        Map<String, Integer> groupIndex = new HashMap<String, Integer>();
        boolean exactSlotMode = false;

        // 1. membership, from the raw key only
        for (int r = 0; r < n; r++) {
            JSONObject row = sourceRows.get(r);
            if (BulkImportUtil.hasBboxColumn(row.keySet())) exactSlotMode = true;
            Object raw = row.opt(GROUP_KEY);
            String membership = null;
            if ((raw instanceof String) && !((String)raw).trim().isEmpty()) {
                st.keyAsTyped[r] = ((String)raw).trim();
                membership = foldKey(st.keyAsTyped[r]);
            } else if ((raw != null) && (raw != JSONObject.NULL) && !(raw instanceof String)) {
                st.keyAsTyped[r] = raw.toString();
                membership = "\u0000" + r; // a group of its own, so the issue is fatal
                st.issue(r, GROUP_KEY, new BulkValidatorException("groupKey must be text",
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_VALUE, GROUP_KEY));
            }
            Integer m = (membership == null) ? null : groupIndex.get(membership);
            if (m == null) {
                m = members.size();
                members.add(new ArrayList<Integer>());
                if (membership != null) {
                    groupIndex.put(membership, m);
                    grouped.add(m);
                }
            }
            members.get(m).add(r);
            mergedIndexOf[r] = m;
        }

        List<Map<String, Object> > mergedRows = new ArrayList<Map<String, Object> >();
        List<Map<String, List<GroupedRows.Cell> > > contributors =
            new ArrayList<Map<String, List<GroupedRows.Cell> > >();
        List<Map<String, Object> > mergedRaw = new ArrayList<Map<String, Object> >();
        for (int m = 0; m < members.size(); m++) {
            Map<String, Object> merged = new HashMap<String, Object>();
            Map<String, List<GroupedRows.Cell> > contrib =
                new HashMap<String, List<GroupedRows.Cell> >();
            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            if (grouped.contains(m)) {
                collapseGroup(st, members.get(m), merged, contrib, raw);
            } else {
                passUngrouped(st, members.get(m).get(0), merged, raw);
            }
            mergedRows.add(merged);
            contributors.add(contrib);
            mergedRaw.add(raw);
            members.set(m, Collections.unmodifiableList(members.get(m)));
        }

        // 8. issues and warnings in (row, field) order
        Comparator<GroupedRows.Issue> byCell = Comparator.comparingInt(
            (GroupedRows.Issue i) -> i.row).thenComparing(i -> i.field);
        st.issues.sort(byCell);
        st.warnings.sort(byCell);
        return new GroupedRows(n, mergedRows, mergedIndexOf, members, grouped, st.keyAsTyped,
                contributors, st.sourceValues, mergedRaw, st.issues, st.warnings, sourceRows,
                exactSlotMode, st.groupedFilenames);
    }

    // 2. an ungrouped row is exactly validateRow() of (a copy of) itself
    private static void passUngrouped(State st, int r, Map<String, Object> merged,
        Map<String, Object> raw) {
        JSONObject row = st.rows.get(r);
        JSONObject copy = new JSONObject();
        for (String k : row.keySet()) {
            copy.put(k, row.get(k));
            raw.put(k, row.get(k));
        }
        merged.putAll(BulkImportUtil.validateRow(copy, st.sh, st.identificationSkipped));
        Map<String, Object> values = new HashMap<String, Object>();
        for (Map.Entry<String, Object> e : merged.entrySet()) {
            if (!(e.getValue() instanceof BulkValidator)) continue;
            Object v = ((BulkValidator)e.getValue()).getValue();
            if (v != null) values.put(e.getKey(), v);
        }
        st.sourceValues.set(r, Collections.unmodifiableMap(values));
    }

    private static void collapseGroup(State st, List<Integer> rows, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Map<String, Object> raw) {
        Set<String> poisoned = new HashSet<String>(); // merged names whose failure is an Issue
        Map<String, List<Entry> > families = new LinkedHashMap<String, List<Entry> >();
        List<Entry> passThrough = new ArrayList<Entry>();

        for (int r : rows) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put(GROUP_KEY, st.keyAsTyped[r]);
            for (Entry e : validateGroupedRow(st, r, poisoned)) {
                values.put(e.cell.field, e.bv.getValue());
                if ("Encounter.mediaAsset".equals(e.bv.getIndexPrefix()))
                    st.groupedFilenames.add(e.bv.getValueString());
                if (isScalar(e.cell.field)) {
                    families.computeIfAbsent(familyKey(e.cell.field),
                        k -> new ArrayList<Entry>()).add(e);
                } else {
                    passThrough.add(e);
                }
            }
            st.sourceValues.set(r, Collections.unmodifiableMap(values));
        }

        // 5. epoch millis may not be combined with calendar components
        List<Entry> millis = families.getOrDefault(MILLIS, List.of());
        List<Entry> parts = new ArrayList<Entry>();
        for (String p : CALENDAR_PARTS) parts.addAll(families.getOrDefault(p, List.of()));
        if (!millis.isEmpty() && !parts.isEmpty()) {
            List<Entry> blamed = new ArrayList<Entry>(millis);
            blamed.addAll(parts);
            for (Entry e : blamed) {
                addContributor(contrib, raw, emitName(e.cell.field), e);
                st.issue(e.cell.row, e.cell.field, new BulkValidatorException(
                    "a group cannot combine a date in milliseconds with month, day, hour or minutes",
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_GROUP_CONFLICT, e.cell.field));
                poisoned.add(emitName(e.cell.field));
            }
            families.remove(MILLIS);
            families.keySet().removeAll(CALENDAR_PARTS);
        }

        // 4. scalar families must agree on their stored form
        for (Map.Entry<String, List<Entry> > fam : families.entrySet())
            mergeFamily(st, fam.getKey(), fam.getValue(), merged, contrib, raw, poisoned);

        // photo, per-photo and compound cells: first contributor, under its own name (for now)
        for (Entry e : passThrough) {
            addContributor(contrib, raw, e.cell.field, e);
            if (!merged.containsKey(e.cell.field)) merged.put(e.cell.field, e.bv);
        }

        // 7. dependent checks; a poisoned name holds a placeholder so nothing is piled on it
        BulkValidatorException placeholder = new BulkValidatorException(
            "already reported on a source cell", ApiException.ERROR_RETURN_CODE_INVALID);
        for (String p : poisoned) merged.put(p, placeholder);
        BulkImportUtil.validateDependent(merged, new HashSet<String>(merged.keySet()), st.sh,
            st.identificationSkipped);
        merged.values().removeIf(v -> v == placeholder);
        // the required-date error replaces whatever the year holds, placeholder included: a
        // poisoned year or millis family was supplied, and its failure is already an Issue
        Object year = merged.get(YEAR);
        if ((poisoned.contains(YEAR) || poisoned.contains(MILLIS)) &&
            (year instanceof BulkValidatorException) &&
            ((BulkValidatorException)year).isType(BulkValidatorException.TYPE_REQUIRED_VALUE))
            merged.remove(YEAR);
        raw.keySet().retainAll(merged.keySet());
        for (Map.Entry<String, List<GroupedRows.Cell> > e : contrib.entrySet())
            e.setValue(Collections.unmodifiableList(e.getValue()));
    }

    /**
     * 3. Header checks on the original keys (blanks included), then each non-absent cell's own
     * validation. Failures become Issues (and poison the cell's family); returns the cells that
     * validated to a value, in field-name order.
     */
    private static List<Entry> validateGroupedRow(State st, int r, Set<String> poisoned) {
        JSONObject row = st.rows.get(r);
        Set<String> keys = new TreeSet<String>(row.keySet());
        keys.remove(GROUP_KEY);
        Set<String> ok = new TreeSet<String>();
        for (String k : keys) {
            try {
                BulkValidator.indexIntValue(k); // unknown name, or an index above MAX_INDEX
                ok.add(k);
            } catch (BulkValidatorException ex) {
                if (!BulkValidator.isValidFieldName(k) && isAbsent(row.opt(k))) {
                    st.warnings.add(new GroupedRows.Issue(r, k, ex, st.keyAsTyped[r]));
                } else {
                    st.issue(r, k, ex);
                }
            }
        }
        String[] alias;
        while ((alias = BulkImportUtil.findIndexAliases(ok)) != null) {
            st.issue(r, alias[1], new BulkValidatorException(alias[0] + " and " + alias[1] +
                " are the same numbered column", ApiException.ERROR_RETURN_CODE_INVALID,
                BulkValidatorException.TYPE_INVALID_DUPLICATE, alias[1]));
            ok.remove(alias[1]);
        }
        List<List<String> > syn = BulkValidator.findSynonyms(ok);
        if (syn != null) {
            for (List<String> syns : syn) {
                for (String dup : syns) {
                    st.issue(r, dup, BulkImportUtil.synonymException(syns, dup));
                    poisoned.add(emitName(dup));
                    ok.remove(dup);
                }
            }
        }
        JSONObject present = new JSONObject();
        for (String k : ok) {
            if (!isAbsent(row.opt(k))) present.put(k, row.get(k));
        }
        Map<String, Object> validated = BulkImportUtil.validateFields(present, st.sh);
        List<Entry> rtn = new ArrayList<Entry>();
        for (String k : new TreeSet<String>(validated.keySet())) {
            Object o = validated.get(k);
            if (o instanceof Exception) {
                st.issue(r, k, asValidatorException((Exception)o, k));
                poisoned.add(emitName(k));
                continue;
            }
            BulkValidator bv = (BulkValidator)o;
            if (bv.getValue() == null) { // a non-absent cell that validated to nothing
                st.issue(r, k, new BulkValidatorException("cannot read a value from " + k +
                    ": " + row.get(k), ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_VALUE, k));
                poisoned.add(emitName(k));
                continue;
            }
            rtn.add(new Entry(new GroupedRows.Cell(r, k), bv, row.get(k)));
        }
        return rtn;
    }

    // 4. one scalar family: one distinct stored value is merged, several are a conflict
    private static void mergeFamily(State st, String family, List<Entry> entries,
        Map<String, Object> merged, Map<String, List<GroupedRows.Cell> > contrib,
        Map<String, Object> raw, Set<String> poisoned) {
        boolean isId = family.equals("Encounter.id");
        boolean bad = false;
        Set<String> distinct = new LinkedHashSet<String>();
        List<Entry> valid = new ArrayList<Entry>();
        for (Entry e : entries) {
            addContributor(contrib, raw, emitName(e.cell.field), e);
            String v = e.bv.getValueString();
            if (isId) {
                if (!Util.isCanonicalUUID(v)) {
                    st.issue(e.cell.row, e.cell.field, new BulkValidatorException(
                        "must be a full UUID (8-4-4-4-12 hexadecimal digits)",
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_VALUE, e.cell.field));
                    bad = true;
                    continue;
                }
                v = v.toLowerCase();
            }
            e.compared = v;
            distinct.add(v);
            valid.add(e);
        }
        if (distinct.size() > 1) {
            Set<Integer> rowsSeen = new HashSet<Integer>();
            for (Entry e : valid) rowsSeen.add(e.cell.row);
            StringBuilder msg = new StringBuilder(((rowsSeen.size() > 1) ?
                "rows of this group disagree on " : "these columns disagree on ") + family + ":");
            String sep = " ";
            for (String v : distinct) {
                msg.append(sep).append('"').append(v).append('"');
                sep = ", ";
            }
            msg.append('.');
            if (REMARKS_FAMILIES.contains(family)) msg.append(' ').append(REMARKS_HINT);
            for (Entry e : valid)
                st.issue(e.cell.row, e.cell.field, new BulkValidatorException(msg.toString(),
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_GROUP_CONFLICT, e.cell.field));
            bad = true;
        }
        for (Entry e : entries) {
            String name = emitName(e.cell.field);
            if (bad) {
                poisoned.add(name);
            } else if (!merged.containsKey(name)) {
                merged.put(name, isId ? idValidator(name, e.compared, st.sh) :
                    BulkValidator.rebound(e.bv, name));
            }
        }
    }

    private static BulkValidator idValidator(String name, String lower, Shepherd sh) {
        try {
            return new BulkValidator(name, lower, sh);
        } catch (BulkValidatorException ex) {
            throw new IllegalStateException("canonical UUID rejected: " + lower, ex);
        }
    }

    private static void addContributor(Map<String, List<GroupedRows.Cell> > contrib,
        Map<String, Object> raw, String name, Entry e) {
        contrib.computeIfAbsent(name, k -> new ArrayList<GroupedRows.Cell>()).add(e.cell);
        raw.putIfAbsent(name, e.raw);
    }

    // absent on a grouped row: null, JSON null, or text that is empty after trim()
    static boolean isAbsent(Object v) {
        return (v == null) || (v == JSONObject.NULL) ||
                   ((v instanceof String) && ((String)v).trim().isEmpty());
    }

    // scalar = not a photo, per-photo, labeled keyword, numbered, measurement or sample field
    static boolean isScalar(String field) {
        try {
            if (BulkValidator.indexIntValue(field) >= 0) return false;
        } catch (BulkValidatorException ex) {
            return false;
        }
        if (BulkValidator.isMeasurementFieldName(field)) return false;
        if (BulkImportUtil.getLabeledKeywordOffset(field) >= 0) return false;
        return !SAMPLE_FIELDS.contains(field);
    }

    // the merged name a cell's value is emitted under: its alias row's canonical name for scalars
    static String emitName(String field) {
        return isScalar(field) ? BulkValidator.canonicalFieldName(field) : field;
    }

    // the key a scalar family is compared under: same-target fields share one key
    static String familyKey(String field) {
        String canonical = BulkValidator.canonicalFieldName(field);
        for (String[] pair : BulkValidator.SAME_TARGET_FIELDS) {
            for (String member : pair) {
                if (member.equals(canonical)) return pair[0];
            }
        }
        return canonical;
    }

    private static BulkValidatorException asValidatorException(Exception ex, String field) {
        if (ex instanceof BulkValidatorException) return (BulkValidatorException)ex;
        return new BulkValidatorException(ex.toString(), ApiException.ERROR_RETURN_CODE_INVALID,
                BulkValidatorException.TYPE_INVALID_VALUE, field);
    }
}
