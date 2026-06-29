---
name: api-reference
description: Reference for the Wildbook token-scoped API and the analytical skills' endpoints and authentication.
---

# Wildbook Token-Scoped API — Agent Skill

You are an AI agent operating Wildbook's **read-only** API on behalf of a human user. You see exactly
what that user is permitted to see (everything is access-controlled to their account).

## Security — read first
- **Never ask for, accept, or store the user's Wildbook username or password.** You do not need them.
- The user generates a short-lived **bearer token** in Wildbook's UI (Account menu → **API Access**)
  and pastes **only the token** to you.
- Treat the token as a secret: never log or persist it, never send it anywhere except Wildbook over
  HTTPS. It expires after a fixed lifetime that is **configured per Wildbook instance** (commonly
  several hours) — do **not** assume any particular duration. The token-mint response's
  `expiresInSeconds` is the authoritative lifetime; rely on it (this matters for long, unattended
  runs). When the token expires, ask the user to paste a fresh one.

## Authentication
Send the token as a bearer header on every request:
```
Authorization: Bearer <token>
```
The token response includes `expiresInSeconds`. An admin user's token sees all data; a normal user's
token is filtered to their own accessible records. The server also enforces internal access-control
fields, which are never returned to you.

## Endpoints

### Search — `POST /api/v3/search/{index}`
`{index}` is one of: `encounter`, `individual`, `annotation`. (`occurrence` and `media_asset` return
`403` for token callers.) Body is an OpenSearch query, e.g.:
```json
{ "query": { "term": { "taxonomy": "Salamandra salamandra" } } }
```
Pagination via `?from=&size=` query params; total hits in the `X-Wildbook-Total-Hits` response header.
Non-admin `individual` search may only query/sort identity fields. Scripted queries and cross-index
term lookups are rejected.

**Counting with aggregations.** A single bounded `terms` aggregation is supported — e.g. to count
records per location without paging them all:
```json
{ "query": { "term": { "taxonomy": "Equus quagga" } },
  "aggs": { "byLoc": { "terms": { "field": "encounterLocationId", "size": 1000 } } } }
```
The response includes an `aggregations` object alongside `hits`. Rules (anything else → **400**, never a
silent empty result):
- exactly one **`terms`** aggregation per name — no sub-aggregations, no other aggregation type
  (`cardinality`, `date_histogram`, scripted, pipeline, `global`/`filter`/`nested` wrappers, …);
- `terms` may set only `field` (required) and integer `size` (1–1000); no `script`/`include`/`order`/etc.;
- `field` must be one of the allow-listed keyword fields — **encounter**: `locationId`, `taxonomy`,
  `country`, `lifeStage`; **annotation**: `viewpoint`, `iaClass`, `encounterLocationId`,
  `encounterTaxonomy`; **individual** (admin): `taxonomy`, `sex`;
- the only other top-level body key allowed with an aggregation is `query`. For counts only, request
  `?size=0` (URL param) so no hits are returned. Counts are access-controlled to your account, exactly
  like `hits`.

**Response shape (important):** the body is a *flat* envelope — `{ "hits": [ {…document fields…}, … ] }`
— where each element is the document's fields directly. It is **not** the standard OpenSearch
`{"hits":{"hits":[{"_source":…}]}}` shape, and the hit total lives only in the `X-Wildbook-Total-Hits`
header, never in the body.

### Media resolve — `POST /api/v3/media/resolve`
Resolve up to 100 annotation IDs you are allowed to see into displayable image references:
```json
{ "annotationIds": ["<uuid>", "<uuid>"] }
```
Returns an array with **one entry per unique requested ID** (duplicate IDs collapse to one), each
carrying a `status`:
- `identified` — resolved; full image reference present **and** assigned to a named animal
  (`individualId` set).
- `unidentified` — resolved; full image reference present but **not yet assigned** to a named animal
  (`individualId` is `null`). This is a legitimate state, not an error.
- `no_image` — the annotation is visible to you and exists, but has no displayable image to return.
- `unavailable` — the ID is not visible to your account **or** does not exist. These two are
  deliberately reported the same way and the response never reveals which (no existence oracle).
- `error` — a transient failure while resolving this one ID; the rest of the batch is unaffected, so
  **retry just this ID** (with backoff). Distinct from `unavailable`, which you should not retry.

A resolved entry (`identified`/`unidentified`) has the shape `{ id, status, imageUrl, imageWidth,
imageHeight, bbox: [x,y,w,h], theta, viewpoint, encounterId, individualId, methodVersion }`; the others
are just `{ id, status }`. **Branch on `status`: only `identified` and `unidentified` entries carry an
`imageUrl`** — skip image handling for `no_image`/`unavailable`, and retry `error` entries. For an
entry that has an image: the `bbox` is in the `imageWidth`×`imageHeight` coordinate space; **fetch
`imageUrl`, read its real pixel dimensions, and scale `bbox` by `realW/imageWidth`, `realH/imageHeight`
before cropping** (usually a no-op).

## OpenSearch schema (token-exposed fields)
Key indices and fields:
- **encounter** — `id`, `taxonomy`, `locationId`/`locationName`, `date`/`dateMillis`, `individualId`,
  `sex`, `lifeStage`, `livingStatus`, `country`, `behavior`, ...
- **individual** — `id`, `displayName`, `names`/`nameMap`, `sex`, `taxonomy`, `timeOfBirth`/`timeOfDeath`.
- **annotation** — `id`, `encounterId`, `viewpoint`, `iaClass`, `matchAgainst`, `mediaAssetId`, and
  `embeddings` (nested: `method`, `methodVersion`, and the MiewID `vector`). The annotation document
  carries **no sighting date** — to analyse by time, collect the `encounterId`s and look them up in the
  encounter index (a `terms` query) to read `date`/`dateMillis`. MiewID `vector`s are **L2-normalised**
  (unit length), so cosine similarity between two of them is just their dot product.

Access-control fields exist server-side but are **never** returned.

## Paging and limits

Search results come back a page at a time. Pass `?from=` and `?size=` on the request and walk the
set across calls: `from=0&size=200`, then `from=200&size=200`, and so on. The total number of
matches is in the `X-Wildbook-Total-Hits` response header — read it first.

There is a hard ceiling: `from + size` must stay at or below **10,000** (OpenSearch's
`max_result_window`). The API does not offer `scroll` or `search_after`, so a result set larger than
10,000 cannot be fully walked — narrow your search (species, site, date range) instead. Pages are
fetched independently, so a result set that changes while you page can shift slightly at page
boundaries.

To turn annotation IDs into a catalog-animal label and a croppable image, call
`POST /api/v3/media/resolve` with up to **100** annotation IDs per call; it returns `individualId`,
`encounterId`, `viewpoint`, `bbox`, `imageUrl`, and `methodVersion`. The annotation search itself
does not return `individualId`.

For whole-population work that would exceed 10,000 records, split the query into sub-scopes that each
stay under the ceiling and combine the results yourself. For **encounter** searches, split by date
range (`dateMillis`) or site (`locationId`). For **annotation** searches — which carry no date — split
by `viewpoint`, or first fetch the encounter IDs for a date band and then query annotations by those
`encounterId`s. This is the reliable pattern for large catalogues today.

**Transient errors:** retrieving large pages (especially with the heavy embedding `vector`s) is slow,
and under sustained paging the API may intermittently return HTTP `500` (`"query failed"`). These are
transient — retry the same request with a short backoff (e.g. wait 1s, then 2s, then 4s, up to ~4
attempts) before giving up, and pace large sweeps rather than firing requests back-to-back.

## Worked examples

**Find an individual's salamander encounters, then view two annotations:**
1. `POST /api/v3/search/encounter` with `{"query":{"term":{"taxonomy":"Salamandra salamandra"}}}`.
2. Collect annotation IDs (search `annotation`, or via the encounters), then
   `POST /api/v3/media/resolve` with those IDs.
3. For each entry whose `status` is `identified` or `unidentified`, fetch `imageUrl`, scale `bbox`
   to the fetched pixels, crop, and present side-by-side. Skip `no_image`/`unavailable`; retry `error`.

**Comparing embeddings for missed matches:** only compare embeddings within the **same `viewpoint` and
same `methodVersion`** — different viewpoints/versions live in different latent spaces and are not
directly comparable. Calibrate similarity against known same-individual pairs before trusting a score.
Similarity baselines are **species-specific** — e.g. same-individual cosine similarity runs around
~0.50 for whale sharks but ~0.35 for grey nurse sharks — so calibrate per species and never reuse a
threshold across species. Similarity also **decays as the time between two sightings grows**; when
judging a match or measuring reliability, factor in the elapsed time between the photos being compared
(join via `encounterId` to the encounter `dateMillis`).

## References
- **Wildbook documentation** — https://wildbook.docs.wildme.org/ — background on the data model
  (encounters, individuals, annotations, occurrences), taxonomy, and platform concepts. This describes
  the broader Wildbook platform and UI; for anything specific to this read-only token API (endpoints,
  allowed indices, token handling) **this skill is authoritative** where the two differ.
