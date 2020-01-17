<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,
org.ecocean.*,
javax.jdo.Query,
java.util.Map, java.util.HashMap,
org.json.JSONObject, org.json.JSONArray,
org.ecocean.servlet.export.ExportExcelFile,
java.util.Collection,
org.joda.time.DateTime,
org.apache.commons.io.FileUtils,
org.apache.commons.lang3.StringEscapeUtils" %>
<%!

private static Encounter getTruthEncounter(Shepherd myShepherd, String individualId) {
    return myShepherd.getEncounter("0e159eb4-9293-4f4d-935b-f2f8871a2097");
}

private static void generateData(Shepherd myShepherd, File file, String dtype) throws java.io.IOException {
    String jdoql = "SELECT FROM org.ecocean.Decision";
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("timestamp");
    Collection col = (Collection)query.execute();
    List<Decision> decs = new ArrayList<Decision>(col);
    query.closeAll();

    List rows = new ArrayList<String[]>();
    if ("match".equals(dtype)) {
        String[] head = new String[]{"Enc ID", "Cat ID", "Timestamp", "Date/Time", "User ID", "Username", "Match Enc ID", "Match Cat ID"};
        rows.add(head);
        for (Decision dec : decs) {
            if (!"match".equals(dec.getProperty())) continue;
            JSONObject d = dec.getValue();
            if (d == null) continue;
            String eid = d.optString("id", null);
            if (eid == null) continue;
            Encounter menc = myShepherd.getEncounter(eid);
            if (menc == null) {
                System.out.println("WARNING queue.generateData() could not find Encounter for match id=" + menc);
                continue;
            }
            String[] row = new String[head.length];
            row[0] = dec.getEncounter().getCatalogNumber();
            row[1] = dec.getEncounter().getIndividualID();
            row[2] = Long.toString(dec.getTimestamp());
            row[3] = new DateTime(dec.getTimestamp()).toString();
            row[4] = dec.getUser().getUUID();
            row[5] = dec.getUser().getUsername();
            row[6] = menc.getCatalogNumber();
            row[7] = menc.getIndividualID();
            rows.add(row);
        }

    } else {  //attributes flavor
        String[] head = new String[]{"Enc ID", "Cat ID", "Timestamp", "Date/Time", "User ID", "Username", "Color/Pattern", "Color/Patt correct", "Life stage", "Life stage correct", "Sex", "Sex correct", "Sex unk ok", "Collar", "Collar correct", "Collar unk ok", "Ear Tip", "Ear Tip correct", "Ear Tip swap ok", "Ear Tip unk ok"};
        rows.add(head);
/*
        Map<String,Integer> indMap = new HashMap<String,Integer>();
        for (int i = 0 ; i < head.length ; i++) {
            indMap.put(head[i], i);
        }
*/
        Map<String,String[]> dataMap = new HashMap<String,String[]>();
        for (Decision dec : decs) {
            JSONObject d = dec.getValue();
            if (d == null) continue;
            String mid = d.optString("_multipleId", null);
            if (mid == null) continue;
            if (dataMap.get(mid) == null) dataMap.put(mid, new String[head.length]);
            dataMap.get(mid)[0] = dec.getEncounter().getCatalogNumber();
            dataMap.get(mid)[1] = dec.getEncounter().getIndividualID();
            dataMap.get(mid)[2] = Long.toString(dec.getTimestamp());
            dataMap.get(mid)[3] = new DateTime(dec.getTimestamp()).toString();
            dataMap.get(mid)[4] = dec.getUser().getUUID();
            dataMap.get(mid)[5] = dec.getUser().getUsername();

            String prop = dec.getProperty();
            if (prop == null) continue;
            Encounter truthEnc = getTruthEncounter(myShepherd, dec.getEncounter().getIndividualID());
            //Integer valI = indMap.get(dec.getProperty());
            String val = d.optString("value", null);
            //if ((val == null) && (d.optJSONArray("value") != null)) val = d.getJSONArray("value").join(", ");

            if (prop.equals("colorPattern")) {
                dataMap.get(mid)[6] = val;
                dataMap.get(mid)[7] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getPatterningCode() != null) && truthEnc.getPatterningCode().equals(val)).toString();
            } else if (prop.equals("lifeStage")) {
                dataMap.get(mid)[8] = val;
                dataMap.get(mid)[9] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getLifeStage() != null) && truthEnc.getLifeStage().equals(val)).toString();
            } else if (prop.equals("sex")) {
                dataMap.get(mid)[10] = val;
                dataMap.get(mid)[11] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getSex() != null) && truthEnc.getSex().equals(val)).toString();
                dataMap.get(mid)[12] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getSex() != null) && (truthEnc.getSex().equals(val) || val.equals("unknown"))).toString();
            } else if (prop.equals("collar")) {
                dataMap.get(mid)[13] = val;
                dataMap.get(mid)[14] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getCollar() != null) && truthEnc.getCollar().equals(val)).toString();
                dataMap.get(mid)[15] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getCollar() != null) && (truthEnc.getCollar().equals(val) || val.equals("unknown"))).toString();
            } else if (prop.equals("earTip")) {
                dataMap.get(mid)[16] = val;
                dataMap.get(mid)[17] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getEarTip() != null) && truthEnc.getEarTip().equals(val)).toString();
                dataMap.get(mid)[18] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getEarTip() != null) && !truthEnc.getEarTip().equals(val) && val.startsWith("yes")).toString();
                dataMap.get(mid)[19] = new Boolean((val != null) && (truthEnc != null) && (truthEnc.getEarTip() != null) && (truthEnc.getEarTip().equals(val) || val.equals("unknown"))).toString();
            } else {
                System.out.println("WARNING: queue.generateData() found bad property " + dec.getProperty() + " on Decision id=" + dec.getId());
            }
        }
        for (String mid : dataMap.keySet()) {
            rows.add(dataMap.get(mid));
//System.out.println(String.join("|", dataMap.get(mid)));
        }
    }

    ExportExcelFile.quickExcel(rows, file);
}

%>
<%

//setup our Properties object to hold all properties
String langCode = ServletUtilities.getLanguageCode(request);
String context = ServletUtilities.getContext(request);
//Properties props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);
request.setAttribute("pageTitle", "Kitizen Science &gt; Queue");
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("queue.jsp");
myShepherd.beginDBTransaction();
User user = AccessControl.getUser(request, myShepherd);
if (user == null) {
    myShepherd.rollbackDBTransaction();
    response.sendRedirect("login.jsp");
    return;
}

String[] validRoles = new String[]{"admin", "super_volunteer", "cat_mouse_volunteer", "cat_walk_volunteer"};
List<Role> userRoles = myShepherd.getAllRolesForUserInContext(user.getUsername(), context);
String maxRole = null;
foundMaxRole:
for (String vr : validRoles) {
    for (Role r : userRoles) {
        if (vr.equals(r.getRolename())) {
            maxRole = vr;
            break foundMaxRole;
        }
    }
}
System.out.println("INFO: queue.jsp maxRole=" + maxRole + " for " + user);

//maxRole = "cat_mouse_volunteer";  //faked for testing
//TODO what to do about cat_walk_volunteer ???
if (maxRole == null) {
    //response.sendError(401, "access denied - no valid role");
    myShepherd.rollbackDBTransaction();
    response.sendRedirect("register.jsp");
    return;
}

boolean isAdmin = (maxRole.equals("super_volunteer") || maxRole.equals("admin"));
boolean forceList = Util.requestParameterSet(request.getParameter("forceList")) || isAdmin;

String dtype = request.getParameter("data");
if (Util.requestParameterSet(dtype)) {
    File xls = new File("/tmp/kitsci_export_" + Util.basicSanitize(dtype) + "_" + new DateTime().toLocalDate() + "_" + Util.generateUUID().substring(0,6) + ".xls");
    generateData(myShepherd, xls, dtype);
    response.setHeader("Content-type", "application/vnd.ms-excel");
    response.setHeader("Content-disposition", "attachment; filename=\"" + xls.getName() + "\"");
    FileUtils.copyFile(xls, response.getOutputStream());
    return;
}

String jdoql = "SELECT FROM org.ecocean.Encounter";
//if (!isAdmin) jdoql = "SELECT FROM org.ecocean.Encounter WHERE state=='new'";  //FIXME this is for testing only
Query query = myShepherd.getPM().newQuery(jdoql);
query.setOrdering("state, dateInMilliseconds");
Collection col = (Collection)query.execute();
List<Encounter> encs = new ArrayList<Encounter>(col);
query.closeAll();

if (!forceList && (encs.size() > 0)) {
    String redir = "encounters/encounterDecide.jsp?id=" + encs.get(0).getCatalogNumber();
    myShepherd.rollbackDBTransaction();
    response.sendRedirect(redir);
    return;
}

String[] theads = new String[]{"ID", "Sub Date"};
if (isAdmin) theads = new String[]{"ID", "State", "Sub Date", "Last Dec", "Dec Ct", "Flags"};
%>

<jsp:include page="header.jsp" flush="true" />
<style>
.row-state-approved {
    display: none;
}
.col-flag {
    background-color: #FAA;
}
.col-fct-0, .col-dct-0, .col-muted {
    color: #BBB;
    background-color: inherit;
}

.col-id {
    position: relative;
}
.col-id img {
    height: 50px;
    float: right;
}
.th-0, .th-1 {
    width: 8em;
}
.th-2, .th-3 {
    width: 12em;
}
.th-4, .th-5 {
    width: 4em;
}
</style>


<div class="container maincontent">
<!-- main role: <%=maxRole%> -->

<% if (isAdmin) { %>
<p>
    <a href="queue.jsp?data=attributes" title="Download XLS with volunteer decisions on attributes">
        <button>Download Attributes XLS</button>
    </a>
    <a href="queue.jsp?data=match" title="Download XLS with volunteer cat ID matches">
        <button>Download ID Match XLS</button>
    </a>
</p>
<% } %>

<% if (encs.size() < 1) { %>
    <h1>There are no submissions needing attention right now!</h1>

<% } else { %>
<table id="queue-table" xdata-page-size="6" data-height="650" data-toggle="table" data-pagination="false">
<thead>
<tr>
<% for (int ci = 0 ; ci < theads.length ; ci++) { %>
    <th class="th-<%=ci%>" data-sortable="true"><%=theads[ci]%></th>
<% } %>
</tr>
</thead>
<tbody>
<%
    for (Encounter enc : encs) {
        out.println("<tr class=\"row-state-" + enc.getState() + "\">");
        out.println("<td class=\"col-id\">");
        if (isAdmin) {
            out.println("<a href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\" target=\"new\">" + enc.getCatalogNumber().substring(0,8) + "</a>");
        } else {
            out.println("<a href=\"encounters/encounterDecide.jsp?id=" + enc.getCatalogNumber() + "\" target=\"new\">" + enc.getCatalogNumber().substring(0,8) + "</a>");
        }
/*
        if (enc.getMedia().size() > 0) {
            out.println("<img src=\"" + enc.getMedia().get(0).safeURL(request) + "\" />");
        }
*/
        out.println("</td>");
        if (isAdmin) out.println("<td class=\"col-state-" + enc.getState() + "\">" + enc.getState() + "</td>");
        out.println("<td>" + enc.getDate() + "</td>");

        if (isAdmin) {
            jdoql = "SELECT FROM org.ecocean.Decision WHERE encounter.catalogNumber=='" + enc.getCatalogNumber() + "'";
            query = myShepherd.getPM().newQuery(jdoql);
            col = (Collection)query.execute();
            List<Decision> decs = new ArrayList<Decision>(col);
            query.closeAll();
            int dct = 0;
            int fct = 0;
            long lastT = 0L;
            for (Decision dec : decs) {
                if ("sex".equals(dec.getProperty())) dct++;
                if ("flag".equals(dec.getProperty())) fct++;
                //out.println("<b>" + dec.getProperty() + "</b> " + dec.getValue() + "</p>");
                if (dec.getTimestamp() > lastT) lastT = dec.getTimestamp();
            }
            if (lastT > 0L) {
                //out.println("<td class=\"col-muted\">9999</td>");
                out.println("<td class=\"col-date\">" + new DateTime(lastT).toLocalDate() + "</td>");
            } else {
                out.println("<td class=\"col-muted\">-</td>");
            }
            out.println("<td class=\"col-dct-" + dct + "\">" + dct + "</td>");
            out.println("<td class=\"col-flag col-fct-" + fct + "\">" + fct + "</td>");
        }

        out.println("</tr>");
    }
%>
</tbody>
</table>

<% } //table %>

</div>

    <script src="javascript/bootstrap-table/bootstrap-table.min.js"></script>
    <link rel="stylesheet" href="javascript/bootstrap-table/bootstrap-table.min.css" />
<script>
$(document).ready(function() {
    $('.col-flag').each(function(i, el) {
        var jel = $(el);
//console.log('%d %o %o %o', i, el, el.parentElement, jel.text());
        if (jel.text() > 0) jel.parent().show();
    });
});
</script>


<jsp:include page="footer.jsp" flush="true" />

<%
myShepherd.rollbackDBTransaction();
%>
