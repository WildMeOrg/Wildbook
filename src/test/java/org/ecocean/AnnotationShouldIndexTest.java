package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the "should this annotation have an OpenSearch doc?" contract that keeps
 * non-candidate / "trivial" (undetected, no-embedding) annotations out of the
 * annotation index. The predicate ({@link Annotation#shouldIndexInOpenSearch()})
 * and the reconciler desired-state SQL ({@link Annotation#getAllVersionsSql()})
 * MUST stay semantically identical; these tests guard both.
 *
 * <p>Deliberately does NOT use {@code isTrivial()} (bbox geometry): matchable
 * whole-image / unity / spot-crop annotations are geometrically trivial but must
 * still be indexed, so the predicate keys on matchAgainst / embedding presence.</p>
 */
class AnnotationShouldIndexTest {

    @Test void nonCandidate_notIndexed() {
        Annotation ann = new Annotation();
        // default: matchAgainst=false, no embeddings -> non-candidate/trivial
        assertFalse(ann.getMatchAgainst(), "precondition: matchAgainst defaults false");
        assertFalse(ann.shouldIndexInOpenSearch(),
            "annotation with matchAgainst=false and no embeddings must NOT be indexed");
    }

    @Test void matchAgainst_isIndexed() {
        Annotation ann = new Annotation();
        ann.setMatchAgainst(true);
        assertTrue(ann.shouldIndexInOpenSearch(),
            "matchAgainst==true annotation must be indexed");
    }

    @Test void hasEmbedding_isIndexed_evenWhenNotMatchAgainst() {
        Annotation ann = new Annotation();
        org.json.JSONArray vec = new org.json.JSONArray();
        for (int i = 0; i < 8; i++) vec.put(0.1d * i);
        new org.ecocean.Embedding(ann, "miewid", "msv4.1", vec); // constructor auto-attaches
        assertTrue(ann.numberEmbeddings() > 0, "precondition: embedding attached");
        assertFalse(ann.getMatchAgainst(), "precondition: matchAgainst still false");
        assertTrue(ann.shouldIndexInOpenSearch(),
            "annotation carrying an embedding must be indexed even if matchAgainst=false");
    }

    // The reconciler SQL must select the SAME set the predicate accepts, or the
    // reconciler would either churn (re-listing excluded docs as missing) or fail to
    // remove them. Pin the filter shape so drift is caught.
    @Test void getAllVersionsSql_filtersToCandidates() {
        String sql = new Annotation().getAllVersionsSql();
        assertTrue(sql.contains("\"MATCHAGAINST\" = true"),
            "reconciler SQL must filter on matchAgainst: " + sql);
        assertTrue(sql.contains("EXISTS"),
            "reconciler SQL must use EXISTS for the embedding check: " + sql);
        assertTrue(sql.contains("\"EMBEDDING\"") && sql.contains("\"ANNOTATION_ID\""),
            "reconciler SQL must check the EMBEDDING table by ANNOTATION_ID: " + sql);
    }

    // Exact-shape guard: pins the predicate AND the ORDER BY so any drift (or a change
    // that diverges from shouldIndexInOpenSearch()) trips a failure.
    @Test void getAllVersionsSql_exactShape() {
        String expected =
                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" " +
                "WHERE \"MATCHAGAINST\" = true OR EXISTS " +
                "(SELECT 1 FROM \"EMBEDDING\" e WHERE e.\"ANNOTATION_ID\" = \"ANNOTATION\".\"ID\") " +
                "ORDER BY \"MATCHAGAINST\" DESC, version";
        assertEquals(expected, new Annotation().getAllVersionsSql(),
            "getAllVersionsSql drifted from the pinned candidate-filter shape");
    }
}
