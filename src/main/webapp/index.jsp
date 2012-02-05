<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, java.io.IOException" %>




<%

//grab a gridManager
GridManager gm=GridManagerFactory.getGridManager();
int numProcessors = gm.getNumProcessors();
int numWorkItems = gm.getIncompleteWork().size();

Shepherd myShepherd=new Shepherd();

//setup our Properties object to hold all properties
	
	//test comment
	
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
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

<link rel="shortcut icon" href="images/favicon.ico" />
<style type="text/css">
<!--


table.adopter {
	border-width: 0px 0px 0px 0px;
	border-spacing: 0px;
	border-style: solid solid solid solid;
	border-color: black black black black;
	border-collapse: separate;
	
}

table.adopter td {
	border-width: 1px 1px 1px 1px;
	padding: 3px 3px 3px 3px;
	border-style: none none none none;
	border-color: gray gray gray gray;
	background-color: #D7E0ED;
	-moz-border-radius: 0px 0px 0px 0px;
	font-size: 12px;
	color: #330099;
}

table.adopter td.name {
	font-size: 12px;
	text-align:center;
	background-color: #D7E0ED;
	
}

table.adopter td.image {
	padding: 0px 0px 0px 0px;
	
}
.style4 {color: #000000}

-->
</style>
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
		  			<p><a href="finish.jsp"><img src="images/Finish_Finning_logo%202.gif" width="190" border="0" /></a><br>
		 			 <center><span class="caption style4"><strong><a href="finish.jsp">Click here to learn more!</a></strong></span>
		 			 </center></p>
		   </div>
		 
	<%
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	%>
<jsp:include page="language.jsp" flush="true">

	<jsp:param name="langCode" value="<%=langCode%>"/>

</jsp:include>
		


			 
			 		 	    <div class="module">
		 <h3>Latest News </h3>
<style>
/* begin styles for RSS Feed 
     This is the most basic style to use for a list with no bullets */



.rss-item  {

  margin-bottom: 1em;;
}


	} 
/* buttons modeled from http://www.wellstyled.com/css-inline-buttons.html */

.pod-play {
   
   margin: 0 0em; padding: 0em 0; _padding:0;
   
   white-space:nowrap;
   text-decoration: none;
   
   background: #fb6;
   color: black;
   }
.pod-play em {
   _width:0em; _cursor:hand;
   font-style: normal;
   margin:0; padding: 0em 0em;
   background: white;
   color: #222;
   }
.pod-play span {
   _width:0em; _cursor:hand;
   margin:0; padding: 0em 0em 0em 0em;
   }
.pod-play:hover {
   background: #666;
   color: white;
   }
.pod-play:hover em {
   background: black;
   color: white
   }


</style>

<script language="JavaScript" src="http://feed2js.org//feed2js.php?src=http%3A%2F%2Fecocean.wordpress.com%2Ffeed%2F&num=2&utf=y&html=a"  charset="UTF-8" type="text/javascript"></script>

<noscript>
<a href="http://feed2js.org//feed2js.php?src=http%3A%2F%2Fecocean.wordpress.com%2Ffeed%2F&num=2&utf=y&html=y">View RSS feed</a>
</noscript>

        </div>

								 
			 <div class="module">
			 <h3>Photographing</h3>
				 <a href="photographing.jsp?langCode=<%=langCode%>"><img src="images/area.jpg" width="190" height="115" border="0" title="Area to photograph" alt="Area to photograph on a whale shark" /></a>
				 <p class="caption"><a href="photographing.jsp?langCode=<%=langCode%>"><%=area%></a></p>
		   </div>
						 
			 <div class="module">
				 <a href="photographing.jsp?langCode=<%=langCode%>"><img src="images/match.jpg" width="190" height="94" border="0" title="We Have A Match!" alt="We Have A Whale Shark Match!" /></a>
				 <p class="caption"><a href="photographing.jsp?langCode=<%=langCode%>"><%=match%></a></p>
		   </div>
						<jsp:include page="awards.jsp" flush="true" /> 

		<div class="module">
    	<h3>Data Sharing</h3>
    	<p><a href="http://data.gbif.org/datasets/provider/261"><center><img src="images/gbif.gif" border="0" alt="Data sharing with the Global Biodiversity Information Facility"/></center></a></p>
		<p><a href="http://ecovision.mit.edu/~ecovision/"><center><img src="http://web.mit.edu/img/google/google-mithome-logo.gif" alt="Data sharing with the Massachusetts Institute of Technology" border="0" />
		</center></a></p>
		<p><a href="http://www.iobis.org/"><center><img src="images/OBIS_logo.gif" alt="Data sharing with the Ocean Biogeographic Information System" border="0" />
		</center></a></p>
		<p><a href="ttp://www.coml.org/"><center><img src="images/coml.gif" alt="Data sharing with the census of marine life" border="0" />
		</center></a></p>
	</div>
			
		</div><!-- end menu -->
	</div><!-- end leftcol -->
	<div id="maincol">
		 <div id="reportit">
		 <h3><a href="<%=submitPath%>"><%=overview_reportit%></a></h3>
		 <a href="<%=submitPath%>"><img src="images/filming_flip.jpg" title="Report a whale shark encounter!" alt="Report a whale shark encounter! - photograph courtesy of Wags &amp; Kelly" width="400" height="130" border="0" /></a>
		 </div>
	    <div id="maintext"><%=overview_maintext%>
				 
		 
		 		 <p>
		 		 <span style="margin: 0px 0px 1em 0px;" #invalid_attr_id="0px"><strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />35000+ photos collected<br />
		       <strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />15000+  sighting reports</strong><br />
		       <strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />3100+ whale sharks collaboratively tagged</strong><br />
			   
			   <span style="margin: 0px 0px 1em 0px;" #invalid_attr_id="0px"><strong><strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />2900+ data contributors</strong></strong></span><br />
		       <strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />365 research days/year</strong><br />
		       
			   <p align="center"><strong><a href="adoptashark.jsp">Please consider adopting a shark to support our mission!</a></strong></p>
		       </strong></span></p>
		 
	    </div>
		 
		 <div id="1000tagged">
		   <h1 class="intro">Growing Success </h1>
		   <p align="center"><img src="images/usablereportsgif.gif" width="390" height="352" /></p>

		 </div>

	 <div id="video"><h1 class="intro">Video</h1>
<object width="400" height="240"><param name="movie" value="http://www.youtube.com/v/1Fp4cBG18R4?fs=1&amp;hl=en_US"></param><param name="allowFullScreen" value="true"></param><param name="allowscriptaccess" value="always"></param><embed src="http://www.youtube.com/v/1Fp4cBG18R4?fs=1&amp;hl=en_US" type="application/x-shockwave-flash" allowscriptaccess="always" allowfullscreen="true" width="400" height="240"></embed></object>
       <p class="caption"><br>
	   Learn more about the work of ECOCEAN with National Geographic's CritterCam!
	   </p>
       <p class="caption"><a href="video.jsp"><img src="images/Crystal_Clear_app_camera.gif" width="40" height="33" border="0" align="middle" />&nbsp;Click here for additional whale shark videos.</a></p>
	</div>
		 <div>
		   <h1 class="intro">Data Contributors</h1>
			<p class="caption">Many individuals and organizations actively contribute and manage data in the ECOCEAN Library. We will be featuring them here soon. </p>
		</div>
		
		<div id="maintext"><h1 class="intro">Contact us</h1>
		<p class="caption">ECOCEAN USA is always hoping for opportunities to better tell the story of whale sharks and the growing body of research and discoveries made possible by dedicated scientists, volunteers, and the general public.</p>
		<p class="caption"><a href="contactus.jsp">Please contact us with you questions.</a></p>
	</div>
		
		
		
	  </div><!-- end maincol -->
	 <div id="rightcol">



			 <div class="module">
				
				
<%
Adoption ad=myShepherd.getRandomAdoption();
if(ad!=null){
%>
<table class="adopter" bgcolor="#D7E0ED" style="background-color:#D7E0Ed " width="190px">
<tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="images/adoption-button-top.gif" /></a></td></tr>
<tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="images/meet-adopter-frame.gif" /></a></td></tr>
			 <tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="/<%=CommonConfiguration.getDataDirectoryName() %>/adoptions/<%=ad.getID()%>/thumb.jpg" /></a></td></tr>
			 
			 <tr><td class="name">
			 	<center><strong><font color="#282460" size="+1"><%=ad.getAdopterName()%></font></strong></center>
			 </td></tr>
		<tr><td>&nbsp;</td></tr>
		<tr><td><table cellpadding="1" border="0"><tr><td>Adopted shark: 
		
		<%
		if((ad.getMarkedIndividual()!=null)&&(!ad.getMarkedIndividual().equals(""))) {
		%>
		<a href="individuals.jsp?number=<%=ad.getMarkedIndividual()%>"><%=ad.getMarkedIndividual()%></a>
		<%
		}
		else if((ad.getEncounter()!=null)&&(!ad.getEncounter().equals(""))) {
		%>
		<a href="encounters/encounter.jsp?number=<%=ad.getEncounter()%>"><%=ad.getEncounter()%></a>
		<%
		}
		%>
		
		</td></tr></table></td></tr>
		<tr><td>&nbsp;</td></tr>
		
		<%
			if((ad.getAdopterQuote()!=null)&&(!ad.getAdopterQuote().equals(""))){
		%>
		
			 <tr><td><table cellpadding="1" border="0"><tr><td>Why are shark research and conservation important?</td></tr></table></td></tr>
			 <tr><td><table cellpadding="1" border="0"><tr><td><em>"<%=ad.getAdopterQuote()%>"</em></td></tr></table></td></tr>
			 <tr><td>&nbsp;</td></tr>
			 <tr><td><table cellpadding="1" border="0"><tr><td><span class="caption">Your adoption gift to ECOCEAN USA is tax deductible in the United States. <a href="adoptashark.jsp">Click here to learn more.</a></span></td></tr></table></td></tr>
			 
			 <%
			 }
			 %>
			
			 <tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="images/meet-adopter-frame-bottom.gif" /></a></td></tr>
			 
			 
			 </tr>

		  </table>
<%
}
%>


</div>

		<div class="module">
		<%
			String status = "diving";
			if(numWorkItems>0){status = "swimming";}
		%>
    	<span class="caption"><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/wiki/doku.php?id=how_to_join_sharkgrid"><img src="images/sharkGrid_small.gif" border="0" alt="sharkGrid"/></a><br />
    	sharkGrid is: <%=status%><br> 
    	<%if(numProcessors<8){ %>
		Processors: 120 on demand<br>
		<%} else { %>
		Processors: <%=numProcessors%><br>
		<%
		}
		%>
		<br />
		<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/wiki/doku.php?id=how_to_join_sharkgrid">Is your computer unused part of the day? Click here to learn how to help our research!</a></span>
		 <br />
		</div>

				 <div class="module">
			 <h3>In the Press</h3>
			 			 <table align="left" cellpadding="2">
			   <tr><td align="left" valign="top"><a href="http://www.bbc.co.uk/oceans/locations/spiceislands/mafia_whalesharks.shtml"><img src="images/BBC_logo.jpg" alt="ECOCEAN Library in BBC Oceans TV series" width="75" height="61" border="1" align="top" title="BBC Oceans"/></a></td>
		   <td><span class="caption"> The ECOCEAN Library was used in support of the <a href="http://www.bbc.co.uk/oceans/locations/spiceislands/mafia_whalesharks.shtml">BBC Oceans</a> television series. </span></td>
			   </tr></table>
			   <br>
			 
			 <table align="left" cellpadding="2">
			   <tr><td align="left" valign="top"><a href="http://www.smithsonianmag.com/science-nature/wild-things-200803.html"><img src="images/NationalGeographic_logo.jpg" width="75" height="75" border="1" align="top" title="National Geographic November 2007"/></a></td>
		   <td><span class="caption"> Tracking whale sharks and polar bears in <a href="http://news.nationalgeographic.com/news/2008/08/080825-whale-sharks-missions.html">National Geographic News</a>. </span></td>
			   </tr></table>
			 <br />
			 <br />
		 </div>
		 
		 <div class="module">
		 	<h3>Find Record</h3>
		   
		 	<form name="form2" method="get" action="individuals.jsp">
		 	<em>Enter a shark number, encounter number, shark nickname, or alternate ID.</em><br/>
		 	<input name="number" type="text" id="shark" size="25" />
		 	<input type="hidden" name="langCode" value="<%=langCode%>" /><br/>
		 	<input name="Go" type="submit" id="Go2" value="Go" />
		 	</form>
			
		 </div>
	    <div class="module">
			 <h3><%=last_sightings%></h3>
			 <script language="JavaScript" src="http://feed2js.org//feed2js.php?src=http%3A%2F%2F<%=CommonConfiguration.getURLLocation(request)%>%2Frss.xml&amp;utf=y" type="text/javascript"></script>
			 <noscript>
			 <a href="http://feed2js.org//feed2js.php?src=http%3A%2F%2F<%=CommonConfiguration.getURLLocation(request)%>%2Frss.xml&amp;utf=y&amp;html=y">View RSS feed</a>
			 </noscript>
			 <p align="left"><a href="rss.xml"><img src="images/rssfeed.gif" width="80" height="15" border="0" alt="Whaleshark.org RSS News Feed" /></a></p>
			 <p align="left">
			 <a href="atom.xml"><img src="images/atom-feed-icon.gif" border="0" alt="ATOM News Feed" /></a></p>
	    </div>
		 	<div class="module">
    	<h3>In-kind Supporters</h3>
    	<p><a href="http://www.murdoch.edu.au/"><center><img src="images/Murdoch_land_RGB_small.gif" border="0" /></center></a></p>
		<p><a href="http://www.google.com/"><center><img src="http://www.google.com/logos/Logo_40wht.gif" border="0" /></center></a></p>
		<p><center>
		  <a href="http://www.olympus.com"><img src="images/olympusblack.gif" width="150" height="33" border="0" /></a>
		</center></p>
				<p><center>
				  <a href="http://www.pny.com"><img src="images/PNYlogoBlk_registration%20copy.gif" width="150" height="35" border="0" /></a>
		                </center></p>
	</div>
 
	

	

	
	</div><!-- end rightcol -->
	
</div><!-- end main -->
<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
<script type='text/javascript'>
// Conversion Name: ACC_Ecocean_Landing
// INSTRUCTIONS 
// The Conversion Tags should be placed at the top of the <BODY> section of the HTML page. 
// In case you want to ensure that the full page loads as a prerequisite for a conversion 
// being recorded, place the tag at the bottom of the page. Note, however, that this may 
// skew the data in the case of slow-loading pages and in general not recommended. 
//
// NOTE: It is possible to test if the tags are working correctly before campaign launch 
// as follows:  Browse to http://bs.serving-sys.com/BurstingPipe/adServer.bs?cn=at, which is 
// a page that lets you set your local machine to 'testing' mode.  In this mode, when 
// visiting a page that includes an conversion tag, a new window will open, showing you 
// the data sent by the conversion tag to the MediaMind servers. 
// 
// END of instructions (These instruction lines can be deleted from the actual HTML)
var ebRand = Math.random()+'';
ebRand = ebRand * 1000000;
//<![CDATA[ 
document.write('<scr'+'ipt src="HTTP://bs.serving-sys.com/BurstingPipe/ActivityServer.bs?cn=as&amp;ActivityID=104657&amp;rnd=' + ebRand + '"></scr' + 'ipt>');
//]]>
</script>
<noscript>
<img width="1" height="1" style="border:0" src="HTTP://bs.serving-sys.com/BurstingPipe/ActivityServer.bs?cn=as&amp;ActivityID=104657&amp;ns=1"/>
</noscript>

</body>
</html>
