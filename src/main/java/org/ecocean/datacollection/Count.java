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

  public Count(String name, Integer value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
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
    return ((this.getName()+": "+value+units).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("count-"+(this.getName()+": "+value+units).replaceAll("null",""));
  }


}
