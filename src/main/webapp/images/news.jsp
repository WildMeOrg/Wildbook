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
<link href="<%=CommonConfiguration.getCSSURLLocation() %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

<style type="text/css">
.thumbnail 
{
float:left;
}
</style>



</head>

<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher" value="<%=request.isUserInRole("researcher")%>"/>
	<jsp:param name="isManager" value="<%=request.isUserInRole("manager")%>"/>
	<jsp:param name="isReviewer" value="<%=request.isUserInRole("reviewer")%>"/>
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>"/>
</jsp:include>	
<div id="main">
	
		<div id="menu">
        <div class="module"></div>
		</div><!-- end menu -->
	
	<div id="maincol-wide">

	  <div id="maintext">
	    <h2> ECOCEAN News</h2>
	  </div>
	  <div>
	    <p>News from around the world</p>
	    <p>&nbsp;</p>
	  </div>
	  <p><img class="thumbnail" src="images/1news.jpg" alt="ECOCEAN" width="289" height="204"/></p>
      <blockquote>
        <p><a href=" http://www.fijitimes.com/story.aspx?id=169807">Predators underwater</a></p>
      </blockquote>
      <p>2 April 2011<br />
        HUMANS are not part of the normal diet for sharks, so you have nothing to fear when out swimming. American shark expert Dr Damien Chapman says sharks have been misunderstood historically ever since the movie Jaws.</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p><img class="thumbnail" src="images/2news.jpg" alt="ECOCEAN" width="289" height="204"/></p>
<blockquote>
  <p><a href="http://www.nwasianweekly.com/2011/03/man-eats-shark-with-california-on-its-way-to-banning-shark-fin-will-washington-follow-suit-3/">Man eats shark: With California on its way to banning shar fin, will Washington follow suit?</a></p>
</blockquote>
<p>31 March 2011<br />
  Certain restaurants in Hawaii are serving their last bowls of shark fin soup due to a newly enacted law, which requires restaurants to cook or dispose of their shark fin inventory.</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p><img class="thumbnail" src="images/3news.jpg" alt="ECOCEAN" width="289" height="204"/></p>
<blockquote>
  <blockquote>
    <p><a href="http://pr-usa.net/index.php?option=com_content&task=view&id=664371&Itemid=30">Indian Ocean Tuna Commission Fails Sharks</a></p>
  </blockquote>
  <p>30 March 2011 <br />
    Fishing nations reject even bare minimum measures for threatened species. Shark Advocates International is expressing deep disappointment at the failure of Parties to the Indian Ocean Tuna Commission (IOTC) to act on shark conservation proposals at their annual meeting this week.</p>
</blockquote>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p><img class="thumbnail" src="images/4news.jpg" alt="ECOCEAN" width="289" height="204"/></p>
<blockquote>
  <p><a href="http://www.abc.net.au/news/stories/2011/03/29/3176509.htm">Clock stopped on Shell’s application for Ningaloo</a></p>
</blockquote>
<p>29 March 2011 <br />
  The federal Environment Minister is asking for more information from the petroleum company Shell about its plans for an exploration well less than 50 kilometres from Ningaloo Reef marine park.</p>
<p>&nbsp;</p>

	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div>
<p>
  <!-- end page -->
  </div><!--end wrapper -->
</p>
<p>&nbsp;</p>
<p>&nbsp;</p>
</body>
</html>
