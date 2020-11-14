/**
 * Manually consolidates user encounter information on the basis of a provided username (the one that will have ownship transferred to it`)
 *
 * @author mfisher
 */

 package org.ecocean.servlet;
 import org.ecocean.servlet.ServletUtilities;

 import org.ecocean.*;
 import org.ecocean.servlet.importer.*;
 import java.sql.*;
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
 import java.util.Random;
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
    System.out.println("dedupe consolidating user " + userToBeConsolidated.toString() + " into user " + userToRetain.toString());

    List<Encounter> photographerEncounters=getPhotographerEncountersForUser(myShepherd.getPM(),userToBeConsolidated);
    if(photographerEncounters!=null && photographerEncounters.size()>0){
      for(int j=0; j<photographerEncounters.size(); j++){
        Encounter currentEncounter=photographerEncounters.get(j);
        consolidatePhotographers(myShepherd, currentEncounter, userToRetain, userToBeConsolidated);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
    List<Encounter> submitterEncounters=getSubmitterEncountersForUser(myShepherd.getPM(),userToBeConsolidated);
    if(submitterEncounters!=null && submitterEncounters.size()>0){
      for(int k=0;k<submitterEncounters.size();k++){
        Encounter currentEncounter=submitterEncounters.get(k);
        consolidateEncounterSubmitters(myShepherd, currentEncounter, userToRetain, userToBeConsolidated);
        consolidateMainEncounterSubmitterId(myShepherd, currentEncounter, userToRetain, userToBeConsolidated);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
    List<Occurrence> submitterOccurrences = getSubmitterOccurrencesForUser(myShepherd.getPM(), userToBeConsolidated);
    if(submitterOccurrences!=null && submitterOccurrences.size()>0){
      for(int j=0;j<submitterOccurrences.size(); j++){
        Occurrence currentOccurrence = submitterOccurrences.get(j);
        if(currentOccurrence!=null){
          consolidateOccurrenceSubmitters(myShepherd, currentOccurrence, userToRetain, userToBeConsolidated);
        }
      }
    }
    consolidateEncounterInformOthers(myShepherd, userToRetain, userToBeConsolidated);
    consolidateImportTaskCreator(myShepherd, userToRetain, userToBeConsolidated);
    myShepherd.getPM().deletePersistent(userToBeConsolidated);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    System.out.println("dedupe ......consolidation complete");
  }

  public static void consolidateEncounterSubmitters(Shepherd myShepherd, Encounter enc, User useMe, User userToRemove){
    System.out.println("dedupe removing "+ userToRemove.toString() +" from submitters list in encounter " + enc.toString() + " and adding user " + useMe.toString());
    if(!useMe.equals(userToRemove)){
      List<User> subs=enc.getSubmitters();
      if(subs!=null && userToRemove!=null && subs.contains(userToRemove)){
        subs.remove(userToRemove);
      }
      if(subs!=null && useMe!=null && !subs.contains(useMe)){
        subs.add(useMe);
      }
      enc.setSubmitters(subs);
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }

  public static void consolidateMainEncounterSubmitterId(Shepherd myShepherd, Encounter enc, User useMe, User userToRemove){
    System.out.println("dedupe changing submitterId for encounter "+ enc.toString() +" from user "+ userToRemove.toString() +" to user " + useMe.toString());
    if(enc.getSubmitterID()!=null && userToRemove.getUsername()!=null && enc.getSubmitterID().equals(userToRemove.getUsername())){
      enc.setSubmitterID(useMe.getUsername());
    }
  }

  public static void consolidateImportTaskCreator(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    System.out.println("dedupe consolidating import tasks created by user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
    String filter="SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE creator.uuid==\""+userToBeConsolidated.getUUID()+"\""; // && user.uuid==\""+userToBeConsolidated.getUUID()+"\" VARIABLES org.ecocean.User user"
    System.out.println("dedupe query is: " + filter);
  	List<ImportTask> impTasks=new ArrayList<ImportTask>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      impTasks=new ArrayList<ImportTask>(c);
    }
    query.closeAll();
    if(impTasks!=null && impTasks.size()>0){
      for(int i=0; i<impTasks.size(); i++){
        ImportTask currentImportTask = impTasks.get(i);
        if(currentImportTask.getCreator()!=null & currentImportTask.getCreator().equals(userToBeConsolidated)){
          currentImportTask.setCreator(userToRetain);
        }
      }
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }else{
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }

  public static void consolidateEncounterInformOthers(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    System.out.println("dedupe consolidating inform others containing user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
    String filter="SELECT FROM org.ecocean.Encounter WHERE this.informOthers.contains(user) && user.uuid=='" + userToBeConsolidated.getUUID() + "' VARIABLES org.ecocean.User user"; // && user.uuid==\""+userToBeConsolidated.getUUID()+"\" VARIABLES org.ecocean.User user"
    System.out.println("dedupe query is: " + filter);
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    if(encs!=null && encs.size()>0){
      for(int i=0; i<encs.size(); i++){
        Encounter currentEncounter = encs.get(i);
        if(currentEncounter.getInformOthers()!=null){
          List<User> currentInformOthers = currentEncounter.getInformOthers();
          if(currentInformOthers.contains(userToBeConsolidated)){
            currentInformOthers.remove(userToBeConsolidated);
          }
          if(!currentInformOthers.contains(userToRetain)){
            currentInformOthers.add(userToRetain);
          }
          currentEncounter.setInformOthers(currentInformOthers);
        }
      }
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }else{
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }

  public static int consolidateUsersAndNameless(Shepherd myShepherd,User useMe,List<User> dupes){ //TODO this is not up-to-date with consolidateUser. Use that as a model when the time comes. Lots of wonky db commit weirdness, so be careful out there -MF
    PersistenceManager persistenceManager = myShepherd.getPM();
  	dupes.remove(useMe);
  	int numDupes=dupes.size();

  	for(int i=0;i<dupes.size();i++){
  		User currentDupe=dupes.get(i);
  		List<Encounter> photographerEncounters=getPhotographerEncountersForUser(persistenceManager,currentDupe);
      if(photographerEncounters!=null && photographerEncounters.size()>0){
        for(int j=0;j<photographerEncounters.size();j++){
          Encounter currentEncounter=photographerEncounters.get(j);
          consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupe);
        }
      }
  		List<Encounter> submitterEncounters= getSubmitterEncountersForUser(persistenceManager,currentDupe);
      if(submitterEncounters!=null && submitterEncounters.size()>0){
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
      //   // System.out.println("dedupe usernameless encounter with catalog number: " + currentEncounter.getCatalogNumber() + " by " + currentEncounter.getSubmitterEmail() + " with username: " + currentEncounter.getSubmitterID());
      //   consolidateUsernameless(myShepherd, currentEncounter, useMe, currentDupe);
  		// }

      List<Occurrence> submitterOccurrences = getSubmitterOccurrencesForUser(persistenceManager, currentDupe);
      if(submitterOccurrences!=null && submitterOccurrences.size()>0){
        for(int j=0;j<submitterOccurrences.size(); j++){
          Occurrence currentOccurrence = submitterOccurrences.get(j);
          consolidateOccurrenceSubmitters(myShepherd, currentOccurrence, useMe, currentDupe);
          myShepherd.commitDBTransaction();
      		myShepherd.beginDBTransaction();
        }
      }

  		dupes.remove(currentDupe);
  		myShepherd.getPM().deletePersistent(currentDupe);
  		myShepherd.commitDBTransaction();
  		myShepherd.beginDBTransaction();
  		i--;
  	}
  	return numDupes;
  }

  public static void consolidateOccurrenceSubmitters(Shepherd myShepherd, Occurrence currentOccurrence, User useMe, User currentDupe){
    String currentOccurrenceSubmitter = currentOccurrence.getSubmitterID();
    System.out.println("dedupe transferring submitterId in occurence " + currentOccurrence.toString() + " from user " + currentDupe.toString() + " to user " + useMe.toString());
    if(Util.stringExists(currentOccurrenceSubmitter) && Util.stringExists(currentDupe.getUsername()) && currentDupe.getUsername().equals(currentOccurrenceSubmitter)){
      if(Util.stringExists(useMe.getUsername())){
        currentOccurrence.setSubmitterID(useMe.getUsername());
      } else{
        //TODO Jon, should I set it to null in this case??
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
    List<String> targetEmails = new ArrayList<String>();
    if(allUsers.size()>0){
      for(int i=0; i<allUsers.size(); i++){
        List<User> dupesForUser = getUsersByHashedEmailAddress(persistenceManager,allUsers.get(i).getHashedEmailAddress());
        if(dupesForUser.size()>1){
          if(processedEmailAddressesToCompareAgainst.contains(allUsers.get(i).getEmailAddress().toLowerCase().trim()) && !targetEmails.contains(allUsers.get(i).getEmailAddress())){
            targetEmails.add(allUsers.get(i).getEmailAddress());
          }
        }
      }
    }
    return targetEmails;
  }

  public static List<User> getSimilarUsers(User user, PersistenceManager persistenceManager){
    String baseQueryString="SELECT * FROM \"USERS\" WHERE ";
    String fullNameFilter = "\"FULLNAME\" ilike ?1"; //" + user.getFullName() + "
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
  	List<User> similarUsers=new ArrayList<User>();
    System.out.println("dedupe combined query for getSimilarUsers is: " + combinedQuery);
    if(!combinedQuery.equals(baseQueryString)){
      Query query = persistenceManager.newQuery("javax.jdo.query.SQL", combinedQuery);
      query.setClass(User.class);
      Collection c = null;
      if(fullNameExists){ //it should only expect a parameter if combinedQuery includes the fullName part
        c = (Collection) (query.execute("%"+user.getFullName()+"%"));
      } else{
        c = (Collection) (query.execute());
      }
      similarUsers=new ArrayList<User>(c);
      query.closeAll();
    }
    if(similarUsers.contains(user)){
      similarUsers.remove(user);
    }
    return similarUsers;
  }

  public static void consolidateUsernameless(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    //TODO flesh this out when you have a "Public" user
    // System.out.println("dedupe assigning usernameless encounters with useMe's username for encounter: " + enc.getCatalogNumber());
    List<User> subs=enc.getSubmitters();
    if(subs.contains(currentUser) || subs.size()==0){
      // System.out.println("dedupe here’s what you’re removing: " + currentUser.getUsername());
      // System.out.println("dedupe here’s what you’re adding: " + useMe.getUsername());
      // subs.remove(currentUser); //TODO comment back in
      // subs.add(useMe); //TODO comment back in
    }
    enc.setSubmitters(subs);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidatePhotographers(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    System.out.println("dedupe transferring photographer "+ currentUser.toString() +" in encounter "+ enc.toString() +" to photographer " + useMe.toString());
    if(!useMe.equals(currentUser)){
      List<User> photographers=enc.getPhotographers();
      if(photographers!=null && useMe !=null && !photographers.contains(useMe)){
        photographers.add(useMe);
      }
      if(photographers!=null && currentUser!=null && photographers.contains(currentUser)){
        photographers.remove(currentUser);
      }
      enc.setPhotographers(photographers);
    }
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
  	String filter="SELECT FROM org.ecocean.Encounter where this.submitterEmail==user.emailAddress && user.username==null || user.username==\"N/A\" VARIABLES org.ecocean.User user";
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query = persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
      for(int i=0; i<encs.size(); i++){
        Encounter currentEncounter = encs.get(i);
      }
    }
    query.closeAll();
    return encs;
  }

  public static List<User> getUsersWithMissingUsernamesWhoMatchEmail(PersistenceManager persistenceManager, String emailAddress){
    List<User> usernamelessUsers=new ArrayList<User>();
    if(!"".equals(emailAddress) && emailAddress!=null){
      String filter="SELECT FROM org.ecocean.User where \"" + emailAddress + "\"==this.emailAddress && this.username==null || this.username==\"N/A\" ";
      Query query=persistenceManager.newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){
        usernamelessUsers=new ArrayList<User>(c);
        for(int i=0; i<usernamelessUsers.size(); i++){
          User currentUser = usernamelessUsers.get(i);
        }
      }
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
      query.closeAll();
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
      query.closeAll();
    }
  	return users;
  }

  public static List<User> getUsersWithEmailAddress(PersistenceManager persistenceManager,String emailAddress){
    System.out.println("dedupe getUsersWithEmailAddress entered");
    if(emailAddress != null){
      List<User> users=new ArrayList<User>();
      String filter = "SELECT FROM org.ecocean.User WHERE emailAddress == \""+emailAddress+"\"";
      Query query = persistenceManager.newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){
        users=new ArrayList<User>(c);
      }
      if(users.size()>0){
        return users;
      } else{
        System.out.println("dedupe ack there were no users");
        return null;
      }
    }else{
      System.out.println("dedupe current email address was invalid. Skipping...");
      return null;
    }
  }

  public static User getFirstUserWithEmailAddress(PersistenceManager persistenceManager,String emailAddress){
    System.out.println("dedupe getFirstUserWithEmailAddress entered");
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
        System.out.println("dedupe ack there were no users");
        return null;
      }
    }else{
      System.out.println("dedupe current email address was invalid. Skipping...");
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
    myShepherd.setAction("UserConsolidate.java");
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
      boolean successStatus = false;
      JSONArray userInfoArr = jsonRes.optJSONArray("userInfoArr");

      // fetch similar users
      if(Util.stringExists(userName) && mergeDesired==false){
        successStatus = true;
        User currentUser = myShepherd.getUser(userName);
        List<User> similarUsers = getSimilarUsers(currentUser, myShepherd.getPM());
        if(similarUsers != null && similarUsers.size()>0){
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
        out.println(returnJson);
        out.close();
      }

      //consolidate the user duplicates indicated by user
      if(mergeDesired==true && Util.stringExists(userName)){
        User currentUser = myShepherd.getUser(userName);
        if(currentUser!=null){
          successStatus = true;
          if(userInfoArr != null && userInfoArr.length()>0){
            for(int i = 0; i<userInfoArr.length(); i++){
              JSONObject currentUserToBeConsolidatedInfo = userInfoArr.getJSONObject(i);
              String currentUserToBeConsolidatedUsername = currentUserToBeConsolidatedInfo.optString("username", null);
              String currentUserToBeConsolidatedEmail = currentUserToBeConsolidatedInfo.optString("email", null);
              String currentUserToBeConsolidatedFullName = currentUserToBeConsolidatedInfo.optString("fullname", null);
              User userToBeConsolidated =  narrowDownUsersToBeMergedToOneIfPossible(currentUser, myShepherd, currentUserToBeConsolidatedUsername, currentUserToBeConsolidatedEmail, currentUserToBeConsolidatedFullName);
              if(userToBeConsolidated!=null){
                //only found one match
                try{
                  consolidateUser(myShepherd, currentUser, userToBeConsolidated);
                  returnJson.put("details_" + currentUserToBeConsolidatedUsername+"__" + currentUserToBeConsolidatedEmail + "__" + currentUserToBeConsolidatedFullName,"SingleMatchFoundForUserAndConsdolidated");
                }
                  catch(Exception e){
                      e.printStackTrace();
                      System.out.println("dedupe error consolidating user: " + userToBeConsolidated.toString() + " into user: " + currentUser.toString());
                      successStatus = false;
                      returnJson.put("details_" + currentUserToBeConsolidatedUsername+"__" + currentUserToBeConsolidatedEmail + "__" + currentUserToBeConsolidatedFullName,"ErrorConsolidatingReportToStaff");
                  } finally{
                  }
              }else{
                //found more than one match or none.
                returnJson.put("details_" + currentUserToBeConsolidatedUsername+"__" + currentUserToBeConsolidatedEmail + "__" + currentUserToBeConsolidatedFullName,"FoundMoreThanOneMatchOrNoMatchesForUser");
                successStatus = false;
              }
            }
          }
          returnJson.put("success",successStatus);
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
        System.out.println("dedupe closing ajax call for user consolidate transaction.....");
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        if (out!=null) {
            out.println(returnJson);
            out.close();
        }
    }
  }

  private User narrowDownUsersToBeMergedToOneIfPossible(User currentUser, Shepherd myShepherd, String currentUserToBeConsolidatedUsername, String currentUserToBeConsolidatedEmail, String currentUserToBeConsolidatedFullName){
    User returnUser = null;
    List<User> currentUsersToBeConsolidated = new ArrayList<User>();

    //check by username
    if(Util.stringExists(currentUserToBeConsolidatedUsername) && !currentUserToBeConsolidatedUsername.equals("undefined")){
      //fetch user if username exists
      currentUsersToBeConsolidated = getUsersByUsername(myShepherd.getPM(), currentUserToBeConsolidatedUsername);
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
        currentUsersToBeConsolidated.remove(currentUser);
      }
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
        //there's only one result. Go ahead and return that one
        returnUser = currentUsersToBeConsolidated.get(0);
      }
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>1){
        //more than one result. Let's see if we can narrow it down to one individual using email instead
        if(Util.stringExists(currentUserToBeConsolidatedEmail)  && !currentUserToBeConsolidatedEmail.equals("undefined")){
          currentUsersToBeConsolidated = getUsersByHashedEmailAddress(myShepherd.getPM(), User.generateEmailHash(currentUserToBeConsolidatedEmail));
          if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
            currentUsersToBeConsolidated.remove(currentUser);
          }
          //try getting it by email address if empty
          if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()<1){
            currentUsersToBeConsolidated = getUsersWithEmailAddress(myShepherd.getPM(), currentUserToBeConsolidatedEmail);
            if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
              currentUsersToBeConsolidated.remove(currentUser);
            }
          }
        }
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
          //there's only one result. Go ahead and return that one
          returnUser = currentUsersToBeConsolidated.get(0);
        }
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>1){
          //more than one result that way. Let's see if we can narrow it down to one individual using fullname instead
          if(Util.stringExists(currentUserToBeConsolidatedFullName)  && !currentUserToBeConsolidatedFullName.equals("undefined")){
            currentUsersToBeConsolidated = getUsersByFullname(myShepherd.getPM(), currentUserToBeConsolidatedFullName);
            if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
              currentUsersToBeConsolidated.remove(currentUser);
            }
          }
          if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
            //there's only one result. Go ahead and return that one
            returnUser = currentUsersToBeConsolidated.get(0);
          }
        }//end if more than one result for email address
      }//end if more than one result for username
    } //end if for username //end check by username

    //check email if username missing or undefined
    if(Util.stringExists(currentUserToBeConsolidatedEmail)  && !currentUserToBeConsolidatedEmail.equals("undefined")){
      currentUsersToBeConsolidated = getUsersByHashedEmailAddress(myShepherd.getPM(), User.generateEmailHash(currentUserToBeConsolidatedEmail));
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
        currentUsersToBeConsolidated.remove(currentUser);
      }
      //try getting it by email address if empty
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()<1){
        currentUsersToBeConsolidated = getUsersWithEmailAddress(myShepherd.getPM(), currentUserToBeConsolidatedEmail);
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
          currentUsersToBeConsolidated.remove(currentUser);
        }
      }
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
        //there's only one result. Go ahead and return that one
        returnUser = currentUsersToBeConsolidated.get(0);
      }
      if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>1){
        //more than one result that way. Let's see if we can narrow it down to one individual using fullname instead
        if(Util.stringExists(currentUserToBeConsolidatedFullName)  && !currentUserToBeConsolidatedFullName.equals("undefined")){
            currentUsersToBeConsolidated = getUsersByFullname(myShepherd.getPM(), currentUserToBeConsolidatedFullName);
            if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()>0){
              currentUsersToBeConsolidated.remove(currentUser);
            }
          }
          if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
            //there's only one result. Go ahead and return that one
            returnUser = currentUsersToBeConsolidated.get(0);
          }
      }
    } //end check email if username missing or undefined
    //check fullname if username and email missing or undefined
    if(Util.stringExists(currentUserToBeConsolidatedFullName)  && !currentUserToBeConsolidatedFullName.equals("undefined")){
        currentUsersToBeConsolidated = getUsersByFullname(myShepherd.getPM(), currentUserToBeConsolidatedFullName);
        currentUsersToBeConsolidated.remove(currentUser);
        if(currentUsersToBeConsolidated!=null && currentUsersToBeConsolidated.size()==1){
          //there's only one result. Go ahead and return that one
          returnUser = currentUsersToBeConsolidated.get(0);
        }
    }//end check fullname if username and email missing or undefined
    return returnUser;
  }

  private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
  }

  public static List<User> removeNulls(List<User>userListWithPotentialNulls){
    List<User> nonNullUsers = new ArrayList<User>();
    if(userListWithPotentialNulls!=null && userListWithPotentialNulls.size()>0){
      for(int i=0; i<userListWithPotentialNulls.size(); i++){
        User currentUser = userListWithPotentialNulls.get(i);
        if(currentUser!=null && !nonNullUsers.contains(currentUser) && currentUser.getUsername()!=null){
          nonNullUsers.add(currentUser);
        }
      }
    }
    return nonNullUsers;
  }

  public static User chooseARandomUserFromList(List<User>users){
    System.out.println("dedupe chooseARandomUserFromList called");
    int maxInd = users.size();
    System.out.println("dedupe highest index in chooseARandomNonNullUserFromRemainingList is: " + maxInd);
    Random randVar = new Random();
    int randIndex = randVar.nextInt(maxInd); //maxInd itself is excluded
    System.out.println("dedupe randIndex is: " + randIndex);
    return users.get(randIndex); //TODO update this
  }
}
