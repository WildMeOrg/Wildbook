package org.ecocean.datacollection;

public class MeasurementEvent extends DataCollectionEvent {

  private static final long serialVersionUID = -7934850478287322048L;

  private Double value;
  private String units;

  public MeasurementEvent() {
  }

  public MeasurementEvent(String correspondingEncounterNumber, String type, Double value, String units, String samplingProtocol) {
    super(correspondingEncounterNumber, type);
    super.setSamplingProtocol(samplingProtocol);
    this.value = value;
    this.units = units;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = (Double) value;
  }

  public String getUnits() {
    return units;
  }

}
