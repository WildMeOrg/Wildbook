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
				<img src="images/logo.gif" width="150" height="90" border="0" title="Ecocean" alt="www.whaleshark.org" />
				
			</div>
						
			
						
<jsp:include page="awards.jsp" flush="true" />	
		</div><!-- end menu -->
	</div><!-- end leftcol -->
	<div id="maincol-wide">

		<div id="maintext">
		  <h2>Why are we raising funds?</h2>
	</div>
			
			<p><strong>ECOCEAN works to generate new knowledge about whale sharks for use in justifying conservation action.</strong></p>
		<p><strong>What are whale sharks doing at Ningaloo Marine Park (NMP)?</strong></p>
			<ul>
			  <li>What’s going on when they are away from the surface (i.e. at depth)?</li>
			  <li>Are the feeding?</li>
			  <li>Are they mating?</li>
        </ul>
<p>Are whale sharks distributed elsewhere along the WA coast i.e. not just at NMP?</p>
			<ul>
			  <li>We know a couple of whale sharks have moved north, towards Indonesia. Is this a rarity – or the norm?</li>
			  <li>Recent sightings recorded at Kalbarri - even off Perth!</li>
			  <li>Many sighting reports received from near the oil and gas facilities in the NW Shelf.</li>
			  <li>What effect is this industry having on the whale shark movements?</li>
			  <li>Can we as ecotourists do more to minimise any impacts we have on these sharks?</li>
        </ul>
<p>It’s time to answer these questions – solve some of the mystery surrounding this threatened species – needed to ensure the long-term conservation of the whale shark.</p>
			<ul>
			  <li>We have the ability to do it at NMP….a location easily accessible.</li>
			  <li>We have the technology: Crittercam / Daily Diaries / Satellite and Acoustic tags etc.</li>
			  <li>We just need the resources – and we can achieve results.</li>
        </ul>
<div align="center"><p><img src="/images/raisingFunds1.png" width="480" height="359" /></p></div>
<h2>&nbsp;</h2>
<p><strong>ECOCEAN provides cutting edge software tools to similarly support mark-recapture efforts for other species.</strong></p>
<ul>
  <li>ECOCEAN works to apply its ‘best practices’, techniques, and tools to assist research for other species.</li>
  <li>ECOCEAN provides a suite of software tools to researchers around the world to assist with localized whale shark research efforts.</li>
  <li>The ECOCEAN research framework brings information technology, scientists, and citizen scientists together.</li>
</ul>
<p>&nbsp;</p>
<div align="center"><p><img src="/images/raisingFunds2.png" width="480" height="359" /></p></div>
<p>&nbsp;</p>
<p><strong>ECOCEAN is committed to marine conservation awareness through education and outreach campaigns throughout the world.</strong></p>
  <ul>
    <li>ECOCEAN works with school groups to promote conservation education.</li>
    <li>The ECOCEAN Whale Shark Photo-Identification Library currently houses whale shark images from 45 different countries!</li>
    <li>ECOCEAN produces educational materials in the form of brochures, pamphlets and through online campaigns to encourage a worldwide effort to protect the sharks of our seas.</li>
  </ul>
<p>&nbsp;</p>
<h2>WE NEED YOUR HELP!</h2>
<ul>
  <li>Public awareness is a key.</li>
  <li>Citizen scientists are helping to collect important sighting data throughout the Australian coast.</li>
  <li>Shark education continues via the ECOCEAN website.</li>
  <li>Community participation can ensure the conservation of the world’s biggest fish.</li>
</ul>
<div align="center"><p><img src="/images/raisingFunds3.png" width="480" height="333" /></p></div>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
