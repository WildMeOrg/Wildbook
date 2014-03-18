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
        import="org.ecocean.CommonConfiguration,java.util.Properties"
        isErrorPage="true" %>
<%

  //setup our Properties object to hold all properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Throwable ex = (Throwable)request.getAttribute("javax.servlet.error.exception");
  Integer exStatusCode = (Integer)request.getAttribute("javax.servlet.error.status_code");
  String exType = (String)request.getAttribute("javax.servlet.error.exception_type");
  String exMessage = (String)request.getAttribute("javax.servlet.error.message");
  String exServletName = (String)request.getAttribute("javax.servlet.error.servlet_name");
  if (exServletName == null)
    exServletName = "Unknown";
  String exRequestUri = (String)request.getAttribute("javax.servlet.error.request_uri");
  if (exRequestUri == null)
    exRequestUri = "Unknown";

  Throwable thrown = (Throwable)request.getAttribute("thrown");

  //set up the file input stream
  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/error.properties"));

%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>

<!-- Standard Content -->
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
          <p>The following error occurred:</p>
          <pre>
<%
  if (thrown != null) {
    thrown.printStackTrace(response.getWriter());
    request.getSession().removeAttribute("thrown");
  }
%>
          </pre>
<%
  if (ex != null) {
%>
          <ul>
            <li>Exception: <c:out value="${requestScope['javax.servlet.error.exception']}" /></li>
            <li>Exception type: <c:out value="${requestScope['javax.servlet.error.exception_type']}" /></li>
            <li>Exception message: <c:out value="${requestScope['javax.servlet.error.message']}" /></li>
            <li>Request URI: <c:out value="${requestScope['javax.servlet.error.request_uri']}" /></li>
            <li>Servlet name: <c:out value="${requestScope['javax.servlet.error.servlet_name']}" /></li>
            <li>Status code: <c:out value="${requestScope['javax.servlet.error.status_code']}" /></li>
          </ul>
<%
  } else {
%>
          <p>No error was found to be reported.</p>
<%
  }
%>
          <p><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp"><%=props.getProperty("clickHere")%></a></p>
        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
      <jsp:include page="footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>


</html>
