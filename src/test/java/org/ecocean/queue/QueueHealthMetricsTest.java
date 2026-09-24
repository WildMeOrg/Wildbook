package org.ecocean.queue;

import java.nio.file.Path;

import io.prometheus.client.CollectorRegistry;
import org.ecocean.metrics.Prometheus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** The scrape-time queue health gauges in /metrics reflect live consumer state. */
public class QueueHealthMetricsTest {
    @TempDir Path tempDir;

    @BeforeEach void isolateQueueBaseDir() {
        FileQueue.overrideQueueBaseDirForTesting(tempDir.toFile());
        QueueUtil.watchdogIntervalSeconds = 3600; // the test runs the check by hand
    }

    @AfterEach void cleanupExecutorsAndState() {
        QueueUtil.cleanup();
        QueueUtil.watchdogIntervalSeconds = QueueUtil.WATCHDOG_INTERVAL_SECONDS;
        FileQueue.overrideQueueBaseDirForTesting(null);
    }

    private static Double sample(CollectorRegistry reg, String name, String queue) {
        if (queue == null) return reg.getSampleValue(name);
        return reg.getSampleValue(name, new String[] { "queue" }, new String[] { queue });
    }

    @Test void deadConsumerShowsUpUntilRestarted() throws Exception {
        final String name = "stub-metrics-" + System.nanoTime();
        final java.util.concurrent.atomic.AtomicInteger polls =
            new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.CountDownLatch release =
            new java.util.concurrent.CountDownLatch(1);
        Queue q = new Queue(name) {
            { this.type = "Stub"; }
            @Override public void publish(String msg) {}
            @Override public void consume(QueueMessageHandler h) {}
            @Override public void shutdown() {}
            @Override public long getQueueSize() {
                return 0;
            }
            @Override public String getNext() {
                if (polls.incrementAndGet() != 1) return null;
                try { // hold the first poll until the test has checked the alive state
                    release.await(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return "{\"m\":\"poison\"}";
            }
        };
        q.messageHandler = new QueueMessageHandler() {
            @Override public boolean handler(String msg) {
                throw new OutOfMemoryError("test: Java heap space");
            }
        };

        QueueUtil.backgroundWithWorkers(q, 1);
        CollectorRegistry reg = Prometheus.queueHealthRegistry();
        assertEquals(1.0, sample(reg, "wildbook_queue_consumers_tracked", null), 0.0,
            "one worker tracked");
        assertEquals(1.0, sample(reg, "wildbook_queue_consumers_alive", name), 0.0,
            "worker alive before it dies");
        release.countDown();

        long end = System.currentTimeMillis() + 15000;
        double dead = 0;
        while (dead < 1 && System.currentTimeMillis() < end) {
            Thread.sleep(100);
            dead = sample(Prometheus.queueHealthRegistry(), "wildbook_queue_consumers_dead", name);
        }
        assertEquals(1.0, dead, 0.0, "a dead worker must be reported as dead");

        assertEquals(1, QueueUtil.checkConsumersOnce(), "watchdog restarts it");
        reg = Prometheus.queueHealthRegistry();
        assertEquals(0.0, sample(reg, "wildbook_queue_consumers_dead", name), 0.0,
            "no longer dead after the restart");
        assertEquals(1.0, sample(reg, "wildbook_queue_consumer_restarts", name), 0.0,
            "the restart is counted");
        assertNotNull(sample(reg, "wildbook_queue_seconds_since_poll", name),
            "seconds since poll is reported");
    }
}
