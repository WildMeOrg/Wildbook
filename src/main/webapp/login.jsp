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
         import="org.ecocean.CommonConfiguration, java.util.Properties" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%

  //setup our Properties object to hold all properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


//set up the file input stream
  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));


%>

<html:html locale="true">

  <!-- Make sure window is not in a frame -->

  <script language="JavaScript" type="text/javascript">

    <!--
    if (window.self != window.top) {
      window.open(".", "_top");
    }
    // -->

  </script>

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

    <style type="text/css">
      <!--
      .style1 {
        color: #FF0000;
        font-weight: bold;
      }

      -->
    </style>
  </head>


  <!-- Standard Content -->
  <!-- Body -->
  <body bgcolor="#FFFFFF" link="#990000">
  <center><!-- Login -->

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
        <div id="main">
          <div id="maincol-wide-solo">

            <div id="maintext">

              <h1 class="intro"><%=props.getProperty("databaseLogin")%>
              </h1>

              <p align="center"><%=props.getProperty("requested")%>
              </p>

              <p>

              <form method="POST"
                    action='<%= response.encodeURL("j_security_check") %>' name="loginForm">
                <table border="0" align="center" cellpadding="5" cellspacing="2">
                  <tr align="left" valign="top">
                    <th align="left"><font color="#000000"><%=props.getProperty("username")%>:
                      <input type="text"
                             name="j_username" size="16" maxlength="16"/></font></th>
                  </tr>

                  <tr align="left" valign="top">
                    <th align="left"><font color="#0000"><%=props.getProperty("password") %>: <input
                      type="password" name="j_password" size="16" maxlength="16"/></font></th>
                  </tr>
                  <!-- login reset buttons layout -->
                  <tr align="left" valign="top">
                    <td align="left">
                      <div align="left">
                        <input name="submit" type="submit"
                               value='<%=props.getProperty("login") %>'/> <input name="reset"
                                                                                 type="reset"
                                                                                 value='<%=props.getProperty("reset") %>'/>
                        &nbsp;&nbsp; </div>
                    </td>
                  </tr>
                </table>
              </form>
              </p>

              <script language="JavaScript" type="text/javascript">
                <!--
                document.forms["loginForm"].elements["j_username"].focus()
                // -->
              </script>

              <p>&nbsp;</p>

              </td>
              </tr>
              </table>

              <p>&nbsp;</p>
            </div>
            <!-- end maintext --></div>
          <!-- end maincol -->
          <jsp:include page="footer.jsp" flush="true"/>
        </div>
        <!-- end page --></div>
      <!--end wrapper -->
  </body>


</html:html>
