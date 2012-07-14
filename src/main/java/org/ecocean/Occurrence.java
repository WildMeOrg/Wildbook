package org.ecocean;

import java.util.ArrayList;

/**
 * Whereas an Encounter is meant to represent one MarkedIndividual at one point in time and space, an Occurrence
 * is meant to represent several Encounters that occur in a natural grouping (e.g., a pod of dolphins). Ultimately
 * the goal of the Encounter class is to represent associations among MarkedIndividuals that are commonly 
 * sighted together.
 * 
 * @author Jason Holmberg
 *
 */
public class Occurrence implements java.io.Serializable{
  
  
  
  /**
   * 
   */
  private static final long serialVersionUID = -7545783883959073726L;
  private ArrayList<Encounter> encounters;
  private String occurrenceID;
  
  //empty constructor used by the JDO enhancer
  public Occurrence(){}
  
  /**
   * Class constructor.
   * 
   * 
   * @param occurrenceID A unique identifier for this occurrence that will become its primary key in the database.
   * @param enc The first encounter to add to this occurrence. 
   */
  public Occurrence(String occurrenceID, Encounter enc){
    this.occurrenceID=occurrenceID;
    encounters=new ArrayList<Encounter>();
    encounters.add(enc);
  }
  
  public void addEncounter(Encounter enc){
    if(encounters==null){encounters=new ArrayList<Encounter>();}
    encounters.add(enc);
  }
  
  public ArrayList<Encounter> getEncounters(){
    return encounters;
  }
  
  public void removeEncounter(Encounter enc){
    if(encounters!=null){
      encounters.remove(enc);
    }
  }
  
  public int getNumberEncounters(){
    if(encounters==null) {return 0;}
    else{return encounters.size();}
  }
  
  public void setEncounters(ArrayList<Encounter> encounters){this.encounters=encounters;}
  
  public ArrayList<String> getMarkedIndividualNamesForThisOccurrence(){
    int size=getNumberEncounters();
    ArrayList<String> names=new ArrayList<String>();
    for(int i=0;i<size;i++){
      Encounter enc=encounters.get(i);
      if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("Unassigned"))){names.add(enc.getIndividualID());}
    }
    return names;
  }
  
  public String getOccurrenceID(){return occurrenceID;}
  


}
