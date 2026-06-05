# Bulk Import Task page: polling stalls across the Import→Detection handoff

**Date:** 2026-06-04
**Branch:** `fix/bulk-import-poll-handoff-stall`
**Files:** `frontend/src/models/bulkImport/useGetBulkImportTask.js` (+ new unit test)

## Problem

On `/react/bulk-import-task`, if the user switches browser tabs while the import
is finishing and returns during the Import→Detection handoff, the page looks
stalled at Import even though the backend has moved on to Detection. A manual
refresh fixes it.

## Root cause (confirmed by code tracing)

Polling is driven by the `refetchInterval` callback in `useGetBulkImportTask.js`
returning `5000` (keep polling) vs `false` (stop). In react-query **v3.39.3**:

- `refetchInterval: false` → `clearRefetchInterval()` destroys the timer
  (`queryObserver.js:259,283`). There is no timer afterward.
- `refetchOnWindowFocus: false` (line 63) → `query.onFocus()` does **not**
  re-arm or re-evaluate the interval (`query.js:156`). Returning to the tab
  triggers no fetch.
- `refetchIntervalInBackground: false` (line 148) → while hidden the interval
  fires but skips the fetch, freezing the last data + last interval value. On
  return you get effectively **one** catch-up fetch; whatever state it lands in
  decides everything.

So if `refetchInterval` ever returns `false` during a legitimate in-progress
state, polling dies permanently with no focus-based recovery.

It returns `false` during real handoff states because the in-flight whitelist
(`IN_FLIGHT_STATUSES = {"processing-pipeline","sent"}`, lines 14-21) and the
`!hasEncounters`-gated early-phase branch (line 101) don't cover the actual
`task.status` strings the backend emits:

1. **The `"imported"` window** — `BulkImport.java:498` commits
   `status="imported"`, `importPercent=1.0`, encounters attached (commit at
   `:516`), *then* calls `initiateIA()` (`:521-523`). `initiateIA` doesn't commit
   `status="processing-detection"` + start the IA task until after the
   `handleBulkImport()` round-trip to the ML service (`:808 → :824`). During that
   window the task JSON is `status="imported"`, `detectionStatus` **absent**,
   `pipelineStarted=false`. `"imported"` ∉ whitelist; `detectionStatus` undefined;
   `hasEncounters===true` disables the early-phase branch → **`false`**.
2. **`"processing-detection"` is not in the whitelist** (`BulkImport.java:808`).
   Detection only stays "alive" because `iaSummaryJson` emits
   `detectionStatus="sent"` (`ImportTask.java:712`). The detection→identification
   handoff (detection complete, identification not yet requested,
   `ImportTask.java:714`) leaves both statuses non-in-flight → **`false`**.

Foreground polls every 5s and mostly samples `"sent"` states, riding over the
brief gaps. The single catch-up fetch after a tab-return is far more likely to
land in a gap, and when it does, polling dies with no recovery.

## Fix design (revised after Codex round-1 review)

Codex flagged that a broad `pipelineStarted && !pipelineComplete` unbounded poll
would **poll forever** on settled-but-incomplete states — the legacy path emits
`detectionStatus="complete"` + `identificationStatus="unknown"` with
`pipelineComplete=false` (`ImportTask.java:759`), and `"identification not
started"` with zero match tasks (`:736`) persists indefinitely. It also flagged
the missing global terminal-error stop and that a `task.dateCreated`-based budget
is blown for long imports. The revised design keeps **unbounded** polling only
for *explicit* in-flight signals and routes every un-statused gap through a
single **first-observed-bounded** branch.

### Change 1 — extract `refetchInterval` as a pure, testable function

The decision logic is an inline closure (not exportable, not unit-testable, reads
`Date.now()` + a `useRef`). Extract:

```js
export function computeBulkImportPollInterval(response, clocks, now = Date.now())
// clocks = { handoffFirstSeen, taskInfoLagFirstSeen }; mutated in place.
// returns 5000 | false. `now` injectable for deterministic tests.
```

### Change 2 — single decision flow (replaces whitelist + early-phase + ad-hoc branches)

```
if response === undefined            -> 5000        (initial load)
if !task                             -> false       (deleted/malformed)
if TERMINAL_ERROR_STATUSES(status)   -> false        (Codex Major 2: global stop, FIRST)
if isInFlight(status|detection|ident)-> 5000, reset handoff clock   (active work, unbounded)
if pipelineComplete                  -> taskInfo-lag poll (bounded) else false
else (handoff/pre-pipeline gap)      -> poll bounded by first-observed handoff budget
```

- **Explicit in-flight = unbounded** keeps the long-standing `"sent"` /
  `"processing-pipeline"` semantics. Each in-flight observation **resets** the
  handoff clock so a later gap (e.g. detection→identification) gets a fresh
  budget instead of inheriting elapsed time from an earlier phase.
- **`pipelineComplete`** routes to the existing taskInfo-aggregation-lag poll
  (kept, bounded by its own first-seen clock); otherwise stop.
- **Handoff/pre-pipeline gap** (not in-flight, not complete, not terminal-error)
  covers every un-statused window: `"processing-background"` (early CSV import,
  no encounters), `"imported"` (import committed, IA not yet started),
  `"processing-detection"` before `detectionStatus="sent"`, and detection-complete
  before `identificationStatus="sent"`. It also catches **settled** non-complete
  states (legacy `"unknown"`, `"identification not started"`). Because a single
  snapshot can't tell transient from settled, the poll is **bounded by
  elapsed-since-first-observed** (NOT `task.dateCreated`): worst case a settled
  state over-polls one budget window then stops — **never infinite** (Codex
  Major 1). First-observed (not task age) keeps long imports covered (Codex
  Major 3).

This removes the old `IN_FLIGHT`-whitelist gap, the `!hasEncounters`-gated
early-phase branch, and the now-unused `taskAgeMillis` helper.

### Change 3 — focus backstop

Set `refetchOnWindowFocus: true` (was `false`) and rewrite the stale comment.
Returning to the tab re-samples immediately and re-arms the interval if work is
still in flight — turning any residual stall (e.g. a handoff budget that expired
while the tab was hidden) into a self-healing one. Cost: one extra GET on focus,
which is the desired "refresh on return". Codex confirmed this is acceptable and
not relied on as the primary fix (Change 2 is).

## Why this combination

- Change 2's in-flight branch handles **active** detection/identification for any
  import length, unbounded, deterministically.
- Change 2's handoff branch closes the **`"imported"` / pre-`sent` / handoff**
  gaps — bounded so settled states can't poll forever.
- Change 3 is the **tab-return safety net**, directly targeting the report.

## Testing

New `frontend/src/__tests__/models/bulkImport/useGetBulkImportTask.test.js`
unit-testing `computeBulkImportPollInterval` across:

- `undefined` response → 5000 (initial load)
- no `task` → false
- `status="processing-background"`, no encounters → 5000 (early phase)
- **`status="imported"`, encounters present, no `iaSummary` detection** → 5000 (regression test for the bug)
- `pipelineStarted && !pipelineComplete` (detection "sent") → 5000
- detection→ident handoff (detection "complete", identification absent, pipelineStarted true) → 5000 (regression)
- `pipelineComplete` and all encounters have taskInfo → false
- `pipelineComplete` but taskInfo lagging, within budget → 5000; past budget → false
- `status="failed"` pre-pipeline → false
- fully-skipped (`pipelineComplete` true, pipelineStarted false) → false (no 5-min over-poll)
- `"imported"` past the 5-min age budget → false (focus backstop covers it)

Run: `cd frontend && npx jest useGetBulkImportTask`

## Risk / out of scope

- Pure frontend change; no backend or DB changes.
- Does not change the 5-min budgets or the post-ident taskInfo-lag logic
  (untouched except for being moved into the extracted function).
- LF normalization required after edit (CRLF working tree under `/mnt/c/`).
- Commit only `useGetBulkImportTask.js` + the new test (working tree has ~646
  unrelated CRLF-noise files).

## Review resolutions (Codex round 1)

- **Major 1** (unbounded `pipelineStarted&&!pipelineComplete` polls forever on
  legacy `"unknown"` / `"identification not started"`): resolved — only explicit
  in-flight statuses poll unbounded; all gaps are first-observed-bounded.
- **Major 2** (no global terminal stop): resolved — terminal-error check runs
  immediately after `!task`, before any in-flight/structural check.
- **Major 3** (long-import budget keyed off `dateCreated`): resolved — handoff
  budget is elapsed-since-first-observed.
- **Major 4** (detection→ident gap imprecise): test both
  detection-complete+annotations-pending and detection-complete+zero-annotations.
- **Minor 5** (relaxed branch over-polls terminal/no-IA): bounded to one budget
  window; malformed/missing `iaSummary` covered by tests.
- **Minor 6/7**: `refetchOnWindowFocus:true` kept as backstop; explicit
  `isInFlight` checks kept, with bounded gap logic kept separate.
- **Minor 8** (unrelated `BulkImportTask.jsx` import-progress ternary always
  renders truthy `importPercent` as 100%): **out of scope** for this branch —
  flagged separately to the user; it is a display bug, not the polling stall.

## Review resolutions (Codex round 2 — on the implementation)

- **Major 1** (`"processing-pipeline"` whitelisted unbounded → polls forever when
  a re-ID's identification settles incomplete, e.g. `"unknown"`/zero-annotation,
  per BulkImport.java:887 overlay): resolved — `IN_FLIGHT_STATUSES` is now only
  `{"sent"}`; `"processing-pipeline"` falls to the bounded handoff branch and
  only polls unbounded while detection/ident is actually `"sent"`.
- **Major 2** (long `"processing-background"`/`"processing-foreground"` imports
  stop at the 5-min budget): resolved — advancing `importPercent` is treated as
  liveness and resets the handoff budget; a wedged import (no progress for a
  full window) still stops. `importPercent` is bounded [0,1] so it cannot reset
  forever.
- **Major 3** (incomplete clock reset): resolved — `taskInfoLagFirstSeen` is now
  cleared on in-flight and on every non-complete branch (so a timed-out lag
  can't carry into a re-run); both clocks reset via a `useEffect` keyed on
  `taskId`.
- Added tests: `"processing-pipeline"`+settled-incomplete (bounded) and +`"sent"`
  (unbounded), long-import-with-advancing-percent (polls past budget),
  wedged-import (stops), taskInfo-lag-clears-on-rerun, exact budget boundary.
