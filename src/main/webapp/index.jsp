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
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,org.ecocean.Shepherd,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory, java.util.Properties" %>


<%

  //grab a gridManager
  GridManager gm = GridManagerFactory.getGridManager();
  int numProcessors = gm.getNumProcessors();
  int numWorkItems = gm.getIncompleteWork().size();

  Shepherd myShepherd = new Shepherd();

//setup our Properties object to hold all properties

  //language setup
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));


%>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title><%=CommonConfiguration.getHTMLTitle()%>
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

  <link rel="shortcut icon" href="images/favicon.ico"/>
  <style type="text/css">
    <!--

    table.adopter {
      border-width: 1px 1px 1px 1px;
      border-spacing: 0px;
      border-style: solid solid solid solid;
      border-color: black black black black;
      border-collapse: separate;
      background-color: white;
    }

    table.adopter td {
      border-width: 1px 1px 1px 1px;
      padding: 3px 3px 3px 3px;
      border-style: none none none none;
      border-color: gray gray gray gray;
      background-color: white;
      -moz-border-radius: 0px 0px 0px 0px;
      font-size: 12px;
      color: #330099;
    }

    table.adopter td.name {
      font-size: 12px;
      text-align: center;
    }

    table.adopter td.image {
      padding: 0px 0px 0px 0px;
    }

    .style2 {
      font-size: x-small;
      color: #000000;
    }

    -->
  </style>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
      <div id="leftcol">
        <div id="menu">
          <%
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
          %>


          <div class="module">
            <h3>Latest News</h3>
            <span class="caption">Add your news items here... </span><span class="caption"><br/>
</span> <br/>
          </div>

          <div class="module">
            <h3>Data Sharing</h3>
            <span class="caption">If you are sharing data, this is a great place to let others know about it... </span>
            <br/>
          </div>

        </div>
        <!-- end menu --></div>
      <!-- end leftcol -->
      <div id="maincol">

        <div id="maintext">
          <h1 class="intro">Overview</h1>

          <p class="caption">This is a great place to present an overview description of this
            mark-recapture project and library...</p>
          <br/>
        </div>

        <div>
          <h1 class="intro">Data Contributors</h1>

          <p class="caption">A great optional area to discuss who is contributing data to this
            library...</p>
        </div>

        <div id="context">
          <h1 class="intro">Contact us</h1>

          <p class="caption">A great place to talk about who is running this mark-recapture
            library...</p>

          <p class="caption"><a href="contactus.jsp">Please contact us
            with you questions.</a></p>
        </div>


      </div>
      <!-- end maincol -->
      <div id="rightcol">


        <div class="module">
          <h3>Find Record</h3>

          <form name="form2" method="get" action="individuals.jsp">
            <em>Enter a marked animal number, encounter number, animal nickname, or alternate
              ID.</em><br/>
            <input name="number" type="text" id="shark" size="25"/>
            <input type="hidden" name="langCode" value="<%=langCode%>"/><br/>
            <input name="Go" type="submit" id="Go2" value="Go"/>
          </form>

        </div>


        <div class="module">
          <h3>RSS/Atom Feeds</h3>

          <p align="left"><a href="rss.xml"><img src="images/rssfeed.gif"
                                                 width="80" height="15" border="0"
                                                 alt="RSS News Feed"/></a></p>

          <p align="left"><a href="atom.xml"><img
            src="images/atom-feed-icon.gif" border="0" alt="ATOM News Feed"/></a></p>
        </div>


      </div>
      <!-- end rightcol --></div>
    <!-- end main -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->

</body>
</html>
