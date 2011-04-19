<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="java.util.Vector,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,org.apache.log4j.Logger,org.apache.log4j.PropertyConfigurator, org.ecocean.*" %>
<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation() %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

</head>
<%

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 

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
	
session=request.getSession(true);
session.putValue("logged", "true");
%>


<body bgcolor="#FFFFFF">
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher" value="<%=request.isUserInRole("researcher")%>"/>
	<jsp:param name="isManager" value="<%=request.isUserInRole("manager")%>"/>
	<jsp:param name="isReviewer" value="<%=request.isUserInRole("reviewer")%>"/>
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>"/>
</jsp:include>	
<div id="main">
	

		<div id="maintext">
<table width="720" border="0">
  <tr>
    <td><h3 align="center">ECOCEAN Whale Shark Photo-identification Library<br>
      Visitor Agreement    </h3>
      <p>Welcome to the ECOCEAN Library! Please read this Visitor Agreement. By using this web site, you accept its terms. This Visitor Agreement applies to any web page using the following domains, which are collectively known as the "ECOCEAN Whale Shark Photo-identification Library" or in shortened form "ECOCEAN Library": </p>
      <ul>
        <li><a href="http://www.ecocean.org">ecocean.org</a> </li>
        <li><a href="http://www.whaleshark.org">whaleshark.org</a> </li>
        <li><a href="http://www.shepherdproject.org">shepherdproject.org </a> </li>
      </ul>
      <p>The Internet is an evolving medium, and we may change the terms of this Visitor Agreement from time to time. By continuing to use any of the ECOCEAN Library sites after we post any such changes, you accept the Visitor Agreement, as modified. We may change, restrict access to, suspend or discontinue the ECOCEAN Library, or any portion of the ECOCEAN Library, at any time. </p>
      <p>If you disagree with any material you find in the ECOCEAN Library, we suggest that you respond by noting your disagreement in an email to <em>webmaster at shepherdproject dot org</em>. We invite you to bring to our attention any material you believe to be factually inaccurate. Please forward a copy of the material to our webmaster along with an explanation of your disagreement.</p>
      <p>If you are an owner of intellectual property who believes your intellectual property has been improperly posted or distributed via the ECOCEAN Library, please notify us immediately by sending email to our webmaster.</p>
      <p>A link to another Web site does not constitute an endorsement of that site (nor of any product, service or other material offered on that site) by the ECOCEAN Library or its participants.</p>
      <p><strong>NO SOLICITING</strong><br>
  You agree not to use the ECOCEAN Library to advertise or to solicit anyone to buy or sell products or services, or to make donations of any kind, without our express written approval.</p>
      <p><strong>USE OF MATERIALS</strong><em><br>
      </em>Any photographs that you submit to the ECOCEAN Library remain YOUR intellectual property, and the ECOCEAN Library and its participants agree not to use them for media purposes without your express permission. However, by submitting photographs and whale shark sighting data you give ECOCEAN and its participants permission to use this data for research and conservation purposes. Data, such as shark identifications, may be derived from your submissions. This data becomes the intellectual property of the ECOCEAN Library and may not be published or re-used without the express permission of the ECOCEAN Library.</p>
      <p>The Internet allows people throughout the world to share valuable information, ideas and creative works. To ensure continued open access to such materials, we all need to protect the rights of those who share their creations with us. Although we make the ECOCEAN Library freely accessible, we don't intend to give up our rights, or anyone else's rights, to the materials appearing in the ECOCEAN Library. The materials available through the ECOCEAN Library are the property of the ECOCEAN Library or, in the case of photographs and images, the property of individual contributors. All photographs and data are protected by copyright, trademark and other intellectual property laws. You may not reproduce any of the materials without the prior written consent of the owner. You may not distribute copies of materials found on the ECOCEAN Library in any form (including by email or other electronic means), without prior written permission from the ECOCEAN Library.</p>
      <p>Requests for permission to use, reproduce, or distribute materials found in the ECOCEAN Library should first be sent to <em>webmaster at whaleshark dot org</em>. Requests will be evaluated and responded to (yes or no) as quickly as possible. Our main concern is to protect intellectual property and to ensure that credit is given where credit is due. Our mission is to facilitate global cooperation within the whale shark research community, and we are working to make as much data as possible available while protecting the rights of individual contributors.</p>
      <p><strong>LINKING<br>
      </strong>We welcome links to the ECOCEAN Library. You are usually free to establish a hypertext link to any of the ECOCEAN Library pages so long as the link does not state or imply any sponsorship of your site by the ECOCEAN Library. Pages linking to the Library should include, to the best of your ability, factually correct information about the ECOCEAN Library and about whale sharks. In other words, please respect the scientific mission of the ECOCEAN Library and help us ensure that only accurate information about whale sharks is disseminated.</p>
      <p><strong>FRAMING</strong> <br>
  No Framing. Without the prior written permission of the ECOCEAN Library, you may not frame any of the content in the ECOCEAN Library, or incorporate into another Web site or other service any intellectual property of the ECOCEAN Library or its data contributors. Requests for permission to frame our content may be sent to: <em>webmaster at whaleshark dot org.</em> </p>
      <p><strong>DISCLAIMER OF WARRANTIES AND LIABILITY<br>
      </strong>We work hard to make the ECOCEAN Library interesting and informative, but we cannot guarantee that our users will always find everything to their liking. Please read this Disclaimer carefully before using the ECOCEAN Library.</p>
      <p><em>YOU AGREE THAT YOUR USE OF THE ECOCEAN LIBRARY IS AT YOUR SOLE RISK. BECAUSE OF THE NUMBER OF POSSIBLE SOURCES OF INFORMATION AVAILABLE THROUGHOUT, AND THE INHERENT HAZARDS AND UNCERTAINTIES OF ELECTRONIC DISTRIBUTION, THERE MAY BE DELAYS, OMISSIONS, INACCURACIES OR OTHER PROBLEMS WITH SUCH INFORMATION. IF YOU RELY ON ANY ECOCEAN LIBRARY MATERIAL, YOU DO SO AT YOUR OWN RISK. YOU UNDERSTAND THAT YOU ARE SOLELY RESPONSIBLE FOR ANY DAMAGE TO YOUR COMPUTER SYSTEM OR LOSS OF DATA THAT RESULTS FROM ANY MATERIAL AND/OR DATA DOWNLOADED FROM OR OTHERWISE PROVIDED THROUGH THE ECOCEAN LIBRARY. THE ECOCEAN LIBRARY IS PROVIDED TO YOU AS IS, WITH ALL FAULTS, AND AS AVAILABLE. UNDER NO CIRCUMSTANCES SHALL THE PARTICIPANTS, PROGRAMMERS, AND CONSULTANTS IN THE ECOCEAN LIBRARY BE LIABLE TO YOU OR ANYONE ELSE FOR ANY DAMAGES ARISING OUT OF USE OF THE ECOCEAN LIBRARY, INCLUDING, WITHOUT LIMITATION, LIABILITY FOR CONSEQUENTIAL, SPECIAL, INCIDENTAL, INDIRECT OR SIMILAR DAMAGES, EVEN IF WE ARE ADVISED BEFOREHAND OF THE POSSIBILITY OF SUCH DAMAGES. (BECAUSE SOME STATES DO NOT ALLOW THE EXCLUSION OR LIMITATION OF CERTAIN CATEGORIES OF DAMAGES, THE ABOVE LIMITATION MAY NOT APPLY TO YOU. IN SUCH STATES, THE LIABILITY OF ECOCEAN AND ITS STAFF AND AFFILIATES IS LIMITED TO THE FULLEST EXTENT PERMITTED BY SUCH STATE LAW.) </em></p>
      <p><strong>USER ACCOUNTS </strong><br>
  The ECOCEAN Library staff does its best to ensure that information we post to the ECOCEAN Library is timely, accurate, and scientifically valuable. To obtain access to certain services of the ECOCEAN Library, you may be given an opportunity to register with the ECOCEAN Library. As part of any such registration process, you will be provided an user name and a password. You agree that the information you supply during that registration process will be accurate and complete and that you will not register under the name of, or attempt to enter the ECOCEAN Library under the name of, another person. You will be responsible for preserving the confidentiality of your password, sharing it with no one else without express permission, and will notify the staff of the ECOCEAN Library of any known or suspected unauthorized use of your account. You agree to indemnify, defend and hold harmless ECOCEAN, its affiliates and participants, and their officers, directors, employees, agents, licensors and suppliers, from and against any and all losses, expenses, damages and costs (including reasonable attorneys' fees) resulting from any violation of this Visitor Agreement or any activity related to your account (including negligent or wrongful conduct) by you or any other person accessing the ECOCEAN Library using your account. </p>
      <p><strong>MISCELLANEOUS</strong><br>
      In the event that any portion of this Visitor Agreement is found to be invalid or unenforceable for any reason, such invalidity or unenforceability shall not affect the enforceability or validity of any other portion of this Visitor Agreement, which shall remain in full force and effect and be construed as if the invalid or unenforceable portion were not part of the Visitor Agreement.</p>
      <p><strong>By using the ECOCEAN Library, you agree to abide by the terms of this Visitor Agreement.</strong> </p>
      <p>We hope you enjoy using the ECOCEAN Library, and we welcome suggestions for improvements. </p>
      <p>Thanks for your support! </p>
    <p>Last updated: July 17, 2007.</p></td>
  </tr>
</table>
</div>
	<!-- end maintext -->



<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
