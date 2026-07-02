# Restore Matchability of Spot-Crop (Unity) Annotations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make genuinely-matchable whole-image spot-crop annotations usable from the React Encounter page by gating the "New Match" button on real matchability (`matchAgainst && acmId`) instead of an `isTrivial`/bbox geometry proxy.

**Architecture:** Backend serializes the real matchability signal (`matchAgainst`, `acmId`) into each annotation in the encounter API response (`/api/v3/encounters/{id}`). The React frontend adds a `hasMatchableAnnotations` store getter computed from the raw annotations and gates the New Match button on it, leaving the existing `encounterAnnotations` getter and all other flows untouched.

**Tech Stack:** Java (Jackson JsonGenerator serialization, JUnit 5 + Mockito), React/MobX (Jest + React Testing Library).

## Global Constraints

- Base branch: `origin/main`; target GitHub Milestone 10.12. Working branch: `fix/react-matchable-unity-annotations` (already created).
- Matchability predicate is exactly `matchAgainst === true && acmId` (truthy) — mirrors backend eligibility (`matchAgainst=true AND acmId IS NOT NULL`).
- Line endings: this repo is LF. Before every `git add`, run `grep -c $'\r' <file>` and expect `0`; if not, `sed -i 's/\r$//' <file>`.
- JUnit 5 assertion message goes LAST: `assertEquals(expected, actual)` / `assertTrue(cond, "msg")`.
- Do NOT modify: `EncounterStore.encounterAnnotations` getter, the `ImageCard` boundary-drawing `rects` filter, `NewMatchStore.annotationIds`, or `SubmitSpotsAndTransformImage.java`.
- CI hides jest failures (continue-on-error); always run jest locally and read the result.

---

### Task 1: Backend — serialize `matchAgainst` and `acmId` per annotation

**Files:**
- Modify: `src/main/java/org/ecocean/Encounter.java` (annotation loop in `opensearchDocumentSerializer`, ~lines 4467-4472)
- Test: `src/test/java/org/ecocean/api/EncounterApiTest.java` (extend `encounterApiGetTest`, ~lines 211-237)

**Interfaces:**
- Consumes: `Annotation.getMatchAgainst()` → `boolean` (`Annotation.java:737`), `Annotation.getAcmId()` → `String` (`Annotation.java:328`).
- Produces: each object in the response `mediaAssets[].annotations[]` now has `matchAgainst` (boolean) and `acmId` (string, may be JSON null). Frontend Task 2 relies on these keys.

- [ ] **Step 1: Add failing assertions + stubs to the existing serialization test**

In `src/test/java/org/ecocean/api/EncounterApiTest.java`, inside `encounterApiGetTest`, add two stubs next to the existing annotation stubs (after `Annotation.java:214`'s `when(mockAnnot.getId())...`):

```java
                        when(mockAnnot.getMatchAgainst()).thenReturn(true);
                        when(mockAnnot.getAcmId()).thenReturn("test-acm-id");
```

Then, immediately after the existing `"id"` assertion (currently ending at line 237), add:

```java
                        assertEquals(true,
                            res.getJSONArray("mediaAssets").getJSONObject(1).getJSONArray(
                                "annotations").getJSONObject(0).getBoolean("matchAgainst"));
                        assertEquals("test-acm-id",
                            res.getJSONArray("mediaAssets").getJSONObject(1).getJSONArray(
                                "annotations").getJSONObject(0).getString("acmId"));
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=EncounterApiTest#encounterApiGetTest`
Expected: FAIL — `JSONObject["matchAgainst"] not found.` (fields not yet serialized).

- [ ] **Step 3: Implement the serialization**

In `src/main/java/org/ecocean/Encounter.java`, in the annotation loop of `opensearchDocumentSerializer`, immediately after the `isTrivial` line (`jgen.writeBooleanField("isTrivial", ann.isTrivial());`, ~line 4471) add:

```java
                    jgen.writeBooleanField("matchAgainst", ann.getMatchAgainst());
                    jgen.writeStringField("acmId", ann.getAcmId());
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=EncounterApiTest#encounterApiGetTest`
Expected: PASS (`BUILD SUCCESS`, Tests run: 1, Failures: 0).

- [ ] **Step 5: Commit**

```bash
cd /mnt/c/Wildbook-clean2
for f in src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/api/EncounterApiTest.java; do grep -c $'\r' "$f"; done
git add src/main/java/org/ecocean/Encounter.java src/test/java/org/ecocean/api/EncounterApiTest.java
git commit -m "feat: serialize matchAgainst and acmId in encounter API annotations

Encounter.opensearchDocumentSerializer now emits matchAgainst and acmId per
annotation so the React Encounter page can gate matching on real eligibility
instead of an isTrivial/bbox geometry proxy. Additive, served live via
jsonForApiGet (no reindex).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Frontend — `hasMatchableAnnotations` getter + ImageModalStore delegation

**Files:**
- Modify: `frontend/src/pages/Encounter/stores/EncounterStore.js` (add getter after `encounterAnnotations`, ~line 613)
- Modify: `frontend/src/pages/Encounter/stores/ImageModalStore.js` (add delegating getter after `encounterAnnotations`, ~line 41)
- Test: `frontend/src/__tests__/pages/Encounter/EncounterStore.test.js` (new tests in the existing "Encounter Annotations / Match Result Clickable" describe block)

**Interfaces:**
- Consumes: `encounterData.mediaAssets[selectedImageIndex].annotations[]` with `{ encounterId, matchAgainst, acmId }` (from Task 1).
- Produces: `EncounterStore.hasMatchableAnnotations` → `boolean`; `ImageModalStore.hasMatchableAnnotations` → `boolean` (delegates). Tasks 3 consume these.

- [ ] **Step 1: Write the failing tests**

In `frontend/src/__tests__/pages/Encounter/EncounterStore.test.js`, add these tests inside the `describe("Encounter Annotations / Match Result Clickable", ...)` block (after the existing `it("filters encounter annotations ...")`):

```javascript
      it("hasMatchableAnnotations is true for a matchAgainst+acmId unity annotation (isTrivial, full-image bbox)", () => {
        store.setEncounterData({
          id: "enc-123",
          mediaAssets: [
            {
              annotations: [
                {
                  id: "ann-1",
                  encounterId: "enc-123",
                  matchAgainst: true,
                  acmId: "acm-1",
                  isTrivial: true,
                  boundingBox: [0, 0, 1000, 500],
                },
              ],
            },
          ],
        });
        store.setSelectedImageIndex(0);

        expect(store.hasMatchableAnnotations).toBe(true);
      });

      it("hasMatchableAnnotations is false when matchAgainst is false or acmId is missing", () => {
        store.setEncounterData({
          id: "enc-123",
          mediaAssets: [
            {
              annotations: [
                { id: "a", encounterId: "enc-123", matchAgainst: false, acmId: "acm-1" },
                { id: "b", encounterId: "enc-123", matchAgainst: true },
              ],
            },
          ],
        });
        store.setSelectedImageIndex(0);

        expect(store.hasMatchableAnnotations).toBe(false);
      });

      it("hasMatchableAnnotations ignores annotations from a different encounterId", () => {
        store.setEncounterData({
          id: "enc-123",
          mediaAssets: [
            {
              annotations: [
                { id: "a", encounterId: "enc-999", matchAgainst: true, acmId: "acm-1" },
              ],
            },
          ],
        });
        store.setSelectedImageIndex(0);

        expect(store.hasMatchableAnnotations).toBe(false);
      });
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && npx jest src/__tests__/pages/Encounter/EncounterStore.test.js -t "hasMatchableAnnotations"`
Expected: FAIL (getter returns `undefined`, not `true`/`false`).

- [ ] **Step 3: Add the getter to EncounterStore**

In `frontend/src/pages/Encounter/stores/EncounterStore.js`, add immediately after the `encounterAnnotations` getter (after the closing `}` at ~line 613):

```javascript
  get hasMatchableAnnotations() {
    const annotations =
      this.encounterData?.mediaAssets?.[this._selectedImageIndex]?.annotations ||
      [];
    return annotations.some(
      (a) =>
        a.encounterId === this.encounterData?.id && a.matchAgainst && !!a.acmId,
    );
  }
```

- [ ] **Step 4: Add the delegating getter to ImageModalStore**

In `frontend/src/pages/Encounter/stores/ImageModalStore.js`, add immediately after the `encounterAnnotations` getter (after its closing `}` at ~line 41):

```javascript
  get hasMatchableAnnotations() {
    return this.encounterStore.hasMatchableAnnotations;
  }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd frontend && npx jest src/__tests__/pages/Encounter/EncounterStore.test.js -t "hasMatchableAnnotations"`
Expected: PASS (3 passed).

- [ ] **Step 6: Commit**

```bash
cd /mnt/c/Wildbook-clean2
for f in frontend/src/pages/Encounter/stores/EncounterStore.js frontend/src/pages/Encounter/stores/ImageModalStore.js frontend/src/__tests__/pages/Encounter/EncounterStore.test.js; do grep -c $'\r' "$f"; done
git add frontend/src/pages/Encounter/stores/EncounterStore.js frontend/src/pages/Encounter/stores/ImageModalStore.js frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
git commit -m "feat: add hasMatchableAnnotations store getter

Computes matchability from raw annotations (matchAgainst && acmId), filtered
by encounterId, without the isTrivial/bbox filter of encounterAnnotations.
ImageModalStore delegates to it.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Frontend — wire the New Match gate in ImageCard and ImageModal

**Files:**
- Modify: `frontend/src/pages/Encounter/ImageCard.jsx` (~lines 107-109 and usages ~793, 804, 806)
- Modify: `frontend/src/components/ImageModal.jsx` (~lines 216-218 and usages ~1109, 1111)
- Test: `frontend/src/__tests__/pages/Encounter/ImageCard.test.js` (`makeStore` default + new disabled test)
- Test: `frontend/src/__tests__/pages/Encounter/ImageModal.test.js` (`makeImageStore` default + new disabled test)

**Interfaces:**
- Consumes: `store.hasMatchableAnnotations` (ImageCard) and `imageStore.hasMatchableAnnotations` (ImageModal) from Task 2.
- Produces: New Match button enabled iff `hasMatchableAnnotations` is truthy.

- [ ] **Step 1: Write failing tests**

In `frontend/src/__tests__/pages/Encounter/ImageCard.test.js`, add `hasMatchableAnnotations: true,` to the `makeStore` defaults object (after `matchResultClickable: false,`, ~line 116). Then add this test after `test("clicking NEW_MATCH opens match criteria modal", ...)` (~line 201):

```javascript
  test("NEW_MATCH does nothing when hasMatchableAnnotations is false", async () => {
    const user = userEvent.setup();
    const store = makeStore({ hasMatchableAnnotations: false });
    renderCard(store);

    await user.click(screen.getByText("NEW_MATCH"));
    expect(store.modals.setOpenMatchCriteriaModal).not.toHaveBeenCalled();
  });
```

In `frontend/src/__tests__/pages/Encounter/ImageModal.test.js`, add `hasMatchableAnnotations: true,` to the `makeImageStore` defaults object (after `matchResultClickable: true,`, ~line 103). Then add this test after `test("match results button is disabled when matchResultClickable is false", ...)` (~line 243):

```javascript
  test("NEW_MATCH button is disabled when hasMatchableAnnotations is false", () => {
    const store = makeImageStore({ hasMatchableAnnotations: false });
    renderModal({ imageStore: store });

    const btn = screen.getByText("NEW_MATCH").closest("button");
    expect(btn).toBeDisabled();
  });
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && npx jest src/__tests__/pages/Encounter/ImageCard.test.js src/__tests__/pages/Encounter/ImageModal.test.js -t "NEW_MATCH"`
Expected: FAIL — ImageCard still reads the old computed `hasNonTrivialAnnotations` (from `encounterAnnotations`), and ImageModal's button still keys off the old variable.

- [ ] **Step 3: Wire ImageCard**

In `frontend/src/pages/Encounter/ImageCard.jsx`, replace lines 107-109:

```javascript
  const hasNonTrivialAnnotations = store.encounterAnnotations?.some(
    (a) => !a.isTrivial && (a.boundingBox?.[2] || 0) > 0 && (a.boundingBox?.[3] || 0) > 0
  );
```

with:

```javascript
  const hasMatchableAnnotations = store.hasMatchableAnnotations;
```

Then rename the three usages: at the `onClick` guard (`if (!hasNonTrivialAnnotations) return;`, ~line 793) and the two `style` references (`cursor:` ~804 and `opacity:` ~806), change `hasNonTrivialAnnotations` → `hasMatchableAnnotations`.

- [ ] **Step 4: Wire ImageModal**

In `frontend/src/components/ImageModal.jsx`, replace lines 216-218:

```javascript
    const hasNonTrivialAnnotations = imageStore.encounterAnnotations?.some(
      (a) => !a.isTrivial && (a.boundingBox?.[2] || 0) > 0 && (a.boundingBox?.[3] || 0) > 0
    );
```

with:

```javascript
    const hasMatchableAnnotations = imageStore.hasMatchableAnnotations;
```

Then rename the two usages: `disabled={!hasNonTrivialAnnotations}` (~line 1109) and `if (!hasNonTrivialAnnotations)` (~line 1111), change `hasNonTrivialAnnotations` → `hasMatchableAnnotations`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && npx jest src/__tests__/pages/Encounter/ImageCard.test.js src/__tests__/pages/Encounter/ImageModal.test.js`
Expected: PASS for the whole two files (existing NEW_MATCH-open test still passes via the `hasMatchableAnnotations: true` default; new disabled tests pass).

- [ ] **Step 6: Verify no stray references remain**

Run: `grep -rn "hasNonTrivialAnnotations" frontend/src`
Expected: no output (all renamed).

- [ ] **Step 7: Commit**

```bash
cd /mnt/c/Wildbook-clean2
for f in frontend/src/pages/Encounter/ImageCard.jsx frontend/src/components/ImageModal.jsx frontend/src/__tests__/pages/Encounter/ImageCard.test.js frontend/src/__tests__/pages/Encounter/ImageModal.test.js; do grep -c $'\r' "$f"; done
git add frontend/src/pages/Encounter/ImageCard.jsx frontend/src/components/ImageModal.jsx frontend/src/__tests__/pages/Encounter/ImageCard.test.js frontend/src/__tests__/pages/Encounter/ImageModal.test.js
git commit -m "fix: gate New Match button on hasMatchableAnnotations

ImageCard and ImageModal now enable New Match when the image has an
annotation with matchAgainst && acmId, instead of the isTrivial/bbox
geometry proxy that hid matchable whole-image spot-crop annotations.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Verification (after all tasks)

- [ ] Backend: `mvn test -Dtest=EncounterApiTest#encounterApiGetTest` → PASS.
- [ ] Frontend: `cd frontend && npx jest src/__tests__/pages/Encounter/EncounterStore.test.js src/__tests__/pages/Encounter/ImageCard.test.js src/__tests__/pages/Encounter/ImageModal.test.js` → new tests PASS; note any pre-existing failures unrelated to this change (e.g. the `encounterAnnotations` bbox-filter test) and do not "fix" them here.
- [ ] `grep -rn "hasNonTrivialAnnotations" frontend/src` → empty.
- [ ] Codex review of the full diff; converge to no Major findings.
- [ ] Open PR against `main`, assign Milestone 10.12; PR body documents the out-of-scope residual (unity annotations remain non-selectable for re-opening past match results) and the cosmetic boundary behavior.

## Notes / Known limitations (carry into PR body)

- Whole-image crops have no sub-rectangle, so no boundary overlay is drawn — cosmetic, expected.
- The `encounterAnnotations` getter stays filtered, so unity annotations remain non-selectable (can't re-open past match results from the Encounter page). New Match itself works end-to-end because it navigates straight to the fresh task's results and `NewMatchStore.annotationIds` already includes the annotation.
