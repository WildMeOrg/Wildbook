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
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties" %>
<%

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  
  String context="context0";
  context=ServletUtilities.getContext(request);


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

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
    </jsp:include>
    <div id="main">
 
      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro">Failed submission</h1>
        </div>
        <p>You have reached this page because your encounter report was
          rejected. There are two reasons this might occur:</p>
        <ol>
          <li>Our system correctly or incorrectly detected a false
            submission by a spambot. There are many attempts by automated Internet
            programs called &quot;spambots&quot; to post unrelated content on web sites. To prevent inappropriate content, we have
            filters that attempt to block spambots.
          </li>
          <li>An unknown problem was encountered.</li>
        </ol>
        <p>We apologize in advance if you believe you have reached this page
          in error and have a genuine whale shark encounter to report. As an
          alternative, please email your photos and encounter information (date,
          time, size, location, etc.) to:</p>

        <p>We appreciate your effort and your help in our research!</p>


        <p>
          <script type="text/javascript"
                  src="http://www.google.com/coop/cse/brand?form=searchbox_001757959497386081976%3An08dpv5rq-m"></script>
          <!-- Google CSE Search Box Ends --></p>
      </div>
      <!-- end maintext --></div>
    <!-- end maincol -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>
