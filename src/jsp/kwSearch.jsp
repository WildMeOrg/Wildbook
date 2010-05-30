<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="org.ecocean.*, java.util.Iterator"%>
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

<body bgcolor="#FFFFFF" link="#990000">
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
<%myShepherd.beginDBTransaction();%>
<p>
<h1 class="intro">
<table>
	<tr>
		<td><img src="../images/tag_big.gif" width="50" height="50"
			hspace="3" vspace="3" align="absmiddle" /> Keyword Photo Search</td>
	</tr>
</table>
</h1>
</p>
<%try {%>
<p>
<%if((request.getParameter("primaryImageName")!=null)&&(!request.getParameter("primaryImageName").equals(""))) {%>
Comparison image selected: <a
	href="../encounters/<%=request.getParameter("primaryImageName")%>"><img
	src="../encounters/<%=request.getParameter("primaryImageName")%>"
	width="100" border="1" align="top"></a> <%}%>
</p>
<p>Keyword photo searching allows you to search the individual
photos in the Library for pre-defined features of interest to you. In
order for a photo to appear in the results of the search, it must first
have one or more keywords assigned to it. You can assign keywords to
each image in that image's encounter page.</p>
<p>In the box below, select one or more keywords to limit your
search by. Note that the results of a search using one keyword will
return all images that have been assigned the keyword. A search using
two keywords returns a subset of photos that each have both keywords
assigned. The more keywords used, the smaller the subset returned.</p>
<table width="720" border="1" cellpadding="3" bordercolor="#000000"
	bgcolor="#CCCCCC">
	<tr>
		<td>
		<p><strong>Select keywords to define your photo search: </strong></p>
		<form action="kwResults.jsp" method="get" name="imageSearch"
			id="imageSearch">
		<%if((request.getParameter("primaryImageName")!=null)&&(!request.getParameter("primaryImageName").equals(""))) {%>
		<input name="primaryImageName" type="hidden"
			value=<%=request.getParameter("primaryImageName").replaceAll(" ","%20")%>>
		<%}%>



		<p>Keyword 1: <select name="search1" id="search1">
			<% 
			  int totalKeywords=myShepherd.getNumKeywords();
			  	Iterator keys=myShepherd.getAllKeywords();
			  	for(int n=0;n<totalKeywords;n++) {
					Keyword word=(Keyword)keys.next();
					String indexname=word.getIndexname();
					String readableName=word.getReadableName();
				%>
			<option value="<%=indexname%>"><%=readableName%></option>
			<%}%>
		</select></p>
		<p>Keyword 2: <select name="search2" id="search2">
			<option value="None" selected>None</option>
			<% 
			  	keys=myShepherd.getAllKeywords();
			  	for(int m=0;m<totalKeywords;m++) {
					Keyword word=(Keyword)keys.next();
					String indexname=word.getIndexname();
					String readableName=word.getReadableName();
				%>
			<option value="<%=indexname%>"><%=readableName%></option>
			<%}%>
		</select></p>
		<p>Keyword 3: <select name="search3" id="search3">
			<option value="None" selected>None</option>
			<% 
			  	keys=myShepherd.getAllKeywords();
			  	for(int p=0;p<totalKeywords;p++) {
					Keyword word=(Keyword)keys.next();
					String indexname=word.getIndexname();
					String readableName=word.getReadableName();
				%>
			<option value="<%=indexname%>"><%=readableName%></option>
			<%}%>
		</select></p>
		<p>Location ID starts with : <input name="locationCode"
			type="text" id="locationCode" size="5" maxlength="5"></p>
		<p><input type="submit" name="Submit" value="Search"></p></form>
		</td>
	</tr>
</table>
<%
} catch(Exception e) {e.printStackTrace();}
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%> <jsp:include page="footer.jsp" flush="true" /></div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


