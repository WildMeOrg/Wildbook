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
    
    surveyTracks = new ArrayList<SurveyTracks>();
    occurrences = new ArrayList<Occurrence>();
    
    setDateTimeCreated();
    setDWCDateLastModified();
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
  }
  
  public ArrayList<Occurrence> getAllOccurrences() {
    if (!occurrences.isEmpty()) {
     return occurrences; 
    } else {
      return null;
    }
  }
  
  public Occurrence getOccurence() {
    
  }
  
  public void setOccurence() {
    
  }
    
  
}





