<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);
    
	
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

   <div class="col-xs-12 col-sm-4 col-md-4 col-lg-4">
        <h1><%=props.getProperty("title") %></h1>
        <p class="lead">
            Here are our fantastic researchers and volunteers.
        </p>
   </div>
   
    <div class="col-xs-12 col-sm-7 col-md-7 col-lg-7">
    	<h3>We have many collaborating researchers and volunteers</h3>
  
          
        <ul class="list-unstyled list-inline block-list volunteer-list">

			
	<%
	if(CommonConfiguration.showUsersToPublic(context)){

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("whoAreWe.jsp");
    myShepherd.beginDBTransaction();
    List<User> allUsers=myShepherd.getAllUsers();
    for (ListIterator<User> it = allUsers.listIterator(); it.hasNext();) {
      User u = it.next();
      if (u.getFullName() != null && u.getFullName().matches("(?i).*\\b(test|demo|rest)\\b.*")
              || u.getUsername() != null && u.getUsername().matches("(?i).*\\b(test|demo|rest)\\b.*"))
        it.remove();
    }

    for (User thisUser : allUsers) {

      String profilePhotoURL="images/empty_profile.jpg";
		    
    		if(thisUser.getUserImage()!=null){
    			profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
    		}
    		%>
    		<li>
                <a>
                    <div class="img-container">
                        <img class="pull-left" src="<%=profilePhotoURL%>"  />
                    </div>
                    <%
					if(thisUser.getFullName()!=null){
		    			String displayName=thisUser.getFullName();
					%>
                    <div class="name"><%= StringEscapeUtils.escapeHtml4(displayName) %></div>
                    <%
					}
                    
                    if(thisUser.getAffiliation()!=null){
    				%>
                    <i><%=thisUser.getAffiliation() %></i>
                    <% 
                    }
                    %>
                </a>
            </li>

    	
    		
    		<% 
       	
       	
     } //end looping through users
     
     myShepherd.rollbackDBTransaction();
     myShepherd.closeDBTransaction();
     myShepherd=null;
     
     %>
     </ul>
     
    <% 
	} //end if(CommonConfiguration.showUsersToPublic()){
	%>


<p>&nbsp;</p>
	</div>
  </div>

<jsp:include page="footer.jsp" flush="true" />

