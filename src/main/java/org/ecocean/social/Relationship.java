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
    this.relatedCommunityName=relatedCommunityName;
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

  private String relatedCommunityName;
  
  private String markedIndividualName1;
  private String markedIndividualRole1;

  private String markedIndividualName2;
  private String markedIndividualRole2;
  
  private String type;
  
  private String relatedComments;
  
  
  
  public String getRelatedCommunityName(){
    return relatedCommunityName;
  }
  public void setRelatedCommunityName(String name){
    relatedCommunityName=name;
  }

  public String getType(){return type;}
  public void setType(String typeName){type=typeName;}
  
  
  public String getMarkedIndividualName1(){
    return markedIndividualName1;
  }
  public void setMarkedIndividualName1(String name){
    markedIndividualName1=name;
  }
  
  
  public String getMarkedIndividualRole1(){
    return markedIndividualRole1;
  }
  public void setMarkedIndividualRole1(String role){
    markedIndividualRole1=role;
  }
  
  
  public String getMarkedIndividualRole2(){
    return markedIndividualRole2;
  }
  public void setMarkedIndividualRole2(String role){
    markedIndividualRole2=role;
  }
  
  
  public String getMarkedIndividualName2(){
    return markedIndividualName2;
  }
  public void setMarkedIndividualName2(String name){
    markedIndividualName2=name;
  }
  
  public void setRelatedComments(String comments){
    relatedComments=comments;
  }
  public String getRelatedComments(){return relatedComments;}
  
  
}
