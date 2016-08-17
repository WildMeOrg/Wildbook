package org.ecocean.datacollection;

public abstract class DataAtom implements java.io.Serializable {

  private Object value;
  private String id;
  private String name; //e.g. "length"

  public DataAtom() {
  }

  public abstract Object getValue();

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
