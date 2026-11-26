package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ecocean.IAJsonProperties;
import org.ecocean.LocationID;
import org.ecocean.Project;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.User;
import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.api.bulk.GroupCollapse;
import org.ecocean.api.bulk.GroupedErrors;
import org.ecocean.api.bulk.GroupedRows;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Bulk import groupKey (#1789), Task 3: GroupCollapse core. Rows sharing an Encounter.groupKey
 * become one merged row; blanks abstain; each source-cell failure is an Issue; dependent failures
 * stay in the merged map and are mapped back through cellsFor. Task 4: photos of a group are
 * packed into consecutive slots, each with its own metadata (C2), plus the C8 and repeat rules.
 * Task 5: compound encounter data (C4) must be one identical set per supplying row, complete
 * and unambiguous; ignored fields are rejected on grouped rows.
 */
class GroupCollapseTest {
    static final String KEY = "Encounter.groupKey";
    static final String UUID_UPPER = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";

    Shepherd sh;
    MockedStatic<LocationID> locationIds;
    MockedStatic<BulkImportUtil> bulkUtil;
    List<String> savedMeasurements;
    Map<String, Set<String> > savedKeywords;

    @BeforeEach void setUp() {
        sh = mock(Shepherd.class);
        when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
        when(sh.getUser(anyString())).thenReturn(mock(User.class));
        locationIds = mockStatic(LocationID.class);
        locationIds.when(() -> LocationID.isValidLocationID(anyString())).thenReturn(true);
        bulkUtil = mockStatic(BulkImportUtil.class, CALLS_REAL_METHODS);
        bulkUtil.when(() -> BulkImportUtil.encounterIdsInUse(anyCollection(), any())).thenReturn(
            new HashSet<String>());
        savedMeasurements = BulkImportUtil.measurementValues;
        savedKeywords = BulkImportUtil.labeledKeywordMap;
        BulkImportUtil.measurementValues = new ArrayList<String>(List.of("length"));
        BulkImportUtil.labeledKeywordMap = new LinkedHashMap<String, Set<String> >();
    }

    @AfterEach void tearDown() {
        TestIAJsonPropertiesUtil.override(null);
        locationIds.close();
        bulkUtil.close();
        BulkImportUtil.measurementValues = savedMeasurements;
        BulkImportUtil.labeledKeywordMap = savedKeywords;
    }

    static JSONObject obj(Object... kv) {
        JSONObject row = new JSONObject();
        for (int i = 0; i < kv.length; i += 2) row.put((String)kv[i], kv[i + 1]);
        return row;
    }

    // a grouped row that satisfies species + date on its own
    static JSONObject full(String key, String photo, Object... kv) {
        JSONObject row = obj(KEY, key, "Encounter.genus", "Genus", "Encounter.specificEpithet",
            "species", "Encounter.year", 2020, "Encounter.mediaAsset0", photo);
        for (int i = 0; i < kv.length; i += 2) row.put((String)kv[i], kv[i + 1]);
        return row;
    }

    // a grouped row with only a photo plus the given cells
    static JSONObject bare(String key, String photo, Object... kv) {
        JSONObject row = obj(KEY, key, "Encounter.mediaAsset0", photo);
        for (int i = 0; i < kv.length; i += 2) row.put((String)kv[i], kv[i + 1]);
        return row;
    }

    GroupedRows collapse(JSONObject... rows) {
        return GroupCollapse.collapse(Arrays.asList(rows), sh, false, BulkValidator.MAX_INDEX + 1);
    }

    static GroupedRows.Cell cell(int row, String field) {
        return new GroupedRows.Cell(row, field);
    }

    static List<GroupedRows.Cell> issueCells(GroupedRows g, String type) {
        List<GroupedRows.Cell> cells = new ArrayList<GroupedRows.Cell>();
        for (GroupedRows.Issue is : g.issues()) {
            if ((type == null) || is.error.isType(type)) cells.add(cell(is.row, is.field));
        }
        return cells;
    }

    static BulkValidatorException mergedError(GroupedRows g, int m, String field) {
        Object o = g.mergedRows().get(m).get(field);
        return (o instanceof BulkValidatorException) ? (BulkValidatorException)o : null;
    }

    static Object mergedValue(GroupedRows g, int m, String field) {
        Object o = g.mergedRows().get(m).get(field);
        assertTrue(o instanceof BulkValidator, field + " -> " + o);
        return ((BulkValidator)o).getValue();
    }

    static void assertNoMergedErrors(GroupedRows g, int m) {
        for (Map.Entry<String, Object> e : g.mergedRows().get(m).entrySet())
            assertTrue(e.getValue() instanceof BulkValidator, e.getKey() + " -> " + e.getValue());
    }

    static JSONObject copy(JSONObject row) {
        JSONObject c = new JSONObject();
        for (String k : row.keySet()) c.put(k, row.get(k));
        return c;
    }

    @Test void ungroupedRowsPassThroughUnchanged() {
        JSONObject[] rows = {
            obj("Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
                "Encounter.year", 2020, "Encounter.mediaAsset0", "a.jpg", "Encounter.sex", "male"),
            obj("Encounter.genus", "Genus", "Encounter.sex", "x", "Encounter.mediaAsset0", "b.jpg"),
            obj(KEY, "  ", "Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
                "Encounter.year", 2020, "Encounter.catalogNumber", UUID_UPPER, "Bogus.field", "x",
                "Encounter.year", 2021, "Sighting.year", 2022)
        };
        GroupedRows g = collapse(rows);
        assertEquals(3, g.sourceRowCount());
        assertEquals(3, g.mergedRows().size());
        assertTrue(g.groupedMergedIndexes().isEmpty());
        assertFalse(g.hasGroupErrors());
        for (int i = 0; i < rows.length; i++) {
            assertEquals(i, g.mergedIndexOf(i));
            assertFalse(g.isGrouped(i));
            assertFalse(g.isGroupedRow(i));
            assertNull(g.groupKeyAsTyped(i));
            assertEquals(List.of(i), g.membersOf(i));
            assertEquals(List.of(cell(i, "Encounter.sex")), g.cellsFor(i, "Encounter.sex"));
            Map<String, Object> expected = BulkImportUtil.validateRow(copy(rows[i]), sh, false);
            Map<String, Object> actual = g.mergedRows().get(i);
            assertEquals(expected.keySet(), actual.keySet(), "row " + i);
            for (String k : expected.keySet()) {
                Object e = expected.get(k);
                Object a = actual.get(k);
                assertEquals(e.getClass(), a.getClass(), k);
                if (e instanceof BulkValidator) {
                    assertEquals(((BulkValidator)e).getValue(), ((BulkValidator)a).getValue(), k);
                } else {
                    assertEquals(((Exception)e).getMessage(), ((Exception)a).getMessage(), k);
                }
            }
        }
        assertTrue(g.issues().isEmpty());
        assertTrue(g.warnings().isEmpty());
        assertEquals("male", g.sourceValues(0).get("Encounter.sex"));
    }

    @Test void caseInsensitiveKeysJoin() {
        GroupedRows g = collapse(full("A1", "a.jpg"), bare("a1", "b.jpg"));
        assertEquals(1, g.mergedRows().size());
        assertEquals(List.of(0, 1), g.membersOf(0));
        assertEquals("A1", g.groupKeyAsTyped(0));
        assertEquals("a1", g.groupKeyAsTyped(1));
        assertTrue(g.isGrouped(0));
        assertTrue(g.isGroupedRow(0) && g.isGroupedRow(1));
        assertEquals(Set.of(0), g.groupedMergedIndexes());
        assertEquals(0, g.mergedIndexOf(1));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertFalse(g.hasGroupErrors());
        // the key is trimmed as typed
        g = collapse(full(" A1 ", "a.jpg"));
        assertEquals("A1", g.groupKeyAsTyped(0));
    }

    @Test void literalKeysStayApart() {
        GroupedRows g = collapse(full("001", "a.jpg"), full("1", "b.jpg"));
        assertEquals(2, g.mergedRows().size());
        assertEquals(List.of(0), g.membersOf(0));
        assertEquals(List.of(1), g.membersOf(1));
        g = collapse(full("É1", "a.jpg"), full("é1", "b.jpg"));
        assertEquals(2, g.mergedRows().size());
        // merged order is each group's first source row, ungrouped rows included
        g = collapse(full("B", "a.jpg"), obj("Encounter.genus", "Genus"), bare("b", "c.jpg"));
        assertEquals(2, g.mergedRows().size());
        assertEquals(List.of(0, 2), g.membersOf(0));
        assertEquals(List.of(1), g.membersOf(1));
        assertEquals(1, g.mergedIndexOf(1));
        assertEquals(0, g.mergedIndexOf(2));
    }

    @Test void explicitBlanksAbstain() {
        String[] keys = { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year",
            "Encounter.catalogNumber", "Encounter.submitterID", "Encounter.sightingID",
            "Encounter.locationID" };
        Object[] vals = { "Genus", "species", 2020, UUID_UPPER, "u1", "S1", "L" };
        JSONObject r0 = obj(KEY, "A1", "Encounter.mediaAsset0", "a.jpg");
        JSONObject r1 = obj(KEY, "a1", "Encounter.mediaAsset0", "b.jpg");
        for (int i = 0; i < keys.length; i++) {
            r0.put(keys[i], vals[i]);
            r1.put(keys[i], "");
        }
        GroupedRows g = collapse(r0, r1);
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertTrue(g.warnings().isEmpty());
        assertNoMergedErrors(g, 0);
        assertEquals(Map.of(KEY, "a1", "Encounter.mediaAsset0", "b.jpg"), g.sourceValues(1));
        assertEquals("u1", mergedValue(g, 0, "Encounter.submitterID"));
        assertEquals(List.of(cell(0, "Encounter.submitterID")),
            g.contributorsOf(0, "Encounter.submitterID"));
        // a JSON null abstains too
        r1.put("Encounter.submitterID", JSONObject.NULL);
        g = collapse(r0, r1);
        assertTrue(g.issues().isEmpty(), g.issues().toString());
    }

    @Test void splitSpeciesAcrossRowsIsCheckedAsPair() {
        GroupedRows g = collapse(bare("G", "a.jpg", "Encounter.genus", "Genus", "Encounter.year", 2020),
            bare("G", "b.jpg", "Encounter.specificEpithet", "species"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        when(sh.isValidTaxonomyName(anyString())).thenReturn(false);
        g = collapse(bare("G", "a.jpg", "Encounter.genus", "Genus", "Encounter.year", 2020),
            bare("G", "b.jpg", "Encounter.specificEpithet", "species"));
        assertNotNull(mergedError(g, 0, "Encounter.genus"));
        assertNotNull(mergedError(g, 0, "Encounter.specificEpithet"));
        assertEquals(List.of(cell(0, "Encounter.genus")), g.cellsFor(0, "Encounter.genus"));
        assertEquals(List.of(cell(1, "Encounter.specificEpithet")),
            g.cellsFor(0, "Encounter.specificEpithet"));
    }

    @Test void splitDateAcrossRowsCombines() {
        GroupedRows g = collapse(full("G", "a.jpg"),
            bare("G", "b.jpg", "Encounter.month", 3, "Encounter.day", "14"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        assertEquals(2020, mergedValue(g, 0, "Encounter.year"));
        assertEquals(3, mergedValue(g, 0, "Encounter.month"));
        assertEquals(14, mergedValue(g, 0, "Encounter.day"));
    }

    @Test void mixedPrefixCoordinatesPair() {
        GroupedRows g = collapse(full("G", "a.jpg", "Sighting.decimalLatitude", 45),
            bare("G", "b.jpg", "Encounter.decimalLongitude", -120));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        Map<String, Object> m = g.mergedRows().get(0);
        assertTrue(m.containsKey("Encounter.decimalLatitude"));
        assertTrue(m.containsKey("Encounter.decimalLongitude"));
        assertFalse(m.containsKey("Sighting.decimalLatitude"));
        assertEquals("Encounter.decimalLatitude",
            ((BulkValidator)m.get("Encounter.decimalLatitude")).getFieldName());
        assertEquals(45.0, mergedValue(g, 0, "Encounter.decimalLatitude"));
        assertEquals(List.of(cell(0, "Sighting.decimalLatitude")),
            g.contributorsOf(0, "Encounter.decimalLatitude"));
    }

    @Test void mixedPrefixFeb30Rejected() {
        GroupedRows g = collapse(
            obj(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.genus", "Genus",
                "Encounter.specificEpithet", "species", "Sighting.month", 2, "Sighting.day", 30),
            bare("G", "b.jpg", "Encounter.year", 2023));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNotNull(mergedError(g, 0, "Encounter.day"));
        assertEquals(List.of(cell(0, "Sighting.day")), g.cellsFor(0, "Encounter.day"));
    }

    @Test void equalValuesInDifferentFormsAgree() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.decimalLatitude", "45.1",
            "Encounter.decimalLongitude", 10), bare("G", "b.jpg", "Encounter.decimalLatitude", 45.10));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(List.of(cell(0, "Encounter.decimalLatitude"), cell(1, "Encounter.decimalLatitude")),
            g.contributorsOf(0, "Encounter.decimalLatitude"));
    }

    @Test void scalarConflictBlamesEachSupplier() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.sex", "male"),
            bare("g", "b.jpg", "Encounter.sex", "female"));
        assertEquals(List.of(cell(0, "Encounter.sex"), cell(1, "Encounter.sex")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(2, g.issues().size());
        assertEquals("G", g.issues().get(0).groupKey);
        assertEquals("g", g.issues().get(1).groupKey);
        assertTrue(g.issues().get(0).error.getMessage().contains("male"));
        assertTrue(g.issues().get(0).error.getMessage().contains("female"));
        assertTrue(g.hasGroupErrors());
    }

    @Test void aliasFamilyCompared() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.latitude", 1.0, "Encounter.longitude", 2.0),
            bare("G", "b.jpg", "Sighting.decimalLatitude", 2.0));
        assertEquals(List.of(cell(0, "Encounter.latitude"), cell(1, "Sighting.decimalLatitude")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
    }

    @Test void sameTargetPairCompared() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.behavior", "a"),
            bare("G", "b.jpg", "Sighting.groupBehavior", "b"));
        assertEquals(List.of(cell(0, "Encounter.behavior"), cell(1, "Sighting.groupBehavior")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertTrue(g.issues().get(0).error.getMessage().startsWith(
            "rows of this group disagree on Encounter.behavior:"), g.issues().get(0).error.getMessage());
        // the pair on one grouped row: the message does not claim the rows disagree
        g = collapse(full("G", "a.jpg", "Encounter.behavior", "a", "Sighting.groupBehavior", "b"),
            bare("G", "b.jpg"));
        assertEquals(List.of(cell(0, "Encounter.behavior"), cell(0, "Sighting.groupBehavior")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        for (GroupedRows.Issue is : g.issues())
            assertEquals("these columns disagree on Encounter.behavior: \"a\", \"b\".",
                is.error.getMessage());
        g = collapse(full("G", "a.jpg", "Encounter.behavior", "a"),
            bare("G", "b.jpg", "Sighting.groupBehavior", "a"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals("a", mergedValue(g, 0, "Encounter.behavior"));
        assertEquals("a", mergedValue(g, 0, "Sighting.groupBehavior"));
        assertEquals(List.of(cell(1, "Sighting.groupBehavior")),
            g.contributorsOf(0, "Sighting.groupBehavior"));
    }

    @Test void unparseableGroupedCellIsInvalid() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.year", 2021),
            bare("G", "b.jpg", "Encounter.year", "2O20"));
        assertEquals(List.of(cell(1, "Encounter.year")), issueCells(g, null));
        assertTrue(g.issues().get(0).error.isType(BulkValidatorException.TYPE_INVALID_VALUE));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.year"));
        assertNoMergedErrors(g, 0);
        assertTrue(g.hasGroupErrors());
    }

    @Test void aliasFamilyWithOneInvalidCellDropsFamily() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.decimalLatitude", 45,
            "Encounter.decimalLongitude", 10), bare("G", "b.jpg", "Sighting.decimalLatitude", 200));
        assertEquals(List.of(cell(1, "Sighting.decimalLatitude")), issueCells(g, null));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.decimalLatitude"));
        // no REQUIRED on the partner longitude, no error left in the merged row
        assertNoMergedErrors(g, 0);
        assertEquals(10.0, mergedValue(g, 0, "Encounter.decimalLongitude"));
    }

    @Test void yearAndMillisAcrossRowsRejected() {
        GroupedRows g = collapse(full("G", "a.jpg"),
            bare("G", "b.jpg", "Encounter.dateInMilliseconds", 1000000L));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNotNull(mergedError(g, 0, "Encounter.year"));
        assertNotNull(mergedError(g, 0, "Encounter.dateInMilliseconds"));
        assertEquals(List.of(cell(0, "Encounter.year")), g.cellsFor(0, "Encounter.year"));
        assertEquals(List.of(cell(1, "Encounter.dateInMilliseconds")),
            g.cellsFor(0, "Encounter.dateInMilliseconds"));
        assertTrue(g.hasGroupErrors());
    }

    @Test void millisWithCalendarComponentConflicts() {
        GroupedRows g = collapse(
            obj(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.genus", "Genus",
                "Encounter.specificEpithet", "species", "Encounter.dateInMilliseconds", 1000000L),
            bare("G", "b.jpg", "Encounter.month", 4));
        assertEquals(List.of(cell(0, "Encounter.dateInMilliseconds"), cell(1, "Encounter.month")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(2, g.issues().size());
        // its failure is already an issue: no REQUIRED on the year
        assertNull(mergedError(g, 0, "Encounter.year"), String.valueOf(g.mergedRows().get(0)));
    }

    @Test void missingYearFansOutToEveryRow() {
        GroupedRows g = collapse(
            obj(KEY, "G", "Encounter.mediaAsset0", "a.jpg", "Encounter.genus", "Genus"),
            bare("G", "b.jpg", "Encounter.specificEpithet", "species"),
            bare("G", "c.jpg", "Sighting.year", ""));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        BulkValidatorException ex = mergedError(g, 0, "Encounter.year");
        assertNotNull(ex);
        assertTrue(ex.isType(BulkValidatorException.TYPE_REQUIRED_VALUE));
        // the member's own column of the family when it has one
        assertEquals(List.of(cell(0, "Encounter.year"), cell(1, "Encounter.year"), cell(2, "Sighting.year")),
            g.cellsFor(0, "Encounter.year"));
        assertTrue(g.hasGroupErrors());
    }

    @Test void twoInvalidCellsOneFieldBothReported() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.sex", "x"),
            bare("G", "b.jpg", "Encounter.sex", "y"));
        assertEquals(List.of(cell(0, "Encounter.sex"), cell(1, "Encounter.sex")), issueCells(g, null));
        assertFalse(g.issues().get(0).error.isType(BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertTrue(g.issues().get(0).error.getMessage().contains("x"));
        assertTrue(g.issues().get(1).error.getMessage().contains("y"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.sex"));
    }

    @Test void groupedUnknownFieldIsError() {
        GroupedRows g = collapse(full("G", "a.jpg", "Bogus.field", "x"));
        assertEquals(List.of(cell(0, "Bogus.field")),
            issueCells(g, BulkValidatorException.TYPE_UNKNOWN_FIELDNAME));
        assertTrue(g.warnings().isEmpty());
        assertTrue(g.hasGroupErrors());
        assertFalse(g.mergedRows().get(0).containsKey("Bogus.field"));
    }

    @Test void groupedIdCanonicalLowercased() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", UUID_UPPER),
            bare("G", "b.jpg", "Encounter.catalogNumber", ""));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(UUID_UPPER.toLowerCase(), mergedValue(g, 0, "Encounter.id"));
        // the same ID in two cases on two rows (and two alias spellings) agrees
        g = collapse(full("G", "a.jpg", "Encounter.id", UUID_UPPER),
            bare("G", "b.jpg", "Encounter.catalogNumber", UUID_UPPER.toLowerCase()));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(UUID_UPPER.toLowerCase(), mergedValue(g, 0, "Encounter.id"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.catalogNumber"));
    }

    @Test void abbreviatedGroupedIdRejected() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", "1-1-1-1-1"));
        assertEquals(List.of(cell(0, "Encounter.id")), issueCells(g, null));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.id"));
    }

    @Test void nonStringGroupKeyIsFatal() {
        GroupedRows g = collapse(full("G", "a.jpg"),
            obj(KEY, new JSONArray(), "Encounter.genus", "Genus", "Encounter.specificEpithet",
                "species", "Encounter.year", 2020, "Encounter.mediaAsset0", "b.jpg"),
            obj(KEY, 7, "Encounter.genus", "Genus", "Encounter.specificEpithet",
                "species", "Encounter.year", 2020, "Encounter.mediaAsset0", "c.jpg"));
        assertEquals(3, g.mergedRows().size());
        assertTrue(g.isGroupedRow(1));
        assertTrue(g.isGroupedRow(2));
        assertEquals(List.of(cell(1, KEY), cell(2, KEY)), issueCells(g, null));
        assertTrue(g.hasGroupErrors());
    }

    @Test void overlongGroupKeyIsFatal() {
        String max = "k".repeat(256);
        String over = "K".repeat(257);
        // a 256-character key groups as usual (trimmed first); 257 is invalid, on rows that
        // would otherwise share it: each is a one-row group of its own
        GroupedRows g = collapse(full("  " + max + "  ", "a.jpg"), bare(max, "b.jpg"),
            full(over, "c.jpg"), full(over.toLowerCase(), "d.jpg"));
        assertEquals(3, g.mergedRows().size());
        assertEquals(List.of(0, 1), g.membersOf(0));
        assertEquals(List.of(2), g.membersOf(1));
        assertEquals(List.of(3), g.membersOf(2));
        assertTrue(g.isGroupedRow(2));
        assertTrue(g.isGroupedRow(3));
        assertEquals(List.of(cell(2, KEY), cell(3, KEY)),
            issueCells(g, BulkValidatorException.TYPE_INVALID_VALUE));
        assertEquals(2, g.issues().size(), g.issues().toString());
        assertEquals("groupKey must be at most 256 characters", g.issues().get(0).error.getMessage());
        assertTrue(g.hasGroupErrors());
    }

    @Test void invalidKeyEchoIsClipped() {
        String huge = "k".repeat(10000);
        JSONArray arrayKey = new JSONArray().put("e".repeat(10000));
        // a bad photo cell on each row, so the row has issues besides its key
        GroupedRows g = collapse(full(huge, "a.jpg", "Encounter.sex", "x", "Encounter.year", "y"),
            full("ok", "b.jpg"), obj(KEY, arrayKey, "Encounter.genus", "Genus",
                "Encounter.specificEpithet", "species", "Encounter.year", "y",
                "Encounter.mediaAsset0", "c.jpg", "Encounter.sex", "x"));
        List<GroupedRows.Cell> keyIssues = new ArrayList<GroupedRows.Cell>();
        for (GroupedRows.Issue is : g.issues()) {
            if (is.field.equals(KEY)) keyIssues.add(cell(is.row, is.field));
        }
        assertEquals(List.of(cell(0, KEY), cell(2, KEY)), keyIssues);
        assertTrue(g.issues().size() > 2, g.issues().toString());
        for (GroupedRows.Issue is : g.issues()) {
            assertNotNull(is.groupKey);
            assertTrue(is.groupKey.length() <= 63, is.groupKey);
        }
        assertEquals("k".repeat(60) + "...", g.groupKeyAsTyped(0));
        assertTrue(g.groupKeyAsTyped(2).startsWith("[\"eee"), g.groupKeyAsTyped(2));
        assertTrue(g.groupKeyAsTyped(2).endsWith("..."), g.groupKeyAsTyped(2));
        assertEquals("ok", g.groupKeyAsTyped(1));
    }

    @Test void clippedKeyEchoKeepsSurrogatePairsWhole() {
        String face = "\uD83D\uDE00"; // one code point, two UTF-16 units
        // over 256 characters; a cut at 60 would split the pair at 59-60
        GroupedRows g = collapse(full("a".repeat(59) + face + "b".repeat(300), "a.jpg"),
            full("b".repeat(61) + "c".repeat(300), "b.jpg"));
        assertEquals("a".repeat(59) + "...", g.groupKeyAsTyped(0));
        assertEquals("b".repeat(60) + "...", g.groupKeyAsTyped(1));
    }

    @Test void remarksConflictMessageTellsHowToFix() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.researcherComments", "one"),
            bare("G", "b.jpg", "Encounter.researcherComments", "two"));
        assertEquals(2, g.issues().size());
        for (GroupedRows.Issue is : g.issues()) {
            assertTrue(is.error.isType(BulkValidatorException.TYPE_GROUP_CONFLICT));
            assertTrue(is.error.getMessage().endsWith(
                "Put encounter remarks on one row of the group, or make them identical."),
                is.error.getMessage());
        }
        g = collapse(full("G", "a.jpg", "Encounter.sex", "male"),
            bare("G", "b.jpg", "Encounter.sex", "female"));
        assertFalse(g.issues().get(0).error.getMessage().contains("remarks"));
    }

    @Test void provenanceIsImmutable() {
        GroupedRows g = collapse(full("G", "a.jpg", "Bogus.field", "x", "Other.field", ""),
            bare("G", "b.jpg"), obj("Encounter.genus", "Genus"));
        assertThrows(UnsupportedOperationException.class,
            () -> g.issues().add(g.issues().get(0)));
        assertThrows(UnsupportedOperationException.class,
            () -> g.warnings().add(g.warnings().get(0)));
        assertThrows(UnsupportedOperationException.class,
            () -> g.sourceValues(0).put("x", "y"));
        assertThrows(UnsupportedOperationException.class,
            () -> g.sourceValues(2).put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> g.membersOf(0).add(9));
        assertThrows(UnsupportedOperationException.class,
            () -> g.contributorsOf(0, "Encounter.genus").add(cell(1, "x")));
        assertThrows(UnsupportedOperationException.class,
            () -> g.cellsFor(0, "Encounter.sex").add(cell(1, "x")));
        assertThrows(UnsupportedOperationException.class, () -> g.groupedMergedIndexes().add(5));
        assertThrows(UnsupportedOperationException.class,
            () -> g.mergedRows().add(new HashMap<String, Object>()));
        // the merged maps themselves stay mutable for the servlet
        g.mergedRows().get(0).put("Encounter.submitterID", "owner");
        // mergedRaw hands out a copy
        g.mergedRaw(0).put("Encounter.genus", "Other");
        assertEquals("Genus", g.mergedRaw(0).get("Encounter.genus"));
    }

    @Test void sharedScalarEqualityCases()
    throws Exception {
        JSONObject fixture;
        try (InputStream in = getClass().getResourceAsStream(
                "/bulk-import/group-equality-cases.json")) {
            assertNotNull(in, "fixture on the test classpath");
            fixture = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(1, fixture.getInt("version"));
        int seen = 0;
        JSONArray cases = fixture.getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            if (!"scalar".equals(c.getString("kind"))) continue;
            seen++;
            String name = c.getString("name");
            assertEquals(BulkValidator.canonicalFieldName(c.getString("fieldA")),
                BulkValidator.canonicalFieldName(c.getString("fieldB")), name + ": one family");
            GroupedRows g = collapse(full("G", "a.jpg", c.getString("fieldA"), c.get("a")),
                bare("G", "b.jpg", c.getString("fieldB"), c.get("b")));
            List<GroupedRows.Cell> conflicts = issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT);
            assertEquals(g.issues().size(), conflicts.size(), name + ": " + g.issues());
            if (c.getBoolean("equal")) {
                assertTrue(conflicts.isEmpty(), name + ": " + g.issues());
            } else {
                assertEquals(List.of(cell(0, c.getString("fieldA")), cell(1, c.getString("fieldB"))),
                    conflicts, name);
            }
        }
        assertEquals(7, seen);
    }

    @Test void blankOutOfRangeHeaderRejected() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset200", ""));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset200")), issueCells(g, null));
        assertTrue(g.warnings().isEmpty());
        // the same as positional field names
        Set<String> fieldNames = new LinkedHashSet<String>(List.of(KEY, "Encounter.genus",
            "Encounter.specificEpithet", "Encounter.year", "Encounter.mediaAsset0",
            "Encounter.mediaAsset200"));
        JSONObject row = BulkImportUtil.rowFromPositional(fieldNames,
            new JSONArray(List.of("G", "Genus", "species", 2020, "a.jpg")));
        assertEquals(JSONObject.NULL, row.get("Encounter.mediaAsset200"));
        g = collapse(row);
        assertEquals(List.of(cell(0, "Encounter.mediaAsset200")), issueCells(g, null));
    }

    @Test void blankUnknownHeaderIsWarning() {
        GroupedRows g = collapse(full("G", "a.jpg", "Bogus.field", ""));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(1, g.warnings().size());
        GroupedRows.Issue w = g.warnings().get(0);
        assertEquals(cell(0, "Bogus.field"), cell(w.row, w.field));
        assertTrue(w.error.isType(BulkValidatorException.TYPE_UNKNOWN_FIELDNAME));
        assertEquals("G", w.groupKey);
        assertFalse(g.hasGroupErrors());
        g = collapse(full("G", "a.jpg", "Bogus.field", "x"));
        assertEquals(1, g.issues().size());
        assertTrue(g.warnings().isEmpty());
    }

    @Test void dependentFailureStaysMergedOnce() {
        when(sh.isValidTaxonomyName(anyString())).thenReturn(false);
        GroupedRows g = collapse(bare("G", "a.jpg", "Encounter.genus", "Genus", "Encounter.year", 2020),
            bare("G", "b.jpg", "Encounter.specificEpithet", "species"));
        assertNotNull(mergedError(g, 0, "Encounter.genus"));
        assertNotNull(mergedError(g, 0, "Encounter.specificEpithet"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertTrue(g.hasGroupErrors());
        assertEquals(List.of(cell(0, "Encounter.genus")), g.cellsFor(0, "Encounter.genus"));
    }

    @Test void poisonedFamilyAddsNoRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.sex", "male"),
            bare("G", "b.jpg", "Encounter.sex", "female"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.sex"));
        assertNoMergedErrors(g, 0);
        // a conflicting year: the conflict is the only report, no REQUIRED on the year
        g = collapse(full("G", "a.jpg"), bare("G", "b.jpg", "Sighting.year", 2021));
        assertEquals(List.of(cell(0, "Encounter.year"), cell(1, "Sighting.year")), issueCells(g, null));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.year"));
        assertNoMergedErrors(g, 0);
        // an invalid year likewise
        g = collapse(bare("G", "a.jpg", "Encounter.genus", "Genus", "Encounter.specificEpithet",
            "species", "Encounter.year", 12));
        assertEquals(List.of(cell(0, "Encounter.year")), issueCells(g, null));
        assertNoMergedErrors(g, 0);
        // a conflicting genus: no REQUIRED on genus
        g = collapse(full("G", "a.jpg"), bare("G", "b.jpg", "Encounter.genus", "Other"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.genus"));
        assertNoMergedErrors(g, 0);
    }

    @Test void sourceRowKeysNotMutated() {
        JSONObject[] rows = {
            full("G", "a.jpg", "Sighting.year", 2021, "Encounter.sex", "", "Bogus.field", ""),
            bare("G", "b.jpg", "Encounter.catalogNumber", UUID_UPPER, "Encounter.id", ""),
            obj("Encounter.year", 2020, "Sighting.year", 2021, "Encounter.genus", "Genus")
        };
        List<Set<String> > before = new ArrayList<Set<String> >();
        List<String> text = new ArrayList<String>();
        for (JSONObject r : rows) {
            before.add(new HashSet<String>(r.keySet()));
            text.add(r.toString());
        }
        collapse(rows);
        for (int i = 0; i < rows.length; i++) {
            assertEquals(before.get(i), rows[i].keySet(), "row " + i);
            assertEquals(new JSONObject(text.get(i)).toMap(), rows[i].toMap(), "row " + i);
        }
    }

    // IA.json where "body" is a matchable class of Genus species
    static void boxClasses() {
        JSONObject idConf = new JSONObject().put("_id_conf",
            new JSONArray().put(new JSONObject().put("default", true)));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(new JSONObject().put("Genus", new JSONObject().put("species",
            new JSONObject().put("body", idConf))));
        TestIAJsonPropertiesUtil.override(iac);
    }

    // every failure of the collapse: issues plus exceptions left in the merged rows
    static int failureCount(GroupedRows g) {
        int n = g.issues().size();
        for (Map<String, Object> m : g.mergedRows()) {
            for (Object o : m.values()) {
                if (o instanceof Exception) n++;
            }
        }
        return n;
    }

    // an ungrouped row with a blank .bbox column: the upload is in exact-slot mode
    static JSONObject blankBboxRow() {
        return obj("Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 2020, "Encounter.mediaAsset0", "z.jpg", "Encounter.mediaAsset0.bbox", "");
    }

    @Test void photosPackInSheetOrderWithOwnMetadata() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.keyword0", "k1"),
            bare("G", "b.jpg", "Encounter.keyword0", "k2"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        assertFalse(g.exactSlotMode());
        assertEquals("a.jpg", mergedValue(g, 0, "Encounter.mediaAsset0"));
        assertEquals("k1", mergedValue(g, 0, "Encounter.keyword0"));
        assertEquals("b.jpg", mergedValue(g, 0, "Encounter.mediaAsset1"));
        assertEquals("k2", mergedValue(g, 0, "Encounter.keyword1"));
        BulkValidator bv = (BulkValidator)g.mergedRows().get(0).get("Encounter.mediaAsset1");
        assertEquals("Encounter.mediaAsset1", bv.getFieldName());
        assertEquals(1, bv.getIndexInt());
        assertEquals("Encounter.keyword1",
            ((BulkValidator)g.mergedRows().get(0).get("Encounter.keyword1")).getFieldName());
        assertEquals(List.of(cell(1, "Encounter.keyword0")), g.contributorsOf(0, "Encounter.keyword1"));
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0")),
            g.contributorsOf(0, "Encounter.mediaAsset1"));
        assertEquals(List.of(cell(0, "Encounter.keyword0")), g.contributorsOf(0, "Encounter.keyword0"));
        assertEquals("b.jpg", g.mergedRaw(0).get("Encounter.mediaAsset1"));
    }

    @Test void exactSlotModeFollowsIndexNotOrdinal() {
        GroupedRows g = collapse(obj(KEY, "G", "Encounter.genus", "Genus", "Encounter.specificEpithet",
            "species", "Encounter.year", 2020, "Encounter.mediaAsset2", "a.jpg", "Encounter.keyword2", "k"),
            blankBboxRow());
        assertTrue(g.exactSlotMode());
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals("a.jpg", mergedValue(g, 0, "Encounter.mediaAsset0"));
        assertEquals("k", mergedValue(g, 0, "Encounter.keyword0"));
        assertEquals(List.of(cell(0, "Encounter.keyword2")), g.contributorsOf(0, "Encounter.keyword0"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset2"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.keyword2"));
    }

    @Test void blankBboxColumnStillSelectsExactSlotMode() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset0.bbox", ""));
        assertTrue(g.exactSlotMode());
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset0.bbox"));
    }

    @Test void positionalModeFollowsOrdinal() {
        JSONObject row = obj(KEY, "G", "Encounter.genus", "Genus", "Encounter.specificEpithet",
            "species", "Encounter.year", 2020, "Encounter.mediaAsset2", "a.jpg", "Encounter.keyword0", "k");
        GroupedRows g = collapse(row);
        assertFalse(g.exactSlotMode());
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals("k", mergedValue(g, 0, "Encounter.keyword0"));
        assertEquals(List.of(cell(0, "Encounter.keyword0")), g.contributorsOf(0, "Encounter.keyword0"));
        // the same row in exact-slot mode: keyword0 has no photo in slot 0
        g = collapse(row, blankBboxRow());
        assertEquals(List.of(cell(0, "Encounter.keyword0")), issueCells(g, null));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.keyword0"));
    }

    @Test void boxStaysWithItsPhoto() {
        boxClasses();
        GroupedRows g = collapse(full("G", "a.jpg"), bare("G", "b.jpg",
            "Encounter.mediaAsset0.bbox", "1,2,3,4", "Encounter.mediaAsset0.iaClass", "body"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        Map<String, Object> m = g.mergedRows().get(0);
        assertFalse(m.containsKey("Encounter.mediaAsset0.bbox"));
        assertTrue(m.containsKey("Encounter.mediaAsset1.bbox"));
        assertEquals("body", mergedValue(g, 0, "Encounter.mediaAsset1.iaClass"));
        assertEquals("Encounter.mediaAsset1.bbox",
            ((BulkValidator)m.get("Encounter.mediaAsset1.bbox")).getFieldName());
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0.bbox")),
            g.contributorsOf(0, "Encounter.mediaAsset1.bbox"));
    }

    @Test void failedPhotoFieldBlocksOnlyItsSlot() {
        boxClasses();
        GroupedRows g = collapse(
            full("G", "a.jpg", "Encounter.mediaAsset0.bbox", "1,2,3,4", "Encounter.mediaAsset0.iaClass", "body"),
            bare("G", "b.jpg", "Encounter.mediaAsset0.bbox", "nonsense",
                "Encounter.mediaAsset0.iaClass", "body"));
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0.bbox")), issueCells(g, null));
        assertEquals(1, failureCount(g), g.mergedRows().toString());
        Map<String, Object> m = g.mergedRows().get(0);
        assertTrue(m.get("Encounter.mediaAsset0.bbox") instanceof BulkValidator);
        assertFalse(m.containsKey("Encounter.mediaAsset1.bbox"));
        assertEquals("b.jpg", mergedValue(g, 0, "Encounter.mediaAsset1"));
    }

    @Test void labeledKeywordInPositionalModeRejected() {
        BulkImportUtil.labeledKeywordMap.put("colour", new LinkedHashSet<String>(List.of("red")));
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset0.colour", "red"),
            bare("G", "b.jpg", "Encounter.mediaAsset0.colour", ""));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset0.colour")), issueCells(g, null));
        assertEquals("labeled keywords need a .bbox column", g.issues().get(0).error.getMessage());
        assertTrue(g.issues().get(0).error.isType(BulkValidatorException.TYPE_INVALID_VALUE));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset0.colour"));
        assertNoMergedErrors(g, 0);
        // in exact-slot mode the labeled keyword follows its photo
        g = collapse(full("G", "a.jpg"), bare("G", "b.jpg", "Encounter.mediaAsset0.colour", "red"),
            blankBboxRow());
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals("red", mergedValue(g, 0, "Encounter.mediaAsset1.colour"));
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0.colour")),
            g.contributorsOf(0, "Encounter.mediaAsset1.colour"));
    }

    @Test void labeledKeywordOffsetBeyondMaxRejected() {
        BulkImportUtil.labeledKeywordMap.put("colour", new LinkedHashSet<String>(List.of("red")));
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset250.colour", ""), blankBboxRow());
        assertEquals(List.of(cell(0, "Encounter.mediaAsset250.colour")), issueCells(g, null));
        assertTrue(g.issues().get(0).error.isType(BulkValidatorException.TYPE_INVALID_VALUE));
        g = collapse(full("G", "a.jpg", "Encounter.mediaAsset250.colour", "red"));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset250.colour")), issueCells(g, null));
        assertEquals(1, failureCount(g));
    }

    @Test void labeledKeywordIndexOverflowingIntRejected() {
        BulkImportUtil.labeledKeywordMap.put("colour", new LinkedHashSet<String>(List.of("red")));
        String col = "Encounter.mediaAsset99999999999.colour";
        // a blank cell under a configured label: an invalid index (fatal), not an unknown header
        GroupedRows g = collapse(full("G", "a.jpg", col, ""), blankBboxRow());
        assertEquals(List.of(cell(0, col)), issueCells(g, BulkValidatorException.TYPE_INVALID_VALUE));
        assertTrue(g.warnings().isEmpty(), g.warnings().toString());
        assertTrue(GroupedErrors.collect(g, true).fatal());
        // an unknown label under the same index is still an unknown (blank, so tolerated) header
        g = collapse(full("G", "a.jpg", "Encounter.mediaAsset99999999999.shade", ""), blankBboxRow());
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(1, g.warnings().size(), g.warnings().toString());
    }

    @Test void yearBeyondJavaTimeIsACellError() {
        // split across a group: the year cell is blamed, nothing throws
        GroupedRows g = collapse(obj(KEY, "G", "Encounter.genus", "Genus",
            "Encounter.specificEpithet", "species", "Encounter.year", 1000000000,
            "Encounter.mediaAsset0", "a.jpg"), bare("G", "b.jpg", "Encounter.month", 1),
            bare("G", "c.jpg", "Encounter.day", 1));
        assertEquals("year value too large", mergedError(g, 0, "Encounter.year").getMessage());
        assertEquals(List.of(cell(0, "Encounter.year")), g.cellsFor(0, "Encounter.year"));
        assertEquals(1, failureCount(g), g.mergedRows().toString());
        GroupedErrors ge = GroupedErrors.collect(g, true);
        assertTrue(ge.fatal());
        assertEquals(1, ge.errors().size(), ge.errors().toString());
        assertEquals("Encounter.year", ge.errors().get(0).getString("fieldName"));
        // shared fix: an ungrouped row with the same date was a 500, now the same cell error
        g = collapse(obj("Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 1000000000, "Encounter.month", 1, "Encounter.day", 1));
        assertEquals("year value too large", mergedError(g, 0, "Encounter.year").getMessage());
        assertEquals(1, failureCount(g), g.mergedRows().toString());
        // without month and day it stays today's "date is in the future"
        g = collapse(obj("Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 1000000000));
        assertEquals("date is in the future", mergedError(g, 0, "Encounter.year").getMessage());
    }

    @Test void rowWithoutPhotoIsError() {
        GroupedRows g = collapse(full("G", "a.jpg"), obj(KEY, "G", "Encounter.mediaAsset0", ""),
            obj(KEY, "G", "Encounter.sex", "male"));
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0"), cell(2, "Encounter.mediaAsset0")),
            issueCells(g, BulkValidatorException.TYPE_REQUIRED_VALUE));
        assertEquals(2, g.issues().size());
        assertEquals("G", g.issues().get(0).groupKey);
        assertTrue(g.hasGroupErrors());
    }

    @Test void repeatedFilenameInGroupIsError() {
        GroupedRows g = collapse(full("G", "a.jpg"), bare("G", "a.jpg", "Encounter.keyword0", "k"));
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_DUPLICATE));
        assertEquals(1, g.issues().size());
        String msg = g.issues().get(0).error.getMessage();
        assertTrue(msg.contains("row 0") && msg.contains("Encounter.mediaAsset0"), msg);
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset1"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.keyword0"));
        // within one row too
        g = collapse(full("G", "a.jpg", "Encounter.mediaAsset1", "a.jpg"));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset1")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_DUPLICATE));
        assertEquals(1, failureCount(g));
    }

    @Test void repeatedBoxedPhotoReportedOnce() {
        boxClasses();
        GroupedRows g = collapse(
            full("G", "a.jpg", "Encounter.mediaAsset0.bbox", "1,2,3,4", "Encounter.mediaAsset0.iaClass", "body"),
            bare("G", "a.jpg", "Encounter.mediaAsset0.bbox", "5,6,7,8", "Encounter.mediaAsset0.iaClass", "body"));
        assertEquals(1, failureCount(g), g.issues() + " " + g.mergedRows());
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_DUPLICATE));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset1"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset1.bbox"));
        // the same photo boxed twice on one row
        g = collapse(full("G", "a.jpg", "Encounter.mediaAsset0.bbox", "1,2,3,4",
            "Encounter.mediaAsset0.iaClass", "body", "Encounter.mediaAsset1", "a.jpg",
            "Encounter.mediaAsset1.bbox", "5,6,7,8", "Encounter.mediaAsset1.iaClass", "body"));
        assertEquals(1, failureCount(g), g.issues() + " " + g.mergedRows());
        assertEquals(List.of(cell(0, "Encounter.mediaAsset1")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_DUPLICATE));
    }

    @Test void photoCapCheckedBeforePacking() {
        GroupedRows g = GroupCollapse.collapse(Arrays.asList(
            full("G", "a.jpg", "Encounter.mediaAsset1", "b.jpg"),
            bare("G", "c.jpg", "Encounter.mediaAsset1", "d.jpg", "Encounter.keyword1", "k")),
            sh, false, 3);
        assertEquals(List.of(cell(1, "Encounter.mediaAsset1")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(1, g.issues().size());
        Map<String, Object> m = g.mergedRows().get(0);
        assertEquals("c.jpg", mergedValue(g, 0, "Encounter.mediaAsset2"));
        assertFalse(m.containsKey("Encounter.mediaAsset3"));
        assertFalse(m.containsKey("Encounter.keyword3"));
        assertFalse(m.containsKey("Encounter.keyword1"));
    }

    @Test void orphanBoxMetadataRejected() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset1.bbox", "1,2,3,4",
            "Encounter.mediaAsset1.iaClass", "body"), bare("G", "b.jpg", "Encounter.keyword1", "k"));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset1.bbox"), cell(0, "Encounter.mediaAsset1.iaClass"),
            cell(1, "Encounter.keyword1")), issueCells(g, BulkValidatorException.TYPE_INVALID_VALUE));
        assertEquals(3, failureCount(g), g.mergedRows().toString());
        Map<String, Object> m = g.mergedRows().get(0);
        assertFalse(m.containsKey("Encounter.mediaAsset1.bbox"));
        assertFalse(m.containsKey("Encounter.mediaAsset0.bbox"));
        // positional orphans: more metadata than photos on the row
        g = collapse(full("G", "a.jpg", "Encounter.quality1", 0.5, "Encounter.mediaAsset1.keywords", "x"));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset1.keywords"), cell(0, "Encounter.quality1")),
            issueCells(g, null));
        // a box column on a positional row with no photo in its slot
        g = collapse(full("G", "a.jpg", "Encounter.mediaAsset1.viewpoint", "left"));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset1.viewpoint")), issueCells(g, null));
        assertEquals(1, failureCount(g));
    }

    @Test void indexAliasInGroupedObjectRowRejected() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset01", "b.jpg",
            "Encounter.mediaAsset1", "c.jpg"));
        assertEquals(List.of(cell(0, "Encounter.mediaAsset1")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_DUPLICATE));
        assertEquals(1, g.issues().size());
        assertEquals("b.jpg", mergedValue(g, 0, "Encounter.mediaAsset1"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset2"));
    }

    @Test void filenameKeptVerbatim() {
        GroupedRows g = collapse(full("G", " a.jpg"), bare("G", "b.jpg "));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(" a.jpg", mergedValue(g, 0, "Encounter.mediaAsset0"));
        assertEquals("b.jpg ", mergedValue(g, 0, "Encounter.mediaAsset1"));
        assertEquals(Set.of(" a.jpg", "b.jpg "), g.groupedFilenames());
    }

    @Test void groupedFilenamesListed() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset1", "b.jpg",
            "Encounter.mediaAsset2", ""), bare("g", "c.jpg"),
            obj("Encounter.mediaAsset0", "d.jpg", "Encounter.genus", "Genus"));
        assertEquals(Set.of("a.jpg", "b.jpg", "c.jpg"), g.groupedFilenames());
        assertFalse(g.exactSlotMode());
    }

    @Test void groupedFileFailureInvalidatesEveryGroupedRowUsingIt() {
        GroupedRows g = collapse(full("G", "a.jpg"), bare("G", "b.jpg"),
            full("H", "x.jpg", "Encounter.mediaAsset7", "a.jpg"),
            obj("Encounter.mediaAsset0", "a.jpg", "Encounter.genus", "Genus",
                "Encounter.specificEpithet", "species", "Encounter.year", 2020),
            obj("Encounter.mediaAsset0", "d.jpg", "Encounter.genus", "Genus",
                "Encounter.specificEpithet", "species", "Encounter.year", 2020));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals(Set.of(0, 2), g.sourceRowsUsing("a.jpg"), "ungrouped row 3 is not listed");
        assertEquals(Set.of(), g.sourceRowsUsing("d.jpg"));
        GroupedErrors ge = GroupedErrors.collect(g, false);
        assertEquals(5, ge.numberRowsValid());
        ge.fileFailed("d.jpg"); // an ungrouped file: nothing here
        assertFalse(ge.fatal());
        assertEquals(5, ge.numberRowsValid());
        ge.fileFailed("a.jpg");
        assertTrue(ge.fatal());
        assertEquals(3, ge.numberRowsValid());
    }

    @Test void sourceTextNamesSourceSlot() {
        GroupedRows g = collapse(full("G", "a.jpg"), obj(KEY, "G", "Encounter.mediaAsset7", "b.jpg",
            "Encounter.mediaAsset7.iaClass", "body"), obj("Encounter.mediaAsset0", "d.jpg"));
        assertEquals("in Encounter.mediaAsset7.iaClass", g.sourceText(0, "in Encounter.mediaAsset1.iaClass"));
        assertEquals("Encounter.mediaAsset7 and Encounter.mediaAsset0",
            g.sourceText(0, "Encounter.mediaAsset1 and Encounter.mediaAsset0"));
        // a packed field with no contributor maps through its photo; an unknown slot is unchanged
        assertEquals("in Encounter.mediaAsset7.bbox", g.sourceText(0, "in Encounter.mediaAsset1.bbox"));
        assertEquals("Encounter.mediaAsset9.bbox", g.sourceText(0, "Encounter.mediaAsset9.bbox"));
        // the importer's box message on the merged row names the source columns
        BulkValidatorException ex = mergedError(g, 0, "Encounter.mediaAsset1.iaClass");
        assertNotNull(ex, g.mergedRows().toString());
        assertEquals(List.of(cell(1, "Encounter.mediaAsset7.iaClass")),
            g.cellsFor(0, "Encounter.mediaAsset1.iaClass"));
        assertEquals("Encounter.mediaAsset7.iaClass needs a box in Encounter.mediaAsset7.bbox",
            g.sourceText(0, ex.getMessage()));
        // an ungrouped row is unchanged
        assertEquals("in Encounter.mediaAsset1.iaClass", g.sourceText(1, "in Encounter.mediaAsset1.iaClass"));
    }

    @Test void positionalSparseOrdinal() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.mediaAsset3", "b.jpg",
            "Encounter.keyword1", "k"));
        assertFalse(g.exactSlotMode());
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        // keyword1 is the second occupied photo's: b.jpg in source slot 3, packed slot 1
        assertEquals("b.jpg", mergedValue(g, 0, "Encounter.mediaAsset1"));
        assertEquals("k", mergedValue(g, 0, "Encounter.keyword1"));
        assertEquals(List.of(cell(0, "Encounter.keyword1")), g.contributorsOf(0, "Encounter.keyword1"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.keyword0"));
    }

    @Test void perPhotoKeywordsAndQualityPacked() {
        GroupedRows g = collapse(full("G", "a.jpg"), bare("G", "b.jpg",
            "Encounter.mediaAsset0.keywords", "x_y", "Encounter.quality0", 0.5));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        assertEquals("x_y", mergedValue(g, 0, "Encounter.mediaAsset1.keywords"));
        assertEquals(0.5, mergedValue(g, 0, "Encounter.quality1"));
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0.keywords")),
            g.contributorsOf(0, "Encounter.mediaAsset1.keywords"));
        assertEquals(List.of(cell(1, "Encounter.quality0")), g.contributorsOf(0, "Encounter.quality1"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.quality0"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset0.keywords"));
    }

    @Test void repeatConsumesNoSlot() {
        GroupedRows g = GroupCollapse.collapse(Arrays.asList(full("G", "a.jpg"),
            bare("G", "a.jpg"), bare("G", "b.jpg")), sh, false, 2);
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0")), issueCells(g, null));
        assertTrue(g.issues().get(0).error.isType(BulkValidatorException.TYPE_INVALID_DUPLICATE));
        assertEquals("b.jpg", mergedValue(g, 0, "Encounter.mediaAsset1"));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.mediaAsset2"));
        // a photo over the cap is not packed, so a later copy of it is over the cap too, not a repeat
        g = GroupCollapse.collapse(Arrays.asList(full("G", "a.jpg"), bare("G", "b.jpg"),
            bare("G", "b.jpg")), sh, false, 1);
        assertEquals(List.of(cell(1, "Encounter.mediaAsset0"), cell(2, "Encounter.mediaAsset0")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(2, g.issues().size());
    }

    // ---- Task 5: compound encounter data (C4), completeness, ignored fields

    static final String P0 = "Encounter.project0.projectIdPrefix";
    static final String N0 = "Encounter.project0.researchProjectName";
    static final String O0 = "Encounter.project0.ownerUsername";
    static final String MS = "MicrosatelliteMarkersAnalysis.";
    static final String NAMES = MS + "alleleNames";
    static final String A0 = MS + "alleles0";
    static final String A1 = MS + "alleles1";

    // every project prefix resolves to an existing project
    void projectsExist() {
        when(sh.getProjectByProjectIdPrefix(anyString())).thenReturn(mock(Project.class));
    }

    static JSONObject withCells(JSONObject row, JSONObject cells) {
        for (String k : cells.keySet()) row.put(k, cells.get(k));
        return row;
    }

    // a grouped row with a sample ID and the three genotype cells
    static JSONObject geno(String photo, String names, String a0, String a1) {
        return full("G", photo, "TissueSample.sampleID", "S1", NAMES, names, A0, a0, A1, a1);
    }

    static List<GroupedRows.Cell> genoCells(int row) {
        return List.of(cell(row, NAMES), cell(row, A0), cell(row, A1));
    }

    static void assertOnly(GroupedRows g, String type, List<GroupedRows.Cell> cells) {
        assertEquals(cells, issueCells(g, null), g.issues().toString());
        assertEquals(cells, issueCells(g, type), g.issues().toString());
        assertEquals(cells.size(), failureCount(g), g.mergedRows().toString());
    }

    @Test void identicalCompoundSetRepeatedIsFine() {
        projectsExist();
        Object[] set = { P0, "P", N0, "Proj", "Encounter.measurement0", 2.5 };
        GroupedRows g = collapse(full("G", "a.jpg", set), bare("G", "b.jpg", set),
            bare("G", "c.jpg", "Encounter.measurement0", "2.5", P0, "P", N0, "Proj"),
            bare("G", "d.jpg"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        assertEquals("P", mergedValue(g, 0, P0));
        assertEquals("Proj", mergedValue(g, 0, N0));
        assertEquals(2.5, mergedValue(g, 0, "Encounter.measurement0"));
        assertEquals(List.of(cell(0, P0), cell(1, P0), cell(2, P0)), g.contributorsOf(0, P0));
    }

    @Test void differentCompoundSetsConflict() {
        projectsExist();
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj"),
            bare("G", "b.jpg", "Encounter.measurement0", 2.5));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT,
            List.of(cell(0, P0), cell(0, N0), cell(1, "Encounter.measurement0")));
        assertFalse(g.mergedRows().get(0).containsKey(P0));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.measurement0"));
        assertTrue(g.hasGroupErrors());
    }

    @Test void measurementSpellingsAreDifferentColumns() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.measurement0", 2.5),
            bare("G", "b.jpg", "Encounter.measurement.length", 2.5));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT,
            List.of(cell(0, "Encounter.measurement0"), cell(1, "Encounter.measurement.length")));
    }

    @Test void differingProjectsConflict() {
        projectsExist();
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P1", N0, "Proj"),
            bare("G", "b.jpg", P0, "P2", N0, "Proj"));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT,
            List.of(cell(0, P0), cell(0, N0), cell(1, P0), cell(1, N0)));
        assertTrue(g.issues().get(0).error.getMessage().contains(P0), g.issues().toString());
        assertFalse(g.issues().get(0).error.getMessage().contains(N0), g.issues().toString());
    }

    @Test void partialRepeatConflicts() {
        projectsExist();
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj"),
            bare("G", "b.jpg", P0, "P"));
        // the conflict is the only report: no completeness check on a set that is not agreed
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT,
            List.of(cell(0, P0), cell(0, N0), cell(1, P0)));
    }

    @Test void nameLabelWithoutValueRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.individualID", "ind1",
            "MarkedIndividual.name0.label", "alias"),
            bare("G", "b.jpg", "MarkedIndividual.name0.label", "alias"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "MarkedIndividual.name0.value"), cell(1, "MarkedIndividual.name0.value")));
        g = collapse(full("G", "a.jpg", "Encounter.individualID", "ind1",
            "MarkedIndividual.name03.value", "X"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "MarkedIndividual.name03.label")));
        g = collapse(full("G", "a.jpg", "Encounter.individualID", "ind1",
            "MarkedIndividual.name0.label", "alias", "MarkedIndividual.name0.value", "X"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void submitterNameWithoutEmailRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.submitter0.fullName", "Ann"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "Encounter.submitter0.emailAddress")));
        g = collapse(full("G", "a.jpg", "Encounter.submitter1.affiliation", "Org"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "Encounter.submitter1.emailAddress")));
        g = collapse(full("G", "a.jpg", "Encounter.submitter0.fullName", "Ann",
            "Encounter.submitter0.emailAddress", "ann@example.org"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void projectOwnerWithoutProjectRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", O0, "bob"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(0, P0), cell(0, N0)));
        // a prefix without a name, a name without a prefix
        projectsExist();
        g = collapse(full("G", "a.jpg", P0, "P"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(0, N0)));
        g = collapse(full("G", "a.jpg", N0, "Proj"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(0, P0)));
    }

    @Test void loneZeroPaddedPrefixPairedByIndex() {
        projectsExist();
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.project00.projectIdPrefix", "P"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "Encounter.project00.researchProjectName")));
        // differently padded partners pair by index, as the importer pairs them
        g = collapse(full("G", "a.jpg", "Encounter.project00.projectIdPrefix", "P", N0, "Proj"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void newProjectWithoutOwnerRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(0, O0)));
        g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj", O0, "bob"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void existingProjectWithoutOwnerIsFine() {
        when(sh.getProjectByProjectIdPrefix("P")).thenReturn(mock(Project.class));
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        // the importer's fuzzy fallback finds it too
        when(sh.getProjectByProjectIdPrefixPrefix("Q-")).thenReturn(mock(Project.class));
        g = collapse(full("G", "a.jpg", P0, "Q-", N0, "Proj"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
    }

    @Test void conflictingDefinitionsForOnePrefixRejected() {
        projectsExist();
        String p1 = "Encounter.project1.projectIdPrefix";
        String n1 = "Encounter.project1.researchProjectName";
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj A", p1, "P", n1, "Proj B"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, List.of(cell(0, n1)));
        g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj A", O0, "ann", p1, "P", n1, "Proj A",
            "Encounter.project1.ownerUsername", "bob"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE,
            List.of(cell(0, "Encounter.project1.ownerUsername")));
        g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj A", p1, "P", n1, "Proj A"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
    }

    @Test void sameEmailWithDifferentSubmitterDetailsRejected() {
        String e0 = "Encounter.submitter0.emailAddress";
        String e1 = "Encounter.submitter1.emailAddress";
        String f1 = "Encounter.submitter1.fullName";
        // the importer creates one user per address, from the first slot only
        GroupedRows g = collapse(full("G", "a.jpg", e0, "ann@example.org",
            "Encounter.submitter0.fullName", "Ann", e1, "ann@example.org", f1, "Anne"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, List.of(cell(0, f1)));
        assertEquals("submitter ann@example.org already has the full name Ann in this group",
            g.issues().get(0).error.getMessage());
        // compared with the first slot that gives a value, not the first slot
        g = collapse(full("G", "a.jpg", e0, "ann@example.org", e1, "ann@example.org",
            "Encounter.submitter1.affiliation", "Org A", "Encounter.submitter2.emailAddress",
            "ann@example.org", "Encounter.submitter2.affiliation", "Org B"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE,
            List.of(cell(0, "Encounter.submitter2.affiliation")));
        // the same details, or another address, are fine
        g = collapse(full("G", "a.jpg", e0, "ann@example.org", "Encounter.submitter0.fullName",
            "Ann", e1, "ann@example.org", f1, "Ann", "Encounter.submitter2.emailAddress",
            "bob@example.org", "Encounter.submitter2.fullName", "Bob"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void prefixOwnersComparedWithFirstGivenOwner() {
        projectsExist();
        String o1 = "Encounter.project1.ownerUsername";
        String o2 = "Encounter.project2.ownerUsername";
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj",
            "Encounter.project1.projectIdPrefix", "P", "Encounter.project1.researchProjectName",
            "Proj", o1, "alice", "Encounter.project2.projectIdPrefix", "P",
            "Encounter.project2.researchProjectName", "Proj", o2, "bob"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, List.of(cell(0, o2)));
        assertEquals("project P already has the owner alice in this group",
            g.issues().get(0).error.getMessage());
    }

    @Test void projectLookupFailureIsReportedOnThePrefix() {
        when(sh.getProjectByProjectIdPrefix(anyString())).thenThrow(new RuntimeException("db down"));
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj"),
            bare("G", "b.jpg", P0, "P", N0, "Proj"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, List.of(cell(0, P0), cell(1, P0)));
        assertEquals("Could not check whether this project exists",
            g.issues().get(0).error.getMessage());
        // an owner means no lookup is needed
        g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj", O0, "bob"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
    }

    @Test void compoundConflictBlamesEveryRow() {
        projectsExist();
        Object[] a = { P0, "P", N0, "A" };
        Object[] b = { P0, "P", N0, "B" };
        List<GroupedRows.Cell> all = List.of(cell(0, P0), cell(0, N0), cell(1, P0), cell(1, N0),
            cell(2, P0), cell(2, N0));
        GroupedRows g = collapse(full("G", "a.jpg", a), bare("G", "b.jpg", a), bare("G", "c.jpg", b));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT, all);
        assertTrue(g.issues().get(0).error.getMessage().contains("rows 0, 1, 2"),
            g.issues().get(0).error.getMessage());
        g = collapse(full("G", "a.jpg", b), bare("G", "b.jpg", a), bare("G", "c.jpg", a));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT, all);
    }

    @Test void protocolWithoutMeasurementRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.measurement0.samplingProtocol", "x"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "Encounter.measurement0")));
        g = collapse(full("G", "a.jpg", "Encounter.length.samplingProtocol", "x"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(0, "Encounter.length")));
        g = collapse(full("G", "a.jpg", "Encounter.length.samplingProtocol", "x",
            "Encounter.measurement0", 3));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void sampleFieldsNeedSampleId() {
        for (String f : List.of("TissueSample.tissueType", "SexAnalysis.sex",
            "MitochondrialDNAAnalysis.haplotype")) {
            GroupedRows g = collapse(full("G", "a.jpg", f, "v"), bare("G", "b.jpg", f, "v"));
            assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
                List.of(cell(0, "TissueSample.sampleID"), cell(1, "TissueSample.sampleID")));
        }
        GroupedRows g = collapse(full("G", "a.jpg", "SexAnalysis.processingLabTaskID", "S1",
            "SexAnalysis.sex", "male"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void thirdAlleleColumnRejected() {
        GroupedRows g = collapse(full("G", "a.jpg", "TissueSample.sampleID", "S1", MS + "alleles2", "1"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, List.of(cell(0, MS + "alleles2")));
    }

    @Test void genotypeListsValidatedTogether() {
        String[][] bad = { { "A,B", "1,x", "1,2" }, { "A,B", "1", "1,2" }, { "A,,B", "1,2,3", "1,2,3" },
            { "A,B,", "1,2,", "3,4," }, { "A,B", "1,2,", "3,4" } };
        for (String[] c : bad) {
            GroupedRows g = collapse(geno("a.jpg", c[0], c[1], c[2]));
            assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, genoCells(0));
            assertFalse(g.mergedRows().get(0).containsKey(A0));
        }
        // the three are given together
        GroupedRows g = collapse(full("G", "a.jpg", "TissueSample.sampleID", "S1", NAMES, "A", A0, "1"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(0, A1)));
        g = collapse(geno("a.jpg", "A,B", "1,2", "3,4"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void genotypeBoundCanonically() {
        GroupedRows g = collapse(geno("a.jpg", " A ,B", "1, 2", " 07,3"),
            geno("b.jpg", " A ,B", "1, 2", " 07,3"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        assertEquals("A,B", mergedValue(g, 0, NAMES));
        assertEquals("1,2", mergedValue(g, 0, A0));
        assertEquals("7,3", mergedValue(g, 0, A1));
        assertEquals(A0, ((BulkValidator)g.mergedRows().get(0).get(A0)).getFieldName());
        assertEquals(List.of(cell(0, A0), cell(1, A0)), g.contributorsOf(0, A0));
        // the source values stay as typed
        assertEquals("1, 2", g.sourceValues(0).get(A0));
    }

    @Test void alleleTokensMustBeIntegers() {
        for (String t : List.of("1.5", "1e2", "x", "2147483648")) {
            GroupedRows g = collapse(geno("a.jpg", "A", t, "1"));
            assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, genoCells(0));
        }
    }

    @Test void sharedAlleleTokenCases()
    throws Exception {
        JSONArray cases = fixtureCases();
        int seen = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            if (!"alleleToken".equals(c.getString("kind"))) continue;
            seen++;
            String name = c.getString("name");
            // a second locus, so an empty token is a cell that is not blank
            GroupedRows g = collapse(geno("a.jpg", "A,B", "1," + c.getString("token"), "1,2"));
            if (c.getBoolean("valid")) {
                assertTrue(g.issues().isEmpty(), name + ": " + g.issues());
                assertEquals("1," + c.getString("canonical"), mergedValue(g, 0, A0), name);
            } else {
                assertEquals(genoCells(0), issueCells(g, BulkValidatorException.TYPE_INVALID_VALUE),
                    name);
            }
        }
        assertEquals(8, seen);
    }

    @Test void paddedAlleleColumnsBoundToLiteralNames() {
        String a00 = MS + "alleles00";
        String a01 = MS + "alleles01";
        GroupedRows g = collapse(full("G", "a.jpg", "TissueSample.sampleID", "S1", NAMES, "A",
            a00, "1", a01, "2"), bare("G", "b.jpg", "TissueSample.sampleID", "S1", NAMES, "A",
            a00, "1", a01, "2"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        Map<String, Object> m = g.mergedRows().get(0);
        assertFalse(m.containsKey(a00));
        assertFalse(m.containsKey(a01));
        assertEquals("1", mergedValue(g, 0, A0));
        assertEquals("2", mergedValue(g, 0, A1));
        assertEquals(A1, ((BulkValidator)m.get(A1)).getFieldName());
        assertEquals(List.of(cell(0, a00), cell(1, a00)), g.contributorsOf(0, A0));
        assertEquals(List.of(cell(0, a01), cell(1, a01)), g.contributorsOf(0, A1));
        // a failure names the original column
        g = collapse(full("G", "a.jpg", "TissueSample.sampleID", "S1", NAMES, "A", a00, "x", a01, "2"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE,
            List.of(cell(0, NAMES), cell(0, a00), cell(0, a01)));
    }

    @Test void conflictingValuesForOneNameLabelRejected() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.individualID", "ind1",
            "MarkedIndividual.name0.label", "alias", "MarkedIndividual.name0.value", "X",
            "MarkedIndividual.name1.label", "alias", "MarkedIndividual.name1.value", "Y"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE,
            List.of(cell(0, "MarkedIndividual.name1.value")));
        g = collapse(full("G", "a.jpg", "Encounter.individualID", "ind1",
            "MarkedIndividual.name0.label", "alias", "MarkedIndividual.name0.value", "X",
            "MarkedIndividual.name1.label", "alias", "MarkedIndividual.name1.value", "X"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
    }

    @Test void namesWithoutIndividualRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "MarkedIndividual.name0.label", "alias",
            "MarkedIndividual.name0.value", "X"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "MarkedIndividual.name0.label"), cell(0, "MarkedIndividual.name0.value")));
        // the individual may come from another row, under either spelling
        g = collapse(full("G", "a.jpg", "MarkedIndividual.name0.label", "alias",
            "MarkedIndividual.name0.value", "X"), bare("G", "b.jpg", "MarkedIndividual.individualID", "i"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
    }

    @Test void nicknameWithoutIndividualRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "MarkedIndividual.nickName", "Nick"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "MarkedIndividual.nickName")));
        g = collapse(full("G", "a.jpg"), bare("G", "b.jpg", "MarkedIndividual.nickname", "Nick"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(1, "MarkedIndividual.nickname")));
        g = collapse(full("G", "a.jpg", "MarkedIndividual.nickName", "Nick",
            "Encounter.individualID", "ind1"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        // a conflicting individual is already reported: nothing is added on the nickname
        g = collapse(full("G", "a.jpg", "MarkedIndividual.nickName", "Nick",
            "Encounter.individualID", "ind1"), bare("G", "b.jpg", "Encounter.individualID", "ind2"));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT,
            List.of(cell(0, "Encounter.individualID"), cell(1, "Encounter.individualID")));
    }

    @Test void socialUnitWithoutIndividualRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "SocialUnit.socialUnitName", "Pod"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE,
            List.of(cell(0, "SocialUnit.socialUnitName")));
        g = collapse(full("G", "a.jpg", "SocialUnit.socialUnitName", "Pod",
            "Encounter.individualID", "ind1", "Membership.role", "lead"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
    }

    @Test void membershipRoleWithoutSocialUnitRequired() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.individualID", "ind1"),
            bare("G", "b.jpg", "Membership.role", "lead"));
        assertOnly(g, BulkValidatorException.TYPE_REQUIRED_VALUE, List.of(cell(1, "Membership.role")));
    }

    JSONArray fixtureCases()
    throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/bulk-import/group-equality-cases.json")) {
            assertNotNull(in, "fixture on the test classpath");
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                       .getJSONArray("cases");
        }
    }

    @Test void sharedCompoundEqualityCases()
    throws Exception {
        projectsExist();
        JSONArray cases = fixtureCases();
        int pairs = 0;
        int groups = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String name = c.getString("name");
            if ("compound".equals(c.getString("kind"))) {
                pairs++;
                JSONObject a = c.getJSONObject("rowA");
                JSONObject b = c.getJSONObject("rowB");
                GroupedRows g = collapse(withCells(full("G", "a.jpg"), a),
                    withCells(bare("G", "b.jpg"), b));
                List<GroupedRows.Cell> expected = new ArrayList<GroupedRows.Cell>();
                if (!c.getBoolean("equal")) {
                    for (String k : new java.util.TreeSet<String>(a.keySet())) expected.add(cell(0, k));
                    for (String k : new java.util.TreeSet<String>(b.keySet())) expected.add(cell(1, k));
                }
                assertEquals(expected, issueCells(g, null), name + ": " + g.issues());
                assertEquals(expected, issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT), name);
            } else if ("compoundGroup".equals(c.getString("kind"))) {
                groups++;
                JSONArray rows = c.getJSONArray("rows");
                JSONObject[] src = new JSONObject[rows.length()];
                for (int r = 0; r < rows.length(); r++)
                    src[r] = withCells((r == 0) ? full("G", "p" + r + ".jpg") :
                        bare("G", "p" + r + ".jpg"), rows.getJSONObject(r));
                GroupedRows g = collapse(src);
                List<GroupedRows.Cell> expected = new ArrayList<GroupedRows.Cell>();
                JSONArray blamed = c.getJSONArray("blamed");
                for (int k = 0; k < blamed.length(); k++)
                    expected.add(cell(blamed.getJSONArray(k).getInt(0),
                        blamed.getJSONArray(k).getString(1)));
                assertEquals(expected, issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT), name);
                assertEquals(expected.size(), g.issues().size(), name);
            }
        }
        assertEquals(3, pairs);
        assertEquals(2, groups);
    }

    @Test void sameMeasurementOffsetTwiceRejected() {
        // within one row: the later column (in name order) is the duplicate
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.measurement0", 2.5,
            "Encounter.length", 2.5));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_DUPLICATE,
            List.of(cell(0, "Encounter.measurement0")));
        g = collapse(full("G", "a.jpg", "Encounter.measurement0", 2.5,
            "Encounter.measurement0.samplingProtocol", "x", "Encounter.length.samplingProtocol", "y"));
        assertOnly(g, BulkValidatorException.TYPE_INVALID_DUPLICATE,
            List.of(cell(0, "Encounter.measurement0.samplingProtocol")));
        // across rows: two different columns, a set conflict only
        g = collapse(full("G", "a.jpg", "Encounter.measurement0", 2.5),
            bare("G", "b.jpg", "Encounter.length", 2.5));
        assertOnly(g, BulkValidatorException.TYPE_GROUP_CONFLICT,
            List.of(cell(0, "Encounter.measurement0"), cell(1, "Encounter.length")));
        // an ungrouped row keeps today's behaviour
        g = collapse(obj("Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 2020, "Encounter.measurement0", 2.5, "Encounter.length", 2.5));
        assertTrue(g.issues().isEmpty());
        assertNoMergedErrors(g, 0);
    }

    @Test void ignoredFieldOnGroupedRowRejected() {
        assertEquals(Set.of("Encounter.project", "MarkedIndividual.name", "Survey.type"),
            GroupCollapse.GROUP_IGNORED_FIELDS);
        for (String f : GroupCollapse.GROUP_IGNORED_FIELDS) {
            GroupedRows g = collapse(full("G", "a.jpg", f, "x"), bare("G", "b.jpg", f, ""));
            assertOnly(g, BulkValidatorException.TYPE_INVALID_VALUE, List.of(cell(0, f)));
            assertEquals("not imported; remove this column from grouped rows",
                g.issues().get(0).error.getMessage());
            assertFalse(g.mergedRows().get(0).containsKey(f));
        }
        // an ungrouped row: today's behaviour
        JSONObject row = obj("Encounter.genus", "Genus", "Encounter.specificEpithet", "species",
            "Encounter.year", 2020, "Survey.type", "x");
        GroupedRows g = collapse(row);
        assertTrue(g.issues().isEmpty());
        assertNoMergedErrors(g, 0);
        assertEquals("x", mergedValue(g, 0, "Survey.type"));
        assertEquals(BulkImportUtil.validateRow(copy(row), sh, false).keySet(),
            g.mergedRows().get(0).keySet());
    }

    @Test void numAdultMalesIsAnOrdinaryScalar() {
        GroupedRows g = collapse(full("G", "a.jpg", "Sighting.numAdultMales", 3,
            "Sighting.numSubFemales", "2"), bare("G", "b.jpg", "Sighting.numAdultMales", "3",
            "Sighting.numSubFemales", 2));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertNoMergedErrors(g, 0);
        assertEquals(3, mergedValue(g, 0, "Sighting.numAdultMales"));
        assertEquals(2, mergedValue(g, 0, "Sighting.numSubFemales"));
        assertEquals(List.of(cell(0, "Sighting.numAdultMales"), cell(1, "Sighting.numAdultMales")),
            g.contributorsOf(0, "Sighting.numAdultMales"));
    }

    @Test void compoundFailureOnOneRowStillComparesOthers() {
        projectsExist();
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.submitter0.emailAddress", "not-an-email"),
            bare("G", "b.jpg", P0, "P", N0, "Proj"), bare("G", "c.jpg", "Encounter.measurement0", 2.5));
        // both independent findings: the failed cell (the email validator's own exception carries
        // no type), and the conflict between the rows whose compound cells all validated
        List<GroupedRows.Cell> conflicts = List.of(cell(1, P0), cell(1, N0),
            cell(2, "Encounter.measurement0"));
        List<GroupedRows.Cell> all = new ArrayList<GroupedRows.Cell>();
        all.add(cell(0, "Encounter.submitter0.emailAddress"));
        all.addAll(conflicts);
        assertEquals(all, issueCells(g, null), g.issues().toString());
        assertEquals(conflicts, issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(all.size(), failureCount(g), g.mergedRows().toString());
        assertTrue(g.issues().get(1).error.getMessage().startsWith("rows 1, 2 of this group"),
            g.issues().get(1).error.getMessage());
        assertFalse(g.mergedRows().get(0).containsKey(P0));
        assertFalse(g.mergedRows().get(0).containsKey("Encounter.measurement0"));
        // the other rows agree: the failed cell is the only report, and nothing is merged
        g = collapse(full("G", "a.jpg", "Encounter.submitter0.emailAddress", "not-an-email"),
            bare("G", "b.jpg", P0, "P", N0, "Proj"), bare("G", "c.jpg", P0, "P", N0, "Proj"));
        assertOnly(g, null, List.of(cell(0, "Encounter.submitter0.emailAddress")));
        assertFalse(g.mergedRows().get(0).containsKey(P0));
    }

    @Test void sharedPrefixLookedUpOnce() {
        GroupedRows g = collapse(full("G", "a.jpg", P0, "P", N0, "Proj"),
            full("H", "b.jpg", P0, "P", N0, "Proj"));
        assertEquals(List.of(cell(0, O0), cell(1, O0)), issueCells(g, BulkValidatorException.TYPE_REQUIRED_VALUE));
        verify(sh, times(1)).getProjectByProjectIdPrefix("P");
        verify(sh, times(1)).getProjectByProjectIdPrefixPrefix("P");
    }

    // ---- Task 6: encounter IDs on grouped rows must be unique and unused ----

    static final String ID_A = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    static final String ID_B = "3f2504e0-4f89-11d3-9a0c-0305e82c3302";

    @Test void idInTwoGroupsConflicts() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", ID_A),
            full("H", "b.jpg", "Encounter.catalogNumber", ID_A.toUpperCase()),
            full("I", "c.jpg", "Encounter.id", ID_B));
        assertEquals(List.of(cell(0, "Encounter.id"), cell(1, "Encounter.catalogNumber")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(2, g.issues().size(), g.issues().toString());
        assertTrue(g.hasGroupErrors());
    }

    @Test void idInGroupAndUngroupedRowConflicts() {
        JSONObject ungrouped = obj("Encounter.genus", "Genus", "Encounter.specificEpithet",
            "species", "Encounter.year", 2020, "Encounter.mediaAsset0", "u.jpg",
            "Encounter.catalogNumber", ID_A.toUpperCase());
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", ID_A), ungrouped);
        assertEquals(List.of(cell(0, "Encounter.id"), cell(1, "Encounter.catalogNumber")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        assertEquals(2, g.issues().size(), g.issues().toString());
        // the ungrouped row's value is left as typed
        assertEquals(ID_A.toUpperCase(), mergedValue(g, 1, "Encounter.catalogNumber"));
    }

    @Test void idInUseRejected() {
        bulkUtil.when(() -> BulkImportUtil.encounterIdsInUse(anyCollection(), any())).thenReturn(
            new HashSet<String>(List.of(ID_A)));
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", ID_A.toUpperCase()),
            bare("G", "b.jpg", "Encounter.catalogNumber", ID_A), full("H", "c.jpg",
            "Encounter.id", ID_B));
        assertEquals(List.of(cell(0, "Encounter.id"), cell(1, "Encounter.catalogNumber")),
            issueCells(g, BulkValidatorException.TYPE_ID_UNAVAILABLE));
        assertEquals(2, g.issues().size(), g.issues().toString());
        assertEquals("Encounter ID unavailable", g.issues().get(0).error.getMessage());
    }

    @Test void idLookupFailureIsError() {
        bulkUtil.when(() -> BulkImportUtil.encounterIdsInUse(anyCollection(), any())).thenThrow(
            new RuntimeException("db down"));
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", ID_A),
            full("H", "b.jpg", "Encounter.id", ID_B));
        assertEquals(List.of(cell(0, "Encounter.id"), cell(1, "Encounter.id")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_VALUE));
        assertEquals(2, g.issues().size(), g.issues().toString());
        assertEquals("Could not check whether this Encounter ID is available",
            g.issues().get(0).error.getMessage());
    }

    @Test void noLookupWhenAllIdsBlank() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", "  "),
            full("H", "b.jpg", "Encounter.catalogNumber", JSONObject.NULL), full("I", "c.jpg"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        bulkUtil.verify(() -> BulkImportUtil.encounterIdsInUse(anyCollection(), any()), never());
    }

    @Test void poisonedIdGetsNoSecondIssue() {
        // a non-UUID cell: only its own failure; and a group whose ID cells disagree: only the
        // conflict, never also "unavailable"
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", "1-1-1-1-1"),
            full("H", "b.jpg", "Encounter.id", ID_A), bare("H", "c.jpg", "Encounter.id", ID_B));
        assertEquals(3, g.issues().size(), g.issues().toString());
        assertEquals(List.of(cell(0, "Encounter.id")),
            issueCells(g, BulkValidatorException.TYPE_INVALID_VALUE));
        assertEquals(List.of(cell(1, "Encounter.id"), cell(2, "Encounter.id")),
            issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT));
        bulkUtil.verify(() -> BulkImportUtil.encounterIdsInUse(anyCollection(), any()), never());
    }

    @Test void conflictedIdExcludedFromLookup() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.id", ID_A),
            full("H", "b.jpg", "Encounter.id", ID_A), full("I", "c.jpg", "Encounter.id", ID_B));
        assertEquals(2, g.issues().size(), g.issues().toString());
        bulkUtil.verify(() -> BulkImportUtil.encounterIdsInUse(
            org.mockito.ArgumentMatchers.eq(new LinkedHashSet<String>(List.of(ID_B))), any()));
    }

    // Important 1 (final review): a conflict message is bounded whatever the group's size
    static final int MESSAGE_BOUND = 500;

    static void assertBoundedMessages(GroupedRows g) {
        assertFalse(g.issues().isEmpty());
        for (GroupedRows.Issue is : g.issues())
            assertTrue(is.error.getMessage().length() < MESSAGE_BOUND, is.error.getMessage());
    }

    @Test void conflictMessageListsThreeValuesThenCount() {
        GroupedRows g = collapse(full("G", "a.jpg", "Encounter.behavior", "a"),
            bare("G", "b.jpg", "Encounter.behavior", "b"), bare("G", "c.jpg", "Encounter.behavior", "c"),
            bare("G", "d.jpg", "Encounter.behavior", "d"));
        assertEquals(4, g.issues().size(), g.issues().toString());
        for (GroupedRows.Issue is : g.issues())
            assertEquals("rows of this group disagree on Encounter.behavior: \"a\", \"b\", \"c\" " +
                "and 1 more.", is.error.getMessage());
    }

    @Test void largeGroupDistinctRemarksBounded() {
        String filler = "x".repeat(1000); // each value is cut in the message
        JSONObject[] rows = new JSONObject[200];
        for (int i = 0; i < rows.length; i++)
            rows[i] = (i == 0) ? full("G", "p0.jpg", "Encounter.researcherComments", i + filler) :
                bare("G", "p" + i + ".jpg", "Encounter.researcherComments", i + filler);
        GroupedRows g = collapse(rows);
        assertBoundedMessages(g);
        int conflicts = 0;
        for (GroupedRows.Issue is : g.issues()) {
            if (!is.error.isType(BulkValidatorException.TYPE_GROUP_CONFLICT)) continue;
            conflicts++;
            assertTrue(is.error.getMessage().contains(" and 197 more."), is.error.getMessage());
            assertTrue(is.error.getMessage().endsWith(
                "Put encounter remarks on one row of the group, or make them identical."),
                is.error.getMessage());
        }
        assertEquals(200, conflicts, g.issues().toString());
    }

    @Test void largeGroupDistinctSexLikeValuesBounded() {
        JSONObject[] rows = new JSONObject[200];
        for (int i = 0; i < rows.length; i++)
            rows[i] = (i == 0) ? full("G", "p0.jpg", "Encounter.behavior", "b" + i) :
                bare("G", "p" + i + ".jpg", "Encounter.behavior", "b" + i);
        GroupedRows g = collapse(rows);
        assertBoundedMessages(g);
        assertEquals(200, issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT).size());
    }

    @Test void largeGroupCompoundConflictBounded() {
        projectsExist();
        JSONObject[] rows = new JSONObject[200];
        for (int i = 0; i < rows.length; i++)
            rows[i] = (i == 0) ? full("G", "p0.jpg", P0, "P" + i, N0, "Proj") :
                bare("G", "p" + i + ".jpg", P0, "P" + i, N0, "Proj");
        GroupedRows g = collapse(rows);
        assertBoundedMessages(g);
        int conflicts = 0;
        for (GroupedRows.Issue is : g.issues()) {
            if (!is.error.isType(BulkValidatorException.TYPE_GROUP_CONFLICT)) continue;
            conflicts++;
            assertTrue(is.error.getMessage().startsWith("rows 0, 1, 2 and 197 more of this group"),
                is.error.getMessage());
        }
        assertEquals(400, conflicts, g.issues().toString());
    }

    @Test void longZeroPaddedHeaderClippedInConflictMessage() {
        projectsExist();
        String longPrefix = "Encounter.project" + "0".repeat(5000) + ".projectIdPrefix";
        JSONObject[] rows = new JSONObject[50];
        for (int i = 0; i < rows.length; i++)
            rows[i] = (i == 0) ? full("G", "p0.jpg", longPrefix, "P", N0, "Proj") :
                bare("G", "p" + i + ".jpg", (i % 2 == 0) ? longPrefix : P0, "P", N0, "Proj");
        GroupedRows g = collapse(rows);
        assertBoundedMessages(g);
        assertEquals(100, issueCells(g, BulkValidatorException.TYPE_GROUP_CONFLICT).size());
        // each error keeps its own exact source column (row 0's issues: N0, then the long prefix)
        GroupedRows.Issue onLong = g.issues().get(1);
        assertTrue(longPrefix.equals(onLong.field), "not the long column");
        assertTrue(longPrefix.equals(onLong.error.getErrors().getJSONObject(0)
            .getString("fieldName")), "not the long column");
        String msg = onLong.error.getMessage();
        assertTrue(msg.contains("(differing: " + P0 + ", Encounter.project000"), msg);
    }
}
