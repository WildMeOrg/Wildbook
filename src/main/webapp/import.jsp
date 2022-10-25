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

org.ecocean.metrics.*,
org.ecocean.ia.WbiaQueueUtil,
java.util.Collections,java.util.Comparator,

java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>

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

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("import.jsp");
myShepherd.beginDBTransaction();

//should the user see the detect and/or detect+ID buttons?
boolean allowIA=false;
boolean allowReID=false;
String iaStatusString="not started";

try{
	User user = AccessControl.getUser(request, myShepherd);
	if (user == null) {
	    response.sendError(401, "access denied");
	    myShepherd.rollbackDBTransaction();
	    myShepherd.closeDBTransaction();
	    return;
	}
	boolean adminMode = request.isUserInRole("admin");
	if(request.isUserInRole("orgAdmin"))adminMode=true;
	boolean forcePushIA=false;
	if(adminMode&&request.getParameter("forcePushIA")!=null)forcePushIA=true;
	
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
			queueStatement = "The machine learning queue is empty and ready for work.";
		}
		else if(Prometheus.getValue("wildbook_wbia_turnaroundtime")!=null){
	  		String val=Prometheus.getValue("wildbook_wbia_turnaroundtime");
	  		try{
	  			Double d = Double.parseDouble(val);
	  			d=d/60.0;
	  			queueStatement = "There are currently "+(wbiaIDQueueSize+wbiaDetectionQueueSize)+" jobs in the machine learning queue. Each job is averaging a turnaround time of "+(int)Math.round(d)+" minutes.";
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
	  
	
	
	
	
	String taskId = request.getParameter("taskId");
	String jdoql = null;
	ImportTask itask = null;
	
	if (taskId != null) {
	    try {
	        itask = (ImportTask) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(ImportTask.class, taskId), true));
	    } catch (Exception ex) {
	    	ex.printStackTrace();
	    }
	    if ((itask == null) || !(adminMode || user.equals(itask.getCreator()))) {
	        out.println("<h1 class=\"error\">taskId " + taskId + " may be invalid</h1><p>Try refreshing this page if you arrived on this page from an import that you just kicked off.</p>");
	        myShepherd.rollbackDBTransaction();
	        myShepherd.closeDBTransaction();
	        return;
	    }
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
	                }
	                if (!foundChildren && (Util.collectionSize(ma.findChildren(myShepherd)) > 0)) foundChildren = true; //only need one
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
	        	
	        	System.out.println("Num tasks: "+tasks.size());
	        	out.println("     <ul>");
	        	//for(Task task:tasks){
	        		out.println("          <li><a target=\"_blank\" href=\"iaResults.jsp?taskId="+tasks.get(0).getId()+"\" >"+annotTypesByTask.get(tasks.get(0).getId())+"</a>");
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
		int numDetectionComplete=0;
		for(MediaAsset asset:allAssets){
			if(asset.getAcmId()!=null)numWithACMID++;
			if(asset.getDetectionStatus()!=null && (asset.getDetectionStatus().equals("complete")||asset.getDetectionStatus().equals("pending"))) numDetectionComplete++;
		}
		%>
		<p>
		Total media assets: <%=allAssets.size()%><br>
		<ul>
			<li>Number with acmIDs: <%=numWithACMID %></li>
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
	
	if(adminMode && "complete".equals(itask.getStatus()) && (itask.getIATask()==null))allowIA=true;

	boolean shouldRefresh=false;
	//let's check shouldRefresh logic
	if(itask.getStatus()!=null && !itask.getStatus().equals("complete"))shouldRefresh=true;
	
	if (itask.getIATask() !=null && itask.iaTaskStarted()) {
        //detection-only Task
		//if(hasIdentificationBenRun(itask)){
		if(!itask.iaTaskRequestedIdentification()){
        	if(numDetectionComplete==allAssets.size()){
        		iaStatusString="detection complete";
        	}
        	else{
        		iaStatusString="detection requests sent ("+numDetectionComplete+"/"+allAssets.size()+" complete). " +queueStatementDetection;
        		shouldRefresh=true;
        	}
        }
        //ID Task
        else{
        	iaStatusString="identification requests sent (see table below for links to each matching job). "+queueStatementID;
        	if(numMatchTasks<numMatchAgainst)shouldRefresh=true;
        	System.out.println("heerios!");
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
	
			})(60);	
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


