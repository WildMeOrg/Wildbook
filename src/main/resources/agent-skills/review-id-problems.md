---
name: review-id-problems
description: Present suspected ID problems from the other analytical tools side by side, capture your confirm/reject decisions, and produce a clean action list for the catalog manager.
---

# Review ID problems

## When to use this
Use this when the person says things like "let me look through the flagged animals", "show me the
pairs that might be duplicates", "I want to go through the misfiled photo list", or "help me
decide which of these matches are real." It is the final step after running find-missed-matches,
find-misfiled-sightings, or how-good-is-our-matching — those tools build the worklist; this tool
walks you through it and records your decisions.

## What it does, in plain terms
It takes the list of suspected problems that the other tools produced, pulls up the photos for each
one, and shows them side by side so you can make an informed judgment. For each case you say yes
(confirm the problem is real) or no (reject it as a false alarm). It keeps track of your decisions
and applies a simple consistency check: if you confirm that Animal A and Animal B look like the same
individual, and separately confirm that Animal B and Animal C look like the same individual, it also
flags the A-and-C pair for your attention.

At the end it produces a clean, deduplicated to-do list — a worklist of merges, splits, or
re-filings that you can hand to the catalog manager to action in Wildbook. This tool is read-only:
it changes nothing in the catalog. All changes are made later, by a person, directly in Wildbook.

## What you'll need
A valid access token for the Wildbook catalog. A worklist from at least one of the other tools
(find-missed-matches, find-misfiled-sightings, or how-good-is-our-matching) containing annotation
IDs or encounter IDs for the suspected problems. You only see records your account is permitted to
view — if a pair involves an animal outside your access, it will be skipped and noted.

## How to do it
1. Accept the worklist produced by the upstream analytical tool. Each entry should carry at least
   two annotation IDs (the pair or group under suspicion) and a brief reason (e.g. "missed match
   candidate", "misfiled sighting").
2. For each entry, resolve the annotation IDs to croppable images by calling
   `POST /api/v3/media/resolve` (up to 100 IDs per call). The response provides a croppable image
   URL and the associated `individualId` for each annotation.
3. Present the resolved crops side by side to the person, grouped by the suspicion type. Label each
   animal clearly with its catalog name or ID. If an annotation's image cannot be resolved (e.g.
   access denied or missing asset), note it as skipped and move on.
4. Ask the person to confirm or reject each case. Record the decision (confirmed / rejected) and any
   free-text note the person adds.
5. Apply simple consistency closure over confirmed pairs: collect all confirmed "same animal" pairs,
   then use union-find (or equivalent) to group them into clusters. For every pair of annotations
   within the same cluster that has not yet been explicitly reviewed, add it to the worklist as a
   derived suggestion — mark it clearly as "suggested by consistency" rather than directly flagged.
6. Deduplicate the final worklist: if the same pair appears from multiple sources (e.g. flagged as
   both a missed match and a misfiled sighting), merge them into one action item with all relevant
   context combined.
7. Present the final deduplicated to-do list, grouped by action type (merges, splits, re-filings).
   For each item include: the animal catalog names or IDs involved, the reason for the flag, whether
   it was directly confirmed or derived by consistency closure, and the person's note if any. Export
   or share the list in whatever format the person prefers (plain text, CSV, or screen-readable
   summary).

## How to report results
Speak plainly. Introduce each case simply: "Here are two photos — the system thinks these might be
the same animal. What do you think?" After the person decides, confirm back: "Got it — marked as
confirmed" or "Noted — rejected." At the end summarise: "You reviewed 18 cases: 11 confirmed, 7
rejected. The consistency check added 3 more suggested merges for your attention." Present the
final worklist as a numbered list of plain-language action items. For example:

> 1. Merge Dolphin #12 and Dolphin #47 (directly confirmed)
> 2. Refile sighting S-2019-0831 from Dolphin #12 to Dolphin #23 (directly confirmed)
> 3. Review possible merge of Dolphin #47 and Dolphin #23 (suggested by consistency — not yet
>    reviewed directly)

Never imply that any action has already been taken. This tool only prepares the list.

## Cautions
This tool is read-only. It shows photos and records decisions; it does not merge, split, refile, or
change any record in Wildbook. All catalog changes must be made manually by the catalog manager
after reviewing this list. You only see records your account is permitted to view; any case
involving a restricted record will be skipped. Derived (consistency-closure) suggestions are
inferences from your confirmed decisions — treat them as starting points for further review, not
as conclusions. When the worklist is large, consider working through it in sessions and saving your
progress so you do not have to restart from the beginning.
