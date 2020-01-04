<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,
org.ecocean.*,
javax.jdo.Query,
java.util.Collection,
org.joda.time.DateTime,
org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
String langCode = ServletUtilities.getLanguageCode(request);
String context = ServletUtilities.getContext(request);
//Properties props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);
request.setAttribute("pageTitle", "Kitizen Science &gt; Queue");
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("queue.jsp");
myShepherd.beginDBTransaction();
//boolean forceList = Util.requestParameterSet(request.getParameter("forceList"));
boolean forceList = true;
User user = AccessControl.getUser(request, myShepherd);
/*
if (user == null) {
    response.sendError(401, "access denied");
    myShepherd.rollbackDBTransaction();
    return;
}
*/

String[] validRoles = new String[]{"cat_walk_volunteer", "cat_mouse_volunteer", "super_volunteer", "admin"};
List<Role> userRoles = myShepherd.getAllRolesForUserInContext(user.getUsername(), context);
String maxRole = null;
for (String vr : validRoles) {
    for (Role role : userRoles) {
        if (vr.equals(role.getRolename())) {
            maxRole = vr;
            break;
        }
    }
}

maxRole = "cat_mouse_volunteer";  //faked for testing
if (maxRole == null) {
    response.sendError(401, "access denied - no valid role");
    myShepherd.rollbackDBTransaction();
    return;
}

boolean isAdmin = true;   //!(maxRole.equals("cat_mouse_volunteer"));

String jdoql = "SELECT FROM org.ecocean.Encounter";
//String jdoql = "SELECT FROM org.ecocean.Encounter WHERE state=='new'";
Query query = myShepherd.getPM().newQuery(jdoql);
query.setOrdering("state, dateInMilliseconds");
Collection col = (Collection)query.execute();
List<Encounter> encs = new ArrayList<Encounter>(col);
query.closeAll();

if (maxRole.equals("cat_mouse_volunteer") && !forceList && (encs.size() > 0)) {
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
</style>


<div class="container maincontent">
<p>main role: <b><%=maxRole%></b></p>

<% if (encs.size() < 1) { %>
    <h1>There are no submissions needing attention right now!</h1>

<% } else { %>
<table id="queue-table" xdata-page-size="6" data-height="650" data-toggle="table" data-pagination="false">
<thead>
<tr>
<th data-sortable="true"><%=String.join("</th><th data-sortable=\"true\">", theads)%></th>
</tr>
</thead>
<tbody>
<%
    for (Encounter enc : encs) {
        out.println("<tr>");
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


<jsp:include page="footer.jsp" flush="true" />

<%
myShepherd.rollbackDBTransaction();
%>
