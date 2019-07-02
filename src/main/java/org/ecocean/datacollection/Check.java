package org.ecocean.datacollection;

import org.ecocean.Util;

public class Check extends DataPoint {

  private Boolean value;

  private String units;

  public Check() {
  }

  public Check(Boolean value, String units) {
    super.setID(Util.generateUUID());
    super.setUnits(units);
    this.value = value;
  }

  public Check(String name, Boolean value, String units) {
    super.setID(Util.generateUUID());
    super.setName(name);
    super.setUnits(units);
    this.value = value;
  }

  public Boolean getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = (Boolean) value;
  }

  public void setValueFromString(String str) {
    if (str==null) setValue(null);
    else setValue(Boolean.valueOf(str));
  }

  public String getValueString() {
    if (value==null) return "";
    return value.toString();
  }

  public Boolean[] getCategories(String context) {
    String[] strings = super.getCategoriesAsStrings(context);
    Boolean[] res = new Boolean[strings.length];
    for (int i=0; i<strings.length; i++) {
      res[i] = Boolean.valueOf(strings[i]);
    }
    return res;
  }

  public String toString() {
    return ((this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }

  public String toLabeledString() {
    return ("Check-"+(this.getName()+": "+value+super.getUnits()).replaceAll("null",""));
  }


}
