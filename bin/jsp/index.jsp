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
.style1 {
	font-weight: bold
}

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
	%> <jsp:include page="language.jsp" flush="true">

	<jsp:param name="langCode" value="<%=langCode%>" />

</jsp:include>



<div class="module">
<h3>Latest News</h3>

<span class="caption"><strong>A new look at Ningaloo
whale sharks<br />
</strong><br> <a
	href="http://www.int-res.com/articles/esr2009/7/n007p039.pdf"><img
	src="images/cov-esr.jpg" alt="Endangered Species Research publication"
	width="76" hspace="3" vspace="3" border="0" align="left"
	title="Latest scientific publication" /></a> ECOCEAN looks at <a
	href="http://www.int-res.com/articles/esr2009/7/n007p039.pdf">abundance,
length, and residency time</a> for whale sharks at Ningaloo Marine Park in
Western Australia. The results are published in <a
	href="http://www.int-res.com/articles/esr2009/7/n007p039.pdf"><em>Endangered
Species Research</em></a>. <br />
</span> <br />
<span class="caption"><strong>Maneuvering "like fighter
pilots"</strong><br />
<br> <a
	href="http://www.esajournals.org/perlserv/?request=get-abstract&doi=10.1890%2F07-0315.1"><img
	src="images/news_flying.jpg" alt="Ecological Applications publication"
	width="77" height="77" hspace="3" vspace="3" border="0" align="left"
	title="Latest scientific publication" /></a> Rolex Laureates and ECOCEAN
take <a href="http://www.sciencealert.com.au/news/20081706-17506-2.html">a
new look at whale shark motion</a>. <br />
</span> <br />
</div>


<div class="module">
<h3>Photographing</h3>
<a href="photographing.jsp?langCode=<%=langCode%>"><img
	src="images/area.jpg" width="190" height="115" border="0"
	title="Area to photograph" alt="Area to photograph on a whale shark" /></a>
<p class="caption"><a
	href="photographing.jsp?langCode=<%=langCode%>"><%=area%></a></p>
</div>

<div class="module"><a
	href="photographing.jsp?langCode=<%=langCode%>"><img
	src="images/match.jpg" width="190" height="94" border="0"
	title="We Have A Match!" alt="We Have A Whale Shark Match!" /></a>
<p class="caption"><a
	href="photographing.jsp?langCode=<%=langCode%>"><%=match%></a></p>
</div>
<jsp:include page="awards.jsp" flush="true" />

<div class="module">
<h3>Data Sharing</h3>
<p><a href="http://data.gbif.org/datasets/provider/261">
<center><img src="images/gbif.gif" border="0"
	alt="Data sharing with the Global Biodiversity Information Facility" /></center>
</a></p>
<p><a href="http://ecovision.mit.edu/~ecovision/">
<center><img
	src="http://web.mit.edu/img/google/google-mithome-logo.gif"
	alt="Data sharing with the Massachusetts Institute of Technology"
	border="0" /></center>
</a></p>
<p><a href="http://www.iobis.org/">
<center><img src="images/OBIS_logo.gif"
	alt="Data sharing with the Ocean Biogeographic Information System"
	border="0" /></center>
</a></p>
<p><a href="http://www.coml.org/">
<center><img src="images/coml.gif"
	alt="Data sharing with the census of marine life" border="0" /></center>
</a></p>
</div>

</div>
<!-- end menu --></div>
<!-- end leftcol -->
<div id="maincol">
<div id="reportit">
<h3><a href="<%=submitPath%>"><%=overview_reportit%></a></h3>
<a href="<%=submitPath%>"><img src="images/filming_flip.jpg"
	title="Report a whale shark encounter!"
	alt="Report a whale shark encounter! - photograph courtesy of Wags &amp; Kelly"
	width="400" height="130" border="0" /></a></div>
<div id="maintext"><%=overview_maintext%>


<p><span style="margin: 0px 0px 1em 0px;" #invalid_attr_id="0px"><strong><img
	src="images/markedindividual_small.gif" width="13" align="absmiddle" />25000+
photos collected<br />
<strong><img src="images/markedindividual_small.gif" width="13"
	align="absmiddle" />9600+ whale shark reports</strong><br />
<span style="margin: 0px 0px 1em 0px;" #invalid_attr_id="0px"><strong><strong><img
	src="images/markedindividual_small.gif" width="13" align="absmiddle" />2100+ data
contributors</strong></strong></span><br />
<strong><img src="images/markedindividual_small.gif" width="13"
	align="absmiddle" />2000+ whale sharks collaboratively tagged</strong><br />
<strong><img src="images/markedindividual_small.gif" width="13"
	align="absmiddle" />365 research days/year</strong><br />

<p align="center"><strong><a href="adoptashark.jsp">Please
consider adopting a shark to support our mission!</a></strong></p>
</strong></span></p>

</div>

<div id="1000tagged">
<h1 class="intro">Growing Success</h1>
<p align="center"><img src="images/usablereportsgif.gif" width="390"
	height="352" /></p>

</div>

<div id="video">
<h1 class="intro">Video</h1>
<img src="images/catalyst_video.jpg" width="402" height="226" />
<p class="caption"><br> Dinosaurs, the Hubble Space Telescope,
and whale sharks. What do these three elements have in common? Click a
link below to watch ABC's Catalyst video to find out more about the
technology used in the ECOCEAN Library!
</p>
<p class="caption"><a
	href="http://www.abc.net.au/science/broadband/catalyst/asx/WhaleShark_Ep39_hi.asx">Windows
Media</a> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a
	href="http://www.abc.net.au/science/broadband/catalyst/ram/WhaleShark_Ep39_hi.ram">RealPlayer</a>
&nbsp;&nbsp;&nbsp;&nbsp;<a
	href="http://www.abc.net.au/catalyst/stories/s2084913.htm">&nbsp;Transcript</a></p>
<p class="caption"><a href="video.jsp"><img
	src="images/Crystal_Clear_app_camera.gif" width="40" height="33"
	border="0" align="middle" />&nbsp;Click here for additional whale
shark videos.</a></p>
</div>
<div>
<h1 class="intro">Data Contributors</h1>
<p class="caption">Many individuals and organizations actively
contribute and manage data in the ECOCEAN Library. We will be featuring
them here soon.</p>
</div>

<div id="maintext">
<h1 class="intro">Contact us</h1>
<p class="caption">ECOCEAN USA is always hoping for opportunities to
better tell the story of whale sharks and the growing body of research
and discoveries made possible by dedicated scientists, volunteers, and
the general public.</p>
<p class="caption"><a href="contactus.jsp">Please contact us
with you questions.</a></p>
</div>



</div>
<!-- end maincol -->
<div id="rightcol">

<div class="module">
<%
			String status = "diving";
			if(numWorkItems>0){status = "swimming";}
		%> <span class="caption"><a
	href="http://<%=CommonConfiguration.getURLLocation()%>/wiki/doku.php?id=how_to_join_sharkgrid"><img
	src="images/sharkGrid_small.gif" border="0" alt="sharkGrid" /></a><br />
sharkGrid is: <%=status%><br> Processors: <%=numProcessors%><br>
<br />
<a
	href="http://<%=CommonConfiguration.getURLLocation()%>/wiki/doku.php?id=how_to_join_sharkgrid">Is
your computer unused part of the day? Click here to learn how to help
our research!</a>
</span> <br />
</div>

<div class="module">
<h3>Adopt a Shark</h3>

<span class="caption"><a href="adoptashark.jsp">Adopt a shark
to support our research and make a public statement for shark
conservation!</a></span><br> <br> <%
Adoption ad=myShepherd.getRandomAdoption();
if(ad!=null){
%>
<table class="adopter">
	<tr>
		<td class="image"><img src="adoptions/<%=ad.getID()%>/thumb.jpg" /></td>
	</tr>

	<tr>
		<td class="name">
		<table>
			<tr>
				<td><img src="images/adoption.gif" align="absmiddle" />
				<td><strong><%=ad.getAdopterName()%></strong></td>
			</tr>
		</table>
		</td>
	</tr>

	<tr>
		<td>Adopted shark: <%
		if((ad.getMarkedIndividual()!=null)&&(!ad.getMarkedIndividual().equals(""))) {
		%> <a href="individuals.jsp?number=<%=ad.getMarkedIndividual()%>"><%=ad.getMarkedIndividual()%></a>
		<%
		}
		else if((ad.getEncounter()!=null)&&(!ad.getEncounter().equals(""))) {
		%> <a href="encounters/encounter.jsp?number=<%=ad.getEncounter()%>"><%=ad.getEncounter()%></a>
		<%
		}
		%>
		</td>
	</tr>
	<tr>
		<td>&nbsp;</td>
	</tr>

	<%
			if((ad.getAdopterQuote()!=null)&&(!ad.getAdopterQuote().equals(""))){
		%>

	<tr>
		<td>Why are shark research and conservation important?</td>
	</tr>
	<tr>
		<td><em>"<%=ad.getAdopterQuote()%>"</em></td>
	</tr>

	<%
			 }
			 %>
	<tr>
		<td><span class="style2">Refresh this page to see other
		adopters chosen at random.</span></td>
	</tr>

</table>
<%
}
%> <br> <span class="caption">Your adoption gift to ECOCEAN
USA is tax deductible in the United States. <a href="adoptashark.jsp">Click
here to learn more.</a></span>

<p class="para"></p>
</div>

<div class="module">
<h3>In the Press</h3>
<table align="left" cellpadding="2">
	<tr>
		<td align="left" valign="top"><a
			href="http://www.bbc.co.uk/oceans/locations/spiceislands/mafia_whalesharks.shtml"><img
			src="images/BBC_logo.jpg"
			alt="ECOCEAN Library in BBC Oceans TV series" width="75" height="61"
			border="1" align="top" title="BBC Oceans" /></a></td>
		<td><span class="caption"> The ECOCEAN Library was used in
		support of the <a
			href="http://www.bbc.co.uk/oceans/locations/spiceislands/mafia_whalesharks.shtml">BBC
		Oceans</a> television series. </span></td>
	</tr>
</table>
<br>

<table align="left" cellpadding="2">
	<tr>
		<td align="left" valign="top"><a
			href="http://www.smithsonianmag.com/science-nature/wild-things-200803.html"><img
			src="images/NationalGeographic_logo.jpg" width="75" height="75"
			border="1" align="top" title="National Geographic November 2007" /></a></td>
		<td><span class="caption"> Tracking whale sharks and polar
		bears in <a
			href="http://news.nationalgeographic.com/news/2008/08/080825-whale-sharks-missions.html">National
		Geographic News</a>. </span></td>
	</tr>
</table>
<br />
<br />
</div>

<div class="module">
<h3>Find Record</h3>

<form name="form2" method="get" action="individuals.jsp"><em>Encounter
a shark number, encounter number, shark nickname, or alternate ID.</em><br />
<input name="shark" type="text" id="shark" size="25" /> <input
	type="hidden" name="langCode" value="<%=langCode%>" /><br />
<input name="Go" type="submit" id="Go2" value="Go" /></form>

</div>
<div class="module">
<h3><%=last_sightings%></h3>
<script language="JavaScript"
	src="http://feed2js.org//feed2js.php?src=http%3A%2F%2F<%=CommonConfiguration.getURLLocation()%>%2Frss.xml&amp;utf=y"
	type="text/javascript"></script>
<noscript><a
	href="http://feed2js.org//feed2js.php?src=http%3A%2F%2F<%=CommonConfiguration.getURLLocation()%>%2Frss.xml&amp;utf=y&amp;html=y">View
RSS feed</a></noscript>
<p align="left"><a href="rss.xml"><img src="images/rssfeed.gif"
	width="80" height="15" border="0" alt="Whaleshark.org RSS News Feed" /></a></p>
<p align="left"><a href="atom.xml"><img
	src="images/atom-feed-icon.gif" border="0" alt="ATOM News Feed" /></a></p>
</div>
<div class="module">
<h3>In-kind Supporters</h3>
<p><a href="http://www.murdoch.edu.au/">
<center><img src="images/Murdoch_land_RGB_small.gif" border="0" /></center>
</a></p>
<p><a href="http://www.google.com/">
<center><img src="http://www.google.com/logos/Logo_40wht.gif"
	border="0" /></center>
</a></p>
<p>
<center><a href="http://www.olympus.com"><img
	src="images/olympusblack.gif" width="150" height="33" border="0" /></a></center>
</p>
<p>
<center><a href="http://www.pny.com"><img
	src="images/PNYlogoBlk_registration%20copy.gif" width="150" height="35"
	border="0" /></a></center>
</p>
</div>






</div>
<!-- end rightcol --></div>
<!-- end main --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
