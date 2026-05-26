package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the gate/publisher wiring in
 * {@link MlServiceProcessor}'s deferred-match path. Uses the
 * package-visible test constructor to inject a
 * {@link MatchVisibilityGate} stub and a recording
 * {@link DeferredMatchPublisher}. Sidesteps Shepherd/OpenSearch:
 * a DEFER outcome short-circuits before runMatchProspects, so the
 * tests assert on the published payload without needing a DB.
 *
 * <p>(Empty-match-prospects design Track 2 C11.)</p>
 */
class MlServiceProcessorGateTest {

    /** Records the payload(s) published by the processor under test. */
    private static final class RecordingPublisher implements DeferredMatchPublisher {
        final List<JSONObject> published = new ArrayList<JSONObject>();
        @Override public void publish(JSONObject payload) {
            published.add(payload);
        }
    }

    /** Always returns a fixed gate outcome. */
    private static final class StubGate implements MatchVisibilityGate {
        final GateOutcome fixed;
        StubGate(GateOutcome fixed) { this.fixed = fixed; }
        @Override public GateOutcome gateForBatch(
            Collection<String> callerAnnotationIds, String childTaskId,
            JSONObject matchConfig, int attempt, Long firstDeferredAt) {
            return fixed;
        }
    }

    private static MlServiceProcessor processorWith(MatchVisibilityGate gate,
        DeferredMatchPublisher publisher) {
        // Override readSkipIdent to false so existing gate tests don't
        // exercise the no-DB fallback path (which logs a PMF warning).
        // The two skipIdent-specific tests below override this seam with
        // their own values via processorWithSkipIdent.
        return new MlServiceProcessor("context0", new MlServiceClient(),
            gate, publisher) {
            @Override boolean readSkipIdent(String taskId) { return false; }
        };
    }

    private static JSONObject deferredJobPayload(int attempt,
        Long firstDeferredAt) {
        JSONObject jo = new JSONObject();
        jo.put("mlServiceV2", true);
        jo.put("deferredMatch", true);
        jo.put("annotationIds", new JSONArray().put("ann-1").put("ann-2"));
        jo.put("taskId", "task-1");
        jo.put("matchConfig", new JSONObject()
            .put("method", "miewid-msv4.1").put("version", "4.1"));
        jo.put("attempt", attempt);
        if (firstDeferredAt != null) {
            jo.put("firstDeferredAt", firstDeferredAt.longValue());
        }
        return jo;
    }

    // --- DEFER path: publisher receives a payload -----------------------

    @Test void runDeferredMatch_publishesPayload_onGateDefer() {
        long firstDeferred = System.currentTimeMillis();
        MatchVisibilityGate.GateOutcome defer =
            MatchVisibilityGate.GateOutcome.defer(2, firstDeferred,
                "sibling MA 42 non-terminal");
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(new StubGate(defer), publisher);
        MlServiceJobOutcome out = p.runDeferredMatch(
            deferredJobPayload(2, firstDeferred));
        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertEquals(1, publisher.published.size(),
            "expected exactly one re-published payload");
    }

    @Test void publishedPayloadCarriesBothRoutingFlags() {
        // Codex round-5 Blocker: IAGateway dispatches v2 jobs only when
        // mlServiceV2==true; MlServiceProcessor branches deferred only
        // when deferredMatch==true. Both required.
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "non-terminal")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        JSONObject payload = publisher.published.get(0);
        assertTrue(payload.optBoolean("mlServiceV2", false),
            "missing mlServiceV2: " + payload);
        assertTrue(payload.optBoolean("deferredMatch", false),
            "missing deferredMatch: " + payload);
    }

    @Test void publishedPayloadIncrementsAttempt() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                3, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(3, firstDeferred));
        JSONObject payload = publisher.published.get(0);
        assertEquals(4, payload.optInt("attempt", -1));
    }

    @Test void publishedPayloadPreservesFirstDeferredAt() {
        // Age-out is measured by elapsed wall-clock from the original
        // DEFER, so firstDeferredAt must be carried forward unchanged
        // across re-fires (Codex round-4 OQ #1).
        long firstDeferred = 1700000000000L;
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        assertEquals(firstDeferred,
            publisher.published.get(0).optLong("firstDeferredAt"));
    }

    @Test void publishedPayloadCarriesLastGateReason() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "sibling MA 42 processing-mlservice")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        assertEquals("sibling MA 42 processing-mlservice",
            publisher.published.get(0).optString("lastGateReason"));
    }

    @Test void publishedPayloadCarriesAnnotationIdsAndTaskId() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        JSONObject payload = publisher.published.get(0);
        JSONArray ids = payload.optJSONArray("annotationIds");
        assertNotNull(ids);
        assertEquals(2, ids.length());
        assertEquals("ann-1", ids.optString(0));
        assertEquals("ann-2", ids.optString(1));
        assertEquals("task-1", payload.optString("taskId"));
    }

    @Test void publishedPayloadCarriesContext() {
        long firstDeferred = System.currentTimeMillis();
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.defer(
                2, firstDeferred, "still waiting")),
            publisher);
        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));
        assertEquals("context0",
            publisher.published.get(0).optString("__context"));
    }

    // --- runDeferredMatch input validation ------------------------------

    @Test void runDeferredMatch_returnsValidationError_onNullPayload() {
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWith(
            new StubGate(MatchVisibilityGate.GateOutcome.ready(
                1, System.currentTimeMillis())),
            publisher);
        MlServiceJobOutcome out = p.runDeferredMatch(null);
        assertEquals(MlServiceJobOutcome.Kind.ERROR_VALIDATION, out.getKind());
        assertEquals(0, publisher.published.size(),
            "publisher should not fire on validation error");
    }

    // --- skipIdent honored on bulk-import "Skip identification" ---------

    /** Recording gate so we can assert whether gateForBatch was invoked. */
    private static final class RecordingGate implements MatchVisibilityGate {
        final GateOutcome fixed;
        int calls = 0;
        RecordingGate(GateOutcome fixed) { this.fixed = fixed; }
        @Override public GateOutcome gateForBatch(
            Collection<String> callerAnnotationIds, String childTaskId,
            JSONObject matchConfig, int attempt, Long firstDeferredAt) {
            calls++;
            return fixed;
        }
    }

    /** Processor whose skipIdent read is forced to a fixed value. */
    private static MlServiceProcessor processorWithSkipIdent(
        MatchVisibilityGate gate, DeferredMatchPublisher publisher,
        boolean skipIdent) {
        return new MlServiceProcessor("context0", new MlServiceClient(),
            gate, publisher) {
            @Override boolean readSkipIdent(String taskId) { return skipIdent; }
        };
    }

    // When the IA Task's params carry skipIdent=true (the bulk-import
    // "Skip identification" checkbox), waitAndRunMatchInternal must
    // short-circuit BEFORE the visibility gate runs. No deferred match
    // is published, no match task is created. Asserting on
    // gate.calls==0 is the proof — if the gate runs, we'd then proceed
    // to runMatchProspects which would create a match Task.
    @Test void waitAndRunMatchInternal_skipIdentTrue_skipsBeforeGate() {
        long firstDeferred = System.currentTimeMillis();
        RecordingGate gate = new RecordingGate(
            MatchVisibilityGate.GateOutcome.ready(1, firstDeferred));
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWithSkipIdent(gate, publisher, true);

        MlServiceJobOutcome out = p.runDeferredMatch(
            deferredJobPayload(2, firstDeferred));

        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind(),
            "skipIdent must yield OK outcome");
        assertEquals(0, gate.calls,
            "visibility gate must not run when skipIdent=true");
        assertEquals(0, publisher.published.size(),
            "no deferred-match payload should be published when skipIdent=true");
    }

    // Negative case: skipIdent=false must let the gate run normally.
    // Pairs with the test above to prove the override is the only
    // thing suppressing the gate (not some unrelated short-circuit).
    @Test void waitAndRunMatchInternal_skipIdentFalse_runsGate() {
        long firstDeferred = System.currentTimeMillis();
        RecordingGate gate = new RecordingGate(
            MatchVisibilityGate.GateOutcome.defer(2, firstDeferred,
                "sibling pending"));
        RecordingPublisher publisher = new RecordingPublisher();
        MlServiceProcessor p = processorWithSkipIdent(gate, publisher, false);

        p.runDeferredMatch(deferredJobPayload(2, firstDeferred));

        assertEquals(1, gate.calls,
            "visibility gate must run when skipIdent=false");
        assertEquals(1, publisher.published.size(),
            "DEFER outcome must publish a payload when skipIdent=false");
    }
}
