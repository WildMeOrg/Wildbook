package org.ecocean.tag;

public class DigitalArchiveTag extends AbstractTag {

  private static final long serialVersionUID = 1L;
  
  private String dTagID;
  private String serialNumber;
  
  public DigitalArchiveTag() {
    
  }
  
  public DigitalArchiveTag(String dt, String serialNumber, String argosPttNumber) {
    this.dTagID = dt;
    this.serialNumber = serialNumber;
  }

  public String getDTagID() {
    return dTagID;
  }

  public void setDTagID(String dt) {
    this.dTagID = dt;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }
  
}