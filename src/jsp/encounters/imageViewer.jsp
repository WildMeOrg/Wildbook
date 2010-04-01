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
<h1 class="intro">Viewing Image <%=src%> of Encounter <%=number%></h1>
<%
	myShepherd.beginDBTransaction();



	Encounter enc=myShepherd.getEncounter(number);
	boolean isOwner=false;
	if(((request.isUserInRole("imageProcessor"))&&(request.isUserInRole(enc.getLocationCode())))||(request.isUserInRole("manager"))) {
		isOwner=true;
	}
	myShepherd.rollbackDBTransaction();
	if(isOwner) {%>
<p><strong>Permission granted</strong>: <a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=number%>/<%=src%>">Click
here to access the original source image</a></p>
<%}

%>
<p><img src="../alert.gif" /> <strong>Important:</strong> The image
below is the intellectual property of the original photographer. It may
be used for research and conservation purposes only. It may not be used
publicly without the express permission of the photographer. This image
may also not be distributed beyond the users of the ECOCEAN Library or
related conservation authorities without the express permission of the
photographer. By copying or downloading this image or any portion of it,
you assume responsibility for its usage and agree to hold ECOCEAN and
other users of the ECOCEAN Library harmless for any misuse of it.</p>

<table width="720">
	<tr>
		<td align="left" valign="top">
		<p><object classid="clsid:D27CDB6E-AE6D-11cf-96B8-444553540000"
			codebase="http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=6,0,29,0"
			width="720" height="480">
			<param name="movie" value="view.swf?number=<%=number%>&src=<%=src%>">
			<param name="quality" value="high">
			<param name="wmode" value="transparent"><embed
				src="view.swf?number=<%=number%>&src=<%=src%>" quality="high"
				pluginspage="http://www.macromedia.com/go/getflashplayer"
				type="application/x-shockwave-flash" width="720" height="480"></embed>
		</object></p>
		<p>&nbsp;</p>
		</td>
	</tr>
</table>
<jsp:include page="../footer.jsp" flush="true" /></div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>