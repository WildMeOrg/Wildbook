<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,
org.ecocean.media.MediaAsset,
javax.jdo.Query,
org.json.JSONArray,
java.util.Set,
java.util.HashSet,
java.util.List,
java.util.Collection,
java.util.ArrayList,
java.util.Iterator,
org.ecocean.security.Collaboration,
java.util.HashMap,
org.ecocean.ia.Task,
java.util.HashMap,
java.util.LinkedHashSet,
org.ecocean.identity.*,
org.ecocean.metrics.*,
org.ecocean.ia.IA,
org.ecocean.ia.WbiaQueueUtil,
java.util.Collections,java.util.Comparator,
org.json.JSONObject,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>

<%!
public static void intakeTask(Shepherd myShepherd, Task task) {

    if ((task.getObjectAnnotations() == null) || (task.getObjectAnnotations().size() < 1)) {
    	System.out.println("!!!!!!Task was empty. What the heck?");
    	return;
    }

    String context = myShepherd.getContext();

    //List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
    IAJsonProperties iaConfig = IAJsonProperties.iaConfig();

        List<JSONObject> opts = iaConfig.identOpts(myShepherd, task.getObjectAnnotations().get(0));
        //now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
        if (opts != null) {
            Iterator<JSONObject> itr = opts.iterator();
            while (itr.hasNext()) {
                if (!itr.next().optBoolean("default", true)) itr.remove();
            }
        }

        System.out.println("identOpts: "+opts);
        JSONObject newTaskParams = new JSONObject();  //we merge parentTask.parameters in with opts from above
  
          newTaskParams = task.getParameters();
          System.out.println("newTaskParams: "+newTaskParams.toString());
          if(newTaskParams.optJSONArray("matchingAlgorithms")!=null) {
            JSONArray matchingAlgorithms=newTaskParams.optJSONArray("matchingAlgorithms");
            System.out.println("matchingAlgorithms1: "+matchingAlgorithms.toString());
            ArrayList<JSONObject> newOpts=new ArrayList<JSONObject>();
            int maLength=matchingAlgorithms.length();
            for(int y=0;y<maLength;y++) {
              newOpts.add(matchingAlgorithms.getJSONObject(y));
            }
            System.out.println("matchingAlgorithms2: "+newOpts.toString());
            if(newOpts.size()>0) {
              opts=newOpts;
              System.out.println("Swapping opts for newOpts!!");
            }


          }
 
        if ((opts == null) || (opts.size() < 1)) {
        	System.out.println("returning opts=null");
        	return;
        }  // no ID for this iaClass.

        // just one IA class, one algorithm case
        
          newTaskParams.put("ibeis.identification", ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
          task.setParameters(newTaskParams);

        boolean fastlane=task.isFastlane(myShepherd);
        newTaskParams.put("fastlane", fastlane);
        if(fastlane)newTaskParams.put("lane", "fast");

        //these are re-used in every task
        JSONArray annArr = new JSONArray();
        annArr.put(task.getObjectAnnotations().get(0).getId());
        JSONObject aj = new JSONObject();
        aj.put("annotationIds", annArr);
        String baseUrl = IA.getBaseURL(context);

        for (int i = 0 ; i < opts.size() ; i++) {
            JSONObject qjob = new JSONObject();
            qjob.put("identify", aj);
            qjob.put("taskId", task.getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            if (opts.get(i) != null) qjob.put("opt", opts.get(i));
            boolean sent = false;
            try {
              if(fastlane) {
                //if fastlane and a smaller, bespoke request, get this into the faster queue
                qjob.put("fastlane", fastlane);
                qjob.put("lane", "fast");
                task.setQueueResumeMessage(qjob.toString());
                sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
              }
              else {
            	task.setQueueResumeMessage(qjob.toString());
                sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
              }
            } catch (java.io.IOException iox) {
                System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
            }
	System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " + task.getObjectAnnotations().size() + " annots; queued? = " + sent + "; " + task);
        }
  
	System.out.println("INFO: IA.intakeAnnotations() finished as " + task);
    
}

%>


<%!
public String dumpTask(Task task) {
    if (task == null) return "NULL TASK";
    Task rootTask = task.getRootTask();
    String t = "=====================================\n" + task.getId() + "\nparams: " + task.getParameters() + "\n";
    t += "- rootTask                     " + (task.equals(rootTask) ? "(this)" : rootTask.getId()) + "\n";
    t += "- parent:                      " + ((task.getParent() == null) ? "NONE" : task.getParent().getId()) + "\n";
    if (task.hasChildren()) {
        t += "- children                    ";
        for (Task kid : task.getChildren()) {
            t += " " + kid.getId();
        }
        t += "\n";
    } else {
        t += "- children                     NONE\n";
    }
    t += "- countObjectMediaAssets:      " + task.countObjectMediaAssets() + "\n";
    t += "- countObjectAnnotations:      " + task.countObjectAnnotations() + "\n";
    return t;
}
%>



<%!
public String getOverallStatus(Task task,Shepherd myShepherd, HashMap<String,Integer> idStatusMap, HttpServletRequest request){
	String status="unknown";
	//resumeStalledTasks
	boolean resumeStalledTasks = false;
	String queueStatusToFix = "";
	if(request.getParameter("resumeStalledTasks")!=null){
		resumeStalledTasks=true;
		queueStatusToFix=request.getParameter("resumeStalledTasks");
	}

	
	
	if(task.hasChildren()){
		//accumulate status across children
		HashMap<String,String> map=new HashMap<String,String>();
		//this should only ever be two layers deep
		for(Task childTask:task.getChildren()){
			if(childTask.hasChildren()){
				for(Task childTask2:childTask.getChildren()){
					if(childTask2.getObjectAnnotations()!=null && childTask2.getObjectAnnotations().size()>0
							&& childTask2.getObjectAnnotations().get(0).getMatchAgainst() && childTask2.getObjectAnnotations().get(0).getIAClass()!=null){
								map.put(childTask2.getId(),childTask2.getStatus(myShepherd));
								//if resume
								if(resumeStalledTasks && childTask2.getStatus(myShepherd).equals(queueStatusToFix)){
									System.out.println("Requeuing task: "+childTask2.getId());
									intakeTask(myShepherd, childTask2); 
								}
					}
					

					
				}
			}
			else{
				if(childTask.getObjectAnnotations()!=null && childTask.getObjectAnnotations().size()>0
						&& childTask.getObjectAnnotations().get(0).getMatchAgainst() && childTask.getObjectAnnotations().get(0).getIAClass()!=null){
							map.put(childTask.getId(),childTask.getStatus(myShepherd));
							
							
							//if resume
							if(resumeStalledTasks && childTask.getStatus(myShepherd).equals(queueStatusToFix)){
								System.out.println("Requeuing task: "+childTask.getId());
								intakeTask(myShepherd, childTask); 
							}
				}

			}
		}
		
		//now, how do we report these?
		HashMap<String, Integer> resultsMap=new HashMap<String,Integer>();
		for(String key:map.values()){
			
			//overall ID results
			if(!idStatusMap.containsKey(key)){idStatusMap.put(key, new Integer(1));}
			else{
				int results=idStatusMap.get(key);
				idStatusMap.put(key, new Integer(results+1));
			}
			
			//task results
			if(!resultsMap.containsKey(key)){resultsMap.put(key, new Integer(1));}
			else{
				int results=resultsMap.get(key);
				resultsMap.put(key, new Integer(results+1));
			}
			
		}
	    return resultsMap.toString();
		
	}
	else{
		status=task.getStatus(myShepherd);
		//overall ID results
		if(!idStatusMap.containsKey(status)){idStatusMap.put(status, new Integer(1));}
		else{
			int results=idStatusMap.get(status);
			idStatusMap.put(status, new Integer(results+1));
		}
	}
	
	return status;
}

%>


<jsp:include page="header.jsp" flush="true"/>

<script>

function confirmCommit() {
	return confirm("Send to IA? This process may take a long time and block other users from using detection and ID quickly.");
}

function confirmCommitID() {
	return confirm("Send to ID? This process may take a long time and block other users from using detection and ID quickly.");
}

function confirmDelete() {
	return confirm("Delete this ImportTask PERMANENTLY? Please consider carefully whether you want to delete all of this imported data.");
}

</script>

<style>
.bootstrap-table {
    height: min-content;
}
.dim, .ct0 {
    color: #AAA;
}

.yes {
    color: #0F5;
}
.no {
    color: #F20;
}

a.button {
    font-weight: bold;
    font-size: 0.9em;
    background-color: #AAA;
    border-radius: 4px;
    padding: 0 6px;
    text-decoration: none;
    cursor: pointer;
}
a.button:hover {
    background-color: #DDA;
    text-decoration: none;
}

.ia-match-filter-dialog .option-cols {
	-webkit-column-count: 1;
	-moz-column-count: 1;
	column-count: 1;
}
.ia-match-filter-dialog .option-cols input {
	vertical-align: top;
}
.ia-match-filter-dialog .option-cols .item {
	padding: 1px 4px;
	border-radius: 5px;
}
.ia-match-filter-dialog .option-cols .item:hover {
	background-color: #AAA;
}
.ia-match-filter-dialog .option-cols .item label {
	font-size: 0.9em;
	width: 90%;
	margin-left: 5px;
	line-height: 1.0em;
}
.ia-match-filter-dialog .option-cols .item-checked label {
	font-weight: bold;
}
.ia-match-filter-dialog ul {
	list-style-type: none;
}
.ia-match-filter-dialog .item-count {
	font-size: 0.8em;
	color: #777;
	margin-left: 9px;
}
.ia-match-filter-section {
	margin-top: 10px;
	border-top: solid 3px #999;
}
.ia-match-filter-title {
	margin: 20px 0 5px 0;
	padding: 1px 0 1px 20px;
	background-color: #b491c8;
	color: #555;
	font-weight: bold;
}
.ia-match-filter-dialog {
	display: none;
	z-index: 3000;
	position: fixed;
	top: 10%;
	width: 80%;
	padding: 15px;
	border: solid 5px #888;
	background-color: #fff;
}


</style>


    <script src="javascript/bootstrap-table/bootstrap-table.min.js"></script>
    <link rel="stylesheet" href="javascript/bootstrap-table/bootstrap-table.min.css" />


<div class="container maincontent">

<%

if(request.getParameter("taskId")==null){
    out.println("<h1 class=\"error\">No ?taskId= parameter was specified.</h1>");
    return;
}

String taskId = request.getParameter("taskId");

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("import.jsp");
myShepherd.beginDBTransaction();
String langCode = ServletUtilities.getLanguageCode(request);
Properties encprops = ShepherdProperties.getOrgProperties("encounter.properties", langCode, context, request, myShepherd);

//should the user see the detect and/or detect+ID buttons?
boolean allowIA=false;
boolean allowReID=false;
String iaStatusString="not started";
boolean shouldRefresh=false;
String refreshSeconds="60";

//track overall ID progress
HashMap<String,Integer> idStatusMap=new HashMap<String,Integer>();

try{
	
	//commented our because it duplicates what web.xml should be doing
	User user = AccessControl.getUser(request, myShepherd);

	boolean adminMode = false;
	if(request.isUserInRole("admin"))adminMode=true;
	//boolean forcePushIA=false;
	
	//admins can force the detection option to appear no matter the state
	if(adminMode&&request.getParameter("forceDetection")!=null)allowIA=true;
	
	  //handle some cache-related security
	  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
	  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
	  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
	  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
	
	  //gather details about WBIA queue
	  	String queueStatement="";
		int wbiaIDQueueSize = WbiaQueueUtil.getSizeIDJobQueue(false);
		int wbiaDetectionQueueSize = WbiaQueueUtil.getSizeDetectionJobQueue(false);
		if(wbiaIDQueueSize==0 && wbiaDetectionQueueSize == 0){
			queueStatement = "The bulk import machine learning queue is empty and ready for work.";
		}
		else if(Prometheus.getValue("wildbook_wbia_turnaroundtime")!=null){
	  		String val=Prometheus.getValue("wildbook_wbia_turnaroundtime");
	  		try{
	  			Double d = Double.parseDouble(val);
	  			d=d/60.0;
	  			queueStatement = "There are currently "+(wbiaIDQueueSize+wbiaDetectionQueueSize)+" jobs in the bulk import machine learning queue. Each job is averaging a turnaround time of "+(int)Math.round(d)+" minutes.";
	  		}
	  		catch(Exception de){de.printStackTrace();}
	  	}
	  	String queueStatementDetection="";
	  	if(Prometheus.getValue("wildbook_wbia_turnaroundtime_detection")!=null){
	  		String val=Prometheus.getValue("wildbook_wbia_turnaroundtime_detection");
	  		try{
	  			Double d = Double.parseDouble(val);
	  			d=d/60.0;
	  			queueStatementDetection = "Each detection job in the queue is currently averaging a turnaround time of "+(int)Math.round(d)+" minutes.";
	  		}
	  		catch(Exception de){de.printStackTrace();}
	  	}
	  	String queueStatementID="";
	  	if(Prometheus.getValue("wildbook_wbia_turnaroundtime_id")!=null){
	  		String val=Prometheus.getValue("wildbook_wbia_turnaroundtime_id");
	  		try{
	  			Double d = Double.parseDouble(val);
	  			d=d/60.0;
	  			queueStatementID = "Each ID job in the queue is currently averaging a turnaround time of "+(int)Math.round(d)+" minutes.";
	  		}
	  		catch(Exception de){de.printStackTrace();}
	  	}
	  
	
	
	
	
	
	String jdoql = null;
	ImportTask itask = null;
	
    try {
        itask = (ImportTask) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(ImportTask.class, taskId), true));
    } catch (Exception ex) {
    	ex.printStackTrace();
    }
    
    
    
    if (itask == null) {
        out.println("<h1 class=\"error\">Your bulk import may not be ready for viewing yet, or task ID " + taskId + " may be invalid.</h1><p>Try refreshing this page if you arrived on this page from an import that you just kicked off.</p>");
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
    }
	
    //security checks
	if (!adminMode&&!ServletUtilities.isUserAuthorizedForImportTask(itask, request, myShepherd)) {
		out.println("<h1 class=\"error\">Access denied.</h1>");
		response.sendError(401, "access denied");
    	myShepherd.rollbackDBTransaction();
    	myShepherd.closeDBTransaction();
    	return;
	}
	

	
	String dumpTask="empty";
	if(itask!=null && itask.getIATask()!=null){
		dumpTask=dumpTask(itask.getIATask());
	}
	
	
	ArrayList<String> locationIds = new ArrayList<String>();

	    out.println("<p><b style=\"font-size: 1.2em;\">Import Task " + itask.getId() + "</b> (" + itask.getCreated().toString().substring(0,10) + ") <a class=\"button\" href=\"imports.jsp\">back to list</a></p>");
	    out.println("<br>Data Import Status: <em>"+itask.getStatus()+"</em>");
	    out.println("<br>Computer Vision Status: <em><span id=\"iaStatus\"></span></em>");
	    
	    out.println("<p id=\"refreshPara\" class=\"caption\">Refreshing results in <span id=\"countdown\"></span> seconds.</p><script>$('#refreshPara').hide();</script>");
	    
	    if(itask.getParameters()!=null){
	    	out.println("<br>Filename: "+itask.getParameters().getJSONObject("_passedParameters").getJSONArray("filename").toString());
	    }	
	    out.println("<br><table id=\"import-table-details\" xdata-page-size=\"6\" xdata-height=\"650\" data-toggle=\"table\" data-pagination=\"false\" ><thead><tr>");
	    String[] headers = new String[]{"Encounter", "Date", "Occurrence", "Individual", "#Images","Match Results by Class"};
	    if (adminMode) headers = new String[]{"Encounter", "Date", "User", "Occurrence", "Individual", "#Images","Match Results by Class"};
	    for (int i = 0 ; i < headers.length ; i++) {
	        out.println("<th data-sortable=\"true\">" + headers[i] + "</th>");
	    }
	
	    out.println("</tr></thead><tbody>");
	    
	
	    List<MediaAsset> allAssets = new ArrayList<MediaAsset>();
	    HashMap<HashMap<String,String>,Integer> annotsMap=new HashMap<HashMap<String,String>,Integer>();
	    List<String> allIndies = new ArrayList<String>();
	    int numIA = 0;
	    int numAnnotations=0;
	    int numMatchAgainst=0;
	    boolean foundChildren = false;
	    int numMatchTasks=0;



	HashMap<String,JSONArray> jarrs = new HashMap<String,JSONArray>();
	    if (Util.collectionSize(itask.getEncounters()) > 0) {
	    	for (Encounter enc : itask.getEncounters()) {
	    		
	    		
	    	//setup self-heal missing acmIDs
	    	ArrayList<MediaAsset> fixACMIDAssets=new ArrayList<MediaAsset>();
	       
	    	JSONArray jarr=new JSONArray();
	    	if (enc.getLocationID() != null && !locationIds.contains(enc.getLocationID())) locationIds.add(enc.getLocationID());
	        out.println("<tr>");
	        out.println("<td><a title=\"" + enc.getCatalogNumber() + "\" href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">" + enc.getCatalogNumber().substring(0,8) + "</a></td>");
	        out.println("<td>" + enc.getDate() + "</td>");
	        if (adminMode) {
	            List<User> subs = enc.getSubmitters();
	            if (Util.collectionSize(subs) < 1) {
	                out.println("<td class=\"dim\">-</td>");
	            } else {
	                List<String> names = new ArrayList<String>();
	                for (User u : subs) {
	                    names.add(u.getDisplayName());
	                }
	                out.println("<td>" + String.join(", ", names) + "</td>");
	            }
	        }
	        if (enc.getOccurrenceID() == null) {
	            out.println("<td class=\"dim\">-</td>");
	        } else {
	            out.println("<td><a title=\"" + enc.getOccurrenceID() + "\" href=\"occurrence.jsp?number=" + enc.getOccurrenceID() + "\">" + (Util.isUUID(enc.getOccurrenceID()) ? enc.getOccurrenceID().substring(0,8) : enc.getOccurrenceID()) + "</a></td>");
	        }
	        if (enc.getIndividual()!=null) {
	            out.println("<td><a title=\"" + enc.getIndividual().getIndividualID() + "\" href=\"individuals.jsp?number=" + enc.getIndividual().getIndividualID() + "\">" + enc.getIndividual().getDisplayName(request, myShepherd) + "</a></td>");
	            if(!allIndies.contains(enc.getIndividual().getIndividualID()))allIndies.add(enc.getIndividual().getIndividualID());
	        } else {
	            out.println("<td class=\"dim\">-</td>");
	        }
	
	        //MediaAssets
	        ArrayList<MediaAsset> mas = enc.getMedia();
	        
	        //de-duplicate MediaAssets
	        Set<MediaAsset> set = new LinkedHashSet<MediaAsset>();
	        set.addAll(mas);
	        mas.clear();
	        mas.addAll(set);
	        
	        
	        if (Util.collectionSize(mas) < 1) {
	            out.println("<td class=\"dim\">0</td>");
	        } else {
	            out.println("<td>" + Util.collectionSize(mas) + "</td>");
	            for (MediaAsset ma : mas) {
	                if (!allAssets.contains(ma)) {
	                    allAssets.add(ma);
	                    jarr.put(ma.getId());
	                    if (ma.getDetectionStatus() != null) numIA++;
	                    
	                    //check acmID and build self-heal list if its missing due to an unexpected WBIA outtage
	                    if("complete".equals(itask.getStatus()) && ma.getAcmId()==null){
	                    	if(!fixACMIDAssets.contains(ma))fixACMIDAssets.add(ma);
	                    }
	                    
	                }
	                if (!foundChildren && (Util.collectionSize(ma.findChildren(myShepherd)) > 0)) foundChildren = true; //only need one
	            }
	            
	            //self-heal missing acmIDs if needed
                if(fixACMIDAssets.size()>0 && "complete".equals(itask.getStatus())){
                	System.out.println("Self-healing acmIDs in import task: "+itask.getId());
                	refreshSeconds="300";  //give more time for these asynch registrations to occur

                	//IBEISIA.sendMediaAssetsNew(fixACMIDAssets, context);

                }
	        }
	        
	        //let's do some annotation tabulation
	        ArrayList<Task> tasks=new ArrayList<Task>();
	        HashMap<String,String> annotTypesByTask=new HashMap<String,String>();
	        for(Annotation annot:enc.getAnnotations()){
	        	if(!annot.isTrivial()){
		        	String viewpoint="null";
		        	String iaClass="null";
		        	if(annot.getViewpoint()!=null)viewpoint=annot.getViewpoint();
		        	if(annot.getIAClass()!=null)iaClass=annot.getIAClass();
		        	HashMap<String,String> thisMap=new HashMap<String,String>();
		        	thisMap.put(iaClass,viewpoint);
		        	if(!annotsMap.containsKey(thisMap)){
		        		annotsMap.put(thisMap,Integer.parseInt("1"));
		        		numAnnotations++;
		        	}
		        	else{
		        		Integer numInts = annotsMap.get(thisMap);
		        		numInts = new Integer(numInts.intValue() + 1);
		        		annotsMap.put(thisMap,numInts);
		        		numAnnotations++;
		        	}
		        	if(annot.getMatchAgainst())numMatchAgainst++;
		        	
		        	//let's look for match results we can easily link for the user
	                        List<Task> relatedTasks = Task.getTasksFor(annot, myShepherd);
		     
		        			
	                        if(relatedTasks!=null && relatedTasks.size()>0){
	                        	
	
	                        
	                            for(Task task:relatedTasks){
	                            	
	                            	if(task.getParent()!=null && task.getParent().getChildren().size()==1 && task.getParameters()!=null && task.getParameters().has("ibeis.identification")){
		                            	//System.out.println("I am a task with only one algorithm");
	                            		if(!tasks.contains(task)){
			        						tasks.add(task);
			        						annotTypesByTask.put(task.getId(),iaClass);
			        					}
	                            	}
	                            	else if(task.getChildren()!=null && task.getChildren().size()>0 && (task.getParent()!=null && task.getParent().getChildren().size()<=1)){
	                            		//System.out.println("I am a task with child ID tasks.");
		                            	if(!tasks.contains(task)){
			        						tasks.add(task);
			        						annotTypesByTask.put(task.getId(),iaClass);
			        					}
	                            	}
	                                else if(task.getChildren()!=null && task.getChildren().size()>2 && task.getParent()==null){
	                                    //System.out.println("I am a task with child ID tasks.");
	                                    if(!tasks.contains(task)){
	                                        tasks.add(task);
	                                        annotTypesByTask.put(task.getId(),iaClass);
	                                     }
	                                  }
		        		}
		        	}		
		        	
	        	} //end if
	        } //end for
	        
	        out.println("<td>");
	        if(tasks.size()>0){
	        	
            	//put the newest tasks at the top
                Collections.sort(tasks, new Comparator<Task>() {
                    @Override public int compare(Task tsk1, Task tsk2) {
                        return Long.compare(tsk1.getCreatedLong(), tsk2.getCreatedLong()); // first asc
                    }
                });
                Collections.reverse(tasks); 		
	        	
	        	//System.out.println("Num tasks: "+tasks.size());
	        	out.println("     <ul>");
	        	//for(Task task:tasks){
	        		out.println("          <li><a target=\"_blank\" href=\"iaResults.jsp?taskId="+tasks.get(0).getId()+"\" >"+annotTypesByTask.get(tasks.get(0).getId())+": "+getOverallStatus(tasks.get(0),myShepherd,idStatusMap,request)+"</a>");
	        	//}
	        	out.println("     </ul>");
	        	numMatchTasks++;
	        }			
	        out.println("</td>");
	
	        out.println("</tr>");
	        
	        jarrs.put(enc.getCatalogNumber(), jarr);
	        
	    	}



			int percent = -1;
	    if (allAssets.size() > 1) percent = Math.round(numIA / allAssets.size() * 100);
	%>
	</tbody></table>
	<p>
	<strong>Summary</strong><br>
		Total marked individuals: <%=allIndies.size() %><br>
		Total encounters: <%=itask.getEncounters().size() %>
	</p>

	
	<%
	try{
		int numWithACMID=0;
		int numAllowedIA=0;
		int numDetectionComplete=0;
		ArrayList<MediaAsset> invalidMediaAssets=new ArrayList<MediaAsset>();
		for(MediaAsset asset:allAssets){
			if(asset.getAcmId()!=null)numWithACMID++;
			
			//check if we can get validity off the image before the expensive check of hitting the AssetStore
			if(asset.isValidImageForIA()!=null){
				if(asset.isValidImageForIA().booleanValue()){numAllowedIA++;}
			}
			else if(asset.validateSourceImage()){numAllowedIA++;myShepherd.updateDBTransaction();}
			
			if(asset.isValidImageForIA() == null || !asset.isValidImageForIA().booleanValue()){
				invalidMediaAssets.add(asset);
			}
			
			
			if(asset.getDetectionStatus()!=null && (asset.getDetectionStatus().equals("complete")||asset.getDetectionStatus().equals("pending"))) numDetectionComplete++;
		}
		%>
		<p>
		Total media assets: <%=allAssets.size()%><br>
		<ul>
			<li>Number with acmIDs: <%=numWithACMID %></li>
			<li>Number valid for image analysis: <%=numAllowedIA %></li>
			
			<%
			if("complete".equals(itask.getStatus()) && invalidMediaAssets.size()>0){
			%>
			<li>Number invalid for image analysis: <%=invalidMediaAssets.size()%>
					<ol>
					<%
					for(MediaAsset inv_asset:invalidMediaAssets){
					%>
						<li><a target="_blank" href="obrowse.jsp?type=MediaAsset&id=<%=inv_asset.getId() %>"><%=inv_asset.getId() %></a></li>
					<%
					}
					%>
					</ol>
				</li>
			<%
			}
			%>
			<li>Number that have completed detection: <%=numDetectionComplete %></li>
		</ul>
	</p>
	<p>Total annotations: <%=numAnnotations %> (<%=numMatchAgainst %> matchable)
		<ul>
			<%
			Set<HashMap<String,String>> annotSet=annotsMap.keySet();
			Iterator<HashMap<String,String>> iterAnnots=annotSet.iterator();
			while(iterAnnots.hasNext()){
				HashMap<String,String> mapHere = iterAnnots.next();
				if(mapHere.toString().equals("{null=null}") ){
					%>
					<li>Nothing found: <%=annotsMap.get(mapHere) %></li>
					<%
				}
				else{
					%>
					<li><%=mapHere.toString() %>: <%=annotsMap.get(mapHere) %></li>
					<%
				}
			}
			%>
			
		</ul>
		</p>
	<%
	
	//let's determine the IA Status
	
	if("complete".equals(itask.getStatus()) && (itask.getIATask()==null))allowIA=true;

	
	//let's check shouldRefresh logic
	if(itask.getStatus()!=null && !itask.getStatus().equals("complete"))shouldRefresh=true;
	
	if (itask.getIATask() !=null && itask.iaTaskStarted()) {
        //detection-only Task
		//if(hasIdentificationBenRun(itask)){
		if(!itask.iaTaskRequestedIdentification()){
        	if(numDetectionComplete==numAllowedIA){
        		iaStatusString="detection complete";
        	}
        	else{
        		iaStatusString="detection requests sent ("+numDetectionComplete+"/"+allAssets.size()+" complete). " +queueStatementDetection;
        		shouldRefresh=true;
        	}
        }
        //ID Task
        else{
        	
        	//let's tabulate ID status map for complete
        	int numComplete = 0;
        	int numTotal = 0;		
        	if(idStatusMap.get("completed")!=null){numComplete=idStatusMap.get("completed");}
        	for(Integer key:idStatusMap.values()){
        		numTotal+=key;
        	}
        	String idStatusString="";
        	if(numTotal>0)idStatusString=numComplete+" individual computer vision tasks complete of "+numTotal+" total. ";
        	
        	if(numComplete==numTotal)shouldRefresh=false;
        
        	iaStatusString="identification requests sent (see table below for links to each matching job). "+idStatusString+queueStatementID;
        	if(numMatchTasks<numMatchAgainst)shouldRefresh=true;
        	
        }
    }
	//let's handle legacy data
	else if(itask.getIATask() == null && numDetectionComplete>0 ){
    	if(numDetectionComplete==allAssets.size()){
    		iaStatusString="detection complete";
    		allowIA=false;
    	}
    	else{
    		iaStatusString="detection requests sent ("+numDetectionComplete+"/"+allAssets.size()+" complete)";
    		shouldRefresh=true;
    		allowIA=false;
    	}
    	if(numMatchTasks>0){
    		iaStatusString="identification requests sent (see below)";
        	if(numMatchTasks<numMatchAgainst)shouldRefresh=true;
        	allowIA=false;
    	}
	}
	if(request.getParameter("resumeStalledTasks")!=null){
		shouldRefresh=false;
	}

	
	%>
	<script>
		$('#iaStatus').text('<%=iaStatusString %>');
	</script>
	<%
	
    //START refresh function
    //if incomplete or IA in process - refresh
    if(shouldRefresh){
    %>
	    <script type="text/javascript">
	    	$('#refreshPara').show();
	  		(function countdown(remaining) {
		    	if(remaining === 0)location.reload(true);
		    	document.getElementById('countdown').innerHTML = remaining;
		    	setTimeout(function(){ countdown(remaining - 1); }, 1000);
	
			})(<%=refreshSeconds  %>);	
	 	</script>
	    
	    <%
    }
    //END refresh function
	
	
	}
	catch(Exception n){
		n.printStackTrace();
	}
	%>
	
	
	
	<script>
	let js_jarrs = new Map();
	<%
	for(String key:jarrs.keySet()){
	%>
		js_jarrs.set('<%=key %>',<%=jarrs.get(key).toString() %>);
	<%
	}
	%>
	
	function sendToIA(skipIdent) {
	    if (!confirmCommit()) return;
	    $('#ia-send-div').hide().after('<div id="ia-send-wait"><i>sending... <b>please wait</b></i></div>');
	    var locationIds = $('#id-locationids').val();
	
	    // some of this borrowed from core.js sendMediaAssetsToIA()
	    // but now we send bulkImport as the entire js_jarrs value
	    var data = {
	        taskParameters: { skipIdent: skipIdent || false, importTaskId: '<%=taskId%>' },
	        bulkImport: {}
	    };
	    for (let [encId, maIds] of js_jarrs) { data.bulkImport[encId] = maIds; }  // convert js_jarrs map into js object
	    if (!skipIdent && locationIds && (locationIds.indexOf('') < 0)) data.taskParameters.matchingSetFilter = { locationIds: locationIds };

		if ($('#match-filter-owner-me').is(':checked')){
			if(!data.taskParameters.matchingSetFilter) data.taskParameters.matchingSetFilter = {};
			data.taskParameters.matchingSetFilter["owner"] = ["me"]
		}
	
	    console.log('sendToIA() SENDING: locationIds=%o data=%o', locationIds, data);
	    $.ajax({
	        url: wildbookGlobals.baseUrl + '/ia',
	        dataType: 'json',
	        data: JSON.stringify(data),
	        type: 'POST',
	        contentType: 'application/javascript',
	        complete: function(x) {
	            console.log('sendToIA() response: %o', x);
		    if ((x.status == 200) && x.responseJSON && x.responseJSON.success) {
		        $('#ia-send-wait').html('<i>Images sent successfully. Refresh this page to track progress.</i>');
		    } else {
		        $('#ia-send-wait').html('<b class="error">an error occurred while sending to identification</b>');
		    }
	        }
	    });
	}
	
	function resendToID() {
	    if (!confirmCommitID()) return;
	    $('#ia-send-div').hide().after('<div id="ia-send-wait"><i>sending... <b>please wait</b></i></div>');
	    //var locationIds = $('#id-locationids').val();
	    var locationIds = '';
		var owner = '';
	    $("#id-locationids option:selected").each(function(){
	    	locationIds+='&locationID='+this.value;
	    });
	    if(locationIds.indexOf('ALL locations')>-1)locationIds='';
	    //if (locationIds && (locationIds.indexOf('') < 0)) data.taskParameters.matchingSetFilter = { locationIds: locationIds };
	
	    console.log('resendToID() SENDING: locationIds=%o', locationIds);

		if ($('#match-filter-owner-me').is(':checked')){
			owner = "&owner=" + encodeURIComponent(JSON.stringify(["me"]));
		}
	    
	    $.ajax({
	        url: wildbookGlobals.baseUrl + '/appadmin/resendBulkImportID.jsp?importIdTask=<%=taskId%>'+locationIds + owner,
	        dataType: 'json',
	        type: 'GET',
	        contentType: 'application/javascript',
	        complete: function(x) {
	            console.log('resendToID() response: %o', x);
		    if ((x.status == 200) && x.responseJSON && x.responseJSON.success) {
		        $('#ia-send-wait').html('<i>ID requests resubmitted successfully. Refresh this page to track progress.</i>');
		    } else {
		        $('#ia-send-wait').html('<b class="error">an error occurred while sending to identification</b>');
		    }
	        }
	    });
	    
	}

	function shouldselectAllOptions(shouldselect){
		$("#id-locationids option").each(function(index,option){
			if(option.value){
				option.selected = shouldselect;
			}

		});
	}

	function selectLocationbyName(location){
		if  (location){

			$("#id-locationids option").each(function(index,option){
				if(option.value === location){
					option.selected = true;
				}

			});

		}
	}

	function showModal(){

		$('.ia-match-filter-dialog').show()
	}
	 
	</script>
	</p>
	
	<p>
	Images sent to IA: <b><%=numIA%></b><%=((percent > 0) ? " (" + percent + "%)" : "")%>
	
	<p>
	Image formats generated? <%=(foundChildren ? "<b class=\"yes\">yes</b>" : "<b class=\"no\">no</b>")%>
	<% if (!foundChildren && (allAssets.size() > 0)) { %>
	    <a style="margin-left: 20px;" class="button">generate children image formats</a>
	<% } %>
	</p>
	<div id="ia-send-div">
	<% 
	if (allowIA) { 
	%>
	    
	    <p><strong>Image Analysis</strong></p>
	    <p><em>The machine learning job queue runs each detection and ID job in a serial queue of jobs, which span multiple users. <%=queueStatement %></em></p>
		    <%
		    if (allAssets.size() > 0) {
		    
		    %>
		    	
		    	<div style="margin-bottom: 30px;margin-top: 30px;"><a class="button" style="margin-left: 20px;" onClick="$('.ia-match-filter-dialog').show()">Send to detection (no identification)</a></div>
		

	 	<% 
		    }
	}
	if((request.isUserInRole("admin") || request.isUserInRole("researcher")) 
			&& itask.getIATask()!=null 
			&& itask.getStatus()!=null
			&& itask.getStatus().equals("complete") 
			&& (iaStatusString.startsWith("identification")||iaStatusString.equals("detection complete"))) {allowReID=true;}

	if (allowReID) { 
		%>
		 <div style="margin-bottom: 30px;margin-top: 30px;">
		    	<a class="button" style="margin-left: 20px;" onClick="showModal()">Send to identification</a>
		   </div>
		    	
		    <%
	}

	
	//who can delete an ImportTask? admin, orgAdmin, or the creator of the ImportTask
	if((itask.getStatus()!=null &&"complete".equals(itask.getStatus())) || (adminMode||(itask.getCreator()!=null && request.getUserPrincipal()!=null && itask.getCreator().getUsername().equals(request.getUserPrincipal().getName())))) {
		    %>

		    <p><strong>Delete this bulk import?</strong></p>
		    	<div style="margin-bottom: 20px;">
		    		<form onsubmit="return confirm('Are you sure you want to PERMANENTLY delete this ImportTask and all its data?');" name="deleteImportTask" class="editFormMeta" method="post" action="DeleteImportTask">
		              	<input name="taskID" type="hidden" value="<%=itask.getId()%>" />
		              	<input style="width: 200px;" align="absmiddle" name="deleteIT" type="submit" style="background-color: yellow;" class="btn btn-sm btn-block deleteEncounterBtn" id="deleteButton" value="Delete ImportTask" />
		        	</form>
		    	</div>

		<div class="ia-match-filter-dialog">
			<h2><%=encprops.getProperty("matchFilterHeader")%></h2>
			<%

				String queueStatementID2="";
				int wbiaIDQueueSize2 = WbiaQueueUtil.getSizeDetectionJobQueue(false);
				if(wbiaIDQueueSize2==0){
					queueStatementID2 = "The machine learning queue is empty and ready for work.";
				}
				else if(Prometheus.getValue("wildbook_wbia_turnaroundtime_detection")!=null){
					String val=Prometheus.getValue("wildbook_wbia_turnaroundtime_detection");
					try{
						Double d = Double.parseDouble(val);
						d=d/60.0;
						queueStatementID2 = "There are currently "+wbiaIDQueueSize2+" ID jobs in the small batch queue. Time to completion is averaging "+(int)Math.round(d)+" minutes based on recent matches. Your time may be faster or slower.";
					}
					catch(Exception de){de.printStackTrace();}
				}
				if(!queueStatementID2.equals("")){
			%>
			<p><em><%=queueStatementID2 %></em></p>
			<%
				}
			%>
			<div class="ia-match-filter-title search-collapse-header" style="padding-left:0; border:none;">
				<span class="el el-lg el-chevron-right rotate-chevron down" style="margin-right: 8px;"></span><%=encprops.getProperty("locationID")%> &nbsp; <span class="item-count" id="total-location-count"></span>
			</div>
			<div class="ia-match-filter-container" style="display: block">
				<div  style="width: 100%; max-height: 500px; overflow-y: scroll">
					<div id="ia-match-filter-location" class="option-cols">

						<div>
							<input type="button" value="<%=encprops.getProperty("selectAll")%>"
								   onClick="shouldselectAllOptions(true)" />
							<input type="button" value="<%=encprops.getProperty("selectNone")%>"
								   onClick="shouldselectAllOptions(false)" />
						</div>
						<br>

						<%=LocationID.getHTMLSelector(true, locationIds, null, "id-locationids", "locationID", "") %>


					</div>

				</div>


				<style type="text/css">
					/* this .search-collapse-header .rotate-chevron logic doesn't work
                     because animatedcollapse.js is eating the click event (I think.).
                     It's unclear atm where/whether to modify animatedcollapse.js to
                     rotate this chevron.
                    */
					.search-collapse-header .rotate-chevron {
						-moz-transition: transform 0.5s;
						-webkit-transition: transform 0.5s;
						transition: transform 0.5s;
					}
					.search-collapse-header .rotate-chevron.down {
						-ms-transform: rotate(90deg);
						-moz-transform: rotate(90deg);
						-webkit-transform: rotate(90deg);
						transform: rotate(90deg);
					}
					.search-collapse-header:hover {
						cursor: pointer;
					}

				</style>
				<script>
					$(".search-collapse-header").click(function(){
						console.log("LOG!: collapse-header is clicked!");
						$(this).children(".rotate-chevron").toggleClass("down");
						$(this).next().slideToggle();
					});
				</script>

			</div>


			<div class="ia-match-filter-title"><%=encprops.getProperty("matchFilterOwnership")%></div>
			<div class="item">
				<input type="checkbox" id="match-filter-owner-me" name="match-filter-owner" value="me" />
				<label for="match-filter-owner-me"><%=encprops.getProperty("matchFilterOwnershipMine")%></label>
			</div>

			<div class="ia-match-filter-section">
				<% if(allowIA) {%>
				<input id="matchbutton" type="button" value="<%=encprops.getProperty("doMatch")%>" onClick="sendToIA(false)" />
				<%}
				if (allowReID){
				%>
				<input id="matchbutton" type="button" value="<%=encprops.getProperty("doMatch")%>" onClick="resendToID()" />

				<% }%>
				<input style="background-color: #DDD;" type="button" value="<%=encprops.getProperty("cancel")%>"
					   onClick="$('.ia-match-filter-dialog').hide()" />
			</div>



		</div>
	</div>



	<%
	}
	%>
	    	
	    </div>
	
	</p>
	
	
	
	<%
	}   //end final else
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
}



%>




</div>

</div>

<jsp:include page="footer.jsp" flush="true"/>


