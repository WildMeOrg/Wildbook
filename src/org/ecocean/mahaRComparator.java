package org.ecocean;

import java.util.Comparator;


class mahaRComparator implements Comparator{
	
	mahaRComparator() {}
	
	public int compare(Object a, Object b) {
        mahaSpotTriangle a1=(mahaSpotTriangle)a;
        mahaSpotTriangle b1=(mahaSpotTriangle)b;
        if (a1.R<b1.R) {return -1;}
        else if (a1.R==b1.R) {return 0;}
        else {return 1;}
        
    }
	

    
        
}