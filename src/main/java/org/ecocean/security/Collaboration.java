package org.ecocean.security;

//import java.util.Date;
import java.util.*;
import java.io.Serializable;
import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;




/**
 * a Collaboration is a defined, two-way relationship between two Users.
 * It can exist in various states, but once fully approved, generally will allow the referenced
 * two users to have more access to each other's data.
 */
public class Collaboration implements java.io.Serializable {

	private static final long serialVersionUID = -1161710718628733038L;
	//username1 is the initiator
	private String username1;
	//username2 is who was invited to join
	private String username2;
	private long dateTimeCreated;
	private String state;
	private String id;

	public static final String STATE_INITIALIZED = "initialized";
	public static final String STATE_REJECTED = "rejected";
	public static final String STATE_APPROVED = "approved";


	//JDOQL required empty instantiator
	public Collaboration() {}

////////////////TODO prevent duplicates
	public Collaboration(String username1, String username2) {
		this.setUsername1(username1);
		this.setUsername2(username2);
		this.setState(STATE_INITIALIZED);
		this.setDateTimeCreated();
	}

	public String getUsername1() {
		return this.username1;
	}

	public void setUsername1(String name) {
		this.username1 = name;
		this.setId();
	}

	public String getUsername2() {
		return this.username2;
	}

	public void setUsername2(String name) {
		this.username2 = name;
		this.setId();
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


//TODO this should do other steps?  maybe? like notify user??
// NOTE the first user, by convention, is the initiator
	public static Collaboration create(String u1, String u2) {
		Collaboration c = new Collaboration(u1,u2);
  //storeNewCollaboration(Collaboration collab) {
		return c;
	}

	//fetch all collabs for the user
	public static List<Collaboration> collaborationsForCurrentUser(HttpServletRequest request) {
		return collaborationsForCurrentUser(request, null);
	}

	//like above, but can specify a state
	public static List<Collaboration> collaborationsForCurrentUser(HttpServletRequest request, String state) {
		String context = ServletUtilities.getContext(request);
		if (request.getUserPrincipal() == null) return null;  //TODO is this cool?
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
  @SuppressWarnings("unchecked")
	public static List<Collaboration> collaborationsForUser(Shepherd myShepherd, String username, String state) {
		String queryString = "SELECT FROM org.ecocean.security.Collaboration WHERE ((username1 == '" + username + "') || (username2 == '" + username + "'))";
		if (state != null) {
			queryString += " && state == '" + state + "'";
		}
//System.out.println("qry -> " + queryString);
		myShepherd.setAction("Collaboration.class1");
		Query query = myShepherd.getPM().newQuery(queryString);
    //ArrayList got = myShepherd.getAllOccurrences(query);
		List returnMe=myShepherd.getAllOccurrences(query);
		query.closeAll();
    return returnMe;

	}



  @SuppressWarnings("unchecked")
	public static List<Collaboration> collaborationsForUser(String context, String username, String state) {
//TODO cache!!!  (may be hit a lot)
		String queryString = "SELECT FROM org.ecocean.security.Collaboration WHERE ((username1 == '" + username + "') || (username2 == '" + username + "'))";
		if (state != null) {
			queryString += " && state == '" + state + "'";
		}
//System.out.println("qry -> " + queryString);
		Shepherd myShepherd = new Shepherd(context);
		myShepherd.setAction("Collaboration.class1");
		myShepherd.beginDBTransaction();
		Query query = myShepherd.getPM().newQuery(queryString);
    //ArrayList got = myShepherd.getAllOccurrences(query);
		List returnMe=myShepherd.getAllOccurrences(query);
		query.closeAll();
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
    return returnMe;
	}

	public static Collaboration collaborationBetweenUsers(User u1, User u2, String context) {
		return collaborationBetweenUsers(context, u1.getUsername(), u2.getUsername());
	}

	public static Collaboration collaborationBetweenUsers(Shepherd myShepherd, String u1, String u2) {
		return findCollaborationWithUser(u2, collaborationsForUser(myShepherd, u1));
	}



	public static Collaboration collaborationBetweenUsers(String context, String u1, String u2) {
		return findCollaborationWithUser(u2, collaborationsForUser(context, u1));
	}

	public static boolean canCollaborate(String context, String u1, String u2) {
		if (User.isUsernameAnonymous(u1) || User.isUsernameAnonymous(u2)) return true;  //TODO not sure???
		if (u1.equals(u2)) return true;
		Collaboration c = collaborationBetweenUsers(context, u1, u2);
		if (c == null) return false;
		if (c.getState().equals(STATE_APPROVED)) return true;
		return false;
	}

	public static Collaboration findCollaborationWithUser(String username, List<Collaboration> all) {
		if (all == null) return null;
		List<Collaboration> collabs = all;
		for (Collaboration c : collabs) {
			if (c.username1.equals(username) || c.username2.equals(username)) return c;
		}
		return null;
	}


	public static String getNotificationsWidgetHtml(HttpServletRequest request) {
		String context = "context0";
		context = ServletUtilities.getContext(request);
		String langCode = ServletUtilities.getLanguageCode(request);
		Properties collabProps = new Properties();
 		collabProps = ShepherdProperties.getProperties("collaboration.properties", langCode, context);
		String notif = "";  //collabProps.getProperty("notificationsNone");

		if (request.getUserPrincipal() == null) return notif;
		String username = request.getUserPrincipal().getName();

		List<Collaboration> collabs = collaborationsForCurrentUser(request);
		int n = 0;
		for (Collaboration c : collabs) {
			if (c.username2.equals(username) && c.getState().equals(STATE_INITIALIZED)) n++;
		}
		if (n > 0) notif = "<div onClick=\"return showNotifications(this);\">" + collabProps.getProperty("notifications") + " <span class=\"notification-pill\">" + n + "</span></div>";
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


	public static boolean canUserAccessEncounter(Encounter enc, HttpServletRequest request) {
		String context = ServletUtilities.getContext(request);
		if (!securityEnabled(context)) return true;
		if (request.isUserInRole("admin")) return true;  //TODO generalize and/or allow other roles all-access

		if (request.getUserPrincipal() == null) return false;
		return canUserAccessEncounter(enc, context, request.getUserPrincipal().getName());
        }
	public static boolean canUserAccessEncounter(Encounter enc, String context, String username) {
		String owner = enc.getAssignedUsername();
		if (User.isUsernameAnonymous(owner)) return true;  //anon-owned is "fair game" to anyone
//System.out.println("owner->" + owner);
//System.out.println("canCollaborate? " + canCollaborate(context, owner, username));
		return canCollaborate(context, owner, username);
	}


	public static boolean canUserAccessOccurrence(Occurrence occ, HttpServletRequest request) {
  	ArrayList<Encounter> all = occ.getEncounters();
		if ((all == null) || (all.size() < 1)) return true;
		for (Encounter enc : all) {
			if (canUserAccessEncounter(enc, request)) return true;  //one is good enough (either owner or in collab or no security etc)
		}
		return false;
	}


	public static boolean canUserAccessMarkedIndividual(MarkedIndividual mi, HttpServletRequest request) {
		return true;  //FOR NOW(?) anyone can get to individual always
/*
  	Vector<Encounter> all = mi.getEncounters();
		if ((all == null) || (all.size() < 1)) return true;
		for (Encounter enc : all) {
			if (canUserAccessEncounter(enc, request)) return true;  //one is good enough (either owner or in collab or no security etc)
		}
		return false;
*/
	}

/*   CURRENTLY NOT USED
	public static boolean doesQueryExcludeUser(Query query, HttpServletRequest request) {
System.out.println("query>>>> " + query.toString());
		String context = ServletUtilities.getContext(request);
		if (!securityEnabled(context)) return false;

		if (request.getUserPrincipal() == null) return true;  //anon user excluded if security enabled????
		String username = request.getUserPrincipal().getName();
System.out.println("username->"+username);

		return false;
	}
*/



}
