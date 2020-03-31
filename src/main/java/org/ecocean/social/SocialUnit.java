package org.ecocean.social;

import org.ecocean.*;

import java.util.ArrayList;
import java.util.List;

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
  
  //this is a convenience method to get the MarkedIndividuals associated with this community via its Relationship objects
  public List<MarkedIndividual> getMarkedIndividuals(){
      //return myShepherd.getAllMarkedIndividualsInCommunity(socialUnitName);
      List<MarkedIndividual> mis = new ArrayList<>();
      for (Membership member : members) {
        mis.add(member.getMarkedIndividual());
      }
      return mis;
  }

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
  

  
}
