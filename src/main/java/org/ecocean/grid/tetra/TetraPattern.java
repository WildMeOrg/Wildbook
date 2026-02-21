package org.ecocean.grid.tetra;

import java.util.Arrays;

/**
 * Immutable representation of a TETRA 4-spot pattern.
 *
 * From 4 spots: computes 6 inter-spot distances, sorts by length,
 * normalizes to 5 edge ratios (each divided by the longest edge),
 * and quantizes into a hash key. The resulting ratios are invariant
 * under rotation, translation, and uniform scaling.
 */
public class TetraPattern implements java.io.Serializable {
    static final long serialVersionUID = 7293851046283710594L;

    private final int spotIdx0, spotIdx1, spotIdx2, spotIdx3;
    private final double[] ratios;
    private final long hashKey;

    /**
     * Constructs a TetraPattern from 4 spot indices into the given coordinate arrays.
     *
     * @param spotX   x-coordinates of all spots
     * @param spotY   y-coordinates of all spots
     * @param i0      index of first spot
     * @param i1      index of second spot
     * @param i2      index of third spot
     * @param i3      index of fourth spot
     * @param numBins quantization bins per ratio dimension
     */
    public TetraPattern(double[] spotX, double[] spotY,
                        int i0, int i1, int i2, int i3,
                        int numBins) {
        this.spotIdx0 = i0;
        this.spotIdx1 = i1;
        this.spotIdx2 = i2;
        this.spotIdx3 = i3;

        double[] edges = computeEdges(spotX, spotY, i0, i1, i2, i3);
        Arrays.sort(edges);

        double longest = edges[5];
        this.ratios = new double[5];
        if (longest > 0) {
            for (int r = 0; r < 5; r++) {
                ratios[r] = edges[r] / longest;
            }
        }

        this.hashKey = quantize(ratios, numBins);
    }

    /**
     * Computes 6 inter-spot Euclidean distances for 4 spots.
     * Order: (i0,i1), (i0,i2), (i0,i3), (i1,i2), (i1,i3), (i2,i3)
     */
    static double[] computeEdges(double[] sx, double[] sy,
                                  int i0, int i1, int i2, int i3) {
        int[] idx = { i0, i1, i2, i3 };
        double[] edges = new double[6];
        int e = 0;
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                double dx = sx[idx[a]] - sx[idx[b]];
                double dy = sy[idx[a]] - sy[idx[b]];
                edges[e++] = Math.sqrt(dx * dx + dy * dy);
            }
        }
        return edges;
    }

    /**
     * Quantizes 5 ratios (each in [0,1]) into a single hash key.
     * Key space: numBins^5 (e.g., 25^5 = 9,765,625).
     */
    static long quantize(double[] ratios, int numBins) {
        long key = 0;
        for (int i = 0; i < 5; i++) {
            int bin = (int) (ratios[i] * numBins);
            if (bin >= numBins) bin = numBins - 1;
            if (bin < 0) bin = 0;
            key = key * numBins + bin;
        }
        return key;
    }

    public long getHashKey() { return hashKey; }

    public double[] getRatios() { return ratios; }

    public int[] getSpotIndices() {
        return new int[] { spotIdx0, spotIdx1, spotIdx2, spotIdx3 };
    }
}
