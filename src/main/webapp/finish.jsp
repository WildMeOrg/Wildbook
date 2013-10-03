<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
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
	//props.load(propsInputStream);
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
<title>Wild Me - Finish Finning!</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="The Wildbook for Whale Sharks photo-identification library is a visual database of whale shark (Rhincodon typus) encounters and of individually catalogued whale sharks. The library is maintained and used by marine biologists to collect and analyse whale shark encounter data to learn more about these amazing creatures." />
<meta name="Keywords" content="whale shark,whale,shark,Rhincodon typus,requin balleine,Rhineodon,Rhiniodon,big fish,Wild Me,Brad Norman, fish, coral, sharks, elasmobranch, mark, recapture, photo-identification, identification, conservation, citizen science" />
<meta name="Author" content="Wild Me - info@whaleshark.org" />
<link href="css/ecocean.css" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="images/favicon.ico" />

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
		  <h1 class="intro">FINISH: End Shark Finning Now </h1>
		</div>
			
		<ul>
		  <li><strong>The biggest threat facing the whale shark </strong><strong>is us.</strong></li>
		  <li><strong>Potentially thousands of whale sharks are hunted each year, </strong><strong>mainly for their fins </strong></li>
		  <li><strong>Whale sharks are just one of over 500 species of shark threatened by the multi billion dollar, </strong><strong>international shark fin trade.</strong></li>
	    </ul>
		<p align="left"><img src="images/dead%20sharks.jpg" width="150" height="131" hspace="5" vspace="5" border="1" align="right" />Sharks have been roaming our oceans for over 400 million years. Now, as a result of human ignorance and arrogance all shark species are under threat. Do you want to be responsible for their disappearance? Join us in our campaign to put an end to the slaughter. Join Wild Me in our campaign to FINISH FINNING!</p>
	    <ul>
	      <li>Tens of millions of sharks are killed each year to supply the fin trade, corresponding to over 2 million tons of shark. </li>
	      <li>Shark populations around the world are in decline by between 70 and over 90 percent. 20 species of shark are listed as endangered by the World Conservation Union (IUCN). One third of all open ocean sharks are listed as threatened or vulnerable to extinction by the IUCN. This includes the whale shark. </li>
	      <li>A ‘finned' shark is a shark that has had its fins cut off and then been thrown back into the ocean while still alive. The shark will then drown, die of starvation or be consumed by other predator species. </li>
	      <li>Shark fin harvesting has increasing to feed the ever-growing demand for shark fin soup. Shark fin soup is often served at Chinese wedding celebrations and special occasions as a sign of affluence. </li>
	      <li>The value of shark fins has increased with economic growth in Asia (particularly China), increasing incentive for the harvesting of fins.</li>
	      <li>It has repeatedly been shown that live sharks have a significant value for marine ecotourism; whale shark tourism is estimated to be worth at least $47.5 million worldwide.</li>
	      <li>The removal of sharks greatly disrupts marine ecosystems by allowing an increase in the abundance of their prey species.</li>
	      <li>Shark populations are slow to recover as they are slow growing, late to mature and produce relatively few offspring. </li>
        </ul>
	    <p>A small donation can help!</p>
	    <p><form action="https://www.paypal.com/cgi-bin/webscr" method="post">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="5075222">
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form></p>
	    <p><strong>The time is now to </strong><strong>FINISH FINNING! </strong></p>
	    <p><em>There is some GOOD NEWS!</em></p>
	    <ul>
	      <li>1 July 2011: Hawaii is set to become the first state in the U.S. to ban the possession of shark fins. </li>
	      <li>July 2010: Maldives will become the second country to announce blanket protection for its sharks; previously, Maldives was a top contributor to the finning market. </li>
	      <li>Spain, one of the top five shark-landing nations, has recently outlawed the fishing of thresher and hammerhead sharks. </li>
	      <li>September 2009: At a UN conference, the President of Palau announced the establishment of the world's first shark sanctuary, banning all commercial shark fishing it its 230,000 square miles of water. </li>
        </ul>
	    <p><em>SHARK FIN SOUP </em></p>
	    <p><img src="images/sharkfinsoup.jpg" width="194" height="145" hspace="5" vspace="5" border="1" align="left" />Shark fin soup is a soup of Chinese origin made with shark fin and flavoured with chicken, beef, or ham stock. The shark fin itself has little flavour; it's main purpose in the dish is to add texture. It is also considered a delicacy and a mark of affluence. Vast quantities of shark fin soup are served to guests at celebratory events including weddings and business banquets. However, the soup is readily available at most Chinese restaurants in countries around the world. </p>
	    <p>Shark fin soup is expensive, often between $100 and $1000 per bowl. For this reason, the fins fetch a comparatively high price at market compared to other fish fare, which has ensured a steady supply of shark fins as fisherman are eager to meet the ever growing demand. The economic boom and corresponding increase in wealth in Asia has created an impossible demand that is destroying shark populations. </p>
	    <p>Shark fin is purchased either frozen or dried. For visual appeal, fins are often treated with hydrogen peroxide to retain color. Whale shark fin is of particular appeal as a display item for its size and easily recognizable spot patterning. </p>
	    <p>Though shark fin proponents maintain that the soup has health benefits, repeated studies have yielded absolutely no evidence to support these claims. Instead, it has been shown that due to high levels of mercury present in shark meat, shark products are barely fit for human consumption. </p>
	    <p><em>LONG-LINING </em></p>
	    <p><img src="images/shark%20fins.jpg" width="191" height="131" hspace="5" vspace="5" border="1" align="left" />Long lines are monofilament fishing lines that can stretch for over 100 miles near the surface. At roughly 30 metre intervals, secondary hooked and baited lines are attached. These ‘curtains of death' are indiscriminant in their catch – they routinely catch not only their target species (tuna, swordfish, sharks) but also the threatened and legally protected leatherback and loggerhead turtles and seabirds. Over one fourth of all long-line catch is discarded back into the sea as already dead ‘bycatch'. </p>
	    <p>Long lines are a significant threat to the survival of several species of shark. The majority of sharks caught on long lines are killed for their fins; their remaining, often still breathing body is thrown back into the ocean as ‘bycatch'. At present there are only a small handful of areas that have banned this destructive method of killing, including the U.S. Pacific coast; however, in most ocean areas long lining is legal. </p>
	    <p><em>MEDIA </em></p>
	    <p>Wild Aid Whale Shark video: </p>
	    <p><a href="http://www.youtube.com/watch?v=yqVaXF7CHpM">http://www.youtube.com/watch?v=yqVaXF7CHpM </a></p>
	    <p>‘Bowls of Blood', by Gary Stokes, Oceanic Love: </p>
	    <p><a href="http://www.vimeo.com/11928920">http://www.vimeo.com/11928920 </a></p>
	    <p>Man &amp; Shark, by Alex Hofford: </p>
	    <p><a href="http://www.vimeo.com/7645560">http://www.vimeo.com/7645560 </a></p>
	    <p>Yao Ming Wild Aid PSA: </p>
	    <p><a href="http://www.youtube.com/watch?v=mJG7RaLX-DM">http://www.youtube.com/watch?v=mJG7RaLX-DM </a></p>
	    <p>A video about shark finning from Wild Aid: </p>
	    <p><a href="http://www.youtube.com/watch?v=C2UKgLsOhRM&amp;feature=player_embedded">http://www.youtube.com/watch?v=C2UKgLsOhRM&amp;feature=player_embedded </a></p>
	    <p>Anti shark finning video by Jack Meade: <a href="http://www.youtube.com/watch?v=eFQX04kERoI&amp;feature=player_embedded">http://www.youtube.com/watch?v=eFQX04kERoI&amp;feature=player_embedded </a></p>
	    <p>Shark Fin Soup information from ‘Sharkwater': </p>
	    <p><a href="http://www.youtube.com/watch?v=KBYJgPqnzU8">http://www.youtube.com/watch?v=KBYJgPqnzU8 </a></p>
	    <p>Sea Shepherd's Galapagos Long-lining campaign: <a href="http://www.youtube.com/watch?v=mLAlljaS_40&amp;feature=related">http://www.youtube.com/watch?v=mLAlljaS_40&amp;feature=related </a></p>
	    <p>National Geographic on shark finning: </p>
	    <p><a href="http://www.youtube.com/watch?v=mCqPXhhxZIg&amp;feature=related">http://www.youtube.com/watch?v=mCqPXhhxZIg&amp;feature=related </a></p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
