---
name: find-misfiled-sightings
description: Find photos that appear to belong to a different catalog animal than the one they are filed under — likely a cataloging mix-up in the field.
---

# Find misfiled sightings

## When to use this
Use this when the person asks things like "did we put a photo under the wrong animal?",
"one of our sightings doesn't look like the rest", "I think a photo was attributed to the
wrong individual", or "we want to audit our catalog for filing mistakes."

## What it does, in plain terms
It looks at how well each photo fits the pattern of the animal it is filed under, and
compares that to how well it would fit other animals nearby in the catalog. When a photo
seems to match a different catalog animal more convincingly than its current one, this
tool flags it. The result is a prioritised worklist for you to review — the tool never
re-files or changes any record. You confirm and make any corrections in Wildbook.

## What you'll need
A valid access token and a narrowed scope — at minimum a species, and ideally a site
and/or date range. The same 10,000-record ceiling applies as in other analytical tools,
so if the scope is very large, ask the person to narrow it. A minimum of three
independent reference photos per animal is required before the tool can score that animal;
animals with fewer photos are skipped and noted in the report.

## How to do it
1. Search `POST /api/v3/search/annotation` for the scope, paging with `from`/`size`
   (`from + size <= 10,000`). Keep each annotation's marking-pattern vector,
   `encounterId`, `viewpoint`, and `methodVersion`.
2. Resolve the annotation IDs with `POST /api/v3/media/resolve` (<=100 IDs per call) to
   attach `individualId` and the croppable image for each annotation.
3. Group strictly by `viewpoint` + `methodVersion` — never compare photos from different
   viewpoints or different model versions; they produce incomparable scores.
4. Calibrate a similarity cutoff using known same-animal pairs within each group, so that
   the threshold is tuned to the actual marking patterns and photo conditions in the scope.
5. For each photo, measure how well it fits its own catalog animal. When computing that
   own-animal similarity, exclude the photo itself and exclude all other photos from the
   same sighting (same `encounterId`), so that only independent reference photos contribute
   to the baseline. If fewer than three independent reference photos remain after exclusion,
   skip scoring for this photo and note it in the output.
6. Measure how well the same photo fits the closest other catalog animal in the group
   (the one yielding the highest similarity outside the animal's own catalog entry).
7. Flag photos where the closest other animal scores clearly higher than the own-animal
   baseline; rank the flagged photos by the size of that margin, largest first.

## How to report results
Speak plainly. For example: "Photo X, from sighting on 12 March at Site A filed under
Dolphin #47, looks more like Dolphin #23 — here are both animals side by side." Show
resolved crops of the flagged photo alongside representative photos from both the current
animal and the suspected-correct animal so the person can judge visually. Give counts as
plain sentences ("3 of 142 sightings may be misfiled"). Produce a worklist for the person
to review in Wildbook. Never say you have re-filed or changed anything — this skill is
read-only and produces a to-do list only.

## Cautions
Only photos of the same viewpoint processed with the same model version are compared —
do not mix them (with the exception of some finned species, such as dolphins, orcas, and others matched by fin shape). 
Results are unreliable when an animal has very few photos; this tool requires at least three independent reference sightings per animal and will skip animals
below that threshold. A photo that looks unusual (unusual lighting, angle, or life-stage)
may be flagged even when correctly filed — treat every flag as a suggestion, not a
conclusion. You only see the catalog data your account is permitted to view, so the
analysis is bounded by that visibility. This skill is read-only: it produces a worklist
of suspected filing errors. No records are changed.
