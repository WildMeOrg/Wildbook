package org.ecocean.datacollection;

import org.ecocean.Util;

public class Count extends DataPoint {

  private Integer value;

  private String units;

  public Count() {
  }

  public Count(Integer value, String units) {
    super.setID(Util.generateUUID());
    this.value = value;
    this.units = units;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = (Integer) value;
  }

  public String getUnits() {
    return units;
  }

  public String toString() {
    return ((this.getName()+": "+value.toString()+units).replaceAll("null",""));
  }

}
