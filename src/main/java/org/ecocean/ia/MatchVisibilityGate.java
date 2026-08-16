package org.ecocean.ia;

import java.util.Collection;

import org.json.JSONObject;

/**
 * Gate that decides whether an ml-service-driven match invocation
 * is safe to fire NOW or should be deferred. Solves the
 * empty-match-prospects bug where per-image ml-service jobs fired
 * their match step before sibling jobs in the same import had
 * persisted their annotations into JDO and OpenSearch.
 *
 * <p>Three-phase gate:</p>
 * <ol>
 *   <li><b>Phase 1 (sibling terminal-state):</b> for each sibling
 *       MediaAsset under the per-asset task's topTask, check both
 *       {@code ma.detectionStatus} and the per-MA child Task's raw
 *       persisted status (via
 *       {@link Task#getStoredStatus()}). Either being terminal is
 *       sufficient. Non-terminal siblings cause DEFER.</li>
 *   <li><b>Phase 2 (eligible annotations):</b> resolve annotation
 *       IDs reachable from the sibling MAs via
 *       {@link MatchEligibilityQuery#findEligibleAnnotationIds},
 *       filtered to those with matchAgainst, acmId, and the right
 *       embedding metadata. Phase 1+2 run under a single Shepherd
 *       scope that closes BEFORE Phase 3 (per c11 Phase A/B/C
 *       pattern — no Shepherd held across network).</li>
 *   <li><b>Phase 3 (visibility wait):</b> two-wait split per Codex
 *       round-4 Major: caller IDs through the existing
 *       {@link org.ecocean.OpenSearch#waitForVisibility} (just
 *       {@code _id} visibility), sibling eligible IDs through
 *       {@link org.ecocean.OpenSearch#waitForAnnotationMatchableIds}
 *       (full matchable predicate).</li>
 * </ol>
 *
 * <p>If any phase reports a wait, the gate returns
 * {@link Kind#DEFER}. Once the elapsed time since
 * {@code firstDeferredAt} exceeds {@code MAX_DEFER_AGE_MILLIS}
 * the gate returns {@link Kind#GIVE_UP} so the caller can run
 * match against whatever is visible rather than block forever.</p>
 *
 * <p>(Empty-match-prospects design Track 2 C10.)</p>
 */
public interface MatchVisibilityGate {
    enum Kind { READY, DEFER, GIVE_UP }

    /**
     * Maximum elapsed wall-clock time from first DEFER to
     * GIVE_UP. Chosen to stay within the existing
     * {@code IAGateway.requeueJob} 30-retry/2-day cap with comfortable
     * margin (Codex round-3 Blocker, refined in round-4 Blocker).
     */
    long MAX_DEFER_AGE_MILLIS = 12L * 60L * 1000L;

    /**
     * Plain-data carrier for the gate decision. Caller-facing
     * fields are all {@code final}.
     */
    final class GateOutcome {
        public final Kind kind;
        public final int attempt;
        public final long firstDeferredAt;
        public final long elapsedMillis;
        public final String reason;

        private GateOutcome(Kind kind, int attempt, long firstDeferredAt,
            long elapsedMillis, String reason) {
            this.kind = kind;
            this.attempt = attempt;
            this.firstDeferredAt = firstDeferredAt;
            this.elapsedMillis = elapsedMillis;
            this.reason = reason;
        }

        public static GateOutcome ready(int attempt, long firstDeferredAt) {
            return new GateOutcome(Kind.READY, attempt, firstDeferredAt,
                System.currentTimeMillis() - firstDeferredAt, null);
        }

        public static GateOutcome defer(int attempt, long firstDeferredAt,
            String reason) {
            return new GateOutcome(Kind.DEFER, attempt, firstDeferredAt,
                System.currentTimeMillis() - firstDeferredAt, reason);
        }

        public static GateOutcome giveUp(int attempt, long firstDeferredAt,
            String reason) {
            return new GateOutcome(Kind.GIVE_UP, attempt, firstDeferredAt,
                System.currentTimeMillis() - firstDeferredAt, reason);
        }
    }

    /**
     * Gate the match for the given child task.
     *
     * @param callerAnnotationIds  the annotations this job's match
     *                              will actually score (always
     *                              included in the visibility wait,
     *                              even if the eligibility filter
     *                              would otherwise exclude them).
     * @param childTaskId          the per-asset child task ID whose
     *                              parent is the topTask carrying
     *                              the sibling MA group.
     * @param matchConfig          the ml-service _id_conf entry,
     *                              source of {@code method} and
     *                              {@code version}.
     * @param attempt              1 for the initial call; the
     *                              deferred-match path increments
     *                              this on each re-fire.
     * @param firstDeferredAt      epoch-ms when the deferral chain
     *                              started, or {@code null} on the
     *                              initial call (the gate stamps
     *                              {@code now} in that case).
     */
    GateOutcome gateForBatch(
        Collection<String> callerAnnotationIds,
        String childTaskId,
        JSONObject matchConfig,
        int attempt,
        Long firstDeferredAt);
}
