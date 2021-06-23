
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
 import org.ecocean.security.Collaboration;
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
 import java.util.LinkedList;
 // import javax.jdo.Query;
 import javax.jdo.*;
 import org.json.JSONArray;
 import org.json.JSONObject;
 import org.json.JSONException;
 import org.joda.time.DateTime;

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

    List<Encounter> photographerEncounters=getPhotographerEncountersForUser(myShepherd.getPM(), userToBeConsolidated);
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
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
    List<Occurrence> submitterOccurrences = getOccurrencesForUser(myShepherd.getPM(), userToBeConsolidated);
    if(submitterOccurrences!=null && submitterOccurrences.size()>0){
      for(int j=0;j<submitterOccurrences.size(); j++){
        Occurrence currentOccurrence = submitterOccurrences.get(j);
        if(currentOccurrence!=null){
          consolidateOccurrenceData(myShepherd, currentOccurrence, userToRetain, userToBeConsolidated);
        }
      }
    }
    consolidateEncounterSubmitterIds(myShepherd, userToRetain, userToBeConsolidated);
    consolidateEncounterInformOthers(myShepherd, userToRetain, userToBeConsolidated);
    consolidateImportTaskCreator(myShepherd, userToRetain, userToBeConsolidated);
    consolidateRoles(myShepherd, userToRetain, userToBeConsolidated);
    consolidateCollaborations(myShepherd, userToRetain, userToBeConsolidated);
    consolidateProjects(myShepherd, userToRetain, userToBeConsolidated);
    consolidateOrganizations(myShepherd, userToRetain, userToBeConsolidated);
    //Note: we made the executive decision to not include AccessControl. Might affect just a handfull of flukebook uers. JVO agrees. -MF

    myShepherd.getPM().deletePersistent(userToBeConsolidated);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    System.out.println("dedupe ......consolidation complete");
  }

  public static void consolidateUserForUserEdit(Shepherd myShepherd, User userToRetain,
      User userToBeConsolidated) { //same as consolidateUser, except it doesn't change roles
    System.out
        .println("dedupe consolidateUserForUserEdit user " + userToBeConsolidated.toString()
            + " into user " + userToRetain.toString());

    List<Encounter> photographerEncounters = getPhotographerEncountersForUser(
        myShepherd.getPM(), userToBeConsolidated);
    if (photographerEncounters != null && photographerEncounters.size() > 0) {
      for (int j = 0; j < photographerEncounters.size(); j++) {
        Encounter currentEncounter = photographerEncounters.get(j);
        consolidatePhotographers(myShepherd, currentEncounter, userToRetain,
            userToBeConsolidated);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
    List<Encounter> submitterEncounters = getSubmitterEncountersForUser(
        myShepherd.getPM(), userToBeConsolidated);
    if (submitterEncounters != null && submitterEncounters.size() > 0) {
      for (int k = 0; k < submitterEncounters.size(); k++) {
        Encounter currentEncounter = submitterEncounters.get(k);
        consolidateEncounterSubmitters(myShepherd, currentEncounter,
            userToRetain, userToBeConsolidated);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
    List<Occurrence> submitterOccurrences = getOccurrencesForUser(
        myShepherd.getPM(), userToBeConsolidated);
    if (submitterOccurrences != null && submitterOccurrences.size() > 0) {
      for (int j = 0; j < submitterOccurrences.size(); j++) {
        Occurrence currentOccurrence = submitterOccurrences.get(j);
        if (currentOccurrence != null) {
          consolidateOccurrenceData(myShepherd, currentOccurrence, userToRetain,
              userToBeConsolidated);
        }
      }
    }
    consolidateEncounterSubmitterIds(myShepherd, userToRetain,
        userToBeConsolidated);
    consolidateEncounterInformOthers(myShepherd, userToRetain,
        userToBeConsolidated);
    consolidateImportTaskCreator(myShepherd, userToRetain,
        userToBeConsolidated);
    consolidateCollaborations(myShepherd, userToRetain, userToBeConsolidated);
    consolidateProjects(myShepherd, userToRetain, userToBeConsolidated);
    consolidateOrganizations(myShepherd, userToRetain, userToBeConsolidated);
    // Note: we made the executive decision to not include AccessControl. Might
    // affect just a handfull of flukebook uers. JVO agrees. -MF

    myShepherd.getPM().deletePersistent(userToBeConsolidated);
    myShepherd.updateDBTransaction();
    System.out.println("dedupe ......consolidateUserForUserEdit complete");
  }

  public static void consolidateOrganizations(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    if(userToBeConsolidated!=null){
      List<Organization> organizationsOfUserToBeConsolidated = userToBeConsolidated.getOrganizations();
      List<Organization> organizationsOfUserToRetain = userToRetain.getOrganizations();
      if(organizationsOfUserToBeConsolidated!=null && organizationsOfUserToBeConsolidated.size()>0){
        for(int i=0; i<organizationsOfUserToBeConsolidated.size(); i++){
          Organization currentOrganization = organizationsOfUserToBeConsolidated.get(i);
          if(currentOrganization!=null){
            if(!organizationsOfUserToRetain.contains(currentOrganization)){
              System.out.println("dedupe removing user: " + userToBeConsolidated.toString() + " from organization: " + currentOrganization.toString() + " and adding user: " + userToRetain.toString());
              currentOrganization.removeMember(userToBeConsolidated);
              currentOrganization.addMember(userToRetain);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
            }
          }//end if currentOrganization!=null
        } //end for loop of organizationsOfUserToBeConsolidated
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      } //end if consolidatedUserProjectsInWhichUserIsListedInUsers exists and has >0 elements
   }//end if userToBeConsolidated null check
  }

  public static void consolidateProjects(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    // System.out.println("dedupe consolidating projects from user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
    //check projects where consolidated user is listed in users lists
    if(userToBeConsolidated!=null){
      List<Project> consolidatedUserProjectsInWhichUserIsListedInUsers = myShepherd.getProjectsForUser(userToBeConsolidated);
      if(consolidatedUserProjectsInWhichUserIsListedInUsers!=null && consolidatedUserProjectsInWhichUserIsListedInUsers.size()>0){
        for(int i=0; i<consolidatedUserProjectsInWhichUserIsListedInUsers.size(); i++){
          Project currentProject = consolidatedUserProjectsInWhichUserIsListedInUsers.get(i);
          if(currentProject!=null){
            System.out.println("dedup removing user: "+userToBeConsolidated.toString()+" from project: "+currentProject.toString());
            currentProject.removeUser(userToBeConsolidated);
            if(currentProject.getUsers()!=null && !currentProject.getUsers().contains(userToRetain)){
              System.out.println("dedup adding user: "+userToRetain.toString()+" to project: "+currentProject.toString());
              currentProject.addUser(userToRetain);
            }
          }
        } //end for loop of consolidatedUserProjectsInWhichUserIsListedInUsers
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      } //end if consolidatedUserProjectsInWhichUserIsListedInUsers exists and has >0 elements

      //check projects where consolidated user is listed as owner and set new owner. Should not be any captured here that aren't captured above, but being super paranoid about it
      List<Project> projectsWithConsolidatedUserAsOwner = myShepherd.getProjectsOwnedByUser(userToBeConsolidated);
      if(projectsWithConsolidatedUserAsOwner!=null && projectsWithConsolidatedUserAsOwner.size()>0){
        for(int i=0; i<projectsWithConsolidatedUserAsOwner.size(); i++){
          Project currentProject = projectsWithConsolidatedUserAsOwner.get(i);
          if(currentProject!=null && userToRetain!=null){
            System.out.println("dedupe setting user: " + userToRetain.toString() + " as owner in project: " + currentProject.toString());
            currentProject.setOwner(userToRetain);
          }
        } //end for loop of projectsWithConsolidatedUserAsOwner
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      } //end if consolidatedUserProjects exists and has >0 elements
    } // end userToBeConsolidated null check
  }


  public static void consolidateCollaborations(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    // System.out.println("dedupe consolidating collaborations from user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
    if(Util.stringExists(userToBeConsolidated.getUsername())){
      List<Collaboration> consolidatedUserCollaborations = Collaboration.collaborationsForUser(myShepherd, userToBeConsolidated.getUsername());
      if(consolidatedUserCollaborations!=null && consolidatedUserCollaborations.size()>0){
        for(int i=0; i<consolidatedUserCollaborations.size(); i++){
          Collaboration currentCollaboration = consolidatedUserCollaborations.get(i);

          boolean swapNeeded = !currentCollaboration.getUsername1().equals(userToBeConsolidated.getUsername());
          if(swapNeeded){
            System.out.println("currentCollaboration before swap is: " + currentCollaboration.toString());
            currentCollaboration.swapUser(currentCollaboration.getUsername1(), currentCollaboration.getUsername2());
            System.out.println("currentCollaboration after swap is: " + currentCollaboration.toString());
            //TODO I can't tell if this persists yet...
          }
          //now we know for sure that userName1 of the collaboration is our userToBeConsolidated.getUsername()...
          if(Util.stringExists(userToRetain.getUsername())){
            Collaboration possibleCollabAlreadyExisting = Collaboration.collaborationBetweenUsers(myShepherd, currentCollaboration.getUsername2(), userToRetain.getUsername());
            if(possibleCollabAlreadyExisting!=null){
              //don't need to create a new one because it already exists
            }else{
              //create a new collab
              Collaboration newCollaboration = new Collaboration(userToRetain.getUsername(), currentCollaboration.getUsername2());
              myShepherd.getPM().makePersistent(newCollaboration);
              System.out.println("dedupe created new collaboration between user: "+userToRetain.toString()+" and user: "+currentCollaboration.getUsername2());
            }
          }
          //remove the old collab
          System.out.println("dedupe removing collaboration between user: " + currentCollaboration.getUsername1() + " and user: " +  currentCollaboration.getUsername2());
          myShepherd.throwAwayCollaboration(currentCollaboration);
        } //end for loop of consolidatedUserCollaborations
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      } //end if consolidatedUserCollaborations exists and has >0 elements
   }//end if userToBeConsolidated has no username
  }



  public static void consolidateEncounterSubmitters(Shepherd myShepherd, Encounter enc, User useMe, User userToRemove){
    if(!useMe.equals(userToRemove)){
      List<User> subs=enc.getSubmitters();
      if(subs!=null && userToRemove!=null && subs.contains(userToRemove)){
        System.out.println("dedupe removing user: " + userToRemove.toString() + " from submitters list in encounter: " + enc.toString());
        subs.remove(userToRemove);
      }
      if(subs!=null && useMe!=null && !subs.contains(useMe)){
        System.out.println("dedupe adding user: " + useMe.toString() + " to submitters list in encounter: " + enc.toString());
        subs.add(useMe);
      }
      enc.setSubmitters(subs);
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }

  public static void consolidateImportTaskCreator(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    String filter="SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE creator.uuid==\""+userToBeConsolidated.getUUID()+"\""; // && user.uuid==\""+userToBeConsolidated.getUUID()+"\" VARIABLES org.ecocean.User user"
    // System.out.println("dedupe query is: " + filter);
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
          System.out.println("dedupe consolidating import task: " + currentImportTask.toString() + " created by user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
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

  public static void consolidateRoles(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    if(Util.stringExists(userToBeConsolidated.getUsername())){
      //username appeared to be the only linking information from the USER_ROLES table, so any sql efforts felt analogous to using myShepherd.getAllRolesForUserInContext, especially given that this is not going to be a particularly time-consuming fetch nor repeated task for each user...
      List<Role> consolidatedUserRoles = myShepherd.getAllRolesForUserInContext(userToBeConsolidated.getUsername(), myShepherd.getContext());
      List<Role> retainedUserRoles = myShepherd.getAllRolesForUserInContext(userToRetain.getUsername(), myShepherd.getContext());
      if(consolidatedUserRoles!=null && consolidatedUserRoles.size()>0){
        for(int i=0; i<consolidatedUserRoles.size(); i++){
          Role currentRole = consolidatedUserRoles.get(i);
          if(!retainedUserRoles.contains(currentRole)){
            //it's a new role for the retained user; add it. Note: this because the role usernames are different, this will in effect capture all retainedUserRoles. But since username is converted downstream, this is not actually a bug. Might could be improved (slightly inefficient), but should work fine.
            if(Util.stringExists(userToRetain.getUsername())){
              currentRole.setUsername(userToRetain.getUsername());
              myShepherd.getPM().makePersistent(currentRole);
              System.out.println("dedupe adding role with username " + currentRole.getUsername() + " and role name: " + currentRole.getRolename() + " from user: " + userToBeConsolidated.toString() +" into user: " + userToRetain.toString());
            } //end if userToRetain name
          } //end if current role not found in retainedUserRoles
        } //end for loop of consolidatedUserRoles
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      } //end if consolidatedUserRoles exists and has >0 elements
   }//end if userToBeConsolidated has no username
  }

  public static void consolidateEncounterSubmitterIds(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    // System.out.println("dedupe consolidating encounter submitter IDs in encounters containing user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString()); //TODO comment out
    if(Util.stringExists(userToBeConsolidated.getUsername()) && Util.stringExists(userToRetain.getUsername())){ //can't look it up if userToBeConsolidated doesn't have username and can't change it to something if userToRetain doesn't have username
      String filter="SELECT FROM org.ecocean.Encounter WHERE this.submitterID=='" + userToBeConsolidated.getUsername() + "' ";
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
          if(currentEncounter!=null){
            System.out.println("dedupe changing submitterId for encounter "+ currentEncounter.toString() +" from user "+ userToBeConsolidated.toString() +" to user " + userToRetain.toString());
            currentEncounter.setSubmitterID(userToRetain.getUsername());
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
          }
        }
      }else{
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
  }

  public static void consolidateEncounterInformOthers(Shepherd myShepherd, User userToRetain, User userToBeConsolidated){
    // System.out.println("dedupe consolidating inform others in encounters containing user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
    String filter="SELECT FROM org.ecocean.Encounter WHERE this.informOthers.contains(user) && user.uuid=='" + userToBeConsolidated.getUUID() + "' VARIABLES org.ecocean.User user"; // && user.uuid==\""+userToBeConsolidated.getUUID()+"\" VARIABLES org.ecocean.User user"
    // System.out.println("dedupe query is: " + filter);
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
            System.out.println("dedupe removing user: " + userToBeConsolidated.toString() + " from informOthers in encounter: " + currentEncounter.toString());
            currentInformOthers.remove(userToBeConsolidated);
          }
          if(!currentInformOthers.contains(userToRetain)){
            System.out.println("dedupe adding user: " + userToRetain.toString() + " to informOthers in encounter: " + currentEncounter.toString());
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
        }
      }
      //TODO assign usernameless encounters to public maybe using the below, and maybe not
      // List<Encounter> usernameLessEncounters= getEncountersForUsersThatDoNotHaveUsernameButHaveSameEmailAddress(myShepherd,currentDupe);
  		// for(int j=0;j<usernameLessEncounters.size();j++){
  		// 	Encounter currentEncounter=usernameLessEncounters.get(j);
      //   // System.out.println("dedupe usernameless encounter with catalog number: " + currentEncounter.getCatalogNumber() + " by " + currentEncounter.getSubmitterEmail() + " with username: " + currentEncounter.getSubmitterID());
      //   consolidateUsernameless(myShepherd, currentEncounter, useMe, currentDupe);
  		// }

      List<Occurrence> submitterOccurrences = getOccurrencesForUser(persistenceManager, currentDupe);
      if(submitterOccurrences!=null && submitterOccurrences.size()>0){
        for(int j=0;j<submitterOccurrences.size(); j++){
          Occurrence currentOccurrence = submitterOccurrences.get(j);
          consolidateOccurrenceData(myShepherd, currentOccurrence, useMe, currentDupe);
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

  public static void consolidateOccurrenceData(Shepherd myShepherd, Occurrence currentOccurrence, User useMe, User currentDupe){
    // System.out.println("dedupe transferring submitterId in occurence " + currentOccurrence.toString() + " from user " + currentDupe.toString() + " to user " + useMe.toString());

    //reset submitterID if it matches the former user's username
    String currentOccurrenceSubmitterId = currentOccurrence.getSubmitterID();
    if(Util.stringExists(currentOccurrenceSubmitterId) && Util.stringExists(currentDupe.getUsername()) && currentDupe.getUsername().equals(currentOccurrenceSubmitterId)){
      if(Util.stringExists(useMe.getUsername())) {
        System.out.println("dedupe transferring submitterId in occurence " + currentOccurrence.toString() + " from user " + currentDupe.toString() + " to user " + useMe.toString());
        currentOccurrence.setSubmitterID(useMe.getUsername());
      }
    } //end if currentOccurrenceSubmitterId and currentDupe username exist and match

    //remove old user from submitters list and add new one if not already present
    List<User> currentSubmitters = currentOccurrence.getSubmitters();
    if(currentSubmitters!=null && currentSubmitters.size()>0){
      if(currentDupe!=null){
        System.out.println("dedupe removing user: " + currentDupe.toString() + " from submitters list in occurence " + currentOccurrence.toString());
        currentSubmitters.remove(currentDupe);
      }
      if(!currentSubmitters.contains(useMe)){
        System.out.println("dedupe adding user: " + useMe.toString() + " to submitters list in occurence " + currentOccurrence.toString());
        currentSubmitters.add(useMe);
      }
      currentOccurrence.setSubmitters(currentSubmitters);
    } // end if currentSubmitters not null and not empty

    //remove old user from inform others and add the new one in if it's missing
    List<User> currentInformOthers = currentOccurrence.getInformOthers();
    if(currentInformOthers!=null && currentInformOthers.size()>0){
      if(currentDupe!=null){
        System.out.println("dedupe removing user: " + currentDupe.toString() + " from informOthers list in occurence " + currentOccurrence.toString());
        currentInformOthers.remove(currentDupe);
      }
      if(!currentInformOthers.contains(useMe)){
        System.out.println("dedupe adding user: " + currentDupe.toString() + " to informOthers list in occurence " + currentOccurrence.toString());
        currentInformOthers.add(useMe);
      }
      currentOccurrence.setInformOthers(currentInformOthers);
    } // end if currentInformOthers not null and not empty
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
    // System.out.println("dedupe transferring photographer "+ currentUser.toString() +" in encounter "+ enc.toString() +" to photographer " + useMe.toString());
    if(!useMe.equals(currentUser)){
      List<User> photographers=enc.getPhotographers();
      if(photographers!=null && currentUser!=null && photographers.contains(currentUser)){
        System.out.println("dedupe removing photographer: " + currentUser.toString() + " from encounter:" + enc.toString());
        photographers.remove(currentUser);
      }
      if(photographers!=null && useMe !=null && !photographers.contains(useMe)){
        System.out.println("dedupe adding photographer: " + currentUser.toString() + " to encounter:" + enc.toString());
        photographers.add(useMe);
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

  public static List<Occurrence> getOccurrencesForUser(PersistenceManager persistenceManager, User user){
    // since a complicated join like "SELECT FROM org.ecocean.Occurrence where (submitters.contains(user) || informOthers.contains(user) ) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user" is not super tractable, we will go with three different database queries, and combine their results into one list
    List<Occurrence> allOccurrences=new ArrayList<Occurrence>();
    List<String> queryList = new ArrayList<String>();
    if(user!=null){
      String queryStringSubmitters = "SELECT FROM org.ecocean.Occurrence where submitters.contains(user) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
      queryList.add(queryStringSubmitters);
      String queryStringInformOthers = "SELECT FROM org.ecocean.Occurrence where informOthers.contains(user) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
      queryList.add(queryStringInformOthers);
      if(Util.stringExists(user.getUsername())){
        String queryStringSubmitterId = "SELECT FROM org.ecocean.Occurrence where submitterID==\"" + user.getUsername()+ "\" && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
        queryList.add(queryStringSubmitterId);
      }
      if(queryList!=null && queryList.size()>0){
        for(String currentQuery: queryList){
          // System.out.println("query in getOccurrencesForUser is: " + currentQuery);
          Query query=persistenceManager.newQuery(currentQuery);
          Collection c = (Collection) (query.execute());
          if(c!=null){
            List<Occurrence> currentOccurrences=new ArrayList<Occurrence>(c);
            System.out.println("there are " + currentOccurrences.size() + " occurrences in the getOccurrencesForUser search: " + currentQuery);
            allOccurrences.addAll(c);
          }
          query.closeAll();
        }
      }
    }
    System.out.println("SUCCESS!!! returning " + allOccurrences.size() + "occurrences…");
    System.out.println(allOccurrences.toString());
    return allOccurrences;
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

  public static JSONObject suspendLessCredentialedAccounts(Shepherd myShepherd){
    JSONObject returnJson = new JSONObject();
    returnJson.put("success", false);
    int suspensionCount = 0;
    List<User> allUsers = myShepherd.getAllUsers();
    int totalNumberOfUsers = allUsers.size();
    PersistenceManager persistenceManager = myShepherd.getPM();
    List<User> matchingUserCohort = new ArrayList<User>();
    for(User currentUser: allUsers){
      if(currentUser!=null && currentUser.getEmailAddress()!=null){
        //for each user, find other users with matching email addresses but non-matching, non-null usernames
        matchingUserCohort = getAllUsersWithEmailAddressWithOptAdditionalFilter(currentUser.getEmailAddress(), persistenceManager, " && username!=null && uuid!=\'" + currentUser.getUUID() + "\'");
        //including the currentUser, find highestCredentialedUser from the list
        matchingUserCohort.add(currentUser);
        User highestCredentialedMatch = getHighestCredentialedUserInList(matchingUserCohort, myShepherd);
        //for the others on the list without that uuid, change their email address to uuid@localhost
        for(User currentMatchingUserCohortMember: matchingUserCohort){
          if(currentMatchingUserCohortMember.getUUID() != highestCredentialedMatch.getUUID()){
            currentMatchingUserCohortMember.setEmailAddress(currentMatchingUserCohortMember.getUUID() + "@localhost");
            System.out.println("dedupe suspending user " + currentMatchingUserCohortMember.toString() + " by setting their email address to " + currentMatchingUserCohortMember.getUUID() + "@localhost");
            System.out.println("dedupe the one not getting suspended is: " + highestCredentialedMatch.toString());
            suspensionCount ++;
          }
        }
      }
    }
    returnJson.put("totalNumberOfUsers", totalNumberOfUsers);
    returnJson.put("numberOfAccountsSuspended", suspensionCount);
    returnJson.put("success", true);
    return returnJson;
  }

  public static JSONObject consolidateLessCompleteUsersWithTheirMoreCompleteCounterParts(Shepherd myShepherd){
    JSONObject returnJson = new JSONObject();
    returnJson.put("success", false);
    //find users with email addresses but no usernames
    List<User> usernamelessUsers = new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE emailAddress!=null && username==null";
    PersistenceManager persistenceManager = myShepherd.getPM();
    Query query = persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      usernamelessUsers=new ArrayList<User>(c);
    }
    if(usernamelessUsers != null && usernamelessUsers.size()>0){
      JSONArray consolidatedIncompleteUsers = new JSONArray();
      for(User currentUsernamelessUser: usernamelessUsers){
        //find similar user(s) with same email address but with a username
        List<User> similarUsers = getOtherUsersWithMatchingEmailAddress(currentUsernamelessUser, persistenceManager);
        if(similarUsers != null && similarUsers.size()>0){
          //find the user in that list with the highest credentials
          User highestCredentialedMatch = getHighestCredentialedUserInList(similarUsers, myShepherd);
          if(highestCredentialedMatch != null){
            //consolidate the currentUser into the found high-credential user
            System.out.println("dedupe about to consolidate usernameless user: " + currentUsernamelessUser.toString() + " with highestCredentialedMatch: " + highestCredentialedMatch.toString());
            // returnJson.put("numEncountersBeforeConsolidation", getNumberEncountersUsingSubmitterId(currentUsernamelessUser, persistenceManager));
            returnJson.put(currentUsernamelessUser.getUUID(), "username: " + currentUsernamelessUser.getUsername() + " being consolidated into user: " + highestCredentialedMatch.getUUID() + " with username: " + highestCredentialedMatch.getUsername());
            consolidateUser(myShepherd, highestCredentialedMatch, currentUsernamelessUser);
            // returnJson.put("numEncountersAfterConsolidation", getNumberEncountersUsingSubmitterId(currentUsernamelessUser, persistenceManager));
            JSONObject currentConsolidatedUserJson = new JSONObject();
            currentConsolidatedUserJson.put("uuid",currentUsernamelessUser.getUUID());
            currentConsolidatedUserJson.put("username", currentUsernamelessUser.getUsername());
            currentConsolidatedUserJson.put("into_uuid",highestCredentialedMatch.getUUID());
            currentConsolidatedUserJson.put("into_username", highestCredentialedMatch.getUsername());
            consolidatedIncompleteUsers.put(currentConsolidatedUserJson);
          }
        }
      }
      returnJson.put("conslidatedUsers", consolidatedIncompleteUsers);
    } else{
      System.out.println("dedupe ack there were no users in this list");
      returnJson.put("success", true);
      return returnJson;
    }
    returnJson.put("success", true);
    return returnJson;
  }

  public static List<User> getOtherUsersWithMatchingEmailAddress(User focalUser, PersistenceManager persistenceManager){ // now a wrapper method
    if(focalUser == null || focalUser.getUUID() == null || focalUser.getEmailAddress() == null) return null;
    return getAllUsersWithEmailAddressWithOptAdditionalFilter(focalUser.getEmailAddress(), persistenceManager, "&& this.uuid!=\'" + focalUser.getUUID() + "\'");
  }

  public static User getHighestCredentialedUserInList(List<User> candidateUsers, Shepherd myShepherd){
    if(candidateUsers == null) return null;
    if(candidateUsers.size() <1 ) return null;
    User currentBestCandidate = candidateUsers.get(0);
    PersistenceManager persistenceManager = myShepherd.getPM();
    for (User currentCandidate: candidateUsers){
      if(hasHigherRoleThan(currentCandidate,currentBestCandidate, myShepherd)){
        currentBestCandidate = currentCandidate;
        continue;
      } else{
        if(hasMoreEncountersThan(currentCandidate, currentBestCandidate, persistenceManager)){
          currentBestCandidate = currentCandidate;
          continue;
        }else{
          if(hasSignedInMoreRecentlyThan(currentCandidate, currentBestCandidate)){
            currentBestCandidate = currentCandidate;
            continue;
          }
        }
      }
    }
    return currentBestCandidate;
  }

  public static boolean hasHigherRoleThan(User userA, User userB, Shepherd myShepherd){ //note this will return true for instance if user a has roles[admin, orgAdmin, researcher] and user b only has roles [admin, orgAdmin].
    if(userA == null || userB == null || userA.getUsername() == null || userB.getUsername() == null) return false;
    List<Role> aRoles = myShepherd.getAllRolesForUserInContext(userA.getUsername(), myShepherd.getContext());
    List<String> aRoleNames = new ArrayList<String>();
    for(Role currentRole: aRoles){
      aRoleNames.add(currentRole.getRolename());
    }
    List<Role> bRoles = myShepherd.getAllRolesForUserInContext(userB.getUsername(), myShepherd.getContext());
    List<String> bRoleNames = new ArrayList<String>();
    for(Role currentRole: bRoles){
      bRoleNames.add(currentRole.getRolename());
    }
    List<String> roleHierarchy = new LinkedList<String>(); //ArrayBlockingQueue because of enforce FIFO structure
    roleHierarchy.add("admin"); //don't know how to make this anything but hard-coded, highest-in-hierarchy first
    roleHierarchy.add("orgAdmin");
    roleHierarchy.add("researcher");
    roleHierarchy.add("rest");
    roleHierarchy.add("machinelearning");
    for(String currentRoleBeingChecked: roleHierarchy){
      if(aRoleNames.contains(currentRoleBeingChecked) && !bRoleNames.contains(currentRoleBeingChecked)){
        return true;
      }
      if(bRoleNames.contains(currentRoleBeingChecked) && !aRoleNames.contains(currentRoleBeingChecked)){
        return false;
      }
    }
    return false;
  }

  public static boolean hasMoreEncountersThan(User userA, User userB, PersistenceManager persistenceManager){
    int sizeA = -1;
    int sizeB = -1;
    if(userA == null || userB == null || (userA.getUsername() == null && userB.getUsername() == null)){
      return false; //if it's indeterminable, it's false
    }
    if(userA.getUsername() != null){
      sizeA = getNumberEncountersUsingSubmitterId(userA, persistenceManager);
    }
    if(userB.getUsername() != null){
      sizeB = getNumberEncountersUsingSubmitterId(userB, persistenceManager);
    }
    if(sizeA > sizeB){
      return true;
    }
    return false;
  }

  public static int getNumberEncountersUsingSubmitterId(User user, PersistenceManager persistenceManager){
    int size = -1;
    if(user == null || user.getUsername() == null){
      return size; //if it's indeterminable, it's false
    } else{
      String filter="SELECT FROM org.ecocean.Encounter where this.submitterID==\"" + user.getUsername() + "\""; //don't check using email address because you are using this in an effort to deduplicate accounts with matching email addresses.
      List<Encounter> encs=new ArrayList<Encounter>();
      Query query = persistenceManager.newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){
        encs=new ArrayList<Encounter>(c);
        if(encs != null){
          size = encs.size();
        }
      }
      query.closeAll();
    }
    return size;
  }

  public static boolean hasSignedInMoreRecentlyThan(User userA, User userB){
    long lastLoginA = -1;
    long lastLoginB = -1;
    if(userA == null || userB == null || (userA.getUsername() == null && userB.getUsername() == null)) return false;
    lastLoginA = userA.getLastLogin();
    lastLoginB = userB.getLastLogin();
    DateTime lastLoginADate = new DateTime(lastLoginA);
    DateTime lastLoginBDate = new DateTime(lastLoginB);
    if(lastLoginADate.compareTo(lastLoginBDate) >0){
      return true;
    }else{
      return false;
    }
  }

  public static List<User> getAllUsersWithEmailAddressWithOptAdditionalFilter(String emailAddress, PersistenceManager persistenceManager, String additionalFilter){
    List<User> returnUsers=new ArrayList<User>();
    if(!"".equals(emailAddress) && emailAddress!=null){
      String filter = null;
      if(additionalFilter != null){
        filter="SELECT FROM org.ecocean.User where this.emailAddress.toLowerCase()==emailVal " + additionalFilter + " PARAMETERS String emailVal";
      }else{
        filter="SELECT FROM org.ecocean.User where this.emailAddress.toLowerCase()==emailVal  PARAMETERS String emailVal";
      }
      Query query = persistenceManager.newQuery(filter);
      Collection c = (Collection) (query.execute(emailAddress.toLowerCase())); //note for posterity: trying this query with ?1 variables or named variables was not successful.
      if(c!=null){
        returnUsers=new ArrayList<User>(c);
      }
      query.closeAll();
    }
    return returnUsers;
  }


  public static List<Encounter> getPhotographerEncountersForUser(PersistenceManager persistenceManager, User user){
    System.out.println("entered getPhotographerEncountersForUser");
  	String filter="SELECT FROM org.ecocean.Encounter where photographers.contains(user) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
    System.out.println("query in getPhotographerEncountersForUser is: " + filter);
  	List<Encounter> encs=new ArrayList<Encounter>();
    Query query= persistenceManager.newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      System.out.println("collection in getPhotographerEncountersForUser not null");
      encs=new ArrayList<Encounter>(c);
      System.out.println("encs are: " + encs.toString());
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

    //for automated user reconciliation
    //consolidate less complete accounts
    JSONObject consolidateLessCompleteAccountsResultsJson = new JSONObject();
    consolidateLessCompleteAccountsResultsJson.put("success", false);
    try{
      Boolean dedupeLessCompleteDesired = jsonRes.optBoolean("dedupeLessCompleteDesired", false);
      if(dedupeLessCompleteDesired){
        consolidateLessCompleteAccountsResultsJson = consolidateLessCompleteUsersWithTheirMoreCompleteCounterParts(myShepherd);
      }
      consolidateLessCompleteAccountsResultsJson.put("success", true);
    } catch (Exception e) {
        System.out.println("dedupe exception e while getting or consolidating users with less complete accounts.");
        e.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: Exception e while getting or consolidating users with less complete accounts.");
    } finally {
        System.out.println("dedupe closing ajax call for user consolidate with less complete accounts transaction.....");
        myShepherd.updateDBTransaction();
        returnJson.put("consolidateLessCompleteAccountsResultsJson", consolidateLessCompleteAccountsResultsJson);
    }

    //suspend lower-credentialed acccounts with matching email addresses and non-null usernames
    JSONObject suspendLowerCredAccountsResultsJson = new JSONObject();
    suspendLowerCredAccountsResultsJson.put("success", false);
    try{
      int suspendedUserCount = 0;
      Boolean suspendLessCredentialedDesired = jsonRes.optBoolean("suspendLessCredentialedDesired", false);
      if(suspendLessCredentialedDesired){
        suspendLowerCredAccountsResultsJson = suspendLessCredentialedAccounts(myShepherd);
      }
      suspendLowerCredAccountsResultsJson.put("success", true);
    } catch (Exception e) {
      System.out.println("dedupe exception while suspending less-credentialed user accounts.");
      e.printStackTrace();
      addErrorMessage(returnJson, "UserConsolidate: Exception while suspending less-credentialed user accounts.");
    } finally {
      System.out.println("dedupe closing ajax call for suspending less-credentialed user accounts transaction.....");
      myShepherd.updateDBTransaction();
      returnJson.put("suspendLowerCredAccountsResultsJson", suspendLowerCredAccountsResultsJson);
    }

    //assign orphaned encounters to Public user
    JSONObject makeEncountersMissingSubmittersPublicJsonResults = new JSONObject();
    makeEncountersMissingSubmittersPublicJsonResults.put("success", false);
    try{
      Boolean assignOrphanedEncountersToPublicDesired = jsonRes.optBoolean("assignOrphanedEncountersToPublicDesired", false);
      if(assignOrphanedEncountersToPublicDesired){
        //if there's not a user named Public with email address public@wildme.org, create one
        User publicUser = myShepherd.getUser("public");
        if(publicUser == null){
          //make it
          String publicUserName = "public";
          String publicUserEmail = "public@wildme.org";
          String publicFullName = "Public";
          String salt = ServletUtilities.getSalt().toHex();
          String hashedPassword = ServletUtilities.hashAndSaltPassword(Util.generateUUID(), salt); //admins can change this later
          publicUser = new User(publicUserName, hashedPassword, salt);
          publicUser.setEmailAddress(publicUserEmail);
          publicUser.setFullName(publicFullName);
          myShepherd.getPM().makePersistent(publicUser);
          myShepherd.updateDBTransaction();

        }
        //find all encounters where submitterID is null or "N/A" and change to Public
        makeEncountersMissingSubmittersPublicJsonResults = EncounterConsolidate.makeEncountersMissingSubmittersPublic(myShepherd);
        myShepherd.updateDBTransaction();
      }
      makeEncountersMissingSubmittersPublicJsonResults.put("success", true);
    } catch (Exception e) {
        System.out.println("dedupe exception while assigning encounters with submitterIDs of null or N/A to the public user");
        e.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: Exception while assigning encounters with submitterIDs of null or N/A to the public user.");
    } finally {
        System.out.println("dedupe closing ajax call for assigning encounters with submitterIDs of null or N/A to the public user.....");
        myShepherd.updateDBTransaction();
        returnJson.put("makeEncountersMissingSubmittersPublicJsonResults", makeEncountersMissingSubmittersPublicJsonResults);
    }


    //assign usernameless users to Anonymous
    JSONObject assignUsernamelessToAnonymousJsonResults = new JSONObject();
    assignUsernamelessToAnonymousJsonResults.put("success", false);
    try{
      int usernamelessCount = 0;
      Boolean renameUsernamelessToAnonymousDesired = jsonRes.optBoolean("renameUsernamelessToAnonymousDesired", false);
      if(renameUsernamelessToAnonymousDesired){
        List<User> usernamelessUsers = new ArrayList<User>();
        usernamelessUsers = findUsersWithNonNullEmailAddressesAndNoUsername(myShepherd);
        if(usernamelessUsers != null && usernamelessUsers.size() > 0){
          //for each of them, change the username and update
          for(User currentUser: usernamelessUsers){
            System.out.println("dedupe assigning Anonymous_+uuid to user: " + currentUser.getUUID());
            currentUser.setUsername("Anonymous_" + currentUser.getUUID());
            usernamelessCount ++;
            myShepherd.updateDBTransaction();
          }
        }
      }
      assignUsernamelessToAnonymousJsonResults.put("usernamelessCount", usernamelessCount);
      assignUsernamelessToAnonymousJsonResults.put("success", true);
    } catch (Exception e) {
        System.out.println("dedupe exception while renaming usernameless non null email users to anonymous_+uuid.");
        e.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: Exception while renaming usernameless non null email users to anonymous_+uuid.");
    } finally {
        System.out.println("dedupe closing ajax call for renaming usernameless non null email users to anonymous_+uuid.....");
        myShepherd.updateDBTransaction();
        returnJson.put("assignUsernamelessToAnonymousJsonResults", assignUsernamelessToAnonymousJsonResults);
    }

    //assign emailless or invalid emails to uuid@localhost
    JSONObject assignEmaillessOrInvalidEmailAddressesJsonResults = new JSONObject();
    assignEmaillessOrInvalidEmailAddressesJsonResults.put("success", false);
    try{
      int emailProblemUserCount = 0;
      Boolean suspendEmaillessOrInvalidEmailDesired = jsonRes.optBoolean("suspendEmaillessOrInvalidEmailDesired", false);
      if(suspendEmaillessOrInvalidEmailDesired){
        List<User> emailProblemUsers = new ArrayList<User>();
        emailProblemUsers = findUsersWithMissingOrInvalidEmailsButNonNullUsernames(myShepherd);
        if(emailProblemUsers != null && emailProblemUsers.size() > 0){
          for(User currentUser: emailProblemUsers){
            System.out.println("dedupe assigning email address " + currentUser.getUUID()+"@localhost" + " to user: " + currentUser.toString());
            currentUser.setEmailAddress(currentUser.getUUID() + "@localhost");
            emailProblemUserCount ++;
            myShepherd.updateDBTransaction();
          }
          assignEmaillessOrInvalidEmailAddressesJsonResults.put("emailProblemUserCount", emailProblemUserCount);
        }
      }
      assignEmaillessOrInvalidEmailAddressesJsonResults.put("success", true);
    } catch (Exception e) {
        System.out.println("dedupe exception while assigning email address uuid@localhost to emailless or invalid emailed but nonnull username users.");
        e.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: Exception while assigning email address uuid@localhost to emailless or invalid emailed but nonnull username users.");
    } finally {
        System.out.println("dedupe closing ajax call for assigning email address uuid@localhost to emailless or invalid emailed but nonnull username users.....");
        myShepherd.updateDBTransaction();
        returnJson.put("assignEmaillessOrInvalidEmailAddressesJsonResults", assignEmaillessOrInvalidEmailAddressesJsonResults);
    }

    //assign emailless and usernameless anonymous_uuid uuid@localhost
    JSONObject assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults = new JSONObject();
    assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults.put("success", false);
    try{
      int renameCounter = 0;
      Boolean suspendUsersMissingEmailAndUsernameDesired = jsonRes.optBoolean("suspendUsersMissingEmailAndUsernameDesired", false);
      if(suspendUsersMissingEmailAndUsernameDesired){
        List<User> worldsWorstUsers = new ArrayList<User>();
        worldsWorstUsers = findUsersWithMissingEmailAndUsername(myShepherd);
        if(worldsWorstUsers != null && worldsWorstUsers.size() > 0){
          for(User currentUser: worldsWorstUsers){
            System.out.println("dedupe assigning email address " + currentUser.getUUID()+"@localhost" + " and username: Anonymous_" + currentUser.getUUID() + " to user: " + currentUser.toString());
            currentUser.setEmailAddress(currentUser.getUUID() + "@localhost");
            currentUser.setUsername("Anonymous_" + currentUser.getUUID());
            renameCounter ++;
            myShepherd.updateDBTransaction();
          }
        }
      }
      assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults.put("renameCounter", renameCounter);
      assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults.put("success", true);
    } catch (Exception e) {
        System.out.println("dedupe exception while assigning email address uuid@localhost to emailless or invalid emailed but nonnull username users.");
        e.printStackTrace();
        addErrorMessage(returnJson, "UserConsolidate: Exception while assigning email address uuid@localhost to emailless or invalid emailed but nonnull username users.");
    } finally {
        System.out.println("dedupe closing ajax call for assigning email address uuid@localhost to emailless or invalid emailed but nonnull username users.....");
        myShepherd.updateDBTransaction();
        returnJson.put("assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults", assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults);
    }

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
      }

      //consolidate the user duplicates indicated by user
      if(mergeDesired==true && Util.stringExists(userName)){
        User currentUser = myShepherd.getUser(userName);
        if(currentUser!=null){
          successStatus = true;
          if(userInfoArr != null && userInfoArr.length()>0){
            for(int i = 0; i<userInfoArr.length(); i++){
              JSONObject currentUserToBeConsolidatedInfo = userInfoArr.getJSONObject(i);
              String currentUserToBeConsolidatedUuid = currentUserToBeConsolidatedInfo.optString("uuid", null);
              if(Util.stringExists(currentUserToBeConsolidatedUuid)){
                System.out.println("dedupe currentUserToBeConsolidatedUuid is: " + currentUserToBeConsolidatedUuid);
              }
              String currentUserToBeConsolidatedUsername = currentUserToBeConsolidatedInfo.optString("username", null);
              String currentUserToBeConsolidatedEmail = currentUserToBeConsolidatedInfo.optString("email", null);
              String currentUserToBeConsolidatedFullName = currentUserToBeConsolidatedInfo.optString("fullname", null);
              User userToBeConsolidated =  myShepherd.getUserByUUID(currentUserToBeConsolidatedUuid);
              if(userToBeConsolidated!=null){
                System.out.println("userToBeConsolidated identified by uuid: " + userToBeConsolidated.toString());
                //only found one match
                try{
                  consolidateUser(myShepherd, currentUser, userToBeConsolidated);
                  returnJson.put("details_" + currentUserToBeConsolidatedUuid+ "__" + currentUserToBeConsolidatedUsername+"__" + currentUserToBeConsolidatedEmail + "__" + currentUserToBeConsolidatedFullName,"SingleMatchFoundForUserAndConsdolidated");
                }
                  catch(Exception e){
                      e.printStackTrace();
                      System.out.println("dedupe error consolidating user: " + userToBeConsolidated.toString() + " into user: " + currentUser.toString());
                      successStatus = false;
                      returnJson.put("details_" + currentUserToBeConsolidatedUuid+ "__" + currentUserToBeConsolidatedUsername+"__" + currentUserToBeConsolidatedEmail + "__" + currentUserToBeConsolidatedFullName,"ErrorConsolidatingReportToStaff");
                  } finally{
                  }
              }else{
                //found more than one match or none.
                returnJson.put("details_" + currentUserToBeConsolidatedUuid+ "__" + currentUserToBeConsolidatedUsername+"__" + currentUserToBeConsolidatedEmail + "__" + currentUserToBeConsolidatedFullName,"FoundMoreThanOneMatchOrNoMatchesForUser");
                successStatus = false;
              }
            }
          }
          returnJson.put("success",successStatus);
        }
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
    Random randVar = new Random();
    int randIndex = randVar.nextInt(maxInd); //maxInd itself is excluded from nextInt
    return users.get(randIndex);
  }

  public static List<User> findUsersWithNonNullEmailAddressesAndNoUsername(Shepherd myShepherd){
    String filter="SELECT FROM org.ecocean.User WHERE this.username==null && this.emailAddress!=null";
    List<User> returnUsers=new ArrayList<User>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      returnUsers=new ArrayList<User>(c);
    }
    query.closeAll();
    return returnUsers;
  }

  public static List<User> findUsersWithMissingOrInvalidEmailsButNonNullUsernames(Shepherd myShepherd){
    String filter="SELECT FROM org.ecocean.User WHERE this.username!=null";
    List<User> nonNullUsernameUsers=new ArrayList<User>();
    List<User> returnUsers=new ArrayList<User>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      nonNullUsernameUsers=new ArrayList<User>(c);
    }
    query.closeAll();
    //only add users with invalid email addresses or missing ones to the returnUsers list
    for(User currentUserCandidate: nonNullUsernameUsers){
      if(currentUserCandidate.getEmailAddress() == null || !Util.isValidEmailAddress(currentUserCandidate.getEmailAddress())){
        returnUsers.add(currentUserCandidate);
      }
    }
    return returnUsers;
  }

  public static List<User> findUsersWithMissingEmailAndUsername(Shepherd myShepherd){
    String filter="SELECT FROM org.ecocean.User WHERE (this.username==null || this.username==\"N/A\" ) && this.emailAddress==null";
    List<User> returnUsers=new ArrayList<User>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      returnUsers=new ArrayList<User>(c);
    }
    query.closeAll();
    return returnUsers;
  }

}
