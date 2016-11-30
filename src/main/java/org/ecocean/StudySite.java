package org.ecocean;

import org.joda.time.DateTime;

public class StudySite implements java.io.Serializable {

  private String id;
  private String typeOfSite; // e.g. "camera trap"

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

  public void setTypeOfSite(String typeOfSite) {
    this.typeOfSite = typeOfSite;
  }

  public String getTypeOfSite() {
    return this.typeOfSite;
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

  public DateTime getDate() {
    return this.date;
  }

  public void setDateEnd(DateTime dateEnd) {
    this.dateEnd = dateEnd;
  }

  public DateTime getDateEnd() {
    return this.dateEnd;
  }




}
