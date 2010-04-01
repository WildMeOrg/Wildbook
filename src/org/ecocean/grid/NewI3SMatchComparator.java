package org.ecocean.grid;

import java.util.Comparator;


public class NewI3SMatchComparator implements Comparator{

	    public int compare(Object a, Object b) {
	        MatchObject a1=(MatchObject)a;
	        MatchObject b1=(MatchObject)b;
	        
	        double a1_adjustedValue=a1.getI3SMatchValue();
	        double b1_adjustedValue=b1.getI3SMatchValue();
	        
	        if (a1_adjustedValue<b1_adjustedValue) {return -1;}
	        else if (a1_adjustedValue==b1_adjustedValue) {return 0;}
	        else {return 1;}
	        
	        
	    }
	    
	    
	    
	}

