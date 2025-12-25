package org.ecocean;

import org.ecocean.Annotation;
import org.ecocean.media.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

class AnnotationTest {
/*
    to create a (real) MediaAsset or a Feature we need to have a Shepherd!
    TODO: extend to this with mockito/Shepherd
    https://semaphoreci.com/community/tutorials/stubbing-and-mocking-with-mockito-2-and-junit
 */
    @Test void createAnnotation() {
        AssetStore store = null; // see note above
        MediaAsset ma = new MediaAsset(store, null);
        Annotation ann = new Annotation("species", ma);

        assertNotNull(ann);
        assertTrue(ann.isTrivial());
        assertEquals(ann.getMediaAsset(), ma);
    }

    @Test void comparisons() {
        Annotation a1 = new Annotation();
        Annotation a2 = new Annotation();
        a1.setIAClass("foo");
        a2.setIAClass("bar");
        assertFalse(a1.equalsIAClass(a2));
        a2.setIAClass("foo");
        assertTrue(a1.equalsIAClass(a2));

        AssetStore store = null;
        MediaAsset ma = new MediaAsset(store, null);

        FeatureType ftype = new FeatureType("org.ecocean.boundingBox");
        JSONObject p1 = new JSONObject();
        p1.put("x", 1);
        p1.put("y", 2);
        p1.put("width", 3);
        p1.put("height", 4);
        p1.put("theta", 5);
        Feature ft1 = new Feature(ftype, p1);
        JSONObject p2 = new JSONObject(p1.toString());
        Feature ft2 = new Feature(ftype, p2);
        ma.addFeature(ft1);
        ma.addFeature(ft2);

        Annotation a3 = new Annotation("species", ft1);
        Annotation a4 = new Annotation("species", ft2);
        assertTrue(a3.equalsBbox(a4));
        assertTrue(a3.equalsTheta(a4));
        assertTrue(a3.equalsShape(a4));

        p2.put("height", 100);
        ft2.setParameters(p2);
        assertFalse(a3.equalsShape(a4));

        assertTrue(a3.equalsViewpoint(a4));
        a3.setViewpoint("foo");
        assertFalse(a3.equalsViewpoint(a4));
    }
}
