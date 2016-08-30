package org.ecocean.datacollection;

import org.ecocean.Util;

// useful for cases such as duration (# milliseconds)
public class LongCount extends DataPoint {

  private Long value;

  private String units;

  public LongCount() {
  }

  public LongCount(Long value, String units) {
    super.setID(Util.generateUUID());
    super.setUnits(units);
    this.value = value;
    this.units = units;
  }

  public LongCount(String name, Long value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    super.setUnits(units);
    this.value = value;
  }


  public Long getValue() {
    return value;
  }

  public String getValueString() {
    if (value==null) return "";
    return value.toString();
  }

  public void setValue(Object value) {
    this.value = (Long) value;
  }

  public void setValueFromString(String str) {
    setValue(Long.valueOf(str));
  }

  public Long[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    Long[] res = new Long[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = Long.valueOf(strings[i]);
    }
    return res;
  }

  public String toString() {
    return ((this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("longcount-"+(this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }


}
