package org.ecocean.datacollection;

import org.ecocean.Util;

public class Count extends DataPoint {

  private Integer value;

  private String units;

  public Count() {
  }

  public Count(Integer value, String units) {
    super.setID(Util.generateUUID());
    super.setUnits(units);
    this.value = value;
  }

  public Count(String name, Integer value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    super.setUnits(units);
    this.value = value;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = (Integer) value;
  }

  public void setValueFromString(String str) {
    setValue(Integer.valueOf(str));
  }

  public String getValueString() {
    if (value==null) return "";
    return value.toString();
  }

  public Integer[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    Integer[] res = new Integer[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = Integer.valueOf(strings[i]);
    }
    return res;
  }

  public String toString() {
    return ((this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("count-"+(this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }


}
