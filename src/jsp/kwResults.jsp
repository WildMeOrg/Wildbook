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
<%

String locCodeQuery="";
if((request.getParameter("locationCode")!=null)&&(!request.getParameter("locationCode").equals(""))){locCodeQuery="_AMP_"+request.getParameter("locationCode");}

myShepherd.beginDBTransaction();
//System.out.println(request.getQueryString());



String pi_name="";
if((request.getParameter("primaryImageName")!=null)&&(!request.getParameter("primaryImageName").equals(""))) {
	pi_name=request.getParameter("primaryImageName")+"_AMP_";
}
else {
	pi_name="NONE_AMP_";
}
String processedRequest=pi_name+request.getParameter("search1")+"_AMP_"+request.getParameter("search2")+"_AMP_"+request.getParameter("search3")+locCodeQuery;
String earl="http://"+CommonConfiguration.getURLLocation()+"/imageSearch?queryString="+processedRequest.replaceAll("%20"," ").replaceAll("%2F","/").replaceAll("%2520"," ");
//System.out.println(earl);
%>
<h1 class="intro">
<table>
	<tr>
		<td><img src="../images/tag_big.gif" width="50" height="50"
			hspace="3" vspace="3" align="absmiddle" /> Photo Search Results</td>
	</tr>
</table>
</h1>
<p>You selected the following search criteria:<br> <%
int searchParams=1;
%>
<ul>
	<%
while((request.getParameter("search"+searchParams)!=null)&&(!request.getParameter("search"+searchParams).equals("None"))) {
	Keyword word=myShepherd.getKeyword(request.getParameter("search"+searchParams));
	String name=word.getReadableName();
	%>
	<li><%=name%></li>
	<%
	searchParams++;
}
%>
</ul>
</p>
<p>Drag and drop thumbnail photos at the bottom into one of the two
squares for viewing. Use the plus and minus magnifying glasses to zoom
in and out. Click and drag the image within either window to center the
feature that you are looking for. Click the blue links below either
window to open the appropriate encounter or full size photo in a new
browser window for more detailed inspection.</p>
<p><object classid="clsid:D27CDB6E-AE6D-11cf-96B8-444553540000"
	codebase="http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=6,0,29,0"
	width="550" height="400">
	<param name="movie"
		value="browsematch.swf?dataURL=<%=earl%>&rootURL=<%=CommonConfiguration.getURLLocation()%>">
	<param name="quality" value="high"><embed
		src="browsematch.swf?dataURL=<%=earl%>&rootURL=<%=CommonConfiguration.getURLLocation()%>"
		quality="high"
		pluginspage="http://www.macromedia.com/go/getflashplayer"
		type="application/x-shockwave-flash" width="550" height="400"></embed>
</object></p>
<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%> <jsp:include page="footer.jsp" flush="true" /></div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>


