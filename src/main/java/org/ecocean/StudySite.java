 package org.ecocean;

import org.joda.time.DateTime;

public class StudySite implements java.io.Serializable {

  private String id;
  private String name;
  private String typeOfSite = "camera trap"; // hard-coded

  // new fields
  private String governmentArea;
  private String population;
  private String huntingState;
  private String referenceSystem;
  private Integer daysNotWorking;
  private String lure;
  private String reward;
  private String typeOfCamera;
  private Double trapsPerNight;
  // end new fields

  private String locationID;

  private Double utmX;
  private Double utmY;
  private String epsgProjCode = GeocoordConverter.DEFAULT_EPSG_CODE_STRING;

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

  // new fields
  public void setGovernmentArea(String governmentArea){
    this.governmentArea = governmentArea;
  }
  public String getGovernmentArea(){
    return governmentArea;
  }
  public void setPopulation(String population){
    this.population = population;
  }
  public String getPopulation(){
    return population;
  }
  public void setHuntingState(String huntingState){
    this.huntingState = huntingState;
  }
  public String getHuntingState(){
    return huntingState;
  }
  public void setReferenceSystem(String referenceSystem){
    this.referenceSystem = referenceSystem;
  }
  public String getReferenceSystem(){
    return referenceSystem;
  }

  public void setDaysNotWorking(Integer daysNotWorking){
    this.daysNotWorking = daysNotWorking;
  }
  public Integer getDaysNotWorking(){
    return daysNotWorking;
  }


  public void setLure(String lure){
    this.lure = lure;
  }
  public String getLure(){
    return lure;
  }
  public void setReward(String reward){
    this.reward = reward;
  }
  public String getReward(){
    return reward;
  }
  public void setTypeOfCamera(String typeOfCamera){
    this.typeOfCamera = typeOfCamera;
  }
  public String getTypeOfCamera(){
    return typeOfCamera;
  }
  public void setTrapsPerNight(Double trapsPerNight){
    this.trapsPerNight = trapsPerNight;
  }
  public Double getTrapsPerNight(){
    return trapsPerNight;
  }



  // a little hackey, but these allow for both UTM and GPS coords
  public void setUtmX(Double utmX) {
    this.utmX = utmX;
  }
  public Double getUtmX() {
    return this.utmX;
  }
  public void setUtmY(Double utmY) {
    this.utmY = utmY;
  }
  public Double getUtmY() {
    return this.utmY;
  }
  public void setEpsgProjCode(String epsgProjCode) {
    this.epsgProjCode = epsgProjCode;
  }
  public String getEpsgProjCode() {
    return this.epsgProjCode;
  }
  private boolean hasUtmValues() {
    return (this.epsgProjCode!=null && this.utmY!=null && this.utmX!=null);
  }
  private void setGpsFromUtm() {
    if (this.hasUtmValues()) {
      double[] latLon = GeocoordConverter.utmToGps(utmX, utmY, epsgProjCode);
      this.setLatitude(latLon[0]);
      this.setLongitude(latLon[1]);
    }
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
