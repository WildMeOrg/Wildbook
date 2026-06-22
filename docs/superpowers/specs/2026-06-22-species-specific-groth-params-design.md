# Species-Specific Modified Groth Parameters — Design

**Date:** 2026-06-22
**Status:** approved (pending Codex design review)

## Problem

The five Modified Groth matching parameters (`epsilon`, `R`, `sizelim`,
`maxTriangleRotation`, `C`) are applied **globally** to every species. Tuning done
for whale sharks (*Rhincodon typus*) is therefore cross-applied to all species.
Corrected, production-faithful parameter sweeps (June 2026) show the per-species
optima genuinely diverge:

| Param | Whale shark optimum | Sand tiger optimum |
|-------|--------------------:|-------------------:|
| epsilon | 0.015 | 0.008 |
| R | 8.8 | 60 |
| sizelim | 0.94 | 0.99 |
| maxTriangleRotation | 20 | 12 |
| C | 1.146 | 0.998 |

`R` differs ~7×. One global set cannot serve both, which matches user reports of
degraded sand tiger (*Carcharias taurus*) matching. The fix is to let the
parameters vary by the **query encounter's species**, falling back to global
defaults when no species-specific tuning exists.

A second, related defect: the parameters are read from **two independent
sources** depending on scan path —
- sync `GrothMatchServlet` reads `CommonConfiguration.getR()` etc. (from
  `commonConfiguration.properties`),
- async `ScanWorkItemCreationThread` reads `GridManager.getGrothR()` etc. (from
  hardcoded `GridManager` fields).

These can (and currently do) disagree. This design consolidates both paths onto
one resolver as part of the same change.

## Goals

1. Groth parameters resolve per query-species, with global fallback.
2. Both scan paths (sync + async) use the **same** resolver — one source of truth.
3. No behavior change for species without an override (given global defaults are
   set to the corrected whale-shark optimum).
4. Operators can tune per species by editing properties, no redeploy.

## Non-Goals (YAGNI)

- No DB- or JSON-backed config, no admin UI.
- No per-location, per-viewpoint, or per-IA-class parameters.
- No change to the Groth algorithm itself.
- The corrected benchmark test (`GrothParameterSweepTest`) fixes are committed
  separately, not bundled into this feature.

## Design

### Storage: species-keyed properties with global fallback

In `commonConfiguration.properties`, parameters may carry an optional
species-suffixed key. The suffix is the species key with the space replaced by an
underscore (`Carcharias taurus` → `Carcharias_taurus`), matching the existing
`GridManager` WB-1791 species convention (`genus + " " + specificEpithet`).

```
# global defaults (fallback) — corrected whale-shark optimum
epsilon=0.015
R=8.8
sizelim=0.94
maxTriangleRotation=20
C=1.146

# per-species overrides (optional) — sand tiger
epsilon.Carcharias_taurus=0.008
R.Carcharias_taurus=60
sizelim.Carcharias_taurus=0.99
maxTriangleRotation.Carcharias_taurus=12
C.Carcharias_taurus=0.998
```

All three property copies are set consistently: the in-jar bundle
(`src/main/resources/bundles/`) and both devops dockerfile copies
(`devops/deploy/`, `devops/development/`). This also resolves the current
divergence (bundle=6.8, devops=8 old defaults).

### Resolver

New value holder `org.ecocean.grid.GrothParams` (immutable; five `double` fields +
getters). New static method on `CommonConfiguration`:

```java
public static GrothParams getGrothParams(String species, String context)
```

Per-parameter resolution order:
1. `getProperty("<name>." + speciesKey, context)` if `species` non-blank
2. else `getProperty("<name>", context)` (global)
3. else a hardcoded safe constant (the corrected whale-shark optimum), so a
   missing or malformed property never crashes a scan.

`speciesKey` = `species.trim().replace(' ', '_')`. Null/blank species skips step 1.
A non-numeric property value is logged and falls through to the next level rather
than throwing.

### Wiring

- **`GrothMatchServlet`** (sync): derive species from the query
  `enc.getGenus()/getSpecificEpithet()`; replace the five
  `CommonConfiguration.getX()` calls with one `getGrothParams(species, context)`.
  The result XML continues to record the params actually used (now species-resolved).
- **`ScanWorkItemCreationThread`** (async): derive species from the query
  `baseEnc` (EncounterLite carries `genus`/`specificEpithet`); replace the five
  `gm.getGrothX()` calls with `getGrothParams(species, context)`. (Verify the
  thread has a usable `context`; pass one in if not.)
- **`GridManager`**: remove the hardcoded `epsilon/R/Sizelim/maxTriangleRotation/C`
  fields and `getGrothX()` getters. Grep first for any other callers; if found,
  point them at the resolver (global lookup). This eliminates the second source.

### Data flow

```
query Encounter ──(genus + specificEpithet)──> speciesKey
                                                   │
GrothMatchServlet / ScanWorkItemCreationThread ────┤
                                                   ▼
              CommonConfiguration.getGrothParams(species, context)
                 species key ──hit──> per-species value
                 miss ──> global value ──miss──> safe constant
                                                   ▼
              EncounterLite.getPointsForBestMatch(..., epsilon, R, sizelim, rot, C, ...)
```

### Error handling / fallback

- Null/blank/unknown species → global defaults.
- Missing global property → hardcoded safe constant (corrected whale-shark optimum).
- Malformed (non-numeric) property → log a warning, fall through; never throw mid-scan.

### Testing

New fast unit test for the resolver (no benchmark CSV needed):
- species override present → returns override values
- species absent from properties → returns global
- null/blank species → returns global
- malformed value → falls back, does not throw
- speciesKey normalization (`"Carcharias taurus"` → `Carcharias_taurus`)

Manual/optional: confirm whale-shark matching is unchanged (global = its optimum)
and sand tiger now uses its override in both scan paths.

## Files touched

| File | Change |
|------|--------|
| `org/ecocean/grid/GrothParams.java` | NEW — immutable 5-param holder |
| `org/ecocean/CommonConfiguration.java` | NEW `getGrothParams(species, context)` + species-key resolution + safe constants |
| `org/ecocean/servlet/GrothMatchServlet.java` | resolve via query species |
| `org/ecocean/grid/ScanWorkItemCreationThread.java` | resolve via query species |
| `org/ecocean/grid/GridManager.java` | retire hardcoded param fields/getters |
| `commonConfiguration.properties` (×3) | consistent globals + sand tiger overrides |
| `org/ecocean/grid/GrothParamsTest.java` | NEW — resolver unit test |

## Risks

- Removing `GridManager` getters could break an unseen caller → mitigate by grep
  before removal.
- Changing the whale-shark global from the deployed `6.8` to `8.8`: small,
  evidence-based, approved by product owner. Whale-shark behavior shifts slightly.
- `context` availability in the async thread → verify during implementation.
