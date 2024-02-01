package org.ecocean;

public class Story implements java.io.Serializable {
  
  
  /**
   * 
   */
  private static final long serialVersionUID = 1965897028115199370L;
  
  private String storyTellerName;
  private String storyTellerEmail;
  private String storyText;
  private String storyMediaURL;
  private String storyID;
  private String correspondingEncounterNumber;
  private String correspondingMarkedIndividualID;
  private String correspondingOccurrenceID;
  private String correspondingSocialUnitName;
  private SinglePhotoVideo correspondingThumbnailSinglePhotoVideo;

  /**
   * empty constructor required by JDO Enhancer
   */
  public Story() {}
  
  public Story(String storyID) {
    this.storyID=storyID;
  }
  
  public String getStoryTellerName(){return storyTellerName;}
  public void setStoryTellerName(String name){this.storyTellerName=name;}
  
  public String getStoryTellerEmail(){return storyTellerEmail;}
  public void setStoryTellerEmail(String email){this.storyTellerEmail=email;}
  
  public String getStoryText(){return storyText;}
  public void setStoryText(String text){this.storyText=text;}
  
  public String getStoryMediaURL(){return storyMediaURL;}
  public void setStoryMediaURL(String url){this.storyMediaURL=url;}
  
  public String getStoryID(){return storyID;}
  public void setStoryID(String id){this.storyID=id;}
  
  public String getCorrespondingEncounterNumber(){return correspondingEncounterNumber;}
  public void setCorrespondingEncounterNumber(String encounterNumber){
    if(encounterNumber!=null){
      this.correspondingEncounterNumber=encounterNumber;
    }
    else{
      this.correspondingEncounterNumber=null;
    }
  }
  
  public String getCorrespondingMarkedIndividualID(){return correspondingMarkedIndividualID;}
  public void setCorrespondingMarkedIndividualID(String indyID){
    if(indyID!=null){
      this.correspondingMarkedIndividualID=indyID;
    }
    else{
      this.correspondingMarkedIndividualID=null;
    }
  }
  
  public String getCorrespondingOccurrenceID(){return correspondingOccurrenceID;}
  public void setCorrespondingOccurrenceID(String occurID){
    if(occurID!=null){
      this.correspondingOccurrenceID=occurID;
    }
    else{
      this.correspondingOccurrenceID=null;
    }
  }
  
  public String getCorrespondingSocialUnitName(){return correspondingSocialUnitName;}
  public void setCorrespondingSocialUnitName(String name){
    if(name!=null){
      this.correspondingSocialUnitName=name;
    }
    else{
      this.correspondingSocialUnitName=null;
    }
  }
  
  public SinglePhotoVideo getCorrespondingThumbnailSinglePhotoVideo(){return correspondingThumbnailSinglePhotoVideo;}
  public void setCorrespondingThumbnailSinglePhotoVideo(SinglePhotoVideo thumb){
    if(thumb!=null){
      this.correspondingThumbnailSinglePhotoVideo=thumb;
      if(storyID!=null)thumb.setCorrespondingStoryID(this.storyID);
    }
    else{
      this.correspondingThumbnailSinglePhotoVideo=null;
    }
  }
  
}
