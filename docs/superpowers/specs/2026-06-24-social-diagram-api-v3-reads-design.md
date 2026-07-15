# Social Diagram read-path migration to `/api/v3`

**Date:** 2026-06-24
**Branch:** `fix/social-diagram-api-v3-reads` (off `fix/api-v3-individuals-get`, which is PR #1633)
**Status:** design — revised after Codex review (rounds 1–2); awaiting spec review

## Problem

The legacy individual page `individuals.jsp` renders a **Social Diagram** tab whose
JavaScript (`src/main/webapp/javascript/bubbleDiagram/encounter-calls.js`) reads data
through the generic DataNucleus REST servlet (`/api/*`, `org.ecocean.servlet.RestServlet`):

| Line | Call | Feeds | Problem |
|------|------|-------|---------|
| 491 | `GET /api/jdoql?SELECT FROM MarkedIndividual WHERE individualID=="…"` | encounter table | **StackOverflowError** — the recursive POJO serializer loops on the `User`↔`Organization` bidirectional reference cycle. Also bypasses the collaboration/role ACL. |
| 655 | `GET /api/jdoql?SELECT FROM org.ecocean.social.Relationship WHERE …` | relationship table | same unsafe servlet |
| 703 | `GET /api/org.ecocean.MarkedIndividual/{id}` (per partner) | partner display in relationship table | same recursive serializer; client-side N+1 |

The `/api/jdoql` MarkedIndividual query is the exact request that produced the reported
`StackOverflowError`.

PR #1633 added a safe, ACL-aware `GET /api/v3/individuals/{id}`, but it returns the
**OpenSearch document** shape (individual scalars, `socialUnits[]`, partner-summary
`relationships[]`, and encounter **aggregates only** — `encounterIds[]`, counts,
co-occurrence map). It exposes **no per-encounter detail**, so it cannot feed the encounter
table directly.

## Scope

**Reads only.** Migrate the three crashing/unsafe read calls (491, 655, 703) onto one new
secured endpoint.

Out of scope (left on the legacy API, by decision):
- The d3 **bubble-graph visualization** — `getData` / line 29, `GET /encounters/occurrenceGraphJson.jsp`.
  See "Known residual exposure" below.
- Relationship **write** flow — line 585 (`getRelationshipData`, edit-form populate) and the
  add/edit/remove relationship form. These POST/PATCH/DELETE through the legacy API and have
  no `/api/v3` equivalent yet. **The relationship table continues to render edit/remove
  buttons that call these legacy write paths**, so the new endpoint must preserve the exact
  relationship identifier those paths expect (see Security/Identity below).

## Approach

Option B: **one dedicated, server-assembled read endpoint**. Chosen over an N+1 across
`/api/v3/encounters/{id}` because Sharkbook individuals routinely have large encounter
counts, and because server-side assembly is what makes per-encounter ACL filtering
enforceable.

### Endpoint

`GET /api/v3/individuals/info/social-data?id={individualId}`

New `args[2] == "social-data"` branch in `org.ecocean.api.MarkedIndividualInfo.doGet`.
This servlet already owns `/api/v3/individuals/info/*` (no routing collision with
`BaseObject`'s `/api/v3/individuals/*`), already returns `401` for an anonymous caller, and
already runs inside a managed Shepherd transaction. No web.xml change; no OpenSearch
serializer change (no reindex).

### Security model

1. **Caller auth** — anonymous → `401` (inherited from `MarkedIndividualInfo`).
2. **Individual gate** — load the individual by id; if
   `individual.canUserView(currentUser, myShepherd)` is false → `403`
   (the `MarkedIndividual.canUserView` from PR #1633).
3. **Per-encounter filter (primary requirement)** — iterate `individual.getEncounters()` and
   include an encounter row **only if `enc.canUserView(currentUser, myShepherd)`**. Encounters
   the caller can't see are dropped before serialization. (Note: `canUserView` admits an
   encounter for an admin OR via `Collaboration.canUserAccessEncounter`.)
4. **Co-occurrence ("occurring with") filter** — computed **server-side in this endpoint**,
   not from the legacy JSP. For each viewable encounter, walk its occurrence's encounter list
   (`occ.getEncounters()`) and include a companion individual **only if that companion's own
   encounter in the occurrence is itself `enc.canUserView(currentUser, myShepherd)`**. Do NOT
   use occurrence-level authorization — `Collaboration.canUserViewOccurrence` admits an
   occurrence when *any* one encounter is viewable, which is exactly the leak: the legacy
   `occurrenceGraphJson.jsp` authorizes that way and then serializes *every* companion's
   id/name/sex/haplotype. The check must be per-companion-encounter.
5. **Relationship partner filter** — resolve the partner robustly (object link
   `Relationship.getOtherMarkedIndividual(self)` first; if null, fall back to the non-self
   `markedIndividualName{1,2}` via `myShepherd.getMarkedIndividual(name)`). Include the
   relationship row **only if the resolved partner is non-null and
   `partner.canUserView(currentUser, myShepherd)`**; otherwise omit the row (and never
   serialize any partner field). Prevents leaking partner identity across collaboration
   boundaries.

### Identity requirement (do not break the retained write path)

The relationship row's `_id` MUST be byte-identical to the value the legacy REST API exposed
as `_id` — the DataNucleus **datastore identity** string. The retained edit/remove buttons
build `persistenceID = _id + "[OID]org.ecocean.social.Relationship"` (`individuals.jsp:1819`)
and `RelationshipDelete` resolves the row via `getObjectById` on that. Derive the id with
DataNucleus identity utilities (e.g. the identity object from `JDOHelper.getObjectId(rel)` /
the PM), matching the legacy format exactly. A regression test (below) must confirm the
emitted `_id`, run through the existing `persistenceID` construction, resolves the same
`Relationship`.

### Response shape

Field names mirror what `encounter-calls.js` already reads, so the JS **row-assembly logic is
retained** (only the data *source* changes, plus `occurringWith` now arrives from the endpoint
and partner detail is embedded).

```json
{
  "success": true,
  "statusCode": 200,
  "individualId": "cb9e44ca-…",
  "encounters": [
    {
      "catalogNumber": "…",
      "dateInMilliseconds": 1610000000000,
      "year": 2021, "month": 1, "day": 7,
      "locationID": "…",
      "occurrenceID": "…",
      "sex": "female",
      "behavior": "…",
      "alternateid": "…",
      "dataTypes": "both",
      "occurringWith": "DISPLAYNAME-A, DISPLAYNAME-B"
    }
  ],
  "relationships": [
    {
      "_id": "<datastore-identity string, legacy-compatible>",
      "type": "…",
      "relatedSocialUnitName": "…",
      "markedIndividualName1": "…",
      "markedIndividualName2": "…",
      "markedIndividualRole1": "…",
      "markedIndividualRole2": "…",
      "startTime": 1610000000000,
      "endTime": -1,
      "partner": {
        "individualID": "…",
        "displayName": "…",
        "nickName": "…",
        "alternateid": "…",
        "sex": "…",
        "localHaplotypeReflection": "…"
      }
    }
  ]
}
```

- `dataTypes` is computed server-side using the **exact legacy precedence** in the JS, treating
  **any** annotation as image/youtube data (i.e. `getAnnotations().size() > 0`, NOT
  non-trivial-only — preserving current behavior): `both` (tissue samples AND annotations) →
  the tissue sample `type` (tissue only) → `youtube-image` (annotation whose `eventID`
  contains "youtube") → `image` (annotation) → `""`.
- `occurringWith` is a **display string** — the comma-joined (`", "`) `displayName`s of the
  ACL-filtered co-occurring individuals — matching the legacy value the encounter table passes
  straight into `makeTable` (it is a column string, not an array; an array would be mis-rendered
  as control data). It replaces the join against the legacy JSP for the encounter table.
- `year`/`month`/`day`/`dateInMilliseconds` are passed through so the JS keeps its existing
  date-precision formatting unchanged.
- `partner` is embedded so the per-partner fetch (line 703) is removed.

### Server assembly

A private method in `MarkedIndividualInfo` builds the payload from domain getters (no
OpenSearch dependency):

- Encounters: `enc.getCatalogNumber()`, `getDateInMilliseconds()`, `getYear/getMonth/getDay`,
  `getLocationID()`, `getOccurrenceID()`, `getSex()`, `getBehavior()`, `getAlternateID()`,
  `getTissueSamples()`, `getAnnotations()`, `getEventID()` (for `dataTypes`); plus the
  ACL-filtered co-occurring individuals for `occurringWith` (via the encounter's occurrence).
- Relationships: `myShepherd.getAllRelationshipsForMarkedIndividual(id)`; per relationship the
  `Relationship` getters, the legacy-compatible `_id`, and the robustly-resolved + ACL-checked
  partner block.

### JavaScript changes (`encounter-calls.js`)

- `getEncounterTableData` (491): replace the jdoql call with one fetch to the new endpoint;
  the existing row-assembly loop is retained but reads `response.encounters[i]` and takes
  `occurringWith` from the row instead of cross-referencing `occurrenceObjectArray`.
- `getRelationshipTableData` (655): build rows from `response.relationships`; the existing
  role/`relationshipWith`/`edit`/`remove` array construction is retained; partner display read
  from the embedded `partner` (no per-partner fetch). The `edit`/`remove` button values keep
  using `_id`, so the legacy write path is unaffected.
- `getIndividualData` (703): **removed**.
- Untouched: `getData`/line 29 (bubble graph), `getRelationshipData`/line 585 (edit-form write
  path), add/edit/remove form handlers.

Because the response carries both arrays, it is fetched once per diagram load and cached in a
module variable; both table builders read from it. (Caching detail finalized in the plan.)

## Error handling

| Condition | Status |
|-----------|--------|
| anonymous caller | 401 |
| missing/blank `id` | 400 |
| individual not found | 404 |
| individual not viewable | 403 |
| success | 200 |

All via the existing `statusCode` JSON convention in `MarkedIndividualInfo`.

## Performance

This removes the **client-side HTTP N+1** (one request instead of 1 + per-encounter-individual
+ per-partner). Server-side cost, however, is more than linear in the focal individual's
encounter count: `occurringWith` walks each focal occurrence's full encounter list
(`occ.getEncounters()`), and each relationship's `partner.canUserView` can scan every encounter
on that partner. To keep this bounded, the implementation **must memoize**:
- per-encounter `canUserView` results (an encounter may recur across the focal individual's
  occurrences and as a companion);
- per-occurrence the computed set of viewable companion individuals;
- per-partner-individual the `canUserView` result (reused across multiple relationships).

All work stays inside the single managed transaction; no new unbounded query is introduced.
The large-individual test (below) must cover a focal individual with **large occurrences**
(many co-occurring encounters) **and** relationships whose **partners have many encounters**,
to exercise these paths.

## Testing

JUnit 5 + Testcontainers, under `src/test/java/org/ecocean/api/`:

1. **Per-encounter ACL** — individual with encounters owned by different users: a non-admin
   collaborator sees only viewable encounters; an admin sees all; counts match the filter.
2. **Co-occurrence ACL** — an occurrence containing a non-viewable companion: that companion
   does NOT appear in any encounter row's `occurringWith`; a viewable companion does.
3. **Individual gate** — non-viewable individual → 403; viewable → 200.
4. **Relationship partner filter** — relationship to a non-viewable/unresolvable partner is
   omitted; to a viewable partner is included with the embedded partner block.
5. **Relationship `_id` round-trip** — the emitted `_id`, passed through the
   `persistenceID = _id + "[OID]org.ecocean.social.Relationship"` construction, resolves the
   same `Relationship` via the `getObjectById` path used by `RelationshipDelete`.
6. **dataTypes precedence** — (tissue+annotation), tissue-only, youtube-annotation,
   plain-annotation, neither → expected `dataTypes` value (matching legacy "any annotation").
7. **Bad input** — blank `id` → 400; unknown `id` → 404; anonymous → 401.
8. **Large individual** — a focal individual with large occurrences (many co-occurring
   encounters) and relationships whose partners have many encounters: completes within the
   single transaction and returns correct, ACL-filtered results (exercises the memoization
   paths).

Manual: open the Social Diagram on an individual; confirm both tables populate and that no
`/api/jdoql` or `/api/org.ecocean.*` request is issued (network tab); confirm edit/remove
buttons still resolve their relationship.

## Known residual exposure (documented, not fixed here)

The d3 **bubble-graph visualization** (`getData`/line 29) still sources companion individuals
from `occurrenceGraphJson.jsp`, which is not ACL-filtered. Per the agreed scope, the encounter
**table** is secured (its `occurringWith` now comes from the filtered endpoint), but the graph
viz retains this pre-existing co-occurrence exposure. Tracked as a follow-up; not described
further in public artifacts beyond this note.

## Dependencies / sequencing

Depends on `MarkedIndividual.canUserView` (PR #1633). This branch is stacked on
`fix/api-v3-individuals-get`; merge after #1633 lands (or rebase onto `main` once it does).

## Out-of-scope follow-ups

- Migrating the relationship **write** flow (585 + form) to `/api/v3`.
- Securing the bubble-graph companion feed (line 29).
- Retiring/locking down the generic `/api/jdoql` + `/api/{class}/{id}` RestServlet surface
  (tracked privately as a security item).
