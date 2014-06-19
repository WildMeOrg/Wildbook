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

	//fetch all collabs for the user
	public static ArrayList collaborationsForUser(HttpServletRequest request) {
		return collaborationsForUser(request, null);
	}

	//like above, but can specify a status
	public static ArrayList collaborationsForUser(HttpServletRequest request, String status) {
		String context = ServletUtilities.getContext(request);
		Shepherd myShepherd = new Shepherd(context);
		if (request.getUserPrincipal() == null) { return null; }  //TODO is this cool?
		String username = request.getUserPrincipal().getName();
System.out.println(" collabs4username->"+username);

		String queryString = "SELECT FROM org.ecocean.security.Collaboration WHERE (username1 == '" + username + "') || (username2 == '" + username + "')";
		if (status != null) {
			queryString += " && STATUS == '" + status + "'";
		}
System.out.println("qry -> " + queryString);
		Query query = myShepherd.getPM().newQuery(queryString);
    //ArrayList got = myShepherd.getAllOccurrences(query);
    return myShepherd.getAllOccurrences(query);
	}

/*
    int numMatchingOccurs = got.size();

    for(int y=0;y<numMatchingOccurs;y++){
    	Occurrence occur=(Occurrence)matchingOccurs.get(y);
    	
    	ArrayList<String> pairs=occur.getCorrespondingHaplotypePairsForMarkedIndividuals(myShepherd);
    	int numPairs = pairs.size();
*/


	public static boolean securityEnabled(String context) {
		String enabled = CommonConfiguration.getProperty("collaborationSecurityEnabled", context);
		if ((enabled == null) || !enabled.equals("true")) {
			return true;
			//return false;
		} else {
			return true;
		}
	}


	public static boolean canUserAccessEncounter(Encounter enc, HttpServletRequest request) {
		String context = ServletUtilities.getContext(request);
		if (!securityEnabled(context)) { return true; }

		if (request.getUserPrincipal() == null) { return false; }  //???
		String username = request.getUserPrincipal().getName();
System.out.println("username->"+username);

		String owner = enc.getAssignedUsername();
		if ((owner == null) || owner.equals("")) { return true; }  //anon-owned is "fair game" to anyone
		if (owner.equals(username)) { return true; }  //easy

///TODO real maths
		return false;
	}

	//public boolean canUserAccessEncounter(Encounter enc, String username, String context) {
	//}

	public static boolean doesQueryExcludeUser(Query query, HttpServletRequest request) {
System.out.println("query>>>> " + query.toString());
		String context = ServletUtilities.getContext(request);
		if (!securityEnabled(context)) return false;

		if (request.getUserPrincipal() == null) return true;  //anon user excluded if security enabled????
		String username = request.getUserPrincipal().getName();
System.out.println("username->"+username);

		return false;
	}


}
