import { useEffect, useRef } from "react";
import { client } from "../../api/client";
import { useQuery } from "react-query";

// Statuses that mean "work is actively in flight" — poll UNBOUNDED while any of
// these is present. Only the IA-phase "sent" signal qualifies: a running
// detection/identification task already polled forever under the old "sent"
// path, so this is not a new infinite-poll risk. Task-level overlay statuses
// (e.g. "processing-pipeline") are deliberately NOT here — that overlay
// persists whenever the IA phase settles incomplete (e.g. re-ID on an
// already-"complete" task whose identification ends "unknown"/zero-annotation),
// and treating it as unbounded-active would poll forever. Everything not in
// this set is routed through the bounded handoff branch below, so an
// unrecognized or settled-but-incomplete status can never poll forever.
const IN_FLIGHT_STATUSES = new Set(["sent"]);

const isInFlight = (status) => IN_FLIGHT_STATUSES.has(status);

// Terminal failure statuses — stop polling immediately and unconditionally,
// even if a stale iaSummary field still looks in-flight. Must run before any
// structural/in-flight check.
const TERMINAL_ERROR_STATUSES = new Set(["error", "failed"]);

// Poll cadence while work is in flight.
const POLL_INTERVAL_MS = 5000;

// Bound for both bounded polls: (a) the handoff / pre-pipeline gap and (b) the
// post-ident encounterTaskInfo-aggregation lag. Measured from the first poll on
// which we OBSERVED the gap — never from task.dateCreated. A long import can be
// older than this budget by the time it reaches a handoff window, which is
// exactly when the gap is most user-visible.
const PHASE_POLL_BUDGET_MS = 5 * 60 * 1000;

// Initial state for the per-gap "first observed" clocks the bounded polls use.
export const initialPollClocks = () => ({
  handoffFirstSeen: null,
  taskInfoLagFirstSeen: null,
  lastImportPercent: null,
});

// Pure polling-cadence decision, extracted from the hook so every branch is
// unit-testable without driving react-query timers. Reads and updates the
// `clocks` object in place; `now` is injectable for deterministic tests.
// Returns POLL_INTERVAL_MS to keep polling or false to stop.
export function computeBulkImportPollInterval(
  response,
  clocks,
  now = Date.now(),
) {
  // react-query passes `undefined` before the first successful response. Poll
  // so we pick up the initial state.
  if (response === undefined) return POLL_INTERVAL_MS;

  // Response came back but no task — backend returned an empty or malformed
  // body (invalid/deleted task id). Re-polling cannot make it re-materialize.
  const task = response?.task;
  if (!task) {
    clocks.handoffFirstSeen = null;
    clocks.taskInfoLagFirstSeen = null;
    return false;
  }

  // Global terminal-error stop: a failed/errored task never makes further
  // progress; ignore any lingering in-flight-looking iaSummary fields.
  if (task.status && TERMINAL_ERROR_STATUSES.has(task.status)) {
    clocks.handoffFirstSeen = null;
    clocks.taskInfoLagFirstSeen = null;
    return false;
  }

  const ia = task.iaSummary || {};

  // Explicit in-flight signal → unbounded poll (detection/identification
  // running). Reset BOTH clocks: a fresh active phase invalidates any earlier
  // handoff gap AND any earlier taskInfo-lag timeout (so a re-run's later
  // aggregation lag gets a fresh budget rather than an already-expired one).
  if (
    isInFlight(task.status) ||
    isInFlight(ia.detectionStatus) ||
    isInFlight(ia.identificationStatus)
  ) {
    clocks.handoffFirstSeen = null;
    clocks.taskInfoLagFirstSeen = null;
    return POLL_INTERVAL_MS;
  }

  // Pipeline finished. The only reason to keep polling now is the
  // encounterTaskInfo aggregation lagging the identificationStatus flip (it
  // drives the per-encounter "Class" match-result links and can trail by one
  // or more polls). Bounded by elapsed-since-first-observed-lag.
  if (ia.pipelineComplete === true) {
    clocks.handoffFirstSeen = null;
    if (ia.identificationStatus === "complete") {
      const encs = Array.isArray(task.encounters) ? task.encounters : [];
      const taskInfo = ia.statsAnnotations?.encounterTaskInfo || {};
      const anyMissing =
        encs.length > 0 &&
        encs.some(
          (e) => !Array.isArray(taskInfo[e.id]) || taskInfo[e.id].length === 0,
        );
      if (anyMissing) {
        if (clocks.taskInfoLagFirstSeen === null) {
          clocks.taskInfoLagFirstSeen = now;
        }
        if (now - clocks.taskInfoLagFirstSeen < PHASE_POLL_BUDGET_MS) {
          return POLL_INTERVAL_MS;
        }
      } else {
        // taskInfo populated — clear so a future lag gets a fresh budget.
        clocks.taskInfoLagFirstSeen = null;
      }
    } else {
      // identification "skipped" (or otherwise non-"complete") has no match
      // results to wait on — clear the lag clock and stop.
      clocks.taskInfoLagFirstSeen = null;
    }
    return false;
  }

  // Handoff / pre-pipeline gap: not explicitly in-flight, not terminal-error,
  // pipeline not yet complete. Covers every un-statused transition window the
  // backend leaves between phases:
  //   - "processing-background" / "processing-foreground" (active CSV import,
  //     encounters not yet persisted)
  //   - "imported": import committed but initiateIA() hasn't started the IA
  //     task yet (BulkImport.java commits "imported" at :516, then initiateIA()
  //     flips it to "processing-detection" + registers the IA task only after
  //     the handleBulkImport round-trip at :824)
  //   - "processing-detection" before detectionStatus="sent" appears
  //   - detection complete before identificationStatus="sent" appears
  //   - "processing-pipeline" overlay (re-ID) when identification is not "sent"
  // It also catches SETTLED non-complete states (legacy
  // identificationStatus="unknown" at ImportTask.java:759; "identification not
  // started" with zero match tasks at :736). A single snapshot can't tell
  // transient from settled, so bound this poll by elapsed-since-first-observed:
  // worst case a settled state over-polls one budget window, then stops —
  // never infinite.
  //
  // We're pre-completion, so any earlier taskInfo-lag timeout is stale; clear
  // it so a later completion gets a fresh lag budget.
  clocks.taskInfoLagFirstSeen = null;

  // Active CSV import reports a monotonically-increasing importPercent. Treat
  // forward progress as liveness: reset the handoff budget whenever
  // importPercent advances, so a legitimately long import is never cut off,
  // while a genuinely wedged one (no progress for a full budget window) still
  // stops. importPercent is bounded [0,1], so this cannot reset forever.
  const pct =
    typeof task.importPercent === "number" ? task.importPercent : null;
  if (
    pct !== null &&
    (clocks.lastImportPercent === null || pct > clocks.lastImportPercent)
  ) {
    clocks.lastImportPercent = pct;
    clocks.handoffFirstSeen = now;
  }

  if (clocks.handoffFirstSeen === null) clocks.handoffFirstSeen = now;
  if (now - clocks.handoffFirstSeen < PHASE_POLL_BUDGET_MS) {
    return POLL_INTERVAL_MS;
  }
  return false;
}

export default function useGetBulkImportTask(taskId) {
  const fetchTask = async () => {
    const { data } = await client.get(`/bulk-import/${taskId}`);
    return data;
  };

  // Per-gap "first observed" timestamps backing the two bounded polls, held in
  // a ref so they persist across re-renders without re-triggering the query.
  const clocksRef = useRef(initialPollClocks());

  // Reset the clocks when the observed task changes (the hook is reused for
  // different task ids, e.g. the unfinished-task probe on the import landing
  // page); otherwise a prior task's expired budget would carry over and stop
  // the new task's polling immediately.
  useEffect(() => {
    clocksRef.current = initialPollClocks();
  }, [taskId]);

  const { data, isLoading, error, refetch } = useQuery(
    ["bulkImportTask", taskId],
    fetchTask,
    {
      enabled: Boolean(taskId),
      retry: false,
      select: (d) => d ?? [],
      refetchInterval: (response) =>
        computeBulkImportPollInterval(response, clocksRef.current),
      // Do not fetch on a hidden tab: the interval keeps firing but skips the
      // fetch, so the last data + last interval value freeze.
      refetchIntervalInBackground: false,
      // Refetch when the tab regains focus. With refetchIntervalInBackground
      // false a backgrounded tab's polling is suspended; in react-query v3 a
      // refetchInterval that returned `false` clears the timer and nothing
      // re-arms it on its own. A focus refetch re-samples the backend the
      // instant the user returns and re-arms polling if work is still in
      // flight — turning a terminal stall into a self-healing one. Cost is one
      // extra GET on focus, which is the desired "refresh on return" behavior.
      refetchOnWindowFocus: true,
    },
  );

  const task = data?.task || {};

  return { task, isLoading, error, refetch };
}
