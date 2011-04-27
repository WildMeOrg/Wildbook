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

						
			
						

		</div><!-- end menu -->
	</div><!-- end leftcol -->
	<div id="maincol-wide">

		<div id="maintext">
		  <h1 class="intro">Who are we</h1>
		</div>
			
			<p>&nbsp;</p>
			<p><strong>ZAVEN ARZOUMANIAN, Director, President</strong></p>
			<p>Zaven Arzoumanian earned degrees in Physics at McGill University (B.Sc.) and Princeton University (M.A., Ph.D.). His research interests include the astrophysics of neutron stars and black holes, testing theories of gravitation and the nature of matter in extreme environments, and new technologies for instrumentation in radio and X-ray astronomy. Zaven is employed as a contract scientist at NASA's Goddard Space Flight Center in Greenbelt, MD, USA. Zaven’s interest in Whale Sharks and pattern-matching computations for biological applications was cultivated in a collaboration with Jason Holmberg and Brad Norman, culminating in the development of a technique for matching spot patterns adapted from astronomy, published in the Journal of Applied Ecology (May 2005).<br />
			  Universities Space Research Association<br />
			  NASA Goddard Space Flight Center<br />
			  Greenbelt, MD 20770<br />
			  USA<br />
			  <a href="mailto:zaven@ecoceanusa.org">zaven@ecoceanusa.org</a></p>
<p><img src="images/zaven.jpg" width="450" height="293" border="1" /></p>
			<p>&nbsp;</p>
			<p><strong>ZEB HOGAN, Director</strong></p>
			<p>Zeb Hogan is a native of Tempe, Arizona and received his Ph.D. in Ecology from the University of California, Davis in 2004. His research interests include migratory fish ecology, multi-species fisheries management, the status and conservation of giant freshwater fish, endangered species issues, and conservation genetics. Zeb is also very involved in environmental education and outreach. He was recently designated a Conservation Science Fellow at the World Wildlife Fund and an Emerging Explorer by the National Geographic Society. He serves as a Director on the ECOCEAN USA board.<br />
			  Center for Limnology <br />
			  University of Wisconsin-Madison <br />
			  680 N. Park Street <br />
			  Madison, WI 53706 USA <br />
			  <a href="mailto:zebhogan@hotmail.com ">zebhogan@hotmail.com </a></p>
<p><img src="images/hogan.jpg" width="450" height="286" border="1" /> </p>
			<p>&nbsp;</p>
			<p><strong>JASON HOLMBERG, Director, Secretary</strong></p>
			<p>Jason Holmberg joined ECOCEAN in 2002 and has logged over 8,000 hours of development time on the ECOCEAN Whale Shark Photo-identification Library. As Project Architect, he has designed and implemented new tools to support digital pattern recognition for Whale Sharks. Using Jaon’s tools, the project has been able to categorize and manage a large amount of Whale Shark data and to identify individual animals from multiple photos taken by different researchers many years apart. Jason’s ECOCEAN interface also encourages public participation in Whale Shark research via an automated email system that sends individual data contributors automatic updates on individual Whale Shark re-sightings. Jason also undertook further field-testing of the project’s methodology and technology in the Galapagos Islands in October 2004, Honduras in March 2005 and Australia in April 2005. He gave two talks at the International Whale Shark Conference in Perth, Australia in May 2005 and later that year accepted a Duke’s Choice Award on behalf of the ECOCEAN team for innovative use of Java technology for Whale Shark data management and pattern recognition. Jason co-authored a widely lauded paper with Zaven Arzoumanian and Brad Norman on a 12-year population study at Ningaloo Marine Park in Ecological Applications (January 2008).<br />
			  ECOCEAN Library Architect<br />
			  4836 NE 31st Avenue<br />
			  Portland, Oregon 97211 <br />
			  USA<br />
			  <a href="mailto:jason@whaleshark.org">jason@whaleshark.org</a></p>
<p><img src="images/holmberg.jpg" width="450" height="293" border="1" /> </p>
			<p>&nbsp;</p>
			<p><strong>MARK MCBRIDE, Director</strong></p>
			<p>Mark McBride is a software developer at Twitter Inc., building APIs that allow developers to access Twitter capabilities. He is also on the board of ECOCEAN USA. He is interested in all aspects of software development, as well as sustainability and conservation<strong>.</strong></p>
<p><img src="images/mcbride.jpg" width="450" height="300" border="1" /> </p>
			<p>&nbsp;</p>
			<p><strong>BRAD NORMAN, Director</strong></p>
		<p>Brad Norman is a researcher with Murdoch University and is heading up a new Whale Shark study program there. His main research interests are Whale Shark biology, behavior and physiology, and sustainable eco-tourism and conservation. Brad began studying Whale Sharks at Ningaloo Marine Park, Western Australia in 1994 and has continued Whale Shark research both in Australia and abroad. Over his many years of work, he has established that the natural patterning on the skin of these sharks does not change over time and can be used to identify individuals. In addition to developing acoustic and satellite tracking programs at Ningaloo and Christmas Island, Indian Ocean with Australian authorities, he has also developed programs with WWF-Philippines, the Wildlife Trust of India, and the Galapagos National Parks. In 2006 he was awarded a Rolex Awards for Enterprise and as a Laureate and in 2007 provided training on Whale Shark monitoring and conservation issues to local stakeholders in eight countries. In 2008 he was also recognized by National Geographic as an Emerging Explorer for his work on species conservation and named an Ocean Hero by the same organization in 2010.<br />
			  Conservation Biologist, ECOCEAN<br />
			  Adjunct Research Associate, Murdoch University<br />
			  68a Railway Street<br />
			  Cottesloe WA 6011<br />
			  Australia<br />
			  <a href="mailto:brad@whaleshark.org">brad@whaleshark.org</a></p>
		
    <p><img src="images/norman.jpg" width="450" height="293" border="1" /></p>
				
			<p>&nbsp;</p>
			<p><strong>ZAID ZAID, Director</strong><br />
			  Zaid A. Zaid is a senior associate in the Litigation/Controversy Department, and a member of the Investigations and Criminal Litigation Practice Group. Zaid completed a summer clerkship in the Office of the Legal Advisor at the United States Department of State, where he drafted memos concerning the Iran-US Claims Tribunal and researched for NAFTA Chapter 11 investment claims against the US Government. Zaid was a political officer in the Foreign Service from 1999 to 2006, and he worked at US embassies in Syria, Tunis, Cairo, the US Mission to the United Nations, and with the Coalition Provisional Authority in Iraq as the liaison to the Iraqi Governing Council as well as at the US Embassy in Baghdad. He is a member of the New York State Bar Association, the American Bar Association. He is the chair of the Fletcher Alumni Club of Washington DC, a board member of ECOCEAN USA, a member of the American Constitution Society, a term member with the Council on Foreign Relations, and a fellow with the Truman National Security Project. <br />
			  1875 Pennsylvania Avenue NW<br />
			  Washington, DC 20006<br />
			  USA<br />
			  <a href="mailto:zaidazaid@gmail.com">zaidazaid@gmail.com</a></p>
	<p><img src="images/zaid.jpg" alt="" width="450" height="293" border="1" /></p>
			<p>&nbsp;</p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
