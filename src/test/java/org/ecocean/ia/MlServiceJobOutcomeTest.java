package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * v2 commit #8: MlServiceJobOutcome factory + accessor behavior.
 */
class MlServiceJobOutcomeTest {

    @Test void okCarriesAnnotationIds() {
        MlServiceJobOutcome o = MlServiceJobOutcome.ok(Arrays.asList("a1", "a2"));
        assertEquals(MlServiceJobOutcome.Kind.OK, o.getKind());
        assertNull(o.getCode());
        assertNull(o.getMessage());
        assertEquals(2, o.getPersistedAnnotationIds().size());
        assertEquals("a1", o.getPersistedAnnotationIds().get(0));
        assertEquals("a2", o.getPersistedAnnotationIds().get(1));
        assertFalse(o.isError());
    }

    @Test void okNullAnnotationListReturnsEmptyList() {
        MlServiceJobOutcome o = MlServiceJobOutcome.ok(null);
        assertNotNull(o.getPersistedAnnotationIds());
        assertEquals(0, o.getPersistedAnnotationIds().size());
        assertFalse(o.isError());
    }

    @Test void okZeroDetectionsIsNotError() {
        MlServiceJobOutcome o = MlServiceJobOutcome.okZeroDetections();
        assertEquals(MlServiceJobOutcome.Kind.OK_ZERO_DETECTIONS, o.getKind());
        assertFalse(o.isError());
        assertEquals(0, o.getPersistedAnnotationIds().size());
    }

    @Test void staleCarriesReason() {
        MlServiceJobOutcome o = MlServiceJobOutcome.stale("encounter deleted");
        assertEquals(MlServiceJobOutcome.Kind.STALE, o.getKind());
        assertEquals("STALE", o.getCode());
        assertEquals("encounter deleted", o.getMessage());
        assertFalse(o.isError());
    }

    @Test void errorFactoriesAreErrors() {
        assertTrue(MlServiceJobOutcome.validationError("INVALID", "bad bbox").isError());
        assertTrue(MlServiceJobOutcome.networkError("NETWORK", "502").isError());
        assertTrue(MlServiceJobOutcome.persistError("PERSIST", "FK").isError());
    }

    @Test void errorFactoriesDefaultCodes() {
        // When the caller passes null code, factories supply a sensible default.
        assertEquals("INVALID", MlServiceJobOutcome.validationError(null, "msg").getCode());
        assertEquals("NETWORK", MlServiceJobOutcome.networkError(null, "msg").getCode());
        assertEquals("PERSIST", MlServiceJobOutcome.persistError(null, "msg").getCode());
    }

    @Test void requeueIsNotError() {
        MlServiceJobOutcome o = MlServiceJobOutcome.requeue();
        assertEquals(MlServiceJobOutcome.Kind.REQUEUE, o.getKind());
        assertFalse(o.isError());
    }

    @Test void persistedAnnotationIdsIsUnmodifiable() {
        // Defensive copy: caller can't mutate after construction.
        List<String> input = new ArrayList<String>(Arrays.asList("a", "b"));
        MlServiceJobOutcome o = MlServiceJobOutcome.ok(input);
        // Mutating the original list does not affect the outcome.
        input.add("c");
        assertEquals(2, o.getPersistedAnnotationIds().size());
        // The returned list rejects mutation.
        assertThrows(UnsupportedOperationException.class,
            () -> o.getPersistedAnnotationIds().add("x"));
    }
}
