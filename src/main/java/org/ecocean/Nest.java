package org.ecocean;


import java.util.List;
import java.util.ArrayList;

import java.io.IOException;

import org.ecocean.datacollection.*;
import org.ecocean.Util;

public class Nest implements java.io.Serializable {

  private String id;
  private List<DataSheet> dataSheets = new ArrayList<DataSheet>();

  private String locationID;
  private String locationNote;
  private Double latitude;
  private Double longitude;

  /**
   * empty constructor required by the JDO Enhancer
   */
  public Nest() {
  }

  public Nest(String id) {
    this.id = id;
  }

  public Nest(DataSheet sheet) {
    this.id = Util.generateUUID();
    this.record(sheet);
  }

  // the goal here is to offer a quick hook to a "null nest",
  // that is a nest that has a datasheet with fields but no values.
  public static Nest nestWithConfigDataSheet(String context) throws IOException {
    return new Nest(DataSheet.fromCommonConfig("nest", context));
  }

  public void record(DataSheet datasheet) {
    this.dataSheets.add(datasheet);
  }

  public void addConfigDataSheet(String context) throws IOException {
    this.record(DataSheet.fromCommonConfig("nest", context));
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
