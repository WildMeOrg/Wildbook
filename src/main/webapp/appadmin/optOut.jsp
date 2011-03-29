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
         import="org.ecocean.CommonConfiguration" %>
<%@ page import="org.ecocean.Shepherd" %>

<%

  Shepherd myShepherd = new Shepherd();

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

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
      <p><font size="+1"><strong>Do you want to leave the
        <%=request.getParameter("name")%> mailing list?</strong></font></p>

      <p>You have followed a link that will remove you from the <%=request.getParameter("name")%>
        mailing list. Do you want to be removed?</p>

      <form action="../MailHandler" method="post" name="optOut" id="optOut">
        <div align="center"><input name="action" type="hidden"
                                   value="removeEmail"> <input name="address" type="hidden"
                                                               value="<%=request.getParameter("address")%>">
          <input
            name="name" type="hidden" value="<%=request.getParameter("name")%>">
          <input name="remover" type="submit" id="remover"
                 value="Remove My E-mail From This List"></div>
      </form>
      <br> <br> <br>
      <jsp:include page="../footer.jsp"
                   flush="true"/>
    </div>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>


