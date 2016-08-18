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
    return (number != null || CommonConfiguration.getProperty(name + number +"Values", context) != null);
  }

  public String[] getCategoriesAsStrings(String context) {
    String allVals = CommonConfiguration.getProperty(name + number +"Values", context);
    if (allVals==null || allVals.equals("")) return new String[0];
    return allVals.split(",\\s*");
  }


  // setter is not abstract so that its argument is typed (not Object)


}
