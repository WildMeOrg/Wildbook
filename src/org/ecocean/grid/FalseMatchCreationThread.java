package org.ecocean.grid;

import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.grid.ScanWorkItem;
import java.util.Vector;
import java.util.Iterator;


public class FalseMatchCreationThread implements Runnable, ISharkGridThread{

	public Thread threadCreationObject;
	public boolean rightSide=false;
	//public boolean writeThis=true;
	public String taskID="";
	java.util.Properties props2=new java.util.Properties();
	boolean finished=false;
	GridManager gm;
	int numFalseComparisons = 0;

	/**Constructor to create a new thread object*/
	public FalseMatchCreationThread(int numFalseComparisons, String taskID) {
		this.taskID=taskID;
		//this.writeThis=writeThis;
		this.numFalseComparisons = numFalseComparisons;
		gm=GridManagerFactory.getGridManager();
		threadCreationObject=new Thread(this, ("FalseMatchCreationThread"));
		
	}
		

		
	/**main method of the shepherd thread*/
	public void run() {
			createThem();
	}
	
	public boolean isFinished(){return finished;}
		
		
	public void createThem() {
		Shepherd myShepherd=new Shepherd();
		GridManager gm=GridManagerFactory.getGridManager();
		
		String secondRun="true";
		String rightScan="false";

		//if(rightSide) {
		//	rightScan="true";
		//}
		props2.setProperty("epsilon", "0.01");
		props2.setProperty("R", "8");
		props2.setProperty("Sizelim", "0.85");
		props2.setProperty("maxTriangleRotation", "10");
		props2.setProperty("C", "0.99");
		props2.setProperty("secondRun", secondRun);
		
		//To-DO
		props2.setProperty("rightScan", "false");
		
		myShepherd.beginDBTransaction();
		//Vector<String> newSWIs=new Vector<String>();
		//Vector<scanWorkItem> addThese=new Vector<scanWorkItem>();
		System.out.println("Successfully created the scanTask shell!");
		//now, add the workItems
		int count=0;
		//myShepherd.beginDBTransaction();
		try{
			
			

				System.out.println("Iterating left-sided encounters to create scanWorkItems for FalseMatchCreationThread...");

				Iterator encs=myShepherd.getAllEncountersNoQuery();
				//Iterator encs2=myShepherd.getAllEncountersNoQuery();
				
				//keep track of already scanned combos
				StringBuffer done=new StringBuffer();
				
				while((encs.hasNext())&&(count<numFalseComparisons)){
					Encounter enc=(Encounter)encs.next();
					Iterator encs2=myShepherd.getAllEncountersNoQuery();
					if(enc.getNumSpots()>0){
						
						while((encs2.hasNext())&&(count<numFalseComparisons)){
							Encounter enc2=(Encounter)encs2.next();
							if(enc2.getNumSpots()>0){
								if(!enc2.isAssignedToMarkedIndividual().equals(enc.isAssignedToMarkedIndividual())){
									//need to check that this item hasn't already been compared
									String option1 = "*"+enc.getEncounterNumber()+"v"+enc2.getEncounterNumber()+"*";
									String option2 = "*"+enc2.getEncounterNumber()+"v"+enc.getEncounterNumber()+"*";
									if((done.indexOf(option1)==-1)&&(done.indexOf(option2)==-1)){
								
										
										String wiIdentifier=taskID+"_"+(new Integer(count)).toString();
										//System.out.println("Creating workItem: "+wiIdentifier);
										ScanWorkItem swi=new ScanWorkItem(enc, enc2, wiIdentifier,taskID,props2);
										
										//System.out.println("Elephant: "+swi.getExistingEncNumber()+" "+swi.getNewEncNumber());
										
										gm.addWorkItem(swi);
										done.append("*"+enc.getEncounterNumber()+"v"+enc2.getEncounterNumber()+"*");
										count++;
									}
							}
								
							}
						}

					}

				}
				
				//System.out.println("Elephant: switching to right side...");
				Iterator encs3=myShepherd.getAllEncountersNoQuery();

				done=new StringBuffer();
				props2.setProperty("rightScan", "true");
				
				while((encs3.hasNext())&&(count<(2*numFalseComparisons))){
					Encounter enc=(Encounter)encs3.next();
					
					if(enc.getNumRightSpots()>0){
						Iterator encs2=myShepherd.getAllEncountersNoQuery();
						while((encs2.hasNext())&&(count<(2*numFalseComparisons))){
							Encounter enc2=(Encounter)encs2.next();
							if(enc2.getNumRightSpots()>0){
								if(!enc2.isAssignedToMarkedIndividual().equals(enc.isAssignedToMarkedIndividual())){
								//need to check that this item hasn't already been compared
								String option1 = "*"+enc.getEncounterNumber()+"v"+enc2.getEncounterNumber()+"*";
								String option2 = "*"+enc2.getEncounterNumber()+"v"+enc.getEncounterNumber()+"*";
								if((done.indexOf(option1)==-1)&&(done.indexOf(option2)==-1)){
								
									String wiIdentifier=taskID+"_"+(new Integer(count)).toString();
									ScanWorkItem swi=new ScanWorkItem(enc, enc2, wiIdentifier,taskID,props2);
									gm.addWorkItem(swi);
									//System.out.println("RElephant: "+swi.getExistingEncNumber()+" "+swi.getNewEncNumber());
									
									done.append("*"+enc.getEncounterNumber()+"v"+enc2.getEncounterNumber()+"*");
									count++;
								}
								}
							}
						}

					}

				}

			//System.out.println("Trying to commit the add of the False Match Tuning Task scanWorkItems after leaving loop");
			myShepherd.commitDBTransaction();
			myShepherd.closeDBTransaction();
			finished=true;
		} 
		catch(Exception e) {
			System.out.println("I failed while constructing the workItems for a new FalseMatchScanTask.");
			e.printStackTrace();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
		}
		
	}
		

}