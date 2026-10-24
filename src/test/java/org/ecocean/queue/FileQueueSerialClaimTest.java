package org.ecocean.queue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the single-consumer (serial) claim path. A single IA consumer must be able to
 * claim and consume messages on ANY filesystem -- the concurrency gate clamps to one worker when
 * atomic moves are unsupported, and that lone worker must still make progress (it uses the original
 * non-atomic rename claim, not ATOMIC_MOVE). No containers required.
 */
public class FileQueueSerialClaimTest {
    @Test void checkedPublicationCanBeConsumedAndReportsFilesystemFailure(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        FileQueue.init("context0"); FileQueue queue = new FileQueue("test-checked-" + System.nanoTime());
        java.lang.reflect.Field dir = FileQueue.class.getDeclaredField("queueDir"); dir.setAccessible(true); dir.set(queue, root.toFile());
        java.nio.file.Files.writeString(root.resolve("addToQueue-incomplete.tmp"), "partial");
        assertNull(queue.getNext(), "consumer must ignore unfinished publication");
        queue.publishChecked("{\"unicode\":\"zèbre\"}");
        assertEquals("{\"unicode\":\"zèbre\"}", queue.getNext());
        java.nio.file.Path notDirectory = java.nio.file.Files.createFile(root.resolve("not-directory"));
        dir.set(queue, notDirectory.toFile());
        assertThrows(java.io.IOException.class, () -> queue.publishChecked("message"));
    }
    @Test void serialConsumerClaimsEveryMessage() throws Exception {
        FileQueue.init("context0");
        // Unique queue name -> isolated subdir under the (possibly shared) base dir.
        FileQueue q = new FileQueue("test-serial-" + System.nanoTime());
        // requireAtomicClaim defaults to false: this is the serial, non-atomic path.
        q.publish("{\"msg\":\"a\"}");
        q.publish("{\"msg\":\"b\"}");
        q.publish("{\"msg\":\"c\"}");

        Set<String> got = new HashSet<String>();
        String m;
        int guard = 0;
        while (((m = q.getNext()) != null) && (guard++ < 100)) {
            got.add(m);
        }
        assertEquals(3, got.size(), "serial consumer must claim all 3 messages on a normal filesystem");
        assertTrue(got.contains("{\"msg\":\"a\"}"), "got message a");
        assertTrue(got.contains("{\"msg\":\"b\"}"), "got message b");
        assertTrue(got.contains("{\"msg\":\"c\"}"), "got message c");
    }
}
