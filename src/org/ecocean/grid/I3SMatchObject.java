package org.ecocean.grid;

import java.util.TreeMap;
//import com.reijns.I3S.*;

/**
 *matchObject is a temporary object used by the <code>shepherd</code> class to store unordered match values
 *@author jholmber
 */
public class I3SMatchObject implements java.io.Serializable{
	static final long serialVersionUID = 9122107217335010239L;
	public String individualName="N/A", date;
	public double matchValue, size;
	public String encounterNumber="N/A";
	private TreeMap map;
	public String newSex="Unknown", catalogSex="Unknown";
	
	public I3SMatchObject(){}
	
	public I3SMatchObject(String individualName, double score, String encounterNumber, String catalogSex, String date, double size, TreeMap map, double mahalSum) {
		this.matchValue=score;	
		this.individualName=individualName;
		this.map=map;
		this.encounterNumber=encounterNumber;
		this.catalogSex=catalogSex;
		this.date=date;
		this.size=size;
		//this.mahalSum=mahalSum;
	}
	
	public TreeMap getMap() {
		return map;
	}
	public double getI3SMatchValue(){return matchValue;}
	
/**	DONT NEED THESE - I think...
	public double getRightmostSharkSpot() {
		double greatest=0;
		for (int i=0;i<scores.length;i++) {
			
			if (scores[i].oldX>greatest) {greatest=scores[i].oldX;}
			
			}
		return greatest;
	}
		
	public double getHighestSharkSpot() {
		double greatest=0;
		for (int i=0;i<scores.length;i++) {
			
			if (scores[i].oldY>greatest) {greatest=scores[i].oldY;}
		}
		return greatest;
	}
		
	public double getRightmostEncounterSpot() {
		double greatest=0;
		for (int i=0;i<scores.length;i++) {
			if (scores[i].newX>greatest) {greatest=scores[i].newX;}
			}
		return greatest;
	}
		
	public double getHighestEncounterSpot() {
		double greatest=0;
		for (int i=0;i<scores.length;i++) {
			if (scores[i].newY>greatest) {greatest=scores[i].newY;}
		}
		return greatest;
	}
	************/
	
	public String getEvaluation() {
		double product=matchValue;
		if(product<1) {
			return "High";
		}
		else if(product<2) {
			return "Moderate";
		}
		else {
			return "Low";
		}
	}
	
	public String getIndividualName(){
		return individualName;
	}
}