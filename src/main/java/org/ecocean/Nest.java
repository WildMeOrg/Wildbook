package org.ecocean;


import java.util.List;
import java.util.ArrayList;
import org.ecocean.datacollection.*;

public class Nest implements java.io.Serializable {

  private String id;
  private List<DataSheet> dataSheets;

  private String locationID;
  private String locationNote;
  private Double latitude;
  private Double longitude;

  /**
   * empty constructor required by the JDO Enhancer
   */
  public Nest() {
    this.dataSheets = new ArrayList<DataSheet>();
  }

  public Nest(String id) {
    this.dataSheets = new ArrayList<DataSheet>();
    this.id = id;
  }

  public void record(DataSheet datasheet) {
    this.dataSheets.add(datasheet);
  }

  public List<DataSheet> getDataSheets() {
    return dataSheets;
  }

  public void setLocationID(String locID) {
    this.locationID = locID;
  }
  public String getLocationID() {
    return locationID;
  }

  public void setLocationNote(String locNote) {
    this.locationNote = locNote;
  }
  public String getLocationNote() {
    return locationNote;
  }

  public void setLatitude(Double lat) {
    this.latitude = lat;
  }
  public Double getLatitude() {
    return latitude;
  }

  public void setLongitude(Double lon) {
    this.longitude = lon;
  }
  public Double getLongitude() {
    return longitude;
  }

}
