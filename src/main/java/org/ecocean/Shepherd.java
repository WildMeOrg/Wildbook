/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
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

import javax.jdo.*;
import javax.servlet.http.HttpServletRequest;

import java.util.*;
import java.io.File;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

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


  /**
   * Constructor to create a new shepherd thread object
   */
  public Shepherd(String context) {
    if (pm == null || pm.isClosed()) {
      //PersistenceManagerFactory pmf = ShepherdPMF.getPMF(context);
      localContext=context;
      try {
        pm = ShepherdPMF.getPMF(localContext).getPersistenceManager();
      } 
      catch (JDOUserException e) {
        System.out.println("Hit an excpetion while trying to instantiate a PM. Not fatal I think.");
        e.printStackTrace();
      }
      //pmf=null;
    }
  }


  public PersistenceManager getPM() {
    return pm;
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


  public boolean storeNewTask(ScanTask task) {
    //beginDBTransaction();
    try {
      pm.makePersistent(task);
      return true;
    } catch (Exception e) {
      System.out.println("I failed to store the new task number: " + task.getUniqueNumber());
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
  
  
  
  public Relationship getRelationship(String type, String indie1,String indie2) {
    Relationship tempRel = null;
    String filter = "this.type == \""+type+"\" && this.markedIndividualName1 == \""+indie1+"\" && this.markedIndividualName2 == \""+indie2+"\"";
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

    ArrayList<Role> roles = getAllRoles();
    int numRoles=roles.size();
    for(int i=0;i<numRoles;i++) {
      Role kw = (Role) roles.get(i);
      if((kw.getRolename().equals(rolename))&&(kw.getUsername().equals(username))&&(kw.getContext().equals(context))){
        return kw;
        }
    }
    return null;
  }

  public ArrayList<Role> getAllRolesForUserInContext(String username, String context) {
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
    try {
      user = ((User) (pm.getObjectById(pm.newObjectIdInstance(User.class, username.trim()), true)));
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

  public Keyword getKeyword(String readableName) {

    Iterator keywords = getAllKeywords();
	while (keywords.hasNext()) {
      Keyword kw = (Keyword) keywords.next();
      if((kw.getReadableName().equals(readableName))||(kw.getIndexname().equals(readableName))){return kw;}
  	}
  return null;

  }




  public ArrayList<String> getKeywordsInCommon(String encounterNumber1, String encounterNumber2) {
    ArrayList<String> inCommon = new ArrayList<String>();
    Encounter enc1 = getEncounter(encounterNumber1);
    Encounter enc2 = getEncounter(encounterNumber2);

    Iterator keywords = getAllKeywords();
    while (keywords.hasNext()) {
      Keyword kw = (Keyword) keywords.next();

      //if ((kw.isMemberOf(enc1)) && (kw.isMemberOf(enc2))) {
      if (enc1.hasKeyword(kw) && enc2.hasKeyword(kw)) {
        inCommon.add(kw.getReadableName());
      }


    }
    return inCommon;
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
    Iterator keywords = getAllKeywords();
	    while (keywords.hasNext()) {
      Keyword kw = (Keyword) keywords.next();
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

  public boolean isOccurrence(String name) {
    try {
      Occurrence tempShark = ((org.ecocean.Occurrence) (pm.getObjectById(pm.newObjectIdInstance(Occurrence.class, name.trim()), true)));
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
    String filter = "this.individualID == \"Unassigned\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query orphanedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (orphanedEncounters.execute());
    return c.iterator();
  }

  public Iterator getUnassignedEncountersIncludingUnapproved() {
    String filter = "this.individualID == \"Unassigned\"";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query orphanedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (orphanedEncounters.execute());
    return c.iterator();
  }

  public Iterator getUnassignedEncountersIncludingUnapproved(Query orphanedEncounters) {
    String filter = "this.individualID == \"Unassigned\" && this.state != \"unidentifiable\"";
    //Extent encClass=pm.getExtent(encounter.class, true);
    orphanedEncounters.setFilter(filter);
    Collection c = (Collection) (orphanedEncounters.execute());
    return c.iterator();
  }

  public Iterator getAllEncountersNoFilter() {
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

  public Iterator getAllEncountersNoQuery() {
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

  public Iterator getAllAdoptionsNoQuery() {
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

  public Iterator getAllAdoptionsWithQuery(Query ads) {
    try {
      Collection c = (Collection) (ads.execute());
      Iterator it = c.iterator();
      return it;
    } catch (Exception npe) {
      npe.printStackTrace();
      return null;
    }
  }

  public Iterator getAllScanTasksNoQuery() {
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

  public Iterator getAllScanWorkItemsNoQuery() {
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
  public Iterator getAllEncounters() {
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

  public Iterator getAllEncounters(Query acceptedEncounters) {
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

  public ArrayList getAllOccurrences(Query myQuery) {
    Collection c;
    try {
      c = (Collection) (myQuery.execute());
      ArrayList list = new ArrayList(c);
      //Collections.reverse(list);
      Iterator it = list.iterator();
      return list;
    } catch (Exception npe) {
      System.out.println("Error encountered when trying to execute getAllOccurrences(Query). Returning a null collection.");
      npe.printStackTrace();
      return null;
    }
  }

  public ArrayList<SinglePhotoVideo> getAllSinglePhotoVideo(Query acceptedEncounters) {
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

  public Iterator getAllEncounters(Query acceptedEncounters, Map<String, Object> paramMap) {
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

  public ArrayList<PatterningPassport> getPatterningPassports() {
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

  public ArrayList getID4AvailableScanWorkItems(Query query, int pageSize, long timeout, boolean forceReturn) {
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

  public ArrayList getPairs(Query query, int pageSize) {
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

  public ArrayList getID4AvailableScanWorkItems(String taskID, Query query, int pageSize, long timeout, boolean forceReturn) {
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

  public ArrayList getAdopterEmailsForMarkedIndividual(Query query,String shark) {
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
  public Iterator getAllEncountersAndUnapproved() {
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
  public Iterator getAllEncounters(String order) {
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

  public ArrayList getAllAdoptionsForMarkedIndividual(String ind,String context) {
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
  public ArrayList<User> getAllUsersForOccurrence(Occurrence indie){
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
  public ArrayList<User> getAllUsersForMarkedIndividual(String indie){
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
 public ArrayList<User> getAllUsersForOccurrence(String occur){
   ArrayList<User> relatedUsers=new ArrayList<User>();
   if(getOccurrence(occur)!=null){
     Occurrence foundOccur=getOccurrence(occur);
     return getAllUsersForOccurrence(foundOccur);
   }
   return relatedUsers;
 }

  public ArrayList getAllAdoptionsForEncounter(String shark) {
    String filter = "this.encounter == '" + shark + "'";
    Extent encClass = pm.getExtent(Adoption.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList listy=new ArrayList(c);
    acceptedEncounters.closeAll();
    return listy;
  }

  public Iterator getAllEncounters(Query acceptedEncounters, String order) {
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
  public Iterator getAllEncounters(String order, String filter2use) {
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


  public Iterator getAllOccurrencesForMarkedIndividual(Query query,String indie) {
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
    return null;
  }

  public ArrayList<Map.Entry> getAllOtherIndividualsOccurringWithMarkedIndividual(String indie){
    HashMap<String,Integer> hmap = new HashMap<String,Integer>();
    //TreeMapOccurrenceComparator cmp=new TreeMapOccurrenceComparator(hmap);
   //TreeMap<String, Integer> map=new TreeMap<String, Integer>(cmp);
   TreeMap<String, Integer> map=new TreeMap<String, Integer>();
   String filter="SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individualID == \""+indie+"\"  VARIABLES org.ecocean.Encounter enc";
   Query query=getPM().newQuery(filter);
   Iterator it=getAllOccurrencesForMarkedIndividual(query,indie);
   if(it!=null){
      while(it.hasNext()){
         Occurrence oc=(Occurrence)it.next();
         //System.out.println("     Found an occurrence for my indie!!!!");
         ArrayList<MarkedIndividual> alreadyCounted=new ArrayList<MarkedIndividual>();
         ArrayList<Encounter> encounters=oc.getEncounters();
         int numEncounters=encounters.size();
         for(int i=0;i<numEncounters;i++){
           Encounter enc=encounters.get(i);
           if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("Unassigned"))&&(!enc.getIndividualID().equals(indie))){
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


  public ArrayList<TissueSample> getAllTissueSamplesForEncounter(String encNum) {
    String filter = "correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(TissueSample.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    return (new ArrayList<TissueSample>(c));
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
  

  public ArrayList<SinglePhotoVideo> getAllSinglePhotoVideosForEncounter(String encNum) {
    String filter = "correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(SinglePhotoVideo.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    ArrayList<SinglePhotoVideo> myArray=new ArrayList<SinglePhotoVideo>(c);
    samples.closeAll();
    return myArray;
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

  public Iterator getAllEncountersNoFilter(String order, String filter2use) {
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
  public Iterator getAllUnidentifiableEncounters(Query rejectedEncounters) {
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
  public Iterator getUnapprovedEncounters(Query acceptedEncounters) {
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList list = new ArrayList(c);
    Iterator it = list.iterator();
    return it;

  }

  public Iterator getUnapprovedEncounters(Query unapprovedEncounters, String order) {
    unapprovedEncounters.setOrdering(order);
    Collection c = (Collection) (unapprovedEncounters.execute());
    Iterator it = c.iterator();
    return it;
  }

  //Returns encounters submitted by the specified user
  public Iterator getUserEncounters(Query userEncounters, String user) {
    Collection c = (Collection) (userEncounters.execute());
    ArrayList list = new ArrayList(c);

    Iterator it = list.iterator();
    return it;
  }


  public Iterator getSortedUserEncounters(Query userEncounters, String order2) {
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
  public Iterator getAllUnidentifiableEncounters(Query unacceptedEncounters, String order) {
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

  public Occurrence getOccurrence(String id) {
    Occurrence tempShark = null;
    try {
      tempShark = ((org.ecocean.Occurrence) (pm.getObjectById(pm.newObjectIdInstance(Occurrence.class, id.trim()), true)));
    } catch (Exception nsoe) {
      nsoe.printStackTrace();
      return null;
    }
    return tempShark;
  }


  /**
   * Returns all of the names of the sharks that can be used for training purporses - i.e. have more than one encounter - in a Vector format
   *
   * @return a Vector of shark names that have more than one encounter with spots associated with them
   * @see encounter, shark, java.util.Vector
   */
  public Vector getPossibleTrainingIndividuals() {
    Iterator allSharks = getAllMarkedIndividuals();
    MarkedIndividual tempShark;
    Vector possibleTrainingSharkNames = new Vector();
    while (allSharks.hasNext()) {
      tempShark = (MarkedIndividual) (allSharks.next());
      if (tempShark.getNumberTrainableEncounters() >= 2) {
        possibleTrainingSharkNames.add(tempShark);
        //System.out.println(tempShark.getName()+" has more than one encounter.");
      }
      ;

    }
    return possibleTrainingSharkNames;

  }

  public Vector getRightPossibleTrainingIndividuals() {
    Iterator allSharks = getAllMarkedIndividuals();
    MarkedIndividual tempShark;
    Vector possibleTrainingSharkNames = new Vector();
    while (allSharks.hasNext()) {
      tempShark = (MarkedIndividual) (allSharks.next());
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
  public Iterator getAllMarkedIndividuals() {
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
    Iterator it = list.iterator();
    return it;
  }

  public ArrayList<MarkedIndividual> getAllMarkedIndividualsFromLocationID(String locCode) {
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
    return newList;
  }



  public Iterator getAllMarkedIndividuals(Query sharks) {
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
  public Iterator getAllMarkedIndividuals(Query sharkies, String order) {
    Map<String, Object> emptyMap = Collections.emptyMap();
    return getAllMarkedIndividuals(sharkies, order, emptyMap);
  }

  public Iterator getAllMarkedIndividuals(Query sharkies, String order, Map<String, Object> params) {
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
    String filter = "this.state == \"unidentifiable\" && this.individualID == " + individual;
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
      filter = "this.numSpotsRight > 0";
    } else {
      filter = "this.numSpotsLeft > 0";
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
      if ((pm != null) && (pm.currentTransaction().isActive())) {

        //System.out.println("     Now commiting a transaction with pm"+(String)pm.getUserObject());
        pm.currentTransaction().commit();
        //return true;
        //System.out.println("A transaction has been successfully committed.");
      } else {
        System.out.println("You are trying to commit an inactive transaction.");
        //return false;
      }


    } catch (JDOUserException jdoe) {
      jdoe.printStackTrace();
      System.out.println("I failed to commit a transaction." + "\n" + jdoe.getStackTrace());
      //return false;
    } catch (JDOException jdoe2) {
      jdoe2.printStackTrace();
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

    } catch (JDOUserException jdoe) {
      jdoe.printStackTrace();
    } catch (JDOFatalUserException fdoe) {
      fdoe.printStackTrace();
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
  }


  public Iterator getAllKeywords() {
    Extent allKeywords = null;
    Iterator it = null;
    try {
      allKeywords = pm.getExtent(Keyword.class, true);
      Query acceptedKeywords = pm.newQuery(allKeywords);
      acceptedKeywords.setOrdering("readableName descending");
      Collection c = (Collection) (acceptedKeywords.execute());
      it = c.iterator();
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return null;
    }
    return it;
  }

  public ArrayList<User> getAllUsers() {
    Collection c;
    ArrayList<User> list = new ArrayList<User>();
    Extent userClass = pm.getExtent(User.class, true);
    Query users = pm.newQuery(userClass);
    users.setOrdering("fullName ascending");
    try {
      c = (Collection) (users.execute());
      if(c!=null){
        list = new ArrayList<User>(c);
      }
      users.closeAll();
      return list;
    } 
    catch (Exception npe) {
      //System.out.println("Error encountered when trying to execute Shepherd.getAllUsers. Returning a null collection because I didn't have a transaction to use.");
      npe.printStackTrace();
      return null;
    }
  }
  
  public String getAllUserEmailAddressesForLocationID(String locationID, String context){
    String addresses="";
    ArrayList<User> users = getAllUsers();
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
      it = c.iterator();
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return null;
    }
    return it;
  }

  public ArrayList<Role> getAllRoles() {
    Extent allKeywords = null;
    ArrayList<Role> it = new ArrayList<Role>();
    try {
      allKeywords = pm.getExtent(Role.class, true);
      Query acceptedKeywords = pm.newQuery(allKeywords);
      Collection c = (Collection) (acceptedKeywords.execute());
      it=new ArrayList<Role>(c);
    } catch (javax.jdo.JDOException x) {
      x.printStackTrace();
      return it;
    }
    return it;
  }

  public Iterator getAllKeywords(Query acceptedKeywords) {
    Extent allKeywords = null;
    Iterator it = null;
    try {
      acceptedKeywords.setOrdering("readableName descending");
      Collection c = (Collection) (acceptedKeywords.execute());
      it = c.iterator();
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


  public ArrayList<SinglePhotoVideo> getThumbnails(Shepherd myShepherd,HttpServletRequest request, ArrayList<String> encList, int startNum, int endNum, String[] keywords) {
    ArrayList<SinglePhotoVideo> thumbs = new ArrayList<SinglePhotoVideo>();
    boolean stopMe = false;
    int encIter=0;
    int count = 0;
    int numEncs=encList.size();
    //while (it.hasNext()) {
    while((count<=endNum)&&(encIter<numEncs)){
      
      String nextCatalogNumber=encList.get(encIter);	
      int numImages=getNumSinglePhotoVideosForEncounter(nextCatalogNumber);
      

      if ((count + numImages) >= startNum) {
    	  Encounter enc = myShepherd.getEncounter(nextCatalogNumber);
    	  ArrayList<SinglePhotoVideo> images=getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber());
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

                  if (images.get(i).getKeywords().contains(word)) {


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
              m_thumb = "http://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
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

  public ArrayList<SinglePhotoVideo> getMarkedIndividualThumbnails(HttpServletRequest request, Iterator<MarkedIndividual> it, int startNum, int endNum, String[] keywords) {
    ArrayList<SinglePhotoVideo> thumbs = new ArrayList<SinglePhotoVideo>();

    boolean stopMe = false;
    int count = 0;
    while (it.hasNext()&&!stopMe) {
      MarkedIndividual markie = it.next();
      Iterator allEncs = markie.getEncounters().iterator();
      while (allEncs.hasNext()&&!stopMe) {
        Encounter enc = (Encounter) allEncs.next();
        ArrayList<SinglePhotoVideo> images=getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber());

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
              } else {
                int numKeywords = keywords.length;
                for (int n = 0; n < numKeywords; n++) {
                  if (!keywords[n].equals("None")) {
                    Keyword word = getKeyword(keywords[n]);

                    if (images.get(i).getKeywords().contains(word)) {


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
                m_thumb = "http://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
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
    if ((fileName.toLowerCase().indexOf(".jpg") != -1) || (fileName.toLowerCase().indexOf(".gif") != -1) || (fileName.toLowerCase().indexOf(".jpeg") != -1) || (fileName.toLowerCase().indexOf(".jpe") != -1) || (fileName.toLowerCase().indexOf(".bmp") != -1) || (fileName.toLowerCase().indexOf(".png") != -1)) {
      return true;
    }
    return false;
  }

  public boolean isAcceptableVideoFile(String fileName) {
    if ((fileName.toLowerCase().indexOf(".mov") != -1) || (fileName.toLowerCase().indexOf(".avi") != -1) || (fileName.toLowerCase().indexOf("mpg") != -1) || (fileName.toLowerCase().indexOf(".wmv") != -1) || (fileName.toLowerCase().indexOf(".mp4") != -1)|| (fileName.toLowerCase().indexOf(".flv") != -1)) {
      return true;
    }
    return false;
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
    Iterator it = getAllAdoptionsNoQuery();
    while (it.hasNext()) {
      Adoption ad = (Adoption) it.next();
      if (currentPosition == ranNum) {
        return ad;
      }
      currentPosition++;
    }
    return null;
  }

  public ArrayList getMarkedIndividualsByAlternateID(String altID) {
    String filter = "this.alternateid.startsWith('" + altID + "')";
    Extent encClass = pm.getExtent(MarkedIndividual.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al = new ArrayList(c);
    acceptedEncounters.closeAll();
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

  public ArrayList getEncountersByAlternateID(String altID) {
    String filter = "this.otherCatalogNumbers.startsWith('" + altID + "')";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al = new ArrayList(c);
    acceptedEncounters.closeAll();
    return al;
  }

  public ArrayList getMarkedIndividualsByNickname(String altID) {
    String filter = "this.nickName.startsWith('" + altID + "')";
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

  public ArrayList<String> getAllLocationIDs() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct locationID");
    q.setOrdering("locationID ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllCountries() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct country");
    q.setOrdering("country ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllHaplotypes() {
    Query q = pm.newQuery(MitochondrialDNAAnalysis.class);
    q.setResult("distinct haplotype");
    q.setOrdering("haplotype ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllRoleNames() {
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

  public ArrayList<String> getAllGeneticSexes() {
    Query q = pm.newQuery(SexAnalysis.class);
    q.setResult("distinct sex");
    q.setOrdering("sex ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllLoci() {
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

  public ArrayList<String> getAllGenuses() {
      Query q = pm.newQuery(Encounter.class);
      q.setResult("distinct genus");
      q.setOrdering("genus ascending");
      Collection results = (Collection) q.execute();
      ArrayList al=new ArrayList(results);
	      q.closeAll();
    return al;
  }

  public ArrayList<String> getAllSpecificEpithets() {
      Query q = pm.newQuery(Encounter.class);
      q.setResult("distinct specificEpithet");
      q.setOrdering("specificEpithet ascending");
      Collection results = (Collection) q.execute();
      ArrayList al=new ArrayList(results);
	      q.closeAll();
    return al;
  }

  public ArrayList<String> getAllBehaviors() {

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

  public ArrayList<String> getAllVerbatimEventDates() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct verbatimEventDate");
    q.setOrdering("verbatimEventDate ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllRecordedBy() {
    Query q = pm.newQuery(Encounter.class);
    q.setResult("distinct recordedBy");
    q.setOrdering("recordedBy ascending");
    Collection results = (Collection) q.execute();
    ArrayList al=new ArrayList(results);
	    q.closeAll();
    return al;
  }

  public ArrayList<Encounter> getEncountersWithHashedEmailAddress(String hashedEmail) {
    String filter = "((this.hashedSubmitterEmail.indexOf('" + hashedEmail + "') != -1)||(this.hashedPhotographerEmail.indexOf('" + hashedEmail + "') != -1)||(this.hashedInformOthers.indexOf('" + hashedEmail + "') != -1))";
    Extent encClass = pm.getExtent(Encounter.class, true);
    Query acceptedEncounters = pm.newQuery(encClass, filter);
    Collection c = (Collection) (acceptedEncounters.execute());
    ArrayList al=new ArrayList(c);
	    acceptedEncounters.closeAll();
    return al;
  }

  public ArrayList<String> getAllPatterningCodes(){
    Query q = pm.newQuery (Encounter.class);
    q.setResult ("distinct patterningCode");
    q.setOrdering("patterningCode ascending");
    Collection results = (Collection)q.execute ();
     ArrayList al=new ArrayList(results);
		    q.closeAll();
    return al;
  }

  public ArrayList<String> getAllLifeStages(){
    Query q = pm.newQuery (Encounter.class);
    q.setResult ("distinct lifeStage");
    q.setOrdering("lifeStage ascending");
    Collection results = (Collection)q.execute ();
     ArrayList al=new ArrayList(results);
		    q.closeAll();
    return al;
  }


  public ArrayList<MarkedIndividual> getAllMarkedIndividualsInCommunity(String communityName){
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
    ArrayList<String> occurenceIDs2=getOccurrenceIDsForMarkedIndividual(individualID2);
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
    for(int i=0;i<numResults;i++){occurrenceIDs.add((String)al.get(i));}
    //System.out.println("zzzOccurrences for "+individualID+": "+occurrenceIDs.toString());
    return occurrenceIDs;
  }
  

  
  public Measurement getMeasurementOfTypeForEncounter(String type, String encNum) {
    String filter = "type == \""+type+"\" && correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(Measurement.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    if((c!=null)&&(c.size()>0)){return (new ArrayList<Measurement>(c)).get(0);}
    else{return null;}
  }
  
  public ArrayList<Measurement> getMeasurementsForEncounter(String encNum) {
    String filter = "correspondingEncounterNumber == \""+encNum+"\"";
    Extent encClass = pm.getExtent(Measurement.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    if((c!=null)&&(c.size()>0)){return (new ArrayList<Measurement>(c));}
    else{return null;}
  }
  
  public Iterator<ScanTask> getAllScanTasksForUser(String user) {
    String filter = "submitter == \""+user+"\"";
    Extent encClass = pm.getExtent(ScanTask.class, true);
    Query samples = pm.newQuery(encClass, filter);
    Collection c = (Collection) (samples.execute());
    if((c!=null)&&(c.size()>0)){return c.iterator();}
    else{return null;}
  }

  

} //end Shepherd class

