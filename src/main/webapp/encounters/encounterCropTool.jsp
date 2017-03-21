
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
         import="org.joda.time.format.DateTimeFormat,
org.ecocean.media.MediaAsset,
org.ecocean.media.MediaAssetFactory,
org.joda.time.format.DateTimeFormatter,org.joda.time.LocalDateTime ,org.ecocean.servlet.ServletUtilities,com.drew.imaging.jpeg.JpegMetadataReader, com.drew.metadata.Directory, com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         



<%


String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);


int imageID = Integer.parseInt(request.getParameter("imageID"));
MediaAsset ma = MediaAssetFactory.load(imageID, myShepherd);
if (ma == null) throw new Exception("unknown MediaAsset id=" + imageID);
Encounter enc = Encounter.findByMediaAsset(ma, myShepherd);
if (enc == null) throw new Exception("could not find Encounter for MediaAsset id=" + imageID);


//TODO we might(?) want to get the _mid sized image at some point??


//allow passing of dorsal-ness by way of param:
boolean passedDorsal = (request.getParameter("isDorsalFin") != null);
boolean isDorsalFin = false;
if (passedDorsal) isDorsalFin = !(request.getParameter("isDorsalFin").equals("false") || request.getParameter("isDorsalFin").equals("0"));

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

String imgSrc = ma.webURL().toString();


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
			width: 97%;
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
			height: 750px;
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

		#image-label {
			position: absolute;
			border-radius: 4px;
			padding: 2px 10px;
			background-color: rgba(255,255,50,0.9);
			display: none;
			pointer-events: none;
		}

		#user-message {
			margin-top: -30px;
			margin-bottom: 20px;
			text-align: center;
			font-size: 0.8em;
			padding: 5px;
			color: #888;
			background-color: #FFA;
			border-radius: 10px;
			width: 98%;
		}

		#image-message {
			pointer-events: none;
			position: absolute;
			right: 25px;
			bottom: 10px;
			background-color: rgba(0,0,0,0.5);
			color: #FFA;
			font-weight: bold;
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

		#image-workarea {
			position: relative;
			margin-left: 125px;
			width: 97%;
		}

		#edge-counts {
			pointer-events: none;
			position: absolute;
			right: 25px;
			bottom: 50px;
			padding: 1px 4px;
			z-index: 2;
			font-size: 0.8em;
			font-weight: bold;
			background-color: #666;
			color: #EEE;
			border-radius: 3px;
			display: none;
		}
	</style>



<script type="text/javascript">



var encounterNumber = '<%=enc.getCatalogNumber()%>';
var imageID = '<%=imageID%>';
var passedDorsal = <%=passedDorsal%>;
var isDorsalFin = <%=isDorsalFin%>;



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


    <link rel="stylesheet" href="//fonts.googleapis.com/css?family=Droid+Sans:regular,bold%7CInconsolata%7CPT+Sans:400,700">
 
 <jsp:include page="../header.jsp" flush="true"/>



  

<script src="//code.jquery.com/ui/1.10.2/jquery-ui.js"></script>

<link rel="stylesheet" href="//code.jquery.com/ui/1.11.1/themes/smoothness/jquery-ui.css">
 <script src="//code.jquery.com/ui/1.11.1/jquery-ui.js"></script>
 

 
<script src="../javascript/jsfeat/jsfeat-min.js"></script>
<script src="../javascript/imageTools2.js"></script>
<script src="../javascript/encounterSpotTool2.js"></script>
<script src="../javascript/astar.js"></script>
<script>
jQuery(document).ready(function() {
	setMode(1);
});
</script>

			  <div class="container maincontent">
			
			
<div id="tools">
	<div class="tool"><b class="tool-head">edit mode</b>
		<div class="detail">hint: use first character for shortcut</div>
	<select id="edit-mode-menu" onChange="return modeMenuChange(this);">
		<option value="1">move image</option>
		<option value="2">zoom</option>
		<option value="4">rotate</option>
		<option value="6">zoom & rotate</option>
	</select>
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
			<input name="encounterNumber" type="hidden" value="<%=enc.getCatalogNumber()%>" />
			<!-- input name="jdoql" type="text" id="jdoql" size="80" -->
			<input name="writeThis" type="hidden" id="writeThis" value="true" />
			<input name="cutoff" type="hidden" value="0.02" />
			<input name="scan" type="submit" id="scan" value="start scan" style="margin-top: 15px;" />
		</form>
	</div>

</div>

<div id="image-workarea">
	<div style="display: none;" id="user-message"></div>
	<div id="image-message"></div>
	<div id="edge-counts"></div>
	<img id="target-img" src="<%=imgSrc%>" onLoad="setTool()" />
</div>

</div>

<div id="dorsal-dialog" style="display:none;" >
Is this an image of a <b>fluke (tail)</b> or a <b>dorsal fin</b>?
</div>

<jsp:include page="../footer.jsp" flush="true"/>




