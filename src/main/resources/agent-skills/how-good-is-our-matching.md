---
name: how-good-is-our-matching
description: Measure how reliably the automatic photo-matching identifies the right animal for a given species, site, or time period — expressed as plain pass/fail percentages a biologist can act on.
---

# How good is our matching?

## When to use this
Use this when the person asks things like "how accurate is our ID system?", "can we trust the
automatic matching for this species?", "what percentage of the time does the system get it right?",
or "we need to report our re-identification reliability to a funder — what are the numbers?"

## What it does, in plain terms
It picks a sample of photos from your catalog that have already been confirmed to belong to a
known, named animal. It then hides each photo and asks the matching system to rank the catalog
animals by how similar they look. If the correct animal comes back at the top, that is a success.
This is repeated across many photos to build up a reliability score for the scope you requested.

The result is a plain-language accuracy report — for example, "the right animal came up first
87% of the time, and was in the top five 96% of the time, across 214 test photos." You can then
decide whether those numbers are good enough for your data collection or reporting purposes.

This skill is read-only: it measures your catalog and produces a results report. It makes no
changes to any records.

## What you'll need
A valid access token and a narrowed scope — at minimum a species, and ideally a site and/or date
range. A useful accuracy measurement requires at least 30 confirmed-identity photos spread across
at least 10 named animals; if the scope has fewer, the tool will warn you that the sample is too
small to trust. The 10,000-record ceiling on searches applies here too, so ask the person to
narrow very large scopes.

## How to do it
1. Search `POST /api/v3/search/annotation` for the scope, paging with `from`/`size`
   (`from + size <= 10,000`). Retain each annotation's marking-pattern vector, `encounterId`,
   `viewpoint`, and `methodVersion`.
2. Resolve the annotation IDs with `POST /api/v3/media/resolve` (<=100 IDs per call) to attach
   `individualId` and the croppable image for each annotation.
3. Discard annotations with no `individualId` — only confirmed-identity photos can serve as
   test cases or catalog references.
4. Group strictly by `viewpoint` + `methodVersion`. Never compare photos across viewpoints or
   across model versions; the similarity scores are not comparable.
5. For each group, conduct a leave-one-out evaluation:
   a. Take one photo (the "query") from a known animal.
   b. Build the reference set from all other photos in the group that belong to a *different*
      sighting (different `encounterId`) than the query. Exclude all photos from the same sighting
      as the query — including photos of other animals recorded in the same encounter — so that
      the scoring cannot be inflated by photos taken alongside the query on the same day.
   c. Rank all catalog animals in the reference set by their highest cosine similarity to the
      query photo.
   d. Record: (i) whether the correct animal ranked first (Rank-1 hit), and (ii) whether the
      correct animal appeared anywhere in the top five (Top-5 hit).
   e. Repeat for every photo in the group that has at least three independent reference photos
      remaining after exclusion; skip and note photos that fall below that threshold.
6. Aggregate Rank-1 and Top-5 counts and divide by the total number of evaluated photos to get
   percentages.

## How to report results
Speak plainly. Lead with the headline numbers, then add context. For example:

> "For left-flank photos of bottlenose dolphins at Site A (2018–2024), the right animal came up
> first 87% of the time, and was in the top five 96% of the time. Those figures are based on 214
> test photos from 58 named individuals."

Always state the sample size (number of test photos and number of individuals covered). If the
sample is below 30 photos or 10 individuals, add a clear caveat: "These numbers are based on a
small sample and may not reflect performance on the full catalog — treat them as indicative only."
Break results out by viewpoint or model version if those subgroups tell meaningfully different
stories. Produce a brief summary table if it helps clarity. Never imply precision the sample size
does not support.

## Cautions
Results apply only to the viewpoint and model version you evaluated — do not generalise across
them. Results describe accuracy for *this* scope (species, site, date range), not the whole
catalog; other scopes may perform differently. Small samples are unreliable — state the sample
size and flag low counts prominently. You only see catalog data the person's account is permitted
to view, so the evaluation is bounded by that visibility. This skill is read-only: it produces an
accuracy report and makes no changes to any records.

## Additional references

This skill is inspired by code developed by Lasha Otarashvili at:
- [Curation Re-ID](https://github.com/LashaO/curation-reid/)
