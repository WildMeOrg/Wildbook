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
java.util.Collection,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>

<%!

private int getNumIndividualsForTask(String taskID, Shepherd myShepherd){
	int num=0;
	String filter="select distinct individualID from org.ecocean.MarkedIndividual where encounters.contains(enc) && itask.encounters.contains(enc) && itask.id == '"+taskID+"' VARIABLES org.ecocean.Encounter enc;org.ecocean.servlet.importer.ImportTask itask";
	Query query=myShepherd.getPM().newQuery(filter);
	try{
		Collection c=(Collection)query.execute();
		num=c.size();
	}
	catch(Exception e){
		e.printStackTrace();
	}
	finally{
		query.closeAll();
	}
	return num;
}

%>

<%!
//Use Feature as a proxy for MediaAssets since they have a 1-to-1 correspondence
//and we thereby have one less table lookup in the query
private int getNumMediaAssetsForTask(String taskID, Shepherd myShepherd){
	int num=0;	
	String filter="select from org.ecocean.media.Feature where itask.id == '"+taskID+"' && itask.encounters.contains(enc) && enc.annotations.contains(annot) && annot.features.contains(this) VARIABLES org.ecocean.Encounter enc;org.ecocean.servlet.importer.ImportTask itask;org.ecocean.Annotation annot";
	Query query=myShepherd.getPM().newQuery(filter);
	try{
		Collection c=(Collection)query.execute();
		num=c.size();
	}
	catch(Exception e){
		e.printStackTrace();
	}
	finally{
		query.closeAll();
	}
	return num;
}
%>

<%!

private int getNumEncountersForTask(String taskID, Shepherd myShepherd){
	int num=0;
	String filter="select from org.ecocean.Encounter where itask.encounters.contains(this) && itask.id == '"+taskID+"' VARIABLES org.ecocean.servlet.importer.ImportTask itask";
	Query query=myShepherd.getPM().newQuery(filter);
	try{
		Collection c=(Collection)query.execute();
		num=c.size();
	}
	catch(Exception e){
		e.printStackTrace();
	}
	finally{
		query.closeAll();
	}
	return num;
}

%>

<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("imports.jsp");
myShepherd.beginDBTransaction();
User user = AccessControl.getUser(request, myShepherd);
if (user == null) {
    response.sendError(401, "access denied");
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    return;
}
boolean adminMode = request.isUserInRole("admin");
if(request.isUserInRole("orgAdmin"))adminMode=true;

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


%>
<jsp:include page="header.jsp" flush="true"/>



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


try{
	Set<String> locationIds = new HashSet<String>();
	
    out.println("<table id=\"import-table\" xdata-page-size=\"6\" data-height=\"650\" data-toggle=\"table\" data-pagination=\"false\" ><thead><tr>");
    String uclause = "";
    if (!adminMode) uclause = " && creator.uuid == '" + user.getUUID() + "' ";
    String jdoql = "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE id != null " + uclause;
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("created desc");
    //query.range(0,100);
    Collection c = (Collection) (query.execute());
    List<ImportTask> tasks = new ArrayList<ImportTask>(c);
    query.closeAll();

    //String[] headers = new String[]{"Import ID", "Date", "Encounters", "Individuals", "Images",  "IA?"};
    String[] headers = new String[]{"Import ID", "User", "Date", "Encounters", "Individuals", "Images",  "IA?", "Status"};
    for (int i = 0 ; i < headers.length ; i++) {
        out.println("<th data-sortable=\"true\">" + headers[i] + "</th>");
    }

    out.println("</tr></thead><tbody>");
    for (ImportTask task : tasks) {
    	if(adminMode || Collaboration.canUserAccessImportTask(task,request)){
	        //List<Encounter> encs = task.getEncounters();
	        //List<MediaAsset> mas = task.getMediaAssets();
	        boolean foundChildren = false;

	        int iaStatus = 0;
	        /*
	        if (Util.collectionSize(mas) > 0) {
	            for (MediaAsset ma : mas) {
	                if (ma.getDetectionStatus() != null) iaStatus++;
	                if (!foundChildren && (Util.collectionSize(ma.findChildren(myShepherd)) > 0)) {
	                    hasChildren = "<td class=\"yes\">yes</td>";
	                    foundChildren = true;
	                    break;
	                }
	            }
	            if (!foundChildren) hasChildren = "<td class=\"no\">no</td>";
	        }
	        */
	
	        int indivCount = getNumIndividualsForTask(task.getId(), myShepherd);
	        
	        /*
	        if (Util.collectionSize(encs) > 0) for (Encounter enc : encs) {
	            if (enc.hasMarkedIndividual()) indivCount++;
	        }
	        */
	
	        out.println("<tr>");
	        out.println("<td><a title=\"" + task.getId() + "\" href=\"import.jsp?taskId=" + task.getId() + "\">" + task.getId().substring(0,8) + "</a></td>");
	        //if (adminMode) {
	            User tu = task.getCreator();
	            String uname = "(guest)";
	            if (tu != null) {
	                uname = tu.getFullName();
	                if (uname == null) uname = tu.getUsername();
	                if (uname == null) uname = tu.getUUID();
	                if (uname == null) uname = Long.toString(tu.getUserID());
	            }
	            out.println("<td>" + uname + "</td>");
	        //}
	        int numEncs=getNumEncountersForTask(task.getId(),myShepherd);
	        out.println("<td>" + task.getCreated().toString().substring(0,10) + "</td>");
	        out.println("<td class=\"ct" + numEncs + "\">" + numEncs + "</td>");
	        out.println("<td class=\"ct" + indivCount + "\">" + indivCount + "</td>");
	        int numMediaAssets=getNumMediaAssetsForTask(task.getId(),myShepherd);
	        out.println("<td class=\"ct" + numMediaAssets + "\">" + numMediaAssets + "</td>");

	        if (iaStatus < 1) {
	            out.println("<td class=\"no\">no</td>");
	        } else {
	            int percent = Math.round(iaStatus / numMediaAssets * 100);
	            out.println("<td class=\"yes\" title=\"" + iaStatus + " of " + numMediaAssets + " (" + percent + "%)\">yes</td>");
	        }
	        out.println("<td>"+task.getStatus()+"</td>");
	        out.println("</tr>");
    	}
    }

%>

</tbody>
</table>



<%
}
catch(Exception n){
	n.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
}
%>



    	
    </div>




<jsp:include page="footer.jsp" flush="true"/>



