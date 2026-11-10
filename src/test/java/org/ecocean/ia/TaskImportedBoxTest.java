package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bulk-import boxes (spec 4.4): the per-box extraction child is recognized by one task parameter. */
class TaskImportedBoxTest {
    @Test void flagReadsTheParameter() {
        Task t = new Task();
        assertFalse(Task.isImportedBoxTask(t));
        t.addParameter(Task.PARAM_IMPORTED_BOX, true);
        assertTrue(Task.isImportedBoxTask(t));
        assertFalse(Task.isImportedBoxTask(null));
    }

    @Test void aFalseFlagIsNotImported() {
        Task t = new Task();
        t.addParameter(Task.PARAM_IMPORTED_BOX, false);
        assertFalse(Task.isImportedBoxTask(t));
    }
}
