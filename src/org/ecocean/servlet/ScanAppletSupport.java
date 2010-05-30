package org.ecocean.servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
//import java.io.PrintWriter;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.util.Iterator;
import org.ecocean.*;
import org.ecocean.grid.*;

import java.util.ArrayList;

import javax.jdo.Extent;
import javax.jdo.FetchPlan;
import javax.jdo.Query;



public class ScanAppletSupport extends HttpServlet{
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
	}
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		doPost(request, response);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
	//public synchronized void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		Shepherd myShepherd=new Shepherd();
		response.setContentType("application/octet-stream");
		GridManager gm=GridManagerFactory.getGridManager();
		String supportedAppletVersion=gm.getSupportedAppletVersion();
		
		if((request.getParameter("version")!=null)&&(request.getParameter("version").equals(supportedAppletVersion))&&(request.getParameter("nodeIdentifier")!=null)){
			//System.out.println("scanAppletSupport: Cleared the first hurdle in scanAppletSupport");
			
			boolean targeted=false;
			if((request.getParameter("newEncounterNumber")!=null)&&(!request.getParameter("newEncounterNumber").equals(""))){
				targeted=true;
			}
			boolean gridSpaceAvailable=gm.isGridSpaceAvailable(request, targeted);
			if(gridSpaceAvailable){
			//System.out.println("I have validated a request, and I am now trying to fill it.");
			
			//new workItem-based support
			if ((request.getParameter("action")!=null)&&(request.getParameter("action").equals("getWorkItemGroup"))) {
				//System.out.println("I am entering getWorkItemGroup");
				//System.out.println("scanAppletSupport: Cleared the second hurdle in scanAppletSupport");
				
				
				getWorkItemGroup(myShepherd, request, response, gm);
			}
			//new workItem-based support -- return an individual scanWorkItem
			//else if ((request.getParameter("action")!=null)&&(request.getParameter("action").equals("getWorkItem"))) {
				//getWorkItem(myShepherd, request, response);
			//}
			//following two methods used for the old individual scan applet
			else if ((request.getParameter("action")!=null)&&(request.getParameter("action").equals("getEncountersWithSpotData"))) {
				getEncountersWithSpotData(myShepherd, request, response);
			}
			else if ((request.getParameter("action")!=null)&&(request.getParameter("action").equals("getEncounter"))&&(request.getParameter("number")!=null)) {
				getEncounter(myShepherd, request, response);
			}
		}
		}
	} //end doPost method

	
	//used for the old non-grid applet -- sends a String Vector of appropriate encounters to scan back to the old applet
	public void getEncountersWithSpotData(Shepherd myShepherd, HttpServletRequest request, HttpServletResponse response) {
		//System.out.println("Processing get encounters request for applet...");
		//int total=myShepherd.getNumEncounters();
		Vector encountersWithSpotData=new Vector();
		//System.out.println("scanAppletSupport servlet is attempting to open a transaction...");
		myShepherd.beginDBTransaction();
		Iterator encounters=myShepherd.getAllEncountersNoQuery();
		
		if((request.getParameter("shark")!=null)&&(!request.getParameter("shark").equals(""))) {
			if(myShepherd.isMarkedIndividual(request.getParameter("shark"))) {
				System.out.println("	scAppletSupport: Processing a single shark scan...");
				MarkedIndividual tempShark=myShepherd.getMarkedIndividual(request.getParameter("shark"));
				encounters=tempShark.getEncounters().iterator();
			}
		}
		System.out.println("Got all encounters...");
		int count=0;
		//try{
		
		while (encounters.hasNext()) {
			count++;
			Encounter enc=(Encounter)encounters.next();
			//System.out.println("     "+count+". Found an encounter..."+enc.getEncounterNumber());
			
			//System.out.println("rightSide is: "+request.getParameter("rightSide"));
			if ((request.getParameter("rightSide")!=null)&&(enc.getRightSpots()!=null)) {
				//encountersWithSpotData.add(enc.getCloneForScan());
				encountersWithSpotData.add(enc.getEncounterNumber()); //remove me
				//System.out.println("          Added right-side data encounter to final list to send.");
				}
			else if ((request.getParameter("rightSide")==null)&&(enc.getSpots()!=null)){
				encountersWithSpotData.add(enc.getEncounterNumber());
				//System.out.println("          Added left-side data encounter to final list to send.");
				
			}
			}
		sendObject(response, encountersWithSpotData);
		System.out.println("scanAppletSupport is attempting to rollback a transaction...");
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		encounters=null;
		encountersWithSpotData=null;
		System.out.println("Done!");
	}
	
	//used for the old non-grid applet -- sends the appropriate encounter to scan back to the old applet
	public void getEncounter(Shepherd myShepherd, HttpServletRequest request, HttpServletResponse response) {
		Encounter transmitEnc=null;
		myShepherd.beginDBTransaction();
		transmitEnc=myShepherd.getEncounterDeepCopy(request.getParameter("number"));
		sendObject(response, transmitEnc);
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
	}
	

	public void getWorkItemGroup(Shepherd myShepherd, HttpServletRequest request, HttpServletResponse response, GridManager gm) {
		//how long to wait for a scanWorkItem to be marked as done before reclaiming it for processing by another agent
		long checkoutTimeout=gm.getCheckoutTimeout();
		
		String nodeIdentifier=request.getParameter("nodeIdentifier");
		GridNode nd=gm.getGridNode(nodeIdentifier);
		int groupSize=gm.getNextGroupSize(nd);
		
		if((request.getParameter("newEncounterNumber")!=null)&&(!request.getParameter("newEncounterNumber").equals(""))){
			
			//System.out.println("newEncounterNumber has been specified");
			try{
				myShepherd.beginDBTransaction();

				int totalWorkItems=gm.getNumWorkItemsCompleteForTask(request.getParameter("newEncounterNumber"))+gm.getNumWorkItemsIncompleteForTask(request.getParameter("newEncounterNumber"));
				//change
				//int totalWorkItemsComplete=myShepherd.getNumWorkItemsCompleteForTask(request.getParameter("newEncounterNumber"));
				int totalWorkItemsComplete = gm.getNumWorkItemsCompleteForTask(request.getParameter("newEncounterNumber"));
				
				boolean force=false;
				int diff=totalWorkItems-totalWorkItemsComplete;
				if(diff<groupSize){
					//System.out.println("scanAppletSupport: I'm diff'ing!");
					groupSize=diff;
					force=true;
				}
				
				//new way
				Extent encClass=myShepherd.getPM().getExtent(ScanWorkItem.class, true);
				Query query=myShepherd.getPM().newQuery(encClass);
				Vector holdSWIs=new Vector();
				
				//get the items to transmit
				getUniqueWorkItems(myShepherd,holdSWIs, request, query, groupSize, checkoutTimeout, force, totalWorkItems,totalWorkItemsComplete,gm);
				
				
				//transmit result and clean up
				myShepherd.closeDBTransaction();
				boolean transmitSuccess=sendObject(response, holdSWIs);
				if(transmitSuccess){
					nd.setLastCheckout(System.currentTimeMillis());
				}
				
			} 
			catch(Exception e){
				System.out.println("Error while dishing out a targeted workItemGroup!");				
				e.printStackTrace();
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
			}

			
		} //end if targeted
		
		// start generic just grab the next available workItem group
		else{
			
			//System.out.println("I'm in scanAppletSupport, generic request, just starting...");
			
			try{
				myShepherd.beginDBTransaction();

				//System.out.println("I'm in scanAppletSupport, generic request, opened a db CONNECTION...");
				
				boolean foundTask=false;
				Vector holdResults=new Vector();

				
				//get the items to transmit
				getUniqueWorkItemsGeneric(foundTask,gm,nd,myShepherd,holdResults, request, groupSize, checkoutTimeout, false, 1,1);


				myShepherd.closeDBTransaction();
				boolean transmitSuccess=sendObject(response, holdResults);
				if(transmitSuccess)nd.setLastCheckout(System.currentTimeMillis());
				
		} //end try 
		catch(Exception e) {
				e.printStackTrace();
				System.out.println("I'm in scanAppletSupport, generic request, caught an exception...");
				
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
				//myShepherd.closeDBTransaction();
		}

		} //end generic
	
	}
	


//transmits requested objects to the applet
public boolean sendObject(HttpServletResponse response, Object encounterVector){
     ObjectOutputStream outputToApplet;
     try
     {
         outputToApplet = new ObjectOutputStream(response.getOutputStream());
         outputToApplet.writeObject(encounterVector);
         outputToApplet.flush();
         outputToApplet.close();
         return true;
     }
     catch (Exception e)
     {
       System.out.println("Caught an error when attempting to return data via the sendEncounterList method of scanAppletSupport servlet");
       e.printStackTrace(); 
       return false;
     }
}

public synchronized void getUniqueWorkItems(Shepherd myShepherd, Vector holdSWIs, HttpServletRequest request, Query query, int groupSize, long checkoutTimeout, boolean force, int totalWorkItems, int totalWorkItemsComplete, GridManager gm){
	
	String id=request.getParameter("newEncounterNumber");
	
	//change
	//separate this out
	//ArrayList list=myShepherd.getID4AvailableScanWorkItems(id,query, groupSize, checkoutTimeout, force);
	ArrayList<ScanWorkItem> list = gm.getWorkItems(groupSize);
	int listSize=list.size();
	
	//change
	//try a force if no scanWorkItems are available for processing
	/*if(listSize==0){
		int numAvailable=myShepherd.getNumWorkItemsIncomplete4Task(id);
		myShepherd.getPM().getFetchPlan().setGroup(FetchPlan.DEFAULT);
		if(numAvailable>0){

			Extent encClass2=myShepherd.getPM().getExtent(scanWorkItem.class, true);
			query=myShepherd.getPM().newQuery(encClass2);
			list=myShepherd.getID4AvailableScanWorkItems(id,query, 1, checkoutTimeout, true);
			listSize=list.size();
			if(listSize>1){listSize=1;}
		}
	}*/
	
	
	
	boolean hasWork=false;
	
	boolean rightScan=false;
	for(int i=0;i<listSize;i++){
		hasWork=true;
		
		ScanWorkItem swi=list.get(i);
		
		long time=System.currentTimeMillis();
		
		swi.setStartTime(time);
		
		swi.setTotalWorkItemsInTask(totalWorkItems);
		swi.setWorkItemsCompleteInTask(totalWorkItemsComplete+i);
		rightScan=swi.rightScan;
		holdSWIs.add(swi);
	}
	query.closeAll();
	if(hasWork){
		query.closeAll();
		myShepherd.commitDBTransaction();
		}
	else{
		query.closeAll();
		if((totalWorkItems!=totalWorkItemsComplete)||(totalWorkItems==0)){
			
			//return a blank workItem telling the applet to wait for a result to be written
			ScanWorkItem scanWI=new ScanWorkItem();
			scanWI.setTotalWorkItemsInTask(-1);
			scanWI.setWorkItemsCompleteInTask(-1);
			scanWI.rightScan=rightScan;
			
			myShepherd.rollbackDBTransaction();
			
			holdSWIs.add(scanWI);
		}
		else{			
			
			//tell the applet to go for it and look at the result
			query.closeAll();
			myShepherd.rollbackDBTransaction();

		
			
			ScanWorkItem scanWI=new ScanWorkItem();
			scanWI.setTotalWorkItemsInTask(0);
			scanWI.setWorkItemsCompleteInTask(0);
			scanWI.rightScan=rightScan;
			holdSWIs.add(scanWI);
		}

	}
	
	//end method
	


	
}

public synchronized void getUniqueWorkItemsGeneric(boolean foundTask,GridManager gm,GridNode nd, Shepherd myShepherd, Vector holdResults, HttpServletRequest request, int groupSize, long checkoutTimeout, boolean force, int totalWorkItems, int totalWorkItemsComplete){
	
	//change
	//get a list of unfinished scanWorkItems
	//ArrayList list=myShepherd.getID4AvailableScanWorkItems(query, groupSize, checkoutTimeout, false);
	ArrayList<ScanWorkItem> list = new ArrayList<ScanWorkItem>();
	if(!nd.isZombie){
		list = gm.getWorkItems(groupSize);
	}

	
	//get the size of the list
	int listSize=list.size();
	
	//change
	//try a force if no scanWorkItems are available for processing
	/*if(listSize==0){
		int numAvailable=myShepherd.getNumWorkItemsIncomplete();
		myShepherd.getPM().getFetchPlan().setGroup(FetchPlan.DEFAULT);
		if(numAvailable>0){
			//if(numAvailable<groupSize){
			//	groupSize=numAvailable;
			//}
			Extent encClass2=myShepherd.getPM().getExtent(scanWorkItem.class, true);
			//Query query2=myShepherd.getPM().newQuery(encClass2);
			query=myShepherd.getPM().newQuery(encClass2);
			//list=myShepherd.getID4AvailableScanWorkItems(query2, groupSize, checkoutTimeout, true);
			//list=myShepherd.getID4AvailableScanWorkItems(query, groupSize, checkoutTimeout, true);
			list=myShepherd.getID4AvailableScanWorkItems(query, 1, checkoutTimeout, true);
			listSize=list.size();
			if(listSize>1){listSize=1;}
		}
	}*/
	
	String activeTask="";
	for(int i=0;i<listSize;i++){

				foundTask=true;
				ScanWorkItem swi2 = list.get(i);

				
				long time=System.currentTimeMillis();
				swi2.setStartTime(time);
				
				//change
				//newSWI.setTotalWorkItemsInTask(1);
				//newSWI.setWorkItemsCompleteInTask(1);
				//holdResults.add(newSWI);
				swi2.setTotalWorkItemsInTask(1);
				swi2.setWorkItemsCompleteInTask(1);
				holdResults.add(swi2);
	}
	if(foundTask&&(!nd.isZombie)){
		//query.closeAll();
		
		myShepherd.commitDBTransaction();
	
	}
	else {
		//System.out.println("I'm in scanAppletSupport, generic request, returning a blank return item...");
		
		//tell the node to go back to a conservative load since we're not sure how intensive the next set of work might be
		nd.setGroupSize(gm.getGroupSize());
		
		myShepherd.rollbackDBTransaction();

		
		//if there is nothing to send, send a blank workItem
		ScanWorkItem scanWI=new ScanWorkItem();
		scanWI.setTotalWorkItemsInTask(0);
		scanWI.setWorkItemsCompleteInTask(0);
		Vector noSWIS=new Vector();
		//boolean transmitSuccess=sendObject(response, noSWIS);
		//if(transmitSuccess)nd.setLastCheckout(System.currentTimeMillis());
		
	}
}





}