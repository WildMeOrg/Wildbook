<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.GregorianCalendar, java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*"%>


<%

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);

Shepherd myShepherd=new Shepherd();

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 


//setup our Properties object to hold all properties
	//Properties props=new Properties();
	
	String langCode="en";
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	
	String locCodeParam = "";
	if(request.getParameter("locCode")!=null){
		locCodeParam = request.getParameter("locCode");
	}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/sharks.properties"));

	
	//load our variables for the submit page
	String title=props.getProperty("title");
	String maintext=props.getProperty("maintext");
	String reportit=props.getProperty("reportit");
	String language=props.getProperty("language");
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
	String contributors=props.getProperty("contributors");
	String about=props.getProperty("about");
	String forum=props.getProperty("forum");
	String blog=props.getProperty("blog");
	String area=props.getProperty("area");
	String match=props.getProperty("match");
	String name=request.getParameter("shark");

//set up our calendar limits
String locCode="NONE";
if((request.getParameter("locCode")!=null)&&(!request.getParameter("locCode").equals(""))) {
				locCode=request.getParameter("locCode");
}
int startYear=1995;
int endYear=nowYear;
int month=0;

//let's see if we have date info to work with
int limitYear1=startYear;
int limitYear2=endYear;
if(request.getParameter("scDate")!=null) {
	//System.out.println(request.getParameter("scDate"));
	String[] result = request.getParameter("scDate").split("/");
	//for(int q=0;q<result.length;q++) {
		//System.out.println(result[q]);
	//}
	if(result.length==3) {
		try{
			//System.out.println("Found the expected 3 split pieces.");
			month=(new Integer(result[0])).intValue();
			limitYear1=(new Integer(result[2])).intValue();
			limitYear2=(new Integer(result[2])).intValue();
		}
		catch (NumberFormatException nfe){}
	}
}



%>

<html>
<head>

<title>ECOCEAN - Encounter Calendar</title>
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
#maincol-calendar {
	float: left;
	width: 780px;
	overflow: hidden;
}
-->
</style>
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

<div id="maincol-calendar">

<div id="maintext">
<p>
<h1 class="intro">Encounter/Sighting Calendar</h1>
</p>
<p>
<center>
<form action="calendar.jsp" method="get"><input name="scDate"
	type="hidden" value="<%=request.getParameter("scDate")%>" /><a
	href="<%=CommonConfiguration.getWikiLocation()%>location_codes"
	target="_blank"><img src="../images/information_icon_svg.gif"
	alt="Help" width="15" height="15" border="0" align="absmiddle" /></a>
Filter results by location code: <input name="locCode" type="text"
	size="5" maxlength="5" value="<%=locCodeParam%>" /> &nbsp;<input
	name="locCodeFilter" type="submit" value="Filter" /></form>
</center>
</p>
<p><!-- scriptcalendar iframe tag -->
<table border="0" cellpadding="0" cellspacing="0">
	<tr>
		<td><!-- JAVASCRIPT COMPONENTS --> <!-- do not edit unless you have moved the component javascript files -->
		<style type="text/css" media="screen">
<!--
@import "scthemes/plain/scrptcal.css";
-->
</style>
		<style type="text/css" media="print">
<!--
@import "scthemes/plain/scprint.css";
-->
</style>
		<script language="javascript" src="sccomponents/scrptcal.js"></script>

		<script language="javascript" src="sccomponents/scspcevt.js"></script>
		<script language="javascript" src="scthemes/plain/schandlr.js"></script>


		<!-- SCRIPTCALENDAR OBJECT --> <!-- edit the properties as you see fit -->
		<script language="javascript">
	var objCal = new scriptcalendar();


	// license key
	objCal.license = new Array( "PMfQzJZFMw1ZTGHP" );

	objCal.xmlFile			= "/calendar?locCode=<%=locCode%>&startYear=<%=limitYear1%>&endYear=<%=limitYear2%>&month=<%=month%>";
	objCal.deadCellBehavior 	= 0;		// dead date cell behavior 1=date; 2=dead text; 4=prev/next text
	objCal.dateSelector 		= 1+2+4+8;	// date selectors
	objCal.prevHtml 		= "&laquo;";	// PREV month link text
	objCal.nextHtml 		= "&raquo;";	// NEXT month link text
	objCal.dateRangeStart 		= <%=startYear%>;		// start year range
	objCal.dateRangeEnd 		= <%=endYear%>;		// end year range
	
	objCal.cellWidth  		= 109;		// width of date cell
	objCal.cellHeight 		= 350;		// height of date cells 
	objCal.padding 			= "0";		// table cellpadding
	objCal.spacing 			= "2";		// table cellspacing

	objCal.beginMonday 		= false;	// begin week on Monday
	objCal.displayWeekNumber	= false;	// display Week Number
	objCal.showFutureEvents		= true;		// show future events
	objCal.showPastEvents		= true;		// show future events
	objCal.expandEventStyle		= true;		// allow event style to grow
	objCal.enableHandlers		= false;	// add event handlers

	objCal.popupWindow		= true;		// use a popup window or a DIV element
	objCal.popupAddParam		= false;		// append the date parameter to the popup window url
	objCal.popupProperties = "width=600,height=400,scrollbars=yes,resizable=yes,titlebar=yes,toolbar=yes,menubar=yes,location=yes,status=yes";

	objCal.initialize();
</script> <!-- POPUP DIV --> <!-- do not edit -->
		<div id="scDivPopup" name="scDivPopup" class="scPopup"
			onclick="this.style.display='none';">[close] <iframe
			id="scIfmPopup" name="scIfmPopup" border="0" scrolling="yes"
			width="500" height="400" src=""> </iframe></div>


		</td>
	</tr>
</table>

</p>
</div>
<!-- end maintext --></div>
<!-- end main-wide --> <jsp:include page="../footer.jsp" flush="true" />
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


