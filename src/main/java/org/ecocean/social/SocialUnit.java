package org.ecocean.social;

import org.ecocean.*;
import java.util.List;

public class SocialUnit implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 3996955430559204532L;

  private String socialUnitName;
  
  //default constructor for JDO
  public SocialUnit(){}
  
  public SocialUnit(String name){
    this.socialUnitName=name;
  }
  

  //this is a convenience method to get the MarkedIndividuals associated with this community via its Relationship objects
  public List<MarkedIndividual> getMarkedIndividuals(Shepherd myShepherd){
      return myShepherd.getAllMarkedIndividualsInCommunity(socialUnitName);
  }
  
  public String getSocialUnitName(){return socialUnitName;}
  public void setSocialUnitName(String name){socialUnitName=name;}
  
  
}
