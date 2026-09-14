# viewUsers ACL Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Wildbook's OpenSearch `viewUsers` ACL field a trustworthy authorization boundary by fixing the two confirmed defects (revocation leak + a second, contradictory writer) and adding the missing revocation triggers.

**Architecture:** Define one correct per-encounter ACL computation, `Encounter.computeViewUsers(Shepherd)`, and route the single-encounter (full-index) path through it (fixing Defect 2). Make the bulk background pass always write `viewUsers` — including the empty array `[]` and for invalid-owner encounters — so revocation propagates (fixing Defect 1). Add the org/role/user-deletion triggers that the existing `Collaboration`-only mechanism misses. Keep the bulk pass's amortized map-building for scale, and add a consistency test asserting bulk and single-encounter agree.

**Tech Stack:** Java 17, DataNucleus JDO, OpenSearch, JUnit 5 + Mockito (`mockStatic` with `CALLS_REAL_METHODS`), Testcontainers (not needed here — all new tests are pure unit tests).

**Branch/worktree:** `acl-viewusers-hardening` at `/mnt/c/Wildbook-acl-hardening` (off `main`). All paths below are relative to that worktree.

**Out of scope (separate branch):** Artifact B (token issuance + live `canUserAccess` endpoint). This plan is the security bugfix (Artifact A) only.

---

## Background: the two defects (verified in source)

- **Defect 1 — revocation leak.** `Encounter.opensearchIndexPermissions()` only writes `viewUsers` when non-empty (`Encounter.java:~4224`, `if (viewUsers.length() > 0)`), and `continue`s past encounters whose `submitterID` doesn't map to a user (`~4178–4184`). Losing the last viewer (or an invalid owner) leaves a stale array indexed indefinitely.
- **Defect 2 — second contradictory writer.** The full-document serializer calls `this.userIdsWithViewAccess(myShepherd)` when `opensearchProcessPermissions==true` (`Encounter.java:4605–4615`). `userIdsWithViewAccess` (`~3347–3358`) calls `Collaboration.collaborationsForUser(submitterID)` with **no state filter** (admits `initialized`/`rejected`/`pending`) and, because `collaborationsForUser` injects *assumed* orgAdmin collaborations only when the **submitter** is an orgAdmin, it **inverts** orgAdmin visibility (grants org members view of the orgAdmin's own encounters) while failing to grant orgAdmins view of members' encounters.

The correct logic already lives in the bulk pass (`Encounter.java:4208–4222`): it asks "which users can see this submitter?" with an approved/edit filter and correct orgAdmin direction.

## Definition of correct per-encounter visibility

For a non-public encounter owned by submitter username `S`, `viewUsers` = the set of user UUIDs who may view it:
1. **Real collaborators:** the *other* party of every **persisted** collaboration involving `S` whose state is `approved` or `edit` (mutual view). Query persisted collaborations directly — do **not** use `collaborationsForUser(S)`, which injects assumed orgAdmin collabs in the wrong direction.
2. **orgAdmins of S's org(s):** every member of any organization `S` belongs to who holds the `orgAdmin` role (one-way: the admin sees `S`, not vice-versa).

Public encounters (`isPubliclyReadable()`), and non-public encounters with an invalid/missing owner, contribute **no** viewers and must be written as `[]` (admin-only via the `user.isAdmin()` branch in `opensearchAccess`).

### Design decision: full reindex omits viewUsers → fail-closed (resolves Codex High #1/#2)
A full-document reindex with `opensearchProcessPermissions==false` will **omit** `viewUsers` (the serializer only emits it when the flag is set). On a full index (document replace) this **drops** the field, which `opensearchAccess` treats as "no viewUsers" ⇒ **admin-only** until the background pass repairs it. This is intentionally **fail-closed** (briefly over-restrictive, never over-permissive) and is why we do **not** pay the per-document ACL cost on every full index (the previously-removed "too expensive" computation, `Base.java:214–216`). We never re-emit the *stale* value: it is either computed fresh (flag set, Task 2) or dropped and repaired by the always-write bulk pass (Task 3). The bulk pass is the "bulk ACL service" the spec calls for; the per-encounter `computeViewUsers` is the single-encounter path.

**Bounding the denial window (Codex Medium #5):** the cases where permissions actually *change* are already gap-free — the edit servlets that alter an encounter's owner/visibility set `opensearchProcessPermissions=true`, so `viewUsers` is written fresh in the same index op (Task 2 makes that write correct). For other full reindexes (where permissions did **not** change), `viewUsers` is briefly dropped and restored on the next bulk pass — fail-closed, and the restored value is identical. To keep that window short after a *bulk/full catalog re-sync*, the operator/runbook sets `OpenSearch.setPermissionsNeeded(true)` so the next background tick recomputes immediately rather than waiting for `BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES` (Task 5 runbook). We deliberately do **not** flip `permissionsNeeded` on every individual encounter index (that would run the bulk pass almost continuously).

## File structure

- **Modify** `src/main/java/org/ecocean/Encounter.java`
  - Add `computeViewUsers(Shepherd)` (new, correct per-encounter ACL).
  - Repoint the full-index serializer (`4605–4615`) to `computeViewUsers`; remove the `opensearch whhhh` debug println.
  - Replace `userIdsWithViewAccess` body to delegate to `computeViewUsers` (keep the method name; callers unchanged).
  - Fix `opensearchIndexPermissions()` always-write (incl. `[]`) and write `[]` for invalid-owner encounters.
- **Modify** `src/main/java/org/ecocean/WildbookLifecycleListener.java`
  - Trigger `OpenSearch.setPermissionsNeeded(true)` on `Organization` store/delete (org membership/hierarchy changes).
- **Modify** the role-mutation path(s) (Task 4 discovery) to call `OpenSearch.setPermissionsNeeded(true)` on orgAdmin role add/remove and user deletion.
- **Create** `src/test/java/org/ecocean/ComputeViewUsersTest.java` (unit tests for the ACL function + Defect 2 regression).
- **Create** `docs/superpowers/runbooks/viewusers-corrective-reindex.md` (one-time corrective reindex operator step).

---

## Task 1: Correct per-encounter ACL function `computeViewUsers`

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java` (add method near `userIdsWithViewAccess`, ~line 3347)
- Test: `src/test/java/org/ecocean/ComputeViewUsersTest.java`

- [ ] **Step 1: Write the failing test (public + invalid owner → empty)**

Create `src/test/java/org/ecocean/ComputeViewUsersTest.java`:

```java
package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ComputeViewUsersTest {

    // User has no public setId; use the single-arg uuid constructor (User.java:118)
    // then set the username. getId() returns that uuid (User.java:173).
    private User user(String username, String id) {
        User u = new User(id);     // constructor sets uuid = id
        u.setUsername(username);
        return u;
    }

    @Test void publicEncounter_yieldsEmpty() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("public"); // User.isUsernameAnonymous => publiclyReadable
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        List<String> ids = enc.computeViewUsers(myShepherd);
        assertTrue(ids.isEmpty(), "public encounter must have no viewUsers");
    }

    @Test void invalidOwner_yieldsEmpty() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("ghost-user");
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        when(myShepherd.getUser("ghost-user")).thenReturn(null); // unmappable owner
        List<String> ids = enc.computeViewUsers(myShepherd);
        assertTrue(ids.isEmpty(), "invalid-owner encounter must fail closed to []");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ComputeViewUsersTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — `computeViewUsers` does not exist (compile error).

- [ ] **Step 3: Implement `computeViewUsers` (minimal: public + invalid owner)**

In `Encounter.java`, add immediately above `userIdsWithViewAccess` (~line 3347):

```java
    /**
     * Correct per-encounter ACL: the set of user UUIDs permitted to view this
     * encounter. Single source of visibility truth for the full-index path.
     * See docs/superpowers/specs/2026-05-29-wildbook-acl-prereqs-design.md.
     *
     * - Public/anonymous-owner encounters and encounters with an invalid owner
     *   yield [] (admin-only via opensearchAccess' isAdmin branch).
     * - Otherwise: the other party of every PERSISTED approved/edit
     *   collaboration with the submitter, plus orgAdmins of the submitter's
     *   organization(s) (one-way).
     */
    public List<String> computeViewUsers(Shepherd myShepherd) {
        List<String> ids = new ArrayList<String>();
        if (this.isPubliclyReadable()) return ids;
        String submitter = this.getSubmitterID();
        User submitterUser = (submitter == null) ? null : myShepherd.getUser(submitter);
        if (submitterUser == null) return ids; // fail closed: admin-only
        return ids;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ComputeViewUsersTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/ComputeViewUsersTest.java
git commit -m "feat(security): computeViewUsers skeleton — public/invalid-owner fail closed"
```

- [ ] **Step 6: Write the failing test (persisted approved/edit collaborators added; rejected/pending excluded)**

Add to `ComputeViewUsersTest.java`. This is the Defect-2 state-filter regression: only `approved`/`edit` persisted collabs grant view.

```java
    @Test void persistedApprovedAndEdit_added_othersExcluded() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("owner");
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        User owner = user("owner", "owner-uuid");
        when(myShepherd.getUser("owner")).thenReturn(owner);
        when(myShepherd.getUser("approvedU")).thenReturn(user("approvedU", "uuid-A"));
        when(myShepherd.getUser("editU")).thenReturn(user("editU", "uuid-E"));
        when(myShepherd.getUser("rejectedU")).thenReturn(user("rejectedU", "uuid-R"));
        when(myShepherd.getUser("pendingU")).thenReturn(user("pendingU", "uuid-P"));
        // owner is not an orgAdmin and belongs to no orgs
        when(myShepherd.getAllOrganizationsForUser(owner)).thenReturn(new ArrayList<Organization>());

        Collaboration cApproved = new Collaboration("owner", "approvedU"); cApproved.setState(Collaboration.STATE_APPROVED);
        Collaboration cEdit = new Collaboration("owner", "editU"); cEdit.setState(Collaboration.STATE_EDIT_PRIV);
        Collaboration cRejected = new Collaboration("owner", "rejectedU"); cRejected.setState(Collaboration.STATE_REJECTED);
        Collaboration cPending = new Collaboration("owner", "pendingU"); cPending.setState(Collaboration.STATE_EDIT_PENDING_PRIV);

        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, org.mockito.Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(myShepherd), eq("owner")))
              .thenReturn(Arrays.asList(cApproved, cEdit, cRejected, cPending));
            List<String> ids = enc.computeViewUsers(myShepherd);
            Set<String> set = new java.util.HashSet<String>(ids);
            assertTrue(set.contains("uuid-A"), "approved collaborator must be a viewer");
            assertTrue(set.contains("uuid-E"), "edit collaborator must be a viewer");
            assertTrue(!set.contains("uuid-R"), "rejected collaborator must NOT be a viewer (Defect 2)");
            assertTrue(!set.contains("uuid-P"), "pending collaborator must NOT be a viewer (Defect 2)");
            assertEquals(2, set.size());
        }
    }
```

- [ ] **Step 7: Run test to verify it fails**

Run: `mvn test -Dtest=ComputeViewUsersTest#persistedApprovedAndEdit_added_othersExcluded -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — `Collaboration.persistedCollaborationsForUser` does not exist; and `computeViewUsers` returns empty.

- [ ] **Step 8: Add `persistedCollaborationsForUser` to Collaboration (no assumed-orgAdmin injection)**

In `src/main/java/org/ecocean/security/Collaboration.java`, add near `collaborationsForUser` (~line 168):

```java
    // Like collaborationsForUser but WITHOUT injecting assumed orgAdmin
    // collaborations. Used by Encounter.computeViewUsers, which handles
    // orgAdmin visibility explicitly and in the correct direction.
    @SuppressWarnings("unchecked")
    public static List<Collaboration> persistedCollaborationsForUser(Shepherd myShepherd,
        String username) {
        String queryString =
            "SELECT FROM org.ecocean.security.Collaboration WHERE ((username1 == '" + username +
            "') || (username2 == '" + username + "'))";
        Query query = myShepherd.getPM().newQuery(queryString);
        Collection c = (Collection)(query.execute());
        ArrayList<Collaboration> returnMe = new ArrayList<Collaboration>(c);
        query.closeAll();
        return returnMe;
    }
```

- [ ] **Step 9: Extend `computeViewUsers` to add approved/edit collaborators**

Replace the body of `computeViewUsers` after the invalid-owner guard:

```java
        if (submitterUser == null) return ids; // fail closed: admin-only
        java.util.Set<String> seen = new java.util.HashSet<String>();
        // 1. persisted approved/edit collaborators (mutual view), correct direction
        for (Collaboration col : Collaboration.persistedCollaborationsForUser(myShepherd, submitter)) {
            if (!col.isApproved() && !col.isEditApproved()) continue;
            String otherUsername = col.getOtherUsername(submitter);
            User other = (otherUsername == null) ? null : myShepherd.getUser(otherUsername);
            if (other != null && other.getId() != null && seen.add(other.getId())) {
                ids.add(other.getId());
            }
        }
        return ids;
```

- [ ] **Step 10: Run test to verify it passes**

Run: `mvn test -Dtest=ComputeViewUsersTest#persistedApprovedAndEdit_added_othersExcluded -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/org/ecocean/Encounter.java src/main/java/org/ecocean/security/Collaboration.java src/test/java/org/ecocean/ComputeViewUsersTest.java
git commit -m "feat(security): computeViewUsers adds approved/edit collaborators, excludes rejected/pending"
```

- [ ] **Step 12: Write the failing test (orgAdmin one-way: admin sees member; member does NOT see admin)**

Add to `ComputeViewUsersTest.java`:

```java
    @Test void orgAdmin_oneWay_adminSeesMemberNotInverse() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        User member = user("member", "member-uuid");
        User admin = user("admin", "admin-uuid");
        when(myShepherd.getUser("member")).thenReturn(member);
        when(myShepherd.getUser("admin")).thenReturn(admin);

        Organization org = mock(Organization.class);
        when(org.getMembers()).thenReturn(Arrays.asList(member, admin));
        when(myShepherd.getAllOrganizationsForUser(member)).thenReturn(Arrays.asList(org));
        when(myShepherd.getAllOrganizationsForUser(admin)).thenReturn(Arrays.asList(org));
        // only 'admin' holds the orgAdmin role
        when(myShepherd.doesUserHaveRole("admin", Organization.ROLE_MANAGER, "context0")).thenReturn(true);
        when(myShepherd.doesUserHaveRole("member", Organization.ROLE_MANAGER, "context0")).thenReturn(false);

        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, org.mockito.Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.persistedCollaborationsForUser(any(Shepherd.class), anyString()))
              .thenReturn(new ArrayList<Collaboration>());

            // encounter owned by member -> admin is a viewer
            Encounter memberEnc = new Encounter(); memberEnc.setSubmitterID("member");
            assertTrue(memberEnc.computeViewUsers(myShepherd).contains("admin-uuid"),
                "orgAdmin must see member's encounter");

            // encounter owned by admin -> member is NOT a viewer (one-way)
            Encounter adminEnc = new Encounter(); adminEnc.setSubmitterID("admin");
            assertTrue(!adminEnc.computeViewUsers(myShepherd).contains("member-uuid"),
                "member must NOT see orgAdmin's encounter (Defect 2 inversion)");
        }
    }
```

- [ ] **Step 13: Run test to verify it fails**

Run: `mvn test -Dtest=ComputeViewUsersTest#orgAdmin_oneWay_adminSeesMemberNotInverse -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — orgAdmin viewers not yet added.

- [ ] **Step 14: Add orgAdmin viewers to `computeViewUsers`**

Insert before `return ids;` in `computeViewUsers`:

```java
        // 2. orgAdmins of the submitter's organization(s) can view (one-way)
        String context = myShepherd.getContext();
        List<Organization> orgs = myShepherd.getAllOrganizationsForUser(submitterUser);
        if (orgs != null) {
            for (Organization org : orgs) {
                List<User> members = org.getMembers();
                if (members == null) continue;
                for (User m : members) {
                    if (m == null || m.getUsername() == null) continue;
                    if (m.getUsername().equals(submitter)) continue; // not self
                    if (!myShepherd.doesUserHaveRole(m.getUsername(), Organization.ROLE_MANAGER, context)) continue;
                    if (m.getId() != null && seen.add(m.getId())) ids.add(m.getId());
                }
            }
        }
```

- [ ] **Step 15: Run test to verify it passes**

Run: `mvn test -Dtest=ComputeViewUsersTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (all four tests).

- [ ] **Step 16: Add the two regression-prone edge cases (multi-org orgAdmin; orgAdmin with real collabs)**

Add to `ComputeViewUsersTest.java`. These are exactly where bulk-vs-single divergence would hide (Codex Low):

```java
    @Test void orgAdmin_multiOrg_seesMembersOfAllOrgs() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        User m1 = user("m1", "m1-uuid");
        User m2 = user("m2", "m2-uuid");
        User admin = user("admin", "admin-uuid");
        when(myShepherd.getUser("m1")).thenReturn(m1);
        Organization orgA = mock(Organization.class);
        Organization orgB = mock(Organization.class);
        when(orgA.getMembers()).thenReturn(Arrays.asList(m1, admin));
        when(orgB.getMembers()).thenReturn(Arrays.asList(m2, admin));
        // m1 belongs to orgA only; admin is orgAdmin in both
        when(myShepherd.getAllOrganizationsForUser(m1)).thenReturn(Arrays.asList(orgA));
        when(myShepherd.doesUserHaveRole("admin", Organization.ROLE_MANAGER, "context0")).thenReturn(true);
        when(myShepherd.doesUserHaveRole("m1", Organization.ROLE_MANAGER, "context0")).thenReturn(false);
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, org.mockito.Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.persistedCollaborationsForUser(any(Shepherd.class), anyString()))
              .thenReturn(new ArrayList<Collaboration>());
            Encounter enc = new Encounter(); enc.setSubmitterID("m1");
            assertTrue(enc.computeViewUsers(myShepherd).contains("admin-uuid"),
                "orgAdmin sees member's encounter even when admin spans multiple orgs");
        }
    }

    @Test void orgAdminOwner_withRealCollabs_doesNotInvert() {
        // owner is itself an orgAdmin AND has a real approved collab.
        // The real collaborator is a viewer; org members must NOT become viewers
        // of the orgAdmin-owner's encounter (no inversion).
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        User owner = user("owner", "owner-uuid");   // orgAdmin
        User member = user("member", "member-uuid");
        User friend = user("friend", "friend-uuid");
        when(myShepherd.getUser("owner")).thenReturn(owner);
        when(myShepherd.getUser("friend")).thenReturn(friend);
        Organization org = mock(Organization.class);
        when(org.getMembers()).thenReturn(Arrays.asList(owner, member));
        when(myShepherd.getAllOrganizationsForUser(owner)).thenReturn(Arrays.asList(org));
        when(myShepherd.doesUserHaveRole("owner", Organization.ROLE_MANAGER, "context0")).thenReturn(true);
        when(myShepherd.doesUserHaveRole("member", Organization.ROLE_MANAGER, "context0")).thenReturn(false);
        Collaboration cFriend = new Collaboration("owner", "friend"); cFriend.setState(Collaboration.STATE_APPROVED);
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, org.mockito.Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(myShepherd), eq("owner")))
              .thenReturn(Arrays.asList(cFriend));
            Set<String> ids = new java.util.HashSet<String>(new Encounter() {{ setSubmitterID("owner"); }}.computeViewUsers(myShepherd));
            assertTrue(ids.contains("friend-uuid"), "real approved collaborator is a viewer");
            assertTrue(!ids.contains("member-uuid"),
                "org member must NOT view the orgAdmin-owner's encounter (no inversion)");
            // owner itself excluded (self), members who aren't orgAdmins excluded
            assertEquals(1, ids.size());
        }
    }
```

- [ ] **Step 17: Run all Task 1 tests; then commit**

Run: `mvn test -Dtest=ComputeViewUsersTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (all six tests).

```bash
git add src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/ComputeViewUsersTest.java
git commit -m "feat(security): computeViewUsers orgAdmin visibility + edge-case tests"
```

---

## Task 2: Route the full-index path through `computeViewUsers` (fix Defect 2)

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java:3347–3358` (userIdsWithViewAccess), `4605–4615` (serializer)

- [ ] **Step 1: Write the failing test (full-index path uses computeViewUsers semantics)**

Add to `ComputeViewUsersTest.java` — asserts `userIdsWithViewAccess` now delegates (rejected excluded):

```java
    @Test void userIdsWithViewAccess_delegatesToComputeViewUsers() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("owner");
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        User owner = user("owner", "owner-uuid");
        when(myShepherd.getUser("owner")).thenReturn(owner);
        when(myShepherd.getUser("rejectedU")).thenReturn(user("rejectedU", "uuid-R"));
        when(myShepherd.getAllOrganizationsForUser(owner)).thenReturn(new ArrayList<Organization>());
        Collaboration cRejected = new Collaboration("owner", "rejectedU"); cRejected.setState(Collaboration.STATE_REJECTED);
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, org.mockito.Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(myShepherd), eq("owner")))
              .thenReturn(Arrays.asList(cRejected));
            assertTrue(enc.userIdsWithViewAccess(myShepherd).isEmpty(),
                "legacy path must no longer admit rejected collaborators");
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ComputeViewUsersTest#userIdsWithViewAccess_delegatesToComputeViewUsers -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: FAIL — old `userIdsWithViewAccess` still admits the rejected collaborator.

- [ ] **Step 3: Replace `userIdsWithViewAccess` body to delegate**

Replace `Encounter.java:3347–3358` body with:

```java
    public List<String> userIdsWithViewAccess(Shepherd myShepherd) {
        // Delegates to the single correct ACL computation. Retained as a named
        // method because the full-index serializer and any external callers
        // reference it. See computeViewUsers().
        return computeViewUsers(myShepherd);
    }
```

- [ ] **Step 4: Remove the debug println in the serializer**

In `Encounter.java:4605–4615`, delete the line `System.out.println("opensearch whhhh: " + id);` (leave the loop body otherwise intact — it now iterates `computeViewUsers` via the delegation).

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ComputeViewUsersTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/ComputeViewUsersTest.java
git commit -m "fix(security): full-index viewUsers path delegates to computeViewUsers (Defect 2)"
```

---

## Task 3: Bulk pass always writes viewUsers, including [] and invalid owners (fix Defect 1)

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java` — `opensearchIndexPermissions()` (`~4129–4244`)

> Note: this method writes directly to OpenSearch, so it is verified by review + the shared `computeViewUsers` unit tests (the per-encounter semantics are identical) plus the runbook's post-deploy forced run, rather than a unit test. Keep the diff minimal and reviewable.

- [ ] **Step 1: Remove the non-empty guard so `[]` is written**

In `opensearchIndexPermissions()`, change the write block (`~4224`) from:

```java
                if (viewUsers.length() > 0) {
                    updateData.put("viewUsers", viewUsers);
                    try {
                        os.indexUpdate("encounter", id, updateData);
                    } catch (Exception ex) {
                    }
                }
```

to (always write, including empty array):

```java
                updateData.put("viewUsers", viewUsers); // always write, incl [] so revocation propagates
                try {
                    os.indexUpdate("encounter", id, updateData);
                } catch (Exception ex) {
                    // quiet during initial index build
                }
```

- [ ] **Step 2: Repair invalid-owner encounters with a FULL reindex, not a partial clear**

The bulk pass uses a partial `_update` (`OpenSearch.indexUpdate`, `OpenSearch.java:765`), so writing only `viewUsers=[]` would **leave a stale `submitterUserId` behind** — and `opensearchAccess` grants owner access via `submitterUserId` *before* it ever checks `viewUsers` (`Encounter.java:4695`). A renamed/deleted owner whose old UUID is still indexed in `submitterUserId` would therefore retain access (Codex High #1). So an invalid-owner encounter must be **fully re-serialized** (a full index drops `submitterUserId` — only written when the submitter resolves, `Encounter.java:4344` — and omits `viewUsers`), yielding admin-only.

In the loop branch where `usernameToId.get(submitterId) == null` (`~4178–4184`), enqueue a full reindex instead of skipping:

```java
                String uid = usernameToId.get(submitterId);
                if (uid == null) {
                    System.out.println("opensearchIndexPermissions(): WARNING invalid username " +
                        submitterId + " on enc " + id + " -> full reindex to clear stale ACL fields");
                    try {
                        Encounter staleEnc = myShepherd.getEncounter(id);
                        if (staleEnc != null) {
                            IndexingManager im = IndexingManagerFactory.getIndexingManager();
                            im.addIndexingQueueEntry(staleEnc, false); // full reindex: drops submitterUserId + viewUsers
                        }
                    } catch (Exception ex) {
                        System.out.println("  invalid-owner reindex enqueue failed for " + id + ": " + ex);
                    }
                    continue;
                }
```

- [ ] **Step 3: Build the project**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS (no compile errors from the edits).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ecocean/Encounter.java
git commit -m "fix(security): bulk permissions pass always writes viewUsers incl [] (Defect 1)"
```

---

## Task 4: Add the missing revocation triggers (collab delete / org / orgAdmin role / user deletion)

**Files:**
- Modify: `src/main/java/org/ecocean/WildbookLifecycleListener.java`
- Modify: `src/main/java/org/ecocean/servlet/UserDelete.java`, `UserConsolidate.java`, `OrganizationEdit.java`, `src/main/webapp/editOrg.jsp`

The existing mechanism: a `Collaboration` `postStore` calls `OpenSearch.setPermissionsNeeded(true)`; the background pass then recomputes. Gaps Codex confirmed:
- **`postDelete` never triggers** — only handles `Base`, so `throwAwayCollaboration` (`Shepherd.java:454`, called by `UserConsolidate.java:265`) silently fails to recompute (Codex High #2).
- Org membership/hierarchy (`Organization`, JDO-persistent `package.jdo:1008`), **orgAdmin role** changes (`Role`, `package.jdo:882`), and user deletes/consolidations don't flip the flag (Codex High #3).

Strategy: add robust **lifecycle hooks** for the three permission-relevant persistent types (Collaboration on delete; Organization and Role on both store and delete), plus explicit `setPermissionsNeeded(true)` at the user delete/consolidate servlets (where objects are removed outside those types).

- [ ] **Step 1: Verify the listener receives Organization/Role events**

Run: `grep -rnE "addInstanceLifecycleListener|WildbookLifecycleListener" src/main/java | head`
Inspect the registration: confirm it is registered with `null` classes (all persistent classes) rather than a fixed class list. If it is class-scoped and excludes `Organization`/`Role`, add them to the registration array. Expected: global registration (the listener already filters by `instanceof`).

- [ ] **Step 2: Add Collaboration to postDelete; add Organization + Role to postStore and postDelete**

In `WildbookLifecycleListener.postStore`, extend the chain after the `Collaboration` branch (~65–69):

```java
        } else if (Collaboration.class.isInstance(obj)) {
            System.out.println("WildbookLifecycleListener postStore() Collaboration -> permissionsNeeded=true");
            OpenSearch.setPermissionsNeeded(true);
        } else if (org.ecocean.Organization.class.isInstance(obj)
                   || org.ecocean.Role.class.isInstance(obj)) {
            System.out.println("WildbookLifecycleListener postStore() " +
                obj.getClass().getSimpleName() + " -> permissionsNeeded=true");
            OpenSearch.setPermissionsNeeded(true);
        }
```

In `postDelete`, after the existing `Base` block, add (note: per the existing comment, deleted-object fields can't be read, so do NOT call `getClass()`/accessors on `obj` — just detect type and flip the flag):

```java
        if (Collaboration.class.isInstance(obj) || org.ecocean.Organization.class.isInstance(obj)
            || org.ecocean.Role.class.isInstance(obj)) {
            System.out.println("WildbookLifecycleListener postDelete() permissions-relevant delete" +
                " -> permissionsNeeded=true");
            OpenSearch.setPermissionsNeeded(true);
        }
```

Add imports for `org.ecocean.Organization` and `org.ecocean.Role` if not already present/qualified.

- [ ] **Step 3: Add explicit triggers at the user delete/consolidate servlets (belt-and-suspenders)**

The lifecycle hooks above should cover Role/Collaboration/Organization deletions, but user deletion/consolidation orchestrate several removals; add an explicit flag flip after each commits, at the sites Codex enumerated:
- `src/main/java/org/ecocean/servlet/UserDelete.java` (after role/user deletion, ~lines 55, 86)
- `src/main/java/org/ecocean/servlet/UserConsolidate.java` (after role/org/user moves and `throwAwayCollaboration`, ~lines 79, 143, 265)
- `src/main/java/org/ecocean/servlet/OrganizationEdit.java` (membership/delete paths, ~lines 150, 263)
- `src/main/webapp/editOrg.jsp` (~line 77)

At each, after the mutating commit, add:

```java
org.ecocean.OpenSearch.setPermissionsNeeded(true);
```

- [ ] **Step 4: Build**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Normalize line endings on every touched file, then commit**

```bash
for f in src/main/java/org/ecocean/WildbookLifecycleListener.java \
         src/main/java/org/ecocean/servlet/UserDelete.java \
         src/main/java/org/ecocean/servlet/UserConsolidate.java \
         src/main/java/org/ecocean/servlet/OrganizationEdit.java \
         src/main/webapp/editOrg.jsp; do perl -i -pe 's/\r\n/\n/g' "$f"; done
git add -A
git commit -m "fix(security): trigger viewUsers recompute on collab/org/role delete and user delete/consolidate"
```

---

## Task 5: One-time corrective reindex runbook

**Files:**
- Create: `docs/superpowers/runbooks/viewusers-corrective-reindex.md`

- [ ] **Step 1: Write the runbook**

```markdown
# Runbook: viewUsers corrective reindex (one-time, post-deploy)

After deploying the viewUsers ACL hardening, existing indexed encounters may
hold stale `viewUsers` arrays written by the old logic. The background
permissions pass now always rewrites `viewUsers` (including `[]`), so a single
forced run repairs every non-public encounter.

Steps:
1. Confirm the deploy is live (new `opensearchIndexPermissions()` in the WAR).
2. Force the permissions pass on next background tick by clearing the
   permissions timestamp / setting permissionsNeeded:
   - Preferred: trigger `OpenSearch.setPermissionsNeeded(true)` (any
     collaboration edit does this), then wait for the background tick, OR
   - Force immediately by restarting the app (a forced run occurs when the
     last run is older than BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES).
3. Verify: pick an encounter whose last collaborator was removed and confirm
   its OpenSearch `viewUsers` is now `[]`:
   `GET encounter/_doc/<id>` → `viewUsers` absent or empty.
4. Verify a known orgAdmin still sees member encounters (spot check search).
```

- [ ] **Step 2: Normalize line endings + commit**

```bash
perl -i -pe 's/\r\n/\n/g' docs/superpowers/runbooks/viewusers-corrective-reindex.md
git add docs/superpowers/runbooks/viewusers-corrective-reindex.md
git commit -m "docs(security): runbook for one-time viewUsers corrective reindex"
```

---

## Task 6: Full test + build verification

- [ ] **Step 1: Run the new unit tests**

Run: `mvn test -Dtest=ComputeViewUsersTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (all tests).

> Bulk-vs-single equivalence (Codex Low #6): a true integration test would need a live OpenSearch + DB, which this codebase does not unit-test (see `OpenSearchVisibilityTest`'s rationale). Instead, the equivalence is covered at the unit level by the `computeViewUsers` cases that exercise exactly where divergence would hide — `orgAdmin_multiOrg_seesMembersOfAllOrgs` and `orgAdminOwner_withRealCollabs_doesNotInvert` (Task 1, Step 16) — since `computeViewUsers` computes the direct inverse of the bulk pass's map. Live equivalence is validated by the corrective-reindex runbook's spot checks (Task 5).

- [ ] **Step 2: Run the existing permission/visibility tests for regressions**

Run: `mvn test -Dtest=PermissionsTest,OpenSearchVisibilityTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx2g"`
Expected: PASS (no regressions).

- [ ] **Step 3: Full compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Confirm no CRLF was introduced**

Run: `git -C /mnt/c/Wildbook-acl-hardening diff --stat main -- '*.java' '*.md' | tail; for f in $(git -C /mnt/c/Wildbook-acl-hardening diff --name-only main); do grep -cP '\r$' "/mnt/c/Wildbook-acl-hardening/$f" | grep -q '^0$' || echo "CRLF in $f"; done`
Expected: no "CRLF in ..." lines.

---

## Notes for the implementer
- JUnit 5 assertion message goes LAST: `assertTrue(cond, "msg")`.
- Do not reindent existing method bodies when editing — keep diffs reviewable (per CLAUDE.md).
- After every Edit/Write of a `.java`/`.md` file: `perl -i -pe 's/\r\n/\n/g' <file>` then `grep -cP '\r$' <file>` must be `0`.
- `User.setId(...)` is used in tests to assign UUIDs to plain `User` objects; confirm the setter exists (it backs `getId()` used by `opensearchAccess`). If `setId` is not public, construct via the available constructor/!setter and adjust the test helper accordingly.
