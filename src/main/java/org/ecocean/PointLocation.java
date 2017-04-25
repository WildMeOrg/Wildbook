package org.ecocean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

import org.ecocean.*;

/**
* Each pointLocation is a specific spot on Earth defined by latitude, longitude
* elevation above (or below) sea level and a time. 
* 
* The Path object is made up of an array of these, and a group over time 
* create a useful way of tracking a survey and survey track. 
*
* @author Colin Kingen
*
*/

public class PointLocation implements java.io.Serializable {
  
  /**
   * 
   */
  private static final long serialVersionUID = -3758129925666366058L;
  
  public UUID pointLocationID = null;
  
  
  private long latitude = -1;
  private long longitude = -1;
  private long bearing = -1;
  
  private List<Measurement> elevation;
  
  // It's in milliseconds!
  private long dateTime = -1;
  
  private String correspondingPathID = null;
  private String correspondingEncounterID = null; 
  private String correspondingOccurrenceID = null; 
  
  public PointLocation(){};
  
  public PointLocation(long lat, long lon) {
    if (latLonCheck(lat, lon)) {
      longitude = lon;
      latitude = lat;
    }
    generateUUID();
  }
  
  public PointLocation(long lat, long lon, long date) {
    if (latLonCheck(lat,lon) && date > 0) {
      longitude = lon;
      latitude = lat;
      dateTime = date;
     }
    generateUUID();
  }
  
  public PointLocation(long lat, long lon, long date, Measurement el) {
    if (latLonCheck(lat,lon) && date > 0 && elevation != null ) {
      longitude = lon;
      latitude = lat;
      dateTime = date;
      elevation.add(el);     
    }
    generateUUID();
  }
  
  public UUID getID() {
    return pointLocationID;
  }
  
  public long getDateTimeInMilli() {
    return dateTime;
  }
  
  public void setDateTimeInMilli(long dt) {  
    if (dt > 9132014) {
      dateTime = dt;
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
    if (longitude != -1) {
      return longitude;
    }
    return -1;
  }

  public void setBearing(long bear) {
    if (bear >= -180 && bear <= 180) {
      bearing = bear;
    }
  }
  
  public long getBearing() {
    if (bearing != -1) {
      return bearing;
    }
    return -1;
  }

  public void setLongitude(long lon) {
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
    this.pointLocationID = UUID.randomUUID();
  }
  
  private boolean latLonCheck(long lat, long lon) {
    if (lat >= -90 && lat <= 90 && lon > -180 && lon < 180) { 
      return true;
    }
    return false;
  }
  
}







