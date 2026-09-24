package org.ecocean.api.bulk;

import java.util.LinkedHashSet;
import java.util.Map;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Legacy validation behavior that the sibling submissions API must not change. */
class BulkSubmissionCompatibilityTest {
    private Shepherd shepherd() {
        Shepherd shepherd = mock(Shepherd.class);
        when(shepherd.isValidTaxonomyName(anyString())).thenReturn(true);
        return shepherd;
    }

    private JSONObject row() {
        return new JSONObject().put("Encounter.year", 2026)
            .put("Encounter.genus", "Loxodonta")
            .put("Encounter.specificEpithet", "africana");
    }

    @Test void legacyValidatorKeepsLocationOptionalWithoutSynthesizingDateFields() {
        Map<String, Object> result = BulkImportUtil.validateRow(row(), shepherd());
        assertFalse(result.containsKey("Encounter.locationID"));
        assertFalse(result.containsKey("Encounter.month"));
        assertFalse(result.containsKey("Encounter.day"));
        assertTrue(result.values().stream().allMatch(v -> v instanceof BulkValidator));
        assertEquals(2026, ((BulkValidator)result.get("Encounter.year")).getValue());
    }

    @Test void objectAndColumnArrayRowsValidateEquivalently() {
        JSONObject object = row();
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.add("Encounter.year");
        columns.add("Encounter.genus");
        columns.add("Encounter.specificEpithet");
        JSONArray values = new JSONArray();
        for (String column : columns) values.put(object.get(column));
        Map<String, Object> objectResult = BulkImportUtil.validateRow(object, shepherd());
        Map<String, Object> arrayResult = BulkImportUtil.validateRow(columns, values, shepherd());
        assertEquals(objectResult.keySet(), arrayResult.keySet());
        for (String column : columns) {
            assertEquals(((BulkValidator)objectResult.get(column)).getValue(),
                ((BulkValidator)arrayResult.get(column)).getValue());
        }
    }

    @Test void unknownFieldRetainsLegacyWarningPolicyChoice() {
        Map<String, Object> result = BulkImportUtil.validateRow(
            row().put("Unknown.field", "value"), shepherd());
        assertTrue(result.get("Unknown.field") instanceof BulkValidatorException);
        BulkValidatorException issue = (BulkValidatorException)result.get("Unknown.field");
        assertTrue(issue.treatAsWarning(true));
        assertFalse(issue.treatAsWarning(false));
    }

    @Test void invalidCalendarDateStillFailsAtSharedValidator() {
        Map<String, Object> result = BulkImportUtil.validateRow(
            row().put("Encounter.month", 2).put("Encounter.day", 30), shepherd());
        assertTrue(result.get("Encounter.day") instanceof BulkValidatorException);
        assertTrue(((BulkValidatorException)result.get("Encounter.day")).getMessage()
            .contains("day is out of range for month"));
    }

    @Test void shortArrayPadsMissingTrailingValuesWithNull() {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.add("Encounter.year");
        columns.add("Encounter.genus");
        columns.add("Encounter.specificEpithet");
        columns.add("Encounter.month");
        Map<String, Object> result = BulkImportUtil.validateRow(columns,
            new JSONArray().put(2026).put("Loxodonta").put("africana"), shepherd());
        assertTrue(result.get("Encounter.month") instanceof BulkValidator);
        assertNull(((BulkValidator)result.get("Encounter.month")).getValue());
    }
}
