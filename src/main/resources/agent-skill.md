# Wildbook Token-Scoped API — Agent Skill

You are an AI agent operating Wildbook's **read-only** API on behalf of a human user. You see exactly
what that user is permitted to see (everything is access-controlled to their account).

## Security — read first
- **Never ask for, accept, or store the user's Wildbook username or password.** You do not need them.
- The user generates a short-lived **bearer token** in Wildbook's UI (Account menu → **API Access**)
  and pastes **only the token** to you.
- Treat the token as a secret: never log or persist it, never send it anywhere except Wildbook over
  HTTPS. It expires (typically ~30 minutes). When it expires, ask the user to paste a fresh one.

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
Non-admin `individual` search may only query/sort identity fields. Aggregations, scripted queries, and
cross-index term lookups are rejected.

### Media resolve — `POST /api/v3/media/resolve`
Resolve up to 100 annotation IDs you are allowed to see into displayable image references:
```json
{ "annotationIds": ["<uuid>", "<uuid>"] }
```
Returns an array of `{ id, imageUrl, imageWidth, imageHeight, bbox: [x,y,w,h], theta, viewpoint,
encounterId, individualId, methodVersion }`. The `bbox` is in the `imageWidth`×`imageHeight`
coordinate space. **Fetch `imageUrl`, read its real pixel dimensions, and scale `bbox` by
`realW/imageWidth`, `realH/imageHeight` before cropping** (usually a no-op). IDs you can't see (or that
don't exist) are simply absent — the response never reveals which.

## OpenSearch schema (token-exposed fields)
See the field reference for full descriptions. Key indices/fields:
- **encounter** — `id`, `taxonomy`, `locationId`/`locationName`, `date`/`dateMillis`, `individualId`,
  `sex`, `lifeStage`, `livingStatus`, `country`, `behavior`, ...
- **individual** — `id`, `displayName`, `names`/`nameMap`, `sex`, `taxonomy`, `timeOfBirth`/`timeOfDeath`.
- **annotation** — `id`, `encounterId`, `viewpoint`, `iaClass`, `matchAgainst`, `mediaAssetId`, and
  `embeddings` (nested: `method`, `methodVersion`, and the MiewID `vector`).

Access-control fields exist server-side but are **never** returned.

## Worked examples

**Find an individual's salamander encounters, then view two annotations:**
1. `POST /api/v3/search/encounter` with `{"query":{"term":{"taxonomy":"Salamandra salamandra"}}}`.
2. Collect annotation IDs (search `annotation`, or via the encounters), then
   `POST /api/v3/media/resolve` with those IDs.
3. For each result, fetch `imageUrl`, scale `bbox` to the fetched pixels, crop, and present
   side-by-side.

**Comparing embeddings for missed matches:** only compare embeddings within the **same `viewpoint` and
same `methodVersion`** — different viewpoints/versions live in different latent spaces and are not
directly comparable. Calibrate similarity against known same-individual pairs before trusting a score.
