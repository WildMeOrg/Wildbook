<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2012 Jason Holmberg
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
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration, org.ecocean.Encounter, org.ecocean.MarkedIndividual, org.ecocean.Shepherd,org.ecocean.servlet.ServletUtilities,javax.jdo.Extent,javax.jdo.FetchPlan, javax.jdo.Query, java.util.Iterator, java.util.Properties" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
	Shepherd myShepherd = new Shepherd(context);
	myShepherd.beginDBTransaction();
	
    //setup our Properties object to hold all properties
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
        langCode = (String) session.getAttribute("langCode");
    }
    
   String mediaId = request.getParameter("mediaId");
   String encounterId = request.getParameter("encounterId");
   
   if (mediaId == null) {
	   mediaId = "";
   }
   if (encounterId == null) {
	   encounterId = "";
   }
%>
<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
</head>

<body>
<div id="wrapper">
<div id="page">
	<jsp:include page="header.jsp" flush="true">
	  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
	</jsp:include>
   
        
    <div id="main">
        <form method="post" action="EncounterSetPatterningPassport" enctype="multipart/form-data">
Encounter ID
<input type="text" name="encounterId" value="<%=encounterId%>">
<p/>
Photo/Media ID
<input type="text" name="mediaId" value="<%=mediaId%>">
<p/>
Patterning Passport XML File ***
<input type="file" name="patterningPassportData">
<p/>
<input type="submit">
</form>
        <!-- end main -->
        <jsp:include page="footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
<!--end wrapper --></div>
<%
   
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
 
    myShepherd = null;
%>
</body>
</html>