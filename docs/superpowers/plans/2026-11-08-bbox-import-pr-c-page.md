# Bulk-import boxes PR C: React Bulk Import page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** the React Bulk Import page accepts a spreadsheet with box columns, keeps every photo in its numbered slot end to end, checks the box columns client side, and warns about un-boxed photos.

**Architecture:** one small pure helper module decides "is this a boxed sheet" and "is this a photo-slot / box column". The store and the three components ask it instead of hard-coding `Encounter.mediaAsset0`. Un-boxed sheets keep today's merged-cell behaviour byte for byte.

**Tech Stack:** React 18, MobX, react-intl, Jest + Testing Library (`frontend/`, `npm test -- --watchAll=false <path>`).

**Spec:** `docs/superpowers/specs/2026-11-06-bulk-import-annotation-bboxes-design.md` §4.6 (and §4.1 exact-slot mode). Stacked on `feat/bbox-import-d-skill`; milestone 11.1.

## Global Constraints

- Exact-slot mode = the site advertises `Encounter.mediaAsset#.bbox` in `siteSettings.bulkImportIndexableFields` (capability signal) AND the sheet has a column matching `^Encounter\.mediaAsset\d+\.bbox$`. On a site without the capability, box columns stay ordinary unknown columns (today's warning) and the sheet is handled as un-boxed.
- Column presence alone only preserves slots. "No detection" applies only when at least one bbox cell is non-empty (§4.1; server `BulkImport.java:764` `anyBox`). The un-boxed-photo warning and the instructions say so.
- Box suffixes: `bbox|iaClass|viewpoint|theta|bboxSource` — one list, in `bulkImportSlots.js`; every regex is built from it.
- In exact-slot mode every `Encounter.mediaAssetN` and every `Encounter.mediaAssetN.*` column (box suffixes, `.keywords`, labeled keywords) is read-only, rendered as `<input readOnly>` so the existing `.is-invalid` / `.invalid-feedback` error display keeps working.
- Client rules never block a value the server accepts; when unsure, defer to the server.
  - bbox mirrors `BulkValidator.parseBbox`: split on `,` keeping empties; exactly four parts; each trimmed part matches `^[+-]?\d+$` and fits a Java int (−2147483648…2147483647); `+1` and `-0` accepted; x ≥ 0, y ≥ 0, width ≥ 1, height ≥ 1.
  - theta: error only when `Number(trimmed)` is a number that is non-finite or outside ±6.2832 (THETA_LIMIT), or the text has no digit at all. Java-only forms (`1D`, `0x1.0p0`) that `Number` cannot parse are left to the server.
  - viewpoint: trimmed value in `siteSettings.annotationViewpoint`.
- iaClass: in `siteSettings.bulkImportMatchableClasses[<row's "Genus species">]`; skipped when `skipDetection` or `skipIdentification` is on.
- Empty box cells are always valid client side (server owns slot/cross-row/bounds/duplicate rules).
- Un-boxed sheets: no behaviour change (merged `Encounter.mediaAsset0` cell, renumbering at submit).
- Locale keys go into all of en/de/es/fr/it (`frontend/src/locale/*.json`), translated (the existing files are translated, not English copies).
- All edited files LF-only.

## Decision (deviation from spec, my call)

The iaClass rule is a **warning**, not an error. The skip checkboxes live on the last step (`BulkImportSetLocation.jsx`), after the table step, so a blocking error would stop a user who intends to tick "skip". The server rejects a non-matchable class on Import anyway (400 → failure modal), so nothing wrong is created.

## Review Focus

1. A boxed sheet whose photo columns have gaps (`mediaAsset0`, `mediaAsset2`) — slots must reach the server unchanged, not renumbered to 0,1.
2. A boxed draft saved before this change (photo slots merged into slot 0, other slots dropped from `columnsDef`; edits may have written the merged value back into `rawData`) — slot identity cannot be recovered, so the resume modal says the spreadsheet must be uploaded again and resuming keeps the uploaded images but drops the saved spreadsheet. Detection is by draft shape only (`isLegacyBoxedDraft(saved)`), so it needs no site settings. Tested with an untouched and an edited legacy fixture.
3. A site with a labeled keyword whose name equals a box suffix (e.g. `viewpoint`) — box columns must use the box rules, not the labeled-keyword rule.
4. A boxed sheet where a photo name is missing from the uploaded images — the missing-photo count and the per-cell error must cover every slot, not just slot 0.
5. Species cell empty or not in `bulkImportMatchableClasses` — the iaClass warning must not throw.

---

### Task 1: Slot helpers (done in commit 1 — reuse, do not re-create)

**Files:** `frontend/src/pages/BulkImport/bulkImportSlots.js`; Test `frontend/src/__tests__/pages/BulkImport/bulkImportSlots.test.js`

**Produces:**
- `BOX_SUFFIXES: string[]` (the single source)
- `isPhotoSlotColumn(col): boolean` — `^Encounter\.mediaAsset\d+$`
- `isSlotColumn(col): boolean` — bare slot or any `Encounter.mediaAssetN.<anything>` (the read-only set)
- `boxSuffixOf(col): string|null` — suffix when it is one of `BOX_SUFFIXES`
- `siteAcceptsBoxes(indexableFields: string[]|undefined): boolean` — includes `Encounter.mediaAsset#.bbox`
- `hasBoxColumns(cols, indexableFields): boolean` — capability AND a `.bbox` column
- `isValidBbox(val): boolean`, `isValidTheta(val): boolean` (blank → true; a value containing any non-ASCII character is left to the server → true)
- `javaTrim(val): string` — strips leading/trailing chars ≤ U+0020 like Java `String.trim()`; used for every box-field blank check and value check (bbox parts, theta, viewpoint, iaClass)
- `isLegacyBoxedDraft(saved): boolean` — `saved.columnsDef` has a `.bbox` column and `saved.rawColumns` has a bare photo slot missing from `saved.columnsDef`

- [ ] Tests: `isValidBbox` true for `"10, 20,30,40"`, `"+1,-0,5,5"`, `"0,0,2147483647,1"`, `"０,０,５,５"` (full-width digits, accepted by Java `Integer.parseInt`), `"\u001c0,0,5,5"`; `isValidTheta("\u001c")` true (blank after Java trim); false for `"1,2,3"`, `"1,2,3,4,5"`, `"1,,3,4"`, `"-1,0,5,5"`, `"0,0,0,5"`, `"1.5,0,5,5"`, `"0,0,2147483648,1"`. `isValidTheta` true for `"6.2832"`, `"-6.2832"`, `"1e-3"`, `"1D"`, `"0x1.0p0"`; false for `"6.3"`, `"Infinity"`, `"abc"`. `hasBoxColumns(["Encounter.mediaAsset3.bbox"], ["Encounter.mediaAsset#.bbox"])` true, same with `[]` false, `["Encounter.mediaAsset0.keywords"]` false. `isLegacyBoxedDraft` true for `{columnsDef:["Encounter.mediaAsset0","Encounter.mediaAsset0.bbox"], rawColumns:["Encounter.mediaAsset0","Encounter.mediaAsset2","Encounter.mediaAsset0.bbox"]}`, false when `rawColumns` adds no slot, false without a `.bbox` column. `isSlotColumn` true for `Encounter.mediaAsset2.keywords` and `Encounter.mediaAsset2.quality`, false for `Encounter.keyword0`.
- [ ] Reuse: replace the inline bare-slot checks at `BulkImportSpreadsheet.jsx:81`, `BulkImportStore.js:553,1304` and the suffix regexes at `BulkImportStore.js:1451,1514` with these helpers.
- [ ] Implement; run; commit.

### Task 2: Store — slots, rules, submit

**Files:** Modify `BulkImportStore.js` (rules ~98, `missingPhotos` ~493, `validateMediaAsset0ColumnOnly` ~788, `updateRawFromNormalizedRow` ~1306, `isDynamicKnownColumn` ~1449, `applyDynamicValidationRules` ~1478, `validateSpreadsheet` ~1605); setters for `validViewpoints`, `matchableClasses`. Test: `__tests__/pages/BulkImport/BulkImportStore.test.js`.

**Produces:** getters `isBoxedSheet` (= `hasBoxColumns(columnsDef, indexableFields)`), `photoColumns` (columnsDef filtered by `isPhotoSlotColumn`), `unboxedPhotoCount` (0 unless at least one bbox cell in the sheet is non-empty; then the count of non-empty photo slots whose same-slot `.bbox` is empty); `setValidViewpoints(arr)`, `setMatchableClasses(obj)`, `setIndexableFields(arr)`; `validateMediaAsset0ColumnOnly` renamed `validateMediaAssetColumnsOnly()` returning errors for every photo column. `setSkipDetection` / `setSkipIdentification` re-run `validateSpreadsheet` and store the new errors and warnings. Warnings may be `{id, values}` objects like errors. `indexableFields` is set once at the page root (`BulkImport.jsx`, from `useSiteSettings`) so it is present before any parse, resume or submit.

- [ ] Tests:
  - `missingPhotos` over a boxed row `{mediaAsset0:"a.jpg", mediaAsset2:"c.jpg"}` with only `a.jpg` uploaded → `["c.jpg"]`.
  - `isDynamicKnownColumn` true for `Encounter.mediaAsset4` and `Encounter.mediaAsset4.bboxSource`; with `labeledKeywordAllowedKeys=["viewpoint"]`, a bad `Encounter.mediaAsset0.viewpoint` yields the box viewpoint error id, not `BULKIMPORT_ERROR_INVALID_LABELEDKEYWORDINVALIDVALUE`.
  - `validateSpreadsheet` errors: bad bbox → `BULKIMPORT_ERROR_INVALID_BBOX`; theta `7` → `BULKIMPORT_ERROR_INVALID_THETA`; viewpoint not in list → `BULKIMPORT_ERROR_INVALID_VIEWPOINT`; empty box cells → none.
  - viewpoint `" left "` with `left` in the list → no error.
  - iaClass not in `matchableClasses["Giraffa giraffa"]` → **warning** `{id: "BULKIMPORT_WARNING_IACLASS_NOT_MATCHABLE", values: {allowed}}`; on one store, toggling `setSkipDetection(true)` clears it, `false` restores it, same for `setSkipIdentification`; none and no throw when species empty/unknown.
  - Submit: boxed rows with slots 0 and 2, and a row with only slot 2 (no slot 0), plus same-slot `.bbox`/`.iaClass` → after `updateRawFromNormalizedRow`, `rawData` keeps exactly those keys and values and `rawColumns` lists them; no `Encounter.mediaAsset1`. Un-boxed merged `"a.jpg, b.jpg"` still renumbers to 0,1.
  - `unboxedPhotoCount` = 1 for `{mediaAsset0:"a", mediaAsset0.bbox:"1,1,5,5", mediaAsset1:"b"}`; = 0 when the bbox column exists but every bbox cell is empty.
  - Payload: for the boxed rows above, the `fieldNames` and positional `rows` built by `usePostBulkImport` (test through the hook with a mocked `client.post`; no production extraction) carry `Encounter.mediaAsset0`, `Encounter.mediaAsset2`, `.bbox`, `.iaClass` in matching positions, and nothing for slot 1.
  - Capability: same sheet with `indexableFields=[]` → `isBoxedSheet` false, box columns get today's unknown-column warning.
- [ ] Implement: the uploaded-image validate fn becomes a named method used for every photo column (rule registered in `applyDynamicValidationRules`); box rules registered there by suffix, checked **before** the labeled-keyword branch, which skips box suffixes (also in the `validateSpreadsheet` labeled block). iaClass handled inside `validateSpreadsheet` (needs the row). Submit renumber block guarded by `!this.isBoxedSheet`.
- [ ] Run store tests; commit.

### Task 3: Spreadsheet parse + table

**Files:** `BulkImportSpreadsheet.jsx:81-102,160-172`; `EditableDataTable.jsx:96,178-234,246-270` and `EditableCell`. Tests: `BulkImportSpreadsheet.test.js`, `BulkImportEditableDataTable.test.js`.

- [ ] Tests: uploading a sheet with `Encounter.mediaAsset0`, `Encounter.mediaAsset2`, `Encounter.mediaAsset0.bbox` on a capable site → `columnsDef` contains both photo columns and the row is not merged. New test: an un-boxed sheet with `Encounter.mediaAsset0`, `Encounter.mediaAsset1` merges into `Encounter.mediaAsset0 = "a.jpg, b.jpg"` (no such test exists today). In a boxed sheet, photo, `.bbox`, `.keywords` and labeled-keyword cells render `<input readOnly>`; other cells stay editable; an invalid read-only `.bbox` cell still shows its error text. The missing-image error renders for `Encounter.mediaAsset2` as for slot 0. A warning given as `{id, values}` renders with its values.
- [ ] Implement: when `hasBoxColumns(rowKeys, siteData.bulkImportIndexableFields)`, skip the mediaAsset filter/unshift and the merge. `EditableCell`: `readOnly` when `store.isBoxedSheet && isSlotColumn(col)`; `isMediaAsset` uses `isPhotoSlotColumn`; warning rendering (`EditableDataTable.jsx:165`) accepts `{id, values}`. `DataTable` passes `annotationViewpoint` and `bulkImportMatchableClasses` to the store (capability is set only at the page root); the throttled reconciliation loops over `store.photoColumns`.
- [ ] Run; commit.

### Task 3b: Resume modal for legacy boxed drafts

**Files:** `BulkImportContinueModal.jsx`; Test `BulkImportContinueModal.test.js`.

- [ ] Tests: with a legacy boxed draft in `localStorage` (untouched merged shape, and an edited one where `rawData.Encounter.mediaAsset0 = "a.jpg, c.jpg"`), the modal shows `BULK_IMPORT_CONTINUE_MODAL_REUPLOAD_SPREADSHEET`; Continue hydrates, then resets all spreadsheet state with the same store calls the spreadsheet close handler uses (`BulkImportSpreadsheet.jsx:~423`: data, raw data/columns, columnsDef, file name, worksheet info, upload progress, validation errors), keeping `uploadedImages` and `submissionId`. After resume the spreadsheet upload control is visible and Next is disabled. A non-legacy draft resumes exactly as today.
- [ ] Implement with `isLegacyBoxedDraft(savedStore)`; run; commit.

### Task 4: Warning, instructions, locales

**Files:** `BulkImportSetLocation.jsx` (above the Import button), `BulkImportInstructionsModal.jsx` (new paragraph after STEP2_ACCEPTED_VALUES), `frontend/src/locale/{en,de,es,fr,it}.json`. Tests: `BulkImportSetLocation.test.js` (create if absent), `BulkImportInstuctionsModal.test.js`.

- Keys: `BULKIMPORT_ERROR_INVALID_BBOX`, `BULKIMPORT_ERROR_INVALID_THETA`, `BULKIMPORT_ERROR_INVALID_VIEWPOINT`, `BULKIMPORT_WARNING_IACLASS_NOT_MATCHABLE` (values: `{allowed}`), `BULKIMPORT_UNBOXED_PHOTOS_WARNING` (values: `{count}`), `BULK_IMPORT_INSTRUCTIONS_BOXES`, `BULK_IMPORT_CONTINUE_MODAL_REUPLOAD_SPREADSHEET` ("Your saved spreadsheet has box columns and must be uploaded again. Your uploaded images are kept.").
- Instructions copy (en): "If your spreadsheet fills in at least one box (Encounter.mediaAsset#.bbox), Wildbook uses your boxes and does not run detection on that import. Photos without a box are imported untouched; import them again in a spreadsheet without box columns to have them detected. Ticking “Skip Detection and Identification” on a boxed spreadsheet still stores the boxes' embeddings for species matched with an ml-service model, so they can be matched later. Photo and box cells can't be edited here: fix them in your spreadsheet and upload it again."
- Warning copy (en): "{count, plural, one {# photo has} other {# photos have}} no box. Those photos will be imported without any annotation and won't be detected or matched."
- [ ] Tests: warning shown with count when `unboxedPhotoCount > 0`, absent for 0 and for un-boxed sheets; modal renders the new paragraph; a locale-parity check that the seven keys exist in all five files.
- [ ] Implement; run; commit.

### Task 5: Skill follow-through

**Files:** `src/main/resources/agent-skills/import-your-own-detections.md` (step 6 spreadsheet route), `src/test/java/org/ecocean/api/AgentSkillContentTest.java`.

- [ ] Drop the "ascending, gap-free photo columns" restriction; say the page keeps numbered photo columns and shows photo/box cells read-only. Keep "When in doubt, use the direct route". Update the test that pinned the restriction to pin the new sentence instead.
- [ ] `mvn test -Dtest=AgentSkillContentTest` (one mvn at a time); commit.

Task 4 also: a test that the Import-step warning is absent when the bbox column exists but is empty everywhere.

### Verification

- [ ] `cd frontend && CI=true npx jest src/__tests__/pages/BulkImport` all green (CI uses `npm run jest`; `npm test` hits a pinned Babel mismatch). Baseline before changes: 21 suites, 247 tests passing; `npx eslint src/pages/BulkImport`; `npm run build` succeeds.
- [ ] Codex branch review until converged; PR base `feat/bbox-import-d-skill`, milestone 29.
