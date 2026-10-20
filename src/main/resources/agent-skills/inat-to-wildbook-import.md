---
name: inat-to-wildbook-import
description: Pull recent iNaturalist sightings of your species, curate them, and prepare them for bulk import into Wildbook.
---

# Get iNaturalist sightings into Wildbook

## When to use this
Use this when the person says things like "get recent iNaturalist observations of my species
ready for Wildbook", "pull last month's jaguar sightings from the Pantanal so I can import them",
or "download weedy seadragon photos from iNaturalist for our catalog."

## What it does, in plain terms
It downloads recent, wild, photographed sightings of the species you name from iNaturalist, keeps
only the ones suitable for photo identification, downloads the photos, and writes a Wildbook
bulk-import spreadsheet plus a folder of photos. It can also make a simple web page for reviewing
the sightings and unchecking any you don't want before import. It only prepares files — you do the
actual import in Wildbook, and you decide what to keep.

## What you'll need
Python 3.6 or newer (standard library only; the Pillow package is needed only if you want animated
GIFs handled). The species name(s), and optionally a place and a number of days back. The Wildbook
`locationID` and `submitterID` you want stamped on the encounters. No iNaturalist account and no
Wildbook token are required — this uses the public iNaturalist API and hands the finished files to
Wildbook's Bulk Import page.

## Where the tool lives
The tool is the public, MIT-licensed repository
`https://github.com/WildMeOrg/inat-download-recent-species-sightings`, pinned to release `v1.0.0`.
Fetch that release — either download `inat-download-new-species-sightings.py` from the `v1.0.0`
tag, or clone the repo and check out `v1.0.0`. Always use the pinned release so results stay
consistent.

## How to do it
1. Get the tool at the pinned release:
   `git clone https://github.com/WildMeOrg/inat-download-recent-species-sightings && cd inat-download-recent-species-sightings && git checkout v1.0.0`
   (or download `inat-download-new-species-sightings.py` from the `v1.0.0` tag).
2. Run it for the person's species and scope. Example:
   `python3 inat-download-new-species-sightings.py --species "Panthera onca" --days 30 --place "Mato Grosso" --use-locationID "MatoGrosso" --use-submitterID "their_wildbook_username" --html-review --output ./inat_data`
   Use `--social-split-observations` for social species where several animals can share one
   observation. Drop `--html-review` to write the CSV directly with no review page.
3. If you made the review page, open it and help the person uncheck low-quality or unlicensed
   sightings; the page keeps good ones checked and sorted to the top. Export the curated CSV.
4. Hand off for import: tell the person to open Wildbook's **Bulk Import** page, upload the
   `photos/` folder, then upload the CSV. Every row is marked `unapproved` so their team verifies
   before anything enters analyses.
5. Each row carries `Encounter.otherCatalogNumbers` set to `iNaturalist:<observation id>`. This is
   the back-reference that lets Wildbook recognise a sighting that was already imported, so keep it
   in the spreadsheet.

## How to report results
Speak plainly. Say, for example, "I found 47 jaguar sightings from the Pantanal in the last 30
days; 39 have open licenses and clear photos and are ready to import." Tell the person where the
CSV and `photos/` folder are, how many rows the spreadsheet has, and the exact next step (upload
the photos then the CSV on Wildbook's Bulk Import page). Never say anything was imported — you only
prepared the files.

## Cautions
Respect iNaturalist licenses: sightings whose photos have no open license are flagged and left
unchecked — do not import them without the owner's permission. The tool is polite to iNaturalist's
servers (about one request per second); large pulls take a few minutes. Everything is stamped
`unapproved` for the team's own review. This skill cannot import for you and cannot change anything
in Wildbook — the person does the upload.

## Additional references
- Tool source (MIT): https://github.com/WildMeOrg/inat-download-recent-species-sightings (release `v1.0.0`)
