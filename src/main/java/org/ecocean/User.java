package org.ecocean;

import java.util.Date;

/**
 * <code>User</code> stores information about a contact/user.
 * Examples: photographer, submitter
 * @author Ed Stastny
 */
public class User {
  // The user's full name
  private String fullName; 
  //Primary email address
  private String emailAddress; 
  // User's snail-mail address/location
  private String physicalAddress; 
  //Primary phone number
  private String phoneNumber; 
  //Organization or project affiliation
  private String affiliation; 
  //Misc. information about this user
  private String notes; 
  //Date of last update of this record, in ms
  private long dateInMilliseconds; 
  
  public User(String fullName, String emailAddress, String physicalAddress, String phoneNumber, String affiliation, String notes) {
    setFullName(fullName);
    setEmailAddress(emailAddress);
    setPhysicalAddress(physicalAddress);
    setPhoneNumber(phoneNumber);
    setAffiliation(affiliation);
    setNotes(notes);
  }
  
  public void RefreshDate() 
  {
    this.dateInMilliseconds = new Date().getTime();
  }
  
  public String getFullName() 
  {
    return this.fullName;
  }
  public void setFullName (String fullName)
  {
    this.fullName = fullName;
    RefreshDate();
  }
  
  public String getEmailAddress ()
  {
    return this.emailAddress;
  }
  public void setEmailAddress (String emailAddress)
  {
    this.emailAddress = emailAddress;
    RefreshDate();
  }
  
  public String getPhysicalAddress () 
  {
    return this.physicalAddress;
  }
  public void setPhysicalAddress (String physicalAddress)
  {
    this.physicalAddress = physicalAddress;
    RefreshDate();
  }
  
  public String getPhoneNumber ()
  {
    return this.phoneNumber;
  }
  public void setPhoneNumber (String phoneNumber)
  {
    this.phoneNumber = phoneNumber;
    RefreshDate();
  }
  
  public String getAffiliation () 
  {
    return this.affiliation;
  }
  public void setAffiliation (String affiliation)
  {
    this.affiliation = affiliation;
    RefreshDate();
  }
  
  public String getNotes ()
  {
    return this.notes;
  }
  public void setNotes (String notes)
  {
    this.notes = notes; 
    RefreshDate();
  }
  
  public long getDateInMilliseconds ()
  {
    return this.dateInMilliseconds;
  }
}
