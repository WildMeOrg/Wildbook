<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2013 Jason Holmberg
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
<%


  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";



  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/removeEmailAddress.properties"));
  props = ShepherdProperties.getProperties("removeEmailAddress.properties", langCode);


  //load our variables for the submit page
  String warning = props.getProperty("warning");
	String hashedEmail="NONE";
	if(request.getParameter("hashedEmail")!=null){hashedEmail=request.getParameter("hashedEmail");}	  



%>
<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
     
      <div id="maincol-wide-solo">

        <div id="maintext">
          <table border="0">
            <tr>
              <td>
                <h1 class="intro"><%=props.getProperty("removeTitle") %></h1>

                <p><%=warning %></p>
                <p></p>
              </td>
            </tr>
            <tr>
              <td>
                <p>&nbsp;</p>

              
                <table width="720" border="0" cellpadding="5" cellspacing="0">
                  <tr>
                    <td align="right" valign="top">
                      <form name="remove_email" method="post" action="/RemoveEmailAddress">
                        <input name="hashedEmail" type="hidden" value="<%=hashedEmail%>" /> 
                        <input name="yes" type="submit" id="yes" value="<%=props.getProperty("remove") %>" />
                       </form>
                    </td>
                 
                    <td align="left" valign="top">
                      <form name="form2" method="post" action="/index.jsp">
                               <input name="no" type="submit" id="no" value="Cancel" />
                      </form>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>

        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
      <jsp:include page="footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>
</html>
