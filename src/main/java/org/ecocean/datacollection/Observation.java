package org.ecocean.datacollection;

import org.ecocean.Util;


public class Observation extends DataPoint {

  private String value;

  public Observation() {
  }

  public Observation(String value) {
    super.setID(Util.generateUUID());
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String toString() {
    return "\""+value+"\"";
  }

}
