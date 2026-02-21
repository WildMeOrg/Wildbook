package org.ecocean.grid.tetra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TetraQueryEngineTest {

    @Test
    void testBinomialCoefficient() {
        assertEquals(1, TetraQueryEngine.binomial(4, 4));
        assertEquals(5, TetraQueryEngine.binomial(5, 4));
        assertEquals(15, TetraQueryEngine.binomial(6, 4));
        assertEquals(35, TetraQueryEngine.binomial(7, 4));
        assertEquals(70, TetraQueryEngine.binomial(8, 4));
        assertEquals(1365, TetraQueryEngine.binomial(15, 4));
        assertEquals(0, TetraQueryEngine.binomial(3, 4));
        assertEquals(0, TetraQueryEngine.binomial(0, 4));
        assertEquals(1, TetraQueryEngine.binomial(5, 0));
    }

    @Test
    void testMatchWithIdenticalSpots() {
        // Build index with a pentagon of spots
        TetraHashIndex leftIndex = new TetraHashIndex(25);
        TetraHashIndex rightIndex = new TetraHashIndex(25);

        double[] pentX = { 0.0, 0.95, 0.59, -0.59, -0.95 };
        double[] pentY = { 1.0, 0.31, -0.81, -0.81, 0.31 };

        // Normalize
        double max = 0;
        for (double v : pentX) max = Math.max(max, Math.abs(v));
        for (double v : pentY) max = Math.max(max, Math.abs(v));
        double[] normX = new double[5];
        double[] normY = new double[5];
        for (int i = 0; i < 5; i++) {
            normX[i] = (pentX[i] + 1) / 2; // shift to positive
            normY[i] = (pentY[i] + 1) / 2;
        }

        leftIndex.indexEncounter("catalog1", normX, normY);

        TetraConfig config = new TetraConfig();
        config.setTopK(10);
        config.setMinVotes(1);
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);

        // Query with the same spots (should match perfectly)
        java.util.ArrayList<org.ecocean.SuperSpot> querySpots = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            querySpots.add(new org.ecocean.SuperSpot(normX[i], normY[i]));
        }

        // Note: match() excludes self by encounterId, so use different ID
        java.util.List<org.ecocean.grid.MatchObject> results =
            engine.match(querySpots, false, "query1");

        assertFalse(results.isEmpty(), "Should find the catalog encounter");
        assertEquals("catalog1", results.get(0).getEncounterNumber());
        assertTrue(results.get(0).getMatchValue() > 0, "Match value should be positive");
    }

    @Test
    void testMatchExcludesSelf() {
        TetraHashIndex leftIndex = new TetraHashIndex(25);
        TetraHashIndex rightIndex = new TetraHashIndex(25);

        double[] x = { 0.1, 0.9, 0.9, 0.1 };
        double[] y = { 0.1, 0.1, 0.9, 0.9 };
        leftIndex.indexEncounter("enc1", x, y);

        TetraConfig config = new TetraConfig();
        config.setMinVotes(1);
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);

        java.util.ArrayList<org.ecocean.SuperSpot> querySpots = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            querySpots.add(new org.ecocean.SuperSpot(x[i], y[i]));
        }

        // Query with same encounter ID - should return empty
        java.util.List<org.ecocean.grid.MatchObject> results =
            engine.match(querySpots, false, "enc1");
        assertTrue(results.isEmpty(), "Should not match self");
    }

    @Test
    void testMatchWithTooFewSpots() {
        TetraHashIndex leftIndex = new TetraHashIndex(25);
        TetraHashIndex rightIndex = new TetraHashIndex(25);
        TetraConfig config = new TetraConfig();
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);

        java.util.ArrayList<org.ecocean.SuperSpot> querySpots = new java.util.ArrayList<>();
        querySpots.add(new org.ecocean.SuperSpot(0, 0));
        querySpots.add(new org.ecocean.SuperSpot(1, 0));
        querySpots.add(new org.ecocean.SuperSpot(0.5, 1));

        java.util.List<org.ecocean.grid.MatchObject> results =
            engine.match(querySpots, false, "q1");
        assertTrue(results.isEmpty(), "Should return empty for <4 spots");
    }

    @Test
    void testScaledPatternFindsMatch() {
        TetraHashIndex leftIndex = new TetraHashIndex(25);
        TetraHashIndex rightIndex = new TetraHashIndex(25);

        // Index a unit square (normalized)
        double[] catX = { 0.0, 1.0, 1.0, 0.0 };
        double[] catY = { 0.0, 0.0, 1.0, 1.0 };
        leftIndex.indexEncounter("catalog1", catX, catY);

        TetraConfig config = new TetraConfig();
        config.setMinVotes(1);
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);

        // Query with a 500x scaled square
        java.util.ArrayList<org.ecocean.SuperSpot> querySpots = new java.util.ArrayList<>();
        querySpots.add(new org.ecocean.SuperSpot(0, 0));
        querySpots.add(new org.ecocean.SuperSpot(500, 0));
        querySpots.add(new org.ecocean.SuperSpot(500, 500));
        querySpots.add(new org.ecocean.SuperSpot(0, 500));

        java.util.List<org.ecocean.grid.MatchObject> results =
            engine.match(querySpots, false, "query1");
        assertFalse(results.isEmpty(), "Scaled pattern should match");
    }

    @Test
    void testDifferentShapeDoesNotMatch() {
        TetraHashIndex leftIndex = new TetraHashIndex(25);
        TetraHashIndex rightIndex = new TetraHashIndex(25);

        // Index a square
        double[] catX = { 0.0, 1.0, 1.0, 0.0 };
        double[] catY = { 0.0, 0.0, 1.0, 1.0 };
        leftIndex.indexEncounter("catalog1", catX, catY);

        TetraConfig config = new TetraConfig();
        config.setMinVotes(1);
        config.setToleranceEnabled(false); // strict matching
        config.setMaxRatioDistance(0.01);   // very tight threshold
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);

        // Query with a very elongated shape (100x1)
        java.util.ArrayList<org.ecocean.SuperSpot> querySpots = new java.util.ArrayList<>();
        querySpots.add(new org.ecocean.SuperSpot(0, 0));
        querySpots.add(new org.ecocean.SuperSpot(100, 0));
        querySpots.add(new org.ecocean.SuperSpot(100, 1));
        querySpots.add(new org.ecocean.SuperSpot(0, 1));

        java.util.List<org.ecocean.grid.MatchObject> results =
            engine.match(querySpots, false, "query1");
        assertTrue(results.isEmpty(), "Very different shape should not match");
    }
}
