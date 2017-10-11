package org.ecocean.movement;

import java.util.ArrayList;
import java.util.UUID;

import org.ecocean.*;
import org.joda.time.DateTime;

/**
* @author Colin Kingen
* 
* A path is a collection of pointLocation objects. Each of these pointLocations contains
* GPS coordinent data, and a group of them for a particular survey 
* gives you the path or paths that a team or individual followed during 
* a specific pointLocation in time. 
*
*This is a very simple object that keeps track of those points, the SurveyTrack
*being the data layer above it. 
*/

public class Path implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -8130232817853279715L;
  
  private String pathID;
  
  private ArrayList<PointLocation> pointLocations; 
  
  public Path(){};
  
  public Path(SurveyTrack st) {
    generateUUID();
    st.setPathID(pathID);
  }
  
  public Path(PointLocation pnt) {
    this.pointLocations = new ArrayList<PointLocation>();
    generateUUID();
    if (pnt != null) {
      this.pointLocations.add(pnt);      
    }
  }
  
  public Path(ArrayList<PointLocation> pts) {
    generateUUID();
    if (pts.size() >= 1) {
      for (int i=0; i<pts.size(); i++ ) {
        this.pointLocations.add(pts.get(i));
      }
    }
  }  
  
  public String getID() {
    return pathID;
  }
  
  public PointLocation getPointLocation(String id) {
    String foundId = null;
    if (id !=null && pointLocations.size() > 0) {
      for (int i=0; i < pointLocations.size(); i++) {
        try {
          foundId = pointLocations.get(i).getID();
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (foundId == id) {
          return pointLocations.get(i);
        }
      }
    }
    return null;
  }
  
  public ArrayList<PointLocation> getAllPointLocations() {
    return pointLocations;
  }
  
  public Long getStartTimeMillis() {
    Long startLong;
    if (pointLocations!=null) {
      startLong = Long.valueOf(pointLocations.get(0).getDateTimeInMilli());
      for (PointLocation pl : pointLocations) {
        Long tempLong = Long.valueOf(pl.getDateTimeInMilli());
        if (tempLong<startLong) {
          startLong = tempLong;
        }
      }
      return startLong;
    }
    return null;
  }
  
  public String getStartTime() {
    DateTime dt = new DateTime(this.getStartTimeMillis());
    return String.valueOf(dt.getHourOfDay()) + ":" + String.valueOf(dt.getMinuteOfHour());
  }
  
  public Long getEndTimeMillis() {
    Long endLong;
    if (pointLocations!=null) {
      endLong = Long.valueOf(pointLocations.get(0).getDateTimeInMilli());
      for (PointLocation pl : pointLocations) {
        Long tempLong = Long.valueOf(pl.getDateTimeInMilli());
        if (tempLong>endLong) {
          endLong = tempLong;
        }
      }
      return endLong;
    }
    return null;
  }
  
  public String getEndTime() {
    DateTime dt = new DateTime(this.getEndTimeMillis());
    return String.valueOf(dt.getHourOfDay()) + ":" + String.valueOf(dt.getMinuteOfHour());
  }
  
  public void addPointLocation(PointLocation p) {
    if (this.getPointLocation(p.getID()) == null) {
      this.pointLocations.add(p);
    }
  }
  
  public void addPointLocationsArray(ArrayList<PointLocation> pts) {
    if (pts.size() >= 1) {
      for (int i=0; i<pts.size(); i++ ) {
        if (getPointLocation(pts.get(i).getID()) == null) {
          try {
            pointLocations.add(pts.get(i));            
          } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not add PointLocation with index "+i);
          }
        }
      }
    }
  }
  
  private void generateUUID() {
    this.pathID = Util.generateUUID();
  }
  
}