
<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="java.util.ArrayList, java.util.List" %>
<%@ page import="org.ecocean.*,org.ecocean.servlet.ServletUtilities, org.ecocean.security.Collaboration, java.util.Properties, java.util.Date, java.text.SimpleDateFormat,
javax.servlet.http.HttpSession,
java.io.*" %>


<%


String context="context0";

//get language
String langCode = ServletUtilities.getLanguageCode(request);

//load user props
Properties props=ShepherdProperties.getProperties("users.properties", langCode,context);

if (session.getAttribute("error") != null) {
	%><script>var errorMessage = '<%=session.getAttribute("error").toString().replaceAll("'", "\\'")%>';</script><%
	session.removeAttribute("error");
} else {
	%><script>var errorMessage = false;</script><%
}

if (session.getAttribute("message") != null) {
	%><script>var message = '<%=session.getAttribute("message").toString().replaceAll("'", "\\'")%>';</script><%
	session.removeAttribute("message");
} else {
	%><script>var message = false;</script><%
}


Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("myAccount.jsp");
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

<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

	<h1 class="intro"><%=(props.getProperty("userAccount")+" "+request.getUserPrincipal()) %></h1>

	<p>

    	
   		<table width="100%" class="tissueSample">
    		    

    		    
	    <%
	    //let's set up any pre-defined values if appropriate
	    String localUsername="";
	    String localAffiliation="";
	    String localEmail="";
	    String localFullName="";
	    String profilePhotoURL="images/user-profile-grey-grey.png";
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
    	<script>
    		function clickEditPermissions(ev) {
					var which = ev.target.getAttribute('class'); //
					var jel = $(ev.target);
					var p = jel.parent();
					var uname = p.data('username');
					p.html('&nbsp;').addClass('throbbing');
					$.ajax({
						url: wildbookGlobals.baseUrl + '/Collaborate?json=1&username=' + uname + '&approve=' + which,
						dataType: 'json',
						success: function(d) {
							if (d.success) {
								p.remove();
								updateNotificationsWidget();
							} else {
								p.removeClass('throbbing').html(d.message);
							}
						},
						error: function(a,x,b) {
							p.removeClass('throbbing').html('error');
						},
						type: 'GET'
					});
				}

    		    </script>
		    <tr>
		    	<td>
		    		<table border="0">
		    			<tr>
		    				<td style="border: solid 0">
		    					<img src="<%=profilePhotoURL%>" width="200px" height="*" />
		    				</td>
		    			</tr>
		    		
		    			<tr>
	    					<td style="border: solid 0">
	    						<form action="MyAccountAddProfileImage?context=context0" method="post" enctype="multipart/form-data" name="UserAddProfileImage">
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
	            	</table></td>
	            
	            	<td><table>
	            		<%
			            List<String> contexts=ContextConfiguration.getContextNames();
			            int numContexts=contexts.size();
			            for(int d=0;d<numContexts;d++){
			            	if(myShepherd.doesUserHaveAnyRoleInContext(localUsername, context)){
			            	%>
			            	   <tr>
    								<td style="border-style: none;">
    									Roles for <%=ContextConfiguration.getNameForContext(("context"+d)) %> (multi-select) 
    								</td>
    							</tr>
			            	<tr><td>            
					            
					        	<select multiple="multiple" name="context<%=d %>rolename" id="rolename" size="5" disabled="disabled">
					        		<%
									for(int q=0;q<numRoles;q++){
										//String selected="";
										if(myShepherd.getUser(request.getUserPrincipal().getName())!=null &&
										   myShepherd.doesUserHaveRole(request.getUserPrincipal().getName(),roles.get(q),("context"+d))
										){
											%><option value="<%=roles.get(q)%>"><%=roles.get(q)%></option><% 
										}
									}
									%>
								</select>
				            
			            	</td></tr>
				            <%	
				            }
			            } // end for loop over contexts
			            %>
			            <tr><td style="border-style: none;">Organization Membership (multi-select) </td></tr>
    				
			            <tr>
						  <td style="border-style: none;">
			    	
			    			<select multiple="multiple" name="organization" id="organization" size="5" disabled="disabled">
					            <option value=""></option>
				    	    	<%
				    	    	
					    		List<Organization> orgs=myShepherd.getAllOrganizationsForUser(thisUser);

					    		int numOrgs=orgs.size();
								for(Organization org:orgs){
									String selected="";
									
									%>
									<option value="<%=org.getId() %>" <%=selected%>><%=org.getName()%></option>
									<%
								}
								%>          
				    		</select>
			
			    		</td>
						            </tr>
			            
		            </table></td>	
	            </form>
            </tr>
        </table>
	<br ></br>

	<h2><%=props.getProperty("socialMediaConnections") %></h2>

	<div style="padding-bottom: 10px;">
	<%
		String types[] = new String[] {"facebook", "flickr"};

	if((CommonConfiguration.getProperty("allowFacebookLogin", "context0")!=null)&&(CommonConfiguration.getProperty("allowFacebookLogin", "context0").equals("true"))){

		String socialType="facebook";
		if (thisUser.getSocial(socialType) == null) {
			out.println("<div class=\"social-disconnected\"><input type=\"button\" onClick=\"return socialConnect('" + socialType + "');\" value=\""+props.getProperty("connect2")+ socialType + "\" /></div>");
		} else {
			out.println("<div class=\"social-connected\">" +props.getProperty("connectedTo") +" "+ socialType + " <input type=\"button\" class=\"social-connect\" onClick=\"return socialDisconnect('" + socialType + "');\" value=\""+props.getProperty("disconnect")+"\" /></div>");
		}
	}
	if((CommonConfiguration.getProperty("allowFlickrLogin", "context0")!=null)&&(CommonConfiguration.getProperty("allowFlickrLogin", "context0").equals("true"))){

		String socialType="flickr";
		if (thisUser.getSocial(socialType) == null) {
			out.println("<div class=\"social-disconnected\"><input type=\"button\" onClick=\"return socialConnect('" + socialType + "');\" value=\""+props.getProperty("connect2")+ socialType + "\" /></div>");
		} else {
			out.println("<div class=\"social-connected\">" +props.getProperty("connectedTo") +" "+ socialType + " <input type=\"button\" class=\"social-connect\" onClick=\"return socialDisconnect('" + socialType + "');\" value=\""+props.getProperty("disconnect")+"\" /></div>");
		}
	}
	%>
	</div>

<%
	if((CommonConfiguration.getProperty("collaborationSecurityEnabled", context)!=null)&&(CommonConfiguration.getProperty("collaborationSecurityEnabled", context).equals("true"))){

		Properties collabProps = new Properties();
 		collabProps = ShepherdProperties.getProperties("collaboration.properties", langCode, context);
		List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
		String me = request.getUserPrincipal().getName();
		String h = "";
		// for developing the edit button without having to update a properties file
		for (Collaboration c : collabs) {
			String state = c.getState();
			String cls = "state-" + c.getState();
			String msg = "state_" + c.getState();
			String click = "";

			if (c.getUsername1().equals(me)) {
				h += "<div class=\"collabRow mine " + cls + "\"><span class=\"who\">to <b><span class=\"collab-name\">" + c.getUsername2() + "<span></b></span><span class=\"state\">" + collabProps.getProperty(msg) + "</span></div>";
			} else if (state!=null) {
				if (msg.equals("state_initialized")) {
					msg = "state_initialized_me";
					click = " <span class=\"invite-response-buttons collab-button\" data-username=\"" + c.getUsername1() + "\"><input type=\"button\" class=\"yes\" value=\"" + collabProps.getProperty("buttonApprove") + "\">";
					click += "<input type=\"button\" class=\"no\" value=\"" + collabProps.getProperty("buttonDeny") + "\"></span>";
					click += "<script>$('.invite-response-buttons input').click(function(ev) { clickApproveDeny(ev); });</script>";
				} else if (state.equals(Collaboration.STATE_APPROVED)) {
					// add button to grant edit access, attached to servlet
					click = " <span class=\"add-edit-perm-button collab-button\" data-username=\""+c.getUsername1()+"\"><input type=\"button\" class=\"edit\" value=\"" + collabProps.getProperty("buttonAddEditPerm") + "\">";
					click += "<script>$('.add-edit-perm-button input').click(function(ev) { clickEditPermissions(ev); });</script>";
				} else if (state.equals(Collaboration.STATE_EDIT_PRIV)) {
					// add button to revoke edit access, attached to servlet
					// this button should behave just like the approve button (downgrading user from edit to view permission)
					click = " <span class=\"revoke-edit-perm-button collab-button\" data-username=\""+c.getUsername1()+"\"><input type=\"button\" class=\"yes\" value=\"" + collabProps.getProperty("buttonRevokeEditPerm") + "\">";
					click += "<script>$('.revoke-edit-perm-button input').click(function(ev) { clickApproveDeny(ev); });</script>";
					System.out.println("EDITABLE State msg = "+msg);
				}
				h += "<div class=\"collabRow notmine " + cls + "\"><span class=\"who\">from <b>" + c.getUsername1() + "</b></span><span class=\"state\">" + collabProps.getProperty(msg) + "</span>" + click + "<div class=\"clear\"></div></div>";
			}
		}
		if (h.equals("")) h = "<p id=\"none-line\">none</p>";
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


		
	} // end if collaborationSecurityEnabled
	
	%>
	<br>
	<div id="init-collab-ui">

		<h4><%=props.getProperty("initiateCollab") %></h4>

		<div class="row">
			<div class="col-xs-4 col-sm-4 col-md-4 col-lg-4 col-xl-4">
				<label><%=props.getProperty("userLookup") %></label>
				<input class="form-control" name="collabTarget" type="text" id="collabTarget" placeholder="<%=props.getProperty("typeToSearch") %>">
			</div>

			<div class="col-xs-4 col-sm-4 col-md-4 col-lg-4 col-xl-4">
				<label><%=props.getProperty("addNote") %></label>
				<input class="form-control" name="collabNote" type="text" id="collabNote" placeholder="<%=props.getProperty("collabNote") %>">
			</div>

			<div class="col-xs-4 col-sm-4 col-md-4 col-lg-4 col-xl-4">
				<label><%=props.getProperty("requestCollaboration") %></label>
				<div class="form-group">
					<input class="btn collab-init-btn" type="button" value="Initiate" onclick="initiateCollab()" />
				</div>
			</div>

			<div class="col-xs-12 col-sm-12 col-md-12 col-lg-12 col-xl-12">
				<p id="collabResp"></p>
			</div>

		</div>
		

	</div>
	
    </p> <!-- end content p -->
    
    <h2><%=props.getProperty("myData") %></h2>
    

<%

String jdoqlString="SELECT FROM org.ecocean.Encounter where submitterID == '"+thisUser.getUsername()+"'";
%>
    <jsp:include page="encounters/encounterSearchResultsAnalysisEmbed.jsp" flush="true">
    	<jsp:param name="jdoqlString" value="<%=jdoqlString %>" />
    </jsp:include>
    
    <p><strong><%=props.getProperty("links2mydata") %></strong></p>
        <p class="caption"><a href="individualSearchResultsAnalysis.jsp?username=<%=localUsername%>"><%=props.getProperty("individualsAssociated") %></a></p>
    
    <p class="caption"><a href="encounters/searchResultsAnalysis.jsp?username=<%=localUsername%>"><%=props.getProperty("encountersAssociated") %></a></p>
    

<%

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
String alreadySent = props.getProperty("alreadySent");
%>
<script>
if (errorMessage) wildbook.showAlert(errorMessage, '', 'Error');
if (message) wildbook.showAlert(message);

function socialDisconnect(svc) {
//console.info('disconnect %s', svc);
	window.location.href = 'SocialConnect?disconnect=1&type=' + svc;
}

function socialConnect(svc) {
//console.info('connect %s', svc);
	window.location.href = 'SocialConnect?type=' + svc;
}

$('#collabTarget').autocomplete({
	source: function( request, response ) {
		$.ajax({
			url: wildbookGlobals.baseUrl + '/UserGetSimpleJSON?searchUser=' + request.term,
			type: 'GET',
			dataType: "json",
			success: function( data ) {
				console.log("trying autocomplete...");
				
				let alreadyCollab = [];
				$(".collab-name").each(function() {
					alreadyCollab.push($(this).text());
				});

				var res = $.map(data, function(item) {
					let fullName = "";
					if (item.fullName!=null&&item.fullName!="undefined") fullName = item.fullName;
					let label = ("name: "+fullName+" user: "+item.username);
					if (alreadyCollab.indexOf(fullName) > -1) {
						label += ' (<%=alreadySent%>)';
					}  
					return { label: label, value: item.username };
				});
				response(res);
			}

		});
	}
});

function initiateCollab() {
	let collabTarget = $("#collabTarget").val();
	let paramStr = "";
	if (collabTarget!=null&&""!=collabTarget) {

		let alreadyCollab = [];
		$(".collab-name").each(function() {
			alreadyCollab.push($(this).text());
		});
		if (alreadyCollab.indexOf(collabTarget) > -1) {
			$("#collabResp").text("You already have a collaboration or request with user "+collabTarget+".");
		} else {
			paramStr = "?username="+collabTarget;
			if ($("#collabNote").val()!=null&&""!=$("#collabNote").val()) {
				paramStr += "&message="+$("#collabNote").val();
			}
			$.ajax({
				url: wildbookGlobals.baseUrl + '/Collaborate'+paramStr,
				type: 'POST',
				dataType: "text",
				contentType: 'application/javascript',
				success: function(d) {
					console.info('Success! Got back '+JSON.stringify(d));
					$("#collabResp").text("The collaboration request has been sent.");
					appendCollabRequest(collabTarget);
					clearCollabInitFields()
				},
				error: function(x,y,z) {
					$("#collabResp").text("There was an error sending this collaboration request.");
					console.log(" got an error....?? ");
					console.warn('%o %o %o', x, y, z);
				}
			});
		}
	} else {
		$("#collabResp").text("You must specify a user to initiate collaboration with.");
	}
}

function clearCollabInitFields() {
	$("#collabTarget").val('');
	$("#collabNote").val('');
}

function appendCollabRequest(name) {
	if ($("#none-line").length > 0) $("#none-line").val('');  
	let newCollab = "<div class=\"collabRow mine state-initialized\"><span class=\"who\">to <b><span class=\"collab-name\">" +name+ "<span></b></span><span class=\"state\">invitation sent</span></div>";
	$(".collab-list").append(newCollab);
}

// end collab type ahead




</script>
</div>

<jsp:include page="footer.jsp" flush="true"/>


