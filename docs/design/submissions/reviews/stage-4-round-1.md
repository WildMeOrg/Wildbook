I found **one Critical issue**. The rest of the adapter boundary looks sound. I only read the code; I didn't run any tests.

## Critical

**1. The deferred path still commits the caller's transaction.** The change skips `markProgress` and `updateStandardChildrenBackground`, but the persistence loop in `createImport()` still goes through Shepherd helpers that commit on their own:

- `BulkImporter.java:155` calls `myShepherd.storeNewEncounter(enc, enc.getId())`. At `Shepherd.java:141-156` that runs `beginDBTransaction()`, which joins the caller's active transaction, then `pm.makePersistent`, then `commitDBTransaction()`.
- `storeNewOccurrence` (`Shepherd.java:197`), `storeNewMarkedIndividual` (`:273`) and `storeNewProject` (`:374`) do the same thing.

What this does in `SubmissionImporter.execute`:
- The first encounter commits every MediaAsset and User saved so far, plus that encounter.
- Each later encounter or occurrence opens and commits its own transaction.
- The final `storeNewImportTask`, which marks the task complete, runs in a transaction that the helpers reopened. The caller's rollback only covers that last piece.

Two more problems follow:
- The helpers catch persistence errors, roll back, and return `"fail"` or `false`. So a failed encounter insert doesn't abort the import. The adapter then writes a mapping for rows that may not exist, and the task still ends up `complete`.
- `BulkImporterSubmissionBoundaryTest` can't see any of this. `Shepherd` is a mock, so `storeNewEncounter` does nothing, and `verify(sh, never()).commitDBTransaction()` passes regardless.

**Fix** (in `BulkImporter`, deferred mode only, so legacy callers keep the old behavior):
```java
// encounters
if (deferSideEffects) { enc.setEncounterNumber(enc.getId()); myShepherd.getPM().makePersistent(enc); }
else myShepherd.storeNewEncounter(enc, enc.getId());
// occurrences
if (deferSideEffects) myShepherd.getPM().makePersistent(occ); else myShepherd.storeNewOccurrence(occ);
// individuals / projects: same pattern
```
With this, persistence errors propagate as a `ServletException` and the caller rolls back.

In the test, add `verify(sh, never()).storeNewEncounter(any(), any())` and `verify(sh, never()).storeNewOccurrence(any())`, plus `verify(pm, atLeastOnce()).makePersistent(any(Encounter.class))`. Also add one legacy assertion that the `storeNew*` helpers are still called.

## Major

None, once Critical #1 is fixed.

## Minor

1. **Duplicate media references aren't rejected.** The validator builds `media` as a `Set` (`SubmissionValidator.java:46-57`), so:
   - `mediaAsset0` and `mediaAsset1` can point to the same file. The encounter then gets two exemplar annotations on one MediaAsset, and the limit check counts that file once.
   - Two rows can reference the same file. The importer makes one MediaAsset, and both encounters annotate it, so the mapping reports the same `mediaAssetIds` for both rows. That mapping is accurate, but it's probably not what "separate encounter each row" is meant to imply.

   Fix: in the validator, reject a repeated value within a row, and either reject or knowingly allow reuse across rows (e.g. a `DUPLICATE_MEDIA_REFERENCE` issue).
2. **An owner without a username fails late.** If `owner.getUsername()` is null, the failure only shows up inside `processRow` ("no value for Encounter.submitterID") as a generic error. Add a check next to the eligibility check at `SubmissionImporter.java:17-18`: `Util.stringIsEmptyOrNull(owner.getUsername())` → `SubmissionException(403, "ACCESS_DENIED", …)`.
3. **The stale-validation check doesn't pin identity.** It compares the digests and normalized rows but not `approved.submissionId` or `approved.revision` against the draft. The content comparison covers most of the risk, but pinning these two is cheap: add `approved.getString("submissionId").equals(draft.getId()) && approved.getInt("revision") == draft.getRevision()`.
4. **Some test gaps:**
   - The boundary test passes `importTaskId = null`, so it never tests that `markProgress` is suppressed. Use a non-null ID and `mockConstruction(Shepherd.class)`, then assert nothing was constructed.
   - There's no adapter-level test showing that `clientRowId` → encounter/occurrence/media mapping follows row order.

## Checked and fine

- **Legacy behavior:** the default is `deferSideEffects = false` with a null collector. `processRow` only gained a return value, and progress and dispatch are unchanged for existing callers.
- **No background work on the new path:** child-image generation and OpenSearch indexing are both inside the `!deferSideEffects` guard, and `markProgress` returns early.
- **One encounter per row:** the strict field set has no `Encounter.id`/`catalogNumber`, sighting ID or individual ID. So each row gets a new UUID encounter and its own `Occurrence`, and no existing individual is touched.
- **ID mapping:** row IDs come from `processRow` through the collector, keyed by row index, and are read after persistence. MediaAsset uses `value-strategy="identity"`, and legacy code already reads `getIdInt()` right after `save`, so the IDs are there.
- **Missing media:** `UploadedFiles.makeMediaAsset` throws instead of returning null. Together with the `media.keySet().equals(requiredFiles)` check, that means BulkImporter's silent skip of a missing asset (`BulkImporter.java:717-731`) can't be hit from this path.
- **Authorization and copies:** the owner comes from the draft (the server-side owner ID), and eligibility is rechecked at execution. `submitterID` is injected, so the client can't set it. Staging paths are UUID-blob plus checked-name with containment and symlink checks. Filenames are already `cleanFileName`-idempotent and unique ignoring case (`SubmissionStore.java:118-121`), so copies into `Encounter.subdir(taskId)` can't collide. Leftover files on rollback are the orphan case you've already deferred to the worker/operator stage.
