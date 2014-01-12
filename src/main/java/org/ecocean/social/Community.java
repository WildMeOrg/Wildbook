package org.ecocean.social;

import org.ecocean.*;
import java.util.ArrayList;

public class Community implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 3996955430559204532L;

  private String communityName;
  
  //default constructor for JDO
  public Community(){}
  
  public Community(String name){
    this.communityName=name;
  }
  

  //this is a convenience method to get the MarkedIndividuals associated with this community via its Relationship objects
  public ArrayList<MarkedIndividual> getMarkedIndividuals(Shepherd myShepherd){
      return myShepherd.getAllMarkedIndividualsInCommunity(communityName);
  }
  
  public String getCommunityName(){return communityName;}
  public void setCommunityName(String name){communityName=name;}
  
  
}
