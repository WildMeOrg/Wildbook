package org.ecocean.queue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for consumer-loop resilience. The poll loop runs via
 * ScheduledExecutorService.scheduleWithFixedDelay, whose contract silently cancels the periodic
 * task if any execution throws — so every failure mode here previously killed the consumer until
 * the next Tomcat restart (2-day silent production outage, GiraffeSpotter 2026-08):
 *
 * - any Exception from getNext() halted the executor by design ("halting" + shutdown), so one
 *   transient I/O error permanently stopped the queue;
 * - a RuntimeException escaping the message handler wasn't caught at all and suppressed the
 *   periodic task with ZERO log output.
 *
 * Only an intentional stop ({@link QueueStopException}: operator STOP file, SHUTDOWN message) may
 * shut a consumer down. No containers required.
 *
 * State hygiene: FileQueue's base dir is a JVM-wide static; each test points it at an isolated
 * {@code @TempDir} and restores the lazy init afterwards. QueueUtil.cleanup() is global (it stops
 * ALL tracked consumer executors) — acceptable here because Surefire runs tests sequentially and
 * no other live consumers exist during this class.
 */
public class QueueConsumerResilienceTest {
    @TempDir Path tempDir;

    @BeforeEach void isolateQueueBaseDir() {
        FileQueue.overrideQueueBaseDirForTesting(tempDir.toFile());
    }

    @AfterEach void cleanupExecutorsAndState() {
        QueueUtil.cleanup();
        FileQueue.overrideQueueBaseDirForTesting(null);
    }

    /** Minimal in-memory Queue: getNext() behavior is driven by the test via call count. */
    private abstract static class StubQueue extends Queue {
        final AtomicInteger polls = new AtomicInteger(0);
        StubQueue(String name, QueueMessageHandler handler) {
            super(name);
            this.type = "Stub";
            this.messageHandler = handler;
        }

        @Override public void publish(String msg) {}

        @Override public void consume(QueueMessageHandler msgHandler) {}

        @Override public void shutdown() {}

        @Override public long getQueueSize() {
            return 0;
        }
    }

    @Test void consumerSurvivesTransientGetNextFailure() throws Exception {
        final CountDownLatch delivered = new CountDownLatch(1);
        QueueMessageHandler handler = new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                delivered.countDown();
                return true;
            }
        };
        StubQueue q = new StubQueue("stub-transient-" + System.nanoTime(), handler) {
            @Override public String getNext() throws IOException {
                int n = polls.incrementAndGet();
                if (n == 1) throw new IOException("transient disk hiccup");
                if (n == 2) return "{\"m\":1}";
                return null;
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        assertTrue(delivered.await(15, TimeUnit.SECONDS),
            "a single transient getNext() failure must not kill the consumer; "
            + "the next tick must still deliver the queued message");
    }

    @Test void consumerSurvivesHandlerRuntimeException() throws Exception {
        final CountDownLatch delivered = new CountDownLatch(1);
        QueueMessageHandler handler = new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                if (msg.contains("bad")) throw new RuntimeException("handler bug on this message");
                delivered.countDown();
                return true;
            }
        };
        StubQueue q = new StubQueue("stub-handlerboom-" + System.nanoTime(), handler) {
            @Override public String getNext() {
                int n = polls.incrementAndGet();
                if (n == 1) return "{\"m\":\"bad\"}";
                if (n == 2) return "{\"m\":\"good\"}";
                return null;
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        assertTrue(delivered.await(15, TimeUnit.SECONDS),
            "a RuntimeException from the handler must cost that one message, not the consumer; "
            + "the next message must still be delivered");
    }

    @Test void queueStopExceptionStopsAllWorkers() throws Exception {
        final CountDownLatch stopSeen = new CountDownLatch(1);
        QueueMessageHandler handler = new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                fail("handler must not run after an intentional stop");
                return true;
            }
        };
        StubQueue q = new StubQueue("stub-stop-" + System.nanoTime(), handler) {
            @Override public String getNext() throws IOException {
                polls.incrementAndGet();
                stopSeen.countDown();
                throw new QueueStopException("STOP requested by test");
            }
        };

        // two workers share one executor: the intentional stop must halt BOTH
        QueueUtil.backgroundWithWorkers(q, 2);
        assertTrue(stopSeen.await(15, TimeUnit.SECONDS), "first poll should happen");
        // shutdown() lets already-started invocations finish; wait for the count to stabilize,
        // then require it to STAY stable — polls resuming means a worker survived the stop
        Thread.sleep(2500);
        int settled = q.polls.get();
        Thread.sleep(2500);
        assertEquals(settled, q.polls.get(),
            "no worker may poll again after an intentional QueueStopException stop");
    }

    @Test void stopFileAndShutdownMessageSignalIntentionalStop() throws Exception {
        FileQueue q = new FileQueue("test-stop-" + System.nanoTime());

        // SHUTDOWN message -> QueueStopException (not a generic IOException)
        q.publish("SHUTDOWN");
        assertThrows(QueueStopException.class, () -> q.getNext(),
            "a SHUTDOWN message must surface as the intentional-stop type");

        // operator STOP file -> QueueStopException, and it wins over queued messages
        q.publish("{\"m\":1}");
        File stop = new File(q.getQueueDir(), "STOP");
        Files.write(stop.toPath(), new byte[0]);
        try {
            assertThrows(QueueStopException.class, () -> q.getNext(),
                "a STOP file must surface as the intentional-stop type");
        } finally {
            stop.delete();
        }
    }

    @Test void publishFailureThrowsInsteadOfLoggingSuccess() throws Exception {
        FileQueue q = new FileQueue("test-pubfail-" + System.nanoTime());

        // deleting the spool dir makes the write fail; publish must THROW, not log success
        assertTrue(q.getQueueDir().delete(), "empty spool dir should delete");
        assertThrows(IOException.class, () -> q.publish("{\"m\":1}"),
            "publish into a missing spool dir must throw");
    }
}
