package org.ecocean.datacollection;

public abstract class DataPoint implements java.io.Serializable {

  private Object value;
  private String id;
  private String name; //e.g. "length"

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

  // setter is not abstract so that its argument is typed (not Object)


}
