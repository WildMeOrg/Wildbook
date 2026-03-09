package org.ecocean.grid;

import org.ecocean.SuperSpot;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import java.util.ArrayList;

/**
 * Tests for EncounterLite JSON serialization round-trip
 * and GridManager cache serialization.
 */
class MatchGraphCacheTest {

    /** Helper: build a JSONObject the same way toJSONObject does (JSONArray for double[]). */
    private static JSONObject makeElJson(String encNum, String indiv, String date,
                                          String sex, double size,
                                          double[] spotsX, double[] spotsY,
                                          double[] rightSpotsX, double[] rightSpotsY) {
        JSONObject j = new JSONObject();
        j.put("encounterNumber", encNum);
        j.put("belongsToMarkedIndividual", indiv);
        j.put("date", date);
        j.put("sex", sex);
        j.put("size", size);
        if (spotsX != null) {
            j.put("spotsX", new JSONArray(spotsX));
            j.put("spotsY", new JSONArray(spotsY));
        }
        if (rightSpotsX != null) {
            j.put("rightSpotsX", new JSONArray(rightSpotsX));
            j.put("rightSpotsY", new JSONArray(rightSpotsY));
        }
        return j;
    }

    @Test
    void testEncounterLiteRoundTripWithAllFields() {
        JSONObject source = makeElJson("enc-001", "shark-42", "2024-03-15",
            "female", 3.5,
            new double[]{10.5, 20.3, 30.1, 40.0, 50.9},
            new double[]{11.2, 21.4, 31.6, 41.8, 51.0},
            new double[]{100.1, 200.2, 300.3},
            new double[]{110.1, 210.2, 310.3});
        source.put("genus", "Rhincodon");
        source.put("specificEpithet", "typus");
        source.put("submitterID", "user-7");
        source.put("locationID", "loc-pacific");
        source.put("leftReferenceSpotsX", new JSONArray(new double[]{1.0, 2.0, 3.0}));
        source.put("leftReferenceSpotsY", new JSONArray(new double[]{4.0, 5.0, 6.0}));
        source.put("rightReferenceSpotsX", new JSONArray(new double[]{7.0, 8.0, 9.0}));
        source.put("rightReferenceSpotsY", new JSONArray(new double[]{10.0, 11.0, 12.0}));

        // Deserialize from hand-built JSON
        EncounterLite loaded = EncounterLite.fromJSONObject(source);

        // Serialize back (this is the code path that actually writes to disk)
        JSONObject output = loaded.toJSONObject();

        // Deserialize again to verify full round-trip
        EncounterLite reloaded = EncounterLite.fromJSONObject(output);

        // Verify string fields
        assertEquals("enc-001", reloaded.getEncounterNumber());
        assertEquals("shark-42", reloaded.getBelongsToMarkedIndividual());
        assertEquals("2024-03-15", reloaded.getDate());
        assertEquals("Rhincodon", reloaded.getGenus());
        assertEquals("typus", reloaded.getSpecificEpithet());
        assertEquals("user-7", reloaded.getSubmitterID());
        assertEquals("loc-pacific", reloaded.getLocationID());

        // Verify left spots
        ArrayList leftSpots = reloaded.getSpots();
        assertNotNull(leftSpots);
        assertEquals(5, leftSpots.size());
        org.ecocean.SuperSpot firstSpot = (org.ecocean.SuperSpot) leftSpots.get(0);
        assertEquals(10.5, firstSpot.getCentroidX(), 1e-9);
        assertEquals(11.2, firstSpot.getCentroidY(), 1e-9);
        org.ecocean.SuperSpot lastSpot = (org.ecocean.SuperSpot) leftSpots.get(4);
        assertEquals(50.9, lastSpot.getCentroidX(), 1e-9);
        assertEquals(51.0, lastSpot.getCentroidY(), 1e-9);

        // Verify right spots
        ArrayList rightSpots = reloaded.getRightSpots();
        assertNotNull(rightSpots);
        assertEquals(3, rightSpots.size());
        org.ecocean.SuperSpot rightFirst = (org.ecocean.SuperSpot) rightSpots.get(0);
        assertEquals(100.1, rightFirst.getCentroidX(), 1e-9);
        assertEquals(110.1, rightFirst.getCentroidY(), 1e-9);

        // Verify reference spots
        org.ecocean.SuperSpot[] leftRef = reloaded.getLeftReferenceSpots();
        assertNotNull(leftRef);
        assertEquals(3, leftRef.length);
        assertEquals(1.0, leftRef[0].getCentroidX(), 1e-9);
        assertEquals(4.0, leftRef[0].getCentroidY(), 1e-9);

        org.ecocean.SuperSpot[] rightRef = reloaded.getRightReferenceSpots();
        assertNotNull(rightRef);
        assertEquals(3, rightRef.length);
        assertEquals(7.0, rightRef[0].getCentroidX(), 1e-9);
        assertEquals(10.0, rightRef[0].getCentroidY(), 1e-9);
    }

    @Test
    void testEncounterLiteRoundTripWithNullOptionalFields() {
        JSONObject source = makeElJson("enc-minimal", "", "", "Unknown", 0.0,
            new double[]{1.0, 2.0, 3.0, 4.0},
            new double[]{5.0, 6.0, 7.0, 8.0},
            null, null);

        EncounterLite loaded = EncounterLite.fromJSONObject(source);
        JSONObject output = loaded.toJSONObject();
        EncounterLite reloaded = EncounterLite.fromJSONObject(output);

        assertEquals("enc-minimal", reloaded.getEncounterNumber());
        assertNull(reloaded.getGenus());
        assertNull(reloaded.getSpecificEpithet());
        assertNull(reloaded.getSubmitterID());
        assertNull(reloaded.getLocationID());

        assertNotNull(reloaded.getSpots());
        assertEquals(4, reloaded.getSpots().size());
        assertNull(reloaded.getRightSpots());
        assertNull(reloaded.getLeftReferenceSpots());
        assertNull(reloaded.getRightReferenceSpots());
    }

    @Test
    void testGridManagerCacheRoundTrip() {
        JSONObject el1Json = makeElJson("enc-A", "ind-1", "2024-01-01", "male", 2.0,
            new double[]{1.0, 2.0, 3.0, 4.0, 5.0},
            new double[]{1.1, 2.1, 3.1, 4.1, 5.1},
            null, null);

        JSONObject el2Json = makeElJson("enc-B", "ind-2", "2024-06-15", "female", 4.0,
            null, null,
            new double[]{10.0, 20.0, 30.0},
            new double[]{11.0, 21.0, 31.0});
        el2Json.put("genus", "Rhincodon");
        el2Json.put("specificEpithet", "typus");

        EncounterLite el1 = EncounterLite.fromJSONObject(el1Json);
        EncounterLite el2 = EncounterLite.fromJSONObject(el2Json);

        // Clear and populate the matchGraph directly
        GridManager gm = GridManagerFactory.getGridManager();
        gm.resetMatchGraphWithInitialCapacity(10);
        GridManager.getMatchGraph().put("enc-A", el1);
        GridManager.getMatchGraph().put("enc-B", el2);

        // Serialize the whole matchGraph to JSON (as cacheWrite does)
        JSONObject cacheJson = GridManager.cacheToJSONObject();

        // Verify structure
        assertTrue(cacheJson.has("timestamp"));
        assertTrue(cacheJson.has("matchGraph"));
        assertEquals(2, cacheJson.getInt("count"));

        JSONObject entries = cacheJson.getJSONObject("matchGraph");
        assertTrue(entries.has("enc-A"));
        assertTrue(entries.has("enc-B"));

        // Simulate what cacheRead does: clear graph and reload from JSON
        gm.resetMatchGraphWithInitialCapacity(10);
        assertEquals(0, GridManager.getMatchGraph().size());

        // Parse the JSON string back (simulates reading from file)
        String jsonString = cacheJson.toString();
        JSONObject parsed = new JSONObject(jsonString);
        JSONObject reloadedEntries = parsed.getJSONObject("matchGraph");
        for (String key : reloadedEntries.keySet()) {
            EncounterLite el = EncounterLite.fromJSONObject(reloadedEntries.getJSONObject(key));
            GridManager.getMatchGraph().put(key, el);
        }

        // Verify reloaded data
        assertEquals(2, GridManager.getMatchGraph().size());

        EncounterLite reloadedA = GridManager.getMatchGraphEncounterLiteEntry("enc-A");
        assertNotNull(reloadedA);
        assertEquals("enc-A", reloadedA.getEncounterNumber());
        assertEquals("ind-1", reloadedA.getBelongsToMarkedIndividual());
        assertNotNull(reloadedA.getSpots());
        assertEquals(5, reloadedA.getSpots().size());

        EncounterLite reloadedB = GridManager.getMatchGraphEncounterLiteEntry("enc-B");
        assertNotNull(reloadedB);
        assertEquals("enc-B", reloadedB.getEncounterNumber());
        assertEquals("Rhincodon", reloadedB.getGenus());
        assertEquals("typus", reloadedB.getSpecificEpithet());
        assertNotNull(reloadedB.getRightSpots());
        assertEquals(3, reloadedB.getRightSpots().size());
        assertNull(reloadedB.getSpots());
    }

    @Test
    void testGrothCompatibilityAfterRoundTrip() {
        // Build via toJSONObject→fromJSONObject to test the real code path
        JSONObject source = makeElJson("enc-groth", "ind-groth", "2024-01-01",
            "Unknown", 0.0,
            new double[]{0.1, 0.5, 0.9, 0.3, 0.7},
            new double[]{0.2, 0.8, 0.1, 0.6, 0.4},
            null, null);

        EncounterLite original = EncounterLite.fromJSONObject(source);

        // Full round-trip through JSON string (as disk cache would)
        String jsonStr = original.toJSONObject().toString();
        EncounterLite el = EncounterLite.fromJSONObject(new JSONObject(jsonStr));

        // getSpots() reconstructs SuperSpot ArrayList from the double arrays
        ArrayList spots = el.getSpots();
        assertNotNull(spots);
        assertEquals(5, spots.size());

        // Verify each spot has a proper Spot with getCentroidX/Y
        for (int i = 0; i < spots.size(); i++) {
            org.ecocean.SuperSpot ss = (org.ecocean.SuperSpot) spots.get(i);
            assertNotNull(ss.getTheSpot(), "SuperSpot at index " + i + " should have a Spot");
            assertTrue(ss.getCentroidX() > 0, "X coordinate should be positive");
            assertTrue(ss.getCentroidY() > 0, "Y coordinate should be positive");
        }

        // Verify the spots can be converted to array form (as Groth does)
        org.ecocean.SuperSpot[] spotArray = new org.ecocean.SuperSpot[0];
        spotArray = (org.ecocean.SuperSpot[]) spots.toArray(spotArray);
        assertEquals(5, spotArray.length);

        // Verify coordinates survive JSON string serialization exactly
        assertEquals(0.1, spotArray[0].getCentroidX(), 1e-9);
        assertEquals(0.2, spotArray[0].getCentroidY(), 1e-9);
        assertEquals(0.7, spotArray[4].getCentroidX(), 1e-9);
        assertEquals(0.4, spotArray[4].getCentroidY(), 1e-9);
    }

    @Test
    void testMatchGraphReadinessFlag() {
        // Initially not ready
        GridManager.setMatchGraphReady(false);
        assertFalse(GridManager.isMatchGraphReady(),
            "matchGraph should not be ready before loading");

        // After manual set, should be ready
        GridManager.setMatchGraphReady(true);
        assertTrue(GridManager.isMatchGraphReady(),
            "matchGraph should be ready after setMatchGraphReady(true)");

        // Reset for other tests
        GridManager.setMatchGraphReady(false);
    }

    @Test
    void testReadinessLifecycle() {
        GridManager gm = GridManagerFactory.getGridManager();

        // 1. Start not ready
        GridManager.setMatchGraphReady(false);
        assertFalse(GridManager.isMatchGraphReady());

        // 2. Simulate successful cache load — readiness set true
        gm.resetMatchGraphWithInitialCapacity(2);
        JSONObject el1Json = makeElJson("enc-life-1", "ind-1", "2024-01-01", "male", 2.0,
            new double[]{1.0, 2.0, 3.0, 4.0}, new double[]{1.1, 2.1, 3.1, 4.1}, null, null);
        GridManager.addMatchGraphEntryBulk("enc-life-1", EncounterLite.fromJSONObject(el1Json));
        GridManager.resetPatternCounts();
        GridManager.setMatchGraphReady(true);
        assertTrue(GridManager.isMatchGraphReady(), "Should be ready after load");
        assertEquals(1, GridManager.getMatchGraph().size());

        // 3. Simulate rebuild start — readiness must go false before clearing
        GridManager.setMatchGraphReady(false);
        gm.resetMatchGraphWithInitialCapacity(10);
        assertFalse(GridManager.isMatchGraphReady(),
            "Should NOT be ready during rebuild");
        assertEquals(0, GridManager.getMatchGraph().size());

        // 4. Simulate rebuild failure — readiness stays false
        // (catch block does not set ready = true)
        assertFalse(GridManager.isMatchGraphReady(),
            "Should remain not ready after failed rebuild");

        // 5. Simulate successful rebuild — readiness set true again
        JSONObject el2Json = makeElJson("enc-life-2", "ind-2", "2024-06-01", "female", 3.0,
            new double[]{5.0, 6.0, 7.0, 8.0}, new double[]{5.1, 6.1, 7.1, 8.1}, null, null);
        GridManager.addMatchGraphEntryBulk("enc-life-2", EncounterLite.fromJSONObject(el2Json));
        GridManager.resetPatternCounts();
        GridManager.setMatchGraphReady(true);
        assertTrue(GridManager.isMatchGraphReady(), "Should be ready after successful rebuild");

        // Cleanup
        GridManager.setMatchGraphReady(false);
        gm.resetMatchGraphWithInitialCapacity(0);
    }

    @Test
    void testGrothMatchAgainstLoadedMatchGraph() {
        // Build a small matchGraph with known spots
        GridManager gm = GridManagerFactory.getGridManager();
        gm.resetMatchGraphWithInitialCapacity(10);

        // Create 3 catalog encounters with distinct spot patterns
        double[][] patterns = {
            {0.1, 0.2, 0.5, 0.8, 0.9},  // enc-cat-1
            {0.1, 0.2, 0.5, 0.8, 0.9},  // enc-cat-2 (same pattern, different individual)
            {0.3, 0.4, 0.6, 0.7, 0.95}, // enc-cat-3 (different pattern)
        };
        double[][] yCoords = {
            {0.1, 0.3, 0.5, 0.7, 0.9},
            {0.1, 0.3, 0.5, 0.7, 0.9},
            {0.2, 0.4, 0.6, 0.8, 0.85},
        };
        String[] ids = {"enc-cat-1", "enc-cat-2", "enc-cat-3"};

        for (int i = 0; i < 3; i++) {
            JSONObject elJson = makeElJson(ids[i], "ind-" + i, "2024-01-0" + (i + 1),
                "Unknown", 0.0, patterns[i], yCoords[i], null, null);
            GridManager.addMatchGraphEntry(ids[i], EncounterLite.fromJSONObject(elJson));
        }
        GridManager.setMatchGraphReady(true);

        assertEquals(3, GridManager.getMatchGraph().size());
        assertTrue(GridManager.isMatchGraphReady());

        // Query with the same pattern as enc-cat-1/2 — they should score highest
        SuperSpot[] querySpots = new SuperSpot[5];
        double[] qx = {0.1, 0.2, 0.5, 0.8, 0.9};
        double[] qy = {0.1, 0.3, 0.5, 0.7, 0.9};
        for (int i = 0; i < 5; i++) {
            querySpots[i] = new SuperSpot(qx[i], qy[i]);
        }

        // Run Groth against the matchGraph (same logic as GrothMatchServlet Phase 2)
        double epsilon = 0.008, R = 6.8, Sizelim = 0.671, maxRot = 22.5, C = 1.146;
        List<MatchObject> results = new java.util.ArrayList<>();
        for (var entry : GridManager.getMatchGraph().entrySet()) {
            EncounterLite el = entry.getValue();
            MatchObject mo = el.getPointsForBestMatch(
                querySpots, epsilon, R, Sizelim, maxRot, C, true, false);
            mo.encounterNumber = entry.getKey();
            results.add(mo);
        }

        MatchObject[] matchArray = results.toArray(new MatchObject[0]);
        Arrays.sort(matchArray, new MatchComparator());

        // enc-cat-1 and enc-cat-2 should have identical best scores (same pattern)
        // enc-cat-3 should score lower
        assertEquals(3, matchArray.length);
        double score1 = matchArray[0].getMatchValue() * matchArray[0].getAdjustedMatchValue();
        double score3 = matchArray[2].getMatchValue() * matchArray[2].getAdjustedMatchValue();
        assertTrue(score1 >= score3,
            "Identical patterns should score >= different pattern");

        // Top two should be cat-1 or cat-2 (same pattern)
        String top1 = matchArray[0].getEncounterNumber();
        String top2 = matchArray[1].getEncounterNumber();
        assertTrue(
            (top1.equals("enc-cat-1") || top1.equals("enc-cat-2")) &&
            (top2.equals("enc-cat-1") || top2.equals("enc-cat-2")),
            "Top 2 matches should be the encounters with identical patterns, got: " +
            top1 + ", " + top2);

        // Cleanup
        GridManager.setMatchGraphReady(false);
        gm.resetMatchGraphWithInitialCapacity(0);
    }
}
