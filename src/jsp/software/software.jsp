<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*"%>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	

	
%>

<html>
<head>
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

<body>
<div id="wrapper">
<div id="page"><jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<div id="main">

<div id="maincol-wide">

<div id="maintext">
<h1 class="intro">Client Software</h1>
</div>
<p>Click on a link below to download the appropriate software
client.</p>

<p><a href="Interconnect.zip"><strong>Interconnect</strong></a><br />
<em>Purpose</em>: Spot mapping and submission to the Shepherd Project Framework<br />
<em>Requirements</em>: <a href="http://www.java.com/en/download/index.jsp">Java 6</a>
</p>

<p><a href="GridClient.zip"><strong>Grid Client</strong></a><br />
<em>Purpose</em>: Spot pattern comparison in the Shepherd Project Framework<br />
<em>Requirements</em>: <a href="http://www.java.com/en/download/index.jsp">Java 6</a>
</p>


</div>
<!-- end maintext --></div>
<!-- end maincol --> <jsp:include page="../footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>
