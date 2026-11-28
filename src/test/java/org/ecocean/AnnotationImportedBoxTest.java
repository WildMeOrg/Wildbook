package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Bulk-import boxes (spec 4.1, 4.4): an annotation created from a user-supplied box carries the
 * provenance marker {@code _bulkImportBbox} in its feature parameters, and every later rule keys on
 * that marker, never on geometry.
 */
class AnnotationImportedBoxTest {
    private static JSONObject box() {
        return new JSONObject().put("x", 1).put("y", 1).put("width", 5).put("height", 5);
    }

    private static Annotation withFeatureParams(JSONObject params) {
        Feature f = new Feature(new FeatureType("org.ecocean.boundingBox"), params);
        return new Annotation(null, f, "body");
    }

    @Test void plainBoxIsNotImported() {
        Annotation a = withFeatureParams(box());
        assertFalse(a.isImportedBox());
        assertNull(a.importedBoxImportTaskId());
    }

    @Test void markerMakesItImportedAndCarriesTheImportId() {
        Annotation a = withFeatureParams(box().put(Annotation.FEATURE_PARAM_IMPORTED_BOX,
            new JSONObject().put("importTaskId", "imp-1").put("created", 1L)));
        assertTrue(a.isImportedBox());
        assertEquals("imp-1", a.importedBoxImportTaskId());
    }

    @Test void markerWithoutImportIdHasNullImportId() {
        Annotation a = withFeatureParams(box().put(Annotation.FEATURE_PARAM_IMPORTED_BOX,
            new JSONObject()));
        assertTrue(a.isImportedBox());
        assertNull(a.importedBoxImportTaskId());
    }

    @Test void noFeaturesIsNotImported() {
        assertFalse(new Annotation().isImportedBox());
        assertNull(new Annotation().importedBoxImportTaskId());
    }
}
