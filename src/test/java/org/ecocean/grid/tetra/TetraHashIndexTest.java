package org.ecocean.grid.tetra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class TetraHashIndexTest {

    private TetraHashIndex index;

    // Square pattern spots
    private static final double[] SQUARE_X = { 0.0, 1.0, 1.0, 0.0 };
    private static final double[] SQUARE_Y = { 0.0, 0.0, 1.0, 1.0 };

    // Diamond pattern spots (rotated square)
    private static final double[] DIAMOND_X = { 0.5, 1.0, 0.5, 0.0 };
    private static final double[] DIAMOND_Y = { 0.0, 0.5, 1.0, 0.5 };

    @BeforeEach
    void setUp() {
        index = new TetraHashIndex(25);
    }

    @Test
    void testIndexAndLookupRoundTrip() {
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);
        assertEquals(1, index.getNumIndexedEncounters());
        assertTrue(index.isEncounterIndexed("enc1"));

        // Generate same pattern and look up
        TetraPattern p = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        List<TetraHashEntry> hits = index.lookup(p.getHashKey());
        assertFalse(hits.isEmpty(), "Should find at least one entry");
        assertEquals("enc1", hits.get(0).getEncounterId());
    }

    @Test
    void testSameShapeDifferentScaleFindsMatch() {
        // Index a unit square
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);

        // Look up with a 5x scaled square (same shape, different scale)
        double[] bigX = { 0, 5, 5, 0 };
        double[] bigY = { 0, 0, 5, 5 };
        TetraPattern p = new TetraPattern(bigX, bigY, 0, 1, 2, 3, 25);
        List<TetraHashEntry> hits = index.lookup(p.getHashKey());
        assertFalse(hits.isEmpty(), "Scaled square should match");
    }

    @Test
    void testRemoveEncounter() {
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);
        assertTrue(index.isEncounterIndexed("enc1"));

        index.removeEncounter("enc1");
        assertFalse(index.isEncounterIndexed("enc1"));
        assertEquals(0, index.getNumIndexedEncounters());

        // Verify lookup no longer finds it
        TetraPattern p = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        List<TetraHashEntry> hits = index.lookup(p.getHashKey());
        assertTrue(hits.isEmpty(), "Should be empty after removal");
    }

    @Test
    void testReindexEncounter() {
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);
        // Re-index with different spots
        double[] newX = { 0, 2, 2, 0 };
        double[] newY = { 0, 0, 3, 3 };
        index.indexEncounter("enc1", newX, newY);

        assertEquals(1, index.getNumIndexedEncounters());
        assertTrue(index.isEncounterIndexed("enc1"));
    }

    @Test
    void testMultipleEncounters() {
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);

        double[] rectX = { 0, 3, 3, 0 };
        double[] rectY = { 0, 0, 1, 1 };
        index.indexEncounter("enc2", rectX, rectY);

        assertEquals(2, index.getNumIndexedEncounters());
        assertTrue(index.isEncounterIndexed("enc1"));
        assertTrue(index.isEncounterIndexed("enc2"));
    }

    @Test
    void testToleranceLookupFindsNeighboringBins() {
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);

        // Slightly perturbed square (should land in neighboring bin)
        double[] pertX = { 0.01, 0.99, 1.01, -0.01 };
        double[] pertY = { 0.01, -0.01, 0.99, 1.01 };
        TetraPattern p = new TetraPattern(pertX, pertY, 0, 1, 2, 3, 25);

        List<TetraHashEntry> hits = index.lookupWithTolerance(
            p.getHashKey(), p.getRatios());
        assertFalse(hits.isEmpty(), "Tolerance lookup should find perturbed match");
    }

    @Test
    void testTooFewSpots() {
        // Only 3 spots - should not index anything
        double[] x3 = { 0, 1, 0.5 };
        double[] y3 = { 0, 0, 1 };
        index.indexEncounter("enc1", x3, y3);
        assertEquals(0, index.getNumIndexedEncounters());
    }

    @Test
    void testFiveSpotsGeneratesMultiplePatterns() {
        // 5 spots -> C(5,4) = 5 patterns
        double[] x5 = { 0, 1, 2, 0.5, 1.5 };
        double[] y5 = { 0, 0, 0, 1, 1 };
        index.indexEncounter("enc1", x5, y5);
        assertTrue(index.isEncounterIndexed("enc1"));
        assertTrue(index.getTableSize() > 0, "Should have entries in hash table");
    }

    @Test
    void testRemoveNonexistentEncounter() {
        // Should not throw
        index.removeEncounter("nonexistent");
        assertEquals(0, index.getNumIndexedEncounters());
    }

    @Test
    void testRotatedSquareMatchesOriginal() {
        // Diamond is just a rotated square - should have same hash key
        index.indexEncounter("enc1", SQUARE_X, SQUARE_Y);

        TetraPattern p = new TetraPattern(DIAMOND_X, DIAMOND_Y, 0, 1, 2, 3, 25);
        List<TetraHashEntry> hits = index.lookup(p.getHashKey());
        assertFalse(hits.isEmpty(), "Rotated square (diamond) should match square");
    }
}
