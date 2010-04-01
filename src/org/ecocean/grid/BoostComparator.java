package org.ecocean.grid;
import java.util.ArrayList;
import java.util.Comparator;

import org.ecocean.Shepherd;

import java.util.Properties;


public class BoostComparator implements Comparator{
    
	String encNumber="";
	Shepherd myShepherd;
	Properties props;
	
	public BoostComparator(String encNumber, Shepherd myShepherd, Properties props){
		this.encNumber=encNumber;
		this.myShepherd=myShepherd;
		this.props=props;
	}
	
    public int compare(Object a, Object b) {
        MatchObject a1=(MatchObject)a;
        MatchObject b1=(MatchObject)b;
        


        
        double a1_adjustedValue=(new Double((String)props.get(a1.getEncounterNumber()))).doubleValue();
        double b1_adjustedValue=(new Double((String)props.get(b1.getEncounterNumber()))).doubleValue();
        
        if (a1_adjustedValue>b1_adjustedValue) {return -1;}
        else if (a1_adjustedValue==b1_adjustedValue) {return 0;}
        else {return 1;}
        
        
    }
    
    
    
}