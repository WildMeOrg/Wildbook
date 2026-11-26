package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * The result of GroupCollapse.collapse(): one merged row per group or ungrouped source row, plus
 * the provenance that maps merged rows and fields back to source rows and cells. Provenance is
 * fixed at construction. Each failure is represented once: a source-cell failure only in issues();
 * a dependent failure only as an exception in a merged map, mapped back through cellsFor().
 */
public final class GroupedRows {
    /** One source cell: zero-based source row and its original column name. */
    public static final class Cell {
        public final int row;
        public final String field;

        public Cell(int row, String field) {
            this.row = row;
            this.field = field;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Cell)) return false;
            Cell c = (Cell)o;
            return (row == c.row) && Objects.equals(field, c.field);
        }

        @Override public int hashCode() {
            return Objects.hash(row, field);
        }

        @Override public String toString() {
            return "(" + row + ", " + field + ")";
        }
    }

    /** A source-only failure on one grouped cell; groupKey is the key as typed on that row. */
    public static final class Issue {
        public final int row;
        public final String field;
        public final BulkValidatorException error;
        public final String groupKey;

        public Issue(int row, String field, BulkValidatorException error, String groupKey) {
            this.row = row;
            this.field = field;
            this.error = error;
            this.groupKey = groupKey;
        }

        @Override public String toString() {
            return "(" + row + ", " + field + ") " + error.getType() + ": " + error.getMessage() +
                       " [" + groupKey + "]";
        }
    }

    private final int sourceRowCount;
    private final List<Map<String, Object> > mergedRows;
    private final int[] mergedIndexOf;
    private final List<List<Integer> > members;
    private final Set<Integer> groupedMergedIndexes;
    private final String[] keyAsTyped;
    private final List<Map<String, List<Cell> > > contributors;
    private final List<Map<String, Object> > sourceValues;
    private final List<Map<String, Object> > mergedRaw;
    private final List<Issue> issues;
    private final List<Issue> warnings;
    private final List<List<String> > sortedKeys; // each source row's keys, sorted
    private final boolean exactSlotMode;
    private final Map<String, Set<Integer> > rowsByGroupedFile;

    // built only by GroupCollapse, which hands over collections it no longer touches
    GroupedRows(int sourceRowCount, List<Map<String, Object> > mergedRows, int[] mergedIndexOf,
        List<List<Integer> > members, Set<Integer> groupedMergedIndexes, String[] keyAsTyped,
        List<Map<String, List<Cell> > > contributors, List<Map<String, Object> > sourceValues,
        List<Map<String, Object> > mergedRaw, List<Issue> issues, List<Issue> warnings,
        List<JSONObject> sourceRows, boolean exactSlotMode,
        Map<String, Set<Integer> > rowsByGroupedFile) {
        this.sourceRowCount = sourceRowCount;
        this.mergedRows = Collections.unmodifiableList(mergedRows);
        this.mergedIndexOf = mergedIndexOf;
        this.members = members;
        this.groupedMergedIndexes = Collections.unmodifiableSet(groupedMergedIndexes);
        this.keyAsTyped = keyAsTyped;
        this.contributors = contributors;
        this.sourceValues = sourceValues;
        this.mergedRaw = mergedRaw;
        this.issues = Collections.unmodifiableList(issues);
        this.warnings = Collections.unmodifiableList(warnings);
        List<List<String> > keys = new ArrayList<List<String> >();
        for (JSONObject row : sourceRows)
            keys.add(List.copyOf(new TreeSet<String>(row.keySet())));
        this.sortedKeys = keys;
        this.exactSlotMode = exactSlotMode;
        this.rowsByGroupedFile = rowsByGroupedFile;
    }

    public int sourceRowCount() {
        return sourceRowCount;
    }

    /** The list is fixed; each map is mutable (the servlet adds file/box errors and the owner). */
    public List<Map<String, Object> > mergedRows() {
        return mergedRows;
    }

    public int mergedIndexOf(int sourceRow) {
        return mergedIndexOf[sourceRow];
    }

    /** Every source row of the merged row, in sheet order. */
    public List<Integer> membersOf(int mergedIndex) {
        return members.get(mergedIndex);
    }

    public boolean isGrouped(int mergedIndex) {
        return groupedMergedIndexes.contains(mergedIndex);
    }

    public boolean isGroupedRow(int sourceRow) {
        return isGrouped(mergedIndexOf[sourceRow]);
    }

    public Set<Integer> groupedMergedIndexes() {
        return groupedMergedIndexes;
    }

    /**
     * The trimmed key as typed on that row; null when the row is ungrouped. A non-String key
     * (an error) returns raw.toString().
     */
    public String groupKeyAsTyped(int sourceRow) {
        return keyAsTyped[sourceRow];
    }

    /** Every cell that supplied a non-absent value to the merged field (empty when none). */
    public List<Cell> contributorsOf(int mergedIndex, String mergedField) {
        if (!isGrouped(mergedIndex)) {
            int row = members.get(mergedIndex).get(0);
            return sourceValues.get(row).containsKey(mergedField) ?
                       List.of(new Cell(row, mergedField)) : List.of();
        }
        List<Cell> cells = contributors.get(mergedIndex).get(mergedField);
        return (cells == null) ? List.of() : cells;
    }

    /**
     * The source cells a merged diagnostic on mergedField belongs to: its contributors; else, per
     * member, the member's own column of that field family if it has one, else (member, mergedField).
     */
    public List<Cell> cellsFor(int mergedIndex, String mergedField) {
        if (!isGrouped(mergedIndex))
            return List.of(new Cell(members.get(mergedIndex).get(0), mergedField));
        List<Cell> cells = contributorsOf(mergedIndex, mergedField);
        if (!cells.isEmpty()) return cells;
        String family = BulkValidator.canonicalFieldName(mergedField);
        List<Cell> rtn = new ArrayList<Cell>();
        for (int row : members.get(mergedIndex)) {
            String own = null;
            for (String k : sortedKeys.get(row)) {
                if (BulkValidator.canonicalFieldName(k).equals(family)) {
                    own = k;
                    break;
                }
            }
            rtn.add(new Cell(row, (own == null) ? mergedField : own));
        }
        return Collections.unmodifiableList(rtn);
    }

    /** Source-only failures on grouped rows, sorted by (row, field). */
    public List<Issue> issues() {
        return issues;
    }

    /** Blank cells under unknown headers on grouped rows, sorted by (row, field). */
    public List<Issue> warnings() {
        return warnings;
    }

    /**
     * Grouped row: validated non-absent values under their original names, the key as typed.
     * Ungrouped row: every non-null validated value of its merged row.
     */
    public Map<String, Object> sourceValues(int sourceRow) {
        return sourceValues.get(sourceRow);
    }

    /** Raw source values under merged names (the first contributor's raw value); a copy. */
    public JSONObject mergedRaw(int mergedIndex) {
        JSONObject rtn = new JSONObject();
        for (Map.Entry<String, Object> e : mergedRaw.get(mergedIndex).entrySet())
            rtn.put(e.getKey(), e.getValue());
        return rtn;
    }

    private static final Pattern SLOT_FIELD = Pattern.compile(
        "Encounter\\.(mediaAsset|keyword|quality)(\\d+)((\\.[A-Za-z]+)?)");

    /**
     * text (a merged-row diagnostic) with each packed field name rewritten to its source cell's
     * name: the field's single contributor, else, for an Encounter.mediaAsset{k}... name, its
     * packed photo's source column plus the same suffix. Other names are unchanged, as is the
     * text of an ungrouped row, and so is any part of the text that is one of the group's photo
     * filenames (a file may be named like a field: "Encounter.mediaAsset0.jpg").
     */
    public String sourceText(int mergedIndex, String text) {
        if ((text == null) || !isGrouped(mergedIndex)) return text;
        Matcher m = SLOT_FIELD.matcher(text);
        StringBuffer sb = new StringBuffer();
        List<int[]> literal = null;
        while (m.find()) {
            String token = m.group();
            String rtn = token;
            if (literal == null) literal = filenameSpans(mergedIndex, text);
            if (!overlaps(literal, m.start(), m.end())) {
                List<Cell> own = contributorsOf(mergedIndex, token);
                if (own.size() == 1) {
                    rtn = own.get(0).field;
                } else if (m.group(1).equals("mediaAsset")) {
                    List<Cell> photo = contributorsOf(mergedIndex,
                        "Encounter.mediaAsset" + m.group(2));
                    if (photo.size() == 1) rtn = photo.get(0).field + m.group(3);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rtn));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // where the merged row's photo filenames (as given, and JSON-escaped) occur in text
    private List<int[]> filenameSpans(int mergedIndex, String text) {
        List<int[]> spans = new ArrayList<int[]>();
        for (int row : members.get(mergedIndex)) {
            for (Map.Entry<String, Object> e : sourceValues.get(row).entrySet()) {
                BulkImportUtil.Slot slot = BulkImportUtil.slotOf(e.getKey());
                if ((slot == null) || !GroupPhotos.PHOTO.equals(slot.prefix) ||
                    (e.getValue() == null))
                    continue;
                String name = e.getValue().toString();
                String quoted = JSONObject.quote(name);
                for (String form : new String[] { name, quoted.substring(1, quoted.length() - 1) }) {
                    if (form.isEmpty()) continue;
                    for (int i = text.indexOf(form); i >= 0; i = text.indexOf(form, i + 1))
                        spans.add(new int[] { i, i + form.length() });
                }
            }
        }
        return spans;
    }

    private static boolean overlaps(List<int[]> spans, int start, int end) {
        for (int[] s : spans) {
            if ((start < s[1]) && (end > s[0])) return true;
        }
        return false;
    }

    /** A .bbox column on any original row, blanks included. */
    public boolean exactSlotMode() {
        return exactSlotMode;
    }

    /** The non-absent bare Encounter.mediaAsset# values on grouped rows. */
    public Set<String> groupedFilenames() {
        return Collections.unmodifiableSet(rowsByGroupedFile.keySet());
    }

    /** The grouped source rows naming filename in a bare photo column, ascending; empty when none. */
    public Set<Integer> sourceRowsUsing(String filename) {
        Set<Integer> rows = rowsByGroupedFile.get(filename);
        return (rows == null) ? Set.of() : Collections.unmodifiableSet(rows);
    }

    public boolean hasGroupErrors() {
        if (!issues.isEmpty()) return true;
        for (int m : groupedMergedIndexes) {
            for (Object o : mergedRows.get(m).values()) {
                if (o instanceof Exception) return true;
            }
        }
        return false;
    }
}
