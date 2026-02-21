package org.ecocean.grid.tetra;

/**
 * A single entry in the TETRA hash table.
 * Maps a hash key to a specific 4-spot pattern in a specific encounter.
 * Uses float[] for ratios to minimize memory (~48 bytes per entry).
 */
public class TetraHashEntry implements java.io.Serializable {
    static final long serialVersionUID = 2847195063841729503L;

    private final String encounterId;
    private final short spotIdx0, spotIdx1, spotIdx2, spotIdx3;
    private final float[] ratios;

    public TetraHashEntry(String encounterId, TetraPattern pattern) {
        this.encounterId = encounterId;
        int[] idx = pattern.getSpotIndices();
        this.spotIdx0 = (short) idx[0];
        this.spotIdx1 = (short) idx[1];
        this.spotIdx2 = (short) idx[2];
        this.spotIdx3 = (short) idx[3];
        double[] dRatios = pattern.getRatios();
        this.ratios = new float[5];
        for (int i = 0; i < 5; i++) {
            this.ratios[i] = (float) dRatios[i];
        }
    }

    public String getEncounterId() { return encounterId; }

    public float[] getRatios() { return ratios; }

    public int[] getSpotIndices() {
        return new int[] { spotIdx0, spotIdx1, spotIdx2, spotIdx3 };
    }
}
