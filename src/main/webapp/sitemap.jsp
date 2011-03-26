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
         import="org.ecocean.CommonConfiguration, org.ecocean.Encounter,org.ecocean.MarkedIndividual, org.ecocean.Shepherd, javax.jdo.Extent, javax.jdo.Query,java.io.File" %>
<%@ page import="java.io.FileInputStream" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Properties" %>
<%

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";

  //Shepherd
  Shepherd myShepherd = new Shepherd();

  //check what language is requested
  if (request.getParameter("langCode") != null) {
    if (request.getParameter("langCode").equals("fr")) {
      langCode = "fr";
    }
    if (request.getParameter("langCode").equals("de")) {
      langCode = "de";
    }
    if (request.getParameter("langCode").equals("es")) {
      langCode = "es";
    }
  }

  //set up the file input stream
  FileInputStream propsInputStream = new FileInputStream(new File((new File(".")).getCanonicalPath() + "/webapps/ROOT/WEB-INF/classes/bundles/" + langCode + "/submit.properties"));
  props.load(propsInputStream);

  //load our variables for the submit page
  String title = props.getProperty("submit_title");
  String submit_maintext = props.getProperty("submit_maintext");
  String submit_reportit = props.getProperty("reportit");
  String submit_language = props.getProperty("language");
  String what_do = props.getProperty("what_do");
  String read_overview = props.getProperty("read_overview");
  String see_all_encounters = props.getProperty("see_all_encounters");
  String see_all_sharks = props.getProperty("see_all_sharks");
  String report_encounter = props.getProperty("report_encounter");
  String log_in = props.getProperty("log_in");
  String contact_us = props.getProperty("contact_us");
  String search = props.getProperty("search");
  String encounter = props.getProperty("encounter");
  String shark = props.getProperty("shark");
  String join_the_dots = props.getProperty("join_the_dots");
  String menu = props.getProperty("menu");
  String last_sightings = props.getProperty("last_sightings");
  String more = props.getProperty("more");
  String ws_info = props.getProperty("ws_info");
  String about = props.getProperty("about");
  String contributors = props.getProperty("contributors");
  String forum = props.getProperty("forum");
  String blog = props.getProperty("blog");
  String area = props.getProperty("area");
  String match = props.getProperty("match");

  //link path to submit page with appropriate language
  String submitPath = "submit.jsp?langCode=" + langCode;


%>

<html>
<head>
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


</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isResearcher"
                 value="<%=request.isUserInRole("researcher")%>"/>
      <jsp:param name="isManager"
                 value="<%=request.isUserInRole("manager")%>"/>
      <jsp:param name="isReviewer"
                 value="<%=request.isUserInRole("reviewer")%>"/>
      <jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>"/>
    </jsp:include>
    <div id="main"><!-- end leftcol -->
      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro">ECOCEAN Whale Shark Photo-identification Library
            Sitemap</h1>
        </div>
        <ul>
          <li><a href="http://<%=CommonConfiguration.getURLLocation(request)%>">Home</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/photographing.jsp">Photographing
            a whale shark</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/submit.jsp">Report
            a whale shark encounter</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp">All
            encounters</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/allIndividuals.jsp">All
            sharks</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/video.jsp">Video</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/thumbs.jsp">Image
            thumbnails</a></li>
          <li><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/wiki/doku.php?id=home">ECOCEAN
            Library Wiki</a></li>
          <li><a href="publications.jsp">Publications</a></li>
          <li><a href="onlinehelp/spot/index.jsp">Spot</a></li>
          <li><strong>All encounters</strong></li>
        </ul>
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
        <ul>
          <li><strong>All sharks</strong></li>
        </ul>
        <%
          Iterator it3 = myShepherd.getAllSharks(sharkQuery);
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
