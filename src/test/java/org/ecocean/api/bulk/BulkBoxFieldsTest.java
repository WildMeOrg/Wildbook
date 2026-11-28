package org.ecocean.api.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.Arrays;
import java.util.List;

import org.ecocean.CommonConfiguration;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Bulk-import boxes PR B, task 1 (spec 4.1 / 4.2): the five per-photo box columns are indexable
 * field names with their own value rules, quality must be finite, and the five suffixes are
 * reserved from labeled keywords.
 */
class BulkBoxFieldsTest {
    private final Shepherd sh = mock(Shepherd.class);

    @BeforeEach @AfterEach void resetLabeledKeywords() {
        BulkImportUtil.labeledKeywordMap = null;
    }

    private Object validateValue(String suffix, Object value) throws BulkValidatorException {
        return BulkValidator.validateValue("Encounter.mediaAsset0" + suffix, value, sh);
    }

    private void assertInvalid(String fieldName, Object value) {
        BulkValidatorException ex = assertThrows(BulkValidatorException.class,
            () -> BulkValidator.validateValue(fieldName, value, sh), fieldName + " = " + value);
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, ex.getType(), fieldName + " = " + value);
        assertEquals(fieldName, ex.getErrors().getJSONObject(0).getString("fieldName"), fieldName + " = " + value);
    }

    @Test void numberedColumnsStopAtTheMaximumIndex() throws Exception {
        assertEquals(199, BulkValidator.indexIntValue("Encounter.mediaAsset199"));
        assertEquals(199, BulkValidator.indexIntValue("Encounter.mediaAsset199.bbox"));
        for (String tooBig : new String[] { "Encounter.mediaAsset200", "Encounter.mediaAsset99999.bbox",
            "Encounter.mediaAsset99999999999", "Encounter.keyword250" }) {
            BulkValidatorException ex = assertThrows(BulkValidatorException.class,
                () -> BulkValidator.indexIntValue(tooBig), tooBig);
            assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, ex.getType(), tooBig);
            assertTrue(ex.getMessage().contains("above the maximum of 199"), ex.getMessage());
            // an invalid value is an error, not a skipped unknown column
            assertTrue(!ex.treatAsWarning(true), tooBig);
        }
        // a too-big column is left out instead of padding the list to its index
        List<String> found = BulkImportUtil.findIndexedFieldNames(new java.util.HashSet<>(Arrays.asList(
            "Encounter.mediaAsset0", "Encounter.mediaAsset99999", "Encounter.mediaAsset99999.bbox")),
            "Encounter.mediaAsset");
        assertEquals(Arrays.asList("Encounter.mediaAsset0"), found);
        found = BulkImportUtil.findIndexedFieldNames(new java.util.HashSet<>(Arrays.asList(
            "Encounter.mediaAsset1.bbox", "Encounter.mediaAsset99999.bbox")), "Encounter.mediaAsset.bbox");
        assertEquals(Arrays.asList(null, "Encounter.mediaAsset1.bbox"), found);
    }

    @Test void bboxParsesTrimsAndCanonicalizes() throws Exception {
        assertEquals("10,20,300,400", BulkValidator.validateValue("Encounter.mediaAsset0.bbox", " 10, 20 ,300,400 ", sh));
        assertEquals("0,0,1,1", BulkValidator.validateValue("Encounter.mediaAsset3.bbox", "0,0,1,1", sh));
        assertNull(BulkValidator.validateValue("Encounter.mediaAsset0.bbox", "  ", sh));
        assertNull(BulkValidator.validateValue("Encounter.mediaAsset0.bbox", null, sh));
    }

    @Test void bboxRejectsBadShapes() {
        for (String bad : new String[] { "10,20,300", "a,b,c,d", "-1,0,5,5", "0,0,0,5", "0,0,5,0",
            "1.5,2,3,4", "10,20,300,400,5", "1,,2,3", "1,2,3,4,", "99999999999,0,1,1" }) {
            assertInvalid("Encounter.mediaAsset0.bbox", bad);
        }
    }

    @Test void iaClassTrimsAndBoundsLength() throws Exception {
        assertEquals("body", validateValue(".iaClass", " body "));
        assertInvalid("Encounter.mediaAsset0.iaClass", "x".repeat(65));
        assertNull(validateValue(".iaClass", ""));
    }

    @Test void viewpointMustBeKnown() throws Exception {
        assertEquals("left", validateValue(".viewpoint", "left"));
        assertInvalid("Encounter.mediaAsset0.viewpoint", "sideways");
        assertNull(validateValue(".viewpoint", null));
    }

    @Test void thetaMustBeFiniteAndInRange() throws Exception {
        assertEquals(1.5, validateValue(".theta", "1.5"));
        assertEquals(-6.2832, validateValue(".theta", "-6.2832"));
        assertEquals(0.25, validateValue(".theta", Double.valueOf(0.25)));
        assertNull(validateValue(".theta", null));
        assertNull(validateValue(".theta", " "));
        assertInvalid("Encounter.mediaAsset0.theta", "6.2833");
        assertInvalid("Encounter.mediaAsset0.theta", "NaN");
        assertInvalid("Encounter.mediaAsset0.theta", "Infinity");
        assertInvalid("Encounter.mediaAsset0.theta", "abc");
    }

    @Test void bboxSourceLength() throws Exception {
        assertEquals("yolo-v8", validateValue(".bboxSource", " yolo-v8 "));
        assertInvalid("Encounter.mediaAsset0.bboxSource", "x".repeat(129));
        assertNull(validateValue(".bboxSource", " "));
    }

    @Test void qualityMustBeFinite() throws Exception {
        assertEquals(0.93, BulkValidator.validateValue("Encounter.quality1", "0.93", sh));
        assertNull(BulkValidator.validateValue("Encounter.quality1", null, sh));
        assertInvalid("Encounter.quality1", "NaN");
        assertInvalid("Encounter.quality1", "Infinity");
        assertInvalid("Encounter.quality1", "abc");
    }

    @Test void boxColumnsAreIndexableFieldNames() throws Exception {
        assertEquals(List.of("bbox", "iaClass", "viewpoint", "theta", "bboxSource"), BulkValidator.BOX_SUFFIXES);
        for (String s : BulkValidator.BOX_SUFFIXES) {
            assertTrue(BulkValidator.isValidFieldName("Encounter.mediaAsset7." + s), s);
            assertEquals(7, BulkValidator.indexIntValue("Encounter.mediaAsset7." + s), s);
            assertEquals("Encounter.mediaAsset." + s, BulkValidator.indexPrefixValue("Encounter.mediaAsset7." + s), s);
        }
        assertEquals(6.2832, BulkValidator.THETA_LIMIT);
    }

    @Test void reservedSuffixesAreNeverLabeledKeywords() {
        try (MockedStatic<CommonConfiguration> cc = mockStatic(CommonConfiguration.class)) {
            cc.when(() -> CommonConfiguration.getIndexedPropertyValues(eq("kwLabel"), anyString()))
                .thenReturn(Arrays.asList("bbox", "colour"));
            cc.when(() -> CommonConfiguration.getIndexedPropertyValues(eq("colour"), anyString()))
                .thenReturn(Arrays.asList("red"));
            cc.when(() -> CommonConfiguration.getIndexedPropertyValues(eq("bbox"), anyString()))
                .thenReturn(Arrays.asList("should-not-be-read"));
            assertEquals(java.util.Set.of("colour"), BulkImportUtil.getLabeledKeywordMap().keySet());
            assertNull(BulkImportUtil.getLabeledKeywordLabel("Encounter.mediaAsset0.bbox"));
            assertTrue(BulkImportUtil.getLabeledKeywordOffset("Encounter.mediaAsset0.bbox") < 0);
            assertEquals("colour", BulkImportUtil.getLabeledKeywordLabel("Encounter.mediaAsset0.colour"));
            assertEquals(0, BulkImportUtil.getLabeledKeywordOffset("Encounter.mediaAsset0.colour"));
        }
    }

    @Test void keywordsSuffixStillWorks() throws Exception {   // (regression)
        assertEquals("Encounter.mediaAsset.keywords", BulkValidator.indexPrefixValue("Encounter.mediaAsset2.keywords"));
        assertEquals("k", BulkValidator.validateValue("Encounter.mediaAsset2.keywords", "k", sh));
    }
}
