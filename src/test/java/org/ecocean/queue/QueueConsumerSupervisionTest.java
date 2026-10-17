package org.ecocean.queue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Supervision of queue consumers whose periodic task died anyway. The consumer tick deliberately
 * rethrows VirtualMachineError (OutOfMemoryError, StackOverflowError), and scheduleWithFixedDelay
 * then silently cancels the task: the thread stays alive and parked, nothing is logged, and the
 * queue stops until Tomcat restarts. Observed on iot.wildbook.org 2026-09-24: an OutOfMemoryError
 * inside a DataNucleus persist killed the detection consumer for ~12 hours.
 *
 * The watchdog must restart a dead consumer on its own executor, but must never resurrect one that
 * was stopped on purpose (QueueStopException) or one torn down by QueueUtil.cleanup().
 */
public class QueueConsumerSupervisionTest {
    @TempDir Path tempDir;

    @BeforeEach void isolateQueueBaseDir() {
        FileQueue.overrideQueueBaseDirForTesting(tempDir.toFile());
        // checks are run by hand; keep the real watchdog from restarting a consumer mid-test
        QueueUtil.watchdogIntervalSeconds = 3600;
    }

    @AfterEach void cleanupExecutorsAndState() {
        QueueUtil.cleanup();
        QueueUtil.clock = System::currentTimeMillis;
        QueueUtil.watchdogIntervalSeconds = QueueUtil.WATCHDOG_INTERVAL_SECONDS;
        FileQueue.overrideQueueBaseDirForTesting(null);
    }

    private static boolean awaitStopped(String queueName, long timeoutMillis) throws Exception {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            QueueUtil.ConsumerStatus s = statusOf(queueName);
            if ((s != null) && s.isStopped()) return true;
            Thread.sleep(100);
        }
        return false;
    }

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

    private static QueueUtil.ConsumerStatus statusOf(String queueName) {
        for (QueueUtil.ConsumerStatus s : QueueUtil.consumerStatus()) {
            if (s.getQueueName().equals(queueName)) return s;
        }
        return null;
    }

    private static boolean awaitDead(String queueName, long timeoutMillis) throws Exception {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            QueueUtil.ConsumerStatus s = statusOf(queueName);
            if ((s != null) && !s.isAlive()) return true;
            Thread.sleep(100);
        }
        return false;
    }

    // Message 1 makes the handler throw `fatal`; message 2 must still be delivered after the
    // watchdog restarts the consumer.
    private void assertRecoversFrom(final Error fatal) throws Exception {
        final CountDownLatch delivered = new CountDownLatch(1);
        QueueMessageHandler handler = new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                if (msg.contains("poison")) throw fatal;
                delivered.countDown();
                return true;
            }
        };
        final String name = "stub-fatal-" + System.nanoTime();
        StubQueue q = new StubQueue(name, handler) {
            @Override public String getNext() {
                int n = polls.incrementAndGet();
                if (n == 1) return "{\"m\":\"poison\"}";
                if (n == 2) return "{\"m\":\"good\"}";
                return null;
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        assertTrue(awaitDead(name, 15000),
            "precondition: a " + fatal.getClass().getSimpleName() +
            " escaping the tick kills the periodic task");
        assertEquals(1, QueueUtil.checkConsumersOnce(),
            "the watchdog check must restart exactly the one dead consumer");
        assertTrue(delivered.await(15, TimeUnit.SECONDS),
            "after the restart the consumer must deliver the next message");

        QueueUtil.ConsumerStatus s = statusOf(name);
        assertNotNull(s, "the restarted consumer must still be tracked");
        assertTrue(s.isAlive(), "the restarted consumer must report alive");
        assertEquals(1, s.getRestarts(), "restart count must be recorded");
        assertNotNull(s.getLastDeathCause(), "the cause of death must be recorded");
        assertTrue(s.getLastDeathCause().contains(fatal.getClass().getSimpleName()),
            "the recorded cause must name the error: " + s.getLastDeathCause());
    }

    @Test void watchdogRestartsConsumerKilledByOutOfMemoryError() throws Exception {
        assertRecoversFrom(new OutOfMemoryError("test: Java heap space"));
    }

    @Test void watchdogRestartsConsumerKilledByStackOverflowError() throws Exception {
        assertRecoversFrom(new StackOverflowError("test: deep recursion"));
    }

    @Test void watchdogDoesNotRestartIntentionallyStoppedConsumer() throws Exception {
        final CountDownLatch stopSeen = new CountDownLatch(1);
        final String name = "stub-stop-" + System.nanoTime();
        StubQueue q = new StubQueue(name, new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                return true;
            }
        }) {
            @Override public String getNext() throws java.io.IOException {
                polls.incrementAndGet();
                stopSeen.countDown();
                throw new QueueStopException("STOP requested by test");
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        assertTrue(stopSeen.await(15, TimeUnit.SECONDS), "first poll should happen");
        assertTrue(awaitStopped(name, 15000), "the intentional stop shuts the executor down");
        assertEquals(0, QueueUtil.checkConsumersOnce(),
            "an intentional stop must never be undone by the watchdog");
        int settled = q.polls.get();
        Thread.sleep(2500);
        assertEquals(settled, q.polls.get(), "the stopped consumer must stay stopped");
        QueueUtil.ConsumerStatus s = statusOf(name);
        assertNotNull(s, "a stopped consumer stays visible in the status");
        assertTrue(s.isStopped(), "an intentional stop must report stopped, not dead");
    }

    @Test void cleanupStopsSupervisionAndForgetsConsumers() throws Exception {
        final String name = "stub-cleanup-" + System.nanoTime();
        StubQueue q = new StubQueue(name, new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                return true;
            }
        }) {
            @Override public String getNext() {
                polls.incrementAndGet();
                return null;
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        assertNotNull(statusOf(name), "consumer should be tracked while running");
        QueueUtil.cleanup();

        List<QueueUtil.ConsumerStatus> after = QueueUtil.consumerStatus();
        assertTrue(after.isEmpty(), "cleanup must forget all consumers: " + after);
        assertEquals(0, QueueUtil.checkConsumersOnce(),
            "nothing may be restarted after cleanup");
        // the executor reports terminated a moment before its thread actually exits
        long end = System.currentTimeMillis() + 5000;
        boolean watchdogThreadAlive = true;
        while (watchdogThreadAlive && System.currentTimeMillis() < end) {
            watchdogThreadAlive = false;
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.isAlive() && t.getName().equals(QueueUtil.WATCHDOG_THREAD_NAME))
                    watchdogThreadAlive = true;
            }
            if (watchdogThreadAlive) Thread.sleep(100);
        }
        assertFalse(watchdogThreadAlive, "the watchdog thread must be gone after cleanup");
    }

    @Test void repeatedDeathsAreRestartedWithBackoff() throws Exception {
        final java.util.concurrent.atomic.AtomicLong now =
            new java.util.concurrent.atomic.AtomicLong(1000000L);
        QueueUtil.clock = now::get;
        final String name = "stub-storm-" + System.nanoTime();
        StubQueue q = new StubQueue(name, new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                throw new OutOfMemoryError("test: every message");
            }
        }) {
            @Override public String getNext() {
                polls.incrementAndGet();
                return "{\"m\":\"poison\"}";
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        assertTrue(awaitDead(name, 15000), "first death");
        assertEquals(1, QueueUtil.checkConsumersOnce(), "the first death restarts immediately");

        assertTrue(awaitDead(name, 15000), "second death");
        assertEquals(0, QueueUtil.checkConsumersOnce(),
            "a second death in a row must wait for the backoff, not restart at once");
        now.addAndGet(QueueUtil.RESTART_BACKOFF_BASE_MILLIS - 1);
        assertEquals(0, QueueUtil.checkConsumersOnce(), "still inside the backoff window");
        now.addAndGet(1);
        assertEquals(1, QueueUtil.checkConsumersOnce(), "restarted once the backoff elapsed");

        // the backoff is measured from when the watchdog detects the death
        assertTrue(awaitDead(name, 15000), "third death");
        assertEquals(0, QueueUtil.checkConsumersOnce(), "third death detected, backing off");
        now.addAndGet(2 * QueueUtil.RESTART_BACKOFF_BASE_MILLIS - 1);
        assertEquals(0, QueueUtil.checkConsumersOnce(), "the backoff doubles on each death");
        now.addAndGet(1);
        assertEquals(1, QueueUtil.checkConsumersOnce(), "restarted after the doubled backoff");
        assertEquals(3, statusOf(name).getRestarts(), "three restarts recorded");
    }

    @Test void restartBackoffIsCapped() {
        assertEquals(0, QueueUtil.restartBackoffMillis(1), "first death: immediate");
        assertEquals(QueueUtil.RESTART_BACKOFF_BASE_MILLIS, QueueUtil.restartBackoffMillis(2),
            "second death: base backoff");
        assertEquals(QueueUtil.RESTART_BACKOFF_MAX_MILLIS, QueueUtil.restartBackoffMillis(50),
            "backoff never exceeds the cap");
    }
}
