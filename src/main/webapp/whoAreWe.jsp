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

<div style="position: fixed; transform: rotate(23deg); color: rgba(255,100,100,0.2); font-size: 20em;">DRAFT</div>

<div>
<h2>About Kitizen Science</h2>

<p>
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam tincidunt sit amet est non lacinia. Nulla commodo lobortis elementum. Pellentesque congue fringilla est, consectetur lacinia ligula facilisis non. Donec in placerat magna, vel molestie nisl. Maecenas porta mattis finibus. Sed ut malesuada tortor. Vivamus convallis justo quis diam sodales, vitae ullamcorper ipsum vestibulum. Suspendisse eget maximus metus. Sed vel bibendum orci. Nam luctus ante vel urna pellentesque, sit amet vehicula elit pharetra. Aliquam pharetra, elit et faucibus consectetur, ligula sem congue odio, at bibendum tellus nisi eget nunc.
</p>

<h2>About Wild Me</h2>
<p>
<img style="height: 120px; float: left; margin-right: 15px;" src="images/wild-me-logo-only-100-100.png" />
Nunc aliquet venenatis rhoncus. Vestibulum cursus laoreet sapien id volutpat. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Nam ac mattis est, ac euismod dui. Duis consectetur laoreet arcu vitae auctor. Nunc eget cursus massa. Morbi sed porta sapien. Nulla facilisis imperdiet elementum. Vestibulum aliquam sagittis arcu. Etiam in elit a diam cursus egestas porttitor id orci. Morbi non metus et nunc fringilla posuere non nec risus. Integer volutpat vestibulum neque, at porttitor urna mollis eget.
</p>

<h2>Our Team</h2>
<p>
<img style="height: 120px; float: left; margin-right: 15px;" src="images/jon_profile.jpg" />
<b>Jon Van Oast</b> is senior engineer at <a href="https://www.wildme.org/">Wild Me</a>, where he helps maintain and
develop <a href="https://www.wildbook.org/">Wildbook</a>.
He is technical lead on <a href="https://giraffespotter.org/">GiraffeSpotter - Wildbook for Giraffe</a>
as well as provides support for many other species.
</p>

<p>
Jon has been developing online collaborative software for over twenty years, with a
strong interest in open source software/hardware, open data, citizen science, and conservation.
He likes dogs, but decidedly leans cat.
</p>
<div style="clear: both;"></div>

</div>

   <div>
    	<h2>Our Users</h2>

        <p class="lead">
            Here are our fantastic volunteers, subjects, and researchers.
            <br /><b>(This list is auto-generated, and can be removed.)</b>
        </p>

  
          
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

