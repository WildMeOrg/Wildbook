package org.ecocean.movement;

import java.util.ArrayList;
import java.util.List;
import org.ecocean.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.json.JSONObject;
import org.json.JSONArray;

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
  
    public ArrayList<PointLocation> getPointLocations() {  //just for consistency with other classes
        return pointLocations;
    }

  public ArrayList<PointLocation> getAllPointLocations() {
    return pointLocations;
  }

    public int getNumPointLocations() {
        return (pointLocations == null) ? 0 : pointLocations.size();
    }
  

/*
    //returns a "representative" -- but much more manageable -- subset of points (i.e. not thousands of points)
    public List<PointLocation> getPointLocationsSubsampled() {
        //MAGIC GOES HERE!
        //e.g. suggested by virgil -- https://github.com/hgoebl/simplify-java
    }
*/
    public List<PointLocation> getPointLocationsSubsampled() {
        Double avg = averageDiff2();
        if (avg == null) return pointLocations;  //its a short list yo, chill out!
        return getPointLocationsSubsampled(avg * 3.0D);  //i dunno... 3?
    }
    //this flavor will keep a distance of at least dist between
    public List<PointLocation> getPointLocationsSubsampled(double dist2) {
        if (Util.collectionSize(pointLocations) < 4) return pointLocations;  //dont waste our time!
        List<PointLocation> rtn = new ArrayList<PointLocation>();
        PointLocation pt = pointLocations.get(0);
        rtn.add(pt);
        for (int i = 1 ; i < pointLocations.size() ; i++) {
            if ((i == pointLocations.size()) || (pt.diff2(pointLocations.get(i)) > dist2)) {  //we always add the final one btw
                rtn.add(pt);
                pt = pointLocations.get(i);
            }
        }
        return rtn;
    }
    //this just dumbly returns atMost points, evenly spaced
    public List<PointLocation> getPointLocationsSubsampled(int atMost) {
        if ((atMost < 1) || (Util.collectionSize(pointLocations) <= atMost)) return pointLocations;
        int gap = (int)Math.floor(pointLocations.size() / atMost);
        List<PointLocation> rtn = new ArrayList();
        for (int i = 0 ; i < pointLocations.size() ; i += gap) {
            rtn.add(pointLocations.get(i));
        }
        return rtn;
    }

    public Double averageDiff2() {
        if (Util.collectionSize(pointLocations) < 2) return null;  //meh?
        Double total = 0D;
        PointLocation pt = pointLocations.get(0);
        for (int i = 1 ; i < pointLocations.size() ; i++) {
            total += pt.diff2(pointLocations.get(i));
            pt = pointLocations.get(i);
        }
        return total / (pointLocations.size() - 1);
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

    public Long getComputedDuration() {
        Long s = getStartTimeMillis();
        Long e = getEndTimeMillis();
        if ((s == null) || (e == null)) return null;
        if (s > e) {
            System.out.println("ERROR!  getComputedDuration() invalid (" + s + " > " + e + ") for " + this);
            return null;
        }
        return e - s;
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
  

    public JSONObject toJSONObject() {
        JSONObject rtn = new JSONObject();
        rtn.put("id", getID());
        rtn.put("pointLocations", toJSONArray(pointLocations));
        return rtn;
    }

    public static JSONArray toJSONArray(List<PointLocation> pts) {
        JSONArray arr = new JSONArray();
        if (!Util.collectionIsEmptyOrNull(pts)) for (PointLocation pt : pts) {
            if (pt != null) arr.put(pt.toJSONObject());
        }
        return arr;
    }

    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getID())
            .append("numPts", this.getNumPointLocations())
            .append("startTime", new DateTime(this.getStartTimeMillis()))
            .append("endTime", new DateTime(this.getEndTimeMillis()))
            .toString();
    }

}

