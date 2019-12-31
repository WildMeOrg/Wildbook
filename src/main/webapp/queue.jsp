<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
String langCode = ServletUtilities.getLanguageCode(request);
String context = ServletUtilities.getContext(request);
//Properties props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);
request.setAttribute("pageTitle", "Kitizen Science &gt; Queue");
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("queue.jsp");
myShepherd.beginDBTransaction();
boolean forceList = Util.requestParameterSet(request.getParameter("forceList"));
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

%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

</div>

<jsp:include page="footer.jsp" flush="true" />

myShepherd.rollbackDBTransaction();
