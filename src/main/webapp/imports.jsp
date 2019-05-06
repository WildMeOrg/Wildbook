<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,
org.ecocean.media.MediaAsset,
javax.jdo.Query,
java.util.List,
java.util.Collection,
java.util.ArrayList,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("imports.jsp");
User user = AccessControl.getUser(request, myShepherd);
if (user == null) {
    response.sendError(401, "access denied");
    return;
}
boolean adminMode = ("admin".equals(user.getUsername()));

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
<style>
.dim, .ct0 {
    color: #AAA;
}

.yes {
    color: #0F5;
}
.no {
    color: #F20;
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
        out.println("<h1 class=\"error\">taskId " + taskId + " is invalid</h1>");
        return;
    }
}


if (itask == null) {
    out.println("<table id=\"import-table\" xdata-page-size=\"6\" data-height=\"650\" data-toggle=\"table\" data-pagination=\"false\" ><thead><tr>");
    DateTime cutoff = new DateTime(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000);
    String uclause = "";
    if (!adminMode) uclause = " creator.uuid == '" + user.getUUID() + "' && ";
    jdoql = "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE " + uclause + " created > cutoff_datetime PARAMETERS DateTime cutoff_datetime import org.joda.time.DateTime";
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("created desc");
    //query.range(0,100);
    Collection c = (Collection) (query.execute(cutoff));
    List<ImportTask> tasks = new ArrayList<ImportTask>(c);
    query.closeAll();

    String[] headers = new String[]{"Import ID", "Date", "#Enc", "#Images", "Img Proc?", "IA?"};
    if (adminMode) headers = new String[]{"Import ID", "User", "Date", "#Enc", "#Images", "Img Proc?", "IA?"};
    for (int i = 0 ; i < headers.length ; i++) {
        out.println("<th data-sortable=\"true\">" + headers[i] + "</th>");
    }

    out.println("</tr></thead><tbody>");
    for (ImportTask task : tasks) {
        List<Encounter> encs = task.getEncounters();
        List<MediaAsset> mas = task.getMediaAssets();
        String hasChildren = "<td class=\"dim\">-</td>";
        String iaStatus = "<td class=\"no\">no</td>";
        if (Util.collectionSize(mas) > 0) {
            for (MediaAsset ma : mas) {
                if (ma.getDetectionStatus() != null) iaStatus = "<td class=\"yes\">yes</td>";
                if (Util.collectionSize(ma.findChildren(myShepherd)) > 0) {
                    hasChildren = "<td class=\"yes\">yes</td>";
                    break;
                }
                hasChildren = "<td class=\"no\">no</td>";
            }
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
        out.println("<td class=\"ct" + Util.collectionSize(mas) + "\">" + Util.collectionSize(mas) + "</td>");
        out.println(hasChildren);
        out.println(iaStatus);
        out.println("</tr>");
    }

%>
<!-- jdoql(<%=jdoql%>) -->
</tbody></table>

<%
} else { //end listing
    out.println("<h1>TODO " + itask + "</h1>");
}
%>

</div>

<jsp:include page="footer.jsp" flush="true"/>

