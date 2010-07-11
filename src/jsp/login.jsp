<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

<%


	String langCode="en";
	
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	
	//setup our Properties object to hold all properties
	//Properties props=new Properties();
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));
	

	
	//link path to submit page with appropriate language
	String submitPath="submit.jsp?langCode="+langCode;
	
%>

<html:html locale="true">

<!-- Make sure window is not in a frame -->

<script language="JavaScript" type="text/javascript">

  <!--
    if (window.self != window.top) {
      window.open(".", "_top");
    }
  // -->

</script>

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

<style type="text/css">
<!--
.style1 {
	color: #FF0000;
	font-weight: bold;
}
-->
</style>
</head>



<!-- Standard Content -->
<!-- Body -->
<body bgcolor="#FFFFFF" link="#990000">
<center><!-- Login -->

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
<div id="maincol-wide-solo">

<div id="maintext">

<h1 class="intro">Database login</h1>

<p align="center">You have requested a higher level of privileges*.
Please enter your user name and password below.</p>
<p>
<form method="POST"
	action='<%= response.encodeURL("j_security_check") %>' name="loginForm">
<table border="0" align="center" cellpadding="5" cellspacing="2">
	<tr align="left" valign="top">
		<th align="left"><font color="#000000">Username: <input type="text"
			name="j_username" size="16" maxlength="16" /></font></th>
	</tr>

	<tr align="left" valign="top">
		<th align="left"><font color="#0000">Password: <input
			type="password" name="j_password" size="16" maxlength="16" /></font></th>
	</tr>
	<!-- login reset buttons layout -->
	<tr align="left" valign="top">
		<td align="left">
		<div align="left">
		<input name="submit" type="submit" value='Login' /> <input name="reset" type="reset" value='Reset' />
		&nbsp;&nbsp; </div>
		</td>
	</tr>
</table>
</form>
</p>

<script language="JavaScript" type="text/javascript">
  <!--
    document.forms["loginForm"].elements["j_username"].focus()
  // -->
</script>

<p>&nbsp;</p>

</td>
</tr>
</table>

<p>&nbsp;</p>
</div>
<!-- end maintext --></div>
<!-- end maincol --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>


</html:html>
