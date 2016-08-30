package org.ecocean.datacollection;


import org.ecocean.Util;
import org.ecocean.ClassEditTemplate;

import java.io.IOException;

public class Amount extends DataPoint {

  private Double value;

  public Amount() {
  }

  public Amount(Double value, String units) {
    super.setID(Util.generateUUID());
    super.setUnits(units);
    this.value = value;
  }

  public Amount(String name, Double value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    super.setUnits(units);
    this.value = value;
  }


  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  public void setValueFromString(String str) {
    setValue(Double.valueOf(str));
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

  public String toString() {
    return ((this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("amount-"+(this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }
}
