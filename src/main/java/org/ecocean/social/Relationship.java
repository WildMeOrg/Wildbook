package org.ecocean.social;

import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.*;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

/**
 * Representing a dyadic social relationship
 */
public class Relationship implements java.io.Serializable{

  //default JDO constructor
  public Relationship(){}
  
  public Relationship(String type, String relatedCommunityName, MarkedIndividual individual1, MarkedIndividual individual2, String individualRole1, String individualRole2){
    this.type=type;
    this.relatedSocialUnitName=relatedCommunityName;
    this.markedIndividualName1=individual1.getIndividualID();
    this.markedIndividualName2=individual2.getIndividualID();
    this.individual1=individual1;
    this.individual2=individual2;
    this.markedIndividualRole1=individualRole1;
    this.markedIndividualRole2=individualRole2;
  }
  
  public Relationship(String type, MarkedIndividual individual1, MarkedIndividual individual2, String individualRole1, String individualRole2){
    this.type=type;
    this.markedIndividualName1=individual1.getIndividualID();
    this.markedIndividualName2=individual2.getIndividualID();
    this.individual1=individual1;
    this.individual2=individual2;
    this.markedIndividualRole1=individualRole1;
    this.markedIndividualRole2=individualRole2;
  }
  
  public Relationship(String type, MarkedIndividual individual1, MarkedIndividual individual2){
    this.type=type;
    this.markedIndividualName1=individual1.getIndividualID();
    this.markedIndividualName2=individual2.getIndividualID();
    this.individual1=individual1;
    this.individual2=individual2;
  }

  private static final long serialVersionUID = 6688796543218832687L;

  private String relatedSocialUnitName;
  
  private String markedIndividualName1;
  private MarkedIndividual individual1;
  private String markedIndividualRole1;
  private String markedIndividual1DirectionalDescriptor;

  private String markedIndividualName2;
  private MarkedIndividual individual2;
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
  
  public MarkedIndividual getMarkedIndividual1() {return individual1;}
  public MarkedIndividual getMarkedIndividual2() {return individual2;}
  
  public void setIndividual1(MarkedIndividual indy) {
    this.individual1=indy;
  }
  public void setIndividual2(MarkedIndividual indy) {
    this.individual2=indy;
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
  
  public JSONObject decorateJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
    if(individual1!=null)jobj.put("individual1", this.individual1.uiJson(request, false));
    if(individual1!=null)jobj.put("individual2", this.individual2.uiJson(request, false));
    return jobj;
  }
  
  public JSONObject uiJson(HttpServletRequest request) throws JSONException {
    JSONObject jobj = new JSONObject();
    if(this.getMarkedIndividualName1()!=null)jobj.put("markedIndividualName1", this.getMarkedIndividualName1());
    if(this.getMarkedIndividualName2()!=null)jobj.put("markedIndividualName2", this.getMarkedIndividualName2());
    if(this.getType()!=null)jobj.put("type", this.getType());
    if(this.getMarkedIndividualRole1()!=null)jobj.put("markedIndividualRole1", this.getMarkedIndividualRole1());
    if(this.getMarkedIndividualRole2()!=null)jobj.put("markedIndividualRole2", this.getMarkedIndividualRole2());
    if(this.getBidirectional()!=null)jobj.put("bidirectional", this.getBidirectional());
    if(this.getRelatedComments()!=null)jobj.put("relatedComments", this.getRelatedComments());
    jobj.put("startTime", this.getStartTime());
    jobj.put("endTime", this.getEndTime());
    if(this.getRelatedSocialUnitName()!=null)jobj.put("relatedSocialUnitName", this.getRelatedSocialUnitName());
    
    
    return sanitizeJson(request, decorateJson(request,decorateJson(request, jobj)));
  }
  
  public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
    

    if(getMarkedIndividual1()!=null && !getMarkedIndividual1().canUserAccess(request))jobj.remove("individual1");
    if(getMarkedIndividual2()!=null && !getMarkedIndividual2().canUserAccess(request))jobj.remove("individual2");

    jobj.put("_sanitized", true);

    return jobj;
}
  
  
  
}
