package org.ecocean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

import org.ecocean.*;
import org.joda.time.DateTime;

/**
 * Each pointLocation is a specific spot on Earth defined by latitude, longitude
 * elevation above (or below) sea level and a time.
 * 
 * The Path object is made up of an array of these, and a group over time create
 * a useful way of tracking a survey and survey track.
 *
 * @author Colin Kingen
 *
 */

public class PointLocation implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -3758129925666366058L;

  public String pointLocationID = null;

  private Double latitude = null;
  private Double longitude = null;
  private Double bearing = null;

  private Measurement elevation;

  private Long dateTime = null;

  private String correspondingPathID = null;
  private String correspondingEncounterID = null;
  private String correspondingOccurrenceID = null;

  public PointLocation() {};

  public PointLocation(Double lat, Double lon) {
    if (latLonCheck(lat, lon)) {
      this.longitude = lon;
      this.latitude = lat;
    }
    generateUUID();
  }

  public PointLocation(Double lat, Double lon, Long date) {
    System.out.println("Lat: "+lat+" Lon: "+lon+" Date: "+date);
    if (latLonCheck(lat,lon)&&date!=null) {
      this.longitude = lon;
      this.latitude = lat;
      this.dateTime = date;
    }
    generateUUID();
  }

  public PointLocation(Double lat, Double lon, Long date, Measurement el) {
    if (latLonCheck(lat, lon)&&date!=null&& elevation!= null) {
      this.longitude = lon;
      this.latitude = lat;
      this.dateTime = date;
      this.elevation = el;
    }
    generateUUID();
  }

  public String getID() {
    return pointLocationID;
  }

  public String getDateTimeInMilli() {
    if (dateTime!=null){
      return dateTime.toString();      
    }
    return null;
  }
  
  public String getDateTimeAsString() {
    if (dateTime!=null){    
      DateTime dt = new DateTime(dateTime);
      return dt.toString();
    }
    return null;
  }
  
  public String getTimeAsString() { 
    if (dateTime!=null){      
      DateTime dt = new DateTime(dateTime);
      String time = String.valueOf(dt.getHourOfDay()) + ":" + String.valueOf(dt.getMinuteOfHour());
      return time;
    }
    return null; 
  }

  public void setDateTimeInMilli(Long dt) {
    if (dt > 9132014) {
      this.dateTime = dt;
    }
  }

  public Double getLatitude() {
    if (latitude != -1) {
      return latitude;
    }
    return null;
  }

  public void setLatitude(Double lat) {
    if (lat >= -90 && lat <= 90) {
      latitude = lat;
    }
  }

  public Double getLongitude() {
    if (longitude != -1) {
      return longitude;
    }
    return null;
  }

  public void setBearing(Double bear) {
    if (bear >= -180 && bear <= 180) {
      bearing = bear;
    }
  }

  public Double getBearing() {
    if (bearing != -1) {
      return bearing;
    }
    return null;
  }

  public void setLongitude(Double lon) {
    if (lon >= -180 && lon <= 180) {
      longitude = lon;
    }
  }

  public String getEncounterID() {
    if (correspondingEncounterID != null) {
      return correspondingEncounterID;
    }
    return null;
  }

  public void setEncounterID(String id) {
    if (id != null && !id.equals("")) {
      correspondingEncounterID = id;
    }
  }

  public String getOccurrenceID() {
    if (correspondingOccurrenceID != null) {
      return correspondingOccurrenceID;
    }
    return null;
  }

  public void setOccurenceID(String id) {
    if (id != null && !id.equals("")) {
      correspondingOccurrenceID = id;
    }
  }

  public String getPathID() {
    if (correspondingPathID != null) {
      return correspondingPathID;
    }
    return null;
  }

  public void setPathID(String id) {
    if (id != null && !id.equals("")) {
      correspondingPathID = id;
    }
  }

  private void generateUUID() {
    this.pointLocationID = Util.generateUUID().toString();
  }

  private boolean latLonCheck(Double lat, Double lon) {
    if (lat!=null&&lon!=null&&lat>=-90&&lat<=90&&lon>-180&&lon<180) {
      return true;
    }
    return false;
  }

}
