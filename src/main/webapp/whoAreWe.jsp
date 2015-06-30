<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	
	
%>

<jsp:include page="header2.jsp" flush="true" />

<div class="container maincontent">

		  <h1 class="intro"><%=props.getProperty("title") %></h1>
	
		

			
	<%
	if(CommonConfiguration.showUsersToPublic(context)){

	Shepherd myShepherd = new Shepherd(context);
	 myShepherd.beginDBTransaction();
     ArrayList<User> allUsers=myShepherd.getAllUsers();
     int numUsers=allUsers.size();
     
 	%>		
	<a name="collaborators"></a>
	<h3><%=props.getProperty("collaborators") %> (<%=numUsers %>)</h3>
<table>
<%
     
     
     int userNum=-1;
     for(int i=0;i<numUsers;i++){
    	 userNum++;
       	User thisUser=allUsers.get(i);
       	String username=thisUser.getUsername();
    	 %>
          <tr class="who"><td> 
           <table align="left">
           	<%
    	
    		
           	String profilePhotoURL="images/empty_profile.jpg";
		    
    		if(thisUser.getUserImage()!=null){
    			profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();

    		}
    		%>
			<tr>
				<td>
					<div style="height: 50px">
						<a><img class="pull-left" height="80px" width="80px" border="1" align="top" src="<%=profilePhotoURL%>"  /></a>
					</div>
				</td>
				<td style="border:none">
					<table>
					<%
					if(thisUser.getFullName()!=null){
		    			String displayName=thisUser.getFullName();
					%>
						<tr>
							<td>
    				
    							<a style="font-weight:normal;border:none"><%=displayName %></a>
    				
    						</td>
    					</tr>
    					<%
     					}
    					if(thisUser.getAffiliation()!=null){
    					%>
    					<tr>
							<td>
	    						<%=thisUser.getAffiliation() %>
    						</td>
    					</tr>
    					<%
    					}
    					if(thisUser.getUserStatement()!=null){
    					%>
    					    					<tr>
							<td>
	    						<p><%=thisUser.getUserStatement() %></p>
    						</td>
    					</tr>
    					<%
    					}
    					%>
    				</table>
    			</td>
			</tr>
			<%
    		String displayName="";
    		if(thisUser.getFullName()!=null){
    			displayName=thisUser.getFullName();
    		
    		}
    		
    		%>
    	</table>
    

	</td></tr>
    		
    		<% 
       	
       	
     } //end looping through users
     %>
     </table>
     <%
     myShepherd.rollbackDBTransaction();
     myShepherd.closeDBTransaction();
     myShepherd=null;
     
	} //end if(CommonConfiguration.showUsersToPublic()){
	%>


<p>&nbsp;</p>
  </div>

<jsp:include page="footer2.jsp" flush="true" />

