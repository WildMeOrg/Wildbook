package org.ecocean;

import org.joda.time.DateTime;

public class StudySite implements java.io.Serializable {

  private String id;
  private String name;
  private String typeOfSite = "camera trap"; // hard-coded default

  private String locationID;

  private Double latitude;
  private Double longitude;

  private String comments;

  private DateTime date;
  private DateTime dateEnd;

  /**
   * empty constructor required by the JDO Enhancer
   */
  public StudySite() {
  }

  public StudySite(String id) {
    this.id = id;
  }

  public StudySite(String id, String typeOfSite, Double lat, Double lon) {
    this.id = id;
    this.typeOfSite = typeOfSite;
    this.latitude = lat;
    this.longitude = lon;
  }

  public void setID(String id) {
    this.id = id;
  }

  public String getID() {
    return this.id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  public void setTypeOfSite(String typeOfSite) {
    this.typeOfSite = typeOfSite;
  }

  public String getTypeOfSite() {
    return this.typeOfSite;
  }

  public void setLocationID(String locationID) {
    this.locationID = locationID;
  }

  public String getLocationID() {
    return this.locationID;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLatitude() {
    return this.latitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public Double getLongitude() {
    return this.longitude;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public String getComments() {
    return this.comments;
  }

  public void setDate(DateTime date) {
    this.date = date;
  }

  public void setDate(Long ldate) {
    this.date = new DateTime(ldate);
  }

  public DateTime getDate() {
    return this.date;
  }

  public void setDateEnd(DateTime dateEnd) {
    this.dateEnd = dateEnd;
  }

  public void setDateEnd(Long dateEnd) {
    this.dateEnd = new DateTime(dateEnd);
  }

  public DateTime getDateEnd() {
    return this.dateEnd;
  }




}
