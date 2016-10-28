package org.ecocean.tag;

public class MetalTag extends AbstractTag {
  static final long serialVersionUID = 9015223799239301859L;
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
