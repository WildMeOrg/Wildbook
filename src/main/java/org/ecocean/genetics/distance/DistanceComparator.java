package org.ecocean.genetics.distance;

import java.util.*;

public class DistanceComparator implements Comparator {
    public DistanceComparator() {}

    public int compare(Object a, Object b) {
        Map.Entry a_enc = (Map.Entry)a;
        Map.Entry b_enc = (Map.Entry)b;

        double ad = (new Double((String)a_enc.getValue())).doubleValue();
        double bd = (new Double((String)b_enc.getValue())).doubleValue();
        if (ad < bd) {
            return -1;
        } else if (ad > bd) {
            return 1;
        }
        return 0;
    }
}
