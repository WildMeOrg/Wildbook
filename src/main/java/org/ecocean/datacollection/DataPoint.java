package org.ecocean.datacollection;

import org.ecocean.CommonConfiguration;

public abstract class DataPoint implements java.io.Serializable {

  private Object value;
  private String id;
  private String name; //e.g. "length"
  private String units; // e.g. "cm"

  private Integer configNo; // Serves as reference if parsed from the datapoint fields in commonConfiguration.properties
  private Integer number; // not-null only for sequential datapoints (e.g. egg weight 1, egg weight 2, egg weight 3)

  public DataPoint() {
  }

  public abstract Object getValue();
  public abstract String getValueString();
  public abstract void setValueFromString(String str);
  public abstract String toString();
  public abstract String toLabeledString(); // this makes it easy to check what type of DataPoint a given datapoint is

  public DataPoint blankCopy() throws java.lang.CloneNotSupportedException {
    DataPoint dp = (DataPoint) this.clone();
    dp.setValueFromString(null);
    return dp;
  }

  public String getName(){
    return name;
  }

  public String getNumberedName(){
    if (number!=null) return (name + number);
    return name;
  }

  public boolean isSequential() {
    return (number!= null);
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
  public void setNumber(Integer n) {
    number = n;
  }

  public String getUnits() {
    return units;
  }

  public void setUnits(String units) {
    this.units = units;
  }

  public Integer getConfigNo() {
    return configNo;
  }

  public void setConfigNo(Integer configNo) {
    this.configNo = configNo;
  }

  public boolean isCategorical(String context) {
    if (configNo==null) return false;
    String lookupName = "datapoint"+configNo+"Values";
    String val = CommonConfiguration.getProperty(lookupName, context);
    System.out.println("isCategorical "+lookupName+" = "+val);
    return (number != null && val != null);
  }

  public boolean isSequential(String context) {
    if (configNo==null) return false;
    String lookupName = "datapoint"+configNo+"Sequential";
    String val = CommonConfiguration.getProperty(lookupName, context);
    System.out.println("isSequential "+lookupName+" = "+val);
    return (val != null);
  }


  public String[] getCategoriesAsStrings(String context) {
    String allVals = CommonConfiguration.getProperty("datapoint"+number+"Values", context);
    if (allVals==null || allVals.equals("")) return new String[0];
    return allVals.split(",\\s*");
  }
}
