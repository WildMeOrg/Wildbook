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
		  <h2>Private Sector Partners</h2>
		</div>
			
			<p>Corporations, foundations and individuals are increasingly playing a vital role in supporting ECOCEAN’s work. We are particularly grateful to the following Australian organizations who have committed time and resources to help us undertake research and education activities to further assist marine conservation initiatives.</p>
		
			
			<h2>Australian Partners</h2>
<p><strong>Department of Environment and Conservation (DEC)</strong></p>
<p>DEC has continued to acknowledge the benefit of ECOCEAN’s program for sustainable management purposes at Ningaloo Marine Park. In providing in-kind support via office facilities and resources at Exmouth, WA, DEC has been integral to the ECOCEAN’s work at Ningaloo&nbsp; each whale shark ‘season’. In 2010, DEC made a significant donation to ECOCEAN to establish a collaborative whale shark photo-identification monitoring project at Ningaloo Marine Park.<a href="http://www.dec.wa.gov.au/" target="_blank">www.dec.wa.gov.au/</a></p>
<p><strong>Department of Fisheries, Western Australia</strong></p>
  <p>The Department of Fisheries, WA (Exmouth) provided support for ECOCEAN’s 2010 tracking work at Ningaloo Marine Park. <a href="http://www.fish.wa.gov.au" target="_blank">www.fish.wa.gov.au</a></p>
<p><strong>Department of Sustainability, Environment, Water, Population and Communities</strong></p>
  <p>Provided funding for whale shark educational materials for distribution throughout Australia.<a href="http://www.environment.gov.au" target="_blank">www.environment.gov.au</a></p>
<p><strong>Earthwatch Institute</strong></p>
  <p>Earthwatch Institute supported ECOCEAN’s field research by offering volunteers the opportunity to join the ‘Whale Sharks of Ningaloo Reef Expedition’. Earthwatch Institute provided resources to support three years of field data. <a href="http://www.earthwatch.org" target="_blank">www.earthwatch.org</a></p>
<p><strong>Longbreak Charters, Gun Marine</strong></p>
<p>For two consecutive research seasons, Longbreak Charters provided a liveaboard charter vessel for ECOCEAN’s research at Ningaloo. In 2009, Gun Marine played a key role in the whale shark behavioural study at Ningaloo by providing ECOCEAN with a vessel for the duration of the project period. </strong><a href="http://www.longbreak.com.au" target="_blank">www.longbreak.com.au</a><strong>; <a href="http://www.gunmarine.com.au" target="_blank">www.gunmarine.com.au</a></strong></p>
<p><strong>MG Kailis</strong></p>
<p>MG Kailis has provided sustainably harvested wild capture prawns for ECOCEAN volunteers working in the field at Exmouth and Coral Bay, Western Australia. <a href="http://www.kailis.com.au" target="_blank">www.kailis.com.au</a></p>
<p><strong>Murdoch University</strong></p>
<p>Ongoing support for whale shark research at Ningaloo Marine Park. <a href="http://www.murdoch.edu.au" target="_blank">www.murdoch.edu.au</a></p>
<p><strong>Novotel Ningaloo Resort Exmouth</strong></p>
<p>Novotel Ningaloo Resort has provided accommodation for ECOCEAN staff throughout the 2010 whale shark season at Ningaloo. <a href="http://www.novotelningaloo.com.au" target="_blank">www.<strong>novotel</strong>ningaloo.com.au</a></p>
<p><strong>Rolex Australia and Smales Jewellers</strong></p>
<p>Rolex Australia and Smales Jewellers assisted with funding for the 2009 whale shark behavioural study at Ningaloo using high-tech tags (‘Daily Diaries’). <a href="http://www.rolex.com" target="_blank">www.rolex.com</a><strong>; </strong><a href="http://www.smales.com.au" target="_blank">www.smales.com.au</a></p>
<p><strong>Skywest Airlines</strong></p>
<p>To accommodate travel to ECOCEAN’s field site at Ningaloo Marine Park, Skywest Airlines donated several flights for ECOCEAN researchers in 2010. <a href="http://www.skywest.com.au" target="_blank">www.skywest.com.au</a></p>
<p><strong>Tabata Australia</strong></p>
<p>Tabata Australia have shown their support for ECOCEAN’s field research by donating diving equipment for ECOCEAN researchers. <a href="http://www.tabata.com.au" target="_blank">www.tabata.com.au</a></p>
<p><strong>Thyne Reid Education Trust</strong></p>
<p>Provided assistance for the development of the ECOCEAN Whale Shark Photo-ID Library. Thyne Reid has pledged their ongoing support for two Honours scholarships each year for students wishing to study whale sharks in partnership with Murdoch University.</p>
<p><strong>Whale Shark Tour Operators, Ningaloo Marine Park</strong></p>
<p>Whale Shark Ecotourism Industry Operators working out of Exmouth and Coral Bay have consistently provided ECOCEAN a place on their vessels to enable us to undertake our research and public awareness programs. </p>
<p><strong>Coral Bay Adventures (</strong><a href="http://www.coralbayadventures.com" target="_blank"><strong>www.coralbayadventures.com</strong></a><strong>)<br />
</strong><strong>Exmouth Diving Centre (</strong><a href="http://www.exmouthdiving.com.au" target="_blank"><strong>www.exmouthdiving.com.au</strong></a><strong>)<br />
Kings Ningaloo Reef Tours Exmouth (</strong><a href="http://www.kingsningalooreeftours.com.au" target="_blank"><strong>www.kingsningalooreeftours.com.au</strong></a><strong>)<br />
Ningaloo Blue (</strong><a href="http://www.ningalooblue.com.au" target="_blank"><strong>www.ningalooblue.com.au</strong></a><strong>)<br />
Ningaloo Experience (</strong><a href="http://www.ningalooexperience.com" target="_blank"><strong>www.ningalooexperience.com</strong></a><strong>)<br />
Ningaloo Reef Dive&nbsp; (</strong><a href="http://www.ningalooreefdive.com" target="_blank"><strong>www.ningalooreefdive.com</strong></a><strong>)<br />
Ningaloo Reef Dreaming (</strong><a href="http://www.ningaloodreaming.com" target="_blank"><strong>www.ningaloodreaming.com</strong></a><strong>)<br />
Ningaloo Whaleshark-n-Dive (</strong><a href="http://www.ningaloowhalesharkndive.com.au" target="_blank"><strong>www.ningaloowhalesharkndive.com.au</strong></a><strong>)<br />
Ocean Eco Adventures (</strong><a href="http://www.oceanecoadventures.com.au" target="_blank"><strong>www.oceanecoadventures.com.au</strong></a><strong>)<br />
Three Islands Whale Shark Dive (</strong><a href="http://www.whalesharkdive.com" target="_blank"><strong>www.whalesharkdive.com</strong></a><strong>)</strong></p>
<p><strong>Woodside Energy Pty Ltd</strong></p>
<p>Provided educational material for international whale shark conservation efforts. <a href="http://www.woodside.com.au" target="_blank">www.<strong>woodside</strong>.com.au</a></p>
<h2 align="center">&nbsp;</h2>
<h2>International Partners</h2>
<p><strong>British Ecological Society</strong></p>
<p>Assisted with the development of SPOT! – the perspective correction software used in the ECOCEAN Library. <a href="http://www.britishecologicalsociety.org" target="_blank">www.britishecologicalsociety.org</a></p>
<p><strong>National Geographic Society</strong></p>
<p>Supporter of ECOCEAN via provision of the ‘Crittercam’ for use in the 2010 research project and technical expertise. <a href="http://www.nationalgeographic.com" target="_blank">www.nationalgeographic.com</a></p>
<p><strong>Georgia Aquarium</strong></p>
<p>Provided assistance through its Marine Conservation Action Fund to initiate a whale shark population study in collaboration with Proyecto Domino in the Mexican Caribbean and Gulf.</p>
<p><strong>Royal Caribbean Cruise Lines (Ocean Fund)</strong></p>
<p>Sponsored significant components of whale shark behavioural field studies at Ningaloo Marine Park, Western Australia in 2008-2010.</p>
<p><strong>United Nations Environment Programme (Regional Seas)</strong></p>
<p>Development of ‘Best Practice’ whale shark ecotourism guidelines for developing industries worldwide. <a href="http://www.unep.org/regionalseas/" target="_blank">www.unep.org/regionalseas/</a></p>
<p>&nbsp;</p>
<h2>Other Supporters</h2>
<p><strong>Barry and Paula Downing</strong></p>
<p><strong>Bill Breheny</strong></p>
<p><strong>Chris Hicks, Rough Copy Design </strong>assisted ECOCEAN with logo design and the layout of educational materials. <a href="http://www.roughcopy.com.au" target="_blank">www.<strong>roughcopy</strong>.com.au</a></p>
<p><strong>Larry Burkett</strong></p>
<p><strong>Luc Longley</strong></p>
<p><strong>Grantham and Johnson Kitto</strong></p>
<p><strong>Olympus Cameras</strong></p>
<p><strong>PNY Technologies</strong></p>
<p><strong>Rolex Awards for Enterprise</strong></p>
<p>&nbsp;</p>
<h2>A Special Thanks For Making These Events Possible…</h2>
<p><strong>&quot;ECOCEAN's 2011 Fundraiser in Perth&quot; </strong><strong>&lt;link to Fundraiser page&gt;</strong></p>
<p>	Supporters of this Event include: </p>
<ul>
  <ul>
    <ul><strong>
      <li>AQWA</li>
      <li>Ben Elton</li>
      <li>Brian Eno</li>
      <li>Fremantle Dockers</li>
      <li>John Butler</li>
      <li>John Stevens and Peter Last</li>
      <li>Jurgen Freund</li>
      <li>King's Ningaloo Reef Tours</li>
      <li>Luc Longley</li>
      <li>Ningaloo Whaleshark n Dive</li>
      <li>Novotel Ningaloo Resort</li>
      <li>Ocean Eco Adventures</li>
      <li>Perth Wildcats</li>
      <li>Rolex (Australia)</li>
      <li>Rosendorff Jewellers</li>
      <li>Sal Salis Ningaloo Reef</li>
      <li>Sea Shepherd Conservation Society</li>
      <li>Tabata Australia</li>
      <li>Three Island Marine Charters</li>
      <li>West Coast Eagles </li>
    </strong></ul>
  </ul></ul>
<p><strong>And THANK YOU to the thousands of individuals who have contributed whale shark sighting information from 45 different countries </strong><br />
  <a name="_GoBack" id="_GoBack"></a><strong>around the world to the </strong><br />
  <strong>ECOCEAN Whale Shark Photo-Identification Library!</strong></p>
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
