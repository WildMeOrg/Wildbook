package org.ecocean.movement;

import org.ecocean.*;
import java.text.SimpleDateFormat;
import java.util.*;

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
  private String parentSurveyID;
  
  private String vesselID;
  private String locationID;
  private String pathID;
  // Line transect, ect.
  private String type;
  
  private Measurement distance;
  
  private String dateTimeCreated;
  private String dateTimeModified;
  
  public SurveyTrack(){};
  
  public SurveyTrack(String surveyID){
    if (surveyID != null) {
      this.parentSurveyID = surveyID; 
      generateUUID();
      setDateTimeCreated();
    }
  }
  
  public SurveyTrack(Survey survey){
    if (survey != null) {
      this.parentSurveyID = survey.getID();
      generateUUID();
      setDateTimeCreated();
      setDateTimeCreated();
    }
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
  
  public String getID (){
    if (surveyTrackID != null && !surveyTrackID.equals("")) {
      return surveyTrackID;      
    }
    return null;
  }
  
  public void setID(String id) {
    if (id != null && !id.equals("")) {
      surveyTrackID = id;
      setDWCDateLastModified();
    }
  }
  
  public String getParentSurveyID() {
    if (parentSurveyID != null) {
      return parentSurveyID;
    } else {
      return null;
    }
  }
  
  public void setPathID(String pid) {
    if (pid != null && !pid.equals("") ) {
      pathID = pid;
      setDWCDateLastModified();
    }
  }
  
  public String getPathID() {
    if (pathID != null) {
      return pathID;
    } else {
      return null;
    }
  }
  
  public void setParentSurveyID(String id) {
    if (id != null && !id.equals("") ) {
      parentSurveyID = id;
      setDWCDateLastModified();
    }
  }
  
  public ArrayList<Occurrence> getAllOccurrences() {
    if (!occurrences.isEmpty()) {
     return occurrences; 
    } else {
      return null;
    }
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
  
  public void addOccurence(Occurrence occ) {
    if (occ != null) {
      occurrences.add(occ);
      setDWCDateLastModified();
    }
  }
  
  public void addMultipleOccurences(ArrayList<Occurrence> occArray) {
    if (occArray.size() >= 1) {
      for (int i=0; i<occArray.size(); i++) {
        occurrences.add(occArray.get(i));
      }
      setDWCDateLastModified();
    }
  }
  
  public Measurement getDistance() {
    if (distance != null) {
      return distance;
    }
    return null;
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
  
}




