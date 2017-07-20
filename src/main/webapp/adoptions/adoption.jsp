<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.*,org.ecocean.servlet.ServletUtilities" %>

<%
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache");
//Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store");
//Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0);
//Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache");
//HTTP 1.0 backward compatibility
String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("adoption.jsp");
  myShepherd.beginDBTransaction();
  
  try{
	  int count = myShepherd.getNumAdoptions();
	  Adoption tempAD = null;
	
	  boolean edit = false;
	
	  session.setAttribute( "emailEdit", false );
	  session.setMaxInactiveInterval(6000);
	
	  String id = "";
	  String adopterName = "";
	  String adopterAddress = "";
	  String adopterEmail = "";
	  String adopterImage="";
	  String adoptionStartDate = "";
	  String adoptionEndDate = "";
	  String adopterQuote = "";
	  String adoptionManager = "";
	  String sharkForm = "";
	  String encounterForm = "";
	  String notes = "";
	  String adoptionType = "";
	  String stripeID="";
	
	  String servletURL = "../AdoptionAction";
	
	  if (request.getParameter("individual") != null) {
	    sharkForm = request.getParameter("individual");
	  }
	
	  boolean isOwner = true;
	
	  /**
	  if (request.isUserInRole("admin")) {
	    isOwner = true;
	  } else if (request.getParameter("number") != null) {
	
	    if (tempAD.getAdoptionManager().trim().equals(request.getRemoteUser())) {
	      isOwner = true;
	    }
	  }
	  */
	
	  if (request.getParameter("number") != null) {
	    tempAD = myShepherd.getAdoption(request.getParameter("number"));
	    edit = true;
	    //servletURL = "/editAdoption";
	    id = tempAD.getID();
	    adopterName = tempAD.getAdopterName();
	    adopterAddress = tempAD.getAdopterAddress();
	    adopterEmail = tempAD.getAdopterEmail();
	    if((tempAD.getAdopterImage()!=null)&&(!tempAD.getAdopterImage().trim().equals(""))){
	    	adopterImage = tempAD.getAdopterImage();
	  	}
	    adoptionStartDate = tempAD.getAdoptionStartDate();
	    adoptionEndDate = tempAD.getAdoptionEndDate();
	    adopterQuote = tempAD.getAdopterQuote();
	    adoptionManager = tempAD.getAdoptionManager();
	    sharkForm = tempAD.getMarkedIndividual();
	    if (tempAD.getEncounter() != null) {
	      encounterForm = tempAD.getEncounter();
	    }
	    notes = tempAD.getNotes();
	    adoptionType = tempAD.getAdoptionType();
	    if(tempAD.getStripeCustomerId()!=null){
	    	stripeID=tempAD.getStripeCustomerId();
	    }
	  }
	%>
	
	
	  <style type="text/css">
	    <!--
	    table.adoption {
	      border-width: 1px 1px 1px 1px;
	      border-spacing: 0px;
	      border-style: solid solid solid solid;
	      border-color: black black black black;
	      border-collapse: separate;
	      background-color: white;
	    }
	
	    -->
	  </style>
	
	 <jsp:include page="../header.jsp" flush="true" />
	
	<script language="javascript" src="../prototype.js"></script>
	<script type="text/javascript" src="../calendarview.js"></script>
	<script type="text/javascript" src="../calendarview2.js"></script>
	
	<!--
	<script type="text/javascript">
	      window.onload = function() {
	
	        Calendar.setup({
	          dateField     : 'adoptionStartDate',
	          parentElement : 'calendar'
	
	        })
	        Calendar2.setup({
	          dateField     : 'adoptionEndDate',
	          parentElement : 'calendar2'
	
	        })
	
		  }
	</script>
	-->
	
	
	
	
	<div class="container maincontent">
	
	
	
	<h1 class="intro"> Adoption Administration</h1>
	
	<p>There are currently <%=count%> adoptions stored in the database.</p>
	
	<p>&nbsp;</p>
	<table class="adoption" width="720px">
	  <tr>
	    <td>
	      <h3><a name="goto" id="goto"></a>View/edit adoption</h3>
	    </td>
	  </tr>
	  <tr>
	    <td>
	      <form action="adoption.jsp#create" method="get">&nbsp;Adoption
	        number: <input name="number" type="text"/><br/>
	        <input name="View/edit adoption" type="submit"
	               value="View/edit adoption"/></form>
	      <br/>
	    </td>
	  </tr>
	</table>
	<br/>
	
	<%
	  String shark = "";
	  if (request.getParameter("individual") != null) {
	    shark = request.getParameter("individual");
	  }
	%>
	
	
	<table class="adoption">
	<tr>
	<td>
	<%
	  if (request.getParameter("number") != null) {
	%>
	<h3><a name="create" id="create"></a>Edit adoption <em><%=request.getParameter("number")%>
	</em></h3>
	<%
	} else {
	%>
	
	<h3><a name="create" id="create"></a>Create adoption</h3>
	<%
	  }
	
	  if (isOwner) {
	%>
	<form action="<%=servletURL%>" method="post"
	      enctype="multipart/form-data" name="adoption_submission"
	      target="_self" dir="ltr" accept-charset="UTF-8">
	  <%
	    }
	  %>
	
	  <table>
	    <tr>
	      <td>Name:</td>
	      <td><input name="adopterName" type="text" size="30"
	                 value="<%=adopterName%>"></input></td>
	    </tr>
	    <tr valign="top">
	      <td>Email:</td>
	      <td><input name="adopterEmail" type="text" size="30"
	                 value="<%=adopterEmail%>"></input><br/>
	
	        <p><em>Note: Multiple email addresses can be entered for
	          adopters, using commas as separators</em>.</p>
	      </td>
	    </tr>
	    <tr>
	      <td>Address:</td>
	      <td><input name="adopterAddress" type="text" size="30"
	                 value="<%=adopterAddress%>"></input></td>
	    </tr>
	    <tr>
	      <td>Image:</td>
	      <%
	      String adopterImageString="";
	      if(adopterImage!=null){
	    	  adopterImageString=adopterImage;
	    	}
	      %>
	      <td><input name="theFile1" type="file" size="30" value="<%=adopterImageString%>"></input>&nbsp;&nbsp;
	      <%
	      if ((adopterImage != null) && (!adopterImageString.equals(""))) {
	      %>
	        <img width="190px" heoght="*"  src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=id%>/adopter.jpg" align="absmiddle"/>&nbsp;
	        <%
	          }
	        %>
	      </td>
	    </tr>
	
	
	    <tr>
	      <td valign="top">Adopter quote:</td>
	      <td>Why are research and conservation for this species important?<br><textarea
	        name="adopterQuote" cols="40" id="adopterQuote" rows="10"><%=adopterQuote%>
	      </textarea>
	      </td>
	    </tr>
	
	
	    <tr>
	      <td>Marked Individual:</td>
	      <td><input name="shark" type="text" size="30"
	                 value="<%=sharkForm%>"> </input> <%if (!sharkForm.equals("")) { %>
	        <a href="../individuals.jsp?number=<%=sharkForm%>">Link</a> <%
	          }
	        %>
	      </td>
	    </tr>
	
	    <tr>
	      <td>Encounter:</td>
	      <td><input name="encounter" type="text" size="30"
	                 value="<%=encounterForm%>"> </input> <%if (!encounterForm.equals("")) { %>
	
	        <a href="../encounters/encounter.jsp?number=<%=encounterForm%>">Link</a>
	
	        <%
	          }
	        %>
	      </td>
	    </tr>
	
	
	    <tr>
	      <td>Adoption type:</td>
	      <td><select name="adoptionType">
	        <%
	          if (adoptionType.equals("Promotional")) {
	        %>
	        <option value="Promotional" selected="selected">Promotional</option>
	        <%
	        } else {
	        %>
	        <option value="Promotional" selected="selected">Promotional</option>
	        <%
	          }
	
	          if (adoptionType.equals("Individual adoption")) {
	        %>
	        <option value="Individual adoption" selected="selected">Individual
	          adoption
	        </option>
	        <%
	        } else {
	        %>
	        <option value="Individual adoption">Individual adoption</option>
	        <%
	          }
	
	
	          if (adoptionType.equals("Group adoption")) {
	        %>
	        <option value="Group adoption" selected="selected">Group
	          adoption
	        </option>
	        <%
	        } else {
	        %>
	        <option value="Group adoption">Group adoption</option>
	        <%
	          }
	
	
	          if (adoptionType.equals("Corporate adoption")) {
	        %>
	        <option value="Corporate adoption" selected="selected">Corporate
	          adoption
	        </option>
	        <%
	        } else {
	        %>
	        <option value="Corporate adoption">Corporate adoption</option>
	        <%
	          }
	        %>
	
	
	      </select></td>
	    </tr>
	
	
	    <tr>
	      <td>Adoption start date:</td>
	      <td><input id="adoptionStartDate" name="adoptionStartDate"
	                 type="text" size="30" value="<%=adoptionStartDate%>"> <em>(e.g.
	        2009-05-15) </input> </em></td>
	    </tr>
	
	    <tr>
	      <td>Adoption end date:</td>
	      <td><input name="adoptionEndDate" type="text" size="30"
	                 value="<%=adoptionEndDate%>"> </input> <em>(e.g. 2010-05-15) </em></td>
	    </tr>
	
	    <!--
				 			 <tr>
				 <td>Adoption end date:</td>
				 <td><div id="calendar2"></div>
	   				 <div id="date2">
					  <input  class="dateField" id="adoptionEndDate" name="adoptionEndDate" type="text" size="30" value="<%=adoptionEndDate%>"></input>
				</div>
					</td>
				</tr>
				 -->
	
	    <tr>
	      <td>Adoption manager (user):</td>
	      <td>
	        <%if (request.getRemoteUser() != null) {%> <input name="adoptionManager"
	                                                          type="text"
	                                                          value="<%=request.getRemoteUser()%>"
	                                                          value="<%=adoptionManager%>"></input> <%} else {%>
	        <input
	          name="adoptionManager" type="text" value="N/A"
	          value="<%=adoptionManager%>"></input> <%}%>
	      </td>
	    </tr>
	    <tr>
	      <td align="left" valign="top">Adoption notes:</td>
	      <td><textarea name="notes" cols="40" id="notes" rows="10"><%=notes%>
	      </textarea>
	
	        <%
	          if (request.getParameter("number") != null) {
	        %> <br/>
	        <input type="hidden" name="number" value="<%=id%>"/> <%
	          }
	
	        %>
	      </td>
	    </tr>
	
	    <%
	      if (isOwner) {
	    %>
	
	    <tr>
	      <td><input type="submit" name="Submit" value="Submit"/></td>
	    </tr>
	
	    <%
	      }
	    %>
	  </table>
	  <br/>
	
	  <%
	    if (isOwner) {
	  %>
	</form>
	<%
	  }
	%>
	</td>
	</tr>
	</table>
	<br/>
	<%
	  if ((request.getParameter("number") != null) && (isOwner)) {
	%>
	<p>&nbsp;</p>
	<table class="adoption" width="720px">
	  <tr>
	    <td>
	      <h3><a name="delete" id="delete"></a>Delete this adoption</h3>
	    </td>
	  </tr>
	  <tr>
	    <td>
	      <form action="emailCancelAdoption.jsp" method="get">
	      <input type="hidden" name="adoption" value="<%=id%>"/> 
	        <input type="hidden" name="number" value="<%=sharkForm%>"/> 
	         <input type="hidden" name="stripeID" value="<%=stripeID%>"/> 
	        
	        
	        <input name="Delete"
	                                                             type="submit" value="Delete"/></form>
	      <br/>
	    </td>
	  </tr>
	</table>
	<br/>
	<%
	  }
	
	  
  }
  catch(Exception e){e.printStackTrace();}
  finally{
  	myShepherd.rollbackDBTransaction();
  	myShepherd.closeDBTransaction();
  }
%>
</div>

<jsp:include page="../footer.jsp" flush="true"/>
