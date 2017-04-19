package org.ecocean;

import java.text.SimpleDateFormat;
import java.util.*;

/** 
*
* @author Colin Kingen
*/

public class SurveyTrack implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = -8570163271211244522L;

  private String surveyTrackID;
  private String parentSurveyID;
  
  public SurveyTrack(){};
  
  public SurveyTrack(String surveyID){
    if (surveyID != null) {
      parentSurveyID = surveyID;      
    }
  }
  
  public SurveyTrack(Survey survey){
    if (survey != null) {
      parentSurveyID = survey.getId();
    }
  }
  
  public String getID(){
    return surveyTrackID;
  }
  
  
}