
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
         import="org.joda.time.format.DateTimeFormat,org.joda.time.format.DateTimeFormatter,org.joda.time.LocalDateTime ,org.ecocean.servlet.ServletUtilities,com.drew.imaging.jpeg.JpegMetadataReader, com.drew.metadata.Directory, com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*,
org.ecocean.media.MediaAsset,
org.ecocean.media.MediaAssetFactory,
org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         



<%


String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("encounterSpotTool.jsp");
myShepherd.beginDBTransaction();
int imageID = Integer.parseInt(request.getParameter("imageID"));
String imgSrc="";
String encNum="";
try{

	
	MediaAsset ma = MediaAssetFactory.load(imageID, myShepherd);
	if (ma == null) throw new Exception("unknown MediaAsset id=" + imageID);
	Encounter enc = null;
	for (Annotation ann : ma.getAnnotations()) {
		enc = Encounter.findByAnnotation(ann, myShepherd);
		encNum=enc.getCatalogNumber();
		if (enc != null) break;
	}
	if (enc == null) throw new Exception("could not find Encounter for MediaAsset id=" + imageID);
	
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
	
	imgSrc = ma.webURL().toString();
	
	
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
}
catch(Exception e){
	e.printStackTrace();
}
finally{
 	myShepherd.rollbackDBTransaction();
 	myShepherd.closeDBTransaction();
}

%>


<jsp:include page="../header.jsp" flush="true"/>

  <style type="text/css">
    <!--

#imageTools-wrapper {
	xdisplay: none;
	position: absolute;
	width: 800px;
	height: 600px;
	background-color: #AAA;
	border: solid 2px #444;
	z-index: 1;
}

#imageTools-wl-wrapper {
	position: absolute;
	top: 3px;
	right: 3px;
	max-height: 400px;
	xmax-width: 380px;
	width: 480px;
}

#imageTools-workCanvas, #imageTools-layerCanvas {
	position: absolute;
	left: 0;
	top: 0;
	xmax-height: 400px;
	width: 480px;
}

#imageTools-layerCanvas {
	pointer-events: none;
}

#imageTools-img-wrapper {
	position: absolute;
	top: 3px;
	left: 3px;
}

#imageTools-img-wrapper img, #imageTools-overlayCanvas {
	position: absolute;
	left: 0;
	top:0;
}

#imageTools-img-wrapper img {
	max-height: 400px;
	max-width: 280px;
}

#imageTools-control {
	margin: 6px;
	position: absolute;
	top: 402px;
	left: 0;
}

#imageTools-about {
	text-align: center;
	width: 300px;
}
#imageTools-about div {
	display: inline-block;
	padding: 2px 5px;
	color: #555;
}

.about-spots-in-region {
	color: #000 !important;
	font-weight: bold;
}


.instruction {
	position: absolute;
	top: -10px;
	background-color: rgba(255,255,100,0.8);
	border: solid rgba(200,200,200,0.8) 2px;
	z-index: 3;
	text-align: center;
	border-radius: 4px;
	padding: 1px 20px;
	left: 20%;
	width: 210px;
}

.spot-picker-radio.disabled {
	color: #555;
}
.spot-picker-radio.selected {
	color: #EE4;
}


#imageTools-message {
	min-height: 1.6em;
	padding: 6px 20px;
	font-size: 1.15em;
	color: #158;
}

#imageTools-buttons {
	padding: 5px;
}
#imageTools-buttons.input {
	margin: 0 6px;
}

 -->



</style>




<script type="text/javascript">



var encounterNumber = '<%=encNum %>';
var mediaAssetId = '<%=imageID%>';
var itool = false;
document.addEventListener('imageTools:workCanvas:update', function(ev) {
	updateSpotCounts();
	updateSaveButton();
});

function checkImage(imgEl) {
	if (imgEl[0].complete) {
		doImageSpots(imgEl);
	} else {
		console.info('waiting on img');
		imgEl.bind('load', function() { doImageSpots(imgEl); });
	}
}

function doImageSpots(imgEl) {
console.log(imgEl);
	if (!imgEl.length) {
		console.warn('no image for doImageSpots()');
		return;
	}

	//imgEl.parent().parent().css('position', 'relative').append($('#imageTools-wrapper'));
	//imgEl.after($('#imageTools-wrapper'));
	//$('#imageTools-wrapper').css({left: '-545px', display: 'inline-block'});

	//var fullImg = $('<img id="imageTools-img" src="' + imgEl.parent().attr('href') + '" />');
///////if (imgEl[0].complete) .....
	//////////imgEl.bind('load', function() { startImageTools(); });

	//remove old ones if any
	//$('#imageTools-img-wrapper canvas').remove();
	//$('#imageTools-img-wrapper img').remove();

	$('#imageTools-img-wrapper').append('<canvas id="imageTools-overlayCanvas"></canvas>');
	//$('#imageTools-img-wrapper').prepend(fullImg);

	startImageTools();
}


var spotTypes = [ 'ref1', 'ref2', 'ref3', 'spot' ];
var spotTypeNames = {
	ref1: '5th gill top',
	ref2: 'posterior pectoral',
	ref3: '5th gill bottom',
	spot: 'spot',
};
var side = false;

function startImageTools() {
	var opts = {
		toolsEnabled: {
			cropRotate: true,
			spotPicker: true
		},

		imgEl: document.getElementById('imageTools-img'),

		wCanvas: document.getElementById('imageTools-workCanvas'),
		oCanvas: document.getElementById('imageTools-overlayCanvas'),
		lCanvas: document.getElementById('imageTools-layerCanvas'),
		//infoEl: document.getElementById('cr-info'),
		//controlEl: document.getElementById('cr-info'),

		activeSpotType: 'ref1',
		activeSpotLabel: spotTypeNames.ref1,

		eventTarget: document,

/*
		spots: [
			{ xy: [30,50], type: "spot" },
			{ xy: [50,70], type: "mystery" }
		]
*/
	};


	$('#imageTools-control').css('top', (opts.imgEl.height + 10) + 'px');

	var sw = opts.imgEl.naturalWidth;
	var sh = opts.imgEl.naturalHeight;
	opts.sourceEl = document.createElement('canvas');
	opts.sourceEl.width = sw * 2;
	opts.sourceEl.height = sh * 2;
	var sctx = opts.sourceEl.getContext("2d");
	sctx.rect(0, 0, sw * 2, sh * 2);
	sctx.fillStyle = '#69D';
	sctx.fill();
	sctx.drawImage(opts.imgEl, 0, 0, sw, sh, sw / 2, sh / 2, sw, sh);
	opts.sourceElOffsetX = sw / 2;
	opts.sourceElOffsetY = sh / 2;
	opts.noBounds = true;

	itool = new ImageTools(opts);

	itool._myClick = function(ev) {
console.info(ev);
		var sc = itool.spotClick(ev);
console.log('spot click results: %o', sc);
		var sv = itool.spotsVisible();
		var msg = '<b>' + sv.length + ' spot' + ((itool.spots.length == 1) ? '' : 's') + ' in field</b>';
		msg += ' (' + itool.spots.length + ' spot' + ((itool.spots.length == 1) ? '' : 's') + ' total)';
		if (sc._removed) {
			$('#imageTools-spotType').val(sc.type);
			itool.activeSpotType = sc.type;
			itool.activeSpotLabel = spotTypeNames[sc.type];
			msg += '- spot removed';
		} else {
/*
			var s = spotTypes.indexOf(itool.activeSpotType) + 1;
			if (s >= spotTypes.length) s = spotTypes.length - 1;
			$('#imageTools-spotType').val(spotTypes[s]);
*/
			var t = nextAvailableSpotType(itool.activeSpotType);
			itool.activeSpotType = t;
			itool.activeSpotLabel = spotTypeNames[t];
			msg += '- spot added';
		}
		updateSpotPicker();
		updateSide();
		updateSaveButton();
		updateSpotCounts();
	};

	updateSide();
	updateSaveButton();
	updateSpotCounts();

	itool.wCanvas.addEventListener('click', itool._myClick, false);

	//mouseup *anywhere* should still complete things
	addEventListener('mouseup', function(ev) { itool.mup(ev); }, false);

	var h = '';
	for (var i = 0 ; i < spotTypes.length ; i++) {
		h += '<div class="spot-picker-radio"><input onClick="return spotTypeChange(this)" type="radio" name="spot-picker" id="spot-picker-' + i + '" value="' + spotTypes[i] + '" /><label for="spot-picker-' + i + '">' + spotTypeNames[spotTypes[i]] + '</label></div>';
	}
	$('#imageTools-spot-type-picker').html(h);

	updateSpotPicker();

	if (!$('#imageTools-message').length) {
		h = '<div id="imageTools-message"></div><div id="imageTools-buttons"><input disabled="disabled" onClick="return spotsSave()" id="imageTools-save-button" value="save" type="button" /><input type="button" value="cancel and return to encounter" onClick="return spotsCancel()" />';
		$('#imageTools-control').append(h);
	}
}


function nextAvailableSpotType(old) {
	var foundIt = false;
	for (var i = 0 ; i < spotTypes.length ; i++) {
		if (foundIt && spotTypeAvailable(spotTypes[i])) return spotTypes[i];
		if (spotTypes[i] == old) foundIt = true;
		if (spotTypeAvailable(old)) return old;  //still can do more of these
	}
	return false;
}


function spotTypeAvailable(t) {
	if (t == 'spot') return true;  //TODO any limit?
	for (var i = 0 ; i < itool.spots.length ; i++) {
		if (itool.spots[i].type == t) return false;
	}
	return true;
}


function userMessage(m) {
	$('#imageTools-userMessage').html(m);
}

function spotTypeChange(el) {
console.log('spotTypeChange on %o', el);
	itool.activeSpotType = el.value;
	itool.activeSpotLabel = spotTypeNames[itool.activeSpotType];
	updateSpotPicker();
	return true;
}

function updateSpotPicker() {
	$('input[value="' + itool.activeSpotType + '"]').prop('checked', 'checked');

	$('input[name="spot-picker"]').each(function(i,el) {
		var jel = $(el);
		jel.parent().removeClass('selected');
		if (spotTypeAvailable(jel.val())) {
			jel.removeAttr('disabled');
			jel.parent().removeClass('disabled');
		} else {
			jel.attr('disabled', 'disabled');
			jel.parent().addClass('disabled');
		}
	});

	$('input[value="' + itool.activeSpotType + '"]').parent().addClass('selected');
}



function updateSaveButton() {
	if (!itool) return;
	var sp = itool.spotsVisible();

	var hasAllNeeded = true;
	for (var i = 0 ; i < spotTypes.length ; i++) {
		if ((spotTypes[i] != 'spot') && spotTypeAvailable(spotTypes[i])) hasAllNeeded = false;
	}

	if (hasAllNeeded) {
		$('#imageTools-save-button').removeAttr('disabled');
	} else {
		$('#imageTools-save-button').attr('disabled', 'disabled');
	}
}

function updateSide() {
	if (!itool) return;
	var x1 = -1;
	var x2 = -1;
	for (var i = 0 ; i < itool.spots.length ; i++) {
		if (itool.spots[i].type == 'ref2') x1 = itool.spots[i].xy[0];
		if (itool.spots[i].type == 'ref3') x2 = itool.spots[i].xy[0];
	}
	if ((x1 < 0) || (x2 < 0) || (x1 == x2)) {
		side = false;
	} else if (x1 > x2) {
		side = 'left';
	} else {
		side = 'right';
	}
	var text = '';
	if (side) text = side + ' side';
	$('.about-side').html(text);
	return side;
}

function updateSpotCounts() {
	if (!itool || !itool.spots || (itool.spots.length < 1)) {
		$('.about-spots-in-region').html('no spots');
		$('.about-spots-total').html('');
		return;
	}

	$('.about-spots-total').html('total spots: ' + itool.spots.length);
	var sp = itool.spotsVisible();
	$('.about-spots-in-region').html('spots in region: ' + sp.length);
}

function spotsCancel() {
	document.location = 'encounter.jsp?number=' + encounterNumber;
	return;
	$('#imageTools-wrapper').hide();
	itool.wCanvas.removeEventListener('click', itool._myClick);
	itool = false;
}


function spotsSave() {
	var sp = itool.spotsVisible();
	console.log('sp = %o', sp);
	if (sp.length < 1) return;
//TODO verify we really have all we need (like when we updateSaveButton())

	$('#imageTools-spot-type-picker').hide();
	$('#imageTools-buttons').hide();
	$('#imageTools-message').html('saving spot data...');

	var scale = itool.wCanvas.width / itool.wCanvas.offsetWidth;
	var sdata = { encId: encounterNumber, mediaAssetId: mediaAssetId, rightSide: (side == 'right'), spots: [], refSpots: [] };
	sdata.imageToolValues = {
		scale: itool.scale,
		rotation: itool.rotation,
		rect: itool.rect,
		_finalScale: scale
	};
	sdata.imageData = itool.wCanvas.toDataURL('image/jpeg', 0.9).substring(23);

/*
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
*/
	for (var i = 0 ; i < sp.length ; i++) {
		var xy = itool.xyOrigToWork(sp[i].xy);
		xy[0] *= scale;
		xy[1] *= scale;
		var regex = new RegExp(/^ref(\d+)$/);
		var m = regex.exec(sp[i].type);
		if (sp[i].type == 'spot') {
			sdata.spots.push(xy);
		} else if (m) {  //refN
			var ind = m[1] - 1;  //("ref1" -> 0)
			sdata.refSpots[ind] = xy;
		} else {  //not sure if we will have other types, but....
			var key = sp[i].type + 'Spots';
			if (!sdata[key]) sdata[key] = [];
			sdata[key].push(xy);
		}
	}

console.log(sdata);


	$.ajax({
		url: '../SubmitSpotsAndImage',
		data: JSON.stringify(sdata),
		contentType: 'application/javascript',
		dataType: 'json',
		success: function(d) { allGood(d); },
		error: function(a,b,c) {
			console.error('%o %o %o', a,b,c);
			$('#imageTools-buttons').show();
			$('#imageTools-message').html('error saving');
		},
		type: 'POST'
	});
}


/*  old non-MA cruft
function sendImage(d) {
	console.info('SUCCESS saving spots: %o', d);
	$('#imageTools-message').html('saving image...');
	var imgData = itool.wCanvas.toDataURL('image/jpeg', 0.9).substring(23);
		var data = 'number=' + encounterNumber + '&' + ((side == 'right') ? 'rightSide=true' : '') + '&imageContents=' + encodeURIComponent(imgData);
//console.log(data); return;
	$.ajax({
		url: '../EncounterAddSpotFile',
		data: data,
		success: function(d) { allGood(d); },
		error: function(a,b,c) {
			console.error('%o %o %o', a,b,c);
			$('#imageTools-buttons').show();
			$('#imageTools-message').html('error saving');
		},
		type: 'POST'
	});
}
*/


function allGood(d) {
	if (!d.success) {
		console.error("error api return %o", d);
		$('#imageTools-buttons').show();
		$('#imageTools-message').html(d.error || 'error saving');
		return;
	}
	console.info('SUCCESS saving image: %o', d);
	$('#imageTools-message').html('spot data and image saved.<div style="margin-top: 7px;"><input type="button" value="start ScanTask" onClick="var win = window.open(\'../ScanTaskHandler?action=addTask&encounterNumber=' + encounterNumber + '&rightSide=' + ((side == 'right') ? 'true' : 'false') + '&cutoff=0.02&writeThis=true\', \'_blank\'); win.focus(); return true;" /> <input type="button" value="return to encounter" onClick="spotsCancel();" /></div>');
}

  </script>




<!--added below for improved map selection -->





<body <%if (request.getParameter("noscript") == null) {%>
  xonload="initialize()" <%}%>>


 
<script src="../javascript/imageTools.js"></script>
<script>
$(document).ready(function() {
	checkImage($('#imageTools-img'));
});
</script>

		<div class="container maincontent">
		
		
<div id="imageTools-wrapper">
	<div id="imageTools-wl-wrapper">
		<div class="instruction">Place spots here</div>
		<canvas id="imageTools-workCanvas"></canvas>
		<canvas id="imageTools-layerCanvas"></canvas>
	</div>


	<div id="imageTools-img-wrapper">
		<div class="instruction">Select region here</div>
		<img id="imageTools-img" src="<%=imgSrc%>" />
	</div>

	<div id="imageTools-control">
		<div id="imageTools-about">
			<div class="about-spots-in-region"></div>
			<div class="about-spots-total"></div>
			<div class="about-side"></div>
		</div>

		<div id="imageTools-spot-type-picker"></div>

	</div>


</div>
<div style="height: 800px;"></div>

</div>




<jsp:include page="../footer.jsp" flush="true"/>



