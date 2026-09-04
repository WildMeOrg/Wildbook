# Issue #1500 — Apply Search ID Fix Implementation Plan (rev 2, post-Codex)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make shared search-query IDs durable (DB-backed instead of `/tmp` files) and fix the frontend defects that make the Apply Search ID feature fail or mislead.

**Architecture:** Replace the `/tmp/OpenSearch-query-<uuid>.json` file storage behind `OpenSearch.queryStore`/`queryLoad` with a minimal JDO entity `org.ecocean.OpenSearchQuery` (id PK, JSON value, indexed created). `queryStore` uses the **caller's** Shepherd on the **same connection**: persist → commit directly on the JDO transaction (so failures propagate — `Shepherd.commitDBTransaction()` swallows them) → re-begin, returning the ID only after a confirmed commit. No second connection per request (avoids pool self-starvation under `maxWait=-1`). Store failure = HTTP 503, not a silent 200. Opportunistic TTL pruning (default 90 days, at most hourly) bounds growth. Frontend: unify the two Apply navigation paths, remove every `"defaultSearchQueryId"` sentinel, gate the stray default POST on the apply-ID page, and route the *applied* query ID to export/map integrations.

**Tech Stack:** Java 11 servlet + DataNucleus JDO (Postgres), JUnit 5 + Mockito + Testcontainers, React 18 + react-query v3 + jest.

## Global Constraints

- Worktree: `/mnt/c/Wildbook-searchid`, branch `fix/issue-1500-stored-query-durability` off `origin/main`.
- After EVERY Edit/Write of a tracked file: `perl -i -pe 's/\r\n/\n/g' <file>` then `grep -cP '\r$' <file>` must print 0.
- No new user-visible UI copy (avoids the 5-language i18n requirement). Do not add new locale keys.
- JUnit 5 assertion message parameter goes LAST.
- The stored-query JSON returned by `queryLoad` must keep the exact merged shape the file version had: top-level query body plus `id`, `indexName`, `created`, `creator` keys (downstream `queryScrubStored`, creator ownership check, and `indexName` read all depend on it).
- `queryStore`'s contract: caller holds an open transaction with **no uncommitted writes** (SearchApi's is read-only); queryStore commits it and re-begins. Document this at the method and the call site.
- `mvn clean install` must pass (gates CI). `npx jest` on touched frontend test files must pass locally (CI hides jest failures).

---

### Task 1: `OpenSearchQuery` JDO entity + storage swap in `OpenSearch`

**Files:**
- Create: `src/main/java/org/ecocean/OpenSearchQuery.java`
- Modify: `src/main/resources/org/ecocean/package.jdo` (after the `SystemValue` class block, ~line 1000)
- Modify: `src/main/java/org/ecocean/OpenSearch.java:96-98` (remove `QUERY_STORAGE_DIR`), `:1537-1568` (`queryStoragePath`/`queryStore`/`queryLoad`)
- Modify: `src/main/java/org/ecocean/api/SearchApi.java:69,164-166` (+ 503 branch)
- Modify: `src/main/java/org/ecocean/EncounterQueryProcessor.java:63,1582`

**Interfaces:**
- Produces: `OpenSearch.queryStore(JSONObject query, String indexName, User user, Shepherd myShepherd)` → String id (null ⇔ not durably stored); `OpenSearch.queryLoad(String id, Shepherd myShepherd)` → merged JSONObject or null; `OpenSearch.queryPrune(Shepherd myShepherd, long cutoffMs)` → long deleted-count; entity helper `OpenSearchQuery.load(Shepherd, String id)`.

- [ ] **Step 1: Write the entity** — `src/main/java/org/ecocean/OpenSearchQuery.java`:

```java
package org.ecocean;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Durable storage for a stored OpenSearch query (see SearchApi "searchQueryId").
 * The value column holds the merged JSON previously written to a /tmp file:
 * the original query body plus id, indexName, created, creator.
 */
public class OpenSearchQuery implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String value;
    private long created;

    public OpenSearchQuery() {}

    public OpenSearchQuery(String id, JSONObject value, long created) {
        this.id = id;
        if (value != null) this.value = value.toString();
        this.created = created;
    }

    public String getId() { return id; }
    public long getCreated() { return created; }

    public JSONObject getValue() {
        return Util.stringToJSONObject(value);
    }

    public static OpenSearchQuery load(Shepherd myShepherd, String id) {
        if (id == null) return null;
        try {
            return ((OpenSearchQuery)(myShepherd.getPM().getObjectById(
                myShepherd.getPM().newObjectIdInstance(OpenSearchQuery.class, id), true)));
        } catch (Exception ex) {
            return null;
        }
    }
}
```

- [ ] **Step 2: JDO mapping** — in `src/main/resources/org/ecocean/package.jdo`, immediately after the `SystemValue` `</class>` block (check the file's existing `<index>` syntax — e.g. grep `<index` — and match it; intended shape):

```xml
	<class name="OpenSearchQuery">
		<field name="id" primary-key="true" />
		<field name="created">
			<column allows-null="true" />
			<index name="OPENSEARCHQUERY_CREATED_IDX" />
		</field>
		<field name="value">
			<column jdbc-type="LONGVARCHAR" />
		</field>
	</class>
```

- [ ] **Step 3: Swap storage in `OpenSearch.java`.** Remove `public static String QUERY_STORAGE_DIR = "/tmp"; // FIXME` and `queryStoragePath`. Replace `queryStore`/`queryLoad`, add `queryPrune` + prune clock:

```java
    // stored search queries: pruned opportunistically at most every PRUNE_INTERVAL
    static final long QUERY_PRUNE_INTERVAL_MILLIS = 3600000L;  // 1 hour
    static final int QUERY_TTL_DAYS_DEFAULT = 90;
    private static volatile long queryLastPruneMillis = 0L;

    /**
     * Persist a stored query durably. Uses the CALLER's Shepherd/connection: commits the
     * caller's open transaction (which must hold no other uncommitted writes — SearchApi's is
     * read-only) via commitDBTransactionWithStatus() so commit failure is detectable (plain
     * commitDBTransaction() swallows it), then re-begins a transaction for the remainder of
     * the request. Returns the new id ONLY after a CONFIRMED commit; null ⇔ durability not
     * confirmed. A confirmed commit followed by a failed re-begin still returns the id —
     * callers needing further DB reads must check myShepherd.isDBTransactionActive().
     */
    public static String queryStore(final JSONObject query, final String indexName,
        final User user, final Shepherd myShepherd) {
        if ((query == null) || (user == null) || (myShepherd == null)) return null;
        JSONObject stored = new JSONObject(query.toString());
        String id = Util.generateUUID();
        long now = System.currentTimeMillis();
        stored.put("id", id);
        stored.put("indexName", indexName);
        stored.put("created", now);
        stored.put("creator", user.getUUID());
        boolean committed = false;
        try {
            myShepherd.getPM().makePersistent(new OpenSearchQuery(id, stored, now));
            if ((now - queryLastPruneMillis) > QUERY_PRUNE_INTERVAL_MILLIS) {
                queryLastPruneMillis = now;
                int ttlDays = (Integer)getConfigurationValue("searchQueryTtlDays",
                    QUERY_TTL_DAYS_DEFAULT);
                queryPrune(myShepherd, now - ttlDays * 86400000L);
            }
            committed = myShepherd.commitDBTransactionWithStatus();
        } catch (Exception ex) {
            System.out.println("OpenSearch.queryStore() failed to store " + id + ": " + ex);
            ex.printStackTrace();
        }
        if (!committed) {
            myShepherd.rollbackDBTransaction();
            myShepherd.beginDBTransaction();
            return null;
        }
        myShepherd.beginDBTransaction(); // recover a transaction for the rest of the request
        return id;
    }

    // deletes stored queries with created < cutoffMillis; runs in the caller's transaction
    public static long queryPrune(final Shepherd myShepherd, final long cutoffMillis) {
        try {
            javax.jdo.Query q = myShepherd.getPM().newQuery(OpenSearchQuery.class,
                "created < " + cutoffMillis);
            long ct = q.deletePersistentAll();
            if (ct > 0)
                System.out.println("OpenSearch.queryPrune() deleted " + ct
                    + " stored queries older than " + new java.util.Date(cutoffMillis));
            return ct;
        } catch (Exception ex) {
            System.out.println("OpenSearch.queryPrune() failed: " + ex);
            return 0;
        }
    }

    public static JSONObject queryLoad(String id, Shepherd myShepherd) {
        OpenSearchQuery osq = OpenSearchQuery.load(myShepherd, id);

        if (osq == null) return null;
        return osq.getValue();
    }
```

Note: `Shepherd` import already present in `OpenSearch.java` (line 21). Prune failure is swallowed on purpose (logged) — it must never break the store; a prune *exception mid-transaction* is covered by the outer catch (rollback + re-begin + null) which is acceptable-rare.

- [ ] **Step 4: Update call sites.**
  - `SearchApi.java:69`: `query = OpenSearch.queryLoad(searchQueryId, myShepherd);`
  - `SearchApi.java` store block (~line 164): replace
    ```java
    if (newQueryToStore) {
        searchQueryId = OpenSearch.queryStore(query, indexName, currentUser);
    }
    ```
    with a fail-closed version — a search whose response promises a share ID must not 200 without one:
    ```java
    boolean storeFailed = false;
    boolean sessionRecoveryFailed = false;
    if (newQueryToStore) {
        // commits + re-begins myShepherd's (read-only so far) transaction; see queryStore javadoc
        searchQueryId = OpenSearch.queryStore(query, indexName, currentUser, myShepherd);
        storeFailed = (searchQueryId == null);
        // commit confirmed but the request's transaction could not be re-established:
        // the id IS durable, but sanitizeDoc etc. need live DB reads — fail the request
        // WITHOUT claiming the store failed, and hand back the (valid) id.
        sessionRecoveryFailed = !storeFailed && !myShepherd.isDBTransactionActive();
    }
    if (storeFailed) {
        response.setStatus(503);
        res.put("success", false);
        res.put("error", "failed to store search query");
    } else if (sessionRecoveryFailed) {
        response.setStatus(503);
        res.put("success", false);
        res.put("error", "search query stored but request could not continue; retry");
        res.put("searchQueryId", searchQueryId);
        response.setHeader("X-Wildbook-Search-Query-Id", searchQueryId);
    } else {
        ... existing applyAclFilter + execution block, unchanged ...
    }
    ```
    (`boolean storeFailed` declared where `newQueryToStore` is; the existing execution block from `if (tokenAuth && !isAdmin)` through the `catch (IOException ex)` moves inside the `else`. Keep indentation minimal per the repo's leak-fix diff convention — do NOT reindent the moved block; add the wrapper braces at the surrounding indent level.)
  - `EncounterQueryProcessor.java:63` and `:1582`: `OpenSearch.queryLoad(searchQueryId, myShepherd);` (both have `myShepherd` in scope — verify variable name at each site).

- [ ] **Step 5: Compile** — `cd /mnt/c/Wildbook-searchid && mvn compile -q 2>&1 | tail -20`. Expected: BUILD SUCCESS.

- [ ] **Step 6: Normalize CRLF on all touched files, then commit**

```bash
git add src/main/java/org/ecocean/OpenSearchQuery.java src/main/resources/org/ecocean/package.jdo \
  src/main/java/org/ecocean/OpenSearch.java src/main/java/org/ecocean/api/SearchApi.java \
  src/main/java/org/ecocean/EncounterQueryProcessor.java
git commit -m "store search queries in the database instead of /tmp files"
```

### Task 2: Tests — real-queryStore roundtrip + mock updates + failure-contract tests

**Files:**
- Create: `src/test/java/org/ecocean/OpenSearchQueryStoreTest.java`
- Modify: `src/test/java/org/ecocean/api/SearchApiTokenAuthTest.java`
- Modify: `src/test/java/org/ecocean/api/SearchApiChildIndexTest.java` (queryLoad stubs at lines ~276, 293)
- Audit: every test in both classes that drives a direct POST **without** `mockStatic(OpenSearch.class)` — the real `queryStore(..., mockShepherd)` would NPE on the mocked PM; add an `osStatic` with a `queryStore` stub (or widen an existing one) to each. Find them by running the suite and fixing every new failure — do not guess.

**Interfaces:**
- Consumes: Task 1 signatures exactly: `queryStore(JSONObject, String, User, Shepherd)`, `queryLoad(String, Shepherd)`, `queryPrune(Shepherd, long)`.

- [ ] **Step 1: Update existing stubs.** `queryLoad(anyString())` → `queryLoad(anyString(), any())` in both test classes. Where a test asserts the POST response carries `searchQueryId`, stub `osStatic.when(() -> OpenSearch.queryStore(any(), anyString(), any(), any())).thenReturn("<uuid literal>")`.

- [ ] **Step 2: Add contract tests** in `SearchApiTokenAuthTest` (reuse `mockUser`, `shepherdReturning`, `su`, `EMPTY_HITS`):

Test A — session path returns the stored id (must FORCE session mode: `when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(null);` and no Authorization header — the class `@BeforeEach` defaults the attr to TRUE):

```java
    @Test void sessionPost_storesQuery_andReturnsSearchQueryId() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null); // session path
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.queryStore(any(), anyString(), any(), any()))
                .thenReturn("11111111-2222-3333-4444-555555555555");
            try (MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(), any(),
                    any())).thenReturn(EMPTY_HITS);
            })) {
                new SearchApi().doPost(request, response);
            }
        }
        verify(response).setStatus(200);
        verify(response).setHeader("X-Wildbook-Search-Query-Id",
            "11111111-2222-3333-4444-555555555555");
        assertTrue(out.toString().contains("11111111-2222-3333-4444-555555555555"),
            "response body carries searchQueryId");
    }
```

Test B — store failure is 503 and the search does NOT execute:

```java
    @Test void post_storeFailure_returns503_andDoesNotExecute() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null);
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.queryStore(any(), anyString(), any(), any()))
                .thenReturn(null); // store failed
            try (MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
            })) {
                new SearchApi().doPost(request, response);
                assertEquals(0, os.constructed().size() == 0 ? 0
                    : mockingDetails(os.constructed().get(0)).getInvocations().size(),
                    "no search executed after store failure");
            }
        }
        verify(response).setStatus(503);
        assertTrue(out.toString().contains("failed to store search query"),
            "error surfaced in body");
    }
```

(If the no-execution assertion is awkward, assert instead that `queryPit` was never invoked on any constructed mock — implementer's choice, but some no-execution assertion must exist.)

- [ ] **Step 3: Testcontainers test through the REAL `queryStore`** — `src/test/java/org/ecocean/OpenSearchQueryStoreTest.java`. Postgres container only; copy the PMF boilerplate from `SearchTokenScopeTest`: `TestPMFUtil.closePMF("context0")` in `@BeforeAll` AND `@AfterAll`, `CommonConfiguration.initialize("context0", props)` minimal, `new Shepherd("context0", dnProperties())` primes the global PMF cache. Body:

```java
    @Test void queryStoreRoundtripAndPrune() {
        // seed a real user (queryStore reads getUUID())
        Shepherd seeder = new Shepherd("context0", dnProperties());
        seeder.setAction("OpenSearchQueryStoreTest.seed");
        User user;
        seeder.beginDBTransaction();
        try {
            user = new User("osqtester",
                ServletUtilities.hashAndSaltPassword("pw", ServletUtilities.getSalt().toHex()),
                "salt");
            seeder.getPM().makePersistent(user);
            seeder.commitDBTransaction();
        } finally {
            seeder.rollbackAndClose();
        }

        // store through the REAL path: persist + direct-commit + re-begin
        Shepherd writer = new Shepherd("context0", dnProperties());
        writer.setAction("OpenSearchQueryStoreTest.write");
        writer.beginDBTransaction();
        String id;
        try {
            JSONObject query = new JSONObject().put("query",
                new JSONObject().put("match_all", new JSONObject()));
            id = OpenSearch.queryStore(query, "encounter", user, writer);
            assertNotNull(id, "queryStore returns id after confirmed commit");
            assertTrue(writer.getPM().currentTransaction().isActive(),
                "queryStore re-begins the caller transaction");
        } finally {
            writer.rollbackAndClose();
        }

        // load from a SEPARATE Shepherd: durability + merged shape
        Shepherd reader = new Shepherd("context0", dnProperties());
        reader.setAction("OpenSearchQueryStoreTest.read");
        reader.beginDBTransaction();
        try {
            JSONObject loaded = OpenSearch.queryLoad(id, reader);
            assertNotNull(loaded, "stored query loads back");
            assertEquals("encounter", loaded.optString("indexName"), "indexName survives");
            assertEquals(user.getUUID(), loaded.optString("creator"), "creator survives");
            assertEquals(id, loaded.optString("id"), "id embedded in stored doc");
            assertNotNull(loaded.optJSONObject("query"), "query body survives");
            assertNull(OpenSearch.queryLoad(Util.generateUUID(), reader), "unknown id is null");
        } finally {
            reader.rollbackAndClose();
        }

        // prune: older-than-cutoff rows deleted, newer kept
        Shepherd pruner = new Shepherd("context0", dnProperties());
        pruner.setAction("OpenSearchQueryStoreTest.prune");
        pruner.beginDBTransaction();
        try {
            pruner.getPM().makePersistent(new OpenSearchQuery("old-one",
                new JSONObject().put("query", new JSONObject()), 1000L));
            pruner.commitDBTransaction();
            pruner.beginDBTransaction();
            OpenSearch.queryPrune(pruner, 2000L);
            pruner.commitDBTransaction();
            pruner.beginDBTransaction();
            assertNull(OpenSearchQuery.load(pruner, "old-one"), "old row pruned");
            assertNotNull(OpenSearchQuery.load(pruner, id), "fresh row kept");
        } finally {
            pruner.rollbackAndClose();
        }
    }
```

- [ ] **Step 4: Run backend tests** — `mvn test -q 2>&1 | tail -40`. Fix EVERY failure from the signature change per the audit note above. E2E `SearchTokenScopeTest`/`ChildIndex` exercise the real DB-backed store via the primed PMF cache — they must stay green with zero test-side changes (they are the regression guard for the implicit global-PMF wiring).

- [ ] **Step 5: Normalize CRLF, commit** — `git commit -m "test: stored-query durable roundtrip, store-failure 503 contract, prune"`.

### Task 3: Frontend — unify Apply navigation, kill the sentinel everywhere, gate the stray POST, route applied ID to exports

**Files:**
- Modify: `frontend/src/components/filterFields/ApplyQueryFilter.jsx`
- Modify: `frontend/src/models/encounters/useFilterEncounters.js`
- Modify: `frontend/src/models/encounters/useFilterEncountersAll.js` (sentinel only)
- Modify: `frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js` (sentinel only)
- Modify: `frontend/src/pages/SearchPages/EncounterSearch.jsx`
- Modify: `frontend/src/components/filterFields/SideBar.jsx`
- Test: `frontend/src/__tests__/pages/EncounterSearchPageAndFilters/ApplyQueryFilter.test.js`
- Test: `frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js`

- [ ] **Step 1: `ApplyQueryFilter.jsx`** — single `applyId` used by both Enter and click; trim + URL-encode; both use `PUBLIC_URL`:

```jsx
  const applyId = () => {
    const trimmed = queryId.trim();
    if (trimmed) {
      window.location.href = `${process.env.PUBLIC_URL}/encounter-search?searchQueryId=${encodeURIComponent(trimmed)}`;
    }
  };
```

`onKeyDown`: `if (e.key === "Enter") { e.preventDefault(); applyId(); }` (preventDefault stops the form's default submit-reload). Button `onClick={applyId}`.

- [ ] **Step 2: `useFilterEncounters.js`** — accept `enabled = true` param; replace `enable: false` with `enabled`; sentinel → `""`:

```js
export default function useFilterEncounters({ queries, params = {}, enabled = true }) {
  ...
    queryOptions: {
      retry: 2,
      enabled,
    },
```

and `searchQueryId: get(result, ["data", "data", "searchQueryId"], "")`. Same sentinel replacement (only) in `useFilterEncountersAll.js` and `useFilterEncountersWithMediaAssets.js` — check no caller compares against the literal string first (`grep -rn defaultSearchQueryId frontend/src`).

- [ ] **Step 3: `EncounterSearch.jsx`** — `useFilterEncounters({ queries: ..., params: ..., enabled: !queryID })`; `<DataTable searchQueryId={queryID || searchQueryId} ...>`; `<ExportModal searchQueryId={queryID || searchQueryId} ...>`.

- [ ] **Step 4: `SideBar.jsx`** — first line of `handleCopy`: `const idToCopy = queryID || searchQueryId; if (!idToCopy) return;` (no new UI copy).

- [ ] **Step 5: Update/extend jest tests.**
  - `ApplyQueryFilter.test.js`: Enter/click expectations become the same URL (PUBLIC_URL is `""` under jest); add trim case (`"  12345  "` → `/encounter-search?searchQueryId=12345`) and whitespace-only no-redirect case.
  - `EncounterSearch.test.js`: with `?searchQueryId=<uuid>` mounted (mock `useSearchParams`/router as the suite already does), assert (a) axios GET `/api/v3/search/<uuid>` is called, (b) the default POST (`useFetch`→axios POST `/api/v3/search/encounter`) is NOT (mock axios and inspect calls), (c) on GET rejection the component clears queryID (alert mocked) and the POST becomes enabled. Follow the suite's existing mocking idiom — read it first; if (c) is impractical in the current harness, cover (a)+(b) and note (c) as manual verification in the PR.

- [ ] **Step 6: Run** — `cd frontend && npx jest src/__tests__/pages/EncounterSearchPageAndFilters/ --silent 2>&1 | tail -15`. Expected: PASS.

- [ ] **Step 7: Normalize CRLF, commit** — `git commit -m "fix Apply Search ID frontend: unify navigation, gate default POST on apply page, no fake fallback id"`.

### Task 4: Full verification + PR

- [ ] **Step 1:** `mvn clean install -q 2>&1 | tail -15` — BUILD SUCCESS, 0 test failures.
- [ ] **Step 2:** `cd frontend && npx jest --silent 2>&1 | tail -10` — no NEW failures vs main baseline (run baseline on a clean main checkout of the same files if unsure).
- [ ] **Step 3:** `git diff origin/main --stat` and `git diff origin/main --ignore-cr-at-eol --stat` must match (no CRLF churn).
- [ ] **Step 4:** Codex review of the full diff (codex-review skill), iterate to convergence.
- [ ] **Step 5:** Push; PR against `main`, milestone 10.12 (`gh api -X PATCH repos/WildMeOrg/Wildbook/issues/<n> -F milestone=24`). PR body must include:
  - diagnosis summary + fixes, references #1500;
  - **deployment note:** table `"OPENSEARCHQUERY"` is auto-created only when the effective DataNucleus props allow DDL (bundled default `datanucleus.schema.autoCreateAll=true`, but deployment override props may disable it / DB role may lack CREATE). Manual DDL fallback:
    ```sql
    CREATE TABLE "OPENSEARCHQUERY" ("ID" varchar(255) NOT NULL PRIMARY KEY,
        "CREATED" bigint, "VALUE" text);
    CREATE INDEX "OPENSEARCHQUERY_CREATED_IDX" ON "OPENSEARCHQUERY" ("CREATED");
    ```
    (verify the exact generated names against the Testcontainers DB during Task 2 and correct this DDL if DataNucleus names differ);
  - retention: stored queries pruned after `searchQueryTtlDays` (default 90) — shared IDs now survive redeploys but not forever;
  - old `/tmp` IDs are not migrated (already ephemeral);
  - does NOT close the #1500 redesign discussion (filter-chip hydration etc.).

**Out of scope (note in PR):** filter-chip hydration from a replayed query; i18n for SideBar's preexisting hardcoded alert strings; the `regularQuery` legacy JSP flows beyond passing the correct id.
