package org.ecocean;

import org.joda.time.DateTime;
import java.text.*;
import org.ecocean.Util;

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
  private String country;

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

  public StudySite(String name) {
    this.id = Util.generateUUID();
    this.name = name;
  }

  public StudySite(String name, String typeOfSite, Double lat, Double lon) {
    this.id = Util.generateUUID();
    this.name = name;
    this.typeOfSite = typeOfSite;
    this.latitude = lat;
    this.longitude = lon;
  }

  public StudySite(String name, Encounter enc) {
    this.id = Util.generateUUID();
    this.name = name;
    importEncounterFields(enc);
  }

  public void importEncounterFields(Encounter enc) {
    System.out.println("Import encounter fields...");
    if (Util.shouldReplace(enc.getGovernmentArea(), getGovernmentArea())) {
      setGovernmentArea(enc.getGovernmentArea());
    }
    if (Util.shouldReplace(enc.getPopulation(), getPopulation())) {
      setPopulation(enc.getPopulation());
    }
    if (Util.shouldReplace(enc.getHuntingState(), getHuntingState())) {
      setHuntingState(enc.getHuntingState());
    }
    if (getLatitude() ==null && !(enc.getLatitudeAsDouble() ==null)) {
      setLatitude( enc.getLatitudeAsDouble());
    }
    if (getLongitude()==null && !(enc.getLongitudeAsDouble()==null)) {
      setLongitude(enc.getLongitudeAsDouble());
    }
    if (Util.shouldReplace(enc.getLocationID(), getLocationID())) {
      setLocationID(enc.getLocationID());
    }
    if (Util.shouldReplace(enc.getCountry(), getCountry())) {
      setCountry(enc.getCountry());
    }

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

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCountry() {
    return this.country;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
    setUtmFromGps(false); //non-overwrite; sets UTM if possible
  }

  public Double getLatitude() {
    return this.latitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
    setUtmFromGps(false); //non-overwrite; sets UTM if possible
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
  // calling setGpsFromUtm() in the Utm setter enforces that these
  // two sets of fields stay consistent
  public void setUtmX(Double utmX) {
    setUtmXOnly(utmX);
    setGpsFromUtm(true);
  }
  public void setUtmXOnly(Double utmX) {
    if (Double.isNaN(utmX)) utmX = null;
    this.utmX = utmX;
  }
  public Double getUtmX() {
    return this.utmX;
  }
  public void setUtmY(Double utmY) {
    setUtmYOnly(utmY);
    setGpsFromUtm(true);
  }
  public void setUtmYOnly(Double utmY) {
    if (Double.isNaN(utmY)) utmY = null;
    this.utmY = utmY;
  }
  public Double getUtmY() {
    return this.utmY;
  }
  public void setEpsgProjCode(String epsgProjCode) {
    this.epsgProjCode = epsgProjCode;
    setGpsFromUtm(true);
  }
  public String getEpsgProjCode() {
    return this.epsgProjCode;
  }
  public boolean hasUtm() {
    return (this.epsgProjCode!=null && this.utmY!=null && this.utmX!=null);
  }
  public boolean hasGps() {
    return (getLatitude()!=null && getLongitude()!=null);
  }
  public void setGpsFromUtm(boolean overwrite) {
    if (overwrite) setGpsFromUtmForce();
    else setGpsFromUtm();
  }
  // assumed overwrite=false
  public void setGpsFromUtm() {
    System.out.println("Starting setGpsFromUtm");
    if (!hasGps()) setGpsFromUtmForce();
  }
  public void setGpsFromUtmForce() {
    System.out.println("Starting setGpsFromUtmForce");
    if (this.hasUtm()) {
      System.out.println("We have UTM, starting to convert:");
      double[] latLon = GeocoordConverter.utmToGps(utmX, utmY, epsgProjCode);
      if (latLon[0]==GeocoordConverter.ERROR_dOUBLE_TUPLE[0]) {
        System.out.println("StudySite.setGpsFromUtm is doing nothing because we got the Error Tuple from GeocoordConverter.");
        return;
      }
      System.out.println("We have GPS, storing: "+latLon);
      this.setLatitude(latLon[0]);
      this.setLongitude(latLon[1]);
    }
  }

  public void setUtmFromGps(boolean overwrite) {
    if (overwrite) setUtmFromGpsForce();
    else setUtmFromGps();
  }
  public void setUtmFromGps() { // does not overwrite
    if (!hasUtm()) setUtmFromGpsForce();
  }
  public void setUtmFromGpsForce() {
    if (hasGps()) {
      double[] doubleUTMs = GeocoordConverter.gpsToUtm(getLatitude(), getLongitude());
      setUtmXOnly(doubleUTMs[0]);
      setUtmYOnly(doubleUTMs[1]);
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

  public String getDateString(String format) {
    return formattedDateString(this.date, format);
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

  public String getDateEndString(String format) {
    return formattedDateString(this.dateEnd, format);
  }

  private String formattedDateString(DateTime dt, String format) {
    if (dt==null) {
      return null;
    } else if (format==null) {
      format = "MM-dd-yyyy";
    }
    return new SimpleDateFormat(format).format(dt.toDate());
  }



}
