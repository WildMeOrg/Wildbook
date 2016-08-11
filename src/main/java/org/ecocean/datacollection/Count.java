package org.ecocean.datacollection;

public class Count extends DataPoint {

  private Integer value;

  private String units;

  public Count() {
  }

  public Count(String correspondingEncounterNumber, String type, Integer value, String units, String samplingProtocol) {
    super(correspondingEncounterNumber, type);
    super.setSamplingProtocol(samplingProtocol);
    this.value = value;
    this.units = units;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Integer value) {
    if(value==null){this.value=null;}
    else{this.value = value;}
  }

  public String getUnits() {
    return units;
  }

}
