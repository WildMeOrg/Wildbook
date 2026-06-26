# Agent-Skill Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Grow the single `GET /api/v3/agent-skill` document into a suite of plain-language skills served from the same single URL, teaching a field biologist's AI assistant to use the read-only token API for re-ID reconciliation.

**Architecture:** One anonymous servlet (`org.ecocean.api.AgentSkill`) keeps serving the bare URL (now an `index.md`) and additionally serves one skill per path segment (`/api/v3/agent-skill/<name>`) selected through a fixed in-code `Map<String,String>` (the single source of truth). Skill bodies are static, version-controlled markdown classpath resources under `/agent-skills/`. The four analytical skills are instructions for a code-capable assistant operating the existing `POST /api/v3/search/*` + `POST /api/v3/media/resolve` endpoints; no new data endpoints, all read-only.

**Tech Stack:** Java 11, Servlet API (`javax.servlet`), JUnit 5 + Mockito (existing test stack), Maven. Markdown resources. web.xml servlet mapping + Shiro `[urls]`.

## Global Constraints

- **Audience = field ecologists / biologists / resource managers, not programmers.** No ML/CS jargon in any user-facing text (`index.md`, every skill's frontmatter, "When to use this", "What it does, in plain terms", "How to report results", "Cautions"). Jargon is permitted ONLY in each skill's "How to do it" section and the "Don't say" column of the jargon table. Automated jargon blacklist (case-insensitive substring): `embedding`, `vector`, `cosine`, `centroid`, `cluster`, `latent`, `bcubed`. (Metric jargon like `mAP`/`rank-1` is NOT auto-scanned — lowercased `map` collides with the ordinary word "map" — and is caught in the manual voice review instead.)
- **Read-only.** Skills detect and recommend only; they end in a human worklist and must never claim to have changed the catalog.
- **Identity label join.** `individualId` is NOT on the `annotation` index. Get it (and the crop) from `POST /api/v3/media/resolve` (≤100 annotation IDs/call), or from `encounter` search by `encounterId`.
- **Paging + hard ceiling.** Search is offset paging via `?from=&size=`; total is in the `X-Wildbook-Total-Hits` header; `from + size` must stay **≤ 10,000** (`max_result_window`); there is **no** scroll/search_after, so >10k result sets cannot be fully walked; pages are independent (not a frozen snapshot).
- **Stay anonymous; no secrets in docs; never name internal ACL fields** (`publiclyReadable`, `submitterUserId`, `submitterUserIds`, `viewUsers`, `editUsers`).
- **Resource path safety.** The skill name is matched `^[a-z0-9-]+$` and used ONLY as a key into the fixed map — never concatenated into a resource path.
- **Repo hygiene:** normalize line endings to LF before staging (`sed -i 's/\r$//' <file>`); JUnit 5 assertion message goes LAST.

---

## File Structure

**Created:**
- `src/main/resources/agent-skills/index.md` — single-URL entry: plain intro + auth basics + toolbox.
- `src/main/resources/agent-skills/api-reference.md` — detailed token/endpoints/schema + paging contract (migrated from the old single file).
- `src/main/resources/agent-skills/find-missed-matches.md`
- `src/main/resources/agent-skills/find-misfiled-sightings.md`
- `src/main/resources/agent-skills/how-good-is-our-matching.md`
- `src/main/resources/agent-skills/review-id-problems.md`
- `src/test/java/org/ecocean/api/AgentSkillContentTest.java` — content guards (structure, jargon, leak) + shared helpers.

**Modified:**
- `src/main/java/org/ecocean/api/AgentSkill.java` — routing, validation, fixed map, headers.
- `src/main/webapp/WEB-INF/web.xml` — add second url-pattern; add Shiro anon subpath rule.
- `src/test/java/org/ecocean/api/AgentSkillTest.java` — retarget bare-URL assertions to `api-reference`; add routing/edge/header tests.

**Deleted:**
- `src/main/resources/agent-skill.md` — content migrated to `api-reference.md` (in Task 7, when the servlet stops serving it).

---

## Task 1: Content-test helpers + `api-reference.md`

**Files:**
- Create: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`
- Create: `src/main/resources/agent-skills/api-reference.md`

**Interfaces:**
- Produces: `AgentSkillContentTest.load(String classpathPath)` → `String` (reads a classpath resource, fails the test if absent); `AgentSkillContentTest.JARGON` (`String[]`), `AgentSkillContentTest.ACL_FIELDS` (`String[]`); `AgentSkillContentTest.assertNoLeak(String md)`; `AgentSkillContentTest.assertNoJargon(String userFacingText)`; `AgentSkillContentTest.userFacingSections(String md)` → `String` (the markdown with the "## How to do it" section and the "Don't say" table column removed). Later content tasks consume these.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/ecocean/api/AgentSkillContentTest.java`:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AgentSkillContentTest {

    static final String[] JARGON = {
        "embedding", "vector", "cosine", "centroid", "cluster", "latent", "bcubed"
    };
    static final String[] ACL_FIELDS = {
        "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"
    };

    static String load(String classpathPath) {
        try (InputStream in = AgentSkillContentTest.class.getResourceAsStream(classpathPath)) {
            assertNotNull(in, "resource must exist on classpath: " + classpathPath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return fail("could not read " + classpathPath + ": " + e);
        }
    }

    static void assertNoLeak(String md) {
        for (String acl : ACL_FIELDS)
            assertFalse(md.contains(acl), "must not expose internal ACL field name " + acl);
    }

    // Strips the "## How to do it" section (up to the next "## ") and the markdown jargon table,
    // leaving only the text the assistant may read aloud to a biologist.
    static String userFacingSections(String md) {
        String stripped = md.replaceAll("(?is)## How to do it.*?(?=\\n## |\\z)", "");
        // remove any markdown table rows (the jargon table's "Don't say" column lives here)
        stripped = stripped.replaceAll("(?m)^\\|.*\\|\\s*$", "");
        return stripped;
    }

    static void assertNoJargon(String userFacingText) {
        String lower = userFacingText.toLowerCase();
        for (String j : JARGON)
            assertFalse(lower.contains(j), "user-facing text must not contain jargon term: " + j);
    }

    @Test void api_reference_has_auth_schema_and_paging_contract() {
        String md = load("/agent-skills/api-reference.md");
        assertFalse(md.isEmpty(), "api-reference must be non-empty");
        assertTrue(md.contains("Authorization: Bearer"), "documents bearer auth");
        assertTrue(md.contains("/api/v3/search/"), "documents search");
        assertTrue(md.contains("/api/v3/media/resolve"), "documents media resolve");
        assertTrue(md.contains("X-Wildbook-Total-Hits"), "documents the total-hits header");
        assertTrue(md.contains("10,000") || md.contains("10000"), "documents the 10k window ceiling");
        assertTrue(md.contains("from") && md.contains("size"), "documents from/size paging");
        assertTrue(md.contains("100"), "documents the media/resolve 100-ID cap");
        for (String idx : new String[] {"encounter", "individual", "annotation"})
            assertTrue(md.contains(idx), "names allowed index " + idx);
        assertTrue(md.contains("403"), "states denied indices return 403");
        assertNoLeak(md);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=AgentSkillContentTest#api_reference_has_auth_schema_and_paging_contract`
Expected: FAIL — `resource must exist on classpath: /agent-skills/api-reference.md`.

- [ ] **Step 3: Create `api-reference.md`**

Copy the entire current `src/main/resources/agent-skill.md` into `src/main/resources/agent-skills/api-reference.md` (preserving its auth/security/endpoints/schema/worked-examples content), then add a `## Paging and limits` section so the paging-contract assertions pass. The new section must contain:

```markdown
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
```

- [ ] **Step 4: Normalize line endings**

Run: `sed -i 's/\r$//' src/main/resources/agent-skills/api-reference.md`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o test -Dtest=AgentSkillContentTest#api_reference_has_auth_schema_and_paging_contract`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/org/ecocean/api/AgentSkillContentTest.java src/main/resources/agent-skills/api-reference.md
git commit -m "feat(agent-skill): add api-reference doc with paging contract + content guards"
```

---

## Task 2: `index.md` (the single-URL entry page)

**Files:**
- Create: `src/main/resources/agent-skills/index.md`
- Modify: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`

**Interfaces:**
- Consumes: `load`, `assertNoLeak`, `assertNoJargon`, `userFacingSections` from Task 1.
- Produces: `index.md` listing exactly the four analytical skills by name and linking `api-reference`.

- [ ] **Step 1: Write the failing test**

Add to `AgentSkillContentTest`:

```java
@Test void index_is_plain_language_and_lists_the_four_tools() {
    String md = load("/agent-skills/index.md");
    assertFalse(md.isEmpty(), "index must be non-empty");
    for (String name : new String[] {
            "find-missed-matches", "find-misfiled-sightings",
            "how-good-is-our-matching", "review-id-problems" })
        assertTrue(md.contains(name), "index toolbox must list " + name);
    assertTrue(md.contains("api-reference"), "index must point to api-reference for API detail");
    assertTrue(md.toLowerCase().contains("read-only")
            || md.toLowerCase().contains("only suggest")
            || md.toLowerCase().contains("you make the changes"),
        "index must state the tools are read-only");
    assertTrue(md.toLowerCase().contains("api access") || md.toLowerCase().contains("token"),
        "index must explain getting a token");
    assertNoLeak(md);
    assertNoJargon(userFacingSections(md));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=AgentSkillContentTest#index_is_plain_language_and_lists_the_four_tools`
Expected: FAIL — resource missing.

- [ ] **Step 3: Create `index.md`**

Write `src/main/resources/agent-skills/index.md` in plain language. Required structure and content:

```markdown
# Wildbook Catalog Helper — Toolbox

These tools help you check and tidy up your animal photo-ID catalog: spot the same animal recorded
twice, find sightings filed under the wrong animal, and judge how reliable the automatic matching
is. Everything here is **read-only** — the tools only ever suggest; you make the changes in Wildbook.

## What you'll need

A short-lived access token from Wildbook. In Wildbook, open your account menu and choose
**API Access** to create one, then paste **only that token** to your assistant — never your username
or password. The token expires after about 30 minutes; create a fresh one when it stops working.
Full technical detail is in the **api-reference** page (fetch `/api/v3/agent-skill/api-reference`).

## The toolbox

| Tool | Use this when you want to… | Fetch |
|---|---|---|
| find-missed-matches | check whether the same animal was recorded twice under different names | `/api/v3/agent-skill/find-missed-matches` |
| find-misfiled-sightings | check whether any sightings are filed under the wrong animal | `/api/v3/agent-skill/find-misfiled-sightings` |
| how-good-is-our-matching | understand how reliable the automatic matching is for a species or site | `/api/v3/agent-skill/how-good-is-our-matching` |
| review-id-problems | go through suspected ID problems photo-by-photo and build a to-do list | `/api/v3/agent-skill/review-id-problems` |

## How this works

When the person you're helping describes one of the tasks above, fetch that tool's page and follow
it. Each page tells you exactly which catalog information to pull and how to present what you find.
```

Note: the toolbox table lines are stripped by `userFacingSections`, so the jargon check passes even though the table is user-visible — the table here contains no jargon anyway. Keep the prose plain.

- [ ] **Step 4: Normalize line endings**

Run: `sed -i 's/\r$//' src/main/resources/agent-skills/index.md`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o test -Dtest=AgentSkillContentTest#index_is_plain_language_and_lists_the_four_tools`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/agent-skills/index.md src/test/java/org/ecocean/api/AgentSkillContentTest.java
git commit -m "feat(agent-skill): add plain-language index/toolbox page"
```

---

## Task 3: `find-missed-matches.md`

**Files:**
- Create: `src/main/resources/agent-skills/find-missed-matches.md`
- Modify: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`

**Interfaces:**
- Consumes: `load`, `assertNoLeak`, `assertNoJargon`, `userFacingSections`, plus a new shared helper `assertSkillStructure` (defined in this task, reused by Tasks 4–6).

- [ ] **Step 1: Write the failing test + shared structure helper**

Add to `AgentSkillContentTest`:

```java
static final String[] REQUIRED_SECTIONS = {
    "## When to use this",
    "## What it does, in plain terms",
    "## What you'll need",
    "## How to do it",
    "## How to report results",
    "## Cautions"
};

// Validates the shared skill template: frontmatter name == file stem, all six sections present,
// no ACL leak, no jargon outside "How to do it"/table, and the read-only worklist promise.
static void assertSkillStructure(String stem) {
    String md = load("/agent-skills/" + stem + ".md");
    assertFalse(md.isEmpty(), stem + " must be non-empty");
    assertTrue(md.contains("name: " + stem),
        stem + " frontmatter name must equal the file stem");
    for (String s : REQUIRED_SECTIONS)
        assertTrue(md.contains(s), stem + " must contain section " + s);
    assertTrue(md.toLowerCase().contains("read-only")
            || md.toLowerCase().contains("only suggest")
            || md.toLowerCase().contains("worklist")
            || md.toLowerCase().contains("to-do"),
        stem + " must state it is read-only / produces a worklist");
    assertNoLeak(md);
    assertNoJargon(userFacingSections(md));
}

@Test void find_missed_matches_is_well_formed() {
    assertSkillStructure("find-missed-matches");
    String md = load("/agent-skills/find-missed-matches.md");
    assertTrue(md.contains("/api/v3/search/annotation"), "names the annotation search");
    assertTrue(md.contains("/api/v3/media/resolve"), "names media resolve for the identity join");
    assertTrue(md.toLowerCase().contains("viewpoint") && md.toLowerCase().contains("methodversion"),
        "states the same-viewpoint + same-methodVersion rule");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=AgentSkillContentTest#find_missed_matches_is_well_formed`
Expected: FAIL — resource missing.

- [ ] **Step 3: Create `find-missed-matches.md`**

Write the file following the shared template. Frontmatter then the six sections. Required substance:

```markdown
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
   (`from + size ≤ 10,000`). Keep each photo's marking-pattern vector, `encounterId`, `viewpoint`,
   and `methodVersion`.
2. Resolve the annotation IDs with `POST /api/v3/media/resolve` (≤100 per call) to attach
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
```

Speak plainly throughout the user-facing sections (no jargon words from the blacklist).

- [ ] **Step 4: Normalize line endings**

Run: `sed -i 's/\r$//' src/main/resources/agent-skills/find-missed-matches.md`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o test -Dtest=AgentSkillContentTest#find_missed_matches_is_well_formed`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/agent-skills/find-missed-matches.md src/test/java/org/ecocean/api/AgentSkillContentTest.java
git commit -m "feat(agent-skill): add find-missed-matches skill"
```

---

## Task 4: `find-misfiled-sightings.md`

**Files:**
- Create: `src/main/resources/agent-skills/find-misfiled-sightings.md`
- Modify: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`

**Interfaces:**
- Consumes: `assertSkillStructure` (Task 3), `load`.

- [ ] **Step 1: Write the failing test**

Add to `AgentSkillContentTest`:

```java
@Test void find_misfiled_sightings_is_well_formed() {
    assertSkillStructure("find-misfiled-sightings");
    String md = load("/agent-skills/find-misfiled-sightings.md");
    assertTrue(md.contains("/api/v3/media/resolve"), "names media resolve for the identity join");
    assertTrue(md.toLowerCase().contains("exclude")
            && (md.toLowerCase().contains("same sighting")
                || md.toLowerCase().contains("same encounter")
                || md.toLowerCase().contains("itself")),
        "states the self / same-sighting exclusion rule");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=AgentSkillContentTest#find_misfiled_sightings_is_well_formed`
Expected: FAIL — resource missing.

- [ ] **Step 3: Create `find-misfiled-sightings.md`**

Follow the template. Frontmatter `name: find-misfiled-sightings`, plain `description`. Required substance for "How to do it":
1. Search `annotation` for the scope (paged), keep marking-pattern vector + `encounterId` + `viewpoint` + `methodVersion`; resolve IDs via `media/resolve` for `individualId` + crops.
2. Group by `viewpoint` + `methodVersion`; calibrate a cutoff against known same-animal pairs.
3. For each photo, measure how well it fits **its own** catalog animal versus the **closest other**
   catalog animal — **excluding the photo itself and all other photos from the same sighting**
   (encounter) from the own-animal measurement; require a minimum number of independent reference
   photos before scoring.
4. Flag photos that fit another animal clearly better than their own.
5. Rank by how much better the other animal fits.
"How to report results": show the flagged sighting beside both its current animal and the
suspected-correct animal; produce a worklist; never claim to have re-filed anything. "Cautions":
same viewpoint/version only; small samples unreliable; account-scoped visibility.

- [ ] **Step 4: Normalize line endings**

Run: `sed -i 's/\r$//' src/main/resources/agent-skills/find-misfiled-sightings.md`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o test -Dtest=AgentSkillContentTest#find_misfiled_sightings_is_well_formed`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/agent-skills/find-misfiled-sightings.md src/test/java/org/ecocean/api/AgentSkillContentTest.java
git commit -m "feat(agent-skill): add find-misfiled-sightings skill"
```

---

## Task 5: `how-good-is-our-matching.md`

**Files:**
- Create: `src/main/resources/agent-skills/how-good-is-our-matching.md`
- Modify: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`

**Interfaces:**
- Consumes: `assertSkillStructure` (Task 3), `load`.

- [ ] **Step 1: Write the failing test**

Add to `AgentSkillContentTest`:

```java
@Test void how_good_is_our_matching_is_well_formed() {
    assertSkillStructure("how-good-is-our-matching");
    String md = load("/agent-skills/how-good-is-our-matching.md");
    assertTrue(md.contains("%") || md.toLowerCase().contains("percent"),
        "reports reliability as a plain percentage");
    assertTrue(md.toLowerCase().contains("same sighting")
            || md.toLowerCase().contains("same encounter"),
        "states the same-sighting exclusion that prevents inflated numbers");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=AgentSkillContentTest#how_good_is_our_matching_is_well_formed`
Expected: FAIL — resource missing.

- [ ] **Step 3: Create `how-good-is-our-matching.md`**

Follow the template. Frontmatter `name: how-good-is-our-matching`. "How to do it": pull the scope's
photos (paged) + resolve for `individualId`; group by viewpoint/version; for each photo of a
known animal, hold it out and rank the catalog animals by similarity, **excluding other photos from
the same sighting**, and record whether the correct animal came back first and within the top five.
"How to report results": give plain percentages ("the right animal came up first 87% of the time,
and was in the top five 96% of the time"), state the sample size, and caveat low samples. "Cautions":
same viewpoint/version only; results are about *this* scope, not the whole catalog; account-scoped.

- [ ] **Step 4: Normalize line endings**

Run: `sed -i 's/\r$//' src/main/resources/agent-skills/how-good-is-our-matching.md`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o test -Dtest=AgentSkillContentTest#how_good_is_our_matching_is_well_formed`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/agent-skills/how-good-is-our-matching.md src/test/java/org/ecocean/api/AgentSkillContentTest.java
git commit -m "feat(agent-skill): add how-good-is-our-matching skill"
```

---

## Task 6: `review-id-problems.md`

**Files:**
- Create: `src/main/resources/agent-skills/review-id-problems.md`
- Modify: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`

**Interfaces:**
- Consumes: `assertSkillStructure` (Task 3), `load`.

- [ ] **Step 1: Write the failing test**

Add to `AgentSkillContentTest`:

```java
@Test void review_id_problems_is_well_formed() {
    assertSkillStructure("review-id-problems");
    String md = load("/agent-skills/review-id-problems.md");
    assertTrue(md.contains("/api/v3/media/resolve"), "uses media resolve to show photos");
    assertTrue(md.toLowerCase().contains("side by side") || md.toLowerCase().contains("side-by-side"),
        "presents photos side by side");
    assertTrue(md.toLowerCase().contains("to-do") || md.toLowerCase().contains("worklist")
            || md.toLowerCase().contains("export"),
        "produces an actionable to-do list");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o test -Dtest=AgentSkillContentTest#review_id_problems_is_well_formed`
Expected: FAIL — resource missing.

- [ ] **Step 3: Create `review-id-problems.md`**

Follow the template. Frontmatter `name: review-id-problems`. This is the human-in-the-loop presenter:
it takes a worklist from the other three tools, resolves the crops with `media/resolve`, shows the
photos side by side, asks the person to confirm or reject each, applies simple consistency closure
(if the person confirms A=B and B=C, also suggest A=C), and produces a clean, deduplicated to-do
list to action in Wildbook. "Cautions": it changes nothing; it only prepares a list. Account-scoped.

- [ ] **Step 4: Normalize line endings**

Run: `sed -i 's/\r$//' src/main/resources/agent-skills/review-id-problems.md`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -o test -Dtest=AgentSkillContentTest#review_id_problems_is_well_formed`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/agent-skills/review-id-problems.md src/test/java/org/ecocean/api/AgentSkillContentTest.java
git commit -m "feat(agent-skill): add review-id-problems skill"
```

---

## Task 7: Servlet routing, web.xml, Shiro + routing tests

**Files:**
- Modify: `src/main/java/org/ecocean/api/AgentSkill.java`
- Modify: `src/main/webapp/WEB-INF/web.xml:582-584` (servlet-mapping) and `:115` (Shiro `[urls]`)
- Modify: `src/test/java/org/ecocean/api/AgentSkillTest.java`
- Delete: `src/main/resources/agent-skill.md`

**Interfaces:**
- Produces: `static final Map<String,String> AgentSkill.SKILL_RESOURCES` (skill name → resource filename; the single source of truth, package-visible for the drift test); `static String AgentSkill.resolveResource(String pathInfo)` (returns the resource filename to serve, or `null` for 404; `null`/`"/"` → `index.md`).

- [ ] **Step 1: Rewrite the routing tests (fail first)**

Replace the body of `src/test/java/org/ecocean/api/AgentSkillTest.java` with:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class AgentSkillTest {

    // serve with a given pathInfo; return [status, contentType, body]
    private String[] serve(String pathInfo) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getPathInfo()).thenReturn(pathInfo);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        new AgentSkill().doGetForTest(req, resp);
        org.mockito.ArgumentCaptor<Integer> st = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(resp).setStatus(st.capture());
        org.mockito.ArgumentCaptor<String> ct = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resp).setContentType(ct.capture());
        return new String[] { String.valueOf(st.getValue()), ct.getValue(), out.toString() };
    }

    @Test void bare_path_serves_index() throws Exception {
        String[] r = serve(null);
        assertEquals("200", r[0], "bare path returns 200");
        assertEquals("text/markdown; charset=UTF-8", r[1]);
        assertTrue(r[2].contains("find-missed-matches"), "bare path serves the index/toolbox");
    }

    @Test void trailing_slash_serves_index() throws Exception {
        assertEquals("200", serve("/")[0], "trailing slash returns the index");
    }

    @Test void each_skill_name_serves_markdown() throws Exception {
        for (String name : AgentSkill.SKILL_RESOURCES.keySet()) {
            String[] r = serve("/" + name);
            assertEquals("200", r[0], name + " returns 200");
            assertEquals("text/markdown; charset=UTF-8", r[1], name + " is markdown");
            assertFalse(r[2].isEmpty(), name + " is non-empty");
        }
    }

    @Test void api_reference_carries_allowlist_and_no_leak() throws Exception {
        String md = serve("/api-reference")[2];
        for (String idx : SearchApi.TOKEN_ALLOWED_INDICES)
            assertTrue(md.contains(idx), "api-reference must list allowed index " + idx);
        assertTrue(md.contains("occurrence") && md.contains("media_asset"),
            "api-reference must name the denied indices");
        assertTrue(md.contains("403"), "api-reference must state denied indices return 403");
        assertTrue(md.contains("Authorization: Bearer"), "api-reference documents bearer auth");
        for (String acl : new String[] {
                "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"})
            assertFalse(md.contains(acl), "must not expose internal ACL field name " + acl);
    }

    @Test void unknown_and_malformed_names_return_404() throws Exception {
        for (String bad : new String[] {
                "/nope", "/index", "/api-reference.md", "/API-REFERENCE",
                "/find-missed-matches/extra", "//double", "/..", "/../secret",
                "/a%2fb", "/%2e%2e", "/with space", "/trailing/" }) {
            assertEquals("404", serve(bad)[0], bad + " must 404");
        }
    }

    @Test void success_sets_nosniff_header() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getPathInfo()).thenReturn(null);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        new AgentSkill().doGetForTest(req, resp);
        verify(resp).setHeader("X-Content-Type-Options", "nosniff");
    }
}
```

Note: `getPathInfo()` returns the URL-*decoded* path, so `%2f`→`/` and `%2e%2e`→`..` arrive as
literal slashes/dots and fail the `^[a-z0-9-]+$` check. The mock returns these decoded forms to
mirror that.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -o test -Dtest=AgentSkillTest`
Expected: FAIL — `AgentSkill.SKILL_RESOURCES` / `getPathInfo`-based routing not present yet
(compile error or assertion failures).

- [ ] **Step 3: Rewrite the servlet**

Replace `src/main/java/org/ecocean/api/AgentSkill.java` with:

```java
package org.ecocean.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * GET /api/v3/agent-skill            -> index.md (the toolbox)
 * GET /api/v3/agent-skill/<name>     -> that skill's markdown (name in SKILL_RESOURCES)
 *
 * Anonymous: these are how-to docs with no secrets. Real data access still requires a bearer token.
 * The name is validated (^[a-z0-9-]+$) and used only as a key into a fixed map — never concatenated
 * into a resource path.
 */
public class AgentSkill extends ApiBase {
    private static final String DIR = "/agent-skills/";
    private static final String INDEX = "index.md";
    private static final Pattern NAME = Pattern.compile("^[a-z0-9-]+$");

    // single source of truth: fetchable skill name -> resource filename (index.md is NOT fetchable)
    static final Map<String, String> SKILL_RESOURCES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("api-reference", "api-reference.md");
        m.put("find-missed-matches", "find-missed-matches.md");
        m.put("find-misfiled-sightings", "find-misfiled-sightings.md");
        m.put("how-good-is-our-matching", "how-good-is-our-matching.md");
        m.put("review-id-problems", "review-id-problems.md");
        SKILL_RESOURCES = Collections.unmodifiableMap(m);
    }

    // returns the resource filename to serve, or null for a 404
    static String resolveResource(String pathInfo) {
        if (pathInfo == null || pathInfo.equals("/")) return INDEX;
        String name = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (!NAME.matcher(name).matches()) return null;
        return SKILL_RESOURCES.get(name);
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle(request, response);
    }

    void doGetForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String resource = resolveResource(request.getPathInfo());
        if (resource == null) {
            response.setStatus(404);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("not found");
            return;
        }
        String md = readResource(resource);
        if (md == null) {
            response.setStatus(500);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("agent skill unavailable");
            return;
        }
        response.setStatus(200);
        response.setContentType("text/markdown; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "public, max-age=300");
        response.getWriter().write(md);
    }

    private String readResource(String filename) throws IOException {
        try (InputStream in = AgentSkill.class.getResourceAsStream(DIR + filename)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 4: Update web.xml — two url-patterns + Shiro anon subpath**

In `src/main/webapp/WEB-INF/web.xml`, change the `AgentSkill` servlet-mapping (around line 582) to carry both patterns:

```xml
	<servlet-mapping>
		<servlet-name>AgentSkill</servlet-name>
		<url-pattern>/api/v3/agent-skill</url-pattern>
		<url-pattern>/api/v3/agent-skill/*</url-pattern>
	</servlet-mapping>
```

And in the Shiro `[urls]` block, immediately after the existing line 115 (`/api/v3/agent-skill = anon`), add:

```
				/api/v3/agent-skill/** = anon
```

- [ ] **Step 5: Delete the migrated old resource**

Run: `git rm src/main/resources/agent-skill.md`

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -o test -Dtest=AgentSkillTest,AgentSkillContentTest`
Expected: PASS (all routing, edge-case, header, content tests).

- [ ] **Step 7: Normalize + commit**

```bash
sed -i 's/\r$//' src/main/java/org/ecocean/api/AgentSkill.java src/test/java/org/ecocean/api/AgentSkillTest.java
git add src/main/java/org/ecocean/api/AgentSkill.java src/main/webapp/WEB-INF/web.xml src/test/java/org/ecocean/api/AgentSkillTest.java
git add -u src/main/resources/agent-skill.md
git commit -m "feat(agent-skill): serve suite from single URL (index + per-skill routing)"
```

---

## Task 8: Drift-guard test (three-way invariant)

**Files:**
- Modify: `src/test/java/org/ecocean/api/AgentSkillContentTest.java`

**Interfaces:**
- Consumes: `AgentSkill.SKILL_RESOURCES` (Task 7); `load` (Task 1).

- [ ] **Step 1: Write the failing test**

Add to `AgentSkillContentTest`:

```java
@Test void catalog_files_and_index_do_not_drift() {
    // (a) every mapped name resolves to a non-empty resource whose frontmatter name == the key
    for (Map.Entry<String, String> e : AgentSkill.SKILL_RESOURCES.entrySet()) {
        String md = load("/agent-skills/" + e.getValue());
        assertFalse(md.isEmpty(), e.getValue() + " must be non-empty");
        assertEquals(e.getValue(), e.getKey() + ".md",
            "map value must be <name>.md for key " + e.getKey());
        assertTrue(md.contains("name: " + e.getKey()),
            e.getValue() + " frontmatter name must equal its map key");
    }
    // (b) index.md links exactly the four analytical skills in its toolbox, plus api-reference
    String index = load("/agent-skills/index.md");
    String[] analytical = {
        "find-missed-matches", "find-misfiled-sightings",
        "how-good-is-our-matching", "review-id-problems" };
    for (String n : analytical)
        assertTrue(index.contains(n), "index toolbox must list " + n);
    assertTrue(index.contains("api-reference"), "index must reference api-reference");
    // (c) the map's analytical keys are exactly those four (api-reference is the only extra)
    java.util.Set<String> keys = new java.util.HashSet<>(AgentSkill.SKILL_RESOURCES.keySet());
    keys.remove("api-reference");
    assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(analytical)), keys,
        "the analytical skills in the map must be exactly the four listed in the index");
}
```

Add `import java.util.Map;` to the imports at the top of `AgentSkillContentTest.java` (the other
`java.util` types above are referenced fully-qualified, so only `Map` needs an import for the
`for (Map.Entry<...> ...)` loop).

- [ ] **Step 2: Run test to verify it passes**

Because Tasks 1–7 already produced a consistent map + files + index, this invariant test should pass
on first run (it is a regression lock, not a red-then-green driver).

Run: `mvn -o test -Dtest=AgentSkillContentTest#catalog_files_and_index_do_not_drift`
Expected: PASS. If it FAILS, a real drift exists — fix the map/file/index to agree before continuing.

- [ ] **Step 3: Run the full endpoint test suite**

Run: `mvn -o test -Dtest=AgentSkillTest,AgentSkillContentTest`
Expected: PASS (all tests).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/ecocean/api/AgentSkillContentTest.java
git commit -m "test(agent-skill): lock the map/files/index drift invariant"
```

---

## Final verification

- [ ] **Build the project** to confirm no compile/resource regressions:

Run: `mvn -o clean compile test-compile -q`
Expected: BUILD SUCCESS.

- [ ] **Run the full suite for the touched classes:**

Run: `mvn -o test -Dtest=AgentSkillTest,AgentSkillContentTest`
Expected: all green.

- [ ] **Manual voice review (review-checklist item the tests can't fully catch):** read each skill's
  user-facing sections aloud and confirm a non-programmer would understand every sentence; fix any
  jargon the blacklist missed.

- [ ] **Codex review** of the final diff before opening the PR (per project practice), focused on the
  routing/validation, the leak/drift/jargon guards, and the plain-language constraint.
