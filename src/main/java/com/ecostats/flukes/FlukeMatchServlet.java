package com.ecostats.flukes;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONArray;
import org.mongodb.morphia.query.Query;

/**
 * Servlet implementation class FlukeMatchServlet
 */
//@WebServlet(description = "Attempts to matches a tracing with other tracings", urlPatterns = { "/FlukeMatchServlet" })
public class FlukeMatchServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    response.setContentType("text/html;charset=utf-8"); //("application/json;charset=utf-8");
	    PrintWriter out = response.getWriter();
    	FlukeMongodb datasource = new FlukeMongodb("localhost",27020);
	    try{
		    if (request.getParameter("encounter_id") != null) {
		    	// get the passed parameter values
		    	String encounter_id = request.getParameter("encounter_id");
		    	String photo_id = datasource.sha1(encounter_id + request.getParameter("photo_id"));
		    	// get a query object to locate the current fluke tracing if any
		    	Query<Fluke> query = getFlukeTracing(datasource, encounter_id, photo_id);
		    	List<Fluke> flukequery = query.asList();
		    	if (flukequery.size()==0){ // the object does not exist in the database, so add it   		
			    	out.println("The current fin tracing has not yet been saved. You must save the tracing before attempting a match search.");
		    	}else{ // get all tracings in the database and do a compare to the current fluke trace
		    		// set the current fluke trace
		    		Fluke fluke = flukequery.get(0);
		    		// build a query of fluke traces to compare to the current fluke
			    	Query<Fluke> query_flukes = datasource.datastore().createQuery(Fluke.class).filter("encounter !=",encounter_id);
			    	// remove the current fluke trace from the query result (i.e. do not compare itself)		
			    	query_flukes.field("encounter").notEqual(encounter_id); 
			    	// VERY RAM using and inefficient! Update to a cursor method at some point
			    	List<Fluke> flukes = query_flukes.asList();
			    	if (flukes.size()==0){
			    		out.println("No fluke traces found to compare.");
			    	}else{
			    		// do the trace comparison
			    		TraceCompare tc = new TraceCompare();
			    		TreeSet<Fluke> matches = tc.processCatalog(flukes,fluke);
			    		if (matches.size()>0){
			    			String result = identifyMatches(matches);
			    			out.println(result);
			    		}else{
			    			out.println("No matches found.");
			    		}
			    	}
		    	}
		    }
	    }catch (Exception e) {
	    	out.println("Internal error. Failed to process match.");
	    }finally{
	    	datasource.close();
	    	out.close();
	    }
	}

	/**
	 * @param matches
	 * @return json String 
	 * @throws JSONException
	 */
	public String identifyMatches(TreeSet<Fluke> matches) throws JSONException {
		String context="context0";
		Shepherd myShepherd = new Shepherd(context);
		myShepherd.beginDBTransaction();
		JSONObject result = new JSONObject();
		try{
			Fluke matched_fluke;
			Encounter encounter;
			String individual_id;
			JSONArray encounters = new JSONArray();
			JSONArray individuals = new JSONArray();
			Iterator<Fluke> iterator = matches.iterator();
			while (iterator.hasNext()){
				matched_fluke=iterator.next();
				encounter = myShepherd.getEncounter(matched_fluke.getEncounter());
				individual_id = encounter.isAssignedToMarkedIndividual();
				if (individual_id.equals("Unassigned")){
					encounters.put("ID: "+matched_fluke.getEncounter()+",  Rank: "+matched_fluke.getMatchValue());
				}else{
					individuals.put("ID: "+individual_id+",  Rank: "+matched_fluke.getMatchValue());
				}
			}
			result.put("individuals", individuals);
			result.put("encounters", encounters);
		}finally{
			myShepherd.closeDBTransaction();
		}
		return result.toString();
	}

	/**
	 * @param datasource
	 * @param encounter_id
	 * @param photo_id
	 * @return MongoDB Morphia Query
	 */
	public Query<Fluke> getFlukeTracing(FlukeMongodb datasource,
			String encounter_id, String photo_id) {
		Query<Fluke> query = datasource.datastore().createQuery(Fluke.class);           
		query.and(
			query.criteria("photo").contains(photo_id),
			query.criteria("encounter").contains(encounter_id)
		);
		return query;
	}

}
