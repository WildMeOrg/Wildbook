package org.ecocean.tag;

public class AcousticTag extends AbstractTag {
  static final long serialVersionUID = -3502387833027576733L;

  private String serialNumber;
  
  private String idNumber;
  
  public AcousticTag() {
    
  }
  
  public AcousticTag(String serialNumber, String idNumber) {
    this.serialNumber = serialNumber;
    this.idNumber = idNumber;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  public String getIdNumber() {
    return idNumber;
  }

  public void setIdNumber(String idNumber) {
    this.idNumber = idNumber;
  }

}
