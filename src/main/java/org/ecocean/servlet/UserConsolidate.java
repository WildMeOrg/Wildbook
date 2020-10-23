/**
 * Manually consolidates user encounter information on the basis of a provided username (the one that will have ownship transferred to it`)
 *
 * @author mfisher
 */

 package org.ecocean.servlet;
 import org.ecocean.servlet.ServletUtilities;

 import org.ecocean.*;

 import com.oreilly.servlet.multipart.FilePart;
 import com.oreilly.servlet.multipart.MultipartParser;
 import com.oreilly.servlet.multipart.ParamPart;
 import com.oreilly.servlet.multipart.Part;
 import javax.servlet.ServletConfig;
 import javax.servlet.ServletException;
 import javax.servlet.http.HttpServlet;
 import javax.servlet.http.HttpServletRequest;
 import javax.servlet.http.HttpServletResponse;
 import java.util.*;
 import java.io.File;
 import java.io.IOException;
 import java.io.PrintWriter;
 import java.util.Properties;
 import org.ecocean.*;
 import org.ecocean.servlet.*;
 // import javax.jdo.Query;
 import javax.jdo.*;
 import org.json.JSONArray;
 import org.json.JSONObject;
 import org.json.JSONException;

public class UserConsolidate extends HttpServlet {

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public static void consolidateUser(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    System.out.println("consolidateUser entered");
    List<Encounter> photographerEncounters=getPhotographerEncountersForUser(myShepherd.getPM(),userToBeConsolidated);
    if(photographerEncounters!=null && photographerEncounters.size()>0){
      for(int j=0; j<photographerEncounters.size(); j++){
        Encounter currentEncounter=photographerEncounters.get(j);
        consolidatePhotographers(myShepherd, currentEncounter, userToRetain, userToBeConsolidated);
      }
    }
    List<Encounter> submitterEncounters=getSubmitterEncountersForUser(myShepherd.getPM(),userToBeConsolidated);
    if(submitterEncounters!=null && submitterEncounters.size()>0){
      for(int k=0;k<submitterEncounters.size();k++){
        Encounter currentEncounter=submitterEncounters.get(k);
        consolidateEncounterSubmitters(myShepherd, currentEncounter, userToRetain, userToBeConsolidated);
        consolidateMainEncounterSubmitterId(myShepherd, currentEncounter, userToRetain, userToBeConsolidated);
      }
    }
    List<Occurrence> submitterOccurrences = getSubmitterOccurrencesForUser(myShepherd.getPM(), userToBeConsolidated);
    if(submitterOccurrences!=null && submitterOccurrences.size()>0){
      for(int j=0;j<submitterOccurrences.size(); j++){
        Occurrence currentOccurrence = submitterOccurrences.get(j);
        consolidateOccurrenceSubmitters(myShepherd, currentOccurrence, userToRetain, userToBeConsolidated);
      }
    }
    myShepherd.getPM().deletePersistent(userToBeConsolidated);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    System.out.println("consolidateUser exiting");
  }

  public static int consolidateUsersAndNameless(Shepherd myShepherd,User useMe,List<User> dupes){
    PersistenceManager persistenceManager = myShepherd.getPM();
    System.out.println("consolidate entered");
  	dupes.remove(useMe);
  	int numDupes=dupes.size();
    System.out.println(numDupes + " duplicates found for user: " + useMe.getUsername());

  	for(int i=0;i<dupes.size();i++){
  		User currentDupe=dupes.get(i);
  		List<Encounter> photographerEncounters=getPhotographerEncountersForUser(persistenceManager,currentDupe);
      if(photographerEncounters!=null && photographerEncounters.size()>0){
        System.out.println(photographerEncounters.size()+" photographer encounters found for user: " + currentDupe.getUsername());
        for(int j=0;j<photographerEncounters.size();j++){
          Encounter currentEncounter=photographerEncounters.get(j);
          consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupe);
        }
      }
  		List<Encounter> submitterEncounters= getSubmitterEncountersForUser(persistenceManager,currentDupe);
      if(submitterEncounters!=null && submitterEncounters.size()>0){
        System.out.println(submitterEncounters.size()+" submitter encounters found for user: " + currentDupe.getUsername());
        for(int j=0;j<submitterEncounters.size();j++){
          Encounter currentEncounter=submitterEncounters.get(j);
          consolidateEncounterSubmitters(myShepherd, currentEncounter, useMe, currentDupe);
          consolidateMainEncounterSubmitterId(myShepherd, currentEncounter, useMe, currentDupe);
        }
      }
      //TODO assign usernameless encounters to public maybe using the below, and maybe not
      // List<Encounter> usernameLessEncounters= getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress(myShepherd,currentDupe);
  		// for(int j=0;j<usernameLessEncounters.size();j++){
  		// 	Encounter currentEncounter=usernameLessEncounters.get(j);
      //   // System.out.println("usernameless encounter with catalog number: " + currentEncounter.getCatalogNumber() + " by " + currentEncounter.getSubmitterEmail() + " with username: " + currentEncounter.getSubmitterID());
      //   consolidateUsernameless(myShepherd, currentEncounter, useMe, currentDupe);
  		// }

      List<Occurrence> submitterOccurrences = getSubmitterOccurrencesForUser(persistenceManager, currentDupe);
      if(submitterOccurrences!=null && submitterOccurrences.size()>0){
        for(int j=0;j<submitterOccurrences.size(); j++){
          Occurrence currentOccurrence = submitterOccurrences.get(j);
          consolidateOccurrenceSubmitters(myShepherd, currentOccurrence, useMe, currentDupe);
        }
      }

  		dupes.remove(currentDupe); //TODO comment in when you are ready
  		myShepherd.getPM().deletePersistent(currentDupe); //TODO comment in when you are ready
  		myShepherd.commitDBTransaction(); //TODO comment in when you are ready
  		myShepherd.beginDBTransaction(); //TODO comment in when you are ready
  		i--;
  	}
  	return numDupes;
  }

  public static void consolidateOccurrenceSubmitters(Shepherd myShepherd, Occurrence currentOccurrence, User useMe, User currentDupe){
    String currentOccurrenceSubmitter = currentOccurrence.getSubmitterID();
    if(Util.stringExists(currentOccurrenceSubmitter) && currentDupe.getUsername().equals(currentOccurrenceSubmitter)){
      if(Util.stringExists(useMe.getUsername())){
        currentOccurrence.setSubmitterID(useMe.getUsername());
      }
    }
  }

  public void manualConsolidateByUsername(Shepherd myShepherd, String userNameOfDesiredUseMe){
    PersistenceManager persistenceManager = myShepherd.getPM();
    System.out.println("manualConsolidateByUsername entered! Username is " + userNameOfDesiredUseMe);
    List<User> potentialUsers = getUsersByUsername(persistenceManager, userNameOfDesiredUseMe);
    if(potentialUsers.size() == 1){
      System.out.println("Heyo just one user has this username!");
      User useMe = potentialUsers.get(0);
      String hashedEmail = useMe.getHashedEmailAddress();
      System.out.println("hashedEmail in manualConsolidate is " + hashedEmail);
      List<User> dupesToBeSubsumed =getUsersByHashedEmailAddress(persistenceManager,useMe.getHashedEmailAddress());
      dupesToBeSubsumed.remove(useMe);
      int numDupes=dupesToBeSubsumed.size();
      for(int i=0;i<dupesToBeSubsumed.size();i++){
        User currentDupeUser=dupesToBeSubsumed.get(i);
        consolidateUser(myShepherd, useMe, currentDupeUser);
        dupesToBeSubsumed.remove(currentDupeUser);
        i--; //TODO really?
      }
    } else{
      System.out.println("More (or fewer) than one user has that username.....aborting");
      for(int j =0; j<potentialUsers.size(); j++){
        System.out.println(potentialUsers.get(j).toString());
      }
    }
  }

  public static List<String> getEmailAddressesOfUsersWithMoreThanOneAccountAssociatedWithEmailAddress(List<User> allUsers, PersistenceManager persistenceManager){
    List<String> targetEmails = new ArrayList<String>();
    if(allUsers.size()>0){
      for(int i=0; i<allUsers.size(); i++){
        List<User> dupesForUser = getUsersByHashedEmailAddress(persistenceManager,allUsers.get(i).getHashedEmailAddress());
        if(dupesForUser.size()>1){
          if(!targetEmails.contains(allUsers.get(i).getEmailAddress().toLowerCase().trim())){
            targetEmails.add(allUsers.get(i).getEmailAddress().toLowerCase().trim());
          }
        }
      }
    }
    return targetEmails;
  }

  public static List<String> getEmailAddressesOfUsersWithMoreThanOneAccountAssociatedWithEmailAddressPreserveCaps(List<User> allUsers, List<String> processedEmailAddressesToCompareAgainst, PersistenceManager persistenceManager){
    System.out.println("getEmailAddressesOfUsersWithMoreThanOneAccountAssociatedWithEmailAddressPreserveCaps entered");
    System.out.println("processedEmailAddressesToCompareAgainst has " + processedEmailAddressesToCompareAgainst.size() + " entries");
    List<String> targetEmails = new ArrayList<String>();
    if(allUsers.size()>0){
      for(int i=0; i<allUsers.size(); i++){
        List<User> dupesForUser = getUsersByHashedEmailAddress(persistenceManager,allUsers.get(i).getHashedEmailAddress());
        if(dupesForUser.size()>1){
          // System.out.println("got here");
          if(processedEmailAddressesToCompareAgainst.contains(allUsers.get(i).getEmailAddress().toLowerCase().trim()) && !targetEmails.contains(allUsers.get(i).getEmailAddress())){
            System.out.println("satisfied this if clause");
            targetEmails.add(allUsers.get(i).getEmailAddress());
          }
        }
      }
    }
    return targetEmails;
  }

  public static List<User> getSimilarUsers(User user, PersistenceManager persistenceManager){
    System.out.println("getSimilarUsers entered");
    System.out.println(user.toString());
    String baseQueryString="SELECT * FROM \"USERS\" WHERE ";
    String fullNameFilter = "\"FULLNAME\" ilike '%" + user.getFullName() + "%'";
    String emailFilter = "\"EMAILADDRESS\" ilike '%" + user.getEmailAddress() + "%'";
    String userNameFilter = "\"USERNAME\" ilike '%" + user.getUsername() + "%'";
    Boolean fullNameExists = user.getFullName() != null && !user.getFullName().equals("");
    Boolean emailExists = user.getEmailAddress() != null && !user.getEmailAddress().equals("");
    Boolean userNameExists = user.getUsername() != null && !user.getUsername().equals("");
    String combinedQuery = baseQueryString;
    if(fullNameExists && emailExists && userNameExists){
      combinedQuery = combinedQuery + fullNameFilter + " OR " + emailFilter + " OR " + userNameFilter;
    }
    if(fullNameExists && userNameExists && !emailExists){
      combinedQuery = combinedQuery + fullNameFilter + " OR " + userNameFilter;
    }
    if(fullNameExists && emailExists && !userNameExists){
      combinedQuery = combinedQuery + fullNameFilter + " OR " + emailFilter;
    }
    if(emailExists && userNameExists && !fullNameExists){
      combinedQuery = combinedQuery + emailFilter + " OR " + userNameFilter;
    }
    if(fullNameExists && !userNameExists && !emailExists){
      combinedQuery = combinedQuery + fullNameFilter;
    }
    if(emailExists && !userNameExists && !fullNameExists){
      combinedQuery = combinedQuery + emailFilter;
    }
    if(userNameExists && !emailExists && !fullNameExists){
      combinedQuery = combinedQuery + userNameFilter;
    }
    System.out.println("combinedQuery is: " + combinedQuery);
  	List<User> similarUsers=new ArrayList<User>();
    Query query = persistenceManager.newQuery("javax.jdo.query.SQL", combinedQuery);
    query.setClass(User.class);
    List<User> tmp = (List<User>) query.execute();
    if(tmp!=null){
      System.out.println("collection got non-zero stuff from query collection");
      System.out.println(tmp.get(0).toString());
      similarUsers=new ArrayList<User>(tmp);
    }
    query.closeAll();
    return similarUsers;
  }

  public static void consolidateEncounterSubmitters(Shepherd myShepherd, Encounter enc, User useMe, User userToRemove){
    // System.out.println("consolidating submitters for encounter: " + enc.getCatalogNumber());
    List<User> subs=enc.getSubmitters();
    if(subs.contains(userToRemove)){
      // System.out.println("here’s what you’re removing: " + userToRemove.getUsername());
      // System.out.println("here’s what you’re adding: " + useMe.getUsername());
      subs.remove(userToRemove);
      subs.add(useMe);
    }
    enc.setSubmitters(subs);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidateMainEncounterSubmitterId(Shepherd myShepherd, Encounter enc, User useMe, User userToRemove){
    System.out.println("consolidateMainEncounterSubmitterId entered");
    if(enc.getSubmitterID().equals(userToRemove.getUsername())){
      enc.setSubmitterID(useMe.getUsername());
    }
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidateUsernameless(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    //TODO flesh this out when you have a "Public" user
    // System.out.println("assigning usernameless encounters with useMe's username for encounter: " + enc.getCatalogNumber());
    List<User> subs=enc.getSubmitters();
    if(subs.contains(currentUser) || subs.size()==0){
      // System.out.println("here’s what you’re removing: " + currentUser.getUsername());
      // System.out.println("here’s what you’re adding: " + useMe.getUsername());
      // subs.remove(currentUser); //TODO comment back in
      // subs.add(useMe); //TODO comment back in
    }
    enc.setSubmitters(subs);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidatePhotographers(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    // System.out.println("consolidating photographers for encounter: " + enc.getCatalogNumber());
    List<User> photographers=enc.getPhotographers();
    if(photographers.contains(currentUser)){
      // System.out.println("here’s what you’re removing: " + currentUser.getUsername());
      // System.out.println("here’s what you’re adding: " + useMe.getUsername());
      photographers.remove(currentUser); //TODO comment back in
      photographers.add(useMe); //TODO comment back in
    }
    enc.setPhotographers(photographers);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static List<Encounter> getSubmitterEncountersForUser(PersistenceManager persistenceManager, User user){
  	String filter="SELECT FROM org.ecocean.Encounter where (submitters.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query=persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    return encs;
  }

  public static List<Occurrence> getSubmitterOccurrencesForUser(PersistenceManager persistenceManager, User user){
  	String filter="SELECT FROM org.ecocean.Occurrence where (submitters.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
  	List<Occurrence> encs=new ArrayList<Occurrence>();
    Query query=persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Occurrence>(c);
    }
    query.closeAll();
    return encs;
  }

  public static List<Encounter> getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress(PersistenceManager persistenceManager, User user){
    System.out.println("getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress entered");
  	String filter="SELECT FROM org.ecocean.Encounter where this.submitterEmail==user.emailAddress && user.username==null || user.username==\"N/A\" VARIABLES org.ecocean.User user";
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query = persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      System.out.println("query was not null!");
      encs=new ArrayList<Encounter>(c);
      System.out.println(encs.size());
      for(int i=0; i<encs.size(); i++){
        // System.out.println("entering for loop in getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress");
        Encounter currentEncounter = encs.get(i);
        // System.out.println("usernameless encounter by " + currentEncounter.getSubmitterEmail() + " with username: " + currentEncounter.getSubmitterID());
        System.out.println(currentEncounter.getSubmitterEmail());
      }
    }
    query.closeAll();
    return encs;
  }

  public static List<User> getUsersWithMissingUsernamesWhoMatchEmail(PersistenceManager persistenceManager, String emailAddress){
    List<User> usernamelessUsers=new ArrayList<User>();
    if(!"".equals(emailAddress) && emailAddress!=null){
      // System.out.println("getUsersWithMissingUsernamesWhoMatchEmail entered. Query email is: " + emailAddress);
      String filter="SELECT FROM org.ecocean.User where \"" + emailAddress + "\"==this.emailAddress && this.username==null || this.username==\"N/A\" ";
      Query query=persistenceManager.newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){
        // System.out.println("query was not null!");
        usernamelessUsers=new ArrayList<User>(c);
        System.out.println("There are " + usernamelessUsers.size() + " users with a known email address but no username");
        for(int i=0; i<usernamelessUsers.size(); i++){
          // System.out.println("entering for loop in getUsersWithMissingUsernamesWhoMatchEmail");
          User currentUser = usernamelessUsers.get(i);
          // System.out.println("usernameless user " + currentUser.getUsername() + " has email address " + currentUser.getEmailAddress());
        }
      }
      // System.out.println("done with getUsersWithMissingUsernamesWhoMatchEmail. Closing...");
      query.closeAll();
    }
    return usernamelessUsers;
  }


  public static List<Encounter> getPhotographerEncountersForUser(PersistenceManager persistenceManager, User user){
  	String filter="SELECT FROM org.ecocean.Encounter where (photographers.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query= persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    return encs;
  }

  public static List<User> getUsersByUsername(PersistenceManager persistenceManager,String username){
    List<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE username == \""+username+"\"";
    Query query=persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
  	return users;
  }

  public static List<User> getUsersByFullname(PersistenceManager persistenceManager,String fullname){
    List<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE fullName == \""+fullname+"\"";
    Query query=persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
  	return users;
  }

  public static List<User> getUsersByHashedEmailAddress(PersistenceManager persistenceManager,String hashedEmail){
    List<User> users=new ArrayList<User>();
    String filter = "SELECT FROM org.ecocean.User WHERE hashedEmailAddress == \""+hashedEmail+"\"";
    Query query = persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
  	return users;
  }

  public static User getFirstUserWithEmailAddress(PersistenceManager persistenceManager,String emailAddress){
    if(emailAddress != null){
      // emailAddress = emailAddress.toLowerCase().trim();
      List<User> users=new ArrayList<User>();
      String filter = "SELECT FROM org.ecocean.User WHERE emailAddress == \""+emailAddress+"\"";
      Query query = persistenceManager.newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){
        users=new ArrayList<User>(c);
      }
      if(users.size()>0){
        return users.get(0);
      } else{
        System.out.println("ack there were no users");
        return null;
      }
    }else{
      System.out.println("current email address was invalid. Skipping...");
      return null;
    }
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    boolean complete = false;
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Access-Control-Allow-Origin", "*");
    PrintWriter out = null;
    try {
        out = response.getWriter();
    } catch (IOException ioe) {
        ioe.printStackTrace();
    }
    String context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("TranslationsGet.java");
    myShepherd.beginDBTransaction();
    JSONObject returnJson = new JSONObject();
    JSONArray userJsonArr = new JSONArray();
    JSONObject jsonRes = ServletUtilities.jsonFromHttpServletRequest(request);
    try{
      //get info from servlet request, if it exsists
      String userName = jsonRes.optString("username", null);
      String mergeDesiredStr = jsonRes.optString("mergeDesired", null);
      boolean mergeDesired = false;
      if(Util.stringExists(mergeDesiredStr)){
        mergeDesired = Boolean.parseBoolean(mergeDesiredStr);
      }
      JSONArray userInfoArr = jsonRes.optJSONArray("userInfoArr");

      // fetch similar users
      if(Util.stringExists(userName) && mergeDesired==false){
        System.out.println("fetching similar users");
        System.out.println("userName is: " + userName);
        User currentUser = myShepherd.getUser(userName);
        List<User> similarUsers = getSimilarUsers(currentUser, myShepherd.getPM());
        similarUsers.remove(currentUser);
        if(similarUsers != null){
          if(similarUsers.size()>0){
            for(int j=0; j<similarUsers.size(); j++){
              JSONObject currentUserJson = new JSONObject();
              currentUserJson.put("uuid",similarUsers.get(j).getUUID());
              currentUserJson.put("username", similarUsers.get(j).getUsername());
              currentUserJson.put("fullname", similarUsers.get(j).getFullName());
              currentUserJson.put("email",similarUsers.get(j).getEmailAddress());
              userJsonArr.put(currentUserJson);
            }
            returnJson.put("success",true);
            returnJson.put("users", userJsonArr);
          }
        }
        out.println(returnJson);
        out.close();
      }

      //consolidate the user duplicates indicated by user
      if(mergeDesired==true && Util.stringExists(userName)){
        System.out.println("consolidating user section entered");
        User currentUser = myShepherd.getUser(userName);
        if(currentUser!=null){
          System.out.println("mergeDesired is: " + mergeDesired);
          if(userInfoArr != null && userInfoArr.length()>0){
            System.out.println("got here a");
            for(int i = 0; i<userInfoArr.length(); i++){
              System.out.println("got here b");
              JSONObject currentUserToBeConsolidatedInfo = userInfoArr.getJSONObject(i);
              String currentUserToBeConsolidatedUsername = currentUserToBeConsolidatedInfo.optString("username", null);
              String currentUserToBeConsolidatedEmail = currentUserToBeConsolidatedInfo.optString("email", null);
              String currentUserToBeConsolidatedFullName = currentUserToBeConsolidatedInfo.optString("fullname", null);
              System.out.println("got here c");
              User userToBeConsolidated =  narrowDownUsersToBeMergedToOneIfPossible(myShepherd, currentUserToBeConsolidatedUsername, currentUserToBeConsolidatedEmail, currentUserToBeConsolidatedFullName);
              System.out.println("got here d");
              if(userToBeConsolidated!=null){
                //only found one match
                System.out.println("got here e");
                consolidateUser(myShepherd, currentUser, userToBeConsolidated);
                returnJson.put("success",true);
              }else{
                //found more than one match or none. TODO fail and report failure?
                returnJson.put("success",false);
              }
            }
          }
        }
        out.println(returnJson);
        out.close();
      }

    }catch (NullPointerException npe) {
        npe.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: NullPointerException npe while getting or merging users.");
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (JSONException je) {
        je.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: JSONException je while getting or merging users.");
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (Exception e) {
        e.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: Exception e while getting or merging users.");
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        if (out!=null) {
            out.println(returnJson);
            out.close();
        }
    }
  }

  private User narrowDownUsersToBeMergedToOneIfPossible(Shepherd myShepherd, String currentUserToBeConsolidatedUsername, String currentUserToBeConsolidatedEmail, String currentUserToBeConsolidatedFullName){
    System.out.println("narrowDownUsersToBeMergedToOneIfPossible entered");
    User returnUser = null;
    List<User> currentUsersToBeConsolidated = new ArrayList<User>();

    //check by username
    if(Util.stringExists(currentUserToBeConsolidatedUsername) && !currentUserToBeConsolidatedUsername.equals("undefined")){
      System.out.println("currentUserToBeConsolidatedUsername in narrowDownUsersToBeMergedToOneIfPossible exists and is: " + currentUserToBeConsolidatedUsername);
      //fetch user if username exists
      currentUsersToBeConsolidated = getUsersByUsername(myShepherd.getPM(), currentUserToBeConsolidatedUsername);
      System.out.println("got here 1");
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
        //there's only one result. Go ahead and return that one
        System.out.println("got here 2");
        returnUser = currentUsersToBeConsolidated.get(0);
      }
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>1){
        System.out.println("got here 3");
        //more than one result. Let's see if we can narrow it down to one individual using email instead
        if(Util.stringExists(currentUserToBeConsolidatedEmail)  && !currentUserToBeConsolidatedEmail.equals("undefined")){
          currentUsersToBeConsolidated = getUsersByHashedEmailAddress(myShepherd.getPM(), User.generateEmailHash(currentUserToBeConsolidatedEmail));
        }
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
          //will still be >1 if currentUserToBeConsolidatedEmail is null or undefined
          System.out.println("got here 4");
          //there's only one result. Go ahead and return that one
          returnUser = currentUsersToBeConsolidated.get(0);
        }
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>1){
          System.out.println("got here 5");
          //more than one result that way. Let's see if we can narrow it down to one individual using fullname instead
          if(Util.stringExists(currentUserToBeConsolidatedFullName)  && !currentUserToBeConsolidatedFullName.equals("undefined")){
            currentUsersToBeConsolidated = getUsersByFullname(myShepherd.getPM(), currentUserToBeConsolidatedFullName);
          }
          if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
            //will still be >1 if currentUserToBeConsolidatedFullName is null or undefined
            System.out.println("got here 6");
            //there's only one result. Go ahead and return that one
            returnUser = currentUsersToBeConsolidated.get(0);
          }
        }//end if more than one result for email address
      }//end if more than one result for username
    } //end if for username //end check by username

    //check email if username missing or undefined
    if(Util.stringExists(currentUserToBeConsolidatedEmail)  && !currentUserToBeConsolidatedEmail.equals("undefined")){
      currentUsersToBeConsolidated = getUsersByHashedEmailAddress(myShepherd.getPM(), User.generateEmailHash(currentUserToBeConsolidatedEmail));
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
        //there's only one result. Go ahead and return that one
        System.out.println("got here 7");
        returnUser = currentUsersToBeConsolidated.get(0);
      }
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>1){
        //more than one result that way. Let's see if we can narrow it down to one individual using fullname instead
        if(Util.stringExists(currentUserToBeConsolidatedFullName)  && !currentUserToBeConsolidatedFullName.equals("undefined")){
            currentUsersToBeConsolidated = getUsersByFullname(myShepherd.getPM(), currentUserToBeConsolidatedFullName);
          }
          if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
            //will still be >1 if currentUserToBeConsolidatedFullName is null or undefined
            System.out.println("got here 8");
            //there's only one result. Go ahead and return that one
            returnUser = currentUsersToBeConsolidated.get(0);
          }
      }
    } //end check email if username missing or undefined
    //check fullname if username and email missing or undefined
    if(Util.stringExists(currentUserToBeConsolidatedFullName)  && !currentUserToBeConsolidatedFullName.equals("undefined")){
        currentUsersToBeConsolidated = getUsersByFullname(myShepherd.getPM(), currentUserToBeConsolidatedFullName);
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
          //there's only one result. Go ahead and return that one
          System.out.println("got here 9");
          returnUser = currentUsersToBeConsolidated.get(0);
        }else{
            System.out.println("couldn’t narrow down to one user in narrowDownUsersToBeMergedToOneIfPossible");
          }
    }//end check fullname if username and email missing or undefined
    return returnUser;
  }

  private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
  }
}
