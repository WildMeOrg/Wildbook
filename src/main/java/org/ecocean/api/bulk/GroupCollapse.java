package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.Collection;
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
    // accepted by validation but never imported (no live reference in BulkImporter)
    public static final Set<String> GROUP_IGNORED_FIELDS = Set.of("Encounter.project",
        "MarkedIndividual.name", "Survey.type");

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
    static final String NICKNAME = "MarkedIndividual.nickName";
    static final String SOCIAL_UNIT = "SocialUnit.socialUnitName";
    static final String ROLE = "Membership.role";
    // a longer trimmed groupKey is invalid (its row is a group of its own, so the issue is fatal)
    static final int MAX_KEY_LENGTH = 256;

    // a conflict message names at most this many values (or rows), then "and N more"
    static final int LIST_LIMIT = 3;
    // a value quoted in a conflict message is cut to this many characters
    static final int VALUE_CLIP = 60;

    private GroupCollapse() {}

    // the first limit items, comma-separated, then " and N more" for the rest
    static String firstFew(Collection<String> items, int limit) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : items) {
            if (i == limit) break;
            sb.append((i == 0) ? "" : ", ").append(s);
            i++;
        }
        if (items.size() > limit) sb.append(" and ").append(items.size() - limit).append(" more");
        return sb.toString();
    }

    // a value as quoted in a message: at most VALUE_CLIP characters, "..." marking a cut
    // never splits a surrogate pair: a cut that would end on a high surrogate ends one earlier
    static String clip(String v) {
        if (v.length() <= VALUE_CLIP) return v;
        int end = Character.isHighSurrogate(v.charAt(VALUE_CLIP - 1)) ? VALUE_CLIP - 1 : VALUE_CLIP;
        return v.substring(0, end) + "...";
    }

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
    static final class Entry {
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

    // one group's agreed encounter ID (lowercase) and the cells that supplied it; the merged row
    // and raw map are kept so a failure can drop the ID from them
    static final class GroupId {
        final String id;
        final List<Entry> cells;
        final Map<String, Object> merged;
        final Map<String, Object> raw;

        GroupId(String id, List<Entry> cells, Map<String, Object> merged,
            Map<String, Object> raw) {
            this.id = id;
            this.cells = cells;
            this.merged = merged;
            this.raw = raw;
        }
    }

    // what collapse() accumulates across groups
    static final class State {
        final List<JSONObject> rows;
        final Shepherd sh;
        final boolean identificationSkipped;
        final String[] keyAsTyped;
        final List<GroupedRows.Issue> issues = new ArrayList<GroupedRows.Issue>();
        final List<GroupedRows.Issue> warnings = new ArrayList<GroupedRows.Issue>();
        final List<Map<String, Object> > sourceValues;
        final Set<String> groupedFilenames = new LinkedHashSet<String>();
        // a grouped photo filename -> the grouped source rows naming it in a bare photo column
        final Map<String, Set<Integer> > rowsByGroupedFile = new HashMap<String, Set<Integer> >();
        final int maxPhotos;
        // project prefix -> whether an existing project answers it; looked up once per upload
        final Map<String, Boolean> projectExists = new HashMap<String, Boolean>();
        final List<GroupId> groupIds = new ArrayList<GroupId>();
        boolean exactSlotMode = false;
        // the source rows with an Issue on a compound cell
        final Set<Integer> compoundIssueRows = new HashSet<Integer>();

        State(List<JSONObject> rows, Shepherd sh, boolean identificationSkipped, int maxPhotos) {
            this.rows = rows;
            this.maxPhotos = maxPhotos;
            this.sh = sh;
            this.identificationSkipped = identificationSkipped;
            this.keyAsTyped = new String[rows.size()];
            this.sourceValues = new ArrayList<Map<String, Object> >(
                Collections.nCopies(rows.size(), (Map<String, Object>)null));
        }

        void issue(int row, String field, BulkValidatorException ex) {
            issues.add(new GroupedRows.Issue(row, field, ex, keyAsTyped[row]));
            if (GroupCompound.isCompound(field)) compoundIssueRows.add(row);
        }
    }

    /**
     * Collapses the rows of one upload: rows sharing a groupKey (ASCII case-folded) become one
     * merged row; every other row is validateRow() of itself. The input rows are not modified.
     * A group's photos are packed into slots 0..n-1 (n at most maxPhotosPerGroup), each with its
     * own metadata.
     */
    public static GroupedRows collapse(List<JSONObject> sourceRows, Shepherd sh,
        boolean identificationSkipped, int maxPhotosPerGroup) {
        State st = new State(sourceRows, sh, identificationSkipped, maxPhotosPerGroup);
        int n = sourceRows.size();
        int[] mergedIndexOf = new int[n];
        List<List<Integer> > members = new ArrayList<List<Integer> >();
        Set<Integer> grouped = new HashSet<Integer>();
        Map<String, Integer> groupIndex = new HashMap<String, Integer>();

        // 1. membership, from the raw key only
        for (int r = 0; r < n; r++) {
            JSONObject row = sourceRows.get(r);
            if (BulkImportUtil.hasBboxColumn(row.keySet())) st.exactSlotMode = true;
            Object raw = row.opt(GROUP_KEY);
            String membership = null;
            if ((raw instanceof String) && !((String)raw).trim().isEmpty()) {
                st.keyAsTyped[r] = ((String)raw).trim();
                if (st.keyAsTyped[r].length() > MAX_KEY_LENGTH) {
                    // the echo is copied onto every error of the row, so it is clipped
                    st.keyAsTyped[r] = clip(st.keyAsTyped[r]);
                    membership = "\u0000" + r; // a group of its own, so the issue is fatal
                    st.issue(r, GROUP_KEY, new BulkValidatorException("groupKey must be at most " +
                        MAX_KEY_LENGTH + " characters", ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_VALUE, GROUP_KEY));
                } else {
                    membership = foldKey(st.keyAsTyped[r]);
                }
            } else if ((raw != null) && (raw != JSONObject.NULL) && !(raw instanceof String)) {
                st.keyAsTyped[r] = clip(raw.toString()); // echoed on every error of the row
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
                merged = passUngrouped(st, members.get(m).get(0), raw);
            }
            mergedRows.add(merged);
            contributors.add(contrib);
            mergedRaw.add(raw);
            members.set(m, Collections.unmodifiableList(members.get(m)));
        }

        // 6. encounter IDs: unique across groups and ungrouped rows, and unused in the database
        checkGroupIds(st, members, grouped);

        // 8. issues and warnings in (row, field) order
        Comparator<GroupedRows.Issue> byCell = Comparator.comparingInt(
            (GroupedRows.Issue i) -> i.row).thenComparing(i -> i.field);
        st.issues.sort(byCell);
        st.warnings.sort(byCell);
        return new GroupedRows(n, mergedRows, mergedIndexOf, members, grouped, st.keyAsTyped,
                contributors, st.sourceValues, mergedRaw, st.issues, st.warnings, sourceRows,
                st.exactSlotMode, st.groupedFilenames, st.rowsByGroupedFile);
    }

    // 2. an ungrouped row is exactly validateRow() of (a copy of) itself: that very map, so its
    // iteration order (the order its errors are reported in) is today's
    private static Map<String, Object> passUngrouped(State st, int r, Map<String, Object> raw) {
        JSONObject row = st.rows.get(r);
        JSONObject copy = new JSONObject();
        for (String k : row.keySet()) {
            copy.put(k, row.get(k));
            raw.put(k, row.get(k));
        }
        Map<String, Object> merged = BulkImportUtil.validateRow(copy, st.sh,
            st.identificationSkipped);
        Map<String, Object> values = new HashMap<String, Object>();
        for (Map.Entry<String, Object> e : merged.entrySet()) {
            if (!(e.getValue() instanceof BulkValidator)) continue;
            Object v = ((BulkValidator)e.getValue()).getValue();
            if (v != null) values.put(e.getKey(), v);
        }
        st.sourceValues.set(r, Collections.unmodifiableMap(values));
        return merged;
    }

    private static final String ID_FAMILY = "Encounter.id";

    // a grouped ID that is no longer usable: the issue went on its source cells, and the merged
    // row loses the value (as for any other poisoned name)
    private static void failId(State st, GroupId gid, String message, String type, String code) {
        for (Entry e : gid.cells)
            st.issue(e.cell.row, e.cell.field, new BulkValidatorException(message, code, type,
                e.cell.field));
        gid.merged.remove(ID_FAMILY);
        gid.raw.remove(ID_FAMILY);
    }

    /**
     * Grouped encounter IDs (lowercase, already canonical): one group each, none shared with an
     * ungrouped row's ID (compared lowercased, the row's value left as typed), none already in
     * the database. A cell that already holds an Issue is never given a second one.
     */
    private static void checkGroupIds(State st, List<List<Integer> > members, Set<Integer> grouped) {
        if (st.groupIds.isEmpty()) return;
        Map<String, List<GroupedRows.Cell> > ungroupedUse =
            new HashMap<String, List<GroupedRows.Cell> >();
        for (int m = 0; m < members.size(); m++) {
            if (grouped.contains(m)) continue;
            int r = members.get(m).get(0);
            JSONObject row = st.rows.get(r);
            for (String k : new TreeSet<String>(row.keySet())) {
                if (!ID_FAMILY.equals(BulkValidator.canonicalFieldName(k)) || isAbsent(row.opt(k)))
                    continue;
                ungroupedUse.computeIfAbsent(row.get(k).toString().toLowerCase(),
                    x -> new ArrayList<GroupedRows.Cell>()).add(new GroupedRows.Cell(r, k));
            }
        }
        Map<String, Integer> groupsUsing = new HashMap<String, Integer>();
        for (GroupId gid : st.groupIds) groupsUsing.merge(gid.id, 1, Integer::sum);

        Set<String> conflicted = new HashSet<String>();
        List<GroupId> live = new ArrayList<GroupId>();
        for (GroupId gid : st.groupIds) {
            if ((groupsUsing.get(gid.id) > 1) || ungroupedUse.containsKey(gid.id)) {
                failId(st, gid, "this Encounter ID is also used by another group or row",
                    BulkValidatorException.TYPE_GROUP_CONFLICT,
                    ApiException.ERROR_RETURN_CODE_INVALID);
                conflicted.add(gid.id);
            } else {
                live.add(gid);
            }
        }
        for (String id : conflicted) {
            for (GroupedRows.Cell c : ungroupedUse.getOrDefault(id,
                Collections.<GroupedRows.Cell>emptyList()))
                st.issue(c.row, c.field, new BulkValidatorException(
                    "this Encounter ID is also used by a group of rows",
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_GROUP_CONFLICT, c.field));
        }
        if (live.isEmpty()) return;

        Set<String> lookup = new LinkedHashSet<String>();
        for (GroupId gid : live) lookup.add(gid.id);
        Set<String> inUse;
        try {
            inUse = BulkImportUtil.encounterIdsInUse(lookup, st.sh);
        } catch (Exception ex) {
            for (GroupId gid : live)
                failId(st, gid, "Could not check whether this Encounter ID is available",
                    BulkValidatorException.TYPE_INVALID_VALUE,
                    ApiException.ERROR_RETURN_CODE_INVALID);
            return;
        }
        for (GroupId gid : live) {
            if (inUse.contains(gid.id))
                failId(st, gid, "Encounter ID unavailable",
                    BulkValidatorException.TYPE_ID_UNAVAILABLE,
                    ApiException.ERROR_RETURN_CODE_INVALID);
        }
    }

    private static void collapseGroup(State st, List<Integer> rows, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Map<String, Object> raw) {
        Set<String> poisoned = new HashSet<String>(); // merged names whose failure is an Issue
        Map<String, List<Entry> > families = new LinkedHashMap<String, List<Entry> >();
        Map<Integer, List<Entry> > compound = new HashMap<Integer, List<Entry> >();
        List<GroupPhotos.RowPhotos> photos = new ArrayList<GroupPhotos.RowPhotos>();

        for (int r : rows) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put(GROUP_KEY, st.keyAsTyped[r]);
            Set<String> present = new TreeSet<String>();
            Set<String> failedPhotoFields = new TreeSet<String>();
            List<Entry> photoEntries = new ArrayList<Entry>();
            compound.put(r, new ArrayList<Entry>());
            for (Entry e : validateGroupedRow(st, r, poisoned, present, failedPhotoFields)) {
                values.put(e.cell.field, e.bv.getValue());
                if (GroupPhotos.PHOTO.equals(e.bv.getIndexPrefix()))
                    st.groupedFilenames.add(e.bv.getValueString());
                BulkImportUtil.Slot slot = BulkImportUtil.slotOf(e.cell.field);
                if ((slot != null) && GroupPhotos.PHOTO.equals(slot.prefix))
                    st.rowsByGroupedFile.computeIfAbsent(String.valueOf(e.bv.getValue()),
                        x -> new TreeSet<Integer>()).add(r);
                if (GroupPhotos.photoPrefix(e.cell.field) != null) {
                    photoEntries.add(e);
                } else if (isScalar(e.cell.field)) {
                    families.computeIfAbsent(familyKey(e.cell.field),
                        k -> new ArrayList<Entry>()).add(e);
                } else {
                    compound.get(r).add(e);
                }
            }
            st.sourceValues.set(r, Collections.unmodifiableMap(values));
            photos.add(GroupPhotos.resolvePhotos(st, r, present, photoEntries, failedPhotoFields));
        }

        // photos: C8, repeats and the cap, then packing into slots 0..n-1
        GroupPhotos.packPhotos(st, photos, merged, contrib, raw, poisoned);

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

        // values the importer skips silently without an individual or a social unit
        checkIndividualDependents(st, merged, contrib, poisoned);

        // C4: compound encounter data, one identical set per supplying row
        GroupCompound.merge(st, rows, compound, merged, contrib, raw, poisoned);

        // 7. dependent checks; a poisoned name holds a placeholder so nothing is piled on it
        BulkValidatorException placeholder = new BulkValidatorException(
            "already reported on a source cell", ApiException.ERROR_RETURN_CODE_INVALID);
        for (String p : poisoned) merged.put(p, placeholder);
        Set<String> checked = new HashSet<String>(merged.keySet());
        BulkImportUtil.validateDependent(merged, new HashSet<String>(checked), st.sh,
            st.identificationSkipped);
        merged.values().removeIf(v -> v == placeholder);
        // project pairing on a grouped row is C4's (by index, so project00 pairs with project0)
        merged.keySet().removeIf(k -> !checked.contains(k) && GroupCompound.isCompound(k));
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
     * validation. Failures become Issues and poison the cell's family; a failed per-photo cell
     * goes to failedPhotoFields instead, as it is packed per photo. present receives every
     * non-absent cell that passed the header checks. Returns the cells that validated to a value,
     * in field-name order.
     */
    private static List<Entry> validateGroupedRow(State st, int r, Set<String> poisoned,
        Set<String> present, Set<String> failedPhotoFields) {
        JSONObject row = st.rows.get(r);
        Set<String> keys = new TreeSet<String>(row.keySet());
        keys.remove(GROUP_KEY);
        Set<String> ok = new TreeSet<String>();
        for (String k : keys) {
            try {
                BulkValidator.indexIntValue(k); // unknown name, or an index above MAX_INDEX
                if (BulkImportUtil.getLabeledKeywordOffset(k) > BulkValidator.MAX_INDEX)
                    throw new BulkValidatorException("index in " + k + " is above the maximum of " +
                        BulkValidator.MAX_INDEX, ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_VALUE, k);
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
                    markFailed(dup, poisoned, failedPhotoFields);
                    ok.remove(dup);
                }
            }
        }
        JSONObject cells = new JSONObject();
        for (String k : ok) {
            if (isAbsent(row.opt(k))) continue;
            if (GROUP_IGNORED_FIELDS.contains(k)) {
                st.issue(r, k, new BulkValidatorException(
                    "not imported; remove this column from grouped rows",
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_VALUE, k));
                continue;
            }
            // the importer applies labeled keywords only in exact-slot mode (BulkImporter.processRow)
            if (!st.exactSlotMode && (BulkImportUtil.getLabeledKeywordOffset(k) >= 0)) {
                st.issue(r, k, new BulkValidatorException("labeled keywords need a .bbox column",
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_VALUE, k));
                continue;
            }
            cells.put(k, row.get(k));
            present.add(k);
        }
        Map<String, Object> validated = BulkImportUtil.validateFields(cells, st.sh);
        List<Entry> rtn = new ArrayList<Entry>();
        for (String k : new TreeSet<String>(validated.keySet())) {
            Object o = validated.get(k);
            if (o instanceof Exception) {
                st.issue(r, k, asValidatorException((Exception)o, k));
                markFailed(k, poisoned, failedPhotoFields);
                continue;
            }
            BulkValidator bv = (BulkValidator)o;
            if (bv.getValue() == null) { // a non-absent cell that validated to nothing
                st.issue(r, k, new BulkValidatorException("cannot read a value from " + k +
                    ": " + row.get(k), ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_VALUE, k));
                markFailed(k, poisoned, failedPhotoFields);
                continue;
            }
            rtn.add(new Entry(new GroupedRows.Cell(r, k), bv, row.get(k)));
        }
        return rtn;
    }

    private static void markFailed(String field, Set<String> poisoned, Set<String> failedPhotoFields) {
        if (GroupPhotos.photoPrefix(field) != null) {
            failedPhotoFields.add(field);
        } else {
            poisoned.add(emitName(field));
        }
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
            // bounded whatever the group's size: every contributing cell carries this message
            List<String> quoted = new ArrayList<String>();
            for (String v : distinct) quoted.add('"' + clip(v) + '"');
            StringBuilder msg = new StringBuilder(((rowsSeen.size() > 1) ?
                "rows of this group disagree on " : "these columns disagree on ") + family + ": " +
                firstFew(quoted, LIST_LIMIT) + ".");
            if (REMARKS_FAMILIES.contains(family)) msg.append(' ').append(REMARKS_HINT);
            for (Entry e : valid)
                st.issue(e.cell.row, e.cell.field, new BulkValidatorException(msg.toString(),
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_GROUP_CONFLICT, e.cell.field));
            bad = true;
        }
        // a group with a poisoned ID cell skips the ID checks: that group already fails
        if (isId && !bad && !valid.isEmpty())
            st.groupIds.add(new GroupId(distinct.iterator().next(), valid, merged, raw));
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

    static void addContributor(Map<String, List<GroupedRows.Cell> > contrib,
        Map<String, Object> raw, String name, Entry e) {
        contrib.computeIfAbsent(name, k -> new ArrayList<GroupedRows.Cell>()).add(e.cell);
        raw.putIfAbsent(name, e.raw);
    }

    // absent on a grouped row: null, JSON null, or text that is empty after trim()
    static boolean isAbsent(Object v) {
        return (v == null) || (v == JSONObject.NULL) ||
                   ((v instanceof String) && ((String)v).trim().isEmpty());
    }

    // scalar = not a photo, per-photo, labeled keyword or compound (C4) field
    static boolean isScalar(String field) {
        try {
            if (BulkValidator.indexIntValue(field) >= 0) return false;
        } catch (BulkValidatorException ex) {
            return false;
        }
        if (BulkImportUtil.getLabeledKeywordOffset(field) >= 0) return false;
        return !GroupCompound.isCompound(field);
    }

    /**
     * Scalar-family completeness on the merged values: without an individual the importer drops
     * a nickname and a social unit, and without a social unit a membership role. REQUIRED on each
     * cell that supplied the dropped value. Nothing is added when the family it depends on
     * already failed (its failure is an Issue).
     */
    private static void checkIndividualDependents(State st, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Set<String> poisoned) {
        if (GroupCompound.noIndividual(merged, poisoned)) {
            requireOnSuppliers(st, NICKNAME, GroupCompound.INDIVIDUAL, merged, contrib, poisoned);
            requireOnSuppliers(st, SOCIAL_UNIT, GroupCompound.INDIVIDUAL, merged, contrib, poisoned);
        }
        if (!poisoned.contains(SOCIAL_UNIT) && !(merged.get(SOCIAL_UNIT) instanceof BulkValidator))
            requireOnSuppliers(st, ROLE, SOCIAL_UNIT, merged, contrib, poisoned);
    }

    private static void requireOnSuppliers(State st, String name, String needs,
        Map<String, Object> merged, Map<String, List<GroupedRows.Cell> > contrib,
        Set<String> poisoned) {
        if (!(merged.get(name) instanceof BulkValidator)) return;
        for (GroupedRows.Cell c : contrib.get(name))
            st.issue(c.row, c.field, new BulkValidatorException(c.field + " needs " + needs +
                " in the group", ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE, c.field));
        merged.remove(name);
        poisoned.add(name);
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
