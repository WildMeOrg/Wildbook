package org.ecocean;

import org.ecocean.api.bulk.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.Assert.*;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;


class BulkGeneralTest {

    static String fieldNameValidEncounterYear = "Encounter.year";
    static String fieldNameInvalid = "Fail.fubar";

    @Test void basicValidation() throws BulkValidatorException {
        BulkValidator bv = new BulkValidator(fieldNameValidEncounterYear, 2000);
        assertNotNull(bv);
        assertFalse(bv.isIndexed());
        assertTrue(BulkValidator.isValidFieldName(fieldNameValidEncounterYear));
        assertFalse(BulkValidator.isValidFieldName(fieldNameInvalid));

        Exception ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator bvFail = new BulkValidator(fieldNameInvalid, 0);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));

        bv = new BulkValidator("Encounter.mediaAsset123", "fake");
        assertTrue(bv.isIndexed());

        // some indexable fieldName tests
        assertTrue(BulkValidator.isValidFieldName("Encounter.mediaAsset123"));
        assertEquals(BulkValidator.indexPrefixValue("Encounter.mediaAsset123"), "Encounter.mediaAsset");
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

}
