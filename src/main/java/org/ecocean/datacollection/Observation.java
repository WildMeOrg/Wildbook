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

  public Observation(String name, String value) {
    super.setID(Util.generateUUID());
    super.setName(name);
    this.value = value;
  }


  public String getValue() {
    return value;
  }

  public String getValueString() {
    return value;
  }

  // returns an array of possible values, or an empty array if any value is allowed
  public String[] getCategories(String context) {
    return super.getCategoriesAsStrings(context);
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String toString() {
    return ((this.getName()+": \""+value+"\"").replace("null",""));
  }
  public String toLabeledString() {
    return ("observation-"+(this.getName()+": \""+value+"\"").replace("null",""));
  }


}
