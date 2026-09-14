package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage of {@link MatchVisibilityGateImpl}'s static
 * helpers and {@link MatchVisibilityGate.GateOutcome} factory
 * methods. The full {@link MatchVisibilityGateImpl#gateForBatch}
 * flow requires Shepherd + OpenSearch and is exercised end-to-end
 * by the live integration harness — same precedent as the C6
 * iaAnnotationIdsStrict path.
 *
 * <p>(Empty-match-prospects design Track 2 C10.)</p>
 */
class MatchVisibilityGateImplTest {

    // --- GateOutcome factory ---------------------------------------------

    @Test void readyOutcome_setsKind_andCarriesAttempt() {
        long firstDeferred = System.currentTimeMillis() - 1000L;
        MatchVisibilityGate.GateOutcome g =
            MatchVisibilityGate.GateOutcome.ready(3, firstDeferred);
        assertEquals(MatchVisibilityGate.Kind.READY, g.kind);
        assertEquals(3, g.attempt);
        assertEquals(firstDeferred, g.firstDeferredAt);
        assertTrue(g.elapsedMillis >= 0);
        assertNull(g.reason);
    }

    @Test void deferOutcome_carriesReason() {
        long firstDeferred = System.currentTimeMillis();
        MatchVisibilityGate.GateOutcome g =
            MatchVisibilityGate.GateOutcome.defer(2, firstDeferred,
                "sibling MA 42 non-terminal");
        assertEquals(MatchVisibilityGate.Kind.DEFER, g.kind);
        assertEquals("sibling MA 42 non-terminal", g.reason);
    }

    @Test void giveUpOutcome_carriesReason() {
        long firstDeferred = System.currentTimeMillis() - (60L * 60L * 1000L);
        MatchVisibilityGate.GateOutcome g =
            MatchVisibilityGate.GateOutcome.giveUp(7, firstDeferred,
                "exceeded MAX_DEFER_AGE_MILLIS");
        assertEquals(MatchVisibilityGate.Kind.GIVE_UP, g.kind);
        assertTrue(g.elapsedMillis > 0);
        assertEquals("exceeded MAX_DEFER_AGE_MILLIS", g.reason);
    }

    // --- isSiblingTerminal -----------------------------------------------

    @Test void isSiblingTerminal_trueForMaCompleteMlservice() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "complete-mlservice", null));
    }

    @Test void isSiblingTerminal_trueForMaComplete() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "complete", null));
    }

    @Test void isSiblingTerminal_trueForMaPending() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "pending", null));
    }

    @Test void isSiblingTerminal_trueForMaPendingSpecies() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "pending-species", null));
    }

    @Test void isSiblingTerminal_trueForMaError() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "error", null));
    }

    @Test void isSiblingTerminal_trueForMaDroppedStale() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "dropped-stale", null));
    }

    @Test void isSiblingTerminal_falseForMaProcessingMlservice() {
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
            "processing-mlservice", null));
    }

    @Test void isSiblingTerminal_falseForMaProcessing() {
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
            "processing", null));
    }

    @Test void isSiblingTerminal_falseForMaInitiated() {
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
            "initiated", null));
    }

    @Test void isSiblingTerminal_falseForMaNew() {
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
            "_new", null));
    }

    @Test void isSiblingTerminal_falseForBothNull() {
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(null, null));
    }

    // Task statuses: note "completed" (Task) vs "complete" (MA).

    @Test void isSiblingTerminal_trueForTaskCompleted_evenWhenMaNonTerminal() {
        // Failure paths in MlServiceProcessor mark child Task=error
        // without advancing MA. Make sure either-being-terminal works.
        // (Use "completed" — the success state for a Task; the parallel
        // "complete" for MA detectionStatus is also covered above.)
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "processing-mlservice", "completed"));
    }

    @Test void isSiblingTerminal_trueForTaskError_whenMaProcessing() {
        // Codex round-2 Blocker/Major case: child Task is error but MA
        // status didn't update.
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "processing-mlservice", "error"));
    }

    @Test void isSiblingTerminal_trueForTaskDroppedStale() {
        assertTrue(MatchVisibilityGateImpl.isSiblingTerminal(
            "processing-mlservice", "dropped-stale"));
    }

    @Test void isSiblingTerminal_falseForTaskQueuingAndMaProcessing() {
        // Neither MA nor Task terminal — gate must wait.
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
            "processing-mlservice", "queuing"));
    }

    @Test void isSiblingTerminal_falseForUnknownStatuses() {
        // Unknown strings (typos, future status names not yet known) must
        // be conservative: treat as non-terminal so the gate waits.
        assertFalse(MatchVisibilityGateImpl.isSiblingTerminal(
            "made-up-status", "another-typo"));
    }

    // --- findChildTaskForSibling -----------------------------------------

    private static MediaAsset mockMa(int id) {
        // MediaAsset.getId() returns String.valueOf(this.id) where id is
        // an int. Don't use setUUID — that's a separate field unrelated
        // to the equality check the gate uses.
        MediaAsset ma = new MediaAsset();
        ma.setId(id);
        return ma;
    }

    private static Task childWithSingletonMa(MediaAsset ma) {
        Task t = new Task();
        ArrayList<MediaAsset> list = new ArrayList<MediaAsset>();
        list.add(ma);
        t.setObjectMediaAssets(list);
        return t;
    }

    @Test void findChild_returnsTheCorrectSingletonChild() {
        MediaAsset maA = mockMa(1);
        MediaAsset maB = mockMa(2);
        Task childA = childWithSingletonMa(maA);
        Task childB = childWithSingletonMa(maB);
        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
            Arrays.asList(childA, childB), maB);
        // Object identity is sufficient — both are returned by reference.
        assertTrue(pick == childB,
            "expected childB to be picked: " + pick);
    }

    @Test void findChild_skipsMultiMaTasks() {
        // The topTask itself contains all sibling MAs; the lookup must
        // NOT mistake the topTask for a per-asset child (Codex round-3
        // Major).
        MediaAsset maA = mockMa(1);
        MediaAsset maB = mockMa(2);
        Task topLike = new Task();
        topLike.setObjectMediaAssets(new ArrayList<MediaAsset>(
            Arrays.asList(maA, maB)));
        Task childA = childWithSingletonMa(maA);
        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
            Arrays.asList(topLike, childA), maA);
        assertTrue(pick == childA, "expected childA, got: " + pick);
    }

    @Test void findChild_returnsNullWhenNoMatch() {
        MediaAsset maA = mockMa(1);
        MediaAsset maB = mockMa(2);
        Task childA = childWithSingletonMa(maA);
        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
            Arrays.asList(childA), maB);
        assertNull(pick);
    }

    @Test void findChild_handlesNullChildrenList() {
        MediaAsset ma = mockMa(1);
        assertNull(MatchVisibilityGateImpl.findChildTaskForSibling(null, ma));
    }

    @Test void findChild_handlesNullSiblingMa() {
        Task child = childWithSingletonMa(mockMa(1));
        assertNull(MatchVisibilityGateImpl.findChildTaskForSibling(
            Arrays.asList(child), null));
    }

    @Test void findChild_usesEqualsNotReferenceCompare() {
        // Codex round-4 Blocker/Major: MediaAsset.getId() returns
        // String.valueOf(int), so two MediaAssets with the same int id
        // return equal-but-distinct String objects. Confirm the lookup
        // uses .equals(), not ==.
        MediaAsset siblingMa = mockMa(7);
        MediaAsset childMa = mockMa(7);  // same id, distinct object
        Task child = childWithSingletonMa(childMa);
        Task pick = MatchVisibilityGateImpl.findChildTaskForSibling(
            Arrays.asList(child), siblingMa);
        assertTrue(pick == child, "lookup should compare by equals, not ==");
    }

    // --- MAX_DEFER_AGE_MILLIS interface constant -------------------------

    @Test void maxDeferAgeMatchesTwelveMinutes() {
        // Bound is 12 minutes — keeps GIVE_UP reachable inside the
        // IAGateway.requeueJob 30-retry / 30s-per-attempt window
        // (Codex round-3 Blocker).
        assertEquals(12L * 60L * 1000L,
            MatchVisibilityGate.MAX_DEFER_AGE_MILLIS);
    }

    // --- gateForBatch public-method coverage -----------------------------
    //
    // Covers the load-bearing path that runs before Shepherd is opened
    // (the age-out check at the top of gateForBatch). Tests requiring
    // Shepherd or OpenSearch interaction are covered by the live
    // integration harness on dev deployments — same precedent as the
    // C6/C9 paths.

    @Test void gateForBatch_returnsGiveUp_whenAgeExceedsMaxDeferAge() {
        // No Shepherd/OS is invoked because the age-out check is the
        // first thing gateForBatch does.
        MatchVisibilityGateImpl gate = new MatchVisibilityGateImpl("ctx0");
        long longAgo = System.currentTimeMillis()
            - (MatchVisibilityGate.MAX_DEFER_AGE_MILLIS + 1000L);
        MatchVisibilityGate.GateOutcome g = gate.gateForBatch(
            java.util.Arrays.asList("ann-1"),
            null,                  // childTaskId — ignored because age fires first
            new org.json.JSONObject().put("method", "miewid-msv4.1").put("version", "4.1"),
            5,                     // attempt
            longAgo);
        assertEquals(MatchVisibilityGate.Kind.GIVE_UP, g.kind);
        assertEquals(5, g.attempt);
        assertEquals(longAgo, g.firstDeferredAt);
        assertTrue(g.reason != null && g.reason.contains("MAX_DEFER_AGE_MILLIS"),
            "GIVE_UP reason should mention MAX_DEFER_AGE_MILLIS: " + g.reason);
    }
}
