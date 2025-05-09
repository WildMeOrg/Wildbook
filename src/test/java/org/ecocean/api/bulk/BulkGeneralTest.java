package org.ecocean;

import org.ecocean.api.bulk.*;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.json.JSONArray;
import org.json.JSONObject;

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
        bv = new BulkValidator("Encounter.informOther99.emailAddress", "fake", null);
        assertTrue(bv.isIndexed());
        assertEquals(bv.getIndexInt(), 99);
        assertEquals(bv.getIndexPrefix(), "Encounter.informOther.emailAddress");
        assertEquals(bv.getValue(), "fake");
        assertEquals(bv.getFieldName(), "Encounter.informOther99.emailAddress");

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
}
