package org.ecocean.datacollection;


import org.ecocean.Util;
import org.ecocean.ClassEditTemplate;

import java.io.IOException;

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

  public Amount(String name, Double value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    this.value = value;
    this.units = units;
  }


  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  public String getValueString() {
    if (value==null) return "";
    return value.toString();
  }

  public Double[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    Double[] res = new Double[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = Double.valueOf(strings[i]);
    }
    return res;
  }

  public String getUnits() {
    return units;
  }

  public String toString() {
    return ((this.getName()+": "+value+units).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("amount-"+(this.getName()+": "+value+units).replaceAll("null",""));
  }
}
