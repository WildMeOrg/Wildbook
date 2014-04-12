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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.CommonConfiguration, org.ecocean.Encounter,org.ecocean.MarkedIndividual, org.ecocean.Shepherd, javax.jdo.Extent, javax.jdo.Query,java.io.File" %>
<%@ page import="java.io.FileInputStream" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Properties" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  //setup our Properties object to hold all properties


  //Shepherd
  Shepherd myShepherd = new Shepherd(context);






%>

<html>
<head>
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

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
    </jsp:include>
    <div id="main"><!-- end leftcol -->
      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro"><%=CommonConfiguration.getHTMLTitle(context) %>
            Sitemap</h1>
        </div>
        <ul>
          <li><a href="http://<%=CommonConfiguration.getURLLocation(request)%>">Home</a></li>
    
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/submit.jsp">Report
            an encounter</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/searchResults.jsp">All
            Encounters</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/individualSearchResults.jsp">All
            Marked Individuals</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/thumbnailSearchResults.jsp?noQuery=true">Image
            thumbnails</a></li>

</ul>
          <h2>All encounters</h2>
        
        <%


          Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
          Query encQuery = myShepherd.getPM().newQuery(encClass);
          Extent sharkClass = myShepherd.getPM().getExtent(MarkedIndividual.class, true);
          Query sharkQuery = myShepherd.getPM().newQuery(sharkClass);
          myShepherd.beginDBTransaction();
          try {
            Iterator it2 = myShepherd.getAllEncounters(encQuery);
            while (it2.hasNext()) {
              Encounter tempEnc2 = (Encounter) it2.next();
        %> <a
        href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=tempEnc2.getEncounterNumber()%>"><%=tempEnc2.getEncounterNumber()%>
      </a> <%
        }
      %>
       
          <h2>All marked individuals</h2>
       
        <%
          Iterator it3 = myShepherd.getAllMarkedIndividuals(sharkQuery);
          while (it3.hasNext()) {
            MarkedIndividual tempShark = (MarkedIndividual) it3.next();
        %> <a
        href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=tempShark.getName()%>"><%=tempShark.getName()%>
      </a> <%
          }


        } catch (Exception e) {
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        encQuery.closeAll();
        encQuery = null;
        sharkQuery.closeAll();
        sharkQuery = null;


      %>
      </div>
      <!-- end maintext --></div>
    <!-- end maincol -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>
