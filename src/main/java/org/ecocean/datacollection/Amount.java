package org.ecocean.datacollection;

import org.ecocean.Util;

public class Amount extends DataPoint {

  private Double value;

  private String units;

  public Amount() {
  }

  public Amount(Double value, String units) {
    super.setID(Util.generateUUID());
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
