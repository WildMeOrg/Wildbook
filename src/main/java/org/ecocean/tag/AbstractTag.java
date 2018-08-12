package org.ecocean.tag;

import java.io.Serializable;

public class AbstractTag implements Serializable {
  static final long serialVersionUID = 8844223450447994780L;

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
