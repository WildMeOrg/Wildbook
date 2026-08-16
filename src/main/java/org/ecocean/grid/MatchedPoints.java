package org.ecocean.grid;

import org.ecocean.Spot;

import java.util.ArrayList;
import java.util.HashMap;

public class MatchedPoints extends ArrayList {
    private HashMap<Long, Integer> lookupMap = new HashMap<>();

    public MatchedPoints() {
        super();
    }

    private static long pairKey(double newX, double newY, double oldX, double oldY) {
        long h1 = Double.doubleToRawLongBits(newX);
        long h2 = Double.doubleToRawLongBits(newY);
        long h3 = Double.doubleToRawLongBits(oldX);
        long h4 = Double.doubleToRawLongBits(oldY);
        // Mix the four long hashes into one
        long h = h1 * 31 + h2;
        h = h * 31 + h3;
        h = h * 31 + h4;
        return h;
    }

    @Override
    public boolean add(Object o) {
        int idx = size();
        boolean result = super.add(o);
        if (result && o instanceof VertexPointMatch) {
            VertexPointMatch vpm = (VertexPointMatch) o;
            lookupMap.put(pairKey(vpm.newX, vpm.newY, vpm.oldX, vpm.oldY), idx);
        }
        return result;
    }

    public int hasMatchedPair(Spot A, Spot B) {
        Long key = pairKey(A.getCentroidX(), A.getCentroidY(), B.getCentroidX(), B.getCentroidY());
        Integer idx = lookupMap.get(key);
        return idx != null ? idx : -1;
    }
}
