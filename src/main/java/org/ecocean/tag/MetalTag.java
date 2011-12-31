package org.ecocean.tag;

public class MetalTag extends AbstractTag {
  private String tagNumber;
  private String location;
  
  public MetalTag(String tagNumber, String location) {
    this.tagNumber = tagNumber;
    this.location = location;
  }

  public MetalTag() {
    
  }
  
  public String getTagNumber() {
    return tagNumber;
  }

  public void setTagNumber(String tagNumber) {
    this.tagNumber = tagNumber;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

}
