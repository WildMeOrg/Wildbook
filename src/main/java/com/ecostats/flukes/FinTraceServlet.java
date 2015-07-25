package com.ecostats.flukes;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.servlet.ServletUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

//why add a whole new database just because you didn't understand Wildbook?
//import org.mongodb.morphia.query.Query;

import org.ecocean.*;
import java.util.ArrayList;



//import com.ecostats.flukes.FlukeMongodb;

/**
 * Servlet implementation class FinTraceServlet
 * 
 * This Servlet saves new, or updates existing, node tracings 
 * for any unique encounter and photo combination.
 */

/**
 * @author Ecological Software Solutions LLC
 */


//@WebServlet("/FinTraceServlet")
public class FinTraceServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	/**
     * @see HttpServlet#HttpServlet()
     */
    public FinTraceServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }
    
    /**
	 * 
	 * The post request contains query parameters for both "path" and "node type" data sent in 
	 * JSON array format for both left and right flukes. The path data set is a [X,Y] array list
	 * of node locations, and the node types data is one dimensional array of node types. 
	 * 
	 * A left fluke dataset example: 
	 *  
     * path_left:{path:[[70,103],[105,232],[167,316]]}
     * nodes_left:{node_types:[-1,2,0]} // -1=tip, 2=Gouge, 0=notch
	 * 
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    //set up for response
	    response.setContentType("text/html;charset=utf-8"); //("application/json;charset=utf-8");
	    PrintWriter out = response.getWriter();
    	//FlukeMongodb datasource = new FlukeMongodb("localhost",0);
	    String context=ServletUtilities.getContext(request);
	    Shepherd myShepherd=new Shepherd(context);
	    myShepherd.beginDBTransaction();
	    try{
		    if (request.getParameter("path_left") != null) {
		      
		    	// get the passed parameter values
		    	String encounter_id = request.getParameter("encounter_id");
		    	Encounter enc=myShepherd.getEncounter(encounter_id);
		    	String photo_id = request.getParameter("photo_id");
		    	boolean notch_open = request.getParameter("notch_open").equals("true");
		    	int trace_type = Integer.parseInt(request.getParameter("trace_type"));
		    	boolean curled_left = request.getParameter("curled_left").equals("true");
		    	boolean curled_right = request.getParameter("curled_right").equals("true");
		    	// get the left path and node information
		    	FinTrace finTraceLeft = getFinTrace(request, out, "left", trace_type, notch_open, curled_left);
		    	// get the right path and node information
		    	FinTrace finTraceRight = getFinTrace(request, out, "right", trace_type, notch_open, curled_right);
		    	// get a query object to locate the current fluke tracing if any
		    	
		    	//let's persist this the right way - we're going to create a 
		    	ArrayList<SuperSpot> leftSpots=convertFinTraceToSuperSpotArray(finTraceLeft);
		    	ArrayList<SuperSpot> rightSpots=convertFinTraceToSuperSpotArray(finTraceRight);
		    	
		    	enc.setSpots(leftSpots);
		    	SinglePhotoVideo spv=myShepherd.getSinglePhotoVideo(photo_id);
		    	enc.setSpotImageFileName(spv.getFilename());
		    	
		    	enc.setRightSpots(rightSpots);
		    	enc.setRightSpotImageFileName(spv.getFilename());
		    	
		    	enc.setDynamicProperty("leftCurled", (new Boolean(finTraceLeft.getCurled()).toString()));
		    	enc.setDynamicProperty("rightCurled", (new Boolean(finTraceRight.getCurled()).toString()));
		    	
		    	
		    	/*
		    	 Kevin - don't add a new database to a project where one isn't needed!!!!
		    	Query<Fluke> query = getFlukeTracing(datasource, encounter_id, photo_id);
		    	List<Fluke> flukequery = query.asList();
		    	if (flukequery.size()==0){ // the object does not exist in the database, so add it
			    	Fluke fluke = new Fluke(finTraceLeft,finTraceRight);
			    	fluke.setEncounter(encounter_id);
			    	fluke.setPhoto(photo_id);
			    	datasource.datastore().save(fluke);		    		
			    	out.println("Fin tracing saved.");
		    	}else{ // update the existing stored object 
		    		Fluke fluke = flukequery.get(0);
		    		fluke.setLeftFluke(finTraceLeft);
		    		fluke.setRightFluke(finTraceRight);
		    		datasource.datastore().update(fluke, datasource.datastore().createUpdateOperations(Fluke.class).set("left_fluke", finTraceLeft).set("right_fluke", finTraceRight));
		    		//datasource.datastore().update(fluke, datasource.datastore().createUpdateOperations(Fluke.class).set("right_fluke", finTraceRight));
			    	out.println("Fin tracing updated.");
		    	}
		    	*/
		    	
		    	
		    	
		    	
		    }
		    myShepherd.commitDBTransaction();
	    }
	    catch (Exception e) {
	    	out.println("Failed to add tracing. Check database accessibility.");
	    	myShepherd.rollbackDBTransaction();
	    }finally{
	    	//datasource.close();
	      
	      myShepherd.closeDBTransaction();
	    	out.close();
	    }
	}

	/**
	 * @param request
	 * @param out
	 * @param side
	 * @return FinTrace
	 */
	public FinTrace getFinTrace(HttpServletRequest request, PrintWriter out, String side, int trace_type, boolean notch_open, boolean curled) {
    	// path and nodes are nested arrays which have to be parsed out into Java arrays
		JSONObject path = new JSONObject(request.getParameter("path_"+side)); 
		JSONObject nodes = new JSONObject(request.getParameter("nodes_"+side)); 
		JSONArray pathArray = path.getJSONArray("path");
		JSONArray nodeArray = nodes.getJSONArray("node_types");
		if(pathArray.length()<3){
			out.println("You need at least three marked nodes for the "+side+" fluke side.");
		}
		double[] x = new double[pathArray.length()];
		double[] y = new double[pathArray.length()];
		double[] n = new double[pathArray.length()];
		for (int i=0;i<pathArray.length();i++){
			x[i]=pathArray.getJSONArray(i).getInt(0);
			y[i]=pathArray.getJSONArray(i).getInt(1);
			n[i]=nodeArray.getInt(i);
		}
		// create new fin tracings from the passed tracing node X,Y locations and node Types 
		FinTrace finTrace = new FinTrace(x,y,n);
		finTrace.setTraceType(trace_type);
		finTrace.setCurled(curled);
		finTrace.setNotchOpen(notch_open);
		return finTrace;
	}

	/**
	 * @param datasource
	 * @param encounter_id
	 * @param photo_id
	 * @return MongoDB Morphia Query
	 */
	
	/*
	public Query<Fluke> getFlukeTracing(FlukeMongodb datasource, String encounter_id, String photo_id) {
		// Build a query to see if a tracing on the current encounter photo already exists
		// alternate query syntax :  Query query = this.datastore.createQuery(Fluke.class)field("photo").equals(photo_id).field("encounter").equals(encounter_id);           
		Query<Fluke> query = datasource.datastore().createQuery(Fluke.class);           
		query.and(
			query.criteria("photo").contains(photo_id),
			query.criteria("encounter").contains(encounter_id)
		);
		return query;
	}
	*/
	
	private ArrayList<SuperSpot> convertFinTraceToSuperSpotArray(FinTrace fintrace){
	  ArrayList<SuperSpot> superspots=new ArrayList<SuperSpot>();
	  
	  double[] x=fintrace.getX();
	  double[] y=fintrace.getY();
	  double[] types=fintrace.getTypes();
	  boolean curled=fintrace.getCurled();
	  boolean notchOpen=fintrace.getNotchOpen();
	  
	  int size=x.length;
	  for(int i=0;i<size;i++){
	    superspots.add(new SuperSpot(x[i],y[i],new Double(types[i])));
	  }
	  
	  
	  
	  return superspots;
	}

}
