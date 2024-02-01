package org.ecocean.movement;

import org.ecocean.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** 
*
* @author Colin Kingen
* 
*  Refers to a Path, creating a log of the movements that
*  took place during a survey track.
* 
*/

public class SurveyTrack implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = -8570163271211244522L;
  
  private ArrayList<Occurrence> occurrences = new ArrayList<Occurrence>();

  private String surveyTrackID;
  
  private String vesselID;
  private String locationID;
  // Line transect, ect.
  private String type;
    private Path path;
  
  private Measurement distance;
  
  private String dateTimeCreated;
  private String dateTimeModified;
  
    public SurveyTrack() {
        generateUUID();
        setDateTimeCreated();
    }
    public SurveyTrack(Path p) {
        this();
        this.setPath(p);
    }

  public String getDateTimeCreated() {
    if (dateTimeCreated != null) {
      return dateTimeCreated;
    }
    return "";
  }

  public void setDateTimeCreated(String time) {
    dateTimeCreated = time;
  }

  public void setDateTimeCreated() {
        dateTimeCreated = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
  }
  
  public String getDWCDateLastModified() {
    return dateTimeModified;
  }

  public void setDWCDateLastModified(String lastModified) {
    dateTimeModified = lastModified;
  }

  public void setDWCDateLastModified() {
    dateTimeModified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
  }
  
    //getStartTime() and getEndTime() and getComputedDuration() are based on our path!
    public Long getStartTime() {
        if (path == null) return null;
        return path.getStartTimeMillis();
    }
    public Long getEndTime() {
        if (path == null) return null;
        return path.getEndTimeMillis();
    }

    public Long getComputedDuration() {
        if (path == null) return null;
        return path.getComputedDuration();
    }

    public String getID(){
        return surveyTrackID;
    }

  public String getPrimaryKeyID(){
    return getID();
  }
  
  public void setID(String id) {
    if (id != null && !id.equals("")) {
      surveyTrackID = id;
      setDWCDateLastModified();
    }
  }

  public void setPrimaryKeyID(String id) {
    setID(id);
  }
  
/*
  public String getParentSurveyID() {
    if (parentSurveyID != null) {
      return parentSurveyID;
    } else {
      return null;
    }
  }
*/

    public void setPath(Path p) {
        path = p;
        setDWCDateLastModified();
    }

    public Path getPath() {
        return path;
    }

    public ArrayList<Occurrence> getOccurrences() {
        return getAllOccurrences();
    }

  public ArrayList<Occurrence> getAllOccurrences() {
    if (!occurrences.isEmpty()) {
      return occurrences; 
    }
    return null;
  }

    public int numOccurrences() {
        if (occurrences == null) return 0;
        return occurrences.size();
    }
  
  public Occurrence getOccurenceByID(String id) {
    for (int i=0; i<occurrences.size(); i++) {
      Occurrence thisOcc = occurrences.get(i);
      if (thisOcc.getOccurrenceID().equals(id)) {
        return thisOcc;
      }
    }
    return null;  
  }
  
  public boolean hasOccurrence(Occurrence queryOcc) {
    if (!occurrences.isEmpty()&&occurrences.contains(queryOcc)) {
      return true;
    }
    return false;
  }
  
  public void addOccurrence(Occurrence occ) {
    try {
      if (occ != null&&!occurrences.contains(occ)) {
        occ.setCorrespondingSurveyTrackID(this.getID()); //see FK rant, -jon
        occurrences.add(occ);
        setDWCDateLastModified();
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("From Survey Track: Failed to this occ!");
    } 
  }
  
    public void setOccurrences(ArrayList<Occurrence> occs) {
        occurrences = occs;
    }

  public void addMultipleOccurrences(ArrayList<Occurrence> occArray, Shepherd myShepherd) {
    if (occArray.size() >= 1) {
      for (Occurrence occ : occArray) {
        if (!occurrences.contains(occ)) {
          occurrences.add(occ);
        }
      }
      setDWCDateLastModified();
    }
  }

    private void createPointLocationForPath(double lat, double lon, Long milliDate) {
        PointLocation pt = new PointLocation(lat,lon,milliDate);
        if (path == null) {
            path = new Path(pt); //this seems a better constructor, so lets go with it
            return;
        }
        path.addPointLocation(pt);
    }

/*
  private Path getOrCreatePath(String pathID, Shepherd myShepherd) {
    Path pth = null;
    myShepherd.beginDBTransaction();
    try {
      if (myShepherd.isPath(pathID)) {
        pth = myShepherd.getPath(pathID);        
      } else {
        pth = new Path(this);
        myShepherd.getPM().makePersistent(pth);
        myShepherd.commitDBTransaction();
      }
    } catch (NullPointerException npe) {
      myShepherd.rollbackDBTransaction();
      npe.printStackTrace();
    }
    return pth;
  }
*/
  
  public Measurement getDistance() {
        return distance;
  }
  
  public void setDistance(Measurement dist) {
    if (dist.getUnits() != null) {
      distance = dist;
      setDWCDateLastModified();
    }
  }
  
  public void setType(String typ) {
    if (typ != null && !typ.equals("")) {
      type = typ;
      setDWCDateLastModified();
    }
  }
  
  public String getType() {
    if (type != null && !type.equals("")) {
      return type;
    } else {
      return null;
    }
  }
  
  public void setLocationID(String loc) {
    if (loc != null && !loc.equals("")) {
      locationID = loc;
      setDWCDateLastModified();
    }
  }
  
  public String getLocationID() {
    if (locationID != null && !locationID.equals("")) {
      return locationID;
    } else {
      return null;
    }
  }
  
  public void setVesselID(String v) {
    if (v != null && !v.equals("")) {
      vesselID = v;
      setDWCDateLastModified();
    }
  }
  
  public String getVesselID() {
    if (vesselID != null && !vesselID.equals("")) {
      return vesselID;
    } else {
      return null;
    }
  }
  
  private void generateUUID() {
    this.surveyTrackID = Util.generateUUID();
  }

    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getID())
            .append("occs", this.numOccurrences())
            .append("path", path)
            .toString();
    }
}




