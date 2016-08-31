package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;

import javax.jdo.FetchPlan;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Properties;

import org.ecocean.*;
import org.ecocean.grid.*;

import java.util.concurrent.ThreadPoolExecutor;






//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class ScanTaskHandlerAWS extends HttpServlet {


	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
  	}

	private int combinations(int n, int k) {
		int t= 1;
		for (int i= Math.min(k, n-k), l= 1; i >= 1; i--, n--, l++) {
			t*= n; t/= l;
		}
		return t;
	}



	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		doPost(request, response);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		
	  String context="context0";
    context=ServletUtilities.getContext(request);
    
	  
	  Shepherd myShepherd=new Shepherd(context);
		GridManager gm=GridManagerFactory.getGridManager();
		//set up for response
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		String action=request.getParameter("action");
		System.out.println("scanTaskHandler action is: "+action);
		
		
    
	

		if(action!=null){



			if ((action.equals("removeTask"))&&(request.getParameter("taskID")!=null)) {
				myShepherd.beginDBTransaction();
				boolean locked=false;
				//gotta check if it's a valid scanTask to begin with!

				//check for permissions to delete this scanTask
				boolean deletePermission=false;
				deletePermission=true;

				if((myShepherd.isScanTask(request.getParameter("taskID")))&&(deletePermission)){
					try {

						//change
						ThreadPoolExecutor es=SharkGridThreadExecutorService.getExecutorService();

						ScanTask st=myShepherd.getScanTask(request.getParameter("taskID"));
						myShepherd.getPM().deletePersistent(st);


						myShepherd.commitDBTransaction();

						//scanTaskCleanupThread swiThread=new scanTaskCleanupThread(request.getParameter("taskID"));
						es.execute(new ScanTaskCleanupThread(request.getParameter("taskID")));

					}
					catch(Exception e) {
						locked=true;
						System.out.println("I encounter the following error while deleting a scanTask:");
						e.printStackTrace();

						myShepherd.rollbackDBTransaction();
					}
					if(!locked) {
						//confirm success
						out.println(ServletUtilities.getHeader(request));
						out.println("<strong>Success:</strong> The scanTask <i>"+request.getParameter("taskID")+"</i> has been removed.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Return to scanTask administration page.</a></p>\n");
						out.println(ServletUtilities.getFooter(context));
					}
					else{
						out.println(ServletUtilities.getHeader(request));
						out.println("<strong>Error:</strong> The scanTask <i>"+request.getParameter("taskID")+"</i> was not removed. The task may be locked by another user or in process. Check the logs for more information.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Return to scanTask administration page.</a></p>\n");
						out.println(ServletUtilities.getFooter(context));

					}
				}
				else {
					myShepherd.rollbackDBTransaction();
					out.println(ServletUtilities.getHeader(request));
					out.println("<strong>Error:</strong> The scanTask <i>"+request.getParameter("taskID")+"</i> was not identified in the database.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Return to scanTask administration page.</a></p>\n");
					out.println(ServletUtilities.getFooter(context));
				}
			}
			
			
			
			//add new task
			else if ((action.equals("addTask"))&&(request.getParameter("encounterNumber")!=null)) {

				myShepherd.getPM().setIgnoreCache(true);

				String jdoql="";

				
				boolean locked=false;
				//String readableName="";
				boolean successfulStore=false;

				//set up our properties
				java.util.Properties props2=new java.util.Properties();
				String secondRun="true";
				String rightScan="false";
				boolean isRightScan=false;
				boolean writeThis=true;
				//String uniqueNum="";
				if(request.getParameter("writeThis")==null) {
					writeThis=false;
				}
				if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
					rightScan="true";
					isRightScan=true;
				}
				props2.setProperty("epsilon", "0.01");
				props2.setProperty("R", "8");
				props2.setProperty("Sizelim", "0.85");
				props2.setProperty("maxTriangleRotation", "10");
				props2.setProperty("C", "0.99");
				props2.setProperty("secondRun", secondRun);
				props2.setProperty("rightScan", rightScan);

				//let's check if a scanTask for this exists
				System.out.println("scanTaskHandler: Checking whether this is a new scanTask...");

				myShepherd.beginDBTransaction();
				Encounter enc=myShepherd.getEncounter(request.getParameter("encounterNumber"));
        String genus="";
        String species="";
        if(enc.getGenus()!=null){genus=enc.getGenus();}
        if(enc.getSpecificEpithet()!=null){species=enc.getSpecificEpithet();}

				String sideIdentifier="L";

				if(rightScan.equals("true")){
					sideIdentifier="R";
					//numComparisons=myShepherd.getNumEncountersWithSpotData(true);
				}
				else{
					//numComparisons=myShepherd.getNumEncountersWithSpotData(false);
				}
				String taskIdentifier="scan"+sideIdentifier+request.getParameter("encounterNumber");
				ScanTask st=new ScanTask();

				//let's do a check to see if too many scanTasks are in the queue
				int taskLimit=gm.getScanTaskLimit();
				int currentNumScanTasks=myShepherd.getNumUnfinishedScanTasks();
				myShepherd.getPM().getFetchPlan().setGroup(FetchPlan.DEFAULT);
				System.out.println("currentNumScanTasks is: "+currentNumScanTasks);
				//int currentNumScanTasks=0;
				if(currentNumScanTasks<taskLimit){

					int numComparisons=0;
					if(rightScan.equals("true")){
						//sideIdentifier="R";
						numComparisons=myShepherd.getNumEncountersWithSpotData(true);
					}
					else{
						numComparisons=myShepherd.getNumEncountersWithSpotData(false);
					}
					myShepherd.getPM().getFetchPlan().setGroup(FetchPlan.DEFAULT);

					System.out.println("scanTaskHandler: Under the limit, so proceeding to check for condiions for creating a new scanTask...");
					if((!myShepherd.isScanTask(taskIdentifier))){
						System.out.println("scanTaskHandler: This scanTask does not exist, so go create it...");

						//check if this encounter has the needed spots to create the task
						boolean hasNeededSpots=false;
						
						//if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)&&(!enc.getGenus().trim().equals(""))&&(!enc.getSpecificEpithet().trim().equals(""))){
						//  jdoql="SELECT FROM org.ecocean.ENCOUNTER WHERE genus == '"+enc.getGenus()+"' && specificEpithet == '"+enc.getSpecificEpithet()+"' ";
						//}
						
						if((rightScan.equals("true"))&&(enc.getRightSpots()!=null)){hasNeededSpots=true;}
						else if(enc.getSpots()!=null){hasNeededSpots=true;}

						if(hasNeededSpots) {

							System.out.println("scanTaskHandler: I have needed spots...proceeding...");


							st=new ScanTask(myShepherd, taskIdentifier, props2, request.getParameter("encounterNumber"), writeThis);
							//st.setNumComparisons(numComparisons-1);
							if(request.getRemoteUser()!=null){st.setSubmitter(request.getRemoteUser());}
							System.out.println("scanTaskHandler: About to create a scanTask...");
							successfulStore=myShepherd.storeNewTask(st);
							if(!successfulStore){

								System.out.println("scanTaskHandler: Unsuccessful store...");

								myShepherd.rollbackDBTransaction();
								myShepherd.closeDBTransaction();
								locked=true;
							}
							else{
								System.out.println("scanTaskHandler: Successful store...");

								myShepherd.commitDBTransaction();
								myShepherd.closeDBTransaction();
								myShepherd=new Shepherd(context);
							}
						}
						else {
							myShepherd.rollbackDBTransaction();
							myShepherd.closeDBTransaction();
							locked=true;
						}

					} else if(myShepherd.isScanTask(taskIdentifier)) {

					          System.out.println("scanTaskHandler addTask: This is an existing scanTask...");

					                //check if this is a restart
					                //if it is, delete the old work items
					                if(request.getParameter("restart")!=null){
					                  ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
					                  ScanTask restartTask=myShepherd.getScanTask(taskIdentifier);
					                  if(restartTask.getUniqueNumber().startsWith("scanR")){
					                    isRightScan=true;
					                    writeThis=restartTask.getWriteThis();

					                  }
					                  st.setFinished(false);
					                  es.execute(new ScanTaskCleanupThread(taskIdentifier));
					                  successfulStore=true;
					                  System.out.println("I have kicked off the cleanup thread.");

					                }
					                else{
					                  locked = true;
					                }


					                myShepherd.rollbackDBTransaction();
					                myShepherd.closeDBTransaction();


					                String rightFilter = "";
					                if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
					                  rightFilter = "&rightSide=true";
					                }

					                //if it exists already, advance to the scanTask administration page to await its completion
					                if(request.getParameter("restart")==null){
					                  response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/scanTaskAdmin.jsp?task="+taskIdentifier);
                }
					}
					else{
						myShepherd.rollbackDBTransaction();
						myShepherd.closeDBTransaction();
						locked=true;
					}
				}

				if(!locked&&successfulStore) {

					try{
					  
					  
					  if(request.getParameter("jdoql")!=null){jdoql=request.getParameter("jdoql");}
					  
					  System.out.println("jdoql is: "+jdoql);

						//kick off the building thread
						ThreadPoolExecutor es=SharkGridThreadExecutorService.getExecutorService();
						
						//launch EC2 instances
            es.execute(new EC2RequestThread());
						
            //now build our jobs for the task
            if(jdoql.equals("")){
              es.execute(new ScanWorkItemCreationThread(taskIdentifier, isRightScan, request.getParameter("encounterNumber"), writeThis,context, jdoql, genus, species, getServletContext(), request));
              
            }
            else{
              es.execute(new ScanWorkItemCreationThread(taskIdentifier, isRightScan, request.getParameter("encounterNumber"), writeThis,context, jdoql, null, null, getServletContext(), request));
            }
						

						


					}
					catch(Exception e) {
						System.out.println("I failed while constructing the workItems for a new scanTask.");
						e.printStackTrace();
						myShepherd.rollbackDBTransaction();
						myShepherd.closeDBTransaction();
						locked=true;
					}
					if(!locked){
						//System.out.println("Trying to commit the add of the scanWorkItems");



						//myShepherd.commitDBTransaction();
						//myShepherd.closeDBTransaction();
						//System.out.println("I committed the workItems!");

						String rightFilter="L";
						String rightURL="";
						if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
							rightFilter="R";
							rightURL="&rightSide=true";

						}

						//confirm success
						out.println(ServletUtilities.getHeader(request));
						out.println("<strong>Success:</strong> Your scan was successfully added to the sharkGrid!");
						//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/workAppletScan.jsp?number=scan"+rightFilter+request.getParameter("encounterNumber")+rightURL+"\">Start scanning for a match.</a></p>\n");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("encounterNumber")+"\">Return to encounter "+request.getParameter("encounterNumber")+".</a></p>\n");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp?task="+taskIdentifier+ "#"+taskIdentifier+"\">Go to sharkGrid administration to monitor for completion.</a></p>\n");
						out.println(ServletUtilities.getFooter(context));
					}
					else{
						out.println(ServletUtilities.getHeader(request));
						out.println("<strong>Failure:</strong> The scan could not be created or was not fully created!");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Go to sharkGrid administration.</a></p>\n");
						out.println(ServletUtilities.getFooter(context));

					}
				}
				else {
							out.println(ServletUtilities.getHeader(request));
							out.println("<strong>Failure:</strong> I have NOT added this scanTask to the queue.");
							if(currentNumScanTasks<taskLimit){
								out.println("The unfinished task limit of "+taskLimit+" has been filled. Please try adding the task to the queue again after existing tasks have finished.");
							}
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Go to sharkGrid administration.</a></p>\n");
							out.println(ServletUtilities.getFooter(context));
				}
			}



			//delete all scan-related items
			else if (action.equals("removeAllWorkItems")) {
				try{

					GridCleanupThread swiThread=new GridCleanupThread(context);
					gm.removeAllWorkItems();

					//confirm success
					out.println(ServletUtilities.getHeader(request));
					out.println("<strong>Success:</strong> I removed all outstanding scanWorkItems from the database.<br>/<strong>Warning!</strong> <em>This may cause any outstanding scanTasks to fail!</em>");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Go to sharkGrid administration.</a></p>\n");
					out.println(ServletUtilities.getFooter(context));
				}
				catch(Exception e){
					e.printStackTrace();
					myShepherd.rollbackDBTransaction();
					out.println(ServletUtilities.getHeader(request));
					out.println("<strong>Failure:</strong> I failed to remove all outstanding scanWorkItems from the database. Check the log for more information.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Go to sharkGrid administration.</a></p>\n");
					out.println(ServletUtilities.getFooter(context));

				}
			}


			else {

					out.println(ServletUtilities.getHeader(request));
					out.println("<p>I did not receive enough data to process your command, or you do not have the necessary permissions to perform this operation.</p>");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/appadmin/scanTaskAdmin.jsp\">Go to sharkGrid administration.</a></p>\n");
					out.println(ServletUtilities.getFooter(context));
			}


		}
		else {
			out.println(ServletUtilities.getHeader(request));
			out.println("<p>I did not receive enough data to process your command, or you do not have the necessary permissions to perform this operation. </p>");
			out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
			out.println(ServletUtilities.getFooter(context));
		}
			myShepherd.closeDBTransaction();
			myShepherd=null;
			out.flush();
			out.close();
		}

}
