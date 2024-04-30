

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
