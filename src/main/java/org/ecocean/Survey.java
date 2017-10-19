package org.ecocean;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.ecocean.movement.*;
/**
* This is an object that contains occurrences. It also has several tracks, with specific
* geographic points (locations) that were traversed. It is intended to be a measure of the work 
* spent to collect data, and a way of relating media assets to a specific period of 
* collection. 
*
* 2017
* @author Colin Kingen
*/

public class Survey implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = -5028529439301775287L;
  private ArrayList<SurveyTrack> surveyTracks;
  
  private String surveyID;
  private String project;
  private String organization;
  private String comments = "None";
  private String type;
  private Long startTime;
  private Long endTime;
  // This is the actual amount of effort spent to gather date. 
  private Measurement effort;
  
  private String dateTimeCreated;
  private String dateTimeModified;
  
  private String date;
  
  private ArrayList<Observation> observations = new ArrayList<Observation>();
  
  //empty constructor used by the JDO enhancer
  public Survey(){}
  
  public Survey(String date){
    this.date=date;
    generateID();
    this.surveyTracks = new ArrayList<SurveyTrack>();
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
    try {
      if (comments != null && !comments.equals("None")) {
        comments += newComments;
      } else {
        comments = newComments;
      }
      setDWCDateLastModified();      
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
  }
  
  public String getID() {
    if (surveyID != null) {
      return surveyID;
    } else {
      return null;
    }
  }
  
  public void setID(String newID) {
    surveyID = newID;
    setDWCDateLastModified();
  }
  
  public void generateID() {
    String id = Util.generateUUID().toString();  
    surveyID = id;
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
    
  public void setProjectName(String proj) {
    if (proj != null && !proj.equals("")) {
      project = proj;
      setDWCDateLastModified();
    }
  }
  
  public String getProjectName() {
    if (project != null && !project.equals("")) {
      return project;
    } else {
      return null;
    }
  }
  
  public void setOrganization(String org) {
    if (org != null && !org.equals("")) {
      organization = org;
      setDWCDateLastModified();
    }
  }
  
  public String getOrganization() {
    if (organization != null && !organization.equals("")) {
      return organization;
    } else {
      return null;
    }
  }
  
  public void setProjectType(String typ) {
    if (typ != null && !typ.equals("")) {
      type = typ;
      setDWCDateLastModified();
    }
  }
  
  public String getProjectType() {
    if (type != null && !type.equals("")) {
      return type;
    } else {
      return null;
    }
  }
  
  public Measurement getEffort() {
    if (effort != null) {
      return effort;
    }
    return null;
  }
  
  public void setEffort(Measurement eff) {
    if (eff.getUnits() != null) {
      effort = eff;
      setDWCDateLastModified();
    }
  }
  
  public void setStartTimeMilli(Long i) {
    if (i > 0) {
      startTime = i;
    }
  }
  
  public String getStartTimeMilli() {
    if (startTime > 0 && startTime != null) {
      return startTime.toString();
    }
    return null;
  }
  
  public void setEndTimeMilli(Long et) {
    if (et > 0) {
      startTime = et;
    }
  }
  
  public void setEndTimeWithDate(String date) {
    String milli =  monthDayYearToMilli(date);
    System.out.println("End Milli : "+milli);
    try {
      Long m = Long.valueOf(milli); 
      endTime = Math.abs(m);      
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Failed to Set endTime from dateString.");
    }
  } 
  
  public void setStartTimeWithDate(String date) {
    String milli =  monthDayYearToMilli(date);
    System.out.println("Start Milli : "+milli);
    try {
      Long m = Long.valueOf(milli);  
      startTime = Math.abs(m);  
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Failed to Set startTime from dateString.");
    }
  } 
  
  public String getEndTimeMilli() {
    if (endTime > 0 && endTime != null) {
      return endTime.toString();
    }
    return null;
  }
  
  private String monthDayYearToMilli(String newDate) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
    String month = newDate.substring(2);
    String day = newDate.substring(3,5);
    String year = newDate.substring(6);
    Date dt;
    try {
      dt = sdf.parse(month+"-"+day+"-"+year);
    } catch (ParseException e) {
      e.printStackTrace();
      System.out.println("Failed to Parse String : "+month+"-"+day+"-"+year);
      return null;
    }
    return String.valueOf(dt.getTime());
  }
  
  public ArrayList<Observation> getBaseObservationArrayList() {
    return observations;
  }

  public void addBaseObservationArrayList(ArrayList<Observation> arr) {
    if (observations.isEmpty()) {
      observations=arr;      
    } else {
     observations.addAll(arr); 
    }
  }
  public void addObservation(Observation obs) {
    observations.add(obs);
  }
  public Observation getObservationByName(String obName) {
    if (observations != null && observations.size() > 0) {
      for (Observation ob : observations) {
        if (ob.getName() != null && ob.getName().equals(obName)) {
          return ob;
        }
      }
    }
    return null;
  }
  public Observation getObservationByID(String obId) {
    if (observations != null && observations.size() > 0) {
      for (Observation ob : observations) {
        if (ob.getID() != null && ob.getID().equals(obId)) {
          return ob;
        }
      }
    }
    return null;
  }

}





