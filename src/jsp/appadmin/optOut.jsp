<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="org.ecocean.*"%>

<%

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
<p><font size="+1"><strong>Do you want to leave the
<%=request.getParameter("name")%> mailing list?</strong></font></p>
<p>You have followed a link that will remove you from the <%=request.getParameter("name")%>
mailing list. Do you want to be removed?</p>
<form action="../MailHandler" method="post" name="optOut" id="optOut">
<div align="center"><input name="action" type="hidden"
	value="removeEmail"> <input name="address" type="hidden"
	value="<%=request.getParameter("address")%>"> <input
	name="name" type="hidden" value="<%=request.getParameter("name")%>">
<input name="remover" type="submit" id="remover"
	value="Remove My E-mail From This List"></div>
</form>
<br> <br> <br> <jsp:include page="../footer.jsp"
	flush="true" />
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


