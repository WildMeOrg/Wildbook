# Pure on-demand PairX enricher

**Status:** designed, post-Codex round 1. Branch: `migrate-ml-service-v2`. Date: 2026-05-23.

## Context

Live bulk-import imports today (233d51a5 at 19:02, 933b6fa5 at 22:41, 2ece4266 at 23:51) all show a ~4-min stagger between matches. Confirmed dominated by `MatchInspectionPairxEnricher` doing 38 sequential pairx HTTP POSTs per MatchResult, called inline-synchronously from `MlServiceProcessor.runMatchProspects` after the vector match commits. The user chose Option 3 from the enricher ELI5: pure on-demand — generate inspection images only when a user opens the inspector.

## Design (post-Codex)

### Backend changes (C7a)

1. **Typed enrichment result** on `MatchInspectionPairxEnricher`:
   ```
   enum ProspectEnrichmentResult { EXISTS, CREATED, NOT_ASSOCIATED, UNSUPPORTED, PAIRX_FAILED }
   class ProspectEnrichmentOutcome { result, asset (nullable), message (nullable) }
   ```
   Per-prospect work moves into a private `enrichSingleDto(PairxDto)` that returns a typed outcome. Batch path (`enrichMatchResult`) calls it in a loop and counts CREATED. New on-demand path (`enrichOneProspect(mrId, prospectAnnId)`) calls it once and returns the outcome.

2. **New REST endpoint** `MatchInspection` extending `ApiBase`, mapped at `/api/v3/match-inspection/{mrId}/{prospectAnnId}` (servlet mapping in `web.xml`, matching the existing `api/` package convention).
   - **POST.** Idempotent: returns existing asset on 200 if `prospect.getAsset()` is already set; otherwise generates, persists, returns 201.
   - **Authz.** `myShepherd.getUser(request)` for authentication; project/encounter access checked via the MatchResult's queryAnnotation → encounter chain (see existing `ServletUtilities.isUserAuthorizedFor*` patterns; pick the closest analog).
   - **Idempotency re-check.** Reopen short Shepherd transaction after enrichment to re-check `prospect.getAsset()` before persisting — defends against concurrent inspector-opens for the same prospect.
   - **Error mapping:** EXISTS/CREATED → 200/201; NOT_ASSOCIATED → 404; UNSUPPORTED → 422; PAIRX_FAILED → 502; auth fail → 401/403; bad UUID → 400.
   - **Response shape:** `{assetId, url, width, height}` matching what `MatchProspectTable.jsx:216-218` reads today.

### Frontend changes (C7b)

1. **`MatchProspectTable.jsx`** — when `inspectorOpen && !previewedRow?.asset`, trigger an on-demand mutation against the new endpoint.
2. **New `useGenerateInspectionAsset(mrId, prospectAnnId)`** in `frontend/src/models/`. React Query `useMutation` (POST is mutation-shaped). On success: write back to the `MatchResultsStore` prospect entry so the page doesn't re-fetch on subsequent inspector-opens, and the row preview becomes the new asset.
3. **Spinner + retry** inside the inspector modal during pending state.
4. **In-flight dedup** — react-query's default `useMutation` re-fires per call; gate at the component level so two rapid opens of the same prospect don't both POST.

### Removing the inline enricher (C7c)

Delete the `try { enrichPairxAssetsForMatchTask(matchTaskId); } catch ...` block from `MlServiceProcessor.runMatchProspects` (lines 573-583 post-2943d2181). Keep matchTaskId hoist and the outer try-finally for the Shepherd — those are unchanged.

**Ship order to avoid UX regression:** C7a → C7b → C7c. C7c is the perf win but it leaves new-match inspectors broken until C7b ships, so C7c lands last.

## Compatibility

- Existing MatchResults already have `prospect.asset` populated from the previous inline enricher. They render unchanged.
- New MatchResults (post-C7c) won't have prospect.asset populated. Frontend lazy-fetch handles it.
- No DB migration. `MatchResultProspect.asset` is already nullable.

## Caveats / risks

- **PairX latency moves to inspector-open time.** First open of any prospect's inspector is now ~6s instead of instant. Subsequent opens of the same prospect are instant (cached).
- **Authz pattern verification needed.** Confirm the exact `ServletUtilities` helper for MatchResult project/encounter access during implementation.
- **Concurrency dedup at frontend.** Multiple rapid clicks shouldn't fire multiple POSTs; even if they do, the backend is idempotent via the pre-persist re-check.

## Workflow

design → Codex round-1 (done — no blocker; 3 Majors incorporated above) → implement C7a → Codex code review → revise → commit. Then C7b same loop. Then C7c.
