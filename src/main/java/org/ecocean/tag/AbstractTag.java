package org.ecocean.tag;

public class AbstractTag {

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  private String id;
  
  protected AbstractTag() {
    
  }
  
}
