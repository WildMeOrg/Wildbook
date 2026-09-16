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
