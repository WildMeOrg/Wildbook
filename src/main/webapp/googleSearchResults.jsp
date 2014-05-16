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
         import="org.ecocean.*, java.util.Properties,org.ecocean.servlet.ServletUtilities" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);
  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  
  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
 // props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("googleSearch.properties", langCode, context);



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
 
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro"><%=props.getProperty("resultsTitle") %></h1>
        </div>
        <!-- Google Search Result Snippet Begins -->
        <div id="results_<%=CommonConfiguration.getGoogleSearchKey(context) %>"></div>
        <script type="text/javascript">
          var googleSearchIframeName = "results_<%=CommonConfiguration.getGoogleSearchKey(context) %>";
          var googleSearchFormName = "searchbox_<%=CommonConfiguration.getGoogleSearchKey(context) %>";
          var googleSearchFrameWidth = 600;
          var googleSearchFrameborder = 0;
          var googleSearchDomain = "www.google.com";
          var googleSearchPath = "/cse";
        </script>
        <script type="text/javascript"
                src="http://www.google.com/afsonline/show_afs_search.js"></script>
        <!-- Google Search Result Snippet Ends -->

      </div>
      <!-- end maintext --></div>
    <!-- end maincol -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>
