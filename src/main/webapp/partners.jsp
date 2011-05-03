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
				<img src="images/logo.gif" width="190" height="115" border="0" title="Ecocean" alt="www.whaleshark.org" />
				
			</div>
						
			
			<!-- awards script here -->			

		</div><!-- end menu -->
	</div><!-- end leftcol -->
	<div id="maincol-wide">

		<div id="maintext">
		  <h2>Partner Organisations</h2>
		</div>
			
<p>&nbsp;</p>
<div>
  <p><a href="http://www.maldiveswhalesharkresearch.org" target="_blank"><img width="132" height="83" src="/images/maldivesProgramme.jpg" alt="Maldives Whale Shark Research Programme" /></a></p>
</div>
<p><strong><u>Maldives Whale Shark Research Programme</u></strong></p>
<p>Researcher: <strong>Richard Rees<br />
</strong>South Ari Atoll, Maldives</p>
<p><a href="http://www.maldiveswhalesharkresearch.org" target="_blank">www.maldiveswhalesharkresearch.org</a> <br />
  The Maldives Whale Shark Research Programme (MWSRP) is a UK registered charity that exists to conduct whale shark research and foster community focused conservation initiatives throughout the Maldives and the Indian Ocean.</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<div>
  <p><a href="http://www.domino.conanp.gob.mx" target="_blank"><img width="132" height="85" src="/images/proyectoDomino.jpg" alt="Proyecto Domino" /></a></p>
</div>
<p><strong><u>Proyecto Domino</u></strong></p>
<p>Researcher: <strong>Rafael de la Parra<br />
</strong>Cancun, Mexico</p>
<p><a href="http://www.domino.conanp.gob.mx" target="_blank">www.domino.conanp.gob.mx<br />
</a>Proyecto Domino was established in 2003 as the &quot;Mexican Atlantic Whale Shark Study, Conservation and Management&quot; in response to the development of a sustainable whale shark tourism industry. Tour operators, authorities, researchers and social organizations now collaborate in an effort to better understand the whale shark aggregation that takes place in the Mexican Caribbean and Gulf each year between May and September.</p>
<p>&nbsp;</p>
<p>&nbsp; </p>
<div>
  <p><a href="http://www.marinemegafauna.org" target="_blank"><img width="90" height="83" src="/images/megaFauna.jpg" alt="The Foundation for the Protection of Marine Megafauna" /></a></p>
</div>
<p><strong><u>The Foundation for the Protection of Marine Megafauna</u></strong></p>
<p>Researcher: <strong>Dr. Simon Pierce<br />
</strong>Tofo Beach, Mozambique</p>
<p><a href="http://www.marinemegafauna.org" target="_blank">www.marinemegafauna.org<br />
</a>The Foundation for the Protection of Marine Megafauna (FPMM) was created in 2009 to research, protect and conserve the large populations of marine megafauna found along the Mozambican coastline.</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<div>
  <p><a href="http://www.utilawhalesharkresearch.com" target="_blank"><img width="141" height="83" src="/images/utila.jpg" alt="Utila Whale Shark Research" /></a></p>
</div>
<p><strong><u>Utila Whale Shark Research</u></strong></p>
<p>Researcher: <strong>Steve Fox<br />
</strong>Utila, Honduras</p>
<p><a href="http://www.utilawhalesharkresearch.com" target="_blank">www.utilawhalesharkresearch.com</a> <br />
  The Utila Whale Shark Research Project is managed locally by the staff of Deep Blue Utila and data collected from visiting divers and snorkelers and from local fisherman. The Project’s objective is to make all results available for local conservation efforts and to ensure their validity through peer review and publication.</p>
<p>&nbsp;</p>
<p>&nbsp; </p>
<div>
  <p><a href="http://www.wwf.org.ph/newsfacts.php?pg=det&amp;id=102" target="_blank"><img width="79" height="79" src="/images/philippines.jpg" alt="WWF Philippines" /></a></p>
</div>
&nbsp;<strong><u>WWF PHILIPPINES</u></strong>
</p>
<p>Researcher: <strong>David David, Embet Guadamor<br />
</strong>Donsol, Philippines</p>
<p><a href="http://www.wwf.org.ph/newsfacts.php?pg=det&amp;id=102">http://www.wwf.org.ph/newsfacts.php?pg=det&amp;id=102<br />
</a>WWF is documenting the sharks through a simple, but effective tool. Every day, WWF researcher Dave David dives with the sharks and photographs their gills. He then enters the photographs into a global whale shark database and compares each photograph with others by carefully aligning the gills and then comparing the spot patterns.&nbsp; He then determines if it is a new sighting or an existing one because a whale shark’s distinctive spots are like a human fingerprint – no two are alike. In fact, anybody who photographs a whale shark can enter their photo into the <a href="http://www.whaleshark.org/" >ECOCEAN</a> database.</p>
<p>&nbsp;</p>
<h2>Partner Individuals</h2>
<p>Researcher: <strong>Alan Duncan<br />
</strong>Koh Phangan, Thailand</p>
<p><a href="http://bit.ly/hYcmwV?r=bb" target="_blank">www.the-diveinn.com/categoryspecies/whale-sharks-identified-ecocean-project<br />
</a>Alan Duncan works to identify all whale shark reported from the Gulf of Thailand and the Andaman Sea.</p>
<p>&nbsp;</p>
<p>Researcher: <strong>: <strong>Dr. Eric Hoffmayer</strong><br />
</strong>Northern Gulf of Mexico, USA</p>
<p><a href="http://www.usm.edu/gcrl/whaleshark/index.php" target="_blank">www.usm.edu/gcrl/whaleshark/index.php<br />
</a>Dr. Hoffmayer heads a research effort to understand the basic biology, behavior, and movement patterns of whale shark in the northern Gulf of Mexico.
</p>
<p></p>
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
