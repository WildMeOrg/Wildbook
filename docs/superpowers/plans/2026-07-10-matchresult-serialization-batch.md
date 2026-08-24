# MatchResult serialization: batch prospect encounter loads

> **For agentic workers:** small, contained follow-up. Steps use `- [ ]`. Stacked on `perf/vector-match-throughput` (PR #1676), which introduces `Shepherd.getEncountersByAnnotationIds`.

**Goal:** Remove the per-prospect `Encounter.findEncounter` JDOQL fanout in `MatchResult.annotationDetails` (the match-result **serialization** path), reusing the batch loader from PR #1676. Cuts up to ~500 sequential encounter queries in that path down to one batch loader call (`ceil(uniqueIds/1000)` physical queries).

**Architecture:** Batch-load the annotation→encounter map once in `MatchResult.prospectsForApiGet` (covering every prospect across all score types), thread it down through `MatchResultProspect.jsonForApiGet` into a new `annotationDetails(ann, myShepherd, encByAnnId)` overload. The overload falls back to `findEncounter` when the map has no entry (or the map is null), so the single `queryAnnotation` call and any other caller are unchanged.

**Tech Stack:** Java 17, DataNucleus JDO, JUnit 5 + Mockito.

## Global Constraints
- **Serialized JSON is identical for the normal single-parent case** (the real world): the batch loader returns the same managed `Encounter` for an annotation with one parent, so the `encounter`/`individual` sub-JSON is identical. **Multi-parent annotations are anomalous** — `Encounter.findByAnnotation` (old) and `getEncountersByAnnotationIds` (new) each pick an arbitrary parent from an unordered query, so for those the selected parent may differ. This is NOT a new correctness change: both paths were already arbitrary, and PR #1676 accepts the same treatment for the indiv-grouping path. Do not claim byte-identity for multi-parent data.
- **Scope of the perf claim:** this removes the fanout in `annotationDetails` only. When `projectIds` is non-empty, `prospectsSorted` → `MatchResultProspect.isInProjects` still calls `findEncounter` per prospect — that is a separate lookup, left out of scope here (possible future follow-up). State the win as "one batch load for serialization," not "one query per view."
- Keep the existing public 2-arg `annotationDetails(Annotation, Shepherd)` and 1-arg `MatchResultProspect.jsonForApiGet(Shepherd)` methods working (delegate), so no other caller breaks.
- Null `myShepherd` must behave exactly as today (no new NPE): when null, do not batch — pass a null map so `annotationDetails` takes its existing `findEncounter(myShepherd)` path.
- Commit LF; `perl -i -pe 's/\r\n/\n/g'` + `grep -cP '\r$'` == 0 on every touched file.

## Baseline (verified on the stacked branch)
- `MatchResult.annotationDetails(Annotation ann, Shepherd myShepherd)` (~707) calls `ann.findEncounter(myShepherd)` (~735) to build the `encounter`/`individual` JSON.
- Callers: `MatchResultProspect.jsonForApiGet` (:85, per prospect) and `MatchResult.jsonForApiGet` (:698, once for `queryAnnotation`).
- `MatchResult.prospectsForApiGet` (~681) loops `prospectScoreTypes()` × `prospectsSorted(type,…)` and calls `mrp.jsonForApiGet(myShepherd)` per prospect — the fanout. The same annotation can recur across the `annot` and `indiv` types.
- `MatchResultProspect.getAnnotation()` (:52) exists. `Shepherd.getEncountersByAnnotationIds(Collection<String>)` exists (PR #1676).

---

## Task 1: Batch the serialization encounter loads

**Files:**
- Modify: `src/main/java/org/ecocean/ia/MatchResult.java` (`annotationDetails` overload; `prospectsForApiGet`)
- Modify: `src/main/java/org/ecocean/ia/MatchResultProspect.java` (`jsonForApiGet` overload)
- Test: `src/test/java/org/ecocean/ia/MatchResultSerializationBatchTest.java` (new)

**Interfaces:**
- `JSONObject MatchResult.annotationDetails(Annotation ann, Shepherd myShepherd, Map<String,Encounter> encByAnnId)` — resolves the encounter from `encByAnnId` when present, else `ann.findEncounter(myShepherd)`. Existing 2-arg delegates with `null`.
- `JSONObject MatchResultProspect.jsonForApiGet(Shepherd myShepherd, Map<String,Encounter> encByAnnId)` — passes the map into `annotationDetails`. Existing 1-arg delegates with `null`.

- [ ] **Step 1: Add the `annotationDetails` map overload; 2-arg delegates**

```java
public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
    return annotationDetails(ann, myShepherd, null);
}

public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd,
    java.util.Map<String, Encounter> encByAnnId) {
    JSONObject aj = new JSONObject();
    if (ann == null) return aj;
    // ... existing body unchanged UP TO the findEncounter line ...
    Encounter enc = resolveEncounter(ann, myShepherd, encByAnnId);
    // ... existing body unchanged AFTER, using enc ...
}

// Prefer the pre-batched map (one query for the whole result); fall back to the per-annotation
// query when the map is absent or lacks this id (e.g. the single queryAnnotation call).
private static Encounter resolveEncounter(Annotation ann, Shepherd myShepherd,
    java.util.Map<String, Encounter> encByAnnId) {
    if ((encByAnnId != null) && (ann.getId() != null) && encByAnnId.containsKey(ann.getId())) {
        return encByAnnId.get(ann.getId());
    }
    return ann.findEncounter(myShepherd);
}
```
Only the single `Encounter enc = ann.findEncounter(myShepherd);` line inside the existing 3-arg body changes to `Encounter enc = resolveEncounter(ann, myShepherd, encByAnnId);`. Everything else in the method is unchanged.

- [ ] **Step 2: Add `MatchResultProspect.jsonForApiGet` map overload; 1-arg delegates**

```java
public JSONObject jsonForApiGet(Shepherd myShepherd) {
    return jsonForApiGet(myShepherd, null);
}

public JSONObject jsonForApiGet(Shepherd myShepherd, java.util.Map<String, Encounter> encByAnnId) {
    JSONObject rtn = new JSONObject();
    rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd, encByAnnId));
    // ... rest of the existing body unchanged (score, asset) ...
}
```
Add `import org.ecocean.Encounter;` if not already present.

- [ ] **Step 3: Batch-load once in `prospectsForApiGet` and pass the map down**

```java
public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
    JSONObject sj = new JSONObject();

    // Batch-load every prospect's parent encounter once (was: one findEncounter per prospect per
    // type in annotationDetails). `prospects` is a nullable Set; guard it (Codex: NPE when empty/
    // null). Null shepherd -> no batch (annotationDetails keeps its per-annotation fallback),
    // preserving current behavior.
    java.util.Map<String, Encounter> encByAnnId = java.util.Collections.emptyMap();
    if ((myShepherd != null) && (prospects != null) && !prospects.isEmpty()) {
        java.util.List<String> annIds = new java.util.ArrayList<String>();
        for (MatchResultProspect mrp : prospects) {
            Annotation a = (mrp == null) ? null : mrp.getAnnotation();
            if ((a != null) && (a.getId() != null)) annIds.add(a.getId());
        }
        encByAnnId = myShepherd.getEncountersByAnnotationIds(annIds);
    }

    for (String type : prospectScoreTypes()) {
        JSONArray jarr = new JSONArray();
        for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
            jarr.put(mrp.jsonForApiGet(myShepherd, encByAnnId));
        }
        sj.put(type, jarr);
    }
    return sj;
}
```
`prospects` is the full prospect collection already on the object (used by `prospectsSorted`/`numberProspects`). Confirm its exact field name and iterate it; if it is not directly iterable here, gather ids by unioning `prospectsSorted(type,…)` over `prospectScoreTypes()` into a `Set` first.

- [ ] **Step 4: Write the equivalence + no-fanout test**

Create `MatchResultSerializationBatchTest`. Mock `Task`; use **`spy`** (not plain mock) annotations so `findEncounter` can be both stubbed AND counted, mirroring `MatchResultIndivProspectsTest`'s `newShepherd()` that stubs `getEncountersByAnnotationIds` from a per-test `id→Encounter` registry. Stub spies with `doReturn(...).when(spy).method(...)` form (not `when(spy.method())`) so the real method is never invoked while stubbing.

**Critical setup note (Codex):** `new MatchResult(task, candidates, …, shepherd)` ALREADY calls `getEncountersByAnnotationIds` once inside `_populateProspectsByIndividual` (Task A). So after constructing the `MatchResult`, call `org.mockito.Mockito.clearInvocations(shepherd)` (and reset the `findEncounter` spy counters) BEFORE exercising `prospectsForApiGet` — otherwise the `times(1)` / `never()` assertions count the construction call too. Prove all three properties precisely:
  1. **Identical JSON (single-parent):** capture `prospectsForApiGet(...)` JSON with the batch stub active (`batchJson`), then with `getEncountersByAnnotationIds` stubbed to return an EMPTY map so every prospect takes the `findEncounter` fallback (`fallbackJson`). Assert the **complete** JSON strings are equal (`batchJson.toString().equals(fallbackJson.toString())`) — not just the sub-objects. Reset `findEncounter` counters between the two runs.
  2. **Fanout gone:** in the batch run where every prospect id IS in the map, assert `findEncounter` is invoked **0 times** across all prospect annotations (`verify(ann, never()).findEncounter(any())`), and `getEncountersByAnnotationIds` exactly once (`times(1)`) — note in a comment this is one loader call = `ceil(uniqueIds/1000)` physical queries, here 1.
  3. **Fallback for a missing id:** one prospect whose annotation id is deliberately absent from the map → assert exactly one `findEncounter` on that annotation and its encounter still appears in the JSON.
- Also cover the empty-prospects case: a `MatchResult` with no prospects → `prospectsForApiGet` returns `{}`-shaped JSON with no NPE and `getEncountersByAnnotationIds` never called.

- [ ] **Step 5: Run tests**

```bash
mvn -o -Dtest=MatchResultSerializationBatchTest,MatchResultIndivProspectsTest,MatchResultTest -DfailIfNoTests=false test
```
Expected: PASS.

- [ ] **Step 6: Normalize + commit**

```bash
perl -i -pe 's/\r\n/\n/g' src/main/java/org/ecocean/ia/MatchResult.java \
  src/main/java/org/ecocean/ia/MatchResultProspect.java \
  src/test/java/org/ecocean/ia/MatchResultSerializationBatchTest.java
git add -A && git commit -m "perf(match): batch prospect encounter loads in match-result serialization"
```

- [ ] **Step 7: Full build**

```bash
mvn clean install   # BUILD SUCCESS, 0 failures
```

## Self-Review (post-Codex round 1)
- Output-identical for single-parent (the real case): `resolveEncounter` returns the same managed encounter `findEncounter` would; multi-parent is anomalous and arbitrary in both paths (scoped, not claimed identical). ✓
- Empty/null `prospects` guarded — no NPE (Codex #1). ✓
- queryAnnotation path untouched (2-arg → null map → findEncounter). ✓
- Null-shepherd behavior preserved (fallback to findEncounter, same as today). ✓
- Perf claim scoped to the serialization path; `isInProjects`/projectIds encounter lookups explicitly out of scope (Codex #4). ✓
- Test proves full-JSON equality (batch vs fallback), zero `findEncounter` when fully mapped, and one fallback for a missing id (Codex #3). ✓
