package org.ecocean;

import java.util.ArrayList;

import org.ecocean.*;

/**
* Each point is a specific spot on Earth defined by latitude, longitude
* elevation above (or below) sea level and a time. 
* 
* The Path object is made up of an array of these, and a group over time 
* create a useful way of tracking a survey and survey track. 
*
* @author Colin Kingen
*
*/

public class Point implements java.io.Serializable {
  
  /**
   * 
   */
  private static final long serialVersionUID = -3758129925666366058L;
  
  
  private long latitude = -1;
  private long longitude = -1;
  private long bearing = -1;
  
  private Measurement elevation = null;
  
  // It's in milliseconds!
  private long dateTime = -1;
  
  private String correspondingPathID = null;
  private String correspondingEncounterID = null; 
  private String correspondingOccurrenceID = null; 
  
  public Point(){};
  
  public Point(long lat, long lon) {
    if (latLonCheck(lat, lon)) {
      longitude = lon;
      latitude = lat;
    }
  }
  
  public Point(long lat, long lon, long date) {
    if (latLonCheck(lat,lon) && date > 0) {
      longitude = lon;
      latitude = lat;
      dateTime = date;
     }
  }
  
  public Point(long lat, long lon, long date, Measurement el) {
    if (latLonCheck(lat,lon) && date > 0 && elevation != null ) {
      longitude = lon;
      latitude = lat;
      dateTime = date;
      elevation = el;     
    }
  }
  
  public long getLatitude() {
    if (latitude != -1) {
      return latitude;
    }
    return -1;
  }

  public void setLatitude(long lat) {
    if (lat >= -90 && lat <= 90) {
      latitude = lat;
    }
  }
  
  public long getLongitude() {
    if (latitude != -1) {
      return latitude;
    }
    return -1;
  }

  public void setLongitude(long lon) {
    if (lon >= -180 && lon <= 180) {
      latitude = lon;
    }
  }
  
  private boolean latLonCheck(long lat, long lon) {
    if (lat >= -90 && lat <= 90 && lon > -180 && lon < 180) { 
      return true;
    }
    return false;
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
}







