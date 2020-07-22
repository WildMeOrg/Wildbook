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
        List<Encounter> encs=getPhotographerEncountersForUser(myShepherd,currentDupeUser);
        for(int j=0;j<encs.size();j++){
          Encounter currentEncounter=encs.get(j);
          consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupeUser);
        }
        List<Encounter> encs2=getSubmitterEncountersForUser(myShepherd,currentDupeUser);
        for(int j=0;j<encs.size();j++){
          Encounter currentEncounter=encs.get(j);
          consolidateSubmitters(myShepherd, currentEncounter, useMe, currentDupeUser);
        }
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
    List<User> subs=enc.getSubmitters();
    if(subs.contains(currentUser)){
      subs.remove(currentUser);
      subs.add(useMe);
    }
    enc.setSubmitters(subs);
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

  public static void consolidatePhotographers(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
    List<User> photos=enc.getPhotographers();
    if(photos.contains(currentUser)){
      photos.remove(currentUser);
      photos.add(useMe);
    }
    enc.setPhotographers(photos);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
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
