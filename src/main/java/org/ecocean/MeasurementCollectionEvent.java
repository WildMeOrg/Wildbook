package org.ecocean;

public class MeasurementCollectionEvent extends DataCollectionEvent {

  private static final long serialVersionUID = -7934850478287322048L;
  
  private Double value;
  
  public MeasurementCollectionEvent() {
  }
  
  public MeasurementCollectionEvent(String correspondingEncounterNumber, String type, Double value) {
    super(correspondingEncounterNumber, type);
    this.value = value;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

}
