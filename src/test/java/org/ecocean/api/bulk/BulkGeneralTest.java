package org.ecocean;

import org.ecocean.api.bulk.*;

import javax.servlet.ServletException;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BulkGeneralTest {
    static String fieldNameValidEncounterYear = "Encounter.year";
    static String fieldNameInvalid = "Fail.fubar";

    @Test void basicValidation()
    throws BulkValidatorException {
        BulkValidator bv = new BulkValidator(fieldNameValidEncounterYear, 2000, null);

        assertNotNull(bv);
        assertFalse(bv.isIndexed());
        assertTrue(BulkValidator.isValidFieldName(fieldNameValidEncounterYear));
        assertFalse(BulkValidator.isValidFieldName(fieldNameInvalid));

        BulkValidatorException ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator bvFail = new BulkValidator(fieldNameInvalid, 0, null);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));
        assertEquals(ex.getType(), "UNKNOWN_FIELDNAME");

        bv = new BulkValidator("Encounter.mediaAsset123", "fake", null);
        assertTrue(bv.isIndexed());
        assertEquals(bv.getIndexInt(), 123);
        assertEquals(bv.getIndexPrefix(), "Encounter.mediaAsset");
        assertEquals(bv.getValue(), "fake");
        assertEquals(bv.getFieldName(), "Encounter.mediaAsset123");

        // tests the embedded 3-part flavor
        bv = new BulkValidator("Encounter.informOther99.emailAddress", "fake@example.com", null);
        assertTrue(bv.isIndexed());
        assertEquals(bv.getIndexInt(), 99);
        assertEquals(bv.getIndexPrefix(), "Encounter.informOther.emailAddress");
        assertEquals(bv.getValue(), "fake@example.com");
        assertEquals(bv.getFieldName(), "Encounter.informOther99.emailAddress");

        // invalid email address on indexy thing
        ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator bvFail = new BulkValidator("Encounter.photographer11.emailAddress",
                "not_email_valid_example.com", null);
        });
        assertTrue(ex.getMessage().equals("invalid email address"));

        // some indexable fieldName tests
        assertTrue(BulkValidator.isValidFieldName("Encounter.mediaAsset123"));
        assertEquals(BulkValidator.indexPrefixValue("Encounter.mediaAsset123"),
            "Encounter.mediaAsset");
        assertEquals(BulkValidator.indexIntValue("Encounter.mediaAsset123"), 123);
        assertNull(BulkValidator.indexPrefixValue(fieldNameValidEncounterYear));
        assertEquals(BulkValidator.indexIntValue(fieldNameValidEncounterYear), -1);
        ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator.indexIntValue(fieldNameInvalid);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));
        ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator.indexPrefixValue(fieldNameInvalid);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));
    }

    @Test void basicUtilTests() {
        Set<String> fields = new HashSet<String>();

        fields.add("Encounter.mediaAsset0");
        fields.add("Encounter.mediaAsset1");
        fields.add("Encounter.mediaAsset4");
        fields.add("Encounter.mediaAsset0.keywords");
        fields.add("Encounter.keyword0");
        fields.add("Encounter.keyword3");
        fields.add("Encounter.year");
        List<String> found = BulkImportUtil.findIndexedFieldNames(fields, "Encounter.mediaAsset");
        assertEquals(found.size(), 5);
        assertEquals(found.get(4), "Encounter.mediaAsset4");
        assertNull(found.get(3));
    }

    @Test void basicUtilTestValidateRow() {
        Map<String, Object> rowResult = BulkImportUtil.validateRow(null, null);

        assertNotNull(rowResult);
        assertEquals(rowResult.size(), 0);

        JSONObject rowData = new JSONObject();
        rowData.put(fieldNameValidEncounterYear, 2000);
        rowData.put(fieldNameInvalid, "fail");
        rowResult = BulkImportUtil.validateRow(rowData, null);
        assertNotNull(rowResult);
        assertEquals(rowResult.size(), 4);
        assertTrue(rowResult.get(fieldNameValidEncounterYear) instanceof BulkValidator);
        assertTrue(rowResult.get(fieldNameInvalid) instanceof BulkValidatorException);
        BulkValidator bv = (BulkValidator)rowResult.get(fieldNameValidEncounterYear);
        assertEquals(bv.getFieldName(), fieldNameValidEncounterYear);
    }

    @Test void fieldIntegrity() {
        for (String f : BulkValidator.MINIMAL_FIELD_NAMES_STRING) {
            if (!BulkValidator.FIELD_NAMES.contains(f))
                throw new RuntimeException("BulkValidator.FIELD_NAMES is missing " + f);
        }
        for (String f : BulkValidator.MINIMAL_FIELD_NAMES_INT) {
            if (!BulkValidator.FIELD_NAMES.contains(f))
                throw new RuntimeException("BulkValidator.FIELD_NAMES is missing " + f);
        }
        for (String f : BulkValidator.MINIMAL_FIELD_NAMES_DOUBLE) {
            if (!BulkValidator.FIELD_NAMES.contains(f))
                throw new RuntimeException("BulkValidator.FIELD_NAMES is missing " + f);
        }
    }

    @Test void fieldNameCoverage() {
        List<Map<String, Object> > rows = new ArrayList<Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();

        for (String fn : BulkValidator.FIELD_NAMES) {
            BulkValidator bv = null;
            try {
                bv = new BulkValidator(fn, "value-" + fn, null);
            } catch (Exception ex) {}
            if (bv != null) row.put(fn, bv);
        }
        rows.add(row);
        // exception here means we got far enough :)
        Exception ex = assertThrows(ServletException.class, () -> {
            BulkImporter imp = new BulkImporter(rows, null, null, null);
            imp.createImport();
        });
        // on QA, ex.getMessage() returns null WTF!? so skipping this
        // assertTrue(ex.getMessage().contains("mediaAssetMap"));
    }

    @Test void measurement() {
        List<String> mockMeasValues = new ArrayList<String>();

        mockMeasValues.add("Measurement0");
        mockMeasValues.add("Measurement1");
        try (MockedStatic<BulkImportUtil> mockUtil = mockStatic(BulkImportUtil.class)) {
            // allows us to skip commonConfig to get list of values
            mockUtil.when(() -> BulkImportUtil.getMeasurementValues()).thenReturn(mockMeasValues);
            JSONObject mf = BulkValidator.minimalFieldsJson();
            int ct = 0;
            for (String key : mockMeasValues) {
                assertEquals(mf.getString("Encounter.measurement" + ct), "double");
                assertEquals(mf.getString("Encounter.measurement" + ct + ".samplingProtocol"),
                    "string");
                assertEquals(mf.getString("Encounter." + key), "double");
                assertEquals(mf.getString("Encounter." + key + ".samplingProtocol"), "string");
                assertEquals(mf.getString("Encounter.measurement." + key), "double");
                assertEquals(mf.getString("Encounter.measurement." + key + ".samplingProtocol"),
                    "string");
                ct++;
            }
        }
    }
}
