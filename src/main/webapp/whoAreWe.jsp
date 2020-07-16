<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.*" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode=ServletUtilities.getLanguageCode(request);
	Properties props = ShepherdProperties.getProperties("whoweare.properties", langCode, context);
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

   <div class="col-xs-12 col-sm-4 col-md-4 col-lg-4">
        <h1><%=props.getProperty("title")%></h1>
        <p class="lead">
          <%=props.getProperty("subtitle")%>
        </p>
   </div>
   
    <div class="col-xs-12 col-sm-7 col-md-7 col-lg-7">
    	<h3><%=props.getProperty("section1.title")%></h3>
        <p><%=props.getProperty("section1.text1")%> <a href="volunteer.jsp"><%=props.getProperty("section1.text1.linkText")%></a></p>
          
        <ul class="list-unstyled list-inline block-list volunteer-list">

			
	<%
	if(CommonConfiguration.showUsersToPublic(context)){

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    List<User> allUsers=myShepherd.getAllUsers();
    for (ListIterator<User> it = allUsers.listIterator(); it.hasNext();) {
      User u = it.next();
      if (u.getFullName() != null && u.getFullName().matches("(?i).*\\b(test|demo|rest)\\b.*")
              || u.getUsername() != null && u.getUsername().matches("(?i).*\\b(test|demo|rest)\\b.*"))
        it.remove();
    }

    for (User thisUser : allUsers) {

      String profilePhotoURL="images/user-profile-grey-grey.png";
		    
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
                    <div class="name"><%=displayName%></div>
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

