package org.ecocean.grid;

import org.ecocean.Spot;

import java.awt.geom.Point2D;

public class SpotTriangle implements java.io.Serializable {
    static final long serialVersionUID = -5400879461028598731L;
    // 3 spots of the triangle
// public spot s1, s2, s3;
    public double Dij, Dik, Djk, D12, D13, D23;
    public double Dxl, Dyl, Dxs, Dys;
    public double CrossProd, VarFact;
    public Spot v1, v2, v3;
    public double logPerimeter;
    public boolean clockwise;
    public double ratioLong2Short, toleranceRatioLong2Short;
    public double cosineAtVertex1, toleranceInCosineAtVertex1;
    public double R, C, S2, tR2, tC2;

    public double r2, r3;
    private double cachedRotation;

    public SpotTriangle(Spot i, Spot j, Spot k, double epsilon) {
        this.Dij = Point2D.distance(i.getCentroidX(), i.getCentroidY(), j.getCentroidX(),
            j.getCentroidY());
        this.Dik = Point2D.distance(i.getCentroidX(), i.getCentroidY(), k.getCentroidX(),
            k.getCentroidY());
        this.Djk = Point2D.distance(j.getCentroidX(), j.getCentroidY(), k.getCentroidX(),
            k.getCentroidY());
        // We now know the lengths of all three sides, so
        // can figure out short, middle, long -- will get
        // six cases.
        if ((Dik >= Djk) && (Dik >= Dij)) {
            v2 = j;
            D13 = Dik;
            if (Djk >= Dij) {
                // ik = long, jk = middle, ij = short
                Dxl = i.getCentroidX() - k.getCentroidX();
                Dyl = i.getCentroidY() - k.getCentroidY();
                Dxs = j.getCentroidX() - i.getCentroidX();
                Dys = j.getCentroidY() - i.getCentroidY();
                D12 = Dij;
                D23 = Djk;
                v1 = i;
                v3 = k;
            } else {
                // ik = long, ij = middle, jk = short
                Dxl = -i.getCentroidX() + k.getCentroidX();
                Dyl = -i.getCentroidY() + k.getCentroidY();
                Dxs = -k.getCentroidX() + j.getCentroidX();
                Dys = -k.getCentroidY() + j.getCentroidY();
                D12 = Djk;
                D23 = Dij;
                v1 = k;
                v3 = i;
            }
        } else if ((Djk > Dik) && (Djk >= Dij)) {
            v2 = i;
            D13 = Djk;
            if (Dik >= Dij) {
                // jk = long, ik = middle, ij = short
                Dxl = -k.getCentroidX() + j.getCentroidX();
                Dyl = -k.getCentroidY() + j.getCentroidY();
                Dxs = -j.getCentroidX() + i.getCentroidX();
                Dys = -j.getCentroidY() + i.getCentroidY();
                D12 = Dij;
                D23 = Dik;
                v1 = j;
                v3 = k;
            } else {
                // jk = long, ij = middle, ik = short
                Dxl = k.getCentroidX() - j.getCentroidX();
                Dyl = k.getCentroidY() - j.getCentroidY();
                Dxs = i.getCentroidX() - k.getCentroidX();
                Dys = i.getCentroidY() - k.getCentroidY();
                D12 = Dik;
                D23 = Dij;
                v1 = k;
                v3 = j;
            }
        } else {
            v2 = k;
            D13 = Dij;
            if (Dik >= Djk) {
                // ij = long, ik = middle, jk = short
                Dxl = j.getCentroidX() - i.getCentroidX();
                Dyl = j.getCentroidY() - i.getCentroidY();
                Dxs = k.getCentroidX() - j.getCentroidX();
                Dys = k.getCentroidY() - j.getCentroidY();
                D12 = Djk;
                D23 = Dik;
                v1 = j;
                v3 = i;
            } else {
                // ij = long, jk = middle, ik = short
                Dxl = -j.getCentroidX() + i.getCentroidX();
                Dyl = -j.getCentroidY() + i.getCentroidY();
                Dxs = -i.getCentroidX() + k.getCentroidX();
                Dys = -i.getCentroidY() + k.getCentroidY();
                D12 = Dik;
                D23 = Djk;
                v1 = i;
                v3 = j;
            }
        }
        r3 = D13;
        r2 = D12;
        R = r3 / r2;
        C = -(Dxl * Dxs + Dyl * Dys) / (r2 * r3);
        S2 = 1.0 - (C * C);
        VarFact = 1.0 / (r3 * r3) - C / (r3 * r2) + 1.0 / (r2 * r2);
        tR2 = R * R * epsilon * epsilon * 2 * VarFact;
        tC2 = 2 * S2 * epsilon * epsilon * VarFact + 3 * C * C * Math.pow(epsilon,
            4) * VarFact * VarFact;
        logPerimeter = Math.log(D12 + D13 + D23);
        CrossProd = Dxs * Dyl - Dxl * Dys;
        if (CrossProd <= 0.0) {
            clockwise = false;
        } else {
            clockwise = true;
        }
        // Pre-compute rotation angle (centroid-relative atan2 of vertex 1)
        double cx = (v1.getCentroidX() + v2.getCentroidX() + v3.getCentroidX()) / 3;
        double cy = (v1.getCentroidY() + v2.getCentroidY() + v3.getCentroidY()) / 3;
        this.cachedRotation = Math.atan2(v1.getCentroidY() - cy, v1.getCentroidX() - cx);
    }

    public boolean containsSpot(Spot x) {
        if ((x.getCentroidX() == v1.getCentroidX()) && (x.getCentroidY() == v1.getCentroidY())) {
            return true;
        } else if ((x.getCentroidX() == v2.getCentroidX()) &&
            (x.getCentroidY() == v2.getCentroidY())) {
            return true;
        } else if ((x.getCentroidX() == v3.getCentroidX()) &&
            (x.getCentroidY() == v3.getCentroidY())) {
            return true;
        }
        return false;
    }

    public Spot getVertex(int x) {
        if (x == 1) {
            return v1;
        } else if (x == 2) {
            return v2;
        } else {
            return v3;
        }
    }

    public double getMyVertexOneRotationInRadians() {
        return cachedRotation;
    }

    public double getTriangleCentroidX() {
        double x1 = v1.getCentroidX();
        // double y1=v1.getCentroidY();
        double x2 = v2.getCentroidX();
        // double y2=v2.getCentroidY();
        double x3 = v3.getCentroidX();

        // double y3=v3.getCentroidY();
        // now calculate the centroid
        return ((x1 + x2 + x3) / 3);
    }

    public double getTriangleCentroidY() {
        // double x1=v1.getCentroidX();
        double y1 = v1.getCentroidY();
        // double x2=v2.getCentroidX();
        double y2 = v2.getCentroidY();
        // double x3=v3.getCentroidX();
        double y3 = v3.getCentroidY();

        // now calculate the centroid
        return ((y1 + y2 + y3) / 3);
    }
}
