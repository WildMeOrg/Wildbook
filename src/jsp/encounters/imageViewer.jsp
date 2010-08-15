<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="org.ecocean.*, javax.jdo.*"%>

<%

String number=request.getParameter("number");
String src=request.getParameter("src");
Shepherd myShepherd=new Shepherd();

%>

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
<div id="maincol-wide-solo">
<h1 class="intro">Viewing Image <%=src%> of Encounter <%=number%></h1>
<%
	myShepherd.beginDBTransaction();



	Encounter enc=myShepherd.getEncounter(number);
	
	myShepherd.rollbackDBTransaction();

	%>
	
<p><a href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=number%>/<%=src%>">Click
here to access the original source image</a></p>

<table width="720">
	<tr>
		<td align="left" valign="top">

		
		<p>
		<object style="width: 810px; height: 540px;" classid="clsid:d27cdb6e-ae6d-11cf-96b8-444553540000" width="640" height="360" codebase="http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=6,0,40,0">
		<param name="src" value="keenerview.swf?image_url=http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=number%>/<%=src%>" />
		<embed style="width: 810px; height: 540px;" type="application/x-shockwave-flash" width="640" height="360" src="keenerview.swf?image_url=http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=number%>/<%=src%>"></embed>
		</object>
		</p>
		
		
		
		<p>&nbsp;</p>
		</td>
	</tr>
</table>
<jsp:include page="../footer.jsp" flush="true" /></div>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>