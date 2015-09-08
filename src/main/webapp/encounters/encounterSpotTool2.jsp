
<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2008-2014 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.joda.time.format.DateTimeFormat,org.joda.time.format.DateTimeFormatter,org.joda.time.LocalDateTime ,org.ecocean.servlet.ServletUtilities,com.drew.imaging.jpeg.JpegMetadataReader, com.drew.metadata.Directory, com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         



<%


String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);


String imageID = request.getParameter("imageID");
SinglePhotoVideo spv = myShepherd.getSinglePhotoVideo(imageID);
String num = spv.getCorrespondingEncounterNumber();
Encounter enc = myShepherd.getEncounter(num);

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
//String fooDir = ServletUtilities.dataDir(context, rootWebappPath);
String baseDir = CommonConfiguration.getDataDirectoryName(context);
/*
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
File encounterDir = new File(encountersDir, num);
*/

String imgSrc = spv.asUrl(enc, baseDir);


//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


//handle translation
  //String langCode = "en";
String langCode=ServletUtilities.getLanguageCode(request);
    



//let's load encounters.properties
  //Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));

  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

	Properties collabProps = new Properties();
 	collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);



  //pageContext.setAttribute("num", num);



  //pageContext.setAttribute("set", encprops.getProperty("set"));
%>

<html>

<head prefix="og:http://ogp.me/ns#">
  <title><%=CommonConfiguration.getHTMLTitle(context) %> - <%=encprops.getProperty("encounter") %> <%=num%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  
  
<!-- social meta start -->
<meta property="og:site_name" content="<%=CommonConfiguration.getHTMLTitle(context) %> - <%=encprops.getProperty("encounter") %> <%=request.getParameter("number") %>" />

<link rel="canonical" href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=request.getParameter("number") %>" />

<meta itemprop="name" content="<%=encprops.getProperty("encounter")%> <%=request.getParameter("number")%>" />
<meta itemprop="description" content="<%=CommonConfiguration.getHTMLDescription(context)%>" />
<meta property="og:title" content="<%=CommonConfiguration.getHTMLTitle(context) %> - <%=encprops.getProperty("encounter") %> <%=request.getParameter("number") %>" />
<meta property="og:description" content="<%=CommonConfiguration.getHTMLDescription(context)%>" />

<meta property="og:url" content="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=request.getParameter("number") %>" />


<meta property="og:type" content="website" />

<!-- social meta end -->

  
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

	<style type="text/css">

		#target-img {
			width: 800px;
			pointer-events: none;
		}

		.imageTools-containerElement {
			background-color: #555;
			background-image: url(../images/checkerboard.png);
		}

		#tools {
			position: absolute;
			left: 20px;
			top: 200px;
			width: 200px;
			height: 400px;
			background-color: #AAA;
			border: solid 2px #888;
			padding: 8px;
		}

	</style>



<script type="text/javascript">



var encounterNumber = '<%=num%>';



function spotsSave() {
	var sp = itool.spotsVisible();
	console.log('sp = %o', sp);
	if (sp.length < 1) return;
//TODO verify we really have all we need (like when we updateSaveButton())

	$('#imageTools-buttons').hide();
	$('#imageTools-message').html('saving spot data...');

	var scale = itool.wCanvas.width / itool.wCanvas.offsetWidth;
	var pdata = 'number=' + encounterNumber;
	if (side == 'right') pdata += '&rightSide=true';
	var scount = 0;
	for (var i = 0 ; i < sp.length ; i++) {
		var xy = itool.xyOrigToWork(sp[i].xy);
		xy[0] *= scale;
		xy[1] *= scale;
		if (sp[i].type == 'spot') {
			pdata += '&spotx' + scount + '=' + xy[0];
			pdata += '&spoty' + scount + '=' + xy[1];
			scount++;
		} else {
			pdata += '&' + sp[i].type + 'x=' + xy[0];
			pdata += '&' + sp[i].type + 'y=' + xy[1];
		}
	}

console.log(pdata);


	$.ajax({
		url: '../SubmitSpots',
		data: pdata,
		success: function(d) { sendImage(d); },
		error: function(a,b,c) {
			console.error('%o %o %o', a,b,c);
			$('#imageTools-buttons').show();
			$('#imageTools-message').html('error saving');
		},
		type: 'POST'
	});
}


  </script>


    <link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Droid+Sans:regular,bold%7CInconsolata%7CPT+Sans:400,700">
    
</head>



<body <%if (request.getParameter("noscript") == null) {%>
  xonload="initialize()" <%}%>>
  

	<div id="wrapper">
		<div id="page">
			<jsp:include page="../header.jsp" flush="true">
  				<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
			</jsp:include>

<script src="http://code.jquery.com/ui/1.10.2/jquery-ui.js"></script>

<link rel="stylesheet" href="//code.jquery.com/ui/1.11.1/themes/smoothness/jquery-ui.css">
 <script src="//code.jquery.com/ui/1.11.1/jquery-ui.js"></script>
 

 
<script src="../javascript/jsfeat/jsfeat-min.js"></script>
<script src="../javascript/imageTools2.js"></script>
<script src="../javascript/encounterSpotTool2.js"></script>

			
<div id="tools">
	<select onChange="return modeMenuChange(this);">
		<option value="8">spots</option>
		<option value="1">move image</option>
		<option value="2">zoom</option>
		<option value="4">rotate</option>
		<option value="6">zoom & rotate</option>
	</select>
</div>

<div id="main">
	<img id="target-img" src="<%=imgSrc%>" onLoad="setTool()" />
</div>


<jsp:include page="../footer.jsp" flush="true"/>

</div>
<!-- end page -->

</div>

<!--end wrapper -->



</body>
</html>


