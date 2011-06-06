<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

	
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
		  <h1 class="intro"><img src="images/adoption.gif" width="56" height="48" align="absmiddle" />Adopt a Shark </h1>
		</div>
			
			<p>You can support the ongoing research of the ECOCEAN Whale Shark Photo-identification Library by adopting a whale shark. A whale shark adoption allows you to:</p>
			<ul>
			  <li>support cutting-edge whale shark research through the ECOCEAN Library </li>
	    <li> receive email updates of resightings of your adopted shark</li>
		<li>display your photo and a quote from you on the shark's page in our library</li>
		</ul>
			<p>Funds raised by shark adoptions are used to offset the costs of maintaining this global library and to support new and existing research projects for the world's most mysterious fish.</p>
			<p>You can adopt a shark at the following levels:</p>
			<ul>
			<li> Children's adoption = USD $25/year</li>
			  <li> Individual adoption = USD $50/year</li>
	    <li>Group adoption = USD $200/year </li>
	          <li>Corporate adoption = USD $1000/year</li>
		</ul>
			<p>The cost of your adoption is tax deductible in the United States through ECOCEAN USA, a 501(c)(3) non-profit organization.</p>
			
			<table><tr><td>
			<h3>Creating an adoption</h3>
			<p>To adopt a whale shark, follow these steps.</p>
			<p>1. Make the appropriate donation using the PayPal link below.</p>
	
<form action="https://www.paypal.com/cgi-bin/webscr" method="post">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="5075222">
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
			<p>2. Send an email to <img src="images/adoptions_email.gif" width="228" height="18" align="absmiddle" />. Include the following in the email</p>
			<ul>
			  <li> your name and address</li>
	    <li>your donation amount and the email/userid that made the PayPal donation </li>
	          <li>the shark you wish to adopt.</li>
		<li>the email to notify with future resightings of the shark </li>
		<li>a photo of yourself, your group, or a corporate logo</li>
		<li>a quote from you stating why whale shark research and conservation are important </li>
		</ul>
	<p>Please allow 24-48 hours after receipt of your email for processing. We are currently working to automate and speed this process through PayPal. </p>
	<p>Your adoption (photograph, name, and quote) will be displayed on the web site page for your shark, and one adoption will be randomly chosen to be displayed on the front page of whaleshark.org.</p>
	<p><em><strong>Thank you for adopting a shark and supporting our global research efforts! </strong></em></p>
	</td>
	<td width="200" align="left">
	<p align="center"><a href="http://www.whaleshark.org/individuals.jsp?number=A-001"><img src="images/sample_adoption.gif" border="0" /></a>	
	</p>
	  <p align="center"><strong>
	  Sample whale shark adoption for whale shark A-001. <br />
	  </strong><strong><a href="http://www.whaleshark.org/individuals.jsp?number=A-001">Click here to see it on the shark page. </a> </strong></p>
	  </td></tr></table>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
