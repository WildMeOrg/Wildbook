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
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";

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

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));


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


  <script language="javascript" type="text/javascript">
    <!--

    function validate() {
      var requiredfields = "";

      if (document.encounter_submission.submitterName.value.length == 0) {
        /*
         * the value.length returns the length of the information entered
         * in the Submitter's Name field.
         */
        requiredfields += "\n   *  Your name";
      }
      if (document.encounter_submission.theFile1.value.length == 0) {
        /*
         * the value.length returns the length of the information entered
         * in the Location field.
         */
        requiredfields += "\n   *  Image 1";
      }
      if ((document.encounter_submission.submitterEmail.value.length == 0) ||
        (document.encounter_submission.submitterEmail.value.indexOf('@') == -1) ||
        (document.encounter_submission.submitterEmail.value.indexOf('.') == -1)) {
        /*
         * here we look to make sure the email address field contains
         * the @ symbol and a . to determine that it is in the correct format
         */
        requiredfields += "\n   *  valid Email address";
      }
      if ((document.encounter_submission.location.value.length == 0)) {
        requiredfields += "\n   *  valid sighting location";
      }

      if (requiredfields != "") {
        requiredfields = "Please correctly enter the following fields:\n" + requiredfields;
        alert(requiredfields);
// the alert function will popup the alert window
        return false;
      }
      else return true;
    }

    //-->
  </script>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide-solo">
        <div id="maintext">
          <p>

          <h1 class="intro">Logout</h1>
          </p>
        </div>

        <p>Thank you for using this software! You are now safely logged out.</p>
        
            <%
		        Logger log = LoggerFactory.getLogger(getClass());
		        log.info(request.getRemoteUser()+" logged out.");
	
	    %>

        <p><a href="welcome.jsp">Click here to log back in.</a></p>

        <p>&nbsp;</p>
      </div>
      <!-- end maintext --></div>
    <!-- end maincol -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
<%
  session.invalidate();
%>
</html>
