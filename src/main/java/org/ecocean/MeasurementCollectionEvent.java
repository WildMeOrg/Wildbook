package org.ecocean;

public class MeasurementCollectionEvent extends DataCollectionEvent {

  private static final long serialVersionUID = -7934850478287322048L;
  
  private Double value;
  
  private String units;
  
  public MeasurementCollectionEvent() {
  }
  
  public MeasurementCollectionEvent(String correspondingEncounterNumber, String type, Double value, String units, String samplingProtocol) {
    super(correspondingEncounterNumber, type);
    super.setSamplingProtocol(samplingProtocol);
    this.value = value;
    this.units = units;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }
  
  public String getUnits() {
    return units;
  }

}
