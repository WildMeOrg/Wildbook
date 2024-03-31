

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.util.Properties" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


  //set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/error.properties"));
  props = ShepherdProperties.getProperties("error.properties", langCode,context);

%>

<html>

<!-- Standard Content -->
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
          <h1 class="intro"><%=props.getProperty("notLogged")%>
          </h1>
          <br>

          <p><a
            href="//<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp"><%=props
            .getProperty("clickHere")%>
          </a></p>
        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
      <jsp:include page="footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>


</html>
