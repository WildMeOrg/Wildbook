<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.lang3.StringEscapeUtils" %>


<%

String context="context0";
//context=ServletUtilities.getContext(request);
  	
  	
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("users.jsp");
  	//get the available user roles
  	List<String> roles=CommonConfiguration.getIndexedPropertyValues("role",context);
	List<String> roleDefinitions=CommonConfiguration.getIndexedPropertyValues("roleDefinition",context);
	int numRoles=roles.size();
  	int numRoleDefinitions=roleDefinitions.size();

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

    <jsp:include page="../header.jsp" flush="true" />

   
   
   
 <div class="container maincontent">
 
     
     <%
     
     myShepherd.beginDBTransaction();
     List<User> allUsers=myShepherd.getAllUsers();
     int numUsers=allUsers.size();
     
     %>

      <h1 class="intro">User Management</h1>
      <h4 class="intro">Existing Users (<%=numUsers %>)</h4>
      <table class="tissueSample">
      	<tr>
      		<th>&nbsp;</th>
      		<th><strong>Username</strong></th>
      		<th><strong>Full Name</strong></th>
      		<th><strong>Email</strong></th>
      		<th><strong>Affiliation</strong></th>
      		<th><strong>Roles</strong></th>
      		<th width="40px"><strong>Edit?</strong></th>
      		<th><strong>Delete?</strong></th>
      		<th><strong>Last Login</strong></th>
      	</tr>
      
      <%
      
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
      		<td>
      		<%
      		if(user.getUserImage()!=null){
      			String profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+user.getUsername()+"/"+user.getUserImage().getFilename();
      		%>
      		<img src="<%=profilePhotoURL %>" width="75px" height="*"/>
      		<%
      		}
      		else{
      		%>
      		&nbsp;
      		<%
      		}
      		%>
      		</td>
      		<td style="font-size:small"><%=StringEscapeUtils.escapeHtml4(user.getUsername())%></td>
      		<td style="font-size:small"><%=StringEscapeUtils.escapeHtml4(fullName)%></td>
      		<td style="font-size:small"><a href="mailto:<%=emailAddress%>"><img height="20px" width="20px" src="../images/Crystal_Clear_app_email.png" /></a></td>
      		<td style="font-size:small"><%=affiliation%></td>
      		<td style="font-size:x-small"><em><%=myShepherd.getAllRolesForUserAsString(user.getUsername()).replaceAll("\r","<br />") %></em></td>
      		<td><a href="users.jsp?context=context0&username=<%=user.getUsername()%>&isEdit=true#editUser"><img width="20px" height="20px" src="../images/Crystal_Clear_action_edit.png" /></a></td>   	
      		<td>
      			<%
      			if(!user.getUsername().equals(request.getUserPrincipal().getName())){
      			%>
      			<form onsubmit="return confirm('Are you sure you want to delete this user?');" action="../UserDelete?context=context0&username=<%=user.getUsername()%>" method="post"><input type="image"  width="20px" height="20px" src="../images/cancel.gif" /></form>
      			<%
      			}
      			else {
      			%>
      			&nbsp;
      			<%
      			}
      			%>
      		</td>
      		<td style="font-size:small">
      		<% 
      		if(user.getLastLoginAsDateString()!=null){
      		%>
      			<em><%=user.getLastLoginAsDateString().substring(0,user.getLastLoginAsDateString().indexOf("T")) %></em>
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
	if(request.getParameter("isEdit")!=null){isEditAddition="&isEdit=true";}
	%>
    	
    		    <table class="tissueSample">
    		    

    		    
    		    <%
    		    //let's set up any pre-defined values if appropriate
    		    String localUsername="";
    		    String localAffiliation="";
    		    String localEmail="";
    		    String localFullName="";
    		    String profilePhotoURL="../images/empty_profile.jpg";
    		    String userProject="";
    		    String userStatement="";
    		    String userURL="";
    		    String receiveEmails="checked=\"checked\"";
    		    boolean hasProfilePhoto=false;
    		    
    		    if((request.getParameter("isEdit")!=null)&&(myShepherd.getUser(request.getParameter("username").trim())!=null)){
    		    	User thisUser=myShepherd.getUser(request.getParameter("username").trim());
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
    		    }
    		    
    		    %>
    		    
    		        		    <tr>
		        		    	<td style="width: 200px;">
		        		    		<table style="border: solid 0;">
		        		    			<tr>
		        		    				<td style="border: solid 0;border-spacing: 0;width: 200px;">
		        		    					<img src="<%=profilePhotoURL%>" width="200px" height="*" />
		        		    				</td>
		        		    			</tr>
		        		    			<%
		        		    			if(request.getParameter("isEdit")!=null){
		        		    			%>
		        		    			<tr>
		        		    					<td style="border: solid 0">
		        		    						<form action="../UserAddProfileImage?context=context0" method="post" enctype="multipart/form-data" name="UserAddProfileImage">
        												<img src="../images/upload_small.gif" align="absmiddle" />&nbsp;Upload photo:<br /> 
		        		    						 <input name="username" type="hidden" value="<%=localUsername%>" id="profileUploadUsernameField" />
        												<input name="file2add" type="file" style="width: 200px"/>
        												<input name="addtlFile" type="submit" id="addtlFile" value="Upload" />
        											</form>
		        		    					</td>
		        		    				</tr>
		        		    				<%
		        		    				if(hasProfilePhoto){
		        		    				%>
		        		    					<tr><td style="border: solid 0">Delete profile photo:&nbsp;<a href="../UserRemoveProfileImage?username=<%=localUsername%>"><img src="../images/cancel.gif" width="16px" height="16px" align="absmiddle" /></a></td></tr>
		        		    			
		        		    				<%
		        		    				}
		        		    			}
		        		    			%>
		        		    			</table>
		        		    		
		        		    	</td>
		        	<form action="../UserCreate?context=context0<%=isEditAddition %>" method="post" id="newUser" accept-charset="UTF-8">	    
    		    	<td><table width="100%" class="tissueSample">
      				<tr><td colspan="3"><em>This function allows you to create a new user account and assign appropriate roles. Available roles are independently configured, listed in commonConfiguration.properties, and matched to the URL-based functions of the Shepherd Project in the Apache Shiro filter in web.xml.</em></td></tr>
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
                        
                        

            		</tr>
                    <tr><td colspan="3">Full name: <input name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>
                    <tr><td colspan="2">Email address: <input name="emailAddress" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td><td colspan="1">Receive automated emails? <input type="checkbox" name="receiveEmails" value="receiveEmails" <%=receiveEmails %>/></td></tr>
                    <tr><td colspan="3">Affiliation: <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>
                     <tr><td colspan="3">Research Project: <input name="userProject" type="text" size="15" maxlength="90" value="<%=userProject %>"></input></td></tr>
                          
                    <tr><td colspan="3">Project URL: <input name="userURL" type="text" size="15" maxlength="90" value="<%=userURL %>"></input></td></tr>
		     <tr><td colspan="3" valign="top">User Statement (255 char. max): <textarea name="userStatement" size="100" maxlength="255"><%=userStatement%></textarea></td></tr>                  
                    
                    <tr><td colspan="3"><input name="Create" type="submit" id="Create" value="Create" /></td></tr>
            </table>
            </td>
            <td>
            <table>
           
            <%
            List<String> contexts=ContextConfiguration.getContextNames();
            int numContexts=contexts.size();
            for(int d=0;d<numContexts;d++){
            	%>
            	 <tr>
            <td>
            
            
            Roles for <%=ContextConfiguration.getNameForContext(("context"+d)) %>(multi-select): 
                        	<select multiple="multiple" name="context<%=d %>rolename" id="rolename" size="5">
                        		<option value=""></option>
								<%
								for(int q=0;q<numRoles;q++){
									String selected="";
									if((request.getParameter("isEdit")!=null)&&(myShepherd.getUser(request.getParameter("username").trim())!=null)){
										if(myShepherd.doesUserHaveRole(request.getParameter("username").trim(),roles.get(q),("context"+d))){
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
            <%	
            }
            %>
            
            </table>
				
            </td>	
            
            
            </form>
            </tr>
            </table>
    	
    </p>
    <%
    if((CommonConfiguration.getProperty("showUserAgreement",context)!=null)&&(CommonConfiguration.getProperty("showUserAgreement",context).equals("true"))){
    %>
            <p>&nbsp;</p>
      <table class="tissueSample" style="border: 1px solid black;" width="100%" border="1">
        <tr>
          <td>
            <p><font size="+1">Reset User Agreement Acceptance for All Users</font></p>
            <p>This command resets all User accounts such that each user must reaccept the User Agreement upon the next login.</p>

            <form name="UserResetAcceptedUserAgreement" method="post" action="../UserResetAcceptedUserAgreement?context=context0">

              <input name="UserResetAcceptedUserAgreementButton" type="submit" id="UserResetAcceptedUserAgreementButton" value="Reset">
              </p></form>
          </td>
        </tr>
      </table>
	<%
	}
	%>
	
	</div>

      <jsp:include page="../footer.jsp" flush="true"/>
    
<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>



