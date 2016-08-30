package org.ecocean.datacollection;

import org.ecocean.CommonConfiguration;

public abstract class DataPoint implements java.io.Serializable {

  private Object value;
  private String id;
  private String name; //e.g. "length"

  private Integer number; // Serves as reference if parsed from the datapoint fields in commonConfiguration.properties

  public DataPoint() {
  }

  public abstract Object getValue();
  public abstract String getValueString();
  public abstract void setValueFromString(String str);
  public abstract String toString();
  public abstract String toLabeledString(); // this makes it easy to check what type of DataPoint a given datapoint is


  public String getName(){
    return name;
  }

  public void setName(String name){
    this.name = name;
  }

  public String getID(){
    return id;
  }
  protected void setID(String id) {
    this.id = id;
  }

  public Integer getNumber() {
    return number;
  }
  protected void setNumber(Integer n) {
    number = n;
  }

  public boolean isCategorical(String context) {
    String lookupName = "datapoint"+number+"Values";
    String val = CommonConfiguration.getProperty(lookupName, context);
    return (number != null && val != null);
  }

  public String[] getCategoriesAsStrings(String context) {
    String allVals = CommonConfiguration.getProperty("datapoint"+number+"Values", context);
    if (allVals==null || allVals.equals("")) return new String[0];
    return allVals.split(",\\s*");
  }
}
