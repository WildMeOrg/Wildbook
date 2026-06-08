# Token-Scoped Individual + Annotation Reads (Spec A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the token-scoped read boundary (Artifact D) from the `encounter` index to the `individual` and `annotation` indices — gated by encounter access — so an agent on a user's token sees individuals/annotations exactly as that user would on Wildbook's own pages, and an admin token sees all.

**Architecture:** The encounter is the ACL unit. We denormalize the encounter ACL (`publiclyReadable`/`submitterUserId(s)`/`viewUsers`) onto the annotation index (union over parent encounter(s)) and the individual index (union over member encounters), computed from **one** centralized source on the `Encounter` (`computeViewUsers` + `isPubliclyReadable` + submitter). The token pre-filter from D then enforces all three indices (index-aware field names); admin bypasses. Individual results pass an allowlist sanitizer (identity only); all responses get a universal ACL-field scrub. Sync triggers re-index children on ACL **and** membership changes; a one-time corrective reindex backfills existing data.

**Tech Stack:** Java 17, OpenSearch (`org.json`, Jackson `JsonGenerator` serializers, knn_vector mapping), DataNucleus JDO (`Shepherd`), Apache Shiro (D's `WildbookTokenAuthenticationFilter`), JUnit 5 + Mockito 5 (unit), embedded Jetty + Testcontainers OpenSearch (integration). Build: Maven.

**Branch / worktree:** continue on `token-auth-scoped-search` at `/mnt/c/Wildbook-token-auth` (builds on A+B+D), or branch off it. All paths relative to that worktree.

**Spec:** `docs/superpowers/specs/2026-06-07-token-scoped-individual-annotation-design.md` (Codex-reviewed).

---

## File Structure

**Modify (main):**
- `src/main/java/org/ecocean/Encounter.java` — add `opensearchAclFields(Shepherd)` single-source helper; add `findAllByAnnotation(...)`.
- `src/main/java/org/ecocean/Annotation.java` — serializer writes union ACL over parent encounter(s); mapping adds the 3 ACL fields.
- `src/main/java/org/ecocean/MarkedIndividual.java` — serializer writes union ACL over member encounters; mapping adds the 3 ACL fields.
- `src/main/java/org/ecocean/OpenSearch.java` — generalize `applyEncounterAclFilter`→`applyAclFilter(query,uuid,indexName)`; `sanitizeDoc` gains a `tokenAuth` param, a universal ACL-field scrub, and the individual allowlist.
- `src/main/java/org/ecocean/api/SearchApi.java` — widen token index allowlist; resolve effective index before the annotation-admin gate; index-aware filter; pass `tokenAuth` to `sanitizeDoc`.
- Reindex triggers: extend ACL-change enqueues + add membership-change enqueues (sites in `IndividualRemoveEncounter.java`, `Encounter.setIndividual`, `MarkedIndividual.addEncounter/removeEncounter/mergeIndividual`).

**Create (test):**
- `src/test/java/org/ecocean/EncounterAclFieldsTest.java`
- `src/test/java/org/ecocean/OpenSearchAclFilterIndexAwareTest.java` (or extend `OpenSearchAclFilterTest`)
- `src/test/java/org/ecocean/OpenSearchSanitizeDocTest.java`
- `src/test/java/org/ecocean/api/SearchApiChildIndexTest.java`
- `src/test/java/org/ecocean/api/SearchTokenScopeChildIndexTest.java` (integration; sibling of `SearchTokenScopeTest`)
- Serializer unit tests for annotation/individual ACL fields.

**Docs:**
- `docs/superpowers/runbooks/childindex-acl-reindex.md` (mapping migration + corrective reindex).

**Key verified facts (current source):**
- `Encounter.computeViewUsers(Shepherd)` → `List<String>` of user UUIDs; returns `[]` when `isPubliclyReadable()` or submitter is null/unresolved (fail-closed). (`Encounter.java:3356`). `userIdsWithViewAccess` delegates to it (`:3391`).
- `Encounter.isPubliclyReadable()` = security-disabled OR `User.isUsernameAnonymous(submitterID)` (`Encounter.java:4109`); `User.isUsernameAnonymous` = null/blank/`"N/A"`/`"public"` (`User.java:392`).
- `submitterUserId` = `getSubmitterUser(myShepherd).getId()` (`Encounter.java:4402`).
- Encounter `viewUsers` is written by the background `opensearchIndexPermissions` pass (`os.indexUpdate`, `Encounter.java:4281`), NOT normally by the serializer — but both compute the same value via `computeViewUsers`, so synchronous child computation matches.
- `MarkedIndividual.opensearchDocumentSerializer(JsonGenerator, Shepherd)` already loops `for (Encounter enc : this.encounters)` (`MarkedIndividual.java:2813`); fields emitted: `id,version,indexTimestamp,sex,displayName,taxonomy,timeOfBirth,timeOfDeath,names,nameMap,socialUnits,relationships,numberEncounters,encounterIds,users,numberOccurrences,cooccurrenceIndividualIds,cooccurrenceIndividualMap,locationGeoPoints,numberMediaAssets`. Mapping at `:2694`.
- `Annotation.opensearchDocumentSerializer(JsonGenerator, Shepherd)` (`Annotation.java:206`) resolves parent via `findEncounter(myShepherd)` → `Encounter.findByAnnotation` (`:3650`) which **returns first of possibly many, null if none**. Mapping at `:163`. Embeddings written `:241`.
- `OpenSearch.sanitizeDoc(sourceDoc, indexName, myShepherd, user)` (`:964`): annotation/individual return raw `_source` (`:969`); encounter strips `viewUsers` + sets `access` (`:972`). No `tokenAuth` param today.
- `OpenSearch.applyEncounterAclFilter(query, userId)` (`:936`) hardcodes encounter field names.
- `SearchApi.doPost` (post-D): token index gate `!"encounter".equals(...)` (`:97`); annotation-admin-only gate `"annotation".equals(indexName) && !isAdmin` (`:85`) runs BEFORE the stored-query owner/encounter gates; ACL filter call (`:129`); `sanitizeDoc` call (`:155`).
- `IndexingManager.addIndexingQueueEntry(Base, boolean unindex)` (`IndexingManager.java:67`) enqueues a reindex of a `Base` object. `Encounter.opensearchIndexDeep()` already reindexes its annotations + individual (`Encounter.java:4680`); `MarkedIndividual.opensearchIndexDeep()` reindexes its encounters. The permissions pass is **encounter-only** today.

---

### Task 1: Centralized ACL source on `Encounter`

One method that returns the three ACL fields, so encounter/annotation/individual indexing agree and the anonymous-vs-invalid-owner distinction lives in one place.

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java`
- Test: `src/test/java/org/ecocean/EncounterAclFieldsTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class EncounterAclFieldsTest {

    // A private (non-anonymous, valid submitter) encounter -> publiclyReadable=false,
    // submitterUserId set, viewUsers = computeViewUsers result.
    @Test void privateEncounter_emitsSubmitterAndViewUsers() {
        Encounter enc = spy(new Encounter());
        Shepherd sh = mock(Shepherd.class);
        User submitter = mock(User.class);
        when(submitter.getId()).thenReturn("owner-uuid");
        doReturn(false).when(enc).isPubliclyReadable();
        doReturn(submitter).when(enc).getSubmitterUser(sh);
        doReturn(Arrays.asList("collab-uuid")).when(enc).computeViewUsers(sh);

        JSONObject acl = enc.opensearchAclFields(sh);
        assertFalse(acl.getBoolean("publiclyReadable"), "private -> not public");
        assertEquals("owner-uuid", acl.getString("submitterUserId"), "submitter uuid");
        assertEquals(1, acl.getJSONArray("viewUsers").length(), "one viewUser");
        assertEquals("collab-uuid", acl.getJSONArray("viewUsers").getString(0));
    }

    // An anonymous/public encounter -> publiclyReadable=true, no submitterUserId, empty viewUsers.
    @Test void publicEncounter_isWorldReadable() {
        Encounter enc = spy(new Encounter());
        Shepherd sh = mock(Shepherd.class);
        doReturn(true).when(enc).isPubliclyReadable();
        JSONObject acl = enc.opensearchAclFields(sh);
        assertTrue(acl.getBoolean("publiclyReadable"), "anonymous -> world readable");
        assertFalse(acl.has("submitterUserId"), "no submitter on public");
        assertEquals(0, acl.getJSONArray("viewUsers").length(), "no viewUsers on public");
    }

    // Invalid/deleted (non-anonymous) submitter -> fail closed: not public, no submitter, empty viewUsers (admin-only).
    @Test void invalidOwner_failsClosed() {
        Encounter enc = spy(new Encounter());
        Shepherd sh = mock(Shepherd.class);
        doReturn(false).when(enc).isPubliclyReadable();
        doReturn(null).when(enc).getSubmitterUser(sh);
        doReturn(java.util.Collections.emptyList()).when(enc).computeViewUsers(sh);
        JSONObject acl = enc.opensearchAclFields(sh);
        assertFalse(acl.getBoolean("publiclyReadable"), "not public");
        assertFalse(acl.has("submitterUserId"), "no resolvable submitter -> admin-only");
        assertEquals(0, acl.getJSONArray("viewUsers").length(), "no viewUsers -> admin-only");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=EncounterAclFieldsTest`
Expected: FAIL — `opensearchAclFields` not defined.

- [ ] **Step 3: Implement `opensearchAclFields`**

Add to `Encounter.java` (near `computeViewUsers`, ~`:3389`):

```java
    /**
     * Single source of this encounter's OpenSearch ACL fields, reused by the encounter,
     * annotation, and individual indexing so all three agree. publiclyReadable mirrors
     * isPubliclyReadable() (security-disabled OR anonymous owner). For a non-public encounter:
     * submitterUserId is the resolved owner's UUID (absent if the owner is invalid/deleted ->
     * admin-only/fail-closed), and viewUsers is computeViewUsers() (approved/edit collaborators +
     * submitter-org orgAdmins). Anonymous-owned -> public; invalid/deleted non-anonymous owner -> closed.
     */
    public org.json.JSONObject opensearchAclFields(Shepherd myShepherd) {
        org.json.JSONObject acl = new org.json.JSONObject();
        boolean pub = this.isPubliclyReadable();
        acl.put("publiclyReadable", pub);
        org.json.JSONArray vu = new org.json.JSONArray();
        if (!pub) {
            User submitter = this.getSubmitterUser(myShepherd);
            if ((submitter != null) && (submitter.getId() != null))
                acl.put("submitterUserId", submitter.getId());
            for (String id : this.computeViewUsers(myShepherd)) vu.put(id);
        }
        acl.put("viewUsers", vu);
        return acl;
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=EncounterAclFieldsTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/EncounterAclFieldsTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/EncounterAclFieldsTest.java
git commit -m "feat(acl): single-source Encounter.opensearchAclFields for child-index denormalization"
```
(End every commit message with: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`)

---

### Task 2: Annotation index — ACL fields (union over parent encounter(s))

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java` (add `findAllByAnnotation`), `src/main/java/org/ecocean/Annotation.java`
- Test: `src/test/java/org/ecocean/AnnotationAclSerializerTest.java` (create)

- [ ] **Step 1: Add `Encounter.findAllByAnnotation` (return ALL parents, not just the first)**

In `Encounter.java`, next to `findByAnnotation` (~`:3650`):

```java
    /** All encounters whose annotations contain this annotation (usually 0 or 1; >1 is anomalous). */
    public static java.util.List<Encounter> findAllByAnnotation(Annotation annot, Shepherd myShepherd) {
        String queryString =
            "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.id == '"
            + annot.getId() + "'";
        javax.jdo.Query query = myShepherd.getPM().newQuery(queryString);
        java.util.List<Encounter> out = new java.util.ArrayList<Encounter>();
        try {
            java.util.List results = (java.util.List) query.execute();
            if (results != null) for (Object o : results) out.add((Encounter) o);
        } finally {
            query.closeAll();
        }
        return out;
    }
```

- [ ] **Step 2: Write the failing serializer test**

`src/test/java/org/ecocean/AnnotationAclSerializerTest.java`:

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.util.Arrays;
import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class AnnotationAclSerializerTest {

    private JSONObject serialize(Annotation ann, Shepherd sh) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        ann.writeAclFields(jg, sh); // the new helper under test (writes only the ACL fields)
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    @Test void unionOverParents_privatePlusPublic() throws Exception {
        Annotation ann = spy(new Annotation());
        Shepherd sh = mock(Shepherd.class);
        Encounter encPrivate = mock(Encounter.class);
        Encounter encPublic = mock(Encounter.class);
        when(encPrivate.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":false,\"submitterUserId\":\"u1\",\"viewUsers\":[\"v1\"]}"));
        when(encPublic.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":true,\"viewUsers\":[]}"));
        doReturn(Arrays.asList(encPrivate, encPublic)).when(ann).parentEncounters(sh);

        JSONObject out = serialize(ann, sh);
        assertTrue(out.getBoolean("publiclyReadable"), "any public parent -> public");
        assertTrue(out.getJSONArray("submitterUserIds").toList().contains("u1"));
        assertTrue(out.getJSONArray("viewUsers").toList().contains("v1"));
    }

    @Test void zeroParents_failsClosed() throws Exception {
        Annotation ann = spy(new Annotation());
        Shepherd sh = mock(Shepherd.class);
        doReturn(java.util.Collections.emptyList()).when(ann).parentEncounters(sh);
        JSONObject out = serialize(ann, sh);
        assertFalse(out.getBoolean("publiclyReadable"), "no parent -> not public");
        assertEquals(0, out.getJSONArray("submitterUserIds").length(), "no parent -> admin-only");
        assertEquals(0, out.getJSONArray("viewUsers").length(), "no parent -> admin-only");
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn test -Dtest=AnnotationAclSerializerTest`
Expected: FAIL — `parentEncounters`/`writeAclFields` not defined.

- [ ] **Step 4: Implement the helpers + wire into the serializer + mapping**

In `Annotation.java`, add:

```java
    /** All parent encounters of this annotation (0 = orphan; >1 = anomalous). */
    public java.util.List<Encounter> parentEncounters(Shepherd myShepherd) {
        return Encounter.findAllByAnnotation(this, myShepherd);
    }

    /** Write the denormalized ACL union over parent encounter(s); 0 parents -> fail closed (admin-only). */
    public void writeAclFields(com.fasterxml.jackson.core.JsonGenerator jgen, Shepherd myShepherd)
    throws java.io.IOException {
        boolean pub = false;
        java.util.Set<String> submitters = new java.util.LinkedHashSet<String>();
        java.util.Set<String> viewers = new java.util.LinkedHashSet<String>();
        for (Encounter enc : this.parentEncounters(myShepherd)) {
            org.json.JSONObject acl = enc.opensearchAclFields(myShepherd);
            if (acl.optBoolean("publiclyReadable", false)) pub = true;
            String sid = acl.optString("submitterUserId", null);
            if (sid != null) submitters.add(sid);
            org.json.JSONArray vu = acl.optJSONArray("viewUsers");
            if (vu != null) for (int i = 0; i < vu.length(); i++) viewers.add(vu.optString(i));
        }
        jgen.writeBooleanField("publiclyReadable", pub);
        jgen.writeArrayFieldStart("submitterUserIds");
        for (String s : submitters) jgen.writeString(s);
        jgen.writeEndArray();
        jgen.writeArrayFieldStart("viewUsers");
        for (String v : viewers) jgen.writeString(v);
        jgen.writeEndArray();
    }
```

Then call it inside `opensearchDocumentSerializer` (after the `encounter*` fields, before the `embeddings` array at `:241`):

```java
        this.writeAclFields(jgen, myShepherd);
```

And add the mapping fields in `opensearchMapping()` (before the `embeddings` block, ~`:182`):

```java
        map.put("publiclyReadable", new JSONObject("{\"type\": \"boolean\"}"));
        map.put("submitterUserIds", keywordType);
        map.put("viewUsers", keywordType);
```

- [ ] **Step 5: Run to verify it passes**

Run: `mvn test -Dtest=AnnotationAclSerializerTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/Annotation.java src/test/java/org/ecocean/AnnotationAclSerializerTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/Annotation.java src/test/java/org/ecocean/AnnotationAclSerializerTest.java
git commit -m "feat(acl): denormalize encounter ACL union onto annotation index"
```

---

### Task 3: Individual index — ACL fields (union over member encounters)

**Files:**
- Modify: `src/main/java/org/ecocean/MarkedIndividual.java`
- Test: `src/test/java/org/ecocean/MarkedIndividualAclSerializerTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class MarkedIndividualAclSerializerTest {

    private JSONObject serialize(MarkedIndividual mi, Shepherd sh) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        mi.writeAclFields(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    @Test void unionOverMembers() throws Exception {
        MarkedIndividual mi = spy(new MarkedIndividual());
        Shepherd sh = mock(Shepherd.class);
        Encounter e1 = mock(Encounter.class);
        Encounter e2 = mock(Encounter.class);
        when(e1.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":false,\"submitterUserId\":\"u1\",\"viewUsers\":[\"v1\"]}"));
        when(e2.opensearchAclFields(sh)).thenReturn(new JSONObject(
            "{\"publiclyReadable\":false,\"submitterUserId\":\"u2\",\"viewUsers\":[\"v2\"]}"));
        doReturn(java.util.Arrays.asList(e1, e2)).when(mi).getEncounters();

        JSONObject out = serialize(mi, sh);
        assertFalse(out.getBoolean("publiclyReadable"));
        assertTrue(out.getJSONArray("submitterUserIds").toList().containsAll(java.util.Arrays.asList("u1","u2")));
        assertTrue(out.getJSONArray("viewUsers").toList().containsAll(java.util.Arrays.asList("v1","v2")));
    }

    @Test void zeroEncounters_worldReadable() throws Exception {
        MarkedIndividual mi = spy(new MarkedIndividual());
        Shepherd sh = mock(Shepherd.class);
        doReturn(java.util.Collections.emptyList()).when(mi).getEncounters();
        JSONObject out = serialize(mi, sh);
        assertTrue(out.getBoolean("publiclyReadable"),
            "encounterless individual -> visible to anyone (matches canUserAccessMarkedIndividual)");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=MarkedIndividualAclSerializerTest`
Expected: FAIL — `writeAclFields` not defined.

- [ ] **Step 3: Implement `writeAclFields` + wire into serializer + mapping**

In `MarkedIndividual.java`, add:

```java
    /** Denormalized ACL union over member encounters; 0 encounters -> world-readable (matches the live gate). */
    public void writeAclFields(com.fasterxml.jackson.core.JsonGenerator jgen, Shepherd myShepherd)
    throws java.io.IOException {
        java.util.List<Encounter> encs = this.getEncounters();
        boolean pub = (encs == null) || encs.isEmpty(); // encounterless -> visible to anyone
        java.util.Set<String> submitters = new java.util.LinkedHashSet<String>();
        java.util.Set<String> viewers = new java.util.LinkedHashSet<String>();
        if (encs != null) {
            for (Encounter enc : encs) {
                org.json.JSONObject acl = enc.opensearchAclFields(myShepherd);
                if (acl.optBoolean("publiclyReadable", false)) pub = true;
                String sid = acl.optString("submitterUserId", null);
                if (sid != null) submitters.add(sid);
                org.json.JSONArray vu = acl.optJSONArray("viewUsers");
                if (vu != null) for (int i = 0; i < vu.length(); i++) viewers.add(vu.optString(i));
            }
        }
        jgen.writeBooleanField("publiclyReadable", pub);
        jgen.writeArrayFieldStart("submitterUserIds");
        for (String s : submitters) jgen.writeString(s);
        jgen.writeEndArray();
        jgen.writeArrayFieldStart("viewUsers");
        for (String v : viewers) jgen.writeString(v);
        jgen.writeEndArray();
    }
```

Call it at the top of `opensearchDocumentSerializer` (right after `super.opensearchDocumentSerializer(...)`, ~`:2725`):

```java
        this.writeAclFields(jgen, myShepherd);
```

> Note: `getEncounters()` returns the live member list (already loaded as `this.encounters`); `enc.opensearchAclFields` calls `computeViewUsers` per member. This is the per-doc cost A's bulk pass avoids; see Task 8's reindex-cost note. Acceptable for a one-time reindex + incremental updates; an optional bulk optimization is a documented follow-up.

Add to `opensearchMapping()` (~`:2719`, before `return map;`):

```java
        map.put("publiclyReadable", new org.json.JSONObject("{\"type\": \"boolean\"}"));
        map.put("submitterUserIds", keywordType);
        // viewUsers is already mapped as keyword in Base.opensearchMapping()
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=MarkedIndividualAclSerializerTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/MarkedIndividual.java src/test/java/org/ecocean/MarkedIndividualAclSerializerTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/MarkedIndividual.java src/test/java/org/ecocean/MarkedIndividualAclSerializerTest.java
git commit -m "feat(acl): denormalize encounter ACL union onto individual index (0 encs -> world-readable)"
```

---

### Task 4: Index-aware ACL pre-filter (`applyAclFilter`)

**Files:**
- Modify: `src/main/java/org/ecocean/OpenSearch.java`
- Test: `src/test/java/org/ecocean/OpenSearchAclFilterIndexAwareTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OpenSearchAclFilterIndexAwareTest {

    private JSONArray shoulds(JSONObject out) {
        return out.getJSONObject("query").getJSONObject("bool")
            .getJSONArray("filter").getJSONObject(0).getJSONObject("bool").getJSONArray("should");
    }

    @Test void encounterUsesSubmitterUserId() throws Exception {
        JSONObject out = OpenSearch.applyAclFilter(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), "u1", "encounter");
        assertEquals("u1", shoulds(out).getJSONObject(1).getJSONObject("term").getString("submitterUserId"));
    }

    @Test void individualUsesSubmitterUserIds() throws Exception {
        JSONObject out = OpenSearch.applyAclFilter(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), "u1", "individual");
        assertEquals("u1", shoulds(out).getJSONObject(1).getJSONObject("term").getString("submitterUserIds"));
    }

    @Test void annotationUsesSubmitterUserIds() throws Exception {
        JSONObject out = OpenSearch.applyAclFilter(
            new JSONObject("{\"query\":{\"match_all\":{}}}"), "u1", "annotation");
        assertEquals("u1", shoulds(out).getJSONObject(1).getJSONObject("term").getString("submitterUserIds"));
    }

    @Test void rejectsNullUser() {
        assertThrows(java.io.IOException.class,
            () -> OpenSearch.applyAclFilter(new JSONObject("{\"query\":{\"match_all\":{}}}"), null, "encounter"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=OpenSearchAclFilterIndexAwareTest`
Expected: FAIL — `applyAclFilter` not defined.

- [ ] **Step 3: Generalize the filter**

In `OpenSearch.java`, replace `applyEncounterAclFilter` with a delegator and add `applyAclFilter`:

```java
    public static JSONObject applyEncounterAclFilter(JSONObject query, String userId)
    throws IOException {
        return applyAclFilter(query, userId, "encounter");
    }

    public static JSONObject applyAclFilter(JSONObject query, String userId, String indexName)
    throws IOException {
        if ((query == null) || !Util.stringExists(userId))
            throw new IOException("applyAclFilter: null query or userId");
        // encounter docs carry a single submitterUserId; annotation/individual carry the union set submitterUserIds
        String submitterField = "encounter".equals(indexName) ? "submitterUserId" : "submitterUserIds";
        JSONArray should = new JSONArray();
        should.put(new JSONObject().put("term", new JSONObject().put("publiclyReadable", true)));
        should.put(new JSONObject().put("term", new JSONObject().put(submitterField, userId)));
        should.put(new JSONObject().put("term", new JSONObject().put("viewUsers", userId)));
        JSONObject aclBool = new JSONObject();
        aclBool.put("should", should);
        aclBool.put("minimum_should_match", 1);
        JSONObject acl = new JSONObject().put("bool", aclBool);

        JSONObject wrapBool = new JSONObject();
        JSONObject inner = query.optJSONObject("query");
        if (inner != null) {
            JSONArray must = new JSONArray();
            must.put(inner);
            wrapBool.put("must", must);
        }
        wrapBool.put("filter", new JSONArray().put(acl));

        JSONObject out = new JSONObject(query.toString());
        out.put("query", new JSONObject().put("bool", wrapBool));
        return out;
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=OpenSearchAclFilterIndexAwareTest,OpenSearchAclFilterTest`
Expected: PASS (the new 4 + D's original 3 still green via the delegator).

- [ ] **Step 5: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchAclFilterIndexAwareTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchAclFilterIndexAwareTest.java
git commit -m "feat(search): index-aware applyAclFilter (submitterUserId vs submitterUserIds)"
```

---

### Task 5: `sanitizeDoc` — universal ACL scrub + token-aware individual allowlist

**Files:**
- Modify: `src/main/java/org/ecocean/OpenSearch.java`
- Test: `src/test/java/org/ecocean/OpenSearchSanitizeDocTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.json.JSONObject;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class OpenSearchSanitizeDocTest {

    private User user(boolean admin) {
        User u = mock(User.class);
        when(u.getId()).thenReturn("u1");
        return u;
    }

    @Test void annotation_stripsAclFields_keepsContent() throws Exception {
        JSONObject doc = new JSONObject("{\"id\":\"a1\",\"viewpoint\":\"left\",\"embeddings\":[{}],"
            + "\"publiclyReadable\":true,\"submitterUserIds\":[\"x\"],\"viewUsers\":[\"y\"]}");
        Shepherd sh = mock(Shepherd.class);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "annotation", sh, user(false), true);
        assertEquals("left", out.getString("viewpoint"), "content kept");
        assertTrue(out.has("embeddings"), "embeddings kept (not precious)");
        assertFalse(out.has("viewUsers"), "ACL fields scrubbed");
        assertFalse(out.has("submitterUserIds"), "ACL fields scrubbed");
        assertFalse(out.has("publiclyReadable"), "ACL fields scrubbed");
    }

    @Test void individualToken_allowlistOnly() throws Exception {
        JSONObject doc = new JSONObject("{\"id\":\"i1\",\"displayName\":\"Fluke\",\"sex\":\"female\","
            + "\"taxonomy\":\"x\",\"numberEncounters\":42,\"users\":[\"owner\"],"
            + "\"encounterIds\":[\"e1\"],\"locationGeoPoints\":[{}],\"viewUsers\":[\"y\"]}");
        Shepherd sh = mock(Shepherd.class);
        User u = user(false);
        when(u.isAdmin(sh)).thenReturn(false);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "individual", sh, u, true);
        assertEquals("Fluke", out.getString("displayName"), "identity kept");
        assertEquals("female", out.getString("sex"), "identity kept");
        assertFalse(out.has("numberEncounters"), "aggregate dropped");
        assertFalse(out.has("users"), "aggregate dropped (would leak hidden submitters)");
        assertFalse(out.has("encounterIds"), "aggregate dropped");
        assertFalse(out.has("locationGeoPoints"), "aggregate dropped");
        assertFalse(out.has("viewUsers"), "ACL field scrubbed");
    }

    @Test void individualAdminToken_fullDocMinusAcl() throws Exception {
        JSONObject doc = new JSONObject("{\"id\":\"i1\",\"numberEncounters\":42,\"viewUsers\":[\"y\"]}");
        Shepherd sh = mock(Shepherd.class);
        User u = user(true);
        when(u.isAdmin(sh)).thenReturn(true);
        JSONObject out = OpenSearch.sanitizeDoc(doc, "individual", sh, u, true);
        assertEquals(42, out.getInt("numberEncounters"), "admin keeps aggregates");
        assertFalse(out.has("viewUsers"), "ACL still scrubbed even for admin");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest=OpenSearchSanitizeDocTest`
Expected: FAIL — `sanitizeDoc` has no 5-arg (tokenAuth) overload.

- [ ] **Step 3: Implement**

In `OpenSearch.java`, add the constants + a universal scrub + the allowlist, and a new 5-arg `sanitizeDoc`; keep the 4-arg as a delegator:

```java
    private static final String[] ACL_FIELDS = {
        "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"
    };
    // identity fields kept for a non-admin token individual hit; everything else dropped (allowlist)
    private static final String[] INDIVIDUAL_TOKEN_KEEP = {
        "id", "version", "indexTimestamp", "displayName", "names", "nameMap",
        "sex", "taxonomy", "timeOfBirth", "timeOfDeath"
    };

    private static void scrubAclFields(JSONObject doc) {
        for (String f : ACL_FIELDS) doc.remove(f);
    }

    // 4-arg overload preserved for existing callers (non-token path)
    public static JSONObject sanitizeDoc(final JSONObject sourceDoc, String indexName,
        Shepherd myShepherd, User user)
    throws IOException {
        return sanitizeDoc(sourceDoc, indexName, myShepherd, user, false);
    }

    public static JSONObject sanitizeDoc(final JSONObject sourceDoc, String indexName,
        Shepherd myShepherd, User user, boolean tokenAuth)
    throws IOException {
        if ((user == null) || (sourceDoc == null)) throw new IOException("null user or sourceDoc");
        if ("annotation".equals(indexName)) {
            JSONObject clean = new JSONObject(sourceDoc.toString());
            scrubAclFields(clean);             // never leak the internal ACL fields
            return clean;                      // content (incl. embeddings) returned as-is
        }
        if ("individual".equals(indexName)) {
            boolean admin = user.isAdmin(myShepherd);
            if (tokenAuth && !admin) {
                JSONObject clean = new JSONObject();
                for (String f : INDIVIDUAL_TOKEN_KEEP) {
                    if (sourceDoc.has(f)) clean.put(f, sourceDoc.get(f));
                }
                return clean;                  // allowlist: identity only, aggregates dropped
            }
            JSONObject clean = new JSONObject(sourceDoc.toString());
            scrubAclFields(clean);
            return clean;
        }
        // these we return some kinda cleaned value
        JSONObject clean = new JSONObject();
        if ("encounter".equals(indexName)) {
            boolean hasAccess = Encounter.opensearchAccess(sourceDoc, user, myShepherd);
            if (hasAccess) {
                clean = new JSONObject(sourceDoc.toString());
                scrubAclFields(clean);
                clean.put("access", "full");
                return clean;
            }
            clean.put("access", "none");
            String[] okFields = new String[] {
                "id", "version", "indexTimestamp", "version", "individualId",
                    "individualDisplayName", "occurrenceId", "otherCatalogNumbers", "dateSubmitted",
                    "date", "locationId", "locationName", "taxonomy", "assignedUsername",
                    "numberAnnotations"
            };
            for (String fieldName : okFields) {
                if (sourceDoc.has(fieldName)) clean.put(fieldName, sourceDoc.get(fieldName));
            }
        } else if ("occurrence".equals(indexName)) {
            boolean hasAccess = user.isAdmin(myShepherd) || hasAccessOccurrence(user, sourceDoc);
            if (hasAccess) {
                clean = new JSONObject(sourceDoc.toString());
                scrubAclFields(clean);
                clean.put("access", "full");
            } else {
                clean = new JSONObject();
                clean.put("id", sourceDoc.optString("id", "unknown"));
                clean.put("access", "none");
            }
        }
        return clean;
    }
```

(This replaces the old `:964-1008` body; the encounter branch now uses `scrubAclFields` instead of only `clean.remove("viewUsers")` — equivalent + safer. The early `annotation/individual return sourceDoc` at `:969` is gone.)

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -Dtest=OpenSearchSanitizeDocTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchSanitizeDocTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/OpenSearch.java src/test/java/org/ecocean/OpenSearchSanitizeDocTest.java
git commit -m "feat(search): universal ACL-field scrub + token individual allowlist in sanitizeDoc"
```

---

### Task 6: `SearchApi` — widen token indices, effective-index ordering, index-aware filter, token-aware sanitize

**Files:**
- Modify: `src/main/java/org/ecocean/api/SearchApi.java`
- Test: `src/test/java/org/ecocean/api/SearchApiChildIndexTest.java` (create)

- [ ] **Step 1: Read the current ladder** (`SearchApi.java:60-130`) to confirm the anchors from the File Structure facts (annotation-admin gate `:85`, encounter-only token gate `:97`, ACL call `:129`, sanitizeDoc `:155`).

- [ ] **Step 2: Edit A — widen the token index gate.** Replace the encounter-only token gate (`:97`):

```java
                } else if (tokenAuth && !"encounter".equals(EFFECTIVE_INDEX)
                    && !"annotation".equals(EFFECTIVE_INDEX)
                    && !"individual".equals(EFFECTIVE_INDEX)) {
                    response.setStatus(403);
                    res.put("error", "token search is limited to encounter, annotation, individual");
```

where `EFFECTIVE_INDEX` is the resolved index expression already used there: `(searchQueryId != null) ? query.optString("indexName", null) : indexName`. Introduce a local `String effectiveIndex = (searchQueryId != null) ? query.optString("indexName", null) : indexName;` right after the stored query is loaded (before the guard ladder) and use it in BOTH this gate and the annotation-admin gate (Edit B).

- [ ] **Step 3: Edit B — resolve effective index before the annotation-admin gate (Codex Medium).** Change the existing annotation-admin gate (`:85`) so it (a) uses `effectiveIndex` not the raw `indexName`, and (b) applies only on the **non-token** path (token path is governed by the ACL filter):

```java
                } else if (!tokenAuth && "annotation".equals(effectiveIndex) && !isAdmin) {
                    // session path: annotation API remains admin-only (token path uses the ACL filter)
                    response.setStatus(403);
                    res.put("error", 403);
```

- [ ] **Step 4: Edit C — index-aware ACL injection.** Replace the ACL call (`:129`):

```java
                    if (tokenAuth && !isAdmin) {
                        query = OpenSearch.applyAclFilter(query, currentUser.getId(), indexName);
                    }
```

- [ ] **Step 5: Edit D — pass tokenAuth to sanitizeDoc.** Replace the sanitize call (`:155`):

```java
                            hitsArr.put(OpenSearch.sanitizeDoc(doc, indexName, myShepherd,
                                currentUser, tokenAuth));
```

- [ ] **Step 6: Write the test**

`src/test/java/org/ecocean/api/SearchApiChildIndexTest.java` — mirror `SearchApiTokenAuthTest`'s Mockito pattern (mock request/response, `mockStatic(ServletUtilities)` for `getContext`/`jsonFromHttpServletRequest`, `mockConstruction(Shepherd)` returning the user, `mockConstruction(OpenSearch)` capturing `queryPit`'s query arg). Tests:

```java
    // token POST /individual (non-admin) -> allowed, ACL filter uses submitterUserIds, results allowlisted
    @Test void tokenIndividualSearch_injectsSubmitterUserIdsFilter() throws Exception { /* assert
        the query passed to queryPit has filter.should containing term submitterUserIds; status 200 */ }

    // token POST /annotation (non-admin) -> allowed, ACL filter applied
    @Test void tokenAnnotationSearch_allowedAndFiltered() throws Exception { /* status 200, filter present */ }

    // token POST /occurrence -> still 403
    @Test void tokenOccurrenceSearch_returns403() throws Exception { /* status 403 */ }

    // session (non-token) GET annotation by stored-query whose indexName=annotation, non-admin -> 403
    // (effective-index resolved before the admin gate)
    @Test void sessionStoredAnnotationQuery_nonAdmin_403() throws Exception { /* status 403 */ }

    // session (non-token) POST /individual, non-admin -> NOT blocked by token gate (proceeds)
    @Test void sessionIndividualSearch_notBlocked() throws Exception { /* status 200 */ }
```

(Use the `EMPTY_HITS` + `mockConstruction<OpenSearch>` capture pattern from `SearchApiTokenAuthTest`; for the individual ACL-filter assertion, check `q.getJSONObject("query").getJSONObject("bool").getJSONArray("filter")...` contains a `submitterUserIds` term.)

- [ ] **Step 7: Run**

Run: `mvn test -Dtest=SearchApiChildIndexTest,SearchApiTokenAuthTest`
Expected: PASS (new cases + D's existing token tests unchanged). Iterate on SearchApi (not tests) until green.

- [ ] **Step 8: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/api/SearchApi.java src/test/java/org/ecocean/api/SearchApiChildIndexTest.java | xargs -r sed -i 's/\r$//'
git add src/main/java/org/ecocean/api/SearchApi.java src/test/java/org/ecocean/api/SearchApiChildIndexTest.java
git commit -m "feat(search): token-expose annotation+individual (effective-index gate, index-aware filter, token sanitize)"
```

---

### Task 7: Reindex triggers — ACL-change and membership-change propagation to children

When an encounter's ACL changes, its annotations + individual must reindex. When encounter↔individual membership changes (reassign/remove/merge/split), **both** old and new individuals (and the moved encounter's annotations) must reindex.

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java` (`setIndividual`), `src/main/java/org/ecocean/MarkedIndividual.java` (`addEncounter`/`removeEncounter`/`mergeIndividual`), `src/main/java/org/ecocean/servlet/IndividualRemoveEncounter.java`, and the ACL-change path (`Encounter.opensearchIndexPermissions` or its enqueue points).
- Test: `src/test/java/org/ecocean/ChildReindexTriggerTest.java` (create)

- [ ] **Step 1: Add a reindex-fanout helper on `Encounter`.** In `Encounter.java`:

```java
    /** Enqueue reindex of this encounter's individual + annotations (children whose denormalized ACL derives from it). */
    public void enqueueChildAclReindex(Shepherd myShepherd) {
        try {
            IndexingManager im = IndexingManagerFactory.getIndexingManager();
            MarkedIndividual ind = this.getIndividual();
            if (ind != null) im.addIndexingQueueEntry(ind, false);
            if (this.getAnnotations() != null)
                for (Annotation ann : this.getAnnotations()) im.addIndexingQueueEntry(ann, false);
        } catch (Exception ex) {
            System.out.println("enqueueChildAclReindex failed for enc " + this.getId() + ": " + ex);
        }
    }
```

- [ ] **Step 2: ACL-change propagation.** In `Encounter.opensearchIndexPermissions` (the bulk pass that updates encounter `viewUsers`, `Encounter.java:~4281`), after a successful `os.indexUpdate("encounter", id, updateData)` for an encounter whose `viewUsers` changed, also enqueue child reindex for that encounter (resolve the `Encounter` object as the invalid-owner branch already does at `:4231`). Because the permissions pass is bulk and may touch many encounters, enqueue child reindex only when the computed `viewUsers`/public actually changed for that doc (compare against the prior indexed value if available, else enqueue). Document this as best-effort, consistent with A.

> Implementation note for the engineer: verify whether `IndexingManager.scheduleIndexingJob` reindexes shallowly (`opensearchIndex()`) or deeply. If shallow, enqueuing the `MarkedIndividual` + each `Annotation` (as above) is correct (each re-serializes itself, recomputing its own ACL union). If it deep-indexes, guard against redundant fan-out. Confirm against `IndexingManager.java` before finalizing.

- [ ] **Step 3: Membership-change hooks (capture OLD + NEW).** At each mutation site, enqueue reindex of the old and new individual (+ the encounter's annotations):
  - `Encounter.setIndividual(MarkedIndividual indiv)` (`:1245`): capture `MarkedIndividual old = this.individual;` before reassigning; after, enqueue reindex for both `old` and `indiv` (if non-null) and this encounter's annotations.
  - `MarkedIndividual.removeEncounter` (`:513`) and `addEncounter` (`:468`): enqueue reindex of `this` individual + the encounter's annotations.
  - `MarkedIndividual.mergeIndividual` (`:2608`): after the merge loop, enqueue reindex of `this` (primary) and `other` (secondary, now empty) + all moved encounters' annotations.
  - `IndividualRemoveEncounter.java` (`:60`,`:74`): the servlet already mutates via `setIndividual(null)`/`removeEncounter`; ensure the enqueue fires (it will, if the hooks are in the model methods — prefer hooking the model methods over the servlet so all callers are covered).

  Each hook calls a small helper, e.g. on `MarkedIndividual`:

```java
    void enqueueAclReindex() {
        try { IndexingManagerFactory.getIndexingManager().addIndexingQueueEntry(this, false); }
        catch (Exception ex) { System.out.println("MarkedIndividual.enqueueAclReindex failed " + this.getId() + ": " + ex); }
    }
```

  Prefer placing enqueues in the **model** methods (`setIndividual`/`add`/`removeEncounter`/`mergeIndividual`) so every caller (servlets, bulk import, merge tools) is covered, not just `IndividualRemoveEncounter`.

- [ ] **Step 4: Test (model-level, Mockito).** `ChildReindexTriggerTest` — use `mockStatic(IndexingManagerFactory)` returning a mock `IndexingManager`; call `Encounter.setIndividual(newInd)` on an encounter whose old individual is `oldInd`; verify `addIndexingQueueEntry(oldInd,false)` AND `addIndexingQueueEntry(newInd,false)` were called. Similar for `removeEncounter`/`mergeIndividual`. (Where a method's internals make pure unit testing hard, assert the helper is invoked and cover the end-to-end propagation in Task 9's integration test.)

- [ ] **Step 5: Run + commit**

Run: `mvn test -Dtest=ChildReindexTriggerTest`
Expected: PASS.
```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/MarkedIndividual.java src/main/java/org/ecocean/servlet/IndividualRemoveEncounter.java src/test/java/org/ecocean/ChildReindexTriggerTest.java | xargs -r sed -i 's/\r$//'
git add -A
git commit -m "feat(acl): reindex child indices on encounter ACL + individual-membership changes"
```

---

### Task 8: Mapping migration + corrective reindex runbook

**Files:**
- Create: `docs/superpowers/runbooks/childindex-acl-reindex.md`

- [ ] **Step 1: Write the runbook** with these operator steps (no code change — the mappings in Tasks 2/3 apply to fresh indices; existing installs need the additive mapping PUT + a reindex):
  1. **Add the new fields to the live mappings** (additive; OpenSearch allows new fields on an existing mapping). For each of `annotation`, `individual`: `PUT /<index>/_mapping {"properties":{"publiclyReadable":{"type":"boolean"},"submitterUserIds":{"type":"keyword"},"viewUsers":{"type":"keyword"}}}` (individual `viewUsers` already exists via Base).
  2. **Corrective reindex, in order** (analogue of A's `viewUsers` corrective pass): (a) encounter anonymous-owner correction / full encounter pass; (b) **full annotation reindex** — the long pole (multiple annotations per encounter; can be 100K+ on large installs); (c) full individual reindex. Use the existing full-reindex tooling.
  3. **Fail-closed note:** until step 2 completes for an index, the new ACL fields are absent → non-admin token reads on that index return nothing (never leaky). Order doesn't risk exposure.
  4. **Reindex cost note (engineering):** the child serializers compute `computeViewUsers` per parent encounter synchronously (the path A's bulk pass avoids for scale). On large installs this makes the one-time annotation/individual reindex expensive. If measured prohibitive, a follow-up optimization is to extend A's bulk `opensearchIndexPermissions` map-building pass to also write child `viewUsers` via `indexUpdate` (deferred; not required for correctness).

- [ ] **Step 2: Commit**

```bash
cd /mnt/c/Wildbook-token-auth
git add docs/superpowers/runbooks/childindex-acl-reindex.md
git commit -m "docs(runbook): child-index ACL mapping migration + corrective reindex"
```

---

### Task 9: End-to-end integration test (real OpenSearch)

**Files:**
- Create: `src/test/java/org/ecocean/api/SearchTokenScopeChildIndexTest.java` (sibling of `SearchTokenScopeTest`)
- Modify: `src/test/resources/shiro.ini` (the token filter is already wired from D; no change needed — the same `/api/v3/search/**` rule covers annotation/individual)

- [ ] **Step 1: Write the integration test** modeled on `SearchTokenScopeTest`'s `@BeforeAll` (Testcontainers OpenSearch+Postgres, embedded Jetty + `SearchApi`, JWT keypair in `CommonConfiguration`, REST Assured). Seeding: index, with the **real** mappings, encounters with mixed ACLs + their annotations (with `publiclyReadable`/`submitterUserIds`/`viewUsers`) + a shared individual spanning multiple owners (with the union fields). Then with a non-admin token (`testy`-style):

```java
    @Test void tokenAnnotationSearch_scopedToVisibleEncounters() throws Exception {
        // match_all on /api/v3/search/annotation -> only annotations whose parent encounter is visible;
        // X-Wildbook-Total-Hits scoped; no publiclyReadable/submitterUserIds/viewUsers in any hit _source;
        // embeddings present in returned hits.
    }

    @Test void tokenIndividualSearch_gatedAndAllowlisted() throws Exception {
        // /api/v3/search/individual -> only individuals with >=1 visible encounter;
        // returned hits contain ONLY identity fields (id/displayName/names/sex/taxonomy/timeOf*),
        // NO numberEncounters/users/encounterIds/locationGeoPoints, NO ACL fields.
    }

    @Test void adminTokenIndividualSearch_full() throws Exception {
        // admin token -> unscoped, aggregates present, ACL fields still scrubbed.
    }

    @Test void tokenOccurrenceSearch_403() throws Exception {
        // /api/v3/search/occurrence with token -> 403 (still deferred).
    }
```

- [ ] **Step 2: Run**

Run: `mvn test -Dtest=SearchTokenScopeChildIndexTest` (pulls OpenSearch container; allow minutes). Also re-run `SearchTokenScopeTest` + `EncounterExportImagesTest` to confirm no regression.
Expected: PASS. If Docker unavailable, `@Disabled("requires Docker/Testcontainers")` with a comment (do not delete), per D's precedent.

- [ ] **Step 3: Normalize + commit**

```bash
cd /mnt/c/Wildbook-token-auth
grep -lU $'\r' src/test/java/org/ecocean/api/SearchTokenScopeChildIndexTest.java | xargs -r sed -i 's/\r$//'
git add src/test/java/org/ecocean/api/SearchTokenScopeChildIndexTest.java
git commit -m "test(search): e2e token-scoped annotation+individual enforcement (real OpenSearch)"
```

---

### Task 10: Full build, self-review, Codex code review

- [ ] **Step 1: Compile + run the affected unit set**

```bash
cd /mnt/c/Wildbook-token-auth
mvn -q -DskipTests compile
mvn test -Dtest=EncounterAclFieldsTest,AnnotationAclSerializerTest,MarkedIndividualAclSerializerTest,OpenSearchAclFilterTest,OpenSearchAclFilterIndexAwareTest,OpenSearchSanitizeDocTest,SearchApiTokenAuthTest,SearchApiChildIndexTest,ChildReindexTriggerTest,EndpointAuthWiringTest,JwtServiceTest,WildbookTokenAuthenticationFilterTest
```
Expected: BUILD SUCCESS; all green. (Run the two Testcontainers ITs separately.)

- [ ] **Step 2: LF check**

```bash
cd /mnt/c/Wildbook-token-auth
git diff --name-only origin/token-auth-scoped-search..HEAD | xargs -r grep -lU $'\r' || echo "LF-clean"
```

- [ ] **Step 3: Self-review vs spec** — confirm each spec section maps to a task: ACL fields (T1-3), index-aware filter (T4), universal scrub + allowlist (T5), SearchApi widen + effective-index + token sanitize (T6), triggers incl. membership (T7), mapping/reindex runbook (T8), e2e proof (T9). Confirm parity edges: annotation union/0-parent (T2), individual 0-encounter world-readable (T3), anonymous-vs-invalid owner in the single source (T1).

- [ ] **Step 4: Codex code review** on the full Spec-A diff (`git diff <task-1-parent>..HEAD`), per project convention. Prompt Codex (read-only, may read files) to specifically check: no fail-open for non-admin token on the new indices; allowlist completeness vs the live individual serializer; universal ACL scrub covers session AND token on all three indices; membership-trigger coverage (old+new individual); annotation union over all parents (not just first); anonymous-vs-invalid-owner correctness. Fold findings, re-review until SAFE TO MERGE.

- [ ] **Step 5: Report** to the user: diff summary, test counts, Codex verdict, the carried-forward follow-ups (global session metadata leak; orgAdmin viewUsers verification — now also covers the individual unions), and the reindex-cost note. Await push/PR decision.

---

## Self-Review (plan author)

**Spec coverage:** every spec section maps to a task (see Task 10 Step 3). The four Codex-High spec items are implemented: annotation union (T2), universal ACL scrub (T5), allowlist sanitizer (T5), membership-change triggers (T7).

**Placeholder scan:** Task 6's test bodies and Task 9's integration test are described as method stubs with explicit assertions to fill — they reference concrete patterns (`SearchApiTokenAuthTest`/`SearchTokenScopeTest`) and exact fields to assert. Task 7 Step 2 carries an explicit engineer verification (shallow-vs-deep indexing) rather than a guess — this is a flagged real uncertainty, not a placeholder; the implementer must confirm against `IndexingManager`.

**Type/name consistency:** `opensearchAclFields` (T1) returns `{publiclyReadable, submitterUserId?, viewUsers[]}` and is consumed by `writeAclFields` in T2/T3 (which emit `submitterUserIds` plural unions). `applyAclFilter(query,uuid,indexName)` (T4) uses `submitterUserId` for encounter, `submitterUserIds` for annotation/individual — matching the serializers. `sanitizeDoc(...,tokenAuth)` (T5) is called with `tokenAuth` in T6. `INDIVIDUAL_TOKEN_KEEP` allowlist matches the identity fields the individual serializer emits.
