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
<%@ page import="org.ecocean.*,org.ecocean.servlet.ServletUtilities, org.ecocean.security.Collaboration, java.util.Properties, java.util.Date, java.text.SimpleDateFormat, java.io.*" %>


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
<%
		Properties collabProps = new Properties();
 		collabProps = ShepherdProperties.getProperties("collaboration.properties", langCode, context);
		ArrayList<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
		String me = request.getUserPrincipal().getName();
		String h = "";
		for (Collaboration c : collabs) {
			String cls = "state-" + c.getState();
			String msg = "state_" + c.getState();
			String click = "";

			if (c.getUsername1().equals(me)) {
				h += "<div class=\"mine " + cls + "\"><span class=\"who\">to <b>" + c.getUsername2() + "</b></span><span class=\"state\">" + collabProps.getProperty(msg) + "</span></div>";
			} else {
				if (msg.equals("state_initialized")) {
					msg = "state_initialized_me";
					click = " <span class=\"invite-response-buttons\" data-username=\"" + c.getUsername1() + "\"><input type=\"button\" class=\"yes\" value=\"" + collabProps.getProperty("buttonApprove") + "\">";
					click += "<input type=\"button\" class=\"no\" value=\"" + collabProps.getProperty("buttonDeny") + "\"></span>";
					click += "<script>$('.invite-response-buttons input').click(function(ev) { clickApproveDeny(ev); });</script>";
				}
				h += "<div class=\"notmine " + cls + "\"><span class=\"who\">from <b>" + c.getUsername1() + "</b></span><span class=\"state\">" + collabProps.getProperty(msg) + "</span>" + click + "</div>";
			}
		}
		if (h.equals("")) h = "<p>none</p>";
		out.println("<div class=\"collab-list\"><h1>" + collabProps.getProperty("collaborationTitle") + "</h1>" + h + "</div>");

		String rootWebappPath = getServletContext().getRealPath("/");
		File webappsDir = new File(rootWebappPath).getParentFile();
		File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
		File collabLogFile = new File(shepherdDataDir, "/users/" + thisUser.getUsername() + "/collaboration.log");
		if (collabLogFile.exists()) {
			long since = new Date().getTime() - (14 * 24*60*60*1000);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			h = "";
			BufferedReader br = new BufferedReader(new FileReader(collabLogFile));
			boolean hasOther = false;
			try {
				StringBuilder sb = new StringBuilder();
				String line = br.readLine();
				while (line != null) {
					String[] fields = line.split("\\t");
					long msec = Long.parseLong(fields[0]);
					if (msec > since) {
//12345	test3	ff95c37a-d08a-4460-a43e-2ee9155ba81d	http://wildme.org/batchupload/encounters/searchResults.jsp?state=unapproved	State is one of the following: unapproved <br />
						h += "<div class=\"logrow\"><span>" + sdf.format(new Date(msec)) + "</span><span>user: <b>" + fields[1] + "</b></span>";
						h += "<span>Encounter: <a href=\"encounters/encounter.jsp?number=" + fields[2] + "\">" + fields[2] + "</a></span>";
						h += "<span class=\"details\">Query: <a href=\"" + fields[3] + "\">" + fields[4] + "</a></div>";
					} else {
						hasOther = true;
					}
					line = br.readLine();
				}
			} finally {
				br.close();
			}
			out.println("<div class=\"collab-log\"><h1>Queries by collaborators</h1><div class=\"scrollbox\">" + h + "</div></div>");
			if (hasOther) out.println("<a href=\"myCollabLog.jsp\">See entire log of queries</a>");
		}

%>
    	
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


