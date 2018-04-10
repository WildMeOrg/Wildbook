package org.ecocean.social;

import org.ecocean.*;
import java.util.ArrayList;

/**
 * Representing a dyadic social relationship
 */
public class Relationship implements java.io.Serializable{

  //default JDO constructor
  public Relationship(){}
  
  public Relationship(String type, String relatedCommunityName, String individualName1, String individualName2, String individualRole1, String individualRole2){
    this.type=type;
    this.relatedSocialUnitName=relatedCommunityName;
    this.markedIndividualName1=individualName1;
    this.markedIndividualName2=individualName2;
    this.markedIndividualRole1=individualRole1;
    this.markedIndividualRole2=individualRole2;
  }
  
  public Relationship(String type, String individualName1, String individualName2, String individualRole1, String individualRole2){
    this.type=type;
    this.markedIndividualName1=individualName1;
    this.markedIndividualName2=individualName2;
    this.markedIndividualRole1=individualRole1;
    this.markedIndividualRole2=individualRole2;
  }
  
  public Relationship(String type, String individualName1, String individualName2){
    this.type=type;
    this.markedIndividualName1=individualName1;
    this.markedIndividualName2=individualName2;
  }

  private static final long serialVersionUID = 6688796543218832687L;

  private String relatedSocialUnitName;
  
  private String markedIndividualName1;
  private String markedIndividualRole1;
  private String markedIndividual1DirectionalDescriptor;

  private String markedIndividualName2;
  private String markedIndividualRole2;
  private String markedIndividual2DirectionalDescriptor;
  
  private String type;
  
  private String relatedComments;
  
  private long startTime=-1;
  private long endTime=-1;
  
  private Boolean bidirectional=true;
  
  
  
  public String getRelatedSocialUnitName(){
    return relatedSocialUnitName;
  }
  public void setRelatedSocialUnitName(String name){
    if(name!=null){relatedSocialUnitName=name;}
    else{relatedSocialUnitName=null;}
  }

  public String getType(){return type;}
  public void setType(String typeName){type=typeName;}
  
  
  public String getMarkedIndividualName1(){
    return markedIndividualName1;
  }
  public void setMarkedIndividualName1(String name){
    if(name!=null){
      markedIndividualName1=name;
    }
    else{markedIndividualName1=null;}
  }
  
  
  public String getMarkedIndividualRole1(){
    return markedIndividualRole1;
  }
  public void setMarkedIndividualRole1(String role){
    if(role!=null){markedIndividualRole1=role;}
    else{markedIndividualRole1=null;}
  }
  
  
  public String getMarkedIndividualRole2(){
    return markedIndividualRole2;
  }
  public void setMarkedIndividualRole2(String role){
    if(role!=null){markedIndividualRole2=role;}
    else{markedIndividualRole2=null;}
  }
  
  
  public String getMarkedIndividualName2(){
    return markedIndividualName2;
  }
  public void setMarkedIndividualName2(String name){
    if(name!=null){
      markedIndividualName2=name;
    }
    else{markedIndividualName2=null;}
  }
  
  public void setRelatedComments(String comments){
    if(comments!=null){relatedComments=comments;}
    else{comments=null;}
  }
  public String getRelatedComments(){return relatedComments;}
  
  public void setStartTime(long time){startTime=time;}
  public void setEndTime(long time){endTime=time;}
  
  public long getStartTime(){return startTime;}
  public long getEndTime(){return endTime;}
  
  public String getMarkedIndividual1DirectionalDescriptor(){return markedIndividual1DirectionalDescriptor;}
  public String getMarkedIndividual2DirectionalDescriptor(){return markedIndividual2DirectionalDescriptor;}
  
  public void setMarkedIndividual1DirectionalDescriptor(String desc){
    if(desc!=null){markedIndividual1DirectionalDescriptor=desc;}
    else{markedIndividual1DirectionalDescriptor=null;}
  }
  public void setMarkedIndividual2DirectionalDescriptor(String desc){
    if(desc!=null){markedIndividual2DirectionalDescriptor=desc;}
    else{markedIndividual2DirectionalDescriptor=null;}
  }
  
  public Boolean getBidirectional(){return bidirectional;}
  public void setBidirectional(Boolean direction){
    if(direction!=null){bidirectional=direction;}
    else{bidirectional=null;}
  }
  
}
