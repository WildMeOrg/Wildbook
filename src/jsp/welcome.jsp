<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Vector,org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,org.apache.log4j.Logger,org.apache.log4j.PropertyConfigurator"%>
<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation() %>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

</head>

<%

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 

	
	//setup our Properties object to hold all properties
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	
	//set up the file input stream
	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/welcome.properties"));
	
session=request.getSession(true);
session.putValue("logged", "true");
if ((request.getParameter("reflect")!=null)) {response.sendRedirect(request.getParameter("reflect"));};
%>

<body bgcolor="#FFFFFF">
<div id="wrapper">
<div id="page"><jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<div id="main">
<div id="leftcol">
<div id="menu"></div>
<!-- end menu --></div>
<!-- end leftcol -->
<div id="maincol-wide">

<div id="maintext">

<h1 class="intro"><%=props.getProperty("loginSuccess")%></h1>


<p><%=props.getProperty("loggedInAs")%> <strong><%=request.getRemoteUser()%></strong>.
</p>
<p><%=props.getProperty("grantedRole")%>

<% 
String role="";
if (request.isUserInRole("admin")) {role="Administrator";}


%> <strong><%=role%></strong>.</p>
<p><%=props.getProperty("pleaseChoose")%></p>
<p>&nbsp;</p>
</div>
<!-- end maintext --></div>
<!-- end maincol --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>
