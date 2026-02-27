/*
 * The Shepherd Project - A Mark-Recapture Framework
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.ecocean.shepherd.core;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.grid.ScanTask;
import org.ecocean.grid.ScanWorkItem;
import org.ecocean.ia.Task;
import org.ecocean.media.*;
import org.ecocean.movement.Path;
import org.ecocean.movement.SurveyTrack;
import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.ecocean.scheduled.WildbookScheduledTask;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.utils.ShepherdState;
import org.ecocean.social.*;

import javax.jdo.*;
import javax.servlet.http.HttpServletRequest;

import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.cache.StoredQuery;
import org.joda.time.DateTime;
import org.json.JSONArray;

/**
 * Shepherd is the main	information	retrieval, processing, and persistence class to	be used	for	all	shepherd
 * project applications. The shepherd class interacts directly with the database and all persistent objects stored within it.
 * Any application seeking access to encounter	data must invoke an	instance of	shepherd first and use it to retrieve any data stored
 * in the database.
 *
 * @author Jason Holmberg
 * @version alpha-2
 * @see shark, encounter, superSpot,	spot
 */

public class Shepherd {
    private PersistenceManager pm;
    public static Vector matches = new Vector();
    private String localContext;

    private String action = "undefined";
    private String shepherdID = "";

    // Constructor to create a new shepherd thread object
    public Shepherd(String context) { this(context, null); }

    public Shepherd(String context, Properties properties) {
        if (pm == null || pm.isClosed()) {
            localContext = context;
            try {
                pm = ShepherdPMF.getPMF(localContext, properties).getPersistenceManager();
                this.shepherdID = Util.generateUUID();

                ShepherdState.setShepherdState(action + "_" + shepherdID, "new");
            } catch (JDOUserException e) {
                System.out.println(
                    "Hit an excpetion while trying to instantiate a PM. Not fatal I think.");
                e.printStackTrace();
            }
        }
    }

    public Shepherd(HttpServletRequest req) {
        this(ServletUtilities.getContext(req));
    }

    // static method so the programmer knows this is an *active* Shepherd
    public static Shepherd newActiveShepherd(String context, String action) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction(action);
        myShepherd.beginDBTransaction();
        return myShepherd;
    }

    public static Shepherd newActiveShepherd(HttpServletRequest req, String action) {
        return newActiveShepherd(ServletUtilities.getContext(req), action);
    }

    // handy with a newActiveShepherd
    public void rollbackAndClose() {
        rollbackDBTransaction();
        closeDBTransaction();
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

    /**
     * Stores a new, unassigned encounter in the database for later retrieval and analysis. Each new encounter is assigned a unique number which is
     * also its unique retrievable ID in the database. This method will be the primary method used for future web submissions to shepherd from
     * web-based applications.
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
            System.out.println(
                "I failed to create a new encounter in shepherd.storeNewEncounter().");
            System.out.println("     uniqueID:" + uniqueID);
            e.printStackTrace();
            return "fail";
        }
        return (uniqueID);
    }

    public String storeNewEncounter(Encounter enc) {
        return storeNewEncounter(enc, Util.generateUUID());
    }

    public String storeNewAnnotation(Annotation enc) {
        beginDBTransaction();
        try {
            pm.makePersistent(enc);
            commitDBTransaction();
            beginDBTransaction();
            // System.out.println("I successfully persisted a new Annotation in Shepherd.storeNewAnnotation().");
        } catch (Exception e) {
            rollbackDBTransaction();
            beginDBTransaction();
            System.out.println(
                "I failed to create a new Annotation in Shepherd.storeNewAnnotation().");
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
            // System.out.println("I successfully persisted a new Workspace in Shepherd.storeNewWorkspace().");
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new workspace in shepherd.storeNewWorkspace().");
            System.out.println("     id:" + wSpace.id);
            e.printStackTrace();
            return "fail";
        }
        return (String.valueOf(wSpace.id));
    }

    public void storeNewOccurrence(Occurrence enc) {
        beginDBTransaction();
        try {
            pm.makePersistent(enc);
            commitDBTransaction();
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new Occurrence in shepherd.storeNewOccurrence().");
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
            System.out.println(
                "I failed to create a new SurveyTrack in shepherd.storeNewSurveyTrack().");
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

    public void storeNewPointLocation(PointLocation plc) {
        beginDBTransaction();
        try {
            pm.makePersistent(plc);
            commitDBTransaction();
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new PointLocation in shepherd.storeNewPointLocation().");
            e.printStackTrace();
        }
    }

    public void storeNewObservation(Observation ob) {
        beginDBTransaction();
        try {
            pm.makePersistent(ob);
            commitDBTransaction();
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new Observation in shepherd.storeNewObservation().");
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
            System.out.println(
                "I failed to create a new MarkedIndividual in Shepherd.storeNewMarkedIndividual().");
            e.printStackTrace();
            return false;
        }
        return true;
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
            System.out.println("I failed to store the new ScanTask number: " +
                scanTask.getUniqueNumber());
            return false;
        }
    }

    public boolean storeNewTask(Task task) {
        beginDBTransaction();
        try {
            pm.makePersistent(task);
            updateDBTransaction();
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
            System.out.println(
                "I failed to create a new collaboration in shepherd.storeNewCollaboration().");
            e.printStackTrace();
            return false;
        }
    }

    public boolean storeNewSocialUnit(SocialUnit su) {
        beginDBTransaction();
        try {
            pm.makePersistent(su);
            commitDBTransaction();
            return true;
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new SocialUnit in shepherd.storeNewSocialUnit().");
            e.printStackTrace();
            return false;
        }
    }

    public boolean storeNewMembership(Membership mem) {
        beginDBTransaction();
        try {
            pm.makePersistent(mem);
            commitDBTransaction();
            return true;
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new social unit Membership in shepherd.storeNewMembership().");
            e.printStackTrace();
            return false;
        }
    }

    public boolean storeNewProject(Project project) {
        beginDBTransaction();
        try {
            pm.makePersistent(project);
            commitDBTransaction();
            return true;
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println("I failed to create a new Project in shepherd.storeNewProject().");
            e.printStackTrace();
            return false;
        }
    }

    public boolean storeNewWildbookScheduledTask(WildbookScheduledTask wst) {
        beginDBTransaction();
        try {
            pm.makePersistent(wst);
            commitDBTransaction();
            return true;
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new WildbookScheduledTask in shepherd.storeNewWildbookScheduledTask().");
            e.printStackTrace();
            return false;
        }
    }

    public boolean storeNewScheduledIndividualMerge(ScheduledIndividualMerge wsim) {
        beginDBTransaction();
        try {
            pm.makePersistent(wsim);
            commitDBTransaction();
            return true;
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new ScheduledIndividualMerge in shepherd.storeNewScheduledIndividualMerge().");
            e.printStackTrace();
            return false;
        }
    }

    public List getAllCollaborations() {
        Collection c;

        try {
            Extent allCollabs = pm.getExtent(Collaboration.class, true);
            Query acceptedCollabs = pm.newQuery(allCollabs);
            c = (Collection)(acceptedCollabs.execute());
            List list = new ArrayList(c);
            System.out.println("getAllCollaborations got " + list.size() + " collabs");
            // Collections.reverse(list);
            acceptedCollabs.closeAll();
            return list;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllCollaborations(). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    /**
     * Removes an encounter from the database. ALL DATA FOR THE ENCOUNTER WILL BE LOST!!!
     *
     * @param enc the encounter to delete the database
     * @see Encounter
     */
    public void throwAwayEncounter(Encounter enc) {
        enc.opensearchUnindexQuiet();
        pm.deletePersistent(enc);
    }

    public void throwAwayWorkspace(Workspace wSpace) {
        pm.deletePersistent(wSpace);
    }

    public void throwAwayMembership(Membership mShip) {
        pm.deletePersistent(mShip);
    }

    public void throwAwaySocialUnit(SocialUnit su) {
        pm.deletePersistent(su);
    }

    public void throwAwayCollaboration(Collaboration collab) {
        pm.deletePersistent(collab);
    }

    public void throwAwayTissueSample(TissueSample genSample) {
        pm.deletePersistent(genSample);
    }

    public void throwAwayGeneticAnalysis(GeneticAnalysis analysis) {
        // String removedParameters = analysis.getHTMLString();
        pm.deletePersistent(analysis);
    }

    public void throwAwayMicrosatelliteMarkersAnalysis(MicrosatelliteMarkersAnalysis analysis) {
        pm.deletePersistent(analysis);
    }

    public void throwAwayAnnotation(Annotation ad) {
        pm.deletePersistent(ad);
    }

    public void throwAwayOccurrence(Occurrence occ) {
        occ.opensearchUnindexQuiet();
        pm.deletePersistent(occ);
    }

    public void throwAwayProject(Project project) {
        pm.deletePersistent(project);
    }

    /**
     * Removes a marked individual from the database. ALL DATA FOR THE INDIVIDUAL WILL BE LOST!!
     *
     * @param MarkedIndividual to delete from the database
     * @see MarkedIndividual
     */
    public void throwAwayMarkedIndividual(MarkedIndividual bye_bye_sharky) {
        bye_bye_sharky.opensearchUnindexQuiet();
        pm.deletePersistent(bye_bye_sharky);
    }

    public Encounter getEncounter(String num) {
        Encounter tempEnc = null;

        try {
            tempEnc = ((Encounter)(pm.getObjectById(pm.newObjectIdInstance(Encounter.class,
                num.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return tempEnc;
    }

    public ImportTask getImportTask(String num) {
        ImportTask tempEnc = null;

        try {
            tempEnc = ((ImportTask)(pm.getObjectById(pm.newObjectIdInstance(ImportTask.class,
                num.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return tempEnc;
    }

    public void storeNewImportTask(ImportTask itask) {
        try {
            pm.makePersistent(itask);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Setting getSetting(String group, String id) {
        if ((group == null) || (id == null)) return null;
        Query qry = pm.newQuery("SELECT FROM org.ecocean.Setting WHERE group=='" + group +
            "' && id=='" + id + "'");
        Setting st = null;
        Collection results = (Collection)(qry.execute());
        if (!results.isEmpty()) st = (Setting)results.iterator().next();
        qry.closeAll();
        return st;
    }

    public Setting getOrCreateSetting(String group, String id) {
        Setting st = getSetting(group, id);

        if (st != null) return st;
        st = new Setting(group, id);
        pm.makePersistent(st);
        return st;
    }

    public Object getSettingValue(String group, String id) {
        Setting st = getSetting(group, id);

        if (st == null) return null;
        return st.getValue();
    }

    public void storeSetting(Setting st) {
        pm.makePersistent(st);
    }

    public void deleteSetting(Setting st) {
        pm.deletePersistent(st);
    }

    public Annotation getAnnotation(String uuid) {
        Annotation annot = null;

        try {
            annot = ((Annotation)(pm.getObjectById(pm.newObjectIdInstance(Annotation.class,
                uuid.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return annot;
    }

    public MediaAsset getMediaAsset(String num) {
        MediaAsset tempMA = null;

        try {
            tempMA = ((MediaAsset)(pm.getObjectById(pm.newObjectIdInstance(MediaAsset.class,
                num.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return tempMA;
    }

    public Collaboration getCollaboration(String id) {
        Collaboration collab = null;

        try {
            collab = ((Collaboration)(pm.getObjectById(pm.newObjectIdInstance(Collaboration.class,
                id.trim()), true)));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return collab;
    }

    public Project getProject(String id) {
        Project project = null;

        try {
            project = ((Project)(pm.getObjectById(pm.newObjectIdInstance(Project.class, id.trim()),
                true)));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return project;
    }

    public Workspace getWorkspace(int id) {
        Workspace tempWork = null;

        try {
            tempWork = (Workspace)(pm.getObjectById(pm.newObjectIdInstance(Workspace.class, id)));
        } catch (Exception nsoe) {
            return null;
        }
        return tempWork;
    }

    public WildbookScheduledTask getWildbookScheduledTask(String id) {
        WildbookScheduledTask task = null;

        try {
            task = (WildbookScheduledTask)(pm.getObjectById(pm.newObjectIdInstance(
                WildbookScheduledTask.class, id.trim()), true));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return task;
    }

    // finds the workspace that user 'owner' created and named 'name'
    public Workspace getWorkspaceForUser(String name, String owner) {
        Workspace result = null;
        String quotedOwner = (owner == null) ? "null" : ("\"" + owner + "\"");
        String filter = "this.name == \"" + name + "\" && this.owner == " + quotedOwner;
        Extent allWorkspaces = pm.getExtent(Workspace.class, true);
        Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
        Collection results = (Collection)(workspaceQuery.execute());

        if (!results.isEmpty()) {
            result = (Workspace)results.iterator().next();
        }
        workspaceQuery.closeAll();
        return result;
    }

    // Returns all of a user's workspaces.
    public ArrayList<Workspace> getWorkspacesForUser(String owner) {
        String filter = "this.owner == \"" + owner + "\"";

        if (owner == null) {
            filter = "this.owner == null";
        }
        Extent allWorkspaces = pm.getExtent(Workspace.class, true);
        Query workspaceQuery = pm.newQuery(allWorkspaces, filter);
        workspaceQuery.setOrdering("accessed descending");

        try {
            Collection results = (Collection)(workspaceQuery.execute());
            ArrayList<Workspace> resultList = new ArrayList<Workspace>();
            if (results != null) {
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
        String enquotedOwner = (owner == null || owner.equals("")) ? "null" : "\"" + owner + "\"";
        String filter = "accessControl.username == " + enquotedOwner;
        Extent allMediaAssets = pm.getExtent(MediaAsset.class, true);
        Query mediaAssetQuery = pm.newQuery(allMediaAssets, filter);

        try {
            Collection results = (Collection)(mediaAssetQuery.execute());
            ArrayList<MediaAsset> resultList = new ArrayList<MediaAsset>();
            if (results != null) {
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
        String enquotedOwner = (owner == null || owner.equals("")) ? "null" : "\"" + owner + "\"";
        String enquotedStatus = (status == null ||
            status.equals("")) ? "null" : "\"" + status + "\"";
        String filter = "accessControl.username == " + enquotedOwner + " && detectionStatus == " +
            enquotedStatus;
        Extent allMediaAssets = pm.getExtent(MediaAsset.class, true);
        Query mediaAssetQuery = pm.newQuery(allMediaAssets, filter);

        try {
            Collection results = (Collection)(mediaAssetQuery.execute());
            ArrayList<MediaAsset> resultList = new ArrayList<MediaAsset>();
            if (results != null) {
                resultList = new ArrayList<MediaAsset>(results);
            }
            mediaAssetQuery.closeAll();
            return resultList;
        } catch (Exception npe) {
            npe.printStackTrace();
            mediaAssetQuery.closeAll();
            return null;
        }
    }

    public ArrayList<Workspace> getWorkspacesForUser(String owner, boolean isImageSet)
    throws JSONException {
        ArrayList<Workspace> unfilteredSpaces = getWorkspacesForUser(owner);
        ArrayList<Workspace> filteredSpaces = new ArrayList<Workspace>();

        for (Workspace wSpace : unfilteredSpaces) {
            if (wSpace != null && (wSpace.computeIsImageSet() == isImageSet)) {
                filteredSpaces.add(wSpace);
            }
        }
        return filteredSpaces;
    }

    public Relationship getRelationship(String type, String indie1, String indie2) {
        Relationship tempRel = null;
        String filter = "this.type == \"" + type + "\" && ((this.markedIndividualName1 == \"" +
            indie1 + "\" && this.markedIndividualName2 == \"" + indie2 +
            "\") || (this.markedIndividualName1 == \"" + indie2 +
            "\" && this.markedIndividualName2 == \"" + indie1 + "\"))";
        Extent encClass = pm.getExtent(Relationship.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                Relationship ts = (Relationship)it.next();
                acceptedEncounters.closeAll();
                return ts;
            }
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return null;
        }
        acceptedEncounters.closeAll();
        return null;
    }

    public Relationship getRelationship(String type, String indie1, String indie2,
        String indieRole1, String indieRole2) {
        Relationship tempRel = null;
        String filter = "this.type == \"" + type + "\" && this.markedIndividualName1 == \"" +
            indie1 + "\" && this.markedIndividualName2 == \"" + indie2 +
            "\" && this.markedIndividualRole1 == \"" + indieRole1 +
            "\" && this.markedIndividualRole2 == \"" + indieRole2 + "\"";
        Extent encClass = pm.getExtent(Relationship.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                Relationship ts = (Relationship)it.next();
                acceptedEncounters.closeAll();
                return ts;
            }
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return null;
        }
        acceptedEncounters.closeAll();
        return null;
    }

    public Relationship getRelationship(String type, String indie1, String indie2,
        String indieRole1, String indieRole2, String relatedCommunityName) {
        Relationship tempRel = null;
        String filter = "this.type == \"" + type + "\" && this.markedIndividualName1 == \"" +
            indie1 + "\" && this.markedIndividualName2 == \"" + indie2 +
            "\" && this.markedIndividualRole1 == \"" + indieRole1 +
            "\" && this.markedIndividualRole2 == \"" + indieRole2 +
            "\" && this.relatedSocialUnitName == \"" + relatedCommunityName + "\"";
        Extent encClass = pm.getExtent(Relationship.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                Relationship ts = (Relationship)it.next();
                acceptedEncounters.closeAll();
                return ts;
            }
        } catch (Exception nsoe) {
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
            tempCom = ((SocialUnit)(pm.getObjectById(pm.newObjectIdInstance(SocialUnit.class,
                name.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return tempCom;
    }

    public List<SocialUnit> getAllSocialUnitsForMarkedIndividual(MarkedIndividual indie) {
        String filter2use =
            "SELECT FROM org.ecocean.social.SocialUnit WHERE members.contains(member) && member.mi.individualID == '"
            + indie.getIndividualID() + "' VARIABLES org.ecocean.social.Membership member";
        Query query = pm.newQuery(filter2use);
        Collection c = (Collection)(query.execute());
        ArrayList<SocialUnit> listy = new ArrayList<SocialUnit>();

        if (c != null) listy = new ArrayList<SocialUnit>(c);
        query.closeAll();
        return listy;
    }

    public List<String> getAllMembershipRoles() {
        List<String> all = new ArrayList<String>();
        Query query = pm.newQuery(
            "SELECT role FROM org.ecocean.social.Membership WHERE role != null");

        query.setResult("distinct role");
        query.setOrdering("role");
        Collection c = (Collection)(query.execute());
        if (c != null) all = new ArrayList<String>(c);
        return all;
    }

    public SocialUnit getSocialUnit(String name) {
        return getCommunity(name);
    }

    public SinglePhotoVideo getSinglePhotoVideo(String num) {
        SinglePhotoVideo tempEnc = null;

        try {
            tempEnc = ((SinglePhotoVideo)(pm.getObjectById(pm.newObjectIdInstance(
                SinglePhotoVideo.class, num.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return tempEnc;
    }

    public Role getRole(String rolename, String username, String context) {
        List<Role> roles = getAllRoles();
        int numRoles = roles.size();

        for (int i = 0; i < numRoles; i++) {
            Role kw = (Role)roles.get(i);
            if ((kw.getRolename().equals(rolename)) && (kw.getUsername().equals(username)) &&
                (kw.getContext().equals(context))) {
                return kw;
            }
        }
        return null;
    }

    public List<Role> getAllRolesForUserInContext(String username, String context) {
        String actualContext = "context0";

        if (context != null) { actualContext = context; }
        String filter = "this.username == '" + username + "' && this.context == '" + actualContext +
            "'";
        Extent encClass = pm.getExtent(Role.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList<Role> roles = new ArrayList<Role>();
        if (c != null) { roles = new ArrayList<Role>(c); }
        acceptedEncounters.closeAll();
        return roles;
    }

    public ArrayList<Role> getAllRolesForUser(String username) {
        String filter = "this.username == '" + username + "'";
        Extent encClass = pm.getExtent(Role.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList<Role> roles = new ArrayList<Role>(c);

        acceptedEncounters.closeAll();
        return roles;
    }

    public boolean doesUserHaveRole(String username, String rolename, String context) {
        if (username == null) return false;
        if (rolename == null) return false;
        username = username.replaceAll("\\'", "\\\\'");
        rolename = rolename.replaceAll("\\'", "\\\\'");
        String filter = "this.username == '" + username + "' && this.rolename == '" + rolename +
            "' && this.context == '" + context + "'";
        Extent encClass = pm.getExtent(Role.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        int size = c.size();
        acceptedEncounters.closeAll();
        if (size > 0) { return true; }
        return false;
    }

    public boolean doesUserHaveAnyRoleInContext(String username, String context) {
        String filter = "this.username == '" + username + "' && this.context == '" + context + "'";
        Extent encClass = pm.getExtent(Role.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        int size = c.size();

        acceptedEncounters.closeAll();
        if (size > 0) { return true; }
        return false;
    }

    public String getAllRolesForUserAsString(String username) {
        String filter = "this.username == '" + username + "'";
        Extent encClass = pm.getExtent(Role.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList<Role> roles = new ArrayList<Role>(c);
        int numRoles = roles.size();
        String rolesFound = "";

        for (int i = 0; i < numRoles; i++) {
            rolesFound += (roles.get(i).getRolename() + "\r");
        }
        acceptedEncounters.closeAll();
        return rolesFound;
    }

    public User getUser(String username) {
        if (username == null) return null;
        User user = null;
        String filter = "SELECT FROM org.ecocean.User WHERE username == \"" + username.trim() +
            "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();
        if (it.hasNext()) {
            user = (User)it.next();
        }
        query.closeAll();
        return user;
    }

    public Organization getOrganizationByName(String name) {
        Organization org = null;
        String filter = "SELECT FROM org.ecocean.Organization WHERE name == \"" + name.trim() +
            "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        if (it.hasNext()) {
            org = (Organization)it.next();
        }
        query.closeAll();
        return org;
    }

    public Organization getOrganization(String uuid) {
        Organization org = null;
        String filter = "SELECT FROM org.ecocean.Organization WHERE id == \"" + uuid.trim() + "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        if (it.hasNext()) {
            org = (Organization)it.next();
        }
        query.closeAll();
        return org;
    }

    public Organization getOrCreateOrganizationByName(String name) {
        return getOrCreateOrganizationByName(name, true);
    }

    public Organization getOrCreateOrganizationByName(String name, boolean commit) {
        Organization org = getOrganizationByName(name);

        if (org == null) {
            org = new Organization(name);
            if (commit) storeNewOrganization(org);
        }
        return org;
    }

    public String storeNewOrganization(Organization org) {
        try {
            pm.makePersistent(org);
            commitDBTransaction();
            // System.out.println("I successfully persisted a new Taxonomy in Shepherd.storeNewAnnotation().");
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new Taxonomy in Shepherd.storeNewAnnotation().");
            e.printStackTrace();
            return "fail";
        }
        return (org.getId());
    }

    public ArrayList<Project> getProjectsForUser(User user) {
        Boolean isAdmin = user.hasRoleByName("admin", this);
        Query query = null;
        Iterator<Project> projectIter = null;
        ArrayList<Project> projectArr = null;

        try {
            String filter = "SELECT FROM org.ecocean.Project WHERE users.contains(user)";
            if (isAdmin) filter = "SELECT FROM org.ecocean.Project";
            query = getPM().newQuery(filter);
            query.declareParameters("User user");
            query.setOrdering("researchProjectName ascending NULLS LAST");
            Collection c = (Collection)query.execute(user);
            projectIter = c.iterator();
            while (projectIter.hasNext()) {
                if (projectArr == null) {
                    projectArr = new ArrayList<>();
                }
                projectArr.add(projectIter.next());
            }
        } catch (JDOException jdoe) {
            jdoe.printStackTrace();
        } finally {
            query.closeAll();
        }
        return projectArr;
    }

    public ArrayList<Project> getProjectsOwnedByUser(User user) {
        Query query = null;
        Iterator<Project> projectIter = null;
        ArrayList<Project> projectArr = null;

        try {
            String filter = "SELECT FROM org.ecocean.Project WHERE ownerId==\"" + user.getId() +
                "\"";
            System.out.println("query in getProjectsOwnedByUser is: " + filter);
            query = getPM().newQuery(filter);
            query.declareParameters("User user");
            Collection c = (Collection)query.execute(user);
            projectIter = c.iterator();
            while (projectIter.hasNext()) {
                if (projectArr == null) {
                    projectArr = new ArrayList<>();
                }
                projectArr.add(projectIter.next());
            }
        } catch (JDOException jdoe) {
            jdoe.printStackTrace();
        } finally {
            query.closeAll();
        }
        if (projectArr != null) {
            System.out.println("returning the following projects from getProjectsOwnedByUser:" +
                projectArr.toString());
        }
        return projectArr;
    }

    public Project getProjectByProjectIdPrefix(String projectIdPrefix) {
        Project project = null;
        String filter = "SELECT FROM org.ecocean.Project WHERE projectIdPrefix == \"" +
            projectIdPrefix.trim() + "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        if (it.hasNext()) {
            project = (Project)it.next();
        }
        query.closeAll();
        return project;
    }

    // this is forgiving in that it only needs to match the start of the prefix and is case-insenstive
    // is the case-insensitivity too much? time will tell! thus, a user passing 'FOO-'
    // will match 'foo-#' or 'Foo-###'. it sorts by (lowercase) prefix value, to at least provide
    // consistency; but this may give undesirable results in a crowded dataset
    public Project getProjectByProjectIdPrefixPrefix(String projectIdPrefix) {
        if (projectIdPrefix == null) return null;
        Project project = null;
        String filter =
            "SELECT FROM org.ecocean.Project WHERE projectIdPrefix.toLowerCase().startsWith(\"" +
            projectIdPrefix.trim().toLowerCase() + "\") ORDER BY projectIdPrefix.toLowerCase()";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();
        if (it.hasNext()) {
            project = (Project)it.next();
        }
        query.closeAll();
        return project;
    }

    public Project getProjectByUuid(String id) {
        Project project = null;
        String filter = "SELECT FROM org.ecocean.Project WHERE id == \"" + id.trim() + "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        if (it.hasNext()) {
            project = (Project)it.next();
        }
        query.closeAll();
        return project;
    }

    public List<User> getUsersWithUsername() {
        return getUsersWithUsername("username ascending");
    }

    public List<User> getUsersWithUsername(String ordering) {
        List<User> users = null;
        String filter = "SELECT FROM org.ecocean.User WHERE username != null";
        Query query = getPM().newQuery(filter);

        query.setOrdering(ordering);
        Collection c = (Collection)(query.execute());
        users = new ArrayList<User>(c);
        query.closeAll();
        return users;
    }

    // filters out social media- and other-app-based users (twitter, ConserveIO, etc)
    public List<User> getNativeUsersWithoutAnonymous() {
        List<User> users = getNativeUsers("fullName ascending NULLS LAST");

        CollectionUtils.filter(users, new Predicate<User>() { // from
                                                              // https://stackoverflow.com/questions/122105/how-to-filter-a-java-collection-based-on-predicate
            @Override public boolean evaluate(User user) {
                if (user.getUsername().contains("Anonymous_")) {
                    return false;
                }
                return true;
            }
        });
        return users;
    }

    public List<User> getNativeUsers() {
        return getNativeUsers("username ascending NULLS LAST");
    }

    public List<User> getNativeUsers(String ordering) {
        List<User> users = null;
        String filter = "SELECT FROM org.ecocean.User WHERE username != null ";

        // bc of how sql's startsWith method works we need the null check below
        filter += "&& (fullName == null || !fullName.startsWith('Conserve.IO User '))";
        Query query = getPM().newQuery(filter);
        query.setOrdering(ordering);
        Collection c = (Collection)(query.execute());
        users = new ArrayList<User>(c);
        query.closeAll();
        return users;
    }

    public User getUserByUUID(String uuid) {
        User user = null;

        try {
            user = ((User)(pm.getObjectById(pm.newObjectIdInstance(User.class, uuid.trim()),
                true)));
        } catch (Exception nsoe) {
            return null;
        }
        return user;
    }

    public String getUsername(HttpServletRequest request) {
        String username = null;

        try {
            username = request.getUserPrincipal().toString();
        } catch (Exception e) {
            // System.out.println("Shepherd.getUsername(HttpServletRequest) called with no user logged in");
        }
        return username;
    }

    public User getUser(HttpServletRequest request) {
        String username = getUsername(request);

        if (username == null) return null;
        return getUser(username);
    }

    public TissueSample getTissueSample(String sampleID, String encounterNumber) {
        TissueSample tempEnc = null;
        String filter = "this.sampleID == \"" + sampleID +
            "\" && this.correspondingEncounterNumber == \"" + encounterNumber + "\"";
        Extent encClass = pm.getExtent(TissueSample.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                TissueSample ts = (TissueSample)it.next();
                acceptedEncounters.closeAll();
                return ts;
            }
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return null;
        }
        acceptedEncounters.closeAll();
        return null;
    }

    public MitochondrialDNAAnalysis getMitochondrialDNAAnalysis(String sampleID,
        String encounterNumber, String analysisID) {
        try {
            MitochondrialDNAAnalysis mtDNA = (MitochondrialDNAAnalysis)getGeneticAnalysis(sampleID,
                encounterNumber, analysisID, "MitochondrialDNA");
            return mtDNA;
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
    }

    public SexAnalysis getSexAnalysis(String sampleID, String encounterNumber, String analysisID) {
        try {
            SexAnalysis mtDNA = (SexAnalysis)getGeneticAnalysis(sampleID, encounterNumber,
                analysisID, "SexAnalysis");
            return mtDNA;
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
    }

    public SexAnalysis getSexAnalysis(String analysisID) {
        try {
            SexAnalysis mtDNA = (SexAnalysis)getGeneticAnalysis(analysisID, "SexAnalysis");
            return mtDNA;
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
    }

    public BiologicalMeasurement getBiologicalMeasurement(String sampleID, String encounterNumber,
        String analysisID) {
        try {
            BiologicalMeasurement mtDNA = (BiologicalMeasurement)getGeneticAnalysis(sampleID,
                encounterNumber, analysisID, "BiologicalMeasurement");
            return mtDNA;
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
    }

    public MicrosatelliteMarkersAnalysis getMicrosatelliteMarkersAnalysis(String sampleID,
        String encounterNumber, String analysisID) {
        try {
            MicrosatelliteMarkersAnalysis msDNA = (MicrosatelliteMarkersAnalysis)getGeneticAnalysis(
                sampleID, encounterNumber, analysisID, "MicrosatelliteMarkers");
            return msDNA;
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
    }

    public MicrosatelliteMarkersAnalysis getMicrosatelliteMarkersAnalysis(String analysisID) {
        try {
            MicrosatelliteMarkersAnalysis msDNA = (MicrosatelliteMarkersAnalysis)getGeneticAnalysis(
                analysisID, "MicrosatelliteMarkers");
            return msDNA;
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
    }

    public <T extends DataCollectionEvent> T findDataCollectionEvent(Class<T> clazz, String num) {
        T dataCollectionEvent = null;

        try {
            dataCollectionEvent = (T)pm.getObjectById((pm.newObjectIdInstance(clazz, num.trim())),
                true);
        } catch (Exception e) {}
        return dataCollectionEvent;
    }

    public <T extends GeneticAnalysis> T findGeneticAnalysis(Class<T> clazz, String num) {
        T dataCollectionEvent = null;

        try {
            dataCollectionEvent = (T)pm.getObjectById((pm.newObjectIdInstance(clazz, num.trim())),
                true);
        } catch (Exception e) {}
        return dataCollectionEvent;
    }

    public Encounter getEncounterDeepCopy(String num) {
        if (isEncounter(num)) {
            Encounter tempEnc = getEncounter(num.trim());
            Encounter transmitEnc = null;
            try {
                transmitEnc = (Encounter)pm.detachCopy(tempEnc);
            } catch (Exception e) {}
            return transmitEnc;
        } else {
            return null;
        }
    }

    public ScanTask getScanTask(String uniqueID) {
        ScanTask tempTask = null;

        try {
            tempTask = ((ScanTask)(pm.getObjectById(pm.newObjectIdInstance(ScanTask.class,
                uniqueID.trim()), true)));
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

        if (taxy == null) {
            taxy = new Taxonomy(scientificName);
            if (commit) storeNewTaxonomy(taxy);
        }
        return taxy;
    }

    public Taxonomy getTaxonomy(String scientificName) {
        if (scientificName == null) return null;
        // lookout!  hactacular uuid-ahead!
        if (scientificName.matches(
            "^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$")) {
            System.out.println("WARNING: Shepherd.getTaxonomy() assuming passed '" +
                scientificName + "' is UUID; hack is passing to getTaxonomyById()");
            return getTaxonomyById(scientificName);
        }
        List al = new ArrayList();
        try {
            String filter = "this.scientificName.toLowerCase() == \"" +
                scientificName.toLowerCase() + "\"";
            Extent keyClass = pm.getExtent(Taxonomy.class, true);
            Query acceptedKeywords = pm.newQuery(keyClass, filter);
            Collection c = (Collection)(acceptedKeywords.execute());
            al = new ArrayList(c);
            try {
                acceptedKeywords.closeAll();
            } catch (NullPointerException npe) {}
        } catch (Exception e) { e.printStackTrace(); }
        return ((al.size() > 0) ? ((Taxonomy)al.get(0)) : null);
    }

    public Taxonomy getTaxonomy(int tsn) {
        Taxonomy tax = null;
        Query query = pm.newQuery("SELECT org.ecocean.Taxonomy WHERE itisTsn == " + tsn);

        try {
            Collection c = (Collection)query.execute();
            Iterator it = c.iterator();
            if (it.hasNext()) tax = (Taxonomy)it.next();
        } catch (Exception ex) {}
        query.closeAll();
        return tax;
    }

    // sadly, getTaxonomy(string) signatured already used above. :( so we have to go non-standard name here:
    // however, checkout the hack to look for a uuid above!
    public Taxonomy getTaxonomyById(String id) {
        try {
            return (Taxonomy)(pm.getObjectById(pm.newObjectIdInstance(Taxonomy.class, id), true));
        } catch (Exception ex) {}
        return null;
    }

    public String storeNewTaxonomy(Taxonomy enc) {
        boolean transactionWasActive = isDBTransactionActive();

        beginDBTransaction();
        try {
            pm.makePersistent(enc);
            commitDBTransaction();
            // System.out.println("I successfully persisted a new Taxonomy in Shepherd.storeNewAnnotation().");
        } catch (Exception e) {
            rollbackDBTransaction();
            System.out.println(
                "I failed to create a new Taxonomy in Shepherd.storeNewAnnotation().");
            e.printStackTrace();
            return "fail";
        }
        if (transactionWasActive) beginDBTransaction();
        return (enc.getId());
    }

    public Keyword getKeyword(String readableName) {
        Iterator<Keyword> keywords = getAllKeywords();

        while (keywords.hasNext()) {
            Keyword kw = keywords.next();
            if ((kw.getReadableName() != null && kw.getReadableName().equals(readableName)) ||
                (kw.getIndexname().equals(readableName))) { return kw; }
        }
        return null;
    }

    public LabeledKeyword getLabeledKeyword(String label, String readableName) {
        try {
            String filter = String.format(
                "SELECT FROM org.ecocean.LabeledKeyword WHERE this.readableName == \"%s\" && this.label == \"%s\"",
                readableName, label);
            Query query = pm.newQuery(filter);
            List<Keyword> ans = (List)query.execute();
            LabeledKeyword lk = null;
            if (ans != null && ans.size() > 0) lk = (LabeledKeyword)ans.get(0);
            query.closeAll();
            return lk;
        } catch (Exception e) {
            System.out.println("Exception on getLabeledKeyword(" + label + ", " + readableName +
                ")!");
            e.printStackTrace();
        }
        return null;
    }

    public LabeledKeyword getOrCreateLabeledKeyword(String label, String value, boolean commit) {
        LabeledKeyword lkw = getLabeledKeyword(label, value);

        if (lkw != null) return lkw;
        try {
            System.out.println(
                "trying to persist new LabeledKeyword in Shepherd.getOrCreateLabeledKeyword()");
            lkw = new LabeledKeyword(label, value);
            if (commit) storeNewKeyword(lkw);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lkw;
    }

    public Keyword getOrCreateKeyword(String name) {
        Keyword kw = getKeyword(name);

        if (kw == null) {
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
            if (enc1.hasKeyword(kw) && enc2.hasKeyword(kw)) {
                inCommon.add(kw.getReadableName());
            }
        }
        return inCommon;
    }

    public ImportTask getImportTaskForEncounter(Encounter enc) {
        Query itq = pm.newQuery(
            "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE encounters.contains(enc) && enc.catalogNumber=='"
            + enc.getID() + "'");
        Collection c = (Collection)(itq.execute());
        Iterator it = c.iterator();
        ImportTask itask = null;

        if (it.hasNext()) {
            itask = (ImportTask)it.next();
            itq.closeAll();
            return itask;
        }
        itq.closeAll();
        return itask;
    }

    public List<String> getProjectIdPrefixsForEncounter(Encounter enc) {
        List<Project> projects = getProjectsForEncounter(enc);
        List<String> projectIdPrefixs = null;

        if (projects != null && projects.size() > 0) {
            projectIdPrefixs = new ArrayList<>();
            for (Project project : projects) {
                projectIdPrefixs.add(project.getProjectIdPrefix());
            }
        }
        return projectIdPrefixs;
    }

    public List<Project> getProjectsForEncounter(Encounter enc) {
        List<Project> projects = null;
        Query query = null;

        try {
            query = pm.newQuery(
                "SELECT FROM org.ecocean.Project WHERE encounters.contains(enc) && enc.catalogNumber=='"
                + enc.getID() + "'");
            Collection c = (Collection)(query.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                if (projects == null) {
                    projects = new ArrayList<Project>();
                }
                projects.add((Project)it.next());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            query.closeAll();
        }
        return projects;
    }

    public List<Project> getParticipatingProjectsForUserId(String userId, String orderBy) {
        List<Project> projects = null;
        Query query = null;
        String queryString =
            "SELECT FROM org.ecocean.Project WHERE users.contains(user) && user.username=='" +
            userId + "' VARIABLES org.ecocean.User user";

        try {
            System.out.println("getParticipatingProjectsForUserId() queryString: " + queryString);
            query = pm.newQuery(queryString);
            if (Util.stringExists(orderBy)) query.setOrdering(orderBy);
            Collection c = (Collection)(query.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                if (projects == null) {
                    projects = new ArrayList<Project>();
                }
                System.out.println("got " + projects.size() + " projects to return...");
                projects.add((Project)it.next());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (query != null) query.closeAll();
        }
        return projects;
    }

    public List<Project> getParticipatingProjectsForUserId(String userId) {
        return getParticipatingProjectsForUserId(userId, null);
    }

    public List<Project> getOwnedProjectsForUserId(String userId, String orderBy) {
        List<Project> projects = null;
        Query query = null;

        try {
            if (!Util.stringExists(orderBy)) {
                query = pm.newQuery("SELECT FROM org.ecocean.Project WHERE ownerId=='" + userId +
                    "'");
            } else {
                query = pm.newQuery("SELECT FROM org.ecocean.Project WHERE ownerId=='" + userId +
                    "' ORDER BY " + orderBy);
            }
            Collection c = (Collection)(query.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                if (projects == null) {
                    projects = new ArrayList<Project>();
                }
                // System.out.println("got "+projects.size()+" projects to return...");
                projects.add((Project)it.next());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            query.closeAll();
        }
        return projects;
    }

    public List<Project> getOwnedProjectsForUserId(String userId) {
        return getOwnedProjectsForUserId(userId, null);
    }

    public boolean isSurvey(String num) {
        try {
            Survey tempSvy = ((org.ecocean.Survey)(pm.getObjectById(pm.newObjectIdInstance(
                Survey.class, num.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isSurveyTrack(String num) {
        try {
            SurveyTrack tempTrack = ((org.ecocean.movement.SurveyTrack)(pm.getObjectById(
                pm.newObjectIdInstance(SurveyTrack.class, num.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isEncounter(String num) {
        try {
            Encounter tempEnc = ((org.ecocean.Encounter)(pm.getObjectById(pm.newObjectIdInstance(
                Encounter.class, num.trim()), true)));
        } catch (Exception nsoe) {
            // nsoe.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean isCommunity(String comName) {
        try {
            SocialUnit tempCom = ((org.ecocean.social.SocialUnit)(pm.getObjectById(
                pm.newObjectIdInstance(SocialUnit.class, comName.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isTissueSample(String sampleID, String encounterNumber) {
        TissueSample tempEnc = null;
        String filter = "this.sampleID == \"" + sampleID +
            "\" && this.correspondingEncounterNumber == \"" + encounterNumber + "\"";
        Extent encClass = pm.getExtent(TissueSample.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                acceptedEncounters.closeAll();
                return true;
            }
            acceptedEncounters.closeAll();
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return false;
        }
        return false;
    }

    // TBD - need separate for haplotype and ms markers
    public boolean isGeneticAnalysis(String sampleID, String encounterNumber, String analysisID,
        String type) {
        TissueSample tempEnc = null;
        String filter = "this.analysisType == \"" + type + "\" && this.analysisID == \"" +
            analysisID + "\" && this.sampleID == \"" + sampleID +
            "\" && this.correspondingEncounterNumber == \"" + encounterNumber + "\"";
        Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                acceptedEncounters.closeAll();
                return true;
            }
            acceptedEncounters.closeAll();
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return false;
        }
        return false;
    }

    public boolean isGeneticAnalysis(String analysisID) {
        return (getGeneticAnalysis(analysisID) != null);
    }

    public GeneticAnalysis getGeneticAnalysis(String analysisID) {
        String filter = "this.analysisID == \"" + analysisID + "\"";
        Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                GeneticAnalysis gen = (GeneticAnalysis)it.next();
                acceptedEncounters.closeAll();
                return gen;
            }
            acceptedEncounters.closeAll();
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return null;
        }
        return null;
    }

    public GeneticAnalysis getGeneticAnalysis(String analysisID, String type) {
        String filter = "this.analysisType == \"" + type + "\" && this.analysisID == \"" +
            analysisID + "\"";
        Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                GeneticAnalysis gen = (GeneticAnalysis)it.next();
                acceptedEncounters.closeAll();
                return gen;
            }
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return null;
        }
        acceptedEncounters.closeAll();
        return null;
    }

    public GeneticAnalysis getGeneticAnalysis(String sampleID, String encounterNumber,
        String analysisID, String type) {
        String filter = "this.analysisType == \"" + type + "\" && this.analysisID == \"" +
            analysisID + "\" && this.sampleID == \"" + sampleID +
            "\" && this.correspondingEncounterNumber == \"" + encounterNumber + "\"";
        Extent encClass = pm.getExtent(GeneticAnalysis.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
            Iterator it = c.iterator();
            while (it.hasNext()) {
                GeneticAnalysis gen = (GeneticAnalysis)it.next();
                acceptedEncounters.closeAll();
                return gen;
            }
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            acceptedEncounters.closeAll();
            return null;
        }
        acceptedEncounters.closeAll();
        return null;
    }

    public boolean isKeyword(String keywordDescription) {
        Iterator<Keyword> keywords = getAllKeywords();

        while (keywords.hasNext()) {
            Keyword kw = keywords.next();
            if (kw.getReadableName().equals(keywordDescription)) { return true; }
        }
        return false;
    }

    public boolean isSinglePhotoVideo(String indexname) {
        try {
            SinglePhotoVideo tempEnc = ((org.ecocean.SinglePhotoVideo)(pm.getObjectById(
                pm.newObjectIdInstance(SinglePhotoVideo.class, indexname.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isScanTask(String uniqueID) {
        try {
            ScanTask tempEnc = ((org.ecocean.grid.ScanTask)(pm.getObjectById(pm.newObjectIdInstance(
                ScanTask.class, uniqueID.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isMarkedIndividual(String name) {
        try {
            MarkedIndividual tempShark = ((org.ecocean.MarkedIndividual)(pm.getObjectById(
                pm.newObjectIdInstance(MarkedIndividual.class, name.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isMarkedIndividual(MarkedIndividual mark) {
        return (mark != null && isMarkedIndividual(mark.getIndividualID()));
    }

    public boolean isOccurrence(String name) {
        try {
            Occurrence tempShark = ((org.ecocean.Occurrence)(pm.getObjectById(
                pm.newObjectIdInstance(Occurrence.class, name.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isOccurrence(Occurrence occ) {
        return (occ != null && isOccurrence(occ.getOccurrenceID()));
    }

    public boolean isPath(String name) {
        try {
            Path tempPath = ((org.ecocean.movement.Path)(pm.getObjectById(pm.newObjectIdInstance(
                Path.class, name.trim()), true)));
        } catch (Exception nsoe) {
            return false;
        }
        return true;
    }

    public boolean isRelationship(String type, String markedIndividualName1,
        String markedIndividualName2, String markedIndividualRole1, String markedIndividualRole2,
        boolean checkBidirectional) {
        try {
            if (getRelationship(type, markedIndividualName1, markedIndividualName2,
                markedIndividualRole1, markedIndividualRole2) != null) {
                return true;
            }
            // if requested by checkBidirectional attribute, also check for the inverse of this relationship
            if (checkBidirectional && (getRelationship(type, markedIndividualName2,
                markedIndividualName1, markedIndividualRole2, markedIndividualRole1) != null)) {
                return true;
            }
        } catch (Exception nsoe) {
            return false;
        }
        return false;
    }

    public boolean isRelationship(String type, String markedIndividualName1,
        String markedIndividualName2, String markedIndividualRole1, String markedIndividualRole2,
        String relatedCommunityName, boolean checkBidirectional) {
        try {
            if (getRelationship(type, markedIndividualName1, markedIndividualName2,
                markedIndividualRole1, markedIndividualRole2, relatedCommunityName) != null) {
                return true;
            }
            // if requested by checkBidirectional attribute, also check for the inverse of this relationship
            if (checkBidirectional && (getRelationship(type, markedIndividualName2,
                markedIndividualName1, markedIndividualRole2, markedIndividualRole1,
                relatedCommunityName) != null)) {
                return true;
            }
        } catch (Exception nsoe) {
            return false;
        }
        return false;
    }

    public Iterator<Encounter> getAllEncountersNoFilter() {
        return getAllEncountersNoQuery();
    }

    public Vector getAllEncountersNoFilterAsVector() {
        Collection c;
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query acceptedEncounters = pm.newQuery(encClass);

        try {
            c = (Collection)(acceptedEncounters.execute());
            Vector list = new Vector(c);
            acceptedEncounters.closeAll();
            return list;
        } catch (Exception npe) {
            acceptedEncounters.closeAll();
            System.out.println(
                "Error encountered when trying to execute getAllEncountersNoFilter. Returning a null collection because I didn't have a transaction to use.");
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
            System.out.println(
                "Error encountered when trying to execute getAllEncountersNoQuery. Returning a null iterator.");
            npe.printStackTrace();
            return null;
        }
    }

    public Iterator<Taxonomy> getAllTaxonomies() {
        try {
            Extent taxClass = pm.getExtent(Taxonomy.class, true);
            Iterator it = taxClass.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllEncountersNoQuery. Returning a null iterator.");
            npe.printStackTrace();
            return null;
        }
    }

    public List<List<String> > getAllTaxonomyCommonNames() {
        return getAllTaxonomyCommonNames(false);
    }

    // forceSpaces will turn `Foo bar_bar` into `Foo bar bar` - use with caution!
    public List<List<String> > getAllTaxonomyCommonNames(boolean forceSpaces) {
        Set<String> allSciNames = new LinkedHashSet<String>();
        Set<String> allComNames = new LinkedHashSet<String>();
        List<String> configNamesSci = CommonConfiguration.getIndexedPropertyValues("genusSpecies",
            getContext());
        List<String> configNamesCom = CommonConfiguration.getIndexedPropertyValues("commonName",
            getContext());

        allSciNames.addAll(configNamesSci);
        allComNames.addAll(configNamesCom);

        List<String> allSciNamesList = new ArrayList<String>(allSciNames);
        List<String> allComNamesList = new ArrayList<String>(allComNames);
        List<List<String> > result = new ArrayList<>();
        if (forceSpaces) {
            List<String> spaceySci = new ArrayList<String>();
            for (String tx : allSciNamesList) {
                spaceySci.add(tx.replaceAll("_", " "));
            }
            List<String> spaceyCom = new ArrayList<String>();
            for (String tx : allComNamesList) {
                spaceyCom.add(tx.replaceAll("_", " "));
            }
            result.add(spaceySci);
            result.add(spaceyCom);
        } else {
            result.add(allSciNamesList);
            result.add(allComNamesList);
        }
        return result;
    }

    // tragically this mixes Taxonomy (class, via db) with commonConfiguration-based values. SIGH
    // TODO when property files go away (yay) this should become just db
    public List<String> getAllTaxonomyNames() {
        return getAllTaxonomyNames(false);
    }

    // forceSpaces will turn `Foo bar_bar` into `Foo bar bar` - use with caution!
    public List<String> getAllTaxonomyNames(boolean forceSpaces) {
        Iterator<Taxonomy> allTaxonomies = getAllTaxonomies();
        Set<String> allNames = new HashSet<String>();

        while (allTaxonomies.hasNext()) {
            Taxonomy taxy = allTaxonomies.next();
            allNames.add(taxy.getScientificName());
        }
        List<String> configNames = CommonConfiguration.getIndexedPropertyValues("genusSpecies",
            getContext());
        allNames.addAll(configNames);

        List<String> allNamesList = new ArrayList<String>(allNames);
        java.util.Collections.sort(allNamesList);
        if (forceSpaces) {
            List<String> spacey = new ArrayList<String>();
            for (String tx : allNamesList) {
                spacey.add(tx.replaceAll("_", " "));
            }
            return spacey;
        }
        return allNamesList;
    }

    public boolean isValidTaxonomyName(String sciName) {
        return getAllTaxonomyNames(true).contains(sciName.replaceAll("_", " "));
    }

    // note: where clause can also contain " ORDER BY xxx"
    public Iterator getAnnotationsFilter(String jdoWhereClause) {
        Query query = null;

        try {
            query = pm.newQuery("SELECT FROM org.ecocean.Annotation WHERE " + jdoWhereClause);
            Collection c = (Collection)(query.execute());
            List list = new ArrayList(c);
            Iterator it = list.iterator();
            query.closeAll();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllAnnotationsFilter. Returning a null iterator.");
            npe.printStackTrace();
            if (query != null) query.closeAll();
            return null;
        }
    }

    public Iterator getAllMediaAssets() {
        try {
            Extent maClass = pm.getExtent(MediaAsset.class, true);
            Iterator it = maClass.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllMediaAssets. Returning a null iterator.");
            npe.printStackTrace();
            return null;
        }
    }

    // note: where clause can also contain " ORDER BY xxx"
    public Iterator getMediaAssetsFilter(String jdoWhereClause) {
        Query query = null;

        try {
            query = pm.newQuery("SELECT FROM org.ecocean.media.MediaAsset WHERE " + jdoWhereClause);
            Collection c = (Collection)(query.execute());
            List list = new ArrayList(c);
            Iterator it = list.iterator();
            query.closeAll();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllAnnotationsFilter. Returning a null iterator.");
            npe.printStackTrace();
            if (query != null) query.closeAll();
            return null;
        }
    }

    public Iterator<ScanTask> getAllScanTasksNoQuery() {
        try {
            Extent taskClass = pm.getExtent(ScanTask.class, true);
            Iterator it = taskClass.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllScanTasksNoQuery. Returning a null iterator.");
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
        return getAllEncountersNoQuery();
    }

    public Iterator<Encounter> getAllEncounters(Query acceptedEncounters) {
        Collection c;

        try {
            c = (Collection)(acceptedEncounters.execute());
            ArrayList list = new ArrayList(c);
            // Collections.reverse(list);
            Iterator it = list.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    public List getAllOccurrences(Query myQuery) {
        Collection c;

        try {
            // System.out.println("getAllOccurrences is called on query "+myQuery);
            c = (Collection)(myQuery.execute());
            ArrayList list = new ArrayList(c);
            // System.out.println("getAllOccurrences got "+list.size()+" occurrences");
            // Collections.reverse(list);
            Iterator it = list.iterator();
            return list;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllOccurrences(Query). Returning a null collection.");
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
            System.out.println(
                "Error encountered when trying to execute getAllEncountersNoQuery. Returning a null iterator.");
            npe.printStackTrace();
            return null;
        }
    }

    public List<SinglePhotoVideo> getAllSinglePhotoVideo(Query acceptedEncounters) {
        Collection c;

        try {
            c = (Collection)(acceptedEncounters.execute());
            ArrayList<SinglePhotoVideo> list = new ArrayList<SinglePhotoVideo>(c);
            return list;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllSinglePhotoVideo(Query). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    public Iterator<Encounter> getAllEncounters(Query acceptedEncounters,
        Map<String, Object> paramMap) {
        Collection c;

        try {
            c = (Collection)(acceptedEncounters.executeWithMap(paramMap));
            ArrayList list = new ArrayList(c);
            // Collections.reverse(list);
            Iterator it = list.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    public Iterator<Occurrence> getAllOccurrences(Query acceptedOccurrences,
        Map<String, Object> paramMap) {
        Collection c;

        try {
            // System.out.println("getAllOccurrences is called on query "+acceptedOccurrences+" and paramMap "+paramMap);
            c = (Collection)(acceptedOccurrences.executeWithMap(paramMap));
            ArrayList list = new ArrayList(c);
            // System.out.println("getAllOccurrences got "+list.size()+" occurrences");
            // Collections.reverse(list);
            Iterator it = list.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllOccurrences(Query). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    public Iterator<Survey> getAllSurveys(Query acceptedSurveys, Map<String, Object> paramMap) {
        Collection c;

        try {
            System.out.println("getAllSurveys is called on query " + acceptedSurveys +
                " and paramMap " + paramMap);
            c = (Collection)(acceptedSurveys.executeWithMap(paramMap));
            ArrayList list = new ArrayList(c);
            System.out.println("getAllSurveys got " + list.size() + " surveys");
            Iterator it = list.iterator();
            return it;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllSurveys(Query). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    public List<Organization> getAllOrganizationsForUser(User user) {
        ArrayList<Organization> al = new ArrayList<Organization>();

        try {
            Query q = getPM().newQuery(
                "SELECT FROM org.ecocean.Organization WHERE members.contains(user) && user.uuid == \""
                + user.getUUID() + "\" VARIABLES org.ecocean.User user");
            q.setOrdering("name ascending");
            Collection results = (Collection)q.execute();
            al = new ArrayList<Organization>(results);
            q.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return al;
        }
        return al;
    }

    public List<Organization> getAllCommonOrganizationsForTwoUsers(User user1, User user2) {
        ArrayList<Organization> al = new ArrayList<Organization>();

        if (user1 == null || user2 == null) return al;
        try {
            Query q = getPM().newQuery(
                "SELECT FROM org.ecocean.Organization WHERE members.contains(user1) && members.contains(user2) && user1.uuid == \""
                + user1.getUUID() + "\" && user2.uuid == \"" + user2.getUUID() +
                "\" VARIABLES org.ecocean.User user1;org.ecocean.User user2");
            Collection results = (Collection)q.execute();
            al = new ArrayList<Organization>(results);
            q.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return al;
        } catch (Exception xe) {
            xe.printStackTrace();
            return al;
        }
        return al;
    }

    public List<Organization> getAllOrganizations() {
        ArrayList<Organization> al = new ArrayList<Organization>();

        try {
            Extent allOrgs = pm.getExtent(Organization.class, true);
            Query q = pm.newQuery(allOrgs);
            q.setOrdering("name ascending");
            Collection results = (Collection)q.execute();
            al = new ArrayList<Organization>(results);
            q.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return al;
        }
        return al;
    }

    public List getPairs(Query query, int pageSize) {
        Collection c;

        query.setRange(0, pageSize);
        try {
            c = (Collection)(query.execute());
            ArrayList list = new ArrayList(c);
            return list;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllEncounters(Query). Returning a null collection.");
            npe.printStackTrace();
            return null;
        }
    }

    /**
     * Retrieves all encounters that are stored in the database in the order specified by the input String
     *
     * @return an Iterator of all valid whale shark encounters stored in the visual database, arranged by the input String
     * @see encounter, java.util.Iterator
     */
    public Iterator<Encounter> getAllEncounters(String order) {
        // String filter = "this.state != \"unidentifiable\" && this.state == \"approved\"";
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query acceptedEncounters = pm.newQuery(encClass);

        acceptedEncounters.setOrdering(order);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList listy = new ArrayList(c);
        // Iterator it = c.iterator();
        Iterator it = listy.iterator();
        acceptedEncounters.closeAll();
        return it;
    }

    /*
     * Retrieve the distinct User objects for all Encounters related to this MarkedIndividual
     *
     */
    public ArrayList<User> getAllUsersForMarkedIndividual(MarkedIndividual indie) {
        ArrayList<User> relatedUsers = new ArrayList<User>();
        ArrayList<String> usernames = indie.getAllAssignedUsers();
        int size = usernames.size();

        if (size > 0) {
            for (int i = 0; i < size; i++) {
                String thisUsername = usernames.get(i);
                if (getUser(thisUsername) != null) {
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
    public List<User> getAllUsersForMarkedIndividual(String indie) {
        ArrayList<User> relatedUsers = new ArrayList<User>();

        if (getMarkedIndividual(indie) != null) {
            MarkedIndividual foundIndie = getMarkedIndividual(indie);
            return getAllUsersForMarkedIndividual(foundIndie);
        }
        return relatedUsers;
    }

    public Iterator<Encounter> getAllEncounters(Query acceptedEncounters, String order) {
        acceptedEncounters.setOrdering(order);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList list = new ArrayList(c);
        // Collections.reverse(list);
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
        // String filter = filter2use + " && this.approved == true";
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter2use);

        acceptedEncounters.setOrdering(order);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList listy = new ArrayList(c);
        Iterator it = listy.iterator();
        acceptedEncounters.closeAll();
        // Iterator it = c.iterator();
        return it;
    }

    public Occurrence getOccurrenceForEncounter(String encounterID) {
        String filter =
            "SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.catalogNumber == \""
            + encounterID + "\"  VARIABLES org.ecocean.Encounter enc";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        while (it.hasNext()) {
            Occurrence occur = (Occurrence)it.next();
            query.closeAll();
            return occur;
        }
        query.closeAll();
        return null;
    }

    public User getUserByEmailAddress(String email) {
        String hashedEmailAddress = User.generateEmailHash(email);

        return getUserByHashedEmailAddress(hashedEmailAddress);
    }

    public User getUserByHashedEmailAddress(String hashedEmail) {
        ArrayList<User> users = new ArrayList<User>();
        String filter = "SELECT FROM org.ecocean.User WHERE hashedEmailAddress == \"" +
            hashedEmail + "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());

        if (c != null) { users = new ArrayList<User>(c); }
        query.closeAll();
        if (users.size() > 0) { return users.get(0); }
        return null;
    }

    // note: if existing user is found *and* fullName is set, user will get updated with new name!
    // (this is to replicate legacy behavior of creating users during encounter submission)
    public User getOrCreateUserByEmailAddress(String email, String fullName) {
        if (Util.stringIsEmptyOrNull(email)) return null;
        User user = getUserByEmailAddress(email);
        if (user == null) user = new User(email, Util.generateUUID());
        if (!Util.stringIsEmptyOrNull(fullName)) user.setFullName(fullName);
        getPM().makePersistent(user);
        return user;
    }

    public List<User> getUsersWithEmailAddresses() {
        ArrayList<User> users = new ArrayList<User>();
        String filter = "SELECT FROM org.ecocean.User WHERE emailAddress != null";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());

        if (c != null) users = new ArrayList<User>(c);
        query.closeAll();
        return users;
    }

    // this seems more what we want
    public List<User> getUsersWithEmailAddressesWhoReceiveEmails() {
        ArrayList<User> users = new ArrayList<User>();
        String filter = "SELECT FROM org.ecocean.User WHERE emailAddress != null && receiveEmails";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());

        if (c != null) users = new ArrayList<User>(c);
        query.closeAll();
        return users;
    }

    public ArrayList<Project> getAllProjectsForMarkedIndividual(MarkedIndividual individual) {
        Query query = null;
        Iterator<Project> projectIter = null;
        ArrayList<Project> projectArr = null;

        try {
            String filter =
                "SELECT FROM org.ecocean.Project WHERE encounters.contains(enc) && enc.individual == individual VARIABLES org.ecocean.Encounter enc";
            query = getPM().newQuery(filter);
            query.declareParameters("MarkedIndividual individual");
            Collection c = (Collection)query.execute(individual);
            projectIter = c.iterator();
            while (projectIter.hasNext()) {
                if (projectArr == null) {
                    projectArr = new ArrayList<>();
                }
                projectArr.add(projectIter.next());
            }
        } catch (JDOException jdoe) {
            jdoe.printStackTrace();
        } finally {
            query.closeAll();
        }
        return projectArr;
    }

    public ArrayList<Project> getAllProjectsForEncounter(Encounter encounter) {
        Query query = null;
        Iterator<Project> projectIter = null;
        ArrayList<Project> projectArr = null;

        try {
            String filter =
                "SELECT FROM org.ecocean.Project WHERE encounters.contains(enc) && enc.catalogNumber == '"
                + encounter.getCatalogNumber() + "' VARIABLES org.ecocean.Encounter enc";
            query = getPM().newQuery(filter);
            Collection c = (Collection)query.execute();
            projectArr = new ArrayList<Project>(c);
        } catch (JDOException jdoe) {
            jdoe.printStackTrace();
        } finally {
            if (query != null) query.closeAll();
        }
        return projectArr;
    }

    public List<Map.Entry> getAllOtherIndividualsOccurringWithMarkedIndividual(
        MarkedIndividual indiv) {
        HashMap<String, Integer> hmap = new HashMap<String, Integer>();
        TreeMap<String, Integer> map = new TreeMap<String, Integer>();
        String filter =
            "SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individual == ind VARIABLES org.ecocean.Encounter enc";
        Query query = getPM().newQuery(filter);

        query.declareParameters("MarkedIndividual ind");
        Collection c = (Collection)query.execute(indiv);
        Iterator<Occurrence> it = c.iterator();
        if (it != null) {
            while (it.hasNext()) {
                Occurrence oc = it.next();
                // System.out.println("     Found an occurrence for my indie!!!!");
                ArrayList<MarkedIndividual> alreadyCounted = new ArrayList<MarkedIndividual>();
                ArrayList<Encounter> encounters = oc.getEncounters();
                int numEncounters = encounters.size();
                for (int i = 0; i < numEncounters; i++) {
                    Encounter enc = encounters.get(i);
                    if ((enc.getIndividual() != null) && (!enc.getIndividual().equals(indiv))) {
                        MarkedIndividual indieEnc = enc.getIndividual();
                        // check if we already have this Indie
                        if (!hmap.containsKey(indieEnc.getIndividualID())) {
                            hmap.put(indieEnc.getIndividualID(), (new Integer(1)));
                            alreadyCounted.add(indieEnc);
                        } else if (!alreadyCounted.contains(indieEnc)) {
                            Integer oldValue = hmap.get(indieEnc.getIndividualID());
                            hmap.put(indieEnc.getIndividualID(), (oldValue + 1));
                            // System.out.println("Iterating: "+indieEnc.getIndividualID());
                        }
                    }
                }
            }
        }
        ArrayList<Map.Entry> as = new ArrayList<Map.Entry>(hmap.entrySet());
        if (as.size() > 0) {
            IndividualOccurrenceNumComparator cmp = new IndividualOccurrenceNumComparator();
            Collections.sort(as, cmp);
            Collections.reverse(as);
        }
        query.closeAll();
        return as;
    }

    public List<TissueSample> getAllTissueSamplesForEncounter(String encNum) {
        String filter = "correspondingEncounterNumber == \"" + encNum + "\"";
        Extent encClass = pm.getExtent(TissueSample.class, true);
        Query samples = pm.newQuery(encClass, filter);
        Collection c = (Collection)(samples.execute());
        ArrayList al = new ArrayList<TissueSample>(c);

        samples.closeAll();
        return (al);
    }

    public ArrayList<TissueSample> getAllTissueSamplesForMarkedIndividual(MarkedIndividual indy) {
        ArrayList<TissueSample> al = new ArrayList<TissueSample>();
        Query q = getPM().newQuery(
            "SELECT FROM org.ecocean.genetics.TissueSample WHERE indy.individualID == '" +
            indy.getIndividualID() +
            "' && indy.encounters.contains(enc) && enc.tissueSamples.contains(this) VARIABLES org.ecocean.Encounter enc;org.ecocean.MarkedIndividual indy");

        try {
            Collection c = (Collection)q.execute();
            al = new ArrayList<TissueSample>(c);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            q.closeAll();
        }
        return al;
    }

    public List<SinglePhotoVideo> getAllSinglePhotoVideosForEncounter(String encNum) {
        ArrayList<Annotation> al = getAnnotationsForEncounter(encNum);
        int numAnnots = al.size();
        ArrayList<SinglePhotoVideo> myArray = new ArrayList<SinglePhotoVideo>();

        for (int i = 0; i < numAnnots; i++) {
            try {
                MediaAsset ma = al.get(i).getMediaAsset();
                AssetStore as = ma.getStore();
                String fullFileSystemPath = as.localPath(ma).toString();
                URL u = ma.safeURL(this);
                String webURL = ((u == null) ? null : u.toString());
                int lastIndex = webURL.lastIndexOf("/") + 1;
                String filename = webURL.substring(lastIndex);
                SinglePhotoVideo spv = new SinglePhotoVideo(encNum, filename, fullFileSystemPath);
                spv.setWebURL(webURL);
                spv.setDataCollectionEventID(ma.getUUID());
                // add Keywords
                if (ma.getKeywords() != null) {
                    ArrayList<Keyword> alkw = ma.getKeywords();
                    int numKeywords = alkw.size();
                    for (int y = 0; y < numKeywords; y++) {
                        Keyword kw = alkw.get(y);
                        spv.addKeyword(kw);
                    }
                }
                myArray.add(spv);
            } catch (Exception e) {}
        }
        return myArray;
    }

    public ArrayList<MediaAsset> getAllMediAssetsWithKeyword(Keyword word0) {
        String keywordQueryString =
            "SELECT FROM org.ecocean.media.MediaAsset WHERE ( this.keywords.contains(word0) && ( word0.indexname == \""
            + word0.getIndexname() + "\" ) ) VARIABLES org.ecocean.Keyword word0";
        Query samples = pm.newQuery(keywordQueryString);
        Collection c = (Collection)(samples.execute());
        ArrayList<MediaAsset> myArray = new ArrayList<MediaAsset>(c);

        samples.closeAll();
        return myArray;
    }

    public List<MediaAsset> getKeywordPhotosForIndividual(MarkedIndividual indy,
        String[] kwReadableNames, int maxResults) {
        String filter =
            "SELECT FROM org.ecocean.Annotation WHERE enc3_0.annotations.contains(this) && enc3_0.individual.individualID == \""
            + indy.getIndividualID() + "\" ";
        String vars = " VARIABLES org.ecocean.Encounter enc3_0";

        for (int i = 0; i < kwReadableNames.length; i++) {
            filter += "  && features.contains(feat" + i + ") && feat" + i +
                ".asset.keywords.contains(word" + i + ") &&  word" + i + ".readableName == \"" +
                kwReadableNames[i] + "\" ";
            vars += ";org.ecocean.Keyword word" + i + ";org.ecocean.media.Feature feat" + i;
        }
        ArrayList<Annotation> results = new ArrayList<Annotation>();
        try {
            Query query = this.getPM().newQuery(filter + vars);
            query.setRange(0, maxResults);
            Collection coll = (Collection)(query.execute());
            results = new ArrayList<Annotation>(coll);
            if (query != null) query.closeAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
        ArrayList<MediaAsset> assResults = new ArrayList<MediaAsset>();
        for (Annotation ann : results) {
            if (ann != null && ann.getMediaAsset() != null) assResults.add(ann.getMediaAsset());
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
        List<MediaAsset> results = getKeywordPhotosForIndividual(indy,
            new String[] { kwName, "ProfilePhoto" }, 1);
        MediaAsset result = (results != null && results.size() > 0) ? results.get(0) : null;

        if (result != null) return (result);
        // we couldn't find a profile photo with the keyword
        results = getKeywordPhotosForIndividual(indy, new String[] { kwName }, 1);
        if (results != null && results.size() > 0) return (results.get(0));
        return null;
    }

    public Iterator<Encounter> getAllEncountersNoFilter(String order, String filter2use) {
        String filter = filter2use;
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        acceptedEncounters.setOrdering(order);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList listy = new ArrayList(c);

        // Iterator it = c.iterator();
        Iterator it = listy.iterator();
        acceptedEncounters.closeAll();
        return it;
    }

    public ArrayList<ScheduledIndividualMerge> getAllIncompleteScheduledIndividualMerges() {
        List<WildbookScheduledTask> tasks = getAllWildbookScheduledTasksWithFilter(
            "!this.taskComplete && this.scheduledTaskType == \"ScheduledIndividualMerge\" ");
        ArrayList<ScheduledIndividualMerge> mergeTasks = new ArrayList<>();

        if (tasks != null) {
            for (WildbookScheduledTask task : tasks) {
                ScheduledIndividualMerge mergeTask = (ScheduledIndividualMerge)task;
                mergeTasks.add(mergeTask);
            }
        }
        return mergeTasks;
    }

    public ArrayList<WildbookScheduledTask> getAllIncompleteWildbookScheduledTasks() {
        return getAllWildbookScheduledTasksWithFilter("!this.taskComplete");
    }

    public ArrayList<ScheduledIndividualMerge> getAllCompleteScheduledIndividualMergesForUsername(
        String username) {
        String usernameQuery = "";

        if (username != null && !"".equals(username)) {
            usernameQuery = " && this.initiatorName == \"" + username.trim() + "\"";
        }
        // this is where long names get you
        List<WildbookScheduledTask> tasks = getAllWildbookScheduledTasksWithFilter(
            "this.taskComplete && this.scheduledTaskType == \"ScheduledIndividualMerge\"" +
            usernameQuery);
        ArrayList<ScheduledIndividualMerge> mergeTasks = new ArrayList<>();
        if (tasks != null) {
            for (WildbookScheduledTask task : tasks) {
                ScheduledIndividualMerge mergeTask = (ScheduledIndividualMerge)task;
                mergeTasks.add(mergeTask);
            }
        }
        return mergeTasks;
    }

    public ArrayList<WildbookScheduledTask> getAllWildbookScheduledTasksWithFilter(String filter) {
        ArrayList<WildbookScheduledTask> taskList = new ArrayList();
        Query query = null;

        try {
            Extent taskClass = pm.getExtent(WildbookScheduledTask.class, true);
            query = pm.newQuery(taskClass, filter);
            query.setOrdering("this.taskScheduledExecutionTimeLong descending");
            Collection c = (Collection)(query.execute());
            taskList = new ArrayList(c);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            query.closeAll();
        }
        return taskList;
    }

    public MarkedIndividual getMarkedIndividual(String id) {
        MarkedIndividual tempShark = null;

        try {
            tempShark = ((org.ecocean.MarkedIndividual)(pm.getObjectById(pm.newObjectIdInstance(
                MarkedIndividual.class, id.trim()), true)));
        } catch (Exception nsoe) {
            // nsoe.printStackTrace();
            return null;
        }
        return tempShark;
    }

    public List<MarkedIndividual> getMarkedIndividualsFromProject(Project project) {
        List<MarkedIndividual> individuals = new ArrayList<MarkedIndividual>();

        try {
            Query query = getPM().newQuery(
                "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && project.encounters.contains(enc) VARIABLES org.ecocean.Encounter enc; org.ecocean.Project project ");
            // query.declareParameters("Project project");
            Collection c = (Collection)query.execute(project);
            individuals = new ArrayList<MarkedIndividual>(c);
            query.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return individuals;
        }
        return individuals;
    }

    // we now use enc.getIndividual because there is a foreign-key connecting encs to inds without another SQL call
    @Deprecated public MarkedIndividual getMarkedIndividualHard(Encounter enc) {
        String num = enc.getCatalogNumber();
        String filter =
            "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && enc.catalogNumber == \""
            + num + "\"  VARIABLES org.ecocean.Encounter enc";
        ArrayList al = new ArrayList();

        try {
            Extent indClass = pm.getExtent(MarkedIndividual.class, true);
            Query acceptedInds = pm.newQuery(indClass, filter);
            Collection c = (Collection)(acceptedInds.execute());
            al = new ArrayList(c);
            try {
                acceptedInds.closeAll();
            } catch (NullPointerException npe) {}
        } catch (Exception e) {
            System.out.println("Exception in getMarkedIndividualHard for enc " + num);
            e.printStackTrace();
        }
        return ((al.size() > 0) ? ((MarkedIndividual)al.get(0)) : null);
    }

    public Task getTask(String id) {
        Task theTask = null;

        try {
            theTask = ((org.ecocean.ia.Task)(pm.getObjectById(pm.newObjectIdInstance(Task.class,
                id.trim()), true)));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return theTask;
    }

    public List<Task> getIdentificationTasksForUser(User user) {
        if ((user == null) || (user.getUsername() == null)) return null;
        // String sql = "SELECT \"TASK\".\"ID\" FROM \"TASK\" "
        String sql = "SELECT \"ID\" FROM \"TASK\" " +
            "JOIN \"TASK_OBJECTANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_OID\" = \"TASK\".\"ID\") "
            +
            "JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"TASK_OBJECTANNOTATIONS\".\"ID_EID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") "
            +
            "JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") "
            + "WHERE \"ENCOUNTER\".\"SUBMITTERID\" = '" + user.getUsername() +
            "' ORDER BY \"TASK\".\"CREATED\" desc";
        Query q = getPM().newQuery("javax.jdo.query.SQL", sql);
        q.setClass(Task.class);
        Collection c = (Collection)q.execute();
        List<Task> all = new ArrayList(c);
        q.closeAll();
        return all;
    }

    public MarkedIndividual getMarkedIndividualQuiet(String name) {
        MarkedIndividual indiv = null;

        try {
            indiv = ((org.ecocean.MarkedIndividual)(pm.getObjectById(pm.newObjectIdInstance(
                MarkedIndividual.class, name.trim()), true)));
        } catch (Exception nsoe) {
            return null;
        }
        return indiv;
    }

    public MarkedIndividual getMarkedIndividual(Encounter enc) {
        if (enc == null) return null;
        if (!Util.stringExists(enc.getIndividualID())) return null;
        return (getMarkedIndividualQuiet(enc.getIndividualID()));
    }

    public Occurrence getOccurrence(String id) {
        Occurrence tempShark = null;

        try {
            tempShark = ((org.ecocean.Occurrence)(pm.getObjectById(pm.newObjectIdInstance(
                Occurrence.class, id.trim()), true)));
        } catch (Exception nsoe) {
            // nsoe.printStackTrace();
            return null;
        }
        return tempShark;
    }

    public Occurrence getOrCreateOccurrence(String id) {
        if (id == null) return new Occurrence(Util.generateUUID());
        Occurrence occ = getOccurrence(id);
        if (occ != null) return occ;
        occ = new Occurrence(id);
        return occ;
    }

    public Survey getSurvey(String id) {
        Survey srv = null;

        try {
            srv = ((org.ecocean.Survey)(pm.getObjectById(pm.newObjectIdInstance(Survey.class,
                id.trim()), true)));
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
        return srv;
    }

    public SurveyTrack getSurveyTrack(String id) {
        SurveyTrack stk = null;

        try {
            stk = ((org.ecocean.movement.SurveyTrack)(pm.getObjectById(pm.newObjectIdInstance(
                SurveyTrack.class, id.trim()), true)));
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
        return stk;
    }

    public Path getPath(String id) {
        Path pth = null;

        try {
            pth = ((org.ecocean.movement.Path)(pm.getObjectById(pm.newObjectIdInstance(Path.class,
                id.trim()), true)));
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
        return pth;
    }

    public PointLocation getPointLocation(String id) {
        PointLocation pl = null;

        try {
            pl = ((org.ecocean.PointLocation)(pm.getObjectById(pm.newObjectIdInstance(
                PointLocation.class, id.trim()), true)));
        } catch (Exception nsoe) {
            nsoe.printStackTrace();
            return null;
        }
        return pl;
    }

    public Occurrence getOccurrence(Encounter enc) {
        if (enc == null) return null;
        Occurrence occ = (getOccurrence(enc.getOccurrenceID()));
        if (occ != null) return occ;
        return getOccurrenceForEncounter(enc.getCatalogNumber());
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
                // System.out.println(tempShark.getName()+" has more than one encounter.");
            }
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
                // System.out.println(tempShark.getName()+" has more than one encounter.");
            }
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
        Collection c = (Collection)(sharks.execute());
        ArrayList list = new ArrayList(c);
        sharks.closeAll();
        Iterator it = list.iterator();
        return it;
    }

    public ArrayList<Project> getAllProjects() {
        Extent projectClass = null;

        try {
            projectClass = pm.getExtent(Project.class, true);
        } catch (javax.jdo.JDOException jdoe) {
            jdoe.printStackTrace();
        }
        Query projectQuery = pm.newQuery(projectClass);
        projectQuery.setOrdering("researchProjectName ascending NULLS LAST");
        Collection c = (Collection)(projectQuery.execute());
        ArrayList<Project> list = new ArrayList<>(c);
        projectQuery.closeAll();
        return list;
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

    public Iterator<MarkedIndividual> getAllMarkedIndividuals(Query sharkies, String order,
        Map<String, Object> params) {
        sharkies.setOrdering(order);
        Collection c = (Collection)(sharkies.executeWithMap(params));
        ArrayList list = new ArrayList(c);
        Iterator it = list.iterator();
        return it;
    }

    public int getNumMarkedIndividuals() {
        int num = 0;
        Query q = pm.newQuery(MarkedIndividual.class); // no filter, so all instances match

        try {
            pm.getFetchPlan().setGroup("count");
            Collection results = (Collection)q.execute();
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
            Collection results = (Collection)q.execute();
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
            Long myValue = (Long)query.execute();
            query.closeAll();
            return myValue.intValue();
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute shepherd.getNumUnfinishedScanTasks(). Returning an zero value.");
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
            Collection c = (Collection)(acceptedEncounters.execute());
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
            Collection c = (Collection)(acceptedOccurrences.execute());
            int num = c.size();
            acceptedOccurrences.closeAll();
            return num;
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            acceptedOccurrences.closeAll();
            return 0;
        }
    }

    public int getNumAssetStores() {
        Extent encClass = pm.getExtent(AssetStore.class, true);
        Query acceptedEncounters = pm.newQuery(encClass);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
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
        String filter = "this.locationID == \"" + locationCode + "\"";
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
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
            Collection c = (Collection)(acceptedSurveys.execute());
            int num = c.size();
            acceptedSurveys.closeAll();
            return num;
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            acceptedSurveys.closeAll();
            return 0;
        }
    }

    public int getNumUnidentifiableEncounters() {
        Extent encClass = pm.getExtent(Encounter.class, true);
        String filter = "this.state == \"unidentifiable\"";
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
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
        String filter = "this.state == \"unidentifiable\" && this.individualID == \"" + individual +
            "\"";
        Query acceptedEncounters = pm.newQuery(encClass, filter);

        try {
            Collection c = (Collection)(acceptedEncounters.execute());
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
            Collection c = (Collection)(acceptedEncounters.execute());
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

    /**
     * Returns the <i>i</i>th numbered encounter for a shark
     *
     * @param  tempShark  the shark to retrieve an encounter from i			the number of the shark to get, numbered from 0...<i>n</i>
     * @return the <i>i</i>th encounter of the specified shark
     * @see MarkedIndividual
     */
    public Encounter getEncounter(MarkedIndividual tempShark, int i) {
        MarkedIndividual myShark = getMarkedIndividual(tempShark.getName());
        Encounter e = (Encounter)myShark.getEncounters().get(i);

        return e;
    }

    public Encounter getEncounter(MediaAsset ma) {
        if (ma == null || !ma.hasAnnotations()) return null;
        Annotation ann = ma.getAnnotations().get(0);
        return ann.findEncounter(this);
    }

    /**
     * Opens the database up for information retrieval, storage, and removal
     */
    public void beginDBTransaction() {
        // PersistenceManagerFactory pmf = ShepherdPMF.getPMF(localContext);
        try {
            if (pm == null || pm.isClosed()) {
                pm = ShepherdPMF.getPMF(localContext).getPersistenceManager();
                pm.currentTransaction().begin();
            } else if (!pm.currentTransaction().isActive()) {
                pm.currentTransaction().begin();
            }
            ShepherdState.setShepherdState(action + "_" + shepherdID, "begin");

            pm.addInstanceLifecycleListener(new WildbookLifecycleListener(), null);
        } catch (JDOUserException jdoe) {
            jdoe.printStackTrace();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
        // pmf=null;
    }

    public boolean isDBTransactionActive() {
        return (pm != null && !pm.isClosed() && pm.currentTransaction().isActive());
    }

    /**
     * Commits (makes permanent) any changes made to an open database
     */
    // TODO: Either (a) throw an exception itself or (b) return boolean of success (the latter was disabled, needs investigation)
    public void commitDBTransaction() {
        try {
            // System.out.println("     shepherd:"+identifyMe+" is trying to commit a transaction");
            // System.out.println("Is the pm null? " + Boolean.toString(pm == null));
            if ((pm != null) && (pm.currentTransaction().isActive())) {
                // System.out.println("     Now commiting a transaction with pm"+(String)pm.getUserObject());
                pm.currentTransaction().commit();

                // return true;
                // System.out.println("A transaction has been successfully committed.");
            } else {
                System.out.println("You are trying to commit an inactive transaction.");
                // return false;
            }
            ShepherdState.setShepherdState(action + "_" + shepherdID, "commit");
        } catch (JDOUserException jdoe) {
            jdoe.printStackTrace();
            System.out.println("I failed to commit a transaction." + "\n" + jdoe.getStackTrace());
            // return false;
        } catch (JDOException jdoe2) {
            jdoe2.printStackTrace();
            Throwable[] throwables = jdoe2.getNestedExceptions();
            int numThrowables = throwables.length;
            for (int i = 0; i < numThrowables; i++) {
                Throwable t = throwables[i];
                if (t instanceof java.sql.SQLException) {
                    java.sql.SQLException exc = (java.sql.SQLException)t;
                    java.sql.SQLException g = exc.getNextException();
                    g.printStackTrace();
                }
                t.printStackTrace();
            }
            // return false;
        }
        // added to prevent conflicting calls jah 1/19/04
        catch (NullPointerException npe) {
            System.out.println(
                "A null pointer exception was thrown while trying to commit a transaction!");
            npe.printStackTrace();
            // return false;
        }
    }

    /**
     * Since we call these together all over Wildbook
     */
    public void updateDBTransaction() {
        commitDBTransaction();
        beginDBTransaction();
    }

    /**
     * Closes a PersistenceManager
     */
    public void closeDBTransaction() {
        try {
            if ((pm != null) && (!pm.isClosed())) {
                pm.close();
            }
            // ShepherdState.setShepherdState(action+"_"+shepherdID, "close");
            ShepherdState.removeShepherdState(action + "_" + shepherdID);

            // logger.info("A PersistenceManager has been successfully closed.");
        } catch (JDOUserException jdoe) {
            System.out.println("I hit an error trying to close a DBTransaction.");
            jdoe.printStackTrace();

            // logger.error("I failed to close a PersistenceManager."+"\n"+jdoe.getStackTrace());
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
                // System.out.println("     Now rollingback a transaction with pm"+(String)pm.getUserObject());
                pm.currentTransaction().rollback();
                // System.out.println("A transaction has been successfully committed.");
            } else {
                // System.out.println("You are trying to rollback an inactive transaction.");
            }
            ShepherdState.setShepherdState(action + "_" + shepherdID, "rollback");
        } catch (JDOUserException jdoe) {
            jdoe.printStackTrace();
        } catch (JDOFatalUserException fdoe) {
            fdoe.printStackTrace();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    public List<User> getAllUsers() {
        return getAllUsers("username ascending NULLS LAST");
    }

    public List<User> getAllUsers(String ordering) {
        Collection c;
        ArrayList<User> list = new ArrayList<User>();
        // System.out.println("Shepherd.getAllUsers() called in context "+getContext());
        Extent userClass = pm.getExtent(User.class, true);
        Query users = pm.newQuery(userClass);

        if (ordering != null) {
            users.setOrdering(ordering);
        }
        try {
            c = (Collection)(users.execute());
            if (c != null) {
                list = new ArrayList<User>(c);
            }
            users.closeAll();
            // System.out.println("Shepherd.getAllUsers() found "+list.size()+" users");
            return list;
        } catch (Exception npe) {
            // System.out.println("Error encountered when trying to execute Shepherd.getAllUsers. Returning a null collection because I didn't have a
            // transaction to use.");
            npe.printStackTrace();
            users.closeAll();
            return null;
        }
    }

    public String getAllUserEmailAddressesForLocationID(String locationID, String context) {
        String addresses = "";
        List<User> users = getUsersWithEmailAddresses();
        int numUsers = users.size();

        for (int i = 0; i < numUsers; i++) {
            User user = users.get(i);
            if (locationID != null && user.getUsername() != null &&
                doesUserHaveRole(user.getUsername(), locationID.trim(), context)) {
                if ((user.getReceiveEmails()) && (user.getEmailAddress() != null)) {
                    addresses += (user.getEmailAddress() + ",");
                }
            }
        }
        return addresses;
    }

    // you probably dont like the above one, so:
    public Set<String> getAllUserEmailAddressesForLocationIDAsSet(String locationID,
        String context) {
        Set<String> emails = new HashSet<String>();

        if (Util.stringIsEmptyOrNull(locationID)) return emails;
        for (User user : getUsersWithEmailAddressesWhoReceiveEmails()) {
            if (doesUserHaveRole(user.getUsername(), locationID, context))
                emails.add(user.getEmailAddress());
        }
        return emails;
    }

    public Iterator getAllOccurrences() {
        Extent allOccurs = null;
        Iterator it = null;

        try {
            allOccurs = pm.getExtent(Occurrence.class, true);
            Query acceptedOccurs = pm.newQuery(allOccurs);
            Collection c = (Collection)(acceptedOccurs.execute());
            ArrayList al = new ArrayList(c);
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
            Collection c = (Collection)(acceptedKeywords.execute());
            it = new ArrayList<Role>(c);
            acceptedKeywords.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return it;
        }
        return it;
    }

    public List<Keyword> getAllKeywordsList() {
        Extent allOccurs = null;
        ArrayList<Keyword> al = new ArrayList<Keyword>();

        try {
            allOccurs = pm.getExtent(Keyword.class, true);
            Query acceptedOccurs = pm.newQuery(allOccurs);
            Collection c = (Collection)(acceptedOccurs.execute());
            al = new ArrayList<Keyword>(c);
            acceptedOccurs.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return null;
        }
        return al;
    }

    public Iterator<Keyword> getAllKeywords() {
        Extent allOccurs = null;
        Iterator<Keyword> it = null;

        try {
            allOccurs = pm.getExtent(Keyword.class, true);
            Query acceptedOccurs = pm.newQuery(allOccurs);
            Collection<Keyword> c = (Collection)(acceptedOccurs.execute());
            Set<Keyword> keywords = new TreeSet<>(c);
            acceptedOccurs.closeAll();
            it = keywords.iterator();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return null;
        }
        return it;
    }

    public List<String> getAllKeywordLabels() {
        Set<String> labels = new HashSet<String>();

        for (LabeledKeyword lkw : getAllLabeledKeywords()) {
            labels.add(lkw.getLabel());
        }
        return Util.asSortedList(labels);
    }

    public List<LabeledKeyword> getAllLabeledKeywords() {
        try {
            Extent extent = pm.getExtent(LabeledKeyword.class, true);
            Query query = pm.newQuery(extent);
            Collection c = (Collection)(query.execute());
            List<LabeledKeyword> ans = new ArrayList(c);
            query.closeAll();
            return ans;
        } catch (Exception npe) {
            System.out.println(
                "Error encountered when trying to execute getAllEncountersNoQuery. Returning empty array.");
            npe.printStackTrace();
            // prevents npe's on search pages, counting methods
            return new ArrayList<LabeledKeyword>();
        }
    }

    // allows keywords to be defined in properties file and appear at the top
    // of the list of all keywords
    public List<Keyword> getSortedKeywordList() {
        List<Keyword> allKeywords = getAllKeywordsNoLabeledKeywords();
        List<String> propKeywordNames = CommonConfiguration.getIndexedPropertyValues("keyword",
            getContext());
        List<Keyword> propKeywords = new ArrayList<Keyword>();

        if ((allKeywords != null) && (propKeywordNames != null)) {
            // System.out.println("getSortedKeywordList got propKeywordNames: "+propKeywordNames);
            for (String propKwName : propKeywordNames) {
                for (Keyword kw : allKeywords) {
                    if ((kw.getReadableName() != null) && kw.getReadableName().equals(propKwName)) {
                        propKeywords.add(kw);
                        break;
                    }
                }
            }
            // System.out.println("getSortedKeywordList got "+propKeywords.size()+" keywords.");
            allKeywords.removeAll(propKeywords); // allKeywords = keywords not in props
            propKeywords.addAll(allKeywords);
            // propKeywords contains all keywords, but those defined in properties are first.
        }
        return propKeywords;
    }

    public List<Keyword> getAllKeywordsNoLabeledKeywords() {
        // we find all keywords in the database and note which ones
        // are also listed in the properties file
        ArrayList<Keyword> al = new ArrayList<Keyword>();
        List<Keyword> finalList = new ArrayList<>();

        try {
            Extent allOccurs = pm.getExtent(Keyword.class, true);
            Query acceptedKeywords = pm.newQuery(allOccurs);
            acceptedKeywords.setOrdering("readableName descending");
            Collection c = (Collection)(acceptedKeywords.execute());
            if (c != null) al = new ArrayList<Keyword>(c);
            acceptedKeywords.closeAll();
            List<LabeledKeyword> lkeywords = getAllLabeledKeywords();
            for (Keyword k : al) {
                boolean isLk = false;
                for (Keyword lk : lkeywords) {
                    if (k.getReadableName().equals(lk.getReadableName())) {
                        isLk = true;
                        break;
                    }
                }
                if (!isLk) {
                    finalList.add(k);
                }
            }
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return null;
        }
        return finalList;
    }

    public Set<Keyword> getAllKeywordsSet() {
        Extent extent = pm.getExtent(Keyword.class, true);
        Query acceptedKeywords = pm.newQuery(extent);
        HashSet<Keyword> al = null;

        System.out.println("I started getAllKeywordsSet.");
        try {
            acceptedKeywords.setOrdering("readableName descending");
            Collection c = (Collection)(acceptedKeywords.execute());
            al = new HashSet<Keyword>(c);
            acceptedKeywords.closeAll();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            return null;
        } finally {
            acceptedKeywords.closeAll();
        }
        System.out.println("got a set of size " + (al != null ? al.size() : "ERROR"));
        return al;
    }

    public int getNumKeywords() {
        Extent allWords = null;
        int num = 0;
        Query q = pm.newQuery(Keyword.class); // no filter, so all instances match

        try {
            Collection results = (Collection)q.execute();
            num = results.size();
        } catch (javax.jdo.JDOException x) {
            x.printStackTrace();
            q.closeAll();
            return num;
        }
        q.closeAll();
        return num;
    }

    public List<SinglePhotoVideo> getMarkedIndividualThumbnails(HttpServletRequest request,
        Iterator<MarkedIndividual> it, int startNum, int endNum, String[] keywords) {
        ArrayList<SinglePhotoVideo> thumbs = new ArrayList<SinglePhotoVideo>();
        boolean stopMe = false;
        int count = 0;

        while (it.hasNext() && !stopMe) {
            MarkedIndividual markie = it.next();
            Iterator allEncs = markie.getEncounters().iterator();
            while (allEncs.hasNext() && !stopMe) {
                Encounter enc = (Encounter)allEncs.next();
                List<SinglePhotoVideo> images = getAllSinglePhotoVideosForEncounter(
                    enc.getCatalogNumber());
                if ((count + images.size()) >= startNum) {
                    for (int i = 0; i < images.size(); i++) {
                        count++;
                        if ((count <= endNum) && (count >= startNum)) {
                            String m_thumb = "";

                            // check for video or image
                            String imageName = (String)images.get(i).getFilename();

                            // check if this image has one of the assigned keywords
                            boolean hasKeyword = false;
                            if ((keywords == null) || (keywords.length == 0)) {
                                hasKeyword = true;
                            } else {
                                int numKeywords = keywords.length;
                                for (int n = 0; n < numKeywords; n++) {
                                    if (!keywords[n].equals("None")) {
                                        Keyword word = getKeyword(keywords[n]);
                                        if ((images.get(i).getKeywords() != null) &&
                                            images.get(i).getKeywords().contains(word)) {
                                            hasKeyword = true;
                                        }
                                    } else {
                                        hasKeyword = true;
                                    }
                                }
                            }
                            // check for specific filename conditions here
                            if ((request.getParameter("filenameField") != null) &&
                                (!request.getParameter("filenameField").equals(""))) {
                                String nameString = ServletUtilities.cleanFileName(
                                    ServletUtilities.preventCrossSiteScriptingAttacks(
                                    request.getParameter("filenameField").trim()));
                                if (!nameString.equals(imageName)) { hasKeyword = false; }
                            }
                            if (hasKeyword && isAcceptableVideoFile(imageName) &&
                                !thumbs.contains(images.get(i))) {
                                m_thumb = request.getScheme() + "://" +
                                    CommonConfiguration.getURLLocation(request) +
                                    "/images/video.jpg" + "BREAK" + enc.getEncounterNumber() +
                                    "BREAK" + imageName;
                                // thumbs.add(m_thumb);
                                thumbs.add(images.get(i));
                            } else if (hasKeyword && isAcceptableImageFile(imageName) &&
                                !thumbs.contains(images.get(i))) {
                                m_thumb = enc.getEncounterNumber() + "/" + (i + 1) + ".jpg" +
                                    "BREAK" + enc.getEncounterNumber() + "BREAK" + imageName;
                                // thumbs.add(m_thumb);
                                thumbs.add(images.get(i));
                            } else {
                                count--;
                            }
                        } else if (count > endNum) {
                            stopMe = true;
                            return thumbs;
                        }
                    }
                } // end if
                else {
                    count += images.size();
                }
            } // end while
        } // end while
        return thumbs;
    }

    public int getNumThumbnails(Iterator it, String[] keywords) {
        // Vector thumbs=new Vector();
        // boolean stopMe=false;
        int count = 0;

        while (it.hasNext()) {
            Encounter enc = (Encounter)it.next();
            for (int i = 0; i < enc.getAdditionalImageNames().size(); i++) {
                count++;
                // String m_thumb="";

                // check for video or image
                String imageName = (String)enc.getAdditionalImageNames().get(i);

                // check if this image has one of the assigned keywords
                boolean hasKeyword = false;
                if ((keywords == null) || (keywords.length == 0)) {
                    hasKeyword = true;
                } else {
                    int numKeywords = keywords.length;
                    for (int n = 0; n < numKeywords; n++) {
                        if (!keywords[n].equals("None")) {
                            Keyword word = getKeyword(keywords[n]);
                            // if (word.isMemberOf(enc.getCatalogNumber() + "/" + imageName)) {
                            if (enc.hasKeyword(word)) {
                                hasKeyword = true;
                                // System.out.println("member of: "+word.getReadableName());
                            }
                        } else {
                            hasKeyword = true;
                        }
                    }
                }
                if (hasKeyword && isAcceptableVideoFile(imageName)) {} else if (hasKeyword &&
                    isAcceptableImageFile(imageName)) {} else {
                    count--;
                }
            }
        } // end while
        return count;
    }

    public int getNumMarkedIndividualThumbnails(Iterator<MarkedIndividual> it, String[] keywords) {
        int count = 0;

        while (it.hasNext()) {
            MarkedIndividual indie = it.next();
            Iterator allEncs = indie.getEncounters().iterator();
            while (allEncs.hasNext()) {
                Encounter enc = (Encounter)allEncs.next();
                for (int i = 0; i < enc.getAdditionalImageNames().size(); i++) {
                    count++;
                    // String m_thumb="";

                    // check for video or image
                    String imageName = (String)enc.getAdditionalImageNames().get(i);

                    // check if this image has one of the assigned keywords
                    boolean hasKeyword = false;
                    if ((keywords == null) || (keywords.length == 0)) {
                        hasKeyword = true;
                    } else {
                        int numKeywords = keywords.length;
                        for (int n = 0; n < numKeywords; n++) {
                            if (!keywords[n].equals("None")) {
                                Keyword word = getKeyword(keywords[n]);
                                // if (word.isMemberOf(enc.getCatalogNumber() + "/" + imageName)) {
                                if (enc.hasKeyword(word)) {
                                    hasKeyword = true;
                                    // System.out.println("member of: "+word.getReadableName());
                                }
                            } else {
                                hasKeyword = true;
                            }
                        }
                    }
                    if (hasKeyword && isAcceptableVideoFile(imageName)) {
                        // m_thumb="http://"+CommonConfiguration.getURLLocation()+"/images/video.jpg"+"BREAK"+enc.getEncounterNumber()+"BREAK"+imageName;
                        // thumbs.add(m_thumb);
                    } else if (hasKeyword && isAcceptableImageFile(imageName)) {
                        // m_thumb=enc.getEncounterNumber()+"/"+(i+1)+".jpg"+"BREAK"+enc.getEncounterNumber()+"BREAK"+imageName;
                        // thumbs.add(m_thumb);
                    } else {
                        count--;
                    }
                }
            } // end while
        } // end while
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
            Encounter enc = (Encounter)it.get(f);
            for (int i = 0; i < enc.getAdditionalImageNames().size(); i++) {
                count++;

                // check for video or image
                String addTextFile = (String)enc.getAdditionalImageNames().get(i);
                if ((!isAcceptableImageFile(addTextFile)) &&
                    (!isAcceptableVideoFile(addTextFile))) {
                    count--;
                }
            }
        } // end while
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

    public List<Encounter> getEncountersByField(String fieldName, String fieldVal) {
        String filter = "this." + fieldName + " == \"" + fieldVal + "\"";
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        ArrayList al = new ArrayList(c);

        acceptedEncounters.closeAll();
        return al;
    }

    public Encounter getEncounterByIndividualAndOccurrence(String indID, String occID) {
        List<Encounter> encs = getEncountersByIndividualAndOccurrence(indID, occID);

        if (encs.size() > 0) return encs.get(0);
        return null;
    }

    public List<Encounter> getEncountersByIndividualAndOccurrence(String indID, String occID) {
        ArrayList al = new ArrayList();

        if (!Util.stringExists(indID) || !Util.stringExists(occID)) return al;
        String filter = "this.individual.individualID == \"" + indID +
            "\" && this.occurrenceID == \"" + occID + "\"";
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query acceptedEncounters = pm.newQuery(encClass, filter);
        Collection c = (Collection)(acceptedEncounters.execute());
        al = new ArrayList(c);

        acceptedEncounters.closeAll();
        return al;
    }

    // get earliest sighting year for setting search parameters
    public int getEarliestSightingYear() {
        try {
            Query q = pm.newQuery("SELECT min(year) FROM org.ecocean.Encounter where year > 0");
            int value = ((Integer)q.execute()).intValue();
            q.closeAll();
            return value;
        } catch (Exception e) { return -1; }
    }

    public int getFirstSubmissionYear() {
        // System.out.println("Starting getFirstSubmissionYear");
        try {
            Query q = pm.newQuery("SELECT min(dwcDateAddedLong) FROM org.ecocean.Encounter");
            // System.out.println("     I have a query");
            long value = ((Long)q.execute()).longValue();
            // System.out.println("     I have a value of: "+value);
            q.closeAll();
            org.joda.time.DateTime lcd = new org.joda.time.DateTime(value);
            return lcd.getYear();
            // return value;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Why can't I find a min dwcDateAddedLong?");
            return -1;
        }
    }

    public int getLastSightingYear() {
        try {
            Query q = pm.newQuery("SELECT max(year) FROM org.ecocean.Encounter");
            int value = ((Integer)q.execute()).intValue();
            q.closeAll();
            return value;
        } catch (Exception e) { return -1; }
    }

    public List<String> getAllLocationIDs() {
        Query q = pm.newQuery(Encounter.class);

        q.setResult("distinct locationID");
        q.setOrdering("locationID ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public List<String> getAllCountries() {
        Query q = pm.newQuery(Encounter.class);

        q.setResult("distinct country");
        q.setOrdering("country ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public List<String> getAllHaplotypes() {
        Query q = pm.newQuery(MitochondrialDNAAnalysis.class);

        q.setResult("distinct haplotype");
        q.setOrdering("haplotype ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public List<String> getAllDistinctHaplotypesForEncounterQuery(String filter) {
        // System.out.println("Filter is:\n"+filter);

        ArrayList<String> al = new ArrayList<String>();

        // check for VARIABLES
        if (filter.indexOf("VARIABLES") > -1) {
            // can't get subqueries with VARIABLES running in JDOQL
            // do this the easy slow way
            Query q = pm.newQuery(filter.replaceFirst(" VARIABLES",
                " && tissueSamples != null VARIABLES"));
            Collection results = (Collection)q.execute();
            ArrayList<Encounter> encs = new ArrayList<Encounter>(results);
            q.closeAll();
            for (Encounter enc : encs) {
                if (enc.getHaplotype() != null && !al.contains(enc.getHaplotype())) {
                    al.add(enc.getHaplotype());
                }
            }
            // now sort them.
            al.sort(String::compareToIgnoreCase);
        } else {
            String subfilter = "SELECT FROM org.ecocean.genetics.MitochondrialDNAAnalysis WHERE (" +
                filter.replaceFirst("SELECT FROM org.ecocean.Encounter",
                "SELECT distinct catalogNumber FROM org.ecocean.Encounter") +
                ").contains(this.correspondingEncounterNumber)";
            // System.out.println("subFilter is:\n"+subfilter);
            Query q = pm.newQuery(subfilter);
            q.setResult("distinct haplotype");
            q.setOrdering("haplotype ascending");
            Collection results = (Collection)q.execute();
            al = new ArrayList(results);
            q.closeAll();
        }
        return al;
    }

    public List<String> getAllDistinctHaplotypesForMarkedIndividualQuery(String filter) {
        ArrayList<String> al = new ArrayList<String>();

        if (filter.indexOf("VARIABLES") > -1) {
            // can't get subqueries with VARIABLES running in JDOQL
            // do this the easy slow way
            Query q = pm.newQuery(filter);
            Collection results = (Collection)q.execute();
            ArrayList<MarkedIndividual> indies = new ArrayList<MarkedIndividual>(results);
            q.closeAll();
            for (MarkedIndividual indy : indies) {
                if (indy.getHaplotype() != null && !al.contains(indy.getHaplotype())) {
                    al.add(indy.getHaplotype());
                }
            }
            // now sort them.
            al.sort(String::compareToIgnoreCase);
        } else {
            String subfilter =
                "SELECT distinct analysis1.haplotype FROM org.ecocean.Encounter WHERE (" +
                filter.replaceFirst("SELECT FROM org.ecocean.MarkedIndividual",
                "SELECT distinct individualID FROM org.ecocean.MarkedIndividual") +
                ").contains(individual.individualID) && tissueSamples.contains(sample1) && sample1.analyses.contains(analysis1) VARIABLES org.ecocean.genetics.TissueSample sample1;org.ecocean.genetics.MitochondrialDNAAnalysis analysis1 ORDER BY analysis1.haplotype";
            System.out.println("Filter is:\n" + filter);
            System.out.println("subFilter is:\n" + subfilter);
            Query q = pm.newQuery(subfilter);
            Collection results = (Collection)q.execute();
            al = new ArrayList(results);
            q.closeAll();
        }
        return al;
    }

    public ArrayList<String> getAllUsernames() {
        Query q = pm.newQuery(User.class);

        q.setResult("distinct username");
        q.setOrdering("username ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public List<String> getAllNativeUsernames() {
        String filter = "SELECT FROM org.ecocean.User WHERE username != null ";

        // bc of how sql's startsWith method works we need the null check below
        filter += "&& (fullName == null || !fullName.startsWith('Conserve.IO User '))";
        Query query = getPM().newQuery(filter);
        query.setResult("distinct username");
        query.setOrdering("username ascending");
        Collection c = (Collection)(query.execute());
        ArrayList usernames = new ArrayList(c);
        query.closeAll();
        return usernames;
    }

    public List<String> getAllGeneticSexes() {
        Query q = pm.newQuery(SexAnalysis.class);

        q.setResult("distinct sex");
        q.setOrdering("sex ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public List<String> getAllLoci() {
        Query q = pm.newQuery(Locus.class);

        q.setResult("distinct name");
        q.setOrdering("name ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public ArrayList<String> getAllSocialUnitNames() {
        ArrayList<String> comNames = new ArrayList<String>();
        Query q = pm.newQuery(SocialUnit.class);

        try {
            q.setResult("distinct socialUnitName");
            q.setOrdering("socialUnitName ascending");
            Collection results = (Collection)q.execute();
            comNames = new ArrayList<String>(results);
        } catch (Exception e) {}
        q.closeAll();
        return comNames;
    }

    public List<String> getAllStrVals(Class fromClass, String fieldName) {
        Query q = pm.newQuery(fromClass);

        q.setResult("distinct " + fieldName);
        q.setOrdering(fieldName + " ascending");
        Collection results = (Collection)q.execute();
        List resList = new ArrayList(results);
        q.closeAll();
        return resList;
    }

    // gets properties vals, then all actual vals, and returns the combined list without repeats
    public List<String> getAllPossibleVals(Class fromClass, String fieldName, Properties props) {
        List<String> indexVals = Util.getIndexedPropertyValues(fieldName, props);
        List<String> usedVals = getAllStrVals(fromClass, fieldName);

        for (String usedVal : usedVals) {
            if (!indexVals.contains(usedVal)) indexVals.add(usedVal);
        }
        return indexVals;
    }

    public List<String> getAllBehaviors() {
        System.out.println("getAllBehaviors!");
        List<String> behaves = getDefinedBehaviors();
        System.out.println("done with getDefinedBehaviors!");
        behaves.addAll(getDBBehaviors());
        return behaves;
    }

    public List<String> getDefinedBehaviors() {
        System.out.println("getDefinedBehaviors!");
        return CommonConfiguration.getIndexedPropertyValues("behavior", this.getContext());
    }

    public List<String> getDBBehaviors() {
        System.out.println("getDBBehaviors!");
        Query q = pm.newQuery(Encounter.class);
        q.setResult("distinct behavior");
        q.setOrdering("behavior ascending");
        Collection results = (Collection)q.execute();
        List al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    // how many more behavior-related lists can we make?
    public Map<String, List<String> > getTaxonomicBehaviors() {
        Map<String, List<String> > rtn = new HashMap<String, List<String> >();

        // empty key is behaviors with no taxonomy
        rtn.put("", getDefinedBehaviors());
        // iaClassesForTaxonomy seems to key off taxonomies with spaces, so....
        for (String sciName : getAllTaxonomyCommonNames(true).get(0)) {
            // in CommonConfiguration.properties, key is like: Foo.bar.bar2.behavior0
            String prefix = sciName.replaceAll(" ", ".") + ".behavior";
            List<String> behaviors = CommonConfiguration.getIndexedPropertyValues(prefix,
                this.getContext());
            if (Util.collectionSize(behaviors) > 0) rtn.put(sciName, behaviors);
        }
        return rtn;
    }

    public List<String> getAllVerbatimEventDates() {
        Query q = pm.newQuery(Encounter.class);

        q.setResult("distinct verbatimEventDate");
        q.setOrdering("verbatimEventDate ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public Iterator<Survey> getAllSurveys() {
        Extent svyClass = pm.getExtent(Survey.class, true);
        Iterator svsIt = svyClass.iterator();

        return svsIt;
    }

    public List<String> getAllPatterningCodes() {
        Query q = pm.newQuery(Encounter.class);

        q.setResult("distinct patterningCode");
        q.setOrdering("patterningCode ascending");
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);
        q.closeAll();
        return al;
    }

    public ArrayList<Relationship> getAllRelationshipsForMarkedIndividual(String indieName) {
        Extent encClass = pm.getExtent(Relationship.class, true);
        String filter2use = "this.markedIndividualName1 == \"" + indieName +
            "\" || this.markedIndividualName2 == \"" + indieName + "\"";
        Query query = pm.newQuery(encClass, filter2use);
        Collection c = (Collection)(query.execute());
        // System.out.println("Num relationships for MarkedIndividual "+indieName+": "+c.size());
        ArrayList<Relationship> listy = new ArrayList<Relationship>(c);

        query.closeAll();
        return listy;
    }

    public ArrayList<String> getAllSocialUnitsForMarkedIndividual(String indieName) {
        String filter2use =
            "SELECT FROM org.ecocean.social.SocialUnit WHERE members.contains(member) && member.mi.individualID == '"
            + indieName + "' VARIABLES org.ecocean.social.Membership member";
        Query query = pm.newQuery(filter2use);

        query.setResult("distinct socialUnitName");
        Collection c = (Collection)(query.execute());
        // System.out.println("Num relationships for MarkedIndividual "+indieName+": "+c.size());
        ArrayList<String> listy = new ArrayList<String>();
        if (c != null) listy = new ArrayList<String>(c);
        query.closeAll();
        return listy;
    }

    public ArrayList<String> getAllRoleNamesForMarkedIndividual(String indieName) {
        ArrayList<String> roles = new ArrayList<String>();
        ArrayList<Relationship> rels = getAllRelationshipsForMarkedIndividual(indieName);
        int numRels = rels.size();

        for (int i = 0; i < numRels; i++) {
            Relationship rel = rels.get(i);
            if ((rel.getMarkedIndividualName1().equals(indieName)) &&
                (rel.getMarkedIndividualRole1() != null) &&
                (!roles.contains(rel.getMarkedIndividualRole1()))) {
                roles.add(rel.getMarkedIndividualRole1());
            }
            if ((rel.getMarkedIndividualName2().equals(indieName)) &&
                (rel.getMarkedIndividualRole2() != null) &&
                (!roles.contains(rel.getMarkedIndividualRole2()))) {
                roles.add(rel.getMarkedIndividualRole2());
            }
        }
        return roles;
    }

    public int getNumCooccurrencesBetweenTwoMarkedIndividual(String individualID1,
        String individualID2) {
        int numCooccur = 0;
        ArrayList<String> occurenceIDs1 = getOccurrenceIDsForMarkedIndividual(individualID1);
        // System.out.println("zzzOccurrences for indie "+individualID1+": "+occurenceIDs1.toString());
        List<String> occurenceIDs2 = getOccurrenceIDsForMarkedIndividual(individualID2);
        // System.out.println("zzzOccurrences for indie "+individualID2+": "+occurenceIDs2.toString());
        int numOccurenceIDs1 = occurenceIDs1.size();

        if ((numOccurenceIDs1 > 0) && (occurenceIDs2.size() > 0)) {
            // System.out.println(numOccurenceIDs1+":"+occurenceIDs2.size());
            for (int i = 0; i < numOccurenceIDs1; i++) {
                if (occurenceIDs2.contains(occurenceIDs1.get(i))) {
                    // System.out.println("Checking occurrence: "+occurenceIDs1.get(i));
                    numCooccur++;
                    // System.out.println("zzzMatching co-occurrence: "+occurenceIDs1.get(i));
                }
            }
        }
        return numCooccur;
    }

    public ArrayList<String> getOccurrenceIDsForMarkedIndividual(String individualID) {
        ArrayList<String> occurrenceIDs = new ArrayList<String>();
        String filter =
            "SELECT distinct occurrenceID FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individualID == \""
            + individualID + "\"  VARIABLES org.ecocean.Encounter enc";
        Query q = pm.newQuery(filter);
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);

        q.closeAll();
        int numResults = al.size();
        for (int i = 0; i < numResults; i++) {
            occurrenceIDs.add((String)al.get(i));
        }
        // System.out.println("zzzOccurrences for "+individualID+": "+occurrenceIDs.toString());
        return occurrenceIDs;
    }

    public ArrayList<String> getLinkedLocationIDs(String locationID) {
        ArrayList<String> locationIDs = new ArrayList<String>();
        String filter =
            "SELECT distinct enc2.locationID FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && encounters.contains(enc2) && enc.locationID == \""
            + locationID + "\" && enc2.locationID != null && enc2.locationID != \"" + locationID +
            "\"  VARIABLES org.ecocean.Encounter enc;org.ecocean.Encounter enc2";
        Query q = pm.newQuery(filter);
        Collection results = (Collection)q.execute();
        ArrayList al = new ArrayList(results);

        q.closeAll();
        int numResults = al.size();
        for (int i = 0; i < numResults; i++) {
            locationIDs.add((String)al.get(i));
        }
        return locationIDs;
    }

    public Measurement getMeasurementOfTypeForEncounter(String type, String encNum) {
        String filter = "type == \"" + type + "\" && correspondingEncounterNumber == \"" + encNum +
            "\"";
        Extent encClass = pm.getExtent(Measurement.class, true);
        Query samples = pm.newQuery(encClass, filter);
        Collection c = (Collection)(samples.execute());

        if ((c != null) && (c.size() > 0)) {
            ArrayList<Measurement> al = new ArrayList<Measurement>(c);
            samples.closeAll();
            return (al).get(0);
        } else { return null; }
    }

    public ArrayList<ScanTask> getAllScanTasksForUser(String user) {
        String filter = "submitter == \"" + user + "\"";
        Extent encClass = pm.getExtent(ScanTask.class, true);
        Query samples = pm.newQuery(encClass, filter);
        Collection c = (Collection)(samples.execute());

        if (c != null) {
            ArrayList<ScanTask> it = new ArrayList<ScanTask>(c);
            samples.closeAll();
            return it;
        } else { samples.closeAll(); return null; }
    }

    public User getRandomUserWithPhotoAndStatement() {
        String filter =
            "fullName != null && userImage != null && userStatement != null && (username.toLowerCase().indexOf('demo') == -1) && (username.toLowerCase().indexOf('test') == -1)";
        Extent encClass = pm.getExtent(User.class, true);
        Query q = pm.newQuery(encClass, filter);
        Collection c = (Collection)(q.execute());

        if ((c != null) && (c.size() > 0)) {
            ArrayList<User> matchingUsers = new ArrayList<>(c);
            q.closeAll();
            int numUsers = matchingUsers.size();
            Random rn = new Random();
            int userNumber = rn.nextInt(numUsers);
            return matchingUsers.get(userNumber);
        }
        q.closeAll();
        return null;
    }

    public ArrayList<Encounter> getMostRecentIdentifiedEncountersByDate(int numToReturn) {
        ArrayList<Encounter> matchingEncounters = new ArrayList<Encounter>();
        String filter = "individual != null";
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query q = pm.newQuery(encClass, filter);

        q.setRange(0, numToReturn + 1);
        q.setOrdering("year descending, month descending, day descending");
        Collection c = (Collection)(q.execute());
        if ((c != null) && (c.size() > 0)) {
            int max = (numToReturn > c.size()) ? c.size() : numToReturn;
            int numAdded = 0;
            while (numAdded < max) {
                ArrayList<Encounter> results = new ArrayList<Encounter>(c);
                matchingEncounters.add(results.get(numAdded));
                numAdded++;
            }
        }
        q.closeAll();
        return matchingEncounters;
    }

    public Map<String,
        Integer> getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(long startTime) {
        Map<String, Integer> matchingUsers = new HashMap<String, Integer>();
        String filter = "submitterID != null && dwcDateAddedLong >= " + startTime;
        // System.out.println("     My filter is: "+filter);
        Extent encClass = pm.getExtent(Encounter.class, true);
        Query q = pm.newQuery(encClass, filter);

        q.setResult("distinct submitterID");
        Collection c = (Collection)(q.execute());
        ArrayList<String> allUsers = new ArrayList<String>(c);
        q.closeAll();

        int numAllUsers = allUsers.size();
        // System.out.println("     All users: "+numAllUsers);
        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
        for (int i = 0; i < numAllUsers; i++) {
            String thisUser = allUsers.get(i);
            if ((!thisUser.trim().equals("")) && (getUser(thisUser) != null)) {
                if (qc.getQueryByName(("numRecentEncounters_" + thisUser)) != null) {
                    CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + thisUser));
                    matchingUsers.put(thisUser, (cq.executeCountQuery(this)));
                } else {
                    String userFilter =
                        "SELECT FROM org.ecocean.Encounter WHERE submitterID == \"" + thisUser +
                        "\" && dwcDateAddedLong >= " + startTime;
                    // update rankings hourly
                    CachedQuery cq = new CachedQuery(("numRecentEncounters_" + thisUser),
                        userFilter, 3600000);
                    qc.addCachedQuery(cq);
                    matchingUsers.put(thisUser, (cq.executeCountQuery(this)));
                }
            }
        }
        return sortByValues(matchingUsers);
    }

    public Map<String, Integer> getTopSubmittersSinceTimeInDescendingOrder(long startTime,
        List<String> ignoreTheseUsernames) {
        System.out.println("getTopSubmittersSinceTimeInDescendingOrder...start");

        Map<String, Integer> matchingUsers = new HashMap<String, Integer>();
        String filter =
            "select from org.ecocean.User where fullName != null && enc.dwcDateAddedLong >= " +
            startTime + " && enc.submitters.contains(this) VARIABLES org.ecocean.Encounter enc";
        Query q = pm.newQuery(filter);
        Collection c = (Collection)(q.execute());
        ArrayList<User> allUsers = new ArrayList<User>(c);
        q.closeAll();

        // System.out.println("     All users: "+numAllUsers);
        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
        for (User user : allUsers) {
            // skip if this is on our ignore list
            if (user.getUsername() != null && !user.getUsername().trim().equals("") &&
                ignoreTheseUsernames.contains(user.getUsername())) { continue; }
            if (qc.getQueryByName(("numRecentEncounters_" + user.getUUID())) != null) {
                CachedQuery cq = qc.getQueryByName(("numRecentEncounters_" + user.getUUID()));
                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
                System.out.println("found " + "numRecentEncounters_" + user.getUUID() + "_" +
                    cq.executeCountQuery(this));
            } else {
                String userFilter = "SELECT FROM org.ecocean.Encounter WHERE dwcDateAddedLong >= " +
                    startTime + " && submitters.contains(user) && user.uuid == '" + user.getUUID() +
                    "' VARIABLES org.ecocean.User user";
                // update rankings hourly
                CachedQuery cq = new CachedQuery(("numRecentEncounters_" + user.getUUID()),
                    userFilter, 3600000);
                qc.addCachedQuery(cq);
                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
                System.out.println("not found " + "numRecentEncounters_" + user.getUUID() + "_" +
                    cq.executeCountQuery(this));
            }
        }
        System.out.println("getTopSubmittersSinceTimeInDescendingOrder...end");
        return sortByValues(matchingUsers);
    }

    public Map<String, Integer> getTopPhotographersSinceTimeInDescendingOrder(long startTime,
        List<String> ignoreTheseUsernames) {
        System.out.println("getTopPhotographersSinceTimeInDescendingOrder...start");

        Map<String, Integer> matchingUsers = new HashMap<String, Integer>();
        String filter =
            "select from org.ecocean.User where fullName != null && enc.dwcDateAddedLong >= " +
            startTime + " && enc.photographers.contains(this) VARIABLES org.ecocean.Encounter enc";
        Query q = pm.newQuery(filter);
        Collection c = (Collection)(q.execute());
        ArrayList<User> allUsers = new ArrayList<User>(c);
        q.closeAll();

        // System.out.println("     All users: "+numAllUsers);
        QueryCache qc = QueryCacheFactory.getQueryCache(getContext());
        for (User user : allUsers) {
            // skip if this is on our ignore list
            if (user.getUsername() != null && !user.getUsername().trim().equals("") &&
                ignoreTheseUsernames.contains(user.getUsername())) { continue; }
            if (qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID())) != null) {
                CachedQuery cq = qc.getQueryByName(("numRecentPhotoEncounters_" + user.getUUID()));
                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
                System.out.println("found " + "numRecentPhotoEncounters_" + user.getUUID() + "_" +
                    cq.executeCountQuery(this));
            } else {
                String userFilter = "SELECT FROM org.ecocean.Encounter WHERE dwcDateAddedLong >= " +
                    startTime + " && photographers.contains(user) && user.uuid == '" +
                    user.getUUID() + "' VARIABLES org.ecocean.User user";
                // update rankings hourly
                CachedQuery cq = new CachedQuery(("numRecentPhotoEncounters_" + user.getUUID()),
                    userFilter, 3600000);
                qc.addCachedQuery(cq);
                matchingUsers.put(user.getUUID(), (cq.executeCountQuery(this)));
                System.out.println("not found " + "numRecentPhotoEncounters_" + user.getUUID() +
                    "_" + cq.executeCountQuery(this));
            }
        }
        System.out.println("getTopPhotographersSinceTimeInDescendingOrder...end");
        return sortByValues(matchingUsers);
    }

    public static <K, V extends Comparable<V> > Map<K, V> sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator = new Comparator<K>() {
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

    public int getNumAnnotationsForEncounter(String encounterID) {
        ArrayList<Annotation> al = getAnnotationsForEncounter(encounterID);

        return al.size();
    }

    public ArrayList<Annotation> getAnnotationsForEncounter(String encounterID) {
        String filter = "SELECT FROM org.ecocean.Annotation WHERE enc.catalogNumber == \"" +
            encounterID +
            "\" && enc.annotations.contains(this)  VARIABLES org.ecocean.Encounter enc";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        ArrayList<Annotation> al = new ArrayList<Annotation>(c);

        query.closeAll();
        return al;
    }

    public ArrayList<Annotation> getAnnotationsWithACMId(String acmId) {
        return getAnnotationsWithACMId(acmId, false);
    }

    public ArrayList<Annotation> getAnnotationsWithACMId(String acmId,
        boolean enforceEncounterAssociation) {
        String filter = "select from org.ecocean.Annotation where acmId == \"" + acmId + "\"";

        if (enforceEncounterAssociation)
            filter = "select from org.ecocean.Annotation where acmId == \"" + acmId +
                "\" && enc.annotations.contains(this) VARIABLES org.ecocean.Encounter enc";
        Query anns = pm.newQuery(filter);
        Collection c = (Collection)(anns.execute());
        ArrayList<Annotation> al = new ArrayList(c);
        anns.closeAll();
        if ((al != null) && (al.size() > 0)) {
            return al;
        }
        return null;
    }

    public ArrayList<MediaAsset> getMediaAssetsWithACMId(String acmId) {
        String filter = "this.acmId == \"" + acmId + "\"";
        Extent annClass = pm.getExtent(MediaAsset.class, true);
        Query anns = pm.newQuery(annClass, filter);
        Collection c = (Collection)(anns.execute());
        ArrayList<MediaAsset> al = new ArrayList(c);

        anns.closeAll();
        if ((al != null) && (al.size() > 0)) {
            return al;
        }
        return null;
    }

    // used to describe where this Shepherd is and what it is supposed to be doing
    public void setAction(String newAction) {
        String state = "";

        if (ShepherdState.getShepherdState(action + "_" + shepherdID) != null) {
            state = ShepherdState.getShepherdState(action + "_" + shepherdID);
            ShepherdState.removeShepherdState(action + "_" + shepherdID);
        }
        this.action = newAction;
        ShepherdState.setShepherdState(action + "_" + shepherdID, state);
    }

    public String getAction() { return action; }

    public List<String> getAllEncounterNumbers() {
        List<String> encs = null;
        String filter = "SELECT DISTINCT catalogNumber FROM org.ecocean.Encounter";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());

        encs = new ArrayList<String>(c);
        query.closeAll();
        return encs;
    }

    public List<String> getAllUsernamesWithRoles() {
        List<String> usernames = null;
        String filter = "SELECT DISTINCT username FROM org.ecocean.Role";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());

        usernames = new ArrayList<String>(c);
        query.closeAll();
        return usernames;
    }

    public List<Encounter> getEncountersForSubmitter(User user) {
        return getEncountersForSubmitter(user, null);
    }

    public List<Encounter> getEncountersForSubmitter(User user, String ordering) {
        if (ordering == null) ordering = "dwcDateAddedLong DESC";
        ArrayList<Encounter> users = new ArrayList<Encounter>();
        String filter =
            "SELECT FROM org.ecocean.Encounter WHERE (submitters.contains(user) && user.uuid == \""
            + user.getUUID() + "\") || submitterID == \"" + user.getUsername() +
            "\" VARIABLES org.ecocean.User user";
        Query query = getPM().newQuery(filter);
        query.setOrdering(ordering);
        Collection c = (Collection)(query.execute());
        if (c != null) { users = new ArrayList<Encounter>(c); }
        query.closeAll();
        return users;
    }

    public List<Encounter> getEncountersForPhotographer(User user, Shepherd myShepherd) {
        ArrayList<Encounter> users = new ArrayList<Encounter>();
        String filter =
            "SELECT FROM org.ecocean.Encounter WHERE photographers.contains(user) && user.uuid == \""
            + user.getUUID() + "\" VARIABLES org.ecocean.User user";
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());

        if (c != null) { users = new ArrayList<Encounter>(c); }
        query.closeAll();
        return users;
    }

    public StoredQuery getStoredQuery(String uuid) {
        StoredQuery sq = null;

        try {
            sq = ((StoredQuery)(pm.getObjectById(pm.newObjectIdInstance(StoredQuery.class, uuid),
                true)));
        } catch (Exception nsoe) {
            return null;
        }
        return sq;
    }

    public List<StoredQuery> getAllStoredQueries() {
        Extent encClass = pm.getExtent(StoredQuery.class, true);
        Query queries = pm.newQuery(encClass);
        Collection c = (Collection)(queries.execute());
        ArrayList<StoredQuery> listy = new ArrayList<StoredQuery>(c);

        queries.closeAll();
        return listy;
    }

    public ImportTask getImportTaskForEncounter(String encounterID) {
        String filter =
            "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE encounters.contains(enc) && enc.catalogNumber == \""
            + encounterID + "\"  VARIABLES org.ecocean.Encounter enc";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        while (it.hasNext()) {
            ImportTask task = (ImportTask)it.next();
            query.closeAll();
            return task;
        }
        query.closeAll();
        return null;
    }

    public List<ImportTask> getImportTasksForUser(User user) {
        List<ImportTask> all = new ArrayList<ImportTask>();
        String filter =
            "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE creator.uuid == \"" +
            user.getUUID() + "\"";
        Query query = getPM().newQuery(filter);

        query.setOrdering("created DESC");
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            all.add((ImportTask)it.next());
        }
        query.closeAll();
        return all;
    }

    public List<ImportTask> getImportTasks() {
        List<ImportTask> all = new ArrayList<ImportTask>();
        String filter = "SELECT FROM org.ecocean.servlet.importer.ImportTask";
        Query query = getPM().newQuery(filter);

        query.setOrdering("created DESC");
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            all.add((ImportTask)it.next());
        }
        query.closeAll();
        return all;
    }

    public User getUserByTwitterHandle(String handle) {
        User user = null;
        String filter = "SELECT FROM org.ecocean.User WHERE twitterHandle == \"" + handle.trim() +
            "\"";
        Query query = getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        Iterator it = c.iterator();

        if (it.hasNext()) {
            user = (User)it.next();
        }
        query.closeAll();
        return user;
    }

    public JSONArray getAllProjectACMIdsJSON(String projectId) {
        JSONArray allAnnotIds = new JSONArray();
        String filter =
            "SELECT FROM org.ecocean.Annotation WHERE acmId!=null && enc.annotations.contains(this) && project.id=='"
            + projectId +
            "' && project.encounters.contains(enc) VARIABLES org.ecocean.Encounter enc;org.ecocean.Project project";
        Query q = pm.newQuery(filter);

        q.setResult("distinct acmId");
        Collection results = (Collection)q.execute();
        ArrayList<String> al = new ArrayList<String>(results);
        q.closeAll();
        for (String ann : al) {
            allAnnotIds.put(ann);
        }
        return allAnnotIds;
    }

    public void cacheEvictAll() {
        PersistenceManagerFactory pmf = ShepherdPMF.getPMF(localContext);

        if (pmf != null) pmf.getDataStoreCache().evictAll();
    }
}
