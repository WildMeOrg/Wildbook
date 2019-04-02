package org.ecocean;

import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.io.Serializable;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.servlet.ServletUtilities;
import org.joda.time.DateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;

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
  private String hashedEmailAddress;
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
	private String uuid;
	
	//String currentContext;
  	
  	//String currentContext;
  	
  	
    private List<Organization> organizations = null;

  	private boolean acceptedUserAgreement=false;
  
  private boolean receiveEmails=true; 

  // turning this off means the user is greedy and mean: they never share data and nobody ever shares with them
  private Boolean sharing=true;

	private HashMap<String,String> social;
  	
  	//JDOQL required empty instantiator
  	public User(){}
  	
  	public User(String fullName, String emailAddress, String physicalAddress, String phoneNumber, String affiliation, String notes) {
  	  uuid=Util.generateUUID();
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
  	  uuid=Util.generateUUID();
  	  setUsername(username);
  	  setPassword(password);
  	  setSalt(salt);
			setReceiveEmails(false);
  	  RefreshDate();
  	  this.lastLogin=-1;
  	}
  	
    public User(String email,String uuid){
      this.uuid=uuid;
      setEmailAddress(email);
      setReceiveEmails(true);
      String salt=ServletUtilities.getSalt().toHex();
      String pass=Util.generateUUID();
      String hashedPassword=ServletUtilities.hashAndSaltPassword(pass, salt);
      setPassword(hashedPassword);
      RefreshDate();
      this.lastLogin=-1;
    }
    
    public User(String uuid){
      this.uuid=uuid;
      setReceiveEmails(false);
      String salt=ServletUtilities.getSalt().toHex();
      String pass=Util.generateUUID();
      String hashedPassword=ServletUtilities.hashAndSaltPassword(pass, salt);
      setPassword(hashedPassword);
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
  public String getHashedEmailAddress ()
  {
    return this.hashedEmailAddress;
  }
  public void setEmailAddress (String emailAddress){
    if(emailAddress!=null){
      this.emailAddress = emailAddress;
      this.hashedEmailAddress = generateEmailHash(emailAddress);
    }
    else{
      this.emailAddress=null;
      //NOTE: we intentionally do NOT null the hashed email address. the hash is a reflection that someone was there, allowing us to count users even if we acknowledge a right-to-forget (GDPR) and remove the email address itself
    }
    RefreshDate();
  }

    public static String generateEmailHash(String addr) {
        if ((addr == null) || (addr.trim().equals(""))) return null;
        return ServletUtilities.hashString(addr.trim().toLowerCase());
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
  public boolean hasAffiliation (String affiliation) {
    return (this.affiliation!=null && affiliation!=null && this.affiliation.toLowerCase().indexOf(affiliation.toLowerCase())>=0);
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

  public boolean hasSharing() {
    // if you haven't specified a sharing policy YOU'RE SHARING
    if (sharing==null) return true;
    return sharing;
  }
  public void setSharing(boolean sharing) {
    this.sharing = sharing;
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
		
    public String getUUID() {return uuid;}
    public String getId() { return uuid; }  //adding this "synonym"(?) for consistency

    public boolean hasRoleByName(String name, Shepherd myShepherd) {
        if (name == null) return false;
        List<Role> roles = myShepherd.getAllRolesForUserInContext(this.username, myShepherd.getContext());
        if (roles == null) return false;
        for (Role r : roles) {
            if (r.getRolename().equals(name)) return true;
        }
        return false;
    }

    //some glorious day this would be better to recurse thru some Organization Objects to get keys.  sigh, to dream.
    public Set<String> getMultiValueKeys() {
        Set<String> rtn = new HashSet<String>();
        rtn.add("_userId_:" + uuid);  //kinda like "private" key?
/*  these should migrate to Organizations!!
        if (Util.stringExists(userProject)) rtn.add("_userProject_:" + userProject.toLowerCase());
        if (Util.stringExists(affiliation)) rtn.add("_affiliation_:" + affiliation.toLowerCase());
*/
        //if the best context we have is a user, we add all the (toplevel) groups they are members of
        if (organizations != null) {
        }
        return rtn;
    }

    public List<Organization> getOrganizations() {
        return organizations;
    }
    public void setOrganizations(List<Organization> orgs) {
        organizations = orgs;
        this.organizationsReciprocate(orgs);
    }
    public void addOrganization(Organization org) {
        if (org == null) return;
        if (organizations == null) organizations = new ArrayList<Organization>();
        if (!organizations.contains(org)) organizations.add(org);
        this.organizationsReciprocate(org);
    }
    public void removeOrganization(Organization org) {
        if ((org == null) || (organizations == null)) return;
        if (org.getMembers() == null) return;
        org.getMembers().remove(this);
        org.updateModified();
        organizations.remove(org);
    }
    //see also isMemberOfDeep()
    public boolean isMemberOf(Organization org) {
        if (org == null) return false;
        return org.hasMember(this);
    }
    public boolean isMemberOfDeep(Organization org) {
        if (org == null) return false;
        return org.hasMemberDeep(this);
    }
    //this is to handle the bidirectional dn madness when *adding* orgs
    //  (removing are handled internally above)
    private void organizationsReciprocate(List<Organization> orgs) {
        if (orgs == null) return;
        for (Organization org : orgs) {
            if ((org.getMembers() != null) && !org.getMembers().contains(this)) org.getMembers().add(this);
        }
    }
    private void organizationsReciprocate(Organization org) {  //single version for convenience
        if (org == null) return;
        List<Organization> orgs = new ArrayList<Organization>();
        orgs.add(org);
        organizationsReciprocate(orgs);
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("uuid", uuid)
                .append("username", username)
                .append("fullName", fullName)
                .toString();
    }

}
