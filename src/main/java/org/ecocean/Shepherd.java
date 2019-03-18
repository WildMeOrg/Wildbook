/*
 * The Shepherd Project - A Mark-Recapture Framework
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import org.ecocean.grid.ScanTask;
import org.ecocean.grid.ScanWorkItem;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.genetics.*;
import org.ecocean.social .*;
import org.ecocean.security.Collaboration;
import org.ecocean.media.*;
import org.ecocean.ia.Task;
import org.ecocean.movement.Path;
import org.ecocean.movement.SurveyTrack;

import javax.jdo.*;
import javax.servlet.http.HttpServletRequest;

import java.util.*;
import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.cache.StoredQuery;


/**
 * <code>Shepherd</code>	is the main	information	retrieval, processing, and persistence class to	be used	for	all	shepherd project applications.
 * The <code>shepherd</code>	class interacts directly with the database and	all	persistent objects stored within it.
 * Any application seeking access to	whale shark	data must invoke an	instance of	shepherd first and use it to retrieve any data stored in the
 * database.
 * <p/>
 * While	a <code>shepherd</code>	object is easily invoked with a	single,	simple constructor,	no data
 * can be retrieved until a new Transaction has been	started. Changes made using the Transaction must be committed (store changes) or rolled back (ignore changes) before the application can finish.
 * Example:
 * <p align="center"><code>
 * <p/>
 * shepherd myShepherd=new shepherd();<br>
 * myShepherd.beginDBTransaction();
 * <p align="center">Now	make any changes to	the	database objects that are needed.
 * <p align="center">
 * <p/>
 * myShepherd.commitDBTransaction();
 * *myShepherd.closeDBTransaction();
 * </code>
 * <p/>
 *
 * @author Jason Holmberg
 * @version alpha-2
 * @see shark, encounter, superSpot,	spot
 */
public class Shepherd {

  private PersistenceManager pm;
  public static Vector matches = new Vector();
  //private PersistenceManagerFactory pmf;
  private String localContext;

  private String action="undefined";
  private String shepherdID="";


  /**
   * Constructor to create a new shepherd thread object
   */
  public Shepherd(String context) {
    if (pm == null || pm.isClosed()) {
      //PersistenceManagerFactory pmf = ShepherdPMF.getPMF(context);
      localContext=context;
      try {
        pm = ShepherdPMF.getPMF(localContext).getPersistenceManager();
        this.shepherdID=Util.generateUUID();

        ShepherdPMF.setShepherdState(action+"_"+shepherdID, "new");
      }
      catch (JDOUserException e) {
        System.out.println("Hit an excpetion while trying to instantiate a PM. Not fatal I think.");
        e.printStackTrace();
      }
      //pmf=null;
    }
  }

    public String getContext() {
        return localContext;
    }

  public PersistenceManager getPM() {
    return pm;
  }

  public String getDataDirectoryName() {
    return CommonConfiguration.getDataDirectoryName(getContext());
  }

  //public PersistenceManagerFactory getPMF() {
  //  return pmf;
  //}


  /**
   * Stores a new, unassigned encounter in the database for later retrieval and analysis.
   * Each new encounter is assigned a unique number which is also its unique retrievable ID in the database.
   * This method will be the primary method used for future web submissions to shepherd from web-based applications.
   *
   * @param enc the new, unassociated encounter to be considered for addition to the database
   * @return an Integer number that represents the unique number of this new encounter in the datatbase
   * @ see encounter
   */
  public String storeNewEncounter(Encounter enc, String uniqueID) {
    enc.setEncounterNumber(uniqueID);
    beginDBTransaction();
    try {
      pm.makePersistent(enc);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new encounter in shepherd.storeNewEncounter().");
      System.out.println("     uniqueID:" + uniqueID);
      e.printStackTrace();
      return "fail";
    }
    return (uniqueID);
  }

  public String storeNewAnnotation(Annotation enc) {
    //enc.setOccurrenceID(uniqueID);
    beginDBTransaction();
    try {
      pm.makePersistent(enc);
      commitDBTransaction();
      //System.out.println("I successfully persisted a new Annotation in Shepherd.storeNewAnnotation().");
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new Annotation in Shepherd.storeNewAnnotation().");
      e.printStackTrace();
      return "fail";
    }
    return (enc.getId());
}

  public String storeNewWorkspace(Workspace wSpace) {
    beginDBTransaction();
    try {
      pm.makePersistent(wSpace);
      commitDBTransaction();
      //System.out.println("I successfully persisted a new Workspace in Shepherd.storeNewWorkspace().");
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new workspace in shepherd.storeNewWorkspace().");
      System.out.println("     id:" + wSpace.id);
      e.printStackTrace();
      return "fail";
    }
    return (String.valueOf(wSpace.id));
  }


  public void storeNewOccurrence(Occurrence enc) {
      //enc.setOccurrenceID(uniqueID);
      beginDBTransaction();
      try {
        pm.makePersistent(enc);
        commitDBTransaction();
      } catch (Exception e) {
        rollbackDBTransaction();
        System.out.println("I failed to create a new Occurrence in shepherd.storeNewOccurrence().");
        e.printStackTrace();

      }
  }

  public void storeNewSurvey(Survey svy) {
    beginDBTransaction();
    try {
      pm.makePersistent(svy);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new Survey in shepherd.storeNewSurvey().");
      e.printStackTrace();
    }
  }
  
  public void storeNewSurveyTrack(SurveyTrack stk) {
    beginDBTransaction();
    try {
      pm.makePersistent(stk);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new SurveyTrack in shepherd.storeNewSurveyTrack().");
      e.printStackTrace();
    }
  }
  
  public void storeNewPath(Path pth) {
    beginDBTransaction();
    try {
      pm.makePersistent(pth);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new Path in shepherd.storeNewPath().");
      e.printStackTrace();
    }
  }
  
  public void storeNewPointLocation(PointLocation plc ) {
    beginDBTransaction();
    try {
      pm.makePersistent(plc);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new PointLocation in shepherd.storeNewPointLocation().");
      e.printStackTrace();
    }
  }

  public void storeNewObservation(Observation ob ) {
    beginDBTransaction();
    try {
      pm.makePersistent(ob);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new Observation in shepherd.storeNewObservation().");
      e.printStackTrace();
    }
  }

  public boolean storeNewMarkedIndividual(MarkedIndividual indie) {

    beginDBTransaction();
    try {
      pm.makePersistent(indie);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new MarkedIndividual in Shepherd.storeNewMarkedIndividual().");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public String storeNewAdoption(Adoption ad, String uniqueID) {
    beginDBTransaction();
    try {
      pm.makePersistent(ad);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new adoption in shepherd.storeNewAdoption().");
      System.out.println("     uniqueID:" + uniqueID);
      e.printStackTrace();
      return "fail";
    }
    return (uniqueID);
  }

  public String storeNewKeyword(Keyword kw) {
    beginDBTransaction();
    try {

      pm.makePersistent(kw);
      commitDBTransaction();
    } catch (Exception e) {
      rollbackDBTransaction();
      return "fail";
    }
    return "success";
  }


  public boolean storeNewScanTask(ScanTask scanTask) {
    beginDBTransaction();
    try {
      pm.makePersistent(scanTask);
      commitDBTransaction();
      return true;
    } catch (Exception e) {
      rollbackDBTransaction();
      e.printStackTrace();
      System.out.println("I failed to store the new ScanTask number: " + scanTask.getUniqueNumber());
      return false;
    }

  }

  public boolean storeNewTask(Task task) {
    beginDBTransaction();
    try {
      pm.makePersistent(task);
      commitDBTransaction();
      return true;
    } catch (Exception e) {
      rollbackDBTransaction();
      e.printStackTrace();
      System.out.println("I failed to store the new IA Task with ID: " + task.getId());
      return false;
    }
  }

  public boolean storeNewCollaboration(Collaboration collab) {
    beginDBTransaction();
    try {
      pm.makePersistent(collab);
      commitDBTransaction();
			return true;

    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new collaboration in shepherd.storeNewCollaboration().");
      e.printStackTrace();
      return false;
    }
  }
  
  


  /**
   * Removes an encounter from the database. ALL DATA FOR THE ENCOUNTER WILL BE LOST!!!
   *
   * @param enc the encounter to delete the database
   * @see Encounter
   */
  public void throwAwayEncounter(Encounter enc) {
    String number = enc.getEncounterNumber();
    pm.deletePersistent(enc);
  }

  public void throwAwayWorkspace(Workspace wSpace) {
    pm.deletePersistent(wSpace);
  }

  public void throwAwayTissueSample(TissueSample genSample) {
    //String removedParameters = genSample.getHTMLString();
    //List<GeneticAnalysis> list=genSample.getGeneticAnalyses();
    /*
    for(int i=0;i<list.size();i++){
      GeneticAnalysis gen=list.get(i);
      genSample.removeGeneticAnalysis(gen);
      pm.deletePersistent(gen);
      i--;
    }*/
    pm.deletePersistent(genSample);
    //return removedParameters;
  }
  public void throwAwayGeneticAnalysis(GeneticAnalysis analysis) {
    //String removedParameters = analysis.getHTMLString();
    pm.deletePersistent(analysis);
    //return removedParameters;
  }

  public void throwAwayMicrosatelliteMarkersAnalysis(MicrosatelliteMarkersAnalysis analysis) {
    //String removedParameters = analysis.getHTMLString();
    /*
    while(analysis.getLoci().size()>0){
      Locus l=analysis.getLoci().get(0);
      analysis.getLoci().remove(0);
      pm.deletePersistent(l);
    }
    */
    pm.deletePersistent(analysis);
    //return removedParameters;
  }

  public void throwAwayAdoption(Adoption ad) {
    String number = ad.getID();
    pm.deletePersistent(ad);
  }

  public void throwAwayKeyword(Keyword word) {
    String indexname = word.getIndexname();
    pm.deletePersistent(word);
  }

  public void throwAwayOccurrence(Occurrence word) {
    pm.deletePersistent(word);
  }


  public void throwAwaySuperSpotArray(SuperSpot[] spots) {
    if (spots != null) {
      for (int i = 0; i < spots.length; i++) {
        pm.deletePersistent(spots[i]);
      }
    }
  }

  /**
   * Removes a marked individual from the database.
   * ALL DATA FOR THE INDIVIDUAL WILL BE LOST!!!
   *
   * @param MarkedIndividual to delete from the database
   * @see MarkedIndividual
   */
  public void throwAwayMarkedIndividual(MarkedIndividual bye_bye_sharky) {
    //String name=bye_bye_sharky.getName();
    pm.deletePersistent(bye_bye_sharky);
  }

  public void throwAwayTask(ScanTask sTask) {
    String name = sTask.getUniqueNumber();

    //throw away the task
    pm.deletePersistent(sTask);
    //PersistenceManagerFactory pmf = ShepherdPMF.getPMF(localContext);
    ShepherdPMF.getPMF(localContext).getDataStoreCache().unpin(sTask);
    ShepherdPMF.getPMF(localContext).getDataStoreCache().evict(sTask);
    //pmf=null;
  }


  public Encounter getEncounter(String num) {
    Encounter tempEnc = null;
    try {
      tempEnc = ((Encounter) (pm.getObjectById(pm.newObjectIdInstance(Encounter.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempEnc;
  }
  
  public Annotation getAnnotation(String uuid) {
    Annotation annot = null;
    try {
      annot = ((Annotation) (pm.getObjectById(pm.newObjectIdInstance(Annotation.class, uuid.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return annot;
  }

  public MediaAsset getMediaAsset(String num) {
    MediaAsset tempMA = null;
    try {
      tempMA = ((MediaAsset) (pm.getObjectById(pm.newObjectIdInstance(MediaAsset.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempMA;
  }

  public MediaAssetSet getMediaAssetSet(String num) {
    MediaAssetSet tempMA = null;
    try {
      tempMA = ((MediaAssetSet) (pm.getObjectById(pm.newObjectIdInstance(MediaAssetSet.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempMA;
  }

  public Workspace getWorkspace(int id) {
    Workspace tempWork = null;
    try {
      tempWork = (Workspace) (pm.getObjectById(pm.newObjectIdInstance(Workspace.class, id)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempWork;
  }
  // finds the workspace that user 'owner' created and named 'name'
  public Workspace getWorkspaceForUser(String name, String owner) {
    String quotedOwner = (owner==null) ? "null" : ("\""+owner+"\"");
    String filter = "this.name == \""+name+"\" && this.owner == "+quotedOwner;
    Extent allWorkspaces = pm.getExtent(Workspace.class, true);
    Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
    Collection results = (Collection) (workspaceQuery.execute());
    if (!results.isEmpty()) {
      return (Workspace) (results.iterator().next());
    }
    return null;
  }

  // Returns all of a user's workspaces.
  public ArrayList<Workspace> getWorkspacesForUser(String owner) {
    String filter = "this.owner == \""+owner+"\"";
    if (owner==null) {
      filter = "this.owner == null";
    }
    Extent allWorkspaces = pm.getExtent(Workspace.class, true);
    Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
    workspaceQuery.setOrdering("accessed descending");

    try {
      Collection results = (Collection) (workspaceQuery.execute());
      ArrayList<Workspace> resultList = new ArrayList<Workspace>();
      if (results!=null) {
        resultList = new ArrayList<Workspace>(results);
      }
      workspaceQuery.closeAll();
      return resultList;
    } catch (Exception npe) {
      npe.printStackTrace();
      return null;
    }
  }

  // Returns all of a user's workspaces.
  public ArrayList<MediaAsset> getMediaAssetsForOwner(String owner) {

    String enquotedOwner = (owner==null || owner.equals("")) ? "null" : "\""+owner+"\"";
    String filter = "accessControl.username == " + enquotedOwner;

    Extent allMediaAssets = pm.getExtent(MediaAsset.class, true);
    Query mediaAssetQuery = pm.newQuery(allMediaAssets, filter);

    try {
      Collection results = (Collection) (mediaAssetQuery.execute());
      ArrayList<MediaAsset> resultList = new ArrayList<MediaAsset>();
      if (results!=null) {
        resultList = new ArrayList<MediaAsset>(results);
      }
      mediaAssetQuery.closeAll();
      return resultList;
    } catch (Exception npe) {
      npe.printStackTrace();
      return null;
    }
  }

  public ArrayList<MediaAsset> getMediaAssetsForOwner(String owner, String status) {

    String enquotedOwner = (owner==null || owner.equals("")) ? "null" : "\""+owner+"\"";

    String enquotedStatus = (status==null || status.equals("")) ? "null" : "\""+status+"\"";

    String filter = "accessControl.username == " + enquotedOwner +" && detectionStatus == "+enquotedStatus;
    Extent allMediaAssets = pm.getExtent(MediaAsset.class, true);
    Query mediaAssetQuery = pm.newQuery(allMediaAssets, filter);

    try {
      Collection results = (Collection) (mediaAssetQuery.execute());
      ArrayList<MediaAsset> resultList = new ArrayList<MediaAsset>();
      if (results!=null) {
        resultList = new ArrayList<MediaAsset>(results);
      }
      mediaAssetQuery.closeAll();
      return resultList;
    } catch (Exception npe) {
      npe.printStackTrace();
      return null;
    }
  }


  // like above but filters on Workspace.isImageSet
  /*
  public ArrayList<Workspace> getWorkspacesForUser(String owner, boolean isImageSet) {
    String quotedOwner = (owner==null) ? "null" : ("\""+owner+"\'");

    String isImageSetBit = isImageSet ? "1" : "0";

    String filter = "this.owner == "+quotedOwner+" && this.isImageSet == "+isImageSetBit;

    Extent allWorkspaces = pm.getExtent(Workspace.class, true);
    Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
    workspaceQuery.setOrdering("accessed descending");

    try {
      Collection results = (Collection) (workspaceQuery.execute());
      ArrayList<Workspace> resultList = new ArrayList<Workspace>();
      if (results!=null) {
        resultList = new ArrayList<Workspace>(results);
      }
      workspaceQuery.closeAll();
      return resultList;
    } catch (Exception npe) {
      npe.printStackTrace();
      return null;
    }
  }*/
  public ArrayList<Workspace> getWorkspacesForUser(String owner, boolean isImageSet) throws JSONException {
    ArrayList<Workspace> unfilteredSpaces = getWorkspacesForUser(owner);
    ArrayList<Workspace> filteredSpaces = new ArrayList<Workspace>();
    for (Workspace wSpace : unfilteredSpaces) {
      if (wSpace!=null && (wSpace.computeIsImageSet() == isImageSet)) {filteredSpaces.add(wSpace);}
    }
    return filteredSpaces;
  }

  public Relationship getRelationship(String type, String indie1,String indie2) {
    Relationship tempRel = null;
    String filter = "this.type == \""+type+"\" && ((this.markedIndividualName1 == \""+indie1+"\" && this.markedIndividualName2 == \""+indie2+"\") || (this.markedIndividualName1 == \""+indie2+"\" && this.markedIndividualName2 == \""+indie1+"\"))";
    Extent encClass = pm.getExtent(Relationship.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
        Collection c = (Collection) (acceptedEncounters.execute());
        Iterator it = c.iterator();
        while(it.hasNext()){
          Relationship ts=(Relationship)it.next();
          acceptedEncounters.closeAll();
          return ts;
        }
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    acceptedEncounters.closeAll();
    return null;
  }


  public Relationship getRelationship(String type, String indie1,String indie2, String indieRole1, String indieRole2) {
    Relationship tempRel = null;
    String filter = "this.type == \""+type+"\" && this.markedIndividualName1 == \""+indie1+"\" && this.markedIndividualName2 == \""+indie2+"\" && this.markedIndividualRole1 == \""+indieRole1+"\" && this.markedIndividualRole2 == \""+indieRole2+"\"";
    Extent encClass = pm.getExtent(Relationship.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
        Collection c = (Collection) (acceptedEncounters.execute());
        Iterator it = c.iterator();
        while(it.hasNext()){
          Relationship ts=(Relationship)it.next();
          acceptedEncounters.closeAll();
          return ts;
        }
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    acceptedEncounters.closeAll();
    return null;
  }

  public Relationship getRelationship(String type, String indie1,String indie2, String indieRole1, String indieRole2, String relatedCommunityName) {
    Relationship tempRel = null;
    String filter = "this.type == \""+type+"\" && this.markedIndividualName1 == \""+indie1+"\" && this.markedIndividualName2 == \""+indie2+"\" && this.markedIndividualRole1 == \""+indieRole1+"\" && this.markedIndividualRole2 == \""+indieRole2+"\" && this.relatedSocialUnitName == \""+relatedCommunityName+"\"";
    Extent encClass = pm.getExtent(Relationship.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
        Collection c = (Collection) (acceptedEncounters.execute());
        Iterator it = c.iterator();
        while(it.hasNext()){
          Relationship ts=(Relationship)it.next();
          acceptedEncounters.closeAll();
          return ts;
        }
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    acceptedEncounters.closeAll();
    return null;
  }


  public SocialUnit getCommunity(String name) {
    SocialUnit tempCom = null;
    try {
      tempCom = ((SocialUnit) (pm.getObjectById(pm.newObjectIdInstance(SocialUnit.class, name.trim()), true)));
    }
    catch (Exception nsoe) {
      return null;
    }
    return tempCom;
  }

  public SinglePhotoVideo getSinglePhotoVideo(String num) {
    SinglePhotoVideo tempEnc = null;
    try {
      tempEnc = ((SinglePhotoVideo) (pm.getObjectById(pm.newObjectIdInstance(SinglePhotoVideo.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempEnc;
  }

  public Role getRole(String rolename, String username, String context) {

    List<Role> roles = getAllRoles();
    int numRoles=roles.size();
    for(int i=0;i<numRoles;i++) {
      Role kw = (Role) roles.get(i);
      if((kw.getRolename().equals(rolename))&&(kw.getUsername().equals(username))&&(kw.getContext().equals(context))){
        return kw;
        }
    }
    return null;
  }

  public List<Role> getAllRolesForUserInContext(String username, String context) {
    String actualContext="context0";
    if(context!=null){actualContext=context;}
    String filter = "this.username == '" + username + "' && this.context == '"+actualContext+"'";
    Extent encClass = pm.getExtent(Role.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList<Role> roles=new ArrayList<Role>();
    if(c!=null){roles=new ArrayList<Role>(c);}
    acceptedEncounters.closeAll();
    return roles;
  }

  public ArrayList<Role> getAllRolesForUser(String username) {
    String filter = "this.username == '" + username + "'";
    Extent encClass = pm.getExtent(Role.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList<Role> roles=new ArrayList<Role>(c);
    acceptedEncounters.closeAll();
    return roles;
  }

  public boolean doesUserHaveRole(String username, String rolename, String context) {
    String filter = "this.username == '" + username + "' && this.rolename == '" + rolename + "' && this.context == '"+context+"'";
    Extent encClass = pm.getExtent(Role.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    int size=c.size();
    acceptedEncounters.closeAll();
    if(size>0){return true;}
    return false;
  }

  public boolean doesUserHaveAnyRoleInContext(String username, String context) {
    String filter = "this.username == '" + username + "' && this.context == '"+context+"'";
    Extent encClass = pm.getExtent(Role.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    int size=c.size();
    acceptedEncounters.closeAll();
    if(size>0){return true;}
    return false;
  }

  public String getAllRolesForUserAsString(String username) {
    String filter = "this.username == '" + username + "'";
    Extent encClass = pm.getExtent(Role.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList<Role> roles=new ArrayList<Role>(c);
    int numRoles=roles.size();
    String rolesFound="";
    for(int i=0;i<numRoles;i++){
      String context="context0";
      if(roles.get(i).getContext()!=null){context=roles.get(i).getContext();}
      String contextName=ContextConfiguration.getNameForContext(context);
      rolesFound+=(contextName+":"+roles.get(i).getRolename()+"\r");
    }
    acceptedEncounters.closeAll();
    return rolesFound;
  }

  
  
  public User getUser(String username) {
    User user= null;
    String filter="SELECT FROM org.ecocean.User WHERE username == \""+username.trim()+"\"";   
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    Iterator it = c.iterator();
    if(it.hasNext()){
      user=(User)it.next();
    }
    query.closeAll();
    return user;
  }
  
  
  public List<User> getUsersWithUsername() {
    return getUsersWithUsername("username ascending");
  }
  
  public List<User> getUsersWithUsername(String ordering) {
    List<User> users=null;
    String filter="SELECT FROM org.ecocean.User WHERE username != null";   
    Query query=getPM().newQuery(filter);
    query.setOrdering(ordering);
    Collection c = (Collection) (query.execute());
    users=new ArrayList<User>(c);
    query.closeAll();
    return users;
  }
  

  
  public User getUserByUUID(String uuid) {
    User user= null;
    try {
      user = ((User) (pm.getObjectById(pm.newObjectIdInstance(User.class, uuid.trim()), true)));
    }
    catch (Exception nsoe) {
      return null;
    }
    return user;
  }

  
  
  
  public TissueSample getTissueSample(String sampleID, String encounterNumber) {
    TissueSample tempEnc = null;
    String filter = "this.sampleID == \""+sampleID+"\" && this.correspondingEncounterNumber == \""+encounterNumber+"\"";

    Extent encClass = pm.getExtent(TissueSample.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
		  TissueSample ts=(TissueSample)it.next();
		  acceptedEncounters.closeAll();
          return ts;
      }
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    acceptedEncounters.closeAll();
    return null;
  }

  public MitochondrialDNAAnalysis getMitochondrialDNAAnalysis(String sampleID, String encounterNumber, String analysisID) {
    try {
      MitochondrialDNAAnalysis mtDNA = (MitochondrialDNAAnalysis)getGeneticAnalysis(sampleID, encounterNumber, analysisID, "MitochondrialDNA");
      return mtDNA;
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
  }

  public SexAnalysis getSexAnalysis(String sampleID, String encounterNumber, String analysisID) {
    try {
      SexAnalysis mtDNA = (SexAnalysis)getGeneticAnalysis(sampleID, encounterNumber, analysisID, "SexAnalysis");
      return mtDNA;
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
  }
  public SexAnalysis getSexAnalysis(String analysisID) {
    try {
      SexAnalysis mtDNA = (SexAnalysis)getGeneticAnalysis(analysisID, "SexAnalysis");
      return mtDNA;
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
}
  public BiologicalMeasurement getBiologicalMeasurement(String sampleID, String encounterNumber, String analysisID) {
    try {
      BiologicalMeasurement mtDNA = (BiologicalMeasurement)getGeneticAnalysis(sampleID, encounterNumber, analysisID, "BiologicalMeasurement");
      return mtDNA;
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
  }


  public MicrosatelliteMarkersAnalysis getMicrosatelliteMarkersAnalysis(String sampleID, String encounterNumber, String analysisID) {
    try {
      MicrosatelliteMarkersAnalysis msDNA = (MicrosatelliteMarkersAnalysis)getGeneticAnalysis(sampleID, encounterNumber, analysisID, "MicrosatelliteMarkers");
      return msDNA;
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
  }
  public MicrosatelliteMarkersAnalysis getMicrosatelliteMarkersAnalysis(String analysisID) {
    try {
      MicrosatelliteMarkersAnalysis msDNA = (MicrosatelliteMarkersAnalysis)getGeneticAnalysis(analysisID, "MicrosatelliteMarkers");
      return msDNA;
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
  }





  public Adoption getAdoption(String num) {
    Adoption tempEnc = null;
    try {
      tempEnc = ((Adoption) (pm.getObjectById(pm.newObjectIdInstance(Adoption.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempEnc;
  }

  public <T extends DataCollectionEvent> T findDataCollectionEvent(Class<T> clazz, String num) {
    T dataCollectionEvent = null;
    try {
      dataCollectionEvent = (T) pm.getObjectById((pm.newObjectIdInstance(clazz, num.trim())), true);
    } catch (Exception e) {
    }
    return dataCollectionEvent;
  }

  public <T extends GeneticAnalysis> T findGeneticAnalysis(Class<T> clazz, String num) {
    T dataCollectionEvent = null;
    try {
      dataCollectionEvent = (T) pm.getObjectById((pm.newObjectIdInstance(clazz, num.trim())), true);
    } catch (Exception e) {
    }
    return dataCollectionEvent;
  }


  public Encounter getEncounterDeepCopy(String num) {
    if (isEncounter(num)) {
      Encounter tempEnc = getEncounter(num.trim());
      Encounter transmitEnc = null;
      try {
        transmitEnc = (Encounter) pm.detachCopy(tempEnc);
      } catch (Exception e) {
      }
      return transmitEnc;
    } else {
      return null;
    }
  }

  public Adoption getAdoptionDeepCopy(String num) {
    if (isAdoption(num)) {
      Adoption tempEnc = getAdoption(num.trim());
      Adoption transmitEnc = null;
      try {
        transmitEnc = (Adoption) pm.detachCopy(tempEnc);
      } catch (Exception e) {
      }
      return transmitEnc;
    } else {
      return null;
    }
  }


  public Keyword getKeywordDeepCopy(String name) {
    if (isKeyword(name)) {
      Keyword tempWord = ((Keyword) (getKeyword(name.trim())));
      Keyword transmitWord = null;
      try {
        transmitWord = (Keyword) (pm.detachCopy(tempWord));
      } catch (Exception e) {
      }
      return transmitWord;
    } else {
      return null;
    }
  }


  public ScanTask getScanTask(String uniqueID) {
    ScanTask tempTask = null;
    try {
      tempTask = ((ScanTask) (pm.getObjectById(pm.newObjectIdInstance(ScanTask.class, uniqueID.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return tempTask;
  }

  public Taxonomy getOrCreateTaxonomy(String scientificName) {
    return getOrCreateTaxonomy(scientificName, true); // shepherds are for committing after all
  }
  public Taxonomy getOrCreateTaxonomy(String scientificName, boolean commit) {
    Taxonomy taxy = getTaxonomy(scientificName);
    if (taxy==null) taxy = new Taxonomy(scientificName);
    if (commit) storeNewTaxonomy(taxy);
    return taxy;
  }
  public Taxonomy getTaxonomy(String scientificName) {
    List al = new ArrayList();
    try{
      String filter = "this.scientificName.toLowerCase() == \"" + scientificName.toLowerCase() + "\"";
      Extent keyClass = pm.getExtent(Taxonomy.class, true);
      Query acceptedKeywords = pm.newQuery(keyClass, filter);
      Collection c = (Collection) (acceptedKeywords.execute());
      al = new ArrayList(c);
      try {
        acceptedKeywords.closeAll();
      } catch (NullPointerException npe) {}
    }
    catch(Exception e){e.printStackTrace();}
    return ((al.size()>0) ? ((Taxonomy) al.get(0)) : null);
  }
  public String storeNewTaxonomy(Taxonomy enc) {
    //enc.setOccurrenceID(uniqueID);
    beginDBTransaction();
    try {
      pm.makePersistent(enc);
      commitDBTransaction();
      //System.out.println("I successfully persisted a new Taxonomy in Shepherd.storeNewAnnotation().");
    } catch (Exception e) {
      rollbackDBTransaction();
      System.out.println("I failed to create a new Taxonomy in Shepherd.storeNewAnnotation().");
      e.printStackTrace();
      return "fail";
    }
    return (enc.getId());
  }



  public Keyword getKeyword(String readableName) {

    Iterator<Keyword> keywords = getAllKeywords();
	while (keywords.hasNext()) {
      Keyword kw = keywords.next();
      if((kw.getReadableName().equals(readableName))||(kw.getIndexname().equals(readableName))){return kw;}
  	}
  return null;

  }
  public Keyword getOrCreateKeyword(String name) {
    Keyword kw = getKeyword(name);
    if (kw==null) {
      kw = new Keyword(name);
      storeNewKeyword(kw);
    }
    return kw;
  }




  public List<String> getKeywordsInCommon(String encounterNumber1, String encounterNumber2) {
    ArrayList<String> inCommon = new ArrayList<String>();
    Encounter enc1 = getEncounter(encounterNumber1);
    Encounter enc2 = getEncounter(encounterNumber2);

    Iterator<Keyword> keywords = getAllKeywords();
    while (keywords.hasNext()) {
      Keyword kw = keywords.next();

      //if ((kw.isMemberOf(enc1)) && (kw.isMemberOf(enc2))) {
      if (enc1.hasKeyword(kw) && enc2.hasKeyword(kw)) {
        inCommon.add(kw.getReadableName());
      }


    }
    return inCommon;
  }

  public boolean isSurvey(String num) {
    try {
      Survey tempSvy = ((org.ecocean.Survey) (pm.getObjectById(pm.newObjectIdInstance(Survey.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }
  
  public boolean isSurveyTrack(String num) {
    try {
      SurveyTrack tempTrack = ((org.ecocean.movement.SurveyTrack) (pm.getObjectById(pm.newObjectIdInstance(SurveyTrack.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }

  public boolean isEncounter(String num) {
    try {
      Encounter tempEnc = ((org.ecocean.Encounter) (pm.getObjectById(pm.newObjectIdInstance(Encounter.class, num.trim()), true)));
    } catch (Exception nsoe) {
      //nsoe.printStackTrace();
      return false;
    }
    return true;
  }

  public boolean isWorkspace(String num) {
    try {
      Workspace tempSpace = ((org.ecocean.Workspace) (pm.getObjectById(pm.newObjectIdInstance(Workspace.class, num.trim()), true)));
    } catch (Exception nsoe) {
      //nsoe.printStackTrace();
      return false;
    }
    return true;
  }


  public boolean isCommunity(String comName) {
    try {
      SocialUnit tempCom = ((org.ecocean.social.SocialUnit) (pm.getObjectById(pm.newObjectIdInstance(SocialUnit.class, comName.trim()), true)));
    }
    catch (Exception nsoe) {
      return false;
    }
    return true;
  }




  public boolean isTissueSample(String sampleID, String encounterNumber) {
    TissueSample tempEnc = null;
    String filter = "this.sampleID == \""+sampleID+"\" && this.correspondingEncounterNumber == \""+encounterNumber+"\"";

    Extent encClass = pm.getExtent(TissueSample.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
		 acceptedEncounters.closeAll();
        return true;
      }
      acceptedEncounters.closeAll();
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return false;
    }
    return false;
  }

  //TBD - need separate for haplotype and ms markers
  public boolean isGeneticAnalysis(String sampleID, String encounterNumber, String analysisID, String type) {
    TissueSample tempEnc = null;
    String filter = "this.analysisType == \""+type+"\" && this.analysisID == \""+analysisID+"\" && this.sampleID == \""+sampleID+"\" && this.correspondingEncounterNumber == \""+encounterNumber+"\"";

    Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
		  acceptedEncounters.closeAll();
        return true;
      }
      acceptedEncounters.closeAll();
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return false;
    }
    return false;
  }
  public boolean isGeneticAnalysis(String analysisID) {
    return (getGeneticAnalysis(analysisID)!=null);
  }

  public GeneticAnalysis getGeneticAnalysis(String analysisID) {
    String filter = "this.analysisID == \""+analysisID+"\"";

    Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
      GeneticAnalysis gen=(GeneticAnalysis)it.next();
      acceptedEncounters.closeAll();
        return gen;
      }
      acceptedEncounters.closeAll();
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    return null;
  }

  public GeneticAnalysis getGeneticAnalysis(String sampleID, String encounterNumber, String analysisID) {
    String filter = "this.analysisID == \""+analysisID+"\" && this.sampleID == \""+sampleID+"\" && this.correspondingEncounterNumber == \""+encounterNumber+"\"";

    Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
		  GeneticAnalysis gen=(GeneticAnalysis)it.next();
		  acceptedEncounters.closeAll();
        return gen;
      }
      acceptedEncounters.closeAll();
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    return null;
  }
  public GeneticAnalysis getGeneticAnalysis(String analysisID, String type) {
    String filter = "this.analysisType == \""+type+"\" && this.analysisID == \""+analysisID+"\"";
        Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
      GeneticAnalysis gen=(GeneticAnalysis)it.next();
      acceptedEncounters.closeAll();
        return gen;
      }
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    acceptedEncounters.closeAll();
    return null;
}
  public GeneticAnalysis getGeneticAnalysis(String sampleID, String encounterNumber, String analysisID, String type) {
    String filter = "this.analysisType == \""+type+"\" && this.analysisID == \""+analysisID+"\" && this.sampleID == \""+sampleID+"\" && this.correspondingEncounterNumber == \""+encounterNumber+"\"";
	      Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {

      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();
      while(it.hasNext()){
		  GeneticAnalysis gen=(GeneticAnalysis)it.next();
		  acceptedEncounters.closeAll();
        return gen;
      }
    }
    catch (Exception nsoe) {
      nsoe.printStackTrace();
      acceptedEncounters.closeAll();
      return null;
    }
    acceptedEncounters.closeAll();
    return null;
  }

  public boolean isAdoption(String num) {
    try {
      Adoption tempEnc = ((org.ecocean.Adoption) (pm.getObjectById(pm.newObjectIdInstance(Adoption.class, num.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }

  public boolean isKeyword(String keywordDescription) {
    Iterator<Keyword> keywords = getAllKeywords();
	    while (keywords.hasNext()) {
      Keyword kw = keywords.next();
      if(kw.getReadableName().equals(keywordDescription)){return true;}
 	}

    return false;
  }

  public boolean isSinglePhotoVideo(String indexname) {
    try {
      SinglePhotoVideo tempEnc = ((org.ecocean.SinglePhotoVideo) (pm.getObjectById(pm.newObjectIdInstance(SinglePhotoVideo.class, indexname.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }

  public boolean isScanTask(String uniqueID) {
    try {
      ScanTask tempEnc = ((org.ecocean.grid.ScanTask) (pm.getObjectById(pm.newObjectIdInstance(ScanTask.class, uniqueID.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }


  public boolean isMarkedIndividual(String name) {
    try {
      MarkedIndividual tempShark = ((org.ecocean.MarkedIndividual) (pm.getObjectById(pm.newObjectIdInstance(MarkedIndividual.class, name.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }
  public boolean isMarkedIndividual(MarkedIndividual ind) {
    return (ind!=null && isMarkedIndividual(ind.getIndividualID()));
  }

  public boolean isOccurrence(String name) {
    try {
      Occurrence tempShark = ((org.ecocean.Occurrence) (pm.getObjectById(pm.newObjectIdInstance(Occurrence.class, name.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }
  public boolean isOccurrence(Occurrence occ) {
    return (occ!=null && isOccurrence(occ.getOccurrenceID()));
  }
  
  public boolean isPath(String name) {
    try {
      Path tempPath = ((org.ecocean.movement.Path) (pm.getObjectById(pm.newObjectIdInstance(Path.class, name.trim()), true)));
    } catch (Exception nsoe) {
      return false;
    }
    return true;
  }

  public boolean isRelationship(String type, String markedIndividualName1, String markedIndividualName2, String markedIndividualRole1, String markedIndividualRole2, boolean checkBidirectional) {
    try {

      if(getRelationship(type, markedIndividualName1,markedIndividualName2, markedIndividualRole1, markedIndividualRole2)!=null){
        return true;
      }
      //if requested by checkBidirectional attribute, also check for the inverse of this relationship
      if(checkBidirectional && (getRelationship(type, markedIndividualName2,markedIndividualName1, markedIndividualRole2, markedIndividualRole1)!=null)){
        return true;
      }

    }
    catch (Exception nsoe) {
      return false;
    }
    return false;
  }




  public boolean isRelationship(String type, String markedIndividualName1, String markedIndividualName2, String markedIndividualRole1, String markedIndividualRole2, String relatedCommunityName, boolean checkBidirectional) {
    try {

      if(getRelationship(type, markedIndividualName1,markedIndividualName2, markedIndividualRole1, markedIndividualRole2, relatedCommunityName)!=null){
        return true;
      }
      //if requested by checkBidirectional attribute, also check for the inverse of this relationship
      if(checkBidirectional && (getRelationship(type, markedIndividualName2,markedIndividualName1, markedIndividualRole2, markedIndividualRole1, relatedCommunityName)!=null)){
        return true;
      }

    }
    catch (Exception nsoe) {
      return false;
    }
    return false;
  }


  /**
   * Adds a new shark to the database
   *
   * @param newShark the new shark to be added to the database
   * @see MarkedIndividual
   */
  public boolean addMarkedIndividual(MarkedIndividual newShark) {
    return storeNewMarkedIndividual(newShark);
  }


  /**
   * Retrieves any unassigned encounters that are stored in the database - but not yet analyzed - to see whether they represent new or already persistent sharks
   *
   * @return an Iterator of shark encounters that have yet to be assigned shark status or assigned to an existing shark in the database
   * @see encounter, java.util.Iterator
   */
  public Iterator getUnassignedEncounters() {
    String filter = "this.individualID == null";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query orphanedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (orphanedEncounters.execute());
    return c.iterator();
  }

  /*
  public Iterator getUnassignedEncountersIncludingUnapproved() {
    String filter = "this.individualID == null";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query orphanedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (orphanedEncounters.execute());
    return c.iterator();
  }
  */

  public Iterator getUnassignedEncountersIncludingUnapproved(Query orphanedEncounters) {
    String filter = "this.individualID == null && this.state != \"unidentifiable\"";
    //Extent encClass=pm.getExtent(encounter.class, true);
    orphanedEncounters.setFilter(filter);
    Collection c = (Collection) (orphanedEncounters.execute());
    return c.iterator();
  }

  public Iterator<Encounter> getAllEncountersNoFilter() {
    /*Collection c;
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass);
    try {
      c = (Collection) (acceptedEncounters.execute());
      //ArrayList list = new ArrayList(c);
      Iterator it = c.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncountersNoFilter. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      return null;
    }*/
    return getAllEncountersNoQuery();
  }

  public Vector getAllEncountersNoFilterAsVector() {
    Collection c;
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass);
    try {
      c = (Collection) (acceptedEncounters.execute());
      Vector list = new Vector(c);
      acceptedEncounters.closeAll();
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncountersNoFilter. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<Encounter> getAllEncountersNoQuery() {
    try {
      Extent encClass = pm.getExtent(Encounter.class, true);
      Iterator it = encClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncountersNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<Occurrence> getAllOccurrencesNoQuery() {
    try {
      Extent encClass = pm.getExtent(Occurrence.class, true);
      Iterator it = encClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllOccurrencesNoQuery. Returning a null iterator.");
      return null;
    }
  }


  public Iterator<Taxonomy> getAllTaxonomies() {
    try {
      Extent taxClass = pm.getExtent(Taxonomy.class, true);
      Iterator it = taxClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllTaxonomies. Returning a null iterator.");
      return null;
    }
  }
  public int getNumTaxonomies() {
    Iterator<Taxonomy> taxis = getAllTaxonomies();
    return (Util.count(taxis));
  }
  public List<String> getAllTaxonomyNames() {
    Iterator<Taxonomy> allTaxonomies = getAllTaxonomies();
    Set<String> allNames = new HashSet<String>();
    while (allTaxonomies.hasNext()) {
      Taxonomy taxy = allTaxonomies.next();
      allNames.add(taxy.getScientificName());
    }
    List<String> configNames = CommonConfiguration.getIndexedPropertyValues("genusSpecies", getContext());
    allNames.addAll(configNames);

    List<String> allNamesList = new ArrayList<String>(allNames);
    java.util.Collections.sort(allNamesList);
    //return (allNamesList);
    return (configNames);
  }


  
  public Iterator<Survey> getAllSurveysNoQuery() {
    try {
      Extent svyClass = pm.getExtent(Survey.class, true);
      Iterator it = svyClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllSurveysNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }


  public Iterator getAllAnnotationsNoQuery() {
    try {
      Extent annClass = pm.getExtent(Annotation.class, true);
      Iterator it = annClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllAnnotationsNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }


  public Iterator getAllMediaAssets() {
    try {
      Extent maClass = pm.getExtent(MediaAsset.class, true);
      Iterator it = maClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllMediaAssets. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }
  
  public ArrayList<MediaAsset> getAllMediaAssetsAsArray() {
    try {
      Extent maClass = pm.getExtent(MediaAsset.class, true);
      ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
      MediaAsset ma = null;
      Iterator it = maClass.iterator();
      while (it.hasNext()) {
        ma = (MediaAsset) it.next();
        mas.add(ma);
      }
      return mas;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllMediaAssets. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }


  public Iterator getAllSinglePhotoVideosNoQuery() {
    try {
      Extent spvClass = pm.getExtent(SinglePhotoVideo.class, true);
      Iterator it = spvClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllSinglePhotoVideosNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<Adoption> getAllAdoptionsNoQuery() {
    try {
      Extent encClass = pm.getExtent(Adoption.class, true);
      Iterator it = encClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllAdoptionsNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<Adoption> getAllAdoptionsWithQuery(Query ads) {
    try {
      Collection c = (Collection) (ads.execute());
      Iterator it = c.iterator();
      return it;
    } catch (Exception npe) {
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<ScanTask> getAllScanTasksNoQuery() {
    try {
      Extent taskClass = pm.getExtent(ScanTask.class, true);
      Iterator it = taskClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllScanTasksNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<ScanWorkItem> getAllScanWorkItemsNoQuery() {
    try {
      Extent taskClass = pm.getExtent(ScanWorkItem.class, true);
      Iterator it = taskClass.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllScanWorkItemsNoQuery. Returning a null iterator.");
      npe.printStackTrace();
      return null;
    }
  }


  /**
   * Retrieves any all approved encounters that are stored in the database
   *
   * @return an Iterator of all whale shark encounters stored in the database that are approved
   * @see encounter, java.util.Iterator
   */
  public Iterator<Encounter> getAllEncounters() {
    /*Collection c;
    //String filter = "!this.state == \"unidentifiable\" && this.state == \"approved\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass);
    try {
      c = (Collection) (acceptedEncounters.execute());
      ArrayList list = new ArrayList(c);
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      return null;
    }*/
    return getAllEncountersNoQuery();
  }

  public Iterator<Encounter> getAllEncounters(Query acceptedEncounters) {
    Collection c;
    try {
      c = (Collection) (acceptedEncounters.execute());
      ArrayList list = new ArrayList(c);
      //Collections.reverse(list);
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public List getAllOccurrences(Query myQuery) {
    Collection c;
    try {
      System.out.println("getAllOccurrences is called on query "+myQuery);
      c = (Collection) (myQuery.execute());
      ArrayList list = new ArrayList(c);
      System.out.println("getAllOccurrences got "+list.size()+" occurrences");
      //Collections.reverse(list);
      Iterator it = list.iterator();
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllOccurrences(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public List<SinglePhotoVideo> getAllSinglePhotoVideo(Query acceptedEncounters) {
    Collection c;
    try {
      c = (Collection) (acceptedEncounters.execute());
      ArrayList<SinglePhotoVideo> list = new ArrayList<SinglePhotoVideo>(c);
      return list;
    }
    catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllSinglePhotoVideo(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<Encounter> getAllEncounters(Query acceptedEncounters, Map<String, Object> paramMap) {
    Collection c;
    try {
      c = (Collection) (acceptedEncounters.executeWithMap(paramMap));
      ArrayList list = new ArrayList(c);
      //Collections.reverse(list);
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator<Occurrence> getAllOccurrences(Query acceptedOccurrences, Map<String, Object> paramMap) {
    Collection c;
    try {
      System.out.println("getAllOccurrences is called on query "+acceptedOccurrences+" and paramMap "+paramMap);
      c = (Collection) (acceptedOccurrences.executeWithMap(paramMap));
      ArrayList list = new ArrayList(c);
      System.out.println("getAllOccurrences got "+list.size()+" occurrences");
      //Collections.reverse(list);
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllOccurrences(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }
  
  public Iterator<Survey> getAllSurveys(Query acceptedSurveys, Map<String, Object> paramMap) {
    Collection c;
    try {
      System.out.println("getAllOccurrences is called on query "+acceptedSurveys+" and paramMap "+paramMap);
      c = (Collection) (acceptedSurveys.executeWithMap(paramMap));
      ArrayList list = new ArrayList(c);
      System.out.println("getAllSurveys got "+list.size()+" surveys");
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllSurveys(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public List<PatterningPassport> getPatterningPassports() {
    int num = 0;
    ArrayList al = new ArrayList<PatterningPassport>();
    try {
      //pm.getFetchPlan().setGroup("count");
      Query q = pm.newQuery(PatterningPassport.class); // no filter, so all instances match
      Collection results = (Collection) q.execute();
      num = results.size();
      al = new ArrayList<PatterningPassport>(results);
      q.closeAll();
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      //return num;
      System.out.println("getPatterningPassports EXCEPTION! " + num);
      return al;
    }
    //return num;
    System.out.println("getPatterningPassports. Returning a collection of length " + al.size() + ". " + num);
    return al;



    /*
    Collection c;
    Extent encClass = this.getPM().getExtent(PatterningPassport.class, true);
    Query query = this.getPM().newQuery(encClass);


    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      ArrayList al = new ArrayList<PatterningPassport>(c);
      System.out.println("getPatterningPassports. Returning a collection of length " + al.size() + ".");
      System.out.println("... list.size() is " + list.size() + ".");
      return al;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getPatterningPassports. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      return null;
    }
    */

  }
  /*
  public ArrayList<File> getAllPatterningPassportFiles() {
    Iterator all_spv = getAllSinglePhotoVideosNoQuery();
    Collection c = new ArrayList();
    while(all_spv.hasNext())
    {
      SinglePhotoVideo spv = (SinglePhotoVideo)all_spv.next();
      File ppFile = spv.getPatterningPassportFile();
      c.add(ppFile);
    }
    ArrayList<File> list = new ArrayList<File>(c);
    return list;

  }
  */

  /*
  public Iterator getAvailableScanWorkItems(Query query,int pageSize, long timeout) {
    Collection c;
    //Extent encClass = getPM().getExtent(ScanWorkItem.class, true);
    //Query query = getPM().newQuery(encClass);
    long timeDiff = System.currentTimeMillis() - timeout;
    query.setFilter("!this.done && this.startTime < " + timeDiff);
    query.setRange(0, pageSize);
    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      Iterator it = list.iterator();
      //query.closeAll();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator getAvailableScanWorkItems(Query query,int pageSize, String taskID, long timeout) {
    Collection c;
    //Extent encClass = getPM().getExtent(ScanWorkItem.class, true);
    //Query query = getPM().newQuery(encClass);
    long timeDiff = System.currentTimeMillis() - timeout;
    String filter = "!this.done && this.taskID == \"" + taskID + "\" && this.startTime < " + timeDiff;
    query.setFilter(filter);
    query.setRange(0, pageSize);
    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      //Collections.reverse(list);
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }
*/
  /*
  public List getID4AvailableScanWorkItems(Query query, int pageSize, long timeout, boolean forceReturn) {
    Collection c;
    query.setResult("uniqueNum");
    long timeDiff = System.currentTimeMillis() - timeout;
    String filter = "";
    if (forceReturn) {
      filter = "this.done == false";
    } else {
      filter = "this.done == false && this.startTime < " + timeDiff;
      query.setRange(0, pageSize);
    }
    query.setFilter(filter);
    //query.setOrdering("createTime ascending");
    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getID4AvailableScanWorkItems. Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }
  */

  public List getPairs(Query query, int pageSize) {
    Collection c;
    query.setRange(0, pageSize);
    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  /*
  public List getID4AvailableScanWorkItems(String taskID, Query query, int pageSize, long timeout, boolean forceReturn) {
    Collection c;
    query.setResult("uniqueNum");
    long timeDiff = System.currentTimeMillis() - timeout;
    String filter = "";
    if (forceReturn) {
      filter = "this.done == false && this.taskID == \"" + taskID + "\"";
    } else {
      filter = "this.done == false && this.taskID == \"" + taskID + "\" && this.startTime < " + timeDiff;
      query.setRange(0, pageSize);
    }
    query.setFilter(filter);
    //query.setOrdering("createTime ascending");
    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getID4AvailableScanWorkItems). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }
  */

  public List<String> getAdopterEmailsForMarkedIndividual(Query query,String shark) {
    Collection c;
    //Extent encClass = getPM().getExtent(Adoption.class, true);
    //Query query = getPM().newQuery(encClass);
    query.setResult("adopterEmail");
    String filter = "this.individual == '" + shark + "'";
    query.setFilter(filter);
    try {
      c = (Collection) (query.execute());
      ArrayList list = new ArrayList(c);
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute shepherd.getAdopterEmailsForMarkedIndividual(). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

/**
  public Iterator<Encounter> getAllEncountersAndUnapproved() {
    Collection c;
    String filter = "this.state != \"unidentifiable\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      c = (Collection) (acceptedEncounters.execute());
      ArrayList list = new ArrayList(c);
      int wr = list.size();
      Iterator it = list.iterator();
      return it;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllEncounters. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      return null;
    }

  }
  */

  /**
   * Retrieves all encounters that are stored in the database in the order specified by the input String
   *
   * @return an Iterator of all valid whale shark encounters stored in the visual database, arranged by the input String
   * @see encounter, java.util.Iterator
   */
  public Iterator<Encounter> getAllEncounters(String order) {
    //String filter = "this.state != \"unidentifiable\" && this.state == \"approved\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass);
    acceptedEncounters.setOrdering(order);
    Collection c = (Collection) (acceptedEncounters.execute());

    ArrayList listy=new ArrayList(c);
    //Iterator it = c.iterator();
    Iterator it=listy.iterator();
    acceptedEncounters.closeAll();
    return it;
  }

  public List<Adoption> getAllAdoptionsForMarkedIndividual(String ind,String context) {
    if(CommonConfiguration.allowAdoptions(context)){
      String filter = "this.individual == '" + ind + "'";
      Extent encClass = pm.getExtent(Adoption.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
      Collection c = (Collection) (acceptedEncounters.execute());
      ArrayList listy=new ArrayList(c);
      acceptedEncounters.closeAll();
      return listy;
    }
    else{
      return (new ArrayList());
    }
  }

  /*
   * Retrieve the distinct User objects for all Encounters related to this MarkedIndividual
   *
   */
  public ArrayList<User> getAllUsersForMarkedIndividual(MarkedIndividual indie){
    ArrayList<User> relatedUsers=new ArrayList<User>();
    ArrayList<String> usernames=indie.getAllAssignedUsers();
    int size=usernames.size();
    if(size>0){
      for(int i=0;i<size;i++){
        String thisUsername=usernames.get(i);
        if(getUser(thisUsername)!=null){
          relatedUsers.add(getUser(thisUsername));
        }
      }
    }

    return relatedUsers;
  }

  /*
   * Retrieve the distinct User objects for all Encounters related to this Occurrence
   *
   */
  public List<User> getAllUsersForOccurrence(Occurrence indie){
    ArrayList<User> relatedUsers=new ArrayList<User>();
    ArrayList<String> usernames=indie.getAllAssignedUsers();
    int size=usernames.size();
    if(size>0){
      for(int i=0;i<size;i++){
        String thisUsername=usernames.get(i);
        if(getUser(thisUsername)!=null){
          relatedUsers.add(getUser(thisUsername));
        }
      }
    }

    return relatedUsers;
  }

  /*
   * Retrieve the distinct User objects for all Encounters related to this MarkedIndividual
   *
   */
  public List<User> getAllUsersForMarkedIndividual(String indie){
    ArrayList<User> relatedUsers=new ArrayList<User>();
    if(getMarkedIndividual(indie)!=null){
      MarkedIndividual foundIndie=getMarkedIndividual(indie);
      return getAllUsersForMarkedIndividual(foundIndie);
    }
    return relatedUsers;
  }

  /* Retrieve the distinct User objects for all Encounters related to this Occurrence
  *
  */
 public List<User> getAllUsersForOccurrence(String occur){
   ArrayList<User> relatedUsers=new ArrayList<User>();
   if(getOccurrence(occur)!=null){
     Occurrence foundOccur=getOccurrence(occur);
     return getAllUsersForOccurrence(foundOccur);
   }
   return relatedUsers;
 }

  public List<Adoption> getAllAdoptionsForEncounter(String shark) {
    String filter = "this.encounter == '" + shark + "'";
    Extent encClass = pm.getExtent(Adoption.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList listy=new ArrayList(c);
    acceptedEncounters.closeAll();
    return listy;
  }

  public Iterator<Encounter> getAllEncounters(Query acceptedEncounters, String order) {
    acceptedEncounters.setOrdering(order);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList list = new ArrayList(c);
    //Collections.reverse(list);
    Iterator it = list.iterator();
    return it;

  }

  /**
   * Retrieves a filtered list of encounters that are stored in the database in the order specified by the input String
   *
   * @return a filtered Iterator of whale shark encounters stored in the visual database, arranged by the input String
   * @see encounter, java.util.Iterator
   */
  public Iterator<Encounter> getAllEncounters(String order, String filter2use) {
    //String filter = filter2use + " && this.approved == true";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter2use);
    acceptedEncounters.setOrdering(order);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList listy = new ArrayList(c);
    Iterator it = listy.iterator();
    acceptedEncounters.closeAll();
    //Iterator it = c.iterator();
    return it;
  }


  public Iterator<Occurrence> getAllOccurrencesForMarkedIndividual(Query query,String indie) {
    //Query acceptedEncounters = pm.newQuery(encClass, filter2use);

    Collection c = (Collection) (query.execute());
    //System.out.println("getAllOccurrencesForMarkedIndividual size: "+c.size());
    Iterator it = c.iterator();
    //query.closeAll();
    return it;
  }

  public Occurrence getOccurrenceForEncounter(String encounterID){
    String filter="SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.catalogNumber == \""+encounterID+"\"  VARIABLES org.ecocean.Encounter enc";
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    Iterator it = c.iterator();

    while(it.hasNext()){
      Occurrence occur=(Occurrence)it.next();
      query.closeAll();
      return occur;
    }
    query.closeAll();
    return null;
  }
  
  public Occurrence getOccurrenceForSurvey(Survey svy) {
    String svyID = svy.getID();
    String filter="SELECT FROM org.ecocean.Occurrence WHERE correspondingSurveyID == \""+svyID+"\"";
    Query q = getPM().newQuery(filter);
    Collection c = (Collection) (q.execute());
    Iterator obArr = c.iterator();
    q.closeAll();
    if (obArr.hasNext()) {
      return (Occurrence) obArr.next();      
    }
    return null;
  }
  
  

  



  public User getUserByEmailAddress(String email){
    String hashedEmailAddress=User.generateEmailHash(email);
    return getUserByHashedEmailAddress(hashedEmailAddress);
  }
  
  public User getUserByHashedEmailAddress(String hashedEmail){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE hashedEmailAddress == \""+hashedEmail+"\"";
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){users=new ArrayList<User>(c);}
    query.closeAll();
    if(users.size()>0){return users.get(0);}
    return null;
  }
  
  
  public List<User> getUsersWithEmailAddresses(){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE emailAddress != null";
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null)users=new ArrayList<User>(c);
    query.closeAll();
    return users;
  }
  
  public User getUserByAffiliation(String affil){
    String filter="SELECT FROM org.ecocean.User WHERE affiliation == \""+affil+"\"";
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    Iterator it = c.iterator();

    while(it.hasNext()){
      User myUser=(User)it.next();
      query.closeAll();
      return myUser;
    }
    query.closeAll();
    return null;
  }
  
  

  public User getUserBySocialId(String service, String id) {
        if ((id == null) || (service == null)) return null;
        List<User> users = getAllUsers();
        for (int i = 0 ; i < users.size() ; i++) {
            if (id.equals(users.get(i).getSocial(service))) return users.get(i);
        }
        return null;

/*   TODO figure out how to query on HashMaps within fields
    String filter="SELECT FROM org.ecocean.User WHERE social_" + service + " == \"" + id + "\"";
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    Iterator it = c.iterator();

    while(it.hasNext()){
      User myUser=(User)it.next();
      query.closeAll();
      return myUser;
    }
    query.closeAll();
    return null;
*/
  }

  public List<Map.Entry> getAllOtherIndividualsOccurringWithMarkedIndividual(String indie){
    HashMap<String,Integer> hmap = new HashMap<String,Integer>();
    //TreeMapOccurrenceComparator cmp=new TreeMapOccurrenceComparator(hmap);
   //TreeMap<String, Integer> map=new TreeMap<String, Integer>(cmp);
   TreeMap<String, Integer> map=new TreeMap<String, Integer>();
   String filter="SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individualID == \""+indie+"\"  VARIABLES org.ecocean.Encounter enc";
   Query query=getPM().newQuery(filter);
   Iterator<Occurrence> it=getAllOccurrencesForMarkedIndividual(query,indie);
   if(it!=null){
      while(it.hasNext()){
         Occurrence oc=it.next();
         //System.out.println("     Found an occurrence for my indie!!!!");
         ArrayList<MarkedIndividual> alreadyCounted=new ArrayList<MarkedIndividual>();
         ArrayList<Encounter> encounters=oc.getEncounters();
         int numEncounters=encounters.size();
         for(int i=0;i<numEncounters;i++){
           Encounter enc=encounters.get(i);
           if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals(indie))){
             MarkedIndividual indieEnc=this.getMarkedIndividual(enc.getIndividualID());
             //check if we already have this Indie
             if(!hmap.containsKey(indieEnc.getIndividualID())){
               hmap.put(indieEnc.getIndividualID(), (new Integer(1)));
               alreadyCounted.add(indieEnc);
             }
             else if(!alreadyCounted.contains(indieEnc)){
               Integer oldValue=hmap.get(indieEnc.getIndividualID());
               hmap.put(indieEnc.getIndividualID(), (oldValue+1));
               //System.out.println("Iterating: "+indieEnc.getIndividualID());
             }

           }
         }
      }
    } //end if

      ArrayList<Map.Entry> as = new ArrayList<Map.Entry>( hmap.entrySet() );

      if(as.size()>0){
        IndividualOccurrenceNumComparator cmp=new IndividualOccurrenceNumComparator();
        Collections.sort( as , cmp);
        Collections.reverse(as);
      }

      query.closeAll();
      return as;
  }

  public List<TissueSample> getAllTissueSamplesForEncounter(String encNum) {
    String filter = "correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(TissueSample.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    ArrayList al=new ArrayList<TissueSample>(c);
    samples.closeAll();
    return (al);
  }
  
  public ArrayList<TissueSample> getAllTissueSamplesNoQuery() {
    Extent tsClass = pm.getExtent(TissueSample.class, true);
    Query tsQuery = pm.newQuery(tsClass, "");
    
    Collection col = (Collection) (tsQuery.execute());
    ArrayList<TissueSample> samples = new ArrayList<>(col);
    return samples;
  }

  public ArrayList<TissueSample> getAllTissueSamplesForMarkedIndividual(MarkedIndividual indy) {
    ArrayList<TissueSample> al = new ArrayList<TissueSample>();
    if(indy.getEncounters()!=null){
      int numEncounters = indy.getEncounters().size();
      for (int i = 0; i < numEncounters; i++) {
        Encounter enc = (Encounter) indy.getEncounters().get(i);
        if(getAllTissueSamplesForEncounter(enc.getCatalogNumber())!=null){
          List<TissueSample> list = getAllTissueSamplesForEncounter(enc.getCatalogNumber());
          if(list.size()>0){
            al.addAll(list);
          }
        }
      }
    return al;
    }
    return null;
  }


  public List<SinglePhotoVideo> getAllSinglePhotoVideosForEncounter(String encNum) {
    ArrayList<Annotation> al=getAnnotationsForEncounter(encNum);
    int numAnnots=al.size();
    ArrayList<SinglePhotoVideo> myArray=new ArrayList<SinglePhotoVideo>();
    for(int i=0;i<numAnnots;i++){
      try{
        MediaAsset ma=al.get(i).getMediaAsset();
        AssetStore as=ma.getStore();
        String fullFileSystemPath=as.localPath(ma).toString();
        URL u = ma.safeURL(this);
        String webURL = ((u == null) ? null : u.toString());
        int lastIndex=webURL.lastIndexOf("/")+1;
        String filename=webURL.substring(lastIndex);
        SinglePhotoVideo spv=new SinglePhotoVideo(encNum, filename, fullFileSystemPath);
        spv.setWebURL(webURL);
        spv.setDataCollectionEventID(ma.getUUID());

        //add Keywords
        if(ma.getKeywords()!=null){
          ArrayList<Keyword> alkw=ma.getKeywords();
          int numKeywords=alkw.size();
          for(int y=0;y<numKeywords;y++){
            Keyword kw=alkw.get(y);
            spv.addKeyword(kw);
          }
        }

        myArray.add(spv);

    }
    catch(Exception e){}

    }
    return myArray;
  }

/*  
  public ArrayList<SinglePhotoVideo> getAllSinglePhotoVideosWithKeyword(Keyword word) {
	  String keywordQueryString="SELECT FROM org.ecocean.SinglePhotoVideo WHERE keywords.contains(word0) && ( word0.indexname == \""+word.getIndexname()+"\" ) VARIABLES org.ecocean.Keyword word0";
      Query samples = pm.newQuery(keywordQueryString);
	  Collection c = (Collection) (samples.execute());
	    ArrayList<SinglePhotoVideo> myArray=new ArrayList<SinglePhotoVideo>(c);
	    samples.closeAll();
	    return myArray;
	  }
*/
  
  public ArrayList<MediaAsset> getAllMediAssetsWithKeyword(Keyword word0){
    String keywordQueryString="SELECT FROM org.ecocean.media.MediaAsset WHERE ( this.keywords.contains(word0) && ( word0.indexname == \""+word0.getIndexname()+"\" ) ) VARIABLES org.ecocean.Keyword word0";
    Query samples = pm.newQuery(keywordQueryString);
    Collection c = (Collection) (samples.execute());
    ArrayList<MediaAsset> myArray=new ArrayList<MediaAsset>(c);
    samples.closeAll();
    return myArray;
  } 

  public List<MediaAsset> getKeywordPhotosForIndividual(MarkedIndividual indy, String[] kwReadableNames, int maxResults){

    String filter="SELECT FROM org.ecocean.Annotation WHERE enc3_0.annotations.contains(this) && enc3_0.individualID == \""+indy.getIndividualID()+"\" ";
    String vars=" VARIABLES org.ecocean.Encounter enc3_0";
    for(int i=0; i<kwReadableNames.length; i++){
      filter+="  && features.contains(feat"+i+") && feat"+i+".asset.keywords.contains(word"+i+") &&  word"+i+".readableName == \""+kwReadableNames[i]+"\" ";
      vars+=";org.ecocean.Keyword word"+i+";org.ecocean.media.Feature feat"+i;
    }

    ArrayList<Annotation> results = new ArrayList<Annotation>();
    try {
      Query query=this.getPM().newQuery(filter+vars);
      query.setRange(0, maxResults);
      Collection coll = (Collection) (query.execute());
      results=new ArrayList<Annotation>(coll);
      if (query!=null) query.closeAll();
    }
    catch(Exception e){
      e.printStackTrace();
    }

    ArrayList<MediaAsset> assResults = new ArrayList<MediaAsset>();
    for (Annotation ann: results) {
      if (ann!=null && ann.getMediaAsset()!=null) assResults.add(ann.getMediaAsset());
    }
    return assResults;
  }

  public List<MediaAsset> getPhotosForIndividual(MarkedIndividual indy, int maxResults) {
    // i hope this works?
    String[] noKeywordNames = new String[0];
    List<MediaAsset> results = getKeywordPhotosForIndividual(indy, noKeywordNames, maxResults);
    return results;
  }

  // this method returns the MediaAsset on an Indy with the given keyword, with preference
  // for assets with the additional keyword "ProfilePhoto"
  public MediaAsset getBestKeywordPhoto(MarkedIndividual indy, String kwName) {

    List<MediaAsset> results = getKeywordPhotosForIndividual(indy, new String[]{kwName, "ProfilePhoto"}, 1);
    MediaAsset result = (results!=null && results.size()>0) ? results.get(0) : null;
    if (result != null) return (result);

    // we couldn't find a profile photo with the keyword
    results = getKeywordPhotosForIndividual(indy, new String[]{kwName}, 1);
    if (results!=null && results.size()>0) return (results.get(0));
    
    return null;
  }


  public ArrayList<MarkedIndividual> getAllMarkedIndividualsSightedAtLocationID(String locationID){
    ArrayList<MarkedIndividual> myArray=new ArrayList<MarkedIndividual>();
    String keywordQueryString="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && ( enc.locationID == \""+locationID+"\" ) VARIABLES org.ecocean.Encounter enc";
    Query samples = pm.newQuery(keywordQueryString);
    Collection c = (Collection) (samples.execute());
    if(c!=null){
      myArray=new ArrayList<MarkedIndividual>(c);
    }
    samples.closeAll();
    return myArray;
  }

  public int getNumMarkedIndividualsSightedAtLocationID(String locationID){
    return getAllMarkedIndividualsSightedAtLocationID(locationID).size();
  }

  public ArrayList<Encounter> getAllEncountersForSpecies(String genus, String specificEpithet) {
    String keywordQueryString="SELECT FROM org.ecocean.Encounter WHERE genus == '"+genus+"' && specificEpithet == '"+specificEpithet+"'";
      Query samples = pm.newQuery(keywordQueryString);
    Collection c = (Collection) (samples.execute());
      ArrayList<Encounter> myArray=new ArrayList<Encounter>(c);
      samples.closeAll();
      return myArray;
    }

  public ArrayList<Encounter> getAllEncountersForSpeciesWithSpots(String genus, String specificEpithet) {
    String keywordQueryString="SELECT FROM org.ecocean.Encounter WHERE genus == '"+genus+"' && specificEpithet == '"+specificEpithet+"' && spots != null";
      Query samples = pm.newQuery(keywordQueryString);
    Collection c = (Collection) (samples.execute());
      ArrayList<Encounter> myArray=new ArrayList<Encounter>(c);
      samples.closeAll();
      return myArray;
    }
  
  public ArrayList<Encounter> getEncountersArrayWithMillis(long millis) {
    String milliString = String.valueOf(millis);
    
    String up = milliString.substring(0, milliString.length() - 6) + 999999;
    String down = milliString.substring(0, milliString.length() - 6) + 000000;
    
    
    String keywordQueryString="SELECT FROM org.ecocean.Encounter WHERE dateInMilliseconds >= "+down+" && dateInMilliseconds <= "+up+" ";
    Query encQuery = pm.newQuery(keywordQueryString);
    Collection col = null;
    try {
      encQuery = pm.newQuery(keywordQueryString);
      if (encQuery.execute() != null) {
        col = (Collection) encQuery.execute();              
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Exception on query : "+keywordQueryString);    
      return null;
    }
    ArrayList<Encounter> encs = new ArrayList<Encounter>(col);
    encQuery.closeAll();
    if (encs != null) {
      return encs;
    } else {
      return null;
    }
  }
  
  public ArrayList<Encounter> getEncounterArrayWithShortDate(String sd) {
    sd = sd.replace("/", "-");
    sd = sd.replace(".", "-");
    sd = sd.trim();
    DateFormat fm = new SimpleDateFormat("yyyy-MM-dd");
    Date d = null;
    try {
      d = (Date)fm.parse(sd);    
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    DateTime dt = new DateTime(d);
    DateTime nextDay = dt.plusDays(1).toDateTime();
    // Since the query involves a date but no time, we need to get the millis of the next day at 12:00AM as well and find all encounters that occurred in between.
    String milliString = String.valueOf(dt.getMillis());
    String millisNext = String.valueOf(nextDay.getMillis());
    System.out.println("Trying to get encounter with date in Millis : "+milliString);
    String keywordQueryString="SELECT FROM org.ecocean.Encounter WHERE dateInMilliseconds >= "+milliString+" && dateInMilliseconds <= "+millisNext+"";
    Collection col = null;
    Query encQuery = null; 
    try {
      encQuery = pm.newQuery(keywordQueryString);
      if (encQuery.execute() != null) {
        col = (Collection) encQuery.execute();              
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Exception on query : "+keywordQueryString);    
      return null;
    }
    ArrayList<Encounter> encs = new ArrayList<Encounter>(col);
    encQuery.closeAll();
    if (encs != null) {
      return encs;
    } else {
      return null;
    }
  }

  public int getNumSinglePhotoVideosForEncounter(String encNum) {
	    String filter = "correspondingEncounterNumber == \""+encNum+"\"";
	    Extent encClass = pm.getExtent(SinglePhotoVideo.class, true);
	    Query samples = pm.newQuery(encClass, filter);
	    Collection c = (Collection) (samples.execute());
	    int numResults=c.size();
	    samples.closeAll();
	    return numResults;
	  }

  public Iterator<Encounter> getAllEncountersNoFilter(String order, String filter2use) {
    String filter = filter2use;
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    acceptedEncounters.setOrdering(order);
    Collection c = (Collection) (acceptedEncounters.execute());

    ArrayList listy=new ArrayList(c);

    //Iterator it = c.iterator();
    Iterator it=listy.iterator();
    acceptedEncounters.closeAll();
    return it;

  }


  public Query getAllEncountersNoFilterReturnQuery(String order, String filter2use) {
    String filter = filter2use;
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    return acceptedEncounters;

  }

  /**
   * Retrieves all encounters that are stored in the database but which have been rejected for the visual database
   *
   * @return an Iterator of all whale shark encounters stored in the database that are unacceptable for the visual ID library
   * @see encounter, java.util.Iterator
   */
  public Iterator<Encounter> getAllUnidentifiableEncounters(Query rejectedEncounters) {
    rejectedEncounters.setFilter("this.state == \"unidentifiable\"");
    Collection c = (Collection) (rejectedEncounters.execute());
    ArrayList list = new ArrayList(c);

    //Collections.reverse(list);
    Iterator it = list.iterator();
    return it;
  }

  /**
   * Retrieves all new encounters that are stored in the database but which have been approved for public viewing in the visual database
   *
   * @return an Iterator of all whale shark encounters stored in the database that are unacceptable for the visual ID library
   * @see encounter, java.util.Iterator
   */
  public Iterator<Encounter> getUnapprovedEncounters(Query acceptedEncounters) {
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList list = new ArrayList(c);
    Iterator it = list.iterator();
    return it;

  }

  public Iterator<Encounter> getUnapprovedEncounters(Query unapprovedEncounters, String order) {
    unapprovedEncounters.setOrdering(order);
    Collection c = (Collection) (unapprovedEncounters.execute());
    Iterator it = c.iterator();
    return it;
  }

  //Returns encounters submitted by the specified user
  public Iterator<Encounter> getUserEncounters(Query userEncounters, String user) {
    Collection c = (Collection) (userEncounters.execute());
    ArrayList list = new ArrayList(c);

    Iterator it = list.iterator();
    return it;
  }


  public Iterator<Encounter> getSortedUserEncounters(Query userEncounters, String order2) {
    userEncounters.setOrdering(order2);
    Collection c = (Collection) (userEncounters.execute());
    Iterator it = c.iterator();
    return it;
  }

  /**
   * Retrieves all encounters that are stored in the database but which have been rejected for the visual database in the order identified by the input String
   *
   * @return an Iterator of all whale shark encounters stored in the database that are unacceptable for the visual ID library in the String order
   * @see encounter, java.util.Iterator
   */
  public Iterator<Encounter> getAllUnidentifiableEncounters(Query unacceptedEncounters, String order) {
    unacceptedEncounters.setOrdering(order);
    Collection c = (Collection) (unacceptedEncounters.execute());
    ArrayList list = new ArrayList(c);

    //Collections.reverse(list);
    Iterator it = list.iterator();
    return it;
  }

  public MarkedIndividual getMarkedIndividual(String name) {
    MarkedIndividual tempShark = null;
    try {
      tempShark = ((org.ecocean.MarkedIndividual) (pm.getObjectById(pm.newObjectIdInstance(MarkedIndividual.class, name.trim()), true)));
    } catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
    return tempShark;
  }

  public Task getTask(String id) {
    Task theTask = null;
    try {
      theTask = ((org.ecocean.ia.Task) (pm.getObjectById(pm.newObjectIdInstance(Task.class, id.trim()), true)));
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return theTask;
  }

  public MarkedIndividual getMarkedIndividualQuiet(String name) {
    MarkedIndividual indiv = null;
    try {
      indiv = ((org.ecocean.MarkedIndividual) (pm.getObjectById(pm.newObjectIdInstance(MarkedIndividual.class, name.trim()), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return indiv;
  }

    //note, new indiv is *not* made persistent here!  so do that yourself if you want to. (shouldnt matter if not-new)
    public MarkedIndividual getOrCreateMarkedIndividual(String name, Encounter enc) {
        MarkedIndividual indiv = getMarkedIndividualQuiet(name);
        if (indiv != null) return indiv;
        indiv = new MarkedIndividual(name, enc);
        enc.assignToMarkedIndividual(name);
        return indiv;
    }


  public Occurrence getOccurrence(String id) {
    Occurrence tempShark = null;
    try {
      tempShark = ((org.ecocean.Occurrence) (pm.getObjectById(pm.newObjectIdInstance(Occurrence.class, id.trim()), true)));
    } catch (Exception nsoe) {
      //nsoe.printStackTrace();
      return null;
    }
    return tempShark;
  }
  public Occurrence getOrCreateOccurrence(String id) {
      if (id==null) return new Occurrence(Util.generateUUID());
      Occurrence occ = getOccurrence(id);
      if (occ != null) return occ;
      occ = new Occurrence(id);
      return occ;
  }

  
  public Survey getSurvey(String id) {
    Survey srv = null;
    try {
      srv = ((org.ecocean.Survey) (pm.getObjectById(pm.newObjectIdInstance(Survey.class, id.trim()), true)));
    } catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
    return srv;
  }
  
  public SurveyTrack getSurveyTrack(String id) {
    SurveyTrack stk = null;
    try {
      stk = ((org.ecocean.movement.SurveyTrack) (pm.getObjectById(pm.newObjectIdInstance(SurveyTrack.class, id.trim()), true)));
    } catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
    return stk;
  }
  
  public Path getPath(String id) {
    Path pth = null;
    try {
      pth = ((org.ecocean.movement.Path) (pm.getObjectById(pm.newObjectIdInstance(Path.class, id.trim()), true)));
    } catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
    return pth;
  }
  
  public PointLocation getPointLocation(String id) {
    PointLocation pl = null;
    try {
      pl = ((org.ecocean.PointLocation) (pm.getObjectById(pm.newObjectIdInstance(PointLocation.class, id.trim()), true)));
    } catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
    return pl;
  }


  /**
   * Returns all of the names of the sharks that can be used for training purporses - i.e. have more than one encounter - in a Vector format
   *
   * @return a Vector of shark names that have more than one encounter with spots associated with them
   * @see encounter, shark, java.util.Vector
   */
  public Vector getPossibleTrainingIndividuals() {
    Iterator<MarkedIndividual> allSharks = getAllMarkedIndividuals();
    MarkedIndividual tempShark;
    Vector possibleTrainingSharkNames = new Vector();
    while (allSharks.hasNext()) {
      tempShark = allSharks.next();
      if (tempShark.getNumberTrainableEncounters() >= 2) {
        possibleTrainingSharkNames.add(tempShark);
        //System.out.println(tempShark.getName()+" has more than one encounter.");
      }
      ;

    }
    return possibleTrainingSharkNames;

  }

  public Vector getRightPossibleTrainingIndividuals() {
    Iterator<MarkedIndividual> allSharks = getAllMarkedIndividuals();
    MarkedIndividual tempShark;
    Vector possibleTrainingSharkNames = new Vector();
    while (allSharks.hasNext()) {
      tempShark = allSharks.next();
      if (tempShark.getNumberRightTrainableEncounters() >= 2) {
        possibleTrainingSharkNames.add(tempShark);
        //System.out.println(tempShark.getName()+" has more than one encounter.");
      }
      ;
    }
    return possibleTrainingSharkNames;

  }


  /**
   * Retrieves an Iterator of all the sharks in the database
   *
   * @return an Iterator containing all of the shark objects that have been stored in the database
   * @see shark, java.util.Iterator
   */
  public Iterator<MarkedIndividual> getAllMarkedIndividuals() {
    Extent allSharks = null;
    try {
      allSharks = pm.getExtent(MarkedIndividual.class, true);
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
    }
    Extent encClass = pm.getExtent(MarkedIndividual.class, true);
    Query sharks = pm.newQuery(encClass);
    Collection c = (Collection) (sharks.execute());
    ArrayList list = new ArrayList(c);
    sharks.closeAll();
    Iterator it = list.iterator();
    return it;
  }

  public Iterator getAllWorkspaces() {
    Extent allWorkspaces = null;
    try {
      allWorkspaces = pm.getExtent(Workspace.class, true);
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
    }
    Query spaces = pm.newQuery(allWorkspaces);
    Collection c = (Collection) (spaces.execute());
    ArrayList list = new ArrayList(c);
    spaces.closeAll();
    Iterator it = list.iterator();
    return it;
  }


  public List<MarkedIndividual> getAllMarkedIndividualsFromLocationID(String locCode) {
    Extent allSharks = null;
    try {
      allSharks = pm.getExtent(MarkedIndividual.class, true);
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
    }
    Extent encClass = pm.getExtent(MarkedIndividual.class, true);
    Query sharks = pm.newQuery(encClass);
    Collection c = (Collection) (sharks.execute());
    ArrayList list = new ArrayList(c);
    ArrayList<MarkedIndividual> newList=new ArrayList<MarkedIndividual>();
    int listSize=list.size();
    for(int i=0;i<listSize;i++){
      MarkedIndividual indie=(MarkedIndividual)list.get(i);
      if(indie.wasSightedInLocationCode(locCode)){newList.add(indie);}
    }
    sharks.closeAll();
    return newList;
  }



  public Iterator<MarkedIndividual> getAllMarkedIndividuals(Query sharks) {
    Collection c = (Collection) (sharks.execute());
    //ArrayList list = new ArrayList(c);
    //Collections.reverse(list);
    Iterator it = c.iterator();
    return it;
  }

  /**
   * Retrieves an Iterator of all the sharks in the database, ordered according to the input String
   *
   * @return an Iterator containing all of the shark objects that have been stored in the database, ordered according to the input String
   * @see shark, java.util.Iterator
   */
  public Iterator<MarkedIndividual> getAllMarkedIndividuals(Query sharkies, String order) {
    Map<String, Object> emptyMap = Collections.emptyMap();
    return getAllMarkedIndividuals(sharkies, order, emptyMap);
  }

  public Iterator<MarkedIndividual> getAllMarkedIndividuals(Query sharkies, String order, Map<String, Object> params) {
    sharkies.setOrdering(order);
    Collection c = (Collection) (sharkies.executeWithMap(params));
    ArrayList list = new ArrayList(c);
    //Collections.reverse(list);
    Iterator it = list.iterator();
    return it;
  }


  public int getNumMarkedIndividuals() {
    int num = 0;
    Query q = pm.newQuery(MarkedIndividual.class); // no filter, so all instances match

    try {
      pm.getFetchPlan().setGroup("count");
      Collection results = (Collection) q.execute();
      num = results.size();

    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      q.closeAll();
      return num;
    }
    q.closeAll();
    return num;
  }

  public int getNumUsers() {
    int num = 0;
    Query q = pm.newQuery(User.class); // no filter, so all instances match

    try {
      Collection results = (Collection) q.execute();
      num = results.size();

    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      q.closeAll();
      return num;
    }
    q.closeAll();
    return num;
  }


  public int getNumScanTasks() {
    Extent allTasks = null;
    int num = 0;
    Query q = pm.newQuery(ScanTask.class); // no filter, so all instances match

    try {
      Collection results = (Collection) q.execute();
      num = results.size();

    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return num;
    }
    q.closeAll();
    return num;
  }

  public int getNumUnfinishedScanTasks() {
    pm.getFetchPlan().setGroup("count");
    Collection c;
    Extent encClass = getPM().getExtent(ScanTask.class, true);
    Query query = getPM().newQuery(encClass);
    String filter = "!this.isFinished";
    query.setFilter(filter);
    query.setResult("count(this)");
    try {
      Long myValue=(Long) query.execute();
      query.closeAll();
      return myValue.intValue();
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute shepherd.getNumUnfinishedScanTasks(). Returning an zero value.");
      npe.printStackTrace();
      query.closeAll();
      return 0;
    }
  }


  public int getNumEncounters() {
    pm.getFetchPlan().setGroup("count");
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "this.state != \"unidentifiable\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }
  public int getNumOccurrences() {
    pm.getFetchPlan().setGroup("count");
    Extent encClass = pm.getExtent(Occurrence.class, true);
    Query acceptedOccurrences = pm.newQuery(encClass);
    try {
      Collection c = (Collection) (acceptedOccurrences.execute());
      int num = c.size();
      acceptedOccurrences.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedOccurrences.closeAll();
      return 0;
    }
  }

  public int getNumAdoptions() {
    pm.getFetchPlan().setGroup("count");
    Extent encClass = pm.getExtent(Adoption.class, true);
    Query acceptedEncounters = pm.newQuery(encClass);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  public int getNumApprovedEncounters() {
    pm.getFetchPlan().setGroup("count");
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "this.state == \"approved\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  public int getNumEncounters(String locationCode) {
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "this.locationID == \"" + locationCode+"\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      acceptedEncounters = null;
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  public int getNumSurveys() {
    pm.getFetchPlan().setGroup("count");
    Extent svyClass = pm.getExtent(Survey.class, true);
    Query acceptedSurveys = pm.newQuery(svyClass);
    try {
      Collection c = (Collection) (acceptedSurveys.execute());
      int num = c.size();
      acceptedSurveys.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedSurveys.closeAll();
      return 0;
    }
  }

  public int getNumUnidentifiableEncountersForMarkedIndividual(String individual) {
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "this.state == \"unidentifiable\" && this.individualID == \"" + individual+"\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      acceptedEncounters = null;
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  public int getNumUnidentifiableEncounters() {
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "this.state == \"unidentifiable\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      acceptedEncounters = null;
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  public Vector getUnidentifiableEncountersForMarkedIndividual(String individual) {
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "this.state == \"unidentifiable\" && this.individualID == \"" + individual+"\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      Vector matches = new Vector(c);
      acceptedEncounters.closeAll();
      acceptedEncounters = null;
      return matches;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return new Vector();
    }
  }

  public int getNumEncountersWithSpotData(boolean rightSide) {
    pm.getFetchPlan().setGroup("count");
    Extent encClass = pm.getExtent(Encounter.class, true);
    String filter = "";
    if (rightSide) {
      filter = "this.rightSpots != null";
    } else {
      filter = "this.spots != null";
    }
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    int num = 0;
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      Iterator it = c.iterator();

      num = c.size();
      acceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }


  public int getNumRejectedEncounters() {
    Extent allEncounters = null;
    String filter = "this.state == \"unidentifiable\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {


      //acceptedEncounters.declareParameters("String rejected");
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      //logger.error("I could not find the number of rejected encounters in the database."+"\n"+x.getStackTrace());
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  public int getNumUnapprovedEncounters() {
    String filter = "this.state == \"unapproved\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query unacceptedEncounters = pm.newQuery(encClass, filter);
    try {


      //acceptedEncounters.declareParameters("String rejected");
      Collection c = (Collection) (unacceptedEncounters.execute());
      int num = c.size();
      unacceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      //logger.error("I could not find the number of rejected encounters in the database."+"\n"+x.getStackTrace());
      unacceptedEncounters.closeAll();
      return 0;
    }
  }


  public int getNumUserEncounters(String user) {
    String filter = "this.submitterID == \"" + user + "\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    try {
      Collection c = (Collection) (acceptedEncounters.execute());
      int num = c.size();
      acceptedEncounters.closeAll();
      return num;
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      acceptedEncounters.closeAll();
      return 0;
    }
  }

  /**
   * Returns the <i>i</i>th numbered encounter for a shark
   *
   * @param  tempShark  the shark to retrieve an encounter from
   * i			the number of the shark to get, numbered from 0...<i>n</i>
   * @return the <i>i</i>th encounter of the specified shark
   * @see MarkedIndividual
   */
  public Encounter getEncounter(MarkedIndividual tempShark, int i) {
    MarkedIndividual myShark = getMarkedIndividual(tempShark.getName());
    Encounter e = (Encounter) myShark.getEncounters().get(i);
    return e;
  }


  /**
   * Opens the database up for information retrieval, storage, and removal
   */
  public void beginDBTransaction() {
    //PersistenceManagerFactory pmf = ShepherdPMF.getPMF(localContext);
    try {
      if (pm == null || pm.isClosed()) {
        pm = ShepherdPMF.getPMF(localContext).getPersistenceManager();
        pm.currentTransaction().begin();
      } else if (!pm.currentTransaction().isActive()) {

        pm.currentTransaction().begin();
      }
      ShepherdPMF.setShepherdState(action+"_"+shepherdID, "begin");


    }
    catch (JDOUserException jdoe) {
      jdoe.printStackTrace();
    }
    catch (NullPointerException npe) {
      npe.printStackTrace();
    }
    //pmf=null;
  }

  /**
   * Commits (makes permanent) any changes made to an open database
   */
//////////// TODO it seems like this should either (a) throw an exception itself; or (b) return boolean of success.  obviously the latter was disabled(?).  whassup?  -jon 20140619
  public void commitDBTransaction() {
    try {
      //System.out.println("     shepherd:"+identifyMe+" is trying to commit a transaction");
      // System.out.println("Is the pm null? " + Boolean.toString(pm == null));
      if ((pm != null) && (pm.currentTransaction().isActive())) {

        //System.out.println("     Now commiting a transaction with pm"+(String)pm.getUserObject());
        pm.currentTransaction().commit();


        //return true;
        //System.out.println("A transaction has been successfully committed.");
      } else {
        System.out.println("You are trying to commit an inactive transaction.");
        //return false;
      }
      ShepherdPMF.setShepherdState(action+"_"+shepherdID, "commit");


    } catch (JDOUserException jdoe) {
      jdoe.printStackTrace();
      System.out.println("I failed to commit a transaction." + "\n" + jdoe.getStackTrace());
      //return false;
    } catch (JDOException jdoe2) {
      jdoe2.printStackTrace();
      Throwable[] throwables=jdoe2.getNestedExceptions();
      int numThrowables=throwables.length;
      for(int i=0;i<numThrowables;i++){
        Throwable t=throwables[i];
        if(t instanceof java.sql.SQLException){
          java.sql.SQLException exc=(java.sql.SQLException)t;
          java.sql.SQLException g=exc.getNextException();
          g.printStackTrace();
        }
        t.printStackTrace();
      }
      //return false;
    }
    //added to prevent conflicting calls jah 1/19/04
    catch (NullPointerException npe) {
      System.out.println("A null pointer exception was thrown while trying to commit a transaction!");
      npe.printStackTrace();
      //return false;
		}
  }

  /**
   * Commits (makes permanent) any changes made to an open database
   */
  public void commitDBTransaction(String action) {
    commitDBTransaction();
  }


  /**
   * Closes a PersistenceManager
   */
  public void closeDBTransaction() {
    try {
      if ((pm != null) && (!pm.isClosed())) {
        pm.close();

      }
      //ShepherdPMF.setShepherdState(action+"_"+shepherdID, "close");
      ShepherdPMF.removeShepherdState(action+"_"+shepherdID);

      //logger.info("A PersistenceManager has been successfully closed.");
    } catch (JDOUserException jdoe) {
      System.out.println("I hit an error trying to close a DBTransaction.");
      jdoe.printStackTrace();

      //logger.error("I failed to close a PersistenceManager."+"\n"+jdoe.getStackTrace());
    } catch (NullPointerException npe) {
      System.out.println("I hit a NullPointerException trying to close a DBTransaction.");
      npe.printStackTrace();
    }
  }


  /**
   * Undoes any changes made to an open database.
   */
  public void rollbackDBTransaction() {
    try {
      if ((pm != null) && (pm.currentTransaction().isActive())) {
        //System.out.println("     Now rollingback a transaction with pm"+(String)pm.getUserObject());
        pm.currentTransaction().rollback();
        //System.out.println("A transaction has been successfully committed.");
      } else {
        //System.out.println("You are trying to rollback an inactive transaction.");
      }
      ShepherdPMF.setShepherdState(action+"_"+shepherdID, "rollback");


    } catch (JDOUserException jdoe) {
      jdoe.printStackTrace();
    } catch (JDOFatalUserException fdoe) {
      fdoe.printStackTrace();
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
  }


  public Iterator<Keyword> getAllKeywords() {
    Extent allKeywords = null;
    Iterator it = null;
    try {
      allKeywords = pm.getExtent(Keyword.class, true);
      Query acceptedKeywords = pm.newQuery(allKeywords);
      acceptedKeywords.setOrdering("readableName descending");
      Collection c = (Collection) (acceptedKeywords.execute());
      ArrayList<Keyword> al=new ArrayList<Keyword>(c);
      acceptedKeywords.closeAll();
      it = al.iterator();
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return null;
    }
    return it;
  }

  public List<User> getAllUsers() {
    return getAllUsers("username ascending NULLS LAST");
  }
  
  public List<User> getAllUsers(String ordering) {
    Collection c;
    ArrayList<User> list = new ArrayList<User>();
    //System.out.println("Shepherd.getAllUsers() called in context "+getContext());
    Extent userClass = pm.getExtent(User.class, true);
    Query users = pm.newQuery(userClass);
    if(ordering!=null) {
      users.setOrdering(ordering);
    }
    try {
      c = (Collection) (users.execute());
      if(c!=null){
        list = new ArrayList<User>(c);
      }
      users.closeAll();
      //System.out.println("Shepherd.getAllUsers() found "+list.size()+" users");
      return list;
    }
    catch (Exception npe) {
      //System.out.println("Error encountered when trying to execute Shepherd.getAllUsers. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      users.closeAll();
      return null;
    }
  }

  public String getAllUserEmailAddressesForLocationID(String locationID, String context){
    String addresses="";
    List<User> users = getUsersWithEmailAddresses();
    int numUsers=users.size();
    for(int i=0;i<numUsers;i++){
      User user=users.get(i);
      if(doesUserHaveRole(user.getUsername(), locationID.trim(),context)){
        if((user.getReceiveEmails())&&(user.getEmailAddress()!=null)){addresses+=(user.getEmailAddress()+",");}
      }
    }
    return addresses;
  }

  public Iterator getAllOccurrences() {
    Extent allOccurs = null;
    Iterator it = null;
    try {
      allOccurs = pm.getExtent(Occurrence.class, true);
      Query acceptedOccurs = pm.newQuery(allOccurs);
      Collection c = (Collection) (acceptedOccurs.execute());
      ArrayList al=new ArrayList(c);
      acceptedOccurs.closeAll();
      it = al.iterator();


    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return null;
    }
    return it;
  }

  public List<Role> getAllRoles() {
    Extent allKeywords = null;
    ArrayList<Role> it = new ArrayList<Role>();
    try {
      allKeywords = pm.getExtent(Role.class, true);
      Query acceptedKeywords = pm.newQuery(allKeywords);
      Collection c = (Collection) (acceptedKeywords.execute());
      it=new ArrayList<Role>(c);
      acceptedKeywords.closeAll();
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return it;
    }
    return it;
  }

  public Iterator<Keyword> getAllKeywords(Query acceptedKeywords) {
    Extent allKeywords = null;
    Iterator it = null;
    try {
      acceptedKeywords.setOrdering("readableName descending");
      Collection c = (Collection) (acceptedKeywords.execute());
      ArrayList<Keyword> al=new ArrayList<Keyword>(c);
      it = al.iterator();
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return null;
    }
    return it;
  }


  public int getNumKeywords() {
    Extent allWords = null;
    int num = 0;
    Query q = pm.newQuery(Keyword.class); // no filter, so all instances match

    try {
      Collection results = (Collection) q.execute();
      num = results.size();

    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      q.closeAll();
      return num;
    }
    q.closeAll();
    return num;
  }


  public List<SinglePhotoVideo> getThumbnails(Shepherd myShepherd,HttpServletRequest request, ArrayList<String> encList, int startNum, int endNum, String[] keywords) {
    ArrayList<SinglePhotoVideo> thumbs = new ArrayList<SinglePhotoVideo>();
    boolean stopMe = false;
    int encIter=0;
    int count = 0;
    int numEncs=encList.size();
    //while (it.hasNext()) {
    while((count<=endNum)&&(encIter<numEncs)){

      String nextCatalogNumber=encList.get(encIter);
      int numImages=getNumAnnotationsForEncounter(nextCatalogNumber);


      if ((count + numImages) >= startNum) {
    	  Encounter enc = myShepherd.getEncounter(nextCatalogNumber);
    	  List<SinglePhotoVideo> images=getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber());
        for (int i = 0; i < images.size(); i++) {
          count++;
          if ((count <= endNum) && (count >= startNum)) {
            String m_thumb = "";

            //check for video or image
            String imageName = (String) images.get(i).getFilename();

            //check if this image has one of the assigned keywords
            boolean hasKeyword = false;
            if ((keywords == null) || (keywords.length == 0)) {
              hasKeyword = true;
            } else {
              int numKeywords = keywords.length;
              for (int n = 0; n < numKeywords; n++) {
                if (!keywords[n].equals("None")) {
                  Keyword word = getKeyword(keywords[n]);

                  if ((images.get(i).getKeywords()!=null)&&images.get(i).getKeywords().contains(word)) {


                  //if (word.isMemberOf(enc.getCatalogNumber() + "/" + imageName)) {

                    hasKeyword = true;
                    //System.out.println("member of: "+word.getReadableName());
                  }
                } else {
                  hasKeyword = true;
                }

              }

            }

			//check for specific filename conditions here
			if((request.getParameter("filenameField")!=null)&&(!request.getParameter("filenameField").equals(""))){
					String nameString=ServletUtilities.cleanFileName(ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("filenameField").trim()));
					if(!nameString.equals(imageName)){hasKeyword=false;}
			}
      if (hasKeyword && isAcceptableVideoFile(imageName)) {
              m_thumb = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
              //thumbs.add(m_thumb);
              thumbs.add(images.get(i));
      }
      else if (hasKeyword && isAcceptableImageFile(imageName)) {
              m_thumb = enc.getEncounterNumber() + "/" + (i + 1) + ".jpg" + "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
              //thumbs.add(m_thumb);
              thumbs.add(images.get(i));
      }
      else {
              count--;
      }
          } else if (count > endNum) {
            stopMe = true;
          }
        }
      } //end if
      else {
        count += numImages;
      }
      encIter++;
    }//end while
    return thumbs;
  }

  public List<SinglePhotoVideo> getMarkedIndividualThumbnails(HttpServletRequest request, Iterator<MarkedIndividual> it, int startNum, int endNum, String[] keywords) {
    ArrayList<SinglePhotoVideo> thumbs = new ArrayList<SinglePhotoVideo>();

    boolean stopMe = false;
    int count = 0;
    while (it.hasNext()&&!stopMe) {
      MarkedIndividual markie = it.next();
      Iterator allEncs = markie.getEncounters().iterator();
      while (allEncs.hasNext()&&!stopMe) {
        Encounter enc = (Encounter) allEncs.next();
        List<SinglePhotoVideo> images=getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber());

        if ((count + images.size()) >= startNum) {
          for (int i = 0; i < images.size(); i++) {
            count++;
            if ((count <= endNum) && (count >= startNum)) {
              String m_thumb = "";

              //check for video or image
              String imageName = (String) images.get(i).getFilename();

              //check if this image has one of the assigned keywords
              boolean hasKeyword = false;
              if ((keywords == null) || (keywords.length == 0)) {
                hasKeyword = true;
              }
              else {
                int numKeywords = keywords.length;
                for (int n = 0; n < numKeywords; n++) {
                  if (!keywords[n].equals("None")) {
                    Keyword word = getKeyword(keywords[n]);

                    if ((images.get(i).getKeywords()!=null)&&images.get(i).getKeywords().contains(word)) {


                    //if (word.isMemberOf(enc.getCatalogNumber() + "/" + imageName)) {

                      hasKeyword = true;
                      //System.out.println("member of: "+word.getReadableName());
                    }
                  } else {
                    hasKeyword = true;
                  }

                }

              }

        //check for specific filename conditions here
        if((request.getParameter("filenameField")!=null)&&(!request.getParameter("filenameField").equals(""))){
            String nameString=ServletUtilities.cleanFileName(ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("filenameField").trim()));
            if(!nameString.equals(imageName)){hasKeyword=false;}
        }
        if (hasKeyword && isAcceptableVideoFile(imageName)) {
                m_thumb = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
                //thumbs.add(m_thumb);
                thumbs.add(images.get(i));
        }
        else if (hasKeyword && isAcceptableImageFile(imageName)) {
                m_thumb = enc.getEncounterNumber() + "/" + (i + 1) + ".jpg" + "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
                //thumbs.add(m_thumb);
                thumbs.add(images.get(i));
        }
        else {
                count--;
        }
            }
            else if (count > endNum) {
              stopMe = true;
              return thumbs;
            }
          }
        } //end if
        else {
          count += images.size();
        }

      }//end while

    }//end while
    return thumbs;
  }

  public int getNumThumbnails(Iterator it, String[] keywords) {
    //Vector thumbs=new Vector();
    //boolean stopMe=false;
    int count = 0;
    while (it.hasNext()) {
      Encounter enc = (Encounter) it.next();
      for (int i = 0; i < enc.getAdditionalImageNames().size(); i++) {
        count++;
        //String m_thumb="";

        //check for video or image
        String imageName = (String) enc.getAdditionalImageNames().get(i);

        //check if this image has one of the assigned keywords
        boolean hasKeyword = false;
        if ((keywords == null) || (keywords.length == 0)) {
          hasKeyword = true;
        } else {
          int numKeywords = keywords.length;
          for (int n = 0; n < numKeywords; n++) {
            if (!keywords[n].equals("None")) {
              Keyword word = getKeyword(keywords[n]);

              //if (word.isMemberOf(enc.getCatalogNumber() + "/" + imageName)) {
              if(enc.hasKeyword(word)){
                hasKeyword = true;
                //System.out.println("member of: "+word.getReadableName());
              }
            } else {
              hasKeyword = true;
            }

          }

        }

        if (hasKeyword && isAcceptableVideoFile(imageName)) {
          //m_thumb="http://"+CommonConfiguration.getURLLocation()+"/images/video.jpg"+"BREAK"+enc.getEncounterNumber()+"BREAK"+imageName;
          //thumbs.add(m_thumb);
        } else if (hasKeyword && isAcceptableImageFile(imageName)) {
          //m_thumb=enc.getEncounterNumber()+"/"+(i+1)+".jpg"+"BREAK"+enc.getEncounterNumber()+"BREAK"+imageName;
          //thumbs.add(m_thumb);
        } else {
          count--;
        }

      }


    }//end while
    return count;
  }


  public int getNumMarkedIndividualThumbnails(Iterator<MarkedIndividual> it, String[] keywords) {
    int count = 0;
    while (it.hasNext()) {
      MarkedIndividual indie = it.next();
      Iterator allEncs = indie.getEncounters().iterator();
      while (allEncs.hasNext()) {
        Encounter enc = (Encounter) allEncs.next();
        for (int i = 0; i < enc.getAdditionalImageNames().size(); i++) {
          count++;
          //String m_thumb="";

          //check for video or image
          String imageName = (String) enc.getAdditionalImageNames().get(i);

          //check if this image has one of the assigned keywords
          boolean hasKeyword = false;
          if ((keywords == null) || (keywords.length == 0)) {
            hasKeyword = true;
          } else {
            int numKeywords = keywords.length;
            for (int n = 0; n < numKeywords; n++) {
              if (!keywords[n].equals("None")) {
                Keyword word = getKeyword(keywords[n]);
                //if (word.isMemberOf(enc.getCatalogNumber() + "/" + imageName)) {
                if(enc.hasKeyword(word)){
                  hasKeyword = true;
                  //System.out.println("member of: "+word.getReadableName());
                }
              } else {
                hasKeyword = true;
              }

            }

          }

          if (hasKeyword && isAcceptableVideoFile(imageName)) {
            //m_thumb="http://"+CommonConfiguration.getURLLocation()+"/images/video.jpg"+"BREAK"+enc.getEncounterNumber()+"BREAK"+imageName;
            //thumbs.add(m_thumb);
          } else if (hasKeyword && isAcceptableImageFile(imageName)) {
            //m_thumb=enc.getEncounterNumber()+"/"+(i+1)+".jpg"+"BREAK"+enc.getEncounterNumber()+"BREAK"+imageName;
            //thumbs.add(m_thumb);
          } else {
            count--;
          }

        }
      } //end while

    }//end while
    return count;
  }


  /**
   * Returns the number of acceptable images/videos in the enter Iterator.
   *
   * @param it The filtered iterator of encounters to count the number of images/videos in.
   * @return The number of acceptable images/videos in the Iterator of encounters.
   */
  public int getNumThumbnails(Vector it) {
    int count = 0;
    int numEncs = it.size();
    for (int f = 0; f < numEncs; f++) {
      Encounter enc = (Encounter) it.get(f);
      for (int i = 0; i < enc.getAdditionalImageNames().size(); i++) {
        count++;

        //check for video or image
        String addTextFile = (String) enc.getAdditionalImageNames().get(i);
        if ((!isAcceptableImageFile(addTextFile)) && (!isAcceptableVideoFile(addTextFile))) {
          count--;
        }
      }
    }//end while
    return count;
  }

  static public boolean isAcceptableImageFile(String fileName) {
    Objects.requireNonNull(fileName);
    return fileName.matches("^(.+)\\.(?i:jpe?g|jpe|png|gif)$");
  }

  static public boolean isAcceptableVideoFile(String fileName) {
    Objects.requireNonNull(fileName);
    return fileName.matches("^(.+)\\.(?i:mp4|mov|avi|mpg|wmv|flv)$");
  }


  public Adoption getRandomAdoption() {

    //get the random number
    int numAdoptions = getNumAdoptions();
    Random ran = new Random();
    int ranNum = 0;
    if (numAdoptions > 1) {
      ranNum = ran.nextInt(numAdoptions);
    }

    //return the adoption
    int currentPosition = 0;
    Iterator<Adoption> it = getAllAdoptionsNoQuery();
    while (it.hasNext()) {
      Adoption ad = it.next();
      if (currentPosition == ranNum) {
        return ad;
      }
      currentPosition++;
    }
    return null;
  }

  public List<MarkedIndividual> getMarkedIndividualsByAlternateID(String altID) {
    ArrayList al = new ArrayList();
    try{
      String filter = "this.alternateid.toLowerCase() == \"" + altID.toLowerCase() + "\"";
      Extent encClass = pm.getExtent(MarkedIndividual.class, true);
      Query acceptedEncounters = pm.newQuery(encClass, filter);
      Collection c = (Collection) (acceptedEncounters.execute());
      al = new ArrayList(c);
      acceptedEncounters.closeAll();
    }
    catch(Exception e){e.printStackTrace();}
    return al;
  }

  /**
   * Provides a case-insensitive way to retrieve a MarkedIndividual. It returns the first instance of such it finds.
   * @param myID The individual ID to return in any case.
   * @return
   */
  public MarkedIndividual getMarkedIndividualCaseInsensitive(String myID) {
    String filter = "this.individualID.toLowerCase() == \""+myID.toLowerCase()+"\"";
    Extent encClass = pm.getExtent(MarkedIndividual.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al = new ArrayList(c);
    acceptedEncounters.closeAll();
    if((al!=null)&&(al.size()>0)){return (MarkedIndividual)al.get(1);}
    return null;
  }

  public List<Encounter> getEncountersByAlternateID(String altID) {
    String filter = "this.otherCatalogNumbers.toLowerCase() == \"" + altID.toLowerCase() + "\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al = new ArrayList(c);
    acceptedEncounters.closeAll();
    return al;
  }

  public List<MarkedIndividual> getMarkedIndividualsByNickname(String altID) {
    String filter = "this.nickName.toLowerCase() == \"" + altID.toLowerCase() + "\"";
    Extent encClass = pm.getExtent(MarkedIndividual.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al = new ArrayList(c);
    acceptedEncounters.closeAll();
    return al;
  }

  //get earliest sighting year for setting search parameters
  public int getEarliestSightingYear() {

    try{
      Query q = pm.newQuery("SELECT min(year) FROM org.ecocean.Encounter where year > 0");
      int value=((Integer) q.execute()).intValue();
      q.closeAll();
      return value;
    }
    catch(Exception e){return -1;}
  }

  public int getFirstSubmissionYear() {
    //System.out.println("Starting getFirstSubmissionYear");
    try{
      Query q = pm.newQuery("SELECT min(dwcDateAddedLong) FROM org.ecocean.Encounter");
      //System.out.println("     I have a query");
      long value=((Long) q.execute()).longValue();
      //System.out.println("     I have a value of: "+value);
      q.closeAll();
      org.joda.time.DateTime lcd = new org.joda.time.DateTime(value);
      return lcd.getYear();
      //return value;
    }
    catch(Exception e){e.printStackTrace();System.out.println("Why can't I find a min dwcDateAddedLong?");return -1;}
  }

  public int getLastSightingYear() {
    try{
      Query q = pm.newQuery("SELECT max(year) FROM org.ecocean.Encounter");
      int value=((Integer) q.execute()).intValue();
      q.closeAll();
      return value;
    }
    catch(Exception e){return -1;}
  }

  public int getLastMonthOfSightingYear(int yearHere) {
    try{
      Query q = pm.newQuery("SELECT max(month) FROM org.ecocean.Encounter WHERE this.year == " + yearHere);
      int value=((Integer) q.execute()).intValue();
      q.closeAll();
      return value;
    }
    catch(Exception e){return -1;}
  }

  public List<String> getAllLocationIDs() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct locationID");
    q.setOrdering("locationID ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
    q.closeAll();
    return al;
  }

  public List<String> getAllCountries() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct country");
    q.setOrdering("country ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public List<String> getAllHaplotypes() {
    Query q = pm.newQuery(MitochondrialDNAAnalysis.class);
    q.setResult("distinct haplotype");
    q.setOrdering("haplotype ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public List<String> getAllRoleNames() {
    Query q = pm.newQuery(Role.class);
    q.setResult("distinct rolename");
    q.setOrdering("rolename ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllUsernames() {
    Query q = pm.newQuery(User.class);
    q.setResult("distinct username");
    q.setOrdering("username ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public List<String> getAllGeneticSexes() {
    Query q = pm.newQuery(SexAnalysis.class);
    q.setResult("distinct sex");
    q.setOrdering("sex ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public List<String> getAllLoci() {
    Query q = pm.newQuery(Locus.class);
    q.setResult("distinct name");
    q.setOrdering("name ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllSocialUnitNames() {
    ArrayList<String> comNames=new ArrayList<String>();
    Query q = pm.newQuery(Relationship.class);
    try{

      q.setResult("distinct relatedSocialUnitName");
      q.setOrdering("relatedSocialUnitName ascending");
      Collection results = (Collection) q.execute();
      comNames=new ArrayList<String>(results);

    }
    catch(Exception e){}
    q.closeAll();
    return comNames;
  }

  public List<String> getAllGenuses() {
      Query q = pm.newQuery(Encounter.class);
      q.setResult("distinct genus");
      q.setOrdering("genus ascending");
      Collection results = (Collection) q.execute();
      ArrayList al=new ArrayList(results);
	      q.closeAll();
    return al;
  }

  public List<String> getAllEncounterStrVals(String fieldName) {
    return getAllStrVals(Encounter.class, fieldName);
  }

  public List<String> getAllStrVals(Class fromClass, String fieldName) {
    Query q = pm.newQuery(fromClass);
    q.setResult("distinct "+fieldName);
    q.setOrdering(fieldName+" ascending");
    Collection results = (Collection) q.execute();
    List resList = new ArrayList(results);
    q.closeAll();
    return resList;
  }

  // gets properties vals, then all actual vals, and returns the combined list without repeats
  public List<String> getAllPossibleVals(Class fromClass, String fieldName, Properties props) {
    List<String> indexVals = Util.getIndexedPropertyValues(fieldName, props);
    List<String> usedVals = getAllStrVals(fromClass, fieldName);
    for (String usedVal: usedVals) {
      if (!indexVals.contains(usedVal)) indexVals.add(usedVal);
    }
    return indexVals;
  }

  public List<String> getAllSpecificEpithets() {
      Query q = pm.newQuery(Encounter.class);
      q.setResult("distinct specificEpithet");
      q.setOrdering("specificEpithet ascending");
      Collection results = (Collection) q.execute();
      ArrayList al=new ArrayList(results);
	      q.closeAll();
    return al;
  }

  public List<String> getAllBehaviors() {

    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct behavior");
    q.setOrdering("behavior ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;


    //temporary way
    /**
     * ArrayList<String> al=new ArrayList<String>();
     Iterator allenc=getAllEncounters();
     while(allenc.hasNext()){
     Encounter enc=(Encounter)allenc.next();
     if((enc.getBehavior()!=null)&&(!enc.getBehavior().equals(""))){
     if(!al.contains(enc.getBehavior())){
     al.add(enc.getBehavior());
     }
     }
     }
     return al;
     */
  }

  public List<String> getAllVerbatimEventDates() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct verbatimEventDate");
    q.setOrdering("verbatimEventDate ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public List<String> getAllRecordedBy() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct recordedBy");
    q.setOrdering("recordedBy ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }
  
  public ArrayList<Survey> getAllSurveys() {
    ArrayList<Survey> svs = new ArrayList<Survey>();
    Extent svyClass = pm.getExtent(Survey.class, true);
    Iterator svsIt = svyClass.iterator();
    Survey sv = null;
    while (svsIt.hasNext()) {
      sv = (Survey) svsIt.next();
      svs.add(sv);
    }
    if (!svs.isEmpty()) {
      return svs;
    }
    return null;      
  }

  public List<Encounter> getEncountersWithHashedEmailAddress(String hashedEmail) {
    String filter = "((this.hashedSubmitterEmail.indexOf('" + hashedEmail + "') != -1)||(this.hashedPhotographerEmail.indexOf('" + hashedEmail + "') != -1)||(this.hashedInformOthers.indexOf('" + hashedEmail + "') != -1))";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al=new ArrayList(c);
	    acceptedEncounters.closeAll();
    return al;
  }

  public List<String> getAllPatterningCodes(){
    Query q = pm.newQuery (Encounter.class);
    q.setResult ("distinct patterningCode");
    q.setOrdering("patterningCode ascending");
    Collection results = (Collection)q.execute ();
     ArrayList al=new ArrayList(results);
		    q.closeAll();
    return al;
  }

  public List<String> getAllLifeStages(){
    Query q = pm.newQuery (Encounter.class);
    q.setResult ("distinct lifeStage");
    q.setOrdering("lifeStage ascending");
    Collection results = (Collection)q.execute ();
     ArrayList al=new ArrayList(results);
		    q.closeAll();
    return al;
  }


  public List<MarkedIndividual> getAllMarkedIndividualsInCommunity(String communityName){
    ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>();
    Extent encClass = pm.getExtent(Relationship.class, true);
    String filter2use = "this.relatedSocialUnitName == \""+communityName+"\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter2use);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList listy = new ArrayList(c);
    int listySize=listy.size();
    for(int i=0;i<listySize;i++){
      Relationship rely=(Relationship)listy.get(i);
      if(rely.getMarkedIndividualName1()!=null){
        String name1=rely.getMarkedIndividualName1();
        if(isMarkedIndividual(name1)){
          MarkedIndividual indie=getMarkedIndividual(name1);
          if(!indies.contains(indie)){indies.add(indie);}
        }
      }
      if(rely.getMarkedIndividualName2()!=null){
        String name2=rely.getMarkedIndividualName2();
        if(isMarkedIndividual(name2)){
          MarkedIndividual indie=getMarkedIndividual(name2);
          if(!indies.contains(indie)){indies.add(indie);}
        }
      }

    }
    acceptedEncounters.closeAll();
    return indies;
  }

  public ArrayList<Relationship> getAllRelationshipsForMarkedIndividual(String indieName){
    Extent encClass = pm.getExtent(Relationship.class, true);
    String filter2use = "this.markedIndividualName1 == \""+indieName+"\" || this.markedIndividualName2 == \""+indieName+"\"";
    Query query = pm.newQuery(encClass, filter2use);
    Collection c = (Collection) (query.execute());
    //System.out.println("Num relationships for MarkedIndividual "+indieName+": "+c.size());
    ArrayList<Relationship> listy = new ArrayList<Relationship>(c);
    query.closeAll();
    return listy;
  }

  public ArrayList<String> getAllSocialUnitsForMarkedIndividual(String indieName){
    Extent encClass = pm.getExtent(Relationship.class, true);

    String filter2use = "this.markedIndividualName1 == \""+indieName+"\" || this.markedIndividualName2 == \""+indieName+"\"";
    Query query = pm.newQuery(encClass, filter2use);
    query.setResult("distinct relatedSocialUnitName");
    Collection c = (Collection) (query.execute());
    //System.out.println("Num relationships for MarkedIndividual "+indieName+": "+c.size());
    ArrayList<String> listy = new ArrayList<String>();
    if(c!=null)listy = new ArrayList<String>(c);
    query.closeAll();
    return listy;
  }

  public ArrayList<String> getAllRoleNamesForMarkedIndividual(String indieName){
    ArrayList<String> roles=new ArrayList<String>();

    ArrayList<Relationship> rels=getAllRelationshipsForMarkedIndividual(indieName);
    int numRels=rels.size();
    for(int i=0;i<numRels;i++){

      Relationship rel=rels.get(i);
      if((rel.getMarkedIndividualName1().equals(indieName))&&(rel.getMarkedIndividualRole1()!=null)&&(!roles.contains(rel.getMarkedIndividualRole1()))){
        roles.add(rel.getMarkedIndividualRole1());
      }
      if((rel.getMarkedIndividualName2().equals(indieName))&&(rel.getMarkedIndividualRole2()!=null)&&(!roles.contains(rel.getMarkedIndividualRole2()))){
        roles.add(rel.getMarkedIndividualRole2());
      }

    }

    return roles;
  }

  public ArrayList<Relationship> getAllRelationshipsForCommunity(String commName){
    //ArrayList<Relationship> relies=new ArrayList<Relationship>();
    Extent encClass = pm.getExtent(Relationship.class, true);
    String filter2use = "this.communityName == \""+commName+"\"";
    Query acceptedEncounters = pm.newQuery(encClass, filter2use);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList<Relationship> listy = new ArrayList<Relationship>(c);
    acceptedEncounters.closeAll();
    return listy;
  }

  public int getNumCooccurrencesBetweenTwoMarkedIndividual(String individualID1,String individualID2){
    int numCooccur=0;

    ArrayList<String> occurenceIDs1=getOccurrenceIDsForMarkedIndividual(individualID1);
    //System.out.println("zzzOccurrences for indie "+individualID1+": "+occurenceIDs1.toString());
    List<String> occurenceIDs2=getOccurrenceIDsForMarkedIndividual(individualID2);
    //System.out.println("zzzOccurrences for indie "+individualID2+": "+occurenceIDs2.toString());

    int numOccurenceIDs1=occurenceIDs1.size();
    if((numOccurenceIDs1>0)&&(occurenceIDs2.size()>0)){
      //System.out.println(numOccurenceIDs1+":"+occurenceIDs2.size());
      for(int i=0;i<numOccurenceIDs1;i++){
        if(occurenceIDs2.contains(occurenceIDs1.get(i))){
          //System.out.println("Checking occurrence: "+occurenceIDs1.get(i));
          numCooccur++;
          //System.out.println("zzzMatching co-occurrence: "+occurenceIDs1.get(i));
        }
      }
    }
    return numCooccur;
  }

  public ArrayList<String> getOccurrenceIDsForMarkedIndividual(String individualID){
    ArrayList<String> occurrenceIDs=new ArrayList<String>();

   String filter="SELECT distinct occurrenceID FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individualID == \""+individualID+"\"  VARIABLES org.ecocean.Encounter enc";

    Query q = pm.newQuery (filter);

    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
    q.closeAll();
    int numResults=al.size();
    for(int i=0;i<numResults;i++) {
      occurrenceIDs.add((String)al.get(i));
    }
    //System.out.println("zzzOccurrences for "+individualID+": "+occurrenceIDs.toString());
    return occurrenceIDs;

  }



  public Measurement getMeasurementOfTypeForEncounter(String type, String encNum) {
    String filter = "type == \""+type+"\" && correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(Measurement.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    if((c!=null)&&(c.size()>0)){
      ArrayList<Measurement> al=new ArrayList<Measurement>(c);
      samples.closeAll();
      return (al).get(0);
    }
    else{return null;}
  }

  public ArrayList<Measurement> getMeasurementsForEncounter(String encNum) {
    String filter = "correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(Measurement.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    if((c!=null)&&(c.size()>0)){
      ArrayList<Measurement> al=new ArrayList<Measurement>(c);
      samples.closeAll();
      return (al);
    }
    else{return null;}
  }

  public ArrayList<ScanTask> getAllScanTasksForUser(String user) {
    String filter = "submitter == \""+user+"\"";
    Extent encClass = pm.getExtent(ScanTask.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());

    if(c!=null){
    ArrayList<ScanTask> it=new ArrayList<ScanTask>(c);
    samples.closeAll();
    return it;
    }
    else{samples.closeAll();return null;}
  }

  public User getRandomUserWithPhotoAndStatement(){
    //(username.toLowerCase().indexOf('demo') == -1)
    String filter = "fullName != null && userImage != null && userStatement != null && (username.toLowerCase().indexOf('demo') == -1) && (username.toLowerCase().indexOf('test') == -1)";
    Extent encClass = pm.getExtent(User.class, true);
    Query q = pm.newQuery(encClass, filter);
    Collection c = (Collection) (q.execute());
    if((c!=null)&&(c.size()>0)){
      ArrayList<User> matchingUsers=new ArrayList<>(c);
      q.closeAll();
      int numUsers=matchingUsers.size();
      Random rn = new Random();
      int userNumber = rn.nextInt(numUsers);
      return matchingUsers.get(userNumber);
    }
    q.closeAll();
    return null;
  }

  public ArrayList<Encounter> getMostRecentIdentifiedEncountersByDate(int numToReturn){
    ArrayList<Encounter> matchingEncounters = new ArrayList<Encounter>();
    String filter = "SELECT FROM org.ecocean.Encounter WHERE individualID != null ORDER BY dwcDateAddedLong descending RANGE 1,"+(numToReturn+1);
    Query q = pm.newQuery(filter);
    Collection c = (Collection) (q.execute());
    matchingEncounters = new ArrayList<Encounter>(c);
    q.closeAll();
    return matchingEncounters;
  }

  public Map<String,Integer> getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(long startTime){


    Map<String,Integer> matchingUsers=new HashMap<String,Integer>();


    String filter = "submitterID != null && dwcDateAddedLong >= "+startTime;
    //System.out.println("     My filter is: "+filter);
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query q = pm.newQuery(encClass, filter);
    q.setResult("distinct submitterID");
    Collection c = (Collection) (q.execute());
    ArrayList<String> allUsers = new ArrayList<String>(c);
    q.closeAll();
    
    
    int numAllUsers=allUsers.size();
    //System.out.println("     All users: "+numAllUsers);
    QueryCache qc=QueryCacheFactory.getQueryCache(getContext());
    for(int i=0;i<numAllUsers;i++){
      String thisUser=allUsers.get(i);
      if((!thisUser.trim().equals(""))&&(getUser(thisUser)!=null)){

        if(qc.getQueryByName(("numRecentEncounters_"+thisUser))!=null){
          CachedQuery cq=qc.getQueryByName(("numRecentEncounters_"+thisUser));
          matchingUsers.put(thisUser, (cq.executeCountQuery(this)));
        }
        
        else{
          String userFilter = "SELECT FROM org.ecocean.Encounter WHERE submitterID == \"" + thisUser + "\" && dwcDateAddedLong >= "+startTime;
          //update rankings hourly
          CachedQuery cq=new CachedQuery(("numRecentEncounters_"+thisUser),userFilter,3600000);
          qc.addCachedQuery(cq);
          matchingUsers.put(thisUser, (cq.executeCountQuery(this)));
          
        }
        
      }
    }

    return sortByValues(matchingUsers);
  }

  public static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
    Comparator<K> valueComparator =  new Comparator<K>() {
        public int compare(K k1, K k2) {
            int compare = map.get(k2).compareTo(map.get(k1));
            if (compare == 0) return 1;
            else return compare;
        }
    };
    Map<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
    sortedByValues.putAll(map);
    return sortedByValues;
}


  public Adoption getRandomAdoptionWithPhotoAndStatement(){
    String filter = "adopterName != null && adopterImage != null && adopterQuote != null";
    Extent encClass = pm.getExtent(Adoption.class, true);
    Query q = pm.newQuery(encClass, filter);
    Collection c = (Collection) (q.execute());
    if((c!=null)&&(c.size()>0)){
      ArrayList<Adoption> matchingAdoptions=new ArrayList<>(c);
      q.closeAll();
      int numUsers=matchingAdoptions.size();
      Random rn = new Random();
      int adoptNumber = rn.nextInt(numUsers);
      return matchingAdoptions.get(adoptNumber);
    }
    q.closeAll();
    return null;
  }

  public int getNumAnnotationsForEncounter(String encounterID){
    ArrayList<Annotation> al=getAnnotationsForEncounter(encounterID);
    return al.size();
  }

  public ArrayList<Annotation> getAnnotationsForEncounter(String encounterID){
    String filter="SELECT FROM org.ecocean.Annotation WHERE enc.catalogNumber == \""+encounterID+"\" && enc.annotations.contains(this)  VARIABLES org.ecocean.Encounter enc";
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    ArrayList<Annotation> al=new ArrayList<Annotation>(c);
    query.closeAll();
    return al;
  }

  public ArrayList<Annotation> getAnnotationsWithACMId(String acmId){
    String filter = "this.acmId == \""+acmId+"\"";
    Extent annClass = pm.getExtent(Annotation.class, true);
    Query anns = pm.newQuery(annClass, filter);
    Collection c = (Collection) (anns.execute());
    ArrayList<Annotation> al = new ArrayList(c);
    anns.closeAll();
    if((al!=null)&&(al.size()>0)) {
      return al;
    }
    return null;
  }

  //used to describe where this Shepherd is and what it is supposed to be doing
  public void setAction(String newAction){

    String state="";

    if(ShepherdPMF.getShepherdState(action+"_"+shepherdID)!=null){
      state=ShepherdPMF.getShepherdState(action+"_"+shepherdID);
      ShepherdPMF.removeShepherdState(action+"_"+shepherdID);
    }
    this.action=newAction;
    ShepherdPMF.setShepherdState(action+"_"+shepherdID, state);
  }

  public String getAction(){return action;}

  public List<String> getAllEncounterNumbers(){
      List<String> encs=null;
      String filter="SELECT DISTINCT catalogNumber FROM org.ecocean.Encounter";  
      Query query=getPM().newQuery(filter);
      Collection c = (Collection) (query.execute());
      encs=new ArrayList<String>(c);
      query.closeAll();
      return encs;
  }
  
  public List<String> getAllUsernamesWithRoles(){
    List<String> usernames=null;
    String filter="SELECT DISTINCT username FROM org.ecocean.Role";  
    Query query=getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    usernames=new ArrayList<String>(c);
    query.closeAll();
    return usernames;
}
  

  public List<Encounter> getEncountersForSubmitter(User user, Shepherd myShepherd){
      ArrayList<Encounter> users=new ArrayList<Encounter>();
      String filter="SELECT FROM org.ecocean.Encounter WHERE submitters.contains(user) && user.uuid == \""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
      Query query=myShepherd.getPM().newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){users=new ArrayList<Encounter>(c);}
      query.closeAll();
      return users;
  }



  public List<Encounter> getEncountersForPhotographer(User user, Shepherd myShepherd){
      ArrayList<Encounter> users=new ArrayList<Encounter>();
      String filter="SELECT FROM org.ecocean.Encounter WHERE photographers.contains(user) && user.uuid == \""+user.getUUID()+"\" VARIABLES org.ecocean.User user";
      Query query=myShepherd.getPM().newQuery(filter);
      Collection c = (Collection) (query.execute());
      if(c!=null){users=new ArrayList<Encounter>(c);}
      query.closeAll();
      return users;
  }
  
  public StoredQuery getStoredQuery(String uuid) {
    StoredQuery sq = null;
    try {
      sq = ((StoredQuery) (pm.getObjectById(pm.newObjectIdInstance(StoredQuery.class, uuid), true)));
    } catch (Exception nsoe) {
      return null;
    }
    return sq;
  }
  

  public List<StoredQuery> getAllStoredQueries() {
    Extent encClass = pm.getExtent(StoredQuery.class, true);
    Query queries = pm.newQuery(encClass);
    Collection c = (Collection) (queries.execute());
    ArrayList<StoredQuery> listy=new ArrayList<StoredQuery>(c);
    queries.closeAll();
    return listy;
  }


} //end Shepherd class
