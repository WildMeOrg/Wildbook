<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%

Shepherd myShepherd=new Shepherd(request);
boolean committing=false;

%>

<html>
<head>
<title>Edit Organization</title>

</head>


<body>



<%

myShepherd.beginDBTransaction();

try {


  String username = request.getParameter("username");
  String orgname = request.getParameter("organization");
  boolean create  = Util.requestHasVal(request, "create");

  if (Util.requestHasVal(request, "commit")) committing=true;

  %>  <h1>Edit organization</h1>
  <p><em>committing = <%=committing%></em></p>
	<%

  // add user by default unless someone passed arg "remove"
  boolean addUser = !Util.requestParameterSet(request.getParameter("remove"));

  if (!Util.stringExists(username) || !Util.stringExists(orgname)) {
    %>
    <p><strong>ERROR</strong>: did not get valid parameter names. Received<ul>
      <li>username = <%=username%></li>
      <li>organization = <%=orgname%></li>
    </ul></p>
    <%
    myShepherd.rollbackAndClose();
    return;
  }

  Organization org = create ? myShepherd.getOrCreateOrganizationByName(orgname, committing) : myShepherd.getOrganizationByName(orgname);
  if (org==null) {%>
    <p><strong>ERROR</strong>: could not find organization <strong><%=orgname%></strong> in the database</p><%
    myShepherd.rollbackAndClose();
    return;
	}
  User user = myShepherd.getUser(username);
  if (user==null) {%>
    <p><strong>ERROR</strong>: could not find user <strong><%=username%></strong> in the database</p><%
    return;
  }

  %>

  <h4> Have user <em><%=user%></em> and org <em><%=org%></em></h4>
  <p>Before any changes, org.hasMember(user) = <%=org.hasMember(user)%> </p><p>
  <%
  myShepherd.beginDBTransaction();
  if (addUser) {
    org.addMember(user);
    %>Successfully added user to org! Org is now <%=org%>. hasMember(user) = <%=org.hasMember(user)%><%
  } else {
    org.removeMember(user);
    %>Successfully removed user from org! Org is now <%=org%>. hasMember(user) = <%=org.hasMember(user)%><%
  }
  if (committing) myShepherd.commitDBTransaction();
  myShepherd.closeDBTransaction();
  %>
  </p>
  <hr></hr>
  <ul>
<%





}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully</p>

</body>
</html>
