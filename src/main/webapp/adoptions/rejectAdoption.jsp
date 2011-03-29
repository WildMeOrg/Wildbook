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
         import="org.ecocean.CommonConfiguration" %>
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


%>
<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide">

        <div id="maintext">
          <table border="0">
            <tr>
              <td>
                <h1 class="intro">Delete an adoption</h1>

                <p>You are about to <strong>DELETE</strong> an adoption from the
                  database. If you choose <strong>Permanently delete</strong>, all data
                  contained within this adoption will be removed from the database<em>.
                    However, the webmaster can restore the adoption in case you
                    accidentally delete it </em>. Choose <strong>Cancel</strong> to retain the
                  adoption in the database as is.</p>
              </td>
            </tr>
            <tr>
              <td>
                <p>Do you want to reject adoption <%=request.getParameter("number")%>?</p>
                <table width="400" border="0" cellpadding="5" cellspacing="0">
                  <tr>
                    <td align="center" valign="top">
                      <form name="reject_form" method="post" action="../DeleteAdoption">
                        <input name="action" type="hidden" id="action" value="reject">
                        <input name="number" type="hidden"
                               value=<%=request.getParameter("number")%>> <input
                        name="yes" type="submit" id="yes" value="Permanently delete"></form>
                    </td>
                    <td align="left" valign="top">
                      <form name="form2" method="GET" action="adoption.jsp"><input
                        name="number" type="hidden"
                        value=<%=request.getParameter("number")%>> <input name="no"
                                                                          type="submit" id="no"
                                                                          value="Cancel"></form>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>

        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
      <jsp:include page="../footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>
</html>
