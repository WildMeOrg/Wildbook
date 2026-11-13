package org.ecocean.api.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.ecocean.IAJsonProperties;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bulk-import boxes PR B, task 3 (spec 4.2 row layer, decisions 2 and 3): per-slot pairing of a
 * box with its photo and class, the class against the species' matchable (or, when identification
 * is skipped, canonical) list, and the same photo twice in one row when a box is involved.
 */
class BulkBoxRowRulesTest {
    private Shepherd sh;

    @BeforeEach void fixture() {
        sh = mock(Shepherd.class);
        when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
        JSONObject json = new JSONObject().put("Genus", new JSONObject().put("species", new JSONObject()
            .put("body", idConf(true))
            .put("fin", idConf(false))
            .put("animal", new JSONObject().put("_save_as", "body"))
            .put("body+tail", idConf(true))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        TestIAJsonPropertiesUtil.override(iac);
    }

    @AfterEach void clearFixture() {
        TestIAJsonPropertiesUtil.override(null);
    }

    private static JSONObject idConf(boolean dflt) {
        return new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", dflt)));
    }

    private static JSONObject row(Object... kv) {
        JSONObject row = new JSONObject().put("Encounter.genus", "Genus").put("Encounter.specificEpithet", "species").put("Encounter.year", 2020);
        for (int i = 0; i < kv.length; i += 2) row.put((String)kv[i], kv[i + 1]);
        return row;
    }

    private Map<String, Object> validate(JSONObject row) {
        return BulkImportUtil.validateRow(row, sh, false);
    }

    private static String errorType(Map<String, Object> m, String field) {
        Object o = m.get(field);
        return (o instanceof BulkValidatorException) ? ((BulkValidatorException)o).getType() : null;
    }

    private static void assertNoErrors(Map<String, Object> m) {
        for (Map.Entry<String, Object> e : m.entrySet())
            assertTrue(e.getValue() instanceof BulkValidator, e.getKey() + " -> " + e.getValue());
    }

    @Test void boxWithoutClassIsRequired() {
        // no iaClass column at all: the error goes on the column the sheet has
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5"));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset0.bbox"));
        assertFalse(m.containsKey("Encounter.mediaAsset0.iaClass"), "no fabricated key for a column the sheet lacks");
        // an empty iaClass cell: the error goes on that column
        m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", JSONObject.NULL));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset0.iaClass"));
        assertNull(errorType(m, "Encounter.mediaAsset0.bbox"));
    }

    @Test void classWithoutBoxIsRequiredOnTheColumnThatIsThere() {
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.iaClass", "body"));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset0.iaClass"));
        assertFalse(m.containsKey("Encounter.mediaAsset0.bbox"), "no fabricated .bbox key: it would switch the importer to exact-slot mode");
        m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.theta", "0.5"));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset0.theta"));
        assertFalse(m.containsKey("Encounter.mediaAsset0.bbox"));
        // an empty .bbox cell next to a class: the error goes on the class, the empty bbox stays a value
        m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", JSONObject.NULL, "Encounter.mediaAsset0.iaClass", "body"));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset0.iaClass"));
        assertTrue(m.get("Encounter.mediaAsset0.bbox") instanceof BulkValidator);
    }

    @Test void rejectedBoxValueIsNotPiledOnByThePairingRules() {
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "not-a-box", "Encounter.mediaAsset0.iaClass", "body"));
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.mediaAsset0.bbox"));
        assertTrue(m.get("Encounter.mediaAsset0.bbox").toString().contains("x,y,width,height"), "the field-layer message survives");
        assertNull(errorType(m, "Encounter.mediaAsset0.iaClass"));
    }

    @Test void samePhotoPlainThenBoxedThenPlainFlagsBothLaterSlots() {
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset1", "a.jpg", "Encounter.mediaAsset1.bbox", "1,1,5,5",
            "Encounter.mediaAsset1.iaClass", "body", "Encounter.mediaAsset2", "a.jpg"));
        assertEquals(BulkValidatorException.TYPE_INVALID_DUPLICATE, errorType(m, "Encounter.mediaAsset1"));
        assertEquals(BulkValidatorException.TYPE_INVALID_DUPLICATE, errorType(m, "Encounter.mediaAsset2"));
        assertNull(errorType(m, "Encounter.mediaAsset0"));
    }

    @Test void boxWithoutPhotoIsInvalid() {
        for (Object[] slot1 : new Object[][] { null, { "" }, { "   " } }) {
            JSONObject r = row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset1.bbox", "1,1,5,5", "Encounter.mediaAsset1.iaClass", "body");
            if (slot1 != null) r.put("Encounter.mediaAsset1", slot1[0]);
            Map<String, Object> m = validate(r);
            assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.mediaAsset1.bbox"), "slot1=" + (slot1 == null ? "absent" : "'" + slot1[0] + "'"));
        }
    }

    @Test void unmatchableClassIsRejectedWithTheList() {
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "fin"));
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.mediaAsset0.iaClass"));
        assertTrue(m.get("Encounter.mediaAsset0.iaClass").toString().contains("valid classes for Genus species: body, body+tail"), m.get("Encounter.mediaAsset0.iaClass").toString());
    }

    @Test void anyCanonicalClassIsAcceptedWhenIdentificationIsSkipped() {
        Map<String, Object> m = BulkImportUtil.validateRow(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "fin"), sh, true);
        assertNoErrors(m);
        m = BulkImportUtil.validateRow(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "bogus"), sh, true);
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.mediaAsset0.iaClass"));
    }

    @Test void classRuleIsSkippedWhenTheTaxonomyIsInvalid() {
        when(sh.isValidTaxonomyName(anyString())).thenReturn(false);
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "fin"));
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.genus"));
        assertNull(errorType(m, "Encounter.mediaAsset0.iaClass"));
    }

    @Test void samePhotoTwiceInARowWithABoxIsADuplicate() {
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "body", "Encounter.mediaAsset2", "a.jpg"));
        assertEquals(BulkValidatorException.TYPE_INVALID_DUPLICATE, errorType(m, "Encounter.mediaAsset2"));
        assertNull(errorType(m, "Encounter.mediaAsset0"));
        m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset1", "a.jpg", "Encounter.mediaAsset1.bbox", "1,1,5,5", "Encounter.mediaAsset1.iaClass", "body"));
        assertEquals(BulkValidatorException.TYPE_INVALID_DUPLICATE, errorType(m, "Encounter.mediaAsset1"));
        assertNull(errorType(m, "Encounter.mediaAsset0"));
    }

    @Test void samePhotoTwiceWithoutBoxesIsStillAccepted() {   // (regression)
        assertNoErrors(validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset1", "a.jpg")));
    }

    @Test void objectRowWithDisjointKeysIsHandled() {
        assertNoErrors(validate(row("Encounter.mediaAsset1", "a.jpg", "Encounter.mediaAsset1.bbox", "1,1,5,5", "Encounter.mediaAsset1.iaClass", "body")));
    }

    @Test void validBoxedRowHasNoErrors() {
        Map<String, Object> m = validate(row("Encounter.mediaAsset0", "a.jpg", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "body",
            "Encounter.mediaAsset0.viewpoint", "left", "Encounter.mediaAsset0.theta", "0.1", "Encounter.mediaAsset0.bboxSource", "yolo", "Encounter.quality0", "0.9"));
        assertNoErrors(m);
        assertEquals("1,1,5,5", ((BulkValidator)m.get("Encounter.mediaAsset0.bbox")).getValue());
    }

    @Test void arrayRowsUseTheSameRules() {
        java.util.Set<String> names = new java.util.LinkedHashSet<String>(java.util.List.of("Encounter.genus", "Encounter.specificEpithet", "Encounter.year", "Encounter.mediaAsset0", "Encounter.mediaAsset0.bbox"));
        JSONArray values = new JSONArray().put("Genus").put("species").put(2020).put("a.jpg").put("1,1,5,5");
        Map<String, Object> m = BulkImportUtil.validateRow(names, values, sh, false);
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset0.bbox"));   // no iaClass column in the sheet
    }

    // ---- a slot's columns are found by number, however the index is written ----

    @Test void zeroPaddedSlotColumnsGetTheSameRules() {
        // box without a class: required, reported on the padded column the sheet has
        Map<String, Object> m = validate(row("Encounter.mediaAsset01", "a.jpg", "Encounter.mediaAsset01.bbox", "1,1,5,5"));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset01.bbox"));
        assertFalse(m.containsKey("Encounter.mediaAsset1.bbox"), "no fabricated canonical key");
        // a class the species does not have
        m = validate(row("Encounter.mediaAsset01", "a.jpg", "Encounter.mediaAsset01.bbox", "1,1,5,5", "Encounter.mediaAsset01.iaClass", "nope"));
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.mediaAsset01.iaClass"));
        // a suffix without its box
        m = validate(row("Encounter.mediaAsset01", "a.jpg", "Encounter.mediaAsset01.theta", "0.5"));
        assertEquals(BulkValidatorException.TYPE_REQUIRED_VALUE, errorType(m, "Encounter.mediaAsset01.theta"));
        // a box without a photo
        m = validate(row("Encounter.mediaAsset01.bbox", "1,1,5,5", "Encounter.mediaAsset01.iaClass", "body"));
        assertEquals(BulkValidatorException.TYPE_INVALID_VALUE, errorType(m, "Encounter.mediaAsset01.bbox"));
        // complete, and with the photo, box and class each spelled differently
        assertNoErrors(validate(row("Encounter.mediaAsset01", "a.jpg", "Encounter.mediaAsset01.bbox", "1,1,5,5", "Encounter.mediaAsset01.iaClass", "body")));
        assertNoErrors(validate(row("Encounter.mediaAsset1", "a.jpg", "Encounter.mediaAsset01.bbox", "1,1,5,5", "Encounter.mediaAsset001.iaClass", "body")));
    }

    @Test void sameNumberedColumnUnderTwoSpellingsIsFound() {
        assertNull(BulkImportUtil.findIndexAliases(new java.util.LinkedHashSet<String>(java.util.List.of(
            "Encounter.mediaAsset0", "Encounter.mediaAsset1", "Encounter.mediaAsset1.bbox", "Encounter.keyword1",
            "Encounter.year", "not.a.field"))));
        String[] pair = BulkImportUtil.findIndexAliases(new java.util.LinkedHashSet<String>(java.util.List.of(
            "Encounter.mediaAsset0", "Encounter.mediaAsset1", "Encounter.mediaAsset01")));
        assertNotNull(pair);
        assertEquals("Encounter.mediaAsset1", pair[0]);
        assertEquals("Encounter.mediaAsset01", pair[1]);
        assertNotNull(BulkImportUtil.findIndexAliases(new java.util.LinkedHashSet<String>(java.util.List.of(
            "Encounter.mediaAsset2.bbox", "Encounter.mediaAsset02.bbox"))));
    }

    @Test void boxSlotReadsTheNumberNotTheSpelling() {
        assertEquals(1, BulkImportUtil.boxSlot("Encounter.mediaAsset01.theta", "theta"));
        assertEquals(1, BulkImportUtil.boxSlot("Encounter.mediaAsset1.theta", "theta"));
        assertEquals(-1, BulkImportUtil.boxSlot("Encounter.mediaAsset1.theta", "bbox"));
        assertEquals(-1, BulkImportUtil.boxSlot("Encounter.mediaAsset1", "bbox"));
        assertEquals(-1, BulkImportUtil.boxSlot("not.a.field", "bbox"));
    }

    @Test void sameSlotsLabeledKeywordUnderTwoSpellingsIsFound() {
        BulkImportUtil.labeledKeywordMap = new java.util.LinkedHashMap<String, java.util.Set<String> >();
        BulkImportUtil.labeledKeywordMap.put("colour", new java.util.LinkedHashSet<String>(java.util.List.of("red")));
        try {
            assertNotNull(BulkImportUtil.findIndexAliases(new java.util.LinkedHashSet<String>(java.util.List.of(
                "Encounter.mediaAsset1.colour", "Encounter.mediaAsset01.colour"))));
            assertNull(BulkImportUtil.findIndexAliases(new java.util.LinkedHashSet<String>(java.util.List.of(
                "Encounter.mediaAsset1.colour", "Encounter.mediaAsset2.colour", "Encounter.mediaAsset1"))));
        } finally {
            BulkImportUtil.labeledKeywordMap = null;
        }
    }
}
