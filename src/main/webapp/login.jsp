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
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


//set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);


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
  <body link="#990000">
  <center><!-- Login -->

    <div id="wrapper">
      <div id="page">
        <jsp:include page="header.jsp" flush="true">
         
          <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
        </jsp:include>
        <div id="main">
          <div id="maincol-wide-solo">

            <div id="maintext">

              <h1 class="intro"><%=props.getProperty("databaseLogin")%>
              </h1>

              <p align="center"><%=props.getProperty("requested")%>
              </p>

              <p align="center">
			<!--  
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
              
              -->
              
              <form action="LoginUser" method="post">
    <table align="left" border="0" cellspacing="0" cellpadding="3">
        <tr>
            <td><%=props.getProperty("username") %></td>
            <td><input type="text" name="username" maxlength="50" /></td>
        </tr>
        <tr>
            <td><%=props.getProperty("password") %></td>
            <td><input type="password" name="password" maxlength="50" /></td>
        </tr>
        <tr>
        <td colspan="2" align="left">
        <input type="checkbox" name="rememberMe" value="true"/> <%=props.getProperty("rememberMe") %> 
        </td>
        </tr>
        <tr>
            <td colspan="2" align="right"><input type="submit" name="submit" value="<%=props.getProperty("login") %>" /></td>
        </tr>
    </table>
</form>
              
              </p>


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
