package org.ecocean.social;

import org.ecocean.MarkedIndividual;

public class SocialGroupMember implements java.io.Serializable {


  private static final long serialVersionUID = -5506628698161111975L;
  private MarkedIndividual individualMember;
  private long entryDate=0;
  private long exitDate=0;
  private String currentSocialRole;
  private String researcherNotes;
  private String individualID;
  
  
  //CTORS
  public SocialGroupMember(){}
  public SocialGroupMember(MarkedIndividual indie){
    this.individualMember=indie;
    this.individualID=indie.getIndividualID();
  }
  
  public MarkedIndividual getIndividualMember(){return individualMember;}
  public void setIndividualMember(MarkedIndividual indie){this.individualMember=indie;}
  
  public String getCurrentSocialRole(){return currentSocialRole;}
  public void setCurrentSocialRole(String role){this.currentSocialRole=role;}
  
  public String getResearcherNotes(){return researcherNotes;}
  public void addResearcherNotes(String newComments) {
    if (researcherNotes != null) {
      researcherNotes += newComments;
    } else {
      researcherNotes = newComments;
    }
  }
  
  public long getEntryDate(){return entryDate;}
  public void setEntryDate(long newDate){entryDate=newDate;}
  
  public long getExitDate(){return exitDate;}
  public void setExitDate(long newDate){exitDate=newDate;}
  
  public String getIndividualID(){return individualID;}
  
}
