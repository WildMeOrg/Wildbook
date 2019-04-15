<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.json.JSONObject, org.json.JSONArray,
org.joda.time.DateTime,
javax.jdo.Query,
java.util.Collection,
java.util.Collections,
java.util.List,
java.util.ArrayList,
org.ecocean.AccessControl,
org.ecocean.User,
org.ecocean.Shepherd,
org.ecocean.ia.Task,
org.ecocean.media.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter
"%><%!

private String cleanDate(DateTime dt) {
    if (dt == null) return "-";
    return dt.toString().substring(0,16).replaceAll("T", " ");
}

private boolean userEquals(User u1, User u2) {
    if ((u1 == null) || (u2 == null) || (u1.getUUID() == null) || (u2.getUUID() == null)) return false;
    return u1.getUUID().equals(u2.getUUID());
}
%>
<jsp:include page="../header.jsp" flush="true" />
    <script src="../javascript/bootstrap-table/bootstrap-table.min.js"></script>
    <link rel="stylesheet" href="../javascript/bootstrap-table/bootstrap-table.min.css">
<style>
body {
    font-family: arial, sans;
}

.task-name {
    padding: 1px 5px;
    background-color: #DDD;
    border-radius: 3px;
    display: inline-block;
    margin: 1px;
}

#task-table td {
    font-size: 0.8em;
    border: solid 1px blue;
    padding: 4px 10px;
}

td.task-res {
    width: 20%;
}

tr.good-enough {
    background-color: #AFE;
    color: #444;
}

tr.unnamed td.ind-name {
    background-color: #FFA;
}

tr.no-res td.task-res {
    background-color: #FCC;
}

th {
    font-weight: bold;
}

</style>

<div class="container maincontent">

<table id="task-table"><thead><tr>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
//org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evictAll();

long cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000;
String jdoql = "SELECT FROM org.ecocean.ia.Task WHERE !objectAnnotations.isEmpty() && created >= " + cutoff;
Query query = myShepherd.getPM().newQuery(jdoql);
query.setOrdering("created desc");
query.range(0,100);
Collection c = (Collection) (query.execute());
List<Task> tasks = new ArrayList<Task>(c);
query.closeAll();
boolean adminMode = "admin".equals(AccessControl.simpleUserString(request));
User user = AccessControl.getUser(request, myShepherd);
if (user == null) throw new RuntimeException("401");

String[] headers = new String[]{"ID Task", "Date", "Loc ID", "Annot", "Encounter", "Ind ID", "Match Res", "User(s)"};
if (!adminMode) headers = new String[]{"ID Task", "Date", "Loc ID", "Encounter", "Ind ID", "Match Res"};
for (int i = 0 ; i < headers.length ; i++) {
    out.println("<th>" + headers[i] + "</th>");
}

out.println("</tr></thead><tbody>");

String logDump = "";
for (Task task : tasks) {
    String classes = "";
    List<Annotation> annots = task.getObjectAnnotations();
    Annotation ann = null;
    if (annots != null) ann = annots.get(0);  //"should never" (currently) have > 1
    Encounter enc = ann.findEncounter(myShepherd);
    List<User> subs = enc.getSubmitters();
    boolean ok = adminMode;
    for (User u : subs) {
        if (userEquals(user, u)) ok = true;
    }
    if (!ok) continue;

    DateTime dt = null;
    if (task.getCreatedLong() > 0L) dt = new DateTime(task.getCreatedLong());


    String thisName = null;
    if ((enc != null) && enc.hasMarkedIndividual()) thisName = enc.getIndividualID();

    ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(task.getId(), "IBEISIA", myShepherd);
    Collections.reverse(logs);  //so it has newest first like mostRecent above
    String taskRes = "";
    boolean goodEnough = false;
    for (IdentityServiceLog l : logs) {
        JSONObject jl = l.getStatusJson();
        if (jl == null) continue;
        if (!"getJobResult".equals(jl.optString("_action", "_FAIL_"))) continue;
        if ((jl.optJSONObject("_response") == null) ||
            (jl.getJSONObject("_response").optJSONObject("response") == null) ||
            (jl.getJSONObject("_response").getJSONObject("response").optJSONObject("json_result") == null) ||
            (jl.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").optJSONObject("cm_dict") == null)
        ) continue;
        String qAnnotId = jl.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").getJSONArray("query_annot_uuid_list").getJSONObject(0).getString("__UUID__");
        JSONObject res = jl.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").getJSONObject("cm_dict").optJSONObject(qAnnotId);
        if (res == null) continue;
        //JSONObject res = jl.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result");
        //logDump += "<p>" + task.getId() + "(" + qAnnotId + ")<xmp>" + res.toString(8) + "</xmp></p>";
        JSONArray as = res.optJSONArray("annot_score_list");
        JSONArray nl = res.optJSONArray("dname_list");
        if ((as == null) || (nl == null) || (as.length() != nl.length())) continue;
        for (int i = 0 ; i < as.length() ; i++) {
            String name = nl.optString(i, null);
            if ((thisName != null) && thisName.equals(name) && (i < 6)) goodEnough = true;
            taskRes += "<span class=\"task-name\" title=\"" + as.optDouble(i) + "\">" + name + "</span> ";
        }
    }
/*
    logDump += "<hr /><div><b>" + task.getId() + "</b>";
    for (IdentityServiceLog l : logs) {
        logDump += "<p>" + l + "</p>";
    }
    logDump += "</div>";
*/
    if (goodEnough) classes += " good-enough";
    if (thisName == null) classes += " unnamed";
    if (taskRes.equals("")) classes += " no-res";


%>
<tr id="task-<%=task.getId()%>" class="<%=classes%>">
    <td id="<%=task.getId()%>"><a href="../iaResults.jsp?taskId=<%=task.getId()%>"><%=task.getId().substring(0,8)%></a></td>

    <td><%=cleanDate(dt)%></td>

    <td><%=(enc.getLocationID() == null) ? "-" : enc.getLocationID()%></td>

<% if ((ann == null) && adminMode) { %>
    <td>(no annotation, deleted?)</td>
<% } else if (adminMode) { %>
    <td title="<%=ann.getIAClass()%> <%=ann.getViewpoint()%>" id="<%=ann.getId()%>"><%=ann.getId().substring(0,8)%></td>
<% } %>

<% if (enc == null) { %>
    <td>(no encounter, deleted?)</td>
<% } else { %>
    <td title="<%=enc.getTaxonomyString()%>" id="<%=enc.getCatalogNumber()%>"><a href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber()%>">Enc <%=enc.getCatalogNumber().substring(0,8)%></a></td>
<% } %>

<% if (enc.hasMarkedIndividual()) { %>
    <td class="ind-name" id="<%=enc.getIndividualID()%>"><a href="../individual.jsp?number=<%=enc.getIndividualID()%>"><%=enc.getIndividualID()%></a></td>
<% } else { %>
    <td class="ind-name">-</td>
<% } %>

    <td class="task-res"><%=(taskRes.equals("")) ? "?" : taskRes%></td>

<% if (adminMode) {
    if ((subs != null) && (subs.size() > 0)) {
        String title = "";
        String td = "";
        for (User u : subs) {
            title += u.getUUID() + ":" + u.getUsername() + " ";
            td += u.getFullName() + "; ";
        }
    %>
    <td title="<%=title%>"><%=td%></td>
<%   } else { %>
    <td>-</td>
<% } } %>
</tr>
<%
}



%>

</tbody></table>

<%=logDump%>

</div>

<jsp:include page="../footer.jsp" flush="true"/>

