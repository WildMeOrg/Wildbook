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
4. Operators can tune per species by editing properties (requires an app restart
   or config-cache reload — `CommonConfiguration` caches the `Properties` per
   context once loaded; a live-reload endpoint is a possible follow-up, out of scope here).

## Non-Goals (YAGNI)

- No DB- or JSON-backed config, no admin UI.
- No per-location, per-viewpoint, or per-IA-class parameters.
- No change to the Groth algorithm itself.
- The corrected benchmark test (`GrothParameterSweepTest`) fixes are committed
  separately, not bundled into this feature.

## Design

### Storage: species-keyed properties with global fallback

In `commonConfiguration.properties`, parameters may carry an optional
species-suffixed key. The suffix is a **normalized** species key (see resolver
below): built from genus + epithet only when both are non-blank, lowercased
(`Locale.ROOT`), with whitespace/punctuation runs collapsed to `_`. So
`Carcharias taurus` → `carcharias_taurus`.

```
# global defaults (fallback) — corrected whale-shark optimum
epsilon=0.015
R=8.8
sizelim=0.94
maxTriangleRotation=20
C=1.146

# per-species overrides (optional) — sand tiger
epsilon.carcharias_taurus=0.008
R.carcharias_taurus=60
sizelim.carcharias_taurus=0.99
maxTriangleRotation.carcharias_taurus=12
C.carcharias_taurus=0.998
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
1. `getProperty("<name>." + speciesKey, context)` if `speciesKey` is non-null
2. else `getProperty("<name>", context)` (global)
3. else a hardcoded safe constant (the corrected whale-shark optimum), so a
   missing or malformed property never crashes a scan.

**Species key** (`CommonConfiguration.speciesKey(genus, epithet)`): returns null
unless **both** genus and epithet are non-blank; otherwise
`(genus + "_" + epithet)`, lowercased with `Locale.ROOT`, runs of non-`[A-Za-z0-9]`
collapsed to a single `_`, leading/trailing `_` trimmed. e.g. `"Carcharias","taurus"`
→ `carcharias_taurus`.

**Value parsing** (each level): `trim()`; treat blank/whitespace as *missing*
(fall through); `Double.parseDouble`; **reject non-finite** (`NaN`/`Infinity`) and
out-of-range values (fall through). Do **not** reuse the existing
`CommonConfiguration.getR()`-style getters — they call `.trim()` on a possibly-null
value and can NPE. A value that is present-but-invalid logs a warning and falls to
the next level.

### Wiring

**Ordering matters**: in both paths the params are currently resolved *before* the
query encounter is loaded; resolution must move to *after* the species is known.

- **`GrothMatchServlet`** (sync): config is read at lines ~66-79 but the query
  `Encounter` isn't loaded until ~89. Capture `enc.getGenus()/getSpecificEpithet()`
  inside the DB-load block, then call `getGrothParams(species, context)` before the
  match loop. Result XML continues to record the params actually used (now species-resolved).
- **`ScanWorkItemCreationThread`** (async): has a `String context` constructor arg
  (~line 23) and `baseEnc = gm.getMatchGraphEncounterLiteEntry(...)` (~83);
  `EncounterLite` exposes `getGenus()/getSpecificEpithet()` (~1586). Move the
  `props2` Groth-param setting to **after** `baseEnc` is loaded (null-check it), then
  set the five params from `getGrothParams(species, context)`. **Keep
  `props2.setProperty("secondRun", ...)` exactly as-is** — `ScanWorkItem` reads it and
  calls `.equals()` on it (~line 94); `secondRun` is NOT part of `GrothParams`.
- **`WriteOutScanTask`** (async result writer): currently writes result XML from the
  **global** `CommonConfiguration.getR()/...` (~86, ~120) — with species overrides this
  would report different params than were used for matching. Re-resolve via the query
  species so the XML records the values actually applied. (Caveat: config edited
  mid-scan can still drift; acceptable.)
- **`GridManager`**: remove the hardcoded `epsilon/R/Sizelim/maxTriangleRotation/C`
  fields and the five `getGrothX()` getters. Codex grep confirms the only callers are
  in `ScanWorkItemCreationThread` (replaced above), so removal is safe. **Leave the
  `secondRun` getter/handling alone.** This eliminates the second source.

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

- Null/blank species (or genus/epithet) → skip species key, use global.
- Property missing or **blank/whitespace** → treat as missing, fall to next level.
- Present-but-invalid value (non-numeric, `NaN`, `Infinity`, out-of-range) → log a
  warning, fall to next level; never throw mid-scan.
- Missing even the global property → hardcoded safe constant (corrected whale-shark
  optimum). Parsing is done in the resolver, not via the old `getR()`-style getters
  (which NPE on null).

### Testing

New fast unit test for the resolver (no benchmark CSV needed):
- species override present → returns override values
- species absent from properties → returns global
- null/blank species → returns global
- malformed value → falls back, does not throw
- speciesKey normalization (`"Carcharias","taurus"` → `carcharias_taurus`; blank
  genus or epithet → null key → global)
- present-but-blank and non-finite (`NaN`/`Infinity`) values → fall through

Manual/optional: confirm whale-shark matching is unchanged (global = its optimum)
and sand tiger now uses its override in both scan paths.

## Files touched

| File | Change |
|------|--------|
| `org/ecocean/grid/GrothParams.java` | NEW — immutable 5-param holder |
| `org/ecocean/CommonConfiguration.java` | NEW `getGrothParams(species, context)` + species-key resolution + safe constants |
| `org/ecocean/servlet/GrothMatchServlet.java` | resolve via query species (after enc load) |
| `org/ecocean/grid/ScanWorkItemCreationThread.java` | resolve via query species (after baseEnc load); keep `secondRun` |
| `org/ecocean/servlet/WriteOutScanTask.java` | resolve result-XML params via query species (not global) |
| `org/ecocean/grid/GridManager.java` | retire 5 hardcoded param fields/getters (keep `secondRun`) |
| `commonConfiguration.properties` (×3) | consistent globals + sand tiger overrides, updated atomically |
| `org/ecocean/grid/GrothParamsTest.java` | NEW — resolver unit test |

## Risks

- Removing `GridManager` getters: Codex grep confirms only `ScanWorkItemCreationThread`
  calls them; removal safe after replacement. `secondRun` getter retained.
- **Behavior changes for *every* species without an override**, because all three
  property copies are being unified onto the corrected whale-shark globals (today:
  bundle=6.8, devops=8). This is intended (the corrected globals beat the deployed
  values for whale sharks too: +0.138 mAP), but it is not a pure no-op for non-sand-tiger
  species — call it out in the PR. Update all three copies atomically.
- **Config is cached per context**; property edits require an app restart / cache
  reload to take effect (no live-reload endpoint in scope).
- New params are slower (~3× per query for whale sharks: higher sizelim builds more
  triangles) — accuracy/throughput tradeoff to note for scan capacity planning.
