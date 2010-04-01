package org.ecocean;

import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Calendar;

public class EncounterDateComparator implements Comparator{
    
    EncounterDateComparator() {}
    
    public int compare(Object a, Object b) {
    	Encounter a_enc=(Encounter)a;
    	Encounter b_enc=(Encounter)b;
        GregorianCalendar a1=new GregorianCalendar(a_enc.getYear(),a_enc.getMonth(),a_enc.getDay());
        GregorianCalendar b1=new GregorianCalendar(b_enc.getYear(),b_enc.getMonth(),b_enc.getDay());
        
        
        if (a1.get(Calendar.YEAR)>b1.get(Calendar.YEAR)) {return -1;}
        if (a1.get(Calendar.YEAR)==b1.get(Calendar.YEAR)) {
        	if (a1.get(Calendar.MONTH)>b1.get(Calendar.MONTH)) {return -1;}
        	if(a1.get(Calendar.MONTH)==b1.get(Calendar.MONTH)) {
        		if (a1.get(Calendar.DAY_OF_MONTH)>b1.get(Calendar.DAY_OF_MONTH)) {return -1;}
        		if (a1.get(Calendar.DAY_OF_MONTH)<b1.get(Calendar.DAY_OF_MONTH)) {return 1;}
        		return 0;
        	}
        	return 1;
        }
        return 1;
    }
    
    
    
}