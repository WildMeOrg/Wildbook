package org.ecocean.servlet;

import java.io.File;
import java.nio.file.Path;

import org.ecocean.queue.FileQueueTestSupport;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retry-cap boundary + typed verdict for {@link IAGateway#requeueJobResult}. `__queueRetries`
 * counts retries already scheduled, so the cap check must refuse at {@code >= MAX_RETRIES}
 * (exactly 30 retries run). The old {@code >} boundary scheduled a 31st retry before refusing
 * the 32nd call. The verdict type distinguishes permanent policy exhaustion (RETRY_CAP/TIME_CAP,
 * caller marks the work terminal) from executor rejection (caller leaves the work recoverable).
 *
 * Note: QUEUED cases schedule a real delayed publish on the shared requeue executor — a benign
 * side effect in the test JVM (the publish lands in the temp/fallback file-queue dir).
 */
public class IAGatewayRequeueBoundaryTest {
    @TempDir Path tempDir;

    @BeforeEach void isolateQueueBaseDir() {
        FileQueueTestSupport.overrideBaseDir(tempDir.toFile());
    }

    @AfterEach void restoreQueueBaseDir() {
        FileQueueTestSupport.overrideBaseDir(null);
    }

    private static JSONObject jobWithRetries(int retries) {
        JSONObject jo = new JSONObject();
        jo.put("taskId", "task-boundary");
        jo.put("__queueStart", System.currentTimeMillis());
        jo.put("__queueRetries", retries);
        return jo;
    }

    @Test void allowsTheFinalRetryUnderTheCap() {
        JSONObject jo = jobWithRetries(29);
        assertEquals(IAGateway.RequeueResult.QUEUED, IAGateway.requeueJobResult(jo, true),
            "retry #30 (the final one) is within budget");
        assertEquals(30, jo.optInt("__queueRetries"), "increment stamps the next retry count");
    }

    @Test void refusesAtExactlyMaxRetries() {
        assertEquals(IAGateway.RequeueResult.RETRY_CAP,
            IAGateway.requeueJobResult(jobWithRetries(30), true),
            "retry #31 must be refused: the cap is 30");
        assertFalse(IAGateway.requeueJob(jobWithRetries(30), true),
            "boolean contract: refused means false");
    }

    @Test void refusesBeyondMaxRetries() {
        assertEquals(IAGateway.RequeueResult.RETRY_CAP,
            IAGateway.requeueJobResult(jobWithRetries(31), true),
            "beyond the cap must always be refused");
    }

    @Test void refusesWhenMaxQueueTimeExceeded() {
        JSONObject jo = jobWithRetries(0);
        jo.put("__queueStart", System.currentTimeMillis() - (3L * 24L * 60L * 60L * 1000L));
        assertEquals(IAGateway.RequeueResult.TIME_CAP, IAGateway.requeueJobResult(jo, true),
            "a job older than the 2-day queue-time cap must be refused regardless of count");
    }

    @Test void nonIncrementingRetriesRideTheTimeCapNotTheCounter() {
        JSONObject jo = jobWithRetries(0);
        assertEquals(IAGateway.RequeueResult.QUEUED, IAGateway.requeueJobResult(jo, false),
            "non-penalized retry is within budget");
        assertEquals(0, jo.optInt("__queueRetries"),
            "increment=false must not grow the counter: such retries are bounded by the "
            + "2-day wall clock, not MAX_RETRIES (deliberate asymmetry)");
    }

    @Test void accruedCapRefusesEvenNonIncrementingRetries() {
        assertEquals(IAGateway.RequeueResult.RETRY_CAP,
            IAGateway.requeueJobResult(jobWithRetries(30), false),
            "once 30 penalized retries have accrued the job is refused regardless of the "
            + "current failure's increment flag");
    }

    @Test void pendingRequeueSurvivesShutdownViaFlush() {
        // requeueJob reports QUEUED once the DELAYED publish runnable is accepted — the durable
        // spool write happens up to 30s later. destroy()'s shutdownNow() cancels those pending
        // runnables, so it must flush them to the spool (what flushPendingRequeues does) or the
        // already-reported-as-queued job is silently lost across a redeploy.
        JSONObject jo = jobWithRetries(0);
        jo.put("mlServiceV2", true); // routes to the detection spool
        assertEquals(IAGateway.RequeueResult.QUEUED, IAGateway.requeueJobResult(jo, true),
            "accepted for a 30s-delayed publish");

        int flushed = IAGateway.flushPendingRequeues();
        assertTrue(flushed >= 1, "the pending payload must be flushed durably, not lost");
        File spool = new File(tempDir.toFile(), "detection");
        File[] files = spool.isDirectory() ? spool.listFiles() : null;
        assertTrue(files != null && files.length >= 1,
            "the flushed payload must be a real file in the persistent detection spool");

        // exactly-once: a second flush (or the delayed runnable firing later) must not publish
        // the same payloads again
        assertEquals(0, IAGateway.flushPendingRequeues(),
            "already-published payloads must never be flushed twice");
    }
}
