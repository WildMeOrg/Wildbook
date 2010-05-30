package org.ecocean.servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

import java.io.File;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.*;
import org.ecocean.grid.*;

import java.util.ArrayList;

import com.reijns.I3S.Pair;



public class ScanWorkItemResultsHandler extends HttpServlet {

	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
  	}

	
	private static Object receiveObject( ObjectInputStream con ) throws Exception
		{
			//System.out.println("scanresultsServlet: I am about to read in the byte array!");
			Object obj=new ScanWorkItemResult();
			try{	
				obj=(Object)con.readObject();
			} 
			catch(java.lang.NullPointerException npe) {
				System.out.println("scanResultsServlet received an empty results set...no matches whatsoever.");
				return obj;
			}
			//System.out.println("scanresultsServlet: I successfully read in the byte array!");

		return obj;

		}
	
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    	doPost(request, response);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
			
			//set up a shepherd for DB transactions
			Shepherd myShepherd=new Shepherd();
			String nodeIdentifier=request.getParameter("nodeIdentifier");
			GridManager gm=GridManagerFactory.getGridManager();
			
			//double cutoff=2;
			String statusText="success";
			//System.out.println("scanWorkItemResultsHandler: I am starting up.");
			response.setContentType("application/octet-stream");
			ObjectInputStream inputFromApplet = null;        
    		PrintWriter out = null;
    		myShepherd.beginDBTransaction();
		try {

			// get an input stream and Vector of results from the applet
			inputFromApplet = new ObjectInputStream(request.getInputStream());
	    	Vector returnedResults=new Vector();
	    	returnedResults=(Vector) receiveObject(inputFromApplet);
        	inputFromApplet.close();
        	
        	
    		//send response to applet
    		try{
    			//setup the servlet output
    			response.setContentType ("text/plain"); 
    			out = response.getWriter();
    			out.println (statusText);
    			out.close();
    		} 
    		catch(Exception e) {
    			e.printStackTrace();
    		}

    		
        	int returnedSize=returnedResults.size();
        	
        	
        	
        	//ArrayList<String> affectedScanTasks=new ArrayList<String>();
        	//String affectedTask="";
        	for(int m=0;m<returnedSize;m++){
        		ScanWorkItemResult wir=(ScanWorkItemResult)returnedResults.get(m);
        		String swiUniqueNum=wir.getUniqueNumberWorkItem();
        		
        		gm.checkinResult(wir);
        		
      

	        	
        	}
        		


        	myShepherd.commitDBTransaction();
        	myShepherd.closeDBTransaction();
        	
        	if(returnedSize>0){
        		GridNode node=gm.getGridNode(nodeIdentifier);
        		node.checkin(returnedSize);
        		gm.incrementCompletedWorkItems(returnedSize);
        	}
        	
			
		}
		catch(Exception e) {
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			System.out.println("scanResultsServlet registered the following error...");
			e.printStackTrace();
			//statusText="failure";
		}
		


		


	}
	

}