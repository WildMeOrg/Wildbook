package org.ecocean.grid;

import java.util.*;
import org.ecocean.grid.*;

/**
 * Test comment
 * @author jholmber
 *
 */
public class AppletWorkItemThread implements Runnable {

	public Thread threadObject;
	public ScanWorkItem swi;
	public Vector results;


	/**Constructor to create a new thread object*/
	public AppletWorkItemThread(ScanWorkItem swi, Vector results) {
		this.swi=swi;
		threadObject=new Thread(this, ("sharkGrid_"+swi.getUniqueNumber()));
		threadObject.setPriority(Thread.MIN_PRIORITY);
		this.results=results;
		
	}
		

	public void run() {
			//executeComparison();
		
		System.out.println();
		try{
			org.ecocean.grid.MatchObject thisResult;
			thisResult=swi.execute();
			results.add(new ScanWorkItemResult(swi.getTaskIdentifier(), swi.getUniqueNumber(), thisResult));
			
		}
		catch(OutOfMemoryError oome){
			oome.printStackTrace();
		}
		catch(Exception e) {
			e.printStackTrace();
			
		}
	}
	

	public void nullThread(){
		swi=null;
		threadObject=null;
	}
		
	public void executeComparison() {}
	

		
	
		

}