**This round converges: no Critical and no Major findings.** I only read the code and didn't run anything. The claims below come from reading the code, not from test results.

## Critical #1 from round 1: resolved

- **Persistence loop:** in deferred mode, the loop at `BulkImporter.java:153-196` now calls `pm.makePersistent` directly for encounters, occurrences, individuals and projects. `setEncounterNumber` is kept, matching `Shepherd.storeNewEncounter:142`. MediaAssets (`MediaAssetFactory.save:81`) and Users (`:148`) were already direct `makePersistent` calls, so nothing on this path begins, commits or rolls back a transaction.
- **Legacy path:** the `else` branches still call the `storeNew*` helpers, `cacheEvictAll` and `updateStandardChildrenBackground`, so legacy behavior is unchanged.
- **Progress updates:** `markProgress` (`:760`) returns early in deferred mode, so its separate Shepherd, its commit and its `catch`-and-print are never reached.
- **Error propagation:** exceptions from `processRow` are wrapped in a `ServletException` (`:114-119`). Exceptions from `makePersistent` in the persistence loop are not caught in `createImport`. Either way they reach `SubmissionJobs.execute:81`, which rolls back and calls `fail(..., "IMPORT_FAILED")`.
- **ImportTask writes:** both call sites now use `sh.getPM().makePersistent(task)` (`SubmissionImporter.java:60`, `SubmissionJobs.java:52`). Neither swallows errors, and the task only reaches `complete` inside the caller's transaction.

## Other commit or swallow paths in the strict field subset

I followed every call reachable from the 16 fields in `SubmissionValidator.FIELDS` plus `mediaAssetN` and the injected `submitterID`:

| Path | Result |
|---|---|
| `getOrCreateMarkedIndividual(null, …)` | Returns null right away. Individual, social-unit and name code is unreachable. |
| `Shepherd.getOrCreateOccurrence(null)` (`Shepherd.java:3096`) | Creates a new object in memory only. |
| `getOrCreateEncounter` | No ID fields, so it creates a new UUID encounter with no lookup. |
| Submitter, photographer, inform-other, project, measurement and sample loops | No matching fields, so none of them run. The swallowing `catch` in `handleSocialUnit` can't be reached. |
| `handleKeywords` | Called with an empty set, so it does nothing. |
| `new Annotation(tx, ma)`, `addEncounterAndUpdateIt`, `setLatLonFromEncs`, `setSubmitterIDFromEncs` | In memory only. |
| `UploadedFiles.makeMediaAsset` / `AssetStore.getDefault` | Reads and file copies only. Failures throw `ApiException`; nothing returns null. |
| `BulkValidator` / `validateRow` | Reads only (`getUser`, taxonomy and config lookups). |
| `SubmissionPolicy.enrolled` | Reads config only. |
| `bulkOpensearchIndex` (the only other `new Shepherd` in the bulk package) | Only reached from the legacy dispatch. |

I found no remaining hidden commits, and no persistence errors that get swallowed.

## Major

None.

## Minor

1. **An owner without a username still fails late** (round-1 Minor #2, not addressed). If `owner.getUsername()` is null, `fields.put("Encounter.submitterID", null)` at `SubmissionImporter.java:29` removes the key. The import then fails inside `processRow` with a generic `IMPORT_FAILED` instead of `ACCESS_DENIED`. It fails safely, but the reason is unclear. The fix is one guard next to lines 17-18.
2. **The boundary test has a few gaps** (`BulkImporterSubmissionBoundaryTest.java`):
   - It passes a non-null task ID, but `markProgress` suppression is only covered indirectly: if suppression broke, a real `new Shepherd` would presumably throw. A `mockConstruction(Shepherd.class)` asserting zero constructions would make this explicit.
   - It doesn't verify `never().cacheEvictAll()` before the legacy run, or `atLeastOnce().cacheEvictAll()` after it.
   - It doesn't verify `pm.makePersistent(any(Occurrence.class))` alongside the Encounter check.
3. **There's still no adapter-level test for row-order mapping** (round-1 Minor #4b). Nothing checks that `clientRowId` maps to the right encounter, occurrence and media for each row.

## Checked and fine

- **Stale-validation check (`:21`):** it now compares `valid`, `revision`, both digests and the canonical rows. `submissionId` isn't compared, but the validation JSON is written by the server onto the same draft, so that's acceptable.
- **Duplicate media:** repeats within a row and reuse across rows are both rejected as `DUPLICATE_MEDIA` (`SubmissionValidator.java:54-55`). That closes round-1 Minor #1.
- **Media availability:** the `media.keySet().equals(requiredFiles)` check plus `makeMediaAsset` throwing means the silent skip at `BulkImporter.java:722` still can't be hit.
- **Orphaned staged files on rollback:** still deferred to Stage 5, as agreed.

**Convergence:** the transaction boundary for the strict field subset is sound. The remaining items are Minor and don't block. The Stage 5 worker is outside this review.
