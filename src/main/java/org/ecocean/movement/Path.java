package org.ecocean.movement;

import java.util.ArrayList;

import org.ecocean.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
* @author Colin Kingen
* 
* A path is a collection of pointLocation objects. Each of these pointLocations contains
* GPS coordinent data, and a group of them for a particular survey 
* gives you the path or paths that a team or individual followed during 
* a specific pointLocation in time. 
*
*This is a very simple object that keeps track of those points, the SurveyTrack
*being the object above it. 
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
    pointLocations = pts;
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
    if (pointLocations!=null&&!pointLocations.isEmpty()) {
      Long startLong = null;
      for (PointLocation pl : pointLocations) {
        if (pl.getDateTimeInMilli()!=null) {
          Long tempLong = Long.valueOf(pl.getDateTimeInMilli());
          if (startLong==null||tempLong<startLong) {
            startLong = tempLong;
          }          
        }
      }
      return startLong;
    }
    return null;
  }
  
  public String getStartTime() {
    if (this.getStartTimeMillis()!=null) {
      DateTime dt = new DateTime(this.getStartTimeMillis());
      DateTimeFormatter out = DateTimeFormat.forPattern("HH:mm");
      String startTime = out.print(dt.getMillis());
      return startTime;
    }
    return null;
  }

  public String getEndTime() {
    if (this.getEndTimeMillis()!=null) {
      DateTime dt = new DateTime(this.getEndTimeMillis());
      DateTimeFormatter out = DateTimeFormat.forPattern("HH:mm");
      String endTime = out.print(dt.getMillis());
      return endTime;
    }
    return null;
  } 

  public Long getEndTimeMillis() {
    if (pointLocations!=null&&!pointLocations.isEmpty()) {
      Long endLong = null;
      for (PointLocation pl : pointLocations) {
        if (pl.getDateTimeInMilli()!=null) {
          Long tempLong = Long.valueOf(pl.getDateTimeInMilli());
          if (endLong==null||tempLong>endLong) {
            endLong = tempLong;
          }
        }
      }
      return endLong;
    }
    return null;
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
