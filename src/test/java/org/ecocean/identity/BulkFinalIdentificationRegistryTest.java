package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BulkFinalIdentificationRegistryTest {

    @AfterEach void reset() {
        BulkFinalIdentificationRegistry.clearForTesting();
    }

    @Test void firstClaimReturnsTrue() {
        assertTrue(BulkFinalIdentificationRegistry.tryClaim("task-1"));
    }

    @Test void subsequentClaimsReturnFalse() {
        assertTrue(BulkFinalIdentificationRegistry.tryClaim("task-1"));
        assertFalse(BulkFinalIdentificationRegistry.tryClaim("task-1"));
        assertFalse(BulkFinalIdentificationRegistry.tryClaim("task-1"));
    }

    @Test void differentTaskIdsIndependent() {
        assertTrue(BulkFinalIdentificationRegistry.tryClaim("task-1"));
        assertTrue(BulkFinalIdentificationRegistry.tryClaim("task-2"));
    }

    @Test void nullTaskIdReturnsFalse() {
        assertFalse(BulkFinalIdentificationRegistry.tryClaim(null));
    }

    @Test void releaseAllowsReclaim() {
        assertTrue(BulkFinalIdentificationRegistry.tryClaim("task-1"));
        assertFalse(BulkFinalIdentificationRegistry.tryClaim("task-1"));
        BulkFinalIdentificationRegistry.release("task-1");
        assertTrue(BulkFinalIdentificationRegistry.tryClaim("task-1"));
    }

    @Test void releaseNullIsNoOp() {
        BulkFinalIdentificationRegistry.release(null);
    }

    @Test void concurrentClaimExactlyOneWins() throws Exception {
        int N = 50;
        ExecutorService ex = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger(0);
        for (int i = 0; i < N; i++) {
            ex.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                if (BulkFinalIdentificationRegistry.tryClaim("hot-task")) wins.incrementAndGet();
            });
        }
        start.countDown();
        ex.shutdown();
        assertTrue(ex.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, wins.get(), "exactly one thread should win the claim");
    }
}
