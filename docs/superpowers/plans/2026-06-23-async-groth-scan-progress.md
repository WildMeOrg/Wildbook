# Plan: Restore async Groth scan + progress bar (regression #2)

## Problem
The React "Start Scan" button POSTs to `/GrothMatch`, which runs the Modified-Groth (+ I3S)
match **synchronously, single-threaded** over the in-memory matchGraph (87k+ encounters with
spots). Over a large same-species candidate set this does not finish inside a browser request:
the tab hangs on `/GrothMatch` and frequently no result XML is ever written ("no Groth results").
The old async flow showed a live progress bar via `scanEndApplet.jsp`; that UI is still intact
but is never driven because the synchronous servlet creates no `ScanTask` and reports no progress.
The old work-item *processor* (WorkAppletHeadlessEpic / remote grid clients via
`ScanWorkItemResultsHandler`) is dead — nothing consumes the work-item queue — so we will NOT
revive per-candidate work items.

## Approach (reuse the intact progress UI; run the fast engine in a background thread)
Keep the optimized in-memory matchGraph engine, but:
1. `/GrothMatch` creates a `ScanTask` (taskID `scan{L|R}{encNumber}`), spawns a background
   thread to run the existing match loop + XML/I3S write, and **redirects immediately** to
   `scanEndApplet.jsp` so its existing 15s-refresh progress bar polls.
2. Progress is reported via a new **in-memory** map on `GridManager` (same singleton that holds
   the matchGraph) — no JDO/schema change, no per-candidate DB writes.
3. `scanEndApplet.jsp` progress display is reused unchanged except the 2 lines that read the
   (dead) work-item counts now read the new progress map.

## Changes

### 1. GridManager — in-memory scan-progress facility (~20 lines, no schema)
- Add `private final ConcurrentHashMap<String,int[]> scanProgress = new ConcurrentHashMap<>();`
  (value = `{completed, total}`).
- `public void setScanProgress(String taskID, int completed, int total)`
- `public int getScanProgressComplete(String taskID)` (0 if absent)
- `public int getScanProgressTotal(String taskID)` (0 if absent)
- `public void clearScanProgress(String taskID)`

### 2. GrothMatchServlet.doPost — make it async
- Keep Phase 1 (load query Encounter, build `queryLite`, `queryArray`, metadata, resolve params)
  exactly as today, in the request thread (fast, fails fast on not-found / matchGraph-not-ready).
- Build `taskID = "scan" + (rightScan ? "R" : "L") + encNumber`.
- Reuse-or-create the `ScanTask`: if `getScanTask(taskID) != null` → `setFinished(false)`,
  `setStarted(true)`, re-store; else `new ScanTask(...)` + `storeNewScanTask`. (No removeScanTask
  API exists; reuse avoids needing one.) Carry location filters if present (parity with old flow).
- `gm.setScanProgress(taskID, 0, matchGraph.size())` then `gm.clearScanProgress` is called by the
  thread at the end.
- Submit a `GrothScanRunnable` to `SharkGridThreadExecutorService.getExecutorService()` (the same
  managed pool WriteOutScanTask uses).
- `response.sendRedirect("encounters/scanEndApplet.jsp?number=" + encNumber + "&taskID=" + taskID
  + (rightScan ? "&rightSide=true" : ""))` — immediately.
- doPost no longer blocks; the candidate loop + Phase 3 move into the runnable.

### 3. GrothScanRunnable (new class, org.ecocean.servlet or org.ecocean.grid)
Constructor takes: context, taskID, encNumber, rightScan, queryArray, queryLite, the five params,
and the enc metadata strings (date/sex/individualID/size/location/locationID). `run()`:
- Iterate `GridManager.getMatchGraph()` running the EXISTING engine (the current loop body:
  `isEligibleCandidate` gate → I3S `i3sScan` → Groth `getPointsForBestMatch` → collect MatchObject).
  Increment a local counter each iteration; `gm.setScanProgress(taskID, i, total)` every ~250
  iterations and once at the end.
- Sort results, write `lastFull{Right}Scan.xml` + `I3SResultWriter.write(...)` (the EXISTING
  Phase 3 logic, moved verbatim; uses its own `Shepherd` for encounter details).
- In a `finally`: load the ScanTask via its own `Shepherd`, `setFinished(true)`, set endTime,
  store + commit; `gm.clearScanProgress(taskID)`. So the JSP always stops polling, even on error
  (the JSP already handles "finished but no XML" → "results could not be written").

### 4. scanEndApplet.jsp — swap the 2 progress-source lines
Lines ~58-59:
```
numComplete = gm.getScanProgressComplete(taskID);
numTotal    = gm.getScanProgressTotal(taskID);
```
Everything else (taskID reconstruction, `st.hasFinished()` gating, progress bar markup, 15s
refresh, results-from-XML display) unchanged.

## Threading / correctness
- matchGraph is a ConcurrentHashMap, read-only during the scan — safe to iterate off-thread.
- `queryLite`/`queryArray`/params/metadata are built in the request thread and only read by the
  runnable (effectively immutable) — safe.
- Shepherd is NOT shared across threads: the runnable creates its own for Phase 3 + the finish.
- ScanTask: created+committed by the servlet before redirect (so first poll sees scanInProgress);
  the runnable re-reads + commits finished=true at the end.
- Re-scan: reuse-or-reset clears stale finished/XML perception (hasFinished=false during the new
  run → progress bar, not the old XML).
- Always-async (drop the synchronous path); the React button + scanEndApplet already expect a
  redirect to the results page.

## Verification
- `mvn -q -o compile` clean.
- Unit-test `GridManager` scan-progress methods (set/get/clear, absent → 0).
- Manual reasoning for the JSP (no JSP unit harness): confirm the 2-line swap compiles via JSP.
- Codex review of the diff (threading + lifecycle focus) before commit.

## Out of scope / follow-ups
- Convergence to `main` (this is part of the sharkbook-only synchronous-Groth+I3S feature).
- Parallelizing the engine within the runnable (single-threaded is fine; it no longer blocks the
  request). Could add later behind `grothMatchThreads`.
