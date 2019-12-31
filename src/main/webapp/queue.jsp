<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,
org.ecocean.*,
javax.jdo.Query,
java.util.Collection,
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


//String jdoql = "SELECT FROM org.ecocean.Encounter";
String jdoql = "SELECT FROM org.ecocean.Encounter WHERE state=='new'";
Query query = myShepherd.getPM().newQuery(jdoql);
query.setOrdering("dateInMilliseconds");
Collection col = (Collection)query.execute();
List<Encounter> encs = new ArrayList<Encounter>(col);
query.closeAll();

if (maxRole.equals("cat_mouse_volunteer") && !forceList && (encs.size() > 0)) {
    String redir = "encounters/encounterDecide.jsp?id=" + encs.get(0).getCatalogNumber();
    myShepherd.rollbackDBTransaction();
    response.sendRedirect(redir);
    return;
}

%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">
<p>main role: <b><%=maxRole%></b></p>

<% if (encs.size() < 1) { %>
    <h1>There are no submissions needing attention right now!</h1>

<% } else { %>
<table>
<%
    for (Encounter enc : encs) {
        out.println("<tr>");
        out.println("<td>" + enc.getCatalogNumber() + "</td>");
        out.println("<td>" + enc.getDate() + "</td>");
        out.println("</tr>");
    }
%>
</table>

<% } //table %>

</div>

<jsp:include page="footer.jsp" flush="true" />

<%
myShepherd.rollbackDBTransaction();
%>
