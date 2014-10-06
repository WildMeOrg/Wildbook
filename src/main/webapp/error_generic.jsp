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
<%@page contentType="text/html; charset=utf-8" language="java"
        import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities"
        isErrorPage="true" %>
<%

  //setup our Properties object to hold all properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  //set up the file input stream
  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/error.properties"));

  String context="context0";
  context=ServletUtilities.getContext(request);
  
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>

<!-- Standard Content -->
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request, context) %>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
  <style>
    #main pre {
      background: #CCC;
      font-size: 0.75em;
    }
  </style>
</head>

<!-- Body -->

<body bgcolor="#FFFFFF" link="#990000">
<div id="wrapper">

  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>

    <div id="main">

      <div id="maincol-wide">

        <div id="maintext">


          <h1 class="intro">Error</h1>

          <p>The following error occurred; please inform the system administrator:</p>

          <c:set var="exception" value="${requestScope['javax.servlet.error.exception']}"/>
          <pre><% exception.printStackTrace(new java.io.PrintWriter(out)); %></pre>


        <!-- end maintext --></div>

      <!-- end maincol --></div>

      <jsp:include page="footer.jsp" flush="true"/>

    <!-- end main --></div>
  <!-- end page --></div>
<!--end wrapper -->
</body>


</html>
