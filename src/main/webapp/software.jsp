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
	<div id="maincol-wide">

		<div id="maintext">
		  <h1 class="intro">Client Software </h1>
		</div>
		<p>Click on a link below to download the appropriate software client.</p>
			<p><a href="interconnect/mac/Interconnect.jar"><strong>Interconnect</strong></a><br />
			  <em>Purpose</em>: Spot mapping and submission to the Wildbook library<br />
		    <em>Requirements</em>: <a href="http://www.java.com/en/download/index.jsp">Java 1.5</a> or higher </p>
			<p><a href="http://www.getpaint.net/"><strong>Paint.NET</strong> (Windows only, external link) </a><br />
              <em>Purpose</em>: Image pre-processing <br />
              <em>Requirements</em>: <a href="http://msdn2.microsoft.com/en-us/netframework/aa731542.aspx">.NET 2.0</a></p>
			
			<p><a href="spot/spot.jnlp"><strong>Spot!</strong></a><br />
              <em>Purpose</em>: Mapping 2D images to 3D models for perspective correction <br />
              <em>Requirements</em>: <a href="http://www.java.com/en/download/index.jsp">Java 1.5</a> or higher   
              
              
              
              
              
              <script type="text/javascript" src="http://www.google.com/coop/cse/brand?form=searchbox_001757959497386081976%3An08dpv5rq-m"></script>
              <!-- Google CSE Search Box Ends -->
            </p>
            
            
            <p><a href="http://www.whaleshark.org/wiki/doku.php?id=how_to_join_sharkgrid"><strong>sharkGrid Client</strong></a><br />
	    			  <em>Purpose</em>: Contribute processing power to match patterns<br />
		    <em>Requirements</em>: <a href="http://www.java.com/en/download/index.jsp">Java 1.6</a> or higher</p>
            
            
	</div>
	<!-- end maintext -->
  </div><!-- end maincol -->
<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
