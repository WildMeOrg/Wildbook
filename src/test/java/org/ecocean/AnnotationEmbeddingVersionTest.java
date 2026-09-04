package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pgvector.PGvector;
import javax.jdo.PersistenceManager;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

/**
 * Attaching or removing an embedding must bump the annotation's version, so the OpenSearch
 * reconciler (Base.opensearchSyncIndex) treats the annotation as stale and reindexes it —
 * which re-serializes the embedding vector into the document _source. Without the bump, a freshly
 * embedded annotation is searchable for kNN matching but its vector never appears in the
 * token-readable _source. Guards the fix in Annotation.addEmbedding()/deleteEmbeddings().
 *
 * Annotation/Embedding identity (equals/hashCode) is id-based, so both objects are given ids here
 * (as persisted objects always have) — without ids the reciprocal addEmbedding/setAnnotation wiring
 * cannot settle.
 */
class AnnotationEmbeddingVersionTest {

    private static Annotation annotationWithId() {
        Annotation ann = new Annotation();
        ann.setId(Util.generateUUID());
        return ann;
    }

    // An embedding with a stable id, not yet wired to any annotation (passing null avoids the
    // constructor's own addEmbedding call).
    private static Embedding looseEmbedding() {
        return new Embedding(null, "miewid", "msv4.1", (PGvector) null);
    }

    @Test void addEmbedding_bumps_version() {
        Annotation ann = annotationWithId();
        long before = ann.getVersion();

        ann.addEmbedding(looseEmbedding());

        assertEquals(1, ann.numberEmbeddings(), "embedding was attached");
        assertTrue(ann.getVersion() > before,
            "addEmbedding() must bump the annotation version so the reconciler reindexes it");
    }

    @Test void addEmbedding_duplicate_does_not_rebump() {
        Annotation ann = annotationWithId();
        Embedding emb = looseEmbedding();
        ann.addEmbedding(emb);
        long afterFirst = ann.getVersion();

        ann.addEmbedding(emb); // same embedding, already attached -> no-op

        assertEquals(1, ann.numberEmbeddings(), "still one embedding");
        assertEquals(afterFirst, ann.getVersion(),
            "re-adding the same embedding must not bump the version (avoids reconciler churn)");
    }

    @Test void deleteEmbeddings_bumps_version() {
        Annotation ann = annotationWithId();
        ann.addEmbedding(looseEmbedding());
        assertEquals(1, ann.numberEmbeddings(), "precondition: one embedding attached");

        PersistenceManager pm = mock(PersistenceManager.class);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);

        long before = ann.getVersion();

        int removed = ann.deleteEmbeddings(sh);

        assertEquals(1, removed, "deleteEmbeddings() returns the count removed");
        verify(pm).deletePersistent(any());
        assertTrue(ann.getVersion() > before,
            "deleteEmbeddings() must bump the annotation version so the reconciler reindexes it");
    }
}
