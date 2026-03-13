package org.ecocean;

import com.pgvector.PGvector;
import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

class EmbeddingTest {
/*
    to create a (real) MediaAsset or a Feature we need to have a Shepherd!
    TODO: extend to this with mockito/Shepherd
    https://semaphoreci.com/community/tutorials/stubbing-and-mocking-with-mockito-2-and-junit
 */

@Test void basicTest() {
    Annotation ann = new Annotation();
    JSONArray arr = new JSONArray("[1,2,3,4]");
    Embedding emb = new Embedding(ann, "test", "test", arr);
    assertEquals("test test", emb.getMethodDescription());
    assertEquals(emb.vectorLength(), 4);

    assertEquals(ann.numberEmbeddings(), 1);
    assertEquals(ann.getEmbeddingByMethod("test"), emb);
    assertEquals(ann.getEmbeddingByMethod("test", "test"), emb);
    assertNull(ann.getEmbeddingByMethod("test", "fail"));

    // this should have same vector
    Embedding other = new Embedding(null, "foo", "bar", arr);
    assertEquals(ann.findEmbeddingByVector(other), emb);
}

/*
    @Test void createAnnotation() {
        AssetStore store = null; // see note above
        MediaAsset ma = new MediaAsset(store, null);
        Annotation ann = new Annotation("species", ma);

        assertNotNull(ann);
        assertTrue(ann.isTrivial());
        assertEquals(ann.getMediaAsset(), ma);
    }
*/
}
