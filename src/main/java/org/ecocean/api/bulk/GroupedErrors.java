package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The field errors of a collapsed upload, reported on source cells in source-row order, and the
 * rule that makes a request fatal. Each failure is reported once: a source-cell failure from
 * GroupedRows.issues(), a dependent failure from the merged map it lives in (mapped back through
 * cellsFor(), its text through sourceText()). Any error on a grouped row, any issue, and any bad
 * photo of a group make the request fatal, whatever the tolerance. Ungrouped rows report exactly
 * as before grouping existed.
 */
public final class GroupedErrors {
    private final GroupedRows g;
    private final List<JSONObject> errors = new ArrayList<JSONObject>();
    private final List<JSONObject> warnings = new ArrayList<JSONObject>();
    private final Set<Integer> invalidRows = new HashSet<Integer>();
    // the source cells of grouped rows that an error names
    private final Set<GroupedRows.Cell> groupedErrorCells = new HashSet<GroupedRows.Cell>();
    private boolean fatal;

    // one candidate entry: a JSON error/warning for one source row
    private static final class Entry {
        final int row;
        final JSONObject json;
        final boolean warning;
        final String dedupe; // the warning's dedupe key (its exception text)

        Entry(int row, JSONObject json, boolean warning, String dedupe) {
            this.row = row;
            this.json = json;
            this.warning = warning;
            this.dedupe = dedupe;
        }
    }

    private GroupedErrors(GroupedRows g) {
        this.g = g;
    }

    /**
     * Collects every error and warning of g's rows. Call it after the servlet has written its
     * file and box findings into the merged maps, and before anything else is put into them: an
     * ungrouped row's errors are reported in its map's iteration order, which a further put can
     * change (a resize). A warning (treatAsWarning) is kept once per distinct exception text, as
     * before; issues and every finding on a grouped row are errors. Only BulkValidatorException
     * values of the merged maps are read.
     */
    public static GroupedErrors collect(GroupedRows g, boolean badFieldnamesAreWarnings) {
        GroupedErrors ge = new GroupedErrors(g);
        Set<String> warningDuplicate = new HashSet<String>();
        List<Entry> entries = new ArrayList<Entry>();

        for (GroupedRows.Issue i : g.issues())
            entries.add(new Entry(i.row, json(i.row, i.field, i.error, i.groupKey, null), false,
                null));
        for (GroupedRows.Issue w : g.warnings()) {
            boolean warning = w.error.treatAsWarning(badFieldnamesAreWarnings);
            entries.add(new Entry(w.row, json(w.row, w.field, w.error, w.groupKey, null), warning,
                w.error.toString()));
        }
        List<Map<String, Object> > merged = g.mergedRows();
        for (int m = 0; m < merged.size(); m++) {
            boolean grouped = g.isGrouped(m);
            for (Map.Entry<String, Object> f : merged.get(m).entrySet()) {
                if (!(f.getValue() instanceof BulkValidatorException)) continue;
                BulkValidatorException bve = (BulkValidatorException)f.getValue();
                if (!grouped) {
                    int row = g.membersOf(m).get(0);
                    entries.add(new Entry(row, json(row, f.getKey(), bve, null, null),
                        bve.treatAsWarning(badFieldnamesAreWarnings), bve.toString()));
                    continue;
                }
                for (GroupedRows.Cell c : g.cellsFor(m, f.getKey()))
                    entries.add(new Entry(c.row, json(c.row, c.field, bve,
                        g.groupKeyAsTyped(c.row), mergedText(g, m)), false, null));
            }
        }
        entries.sort(Comparator.comparingInt((Entry e) -> e.row)); // stable: in-row order kept
        for (Entry e : entries) {
            if (e.warning) {
                // hackily prevents multiple identical warnings (like bad fieldname x N rows)
                if (warningDuplicate.add(e.dedupe)) ge.warnings.add(e.json);
                continue;
            }
            ge.errors.add(e.json);
            ge.invalidRows.add(e.row);
            if (g.isGroupedRow(e.row)) {
                ge.fatal = true;
                ge.groupedErrorCells.add(new GroupedRows.Cell(e.row,
                    e.json.optString("fieldName", null)));
            }
        }
        if (g.hasGroupErrors()) ge.fatal = true;
        return ge;
    }

    // rewrites a merged-row text to source names
    private static UnaryOperator<String> mergedText(GroupedRows g, int m) {
        return text -> g.sourceText(m, text);
    }

    private static JSONObject json(int row, String field, BulkValidatorException bve,
        String groupKey, UnaryOperator<String> text) {
        JSONObject err = new JSONObject();
        err.put("rowNumber", row);
        err.put("fieldName", field);
        err.put("type", bve.getType());
        JSONArray errs = bve.getErrors();
        if ((text != null) && (errs != null)) {
            errs = new JSONArray(text.apply(errs.toString()));
            for (int i = 0; i < errs.length(); i++) {
                JSONObject one = errs.optJSONObject(i);
                if ((one != null) && one.has("fieldName")) one.put("fieldName", field);
            }
        }
        err.put("errors", errs);
        err.put("details", (text == null) ? bve.toString() : text.apply(bve.toString()));
        if (groupKey != null) err.put("groupKey", groupKey);
        return err;
    }

    /** True when filename is a photo of some group. */
    public boolean isGroupedFile(String filename) {
        return g.groupedFilenames().contains(filename);
    }

    /**
     * A file failed its check: a group's photo makes the request fatal and invalidates every
     * grouped source row naming it.
     */
    public void fileFailed(String filename) {
        if (!isGroupedFile(filename)) return;
        fatal = true;
        invalidRows.addAll(g.sourceRowsUsing(filename));
    }

    /** The field errors, in source-row order. */
    public List<JSONObject> errors() {
        return Collections.unmodifiableList(errors);
    }

    /** The field warnings, in source-row order, one per distinct exception text. */
    public List<JSONObject> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public boolean fatal() {
        return fatal;
    }

    /**
     * Valid source cells on grouped rows: each validated non-absent cell (the key included) that
     * no field error names. The same unit as the field errors, which name source cells.
     */
    public int numberGroupedCellsValid() {
        int n = 0;
        for (int r = 0; r < g.sourceRowCount(); r++) {
            if (!g.isGroupedRow(r)) continue;
            for (String field : g.sourceValues(r).keySet()) {
                if (!groupedErrorCells.contains(new GroupedRows.Cell(r, field))) n++;
            }
        }
        return n;
    }

    /** Source rows that no error names (and, for a grouped row, whose photos all passed). */
    public int numberRowsValid() {
        return g.sourceRowCount() - invalidRows.size();
    }
}
