package org.ecocean.tag;

public class SatelliteTag extends AbstractTag {
  static final long serialVersionUID = 1623817087546820787L;

  private String name;
  private String serialNumber;
  private String argosPttNumber;
  
  public SatelliteTag() {
    
  }
  
  public SatelliteTag(String name, String serialNumber, String argosPttNumber) {
    this.name = name;
    this.serialNumber = serialNumber;
    this.argosPttNumber = argosPttNumber;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  public String getArgosPttNumber() {
    return argosPttNumber;
  }

  public void setArgosPttNumber(String argosPttNumber) {
    this.argosPttNumber = argosPttNumber;
  }
  
}
