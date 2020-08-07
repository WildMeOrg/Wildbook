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
 import javax.jdo.Query;

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
    System.out.println("consolidate entered");
  	dupes.remove(useMe);
  	int numDupes=dupes.size();

  	for(int i=0;i<dupes.size();i++){
  		User currentDupe=dupes.get(i);
  		List<Encounter> photographerEncounters=getPhotographerEncountersForUser(myShepherd,currentDupe);
  		for(int j=0;j<photographerEncounters.size();j++){
  			Encounter currentEncounter=photographerEncounters.get(j);
  			consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupe);
  		}
  		List<Encounter> submitterEncounters= getSubmitterEncountersForUser(myShepherd,currentDupe);
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
  		dupes.remove(currentDupe);
  		myShepherd.getPM().deletePersistent(currentDupe);
  		myShepherd.commitDBTransaction();
  		myShepherd.beginDBTransaction();
  		i--;
  	}
  	return numDupes;
  }

  public void manualConsolidateByUsername(Shepherd myShepherd, String userNameOfDesiredUseMe){
    System.out.println("manualConsolidateByUsername entered! Username is " + userNameOfDesiredUseMe);
    List<User> potentialUsers = getUsersByUsername(myShepherd, userNameOfDesiredUseMe);
    if(potentialUsers.size() == 1){
      System.out.println("Heyo just one user has this username!");
      User useMe = potentialUsers.get(0);
      String hashedEmail = useMe.getHashedEmailAddress();
      System.out.println("hashedEmail in manualConsolidate is " + hashedEmail);
      ArrayList<User> dupesToBeSubsumed =getUsersByHashedEmailAddress(myShepherd,useMe.getHashedEmailAddress());
      dupesToBeSubsumed.remove(useMe);
      int numDupes=dupesToBeSubsumed.size();
      for(int i=0;i<dupesToBeSubsumed.size();i++){
        User currentDupeUser=dupesToBeSubsumed.get(i);
        List<Encounter> photographerEncounters=getPhotographerEncountersForUser(myShepherd,currentDupeUser);
        for(int j=0;j<photographerEncounters.size();j++){
          Encounter currentEncounter=photographerEncounters.get(j);
          consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupeUser);
        }
        List<Encounter> submitterEncounters=getSubmitterEncountersForUser(myShepherd,currentDupeUser);
        for(int k=0;k<submitterEncounters.size();k++){
          Encounter currentEncounter=submitterEncounters.get(k);
          consolidateSubmitters(myShepherd, currentEncounter, useMe, currentDupeUser);
        }
        //TODO consolidateUsernamelessEncounters
        dupesToBeSubsumed.remove(currentDupeUser);
        myShepherd.getPM().deletePersistent(currentDupeUser);
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

  public static void consolidateSubmitters(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    System.out.println("consolidating submitters for encounter: " + enc.getCatalogNumber());
    List<User> subs=enc.getSubmitters();
    if(subs.contains(currentUser)){
      System.out.println("here’s what you’re removing: " + currentUser.getUsername());
      System.out.println("here’s what you’re adding: " + useMe.getUsername());
      // subs.remove(currentUser); //TODO comment back in
      // subs.add(useMe); //TODO comment back in
    }
    enc.setSubmitters(subs);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidateUsernameless(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    System.out.println("assigning usernameless encounters with useMe's username for encounter: " + enc.getCatalogNumber());
    List<User> subs=enc.getSubmitters();
    if(subs.contains(currentUser) || subs.size()==0){
      System.out.println("here’s what you’re removing: " + currentUser.getUsername());
      System.out.println("here’s what you’re adding: " + useMe.getUsername());
      // subs.remove(currentUser); //TODO comment back in
      // subs.add(useMe); //TODO comment back in
    }
    enc.setSubmitters(subs);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static void consolidatePhotographers(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    System.out.println("consolidating photographers for encounter: " + enc.getCatalogNumber());
    List<User> photos=enc.getPhotographers();
    if(photos.contains(currentUser)){
      System.out.println("here’s what you’re removing: " + currentUser.getUsername());
      System.out.println("here’s what you’re adding: " + useMe.getUsername());
      // photos.remove(currentUser); //TODO comment back in
      // photos.add(useMe); //TODO comment back in
    }
    enc.setPhotographers(photos);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  public static List<Encounter> getSubmitterEncountersForUser(Shepherd myShepherd, User user){
  	String filter="SELECT FROM org.ecocean.Encounter where (submitters.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    return encs;
  }

  public static List<Encounter> getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress(Shepherd myShepherd, User user){
    System.out.println("getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress entered");
  	String filter="SELECT FROM org.ecocean.Encounter where this.submitterEmail==user.emailAddress && user.username==null || user.username==\"N/A\" VARIABLES org.ecocean.User user";
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
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

  public static List<User> getUsersWithMissingUsernamesWhoMatchEmailOfAnotherUser(String emailAddress){
    // System.out.println("getUsersWithMissingUsernamesWhoMatchEmailOfAnotherUser entered");
  	String filter="SELECT FROM org.ecocean.Users where "+ emailAddress+ "==this.emailAddress && this.username==null || this.username==\"N/A\" ";
  	ArrayList<User> usernamelessUsers=new ArrayList<User>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      // System.out.println("query was not null!");
      usernamelessUsers=new ArrayList<User>(c);
      // System.out.println(usernamelessUsers.size());
      for(int i=0; i<usernamelessUsers.size(); i++){
        // System.out.println("entering for loop in getUsersWithMissingUsernamesWhoMatchEmailOfAnotherUser");
        User currentUser = usernamelessUsers.get(i);
        System.out.println("usernameless user has email address " + currentUser.getEmailAddress());
      }
    }
    query.closeAll();
    return encs;
  }


  public static List<Encounter> getPhotographerEncountersForUser(Shepherd myShepherd, User user){
  	String filter="SELECT FROM org.ecocean.Encounter where (photographers.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
  	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      encs=new ArrayList<Encounter>(c);
    }
    query.closeAll();
    return encs;
  }

  public static ArrayList<User> getUsersByUsername(Shepherd myShepherd,String username){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE username == \""+username+"\"";
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
  	return users;
  }

  public static ArrayList<User> getUsersByHashedEmailAddress(Shepherd myShepherd,String hashedEmail){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE hashedEmailAddress == \""+hashedEmail+"\"";
    Query query=myShepherd.getPM().newQuery(filter);
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
      if(!userNameToUse.trim().equals("")){
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
