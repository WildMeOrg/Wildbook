<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="java.util.ArrayList" %>
<%@ page import="org.ecocean.*,org.ecocean.servlet.ServletUtilities, org.ecocean.security.Collaboration, java.util.Properties, java.util.Date, java.text.SimpleDateFormat, java.io.*" %>


<%


String context="context0";

//get language
String langCode = ServletUtilities.getLanguageCode(request);

//load user props
Properties props=ShepherdProperties.getProperties("users.properties", langCode,context);



  	
  	
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("setNewPassword.jsp");
  	

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>


    <jsp:include page="header.jsp" flush="true" />

   
   <div class="container maincontent">

	<h1><%=props.getProperty("resetPassword") %></h1>

	<p>

    	<%
    	if((request.getParameter("username")!=null)&&(request.getParameter("OTP")!=null)&&(request.getParameter("time")!=null)){
    	%>
    		    <table width="100%" class="tissueSample">
    		    

		    		    
		    		    <%
		    		    //let's set up any pre-defined values if appropriate
		    		   
		    		    myShepherd.beginDBTransaction();
		    		    
		    		    
		    		    User thisUser=myShepherd.getUser(request.getParameter("username"));
		    		    
		    		    
    		    
    		    		%>
    		    
    		        	<tr>
		        		   
		        			<form action="UserResetPassword" method="post" id="editUser">	    
    		    	
    		    				<input type="hidden" name="username" value="<%=request.getParameter("username") %>" />
    		    				<input type="hidden" name="OTP" value="<%=request.getParameter("OTP") %>" />
    		    				<input type="hidden" name="time" value="<%=request.getParameter("time") %>" />
    		    	
    		    			
    		    				<table width="100%">
      								<tr>
            							<td style="border-bottom: 0px white;"><%=props.getProperty("newPassword") %> <input name="password" type="password" size="15" maxlength="90" ></input></td>
                        				<td style="border-bottom: 0px white;" colspan="2"><%=props.getProperty("confirm") %> <%=props.getProperty("newPassword") %> <input name="password2" type="password" size="15" maxlength="90" ></input></td>
            						</tr>
                    				<tr>
                    					<td colspan="3"><input name="Create" type="submit" id="Create" value="<%=props.getProperty("update") %>" /></td></tr>
            					</table>
            				

            				</form>
            			</tr>
            	</table>
<%

	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}
else{
%>
<%=props.getProperty("notEnough") %>
<%
}
%>

    	
    </div>
   
 <%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>   
 
      <jsp:include page="footer.jsp" flush="true"/>
 




