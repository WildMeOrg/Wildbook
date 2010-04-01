package org.ecocean.grid;


import java.util.*;
//import java.io.*;

import javax.jdo.Extent;
import javax.jdo.Query;

import org.ecocean.*;



public class ScanTaskCleanupThread implements Runnable, ISharkGridThread {

	public Thread threadCleanupObject;
	String taskID;
	public boolean finished=false;

	/**Constructor to create a new thread object
	 * test
	 * */
	public ScanTaskCleanupThread(String taskID) {
		this.taskID=taskID;
		threadCleanupObject=new Thread(this, ("sharkGridCleanup_"+taskID));
		//threadCleanupObject.start();
	}
		

		
	/**main method of the shepherd thread*/
	public void run() {
			cleanup();
		}
	
	public boolean isFinished(){return finished;}
		
		
	public void cleanup() {
		//shepherd myShepherd=new shepherd();
		
		//myShepherd.beginDBTransaction();
		
			

				
				try{
					
					
					GridManager gm=GridManagerFactory.getGridManager();
					gm.removeWorkItemsForTask(taskID);
					gm.removeCompletedWorkItemsForTask(taskID);
					
				
					
					
				}
				catch(Exception e) {
					System.out.println("scanTaskCleanupThread: Failed on cleanup!");
					e.printStackTrace();
					//myShepherd.rollbackDBTransaction();
				}
			
			
			//myShepherd.closeDBTransaction();
			finished=true;
	}
	

		
	
		

}