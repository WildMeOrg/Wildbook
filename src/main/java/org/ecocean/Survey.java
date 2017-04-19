package org.ecocean;

import java.text.SimpleDateFormat;
import java.util.*;

/**
* This is an object that contains occurences. It also has several tracks, with specific
* geographic points that were traversed. It is intended to be a measure of the work 
* spent to collect data, and a way of relating media assets to a specific period of 
* collection. 
*
* @author Colin Kingen
*/

public class Survey implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = -5028529439301775287L;
  private ArrayList<SurveyTrack> surveyTracks;
  private ArrayList<Occurrence> occurrences;
  
  private String surveyID;
  
  
  private String comments = "None";
  
  private String dateTimeCreated;
  private String dateTimeModified;
  
  private String date;
  
  
  //empty constructor used by the JDO enhancer
  public Survey(){}
  
  public Survey(String date){
    this.date=date;
    
    surveyTracks = new ArrayList<SurveyTrack>();
    occurrences = new ArrayList<Occurrence>();
    
    setDateTimeCreated();
    setDWCDateLastModified();
  }
  
  public void setDate(String newDate) {
    date = newDate;
  }
  
  public String getDate() {
    if (date != null) {
      return date;      
    }
    return null;
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
    
  public String getComments() {
    if (comments != null) {
      return comments;
    } else {
      return "None";
    }
  }
  
  public void addComments(String newComments) {
    if (comments != null && !comments.equals("None")) {
      comments += newComments;
    } else {
      comments = newComments;
    }
    setDWCDateLastModified();
  }
  
  public String getId() {
    if (surveyID != null) {
      return surveyID;
    } else {
      return null;
    }
  }
  
  public void setId(String newID) {
    surveyID = newID;
    setDWCDateLastModified();
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
  
  public ArrayList<SurveyTrack> getAllSurveyTracks() {
    if (!surveyTracks.isEmpty()) {
     return surveyTracks; 
    } else {
      return null;
    }
  }
  
  public SurveyTrack getSurveyTrackByID(String id) {
    for (int i=0; i<surveyTracks.size(); i++) {
      SurveyTrack thisTrack = surveyTracks.get(i);
      if (thisTrack.getID().equals(id)) {
        return thisTrack;
      }
    }
    return null;
  }
  
  public void addSurveyTrack(SurveyTrack thisTrack) {
    if (thisTrack != null) {
      surveyTracks.add(thisTrack);
      setDWCDateLastModified();
    }
  }
  
  public void addMultipleSurveyTrack(ArrayList<SurveyTrack> trackArray) {
    if (trackArray.size() >= 1) {
      for (int i=0; i<trackArray.size(); i++) {
        surveyTracks.add(trackArray.get(i));
      }
      setDWCDateLastModified();
    }
  }
    
  
}





