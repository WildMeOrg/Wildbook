package org.ecocean;

import com.pgvector.PGvector;
import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.ia.Task;
import org.json.JSONArray;
import org.json.JSONObject;
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
    assertEquals("test-test", emb.getMethodDescription());
    assertEquals(emb.vectorLength(), 4);

    assertEquals(ann.numberEmbeddings(), 1);
    assertEquals(ann.getEmbeddingByMethod("test"), emb);
    assertEquals(ann.getEmbeddingByMethod("test", "test"), emb);
    assertNull(ann.getEmbeddingByMethod("test", "fail"));
    assertTrue(ann.getEmbeddingCounts().containsKey("test-test"));
    assertTrue(ann.getEmbeddingCounts().get("test-test") == 1);

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

    // Migration v2 §commit #3: pure-logic gate tests for findMatchProspects.
    // The deeper invariants (count-before-mutate, parent-task-status from
    // subtask outcomes) need a real Annotation/Shepherd/OpenSearch to
    // exercise; those are validated end-to-end in the processor commit.

    @Test void findMatchProspects_returnsFalseForNullConfig() {
        assertFalse(Embedding.findMatchProspects(null, null, null));
    }

    @Test void findMatchProspects_returnsFalseForNonVectorConfig() {
        // Neither method nor api_endpoint set → not a vector config.
        JSONObject conf = new JSONObject().put("pipeline_root", "HotSpotter");
        assertFalse(Embedding.findMatchProspects(conf, null, null));
    }

    @Test void findMatchProspects_acceptsMethodOnlyConfig() {
        // New _id_conf contract: method/version present, no api_endpoint.
        // task=null branch returns true once the gate accepts.
        JSONObject conf = new JSONObject()
            .put("method", "miewid").put("version", "4.1")
            .put("pipeline_root", "vector");
        assertTrue(Embedding.findMatchProspects(conf, null, null));
    }

    @Test void findMatchProspects_acceptsLegacyApiEndpointConfig() {
        // Legacy: api_endpoint present, no method. Must keep working.
        JSONObject conf = new JSONObject().put("api_endpoint", "http://legacy");
        assertTrue(Embedding.findMatchProspects(conf, null, null));
    }

    @Test void findMatchProspects_zeroAnnotationsCompletesParent() {
        JSONObject conf = new JSONObject().put("method", "miewid");
        Task task = new Task();   // no annotations
        boolean rtn = Embedding.findMatchProspects(conf, task, null);
        assertTrue(rtn);
        // Task.getStatus(Shepherd) is the public accessor and runs an
        // inactivity-timeout check that needs the persistent state. For a
        // unit test on a brand-new in-memory Task, we go through the
        // statusInEndState predicate, which only inspects this.status.
        assertTrue(task.statusInEndState());
        assertNotNull(task.getCompletionDateInMilliseconds());
    }
}
