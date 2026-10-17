package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.ecocean.Annotation;
import org.ecocean.ia.IA;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * validForIdentification must not consult the legacy IA.properties identificationClassN
 * allowlist: identification eligibility per class is configured in IA.json.
 */
class IBEISIAValidForIdentificationTest {
    private static final String CONTEXT = "context0";

    // non-trivial bbox annotation with a null acmId, so AnnotationLite's static cache is untouched
    private Annotation bboxAnnotation(String iaClass) {
        JSONObject params = new JSONObject();
        params.put("x", 10);
        params.put("y", 20);
        params.put("width", 100);
        params.put("height", 50);
        Feature ft = new Feature(new FeatureType("org.ecocean.boundingBox"), params);
        // validForIdentification stringifies features, and MediaAsset.toString() needs a store
        MediaAsset ma = new MediaAsset(mock(AssetStore.class), null);
        ma.addFeature(ft);
        Annotation ann = new Annotation("Atelopus zeteki", ft, iaClass);
        assertNull(ann.getAcmId(), "fixture must not populate the AnnotationLite cache");
        assertNotNull(ann.getBbox(), "fixture must have a bbox");
        assertFalse(ann.isTrivial(), "fixture must be non-trivial so the class check would apply");
        return ann;
    }

    private MockedStatic<IA> legacyIdentificationClasses(String identificationClass0) {
        MockedStatic<IA> ia = mockStatic(IA.class, Answers.CALLS_REAL_METHODS);
        ia.when(() -> IA.getProperty(eq(CONTEXT), anyString())).thenReturn(null);
        ia.when(() -> IA.getProperty(CONTEXT, "identificationClass0")).thenReturn(
            identificationClass0);
        return ia;
    }

    @Test void ignoresStaleIdentificationClassList() {
        Annotation ann = bboxAnnotation("frog");

        try (MockedStatic<IA> ia = legacyIdentificationClasses("fire_sal")) {
            assertTrue(IBEISIA.validForIdentification(ann, CONTEXT),
                "an iaClass missing from identificationClassN must still be valid");
        }
    }

    @Test void ignoresBlankIdentificationClassList() {
        Annotation ann = bboxAnnotation("frog");

        // "identificationClass0 =" in IA.properties yields "" rather than null
        try (MockedStatic<IA> ia = legacyIdentificationClasses("")) {
            assertTrue(IBEISIA.validForIdentification(ann, CONTEXT),
                "a blank identificationClassN entry must not reject every annotation");
        }
    }

    @Test void nullAnnotationIsInvalid() {
        assertFalse(IBEISIA.validForIdentification(null, CONTEXT));
    }

    @Test void annotationWithoutBboxIsInvalid() {
        Annotation ann = new Annotation("Atelopus zeteki",
            new Feature(new FeatureType("org.ecocean.boundingBox"), new JSONObject()), "frog");

        assertFalse(IBEISIA.validForIdentification(ann, CONTEXT));
    }
}
