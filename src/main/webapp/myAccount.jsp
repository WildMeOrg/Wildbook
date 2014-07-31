<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2014 Jason Holmberg
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
<%@ page import="org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.util.Properties" %>


<%


String context="context0";

//get language
String langCode = ServletUtilities.getLanguageCode(request);

//load user props
Properties props=ShepherdProperties.getProperties("users.properties", langCode,context);



  	
  	
  Shepherd myShepherd = new Shepherd(context);
  	//get the available user roles
  	ArrayList<String> roles=CommonConfiguration.getSequentialPropertyValues("role",context);
	ArrayList<String> roleDefinitions=CommonConfiguration.getSequentialPropertyValues("roleDefinition",context);
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
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

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
    <jsp:include page="header.jsp" flush="true">

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
     
  

      <h1 class="intro"><%=(props.getProperty("userAccount")+" "+request.getUserPrincipal()) %></h1>
   
    
	<p>

    	
    		    <table width="100%" class="tissueSample">
    		    

    		    
    		    <%
    		    //let's set up any pre-defined values if appropriate
    		    String localUsername="";
    		    String localAffiliation="";
    		    String localEmail="";
    		    String localFullName="";
    		    String profilePhotoURL="images/empty_profile.jpg";
    		    String userProject="";
    		    String userStatement="";
    		    String userURL="";
    		    String receiveEmails="checked=\"checked\"";
    		    boolean hasProfilePhoto=false;
    		    
    		    User thisUser=myShepherd.getUser(request.getUserPrincipal().getName());
    		    	localUsername=thisUser.getUsername();
    		    	if(thisUser.getAffiliation()!=null){
    		    		localAffiliation=thisUser.getAffiliation();
    		    	}
    		    	if(thisUser.getEmailAddress()!=null){
    		    		localEmail=thisUser.getEmailAddress();
    		    	}
    		    	if(!thisUser.getReceiveEmails()){receiveEmails="";}
    		    	if(thisUser.getFullName()!=null){
    		    		localFullName=thisUser.getFullName();
    		    	}
    		    	if(thisUser.getUserProject()!=null){
			    userProject=thisUser.getUserProject();
    		    	}
    		    	if(thisUser.getUserStatement()!=null){
				userStatement=thisUser.getUserStatement();
    		    	}
    		    	if(thisUser.getUserURL()!=null){
				userURL=thisUser.getUserURL();
    		    	}
    		    	if(thisUser.getUserImage()!=null){
    		    		profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
    		    	}
    		    	if(thisUser.getUserImage()!=null){hasProfilePhoto=true;}
    		    
    		    
    		    %>
    		    
    		        		    <tr>
		        		    	<td>
		        		    		<table border="0">
		        		    			<tr>
		        		    				<td style="border: solid 0">
		        		    					<img src="<%=profilePhotoURL%>" width="200px" height="*" />
		        		    				</td>
		        		    			</tr>
		        		    		
		        		    			<tr>
		        		    					<td style="border: solid 0"><form action="MyAccountAddProfileImage?context=context0" method="post" enctype="multipart/form-data" name="UserAddProfileImage">
        												<img src="images/upload_small.gif" align="absmiddle" />&nbsp;<%=props.getProperty("uploadPhoto") %><br /> 
		        		    						 <input name="username" type="hidden" value="<%=localUsername%>" id="profileUploadUsernameField" />
        												<input name="file2add" type="file" size="20" />
        												<input name="addtlFile" type="submit" id="addtlFile" value="<%=props.getProperty("upload") %>" />
        											</form>
		        		    					</td>
		        		    				</tr>
		        		    				<%
		        		    				if(hasProfilePhoto){
		        		    				%>
		        		    					<tr><td style="border: solid 0"><%=props.getProperty("deleteProfile") %>&nbsp;<a href="MyAccountRemoveProfileImage"><img src="images/cancel.gif" width="16px" height="16px" align="absmiddle" /></a></td></tr>
		        		    			
		        		    				<%
		        		    				}
		        		    			
		        		    			%>
		        		    			</table>
		        		    		
		        		    	</td>
		        	<form action="UserSelfUpdate?context=context0" method="post" id="editUser">	    
    		    	<td><table width="100%" class="tissueSample">
      				<tr>
            	
                        
                        <td style="border-bottom: 0px white;"><%=props.getProperty("newPassword") %> <input name="password" type="password" size="15" maxlength="90" ></input></td>
                        <td style="border-bottom: 0px white;" colspan="2"><%=props.getProperty("confirm") %> <%=props.getProperty("newPassword") %> <input name="password2" type="password" size="15" maxlength="90" ></input></td>
                        
                        

            		</tr>
            		<tr><td colspan="3" style="border-top: 0px white;font-style:italic;"><%=props.getProperty("leaveBlankNoChangePassword") %></td></tr>
                    <tr><td colspan="3"><%=props.getProperty("fullname") %> <input name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>
                    <tr><td colspan="2"><%=props.getProperty("emailAddress") %> <input name="emailAddress" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td><td colspan="1"><%=props.getProperty("receiveEmails") %> <input type="checkbox" name="receiveEmails" value="receiveEmails" <%=receiveEmails %>/></td></tr>
                    <tr><td colspan="3"><%=props.getProperty("affiliation") %> <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>
                     <tr><td colspan="3"><%=props.getProperty("researchProject") %> <input name="userProject" type="text" size="15" maxlength="90" value="<%=userProject %>"></input></td></tr>
                          
                    <tr><td colspan="3"><%=props.getProperty("projectURL") %> <input name="userURL" type="text" size="15" maxlength="90" value="<%=userURL %>"></input></td></tr>
		     <tr><td colspan="3" valign="top"><%=props.getProperty("researchStatement") %> <textarea name="userStatement" size="100" maxlength="255"><%=userStatement%></textarea></td></tr>                  
                    
                    <tr><td colspan="3"><input name="Create" type="submit" id="Create" value="<%=props.getProperty("update") %>" /></td></tr>
            </table>
            </td>
            <td>
            <table>
           
            <%
            ArrayList<String> contexts=ContextConfiguration.getContextNames();
            int numContexts=contexts.size();
            for(int d=0;d<numContexts;d++){
            	if(myShepherd.doesUserHaveAnyRoleInContext(localUsername, context)){
            	%>
            	 <tr>
            <td>
            
            
            <%=props.getProperty("roles4") %> <%=ContextConfiguration.getNameForContext(("context"+d)) %> 
                        	<select multiple="multiple" name="context<%=d %>rolename" id="rolename" size="5" disabled="disabled">
                        		<%
								for(int q=0;q<numRoles;q++){
									//String selected="";
									if(myShepherd.getUser(request.getUserPrincipal().getName())!=null){
										if(myShepherd.doesUserHaveRole(request.getUserPrincipal().getName(),roles.get(q),("context"+d))){
											%>
											<option value="<%=roles.get(q)%>"><%=roles.get(q)%></option>
											<% 
										}
									}
									
					    		    	
								
								}
								%>
                                
            				</select>
            
            
            </td>
            </tr>
            <%	
            }
            }
            %>
            
            </table>
				
            </td>	
            
            
            </form>
            </tr>
            </table>
    	
    </p>

      <jsp:include page="footer.jsp" flush="true"/>
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


