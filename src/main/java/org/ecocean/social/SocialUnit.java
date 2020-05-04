package org.ecocean.social;

import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.*;
import org.ecocean.security.Collaboration;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

public class SocialUnit implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 3996955430559204532L;

  private String socialUnitName;

  private List<Membership> members = new ArrayList<>();
  
  //default constructor for JDO
  public SocialUnit(){}
  
  public SocialUnit(String name){
    this.socialUnitName=name;
  }
  
  public List<MarkedIndividual> getMarkedIndividuals(){
      List<MarkedIndividual> mis = new ArrayList<>();
      for (Membership member : members) {
        mis.add(member.getMarkedIndividual());
      }
      return mis;
  }

  // preserve calls to old method that required shepherd
  public List<MarkedIndividual> getMarkedIndividuals(Shepherd myShepherd) {
    return getMarkedIndividuals();
  }

  public List<Membership> getAllMembers() {
    return members;
  }

  public void addMember(Membership mem) {
    if (!members.contains(mem)) {
      members.add(mem);
    }
  }

  public boolean hasMarkedIndividualAsMember(MarkedIndividual queryMi) {
    for (MarkedIndividual targetMi : getMarkedIndividuals()) {
      if (targetMi.getId().equals(queryMi.getId())) return true; 
    }
    return false;
  }

  public Membership getMembershipForMarkedIndividual(MarkedIndividual mi) {
    for (Membership member : members) {
      if (member.getMarkedIndividual().getId().equals(mi.getId())) {
        return member;
      }
    }
    return null;
  }

  public boolean removeMember(MarkedIndividual mi, Shepherd myShepherd) {
    if (hasMarkedIndividualAsMember(mi)) {
      Membership toRemove = getMembershipForMarkedIndividual(mi);
      myShepherd.beginDBTransaction();
      members.remove(toRemove);
      myShepherd.throwAwayMembership(toRemove);
      myShepherd.commitDBTransaction();
      return true;
    }
    return false;
  }
  
  public String getSocialUnitName(){return socialUnitName;}
  public void setSocialUnitName(String name){socialUnitName=name;}
  

  //convenience function to Collaboration permissions
  public boolean canUserAccess(HttpServletRequest request) {
    return Collaboration.canUserAccessSocialUnit(this, request);
  }
  
  public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
            jobj.put("socialUnitName", this.getSocialUnitName());
            if (this.canUserAccess(request)) return jobj;
            jobj.remove("members");
            jobj.put("_sanitized", true);
            return jobj;
        }
  

  
  public JSONObject decorateJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
    if (!this.canUserAccess(request)) return jobj;
    
    return jobj;
  }

  
//Returns a somewhat rest-like JSON object containing the metadata
 public JSONObject uiJson(HttpServletRequest request) throws JSONException {
   return uiJson(request, true);
 }
  // Returns a somewhat rest-like JSON object containing the metadata
  public JSONObject uiJson(HttpServletRequest request, boolean includeMembers) throws JSONException {
    JSONObject jobj = new JSONObject();
    jobj.put("socialUnitName", this.getSocialUnitName());
    if(includeMembers) {
      JSONArray arr=new JSONArray();
      
      for (Membership member : members) {
        arr.put(member.uiJson(request));
      }
      
      jobj.put("members", arr);
    }
    return sanitizeJson(request,decorateJson(request, jobj));
  }
  

  
}
