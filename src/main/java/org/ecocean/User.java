package org.ecocean;

import java.util.Date;
import java.util.HashMap;
import java.io.Serializable;
import org.ecocean.SinglePhotoVideo;

import org.joda.time.DateTime;

/**
 * <code>User</code> stores information about a contact/user.
 * Examples: photographer, submitter
 * @author Ed Stastny
 */
public class User implements Serializable {
  
  
  private static final long serialVersionUID = -1261710718629763048L;
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
  
  private String userProject;
  private String userStatement;
  private String userURL;
  private SinglePhotoVideo userImage;
  
  //Misc. information about this user
  private String notes;
  //Date of last update of this record, in ms
  private long dateInMilliseconds;
  private long userID;

  private long lastLogin=-1;
  
  	private String username;
  	private String password ;
  	private String salt;
  	
  	//String currentContext;
  	
  	
  	private boolean acceptedUserAgreement=false;
  
  private boolean receiveEmails=true; 

	private HashMap<String,String> social;
  	
  	//JDOQL required empty instantiator
  	public User(){}
  	
  	public User(String fullName, String emailAddress, String physicalAddress, String phoneNumber, String affiliation, String notes) {
  	  setFullName(fullName);
  	  setEmailAddress(emailAddress);
  	  setPhysicalAddress(physicalAddress);
  	  setPhoneNumber(phoneNumber);
  	  setAffiliation(affiliation);
  	  setNotes(notes);
  	  RefreshDate();
  	  this.lastLogin=-1;
  	}
  	
  	public User(String username,String password, String salt){
  	  setUsername(username);
  	  setPassword(password);
  	  setSalt(salt);
			setReceiveEmails(true);
  	  RefreshDate();
  	  this.lastLogin=-1;
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
    if(fullName!=null){
      this.fullName = fullName;
    }
    else{
      this.fullName=null;
    }
    RefreshDate();
  }

  public String getEmailAddress ()
  {
    return this.emailAddress;
  }
  public void setEmailAddress (String emailAddress){
    if(emailAddress!=null){
      this.emailAddress = emailAddress;
    }
    else{this.emailAddress=null;}
    RefreshDate();
  }

  public String getPhysicalAddress ()
  {
    return this.physicalAddress;
  }
  public void setPhysicalAddress (String physicalAddress)
  {
    
    if(physicalAddress!=null){this.physicalAddress = physicalAddress;}
    else{this.physicalAddress=null;}
    RefreshDate();
  }

  public String getPhoneNumber ()
  {
    return this.phoneNumber;
  }
  public void setPhoneNumber (String phoneNumber)
  {
    if(phoneNumber!=null){this.phoneNumber = phoneNumber;}
    else{this.phoneNumber=null;}
    RefreshDate();
  }

  public String getAffiliation ()
  {
    return this.affiliation;
  }
  public void setAffiliation (String affiliation)
  {
    if(affiliation!=null){
      this.affiliation = affiliation;
    }
    else{this.affiliation=null;}
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




  	public long getUserID() {
  		return userID;
  	}
  	public void setUserID(long userID) {
  		this.userID = userID;
  	}
  	public String getUsername() {
  		return username;
  	}
  	public void setUsername(String username) {
  		this.username = username;
  	}
  	public String getPassword() {
  		return password;
  	}
  	public void setPassword(String password) {
  		this.password = password;
  	}
  	
  	public void setSalt(String salt){this.salt=salt;}
  	public String getSalt(){return salt;}


    public void setUserProject(String newProj) {
      if(newProj!=null){userProject = newProj;}
    else{userProject=null;}
    }
    public String getUserProject(){return userProject;}
    
    public void setUserStatement(String newState) {
      if(newState!=null){userStatement = newState;}
    else{userStatement=null;}
    }
    public String getUserStatement(){return userStatement;}
    
    public SinglePhotoVideo getUserImage(){return userImage;}
    

    public void setUserImage(SinglePhotoVideo newImage) {
      if(newImage!=null){userImage = newImage;}
    else{userImage=null;}
    }
    
    public void setUserURL(String newURL) {
      if(newURL!=null){userURL = newURL;}
    else{userURL=null;}
    }
    public String getUserURL(){return userURL;}
  	
    public long getLastLogin(){
      return lastLogin;
    }
    
    public String getLastLoginAsDateString(){
      if(lastLogin==-1) return null;
      return (new DateTime(this.lastLogin)).toString();
    }
    
    public void setLastLogin(long lastie){this.lastLogin=lastie;}
    

    public boolean getReceiveEmails(){return receiveEmails;}
    public void setReceiveEmails(boolean receive){this.receiveEmails=receive;}
    
    

    public boolean getAcceptedUserAgreement(){return acceptedUserAgreement;}
    
    public void setAcceptedUserAgreement(boolean accept){this.acceptedUserAgreement=accept;}


		public String getSocial(String type) {
			if (social == null) return null;
			return social.get(type);
		}
		public void setSocial(String type, String s) {
        if ((s == null) || s.equals("")) {
            unsetSocial(type);
            return;
        }
        if (social == null) social = new HashMap<String,String>();
        social.put(type, s);
		}
		public void setSocial(String type) {
			unsetSocial(type);
		}
		public void unsetSocial(String type) {
			if (social == null) return;
			social.remove(type);
		}


		//TODO this needs to be dealt with better.  see: rant about saving usernames from forms
		public static boolean isUsernameAnonymous(String uname) {
			return ((uname == null) || uname.equals("") || uname.equals("N/A"));
		}

    //public String getCurrentContext(){return currentContext;}
    //public void setCurrentContext(String newContext){currentContext=newContext;}

}
