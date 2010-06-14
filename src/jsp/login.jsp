<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*"%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html"%>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic"%>

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
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));
	
	
	//load our variables for the submit page
	String title=props.getProperty("submit_title");
	String submit_maintext=props.getProperty("submit_maintext");
	String submit_reportit=props.getProperty("reportit");
	String submit_language=props.getProperty("language");
	String what_do=props.getProperty("what_do");
	String read_overview=props.getProperty("read_overview");
	String see_all_encounters=props.getProperty("see_all_encounters");
	String see_all_sharks=props.getProperty("see_all_sharks");
	String report_encounter=props.getProperty("report_encounter");
	String log_in=props.getProperty("log_in");
	String contact_us=props.getProperty("contact_us");
	String search=props.getProperty("search");
	String encounter=props.getProperty("encounter");
	String shark=props.getProperty("shark");
	String join_the_dots=props.getProperty("join_the_dots");
	String menu=props.getProperty("menu");
	String last_sightings=props.getProperty("last_sightings");
	String more=props.getProperty("more");
	String ws_info=props.getProperty("ws_info");
	String about=props.getProperty("about");
	String contributors=props.getProperty("contributors");
	String forum=props.getProperty("forum");
	String blog=props.getProperty("blog");
	String area=props.getProperty("area");
	String match=props.getProperty("match");
	
	//link path to submit page with appropriate language
	String submitPath="submit.jsp?langCode="+langCode;
	
	Shepherd myShepherd=new Shepherd();
	
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
<title>ECOCEAN - Login</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="The ECOCEAN Whale Shark Photo-identification Library is a visual database of whale shark (Rhincodon typus) encounters and of individually catalogued whale sharks. The library is maintained and used by marine biologists to collect and analyse whale shark encounter data to learn more about these amazing creatures." />
<meta name="Keywords"
	content="whale shark,whale,shark,Rhincodon typus,requin balleine,Rhineodon,Rhiniodon,big fish,ECOCEAN,Brad Norman, fish, coral, sharks, elasmobranch, mark, recapture, photo-identification, identification, conservation, citizen science" />
<meta name="Author" content="ECOCEAN - info@ecocean.org" />
<link
	href="http://<%=CommonConfiguration.getURLLocation() %>/css/ecocean.css"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="http://<%=CommonConfiguration.getURLLocation() %>/images/favicon.ico" />
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
<div id="maincol-wide">

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
<p align="center"><font size="-1">*If you have problems
logging in or would like to know more about gaining higher privileges,
please contact:<br />
<br />
<img
	src="http://<%=CommonConfiguration.getURLLocation() %>/images/webmaster.gif"
	width="228" height="18" /></font></p>
<p align="center"><font size="-1"></font></p>
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
