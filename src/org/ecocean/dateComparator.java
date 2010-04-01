package org.ecocean;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Calendar;

public class dateComparator implements Comparator{
    
    public int compare(Object a, Object b) {
        GregorianCalendar a1=(GregorianCalendar)a;
        GregorianCalendar b1=(GregorianCalendar)b;
        
        
        if (a1.get(Calendar.YEAR)>b1.get(Calendar.YEAR)) {return 1;}
        else if (a1.get(Calendar.YEAR)==b1.get(Calendar.YEAR)) {
        	if (a1.get(Calendar.DAY_OF_YEAR)>b1.get(Calendar.DAY_OF_YEAR)) {return 1;}
        	else if(a1.get(Calendar.DAY_OF_YEAR)==b1.get(Calendar.DAY_OF_YEAR)) {
        		return 0;
        	}
        	else {return -1;}
        }
        else {return -1;}
    }
    
    
    
}