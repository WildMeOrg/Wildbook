package org.ecocean.grid.tetra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TetraPatternTest {

    // Simple square: (0,0), (1,0), (1,1), (0,1)
    private static final double[] SQUARE_X = { 0, 1, 1, 0 };
    private static final double[] SQUARE_Y = { 0, 0, 1, 1 };

    @Test
    void testPatternFromSquare() {
        TetraPattern p = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        double[] ratios = p.getRatios();
        assertNotNull(ratios);
        assertEquals(5, ratios.length);
        // Square has 4 edges of length 1 and 2 diagonals of length sqrt(2)
        // Sorted edges: [1, 1, 1, 1, sqrt(2), sqrt(2)]
        // Ratios: [1/sqrt(2), 1/sqrt(2), 1/sqrt(2), 1/sqrt(2), sqrt(2)/sqrt(2)]
        //       = [0.7071, 0.7071, 0.7071, 0.7071, 1.0]
        double expected = 1.0 / Math.sqrt(2);
        assertEquals(expected, ratios[0], 0.001);
        assertEquals(expected, ratios[1], 0.001);
        assertEquals(expected, ratios[2], 0.001);
        assertEquals(expected, ratios[3], 0.001);
        assertEquals(1.0, ratios[4], 0.001);
    }

    @Test
    void testInvariantUnderTranslation() {
        // Original square
        TetraPattern p1 = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        // Translated by (100, 200)
        double[] tx = { 100, 101, 101, 100 };
        double[] ty = { 200, 200, 201, 201 };
        TetraPattern p2 = new TetraPattern(tx, ty, 0, 1, 2, 3, 25);

        assertEquals(p1.getHashKey(), p2.getHashKey());
        assertArrayEquals(p1.getRatios(), p2.getRatios(), 1e-10);
    }

    @Test
    void testInvariantUnderUniformScaling() {
        TetraPattern p1 = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        // Scaled by factor 5
        double[] sx = { 0, 5, 5, 0 };
        double[] sy = { 0, 0, 5, 5 };
        TetraPattern p2 = new TetraPattern(sx, sy, 0, 1, 2, 3, 25);

        assertEquals(p1.getHashKey(), p2.getHashKey());
        assertArrayEquals(p1.getRatios(), p2.getRatios(), 1e-10);
    }

    @Test
    void testInvariantUnderRotation() {
        TetraPattern p1 = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        // Rotate 45 degrees around origin
        double angle = Math.PI / 4;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double[] rx = new double[4];
        double[] ry = new double[4];
        for (int i = 0; i < 4; i++) {
            rx[i] = SQUARE_X[i] * cos - SQUARE_Y[i] * sin;
            ry[i] = SQUARE_X[i] * sin + SQUARE_Y[i] * cos;
        }
        TetraPattern p2 = new TetraPattern(rx, ry, 0, 1, 2, 3, 25);

        assertEquals(p1.getHashKey(), p2.getHashKey());
        assertArrayEquals(p1.getRatios(), p2.getRatios(), 1e-10);
    }

    @Test
    void testInvariantUnderCombinedTransform() {
        TetraPattern p1 = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        // Scale by 3, rotate 60 degrees, translate by (50, -30)
        double scale = 3.0;
        double angle = Math.PI / 3;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double[] tx = new double[4];
        double[] ty = new double[4];
        for (int i = 0; i < 4; i++) {
            double sx = SQUARE_X[i] * scale;
            double sy = SQUARE_Y[i] * scale;
            tx[i] = sx * cos - sy * sin + 50;
            ty[i] = sx * sin + sy * cos - 30;
        }
        TetraPattern p2 = new TetraPattern(tx, ty, 0, 1, 2, 3, 25);

        assertEquals(p1.getHashKey(), p2.getHashKey());
        assertArrayEquals(p1.getRatios(), p2.getRatios(), 1e-10);
    }

    @Test
    void testDifferentShapesProduceDifferentKeys() {
        // Square
        TetraPattern p1 = new TetraPattern(SQUARE_X, SQUARE_Y, 0, 1, 2, 3, 25);
        // Very elongated rectangle: 10x1
        double[] rectX = { 0, 10, 10, 0 };
        double[] rectY = { 0, 0, 1, 1 };
        TetraPattern p2 = new TetraPattern(rectX, rectY, 0, 1, 2, 3, 25);

        assertNotEquals(p1.getHashKey(), p2.getHashKey());
    }

    @Test
    void testQuantizeRanges() {
        // All ratios at 0 should produce key 0
        double[] zeros = { 0, 0, 0, 0, 0 };
        assertEquals(0, TetraPattern.quantize(zeros, 25));

        // All ratios at just below 1.0 should produce max key
        double[] ones = { 0.99, 0.99, 0.99, 0.99, 0.99 };
        long maxKey = TetraPattern.quantize(ones, 25);
        // Each dimension at bin 24: 24*25^4 + 24*25^3 + 24*25^2 + 24*25 + 24
        long expected = 24L * 25 * 25 * 25 * 25 + 24L * 25 * 25 * 25 +
                        24L * 25 * 25 + 24L * 25 + 24L;
        assertEquals(expected, maxKey);
    }

    @Test
    void testComputeEdges() {
        // Right triangle: (0,0), (3,0), (0,4), (3,4)
        double[] x = { 0, 3, 0, 3 };
        double[] y = { 0, 0, 4, 4 };
        double[] edges = TetraPattern.computeEdges(x, y, 0, 1, 2, 3);
        assertEquals(6, edges.length);
        // Edges: (0,1)=3, (0,2)=4, (0,3)=5, (1,2)=5, (1,3)=4, (2,3)=3
        java.util.Arrays.sort(edges);
        assertEquals(3.0, edges[0], 0.001);
        assertEquals(3.0, edges[1], 0.001);
        assertEquals(4.0, edges[2], 0.001);
        assertEquals(4.0, edges[3], 0.001);
        assertEquals(5.0, edges[4], 0.001);
        assertEquals(5.0, edges[5], 0.001);
    }

    @Test
    void testSpotIndicesPreserved() {
        double[] x = { 0, 1, 2, 3, 4 };
        double[] y = { 0, 1, 0, 1, 0 };
        TetraPattern p = new TetraPattern(x, y, 1, 2, 3, 4, 25);
        int[] idx = p.getSpotIndices();
        assertEquals(1, idx[0]);
        assertEquals(2, idx[1]);
        assertEquals(3, idx[2]);
        assertEquals(4, idx[3]);
    }

    @Test
    void testDegenerateCollinearSpots() {
        // Collinear points: all on x-axis
        double[] x = { 0, 1, 2, 3 };
        double[] y = { 0, 0, 0, 0 };
        TetraPattern p = new TetraPattern(x, y, 0, 1, 2, 3, 25);
        // Should not throw, ratios should be valid
        double[] ratios = p.getRatios();
        assertNotNull(ratios);
        assertEquals(5, ratios.length);
        // All ratios should be between 0 and 1
        for (double r : ratios) {
            assertTrue(r >= 0, "Ratio should be >= 0: " + r);
            assertTrue(r <= 1.0, "Ratio should be <= 1: " + r);
        }
    }
}
