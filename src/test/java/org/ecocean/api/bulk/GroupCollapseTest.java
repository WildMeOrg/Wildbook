package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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

import org.ecocean.LocationID;
import org.ecocean.User;
import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.api.bulk.GroupCollapse;
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
 * stay in the merged map and are mapped back through cellsFor.
 */
class GroupCollapseTest {
    static final String KEY = "Encounter.groupKey";
    static final String UUID_UPPER = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";

    Shepherd sh;
    MockedStatic<LocationID> locationIds;
    List<String> savedMeasurements;
    Map<String, Set<String> > savedKeywords;

    @BeforeEach void setUp() {
        sh = mock(Shepherd.class);
        when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
        when(sh.getUser(anyString())).thenReturn(mock(User.class));
        locationIds = mockStatic(LocationID.class);
        locationIds.when(() -> LocationID.isValidLocationID(anyString())).thenReturn(true);
        savedMeasurements = BulkImportUtil.measurementValues;
        savedKeywords = BulkImportUtil.labeledKeywordMap;
        BulkImportUtil.measurementValues = new ArrayList<String>(List.of("length"));
        BulkImportUtil.labeledKeywordMap = new LinkedHashMap<String, Set<String> >();
    }

    @AfterEach void tearDown() {
        locationIds.close();
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
        g = collapse(full("G", "a.jpg", "Encounter.behavior", "a"),
            bare("G", "b.jpg", "Sighting.groupBehavior", "a"));
        assertTrue(g.issues().isEmpty(), g.issues().toString());
        assertEquals("a", mergedValue(g, 0, "Encounter.behavior"));
        assertEquals("a", mergedValue(g, 0, "Sighting.groupBehavior"));
        assertEquals(List.of(cell(1, "Sighting.groupBehavior")),
            g.contributorsOf(0, "Sighting.groupBehavior"));
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

    @Test void interimPhotoHelpers() {
        GroupedRows g = collapse(full("G", "a.jpg"), bare("G", "b.jpg"),
            obj("Encounter.mediaAsset0", "c.jpg", "Encounter.genus", "Genus"));
        assertEquals(Set.of("a.jpg", "b.jpg"), g.groupedFilenames());
        assertFalse(g.exactSlotMode());
        assertEquals("in Encounter.mediaAsset1.iaClass", g.sourceText(0, "in Encounter.mediaAsset1.iaClass"));
        // a .bbox column anywhere, even blank, means exact-slot mode
        g = collapse(full("G", "a.jpg"), obj("Encounter.mediaAsset0.bbox", "", "Encounter.genus", "Genus"));
        assertTrue(g.exactSlotMode());
    }
}
