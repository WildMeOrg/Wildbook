package org.ecocean;

public class Measurement extends DataCollectionEvent {

  private static final long serialVersionUID = -7934850478287322048L;
  
  private Double value;
  
  private String units;
  
  public Measurement() {
  }
  
  public Measurement(String correspondingEncounterNumber, String type, Double value, String units, String samplingProtocol) {
    super(correspondingEncounterNumber, type);
    super.setSamplingProtocol(samplingProtocol);
    this.value = value;
    this.units = units;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    if(value==null){this.value=null;}
    else{this.value = value;}
  }
  
  public String getUnits() {
    return units;
  }

}
