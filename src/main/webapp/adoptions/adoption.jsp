<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
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

  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Locale locale = new Locale(langCode);
  Properties props = ShepherdProperties.getProperties("adoption.properties", langCode, context);
  Shepherd myShepherd = new Shepherd(context);
  int count = myShepherd.getNumAdoptions();
  Adoption tempAD = null;

  boolean edit = false;

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



<h1 class="intro"><%=props.getProperty("title")%></h1>

<p><%=StringUtils.format(locale, props.getProperty("count"), count)%></p>

<p>&nbsp;</p>
<table class="adoption" width="720px">
  <tr>
    <td>
      <h3><a name="goto" id="goto"></a><%=props.getProperty("viewEdit")%></h3>
    </td>
  </tr>
  <tr>
    <td>
      <form action="adoption.jsp#create" method="get">
        <%=props.getProperty("number")%>:
        <input name="number" type="text"/><br/>
        <input name="<%=props.getProperty("viewEdit")%>" type="submit" value="<%=props.getProperty("viewEdit")%>"/></form>
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
<h3><a name="create" id="create"></a><%=props.getProperty("edit")%> <em><%=request.getParameter("number")%></em></h3>
<%
} else {
%>

<h3><a name="create" id="create"></a><%=props.getProperty("create")%></h3>
<%
}

  if (isOwner) {
%>
<form action="<%=servletURL%>" method="post" enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
<%
  }
%>

  <table>
    <tr>
      <td><%=props.getProperty("name")%>:</td>
      <td><input name="adopterName" type="text" size="30" value="<%=adopterName%>"></input></td>
    </tr>
    <tr valign="top">
      <td><%=props.getProperty("email")%>:</td>
      <td>
        <input name="adopterEmail" type="text" size="30" value="<%=adopterEmail%>"></input><br/>
        <p><em><%=props.getProperty("email.note")%></em></p>
      </td>
    </tr>
    <tr>
      <td><%=props.getProperty("address")%>:</td>
      <td><input name="adopterAddress" type="text" size="30" value="<%=adopterAddress%>"></input></td>
    </tr>
    <tr>
      <td><%=props.getProperty("image")%>:</td>
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
        <img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=id%>/thumb.jpg" align="absmiddle"/>&nbsp; 
        <%
          }
        %>
      </td>
    </tr>


    <tr>
      <td valign="top"><%=props.getProperty("quote")%>:</td>
      <td><%=props.getProperty("quote.note")%><br><textarea name="adopterQuote" cols="40" id="adopterQuote" rows="10"><%=adopterQuote%></textarea>
      </td>
    </tr>


    <tr>
      <td><%=props.getProperty("individual")%>:</td>
      <td><input name="shark" type="text" size="30" value="<%=sharkForm%>"> </input>
        <%if (!sharkForm.equals("")) { %>
        <a href="../individuals.jsp?number=<%=sharkForm%>"><%=props.getProperty("individual.link")%></a>
        <%
          }
        %>
      </td>
    </tr>

    <tr>
      <td><%=props.getProperty("encounter")%>:</td>
      <td><input name="encounter" type="text" size="30" value="<%=encounterForm%>"> </input>
        <%if (!encounterForm.equals("")) { %>
        <a href="../encounters/encounter.jsp?number=<%=encounterForm%>"><%=props.getProperty("encounter.link")%></a>
        <%
          }
        %>
      </td>
    </tr>


    <tr>
      <td><%=props.getProperty("type")%>:</td>
      <td><select name="adoptionType">
        <%
          if (adoptionType.equals("Promotional")) {
        %>
        <option value="Promotional" selected="selected"><%=props.getProperty("type.promotional")%></option>
        <%
        } else {
        %>
        <option value="Promotional" selected="selected"><%=props.getProperty("type.promotional")%></option>
        <%
          }

          if (adoptionType.equals("Individual adoption")) {
        %>
        <option value="Individual adoption" selected="selected"><%=props.getProperty("type.individual")%></option>
        <%
        } else {
        %>
        <option value="Individual adoption"><%=props.getProperty("type.individual")%></option>
        <%
          }


          if (adoptionType.equals("Group adoption")) {
        %>
        <option value="Group adoption" selected="selected"><%=props.getProperty("type.group")%></option>
        <%
        } else {
        %>
        <option value="Group adoption"><%=props.getProperty("type.group")%></option>
        <%
          }


          if (adoptionType.equals("Corporate adoption")) {
        %>
        <option value="Corporate adoption" selected="selected"><%=props.getProperty("type.corporate")%></option>
        <%
        } else {
        %>
        <option value="Corporate adoption"><%=props.getProperty("type.corporate")%></option>
        <%
          }
        %>


      </select></td>
    </tr>


    <tr>
      <td><%=props.getProperty("startDate")%>:</td>
      <td><input id="adoptionStartDate" name="adoptionStartDate" type="text" size="30" value="<%=adoptionStartDate%>"> <em>(e.g. 2009-05-15) </input> </em></td>
    </tr>

    <tr>
      <td><%=props.getProperty("endDate")%>:</td>
      <td><input name="adoptionEndDate" type="text" size="30" value="<%=adoptionEndDate%>"> </input> <em>(e.g. 2010-05-15) </em></td>
    </tr>

    <!--
			 			 <tr>
			 <td><%=props.getProperty("endDate")%>:</td>
			 <td><div id="calendar2"></div>
   				 <div id="date2">
				  <input  class="dateField" id="adoptionEndDate" name="adoptionEndDate" type="text" size="30" value="<%=adoptionEndDate%>"></input>
			</div>
				</td>
			</tr>
			 -->

    <tr>
      <td><%=props.getProperty("manager")%>:</td>
      <td>
        <%if (request.getRemoteUser() != null) {%>
        <input name="adoptionManager" type="text" value="<%=request.getRemoteUser()%>" value="<%=adoptionManager%>"></input>
        <%} else {%>
        <input name="adoptionManager" type="text" value="N/A" value="<%=adoptionManager%>"></input>
        <%}%>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top"><%=props.getProperty("notes")%>:</td>
      <td><textarea name="notes" cols="40" id="notes" rows="10"><%=notes%></textarea>
        <%
          if (request.getParameter("number") != null) {
        %>
        <br/><input type="hidden" name="number" value="<%=id%>"/>
        <%
          }
        %>
      </td>
    </tr>

    <%
      if (isOwner) {
    %>
    <tr>
      <td><input type="submit" name="Submit" value="<%=props.getProperty("submit")%>"/></td>
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
      <h3><a name="delete" id="delete"></a><%=props.getProperty("deleteAdoption")%></h3>
    </td>
  </tr>
  <tr>
    <td>
      <form action="rejectAdoption.jsp" method="get">
        <input type="hidden" name="number" value="<%=id%>"/>
        <input name="Delete" type="submit" value="<%=props.getProperty("delete")%>"/>
      </form>
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
      <h3><a name="restore" id="restore"></a><%=props.getProperty("restoreAdoption")%></h3>
    </td>
  </tr>
  <tr>
    <td>
      <form action="../ResurrectDeletedAdoption" method="get" name="restoreDeletedAdoption">
        <%=props.getProperty("number")%>:
        <input name="number" type="text" size="25"/>
        <input type="submit" name="Submit" value="<%=props.getProperty("submit")%>"/>
      </form>
      <br/>
  </tr>
  </td>
</table>
<%
  }
%>
</div>

<jsp:include page="../footer.jsp" flush="true"/>



