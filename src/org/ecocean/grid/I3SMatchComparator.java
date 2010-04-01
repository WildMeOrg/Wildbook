package org.ecocean.grid;

import java.util.Comparator;


public class I3SMatchComparator implements Comparator{
    
    public int compare(Object a, Object b) {
        I3SMatchObject a1=(I3SMatchObject)a;
        I3SMatchObject b1=(I3SMatchObject)b;
        
        double a1_adjustedValue=a1.matchValue;
        double b1_adjustedValue=b1.matchValue;
        
        if (a1_adjustedValue<b1_adjustedValue) {return -1;}
        else if (a1_adjustedValue==b1_adjustedValue) {return 0;}
        else {return 1;}
        
        
    }
    
    
    
}