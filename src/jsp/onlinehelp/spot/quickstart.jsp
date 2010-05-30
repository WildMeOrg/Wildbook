<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Properties, javax.jdo.*,java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,java.util.Iterator"%>
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
	FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	props.load(propsInputStream);
	
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
	
%>

<html>
<head>
<title>ECOCEAN - Whale Shark Photo-identification Library
Sitemap</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="The ECOCEAN Whale Shark Photo-identification Library is a visual database of whale shark (Rhincodon typus) encounters and of individually catalogued whale sharks. The library is maintained and used by marine biologists to collect and analyse whale shark encounter data to learn more about these amazing creatures." />
<meta name="Keywords"
	content="whale shark,whale,shark,Rhincodon typus,requin balleine,Rhineodon,Rhiniodon,big fish,ECOCEAN,Brad Norman, fish, coral, sharks, elasmobranch, mark, recapture, photo-identification, identification, conservation, citizen science" />
<meta name="Author" content="ECOCEAN - info@ecocean.org" />
<link href="http://www.whaleshark.org/css/ecocean.css" rel="stylesheet"
	type="text/css" />
<link rel="alternate" type="application/rss+xml"
	title="Whaleshark.org feed" href="http://www.whaleshark.org/rss.xml" />
<link rel="shortcut icon"
	href="http://www.whaleshark.org/images/favicon.ico" />

</head>

<script type="text/javascript" src="spot_real_media/realembed.js"></script>

<body>
<div id="wrapper">
<div id="page"><jsp:include page="../../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<div id="main"><!-- end leftcol -->
<div id="maincol-calendar">

<div id="maintext">
<h1 class="intro">Spot! Quickstart Guide</h1>
</div>
<p>The following video demonstrates basic usage of <strong>Spot!</strong>.
The video requires the Free version of the <a
	href="http://www.real.com/">RealPlayer</a>. More information about
Spot! can be found <a
	href="http://www.whaleshark.org/onlinehelp/spot/index.jsp">here</a>.</p>
<p>
<table border="0" cellpadding="0"
	style="text-align: center; margin: auto" width="640" align="center">
	<tr>
		<td>
		<div id="media">
		<div id="cs_noJSorReal">
		<p>The Camtasia Studio video content presented here requires
		JavaScript to be enabled and the latest version of the Real Player. If
		you are you using a browser with JavaScript disabled please enable it
		now. Otherwise, please update your version of the free Real Player by
		<a href="http://www.real.com/player">downloading here</a>.</p>
		</div>
		</div>
		<div id="player"></div>
		<script type="text/javascript">
                if ( isRealInstalled() )
                {
                   var real = new RealEmbed ( "spot_real_media/spot_real.rm", "realplayer", "640", "480" );
                   real.addParam( "controls", "imagewindow" );
                   real.addParam( "console", "One" );
                   real.addParam( "autoStart", "true" );
                   real.write( "media" );
                   var con = new RealEmbed ( "spot_real_media/spot_real.rm", "realControls", "640", "36" );
                   con.addParam( "controls", "ControlPanel" );
                   con.addParam( "console", "One" );
                   con.addParam( "autoStart", "true" );
                   con.write( "player" );
                }
             </script></td>
	</tr>
</table>
</p>
</div>
</div><jsp:include page="../../footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>
