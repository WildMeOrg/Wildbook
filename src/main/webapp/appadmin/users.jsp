<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="java.util.ArrayList" %>
<%@ page import="org.ecocean.*" %>


<%

  Shepherd myShepherd = new Shepherd();

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle() %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>

  <style type="text/css">
    <!--
    .style1 {
      color: #FF0000
    }

    -->
  </style>
</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../header.jsp" flush="true">

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
     

      <h1 class="intro">User Management</h1>
      <h4 class="intro">Existing Users</h4>
      <table width="100%" class="tissueSample">
      <tr><th><strong>Username</strong></th><th><strong>Roles</strong></th><th><strong>Delete?</strong></th></tr>
      
      <%
      myShepherd.beginDBTransaction();
      ArrayList<User> allUsers=myShepherd.getAllUsers();
      int numUsers=allUsers.size();
      for(int i=0;i<numUsers;i++){
      	User user=allUsers.get(i);
      	%>
      	<tr><td><%=user.getUsername()%></td><td><em><%=myShepherd.getAllRolesForUserAsString(user.getUsername()) %></em></td><td><a href="../UserDelete?username=<%=user.getUsername()%>"><img src="../images/cancel.gif" /></a></td></tr>
      	<%
      
      }
      
      %>
 
	</table>
	
	<h4 class="intro">Create a User</h4>
	<p>
    	<form action="../UserCreate" method="post" id="newUser">
    		    <table width="100%" class="tissueSample">
      				<tr><th><strong>Username</strong></th><th><strong>Password</strong></th><th><strong>Roles</strong></th></tr>
                   	<tr><td colspan="3"><em>This function allows you to create a new user account and assign appropriate roles. Available roles are independently configured and listed in commonConfiguration.properties and matched to the URL-based functions of the Shepherd Project in web.xml.</em></td></tr>
      				<tr>
            			<td>Username: <input name="username" type="text" size="15" maxlength="90" /></td>
                        <td>Password: <input name="password" type="text" size="15" maxlength="90" /></td>
                        
                        
                        <td> Roles: 
                        	<select multiple="multiple" name="rolename" id="rolename" size="5" required="required">

             					 <option value="admin">admin</option>
              					<option value="destroyer">destroyer</option>

            				</select>
                        </td>	
            		</tr>
                    <tr><td colspan="3"><input name="Create" type="submit" id="Create" value="Create" /></td></tr>
            </table>
    	</form>
    </p>
	
      <jsp:include page="../footer.jsp" flush="true"/>
    </div>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>
</body>
</html>


