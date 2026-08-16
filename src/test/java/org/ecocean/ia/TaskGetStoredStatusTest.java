package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link Task#getStoredStatus()} — the raw, side-effect-free
 * accessor needed by the empty-match-prospects Track 2 batch gate so a
 * read of a sibling task's status doesn't trigger the timed-out-task
 * mutation that {@link Task#getStatus(org.ecocean.shepherd.core.Shepherd)}
 * performs. (Empty-match-prospects design Track 2 C7.)
 */
class TaskGetStoredStatusTest {

    @Test void returnsNull_whenStatusUnset() {
        assertNull(new Task().getStoredStatus());
    }

    @Test void returnsValue_whenStatusSet() {
        Task t = new Task();
        t.setStatus("completed");
        assertEquals("completed", t.getStoredStatus());
    }

    @Test void returnsValue_forNonTerminalStatus() {
        Task t = new Task();
        t.setStatus("processing-mlservice");
        assertEquals("processing-mlservice", t.getStoredStatus());
    }

    @Test void doesNotMutate_evenWhenTaskWouldTimeOutInGetStatus()
    throws Exception {
        // Force the conditions under which getStatus(Shepherd) would flip
        // status to "error": non-terminal status, timedOutDueToInactivity
        // returns true. getStoredStatus must NOT trigger that mutation;
        // it just returns the persisted value.
        Task t = new Task();
        t.setStatus("processing-mlservice");  // non-terminal
        // Reach in and backdate `modified` past TIMEOUT_INACTIVE_MILLIS
        // (7 days). Reflection is the surgical way; the field is private
        // with no public setter for arbitrary past times.
        java.lang.reflect.Field modifiedField = Task.class.getDeclaredField("modified");
        modifiedField.setAccessible(true);
        long longAgo = System.currentTimeMillis() - (Task.TIMEOUT_INACTIVE_MILLIS + 1000L);
        modifiedField.setLong(t, longAgo);
        java.lang.reflect.Field createdField = Task.class.getDeclaredField("created");
        createdField.setAccessible(true);
        createdField.setLong(t, longAgo);

        // Sanity: timeout predicate now returns true.
        org.junit.jupiter.api.Assertions.assertTrue(t.timedOutDueToInactivity());

        // getStoredStatus reads without mutating.
        assertEquals("processing-mlservice", t.getStoredStatus());

        // Confirm the underlying field is still the original value: a
        // second read still sees the pre-timeout value because the
        // accessor never wrote.
        assertEquals("processing-mlservice", t.getStoredStatus());
    }
}
