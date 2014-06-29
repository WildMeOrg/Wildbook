<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.ArrayList,org.ecocean.servlet.ServletUtilities,org.ecocean.*,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, java.io.IOException" %>




<%

//grab a gridManager
GridManager gm=GridManagerFactory.getGridManager();
int numProcessors = gm.getNumProcessors();
int numWorkItems = gm.getIncompleteWork().size();

String context="context0";
context=ServletUtilities.getContext(request);


  Shepherd myShepherd = new Shepherd(context);
  
  	//check usernames and passwords
	myShepherd.beginDBTransaction();
  	//ArrayList<User> users=myShepherd.getAllUsers();
  	
  	/*
  	if(users.size()==0){
  		String salt=ServletUtilities.getSalt().toHex();
        String hashedPassword=ServletUtilities.hashAndSaltPassword("tomcat123", salt);
        //System.out.println("Creating default hashed password: "+hashedPassword+" with salt "+salt);
        
        
  		User newUser=new User("tomcat",hashedPassword,salt);
  		myShepherd.getPM().makePersistent(newUser);
  		System.out.println("Creating tomcat user account...");
  		
  	  	ArrayList<Role> roles=myShepherd.getAllRoles();
  	  	if(roles.size()==0){
  	  	System.out.println("Creating tomcat roles...");
  	  		
  	  		Role newRole1=new Role("tomcat","admin");
  	  		myShepherd.getPM().makePersistent(newRole1);
	  		Role newRole4=new Role("tomcat","destroyer");
	  		myShepherd.getPM().makePersistent(newRole4);
	  		
	  		System.out.println("Creating tomcat user account...");
  	  	}
  	}
  	*/


  	myShepherd.commitDBTransaction();

//setup our Properties object to hold all properties
	
	//test comment
	
	//language setup
	String langCode=ServletUtilities.getLanguageCode(request);

	Properties props=ShepherdProperties.getProperties("overview.properties", langCode,context);
	
	//adding a comment here
	
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
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context) %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context) %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request, context) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>" />

<link rel="shortcut icon" href="images/favicon.ico" />
<style type="text/css">
<!--


table.adopter {
	border-width: 0px 0px 0px 0px;
	border-spacing: 0px;
	border-style: solid solid solid solid;
	border-color: black black black black;
	border-collapse: separate;
	
}

table.adopter td {
	border-width: 1px 1px 1px 1px;
	padding: 3px 3px 3px 3px;
	border-style: none none none none;
	border-color: gray gray gray gray;
	background-color: #D7E0ED;
	-moz-border-radius: 0px 0px 0px 0px;
	font-size: 12px;
	color: #330099;
}

table.adopter td.name {
	font-size: 12px;
	text-align:center;
	background-color: #D7E0ED;
	
}

table.adopter td.image {
	padding: 0px 0px 0px 0px;
	
}
.style4 {color: #000000}

-->
</style>
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

	<div id="maincol-wide">
		 
		 <div>
		 	<a href="<%=submitPath%>"><img src="images/stumpy_banner.png" title="Report a whale shark encounter!" alt="Report a whale shark encounter! - photograph courtesy of Amber Triglone" border="0" />
			</a>
		 </div>
<br />
	    <div id="maintext"><%=overview_maintext%>
		 		 <p>
		 		 <span style="margin: 0px 0px 1em 0px;" #invalid_attr_id="0px"><strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />53000+ photos collected<br />
		       <strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />25000+  sighting reports</strong><br />
		       <strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />5200+ whale sharks collaboratively tagged</strong><br />
			   
			   <span style="margin: 0px 0px 1em 0px;" #invalid_attr_id="0px"><strong><strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />4000+ data contributors</strong></strong></span><br />
		       <strong><img src="images/lilshark2.gif" width="13" align="absmiddle" />365 research days/year</strong><br />
		       
			   <p align="center"><strong><a href="adoptashark.jsp">Please consider adopting a shark to support our mission!</a></strong></p>
		       </strong></span></p>
		 
	    </div>
        
        
		 
		 <div id="1000tagged">
		   <h1 class="intro">Growing Success </h1>
		   <p align="center"><img src="images/usablereportsgif.gif" /></p>

		 </div>


		


		<div id="maintext"><h1 class="intro">Contact Us</h1>
		<p class="caption">Wild Me is always looking for opportunities to better tell the story of whale sharks and the growing body of research and discoveries made possible by dedicated scientists, volunteers, and the general public.</p>
		<p class="caption"><a href="contactus.jsp">Please contact us with your questions.</a></p>
		</div>
		
	
								
		
				<div class="module">
		    	<h1 class="intro">Data Sharing</h1>
		    	<p><a href="http://data.gbif.org/datasets/provider/261"><img src="images/gbif.gif" border="0" alt="Data sharing with the Global Biodiversity Information Facility"/></a>
				<a href="http://ecovision.mit.edu/~ecovision/"><img src="http://web.mit.edu/img/google/google-mithome-logo.gif" alt="Data sharing with the Massachusetts Institute of Technology" border="0" />
				</a>
				<a href="http://www.iobis.org/"><img src="images/OBIS_logo.gif" alt="Data sharing with the Ocean Biogeographic Information System" border="0" />
				</a>
				<a href="ttp://www.coml.org/"><img src="images/coml.gif" alt="Data sharing with the census of marine life" border="0" />
				</a></p>
			</div>
			
							<div class="module">
		    	<h1 class="intro">Development</h1>
		    	<p class="caption"><img border="1"  style="padding:2px; border:1px; margin:2px;" align="left" src="http://www.wildme.org/wildbook/lib/exe/fetch.php?w=150&amp;media=jason_profile.jpg" class="media" title="jason_profile.jpg" alt="jason_profile.jpg" width="75">Wildbook for Whale Sharks is maintained and developed by <a href="https://www.facebook.com/holmbergius">Jason Holmberg (Information Architect)</a> with significant support and input from the <a href="whoAreWe.jsp#collaborators">research community</a>.</p>
			</div>
					

	  </div><!-- end maincol -->
	 <div id="rightcol">


			 <div class="module">
		
         <div id="supporters"><h3>Our Supporters</h3>
		<p class="caption">We gratefully acknowledge the financial support of the following organizations and individuals, without which continuing operation of this Wildbook would not be possible.</p>
		<table>
			<tr>
			
				<tr>
				<td class="image"><a href="http://www.qatarwhalesharkproject.com"><img src="images/QWSP_Logo.png" width="150" alt="Qatar Whale Shark Research Project" border="0"/></a>
			</td>
			</tr>
				<td><img src="images/Siren_Fleet.jpg" width="150" alt="Siren Fleet" border="0"/></td>
				</tr>
			
			
		</table>
		</div>
				
<%
Adoption ad=myShepherd.getRandomAdoption();
if(ad!=null){
%>
<table class="adopter" bgcolor="#D7E0ED" style="background-color:#D7E0Ed " width="190px">
<tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="images/meet-adopter-frame.gif" /></a></td></tr>
			 <tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=ad.getID()%>/thumb.jpg" /></a></td></tr>
			 
			 <tr><td class="name">
			 	<center><strong><font color="#282460" size="+1"><%=ad.getAdopterName()%></font></strong></center>
			 </td></tr>
		<tr><td>&nbsp;</td></tr>
		<tr><td><table cellpadding="1" border="0"><tr><td>Adopted shark: 
		
		<%
		if((ad.getMarkedIndividual()!=null)&&(!ad.getMarkedIndividual().equals(""))) {
		%>
		<a href="individuals.jsp?number=<%=ad.getMarkedIndividual()%>"><%=ad.getMarkedIndividual()%></a>
		<%
		}
		else if((ad.getEncounter()!=null)&&(!ad.getEncounter().equals(""))) {
		%>
		<a href="encounters/encounter.jsp?number=<%=ad.getEncounter()%>"><%=ad.getEncounter()%></a>
		<%
		}
		%>
		
		</td></tr></table></td></tr>
		<tr><td>&nbsp;</td></tr>
		
		<%
			if((ad.getAdopterQuote()!=null)&&(!ad.getAdopterQuote().equals(""))){
		%>
		
			 <tr><td><table cellpadding="1" border="0"><tr><td>Why are shark research and conservation important?</td></tr></table></td></tr>
			 <tr><td><table cellpadding="1" border="0"><tr><td><em>"<%=ad.getAdopterQuote()%>"</em></td></tr></table></td></tr>
			 <tr><td>&nbsp;</td></tr>
			 <tr><td><table cellpadding="1" border="0"><tr><td><span class="caption">Your adoption gift to Wild Me is tax deductible in the United States. <a href="adoptashark.jsp">Click here to learn more.</a></span></td></tr></table></td></tr>
			 
			 <%
			 }
			 %>
			
			 <tr><td class="image"><a href="http://www.whaleshark.org/adoptashark.jsp"><img border="0" src="images/meet-adopter-frame-bottom.gif" /></a></td></tr>
			 
			 
			 </tr>

		  </table>
<%
}
%>


</div>


		
	    <div class="module">
			 <h3><%=last_sightings%></h3>
			 <script language="JavaScript" src="http://feed2js.org//feed2js.php?src=http%3A%2F%2F<%=CommonConfiguration.getURLLocation(request)%>%2Frss.xml&amp;utf=y" type="text/javascript"></script>
			 <noscript>
			 <a href="http://feed2js.org//feed2js.php?src=http%3A%2F%2F<%=CommonConfiguration.getURLLocation(request)%>%2Frss.xml&amp;utf=y&amp;html=y">View RSS feed</a>
			 </noscript>
			 <p align="left"><a href="rss.xml"><img src="images/rssfeed.gif" width="80" height="15" border="0" alt="Whaleshark.org RSS News Feed" /></a></p>
			 <p align="left">
			 <a href="atom.xml"><img src="images/atom-feed-icon.gif" border="0" alt="ATOM News Feed" /></a></p>
	    </div>
	
 
	

	

	
	</div><!-- end rightcol -->
	
</div><!-- end main -->
<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
<script type='text/javascript'>
// Conversion Name: ACC_Ecocean_Landing
// INSTRUCTIONS 
// The Conversion Tags should be placed at the top of the <BODY> section of the HTML page. 
// In case you want to ensure that the full page loads as a prerequisite for a conversion 
// being recorded, place the tag at the bottom of the page. Note, however, that this may 
// skew the data in the case of slow-loading pages and in general not recommended. 
//
// NOTE: It is possible to test if the tags are working correctly before campaign launch 
// as follows:  Browse to http://bs.serving-sys.com/BurstingPipe/adServer.bs?cn=at, which is 
// a page that lets you set your local machine to 'testing' mode.  In this mode, when 
// visiting a page that includes an conversion tag, a new window will open, showing you 
// the data sent by the conversion tag to the MediaMind servers. 
// 
// END of instructions (These instruction lines can be deleted from the actual HTML)
var ebRand = Math.random()+'';
ebRand = ebRand * 1000000;
//<![CDATA[ 
document.write('<scr'+'ipt src="HTTP://bs.serving-sys.com/BurstingPipe/ActivityServer.bs?cn=as&amp;ActivityID=104657&amp;rnd=' + ebRand + '"></scr' + 'ipt>');
//]]>
</script>
<noscript>
<img width="1" height="1" style="border:0" src="HTTP://bs.serving-sys.com/BurstingPipe/ActivityServer.bs?cn=as&amp;ActivityID=104657&amp;ns=1"/>
</noscript>

</body>
</html>
