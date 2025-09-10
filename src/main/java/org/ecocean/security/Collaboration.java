package org.ecocean.security;

import java.util.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.*;
import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.social.*;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

/**
 * a Collaboration is a defined, two-way relationship between two Users. It can exist in various states, but once fully approved, generally will allow
 * the referenced two users to have more access to each other's data.
 */
public class Collaboration implements java.io.Serializable {
    private static final long serialVersionUID = -1161710718628733038L;
    // username1 is the initiator
    private String username1;
    // username2 is who was invited to join
    private String username2;
    private long dateTimeCreated;
    private String state;
    private String id;

    public static final String STATE_INITIALIZED = "initialized";
    public static final String STATE_REJECTED = "rejected";
    public static final String STATE_APPROVED = "approved";
    // one step higher than approved is having edit privileges
    public static final String STATE_EDIT_PRIV = "edit";
    public static final String STATE_EDIT_PENDING_PRIV = "edit_pending";

    private String editInitiator;

    // JDOQL required empty instantiator
    public Collaboration() {}

    public Collaboration(String username1, String username2) {
        this.setUsername1(username1);
        this.setUsername2(username2);
        this.setState(STATE_INITIALIZED);
        this.setDateTimeCreated();
    }

    public Collaboration(User u1, User u2) {
        this(u1.getUsername(), u2.getUsername());
    }

    public String getUsername1() {
        return this.username1;
    }

    public void swapUser(String user1Name, String user2Name) {
        setUsername1(user2Name);
        setUsername2(user1Name);
    }

    public void swapUser(User user1, User user2) {
        swapUser(user1.getUsername(), user2.getUsername());
    }

    public void setUsername1(String name) {
        this.username1 = name;
        this.setId();
    }

    public String getOtherUsername(String name) {
        if (name == null) return null;
        if (name.equals(username1)) return username2;
        if (name.equals(username2)) return username1;
        return null;
    }

    public String getUsername2() {
        return this.username2;
    }

    public void setUsername2(String name) {
        this.username2 = name;
        this.setId();
    }

    public String getDateStringCreated() {
        Date date = new Date(getDateTimeCreated());

        return (date.toString());
    }

    public long getDateTimeCreated() {
        return this.dateTimeCreated;
    }

    public void setDateTimeCreated(long d) {
        this.dateTimeCreated = d;
    }

    public void setDateTimeCreated() {
        this.setDateTimeCreated(new Date().getTime());
    }

    public void setState(String s) {
        this.state = s;
    }

    public void setApproved() {
        this.setState(STATE_APPROVED);
    }

    public void setRejected() {
        this.setState(STATE_REJECTED);
    }

    public boolean isApproved() {
        return (this.state != null && this.state.equals(STATE_APPROVED));
    }

    public boolean isEditApproved() {
        return STATE_EDIT_PRIV.equals(this.state);
    }

    public String getState() {
        return this.state;
    }

    public String getId() {
        return this.id;
    }

    public void setId() {
        if (this.username1 == null || this.username2 == null) return;
        if (this.username1.compareTo(this.username2) < 0) {
            this.id = username1 + ":" + username2;
        } else {
            this.id = username2 + ":" + username1;
        }
    }

// NOTE the first user, by convention, is the initiator
    public static Collaboration create(String u1, String u2) {
        Collaboration c = new Collaboration(u1, u2);

        return c;
    }

    // fetch all collabs for the user
    public static List<Collaboration> collaborationsForCurrentUser(HttpServletRequest request) {
        return collaborationsForCurrentUser(request, null);
    }

    // like above, but can specify a state
    public static List<Collaboration> collaborationsForCurrentUser(HttpServletRequest request,
        String state) {
        String context = ServletUtilities.getContext(request);

        if (request.getUserPrincipal() == null) return null; // TODO: evaluate is this cool?
        String username = request.getUserPrincipal().getName();
        return collaborationsForUser(context, username, state);
    }

    public static List<Collaboration> collaborationsForUser(String context, String username) {
        return collaborationsForUser(context, username, null);
    }

    public static List<Collaboration> collaborationsForUser(Shepherd myShepherd, String username) {
        return collaborationsForUser(myShepherd, username, null);
    }

    // copied with Shepherd instead of context in hopes this fixes the issue where we couldn't save an updated collab with another shepherd
    @SuppressWarnings("unchecked") public static List<Collaboration> collaborationsForUser(
        Shepherd myShepherd, String username, String state) {
        String queryString =
            "SELECT FROM org.ecocean.security.Collaboration WHERE ((username1 == '" + username +
            "') || (username2 == '" + username + "'))";

        if (state != null) {
            queryString += " && state == '" + state + "'";
        }
// System.out.println("qry -> " + queryString);
        // myShepherd.setAction("Collaboration.class1");
        Query query = myShepherd.getPM().newQuery(queryString);
        // ArrayList got = myShepherd.getAllOccurrences(query);
        // List returnMe=myShepherd.getAllOccurrences(query);
        Collection c = (Collection)(query.execute());
        ArrayList<Collaboration> returnMe = new ArrayList<Collaboration>(c);
        query.closeAll();

        returnMe = (ArrayList<Collaboration>)addAssumedOrgAdminCollaborations(returnMe, myShepherd,
            username);

        return returnMe;
    }

    @SuppressWarnings("unchecked") public static List<Collaboration> collaborationsForUser(
        String context, String username, String state) {
// TODO: implement cache as this may be hit a lot
        String queryString =
            "SELECT FROM org.ecocean.security.Collaboration WHERE ((username1 == '" + username +
            "') || (username2 == '" + username + "'))";

        if (state != null) {
            queryString += " && state == '" + state + "'";
        }
// System.out.println("qry -> " + queryString);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("Collaboration.class1");
        myShepherd.beginDBTransaction();
        List returnMe = new ArrayList();
        try {
            Query query = myShepherd.getPM().newQuery(queryString);
            // ArrayList got = myShepherd.getAllOccurrences(query);
            returnMe = myShepherd.getAllOccurrences(query);
            query.closeAll();

            returnMe = addAssumedOrgAdminCollaborations(returnMe, myShepherd, username);
        } catch (Exception e) { e.printStackTrace(); } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        return returnMe;
    }

    public static Collaboration collaborationBetweenUsers(Shepherd myShepherd, String u1,
        String u2) {
        return findCollaborationWithUser(u2, collaborationsForUser(myShepherd, u1));
    }

    public static Collaboration collaborationBetweenUsers(User u1, User u2, String context) {
        if (u1 == null || u2 == null) return null;
        return collaborationBetweenUsers(u1.getUsername(), u2.getUsername(), context);
    }

    public static Collaboration collaborationBetweenUsers(String username1, String username2,
        String context) {
        if (username1 == null || username2 == null) return null;
        String queryString = "SELECT FROM org.ecocean.security.Collaboration WHERE ";
        queryString += "(username1 == '" + username1 + "' && username2 == '" + username2 + "') || ";
        queryString += "(username1 == '" + username2 + "' && username2 == '" + username1 + "')";
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<Collaboration> results = new ArrayList<Collaboration>();
        myShepherd.setAction("collaborationBetweenUsers");
        myShepherd.beginDBTransaction();
        Query query = myShepherd.getPM().newQuery(queryString);
        try {
            Collection coll = (Collection)query.execute();
            results = new ArrayList<Collaboration>(coll);
            query.closeAll();
            // System.out.println("collaborationBetweenUsers(String username1, String username2, String context)");
            // System.out.println("collaborationBetweenUsers: "+username1+":"+username2);
            // System.out.println("     State now: "+results.toString());
            // we assume that the question is directional
            // username1 is who we need to reconcile and might be an orgAdmin
            // this is consistent with the current method calling this function
            // if username 1 is an orgAdmin then look for assumed Collaborations
            if (myShepherd.doesUserHaveRole(username1, "orgAdmin", myShepherd.getContext())) {
                // this is a superset of collabs for username1
                ArrayList<Collaboration> tempResults = new ArrayList<Collaboration>();
                List<Collaboration> orgAdminCollabs = addAssumedOrgAdminCollaborations(tempResults,
                    myShepherd, username1);
                for (Collaboration c : orgAdminCollabs) {
                    if (c.getUsername2().equals(username2)) {
                        results.add(0, c);
                        System.out.println("adding derived collab: " + c.toString());
                    }
                }
                // System.out.println("     yState now: "+results.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        if (results == null || results.size() < 1) return null;
        return ((Collaboration)results.get(0));
    }

    public static boolean canCollaborate(User u1, User u2, String context) {
        if (u1.equals(u2)) return true;
        Collaboration c = collaborationBetweenUsers(u1, u2, context);
        if (c == null) return false;
        if (c.getState().equals(STATE_APPROVED) || c.getState().equals(STATE_EDIT_PRIV))
            return true;
        return false;
    }

    public static boolean canCollaborate(String context, String u1, String u2) {
        if (User.isUsernameAnonymous(u1) || User.isUsernameAnonymous(u2)) return true;
        if (u1.equals(u2)) return true;
        Collaboration c = collaborationBetweenUsers(u1, u2, context);
        // System.out.println("canCollaborate(String context, String u1, String u2)");
        if (c == null) return false;
        if (c.getState().equals(STATE_APPROVED) || c.getState().equals(STATE_EDIT_PRIV))
            return true;
        return false;
    }

    public static boolean canEditEncounter(Encounter enc, HttpServletRequest request) {
        try {
            String name1 = request.getUserPrincipal().getName();
            String name2 = enc.getSubmitterID();
            return canEdit(ServletUtilities.getContext(request), name1, name2);
        } catch (Exception missingUser) {
            System.out.println("Collaboration.canEditEncounter hit exception on request=" +
                request + " and enc=" + enc + "; returning false.");
        }
        return false;
    }

    public static boolean canEdit(String context, String u1, String u2) {
        if (u1.equals(u2)) return true;
        Collaboration c = collaborationBetweenUsers(u1, u2, context);
        if (c == null) return false;
        if (c.getState().equals(STATE_EDIT_PRIV)) return true;
        return false;
    }

    public static boolean canEdit(String context, User u1, User u2) {
        if (u1.equals(u2)) return true;
        Collaboration c = collaborationBetweenUsers(u1, u2, context);
        if (c == null) return false;
        if (c.getState().equals(STATE_EDIT_PRIV)) return true;
        return false;
    }

    public static Collaboration findCollaborationWithUser(String username,
        List<Collaboration> all) {
        if (all == null) return null;
        List<Collaboration> collabs = all;
        for (Collaboration c : collabs) {
            if (c.username1.equals(username) || c.username2.equals(username)) return c;
        }
        return null;
    }

    public static String getNotificationsWidgetHtml(HttpServletRequest request,
        Shepherd myShepherd) {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        String langCode = ServletUtilities.getLanguageCode(request);
        Properties collabProps = new Properties();
        collabProps = ShepherdProperties.getProperties("collaboration.properties", langCode,
            context);
        String notif = ""; // collabProps.getProperty("notificationsNone");
        if (request.getUserPrincipal() == null) return notif;
        String username = request.getUserPrincipal().getName();
        List<Collaboration> collabs = collaborationsForCurrentUser(request);
        int n = 0;
        for (Collaboration c : collabs) {
            if (c.getEditInitiator() != null && !c.getEditInitiator().equals(username) &&
                (c.getState().equals(STATE_INITIALIZED) ||
                c.getState().equals(STATE_EDIT_PENDING_PRIV))) n++;
        }
        // make Notifications class to do this outside Collaboration, eeergghh
        try {
            ArrayList<ScheduledIndividualMerge> potentialForNotification =
                myShepherd.getAllCompleteScheduledIndividualMergesForUsername(username);

            System.out.println("Collaboration:potentialForNotification: " +
                potentialForNotification.size());

            ArrayList<ScheduledIndividualMerge> incomplete =
                myShepherd.getAllIncompleteScheduledIndividualMerges();

            System.out.println("Collaboration:incomplete: " + incomplete.size());

            potentialForNotification.addAll(incomplete);
            for (ScheduledIndividualMerge merge : potentialForNotification) {
                if (!merge.ignoredByUser(username) && merge.isUserParticipent(username)) {
                    n++;
                }
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        if (n > 0)
            notif =
                "<div class=\"notification-container\" onClick=\"return showNotifications(this);\">"
                + " <span class=\"notification-pill\">" + n + "</span></div>";
        return notif;
    }

    public static boolean securityEnabled(String context) {
        String enabled = CommonConfiguration.getProperty("collaborationSecurityEnabled", context);

        if ((enabled == null) || !enabled.equals("true")) {
            return false;
        } else {
            return true;
        }
    }

    // here "View" is a weaker action than "Access".
    // "View" means "you can see that the data exists but may not necessarily access the data"
    public static boolean canUserViewOwnedObject(String ownerName, HttpServletRequest request,
        Shepherd myShepherd) {
        if (request.isUserInRole("admin")) return true;
        if (ownerName == null || request.isUserInRole("admin")) return true;
        User viewer = myShepherd.getUser(request);
        User owner = myShepherd.getUser(ownerName);
        return canUserViewOwnedObject(viewer, owner, request);
    }

    public static boolean canUserViewOwnedObject(User viewer, User owner,
        HttpServletRequest request) {
        // if they own it
        if (viewer != null && owner != null && viewer.getUUID() != null &&
            viewer.getUUID().equals(owner.getUUID())) return true;                                                                  // should really be user .equals() method
        // if viewer and owner have sharing turned on
        if (((viewer != null && viewer.hasSharing() && (owner == null || owner.hasSharing()))))
            return true; // just based on sharing
        // if they have a collaboration
        return canCollaborate(viewer, owner, ServletUtilities.getContext(request));
    }

    public static boolean canUserAccessOwnedObject(String ownerName, HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);

        if (!securityEnabled(context)) return true;
        if (request.isUserInRole("admin")) return true;
        if (User.isUsernameAnonymous(ownerName)) return true; // anon-owned is "fair game" to anyone
        if (request.getUserPrincipal() == null) {
            return canCollaborate(context, ownerName, "public");
        }
        String username = request.getUserPrincipal().getName();
        // System.out.println("canUserAccessOwnedObject(String ownerName, HttpServletRequest request)");
        return canCollaborate(context, username, ownerName);
    }

    public static boolean canUserAccessEncounter(Encounter enc, HttpServletRequest request) {
        if (enc != null && enc.getSubmitterID() == null) return true;
        // System.out.println("canUserAccessEncounter(Encounter enc, HttpServletRequest request)");
        return canUserAccessOwnedObject(enc.getAssignedUsername(), request);
    }

    public static boolean canUserAccessEncounter(Encounter enc, String context, String username) {
        String owner = enc.getAssignedUsername();

        if (User.isUsernameAnonymous(owner)) return true; // anon-owned is "fair game" to anyone
        // System.out.println("canUserAccessEncounter(Encounter enc, String context, String username)");
        return canCollaborate(context, username, owner);
    }

    public static boolean canUserAccessOccurrence(Occurrence occ, HttpServletRequest request) {
        if (canUserAccessOwnedObject(occ.getSubmitterID(), request)) return true;
        ArrayList<Encounter> all = occ.getEncounters();
        if ((all == null) || (all.size() < 1)) return true;
        for (Encounter enc : all) {
            if (canUserAccessEncounter(enc, request)) return true; // one is good enough (either owner or in collab or no security etc)
        }
        return false;
    }

    public static boolean canUserAccessImportTask(ImportTask occ, HttpServletRequest request) {
        // first check if the User on the ImportTask matches the current user
        if (occ.getCreator() != null && request.getUserPrincipal() != null &&
            occ.getCreator().getUsername().equals(request.getUserPrincipal().getName())) {
            return true;
        }
        // otherwise check the Encounters
        List<Encounter> all = occ.getEncounters();
        if ((all == null) || (all.size() < 1)) return true;
        for (Encounter enc : all) {
            if (canUserAccessEncounter(enc, request)) return true; // one is good enough (either owner or in collab or no security etc)
        }
        return false;
    }

    // like above, but can pass username
    public static boolean canUserAccessImportTask(ImportTask itask, String context,
        String username) {
        if (itask.getCreator() != null && username != null &&
            itask.getCreator().getUsername().equals(username)) {
            return true;
        }
        // otherwise check the Encounters
        List<Encounter> all = itask.getEncounters();
        if ((all == null) || (all.size() < 1)) return true;
        for (Encounter enc : all) {
            if (canUserAccessEncounter(enc, context, username)) return true; // one is good enough (either owner or in collab or no security etc)
        }
        return false;
    }

    public static boolean canUserAccessMarkedIndividual(MarkedIndividual mi,
        HttpServletRequest request) {
        Vector<Encounter> all = mi.getEncounters();

        if ((all == null) || (all.size() < 1)) return true;
        for (Encounter enc : all) {
            if (canUserAccessEncounter(enc, request)) return true; // one is good enough (either owner or in collab or no security etc)
        }
        return false;
    }

    // Check if User (via request) has edit access to every Encounter in this Individual
    public static boolean canUserFullyEditMarkedIndividual(MarkedIndividual mi,
        HttpServletRequest request) {
        if (request.isUserInRole("admin")) return true;
        Vector<Encounter> all = mi.getEncounters();
        if ((all == null) || (all.size() < 1)) return false;
        for (Encounter enc : all) {
            if (!canEditEncounter(enc, request)) return false; // one is good enough (either owner or in collab or no security etc)
        }
        return true;
    }

    public static boolean canUserAccessSocialUnit(SocialUnit su, HttpServletRequest request) {
        List<MarkedIndividual> all = su.getMarkedIndividuals();

        if ((all == null) || (all.size() < 1)) return true;
        for (MarkedIndividual indy : all) {
            if (canUserAccessMarkedIndividual(indy, request)) return true; // one is good enough (either owner or in collab or no security etc)
        }
        return false;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("username1", getUsername1())
                   .append("username2", getUsername2())
                   .append("state", getState())
                   .append("dateTimeCreated", getDateStringCreated())
                   .toString();
    }

    public String getEditInitiator() {
        if (editInitiator != null && !"".equals(editInitiator)) {
            return editInitiator;
        } else if (this.getState().equals(STATE_REJECTED)) {
            return null;
        } else {
            this.editInitiator = this.username1; // probably old collaboration request where position 1 is always initiator
            return editInitiator;
        }
    }

    public void setEditInitiator(String username) {
        if (username == null) { this.editInitiator = null; } else {
            this.editInitiator = username;
        }
    }

    private static List<Collaboration> addAssumedOrgAdminCollaborations(
        List<Collaboration> returnMe, Shepherd myShepherd, String username) {
        // orgAdmin check
        // for the current user, check if they're an orgAdmin and therefore get default collaborations with all members
        // in which we assume that an orgAdmin only belongs to one org
        // and has a default, edit-level collaboration with all users in org
        if (returnMe != null && myShepherd.doesUserHaveRole(username, "orgAdmin",
            myShepherd.getContext())) {
            if (myShepherd.getUser(username) != null) {
                List<Organization> orgs = myShepherd.getAllOrganizationsForUser(myShepherd.getUser(
                    username));
                // while we assume they are in only one org, there must be exceptions, so be prepared for multi-org orgadmins here
                for (Organization org : orgs) {
                    List<User> users = org.getMembers();
                    for (User user : users) {
                        if (user.getUsername() != null && !user.getUsername().equals(username)) {
                            // System.out.println("dding collab for: "+username+":"+user.getUsername());

                            // so this is someone else than the orgAdmin and therefore someone we should have a default
                            // edit-level collaboration with
                            Collaboration tempCollab = new Collaboration(username,
                                user.getUsername());
                            tempCollab.setState(STATE_EDIT_PRIV);
                            tempCollab.setEditInitiator(username);
                            returnMe.add(tempCollab);
                        }
                    }
                }
            }
        }
        return returnMe;
    }
}
