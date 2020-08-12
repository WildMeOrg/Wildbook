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

public class UserConsolidate extends HttpServlet {

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public static int consolidate(Shepherd myShepherd,User useMe,List<User> dupes){
    PersistenceManager persistenceManager = myShepherd.getPM();
    System.out.println("consolidate entered");
  	dupes.remove(useMe);
  	int numDupes=dupes.size();
    System.out.println(numDupes + " duplicates found for user: " + useMe.getUsername());

  	for(int i=0;i<dupes.size();i++){
  		User currentDupe=dupes.get(i);
  		List<Encounter> photographerEncounters=getPhotographerEncountersForUser(persistenceManager,currentDupe);
      System.out.println(photographerEncounters.size()+" photographer encounters found for user: " + currentDupe.getUsername());
  		for(int j=0;j<photographerEncounters.size();j++){
  			Encounter currentEncounter=photographerEncounters.get(j);
  			consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupe);
  		}
  		List<Encounter> submitterEncounters= getSubmitterEncountersForUser(persistenceManager,currentDupe);
      System.out.println(submitterEncounters.size()+" submitter encounters found for user: " + currentDupe.getUsername());
  		for(int j=0;j<submitterEncounters.size();j++){
  			Encounter currentEncounter=submitterEncounters.get(j);
        consolidateSubmitters(myShepherd, currentEncounter, useMe, currentDupe);
  		}
      // List<Encounter> usernameLessEncounters= getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress(myShepherd,currentDupe);
  		// for(int j=0;j<usernameLessEncounters.size();j++){
  		// 	Encounter currentEncounter=usernameLessEncounters.get(j);
      //   // System.out.println("usernameless encounter with catalog number: " + currentEncounter.getCatalogNumber() + " by " + currentEncounter.getSubmitterEmail() + " with username: " + currentEncounter.getSubmitterID());
      //   consolidateUsernameless(myShepherd, currentEncounter, useMe, currentDupe);
  		// }

  		dupes.remove(currentDupe); //TODO comment in when you are ready
  		myShepherd.getPM().deletePersistent(currentDupe); //TODO comment in when you are ready
  		myShepherd.commitDBTransaction(); //TODO comment in when you are ready
  		myShepherd.beginDBTransaction(); //TODO comment in when you are ready
  		i--;
  	}
  	return numDupes;
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
      ArrayList<User> dupesToBeSubsumed =getUsersByHashedEmailAddress(persistenceManager,useMe.getHashedEmailAddress());
      dupesToBeSubsumed.remove(useMe);
      int numDupes=dupesToBeSubsumed.size();
      for(int i=0;i<dupesToBeSubsumed.size();i++){
        User currentDupeUser=dupesToBeSubsumed.get(i);
        List<Encounter> photographerEncounters=getPhotographerEncountersForUser(persistenceManager,currentDupeUser);
        for(int j=0; j<photographerEncounters.size(); j++){
          Encounter currentEncounter=photographerEncounters.get(j);
          consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupeUser);
        }
        List<Encounter> submitterEncounters=getSubmitterEncountersForUser(persistenceManager,currentDupeUser);
        for(int k=0;k<submitterEncounters.size();k++){
          Encounter currentEncounter=submitterEncounters.get(k);
          consolidateSubmitters(myShepherd, currentEncounter, useMe, currentDupeUser);
        }
        //TODO consolidateEncounterSubmitterIds
        //TODO consolidateOccurenceSubmitterIds
        dupesToBeSubsumed.remove(currentDupeUser);
        persistenceManager.deletePersistent(currentDupeUser);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        i--;
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
          if(!targetEmails.contains(allUsers.get(i).getEmailAddress())){
            targetEmails.add(allUsers.get(i).getEmailAddress());
          }
        }
      }
    }
    return targetEmails;
  }

  public static void consolidateSubmitters(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    // System.out.println("consolidating submitters for encounter: " + enc.getCatalogNumber());
    List<User> subs=enc.getSubmitters();
    if(subs.contains(currentUser)){
      // System.out.println("here’s what you’re removing: " + currentUser.getUsername());
      // System.out.println("here’s what you’re adding: " + useMe.getUsername());
      subs.remove(currentUser); //TODO comment back in
      subs.add(useMe); //TODO comment back in
    }
    enc.setSubmitters(subs);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidateUsernameless(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    //TODO don't think this is needed, and certainly broken currently?
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
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    return encs;
  }

  public static List<Encounter> getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress(PersistenceManager persistenceManager, User user){
    System.out.println("getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress entered");
  	String filter="SELECT FROM org.ecocean.Encounter where this.submitterEmail==user.emailAddress && user.username==null || user.username==\"N/A\" VARIABLES org.ecocean.User user";
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
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
    ArrayList<User> usernamelessUsers=new ArrayList<User>();
    if(!"".equals(emailAddress) && emailAddress!=null){
      System.out.println("getUsersWithMissingUsernamesWhoMatchEmail entered. Query email is: " + emailAddress);
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
          System.out.println("usernameless user " + currentUser.getUsername() + " has email address " + currentUser.getEmailAddress());
        }
      }
      System.out.println("done with getUsersWithMissingUsernamesWhoMatchEmail. Closing...");
      query.closeAll();
    }
    return usernamelessUsers;
  }


  public static List<Encounter> getPhotographerEncountersForUser(PersistenceManager persistenceManager, User user){
  	String filter="SELECT FROM org.ecocean.Encounter where (photographers.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query= persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    return encs;
  }

  public static ArrayList<User> getUsersByUsername(PersistenceManager persistenceManager,String username){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE username == \""+username+"\"";
    Query query=persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
  	return users;
  }

  public static ArrayList<User> getUsersByHashedEmailAddress(PersistenceManager persistenceManager,String hashedEmail){
    ArrayList<User> users=new ArrayList<User>();
    String filter = "SELECT FROM org.ecocean.User WHERE hashedEmailAddress == \""+hashedEmail+"\"";
    Query query = persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
  	return users;
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    //context=ServletUtilities.getContext(request);
    //set up the user directory
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    // File usersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    // if(!usersDir.exists()){usersDir.mkdirs();}
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    // boolean createThisUser = false;
    //String addedRoles="";
    boolean isEdit=true;
      String username=request.getUserPrincipal().getName();
      String userNameToUse="";
      if(request.getParameter("username-input")!=null){
        userNameToUse=request.getParameter("username-input").trim();
      }
      Shepherd myShepherd = new Shepherd(context);
      myShepherd.setAction("UserConsolidate.class");
      User newUser=myShepherd.getUser(username);
      if(newUser!=null){
        myShepherd.beginDBTransaction();
      //set password
      if(!userNameToUse.trim().equals("") && userNameToUse != null){
        manualConsolidateByUsername(myShepherd, userNameToUse);
      }
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
      myShepherd=null;
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Success:</strong> Records were consolidated under '" + userNameToUse + "'!");
      out.println(ServletUtilities.getFooter(context));
    }
    else{
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Records were NOT consolidated.");
        out.println(ServletUtilities.getFooter(context));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    out.close();
  }
}
