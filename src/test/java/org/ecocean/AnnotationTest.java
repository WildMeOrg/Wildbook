package org.ecocean;

// import static org.junit.jupiter.api.Assertions.assertEquals;
import org.ecocean.Annotation;
import org.ecocean.media.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

class AnnotationTest {
/*
    to create a (real) MediaAsset or a Feature we need to have a Shepherd!
    TODO extend to this with mockito/Shepherd
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
}
