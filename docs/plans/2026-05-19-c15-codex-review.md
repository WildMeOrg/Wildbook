OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e4171-7f4a-7373-a413-dd4e39e3745b
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing code on the `migrate-ml-service-v2` branch of the
Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you the
project conventions, repo gotchas, and current architecture that the
code under review assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (3.1 on the live amphibian-reptile deployment),
  React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has the 20 v2 commits plus the Track 1 empty-match-prospects work
  in progress. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md` and
  `docs/plans/2026-05-18-empty-match-prospects-design.md`.

## Shepherd pattern

```java
Shepherd shep = new Shepherd(context);
shep.setAction(ACTION_PREFIX + "methodName");
try {
    shep.beginDBTransaction();
    // ... JDO operations ...
    shep.commitDBTransaction();
} catch (Exception ex) {
    // log
} finally {
    shep.rollbackAndClose();
}
```

`rollbackAndClose` is idempotent — safe after commit and safe after
early return.

**Critical gotcha:** never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the HTTP
work without Shepherd in Phase B, persist outcome in a fresh Shepherd
in Phase C.

## JDO naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes. The
`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
direct FK, not a JDO-generated join).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh
boundary; **does not** drain the Wildbook IndexingManager queue. If
you need "after this write the doc must be searchable" semantics,
use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
in c7, commit `f429c5bf8`).

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",   // "vector" = ml-service v2
        "method": "miewid-msv4.1",   // embedding model id
        "version": "4.1",
        "embedding_dimension": 2152,
        // legacy entries have api_endpoint instead of method/version
      }
    },
    "_mlservice_conf": {
      "default": {
        "base_url": "https://ml-service.example.com:8008",
        "detection_endpoint": "/pipeline/",
        "extraction_endpoint": "/extract/",
        "model_id": "...",
        "match_against_species": [...]
      }
    }
  }
}
```

`Embedding.findMatchProspects` gates entry on
`isVectorConfig = method != null || api_endpoint != null` — both
nullable independently.

## v1 antipatterns to avoid

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.

## CRLF/LF gotcha on this Windows-mounted repo

The Edit tool sometimes flips LF files to CRLF when editing on
`/mnt/c/Wildbook-clean2`. Reviewers should call this out if they
see `git ls-files --eol` reporting `i/lf w/crlf`.

## What we want from this review

Code-review the diff below. Focus on:
- Correctness given the Wildbook conventions above.
- Whether the implementation matches the locked design.
- Test coverage and gaps.
- Anything else.

**Do not write to any file.** Review-only.

---

# Codex code-review: C15 — Task.matchResultsJson decouples MR serialization from methodInfo

Follow-up to Codex's own root-cause analysis (saved at
docs/plans/2026-05-19-empty-results-rca.md — about to be added).
Codex identified that Task.matchResultsJson wraps the existing-MR
serialization inside `if (methodInfo != null)`, and the v2
ml-service path never persists `ibeis.identification` on its match
task params, so methodInfo is null and the matchResults field is
silently omitted from the API response despite the MR existing in
the DB.

Confirmed end-to-end: the React match-results page makes an
authenticated call to /api/v3/tasks/<id>/match-results and gets
back a task node with `statusOverall: "completed"` and
`__taskAnnotations` but NO `matchResults` field — exactly Codex's
predicted failure mode.

## Fix

Move existing-MR serialization (getLatestMatchResult + rtn.put)
OUTSIDE the methodInfo guard. Keep the legacy log-based
generateMatchResults call INSIDE the methodInfo guard since its
job is to interpret identification-method-specific log JSON
(only relevant for WBIA-log-based identification, not vector
search). methodInfo continues to gate the "method" / "matchingSetFilter"
JSON keys since those are display-extras for the identification UI.

## Diff

diff --git a/src/main/java/org/ecocean/ia/Task.java b/src/main/java/org/ecocean/ia/Task.java
index cc543ce2a..f7af5596d 100644
--- a/src/main/java/org/ecocean/ia/Task.java
+++ b/src/main/java/org/ecocean/ia/Task.java
@@ -835,34 +835,40 @@ public class Task implements java.io.Serializable {
             rtn.put("__taskAnnotations", annotArr);
         }
         JSONObject methodInfo = getIdentificationMethodInfo();
-        // we basically use this to determine if we are "identification-like" enough
-        // to display extended details
+        // methodInfo gates the "identification-like" extras (method label,
+        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
+        // ml-service path doesn't persist `ibeis.identification` on its
+        // match tasks, so methodInfo is null even though the MatchResult
+        // is correctly persisted in the DB. Without the decoupling below,
+        // the API silently drops the matchResults field from the JSON
+        // tree and the React match-results page renders empty
+        // (empty-match-prospects design Track 2 C15).
         if (methodInfo != null) {
             rtn.put("method", methodInfo);
             rtn.put("matchingSetFilter", getMatchingSetFilter());
-/*
-            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
-               (as there may be non-leaf nodes with methodInfo)
- * note: we try getting it regardless of children ("just in case"); but only try to generate if none
-            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
-               as vector results should have generated their MatchResult upon completion
- */
-            MatchResult mr = getLatestMatchResult(myShepherd);
-            if ((mr == null) && !hasChildren()) {
-                System.out.println(
-                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
-                    this.getId());
-                List<MatchResult> mrs = generateMatchResults(myShepherd);
-                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
-                if (mrs.size() > 0) {
-                    mr = mrs.get(mrs.size() - 1);
-                    // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
-                    rtn.put("_commitShepherd", true);
-                }
+        }
+        // Always serialize an existing MatchResult regardless of methodInfo.
+        // Vector (v2) results generate their MatchResult eagerly during
+        // matching, so getLatestMatchResult will find one whenever the
+        // pipeline ran successfully. Legacy WBIA results still rely on
+        // generateMatchResults (log-based) to construct the MR on demand,
+        // and that path stays gated by methodInfo since it interprets
+        // identification-method-specific log JSON.
+        MatchResult mr = getLatestMatchResult(myShepherd);
+        if ((mr == null) && (methodInfo != null) && !hasChildren()) {
+            System.out.println(
+                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
+                this.getId());
+            List<MatchResult> mrs = generateMatchResults(myShepherd);
+            rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
+            if (mrs.size() > 0) {
+                mr = mrs.get(mrs.size() - 1);
+                // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
+                rtn.put("_commitShepherd", true);
             }
-            if (mr != null)
-                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
         }
+        if (mr != null)
+            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
         // now we recurse thru children if applicable
         if (hasChildren()) {
             JSONArray charr = new JSONArray();

## Validation

- Compiles cleanly (`mvn -DskipITs compile` → BUILD SUCCESS)
- Existing TaskStatusInEndStateTest + TaskGetStoredStatusTest still
  pass (will re-run after this commit).
- LF-normalized.

## Review scope

- Does decoupling break any existing identification-like flow?
  methodInfo is still set when present; "method" and
  "matchingSetFilter" keys still emitted; generateMatchResults
  still runs in the same conditions as before (methodInfo != null
  AND !hasChildren AND mr == null).
- The only behavior change is that existing MRs are ALWAYS
  serialized into the response, regardless of methodInfo. Is that
  a regression for any caller that depended on matchResults NOT
  being present when the task wasn't identification-like?
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this review read-only. I’m going to inspect the surrounding `Task` code and the helper methods so the review is grounded in the actual control flow, not just the patch hunk.
exec
/bin/bash -lc "rg -n \"matchResultsJson|getLatestMatchResult|generateMatchResults|getIdentificationMethodInfo|jsonForApiGet\" src/main/java/org/ecocean/ia/Task.java src/main/java -g'*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git branch --show-current' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol src/main/java/org/ecocean/ia/Task.java' in /mnt/c/Wildbook-clean2
 succeeded in 1031ms:
src/main/java/org/ecocean/ia/Task.java:744:    public JSONObject getIdentificationMethodInfo() {
src/main/java/org/ecocean/ia/Task.java:781:    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:789:    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:801:                    System.out.println("[INFO] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:808:                    System.out.println("[ERROR] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:837:        JSONObject methodInfo = getIdentificationMethodInfo();
src/main/java/org/ecocean/ia/Task.java:852:        // matching, so getLatestMatchResult will find one whenever the
src/main/java/org/ecocean/ia/Task.java:854:        // generateMatchResults (log-based) to construct the MR on demand,
src/main/java/org/ecocean/ia/Task.java:857:        MatchResult mr = getLatestMatchResult(myShepherd);
src/main/java/org/ecocean/ia/Task.java:860:                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/Task.java:862:            List<MatchResult> mrs = generateMatchResults(myShepherd);
src/main/java/org/ecocean/ia/Task.java:871:            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/ia/Task.java:877:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
src/main/java/org/ecocean/Base.java:284:    public JSONObject jsonForApiGet(Shepherd myShepherd, User user)
src/main/java/org/ecocean/Base.java:289:        // override jsonForApiGet() if this is undesirable behavior (e.g. Encounter)
src/main/java/org/ecocean/api/GenericObject.java:124:                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
src/main/java/org/ecocean/Encounter.java:1645:    // this is for jsonForApiGet()
src/main/java/org/ecocean/Encounter.java:4773:    public org.json.JSONObject jsonForApiGet(Shepherd myShepherd, User user)
src/main/java/org/ecocean/api/BaseObject.java:258:    we now let jsonForApiGet() handle this, as some objects non-logged-in users can see
src/main/java/org/ecocean/api/BaseObject.java:259:    part of. therefore, jsonForApiGet() must set statusCode/error as needed.
src/main/java/org/ecocean/api/BaseObject.java:266:                rtn = obj.jsonForApiGet(myShepherd, currentUser);
src/main/java/org/ecocean/ia/Task.java:744:    public JSONObject getIdentificationMethodInfo() {
src/main/java/org/ecocean/ia/Task.java:781:    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:789:    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:801:                    System.out.println("[INFO] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:808:                    System.out.println("[ERROR] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:837:        JSONObject methodInfo = getIdentificationMethodInfo();
src/main/java/org/ecocean/ia/Task.java:852:        // matching, so getLatestMatchResult will find one whenever the
src/main/java/org/ecocean/ia/Task.java:854:        // generateMatchResults (log-based) to construct the MR on demand,
src/main/java/org/ecocean/ia/Task.java:857:        MatchResult mr = getLatestMatchResult(myShepherd);
src/main/java/org/ecocean/ia/Task.java:860:                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/Task.java:862:            List<MatchResult> mrs = generateMatchResults(myShepherd);
src/main/java/org/ecocean/ia/Task.java:871:            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/ia/Task.java:877:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
src/main/java/org/ecocean/ia/MatchResultProspect.java:82:    public JSONObject jsonForApiGet(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:565:                jarr.put(mrp.jsonForApiGet(myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:572:    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {

 succeeded in 1014ms:
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/Task.java

codex
The line-ending check is clean for `Task.java` (`i/lf w/lf`). I found the changed method and the API caller; next I’m reading the helper implementations and the endpoint wrapper to check whether “always serialize existing MR” has side effects.
 succeeded in 12273ms:
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/ia/Task.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
migrate-ml-service-v2

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '720,900p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '520,610p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/GenericObject.java | sed -n '90,150p'" in /mnt/c/Wildbook-clean2
 succeeded in 206ms:
   520	        JSONObject params = task.getParameters();
   521	        if (params == null) return null;
   522	        return params.optJSONObject("matchingSetFilter");
   523	    }
   524	
   525	/*
   526	    see note at top about candidates vs numberCandidates
   527	    public int numberCandidates() {
   528	        return Util.collectionSize(candidates);
   529	    }
   530	 */
   531	    public int numberProspects() {
   532	        return this.numberProspects;
   533	    }
   534	
   535	    public Set<String> prospectScoreTypes() {
   536	        Set<String> types = new HashSet<String>();
   537	
   538	        if (numberProspects() == 0) return types;
   539	        for (MatchResultProspect mrp : prospects) {
   540	            types.add(mrp.getType());
   541	        }
   542	        return types;
   543	    }
   544	
   545	    // if cutoff < 0 then it will not be truncated at all
   546	    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
   547	        Set<String> projectIds, Shepherd myShepherd) {
   548	        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
   549	
   550	        if (numberProspects() == 0) return pros;
   551	        for (MatchResultProspect mrp : prospects) {
   552	            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
   553	        }
   554	        Collections.sort(pros);
   555	        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
   556	        return pros;
   557	    }
   558	
   559	    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   560	        JSONObject sj = new JSONObject();
   561	
   562	        for (String type : prospectScoreTypes()) {
   563	            JSONArray jarr = new JSONArray();
   564	            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
   565	                jarr.put(mrp.jsonForApiGet(myShepherd));
   566	            }
   567	            sj.put(type, jarr);
   568	        }
   569	        return sj;
   570	    }
   571	
   572	    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   573	        JSONObject rtn = new JSONObject();
   574	
   575	        rtn.put("id", id);
   576	        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
   577	        rtn.put("numberTotalProspects", numberProspects());
   578	        rtn.put("numberCandidates", getNumberCandidates());
   579	        rtn.put("created", Util.millisToISO8601String(created));
   580	        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
   581	        rtn.put("projectIds", projectIds);
   582	        return rtn;
   583	    }
   584	
   585	    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
   586	        JSONObject aj = new JSONObject();
   587	
   588	        if (ann == null) return aj;
   589	        MediaAsset ma = ann.getMediaAsset();
   590	        // populate bounding box stuff (note: it may reset aj so must be done first)
   591	        if (ann.getFeatures() != null) {
   592	            for (Feature ft : ann.getFeatures()) {
   593	                if (ft.isUnity()) {
   594	                    aj.put("trivial", true);
   595	                    aj.put("x", 0);
   596	                    aj.put("y", 0);
   597	                    // would be weird to be null, but.....
   598	                    if (ma != null) {
   599	                        aj.put("width", (int)ma.getWidth());
   600	                        aj.put("height", (int)ma.getHeight());
   601	                    }
   602	                } else {
   603	                    // basically if we have more than one feature, only one wins
   604	                    if (ft.getParameters() != null) aj = ft.getParameters();
   605	                }
   606	            }
   607	        }
   608	        if (ma != null) {
   609	            JSONObject mj = ma.toSimpleJSONObject();
   610	            mj.put("rotationInfo", ma.getRotationInfo());

 succeeded in 208ms:
   720	
   721	    // this will set all date stuff based on ms since epoch
   722	    public void setCompletionDateInMilliseconds(Long ms) {
   723	        this.completionDateInMilliseconds = ms;
   724	    }
   725	
   726	    // no arg = set to now
   727	    public void setCompletionDateInMilliseconds() {
   728	        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
   729	    }
   730	
   731	    // capture original queue message to make this Task more easily resumeable
   732	    public String getQueueResumeMessage() { return queueResumeMessage; }
   733	    public void setQueueResumeMessage(String message) {
   734	        if (message == null) { queueResumeMessage = null; } else {
   735	            queueResumeMessage = message;
   736	        }
   737	    }
   738	
   739	    public JSONObject getMatchingSetFilter() {
   740	        if (getParameters() == null) return null;
   741	        return getParameters().optJSONObject("matchingSetFilter");
   742	    }
   743	
   744	    public JSONObject getIdentificationMethodInfo() {
   745	        if (getParameters() == null) return null;
   746	        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
   747	        JSONObject rtn = new JSONObject();
   748	        // vector/embed flavor
   749	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
   750	            null) != null) {
   751	            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
   752	                "model_id", null);
   753	            if (modelId == null) {
   754	                rtn.put("description", "Vector embedding match");
   755	            } else {
   756	                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
   757	                rtn.put("modelId", modelId);
   758	            }
   759	            return rtn;
   760	        }
   761	        // it seems both of these are in most logs (and are identical), but being safe in case there are
   762	        // examples in the wild with only one
   763	        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   764	            "query_config_dict");
   765	        if (conf == null)
   766	            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   767	                "queryConfigDict");
   768	        // we set HotSpotter if pipeline_root is not set here
   769	        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
   770	        rtn.put("description",
   771	            getParameters().getJSONObject("ibeis.identification").optString("description",
   772	            "unknown algorithm/method"));
   773	        return rtn;
   774	    }
   775	
   776	    // convenience
   777	    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
   778	        return myShepherd.getMatchResults(this);
   779	    }
   780	
   781	    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
   782	        List<MatchResult> all = myShepherd.getMatchResults(this);
   783	
   784	        if (Util.collectionIsEmptyOrNull(all)) return null;
   785	        return all.get(0);
   786	    }
   787	
   788	    // logs are returned in chronological order here, so if the latest is desired, take the LAST one
   789	    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
   790	        List<MatchResult> mrs = new ArrayList<MatchResult>();
   791	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(this.id, "IBEISIA",
   792	            myShepherd);
   793	
   794	        if (logs == null) return mrs;
   795	        for (IdentityServiceLog log : logs) {
   796	            JSONObject res = log.getJsonResult();
   797	            // in theory this is how we can tell if it is an ident result log versus detection
   798	            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
   799	                try {
   800	                    MatchResult mr = new MatchResult(log, myShepherd);
   801	                    System.out.println("[INFO] generateMatchResults() [log t=" +
   802	                        log.getTimestamp() + "] on Task " + this.getId() + " generated: " + mr);
   803	                    myShepherd.getPM().makePersistent(mr);
   804	                    mrs.add(mr);
   805	                    setStatusDetailsAddLog("Created " + mr + " from IdentityServiceLog " +
   806	                        log.getTimestamp());
   807	                } catch (java.io.IOException ex) {
   808	                    System.out.println("[ERROR] generateMatchResults() [log t=" +
   809	                        log.getTimestamp() + "] on Task " + this.getId() + " failed: " + ex);
   810	                    ex.printStackTrace();
   811	                    setStatusDetailsAddError("UNKNOWN",
   812	                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
   813	                        " failed due to: " + ex);
   814	                }
   815	            }
   816	        }
   817	        return mrs;
   818	    }
   819	
   820	    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   821	        JSONObject rtn = new JSONObject();
   822	
   823	        rtn.put("id", getId());
   824	        rtn.put("parentTaskId", getParentId());
   825	        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
   826	        rtn.put("dateCompleted", Util.millisToISO8601String(getCompletionDateInMilliseconds()));
   827	        rtn.put("timeInactiveMillis", timeInactive());
   828	        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
   829	        // we should have it here, hence this debugging value ... possible optimization for later
   830	        if (hasObjectAnnotations()) {
   831	            JSONArray annotArr = new JSONArray();
   832	            for (Annotation ann : getObjectAnnotations()) {
   833	                if (ann != null) annotArr.put(ann.getId());
   834	            }
   835	            rtn.put("__taskAnnotations", annotArr);
   836	        }
   837	        JSONObject methodInfo = getIdentificationMethodInfo();
   838	        // methodInfo gates the "identification-like" extras (method label,
   839	        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
   840	        // ml-service path doesn't persist `ibeis.identification` on its
   841	        // match tasks, so methodInfo is null even though the MatchResult
   842	        // is correctly persisted in the DB. Without the decoupling below,
   843	        // the API silently drops the matchResults field from the JSON
   844	        // tree and the React match-results page renders empty
   845	        // (empty-match-prospects design Track 2 C15).
   846	        if (methodInfo != null) {
   847	            rtn.put("method", methodInfo);
   848	            rtn.put("matchingSetFilter", getMatchingSetFilter());
   849	        }
   850	        // Always serialize an existing MatchResult regardless of methodInfo.
   851	        // Vector (v2) results generate their MatchResult eagerly during
   852	        // matching, so getLatestMatchResult will find one whenever the
   853	        // pipeline ran successfully. Legacy WBIA results still rely on
   854	        // generateMatchResults (log-based) to construct the MR on demand,
   855	        // and that path stays gated by methodInfo since it interprets
   856	        // identification-method-specific log JSON.
   857	        MatchResult mr = getLatestMatchResult(myShepherd);
   858	        if ((mr == null) && (methodInfo != null) && !hasChildren()) {
   859	            System.out.println(
   860	                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
   861	                this.getId());
   862	            List<MatchResult> mrs = generateMatchResults(myShepherd);
   863	            rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
   864	            if (mrs.size() > 0) {
   865	                mr = mrs.get(mrs.size() - 1);
   866	                // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
   867	                rtn.put("_commitShepherd", true);
   868	            }
   869	        }
   870	        if (mr != null)
   871	            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
   872	        // now we recurse thru children if applicable
   873	        if (hasChildren()) {
   874	            JSONArray charr = new JSONArray();
   875	            for (Task child : children) {
   876	                // TODO decide if we need to process child????
   877	                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
   878	                // we have to bubble this up all the way to the toplevel  :/
   879	                if (childJson.optBoolean("_commitShepherd", false))
   880	                    rtn.put("_commitShepherd", true);
   881	                charr.put(childJson);
   882	            }
   883	            rtn.put("children", charr);
   884	            // if we dont have children (leaf nodes) we get the status
   885	        } else {
   886	            // unsure which of these two things is more accurate or useful; thus including both
   887	            rtn.put("status", getStatus(myShepherd));
   888	            rtn.put("statusOverall", getOverallStatus(myShepherd));
   889	            rtn.put("statusDetails", getStatusDetails());
   890	        }
   891	        return rtn;
   892	    }
   893	}

 succeeded in 262ms:
    90	                                jann.put("encounterTaxonomy", enc.getTaxonomyString());
    91	                            }
    92	                            jann.put("id", ann.getId());
    93	                            janns.put(jann);
    94	                        }
    95	                        rtn.put("annotations", janns);
    96	                    }
    97	                }
    98	                break;
    99	            case "tasks":
   100	                if (currentUser == null) {
   101	                    rtn.put("statusCode", 401);
   102	                    rtn.put("error", "access denied");
   103	                } else {
   104	                    if ((args.length > 2) && ("match-results".equals(args[2]))) {
   105	                        Task task = myShepherd.getTask(args[1]);
   106	                        if (task == null) {
   107	                            rtn.put("statusCode", 404);
   108	                            rtn.put("error", "not found");
   109	                        } else {
   110	                            // right now we replicate legacy functionality and allow access to anyone
   111	                            // based on task id only, rather than blocking based on task.canUserAccess()
   112	                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
   113	                            Set<String> projectIds = null;
   114	                            String[] pvals = request.getParameterValues("projectId");
   115	                            if ((pvals != null) && (pvals.length > 0))
   116	                                projectIds = new HashSet<String>(Arrays.asList(
   117	                                    request.getParameterValues("projectId")));
   118	                            try {
   119	                                // note: negative size means all of them (no cutoff)
   120	                                prospectsSize = Integer.parseInt(request.getParameter(
   121	                                    "prospectsSize"));
   122	                            } catch (NumberFormatException ex) {}
   123	                            rtn.put("prospectsSize", prospectsSize);
   124	                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
   125	                                myShepherd);
   126	                            rtn.put("projectIds", projectIds);
   127	                            rtn.put("matchResultsRoot", mrJson);
   128	                            rtn.put("success", true);
   129	                            rtn.put("statusCode", 200);
   130	                            // this means we created on-the-fly some MatchResult(s) that need persisting
   131	                            commitShepherd = (mrJson != null) &&
   132	                                mrJson.optBoolean("_commitShepherd", false);
   133	                            if (commitShepherd) myShepherd.commitDBTransaction();
   134	                        }
   135	                    } else {
   136	                        throw new ApiException("invalid tasks operation");
   137	                    }
   138	                }
   139	                break;
   140	            default:
   141	                throw new ApiException("bad class");
   142	            }
   143	        } catch (ApiException apiEx) {
   144	            rtn.put("statusCode", 400);
   145	            rtn.put("errors", apiEx.getErrors());
   146	            rtn.put("debug", apiEx.toString());
   147	        } finally {
   148	            if (commitShepherd) {
   149	                myShepherd.closeDBTransaction();
   150	            } else {

exec
/bin/bash -lc "rg -n \"List<MatchResult> getMatchResults|MatchResult.*Task|query.*MatchResult|getMatchResults\\(\" src/main/java/org/ecocean -g'*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff -- src/main/java/org/ecocean/ia/Task.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"new MatchResult|setTask\\(|task.*MatchResult|makePersistent\\(mr\\)|MatchResult\\(\" src/main/java/org/ecocean src/test/java -g'*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 1103ms:
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/ia/Task.java:777:    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:778:        return myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:782:        List<MatchResult> all = myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:828:        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
src/main/java/org/ecocean/ia/Task.java:860:                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/MlServiceProcessor.java:566:     * MatchResult attached to a child of {@code matchTaskId}. Runs
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        List<String> mrIds = collectMatchResultIds(matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:593:    private List<String> collectMatchResultIds(String matchTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:596:        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:608:                    List<MatchResult> mrs = shep.getMatchResults(child);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:122:            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
src/main/java/org/ecocean/ia/MatchResult.java:59:    public MatchResult(Task task) {
src/main/java/org/ecocean/ia/MatchResult.java:70:    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:77:    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820:    public List<MatchResult> getMatchResults(Task task) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2834:    public List<MatchResult> getMatchResults(Annotation ann) {

 succeeded in 1213ms:
diff --git a/src/main/java/org/ecocean/ia/Task.java b/src/main/java/org/ecocean/ia/Task.java
index cc543ce2a..f7af5596d 100644
--- a/src/main/java/org/ecocean/ia/Task.java
+++ b/src/main/java/org/ecocean/ia/Task.java
@@ -835,34 +835,40 @@ public class Task implements java.io.Serializable {
             rtn.put("__taskAnnotations", annotArr);
         }
         JSONObject methodInfo = getIdentificationMethodInfo();
-        // we basically use this to determine if we are "identification-like" enough
-        // to display extended details
+        // methodInfo gates the "identification-like" extras (method label,
+        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
+        // ml-service path doesn't persist `ibeis.identification` on its
+        // match tasks, so methodInfo is null even though the MatchResult
+        // is correctly persisted in the DB. Without the decoupling below,
+        // the API silently drops the matchResults field from the JSON
+        // tree and the React match-results page renders empty
+        // (empty-match-prospects design Track 2 C15).
         if (methodInfo != null) {
             rtn.put("method", methodInfo);
             rtn.put("matchingSetFilter", getMatchingSetFilter());
-/*
-            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
-               (as there may be non-leaf nodes with methodInfo)
- * note: we try getting it regardless of children ("just in case"); but only try to generate if none
-            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
-               as vector results should have generated their MatchResult upon completion
- */
-            MatchResult mr = getLatestMatchResult(myShepherd);
-            if ((mr == null) && !hasChildren()) {
-                System.out.println(
-                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
-                    this.getId());
-                List<MatchResult> mrs = generateMatchResults(myShepherd);
-                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
-                if (mrs.size() > 0) {
-                    mr = mrs.get(mrs.size() - 1);
-                    // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
-                    rtn.put("_commitShepherd", true);
-                }
+        }
+        // Always serialize an existing MatchResult regardless of methodInfo.
+        // Vector (v2) results generate their MatchResult eagerly during
+        // matching, so getLatestMatchResult will find one whenever the
+        // pipeline ran successfully. Legacy WBIA results still rely on
+        // generateMatchResults (log-based) to construct the MR on demand,
+        // and that path stays gated by methodInfo since it interprets
+        // identification-method-specific log JSON.
+        MatchResult mr = getLatestMatchResult(myShepherd);
+        if ((mr == null) && (methodInfo != null) && !hasChildren()) {
+            System.out.println(
+                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
+                this.getId());
+            List<MatchResult> mrs = generateMatchResults(myShepherd);
+            rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
+            if (mrs.size() > 0) {
+                mr = mrs.get(mrs.size() - 1);
+                // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
+                rtn.put("_commitShepherd", true);
             }
-            if (mr != null)
-                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
         }
+        if (mr != null)
+            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
         // now we recurse thru children if applicable
         if (hasChildren()) {
             JSONArray charr = new JSONArray();

 succeeded in 1311ms:
src/main/java/org/ecocean/api/GenericObject.java:130:                            // this means we created on-the-fly some MatchResult(s) that need persisting
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/Embedding.java:379:                myShepherd.getPM().makePersistent(mr);
src/main/java/org/ecocean/ia/MatchResult.java:54:    public MatchResult() {
src/main/java/org/ecocean/ia/MatchResult.java:59:    public MatchResult(Task task) {
src/main/java/org/ecocean/ia/MatchResult.java:64:    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:70:    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:77:    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
src/main/java/org/ecocean/ia/MatchResult.java:215:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/main/java/org/ecocean/ia/MatchResult.java:255:            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:62:    public int enrichMatchResult(String matchResultId) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:108:            MatchResult mr = shep.getMatchResult(matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:223:            MatchResult mr = shep.getMatchResult(dto.matchResultId);
src/main/java/org/ecocean/ia/Task.java:781:    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:800:                    MatchResult mr = new MatchResult(log, myShepherd);
src/main/java/org/ecocean/ia/Task.java:803:                    myShepherd.getPM().makePersistent(mr);
src/main/java/org/ecocean/ia/Task.java:841:        // match tasks, so methodInfo is null even though the MatchResult
src/main/java/org/ecocean/ia/Task.java:857:        MatchResult mr = getLatestMatchResult(myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1884:                MatchResult mr = new MatchResult(task, j, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1886:                myShepherd.getPM().makePersistent(mr);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:579:                enricher.enrichMatchResult(mrId);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:84:    @Test void findProspect_returnsNullForNullMatchResult() {
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:89:        MatchResult mr = new MatchResult();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:95:        MatchResult mr = new MatchResult();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:100:        MatchResultProspect p1 = new MatchResultProspect(a1, 1.0d, "annot", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:101:        MatchResultProspect p2 = new MatchResultProspect(a2, 0.5d, "annot", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:116:        MatchResult mr = new MatchResult();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:119:        MatchResultProspect annotP = new MatchResultProspect(a1, 1.0d, "annot", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:120:        MatchResultProspect indivP = new MatchResultProspect(a1, 0.8d, "indiv", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:137:        MatchResult mr = new MatchResult();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:141:        set.add(new MatchResultProspect(a1, 1.0d, "annot", null));
src/test/java/org/ecocean/MatchResultTest.java:36:        MatchResult mr = new MatchResult(task);
src/test/java/org/ecocean/MatchResultTest.java:91:        MatchResult mr = new MatchResult(task, annList, numCand, null);
src/test/java/org/ecocean/MatchResultTest.java:100:        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2808:    public MatchResult getMatchResult(String id) {

codex
The implementation is only adding one query per task node and still keeps the on-demand legacy generation behind `methodInfo && leaf`. I’m checking the JDO query ordering and the v2 writer path now, because that’s where subtle “latest result” or parent/child behavior would show up.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/shepherd/core/Shepherd.java | sed -n '2800,2855p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/identity/IBEISIA.java | sed -n '1860,1895p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '340,395p'" in /mnt/c/Wildbook-clean2
 succeeded in 249ms:
     1	package org.ecocean.ia;
     2	
     3	import java.io.File;
     4	import java.io.IOException;
     5	import java.net.URL;
     6	import java.util.ArrayList;
     7	import java.util.Collections;
     8	import java.util.Comparator;
     9	import java.util.HashMap;
    10	import java.util.HashSet;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	
    15	import org.json.JSONArray;
    16	import org.json.JSONObject;
    17	
    18	import org.ecocean.api.UploadedFiles;
    19	import org.ecocean.Annotation;
    20	import org.ecocean.Encounter;
    21	import org.ecocean.ia.MLService;
    22	import org.ecocean.ia.Task;
    23	import org.ecocean.identity.IBEISIA;
    24	import org.ecocean.identity.IdentityServiceLog;
    25	import org.ecocean.media.AssetStore;
    26	import org.ecocean.media.Feature;
    27	import org.ecocean.media.MediaAsset;
    28	import org.ecocean.media.URLAssetStore;
    29	import org.ecocean.MarkedIndividual;
    30	import org.ecocean.RestClient;
    31	import org.ecocean.shepherd.core.Shepherd;
    32	import org.ecocean.Util;
    33	
    34	public class MatchResult implements java.io.Serializable {
    35	    private String id;
    36	    private long created;
    37	    private Task task;
    38	    private Set<MatchResultProspect> prospects;
    39	    private Annotation queryAnnotation;
    40	    private int numberCandidates = 0;
    41	    // we store *actual* count here, but they may not all exist
    42	    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
    43	    private int numberProspects = 0;
    44	    // not sure we really *need* true fk link to these annots
    45	    // they might be gone now and will we ever use this?
    46	    // so for now we just populate numberCandidates
    47	    private Set<Annotation> candidates;
    48	    // fallback number to cutoff number of prospects to return
    49	    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
    50	    // number of MatchResultProspects [per type] to actually store (hotspotter
    51	    // results can produce thousands, but storing them all is excessive)
    52	    public static final int MAXIMUM_PROSPECTS_STORED = 500;
    53	
    54	    public MatchResult() {
    55	        id = Util.generateUUID();
    56	        created = System.currentTimeMillis();
    57	    }
    58	
    59	    public MatchResult(Task task) {
    60	        this();
    61	        this.task = task;
    62	    }
    63	
    64	    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
    65	    throws IOException {
    66	        this();
    67	        this.createFromIdentityServiceLog(isLog, myShepherd);
    68	    }
    69	
    70	    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
    71	    throws IOException {
    72	        this();
    73	        this.task = task;
    74	        this.createFromJsonResult(jsonResult, myShepherd);
    75	    }
    76	
    77	    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
    78	        Shepherd myShepherd)
    79	    throws IOException {
    80	        this();
    81	        this.task = task;
    82	        this.numberCandidates = numberCandidates;
    83	        this.setQueryAnnotationFromTask();
    84	        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
    85	        this.populateProspects(annots, false, myShepherd);
    86	        this.populateProspects(annots, true, myShepherd);
    87	    }
    88	
    89	    public int getNumberCandidates() {
    90	        return numberCandidates;
    91	    }
    92	
    93	    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
    94	    throws IOException {
    95	        if (isLog == null) throw new IOException("log passed is null");
    96	        String taskId = isLog.getTaskID();
    97	        this.task = myShepherd.getTask(taskId);
    98	        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
    99	        JSONObject res = isLog.getJsonResult();
   100	        if (res == null) {
   101	            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
   102	                isLog.getStatusJson());
   103	            throw new IOException("could not get json result");
   104	        }
   105	        createFromJsonResult(res, myShepherd);
   106	    }
   107	
   108	    public Annotation setQueryAnnotationFromTask()
   109	    throws IOException {
   110	        if (this.task == null)
   111	            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
   112	        int numAnns = this.task.countObjectAnnotations();
   113	        if (numAnns < 1)
   114	            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
   115	        if (numAnns > 1)
   116	            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
   117	                " annotations; using first");
   118	        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
   119	        return this.queryAnnotation;
   120	    }

 succeeded in 300ms:
  2800	        Query q = getPM().newQuery("javax.jdo.query.SQL", sql);
  2801	        q.setClass(Task.class);
  2802	        Collection c = (Collection)q.execute();
  2803	        List<Task> all = new ArrayList(c);
  2804	        q.closeAll();
  2805	        return all;
  2806	    }
  2807	
  2808	    public MatchResult getMatchResult(String id) {
  2809	        MatchResult mr = null;
  2810	
  2811	        try {
  2812	            mr = (MatchResult)(pm.getObjectById(pm.newObjectIdInstance(MatchResult.class, id),
  2813	                true));
  2814	        } catch (Exception ex) {
  2815	            ex.printStackTrace();
  2816	        }
  2817	        return mr;
  2818	    }
  2819	
  2820	    public List<MatchResult> getMatchResults(Task task) {
  2821	        List<MatchResult> all = new ArrayList<MatchResult>();
  2822	
  2823	        if (task == null) return all;
  2824	        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE task.id == '" + task.getId() +
  2825	            "'";
  2826	        Query query = pm.newQuery(filter);
  2827	        query.setOrdering("created DESC");
  2828	        Collection c = (Collection)query.execute();
  2829	        if (c != null) all = new ArrayList<MatchResult>(c);
  2830	        query.closeAll();
  2831	        return all;
  2832	    }
  2833	
  2834	    public List<MatchResult> getMatchResults(Annotation ann) {
  2835	        List<MatchResult> all = new ArrayList<MatchResult>();
  2836	
  2837	        if (ann == null) return all;
  2838	        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
  2839	            ann.getId() + "'";
  2840	        Query query = pm.newQuery(filter);
  2841	        query.setOrdering("created DESC");
  2842	        Collection c = (Collection)query.execute();
  2843	        if (c != null) all = new ArrayList<MatchResult>(c);
  2844	        query.closeAll();
  2845	        return all;
  2846	    }
  2847	
  2848	    // faster deletion of all MatchResults associated with Annotation
  2849	    public long deleteMatchResults(Annotation ann) {
  2850	        if (ann == null) return 0l;
  2851	        long t = System.currentTimeMillis();
  2852	        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
  2853	            ann.getId() + "'";
  2854	        Query query = pm.newQuery(filter);
  2855	        long ct = query.deletePersistentAll(); 

 succeeded in 263ms:
  1860	                if (!needReviewMap.keySet().contains(aid)) {
  1861	                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
  1862	                    needNameResolution.add(anns.get(aid));
  1863	                }
  1864	            }
  1865	        } else {
  1866	            for (String aid : anns.keySet()) {
  1867	                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
  1868	                needNameResolution.add(anns.get(aid));
  1869	            }
  1870	            jlog.put("loopComplete", true);
  1871	            rtn.put("loopComplete", true);
  1872	            jlog.put("_infDict", infDict);
  1873	            exitIdentificationLoop(infDict, myShepherd);
  1874	        }
  1875	        resolveNames(needNameResolution, j.optJSONObject("cm_dict"), myShepherd);
  1876	        log(taskID, null, jlog, myShepherd.getContext());
  1877	
  1878	        // set "completed" on Task
  1879	        Task task = myShepherd.getTask(taskID);
  1880	        if (task != null) {
  1881	            task.setStatus("completed");
  1882	            task.setCompletionDateInMilliseconds();
  1883	            try {
  1884	                MatchResult mr = new MatchResult(task, j, myShepherd);
  1885	                System.out.println("processCallbackIdentify() created " + mr + " on " + task);
  1886	                myShepherd.getPM().makePersistent(mr);
  1887	                task.setStatusDetailsAddLog("Created " + mr + " upon task completion");
  1888	            } catch (IOException ex) {
  1889	                System.out.println("processCallbackIdentify() failed to create MatchResult on " +
  1890	                    task + ": " + ex);
  1891	                ex.printStackTrace();
  1892	                task.setStatusDetailsAddError("UNKNOWN",
  1893	                    "Creation of MatchResult upon task completion failed due to: " + ex);
  1894	            }
  1895	        }

 succeeded in 309ms:
   340	            OpenSearch os = new OpenSearch();
   341	            int numberCandidates = -2;
   342	            try {
   343	                numberCandidates = os.queryCount("annotation", matchingSetQuery);
   344	            } catch (IOException ex) {
   345	                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
   346	            }
   347	            // Read method/version directly from the new _id_conf contract;
   348	            // fall back to splitting model_id for legacy configs.
   349	            String method = iaConfig.optString("method", null);
   350	            String version = iaConfig.optString("version", null);
   351	            if (!Util.stringExists(method)) {
   352	                String[] mv = MLService.getMethodValues(iaConfig);
   353	                method = mv[0];
   354	                version = mv[1];
   355	            }
   356	            JSONObject matchQuery = ann.getMatchQuery(method, version,
   357	                new JSONObject(matchingSetQuery.toString()));
   358	            // i think this will never happen now, due to on-the-fly fix above; but leaving to be safe
   359	            if (matchQuery == null) {
   360	                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
   361	                    " due to no suitable embeddings for " + iaConfig);
   362	                subTask.setStatus("error");
   363	                subTask.setStatusDetailsAddError("REQUIRED",
   364	                    "no suitable embeddings for getMatches()");
   365	                subTask.setCompletionDateInMilliseconds();
   366	                myShepherd.getPM().makePersistent(subTask);
   367	                subtasksFailed++;
   368	                continue; // on to next ann
   369	            }
   370	            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
   371	            System.out.println("findMatchProspects() on " + ann + " found " +
   372	                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
   373	                " candidates) for subTask " + subTask.getId());
   374	            boolean mrOk = true;
   375	            try {
   376	                // we build this even if empty, cuz that means we got results; just not nice ones
   377	                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
   378	                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
   379	                myShepherd.getPM().makePersistent(mr);
   380	            } catch (IOException ex) {
   381	                System.out.println("findMatchProspects() MatchResult creation failed on " +
   382	                    subTask + ": " + ex);
   383	                ex.printStackTrace();
   384	                mrOk = false;
   385	            }
   386	            if (mrOk) {
   387	                subTask.setStatus("completed");
   388	                subtasksOk++;
   389	            } else {
   390	                subTask.setStatus("error");
   391	                subTask.setStatusDetailsAddError("UNKNOWN",
   392	                    "MatchResult persistence failed");
   393	                subtasksFailed++;
   394	            }
   395	            subTask.setCompletionDateInMilliseconds();

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/api/TaskMatchResults.java | sed -n '1,240p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"match-results|matchResultsRoot|matchResults|TaskMatchResults|_generatedMatchResultsSize\" src/test/java frontend/src -g'*.java' -g'*.js' -g'*.jsx'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getIdentificationMethodInfo\\(|matchResultsJson\\(\" src/test/java src/main/java -g'*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 242ms:
     1	package org.ecocean.api;
     2	
     3	import javax.jdo.PersistenceManager;
     4	import javax.jdo.PersistenceManagerFactory;
     5	import javax.servlet.http.HttpServletRequest;
     6	import javax.servlet.http.HttpServletResponse;
     7	import javax.servlet.ServletException;
     8	
     9	import org.ecocean.ia.Task;
    10	
    11	import org.ecocean.api.GenericObject;
    12	// import org.ecocean.CommonConfiguration;
    13	import org.ecocean.shepherd.core.Shepherd;
    14	import org.ecocean.shepherd.core.ShepherdPMF;
    15	import org.ecocean.User;
    16	
    17	import org.json.JSONArray;
    18	import org.json.JSONObject;
    19	
    20	import java.io.BufferedReader;
    21	import java.io.IOException;
    22	import java.io.PrintWriter;
    23	import java.io.StringReader;
    24	import java.io.StringWriter;
    25	import java.util.ArrayList;
    26	import java.util.List;
    27	import java.util.Map;
    28	
    29	import org.junit.jupiter.api.Assertions;
    30	import org.junit.jupiter.api.BeforeEach;
    31	import org.junit.jupiter.api.Test;
    32	import static org.junit.Assert.*;
    33	import static org.junit.jupiter.api.Assertions.assertThrows;
    34	
    35	import org.mockito.MockedConstruction;
    36	import org.mockito.MockedStatic;
    37	import static org.mockito.ArgumentMatchers.anyString;
    38	import static org.mockito.ArgumentMatchers.eq;
    39	import static org.mockito.Mockito.any;
    40	import static org.mockito.Mockito.doNothing;
    41	import static org.mockito.Mockito.doThrow;
    42	import static org.mockito.Mockito.mock;
    43	import static org.mockito.Mockito.mockConstruction;
    44	import static org.mockito.Mockito.mockStatic;
    45	import static org.mockito.Mockito.verify;
    46	import static org.mockito.Mockito.when;
    47	
    48	class TaskMatchResults {
    49	    PersistenceManagerFactory mockPMF;
    50	    HttpServletRequest mockRequest;
    51	    HttpServletResponse mockResponse;
    52	    GenericObject apiServlet;
    53	    StringWriter responseOut;
    54	
    55	    @BeforeEach void setUp()
    56	    throws IOException {
    57	        mockRequest = mock(HttpServletRequest.class);
    58	        mockResponse = mock(HttpServletResponse.class);
    59	        mockPMF = mock(PersistenceManagerFactory.class);
    60	        apiServlet = new GenericObject();
    61	
    62	        responseOut = new StringWriter();
    63	        PrintWriter writer = new PrintWriter(responseOut);
    64	        when(mockResponse.getWriter()).thenReturn(writer);
    65	
    66	/*
    67	        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
    68	            (mock, context) -> {
    69	                when(mock.getUser(any(HttpServletRequest.class))).thenThrow(new RuntimeException("ohmgee"));
    70	            })) {
    71	            mockRequest = mock(HttpServletRequest.class);
    72	            mockResponse = mock(HttpServletResponse.class);
    73	            apiServlet = new SiteSettings();
    74	        }
    75	 */
    76	    }
    77	
    78	    @Test void apiGet401()
    79	    throws ServletException, IOException {
    80	        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks");
    81	        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
    82	                (mock, context) -> {
    83	            doNothing().when(mock).beginDBTransaction();
    84	        })) {
    85	            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
    86	                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
    87	                apiServlet.doGet(mockRequest, mockResponse);
    88	                responseOut.flush();
    89	                JSONObject jout = new JSONObject(responseOut.toString());
    90	                verify(mockResponse).setStatus(401);
    91	                assertFalse(jout.getBoolean("success"));
    92	            }
    93	        }
    94	    }
    95	
    96	    // basically tests api path without /match-results
    97	    @Test void apiGetInvalidOperation()
    98	    throws ServletException, IOException {
    99	        User user = mock(User.class);
   100	        String id = "some-id";
   101	
   102	        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id);
   103	        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
   104	
   105	        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
   106	                (mock, context) -> {
   107	            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
   108	            when(mock.getTask(any(String.class))).thenReturn(null);
   109	        })) {
   110	            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
   111	                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
   112	                apiServlet.doGet(mockRequest, mockResponse);
   113	                responseOut.flush();
   114	                JSONObject jout = new JSONObject(responseOut.toString());
   115	                verify(mockResponse).setStatus(400);
   116	                assertFalse(jout.getBoolean("success"));
   117	                assertTrue(jout.getString("debug").contains("invalid tasks operation"));
   118	            }
   119	        }
   120	    }
   121	
   122	    @Test void apiGet404()
   123	    throws ServletException, IOException {
   124	        User user = mock(User.class);
   125	        String id = "404-id";
   126	
   127	        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
   128	        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
   129	
   130	        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
   131	                (mock, context) -> {
   132	            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
   133	            when(mock.getTask(any(String.class))).thenReturn(null);
   134	        })) {
   135	            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
   136	                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
   137	                apiServlet.doGet(mockRequest, mockResponse);
   138	                responseOut.flush();
   139	                JSONObject jout = new JSONObject(responseOut.toString());
   140	                verify(mockResponse).setStatus(404);
   141	                assertFalse(jout.getBoolean("success"));
   142	            }
   143	        }
   144	    }
   145	
   146	    @Test void apiGetSuccess()
   147	    throws ServletException, IOException {
   148	        User user = mock(User.class);
   149	
   150	        // this doesnt really test the output value of "matchResultsRoot", since we
   151	        // test task.matchResultsJson() seperately so dont care about the null value here
   152	        Task task = mock(Task.class);
   153	        String id = "ok-id";
   154	
   155	        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
   156	        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
   157	
   158	        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
   159	                (mock, context) -> {
   160	            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
   161	            when(mock.getTask(any(String.class))).thenReturn(task);
   162	        })) {
   163	            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
   164	                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
   165	                apiServlet.doGet(mockRequest, mockResponse);
   166	                responseOut.flush();
   167	                JSONObject jout = new JSONObject(responseOut.toString());
   168	                verify(mockResponse).setStatus(200);
   169	                assertTrue(jout.getBoolean("success"));
   170	            }
   171	        }
   172	    }
   173	}

 succeeded in 1161ms:
src/test/java/org/ecocean/api/TaskMatchResults.java:48:class TaskMatchResults {
src/test/java/org/ecocean/api/TaskMatchResults.java:96:    // basically tests api path without /match-results
src/test/java/org/ecocean/api/TaskMatchResults.java:127:        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
src/test/java/org/ecocean/api/TaskMatchResults.java:150:        // this doesnt really test the output value of "matchResultsRoot", since we
src/test/java/org/ecocean/api/TaskMatchResults.java:151:        // test task.matchResultsJson() seperately so dont care about the null value here
src/test/java/org/ecocean/api/TaskMatchResults.java:155:        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
frontend/src/AuthenticatedSwitch.jsx:86:            <Route path="/match-results" element={<MatchResults />} />
src/test/java/org/ecocean/servlet/ReactAppServletTest.java:30:        assertEquals("/react/match-results",
src/test/java/org/ecocean/servlet/ReactAppServletTest.java:31:            ReactAppServlet.stripContextPath(req, "/react/match-results"));
src/test/java/org/ecocean/servlet/ReactAppServletTest.java:36:        assertEquals("/react/match-results",
src/test/java/org/ecocean/servlet/ReactAppServletTest.java:38:                "/wildbook/react/match-results"));
src/test/java/org/ecocean/servlet/ReactAppServletTest.java:59:        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react/match-results"));
frontend/src/pages/BulkImport/BulkImportTask.jsx:216:          const link = `/react/match-results?taskId=${arr[0]}`;
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:1:import MatchResultsStore from "../../../pages/MatchResultsPage/stores/matchResultsStore";
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:17:  matchResults: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:39:  matchResultsRoot: makeProspect(nodeOverrides),
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:163:      matchResultsRoot: makeProspect({
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:165:        matchResults: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:181:  test("clears state when matchResultsRoot is null", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:182:    store.loadData({ matchResultsRoot: null });
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:573:        matchResults: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:574:          ...makeProspect().matchResults,
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:576:            ...makeProspect().matchResults.queryAnnotation,
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:648:      expect.stringContaining("/api/v3/tasks/task-abc/match-results"),
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:160:  matchResultsRoot: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:167:    matchResults: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:188:const renderComponent = (url = "/match-results?taskId=task-1") =>
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:220:    renderComponent("/match-results");
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:372:        matchResultsRoot: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:375:          matchResults: {
frontend/src/pages/Encounter/ImageCard.jsx:707:                  const url = `/react/match-results?taskId=${encodeURIComponent(taskId)}`;
frontend/src/pages/Encounter/ImageCard.jsx:748:                    const url = `/react/match-results?taskId=${encodeURIComponent(selectedAnnotation.iaTaskId)}`;
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:14:    matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:46:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:47:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:60:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:61:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:117:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:118:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:131:    test("returns common object when no matchResults but task is running", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:132:      const node = makeNode({ matchResults: null });
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:142:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:143:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:172:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:173:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:199:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:200:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:210:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:211:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:228:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:229:          queryAnnotation: makeNode().matchResults.queryAnnotation,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:238:        matchResults: {
frontend/src/components/ImageModal.jsx:1044:                        `/react/match-results?taskId=${taskId}`,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:86:    const root = results?.matchResultsRoot;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:96:    const rootHasMatchResults = !!root?.matchResults;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:425:          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:447:          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:457:        const root = result?.data?.matchResultsRoot;
frontend/src/pages/MatchResultsPage/MatchResults.jsx:6:import MatchResultsStore from "./stores/matchResultsStore";
frontend/src/pages/MatchResultsPage/MatchResults.jsx:97:    return <FullScreenLoader data-testid="match-results-loader" />;
frontend/src/pages/MatchResultsPage/MatchResults.jsx:105:      id="match-results-page"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:106:      data-testid="match-results-page"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:122:        <div data-testid="match-results-bottom-bar">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:134:          data-testid="match-results-bottom-bar-spacer"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:140:        id="match-results-header"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:141:        data-testid="match-results-header"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:144:          <h2 id="match-results-title" data-testid="match-results-title">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:153:            id="match-results-instructions-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:154:            data-testid="match-results-instructions-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:158:              data-testid="match-results-instructions-icon"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:166:        id="match-results-toolbar"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:167:        data-testid="match-results-toolbar"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:171:          id="match-results-viewmode"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:172:          data-testid="match-results-viewmode"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:177:            id="match-results-viewmode-individual"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:178:            data-testid="match-results-viewmode-individual"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:202:            id="match-results-viewmode-image"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:203:            data-testid="match-results-viewmode-image"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:228:          id="match-results-controls"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:229:          data-testid="match-results-controls"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:233:            id="match-results-num-results-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:234:            data-testid="match-results-num-results-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:241:                data-testid="match-results-num-results-hint"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:252:              id="match-results-num-results-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:253:              data-testid="match-results-num-results-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:256:                id="match-results-num-results-input"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:257:                data-testid="match-results-num-results-input"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:282:                  id="match-results-num-results-apply"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:283:                  data-testid="match-results-num-results-apply"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:311:            id="match-results-project-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:312:            data-testid="match-results-project-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:320:              id="match-results-project-select-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:321:              data-testid="match-results-project-select-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:355:            id="match-results-filter-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:356:            data-testid="match-results-filter-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:365:            <FilterIcon data-testid="match-results-filter-icon" />
frontend/src/pages/MatchResultsPage/MatchResults.jsx:370:      <div id="match-results-content" data-testid="match-results-content">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:374:            id="match-results-empty"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:375:            data-testid="match-results-empty"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:383:          <div id="match-results-sections" data-testid="match-results-sections">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:388:                  id={`match-results-section-${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:389:                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
frontend/src/pages/Encounter/MatchCriteria.jsx:133:                  const url = `/react/match-results?taskId=${result?.data?.taskId}`;
frontend/src/pages/MatchResultsPage/helperFunctions.js:9:  const prospects = node.matchResults?.prospects?.[type];
frontend/src/pages/MatchResultsPage/helperFunctions.js:15:  const numberCandidatesRaw = node.matchResults?.numberCandidates;
frontend/src/pages/MatchResultsPage/helperFunctions.js:37:        node.matchResults?.queryAnnotation?.encounter?.id ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:39:        node.matchResults?.queryAnnotation?.encounter?.locationId ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:42:        node.matchResults?.queryAnnotation?.individual?.id ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:44:        node.matchResults?.queryAnnotation?.individual?.displayName ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:46:        node.matchResults?.queryAnnotation?.asset ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:48:        node.matchResults?.queryAnnotation?.asset?.url ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:49:      queryEncounterAnnotation: node.matchResults?.queryAnnotation
frontend/src/pages/MatchResultsPage/helperFunctions.js:51:            id: node.matchResults.queryAnnotation?.id,
frontend/src/pages/MatchResultsPage/helperFunctions.js:52:            x: node.matchResults.queryAnnotation?.x,
frontend/src/pages/MatchResultsPage/helperFunctions.js:53:            y: node.matchResults.queryAnnotation?.y,
frontend/src/pages/MatchResultsPage/helperFunctions.js:54:            width: node.matchResults.queryAnnotation?.width,
frontend/src/pages/MatchResultsPage/helperFunctions.js:55:            height: node.matchResults.queryAnnotation?.height,
frontend/src/pages/MatchResultsPage/helperFunctions.js:56:            theta: node.matchResults.queryAnnotation?.theta,
frontend/src/pages/MatchResultsPage/helperFunctions.js:57:            boundingBox: node.matchResults.queryAnnotation?.boundingBox,
frontend/src/pages/MatchResultsPage/helperFunctions.js:58:            isTrivial: node.matchResults.queryAnnotation?.isTrivial,
frontend/src/pages/MatchResultsPage/helperFunctions.js:59:            trivial: node.matchResults.queryAnnotation?.trivial,
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:441:          id="match-results-bottom-bar"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:442:          data-testid="match-results-bottom-bar"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:447:            id="match-results-bottom-bar-inner"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:448:            data-testid="match-results-bottom-bar-inner"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:457:              id="match-results-bottom-bar-left"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:458:              data-testid="match-results-bottom-bar-left"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:466:              id="match-results-bottom-bar-right"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:467:              data-testid="match-results-bottom-bar-right"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:478:                id="match-results-bottom-bar-cancel"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:479:                data-testid="match-results-bottom-bar-cancel"
frontend/src/components/home/PickUpWhereYouLeft.jsx:16:      ? `/react/match-results?taskId=${data?.latestMatchTask?.id}`
frontend/src/__tests__/pages/Encounter/ImageModal.test.js:228:      "/react/match-results?taskId=task-123",
frontend/src/__tests__/pages/Encounter/ImageCard.test.js:305:    expect(url).toContain("/react/match-results?taskId=TASK-99");
frontend/src/__tests__/pages/Encounter/ImageCard.test.js:374:    expect(url).toContain("/react/match-results?taskId=TASK-FR-1");
frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js:215:        "/react/match-results?taskId=t123",
frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js:79:    expect(latestMatchItem).toHaveTextContent("/react/match-results?taskId=123");

 succeeded in 1150ms:
src/test/java/org/ecocean/api/TaskMatchResults.java:151:        // test task.matchResultsJson() seperately so dont care about the null value here
src/main/java/org/ecocean/api/GenericObject.java:124:                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
src/main/java/org/ecocean/ia/Task.java:744:    public JSONObject getIdentificationMethodInfo() {
src/main/java/org/ecocean/ia/Task.java:820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:837:        JSONObject methodInfo = getIdentificationMethodInfo();
src/main/java/org/ecocean/ia/Task.java:860:                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/Task.java:877:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);

codex
The code paths that create `MatchResult` only attach it to matching tasks (`Embedding.findMatchProspects`, WBIA callback/log generation). That makes the broadened serialization look aligned with the data model: the presence of an MR is already the meaningful signal, while `methodInfo` is just metadata.
exec
/bin/bash -lc 'rg -n "C15|matchResultsJson|methodInfo|matchResults field|empty results" docs/plans/2026-05-18-empty-match-prospects-design.md docs/plans/2026-05-19-empty-results-rca.md docs/plans/2026-05-09-ml-service-migration-v2.md' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/helperFunctions.js | sed -n '1,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js | sed -n '70,120p'" in /mnt/c/Wildbook-clean2
 succeeded in 264ms:
     1	const collectProspects = (node, type, result = []) => {
     2	  if (!node) return result;
     3	
     4	  const hasMethod = !!node.method;
     5	  const taskCreated = !!node.statusOverall || hasMethod || !!node.dateCreated;
     6	  const methodName = node.method?.name ?? node.method?.description;
     7	  const methodDescription = node.method?.description ?? null;
     8	
     9	  const prospects = node.matchResults?.prospects?.[type];
    10	
    11	  const safeProspects = Array.isArray(prospects)
    12	    ? prospects.filter((p) => p && typeof p === "object")
    13	    : [];
    14	
    15	  const numberCandidatesRaw = node.matchResults?.numberCandidates;
    16	  const numberCandidates =
    17	    typeof numberCandidatesRaw === "number" ? numberCandidatesRaw : "-";
    18	
    19	  let emptyStateType = null;
    20	
    21	  if (numberCandidates === 0) {
    22	    emptyStateType = "no_candidates";
    23	  } else if (numberCandidates > 0 && safeProspects.length === 0) {
    24	    emptyStateType = "no_prospects";
    25	  }
    26	
    27	  const taskStatusOverall = node.statusOverall ?? null;
    28	  const nodeIsTerminal = isTerminalStatus(taskStatusOverall);
    29	  const nodeIsStillRunning = !!taskStatusOverall && !nodeIsTerminal;
    30	
    31	  if (taskCreated) {
    32	    const common = {
    33	      algorithm: methodName,
    34	      date: node.dateCreated,
    35	      numberCandidates: numberCandidatesRaw ?? "-",
    36	      queryEncounterId:
    37	        node.matchResults?.queryAnnotation?.encounter?.id ?? null,
    38	      encounterLocationId:
    39	        node.matchResults?.queryAnnotation?.encounter?.locationId ?? null,
    40	      matchingSetFilter: node.matchingSetFilter,
    41	      queryIndividualId:
    42	        node.matchResults?.queryAnnotation?.individual?.id ?? null,
    43	      queryIndividualDisplayName:
    44	        node.matchResults?.queryAnnotation?.individual?.displayName ?? null,
    45	      queryEncounterImageAsset:
    46	        node.matchResults?.queryAnnotation?.asset ?? null,
    47	      queryEncounterImageUrl:
    48	        node.matchResults?.queryAnnotation?.asset?.url ?? null,
    49	      queryEncounterAnnotation: node.matchResults?.queryAnnotation
    50	        ? {
    51	            id: node.matchResults.queryAnnotation?.id,
    52	            x: node.matchResults.queryAnnotation?.x,
    53	            y: node.matchResults.queryAnnotation?.y,
    54	            width: node.matchResults.queryAnnotation?.width,
    55	            height: node.matchResults.queryAnnotation?.height,
    56	            theta: node.matchResults.queryAnnotation?.theta,
    57	            boundingBox: node.matchResults.queryAnnotation?.boundingBox,
    58	            isTrivial: node.matchResults.queryAnnotation?.isTrivial,
    59	            trivial: node.matchResults.queryAnnotation?.trivial,
    60	          }
    61	        : null,
    62	      methodName,
    63	      methodDescription,
    64	      method: node.method ?? null,
    65	      taskId: node.id ?? null,
    66	      taskStatus: node.status ?? null,
    67	      taskStatusOverall,
    68	      hasResults: safeProspects.length > 0,
    69	      emptyStateType,
    70	      errors: node.statusDetails?.errors ?? null,
    71	    };
    72	
    73	    if (safeProspects.length > 0) {
    74	      result.push(
    75	        ...safeProspects.map((item) => ({
    76	          ...item,
    77	          ...common,
    78	        })),
    79	      );
    80	    } else if (nodeIsStillRunning || nodeIsTerminal) {
    81	      result.push(common);
    82	    }
    83	  }
    84	
    85	  if (Array.isArray(node.children)) {
    86	    node.children.forEach((child) => collectProspects(child, type, result));
    87	  }
    88	
    89	  return result;
    90	};

 succeeded in 263ms:
    70	    if (withQueryAnnotationEncounter) return withQueryAnnotationEncounter;
    71	
    72	    const withSomeQueryContext = candidates.find(
    73	      (item) =>
    74	        item?.queryEncounterImageAsset ||
    75	        item?.queryEncounterImageUrl ||
    76	        item?.queryEncounterAnnotation ||
    77	        item?.matchingSetFilter ||
    78	        item?.queryIndividualId,
    79	    );
    80	    if (withSomeQueryContext) return withSomeQueryContext;
    81	
    82	    return candidates[0];
    83	  }
    84	
    85	  loadData(results, { preserveSelection = false } = {}) {
    86	    const root = results?.matchResultsRoot;
    87	
    88	    this._annotResults = getAllAnnot(root);
    89	    this._indivResults = getAllIndiv(root);
    90	    this._taskStillRunning = isMatchTaskStillRunning(root);
    91	    this._taskHasError = hasMatchTaskError(root);
    92	    this._taskIsTerminal = isMatchTaskTerminal(root);
    93	
    94	    const rootHasChildren =
    95	      Array.isArray(root?.children) && root.children.length > 0;
    96	    const rootHasMatchResults = !!root?.matchResults;
    97	
    98	    this._rootStillRunning =
    99	      !rootHasChildren &&
   100	      !rootHasMatchResults &&
   101	      !!root?.statusOverall &&
   102	      root.statusOverall !== "completed" &&
   103	      root.statusOverall !== "error";
   104	
   105	    this._rootHasError =
   106	      !rootHasChildren &&
   107	      !rootHasMatchResults &&
   108	      root?.statusOverall === "error";
   109	
   110	    const hasAnyResults =
   111	      (Array.isArray(this._annotResults) && this._annotResults.length > 0) ||
   112	      (Array.isArray(this._indivResults) && this._indivResults.length > 0);
   113	
   114	    if (!hasAnyResults) {
   115	      this._rawAnnots = [];
   116	      this._rawIndivs = [];
   117	      this._processedAnnots = [];
   118	      this._processedIndivs = [];
   119	      this._encounterId = null;
   120	      this._matchingSetFilter = {};

 exited 2 in 333ms:
rg: docs/plans/2026-05-19-empty-results-rca.md: No such file or directory (os error 2)
docs/plans/2026-05-09-ml-service-migration-v2.md:366:empty results before flipping a species to ml-service" because all empty

exec
/bin/bash -lc "rg -n \"setParameters|getParameters|addObjectAnnotation|setObjectAnnotations|children|addChild|setParent|setStatus\\(\" src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,180p'" in /mnt/c/Wildbook-clean2
 succeeded in 206ms:
37:    private List<Task> children = null;
56:        this.setParameters(p.getParameters());
57:        this.setParent(p);
132:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
133:        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
139:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
145:        return this.getParameters().optBoolean("ibeis.detection", false);
148:        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
180:    public void setObjectAnnotations(List<Annotation> anns) {
248:        return children;
253:            children = null;
256:        children = new ArrayList<Task>();
258:            this.addChild(kid); // let this do the work
262:    public List<Task> addChild(Task kid) {
263:        if (children == null) children = new ArrayList<Task>();
264:        if (kid == null) return children;
265:        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
266:        if (!children.contains(kid)) children.add(kid);
267:        return children;
270:    public void setParent(Task t) {
272:        t.addChild(this);
285:        return (children == null) ? 0 : children.size();
300:        for (Task kid : children) {
314:        for (Task kid : children) {
324:        for (Task kid : children) {
336:            for (Task kid : this.children) {
347:            for (Task kid : this.children) {
419:    public JSONObject getParameters() { // only return as JSONObject!
424:    public void setParameters(String s) { // best be json, yo
428:    public void setParameters(JSONObject j) {
437:    public void setParameters(String key, Object value) {
447:        JSONObject j = this.getParameters();
461:        j.put("parameters", this.getParameters());
482:            for (Task kid : this.children) {
485:            j.put("children", jc);
511:                   .append("params=" + ((this.getParameters() ==
512:                null) ? "(none)" : this.getParameters().toString()))
588:            List<Task> children = getChildren();
590:            for (Task t : children) {
663:            // accumulate status across children
707:        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
713:    public void setStatus(String newStatus) {
740:        if (getParameters() == null) return null;
741:        return getParameters().optJSONObject("matchingSetFilter");
745:        if (getParameters() == null) return null;
746:        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
749:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
751:            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
763:        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
766:            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
771:            getParameters().getJSONObject("ibeis.identification").optString("description",
872:        // now we recurse thru children if applicable
875:            for (Task child : children) {
883:            rtn.put("children", charr);
884:            // if we dont have children (leaf nodes) we get the status

 succeeded in 210ms:
     1	/*
     2	    an instance of an ia.Task can be persisted and represents the state of that task
     3	    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
     4	 */
     5	package org.ecocean.ia;
     6	
     7	import java.util.ArrayList;
     8	import java.util.Collection;
     9	import java.util.Collections;
    10	import java.util.HashMap;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	import javax.jdo.Query;
    15	import org.apache.commons.lang3.builder.ToStringBuilder;
    16	import org.ecocean.Annotation;
    17	import org.ecocean.Encounter;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.shepherd.core.Shepherd;
    20	import org.ecocean.User;
    21	import org.ecocean.Util;
    22	import org.joda.time.DateTime;
    23	import org.json.JSONArray;
    24	import org.json.JSONObject;
    25	
    26	import org.ecocean.identity.IdentityServiceLog;
    27	
    28	public class Task implements java.io.Serializable {
    29	    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    30	    private String id = null;
    31	    private long created = -1;
    32	    private long modified = -1;
    33	    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    34	    private List<MediaAsset> objectMediaAssets = null;
    35	    private List<Annotation> objectAnnotations = null;
    36	    private Task parent = null;
    37	    private List<Task> children = null;
    38	    private String parameters = null;
    39	    private String status;
    40	    // general use, but notably will contain error details when status=error
    41	    private String statusDetails = null;
    42	    private Long completionDateInMilliseconds;
    43	    private String queueResumeMessage;
    44	
    45	    public Task() {
    46	        this(Util.generateUUID());
    47	    }
    48	    public Task(String id) {
    49	        this.id = id;
    50	        created = System.currentTimeMillis();
    51	        modified = System.currentTimeMillis();
    52	    }
    53	    // makes a child of the passed Task (and inherits the parameters!!)
    54	    public Task(Task p) {
    55	        this();
    56	        this.setParameters(p.getParameters());
    57	        this.setParent(p);
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public long getCreatedLong() {
    65	        return created;
    66	    }
    67	
    68	    public long getModifiedLong() {
    69	        return modified;
    70	    }
    71	
    72	    public long timeInactive() {
    73	        long now = System.currentTimeMillis();
    74	
    75	        if (modified > 0) return (now - modified);
    76	        if (created > 0) return (now - created);
    77	        // weird or inconclusive:
    78	        return -1l;
    79	    }
    80	
    81	    public boolean timedOutDueToInactivity() {
    82	        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    83	    }
    84	
    85	    public boolean statusInEndState() {
    86	        if ("completed".equals(status)) return true;
    87	        if ("error".equals(status)) return true;
    88	        // ml-service migration v2: "dropped-stale" is terminal — the task's
    89	        // target was deleted before the queued job ran. Neither success nor
    90	        // error; the inactivity-timeout watchdog must not flip it to error.
    91	        if ("dropped-stale".equals(status)) return true;
    92	        return false;
    93	    }
    94	
    95	    public void setModified() {
    96	        modified = System.currentTimeMillis();
    97	    }
    98	
    99	    public boolean canUserAccess(User user, Shepherd myShepherd) {
   100	        if (user == null) return false;
   101	        if (user.isAdmin(myShepherd)) return true;
   102	        Encounter enc = null;
   103	        // if we have annotations, use first to determine encounter
   104	        if (this.countObjectAnnotations() > 0) {
   105	            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
   106	        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
   107	            MediaAsset ma = this.getObjectMediaAssets().get(0);
   108	            // we iterate over all annots on this asset til we find an encounter.
   109	            // it might be better to find *all* encounters and return access based on each;
   110	            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
   111	            // so i think this means asset-based access of tasks will be rare or unused anyway
   112	            for (Annotation ann : ma.getAnnotations()) {
   113	                if (ann != null) enc = ann.findEncounter(myShepherd);
   114	                if (enc != null) break;
   115	            }
   116	        }
   117	        if (enc == null) return false;
   118	        if (enc.isPubliclyReadable()) return true;
   119	        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
   120	        return enc.canUserAccess(user, myShepherd.getContext());
   121	    }
   122	
   123	/*
   124	    // not really convinced these are accurate enough to use
   125	    //   actual computation of these things is complicated
   126	    //   leaving these for future potential exploration, if needed.
   127	
   128	    public boolean isTypeDetection() {
   129	        if (this.hasObjectMediaAssets()) return true;
   130	        if (this.hasObjectAnnotations()) return false;
   131	        if (this.parameters == null) return false;
   132	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
   133	        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
   134	        return false;
   135	    }
   136	    public boolean isTypeIdentification() {
   137	        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
   138	        if (this.parameters == null) return false;
   139	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
   140	        return false;
   141	    }
   142	
   143	    public boolean initiatedWithDetection() {
   144	        if (this.parameters == null) return false;
   145	        return this.getParameters().optBoolean("ibeis.detection", false);
   146	    }
   147	    public boolean initiatedWithIdentification() {
   148	        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
   149	    }
   150	 */
   151	    public int countObjectMediaAssets() {
   152	        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
   153	    }
   154	
   155	    public int countObjectAnnotations() {
   156	        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
   157	    }
   158	
   159	    public int countObjects() {
   160	        return countObjectMediaAssets() + countObjectAnnotations();
   161	    }
   162	
   163	    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
   164	    public boolean hasObjectMediaAssets() {
   165	        return (countObjectMediaAssets() > 0);
   166	    }
   167	
   168	    public boolean hasObjectAnnotations() {
   169	        return (countObjectAnnotations() > 0);
   170	    }
   171	
   172	    public boolean hasObjects() {
   173	        return (countObjects() > 0);
   174	    }
   175	
   176	    public void setObjectMediaAssets(List<MediaAsset> mas) {
   177	        objectMediaAssets = mas;
   178	    }
   179	
   180	    public void setObjectAnnotations(List<Annotation> anns) {

exec
/bin/bash -lc "rg -n \"class Task.*Test|class TaskGetStoredStatus|class TaskStatusInEndState|class TaskMatchResults\" src/test/java/org/ecocean -g'*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "surefire|includes|excludes|maven-surefire|failsafe" pom.xml **/pom.xml' in /mnt/c/Wildbook-clean2
 exited 2 in 290ms:
rg: **/pom.xml: No such file or directory (os error 2)
pom.xml:660:        <artifactId>maven-surefire-plugin</artifactId>
pom.xml:671:          <includes>
pom.xml:674:          </includes>
pom.xml:843:          <includes>
pom.xml:845:          </includes>

 succeeded in 446ms:
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:13:class TaskStatusInEndStateTest {
src/test/java/org/ecocean/api/TaskMatchResults.java:48:class TaskMatchResults {
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:15:class TaskGetStoredStatusTest {

exec
/bin/bash -lc "nl -ba pom.xml | sed -n '650,680p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba pom.xml | sed -n '835,850p'" in /mnt/c/Wildbook-clean2
 succeeded in 211ms:
   835	        <groupId>org.lesscss</groupId>
   836	        <artifactId>lesscss-maven-plugin</artifactId>
   837	        <version>1.7.0.1.1</version>
   838	        <configuration>
   839	          <sourceDirectory>${project.basedir}/src/main/webapp/cust/mantamatcher/styles</sourceDirectory>
   840	          <outputDirectory>
   841	            ${project.build.directory}/${project.build.finalName}/cust/mantamatcher/css</outputDirectory>
   842	          <compress>true</compress>
   843	          <includes>
   844	            <include>manta.less</include>
   845	          </includes>
   846	        </configuration>
   847	        <executions>
   848	          <execution>
   849	            <goals>
   850	              <goal>compile</goal>

 succeeded in 211ms:
   650	        <configuration>
   651	          <source>17</source>      <!-- Use Java 17 language features -->
   652	          <target>11</target>      <!-- Generate Java 11 bytecode - datanucleus 5.0 enhancer limitation -->
   653	          <release>11</release>    <!-- Ensure Java 11 compatibility -->
   654	          <encoding>UTF-8</encoding>
   655	        </configuration>
   656	      </plugin>
   657	
   658	      <plugin>
   659	        <groupId>org.apache.maven.plugins</groupId>
   660	        <artifactId>maven-surefire-plugin</artifactId>
   661	        <version>3.2.5</version>
   662	        <configuration>
   663	          <!-- Java 17 module system compatibility -->
   664	          <argLine>
   665	            --add-opens java.base/java.lang=ALL-UNNAMED
   666	            --add-opens java.base/java.util=ALL-UNNAMED
   667	            --add-opens java.base/java.time=ALL-UNNAMED
   668	            --add-opens java.base/java.io=ALL-UNNAMED
   669	          </argLine>
   670	          <!-- Optional: Configure test execution -->
   671	          <includes>
   672	            <include>**/*Test.java</include>
   673	            <include>**/*Tests.java</include>
   674	          </includes>
   675	          <forkCount>1</forkCount>
   676	          <reuseForks>true</reuseForks>
   677	        </configuration>
   678	      </plugin>
   679	
   680	      <plugin>

codex
I don’t see a behavior mismatch in the Java flow. One coverage detail did stand out: the existing `TaskMatchResults` test class is not matched by the Surefire includes because it lacks a `*Test.java` suffix, and it also does not assert the returned `matchResultsRoot` shape.
exec
/bin/bash -lc 'git diff --numstat -- src/main/java/org/ecocean/ia/Task.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/ia/Task.java' in /mnt/c/Wildbook-clean2
 succeeded in 396ms:
29	23	src/main/java/org/ecocean/ia/Task.java

 succeeded in 418ms:
codex
**Findings**

No blocking correctness findings in [Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:837). For `methodInfo != null`, the flow is behaviorally equivalent: legacy extras are still emitted, existing MR is checked first, lazy WBIA generation still only runs for `mr == null && !hasChildren()`, and serialization still happens afterward.

The v2 path also lines up: `Embedding.findMatchProspects` persists `MatchResult` on the subtask at [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:377), and `Shepherd.getMatchResults` returns task MRs ordered newest first at [Shepherd.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820). So using MR existence as the serialization signal is consistent with the data model.

**Test Gap**

Low: there is no backend regression test for the exact failure mode. The existing [TaskMatchResults.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/api/TaskMatchResults.java:48) does not assert the `matchResultsRoot` shape, and it is not picked up by the current Surefire includes in [pom.xml](/mnt/c/Wildbook-clean2/pom.xml:671) because the file/class name lacks a `*Test` suffix.

I’d add or rename a test that covers: task has no `ibeis.identification`, `Shepherd.getMatchResults(task)` returns an existing MR, and `matchResultsJson()` includes `matchResults` without emitting `method`.

**Checks**

`Task.java` is still LF-normalized (`i/lf w/lf`), and `git diff --check -- src/main/java/org/ecocean/ia/Task.java` passed. I did not run compile/tests to respect the review-only/no-file-writes constraint.
tokens used
48,033
**Findings**

No blocking correctness findings in [Task.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:837). For `methodInfo != null`, the flow is behaviorally equivalent: legacy extras are still emitted, existing MR is checked first, lazy WBIA generation still only runs for `mr == null && !hasChildren()`, and serialization still happens afterward.

The v2 path also lines up: `Embedding.findMatchProspects` persists `MatchResult` on the subtask at [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:377), and `Shepherd.getMatchResults` returns task MRs ordered newest first at [Shepherd.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820). So using MR existence as the serialization signal is consistent with the data model.

**Test Gap**

Low: there is no backend regression test for the exact failure mode. The existing [TaskMatchResults.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/api/TaskMatchResults.java:48) does not assert the `matchResultsRoot` shape, and it is not picked up by the current Surefire includes in [pom.xml](/mnt/c/Wildbook-clean2/pom.xml:671) because the file/class name lacks a `*Test` suffix.

I’d add or rename a test that covers: task has no `ibeis.identification`, `Shepherd.getMatchResults(task)` returns an existing MR, and `matchResultsJson()` includes `matchResults` without emitting `method`.

**Checks**

`Task.java` is still LF-normalized (`i/lf w/lf`), and `git diff --check -- src/main/java/org/ecocean/ia/Task.java` passed. I did not run compile/tests to respect the review-only/no-file-writes constraint.
