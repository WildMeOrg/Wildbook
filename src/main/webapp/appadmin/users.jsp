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
  	//get our Shepherd for data retrieval and persistence
  	Shepherd myShepherd = new Shepherd();
  
  	//get the available user roles
  	ArrayList<String> roles=CommonConfiguration.getSequentialPropertyValues("role");
	ArrayList<String> roleDefinitions=CommonConfiguration.getSequentialPropertyValues("roleDefinition");
	int numRoles=roles.size();
  	int numRoleDefinitions=roleDefinitions.size();

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
      <table width="810px" class="tissueSample">
      <tr><th><strong>Username</strong></th>
      <th><strong>Full Name</strong></th>
      <th><strong>Email</strong></th>
      <th><strong>Affiliation</strong></th>
      <th><strong>Roles</strong></th>
      <th width="40px"><strong>Edit?</strong></th>
      <th><strong>Delete?</strong></th></tr>
      
      <%
      myShepherd.beginDBTransaction();
      ArrayList<User> allUsers=myShepherd.getAllUsers();
      int numUsers=allUsers.size();
      for(int i=0;i<numUsers;i++){
      	User user=allUsers.get(i);
      	String affiliation="&nbsp;";
      	if(user.getAffiliation()!=null){affiliation=user.getAffiliation();}
      	String fullName="&nbsp;";
      	if(user.getFullName()!=null){fullName=user.getFullName();}
      	String emailAddress="&nbsp;";
      	if(user.getEmailAddress()!=null){emailAddress=user.getEmailAddress();}
      	%>
      	<tr>
      		<td><%=user.getUsername()%></td>
      		<td><%=fullName%></td>
      		<td><%=emailAddress%></td>
      		<td><%=affiliation%></td>
      		<td><em><%=myShepherd.getAllRolesForUserAsString(user.getUsername()) %></em></td>
      		<td><a href="users.jsp?username=<%=user.getUsername()%>&isEdit=true#editUser"><img width="20px" height="20px" src="../images/Crystal_Clear_action_edit.png" /></a></td>   	
      		<td>
      			<%
      			if(!user.getUsername().equals(request.getUserPrincipal().getName())){
      			%>
      			<a href="../UserDelete?username=<%=user.getUsername()%>"><img  width="20px" height="20px" src="../images/cancel.gif" /></a>
      			<%
      			}
      			else {
      			%>
      			&nbsp;
      			<%
      			}
      			%>
      		</td>
      	</tr>
      	<%
      
      }
      
      %>
 
	</table>
    
     <h4 class="intro">Existing Roles</h4>
     <table width="100%" class="tissueSample">
      <tr><th><strong>Role</strong></th><th><strong>Definition</strong></th></tr>
		<%
								for(int q=0;q<numRoles;q++){
									String localRole=roles.get(q);
									String localDefinition="";
									if(numRoleDefinitions>=q){localDefinition=roleDefinitions.get(q);}
								%>
             					 <tr><td><%=localRole%></td><td><%=localDefinition%></td></tr>
              					<%
								}
		%>
    </table>
    
	<h4 class="intro"><a name="editUser" /></a>Create/Edit a User</h4>
	<p>
	<%
	String isEditAddition="";
	if(request.getParameter("isEdit")!=null){isEditAddition="?isEdit=true";}
	%>
    	<form action="../UserCreate<%=isEditAddition %>" method="post" id="newUser">
    		    <table width="100%" class="tissueSample">
    		    <%
    		    //let's set up any pre-defined values if appropriate
    		    String localUsername="";
    		    String localAffiliation="";
    		    String localEmail="";
    		    String localFullName="";
    		    
    		    if((request.getParameter("isEdit")!=null)&&(myShepherd.getUser(request.getParameter("username").trim())!=null)){
    		    	User thisUser=myShepherd.getUser(request.getParameter("username").trim());
    		    	localUsername=thisUser.getUsername();
    		    	if(thisUser.getAffiliation()!=null){
    		    		localAffiliation=thisUser.getAffiliation();
    		    	}
    		    	if(thisUser.getEmailAddress()!=null){
    		    		localEmail=thisUser.getEmailAddress();
    		    	}
    		    	if(thisUser.getFullName()!=null){
    		    		localFullName=thisUser.getFullName();
    		    	}
    		    }
    		    
    		    %>
    		    
    		    
      				<tr><td colspan="4"><em>This function allows you to create a new user account and assign appropriate roles. Available roles are independently configured, listed in commonConfiguration.properties, and matched to the URL-based functions of the Shepherd Project in the Apache Shiro filter in web.xml.</em></td></tr>
      				<tr>
            			
                        <%
                        String disabled="";
                        String readonly="";
                        if(request.getParameter("isEdit")!=null){
                        	disabled="disabled=\"disabled\"";
                        	readonly="readonly=\"readonly\"";
                        }
                        %>
                        <td>Username: <input name="username" type="text" size="15" maxlength="90" value="<%=localUsername %>" <%=readonly %>></input></td>
                        
                        <td>Password: <input name="password" type="password" size="15" maxlength="90" <%=disabled %>></input></td>
                        <td>Confirm Password: <input name="password2" type="password" size="15" maxlength="90" <%=disabled %>></input></td>
                        
                        
                        <td> Roles (multi-select): 
                        	<select multiple="multiple" name="rolename" id="rolename" size="5" required="required">
								<%
								for(int q=0;q<numRoles;q++){
									String selected="";
									if((request.getParameter("isEdit")!=null)&&(myShepherd.getUser(request.getParameter("username").trim())!=null)){
										if(myShepherd.doesUserHaveRole(request.getParameter("username").trim(),roles.get(q))){
											selected="selected=\"true\"";
										}
									}
									
					    		    	
								%>
             					 <option value="<%=roles.get(q)%>" <%=selected%>><%=roles.get(q)%></option>
              					<%
								}
								%>
                                
            				</select>
                        </td>	
            		</tr>
                    <tr><td colspan="4">Full name: <input name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>
                    <tr><td colspan="4">Email address: <input name="emailAddress" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td></tr>
                    <tr><td colspan="4">Affiliation: <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>
                    <tr><td colspan="4"><input name="Create" type="submit" id="Create" value="Create" /></td></tr>
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


