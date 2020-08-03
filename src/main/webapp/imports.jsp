<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,
org.ecocean.media.MediaAsset,
javax.jdo.Query,
org.json.JSONArray,
java.util.List,
java.util.Collection,
java.util.ArrayList,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
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

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

/*
//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  props = ShepherdProperties.getProperties("login.properties", langCode,context);
*/
  


%>
<jsp:include page="header.jsp" flush="true"/>

<script>



function confirmCommit() {
	confirm("Send to IA? This process may take a long time and block other users from using detection and ID quickly.");
}

async function sendToIA(taskid){
	
    $.ajax({
        url: "ImportTaskDetection?importTaskID="+taskid,
        success: function(data) {
            cosole.log("finished task");
            alert("Success! ImportTask media asset were submitted to the detection queue.");
            location.reload();
            return false;
        }
    });

    
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

String taskId = request.getParameter("taskId");
String jdoql = null;
ImportTask itask = null;

if (taskId != null) {
    try {
        itask = (ImportTask) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(ImportTask.class, taskId), true));
    } catch (Exception ex) {}
    if ((itask == null) || !(adminMode || user.equals(itask.getCreator()))) {
        out.println("<h1 class=\"error\">taskId " + taskId + " may be invalid</h1><p>Try refreshing this page if you arrived on this page from an import that you just kicked off.</p>");
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
    }
}


if (itask == null) {
    DateTime cutoff = new DateTime(System.currentTimeMillis() - (31L * 24L * 60L * 60L * 1000L));
    out.println("<p style=\"font-size: 0.8em; color: #888;\">Since <b>" + cutoff.toString().substring(0,10) + "</b></p>");
    out.println("<table id=\"import-table\" xdata-page-size=\"6\" data-height=\"650\" data-toggle=\"table\" data-pagination=\"false\" ><thead><tr>");
    String uclause = "";
    if (!adminMode) uclause = " creator.uuid == '" + user.getUUID() + "' && ";
    jdoql = "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE " + uclause + " created > cutoff_datetime PARAMETERS DateTime cutoff_datetime import org.joda.time.DateTime";
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("created desc");
    //query.range(0,100);
    Collection c = (Collection) (query.execute(cutoff));
    List<ImportTask> tasks = new ArrayList<ImportTask>(c);
    query.closeAll();

    String[] headers = new String[]{"Import ID", "Date", "#Enc", "w/Indiv", "#Images", "Img Proc?", "IA?"};
    if (adminMode) headers = new String[]{"Import ID", "User", "Date", "#Enc", "w/Indiv", "#Images", "Img Proc?", "IA?", "Status"};
    for (int i = 0 ; i < headers.length ; i++) {
        out.println("<th data-sortable=\"true\">" + headers[i] + "</th>");
    }

    out.println("</tr></thead><tbody>");
    for (ImportTask task : tasks) {
        List<Encounter> encs = task.getEncounters();
        List<MediaAsset> mas = task.getMediaAssets();
        boolean foundChildren = false;
        String hasChildren = "<td class=\"dim\">-</td>";
        int iaStatus = 0;
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

        int indivCount = 0;
        if (Util.collectionSize(encs) > 0) for (Encounter enc : encs) {
            if (enc.hasMarkedIndividual()) indivCount++;
        }

        out.println("<tr>");
        out.println("<td><a title=\"" + task.getId() + "\" href=\"imports.jsp?taskId=" + task.getId() + "\">" + task.getId().substring(0,8) + "</a></td>");
        if (adminMode) {
            User tu = task.getCreator();
            String uname = "(guest)";
            if (tu != null) {
                uname = tu.getFullName();
                if (uname == null) uname = tu.getUsername();
                if (uname == null) uname = tu.getUUID();
                if (uname == null) uname = Long.toString(tu.getUserID());
            }
            out.println("<td>" + uname + "</td>");
        }
        out.println("<td>" + task.getCreated().toString().substring(0,10) + "</td>");
        out.println("<td class=\"ct" + Util.collectionSize(encs) + "\">" + Util.collectionSize(encs) + "</td>");
        out.println("<td class=\"ct" + indivCount + "\">" + indivCount + "</td>");
        out.println("<td class=\"ct" + Util.collectionSize(mas) + "\">" + Util.collectionSize(mas) + "</td>");
        out.println(hasChildren);
        if (iaStatus < 1) {
            out.println("<td class=\"no\">no</td>");
        } else {
            int percent = Math.round(iaStatus / Util.collectionSize(mas) * 100);
            out.println("<td class=\"yes\" title=\"" + iaStatus + " of " + Util.collectionSize(mas) + " (" + percent + "%)\">yes</td>");
        }
        out.println("<td>"+task.getStatus()+"</td>");
        out.println("</tr>");
    }

%>
<!-- jdoql(<%=jdoql%>) -->
</tbody></table>

<%
} else { //end listing

    out.println("<p><b style=\"font-size: 1.2em;\">Import Task " + itask.getId() + "</b> (" + itask.getCreated().toString().substring(0,10) + ") <a class=\"button\" href=\"imports.jsp\">back to list</a></p>");
    out.println("<br>Status: "+itask.getStatus());
    out.println("<br>Filename: "+itask.getParameters().getJSONObject("_passedParameters").getJSONArray("filename").toString());
	out.println("<br><table id=\"import-table-details\" xdata-page-size=\"6\" xdata-height=\"650\" data-toggle=\"table\" data-pagination=\"false\" ><thead><tr>");
    String[] headers = new String[]{"Enc", "Date", "Occ", "Indiv", "#Images"};
    if (adminMode) headers = new String[]{"Enc", "Date", "User", "Occ", "Indiv", "#Images"};
    for (int i = 0 ; i < headers.length ; i++) {
        out.println("<th data-sortable=\"true\">" + headers[i] + "</th>");
    }

    out.println("</tr></thead><tbody>");
    
    
    //if incomplete refresh
    if(itask.getStatus()!=null && !itask.getStatus().equals("complete")){
    %>
	    
	    <p class="caption">Refreshing results in <span id="countdown"></span> seconds.</p>
	  <script type="text/javascript">
	  (function countdown(remaining) {
		    if(remaining === 0)location.reload(true);
		    document.getElementById('countdown').innerHTML = remaining;
		    setTimeout(function(){ countdown(remaining - 1); }, 1000);
	
		})
		    (60);	
		    
	  </script>
	    
	    
	    <%
    }

    List<MediaAsset> allAssets = new ArrayList<MediaAsset>();
    int numIA = 0;
    boolean foundChildren = false;

    JSONArray jarr = new JSONArray();
    if (Util.collectionSize(itask.getEncounters()) > 0) for (Encounter enc : itask.getEncounters()) {
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
        if (enc.hasMarkedIndividual()) {
            out.println("<td><a title=\"" + enc.getIndividualID() + "\" href=\"individuals.jsp?number=" + enc.getIndividualID() + "\">" + enc.getIndividual().getDisplayName() + "</a></td>");
        } else {
            out.println("<td class=\"dim\">-</td>");
        }

        ArrayList<MediaAsset> mas = enc.getMedia();
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

        out.println("</tr>");
    }
    int percent = -1;
    if (allAssets.size() > 1) percent = Math.round(numIA / allAssets.size() * 100);
%>
</tbody></table>
<p>
Total images: <b><%=allAssets.size()%></b>
<script>var allAssetIds = <%=jarr.toString(4)%>;</script>
</p>

<p>
Images sent to IA: <b><%=numIA%></b><%=((percent > 0) ? " (" + percent + "%)" : "")%>
</p>
<% 

if ((numIA < 1) && (allAssets.size() > 0) && itask.getStatus()!=null && itask.getStatus().equals("complete") && request.isUserInRole("admin")) { %>
    <p>
    <a onclick="sendToIA('<%=request.getParameter("taskId") %>')"><button onclick="confirmCommit()">Run detection</button></a>
    </p>
<% } %>


<p>
Image formats generated? <%=(foundChildren ? "<b class=\"yes\">yes</b>" : "<b class=\"no\">no</b>")%>
<% if (!foundChildren && (allAssets.size() > 0)) { %>
    <a style="margin-left: 20px;" class="button">generate children image formats</a>
<% } %>
</p>

<%
}   //end final else
%>

</div>

<jsp:include page="footer.jsp" flush="true"/>

<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>

