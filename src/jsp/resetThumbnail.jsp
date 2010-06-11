<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Vector, java.io.FileReader, java.io.BufferedReader, java.util.Properties, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,org.ecocean.*"%>
<%@ taglib uri="di" prefix="di"%>
<%
String number=request.getParameter("number");
int imageNum=1;
try{
	imageNum=(new Integer(request.getParameter("imageNum"))).intValue();
}
catch(Exception cce){}

//System.out.println(number);
//int number=(new Integer(num)).intValue();
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

<div id="maincol-wide">

<div id="maintext">
<%
		myShepherd.beginDBTransaction();
		Encounter enc=myShepherd.getEncounter(number);
		//Enumeration images=enc.getAdditionalImageNames().elements();
		String addText=(String)enc.getAdditionalImageNames().get((imageNum-1));	
		//String addText="";
		if(myShepherd.isAcceptableVideoFile(addText)){addText="images/video_thumb.jpg";}
		else{addText="encounters/"+request.getParameter("number")+"/"+addText;}
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		String thumbLocation="file-encounters/"+number+"/thumb.jpg";
		
		System.gc();
		//generate the thumbnail image
			%> <di:img width="100" height="75" border="0" fillPaint="#000000"
	output="<%=thumbLocation%>" expAfter="0" threading="limited"
	align="left" valign="left">
	<di:image width="100" height="*" srcurl="<%=addText%>" />
</di:img>
<h1 class="intro">Success</h1>
<p>I have successfully reset the thumbnail image for encounter
number <strong><%=number%></strong>!</p>
<p><a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=number%>">View
encounter #<%=number%></a>.</p>


</div>
<!-- end maintext --></div>
<!-- end maincol --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>