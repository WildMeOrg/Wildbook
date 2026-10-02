package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.ecocean.servlet.IAGateway;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for requeue-exhaustion handling. A retryable ml-service failure goes to
 * IAGateway.requeueJobResult(), which permanently gives up after its retry/time cap. Previously
 * that verdict was ignored: the job was silently dropped and the MediaAsset stayed in
 * 'processing-mlservice' forever — non-terminal, so it pinned bulk-import detection below 100%
 * with nothing left to ever complete it (GiraffeSpotter 2026-08). Policy exhaustion
 * (RETRY_CAP/TIME_CAP) must land the asset/task in a terminal error state ('error' counts as
 * detection-complete in ImportTask.iaSummaryJson). EXECUTOR_REJECTED (webapp undeploy) must NOT
 * be terminal: the asset stays recoverable for the startup stale-mlservice reconciler. No
 * containers or DB required — terminal writes are observed via the package-private marker seams.
 */
public class MlServiceProcessorRetryExhaustionTest {
    private static final class NoopGate implements MatchVisibilityGate {
        @Override public GateOutcome gateForBatch(Collection<String> callerAnnotationIds,
            String childTaskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
            throw new UnsupportedOperationException("gate is not reached in these tests");
        }
    }

    private static final class NoopPublisher implements DeferredMatchPublisher {
        @Override public boolean publish(JSONObject payload) {
            return true;
        }
    }

    /** Stubs the requeue verdict and records terminal-state writes instead of touching the DB. */
    private static final class RecordingProcessor extends MlServiceProcessor {
        final IAGateway.RequeueResult requeueVerdict;
        final List<Boolean> requeueIncrements = new ArrayList<Boolean>();
        String detectionFailureMaId = null;
        String detectionFailureCode = null;
        String taskErrorTaskId = null;
        String taskErrorCode = null;

        RecordingProcessor(IAGateway.RequeueResult requeueVerdict) {
            super("context0", new MlServiceClient(), new NoopGate(), new NoopPublisher());
            this.requeueVerdict = requeueVerdict;
        }

        @Override IAGateway.RequeueResult requeueJob(JSONObject jobData, boolean increment) {
            requeueIncrements.add(Boolean.valueOf(increment));
            return requeueVerdict;
        }

        @Override void markDetectionFailure(String maId, String taskId, String code,
            String message) {
            this.detectionFailureMaId = maId;
            this.detectionFailureCode = code;
        }

        @Override void markTaskError(String taskId, String code, String message) {
            this.taskErrorTaskId = taskId;
            this.taskErrorCode = code;
        }
    }

    private static IAException retryable() {
        return new IAException("SERVER_ERROR", "ml-service 500", true, true);
    }

    private static JSONObject job() {
        JSONObject jo = new JSONObject();
        jo.put("mlServiceV2", true);
        jo.put("taskId", "task-1");
        return jo;
    }

    @Test void detectionFailureRequeuesWhileBudgetAllows() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.QUEUED);
        MlServiceJobOutcome out = p.handleRetryableDetectionFailure(job(), "ma-1", "task-1",
            retryable());

        assertEquals(MlServiceJobOutcome.Kind.REQUEUE, out.getKind(), "budget left -> requeue");
        assertEquals(1, p.requeueIncrements.size(), "exactly one requeue attempt");
        assertTrue(p.requeueIncrements.get(0).booleanValue(),
            "increment flag must come from the exception");
        assertNull(p.detectionFailureCode, "no terminal write while the job can still retry");
    }

    @Test void detectionRetryExhaustionLandsTerminal() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.RETRY_CAP);
        MlServiceJobOutcome out = p.handleRetryableDetectionFailure(job(), "ma-1", "task-1",
            retryable());

        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind(),
            "exhaustion is a terminal network-class error, not a silent drop");
        assertEquals("RETRIES_EXHAUSTED", p.detectionFailureCode,
            "the MediaAsset/task must be marked terminal on exhaustion");
        assertEquals("ma-1", p.detectionFailureMaId, "terminal write targets the right asset");
    }

    @Test void detectionTimeCapAlsoLandsTerminal() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.TIME_CAP);
        p.handleRetryableDetectionFailure(job(), "ma-1", "task-1", retryable());

        assertEquals("RETRIES_EXHAUSTED", p.detectionFailureCode,
            "the 2-day queue-time cap is policy exhaustion too");
    }

    @Test void detectionExecutorRejectionStaysRecoverable() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.EXECUTOR_REJECTED);
        MlServiceJobOutcome out = p.handleRetryableDetectionFailure(job(), "ma-1", "task-1",
            retryable());

        assertEquals(MlServiceJobOutcome.Kind.REQUEUE, out.getKind(),
            "undeploy-time rejection must not finalize the job");
        assertNull(p.detectionFailureCode,
            "no terminal write on executor rejection: the asset must stay "
            + "processing-mlservice so the startup reconciler can recover it after redeploy");
    }

    @Test void extractionFailureRequeuesWhileBudgetAllows() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.QUEUED);
        MlServiceJobOutcome out = p.handleRetryableExtractionFailure(job(), "task-1", retryable());

        assertEquals(MlServiceJobOutcome.Kind.REQUEUE, out.getKind(), "budget left -> requeue");
        assertNull(p.taskErrorCode, "no terminal write while the job can still retry");
    }

    @Test void extractionRetryExhaustionLandsTerminal() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.RETRY_CAP);
        MlServiceJobOutcome out = p.handleRetryableExtractionFailure(job(), "task-1", retryable());

        assertEquals(MlServiceJobOutcome.Kind.ERROR_NETWORK, out.getKind(),
            "exhaustion is a terminal network-class error, not a silent drop");
        assertEquals("RETRIES_EXHAUSTED", p.taskErrorCode,
            "the task must be marked terminal on exhaustion");
        assertEquals("task-1", p.taskErrorTaskId, "terminal write targets the right task");
    }

    @Test void extractionExecutorRejectionStaysRecoverable() {
        RecordingProcessor p = new RecordingProcessor(IAGateway.RequeueResult.EXECUTOR_REJECTED);
        MlServiceJobOutcome out = p.handleRetryableExtractionFailure(job(), "task-1", retryable());

        assertEquals(MlServiceJobOutcome.Kind.REQUEUE, out.getKind(),
            "undeploy-time rejection must not finalize the job");
        assertNull(p.taskErrorCode,
            "no terminal write on executor rejection: the inactivity watchdog is the backstop");
    }
}
