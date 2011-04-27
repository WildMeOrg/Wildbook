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
		  <h2>Support ECOCEAN</h2>
		</div>
			
<p><strong><br />
</strong>Private Donors are increasingly vital to ECOCEAN’s work.<br />
ECOCEAN welcomes new and innovative partnerships with the private sector. <br />
These partnerships align ECOCEAN’s research and education activities with likeminded corporate citizens. In turn, by helping ECOCEAN, corporations can engage their employees, customers and stakeholders in ECOCEAN’s research and conservation mission.</p>
<p>&nbsp;</p>
<p><strong><strong><a href="http://webcache.googleusercontent.com/search?q=cache:PB1_Z2qGZMgJ:www.lists.edna.edu.au/lists/lists/attachments/message/969277/ECOCEAN%2520Wish%2520List.pdf%3Bjsessionid%3DB0C8A89E4135BB7C0058C42F6E75679E+why+are+we+raising+funds,+ecocean&cd=2&hl=en&ct=clnk&client=safari&source=www.google.com" title="Why are we raising funds?">Why are we raising funds?</a></strong><br />
</strong></p>
<p>&nbsp;</p>
<h2>YOUR COMPANY</h2>
<p>By signing up to help ECOCEAN, your company can support international shark research and conservation efforts.</p>
<p>&nbsp;</p>
<p><strong><strong>Maximize impact</strong></strong></p>
<p>ECOCEAN can maximize early private sector support quickly and effectively.</p>
<p>With whale sharks protected in only ten percent of the nearly 100 countries they visit worldwide, time is of the essence to work together to better understand this species. Your company can provide strategic support while receiving recognition for early participation on the ground. Can your company provide any of the following?</p>
<ul>
  <ul>
    <ul>
      <li>Direct funding</li>
      <li>Logistics equipment (boat / car / satellite and data-logging tags)</li>
      <li>Information technology equipment (network server / storage system)</li>
      <li>Field expenses (including food / accommodation / computers / cameras / flights etc.)</li>
      <li>Expertise (Web design / software engineering)  </li>
    </ul>
  </ul>
</ul>
<p>To find out how your company can create a long-term partnership with ECOCEAN or help to protect the largest fish in the ocean, please e-mail us at:<br />
ecocean@whaleshark.org<br />
or phone +61 414953627 (Australia) / +1 503-233-2062 (U.S.)</p>
<p>See our list of current <a href="http://www.whaleshark.org/privateSector.jsp?langCode=en">private sector partners</a>.</p>
<p>&nbsp;</p>
<div align="center"><p><img src="images/logo.gif" alt="ECOCEAN" width="289" height="204"/></p></div>
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
