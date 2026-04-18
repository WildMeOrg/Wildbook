# Bulk Import re-ID Race Fix — Implementation Summary (v3)

**Goal:** Eliminate the race condition where bulk-import re-ID jobs kick off per-encounter immediately after each detection callback, missing intra-bulk matches because sibling annotations aren't yet in WBIA's matching index.

**Architecture:** Branch `processCallback` on bulk imports. When a detection callback belongs to a bulk import (detected via `importTaskId` in task parameters), defer identification. On each callback, check whether ALL MediaAssets in the ImportTask have terminal detection status. Only the LAST terminal callback (claimed atomically via JVM-global registry + persistent field for restart-survival) fires a single bulk-final ID pass over ALL eligible annotations from the ImportTask.

## Files Changed

| File | Change |
|------|--------|
| `identity/BulkFinalIdentificationRegistry.java` | NEW — JVM-global ConcurrentHashMap single-winner claim |
| `ia/Task.java` | ADD `bulkFinalIdentificationFired` persistent Boolean + getter/setter |
| `ia/package.jdo` | ADD JDO mapping for new Task field |
| `importer/ImportTask.java` | ADD `isAllDetectionTerminal()` — checks all MAs have terminal status |
| `identity/IBEISIA.java` | REWRITE identification block in `processCallback` (~1416-1441). Extracted helpers: `getImportTaskId`, `fireBulkFinalIdentificationIfReady`, `groupByEncounter`, `collectNewAnnotations`, `kickoffIdentificationForAnnotations` |

## Gate Logic (in order)

1. `skipIdent` property check → early return (unchanged)
2. `taskParametersSkipIdent(parentTask)` → early return (unchanged)
3. **NEW**: If `importTaskId` is in task parameters → bulk path:
   a. `itask.isAllDetectionTerminal()` → false? return (not ready yet)
   b. `rootTask.isBulkFinalIdentificationFired()` → true? return (restart survival)
   c. `BulkFinalIdentificationRegistry.tryClaim(importTaskId)` → false? return (another thread won)
   d. Mark persistent flag, commit
   e. Enumerate ALL `itask.getAnnotations()` filtered by `matchAgainst && validForIdentification`
   f. Group by encounter, kick off ID per encounter with location filters
4. If NOT bulk → today's per-callback behavior (unchanged logic, refactored into helpers)

## Tests (22 total, all passing)

- `BulkFinalIdentificationRegistryTest` — 5 tests including concurrent 50-thread race
- `IBEISIABulkCallbackTest` — 5 tests for bulk detection via task parameters
- `IBEISIAGroupByEncounterTest` — 4 tests for encounter grouping
- `ImportTaskDetectionTerminalTest` — 8 tests for terminal status checking

## Edge Cases

- **Pending assets (human review required):** Terminal for gating purposes; no annotations created, so they won't be in the ID pass. Follow-up re-ID needed after review.
- **Restart mid-bulk:** Persistent `Task.bulkFinalIdentificationFired` on the root IA Task (via `ImportTask.getIATask()`) prevents re-fire.
- **Multi-species/multi-algorithm callbacks:** Claim is keyed on `importTaskId` (not callback task ID), so multiple callback tasks for the same import share the same claim. Root IA Task is resolved via `itask.getIATask()` to avoid broken `getRootTask()` when `addChild()` doesn't set parent pointers.
- **Zero eligible annotations after filtering:** `kickoffIdentificationForAnnotations` logs and returns.
- **Non-bulk regression:** Non-bulk path explicitly goes through same `collectNewAnnotations` + `kickoffIdentificationForAnnotations`, preserving identical behavior.
- **Multi-Tomcat:** Current implementation is single-Tomcat-safe. For multi-instance, replace registry with DB compare-and-set on the persistent field.
