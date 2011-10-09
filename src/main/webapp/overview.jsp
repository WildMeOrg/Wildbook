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

	<div id="maincol-wide-solo">

		<div id="maintext">
		  <h2>Overview</h2>
		</div>
			
		<p>
		Manta Matcher represents the first global manta ray database. This site was specifically designed to manage manta ray sightings and identifications across their distribution. After the success of the Ecocean whale shark database, Manta Matcher was a logical follow-up. Manta rays are widely distributed, migratory, and have unique spot patterning on their ventral surface that can be used to permanently identify individuals.  At the present time, this database will enable researchers to upload and organize individually identified manta rays in their populations. This system is also intended to promote collaborations by way of cross-referencing databases to check for both regional and long distance movement. 
		</p>
		<p>
		An automated component to this site is currently in development. This component will greatly enhance the functionality of the site by allowing faster and more accurate cross- referencing. 
		</p>
		<p>It is our expectation that researchers will ultimately be able use sightings data to determine the abundance, trends, movements, and population structure of manta ray populations at individual aggregation sites across the globe. Using encounter photos and encounter information with mantas, you too can help update and maintain this global database. 
		</p>
		</p>
		
		<h2>About Mantas</h2>
		<p>
		Did you know that the natural spot patterns on the underside of manta rays can be used to distinguish between individuals like a fingerprint? Researchers are now using these natural markings to keep track of different population and examine their movements around the world. Contributing to this ongoing work is a way for the public to engage in what we like to call 'Citizen Science'. We cannot do this alone and we are asking for the public to help us solve some of the mysteries of mantas by adding photos to our global database.
		</p>
		<p>
		Below we explain a little more about the different species of Manta and outline the best ways to photograph these animals in the world and upload your photos to our website. 
		</p>
		
		<a name="species"><h3>Different species of manta</h3></a>
		<p>
		Currently two different species of Mantas are recognized, and it will be important to note which species you have observed. Luckily the two species can be visually differentiated and the following guide will help you distinguish between <em>Manta alfredi</em> and <em>Manta birostris</em>. 
		<p>
		
			<table>
										<tr>
										<td>
										<img src="images/differences_between_species.jpg" />
										</td>
										</tr>
										<tr>
										<td align="center">
										<strong>Fig 1. Differences between manta species</strong>
										</td>
										</tr>
		</table>
		
		<p>
		<em>Manta alfredi</em> (or Reef Mantas) can be found in all three of the world's major oceans but are most commonly encountered in the Indian Ocean and south Pacific. Key aggregation sites include: Hawaii, Australia, Komodo, Maldives, Yap, Palau, Bali, and Southern Mozambique.  
		</p>
		<ul>
		
		<li>Manta alfredi the smaller of the two species and does not exceed 5 meters (16.5 feet) disc width (which is the distance between the two wing tips). </li>
		<li>Their underside is predominantly white with the exception of their unique spot and patch patterning. These natural marks can be found on the pectoral fins, the stomach, and in-between the gill slits.  Their mouths are most commonly white in colour. See figure for examples of variation in its underside colouration.</li>
		<li>The topside of Manta alfredi is predominantly black with light patches particularly around the upper back. We refer to these marks as shoulder patches. In Manta alfredi these patches can be prominent or very faint but it is their general shape that is used to differentiate them from Manta birostris. See figure for examples of variation in the shoulder patches of Manta alfredi.</li>
		<li>Does not have remnant stinging spine at the base of the tail

		</ul>
		</p>
		
		<p>
		<table>
												<tr>
												<td>
												<img src="images/dorsal_malfredi.jpg" />
												</td>
												</tr>
												<tr>
												<td align="center">
												<strong>Fig 2. Dorsal surface of <em>Manta alfredi</em></strong>
												</td>
												</tr>
		</table>
		</p>
		
		<p>
		<table>
														<tr>
														<td>
														<img src="images/ventral_alfredi.jpg" />
														</td>
														</tr>
														<tr>
														<td align="center">
														<strong>Fig 3. Ventral surface of <em>Manta alfredi</em></strong>
														</td>
														</tr>
		</table>
		</p>
		
		
		<p>
		<em>Manta birostris</em> is the most widely distributed manta ray and is found in all three of the world major oceans. This species is commonly encountered at offshore islands and seamounts. Key aggregation sites include: Southern Brazil, Ecuador, Thailand, Egypt, Southern Mozambique, and Mexico (Pacific Ocean).
		
		<ul>
		<li>Manta birostris is the larger of the two species reaching up to 7 meter disc width (23 feet). </li>
		<li>Their underside appear much darker than Manta alfredi as they have dark faces/mouths and a dark V-shape along the trailing edge of the pectoral fins. They also have natural spot patterning on their undersides but the spots and patches always occur centrally on the stomach area and doe not extend between the gill slits or far out on the pectoral fins. See figure for examples of variation in its underside colouration.</li>
		<li>The topside of Manta birostris is predominantly black with light patches on the upper back. We refer to these marks as shoulder patches. In Manta birostris these patches are bright and prominent. Their general shape that is used to differentiate them from Manta alfredi. In this species the shoulder patches look like distinct triangles. See figure for examples of variation in the shoulder patches of Manta birostris.</li>
		<li>Has remnant stinging spine at the base of the tail.</li>

		
		</ul>
		</p>
		
			<p>
				<table>
														<tr>
														<td>
														<img src="images/dorsal_giant.jpg" />
														</td>
														</tr>
														<tr>
														<td align="center">
														<strong>Fig 4. Dorsal surface of <em>Manta birostris</em></strong>
														</td>
														</tr>
				</table>
				</p>
				
				<p>
				<table>
																<tr>
																<td>
																<img src="images/ventral_giants.jpg" />
																</td>
																</tr>
																<tr>
																<td align="center">
																<strong>Fig 5. Ventral surface of <em>Manta birostris</em></strong>
																</td>
																</tr>
				</table>
		</p>
		<p>
		<table>
										<tr>
										<td>
										<img width="810px" height="*" src="images/spine_prescence.jpg" />
										</td>
										</tr>
										<tr>
										<td align="center">
										<strong>Fig 6. Remnant spine in <em>Manta birostris</em></strong>
										</td>
										</tr>
		</table>
		</p>
		<h3>Color morphs</h3>
		
		<p>To make matters slightly confusing, two distinct color morphs have been identified in manta rays, black mantas and white mantas. A genetic disorder causes these different colour variants and both conditions are rare.</p>
		<p>Black mantas, or melanistic mantas, have an entirely black topside, and a predominately black undersides except for a variably sized white blaze along its mid-line. </p>
		<p>White mantas, or leucistic mantas, have a reduction pigment making their skin appear lighter, even white. </p>
		<p>Don't worry if you have doubts about identifying the animal you encountered or uploading your ID shots. If you do not feel comfortable assigning a species or coluration type when you upload your photo, our trained staff will do it for you. Simply click on unsure or unknown when prompted.</p>

		<table>
						<tr>
						<td>
						<img width="810px" height="*" src="images/color_morphs.jpg" />
						</td>
						</tr>
						<tr>
						<td align="center">
						<strong>Fig 7. Color morphs in mantas</strong>
						</td>
						</tr>
		</table>
			<p>&nbsp;</p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
