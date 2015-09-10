
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
			height: 500px;
			background-color: #AAA;
			border: solid 2px #888;
			padding: 8px;
		}

		.tool b.tool-head {
			color: #444;
			display: block;
			background-color: #DDD;
			padding: 4px 10px;
			margin: 15px 0px 8px -10px;
			width: 100%;
		}

		.tool label {
			font-size: 0.9em;
			margin-right: 10px;
		}

		#scan-tool {
			display: none;
		}

		#user-message {
			margin-top: -5px;
			text-align: center;
			font-size: 0.8em;
			padding: 5px;
			color: #888;
			height: 1.2em;
			background-color: #FFA;
			border-radius: 10px;
			width: 98%;
		}

		#image-message {
			pointer-events: none;
			position: absolute;
			right: 25px;
			top: 40px;
			background-color: rgba(0,0,0,0.5);
			color: #FFA;
			text-weight: bold;
			z-index: 2;
			font-size: 0.9em;
			padding: 2px 8px;
			border-radius: 4px;
			display: none;
		}

		#main {
			position: relative;
		}

		.detail {
			margin: 12px 0 7px 0;
			font-size: 0.8em;
		}
	</style>



<script type="text/javascript">



var encounterNumber = '<%=num%>';
var imageID = '<%=imageID%>';



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
 
 <jsp:include page="../header.jsp" flush="true"/>



  

<script src="http://code.jquery.com/ui/1.10.2/jquery-ui.js"></script>

<link rel="stylesheet" href="//code.jquery.com/ui/1.11.1/themes/smoothness/jquery-ui.css">
 <script src="//code.jquery.com/ui/1.11.1/jquery-ui.js"></script>
 

 
<script src="../javascript/jsfeat/jsfeat-min.js"></script>
<script src="../javascript/imageTools2.js"></script>
<script src="../javascript/encounterSpotTool2.js"></script>

			  <div class="container maincontent">
			
			
<div id="tools">
	<div class="tool"><b class="tool-head">edit mode</b>
		<div class="detail">hint: use first character for shortcut</div>
	<select id="edit-mode-menu" onChange="return modeMenuChange(this);">
		<option value="0">spots (only)</option>
		<option value="8">delete spots</option>
		<option value="1">move image</option>
		<option value="2">zoom</option>
		<option value="4">rotate</option>
		<option value="6">zoom & rotate</option>
	</select>
	</div>

	<div class="tool"><b class="tool-head">edge mode</b>
		<input type="radio" name="edge-mode" value="auto" checked /> auto-detect edge<br />
<div style="text-decoration: line-through; color: #666;" title="&#9888; not yet fully functional - use at own risk.">
		<input type="radio" name="edge-mode" value="manual" /> manually select points
</div>
		<div class="detail">edge detection settings:</div>
		<div id="edge-params"></div>
		<div class="detail">edge transparency:</div>
		<div id="edge-transparency"></div>
	</div>

	<div class="tool"><b class="tool-head">actions</b>
		<div><input id="save-button" type="button" value="save" disabled="disabled" onClick="save()" /></div>
		<div style="margin-top: 15px;" >
			<input type="button" value="reset all" onClick="resetAll()" />
			<input type="button" value="back to encounter" onClick="backToEncounter()" />
		</div>
	</div>

	<div id="scan-tool" class="tool"><b class="tool-head">scan for matches</b>
		<form target="_new" method="post" action="../ScanTaskHandler">
			<input name="action" type="hidden" id="action" value="addTask" /> 
			<input name="encounterNumber" type="hidden" value="<%=num%>" />
				<input name="rightSide" id="rightSide-left" type="radio" value="false" checked="checked" />
				<label for="rightSide-left">left side</label>
				<input name="rightSide" id="rightSide-right" type="radio" value="true" />
				<label for="rightSide-right">right side</label>
			<!-- input name="jdoql" type="text" id="jdoql" size="80" -->
			<input name="writeThis" type="hidden" id="writeThis" value="true" />
			<input name="cutoff" type="hidden" value="0.02" />
			<input name="scan" type="submit" id="scan" value="start scan" style="margin-top: 15px;" />
		</form>
	</div>

</div>

	<div id="user-message"></div>
	<div id="image-message"></div>
	<img id="target-img" src="<%=imgSrc%>" onLoad="setTool()" />

</div>
<jsp:include page="../footer.jsp" flush="true"/>




