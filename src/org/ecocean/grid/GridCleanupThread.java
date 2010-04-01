package org.ecocean.grid;


import java.util.*;
import java.io.*;

import javax.jdo.Extent;
import javax.jdo.Query;

import com.reijns.I3S.*;

import org.ecocean.Shepherd;


public class GridCleanupThread implements Runnable{

	public Thread threadCleanupObject;


	/**Constructor to create a new thread object*/
	public GridCleanupThread() {

		threadCleanupObject=new Thread(this, "gridCleanup");
		threadCleanupObject.start();
	}
		

		
	/**main method of the shepherd thread*/
	public void run() {
			cleanup();
		}
		
		
	public void cleanup() {
		Shepherd myShepherd=new Shepherd();
		
		myShepherd.beginDBTransaction();
		
			
			
			//Iterator matchObjects=myShepherd.getAllMatchObjectsNoQuery();
			//int count=0;
			/*while (matchObjects.hasNext()) {
				
				try{
					matchObject mo=(matchObject)matchObjects.next();
					myShepherd.throwAwayMatchObject(mo);
					count++;
					if((count % 5)==0){
						myShepherd.commitDBTransaction();
						myShepherd.beginDBTransaction();
					}	
				}
				catch(Exception e) {
					System.out.println("I failed while constructing the workItems for a new scanTask.");
					e.printStackTrace();
					myShepherd.rollbackDBTransaction();
					myShepherd.beginDBTransaction();
				}
			}*/
			
			//Iterator vpms=myShepherd.getAllPairsNoQuery();
			Extent encClass=myShepherd.getPM().getExtent(Pair.class, true);
			Query query=myShepherd.getPM().newQuery(encClass);
			int count=0;
			int size=1;
			while (size>0) {
				try{
					
					ArrayList pairs=myShepherd.getPairs(query, 50);
					size=pairs.size();
					for(int m=0;m<size;m++){
						Pair mo=(Pair)pairs.get(m);
						myShepherd.getPM().deletePersistent(mo);
					}
					myShepherd.commitDBTransaction();
					myShepherd.beginDBTransaction();
				}
				catch(Exception e) {
					System.out.println("I failed while constructing the workItems for a new scanTask.");
					e.printStackTrace();
					myShepherd.rollbackDBTransaction();
					myShepherd.beginDBTransaction();
				}
			}
			
			myShepherd.commitDBTransaction();
			myShepherd.closeDBTransaction();
	}
	

		
	
		

}