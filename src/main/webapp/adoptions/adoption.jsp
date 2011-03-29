<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.Adoption" %>
<%@ page import="org.ecocean.CommonConfiguration" %>
<%@ page import="org.ecocean.Shepherd" %>

<%
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  Shepherd myShepherd = new Shepherd();
  int count = myShepherd.getNumAdoptions();
  Adoption tempAD = null;

  boolean edit = false;

  String id = "";
  String adopterName = "";
  String adopterAddress = "";
  String adopterEmail = "";
  String adopterImage = "";
  String adoptionStartDate = "";
  String adoptionEndDate = "";
  String adopterQuote = "";
  String adoptionManager = "";
  String sharkForm = "";
  String encounterForm = "";
  String notes = "";
  String adoptionType = "";

  String servletURL = "../adoptionForm.jh";

  if (request.getParameter("individual") != null) {
    sharkForm = request.getParameter("individual");
  }

  boolean isOwner = false;
  if (request.isUserInRole("admin")) {
    isOwner = true;
  } else if (request.getParameter("number") != null) {

    if (tempAD.getAdoptionManager().trim().equals(request.getRemoteUser())) {
      isOwner = true;
    }
  }

  if (request.getParameter("number") != null) {
    tempAD = myShepherd.getAdoption(request.getParameter("number"));
    edit = true;
    //servletURL = "/editAdoption";
    id = tempAD.getID();
    adopterName = tempAD.getAdopterName();
    adopterAddress = tempAD.getAdopterAddress();
    adopterEmail = tempAD.getAdopterEmail();
    adopterImage = tempAD.getAdopterImage();
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
  }
%>

<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle() %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>


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
</head>

<body>

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


<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">


<p>

<h1 class="intro"> Adoption Administration</h1>
</p>
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
      target="_self" dir="ltr" lang="en">
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
      <td><input name="theFile1" type="file" size="30"
                 value="<%=adopterImage%>"></input>&nbsp;&nbsp; <%if ((adopterImage != null) && (!adopterImage.equals(""))) {%>
        <img
          src="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/<%=id%>/thumb.jpg"
          align="absmiddle"/>&nbsp; <%
          }
        %>
      </td>
    </tr>


    <tr>
      <td valign="top">Adopter quote:</td>
      <td>Why are shark research and conservation important?<br><textarea
        name="adopterQuote" cols="40" id="adopterQuote" rows="10"><%=adopterQuote%>
      </textarea>
      </td>
    </tr>


    <tr>
      <td>Shark:</td>
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
      <form action="rejectAdoption.jsp" method="get"><input
        type="hidden" name="number" value="<%=id%>"/> <input name="Delete"
                                                             type="submit" value="Delete"/></form>
      <br/>
    </td>
  </tr>
</table>
<br/>
<%
  }

  if (isOwner) {
%>
<table class="adoption" width="720px">
  <tr>
    <td>
      <h3><a name="restore" id="restore"></a>Restore a deleted adoption</h3>
    </td>
  </tr>
  <tr>
    <td>
      <form action="../ResurrectDeletedAdoption" method="get"
            name="restoreDeletedAdoption">Adoption #: <input name="number"
                                                             type="text" size="25"/> <input
        type="submit" name="Submit"
        value="Submit"/></form>
      <br/>
  </tr>
  </td>
</table>
<%
  }
%>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


