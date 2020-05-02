package org.ecocean;

import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import java.io.Serializable;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.servlet.ServletUtilities;
import org.joda.time.DateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;

import javax.servlet.http.HttpServletRequest;

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

    public String getDisplayName() {
        if (fullName != null) return fullName;
        if (username != null) return username;
        return uuid;
    }

  // this is handy for UI: does this user (belong to an organization that) use a custom .properties file?
  public boolean hasCustomProperties() {
    return ShepherdProperties.userHasOverrideString(this);
  }
  public static boolean hasCustomProperties(HttpServletRequest request, Shepherd myShepherd) {
    if (request == null) return false;
    Shepherd readOnlyShep = Shepherd.newActiveShepherd(request, "hasCustomProperties");
    boolean ans = hasCustomProperties(readOnlyShep, request);
    readOnlyShep.rollbackAndClose();
    return ans;
  }
  public static boolean hasCustomProperties(Shepherd myShepherd, HttpServletRequest request) {
    if (request == null) return false;
    String manualOrg = request.getParameter("organization");
    if (Util.stringExists(manualOrg)) {
      if (ShepherdProperties.orgHasOverwrite(manualOrg)) return true;
    }
    User user = myShepherd.getUser(request);
    if (user == null) return false;
    return user.hasCustomProperties();
  }
  
  public static boolean hasCustomProperties(HttpServletRequest request) {
     Shepherd myShepherd = new Shepherd(request);
     myShepherd.setAction("User.hasCustomProperties");
     myShepherd.beginDBTransaction();
     boolean hasIt=hasCustomProperties(request, myShepherd);
     myShepherd.rollbackDBTransaction();
     myShepherd.closeDBTransaction();
     return hasIt;
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
    // Use this method to find out what organization-wide nameKey a user would want, to use to generate new individual names.
    public String getIndividualNameKey() {
        for (Organization org: organizations) {
          if (Util.stringExists(org.getIndividualNameKey())) return org.getIndividualNameKey();
        }
        return null;
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

    //basically mean uuid-equivalent, so deal
    public boolean equals(final Object u2) {
        if (u2 == null) return false;
        if (!(u2 instanceof User)) return false;
        User two = (User)u2;
        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
        return this.uuid.equals(two.getUUID());
    }
    public int hashCode() {  //we need this along with equals() for collections methods (contains etc) to work!!
        if (uuid == null) return Util.generateUUID().hashCode();  //random(ish) so we dont get two users with no uuid equals! :/
        return uuid.hashCode();
    }





    public String toString() {
        return new ToStringBuilder(this)
                .append("uuid", uuid)
                .append("username", username)
                .append("fullName", fullName)
                .toString();
    }
    
    // Returns a somewhat rest-like JSON object containing the metadata
    public JSONObject uiJson(HttpServletRequest request, boolean includeOrganizations) throws JSONException {
      JSONObject jobj = new JSONObject();
      jobj.put("uuid", this.getId());
      jobj.put("emailAddress", this.getEmailAddress());
      jobj.put("fullName", this.getFullName());
      jobj.put("affiliation", this.getAffiliation());
      jobj.put("lastLogin", Long.toString(this.getLastLogin()));
      jobj.put("username", this.getUsername());

      if(includeOrganizations) {
        Vector<String> orgIDs = new Vector<String>();
        for (Organization org : this.organizations) {
          orgIDs.add(org.toJSONObject().toString());
        }
        jobj.put("organizations", orgIDs.toArray());
      }
      return jobj;
    }

    public org.json.JSONObject toApiJSONObject(Map<String,Object> opts) {
        org.json.JSONObject u = new org.json.JSONObject();
        u.put("uuid", uuid);
        u.put("fullName", fullName);
        u.put("username", username);
        return u;
    }

//this tomfoolery is just to still support legacy api.  :(  TEMPORARY  FIXME
// plus SUPER-hackery to provide mockup-profile photos!
	public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
            Shepherd myShepherd = new Shepherd(ServletUtilities.getContext(request));
            int r = (new java.util.Random()).nextInt(8);  //only 0-4 are images we want, the rest are to generate no profile photo
            MediaAsset ma = null;
            if (r < 5) ma = MediaAssetFactory.load(r + 81926, myShepherd);
            if (ma != null) {
                jobj.put("profileAsset", ma.sanitizeJson(request, new JSONObject()));
            }
            jobj.remove("salt");
            jobj.remove("password");
            jobj.remove("lastLogin");
            jobj.remove("emailAddress");
            jobj.remove("hashedEmailAddress");
            jobj.remove("userID");
            jobj.remove("dateInMilliseconds");
            jobj.remove("receiveEmails");
            return jobj;
        }
}
