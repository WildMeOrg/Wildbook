# Bulk-import bounding boxes, PR D: agent skill `import-your-own-detections` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

Plan rev 3 (2026-11-08; Codex branch round 1 applied: page route requires ascending photo columns from slot 0 with no gaps and no preview-based relaxation, VOC zero/one-based conventions, conditional import-only preparation, mode-aware reporting, tighter tests; rev 2: Codex plan round 1: 3 Major + 6 Minor applied while writing the skill: viewpoints come from `annotationViewpoint` in site settings (the static-list deviation is withdrawn); the spreadsheet route is constrained until PR C (slots from 0 with no gaps, exact-slot metadata warning, fall back to the direct route); import-only still extracts and NO_EXTRACTION_CONFIG means imported-but-not-prepared, fix config, do not resend; explicit prerequisites incl. upload filename rules, resizing/letterboxing, CSV convention; theta range, finite quality, 128-char source; index wording qualified; vocabulary enforced outside How to do it including tables; reporting says imported only once status reads `imported`; tests assert the registry mapping, parse the row example through SubmissionJson.rows and BulkValidator.parseBbox, recompute the worked YOLO example from the document, and pin step-level instructions). Stacks on PR E (`feat/bbox-import-e-submissions`, #1851); branch `feat/bbox-import-d-skill`, worktree `/mnt/c/Wildbook-bbox`.

**Goal:** An agent helping a biologist can turn their own detector's output (COCO JSON, YOLO txt, Pascal VOC XML, or a CSV of boxes) into rows Wildbook accepts, then push them through the submissions API (first) or hand a spreadsheet to the Bulk Import page (second), with the same rules the server enforces.

**Architecture:** One markdown skill, `src/main/resources/agent-skills/import-your-own-detections.md`, registered in `AgentSkill.SKILL_RESOURCES`, listed in `index.md`, pinned by `AgentSkillContentTest`. No production logic changes.

**Spec:** `docs/superpowers/specs/2026-11-06-bulk-import-annotation-bboxes-design.md` §4.7 (binding), §4.1 (rules), §4.9 (API route), decisions 1, 2, 3, 8; staging facts from 2026-11-08 (see the Codex log).

## Global Constraints

- Structure as `inat-to-wildbook-import.md`: frontmatter `name: import-your-own-detections`, a one-line `description`, and the REQUIRED_SECTIONS (`## When to use this`, `## What it does, in plain terms`, `## What you'll need`, `## How to do it`, `## How to report results`, `## Cautions`), plus `## Additional references`.
- Capability check is step 1 and gates everything: API route: `GET /api/v3/submissions/capabilities` lists `bbox` and `iaClass` in `rowFields.perMedia`; page route: `/api/v3/site-settings` `bulkImportIndexableFields` lists `Encounter.mediaAsset#.bbox`. If missing, stop and say the site does not accept animal boxes yet (an older site would reject the columns or ignore them and run its own detector).
- Labels: read `bulkImportMatchableClasses` from `/api/v3/site-settings` for the species (key = scientific name with spaces). Map each detector label to one of those animal type labels; ask the person when the mapping is not obvious; never guess. Import-only may use any class the species stores, but the skill only offers the matchable list (simplest correct advice).
- Viewpoints: optional; read from site setting `annotationViewpoint` (rev 2; the rev-1 static-list deviation is withdrawn).
- Boxes: pixel `x,y,width,height` on the photo exactly as stored (the raw pixel grid; Wildbook does not apply the EXIF orientation tag). Read each photo's size from its header; if an orientation tag other than 1 is present, warn and offer to rotate the image pixels first (and recompute boxes) or confirm the detector ran on the unrotated pixels. Conversion recipes for COCO (`[x,y,w,h]`, already pixels), YOLO (normalized centre `cx,cy,w,h` times image size), VOC (`xmin,ymin,xmax,ymax`), each with the §4.1 rounding: left/top floored, right/bottom ceiled, clamped to the image, then width = right - left, height = bottom - top; drop a box whose width or height ends below 1.
- Rows: one row per animal per photo; the same photo repeated on several rows for several animals, every one of those rows boxed, never the identical box twice; several photos in one row only when they show the same animal; a whole-photo box is fine. `.bboxSource` = the detector/model name; `Encounter.quality{N}` = the detector score (same slot number as the photo); drop boxes under a confidence floor the person confirms (suggest 0.5 when they have none). Leave `.theta` out unless the detector's rotation convention was verified (radians, clockwise as displayed, about the box centre).
- Semantics (decision 8): an import that carries any box never runs Wildbook's detector on any of its photos; boxes are used as-is and matched unless the person chose import-only; a photo without a box in such an import is imported untouched. For detection on unboxed photos: a second, separate import without box columns.
- Scale: at most 200 rows per submission (more → several submissions); large frame sets multiply matching work; two frames of the same animal in different rows will show up as a match on both rows' results (expected, not an error).
- API route first: follow `submit-sightings` for the token, draft, uploads, rows, validate, fix, commit and status; the per-photo box fields ride in the same row objects; show one complete row example with box fields (validated in the test against `SubmissionValidator.supported`). Page route second: the same columns in the spreadsheet; the Bulk Import page validates the same rules.
- Reporting: plain language; counts of photos, boxes, rows, dropped boxes and why; records reported as imported only when status reads `imported`; matching reported separately from mode and identification state/code (rev 2).
- Vocabulary in user-facing text: "box"/"animal box", "animal type label"; the words "annotation", "embedding", "iaClass" (and the shared JARGON list) only inside `## How to do it` (tables elsewhere included in the check, rev 2). Staging lesson: `NO_EXTRACTION_CONFIG` on the import task means records and boxes were imported but not prepared, because a vector-configured class lacks species-level routing; give the operator the task link and message, do not resend (rev 2).
- Every edited file LF-only; commit trailers `Co-Authored-By: Codex <noreply@openai.com>` then `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; PR base `feat/bbox-import-e-submissions`, milestone 29.

## Review Focus
1. A YOLO box whose centre sits on the image edge: the clamp keeps x,y >= 0 and right <= W. Covered by the worked example in the skill (pinned in the test).
2. A detector that ran on EXIF-rotated pixels: the skill must catch it before rows are built.
3. An unmapped label ("animal"): the skill asks, never defaults.
4. The page route on a site without the box columns: stop at step 1.
5. Photos with no detections in the same batch: imported untouched; tell the person they need a separate import for detection.

### Task 1: The skill document
- Create `src/main/resources/agent-skills/import-your-own-detections.md` per Global Constraints, with: a COCO, YOLO and VOC conversion table; a worked YOLO edge example (image 1152x768, `cx=0.99 cy=0.5 w=0.04 h=0.2` → left floor(1140.48-23.04)=1117, right ceil(1163.52)=1164 clamped 1152 → `1117,307,35,154`, exact numbers computed in the test from the stated formula); a row example (JSON) with `Encounter.mediaAsset0.bbox`, `.iaClass`, `.bboxSource`, `Encounter.quality0`.
- [x] Commit `feat(agent-skill): import-your-own-detections`

### Task 2: Registration and index
- `AgentSkill.SKILL_RESOURCES.put("import-your-own-detections", "import-your-own-detections.md")`; `index.md` row under "Get sightings into Wildbook (import prep)" and a line under the submissions section pointing to it for detector output.
- [x] Commit with Task 3.

### Task 3: Tests (`AgentSkillContentTest`)
- `catalog_files_and_index_do_not_drift`: exclude the new key with the other import/intake skills.
- New `import_your_own_detections_is_well_formed`: REQUIRED_SECTIONS; frontmatter name; index lists it and its URL; mentions `rowFields.perMedia`, `bulkImportIndexableFields`, `bulkImportMatchableClasses`; every JSON row example's fields are `SubmissionValidator.supported`; every example viewpoint is `Annotation.isValidViewpoint` (rev 2: examples only, the list comes from site settings); the worked YOLO example's numbers equal the formula; `assertNoLeak`; `assertNoJargon(userFacingSections(md))`; user-facing text has no "annotation" or "iaClass".
- [x] Run `mvn -q test -Dtest=AgentSkillContentTest,AgentSkillTest`; commit `test(agent-skill): pin import-your-own-detections`.

### Task 4: Verify, review, PR
- [x] Full suite; LF check; Codex branch review to converged; PR `--base feat/bbox-import-e-submissions`, milestone 29.
- [ ] Follow-ups (outside this repo, recorded only): the local `wildbook-import` skill; a wildbook.docs page.
