package org.ecocean;

import java.util.Comparator;
import java.util.Map;

public class IndividualOccurrenceNumComparator implements Comparator {

  public IndividualOccurrenceNumComparator() {}
  
    public int compare( Object o1 , Object o2 ){  
        Map.Entry e1 = (Map.Entry)o1 ;  
        Map.Entry e2 = (Map.Entry)o2 ;  
        Integer first = (Integer)e1.getValue();  
        Integer second = (Integer)e2.getValue();  
        return first.compareTo( second );  
    }  
}
  

