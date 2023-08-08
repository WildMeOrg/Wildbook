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
org.ecocean.ia.WbiaQueueUtil,
java.util.Collections,java.util.Comparator,
org.json.JSONObject,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>

<%!
public boolean isUserAuthorizedForImportTask(HttpServletRequest req, ImportTask itask, Shepherd myShepherd){
	try{		
		//users with admin role always pass
		if(req.isUserInRole("admin")){return true;}
		
		//the task creator should always pass
		User user = AccessControl.getUser(req, myShepherd);
		if(user!=null && itask.getCreator()!=null && user.equals(itask.getCreator())){return true;}
		
	    //if you're collaborating with a user who owns a bulk import, you can see it
	    if(user!=null & itask.getCreator()!=null && Collaboration.collaborationBetweenUsers(myShepherd,user.getUsername(), itask.getCreator().getUsername())!=null){return true;}
	    
	 
	
		//if this user is the orgAdmin for the bulk import's uploading user, they can see it
		if(ServletUtilities.isCurrentUserOrgAdminOfTargetUser(itask.getCreator(), req, myShepherd)){return true;}
		
		//if this user's username == an Encounter.submitterID of an Encounter in this bulk import
		String filter = "select from org.ecocean.Encounter where itask.encounters.contains(this) && itask.id =='"+itask.getId()+"' VARIABLES org.ecocean.servlet.importer.ImportTask itask";	
	    Query q = myShepherd.getPM().newQuery(filter);
	    q.setResult("distinct submitterID");
	    Collection results = (Collection) q.execute();
	    ArrayList<String> al=new ArrayList<String>(results);
	    q.closeAll();
	    if(user!=null && al.contains(user.getUsername())){return true;}
	}
	catch(Exception e){e.printStackTrace();}
   
	//otherwise, nope....you are not authorized
	return false;
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
public String getTaskStatus(Task task,Shepherd myShepherd){
	String status="waiting to queue";
	ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(task.getId(), "IBEISIA", myShepherd);
	if(logs!=null && logs.size()>0){
		
		Collections.reverse(logs);  //so it has newest first like mostRecent above
		IdentityServiceLog l =logs.get(0);
		JSONObject islObj = l.toJSONObject();
		if(islObj.optString("status")!=null && islObj.optString("status").equals("completed")){
			status=islObj.optString("status");
		}
		else if(islObj.toString().indexOf("HTTP error code")>-1){
			status="error";
		}
		else if(!islObj.optString("queueStatus").equals("")){
			status=islObj.optString("queueStatus");
		}
		else if(islObj.opt("status")!=null && islObj.opt("status").toString().indexOf("initIdentify")>-1){
			status="queuing";
		}

		//if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
		
	}
	return status;
	
}
%>

<%!
public String getOverallStatus(Task task,Shepherd myShepherd, HashMap<String,Integer> idStatusMap){
	String status="queuing";
	if(task.hasChildren()){
		//accumulate status across children
		HashMap<String,String> map=new HashMap<String,String>();
		//this should only ever be two layers deep
		for(Task childTask:task.getChildren()){
			if(childTask.hasChildren()){
				for(Task childTask2:childTask.getChildren()){
					map.put(childTask2.getId(),getTaskStatus(childTask2,myShepherd));
				}
			}
			else{
				map.put(childTask.getId(),getTaskStatus(childTask,myShepherd));
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
		status=getTaskStatus(task,myShepherd);
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
	//if(adminMode&&request.getParameter("forcePushIA")!=null)forcePushIA=true;
	
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
	if (!isUserAuthorizedForImportTask(request, itask, myShepherd)) {
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
	
	
	Set<String> locationIds = new HashSet<String>();

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
	    	if (enc.getLocationID() != null) locationIds.add(enc.getLocationID());
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
	        		out.println("          <li><a target=\"_blank\" href=\"iaResults.jsp?taskId="+tasks.get(0).getId()+"\" >"+annotTypesByTask.get(tasks.get(0).getId())+": "+getOverallStatus(tasks.get(0),myShepherd,idStatusMap)+"</a>");
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
		for(MediaAsset asset:allAssets){
			if(asset.getAcmId()!=null)numWithACMID++;
			
			//check if we can get validity off the image before the expensive check of hitting the AssetStore
			if(asset.isValidImageForIA()!=null){
				if(asset.isValidImageForIA().booleanValue()){numAllowedIA++;}
			}
			else if(asset.validateSourceImage()){numAllowedIA++;myShepherd.updateDBTransaction();}
			
			
			if(asset.getDetectionStatus()!=null && (asset.getDetectionStatus().equals("complete")||asset.getDetectionStatus().equals("pending"))) numDetectionComplete++;
		}
		%>
		<p>
		Total media assets: <%=allAssets.size()%><br>
		<ul>
			<li>Number with acmIDs: <%=numWithACMID %></li>
			<li>Number valid for image analysis: <%=numAllowedIA %></li>
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
	    var locationIds = $('#id-locationids').val();
	    var locationIds = '';
	    $("#id-locationids > option").each(function(){
	    	locationIds+='&locationID='+this.value;
	    });
	    if(locationIds.indexOf('ALL locations')>-1)locationIds='';
	    //if (locationIds && (locationIds.indexOf('') < 0)) data.taskParameters.matchingSetFilter = { locationIds: locationIds };
	
	    console.log('resendToID() SENDING: locationIds=%o', locationIds);
	    $.ajax({
	        url: wildbookGlobals.baseUrl + '/appadmin/resendBulkImportID.jsp?importIdTask=<%=taskId%>'+locationIds,
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
		    	
		    	<div style="margin-bottom: 20px;"><a class="button" style="margin-left: 20px;" onClick="sendToIA(true); return false;">Send to detection (no identification)</a></div>
		

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
		 <div style="margin-bottom: 20px;">   	
		    	<a class="button" style="margin-left: 20px;" onClick="resendToID(); return false;">Send to identification</a> matching against <b>location(s):</b>
		    	<select multiple id="id-locationids" style="vertical-align: top;">
		        	<option selected><%= String.join("</option><option>", locationIds) %></option>
		        	<option value="">ALL locations</option>
		    	</select>
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

<jsp:include page="footer.jsp" flush="true"/>


