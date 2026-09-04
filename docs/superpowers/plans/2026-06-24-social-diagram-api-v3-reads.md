# Social Diagram read-path migration to `/api/v3` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three crashing/unsafe legacy `/api/*` reads behind the `individuals.jsp` Social Diagram with one secured, ACL-filtering `/api/v3` endpoint, and rewrite the diagram JS to use it.

**Architecture:** A new `social-data` GET branch in the existing `org.ecocean.api.MarkedIndividualInfo` servlet assembles, server-side and ACL-filtered, the encounter table and relationship table data for one individual. The legacy d3 JS (`encounter-calls.js`) is rewritten to fetch that single endpoint instead of `/api/jdoql` + per-object REST gets.

**Tech Stack:** Java 17, DataNucleus JDO, servlets; JUnit 5 + Testcontainers (Postgres) + Mockito for backend tests; legacy d3/jQuery JS (no automated harness — manual verification).

## Global Constraints

- Commit LF only. After every Edit/Write to a tracked file run `perl -i -pe 's/\r\n/\n/g' <file>` and verify `grep -cP '\r$' <file>` is `0`.
- Branch: `fix/social-diagram-api-v3-reads` (already created, stacked on `fix/api-v3-individuals-get` / PR #1633). Do not branch off bare `main` — this depends on `MarkedIndividual.canUserView`.
- Reads only. Do NOT modify the relationship write flow (`RelationshipDelete`, the add/edit form, `getRelationshipData`/line 585) or the bubble-graph JSP feed (line 29).
- No new endpoint URL/web.xml mapping; no OpenSearch serializer change.
- Spec: `docs/superpowers/specs/2026-06-24-social-diagram-api-v3-reads-design.md` (authoritative; this plan implements it).
- Maven single-test run: `mvn test -Dtest=MarkedIndividualInfoTest#<method> -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -Xmx2g"` (match the repo's existing surefire argLine; copy from a currently-passing api test invocation if it differs).

## File Structure

- **Modify** `src/main/java/org/ecocean/api/MarkedIndividualInfo.java` — add the `social-data` route branch and private assembly helpers. All new server logic lives here (one focused servlet; no new class needed).
- **Modify** `src/main/java/org/ecocean/MarkedIndividual.java` — add a `getAlternateID()` getter (partner display needs it; field is private with no accessor).
- **Create** `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java` — Testcontainers + direct-`doGet` tests for routing, status codes, and ACL filtering.
- **Modify** `src/main/webapp/javascript/bubbleDiagram/encounter-calls.js` — rewrite `getEncounterTableData` (491) and `getRelationshipTableData` (655); delete `getIndividualData` (703).

---

### Task 1: Add `MarkedIndividual.getAlternateID()`

**Files:**
- Modify: `src/main/java/org/ecocean/MarkedIndividual.java` (near the existing `getNickName()` at ~line 888)

**Interfaces:**
- Produces: `public String MarkedIndividual.getAlternateID()` returning the private `alternateid` field. Consumed by Task 5's partner block.

- [ ] **Step 1: Add the getter**

Insert after `getNickName()` (~line 890):

```java
    public String getAlternateID() {
        return alternateid;
    }
```

- [ ] **Step 2: Normalize + compile**

Run: `perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/MarkedIndividual.java && grep -cP '\r$' src/main/java/org/ecocean/MarkedIndividual.java` (expect `0`)
Run: `mvn -o compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/ecocean/MarkedIndividual.java
git commit -m "feat: add MarkedIndividual.getAlternateID accessor"
```

---

### Task 2: Endpoint skeleton — routing, auth gate, status codes

Add the `social-data` branch returning `{encounters:[], relationships:[]}` for a viewable individual; establish the test class + Testcontainers fixture.

**Files:**
- Modify: `src/main/java/org/ecocean/api/MarkedIndividualInfo.java`
- Test: `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java` (create)

**Interfaces:**
- Produces: `GET /api/v3/individuals/info/social-data?id={individualId}` → JSON `{success, statusCode, individualId, encounters:[], relationships:[]}`. Status: 401 anonymous, 400 blank id, 404 unknown id, 403 not-viewable, 200 ok. Private method signature later tasks extend: `private JSONObject getSocialData(Shepherd myShepherd, HttpServletRequest request, User user)`.

- [ ] **Step 1: Write failing tests for routing/status codes**

Create `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java`. Use the established harness (Testcontainers Postgres + `context0` PMF init as in `SearchTokenScopeChildIndexTest`; direct `doGet` with mocked request/response + `StringWriter` as in `AuthTokenTest`). Shared fixture:

```java
package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MarkedIndividualInfoTest {
    @Container static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test").withUsername("wildbook").withPassword("wildbook");

    static Properties props;

    @BeforeAll static void setUp() {
        Properties cc = new Properties();
        cc.setProperty("collaborationSecurityEnabled", "true");
        CommonConfiguration.initialize("context0", cc);
        props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");
    }

    @AfterAll static void tearDown() { TestPMFUtil.closePMF("context0"); }

    // helper: invoke doGet and return parsed JSON body + captured status
    static JSONObject invoke(User authUser, String idParam) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        when(req.getRequestURI()).thenReturn("/api/v3/individuals/info/social-data");
        when(req.getParameter("id")).thenReturn(idParam);
        // ServletUtilities.getContext + getUser must yield context0 + authUser:
        when(req.getServletContext()).thenReturn(null);
        // NOTE: confirm how MarkedIndividualInfo resolves the User from request in this repo
        //       (myShepherd.getUser(request)); stub the session/principal accordingly.
        new MarkedIndividualInfo().doGet(req, resp);
        return new JSONObject(out.toString());
    }
}
```

Add the status-code tests (create one viewable individual with one owned encounter in a `@BeforeAll`-seeded transaction using `props`; see `EncounterExportImagesTest` seeding):

```java
    @Test void anonymousIs401() throws Exception {
        JSONObject j = invoke(null, "any-id");
        assertEquals(401, j.optInt("statusCode"));
    }
    @Test void blankIdIs400() throws Exception {
        JSONObject j = invoke(seededOwner, "");
        assertEquals(400, j.optInt("statusCode"));
    }
    @Test void unknownIdIs404() throws Exception {
        JSONObject j = invoke(seededOwner, "00000000-0000-0000-0000-000000000000");
        assertEquals(404, j.optInt("statusCode"));
    }
    @Test void viewableReturns200WithArrays() throws Exception {
        JSONObject j = invoke(seededOwner, seededIndivId);
        assertEquals(200, j.optInt("statusCode"));
        assertTrue(j.has("encounters"));
        assertTrue(j.has("relationships"));
    }
```

(Seed `seededOwner`, `seededIndivId`, and a non-owner user `stranger` in `@BeforeAll` after `setUp`; persist a `User`, an `Encounter` with `enc.setSubmitterID(owner.getUsername())`, and `new MarkedIndividual(displayName, enc)` via `storeNewMarkedIndividual`. Add `notViewableIs403` once a stranger user exists and `collaborationSecurityEnabled` is on.)

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn test -Dtest=MarkedIndividualInfoTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL (the `social-data` branch does not exist yet → body is the servlet's `unknown request` default, statusCode 500/mismatch).

- [ ] **Step 3: Implement the route branch + gate**

In `MarkedIndividualInfo.doGet`, extend the dispatch (after the existing `next_name` branch):

```java
        if (args[0].equals("individuals") && args[1].equals("info")) {
            if (args[2].equals("next_name")) {
                results = getNextNames(myShepherd, request, currentUser);
            } else if (args[2].equals("social-data")) {
                results = getSocialData(myShepherd, request, currentUser);
            }
        }
```

Add the method (skeleton — encounters/relationships filled in Tasks 3–5):

```java
    private JSONObject getSocialData(Shepherd myShepherd, HttpServletRequest request, User user) {
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        String id = request.getParameter("id");
        if (!Util.stringExists(id)) {
            rtn.put("statusCode", 400);
            rtn.put("error", "missing id");
            return rtn;
        }
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(id.trim());
        if (indiv == null) {
            rtn.put("statusCode", 404);
            rtn.put("error", "not found");
            return rtn;
        }
        if (!indiv.canUserView(user, myShepherd)) {
            rtn.put("statusCode", 403);
            rtn.put("error", "access denied");
            return rtn;
        }
        rtn.put("individualId", indiv.getId());
        rtn.put("encounters", new org.json.JSONArray());
        rtn.put("relationships", new org.json.JSONArray());
        rtn.put("success", true);
        rtn.put("statusCode", 200);
        return rtn;
    }
```

Add imports as needed: `org.ecocean.Encounter`, `org.ecocean.social.Relationship`, `org.json.JSONArray`, `java.util.*`.

- [ ] **Step 4: Run tests — verify pass**

Run: `mvn test -Dtest=MarkedIndividualInfoTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (status-code tests green).

- [ ] **Step 5: Normalize + commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/api/MarkedIndividualInfo.java src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java
git add src/main/java/org/ecocean/api/MarkedIndividualInfo.java src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java
git commit -m "feat: add /api/v3/individuals/info/social-data route with auth gate"
```

---

### Task 3: Encounter rows with per-encounter ACL filter + dataTypes

**Files:**
- Modify: `src/main/java/org/ecocean/api/MarkedIndividualInfo.java`
- Test: `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java`

**Interfaces:**
- Consumes: `getSocialData` skeleton (Task 2).
- Produces: each `encounters[]` row = `{catalogNumber, dateInMilliseconds, year, month, day, locationID, occurrenceID, sex, behavior, alternateid, dataTypes}` (occurringWith added in Task 4). Private helpers `boolean encCanView(Encounter, User, Shepherd, Map<String,Boolean>)`, `String computeDataTypes(Encounter)`.

- [ ] **Step 1: Write failing tests**

Add to the test class (seed: an individual with two encounters owned by different users; a third encounter with a tissue sample + annotation):

```java
    @Test void onlyViewableEncountersReturned() throws Exception {
        // owner sees both their encounters; stranger (no collab) sees only public ones
        JSONObject asOwner = invoke(seededOwner, seededIndivId);
        JSONObject asStranger = invoke(stranger, seededIndivId);
        assertTrue(asOwner.getJSONArray("encounters").length()
                   > asStranger.getJSONArray("encounters").length());
    }
    @Test void dataTypesBothWhenTissueAndAnnotation() throws Exception {
        JSONObject j = invoke(seededOwner, indivWithTissueAndAnnotId);
        String dt = j.getJSONArray("encounters").getJSONObject(0).getString("dataTypes");
        assertEquals("both", dt);
    }
```

- [ ] **Step 2: Run — verify fail**

Run: `mvn test -Dtest=MarkedIndividualInfoTest#onlyViewableEncountersReturned -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL (encounters array is empty).

- [ ] **Step 3: Implement encounter assembly**

Replace `rtn.put("encounters", new org.json.JSONArray());` in `getSocialData` with:

```java
        Map<String, Boolean> encViewCache = new HashMap<String, Boolean>();
        JSONArray encArr = new JSONArray();
        if (indiv.getEncounters() != null) {
            for (Encounter enc : indiv.getEncounters()) {
                if (!encCanView(enc, user, myShepherd, encViewCache)) continue;
                JSONObject e = new JSONObject();
                e.put("catalogNumber", enc.getCatalogNumber());
                Long dim = enc.getDateInMilliseconds();
                if (dim != null) e.put("dateInMilliseconds", dim.longValue());
                e.put("year", enc.getYear());
                e.put("month", enc.getMonth());
                e.put("day", enc.getDay());
                e.put("locationID", enc.getLocationID());
                e.put("occurrenceID", enc.getOccurrenceID());
                e.put("sex", enc.getSex());
                e.put("behavior", enc.getBehavior());
                e.put("alternateid", enc.getAlternateID());
                e.put("dataTypes", computeDataTypes(enc));
                encArr.put(e);
            }
        }
        rtn.put("encounters", encArr);
```

Add helpers:

```java
    private boolean encCanView(Encounter enc, User user, Shepherd myShepherd,
        Map<String, Boolean> cache) {
        String eid = enc.getId();
        Boolean cached = cache.get(eid);
        if (cached != null) return cached.booleanValue();
        boolean v = enc.canUserView(user, myShepherd);
        cache.put(eid, v);
        return v;
    }

    // Mirrors the legacy encounter-calls.js dataTypes precedence (ANY annotation counts;
    // tissue type is the static "TissueSample").
    private String computeDataTypes(Encounter enc) {
        List<org.ecocean.genetics.TissueSample> ts = enc.getTissueSamples();
        boolean hasTissue = (ts != null) && !ts.isEmpty();
        List<org.ecocean.Annotation> anns = enc.getAnnotations();
        boolean hasAnn = (anns != null) && !anns.isEmpty();
        if (hasTissue && hasAnn) return "both";
        if (hasTissue) return "TissueSample";
        if (hasAnn) {
            String eventID = enc.getEventID();
            if ((eventID != null) && (eventID.indexOf("youtube") > -1)) return "youtube-image";
            return "image";
        }
        return "";
    }
```

- [ ] **Step 4: Run — verify pass**

Run: `mvn test -Dtest=MarkedIndividualInfoTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 5: Normalize + commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/api/MarkedIndividualInfo.java src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java
git add -A && git commit -m "feat: social-data encounter rows with per-encounter ACL filter + dataTypes"
```

---

### Task 4: `occurringWith` (per-companion-encounter ACL, memoized display string)

**Files:**
- Modify: `src/main/java/org/ecocean/api/MarkedIndividualInfo.java`
- Test: `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java`

**Interfaces:**
- Consumes: `encCanView` cache (Task 3).
- Produces: each encounter row gains `occurringWith` (a `", "`-joined string of viewable co-occurring individual `displayName`s). Helper `String occurringWith(Encounter enc, MarkedIndividual focal, User, Shepherd, Map<String,Boolean> encViewCache, Map<String,String> occCache)`.

- [ ] **Step 1: Write failing test (the co-occurrence leak guard)**

Seed: an occurrence containing the focal individual's encounter + a companion encounter owned by `stranger` (not viewable by `seededOwner` if security blocks it — invert ownership so the focal owner can see focal but NOT the companion). Assert the companion's displayName is absent from `occurringWith`:

```java
    @Test void occurringWithExcludesNonViewableCompanion() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivIdInSharedOcc);
        org.json.JSONArray encs = j.getJSONArray("encounters");
        for (int i = 0; i < encs.length(); i++) {
            String ow = encs.getJSONObject(i).optString("occurringWith", "");
            assertFalse(ow.contains(hiddenCompanionDisplayName),
                "non-viewable companion must not leak into occurringWith");
        }
    }
    @Test void occurringWithIncludesViewableCompanion() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivIdWithViewableCompanion);
        String ow = j.getJSONArray("encounters").getJSONObject(0).optString("occurringWith", "");
        assertTrue(ow.contains(viewableCompanionDisplayName));
    }
```

- [ ] **Step 2: Run — verify fail**

Run: `mvn test -Dtest=MarkedIndividualInfoTest#occurringWithIncludesViewableCompanion -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL (no `occurringWith` field yet).

- [ ] **Step 3: Implement `occurringWith`**

Thread the focal individual and an occurrence cache into the encounter loop. Add `Map<String,String> occCache = new HashMap<String,String>();` next to `encViewCache`, and inside the row build add:

```java
                e.put("occurringWith", occurringWith(enc, indiv, user, myShepherd, encViewCache, occCache));
```

Add the helper:

```java
    // ACL-correct co-occurrence: per the spec, a companion is included ONLY if the
    // companion's own encounter in the occurrence passes enc.canUserView — never
    // occurrence-level auth (which admits an occurrence if ANY encounter is viewable).
    private String occurringWith(Encounter enc, MarkedIndividual focal, User user,
        Shepherd myShepherd, Map<String, Boolean> encViewCache, Map<String, String> occCache) {
        String occId = enc.getOccurrenceID();
        if (occId == null) return "";
        String memo = occCache.get(occId);
        if (memo != null) return memo;
        org.ecocean.Occurrence occ = enc.getOccurrence(myShepherd);
        String focalId = focal.getId();
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
        if ((occ != null) && (occ.getEncounters() != null)) {
            for (Encounter coEnc : occ.getEncounters()) {
                MarkedIndividual coInd = coEnc.getIndividual();
                if (coInd == null) continue;
                if ((focalId != null) && focalId.equals(coInd.getIndividualID())) continue;
                if (!encCanView(coEnc, user, myShepherd, encViewCache)) continue;
                String dn = coInd.getDisplayName();
                if (Util.stringExists(dn)) names.add(dn);
            }
        }
        String joined = String.join(", ", names);
        occCache.put(occId, joined);
        return joined;
    }
```

- [ ] **Step 4: Run — verify pass**

Run: `mvn test -Dtest=MarkedIndividualInfoTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 5: Normalize + commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/api/MarkedIndividualInfo.java src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java
git add -A && git commit -m "feat: ACL-filtered occurringWith on social-data encounter rows"
```

---

### Task 5: Relationship rows — `_id`, partner resolution + ACL, embedded partner

**Files:**
- Modify: `src/main/java/org/ecocean/api/MarkedIndividualInfo.java`
- Test: `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java`

**Interfaces:**
- Produces: each `relationships[]` row = `{_id, type, relatedSocialUnitName, markedIndividualName1, markedIndividualName2, markedIndividualRole1, markedIndividualRole2, startTime, endTime, partner:{individualID, displayName, nickName, alternateid, sex, localHaplotypeReflection}}`. Helpers `JSONObject relationshipRow(...)`, `boolean partnerCanView(...)`, `String relationshipDatastoreId(Relationship)`.

- [ ] **Step 1: Write failing tests (`_id` round-trip + partner filter)**

Seed: focal individual with a relationship to a viewable partner, and a second relationship to a non-viewable partner. The `_id` round-trip asserts the emitted id, reconstructed as the legacy `persistenceID`, resolves the same `Relationship`:

```java
    @Test void relationshipIdRoundTripsThroughGetObjectById() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivWithViewableRel);
        String id = j.getJSONArray("relationships").getJSONObject(0).getString("_id");
        String persistenceID = id + "[OID]org.ecocean.social.Relationship";
        Shepherd s = new Shepherd("context0", props);
        s.beginDBTransaction();
        try {
            org.ecocean.social.Relationship rel =
                (org.ecocean.social.Relationship) s.getPM()
                    .getObjectById(org.ecocean.social.Relationship.class, persistenceID);
            assertNotNull(rel, "emitted _id must resolve the same Relationship the legacy "
                + "edit/remove path resolves");
        } finally { s.rollbackAndClose(); }
    }
    @Test void relationshipToNonViewablePartnerOmitted() throws Exception {
        JSONObject j = invoke(focalOwner, focalIndivWithHiddenPartnerRel);
        // only the viewable-partner relationship is present
        org.json.JSONArray rels = j.getJSONArray("relationships");
        for (int i = 0; i < rels.length(); i++) {
            String pid = rels.getJSONObject(i).getJSONObject("partner").getString("individualID");
            assertNotEquals(hiddenPartnerId, pid);
        }
    }
```

- [ ] **Step 2: Run — verify fail**

Run: `mvn test -Dtest=MarkedIndividualInfoTest#relationshipIdRoundTripsThroughGetObjectById -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL (relationships array empty).

- [ ] **Step 3: Implement relationship assembly**

Replace `rtn.put("relationships", new org.json.JSONArray());` with:

```java
        Map<String, Boolean> partnerViewCache = new HashMap<String, Boolean>();
        JSONArray relArr = new JSONArray();
        for (Relationship rel : myShepherd.getAllRelationshipsForMarkedIndividual(indiv.getId())) {
            JSONObject row = relationshipRow(rel, indiv, user, myShepherd, partnerViewCache);
            if (row != null) relArr.put(row);
        }
        rtn.put("relationships", relArr);
```

Add helpers:

```java
    private JSONObject relationshipRow(Relationship rel, MarkedIndividual focal, User user,
        Shepherd myShepherd, Map<String, Boolean> partnerViewCache) {
        MarkedIndividual partner = rel.getOtherMarkedIndividual(focal);
        if (partner == null) { // object link not populated; fall back to non-self name
            String focalId = focal.getId();
            String n1 = rel.getMarkedIndividualName1();
            String n2 = rel.getMarkedIndividualName2();
            String otherName = null;
            if ((n1 != null) && !n1.equals(focalId)) otherName = n1;
            else if ((n2 != null) && !n2.equals(focalId)) otherName = n2;
            if (otherName != null) partner = myShepherd.getMarkedIndividual(otherName);
        }
        if (partner == null) return null;
        if (!partnerCanView(partner, user, myShepherd, partnerViewCache)) return null;

        JSONObject r = new JSONObject();
        r.put("_id", relationshipDatastoreId(rel));
        r.put("type", rel.getType());
        r.put("relatedSocialUnitName", rel.getRelatedSocialUnitName());
        r.put("markedIndividualName1", rel.getMarkedIndividualName1());
        r.put("markedIndividualName2", rel.getMarkedIndividualName2());
        r.put("markedIndividualRole1", rel.getMarkedIndividualRole1());
        r.put("markedIndividualRole2", rel.getMarkedIndividualRole2());
        r.put("startTime", rel.getStartTime());
        r.put("endTime", rel.getEndTime());

        JSONObject p = new JSONObject();
        p.put("individualID", partner.getIndividualID());
        p.put("displayName", partner.getDisplayName());
        p.put("nickName", partner.getNickName());
        p.put("alternateid", partner.getAlternateID());
        p.put("sex", partner.getSex());
        p.put("localHaplotypeReflection", partner.getHaplotype());
        r.put("partner", p);
        return r;
    }

    private boolean partnerCanView(MarkedIndividual partner, User user, Shepherd myShepherd,
        Map<String, Boolean> cache) {
        String pid = partner.getId();
        Boolean cached = cache.get(pid);
        if (cached != null) return cached.booleanValue();
        boolean v = partner.canUserView(user, myShepherd);
        cache.put(pid, v);
        return v;
    }

    // Emits the DataNucleus datastore-identity string in the legacy `_id` form, so the
    // retained edit/remove buttons (which append "[OID]org.ecocean.social.Relationship")
    // resolve the same row via getObjectById. Verified by relationshipIdRoundTripsThroughGetObjectById.
    private String relationshipDatastoreId(Relationship rel) {
        Object oid = javax.jdo.JDOHelper.getObjectId(rel);
        if (oid == null) return null;
        String s = oid.toString();
        int idx = s.indexOf("[OID]");
        return (idx > -1) ? s.substring(0, idx) : s;
    }
```

> If the round-trip test fails on the id format, inspect the actual `JDOHelper.getObjectId(rel).toString()` value in the test and adjust `relationshipDatastoreId` to emit exactly the substring the legacy `_id` carried (the test is the source of truth here).

- [ ] **Step 4: Run — verify pass**

Run: `mvn test -Dtest=MarkedIndividualInfoTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 5: Normalize + commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/api/MarkedIndividualInfo.java src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java
git add -A && git commit -m "feat: social-data relationship rows with partner ACL filter + legacy-compatible _id"
```

---

### Task 6: Large-individual correctness/memoization test

**Files:**
- Test: `src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java`

**Interfaces:**
- Consumes: full `getSocialData` (Tasks 3–5).

- [ ] **Step 1: Write the test**

Seed a focal individual with several encounters across a large occurrence (many co-occurring encounters) and a relationship whose partner has many encounters. Assert the call completes and returns correctly-filtered data:

```java
    @Test void largeIndividualAssemblesCorrectly() throws Exception {
        JSONObject j = invoke(focalOwner, largeFocalIndivId);
        assertEquals(200, j.optInt("statusCode"));
        // every returned encounter is one the owner may view; occurringWith only viewable companions
        assertTrue(j.getJSONArray("encounters").length() > 0);
    }
```

- [ ] **Step 2: Run — verify pass** (logic already implemented; this guards the memoized paths)

Run: `mvn test -Dtest=MarkedIndividualInfoTest#largeIndividualAssemblesCorrectly -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 3: Run the full backend suite once**

Run: `mvn -o test -Dtest=MarkedIndividualInfoTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: all green.

- [ ] **Step 4: Commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/test/java/org/ecocean/api/MarkedIndividualInfoTest.java
git add -A && git commit -m "test: large-individual social-data assembly + memoization coverage"
```

---

### Task 7: Rewrite the diagram JS to the new endpoint

No automated harness exists for `src/main/webapp/javascript/`; verification is manual + Codex review of the diff.

**Files:**
- Modify: `src/main/webapp/javascript/bubbleDiagram/encounter-calls.js`

**Interfaces:**
- Consumes: `GET /api/v3/individuals/info/social-data?id={individualId}` → `{encounters:[…], relationships:[…]}` (Tasks 2–5).

- [ ] **Step 1: Add a single fetch + cache, and rewrite `getEncounterTableData` (line ~488)**

Replace the body of `getEncounterTableData` so it fetches the new endpoint once and builds rows from `response.encounters`, taking `occurringWith` directly from each row instead of cross-referencing `occurrenceObjectArray`. Keep the existing date-precision logic (`dateInMilliseconds`/`year`/`month`/`day`), `dataTypes`→icon mapping, and the final `makeTable(encounterData, "#encountHead", "#encountBody", "date", null)` + row-click wiring unchanged:

```javascript
var getEncounterTableData = function(occurrenceObjectArray, individualID) {
    d3.json(wildbookGlobals.baseUrl + "/api/v3/individuals/info/social-data?id=" + encodeURIComponent(individualID), function(error, json) {
        if (error) { console.log("error"); return; }
        var encounterData = [];
        var encs = (json && json.encounters) ? json.encounters : [];
        for (var i = 0; i < encs.length; i++) {
            var row = encs[i];
            var date;
            var dim = new Date(row.dateInMilliseconds);
            if (dim > 0) {
                date = dim.toISOString().substring(0, 10);
                if (row.day < 1) { date = date.substring(0, 7); }
                if (row.month < 0) { date = date.substring(0, 4); }
            } else if (row.year) {
                date = row.year;
                if (row.month) { date += "-" + row.month; }
                if (row.day) { date += "-" + row.day; }
            } else { date = dict['unknown']; }
            var encounter = {
                catalogNumber: row.catalogNumber,
                date: date,
                location: row.locationID || "",
                dataTypes: row.dataTypes || "",
                alternateID: row.alternateid,
                sex: row.sex,
                occurringWith: row.occurringWith || "",
                behavior: row.behavior
            };
            encounterData.push(encounter);
        }
        makeTable(encounterData, "#encountHead", "#encountBody", "date", null);
        $('#encountTable tr').attr("onClick", "return encountTableAuxClick(this);")
                             .attr("onAuxClick", "return encountTableAuxClick(this);")
                             .each(function() {
                                encountUrl = "encounters/encounter.jsp?number=" + ($(this).attr("class"));
                                $(this).find("td").first().wrapInner("<a href=\"" + encountUrl + "\"></a>");
                             });
    });
}
```

- [ ] **Step 2: Rewrite `getRelationshipTableData` (line ~653) and delete `getIndividualData` (line ~698)**

Build relationship rows from `response.relationships`, reading partner display from the embedded `partner` object (no per-partner fetch). Preserve the `roles`/`relationshipWith`/`edit`/`remove` row shape `makeRelTable` expects and keep `_id` for the edit/remove buttons:

```javascript
var getRelationshipTableData = function(individualID) {
    d3.json(wildbookGlobals.baseUrl + "/api/v3/individuals/info/social-data?id=" + encodeURIComponent(individualID), function(error, json) {
        if (error) { console.log("error"); return; }
        var relationshipTableData = [];
        var rels = (json && json.relationships) ? json.relationships : [];
        for (var i = 0; i < rels.length; i++) {
            var rel = rels[i];
            var relationshipID = rel._id;
            var markedIndividualRole, relationshipWithRole;
            if (rel.markedIndividualName1 != individualID) {
                markedIndividualRole = rel.markedIndividualRole2;
                relationshipWithRole = rel.markedIndividualRole1;
            } else {
                markedIndividualRole = rel.markedIndividualRole1;
                relationshipWithRole = rel.markedIndividualRole2;
            }
            var p = rel.partner || {};
            relationshipTableData.push({
                "roles": [markedIndividualRole, relationshipWithRole],
                "relationshipWith": [p.individualID, p.nickName, p.alternateid, p.sex, p.localHaplotypeReflection, p.displayName],
                "type": rel.type,
                "socialUnit": rel.relatedSocialUnitName,
                "edit": ["edit", relationshipID],
                "remove": ["remove", relationshipID]
            });
        }
        makeRelTable(relationshipTableData, "#relationshipHead", "#relationshipBody", "text");
    });
}
```

Delete the entire `getIndividualData = function (...) { ... }` definition (it is no longer called).

- [ ] **Step 3: Normalize line endings**

Run: `perl -i -pe 's/\r\n/\n/g' src/main/webapp/javascript/bubbleDiagram/encounter-calls.js && grep -cP '\r$' src/main/webapp/javascript/bubbleDiagram/encounter-calls.js`
Expected: `0`

- [ ] **Step 4: Manual verification**

Deploy/refresh, open `individuals.jsp?number=<id>` → Social Diagram tab. Confirm in the browser Network tab:
- One `GET /api/v3/individuals/info/social-data?id=…` (200), no `/api/jdoql` and no `/api/org.ecocean.*` requests.
- Encounter table populates with date/location/dataTypes icons/occurringWith/sex/behavior.
- Relationship table populates with roles/partner display; **edit and remove buttons still open/operate** (they call the unchanged legacy write path keyed by `_id`).
- An account that cannot view some encounters sees only its viewable encounters and no hidden companions in "occurring with".

- [ ] **Step 5: Codex review of the JS diff, then commit**

Run a Codex review (codex-review skill) on the `encounter-calls.js` diff before committing; address any Major/Minor findings. Then:

```bash
git add src/main/webapp/javascript/bubbleDiagram/encounter-calls.js
git commit -m "feat: Social Diagram reads via /api/v3 social-data; drop legacy /api/jdoql + per-partner gets"
```

---

## Self-Review (completed)

- **Spec coverage:** endpoint+routing (Task 2) ✓; individual gate (Task 2) ✓; per-encounter ACL (Task 3) ✓; dataTypes legacy semantics (Task 3) ✓; occurringWith ACL display-string (Task 4) ✓; relationship rows + partner ACL + legacy `_id` (Task 5) ✓; memoization + large-individual (Tasks 3–6) ✓; JS rewrite incl. removal of line 703 and retention of edit/remove (Task 7) ✓; error codes (Task 2) ✓; bubble-graph residual left untouched (no task touches line 29) ✓.
- **Placeholder scan:** all code steps contain real code; the one conditional ("if the round-trip test fails…") is guidance attached to a real implementation, not a placeholder.
- **Type consistency:** `encCanView`/`partnerCanView` cache `Map<String,Boolean>`; `occCache` `Map<String,String>`; helper names and the `getSocialData` signature are consistent across Tasks 2–6; JS field names (`alternateid`, `occurringWith`, `_id`, `partner.*`) match the endpoint output.

## Execution dependency note

Test seeding (`seededOwner`, `stranger`, `focalOwner`, the various individual ids, large/occurrence/partner fixtures) is described per task; consolidate it into `@BeforeAll`/helper methods in the test class as tasks accrete, following `EncounterExportImagesTest` and `SearchTokenScopeChildIndexTest`. Confirm how `MarkedIndividualInfo` resolves the current `User` from the request (`myShepherd.getUser(request)`) and stub that consistently in `invoke(...)`.
