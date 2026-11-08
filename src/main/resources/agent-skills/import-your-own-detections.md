---
name: import-your-own-detections
description: Turn your own detector's boxes (camera traps, BRUVs, drones) into Wildbook sightings that are matched without Wildbook re-detecting the animals.
---

# Import your own detector's animal boxes into Wildbook

## When to use this
Use this when the person already ran their own detector and says things like "we have
MegaDetector output for last season's camera-trap frames, can Wildbook use our boxes",
"import our BRUV detections and match the sharks", or "here are YOLO labels for these drone
photos, send them to Wildbook without detecting again."

## What it does, in plain terms
It reads the person's photos and their detector's boxes, turns each box into one animal box
Wildbook understands, and sends them in. Wildbook then uses those boxes exactly as given: it does
not run its own detector on any photo in that batch, and it compares each boxed animal against
known individuals (unless the person asked for import only). Photos in the same batch that have
no box are kept as plain photos and are not searched for animals; to have Wildbook find animals
on those, send them separately, in a batch without boxes.

## What you'll need
- The photos themselves, as files (video already split into frames), each a JPEG or PNG.
- The detector's output for those photos: COCO JSON, YOLO text files, Pascal VOC XML, or a CSV
  with one box per line, plus the detector's name and version.
- What the detector's coordinates refer to: the original photo, or a resized or letterboxed copy.
- The species, a configured location ID and the observation date for every sighting.
- For sending directly: a submissions token for the person's own Wildbook account (see
  `submit-sightings`). For a spreadsheet: a Wildbook account that can use the Bulk Import page.

## How to do it

### 1. Check that this Wildbook accepts animal boxes
- Direct route (preferred): `GET /api/v3/submissions/capabilities`. Continue only when
  `rowFields.perMedia` lists `bbox` and `iaClass`.
- Spreadsheet route: `GET /api/v3/site-settings`. Continue only when `bulkImportIndexableFields`
  lists `Encounter.mediaAsset#.bbox`.
If the check fails, stop and tell the person: "This Wildbook does not accept animal boxes yet."
An older site would reject the box columns, or ignore them and run its own detector instead.

### 2. Read the site's labels
From `/api/v3/site-settings`:
- `bulkImportMatchableClasses`: for the species (key = scientific name with spaces, for example
  `Giraffa giraffa giraffa`) the animal type labels Wildbook can match, for example
  `["giraffe_whole"]`. Use only these in `.iaClass`. (An import-only batch would also accept
  other labels the species stores, but those boxes could never be matched later, so this workflow
  deliberately sticks to the matchable ones.)
- `annotationViewpoint`: the allowed viewpoint values, for example `left`, `right`, `front`,
  `back`, `frontleft`. Viewpoint is optional.

Map each detector label (for example `animal`, `giraffe`, `0`) to one of those animal type labels.
When the right label is not obvious, ask the person; never guess. If the species has no matchable
label, stop and tell the operator the species is not set up for matching.

### 3. Check every photo before converting
- Read each photo's width and height from the file itself, exactly as stored. Wildbook measures
  boxes on that stored pixel grid and does not apply the EXIF orientation tag.
- If a photo carries an EXIF orientation other than 1, find out whether the detector ran on the
  stored pixels or on a rotated view. If it ran on a rotated view, either rotate the photo's
  pixels (and save it without the tag) before sending it, or convert the boxes back to the stored
  grid. Never send boxes from a rotated view against an unrotated file.
- If the detector worked on a resized or letterboxed copy, scale and shift the boxes back to the
  original photo first.
- Filenames for the direct route: 1–128 ASCII letters, digits, dots, underscores and hyphens,
  starting with a letter or digit, `.jpg`/`.jpeg` for JPEG bytes and `.png` for PNG bytes, and no
  two names that differ only in letter case. Rename a copy when needed and keep a table from the
  original name to the uploaded name.

### 4. Convert every box to `x,y,width,height`
Wildbook takes whole pixels: `x,y` is the top-left corner (origin at the photo's top-left,
x to the right, y down), and `width,height` is the size. Convert each format to a left, top,
right and bottom edge in pixels:

| Format | Left | Top | Right | Bottom |
|---|---|---|---|---|
| COCO `bbox: [x, y, w, h]` (pixels) | x | y | x + w | y + h |
| YOLO `class cx cy w h` (fractions of the photo) | (cx − w/2) × W | (cy − h/2) × H | (cx + w/2) × W | (cy + h/2) × H |
| Pascal VOC `xmin ymin xmax ymax`, zero-based edges | xmin | ymin | xmax | ymax |
| Pascal VOC `xmin ymin xmax ymax`, one-based and inclusive (the original VOC convention) | xmin − 1 | ymin − 1 | xmax | ymax |
| CSV | confirm with the person which of the above its columns follow, including the same zero-based or one-based question | | | |

Settle the convention before converting: where the origin is, and whether the far edge is
included. A one-based inclusive box `1,1,1,1` is one pixel, `0,0,1,1` in Wildbook's terms.

Then round outward and keep the box inside the photo: left and top are rounded down, right and
bottom are rounded up, all four are clamped to `0…W` and `0…H`, and `width = right − left`,
`height = bottom − top`. Drop a box whose width or height ends up below 1 and say so.

Worked example (YOLO, photo 1152×768, `cx=0.99 cy=0.5 w=0.04 h=0.2`): left = floor(1117.44) =
1117, top = floor(307.2) = 307, right = ceil(1163.52) = 1164 clamped to 1152, bottom =
ceil(460.8) = 461. The box is `1117,307,35,154`.

A box covering the whole photo is fine (pre-cropped frames often have one).

### 5. Build the rows
- One row per animal per photo. Several animals on one photo: one row each, every row naming the
  same photo with that animal's own box. Every row that names that photo must carry a box, and no
  two rows may carry the identical box.
- Several photos in one row only when they show the same animal; give each its own slot number:
  `Encounter.mediaAsset0`, `Encounter.mediaAsset1`, …
- Per photo slot N:
  - `Encounter.mediaAsset{N}`: the uploaded filename.
  - `Encounter.mediaAsset{N}.bbox`: `"x,y,width,height"`.
  - `Encounter.mediaAsset{N}.iaClass`: the animal type label (required with a box).
  - `Encounter.mediaAsset{N}.viewpoint`: optional, one of `annotationViewpoint`.
  - `Encounter.mediaAsset{N}.bboxSource`: the detector name and version, at most 128 characters
    after leading and trailing spaces are trimmed.
  - `Encounter.quality{N}`: the detector's score for that box, same N as its photo; any finite
    number.
  - `Encounter.mediaAsset{N}.theta`: leave it out unless the detector reports rotated boxes and
    its convention was checked: radians, clockwise as displayed, about the box centre, between
    −6.2832 and 6.2832; the box itself must fit the photo before rotation.
- Drop boxes below a confidence floor the person confirms (suggest 0.5 when they have none) and
  count them.
- Add the sighting fields every row needs (`Encounter.genus`, `Encounter.specificEpithet`,
  `Encounter.year` and the rest of the date, `Encounter.locationID`); see `submit-sightings` for
  the full list.

Example row for the direct route:

```json
{"rows": [
  {"clientRowId": "frame-0412-animal-1",
   "fields": {
     "Encounter.genus": "Giraffa",
     "Encounter.specificEpithet": "giraffa giraffa",
     "Encounter.year": 2025, "Encounter.month": 10, "Encounter.day": 25,
     "Encounter.locationID": "Aachen",
     "Encounter.mediaAsset0": "frame-0412.jpg",
     "Encounter.mediaAsset0.bbox": "40,34,514,695",
     "Encounter.mediaAsset0.iaClass": "giraffe_whole",
     "Encounter.mediaAsset0.viewpoint": "left",
     "Encounter.mediaAsset0.bboxSource": "megadetector-v5a",
     "Encounter.quality0": 0.93}}
]}
```

### 6. Send them
- **Direct route (preferred):** follow `submit-sightings` for the token, draft, uploads, rows,
  validation, fixes, commit and status. Send at most 200 rows per submission; split larger sets
  into several submissions, keeping every row of one photo in the same submission. The box
  fields ride in the same row objects. Choose `detect-and-identify` to have the boxes matched,
  or `import-only` to store them and prepare them for later matching without matching now (the
  preparing happens only where the species is set up for matching on this site; see step 7).
  Validation rejects a box outside its photo (`BOX_OUT_OF_BOUNDS`), a missing or unmatchable
  label (`MISSING_CLASS`, `UNKNOWN_CLASS`), a photo boxed on one row but not another
  (`DUPLICATE_MEDIA`) and the identical box twice (`DUPLICATE_BOX`); fix the rows and validate
  again.
- **Spreadsheet route:** use the same column names on the Bulk Import page, with three rules,
  because the page collects a row's photos in the order their columns appear and numbers them
  again from 0: put the photo columns in ascending order (`Encounter.mediaAsset0`, then
  `Encounter.mediaAsset1`, …), fill every row's photos from slot 0 with no empty photo cell
  before its last photo, and keep each box, label and score on the same number as its photo.
  Breaking any of these can put a box on the wrong photo, and a box that still fits the other
  photo would not be caught. Also, once any column ends in `.bbox`, every row of that sheet, boxed
  or not, reads keywords and `Encounter.quality{N}` by the exact slot number. When in doubt, use
  the direct route.

### 7. After sending
On the submission status (`GET /api/v3/submissions/{id}`), detection reads `skipped` with code
`BOXES_SUPPLIED`: expected, Wildbook used the supplied boxes. `identification` carries the
hand-off state described in `submit-sightings` (`pending`, `dispatching`, `dispatched`, or a
failure with its own code). For `import-only`, a `pending`, `dispatching` or `dispatched` hand-off
also carries code `BOXES_EXTRACTED_ONLY`: the boxes were sent to be prepared for later matching,
which does not by itself prove the preparation finished.

If the linked import task shows the error `NO_EXTRACTION_CONFIG`, the records and boxes were
imported, but this site has matching configured for that animal type label without the
species-level routing that prepares boxes, so the boxes were not prepared. Give the operator the
import task link and the error's own message so they can fix the site configuration. Do not send
the photos again. (A species with no matching set up at all has nothing to prepare; its boxes are
simply stored.)

## How to report results
Speak plainly. Before anything is sent, say what is ready, for example: "214 photos, 263 animal
boxes in 263 rows; 31 boxes below your 0.5 confidence floor were left out; 12 photos had no box
and are not included." Never say anything was imported while you have only prepared rows. After a
direct submission, report the records as imported only once its status reads `imported`. Report
matching separately, from the processing mode and the `identification` state and code: for
example "matching was handed off and is running", "import-only: boxes stored, no matching", or
"the hand-off failed; the operator needs to look". Give the person the import task link to review
matches. If anything was rejected, list the rows and the plain reason.

## Cautions
- Boxes are measured on the photo exactly as stored; a photo the detector saw rotated, resized or
  letterboxed needs its boxes converted first.
- A photo either has a box on every row it appears in or appears on only one row with no box.
- Wildbook never runs its own detector on any photo in a batch that has boxes: photos without a
  box in that batch are imported untouched, as plain photos. For detection on those, send a
  separate batch without box columns.
- One box per photo per row; a second animal on the same photo is another row.
- Many frames of the same animal multiply the matching work; two frames of one animal in
  different rows of the same batch will show each other as matches, which is expected.
- Never invent an animal type label, a viewpoint or a rotation the detector did not give.

## Additional references
- `submit-sightings`: tokens, drafts, uploads, validation, commit and status for the direct route.
- `api-reference`: site settings and other read endpoints.
