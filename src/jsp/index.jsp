<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.*,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, java.io.IOException"%>




<%

//grab a gridManager
GridManager gm=GridManagerFactory.getGridManager();
int numProcessors = gm.getNumProcessors();
int numWorkItems = gm.getIncompleteWork().size();

Shepherd myShepherd=new Shepherd();

//setup our Properties object to hold all properties
	
	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("en")) {langCode="en";}
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/overview.properties"));
	
	
	//load our variables for the overview page
	String title=props.getProperty("overview_title");
	String overview_maintext=props.getProperty("overview_maintext");
	String overview_reportit=props.getProperty("overview_reportit");
	String overview_language=props.getProperty("overview_language");
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

%>

<html>
<head>
<title><%=title%></title>
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

<link rel="shortcut icon" href="images/favicon.ico" />
<style type="text/css">
<!--

table.adopter {
	border-width: 1px 1px 1px 1px;
	border-spacing: 0px;
	border-style: solid solid solid solid;
	border-color: black black black black;
	border-collapse: separate;
	background-color: white;
}

table.adopter td {
	border-width: 1px 1px 1px 1px;
	padding: 3px 3px 3px 3px;
	border-style: none none none none;
	border-color: gray gray gray gray;
	background-color: white;
	-moz-border-radius: 0px 0px 0px 0px;
	font-size: 12px;
	color: #330099;
}

table.adopter td.name {
	font-size: 12px;
	text-align: center;
}

table.adopter td.image {
	padding: 0px 0px 0px 0px;
}

.style2 {
	font-size: x-small;
	color: #000000;
}
-->
</style>
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
<div id="leftcol">
<div id="menu">
<%
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	%> 



<div class="module">
<h3>Latest News</h3>
<span class="caption">Add your news items here... </span><span class="caption"><br />
</span> <br />
</div>

<div class="module">
<h3>Data Sharing</h3>
<span class="caption">If you are sharing data, this is a great place to let others know about it... </span>
<br />
</div>

</div>
<!-- end menu --></div>
<!-- end leftcol -->
<div id="maincol">

<div id="maintext">
<h1 class="intro">Overview</h1>
<p class="caption">This is a great place to present an overview description of this mark-recapture project and library...</p>
<br/>
</div>

<div>
<h1 class="intro">Data Contributors</h1>
<p class="caption">A great optional area to discuss who is contributing data to this library...</p>
</div>

<div id="maintext">
<h1 class="intro">Contact us</h1>
<p class="caption">A great place to talk about who is running this mark-recapture library...</p>
<p class="caption"><a href="contactus.jsp">Please contact us
with you questions.</a></p>
</div>



</div>
<!-- end maincol -->
<div id="rightcol">




 <div class="module">
		 	<h3>Find Record</h3>
		   
		 	<form name="form2" method="get" action="individuals.jsp">
		 	<em>Enter a marked animal number, encounter number, animal nickname, or alternate ID.</em><br/>
		 	<input name="number" type="text" id="shark" size="25" />
		 	<input type="hidden" name="langCode" value="<%=langCode%>" /><br/>
		 	<input name="Go" type="submit" id="Go2" value="Go" />
		 	</form>
			
		 </div>
		 
		 
<div class="module">
<h3>RSS/Atom Feeds</h3>
<p align="left"><a href="rss.xml"><img src="images/rssfeed.gif"
	width="80" height="15" border="0" alt="RSS News Feed" /></a></p>
<p align="left"><a href="atom.xml"><img
	src="images/atom-feed-icon.gif" border="0" alt="ATOM News Feed" /></a></p>
</div>






</div>
<!-- end rightcol --></div>
<!-- end main --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
