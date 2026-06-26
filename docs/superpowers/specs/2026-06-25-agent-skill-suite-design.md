# Agent-Skill Suite — Design

**Date:** 2026-06-25
**Status:** Approved (brainstorm); Codex design review incorporated (1 round, 8 findings: annotation
index lacks `individualId` → media/resolve join; bounded scope; self-match exclusion; keep both
servlet mappings; stricter name validation + fixed map; fuller drift invariant; wider jargon guard;
response-header hardening). Pending implementation plan.
**Branch:** `feature/agent-skill-suite`
**Relates to:** PR #1619 (token UI + single agent-skill), `org.ecocean.api.AgentSkill`, `org.ecocean.api.SearchApi`

## Summary

Wildbook currently serves a single curated agent skill at `GET /api/v3/agent-skill`
(`org.ecocean.api.AgentSkill` → classpath `/agent-skill.md`). This design grows that one
document into a **suite of skills served from the same single URL**, so a user can paste one link
to their AI assistant and that assistant can then pull whichever skill matches the user's task.

The suite teaches an AI assistant to operate Wildbook's read-only token API to help with
**re-identification reconciliation** — finding the same animal recorded twice, finding sightings
filed under the wrong animal, and judging how reliable the automatic matching is. The methods are
adapted from the open `curation-reid` project, reframed to run live against the API rather than a
local copy of the catalog.

## Audience and voice (the governing constraint)

These skills are for **field ecologists, terrestrial and marine biologists, resource managers, and
scientists — not programmers.** No ML/CS jargon in anything the user hears.

**Two audiences, one document.** Each skill is read by two parties:

- **The AI assistant** reads the instructions and needs them precise enough to compute correctly.
- **The biologist** never reads the skill file — they talk to the assistant in their own words and
  read its answers.

So: a skill's *method* section may name techniques precisely (the assistant needs that), but the
skill's *triggers* must match how a biologist asks, and everything the assistant *says back* must be
in conservation terms. The structural split below (a "How to do it" section vs. a "How to report
results" section) enforces this.

**Jargon → plain mapping** (every skill carries this; the assistant must follow it when speaking):

| Don't say | Say |
|---|---|
| embedding / vector / latent space | the pattern of the animal's markings |
| cluster / centroid | a group of photos that look like the same animal / its typical look |
| cosine distance, suspicion ratio | how closely two animals match (as a confidence) |
| false merge / false split | two animals recorded as one / one animal recorded as two |
| rank-1, mAP, BCubed | "the right animal came up first X% of the time" |
| annotation / individual / encounter | marked animal in a photo / catalog animal / sighting |
| query vs. gallery | the photo you're checking vs. your catalog |

## Assumptions

- **Code-capable assistant.** The analytical skills require comparing many numeric marking-patterns
  (similarities, grouping, ranking). The skills assume the user's assistant can write and run code
  in a sandbox (e.g. Claude Code, or an assistant with a code-execution tool). Skills instruct it to
  fetch the data via the API, then compute in code.
- **Read-only.** The token API exposes search + media resolve only. Every skill *detects and
  recommends*; none can apply a merge/split. Each ends in a worklist the user actions in the
  Wildbook UI. The skills must never claim to have changed the catalog.
- **The identity label lives on media/resolve, not the annotation search.** Verified against the
  code: the `annotation` index serializes `id`, `encounterId`, `viewpoint`, `iaClass`,
  `matchAgainst`, `mediaAssetId`, and `embeddings` — but **not** `individualId`. `POST
  /api/v3/media/resolve` returns `individualId` (and `encounterId`, `viewpoint`, `bbox`, `imageUrl`,
  `methodVersion`) for up to 100 annotation IDs per call. So every analytical skill's data-gathering
  is a two-step join: search `annotation` for the marking-pattern vectors + `encounterId` +
  `viewpoint` + `methodVersion`, then `media/resolve` those annotation IDs (≤100/call) to attach the
  catalog-animal label and the crop. (Equivalent fallback: search `encounter` by `encounterId` to
  read `individualId`.)
- **Bounded scope, not whole-catalog.** The token search accepts only `from`/`size` (no
  scroll/search-after), so it is subject to OpenSearch's `max_result_window`, and `media/resolve`
  caps at 100 IDs/call. Skills must therefore require a narrowed scope (species + site and/or date
  range), read `X-Wildbook-Total-Hits` up front, and stop and ask the user to narrow when the count
  exceeds a documented threshold. Skills must not promise whole-catalog comparisons.
- **Anonymous docs.** Skill documents contain no secrets (they are how-to text). Real data access
  still requires the user's bearer token. Docs must not name internal access-control field names.

## Architecture

### Delivery: index + on-demand per-skill (single URL)

One `web.xml` mapping; the bare URL returns a static curated index, and a path segment selects one
skill. Progressive disclosure: the assistant loads only the skill the user's task needs.

| Request | Serves |
|---|---|
| `GET /api/v3/agent-skill` | `index.md` |
| `GET /api/v3/agent-skill/<name>` | `<name>.md` (name in the allow-list) |
| `GET /api/v3/agent-skill/<unknown>` | `404` plain text |

### Resource layout

Flat, git-tracked, version-controlled markdown (same model as today's single file):

```
src/main/resources/agent-skills/
  index.md                  # single-URL entry; embeds auth basics + skill catalog
  api-reference.md          # detailed token/endpoints/schema (≈ today's agent-skill.md)
  find-missed-matches.md
  find-misfiled-sightings.md
  how-good-is-our-matching.md
  review-id-problems.md
```

### Servlet behavior (`org.ecocean.api.AgentSkill`)

- Read `getPathInfo()`. If it is `null` or `"/"` → serve `index.md` (the default-only payload;
  `index` is **not** a fetchable name, so `/api/v3/agent-skill/index` → 404).
- Otherwise require it to match **exactly one segment**: `^/[a-z0-9-]+$`. Anything else (extra
  segments, `.md` suffix, path params, mixed case, encoded slashes/dots, null bytes) → `404`.
- Map the validated segment through a **fixed `Map<String,String>` (name → exact classpath
  resource)** — the single source of truth for the suite. The name is **never** concatenated into a
  resource path; it only keys this map. Keys are exactly the fetchable skills:
  `{ api-reference, find-missed-matches, find-misfiled-sightings, how-good-is-our-matching,
  review-id-problems }` (every packaged file under `agent-skills/` **except** `index.md`). A miss →
  `404`. This removes any path-traversal / arbitrary-resource-disclosure surface.
- Unknown name → `404` plain text. Mapped name whose resource is somehow missing → `500` plain text
  (as the servlet does today). Success → `200`, `text/markdown; charset=UTF-8`.
- **Response headers** (cheap hardening): `X-Content-Type-Options: nosniff`, a sensible
  `Cache-Control` (these are static version-controlled docs), and `ETag`/`Last-Modified` for
  conditional GETs.
- Remains anonymous (no token required).

### Two infra changes (load-bearing)

1. **web.xml** — **keep** the exact `url-pattern` `/api/v3/agent-skill` **and add** a second
   `url-pattern` `/api/v3/agent-skill/*` on the same `AgentSkill` servlet. (Keeping both rather than
   replacing the exact mapping avoids relying on bare-path-matches-prefix behavior — this project
   has prior history of Shiro/Tomcat path-matching edge cases that abandoned a `/api/**` web.xml
   approach.) For the exact mapping `getPathInfo()` is `null` → index; for the prefix mapping it is
   the `/segment`.
2. **Shiro `[urls]`** — Shiro is first-match. Keep the existing `/api/v3/agent-skill = anon` and add
   `/api/v3/agent-skill/** = anon` directly adjacent to it, or subpaths fall through to the default
   auth filter.

### Migration / backward compatibility

Today the bare URL returns the full API how-to. After this change it returns the **index**, which
embeds condensed auth basics inline (so a single fetch still teaches a user's assistant how to get
and use a token). The detailed how-to moves to `api-reference.md`. The feature is ~2 weeks live and
nothing in-repo consumes the old payload, so changing the bare-URL body is acceptable.

## Content design

### `index.md` (the single-URL payload)

Plain-language and browser-legible. Structure:

1. **Title + one plain paragraph** — what these tools help you do, and that everything is read-only
   (the tools only suggest; the user makes changes in Wildbook).
2. **What you'll need** — condensed auth basics: get a token from the Wildbook account menu → API
   Access; paste only the token; it expires (~30 min); see `api-reference` for full detail.
3. **The toolbox** — a short table, one row per skill: plain name · "use this when you want to…"
   (the user's own words) · the fetch URL.
4. **How this works** — one line telling the assistant to fetch the matching skill on demand.

### Shared skill template

Each analytical skill is a markdown file with frontmatter (`name`, plain-language `description`) and
these six sections:

| Section | Purpose |
|---|---|
| **When to use this** | The user phrasings that trigger it ("did we record the same animal twice?"). |
| **What it does, in plain terms** | Conservation framing, no jargon, plus the "speak plainly" reminder + jargon→plain table. |
| **What you'll need** | A token; which searches/scope (species, site, date range). |
| **How to do it** | The rigorous method for the code-capable assistant: which API calls, what to compute, the same-viewpoint + same-methodVersion rule, the calibration-against-known-pairs step, thresholds. May name techniques precisely. |
| **How to report results** | Plain language + percentages; side-by-side crops via media/resolve; read-only ⇒ produce a worklist; never claim to have changed the catalog. |
| **Cautions** | Don't compare across viewpoints/versions; small samples are unreliable; you only ever see data the user's account permits. |

### The four analytical skills

**Shared method rules** (apply to all four; the per-skill descriptions assume them):

- **Data join.** Search `annotation` (scoped, paged with `from`/`size`) for the marking-pattern
  vectors + `encounterId` + `viewpoint` + `methodVersion`, then `media/resolve` the annotation IDs
  (≤100/call) to attach `individualId` and the crop. `individualId` is **not** on the annotation
  index (see Assumptions).
- **Partition strictly by viewpoint + methodVersion.** Never compare marking-patterns across
  different viewpoints or model versions — they live in different spaces.
- **Calibrate** a similarity cutoff against known same-animal pairs before trusting any score.
- **Exclude self and same-sighting.** Every fit/ranking computation must exclude the photo being
  judged *and* all other photos from the same sighting (encounter); otherwise the own-animal score
  is trivially perfect (self-match bias) or inflated by easy same-session lookalikes. Require a
  minimum number of independent reference photos before scoring an animal.
- **Stay bounded.** Read `X-Wildbook-Total-Hits` first; if the scope exceeds the documented
  threshold, stop and ask the user to narrow (species + site and/or date range). No whole-catalog
  promises.

All are read-only and end in a human worklist.

1. **`find-missed-matches`** — *the same animal recorded under two names, or one name covering two
   animals.* Compare each catalog animal's photos against every other's. Surface (a) two **different**
   catalog animals whose photos match closely → likely the same individual recorded twice (missed
   match / duplicate); (b) one catalog animal whose photos fall into two visually distant groups → a
   possible mix-up. Output: ranked merge/split candidates with side-by-side crops.

2. **`find-misfiled-sightings`** — *a sighting filed under the wrong animal.* For each marked animal
   in a photo, compare how well it fits **its own** catalog animal vs. the **closest other** catalog
   animal; flag where another animal fits clearly better. (The suspicion-ratio idea from
   curation-reid, in plain terms.) Output: flagged sightings shown beside both the current and the
   suspected-correct animal.

3. **`how-good-is-our-matching`** — *reliability of automatic matching for a species/site.* Hold out
   each photo of a known animal and check how often the correct animal comes back first, and within
   the top five — **excluding other photos from the same sighting** so numbers aren't inflated by
   easy same-session lookalikes. Output: plain percentages with sample size and caveats.

4. **`review-id-problems`** — *the human-in-the-loop presenter.* Takes a worklist from the other
   three, shows photos side by side, asks the user to confirm or reject each, and applies simple
   consistency closure (if A=B and B=C, propose A=C). Output: a clean, deduplicated to-do list the
   catalog manager actions in Wildbook. Makes no changes.

## Error handling

- No path segment → index.
- Unknown name / traversal attempt (`../`, encoded variants) → `404` plain text.
- Allow-listed name with missing packaged resource → `500` plain text.
- All responses `charset=UTF-8`; success is `text/markdown`, errors are `text/plain`.

## Testing

- **`AgentSkillTest`** (extends the existing test): bare path → 200 index; trailing slash `/` → 200
  index; each mapped name → 200 `text/markdown`; anon (no token) works; `nosniff` header present.
  **Edge cases → 404:** unknown name; `index`; `.md` suffix; mixed case; extra segment; double
  slash; encoded slash `%2f`; encoded dots `%2e%2e`; trailing dot; path params; null byte `%00`.
  If feasible, exercise these at container level (bare path, trailing slash, subpath, unknown,
  encoded slash) to catch Tomcat/Shiro path-matching behavior, not just the servlet method.
- **Drift-guard test** (mirrors the existing `TOKEN_ALLOWED_INDICES` drift test) — the servlet's
  `Map<String,String>` is the single source of truth. Assert a three-way invariant: (a) packaged
  resources under `agent-skills/` == `index.md` + `map.values()`; (b) every mapped name resolves to
  a non-empty resource and equals that file's frontmatter `name` (name == file stem); (c) `index.md`
  links exactly the four analytical skills in its toolbox and links `api-reference` from the
  "What you'll need" section. No orphans, no missing files, no unlisted skill, no broken fetch URL.
- **Leak-guard test**: no served document contains the internal access-control field names.
- **Jargon-guard test** over the user-facing sections of **every** skill — frontmatter
  (`name`/`description`), "When to use this", "What it does, in plain terms", "How to report
  results", and "Cautions" — asserting absence of a jargon blacklist (`embedding`, `vector`,
  `cosine`, `centroid`, `cluster`, …). **Exempt only** the "How to do it" section (which must name
  techniques precisely for the assistant) and the "Don't say" column of the jargon table. A manual
  read-aloud check remains a review-checklist item for tone the test can't catch.

## Out of scope (deferred)

- **Downloadable zip bundle** (`?format=zip` in Agent-Skills layout) — deferred (YAGNI); a pasted
  URL covers the field-biologist audience. Add later if a technical-install audience appears.
- **Server-side analysis endpoints** — the analysis stays assistant-side over the existing
  read-only search/resolve API; no new data endpoints.
- **Any write path** — the API is read-only by design; skills recommend, the user actions.

## Open considerations

- The four analytical skills cannot be fully validated until tested against a live instance with
  real marking-patterns; their thresholds/calibration guidance will likely need a tuning pass after
  first live use. The spec fixes their *structure and method*; numeric defaults are provisional.
- Codex review of this design and of the final content/code is expected before merge (per project
  practice), with particular attention to the plain-language constraint and the leak/drift guards.
