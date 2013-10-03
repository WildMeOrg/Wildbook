<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
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
	props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
	
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
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />


</head>

<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher" value="<%=request.isUserInRole(\"researcher\")%>"/>
	<jsp:param name="isManager" value="<%=request.isUserInRole(\"manager\")%>"/>
	<jsp:param name="isReviewer" value="<%=request.isUserInRole(\"reviewer\")%>"/>
	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>	
<div id="main">
	<div id="leftcol">
		<div id="menu">
						
			<div class="module">
				<img src="images/area.jpg" width="190" height="115" border="0" title="Area to photograph" alt="Area to photograph" />
				<p class="caption"><%=area%></p>
			</div>
						
			<div class="module">
				<img src="images/match.jpg" width="190" height="94" border="0" title="We Have A Match!" alt="We Have A Match!" />
				<p class="caption"><%=match%></p>
			</div>
						

<jsp:include page="awards.jsp" flush="true" />
		</div><!-- end menu -->
	</div><!-- end leftcol -->
	<div id="maincol-wide">

		<div id="maintext">
		  <h1 class="intro"><img src="video_camera.gif" width="42" height="42" border="0" align="middle" />&nbsp;Whale Shark Video</h1>
		</div>
			<p>The following videos provide additional information about whale sharks and whale shark-related research. </p>
			<ul>
			  <li><strong><a href="#general">General videos</a> (ECOCEAN, whale sharks, etc.) </strong></li>
			  <li><strong><a href="#tutorials">User tutorials</a> (Using the Wildbook for Whale Sharks) </strong></li>
	    </ul>
			<h3 class="contentheading"><strong><a name="general" id="general"></a>General videos</strong></h3>
			<p><strong>ECOCEAN and National Geographic's CritterCam </strong></p>
			<object width="640" height="385"><param name="movie" value="http://www.youtube.com/v/1Fp4cBG18R4?fs=1&amp;hl=en_US"></param><param name="allowFullScreen" value="true"></param><param name="allowscriptaccess" value="always"></param><embed src="http://www.youtube.com/v/1Fp4cBG18R4?fs=1&amp;hl=en_US" type="application/x-shockwave-flash" allowscriptaccess="always" allowfullscreen="true" width="640" height="385"></embed></object>
			<p><strong>ECOCEAN: Brad Norman and the Rolex Awards</strong></p>
			<p>		<img style="visibility:hidden;width:0px;height:0px;" border=0 width=0 height=0 src="http://counters.gigya.com/wildfire/IMP/CXNID=2000002.0NXC/bT*xJmx*PTEyNDYyOTU2NDU4NDMmcHQ9MTI*NjI5NTY1NjMxMiZwPTE5ODY4MSZkPTJvZjdjNGlwNmkmZz*yJnQ9Jm89YWRiYTg*OTFjYzUwNDgzMTg*MWIyZDEyNzQ2NDRiNTEmb2Y9MA==.gif" /><object name="kaltura_player_1246295646" id="kaltura_player_1246295646" type="application/x-shockwave-flash" allowScriptAccess="always" allowNetworking="all" allowFullScreen="true" height="364" width="400" data="http://www.kaltura.com/index.php/kwidget/wid/8uu4h93c44/uiconf_id/67100">
  <param name="allowScriptAccess" value="always"/>
  <param name="allowNetworking" value="all"/>
  <param name="allowFullScreen" value="true"/>
  <param name="bgcolor" value="#000000"/>
  <param name="movie" value="http://www.kaltura.com/index.php/kwidget/wid/8uu4h93c44/uiconf_id/67100"/>
  <param name="flashVars" value=""/>
  <param name="wmode" value="opaque"/>
  <a href="http://corp.kaltura.com">video platform</a>
  <a href="http://corp.kaltura.com/technology/video_management">video management</a>
  <a href="http://corp.kaltura.com/solutions/overview">video solutions</a>
  <a href="http://corp.kaltura.com/technology/video_player">free video player</a>
</object></p>
		<p class="caption"><br>Video provided by courtesy of the <a href="http://www.rolexawards.com">Rolex Awards for Enterprise</a>.</p>
		<p>&nbsp;</p>
			<p><strong>National Geographic: Whale Shark</strong></p>
			<p><object width="425" height="350"><param name="movie" value="http://www.youtube.com/v/uQrBwN39LJI"></param><param name="wmode" value="transparent"></param><embed src="http://www.youtube.com/v/uQrBwN39LJI" type="application/x-shockwave-flash" wmode="transparent" width="425" height="350"></embed></object></p>
			<p>&nbsp;</p>
			<p><strong>ECOCEAN: Earthwatch Volunteers Collecting Data</strong></p>
			<p><img style="visibility:hidden;width:0px;height:0px;" border=0 width=0 height=0 src="http://counters.gigya.com/wildfire/IMP/CXNID=2000002.0NXC/bT*xJmx*PTEyNDYyOTU4MjE*NjgmcHQ9MTI*NjI5NTgyNzY3MSZwPTE5ODY4MSZkPXh*ZGgyYmpseGwmZz*yJnQ9Jm89YWRiYTg*OTFjYzUwNDgzMTg*MWIyZDEyNzQ2NDRiNTEmb2Y9MA==.gif" /><object name="kaltura_player_1246295824" id="kaltura_player_1246295824" type="application/x-shockwave-flash" allowScriptAccess="always" allowNetworking="all" allowFullScreen="true" height="364" width="400" data="http://www.kaltura.com/index.php/kwidget/wid/1uulyvts0w/uiconf_id/67100">
  <param name="allowScriptAccess" value="always"/>
  <param name="allowNetworking" value="all"/>
  <param name="allowFullScreen" value="true"/>
  <param name="bgcolor" value="#000000"/>
  <param name="movie" value="http://www.kaltura.com/index.php/kwidget/wid/1uulyvts0w/uiconf_id/67100"/>
  <param name="flashVars" value=""/>
  <param name="wmode" value="opaque"/>
  <a href="http://corp.kaltura.com">video platform</a>
  <a href="http://corp.kaltura.com/technology/video_management">video management</a>
  <a href="http://corp.kaltura.com/solutions/overview">video solutions</a>
  <a href="http://corp.kaltura.com/technology/video_player">free video player</a>
</object></p>
		<p class="caption"><br>
	    Video courtesy of Wags & Kelly 2006 - <a href="http://www.hdvunderwater.com">www.hdvunderwater.com</a>. All rights reserved.</p>
			<p class="contentheading"><a name="tutorials" id="tutorials"></a>User tutorials </p>
			<ul>
			  <li><a href="video/reportingencounters/ReportingAnEncounter.htm">Reporting a whale shark encounter</a></li>
	    </ul>
			<p><a href="video/reportingencounters/ReportingAnEncounter.htm"><img src="images/tutorial1.gif" border="1" /></a></p>
			<ul>
			  <li><a href="video/approvingencounters/ApprovingAnEncounter.htm">Reviewing and approving an encounter</a></li>
	    </ul>
			<p><a href="video/approvingencounters/ApprovingAnEncounter.htm"><img src="images/tutorial2.gif" width="521" height="378" border="1" /></a></p>
			<ul>
			  <li><a href="video/usingpaintnet/Using%20Paint.NET.htm">Processing an image for pattern recognition</a></li>
	    </ul>
			<p><a href="video/usingpaintnet/Using%20Paint.NET.htm"><img src="images/tutorial3.gif" width="578" height="381" border="1" /></a></p>
			<ul>
			  <li><a href="video/mappingspots/MappingSpots.htm">Mapping spots</a></li>
	    </ul>
			<p><a href="video/mappingspots/MappingSpots.htm"><img src="images/tutorial4.gif" width="521" height="378" border="1" /></a></p>
			<ul>
			  <li><a href="video/scanningformatches/ScanningForAMatch.htm">Scanning for matches</a></li>
	    </ul>
			<p><a href="video/scanningformatches/ScanningForAMatch.htm"><img src="images/tutorial5.gif" width="566" height="481" border="1" /></a></p>
			<ul>
			  <li><a href="video/spot/Spot/Spot.htm">Using Spot! for 3D perspective correction </a></li>
	    </ul>
			<p><a href="video/spot/Spot/Spot.htm"><img src="images/tutorial6.gif" border="1" /></a></p>
			<p>&nbsp;</p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->
  <jsp:include page="footer.jsp" flush="true" />  
</div>
<!-- end page -->
</div><!--end wrapper -->
</body>
</html>
