<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.io.*" %>
<%@ page import="java.text.MessageFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.security.Collaboration" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
	String context = ServletUtilities.getContext(request);
	String langCode = ServletUtilities.getLanguageCode(request);
	Properties props = ShepherdProperties.getProperties("users.properties", langCode, context);

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
        												<img src="images/upload_small.gif" align="absmiddle" />&nbsp;<%=props.getProperty("uploadPhoto")%><br />
		        		    						 <input name="username" type="hidden" value="<%=localUsername%>" id="profileUploadUsernameField" />
        												<input name="file2add" type="file" size="20" />
        												<input name="addtlFile" type="submit" id="addtlFile" value="<%=props.getProperty("upload")%>" />
        											</form>
		        		    					</td>
		        		    				</tr>
		        		    				<%
		        		    				if(hasProfilePhoto){
		        		    				%>
		        		    					<tr><td style="border: solid 0"><%=props.getProperty("deleteProfilePhoto")%>&nbsp;<a href="MyAccountRemoveProfileImage"><img src="images/cancel.gif" width="16px" height="16px" align="absmiddle" /></a></td></tr>
		        		    			
		        		    				<%
		        		    				}
		        		    			
		        		    			%>
		        		    			</table>
		        		    		
		        		    	</td>
		        	<form action="UserSelfUpdate?context=context0" method="post" id="editUser">	    
    		    	<td><table width="100%" class="tissueSample">
      				<tr>
            	
                        
                        <td style="border-bottom: 0px white;"><%=props.getProperty("createEdit.newPassword")%>: <input name="password" type="password" size="15" maxlength="90" ></input></td>
                        <td style="border-bottom: 0px white;" colspan="2"><%=MessageFormat.format(props.getProperty("createEdit.confirm"), props.getProperty("createEdit.newPassword"))%>: <input name="password2" type="password" size="15" maxlength="90" ></input></td>
                        
                        

            		</tr>
            		<tr><td colspan="3" style="border-top: 0px white;font-style:italic;"><%=props.getProperty("createEdit.leaveBlankNoChangePassword")%></td></tr>
                    <tr><td colspan="3"><%=props.getProperty("createEdit.fullName")%>: <input name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>
                    <tr><td colspan="2"><%=props.getProperty("createEdit.email")%>: <input name="emailAddress" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td><td colspan="1"><%=props.getProperty("createEdit.receiveAutoEmails")%> <input type="checkbox" name="receiveEmails" value="receiveEmails" <%=receiveEmails%>/></td></tr>
                    <tr><td colspan="3"><%=props.getProperty("createEdit.affiliation")%>: <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>
                     <tr><td colspan="3"><%=props.getProperty("createEdit.researchProject")%>: <input name="userProject" type="text" size="15" maxlength="90" value="<%=userProject %>"></input></td></tr>
                          
                    <tr><td colspan="3"><%=props.getProperty("createEdit.projectURL")%>: <input name="userURL" type="text" size="15" maxlength="90" value="<%=userURL %>"></input></td></tr>
		     <tr><td colspan="3" valign="top"><%=props.getProperty("createEdit.researchStatement")%>: <textarea name="userStatement" size="100" maxlength="255"><%=userStatement%></textarea></td></tr>
                    
                    <tr><td colspan="3"><input name="Create" type="submit" id="Create" value="<%=props.getProperty("update")%>" /></td></tr>
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
            
            
            <%=MessageFormat.format(props.getProperty("createEdit.roles4"), ContextConfiguration.getNameForContext(("context"+d)))%>:<br />
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
<br ></br>
<h2><%=props.getProperty("socialMediaConnections")%></h2>
<div style="padding-bottom: 10px;">
<%
	String types[] = new String[] {"facebook", "flickr"};

if((CommonConfiguration.getProperty("allowFacebookLogin", "context0")!=null)&&(CommonConfiguration.getProperty("allowFacebookLogin", "context0").equals("true"))){

		String socialType="facebook";
		if (thisUser.getSocial(socialType) == null) {
			out.println(String.format("<div class=\"social-disconnected\"><input type=\"button\" onClick=\"return socialConnect('%s');\" value=\"%s\" /></div>", socialType, MessageFormat.format(props.getProperty("connectTo"), socialType)));
		} else {
			out.println(String.format("<div class=\"social-connected\">%s<input type=\"button\" class=\"social-connect\" onClick=\"return socialDisconnect('%s');\" value=\"disconnect\" /></div>", MessageFormat.format(props.getProperty("connectedTo"), socialType), socialType));
		}
}
if((CommonConfiguration.getProperty("allowFlickrLogin", "context0")!=null)&&(CommonConfiguration.getProperty("allowFlickrLogin", "context0").equals("true"))){

	String socialType="flickr";
	if (thisUser.getSocial(socialType) == null) {
		out.println(String.format("<div class=\"social-disconnected\"><input type=\"button\" onClick=\"return socialConnect('%s');\" value=\"%s\" /></div>", socialType, MessageFormat.format(props.getProperty("connectTo"), socialType)));
	} else {
		out.println(String.format("<div class=\"social-connected\">%s<input type=\"button\" class=\"social-connect\" onClick=\"return socialDisconnect('%s');\" value=\"disconnect\" /></div>", MessageFormat.format(props.getProperty("connectedTo"), socialType), socialType));
	}
}
%>
</div>

<%
	if((CommonConfiguration.getProperty("collaborationSecurityEnabled", context)!=null)&&(CommonConfiguration.getProperty("collaborationSecurityEnabled", context).equals("true"))){

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
	}

%>
    	
    </p>
    
    <h2><%=props.getProperty("myData")%></h2>
    

<%
String jdoqlString="SELECT FROM org.ecocean.Encounter where submitterID == '"+thisUser.getUsername()+"'";
%>
    <jsp:include page="encounters/encounterSearchResultsAnalysisEmbed.jsp" flush="true">
    	<jsp:param name="jdoqlString" value="<%=jdoqlString %>" />
    </jsp:include>
    
    <p><strong><%=props.getProperty("myData.links")%></strong></p>
        <p class="caption"><a href="individualSearchResultsAnalysis.jsp?username=<%=localUsername%>"><%=props.getProperty("individualsAssociated")%></a></p>
    
    <p class="caption"><a href="encounters/searchResultsAnalysis.jsp?username=<%=localUsername%>"><%=props.getProperty("encountersAssociated")%></a></p>
    

<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
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

</script>
</div>

<jsp:include page="footer.jsp" flush="true"/>


