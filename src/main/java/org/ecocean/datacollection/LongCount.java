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
    this.value = value;
    this.units = units;
  }

  public LongCount(String name, Long value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    this.value = value;
    this.units = units;
  }


  public Long getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = (Long) value;
  }

  public String getValueString() {
    return value.toString();
  }

  public Long[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    Long[] res = new Long[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = Long.valueOf(strings[i]);
    }
    return res;
  }


  public String getUnits() {
    return (units);
  }

  public String toString() {
    return ((this.getName()+": "+value+units).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("longcount-"+(this.getName()+": "+value+units).replaceAll("null",""));
  }


}
