package org.ecocean;


import java.util.List;
import java.util.ArrayList;

import java.io.IOException;

import org.ecocean.datacollection.*;
import org.ecocean.Util;

public class Nest implements java.io.Serializable {

  private String id;
  private String name;
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

  public String getID() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public void addConfigDataSheet(String context, String subname) throws IOException {
    //DataSheet sheetie = DataSheet.fromCommonConfig("nest", context);
    DataSheet sheetie = DataSheet.fromCommonConfig(subname, context);
    sheetie.setName(subname);
    System.out.println();
    System.out.println("Just made a named config data sheet, "+sheetie.getName());
    this.record(sheetie);
  }



  public List<DataSheet> getDataSheets() {
    return dataSheets;
  }

  public DataSheet getDataSheet(int i) {
    return dataSheets.get(i);
  }

  public boolean remove(DataSheet datasheet) {
    return this.dataSheets.remove(datasheet);
  }

  public void remove(int i) {
    this.dataSheets.remove(i);
  }

  public int countSheets() {
    return this.dataSheets.size();
  }





  public void setLocationID(String locationID) {
    this.locationID = locationID;
  }
  public String getLocationID() {
    return locationID;
  }

  public void setLocationNote(String locationNote) {
    this.locationNote = locationNote;
  }
  public String getLocationNote() {
    return locationNote;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }
  public Double getLatitude() {
    return latitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }
  public Double getLongitude() {
    return longitude;
  }


  // following functions are largely on DataSheets,
  // but live in this class because they only make
  // sense in the nest context

  // eggs: on some datasheets, researchers will want to record info
  // about N eggs. This is stored by reserving the end of the datapoint
  // array for only egg measurements
  public int getEggCount(int sheetNo) {
    DataSheet ds = getDataSheet(sheetNo);
    DataPoint lastDP = ds.get(ds.size()-1);
    String lastName = lastDP.getNumberedName();
    System.out.println("   lastName = "+lastName);
    System.out.println("   indexOfEgg = " + lastName.toLowerCase().indexOf("egg"));
    System.out.println("   indexOfHam = " + lastName.toLowerCase().indexOf("ham"));
    boolean lastDPAnEgg = (lastName.toLowerCase().indexOf("egg") > -1);

    String intFromLastName = lastName.replaceAll("[^-?0-9]+", "");
    System.out.println("   intFromLastName = "+intFromLastName);

    if (!lastDPAnEgg) return 0;
    int ans = Integer.parseInt(intFromLastName);
    int otherAttempt = getDataSheet(sheetNo).getLastNumber("egg");
    System.out.println("first answer = "+ans+" and second answer = "+otherAttempt);

    return (Integer.parseInt(intFromLastName) + 1);
  }

  public void addNewEgg(int sheetNo) {
    DataSheet ds = getDataSheet(sheetNo);
    int eggNo = getEggCount(sheetNo);
    DataPoint eggDiameter = new Amount("egg "+eggNo+" diam.", (Double) null, "cm");
    DataPoint eggWeight = new Amount("egg "+eggNo+" weight", (Double) null, "g");
    ds.add(eggDiameter);
    ds.add(eggWeight);
  }


}
