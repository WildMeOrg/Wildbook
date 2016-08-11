<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.*" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="java.text.MessageFormat" %>
<%
	String context = ServletUtilities.getContext(request);
	String langCode = ServletUtilities.getLanguageCode(request);
	Locale locale = new Locale(langCode);
	Properties props = ShepherdProperties.getProperties("users.properties", langCode, context);
	Properties cciProps = ShepherdProperties.getProperties("commonCoreInternational.properties", langCode, context);

  Shepherd myShepherd = new Shepherd(context);
	// Get the available user roles
	Map<String, String> rolesMap = CommonConfiguration.getIndexedValuesMap("role", context);
	Map<String, String> roleDefinitions = CommonConfiguration.getIndexedValuesMap("roleDefinition", context);
	// Get i18n resources (to allow i18n if defined)
	Map<String, String> roleDefinitionsI18n = Util.getIndexedValuesMap(cciProps, "roleDefinition");

  // Handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">
 
     
     <%
     
     myShepherd.beginDBTransaction();
     ArrayList<User> allUsers=myShepherd.getAllUsers();
     int numUsers=allUsers.size();
     
     %>

      <h1 class="intro"><%=props.getProperty("title")%></h1>
      <h4 class="intro"><%=StringUtils.format(locale, props.getProperty("existingUsers"), numUsers)%></h4>
      <table class="tissueSample">
      	<tr>
      		<th>&nbsp;</th>
      		<th><strong><%=props.getProperty("column.username")%></strong></th>
      		<th><strong><%=props.getProperty("column.fullName")%></strong></th>
      		<th><strong><%=props.getProperty("column.email")%></strong></th>
      		<th><strong><%=props.getProperty("column.affiliation")%></strong></th>
      		<th><strong><%=props.getProperty("column.roles")%></strong></th>
      		<th width="40px"><strong><%=props.getProperty("column.edit")%></strong></th>
      		<th><strong><%=props.getProperty("column.delete")%></strong></th>
      		<th><strong><%=props.getProperty("column.lastLogin")%></strong></th>
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
      		<td style="font-size:small"><%=user.getUsername()%></td>
      		<td style="font-size:small"><%=fullName%></td>
      		<td style="font-size:small"><a href="mailto:<%=emailAddress%>"><img height="20px" width="20px" src="../images/Crystal_Clear_app_email.png" /></a></td>
      		<td style="font-size:small"><%=affiliation%></td>
      		<td style="font-size:x-small"><em><%=myShepherd.getAllRolesForUserAsString(user.getUsername()).replaceAll("\r","<br />") %></em></td>
      		<td><a href="users.jsp?context=context0&username=<%=user.getUsername()%>&isEdit=true#editUser"><img width="20px" height="20px" src="../images/Crystal_Clear_action_edit.png" /></a></td>   	
      		<td>
      			<%
      			if(!user.getUsername().equals(request.getUserPrincipal().getName())){
      			%>
      			<form onsubmit="return confirm('<%=props.getProperty("confirmDelete")%>');" action="../UserDelete?context=context0&username=<%=user.getUsername()%>" method="post"><input type="image"  width="20px" height="20px" src="../images/cancel.gif" /></form>
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
    
     <h4 class="intro"><%=props.getProperty("existingRoles")%></h4>
     <table width="100%" class="tissueSample">
      <tr>
				<th><strong><%=props.getProperty("column.role")%></strong></th>
				<th><strong><%=props.getProperty("column.definition")%></strong></th>
			</tr>
<%
	for (Map.Entry<String, String> me : rolesMap.entrySet()) {
		// Get i18n version of definition, with fallback to standard definition.
		String roleDef = roleDefinitionsI18n.get(me.getKey().replace("role", "roleDefinition"));
		if (roleDef == null)
			roleDef = roleDefinitions.get(me.getKey().replace("role", "roleDefinition"));
%>
			 <tr><td><%=me.getValue()%></td><td><%=roleDef%></td></tr>
<%
	}
%>
    </table>
    
	<h4 class="intro"><a name="editUser" /></a><%=props.getProperty("createEditUser")%></h4>
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
		        	<form action="../UserCreate?context=context0<%=isEditAddition %>" method="post" id="newUser">	    
    		    	<td><table width="100%" class="tissueSample">
      				<tr><td colspan="3"><em><%=props.getProperty("createEdit.describe")%></em></td></tr>
      				<tr>
            			
                        <%
                        String disabled="";
                        String readonly="";
                        if(request.getParameter("isEdit")!=null){
                        	disabled="disabled=\"disabled\"";
                        	readonly="readonly=\"readonly\"";
                        }
                        %>
                        <td><%=props.getProperty("createEdit.username")%>: <input name="username" type="text" size="15" maxlength="90" value="<%=localUsername %>" <%=readonly %>></input></td>
                        
                        <td><%=props.getProperty("createEdit.password")%>: <input name="password" type="password" size="15" maxlength="90" <%=disabled %>></input></td>
                        <td><%=MessageFormat.format(props.getProperty("createEdit.confirm"), props.getProperty("createEdit.password"))%>: <input name="password2" type="password" size="15" maxlength="90" <%=disabled %>></input></td>
                        
                        

            		</tr>
                    <tr><td colspan="3"><%=props.getProperty("createEdit.fullName")%>: <input name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>
                    <tr><td colspan="2"><%=props.getProperty("createEdit.email")%>: <input name="emailAddress" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td><td colspan="1">Receive automated emails? <input type="checkbox" name="receiveEmails" value="receiveEmails" <%=receiveEmails %>/></td></tr>
                    <tr><td colspan="3"><%=props.getProperty("createEdit.affiliation")%>: <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>
                     <tr><td colspan="3"><%=props.getProperty("createEdit.researchProject")%>: <input name="userProject" type="text" size="15" maxlength="90" value="<%=userProject %>"></input></td></tr>
                          
                    <tr><td colspan="3"><%=props.getProperty("createEdit.projectURL")%>: <input name="userURL" type="text" size="15" maxlength="90" value="<%=userURL %>"></input></td></tr>
		     <tr><td colspan="3" valign="top"><%=props.getProperty("createEdit.researchStatement")%>: <textarea name="userStatement" size="100" maxlength="255"><%=userStatement%></textarea></td></tr>
                    
                    <tr><td colspan="3"><input name="Create" type="submit" id="Create" value="<%=request.getParameter("isEdit") != null ? props.getProperty("edit") : props.getProperty("create")%>" /></td></tr>
            </table>
            </td>
            <td>
            <table>
           
            <%
            ArrayList<String> contexts=ContextConfiguration.getContextNames();
            int numContexts=contexts.size();
            for(int d=0;d<numContexts;d++){
            	%>
            	 <tr>
            <td>
            
            
            <%=MessageFormat.format(props.getProperty("createEdit.roles4"), ContextConfiguration.getNameForContext(("context"+d)))%>:
                        	<select multiple="multiple" name="context<%=d %>rolename" id="rolename" size="5">
                        		<option value=""></option>
								<%
								boolean isEdit = request.getParameter("isEdit") != null;
								User user = request.getParameter("username") != null ? myShepherd.getUser(request.getParameter("username").trim()) : null;
								for (String role : rolesMap.values()) {
									String selected = "";
									if (isEdit && user != null && myShepherd.doesUserHaveRole(request.getParameter("username"), role, "context" + d)) {
										selected = "selected=\"true\"";
									}
									%>
														<option value="<%=role%>" <%=selected%>><%=role%></option>
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
            <p><font size="+1"><%=props.getProperty("resetUserAgreement")%></font></p>
            <p><%=props.getProperty("resetUserAgreement.describe")%></p>

            <form name="UserResetAcceptedUserAgreement" method="post" action="../UserResetAcceptedUserAgreement?context=context0">
              <input name="UserResetAcceptedUserAgreementButton" type="submit" id="UserResetAcceptedUserAgreementButton" value="<%=props.get("reset")%>">
						</form>
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



