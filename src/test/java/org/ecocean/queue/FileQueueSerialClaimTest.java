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
