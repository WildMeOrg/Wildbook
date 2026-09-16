---
name: find-missed-matches
description: Check whether the same animal was recorded twice in your catalog under different names, or one name is covering two animals.
---

# Find missed matches

## When to use this
Use this when the person asks things like "did we record the same animal twice?", "are there
duplicates in our catalog?", or "we combined two field sites — find the animals that are really the
same one."

## What it does, in plain terms
It compares the markings of the animals in your catalog and points out two kinds of problem: two
differently-named animals whose photos look like the same individual (a missed match — likely
duplicates), and one named animal whose photos fall into two clearly different-looking groups (a
possible mix-up). It only suggests; you confirm and make any changes in Wildbook.

## What you'll need
A valid access token and a narrowed scope — a species, and usually a site and/or date range. Check
the `X-Wildbook-Total-Hits` header first; if there are more than a couple of thousand marked animals
in scope, ask the person to narrow it (the catalog can't be walked past 10,000 records, and an
all-pairs comparison gets slow well before that).

## How to do it
1. Search `POST /api/v3/search/annotation` for the scope, paging with `from`/`size`
   (`from + size <= 10,000`). Keep each photo's marking-pattern vector, `encounterId`, `viewpoint`,
   and `methodVersion`.
2. Resolve the annotation IDs with `POST /api/v3/media/resolve` (<=100 per call) to attach
   `individualId` and the croppable image.
3. Group strictly by `viewpoint` + `methodVersion` — never compare across them.
4. Calibrate: within known same-animal pairs, measure typical cosine similarity to set a cutoff.
5. Missed matches: for every pair of photos from two *different* `individualId`s in the same
   viewpoint/version group whose similarity exceeds the calibrated cutoff, propose those two animals
   as the same individual. Exclude same-encounter photos from any single comparison.
6. Possible mix-ups: for each animal, if its photos split into two internally-similar but mutually
   distant groups, flag it.
7. Rank candidates by strength of similarity.

## How to report results
Speak plainly. Say, for example, "Two catalog animals look like the same dolphin — here they are
side by side." Show the resolved crops together so the person can judge. Give counts as plain
sentences. Produce a worklist of suggested merges (and any suspected splits) for the person to
action in Wildbook. Never say you merged or changed anything — you cannot.

## Cautions
Only compare photos of the same viewpoint taken with the same model version. Small numbers of photos
are unreliable — say so. You only ever see catalog data the person's account is allowed to see.
This skill is read-only: it produces a worklist of suggestions. You make no changes to the catalog.

## Additional references

This skill is inspired by code developed by Lasha Otarashvili at:
- [Curation Re-ID](https://github.com/LashaO/curation-reid/)
