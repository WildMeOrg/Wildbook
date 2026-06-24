# Social Diagram read-path migration to `/api/v3`

**Date:** 2026-06-24
**Branch:** `fix/social-diagram-api-v3-reads` (off `fix/api-v3-individuals-get`, which is PR #1633)
**Status:** design — awaiting spec review

## Problem

The legacy individual page `individuals.jsp` renders a **Social Diagram** tab whose
JavaScript (`src/main/webapp/javascript/bubbleDiagram/encounter-calls.js`) reads data
through the generic DataNucleus REST servlet (`/api/*`, `org.ecocean.servlet.RestServlet`):

| Line | Call | Feeds | Problem |
|------|------|-------|---------|
| 491 | `GET /api/jdoql?SELECT FROM MarkedIndividual WHERE individualID=="…"` | encounter table | **StackOverflowError** — the recursive POJO serializer loops on the `User`↔`Organization` bidirectional reference cycle. Also bypasses the collaboration/role ACL. |
| 655 | `GET /api/jdoql?SELECT FROM org.ecocean.social.Relationship WHERE …` | relationship table | same unsafe servlet |
| 703 | `GET /api/org.ecocean.MarkedIndividual/{id}` (per partner) | partner display in relationship table | same recursive serializer; N+1 |

The `/api/jdoql` MarkedIndividual query is the exact request that produced the reported
`StackOverflowError`.

A separate, already-merged change (PR #1633) added a safe, ACL-aware
`GET /api/v3/individuals/{id}`. However, that endpoint returns the **OpenSearch document**
shape — individual scalars, `socialUnits[]`, `relationships[]` (partner summary only), and
**encounter aggregates only** (`encounterIds[]`, counts, co-occurrence map). It exposes
**no per-encounter detail** (date, location, catalogNumber, tissue/annotation indicators,
behavior, per-encounter sex), so it cannot directly feed the encounter table.

## Scope

**Reads only.** Migrate the three crashing/unsafe read calls (491, 655, 703).

Out of scope (left on the legacy API for a separate effort):
- Co-occurrence table — line 29, `GET /encounters/occurrenceGraphJson.jsp` (a dedicated
  JSP, not the crashing servlet).
- Relationship **write** flow — line 585 (`getRelationshipData`, populates the edit form)
  and the add/edit/remove relationship form. These POST/PATCH/DELETE through the legacy
  API and have no `/api/v3` equivalent yet.

## Approach

Option B from brainstorming: **one dedicated, server-assembled read endpoint** (chosen to
avoid an N+1 over `/api/v3/encounters/{id}`, since Sharkbook individuals routinely have
large encounter counts). Server-side assembly is also what makes per-encounter ACL
filtering enforceable.

### Endpoint

`GET /api/v3/individuals/info/social-data?id={individualId}`

New `args[2] == "social-data"` branch in `org.ecocean.api.MarkedIndividualInfo.doGet`.
This servlet already owns `/api/v3/individuals/info/*` (so no routing collision with
`BaseObject`'s `/api/v3/individuals/*`), already returns `401` for an anonymous caller,
and already runs inside a managed Shepherd transaction. No change to web.xml. No change to
any OpenSearch serializer (so no reindex).

### Security model

1. **Caller auth** — anonymous → `401` (inherited from `MarkedIndividualInfo`).
2. **Individual gate** — load the individual by id; if
   `individual.canUserView(currentUser, myShepherd)` is false → `403`. Uses the
   `MarkedIndividual.canUserView` added in PR #1633 (admin, or can view ≥1 encounter).
3. **Per-encounter filter (primary requirement)** — iterate `individual.getEncounters()`
   and include an encounter row **only if `enc.canUserView(currentUser, myShepherd)`**
   (admin / owner / collaboration via `Collaboration.canUserAccessEncounter`). Non-viewable
   encounters are dropped before serialization — never written to the response. This closes
   the legacy behavior where `/api/jdoql` returned every encounter regardless of ACL.
4. **Relationship partner filter** — include a relationship row only if its partner
   `MarkedIndividual` is viewable by the caller (`partner.canUserView`). Prevents leaking a
   partner's identity across a collaboration boundary. A relationship whose partner is not
   resolvable/viewable is omitted.

### Response shape

Field names mirror what `encounter-calls.js` already consumes, to keep the JS rewrite small.

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
      "dataTypes": "both"
    }
  ],
  "relationships": [
    {
      "_id": "relationship-uuid",
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

- `dataTypes` is computed server-side using the exact precedence currently in the JS:
  `both` (tissue samples AND non-trivial annotations) → tissue sample `type` (tissue only)
  → `youtube-image` (annotations whose `eventID` contains "youtube") → `image` (annotations)
  → `""`.
- `year` / `month` / `day` / `dateInMilliseconds` are passed through so the JS keeps its
  existing date-precision formatting unchanged.
- `partner` is embedded so the per-partner fetch (line 703) is removed entirely.

### Server assembly

A private method in `MarkedIndividualInfo` builds the payload from domain getters
(no OpenSearch dependency):

- Encounters: `enc.getCatalogNumber()`, `enc.getDateInMilliseconds()`, `getYear/getMonth/getDay`,
  `getLocationID()`, `getOccurrenceID()`, `getSex()`, `getBehavior()`, `getAlternateID()`,
  `getTissueSamples()`, `getAnnotations()` (non-trivial count), `getEventID()` (for `dataTypes`).
- Relationships: `myShepherd.getAllRelationshipsForMarkedIndividual(id)`, then per relationship
  the `Relationship` getters plus the partner `MarkedIndividual` (`getOtherMarkedIndividual`/
  the non-self name) for the embedded partner block.

### JavaScript changes (`encounter-calls.js`)

- `getEncounterTableData` (491): one `d3.json` GET to the new endpoint; build the table from
  `response.encounters`. The `occurringWith` cross-referencing against
  `occurrenceObjectArray` (from the co-occurrence JSP) is preserved; only the data source for
  encounter rows changes.
- `getRelationshipTableData` (655): build the table from `response.relationships`; partner
  display read from the embedded `partner` object.
- `getIndividualData` (703): **removed** — partner detail now arrives embedded.
- Untouched: `getData`/line 29 (co-occurrence JSP), `getRelationshipData`/line 585 (edit-form
  write path), the add/edit/remove form handlers.

To avoid double-fetching, the response (which carries both `encounters` and `relationships`)
may be fetched once per diagram load and cached in a module variable, with both table
builders reading from it. Exact caching detail deferred to the implementation plan.

## Error handling

| Condition | Status |
|-----------|--------|
| anonymous caller | 401 |
| missing/blank `id` | 400 |
| individual not found | 404 |
| individual not viewable | 403 |
| success | 200 |

All via the existing `statusCode` JSON convention in `MarkedIndividualInfo`.

## Testing

JUnit 5 + Testcontainers, in `src/test/java/org/ecocean/api/` (or the closest existing
home for this servlet's tests):

1. **Per-encounter ACL** — an individual with encounters owned by different users: a
   non-admin collaborator sees only the encounters they may view; an admin sees all;
   counts match the filter.
2. **Individual gate** — a non-viewable individual returns 403; a viewable one returns 200.
3. **Relationship partner filter** — a relationship to a non-viewable partner is omitted;
   to a viewable partner is included with the embedded partner block.
4. **dataTypes precedence** — encounters with (tissue+annotation), tissue-only,
   youtube-annotation, plain-annotation, and neither map to the expected `dataTypes` value.
5. **Bad input** — blank `id` → 400; unknown `id` → 404; anonymous → 401.

Manual verification: open the Social Diagram on an individual; confirm both tables populate
and that no `/api/jdoql` or `/api/org.ecocean.*` request is issued (network tab).

## Dependencies / sequencing

Depends on `MarkedIndividual.canUserView` (PR #1633). This branch is stacked on
`fix/api-v3-individuals-get`; it should merge after #1633 (or be rebased onto `main` once
#1633 lands).

## Out-of-scope follow-ups (noted, not done here)

- Migrating the relationship **write** flow (585 + form) to `/api/v3` endpoints.
- Retiring / locking down the generic `/api/jdoql` + `/api/{class}/{id}` RestServlet surface
  (tracked privately as a security item).
