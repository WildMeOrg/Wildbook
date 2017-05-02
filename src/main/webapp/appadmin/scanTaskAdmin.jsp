<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,org.ecocean.grid.*, java.util.ArrayList,java.util.Iterator, java.util.Properties, java.util.concurrent.ThreadPoolExecutor" %>
<%

//String context="context0";
String context=ServletUtilities.getContext(request);
  //concurrency examination for creation and removal threads
  ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();

//get a shepherd
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("scanTaskAdmin.jsp");

//summon thee a gridManager!
  GridManager gm = GridManagerFactory.getGridManager();
  if (request.getParameter("numAllowedNodes") != null) {
    try {
      int newThrottle = (new Integer(request.getParameter("numAllowedNodes"))).intValue();
      gm.setNumAllowedNodes(newThrottle);
    } catch (NumberFormatException nfe) {
    	nfe.printStackTrace();
    }
  }
  if (request.getParameter("nodeTimeout") != null) {
    try {
      int newTimeout = (new Integer(request.getParameter("nodeTimeout"))).intValue();
      gm.setNodeTimeout(newTimeout);
    } catch (NumberFormatException nfe) {
    	nfe.printStackTrace();
    }
  }
  if (request.getParameter("checkoutTimeout") != null) {
    try {
      int newTimeout = (new Integer(request.getParameter("checkoutTimeout"))).intValue();
      gm.setCheckoutTimeout(newTimeout);
    } catch (NumberFormatException nfe) {
    	nfe.printStackTrace();
    }
  }
  if (request.getParameter("scanTaskLimit") != null) {
    try {
      int limit = (new Integer(request.getParameter("scanTaskLimit"))).intValue();
      gm.setScanTaskLimit(limit);
    } catch (NumberFormatException nfe) {
    	nfe.printStackTrace();
    }
  }
  if (request.getParameter("maxGroupSize") != null) {
    try {
      int limit = (new Integer(request.getParameter("maxGroupSize"))).intValue();
      gm.setMaxGroupSize(limit);
    } catch (NumberFormatException nfe) {
    	nfe.printStackTrace();
    }
  }


//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";

String langCode=ServletUtilities.getLanguageCode(request);
    

  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props=ShepherdProperties.getProperties("submit.properties", langCode, context);



%>

<style>
td, th {
    border: 1px solid black;font-size: 10pt;

}
table {
    border-collapse: collapse;
}
</style>

<jsp:include page="../header.jsp" flush="true" />
     <div class="container maincontent">
<h1>Grid Administration
  <a href="<%=CommonConfiguration.getWikiLocation(context)%>sharkgrid" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a></h1>

<%
  myShepherd.beginDBTransaction();
  try {

String showContext="My ";

if(request.getParameter("showAll")==null){
%>
<p class="caption">Your scanTasks are shown below. Click <b>Show All scanTasks</b> to see all of the tasks in the grid for all users.</p>

<p class="caption">Refreshing results in <span id="countdown"></span> seconds.</p>
  <script type="text/javascript">
  (function countdown(remaining) {
	    if(remaining === 0)
	        location.reload(true);
	    document.getElementById('countdown').innerHTML = remaining;
	    setTimeout(function(){ countdown(remaining - 1); }, 1000);
	})(30);
  </script>

<p>
	<a style="cursor:pointer;color: blue" class="caption" href="scanTaskAdmin.jsp?showAll=true">Show All scanTasks</a>
</p>
<%
}
else{
	showContext="All ";
%>
<p class="caption">All scanTasks are shown below. Click <b>Show My scanTasks</b> to see only your tasks below.</p>

<p>
	<a style="cursor:pointer;color: blue" class="caption" href="scanTaskAdmin.jsp">Show My scanTasks</a>
</p>
<%
}
%>

<h3><%=showContext %>Pending scanTasks</h3>
<table class="table">
<thead>
  <tr>
    <th><strong>Identifier</strong></th>
    <th><strong>User</strong></th>
    <th><strong>Completion</strong></th>
    <th colspan="2"><strong>Actions</strong></th>
  </tr>
  </thead>
  <tbody>
  <%
  Iterator<ScanTask> it = null;
  if(request.getParameter("showAll")!=null){it=myShepherd.getAllScanTasksNoQuery();}
  else{it=myShepherd.getAllScanTasksForUser(request.getUserPrincipal().toString()).iterator();}
  	
    
    
    
    int scanNum = 0;
    while ((it!=null)&&(it.hasNext())) {
      ScanTask st = it.next();
      if (!st.hasFinished()) {
        scanNum++;
        int numTotal = st.getNumComparisons();

        int numComplete = gm.getNumWorkItemsCompleteForTask(st.getUniqueNumber());

        int numGenerated = gm.getNumWorkItemsIncompleteForTask(st.getUniqueNumber());

        //int numTaskTot = st.getNumComparisons();
		String numTaskTot=numComplete+"/"+st.getNumComparisons();
		if(st.getNumComparisons()==Integer.MAX_VALUE){numTaskTot="Building...";}
        
        
   String styleString="";
   if((request.getParameter("task")!=null)&&(st.getUniqueNumber().equals(request.getParameter("task")))){styleString="background-color: #66CCFF;border-collapse:collapse;";}
        
  %>
  <tr id="<%=st.getUniqueNumber()%>" >
    <td style="<%=styleString %>"><%=scanNum%>. <%=st.getUniqueNumber()%>
    </td>
    <td><%=st.getSubmitter()%>
    </td>
    <td><%=numTaskTot%>
    </td>
    <td>
      <%
      if ((numComplete > 0) && (numComplete >= st.getNumComparisons())) {
      %>
      <form name="scanNum<%=scanNum%>_writeOut" method="post"
            action="../<%=CommonConfiguration.getProperty("patternMatchingEndPointServletName", context) %>"><input name="number" type="hidden"
                                                id="number" value="<%=st.getUniqueNumber()%>"> <%

        %> <input name="scanNum<%=scanNum%>_WriteResult" type="submit"
                  id="scanNum<%=scanNum%>_WriteResult" value="Write Result"></form>
       <%
      }
      boolean hasPermissionForThisEncounter=false;
      if ((request.isUserInRole("admin")) || (request.getRemoteUser().equals(st.getSubmitter()))) {hasPermissionForThisEncounter=true;}
      else if(myShepherd.isEncounter(st.getUniqueNumber().replaceAll("scanL", "").replaceAll("scanR", ""))){
    	Encounter scanEnc=myShepherd.getEncounter(st.getUniqueNumber().replaceAll("scanL", "").replaceAll("scanR", ""));
    	if((scanEnc.getLocationID()!=null)&&(request.isUserInRole(scanEnc.getLocationID()))){hasPermissionForThisEncounter=true;}  	
      }
      if (hasPermissionForThisEncounter) {%>
      <form name="scanNum<%=scanNum%>" method="post"
            action="../ScanTaskHandler"><input name="action" type="hidden"
                                               id="action" value="removeTask"><input name="taskID"
                                                                                     type="hidden"
                                                                                     id="taskID"
                                                                                     value="<%=st.getUniqueNumber()%>"><input
        name="delete" type="submit" id="delete" value="Delete"></form>
        </td>
        <td>
        <%
        if(request.isUserInRole("admin")){
        %>
              <form name="scanNum<%=scanNum%>" method="post" action="../ScanTaskHandler">
              	<input name="action" type="hidden" id="action" value="addTask" />
              	<input name="restart" type="hidden" id="restart" value="true" />
              	<input name="encounterNumber" type="hidden" id="encounterNumber" value="<%=st.getUniqueNumber().replaceAll("scanL", "").replaceAll("scanR", "") %>" />
              	<input name="taskID" type="hidden" id="taskID" value="<%=st.getUniqueNumber()%>" />
              	
              	<%
              	if(st.getUniqueNumber().startsWith("scanR")){
              	%>
              		<input name="rightSide" type="hidden" id="restart" value="true" />
              	<%
              	}
              	%>
              	
              		<input name="restart" type="submit" id="restart" value="Restart" />
              </form>
        <%
        }
        %>
        
      <%
        }
      %>

    </td>
  </tr>
  <%
      }
    }
  %>
  </tbody>
</table>


<h3><%=showContext %>Completed scanTasks</h3>

  <table class="table">
  <thead>
  <tr>
    <th width="62" class="ptcol"><strong>Identifier</strong></th>
    <th width="32"><strong>User</strong></th>
    <th><strong>Results</strong></th>
    <th><strong>Actions</strong></th>
	<th><strong>Individual ID</strong></th>
	<th><strong>Enc. State</strong></th>
  </tr>
  </thead>
  <tbody>
  <%
    Iterator it2 = null;
  if(request.getParameter("showAll")!=null){it2=myShepherd.getAllScanTasksNoQuery();}
  else{it2=myShepherd.getAllScanTasksForUser(request.getUserPrincipal().toString()).iterator();}	
  
    scanNum = 0;
    while ((it2!=null)&&(it2.hasNext())) {
      ScanTask st = (ScanTask) it2.next();
      

      
      Encounter scanEnc=new Encounter();
      if(myShepherd.isEncounter(st.getUniqueNumber().replaceAll("scanL", "").replaceAll("scanR", ""))){
      	scanEnc=myShepherd.getEncounter(st.getUniqueNumber().replaceAll("scanL", "").replaceAll("scanR", ""));
      }
      if (st.hasFinished()) {
    	  
          //clean up after the task if needed
          gm.removeCompletedWorkItemsForTask(st.getUniqueNumber());

        //determine if left or right-side scan
        //scanWorkItem[] swis9=st.getWorkItems();
        //scanWorkItem swi9=(scanWorkItem)myShepherd.getScanWorkItemsForTask(st.getUniqueNumber(), 1).next();
        String sideAddition = "false";
        if (st.getUniqueNumber().indexOf("scanR") != -1) {
          sideAddition = "true";
        }

        scanNum++;
        
        String styleString="";
        if((request.getParameter("task")!=null)&&(st.getUniqueNumber().equals(request.getParameter("task")))){styleString="background-color: #66CCFF;border-collapse:collapse;";}
             
       %>
       <tr id="<%=st.getUniqueNumber()%>" >

    <td style="<%=styleString %>"><%=st.getUniqueNumber()%>
    </td>
    <td><%=st.getSubmitter()%>
    </td>
    <%
      String gotoURL = "//" + CommonConfiguration.getURLLocation(request) + "/"+CommonConfiguration.getProperty("patternMatchingResultsPage", context);
      if (st.getUniqueNumber().equals("TuningTask")) {
        gotoURL = "endTuningTask.jsp";
      }
    %>

    <td>
      <form name="scanNumJoin<%=scanNum%>" method="get"
            action="<%=gotoURL%>"><input name="rightSide" type="hidden"
                                         id="rightSide" value="<%=sideAddition%>"><input
        name="writeThis" type="hidden" id="writeThis" value="true"><input
        name="number" type="hidden" id="number"
        value="<%=st.getUniqueNumber().substring(5)%>"><input
        name="viewresult" type="submit" id="viewresult" value="View"></form>
    </td>
    <td>
      <%      
      boolean hasPermissionForThisEncounter=false;
      if ((request.isUserInRole("admin")) || (request.getRemoteUser().equals(st.getSubmitter()))) {hasPermissionForThisEncounter=true;}
      else if(myShepherd.isEncounter(st.getUniqueNumber().replaceAll("scanL", "").replaceAll("scanR", ""))){
    	if((scanEnc.getLocationID()!=null)&&(request.isUserInRole(scanEnc.getLocationID()))){hasPermissionForThisEncounter=true;}  	
      }
      if (hasPermissionForThisEncounter) {%>
      <form name="scanNum<%=scanNum%>" method="post"
            action="../ScanTaskHandler"><input name="action" type="hidden"
                                               id="action" value="removeTask"><input name="taskID"
                                                                                     type="hidden"
                                                                                     id="taskID"
                                                                                     value="<%=st.getUniqueNumber()%>"><input
        name="delete" type="submit" id="delete" value="Delete"></form>
      <%
      } else {%> N/A <%
      }
    %>
    </td>



						
						<td>
						<%
						if((scanEnc.getIndividualID()!=null)&&(!scanEnc.getIndividualID().equals("Unassigned"))){
						%>
						<a href="../individuals.jsp?number=<%=scanEnc.getIndividualID()%>"><%=scanEnc.getIndividualID()%></a>
						<%
      					}
      					else{
						%>
						&nbsp;
						<%
      					}
						%>
				
						</td>
						<td><%=scanEnc.getState() %></td>

  </tr>
  <%
      }
    }
  %>
  </tbody>
</table>




<h3>gridManager statistics</h3>

<p>Number of nodes: <%=gm.getNumNodes()%> of <%=gm.getNumAllowedNodes()%>
  allowed*.<br> <span class="style2">*Nodes working on a
single scan are allowed to exceed the total.</span>
</p>
<%
  if (gm.getNumNodes() > 0) {
%>
<table class="table">
<thead>
  <tr>
    <th width="18"><span>IP</span></th>
    <th width="38"><span>NodeID</span></th>
    <th width="30"  ><span>#CPU</span></th>


    <th width="71"  >
      <div align="left"><span class="style1">Chunk size</span></div>
    </th>

  </tr>
  </thead>
  <tbody>
  <%
    ArrayList nodes = gm.getNodes();
    int numNodes = nodes.size();
    for (int y = 0; y < numNodes; y++) {
      GridNode nd = (GridNode) nodes.get(y);
      long currenTime = System.currentTimeMillis();
      long nodeTimeout = gm.getNodeTimeout();
      if ((currenTime - nd.getLastHeartbeat()) < nodeTimeout) {
  %>
  <tr>
    <td><span class="style2"><%=nd.ipAddress()%></span></td>
    <td><span class="style2"><%=nd.getNodeIdentifier()%></span></td>
    <td><span class="style2"><%=nd.numProcessors%></span></td>
    
    <td><span class="style2"><%=nd.groupSize%></span></td>



  </tr>
  <%
      } //end if
    } //end for
  %>
  </tbody>
</table>
<%}%>
<p>% inefficient collisions (nodes checking in duplicate work) since
  startup: <%=gm.getCollisionRatePercentage()%>
</p>

<p>Total work items completed since startup: <%=gm.getNumCompletedWorkItems()%>
  (<%=gm.getNumCollisions()%> collisions)</p>

<p>Total work items and results in queue: <%=gm.getNumWorkItemsAndResults()%>
  <%
  int toDo=gm.getToDoSize();
  int numDone=gm.getDoneSize();
 
  %>
  
  (To-Do: <%=toDo%> Done: <%=numDone%>)</p>

<%
  if (request.isUserInRole("admin")) {
%>
<h3>gridManager adjustment</h3>
<table class="table">
  <tr>
    <form name="setNumAllowedNodes" id="setNumAllowedNodes" method="get"
          action="scanTaskAdmin.jsp">
      <td>Set number of allowed nodes (does not limit targeted scans):
      </td>
      <td><input name="numAllowedNodes" type="text"
                 id="numAllowedNodes" value="<%=gm.getNumAllowedNodes()%>" size="5"
                 maxlength="5"/> <input type="submit" name="Submit" value="Set"/>
      </td>
    </form>
  </tr>
  <tr>
    <form name="setNodeTimeout" id="setNodeTimeout" method="get"
          action="scanTaskAdmin.jsp">
      <td>Set node timeout (milliseconds):</td>
      <td><input name="nodeTimeout" type="text" id="nodeTimeout"
                 value="<%=gm.getNodeTimeout()%>" size="10" maxlength="15"/> <input
        type="submit" name="Submit2" value="Set"/></td>
    </form>
  </tr>
  <tr>
    <form name="setCheckoutTimeout" id="setCheckoutTimeout" method="get"
          action="scanTaskAdmin.jsp">
      <td>Set checkout timeout (milliseconds):</td>
      <td><input name="checkoutTimeout" type="text"
                 id="checkoutTimeout" value="<%=gm.getCheckoutTimeout()%>" size="10"
                 maxlength="15"/> <input type="submit" name="Submit3" value="Set"/>
      </td>
    </form>
  </tr>
  <tr>
    <form name="setScanTaskLimit" id="setScanTaskLimit" method="get"
          action="scanTaskAdmin.jsp">
      <td>Set number allowed scanTasks:</td>
      <td><input name="scanTaskLimit" type="text" id="scanTaskLimit"
                 value="<%=gm.getScanTaskLimit()%>" size="5" maxlength="5"/> <input
        type="submit" name="Submit4" value="Set"/></td>
    </form>
  </tr>
  <tr>
    <form name="setMaxGroupSize" id="setMaxGroupSize" method="get"
          action="scanTaskAdmin.jsp">
      <td>Set maximum chunk/group size sent to nodes:</td>
      <td><input name="maxGroupSize" type="text" id="maxGroupSize"
                 value="<%=gm.maxGroupSize%>" size="5" maxlength="5"/> <input
        type="submit" name="Submit5" value="Set"/></td>
    </form>
  </tr>
</table>
<h3>Creation/deletion threads</h3>

<p>Number of tasks creating/deleting: <%=es.getActiveCount()%>
  (<%=(es.getTaskCount() - es.getCompletedTaskCount())%>
  total in queue)<br> <br>


  <%}%>

</p>

<p>Number left-side patterns in the potential match graph: <%=gm.getNumLeftPatterns() %></p>
<p>Number right-side patterns in the potential match graph: <%=gm.getNumRightPatterns() %></p>
<%

  } catch (Exception e) {
    System.out.println("Error in scanTaskAdmin.jsp!");
    e.printStackTrace();

  }
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();

  

%>

</div>



<jsp:include page="../footer.jsp" flush="true" />

