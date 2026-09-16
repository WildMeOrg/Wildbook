package org.ecocean.queue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Task C concurrency gate: {@link QueueUtil#effectiveWorkers} (the fail-closed
 * clamp) and {@link FileQueue#supportsAtomicMove} (the deployment probe). No containers required.
 */
public class QueueGatingTest {
    @Test void effectiveWorkersClampsAndFailsClosed() {
        // atomic move supported -> clamp into [1,8]
        assertEquals(4, QueueUtil.effectiveWorkers(4, true), "honors configured count when atomic");
        assertEquals(8, QueueUtil.effectiveWorkers(99, true), "clamps to max 8");
        assertEquals(1, QueueUtil.effectiveWorkers(0, true), "clamps to min 1");
        assertEquals(1, QueueUtil.effectiveWorkers(-5, true), "negative clamps to 1");
        // atomic move NOT supported -> always 1, regardless of the requested count (fail closed)
        assertEquals(1, QueueUtil.effectiveWorkers(4, false), "fail closed to 1 when non-atomic");
        assertEquals(1, QueueUtil.effectiveWorkers(8, false), "fail closed to 1 when non-atomic");
    }

    @Test void supportsAtomicMoveIsSafeForBadInput() {
        assertFalse(FileQueue.supportsAtomicMove(null), "null dir -> false");
        assertFalse(FileQueue.supportsAtomicMove(new File("/no/such/dir/here")), "missing dir -> false");
    }

    @Test void supportsAtomicMoveDetectsARealDir() throws Exception {
        File tmp = Files.createTempDirectory("atomicmove-probe-test").toFile();
        try {
            // A normal local temp filesystem supports atomic moves; the probe must not throw and
            // must clean up after itself (no probe files left behind).
            boolean ok = FileQueue.supportsAtomicMove(tmp);
            assertTrue(ok, "local temp dir should support atomic move");
            File[] leftovers = tmp.listFiles();
            assertNotNull(leftovers, "temp dir listable");
            assertEquals(0, leftovers.length, "probe must clean up its temp files");
        } finally {
            tmp.delete();
        }
    }
}
