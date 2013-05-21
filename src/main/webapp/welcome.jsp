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
         import="java.util.ArrayList,org.ecocean.*,java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
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

</head>

<%

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


  //setup our Properties object to hold all properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


  //set up the file input stream
  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/welcome.properties"));

  session = request.getSession(true);
  session.putValue("logged", "true");
  if ((request.getParameter("reflect") != null)) {
    response.sendRedirect(request.getParameter("reflect"));
  }
  ;
%>

<body bgcolor="#FFFFFF">
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
      <div id="leftcol">
        <div id="menu"></div>
        <!-- end menu --></div>
      <!-- end leftcol -->
      <div id="maincol-wide">

        <div id="maintext">

          <h1 class="intro"><%=props.getProperty("loginSuccess")%>
          </h1>


          <p><%=props.getProperty("loggedInAs")%> <strong><%=request.getRemoteUser()%>
          </strong>.
          </p>

          <p><%=props.getProperty("grantedRole")%>
			<%
			Shepherd myShepherd=new Shepherd();
			myShepherd.beginDBTransaction();
			%>
             <strong><%=myShepherd.getAllRolesForUserAsString(request.getRemoteUser())%></strong></p>
            
            <%
            
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            
	        Logger log = LoggerFactory.getLogger(getClass());
	        log.info(request.getRemoteUser()+" logged in from IP address "+request.getRemoteAddr()+".");

	    %>


          <p><%=props.getProperty("pleaseChoose")%>
          </p>

          <p>&nbsp;</p>
        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
      <jsp:include page="footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>
</html>
