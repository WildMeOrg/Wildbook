package org.ecocean.ia;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * v2 commit #5: Task.statusInEndState recognizes "dropped-stale" as terminal.
 * The inactivity-timeout watchdog must not flip a deliberately-dropped task
 * (target deleted before the queued ml-service job ran) to "error".
 */
class TaskStatusInEndStateTest {

    @Test void completedIsTerminal() {
        Task t = new Task();
        t.setStatus("completed");
        assertTrue(t.statusInEndState());
    }

    @Test void errorIsTerminal() {
        Task t = new Task();
        t.setStatus("error");
        assertTrue(t.statusInEndState());
    }

    @Test void droppedStaleIsTerminal() {
        Task t = new Task();
        t.setStatus("dropped-stale");
        assertTrue(t.statusInEndState());
    }

    @Test void initiatedIsNotTerminal() {
        Task t = new Task();
        t.setStatus("initiated");
        assertFalse(t.statusInEndState());
    }

    @Test void typoIsNotTerminal() {
        // "completed-foo" must not accidentally pass the terminal check.
        Task t = new Task();
        t.setStatus("completed-foo");
        assertFalse(t.statusInEndState());
    }
}
