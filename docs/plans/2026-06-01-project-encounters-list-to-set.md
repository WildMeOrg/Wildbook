# Design: Project.encounters List → Set (eliminate PROJECT_ENCOUNTERS IDX race)

**Date:** 2026-06-01
**Branch:** `fix/project-encounters-set` (off `main`)
**Status:** design — pending Codex review

## Problem

Bulk imports on zebra.wildme.org fail at row 0 with the generic "Your task failed
to process due to an error." The real cause, from the container log:

```
org.ecocean.Project.addEncounter (Project.java:234)
  → INSERT INTO PROJECT_ENCOUNTERS (ID_OID, CATALOGNUMBER_EID, IDX)
  → ERROR: duplicate key value violates unique constraint "PROJECT_ENCOUNTERS_pkey"
    Detail: Key (ID_OID, IDX)=(d5d951af-…, 5890) already exists.
```

`Project.encounters` is a Java `List` (`ArrayList`) mapped via JDO `<collection><join/>`.
Because the field type is `List`, DataNucleus makes it an **indexed list**: the join
table `PROJECT_ENCOUNTERS` gets an `IDX` ordering column and a primary key of
`(ID_OID, IDX)`. When two threads add encounters to the same project concurrently
(e.g. two bulk-import background threads, which we had in flight), each computes the
same next index `N = current size`, both `INSERT … IDX = N`; one wins, the other dies
on the PK. The loser's transaction rolls back, so the table stays gap-free — which is
exactly what we observe in production: `SELECT COUNT(*), MIN(IDX), MAX(IDX)` returned
`6075, 0, 6074` (contiguous → race, not corruption).

Project membership has **no meaningful order**, so the `IDX` column buys nothing and
creates this whole class of collision.

## Goal / non-goals

**Goal:** Remove the ordering column so concurrent adds of *distinct* encounters can no
longer collide. Map `Project.encounters` as an unordered `Set`, with join-table PK
`(ID_OID, CATALOGNUMBER_EID)`.

**Non-goals:**
- Not changing `Encounter.equals()`/`hashCode()` globally (high blast radius; see Risks).
- Not changing the public `Project.getEncounters()` return type (keep caller churn ~0).
- `Project.users` has the identical List+join mapping and the same latent race. **Out of
  scope** for this PR to keep it small and reviewable; flagged as a recommended identical
  follow-up.

## Design

### 1. Java: `Project.java`

- Field: `private List<Encounter> encounters` → `private Set<Encounter> encounters`,
  using `LinkedHashSet`. (Insertion order is deterministic within a session; note that
  after the `IDX` column is dropped the *cross-load* DB order is unspecified — that is
  correct set semantics, and no caller or test asserts project-encounter order. We do not
  claim stable JSON ordering.)
- `removeEncounter()`, `clearAllEncounters()`, `numEncounters()` and the iterating methods
  (`asJSONObject`, `getAllACMIdsJSON`, `getAllAnnotIdsJSON`, `getPercentWithIncrementalIds`)
  use `Collection` operations (`remove`, `size`, for-each) — unchanged on a `Set`. The
  `null`-init sites change `new ArrayList<>()` → `new LinkedHashSet<>()`.
- **Equality is sound for a hash `Set`.** `Encounter extends Base`; `Base.equals()`
  (Base.java:465) compares by `getId()` (= `catalogNumber`), consistent with
  `Encounter.hashCode()` (also `catalogNumber`-based). So `Set.add/contains/remove` dedup
  correctly by id in O(1) — no explicit id-loop needed. The **one** hazard is a null
  `catalogNumber`, which `Encounter.hashCode()` maps to a *random* value (defeating dedup),
  so `addEncounter` rejects null-id encounters:
  ```java
  public void addEncounter(final Encounter enc) {
      if (enc == null) return;
      if (enc.getCatalogNumber() == null) { /* log WARN */ return; }
      setTimeLastModified();
      if (encounters == null) encounters = new LinkedHashSet<Encounter>();
      if (!encounters.add(enc)) { /* log INFO: already a member */ }
  }
  ```
  `removeEncounter` stays `encounters.remove(enc)` (id-based via Base.equals).
- **Keep `getEncounters()` returning `List<Encounter>`**, returning a copy:
  `return encounters == null ? null : new ArrayList<>(encounters);`
  This preserves every caller (some assign to `List<Encounter>`; `projectManagement.jsp`
  indexes with `.get(j)`), so no caller or JSP needs editing for compilation. Callers only
  read; none mutate the returned collection (mutation goes through `addEncounter`/
  `removeEncounter`), so a detached copy is safe.
- **Add `boolean containsEncounter(Encounter)`** (Codex finding #4) so hot callers that
  currently do `getEncounters().contains(enc)` don't allocate a ~6000-element copy first:
  ```java
  public boolean containsEncounter(final Encounter enc) {
      if (enc == null || encounters == null) return false;
      final String encId = enc.getCatalogNumber();
      if (encId == null) return false;
      for (final Encounter e : encounters) if (encId.equals(e.getCatalogNumber())) return true;
      return false;
  }
  ```
  Update the membership-check callers to use it: `ProjectUpdate.java:242`,
  `ProjectGet.java:104`, `iaResultsAnnotFeed.jsp:207`. (Other callers iterate or index and
  keep using `getEncounters()`.)

### 2. JDO: `package.jdo`

No XML change. DataNucleus chooses indexed-list vs. set purely from the Java field type:
`List` ⇒ adds `IDX`; `Set` ⇒ no order column, join-table PK `(owner, element)`. The
existing block already has no `<order>` element:

```xml
<field name="encounters" persistence-modifier="persistent" default-fetch-group="true">
  <collection element-type="org.ecocean.Encounter" dependent-element="false" />
  <join/>
</field>
```

A `mvn clean install` re-runs DataNucleus enhancement against the new field type.

### 3. Database migration (REQUIRED, run before deploying the new WAR)

DataNucleus schema auto-update only **adds**; it will not drop `IDX` or alter the PK. The
existing `IDX` column is `NOT NULL` (it is part of the current PK), so a `Set`-style
`INSERT (ID_OID, CATALOGNUMBER_EID)` would violate `NOT NULL` until `IDX` is dropped.
Therefore each instance must run, before deploying:

```sql
-- Backup first (core table):
CREATE TABLE "PROJECT_ENCOUNTERS_bak_20260601" AS SELECT * FROM "PROJECT_ENCOUNTERS";

BEGIN;
-- Precheck 1: no NULL CATALOGNUMBER_EID (ADD PRIMARY KEY fails on nulls). Expect 0.
SELECT COUNT(*) FROM "PROJECT_ENCOUNTERS" WHERE "CATALOGNUMBER_EID" IS NULL;

-- Precheck 2: no duplicate (ID_OID, CATALOGNUMBER_EID) pairs. Expect 0 rows.
SELECT "ID_OID", "CATALOGNUMBER_EID", COUNT(*)
FROM "PROJECT_ENCOUNTERS"
GROUP BY "ID_OID", "CATALOGNUMBER_EID"
HAVING COUNT(*) > 1;
-- If either precheck is non-empty, STOP and investigate (do not force the PK).

-- Drop the index-based PK, drop the ordering column, add the membership PK.
ALTER TABLE "PROJECT_ENCOUNTERS" DROP CONSTRAINT "PROJECT_ENCOUNTERS_pkey";
ALTER TABLE "PROJECT_ENCOUNTERS" DROP COLUMN "IDX";
ALTER TABLE "PROJECT_ENCOUNTERS" ADD CONSTRAINT "PROJECT_ENCOUNTERS_pkey"
    PRIMARY KEY ("ID_OID", "CATALOGNUMBER_EID");

-- Preserve an index on the element column for element-keyed lookups (the old PK led on
-- ID_OID; queries that filter by encounter — e.g. Encounter/Shepherd project lookups —
-- want CATALOGNUMBER_EID indexed). The new PK leads on ID_OID, so add the reverse index.
CREATE INDEX IF NOT EXISTS "PROJECT_ENCOUNTERS_N1" ON "PROJECT_ENCOUNTERS" ("CATALOGNUMBER_EID");
COMMIT;
```

(Confirm the exact constraint name per instance with `\d "PROJECT_ENCOUNTERS"`; the
production error quoted it as `PROJECT_ENCOUNTERS_pkey`.)

**Rollback** (only needed to revert to the old List code): restore from
`PROJECT_ENCOUNTERS_bak_20260601`, which still has the original 0-based `IDX`. If
rebuilding `IDX` from a backup that lacks it, use
`row_number() OVER (PARTITION BY "ID_OID" ORDER BY "CATALOGNUMBER_EID") - 1` (old `IDX`
is 0-based).

### 4. Deploy sequence (per instance) — order matters

The old (List) code requires `IDX`; the new (Set) code never writes it. The two must not
run against the table at the same time. Therefore:

1. **Stop** the old Wildbook WAR / Tomcat (no old code may touch `PROJECT_ENCOUNTERS`).
2. Backup `PROJECT_ENCOUNTERS`.
3. Run the migration SQL (both prechecks must return 0).
4. Deploy and **start** the new WAR.
5. Smoke test: a bulk import that assigns to a project completes; concurrent imports into
   one project no longer raise `PROJECT_ENCOUNTERS_pkey`.

## Caller impact (audited — all safe with Option A)

| Caller | Use | Safe? |
|---|---|---|
| `ProjectUpdate.java:242` | `getEncounters().contains(enc)` | yes (List copy) |
| `ProjectGet.java:101,104` | `List<Encounter> = getEncounters(); .contains` | yes |
| `ProjectIA.java:72` | iterate | yes |
| `ProjectDelete.java:61` | iterate | yes |
| `projectList.jsp:96` | `.size()` | yes |
| `projectManagement.jsp:187` | `.get(j)` index loop | yes (still a `List`) |
| `project.jsp:63` | assign | yes |
| `iaResultsAnnotFeed.jsp:207` | `.contains` → switch to `containsEncounter` | yes |

No caller mutates the returned collection; none break.

**Membership-mutation paths** (call `addEncounter`/`removeEncounter`/`clearAllEncounters`,
which operate on the live `Set` — all continue to work; none depend on list index/order):
`BulkImporter.java:333`, `StandardImport.java:1228`, `Encounter.java:3733`,
`EncounterDelete.java:120`, `ImportTask.java:608`, `DeleteImportTask.java:97`. These all
benefit from the stricter id-based `addEncounter` dedup. Verify during implementation that
none read back an order-dependent result.

## Risks & mitigations

- **Equality:** `Encounter` inherits id-based `equals()` from `Base` (Base.java:465),
  consistent with its id-based `hashCode()`, so a hash `Set` dedups correctly by id — there
  is no identity-vs-value mismatch. The only hazard is a null `catalogNumber` (random
  hashCode), which `addEncounter` now rejects up front. No change to `Encounter.equals()`
  is needed or made.
- **Residual concurrency:** two threads adding the *same* encounter to the *same* project at
  the same instant could still collide on the new PK. This is far rarer than the IDX race
  (which collided on *different* encounters) and is a true "added twice" no-op; acceptable,
  and now surfaced via the [error-visibility PR] instead of being invisible.
- **Multi-instance rollout:** the migration must run on every Wildbook before its WAR is
  upgraded. Documented in Deploy sequence.

## Test plan

- Unit: `Project` add/remove/contains/size/JSON round-trip on the `Set` field.
- Build: `mvn clean install` (DataNucleus enhancement) compiles.
- Integration (staging): run two concurrent bulk imports into the same project; both
  complete with no `PROJECT_ENCOUNTERS_pkey` error. Verify membership correctness.
