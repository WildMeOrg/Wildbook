package org.ecocean.tag;

import java.io.Serializable;
import java.util.ArrayList;

import org.ecocean.Observation;

public class AbstractTag implements Serializable {
  static final long serialVersionUID = 8844223450447994780L;

  private String id;
  private ArrayList<Observation> observations;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
  
  public ArrayList<Observation> getAllObservations() {
    return observations;
  }

  public void setAllObservations(ArrayList<Observation> obs) {
    this.observations = obs;
  }
  
  public void addObservation(Observation ob) {
    this.observations.add(ob);
  }
  
  public void removeObservation(Observation ob) {
    this.observations.remove(ob);
  }

  
  protected AbstractTag() {
    
  }
  
}
