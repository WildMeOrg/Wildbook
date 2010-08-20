<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	//check what language is requested
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	//set up the file input stream
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));

	

	
%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle()%></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription()%>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords()%>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor()%>" />
<link href="<%=CommonConfiguration.getCSSURLLocation()%>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon()%>" />
</head>

<link rel="shortcut icon" href="images/favicon.ico" />

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
	
	<div id="maincol-wide-solo">

		<div id="maintext">
		  <h1 class="intro">Third Party Software</h1>
		</div>
			
		<p>The following third party software is used with permission and/or under an appropriate license within the Shepherd Project Framework.</p>
		<p><strong>Third Party Commercial Licenses (used with permission and/or under non-commercial guidelines)</strong></p>
		<ul>
		  <li>Dynamic Image as included in this software is used with the permission of its developer Guang Yang and licensed only for use with the Shepherd Project Framework for non-commercial, mark-recapture studies.</li>
	    <li>CSSPlay for dropdown navigation menus is used under non-commercial guidelines. <a href="http://www.cssplay.co.uk/menus/simple_vertical.html">http://www.cssplay.co.uk/menus/simple_vertical.html</a></li>
		<li>Highslide JS 4.1.9 is used under non-commercial guidelines. <a href="http://highslide.com/">http://highslide.com/</a></li>
		</ul>
		<p><strong>Open Source Components </strong></p>
		<ul>
		<li>DataNucleus Access Platform 2.1 is used for object-relational mapping and persistence under the Apache 2 License.<a href="http://www.datanucleus.org%20">http://www.datanucleus.org</a></li>
		<li>Joda Time for date manipulation and standardization is used under the Apache 2 License.<a href="http://joda-time.sourceforge.net/">http://joda-time.sourceforge.net/</a></li>
		<li>Crystal Clear icons are used under the GNU Lesser General Public License. <a href="http://commons.wikimedia.org/wiki/Crystal_Clear">http://commons.wikimedia.org/wiki/Crystal_Clear</a></li>
		<li>A Derby database is embedded for default usage and is available under the Apache 2 License. <a href="http://db.apache.org/derby/">http://db.apache.org/derby/</a></li>
		<li>Project Rome is used for writing out Atom feeds. <a href="https://rome.dev.java.net/">https://rome.dev.java.net/</a></li>
		<li>A Java version of the <a href="http://www.reijns.com/i3s/">I3S 1.0 algorithm</a> is used under the GPL v2 license as one of two spot pattern recognition algorithms.</li>
		<li>Metadata Extractor 2.3.1 for EXIF image data extraction. <a href="http://www.drewnoakes.com/code/exif/">http://www.drewnoakes.com/code/exif/</a></li>
		<li>Sanselan 0.97 for image size detection. <a href="http://incubator.apache.org/sanselan/site/index.html">http://incubator.apache.org/sanselan/site/index.html</a></li>
		</ul>

	  </div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
