# Species-Specific Modified Groth Parameters — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve Modified Groth matching parameters per query-species (global fallback), via one consolidated resolver used by both scan paths.

**Architecture:** A new immutable `GrothParams` holder + a `CommonConfiguration.getGrothParams(genus, epithet, context)` resolver (species-key → global → safe constant). The sync (`GrothMatchServlet`), async-create (`ScanWorkItemCreationThread`), and async-write (`WriteOutScanTask`) paths all call it; `GridManager`'s hardcoded param fields/getters are retired. Per-species values live as suffixed keys in `commonConfiguration.properties`.

**Tech Stack:** Java 17, Maven, JUnit 5. Repo: Wildbook (worktree `/mnt/c/Wildbook-groth-wt`, branch `skill/species-groth-params`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-22-species-specific-groth-params-design.md`.
- Species key: built from genus+epithet only when **both** non-blank; lowercased `Locale.ROOT`; non-`[a-z0-9]` runs → single `_`; trim leading/trailing `_`; null otherwise.
- Param parse: trim; blank → missing; `Double.parseDouble`; reject non-finite and `<= 0`; on invalid, fall to next level (never throw mid-scan).
- Resolution order per param: `name.<speciesKey>` → `name` (global) → hardcoded default.
- Safe-constant defaults = corrected whale-shark optimum: epsilon=0.015, R=8.8, sizelim=0.94, maxTriangleRotation=20.0, C=1.146.
- `secondRun` is NOT part of `GrothParams`; leave its handling untouched.
- Build check command (whole repo, incremental): `mvn -q -DskipTests test-compile`.

---

### Task 1: `GrothParams` holder + `CommonConfiguration` resolver (TDD)

**Files:**
- Create: `src/main/java/org/ecocean/grid/GrothParams.java`
- Modify: `src/main/java/org/ecocean/CommonConfiguration.java` (add resolver + helpers + constants)
- Test: `src/test/java/org/ecocean/grid/GrothParamsTest.java`

**Interfaces:**
- Produces:
  - `org.ecocean.grid.GrothParams(double epsilon, double R, double sizelim, double maxTriangleRotation, double C)` with getters `getEpsilon/getR/getSizelim/getMaxTriangleRotation/getC` (all `double`).
  - `CommonConfiguration.getGrothParams(String genus, String specificEpithet, String context) -> GrothParams`
  - `CommonConfiguration.resolveGrothParams(String genus, String specificEpithet, java.util.function.Function<String,String> lookup) -> GrothParams` (package-visible, testable)
  - `CommonConfiguration.speciesKey(String genus, String specificEpithet) -> String` (nullable)

- [ ] **Step 1: Write the failing test** — `src/test/java/org/ecocean/grid/GrothParamsTest.java`

```java
package org.ecocean.grid;

import org.ecocean.CommonConfiguration;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GrothParamsTest {

    private static java.util.function.Function<String,String> lookup(Map<String,String> m) {
        return m::get; // returns null for absent keys
    }

    @Test void speciesKeyNormalizes() {
        assertEquals("carcharias_taurus", CommonConfiguration.speciesKey("Carcharias", "taurus"));
        assertEquals("carcharias_taurus", CommonConfiguration.speciesKey("  Carcharias ", " taurus "));
    }

    @Test void speciesKeyNullWhenEitherBlank() {
        assertNull(CommonConfiguration.speciesKey("Carcharias", ""));
        assertNull(CommonConfiguration.speciesKey(null, "taurus"));
        assertNull(CommonConfiguration.speciesKey("  ", "taurus"));
    }

    @Test void speciesOverrideWins() {
        Map<String,String> p = new HashMap<>();
        p.put("R", "8.8"); p.put("R.carcharias_taurus", "60");
        GrothParams gp = CommonConfiguration.resolveGrothParams("Carcharias", "taurus", lookup(p));
        assertEquals(60.0, gp.getR(), 1e-9);
    }

    @Test void fallsBackToGlobalWhenNoSpeciesKey() {
        Map<String,String> p = new HashMap<>();
        p.put("R", "8.8");
        GrothParams gp = CommonConfiguration.resolveGrothParams("Rhincodon", "typus", lookup(p));
        assertEquals(8.8, gp.getR(), 1e-9);
    }

    @Test void fallsBackToConstantWhenMissing() {
        GrothParams gp = CommonConfiguration.resolveGrothParams("Foo", "bar", lookup(new HashMap<>()));
        assertEquals(8.8, gp.getR(), 1e-9);          // default R
        assertEquals(0.015, gp.getEpsilon(), 1e-9);  // default epsilon
        assertEquals(1.146, gp.getC(), 1e-9);        // default C
    }

    @Test void nullSpeciesUsesGlobal() {
        Map<String,String> p = new HashMap<>();
        p.put("R", "8.8"); p.put("R.carcharias_taurus", "60");
        GrothParams gp = CommonConfiguration.resolveGrothParams(null, null, lookup(p));
        assertEquals(8.8, gp.getR(), 1e-9);
    }

    @Test void invalidValuesFallThrough() {
        Map<String,String> p = new HashMap<>();
        p.put("R.carcharias_taurus", "   ");   // blank
        p.put("R", "NaN");                       // non-finite
        GrothParams gp = CommonConfiguration.resolveGrothParams("Carcharias", "taurus", lookup(p));
        assertEquals(8.8, gp.getR(), 1e-9);     // both invalid -> default
        Map<String,String> p2 = new HashMap<>();
        p2.put("sizelim", "-1");                 // <= 0
        assertEquals(0.94, CommonConfiguration.resolveGrothParams("X","y", lookup(p2)).getSizelim(), 1e-9);
    }
}
```

- [ ] **Step 2: Run the test, verify it fails to compile / fails**

Run: `mvn -q test -Dtest=GrothParamsTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED"`
Expected: FAIL — `GrothParams` / `resolveGrothParams` / `speciesKey` symbols not found.

- [ ] **Step 3: Create `GrothParams`** — `src/main/java/org/ecocean/grid/GrothParams.java`

```java
package org.ecocean.grid;

/** Immutable holder for the five Modified Groth matching parameters. */
public final class GrothParams {
    private final double epsilon, R, sizelim, maxTriangleRotation, C;

    public GrothParams(double epsilon, double R, double sizelim,
                       double maxTriangleRotation, double C) {
        this.epsilon = epsilon;
        this.R = R;
        this.sizelim = sizelim;
        this.maxTriangleRotation = maxTriangleRotation;
        this.C = C;
    }

    public double getEpsilon() { return epsilon; }
    public double getR() { return R; }
    public double getSizelim() { return sizelim; }
    public double getMaxTriangleRotation() { return maxTriangleRotation; }
    public double getC() { return C; }
}
```

- [ ] **Step 4: Add the resolver to `CommonConfiguration`** — insert these members (near the other Groth getters, e.g. after `getC`). Add `import org.ecocean.grid.GrothParams;` and `import java.util.function.Function;` and `import java.util.Locale;` if not present.

```java
// --- Species-aware Modified Groth parameters (June 2026) ---
// Safe-constant defaults = corrected whale-shark optimum.
private static final double GROTH_DEFAULT_EPSILON = 0.015;
private static final double GROTH_DEFAULT_R = 8.8;
private static final double GROTH_DEFAULT_SIZELIM = 0.94;
private static final double GROTH_DEFAULT_MAXROT = 20.0;
private static final double GROTH_DEFAULT_C = 1.146;

/** Normalized property-key suffix from a species, or null if either part is blank. */
public static String speciesKey(String genus, String specificEpithet) {
    if (genus == null || specificEpithet == null) return null;
    String g = genus.trim(), s = specificEpithet.trim();
    if (g.isEmpty() || s.isEmpty()) return null;
    String key = (g + "_" + s).toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
    return key.isEmpty() ? null : key;
}

/** Parse a positive, finite double, or null if missing/blank/invalid. */
private static Double positiveFiniteOrNull(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    try {
        double d = Double.parseDouble(t);
        if (!Double.isFinite(d) || d <= 0.0) return null;
        return d;
    } catch (NumberFormatException e) {
        return null;
    }
}

private static double resolveOne(String name, String speciesKey,
                                 Function<String,String> lookup, double dflt) {
    if (speciesKey != null) {
        Double v = positiveFiniteOrNull(lookup.apply(name + "." + speciesKey));
        if (v != null) return v;
    }
    Double g = positiveFiniteOrNull(lookup.apply(name));
    if (g != null) return g;
    return dflt;
}

/** Testable core: resolve all five params against an arbitrary property lookup. */
static GrothParams resolveGrothParams(String genus, String specificEpithet,
                                      Function<String,String> lookup) {
    String sk = speciesKey(genus, specificEpithet);
    return new GrothParams(
        resolveOne("epsilon", sk, lookup, GROTH_DEFAULT_EPSILON),
        resolveOne("R", sk, lookup, GROTH_DEFAULT_R),
        resolveOne("sizelim", sk, lookup, GROTH_DEFAULT_SIZELIM),
        resolveOne("maxTriangleRotation", sk, lookup, GROTH_DEFAULT_MAXROT),
        resolveOne("C", sk, lookup, GROTH_DEFAULT_C));
}

/** Resolve the Groth params for a query species using configured properties. */
public static GrothParams getGrothParams(String genus, String specificEpithet, String context) {
    return resolveGrothParams(genus, specificEpithet, name -> getProperty(name, context));
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `mvn -q test -Dtest=GrothParamsTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED"`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/ecocean/grid/GrothParams.java \
        src/main/java/org/ecocean/CommonConfiguration.java \
        src/test/java/org/ecocean/grid/GrothParamsTest.java
git commit -m "feat(groth): species-aware GrothParams resolver in CommonConfiguration"
```

---

### Task 2: Wire the sync path (`GrothMatchServlet`)

**Files:**
- Modify: `src/main/java/org/ecocean/servlet/GrothMatchServlet.java`

**Interfaces:**
- Consumes: `CommonConfiguration.getGrothParams(genus, epithet, context)`, `GrothParams` getters.

- [ ] **Step 1: Capture query species during the DB-load block.** In the `try` block that loads `enc` (around the existing `encDate = enc.getDate();` lines), add:

```java
queryGenus = enc.getGenus();
querySpecificEpithet = enc.getSpecificEpithet();
```
Declare `String queryGenus = null, querySpecificEpithet = null;` alongside the other `enc*` locals declared before the DB block.

- [ ] **Step 2: Replace the early global reads with a species-resolved lookup.** Replace the existing block:

```java
epsilon = Double.parseDouble(CommonConfiguration.getEpsilon(context));
R = Double.parseDouble(CommonConfiguration.getR(context));
Sizelim = Double.parseDouble(CommonConfiguration.getSizelim(context));
maxTriangleRotation = Double.parseDouble(
    CommonConfiguration.getMaxTriangleRotation(context));
C = Double.parseDouble(CommonConfiguration.getC(context));
```
with a single resolve performed AFTER `enc` is loaded (move it just before the match loop / Phase 2):

```java
org.ecocean.grid.GrothParams gp =
    CommonConfiguration.getGrothParams(queryGenus, querySpecificEpithet, context);
epsilon = gp.getEpsilon();
R = gp.getR();
Sizelim = gp.getSizelim();
maxTriangleRotation = gp.getMaxTriangleRotation();
C = gp.getC();
```
(Keep the `double epsilon, R, Sizelim, maxTriangleRotation, C;` declarations; just assign later. The result-XML `root.addAttribute(...)` lines already use these locals and now reflect the species-resolved values — no change needed there.)

- [ ] **Step 3: Compile**

Run: `mvn -q -DskipTests test-compile`
Expected: BUILD SUCCESS, no errors in `GrothMatchServlet.java`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ecocean/servlet/GrothMatchServlet.java
git commit -m "feat(groth): sync scan resolves params by query species"
```

---

### Task 3: Wire the async-create path (`ScanWorkItemCreationThread`)

**Files:**
- Modify: `src/main/java/org/ecocean/grid/ScanWorkItemCreationThread.java`

**Interfaces:**
- Consumes: `CommonConfiguration.getGrothParams(genus, epithet, context)`. The thread's `context` field (constructor arg) and `baseEnc` (already loaded via `gm.getMatchGraphEncounterLiteEntry(encounterNumber)`).

- [ ] **Step 1: Move param setting to after `baseEnc` is loaded and resolve by species.** Today `props2` Groth params are set from `gm.getGrothEpsilon()` etc. BEFORE `baseEnc` exists. Delete those five lines:

```java
props2.setProperty("epsilon", gm.getGrothEpsilon());
props2.setProperty("R", gm.getGrothR());
props2.setProperty("Sizelim", gm.getGrothSizelim());
props2.setProperty("maxTriangleRotation", gm.getGrothMaxTriangleRotation());
props2.setProperty("C", gm.getGrothC());
```
Keep the `props2.setProperty("secondRun", gm.getGrothSecondRun());` and `props2.setProperty("rightScan", rightScan);` lines as-is.

After `EncounterLite baseEnc = gm.getMatchGraphEncounterLiteEntry(encounterNumber);` (and its existing null handling), insert:

```java
String qGenus = (baseEnc != null) ? baseEnc.getGenus() : null;
String qEpithet = (baseEnc != null) ? baseEnc.getSpecificEpithet() : null;
org.ecocean.grid.GrothParams gp =
    CommonConfiguration.getGrothParams(qGenus, qEpithet, context);
props2.setProperty("epsilon", String.valueOf(gp.getEpsilon()));
props2.setProperty("R", String.valueOf(gp.getR()));
props2.setProperty("Sizelim", String.valueOf(gp.getSizelim()));
props2.setProperty("maxTriangleRotation", String.valueOf(gp.getMaxTriangleRotation()));
props2.setProperty("C", String.valueOf(gp.getC()));
```
Add `import org.ecocean.CommonConfiguration;` if not already imported.

- [ ] **Step 2: Compile**

Run: `mvn -q -DskipTests test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/ecocean/grid/ScanWorkItemCreationThread.java
git commit -m "feat(groth): async scan-item creation resolves params by query species"
```

---

### Task 4: Wire the async result writer (`WriteOutScanTask`)

**Files:**
- Modify: `src/main/java/org/ecocean/servlet/WriteOutScanTask.java`

**Interfaces:**
- Consumes: `CommonConfiguration.getGrothParams(genus, epithet, context)`.

- [ ] **Step 1: Resolve once by the query species and pass those values into the XML writer.** At each site that currently passes `CommonConfiguration.getR(context), CommonConfiguration.getEpsilon(context), CommonConfiguration.getSizelim(context), CommonConfiguration.getMaxTriangleRotation(context), CommonConfiguration.getC(context)` (~lines 86-89 and 121-124), first resolve params from the query encounter's species (the query encounter is available as the scan subject — use its `getGenus()/getSpecificEpithet()`), then pass `gp` getters:

```java
GrothParams gp = CommonConfiguration.getGrothParams(
    queryEnc.getGenus(), queryEnc.getSpecificEpithet(), context);
// ... pass gp.getR(), gp.getEpsilon(), gp.getSizelim(),
//     gp.getMaxTriangleRotation(), gp.getC() in place of the CommonConfiguration.getX calls
```
Add `import org.ecocean.grid.GrothParams;`. (If the query `Encounter` object isn't already in scope at these lines, load it once via the existing `Shepherd`/`encNumber` and reuse for both sites.)

- [ ] **Step 2: Compile**

Run: `mvn -q -DskipTests test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/ecocean/servlet/WriteOutScanTask.java
git commit -m "fix(groth): async result XML records species-resolved params"
```

---

### Task 5: Retire `GridManager` hardcoded param fields/getters

**Files:**
- Modify: `src/main/java/org/ecocean/grid/GridManager.java`

**Interfaces:**
- Removes: `getGrothEpsilon/getGrothR/getGrothSizelim/getGrothMaxTriangleRotation/getGrothC` and their backing fields. Keeps `getGrothSecondRun` and its field.

- [ ] **Step 1: Confirm no remaining callers** (besides the now-updated async thread).

Run: `rg -n "getGroth(Epsilon|R|Sizelim|MaxTriangleRotation|C)\b" src/main`
Expected: no matches (the only former caller, `ScanWorkItemCreationThread`, was changed in Task 3).

- [ ] **Step 2: Delete the five fields** (`private String epsilon = "0.008";` … `private String C = "1.146";`) and the five getters (`getGrothEpsilon`…`getGrothC`). Leave `secondRun` field + `getGrothSecondRun()` intact.

- [ ] **Step 3: Compile**

Run: `mvn -q -DskipTests test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/ecocean/grid/GridManager.java
git commit -m "refactor(groth): remove GridManager hardcoded params (one source of truth)"
```

---

### Task 6: Set global + sand-tiger property values (all three copies)

**Files:**
- Modify: `src/main/resources/bundles/commonConfiguration.properties`
- Modify: `devops/deploy/.dockerfiles/tomcat/commonConfiguration.properties`
- Modify: `devops/development/.dockerfiles/tomcat/commonConfiguration.properties`

- [ ] **Step 1: In each of the three files**, set the global Groth block to the corrected whale-shark optimum and add the sand-tiger overrides. Replace the existing `R=/epsilon=/sizelim=/maxTriangleRotation=/C=` lines with:

```
# Modified Groth — global defaults (corrected whale-shark optimum, 2026-06)
epsilon=0.015
R=8.8
sizelim=0.94
maxTriangleRotation=20
C=1.146
# Per-species overrides (sand tiger / ragged-tooth)
epsilon.carcharias_taurus=0.008
R.carcharias_taurus=60
sizelim.carcharias_taurus=0.99
maxTriangleRotation.carcharias_taurus=12
C.carcharias_taurus=0.998
```

- [ ] **Step 2: Verify all three are consistent**

Run: `rg -n "^(R|epsilon|sizelim|maxTriangleRotation|C)(\.|=)" src/main/resources/bundles/commonConfiguration.properties devops/deploy/.dockerfiles/tomcat/commonConfiguration.properties devops/development/.dockerfiles/tomcat/commonConfiguration.properties`
Expected: identical 10-line Groth block in all three.

- [ ] **Step 3: Full test + compile sanity**

Run: `mvn -q test -Dtest=GrothParamsTest -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED"` then `mvn -q -DskipTests test-compile`
Expected: tests PASS, BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/bundles/commonConfiguration.properties \
        devops/deploy/.dockerfiles/tomcat/commonConfiguration.properties \
        devops/development/.dockerfiles/tomcat/commonConfiguration.properties
git commit -m "config(groth): global=whale-shark optimum + sand-tiger overrides (all copies)"
```

---

## Self-Review notes

- **Spec coverage:** resolver (T1) ✓, sync wiring (T2) ✓, async-create wiring (T3) ✓, async-write XML (T4) ✓, retire GridManager (T5) ✓, properties ×3 (T6) ✓, testing (T1) ✓. `secondRun` preserved (T3, T5). Reorder-after-load (T2, T3). Normalization/parse robustness (T1).
- **Verification limits:** T2-T5 are servlet/thread integration — verified by compile + the T1 unit test + reasoned review (no unit harness for the scan servlets). A final Codex pass on the full diff + `/code-review` precede the PR.
- **Exact line numbers** are intentionally given as anchors (code-to-replace) rather than fixed line numbers, since edits shift them.
