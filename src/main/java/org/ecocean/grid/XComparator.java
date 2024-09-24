package org.ecocean.grid;

import java.util.Comparator;
import org.ecocean.SuperSpot;

public class XComparator implements Comparator {
    public XComparator() {}

    public int compare(Object a, Object b) {
        SuperSpot a1 = (SuperSpot)a;
        SuperSpot b1 = (SuperSpot)b;
        double a1_adjustedValue = 0;
        double b1_adjustedValue = 0;

        a1_adjustedValue = a1.getCentroidX();
        b1_adjustedValue = b1.getCentroidX();
        if (a1_adjustedValue < b1_adjustedValue) {
            // System.out.println(a1_adjustedValue+" > "+b1_adjustedValue);
            return -1;
        } else if (a1_adjustedValue == b1_adjustedValue) {
            // System.out.println(a1_adjustedValue+" = "+b1_adjustedValue);
            return 0;
        } else {
            // System.out.println(a1_adjustedValue+" < "+b1_adjustedValue);
            return 1;
        }
    }
}
