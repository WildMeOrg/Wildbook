
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

    .style2 {
      color: #000000;
      font-size: small;
    }

    .style3 {
      font-weight: bold
    }

    .style4 {
      color: #000000
    }

    table.adopter {
      border-width: 1px 1px 1px 1px;
      border-spacing: 0px;
      border-style: solid solid solid solid;
      border-color: black black black black;
      border-collapse: separate;
      background-color: white;
    }

    table.adopter td {
      border-width: 1px 1px 1px 1px;
      padding: 3px 3px 3px 3px;
      border-style: none none none none;
      border-color: gray gray gray gray;
      background-color: white;
      -moz-border-radius: 0px 0px 0px 0px;
      font-size: 12px;
      color: #330099;
    }

    table.adopter td.name {
      font-size: 12px;
      text-align: center;
    }

    table.adopter td.image {
      padding: 0px 0px 0px 0px;
    }

    div.scroll {
      height: 200px;
      overflow: auto;
      border: 1px solid #666;
      background-color: #ccc;
      padding: 8px;
    }

    -->




</style>




<script type="text/javascript">



var encounterNumber = '<%=num%>';
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
	ref1: 'left tip',
	ref2: 'notch',
	ref3: 'right tip',
	spot: 'point',
};
var side = false;
var sctx;

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
	sctx = opts.sourceEl.getContext("2d");
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


function allGood(d) {
	console.info('SUCCESS saving image: %o', d);
	$('#imageTools-message').html('spot data and image saved.<div style="margin-top: 7px;"><input type="button" value="start ScanTask" onClick="var win = window.open(\'../ScanTaskHandler?action=addTask&encounterNumber=' + encounterNumber + '&rightSide=' + ((side == 'right') ? 'true' : 'false') + '&cutoff=0.02&writeThis=true\', \'_blank\'); win.focus(); return true;" /> <input type="button" value="return to encounter" onClick="spotsCancel();" /></div>');
}

  </script>


    <link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Droid+Sans:regular,bold%7CInconsolata%7CPT+Sans:400,700">
    <link rel="stylesheet" href="https://inspirit.github.io/jsfeat/css/bootstrap.css">
    <link rel="stylesheet" href="https://inspirit.github.io/jsfeat/css/jsfeat.css">
    <script type="text/javascript" async="" src="../javascript/jsfeat/ga.js"></script>
    <style type="text/css">
    	.dg ul{list-style:none;margin:0;padding:0;width:100%;clear:both}.dg.ac{position:fixed;top:0;left:0;right:0;height:0;z-index:0}.dg:not(.ac) .main{overflow:hidden}.dg.main{-webkit-transition:opacity 0.1s linear;-o-transition:opacity 0.1s linear;-moz-transition:opacity 0.1s linear;transition:opacity 0.1s linear}.dg.main.taller-than-window{overflow-y:auto}.dg.main.taller-than-window .close-button{opacity:1;margin-top:-1px;border-top:1px solid #2c2c2c}.dg.main ul.closed .close-button{opacity:1 !important}.dg.main:hover .close-button,.dg.main .close-button.drag{opacity:1}.dg.main .close-button{-webkit-transition:opacity 0.1s linear;-o-transition:opacity 0.1s linear;-moz-transition:opacity 0.1s linear;transition:opacity 0.1s linear;border:0;position:absolute;line-height:19px;height:20px;cursor:pointer;text-align:center;background-color:#000}.dg.main .close-button:hover{background-color:#111}.dg.a{float:right;margin-right:15px;overflow-x:hidden}.dg.a.has-save ul{margin-top:27px}.dg.a.has-save ul.closed{margin-top:0}.dg.a .save-row{position:fixed;top:0;z-index:1002}.dg li{-webkit-transition:height 0.1s ease-out;-o-transition:height 0.1s ease-out;-moz-transition:height 0.1s ease-out;transition:height 0.1s ease-out}.dg li:not(.folder){cursor:auto;height:27px;line-height:27px;overflow:hidden;padding:0 4px 0 5px}.dg li.folder{padding:0;border-left:4px solid rgba(0,0,0,0)}.dg li.title{cursor:pointer;margin-left:-4px}.dg .closed li:not(.title),.dg .closed ul li,.dg .closed ul li > *{height:0;overflow:hidden;border:0}.dg .cr{clear:both;padding-left:3px;height:27px}.dg .property-name{cursor:default;float:left;clear:left;width:40%;overflow:hidden;text-overflow:ellipsis}.dg .c{float:left;width:60%}.dg .c input[type=text]{border:0;margin-top:4px;padding:3px;width:100%;float:right}.dg .has-slider input[type=text]{width:30%;margin-left:0}.dg .slider{float:left;width:66%;margin-left:-5px;margin-right:0;height:19px;margin-top:4px}.dg .slider-fg{height:100%}.dg .c input[type=checkbox]{margin-top:9px}.dg .c select{margin-top:5px}.dg .cr.function,.dg .cr.function .property-name,.dg .cr.function *,.dg .cr.boolean,.dg .cr.boolean *{cursor:pointer}.dg .selector{display:none;position:absolute;margin-left:-9px;margin-top:23px;z-index:10}.dg .c:hover .selector,.dg .selector.drag{display:block}.dg li.save-row{padding:0}.dg li.save-row .button{display:inline-block;padding:0px 6px}.dg.dialogue{background-color:#222;width:460px;padding:15px;font-size:13px;line-height:15px}#dg-new-constructor{padding:10px;color:#222;font-family:Monaco, monospace;font-size:10px;border:0;resize:none;box-shadow:inset 1px 1px 1px #888;word-wrap:break-word;margin:12px 0;display:block;width:440px;overflow-y:scroll;height:100px;position:relative}#dg-local-explain{display:none;font-size:11px;line-height:17px;border-radius:3px;background-color:#333;padding:8px;margin-top:10px}#dg-local-explain code{font-size:10px}#dat-gui-save-locally{display:none}.dg{color:#eee;font:11px 'Lucida Grande', sans-serif;text-shadow:0 -1px 0 #111}.dg.main::-webkit-scrollbar{width:5px;background:#1a1a1a}.dg.main::-webkit-scrollbar-corner{height:0;display:none}.dg.main::-webkit-scrollbar-thumb{border-radius:5px;background:#676767}.dg li:not(.folder){background:#1a1a1a;border-bottom:1px solid #2c2c2c}.dg li.save-row{line-height:25px;background:#dad5cb;border:0}.dg li.save-row select{margin-left:5px;width:108px}.dg li.save-row .button{margin-left:5px;margin-top:1px;border-radius:2px;font-size:9px;line-height:7px;padding:4px 4px 5px 4px;background:#c5bdad;color:#fff;text-shadow:0 1px 0 #b0a58f;box-shadow:0 -1px 0 #b0a58f;cursor:pointer}.dg li.save-row .button.gears{background:#c5bdad url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAsAAAANCAYAAAB/9ZQ7AAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAQJJREFUeNpiYKAU/P//PwGIC/ApCABiBSAW+I8AClAcgKxQ4T9hoMAEUrxx2QSGN6+egDX+/vWT4e7N82AMYoPAx/evwWoYoSYbACX2s7KxCxzcsezDh3evFoDEBYTEEqycggWAzA9AuUSQQgeYPa9fPv6/YWm/Acx5IPb7ty/fw+QZblw67vDs8R0YHyQhgObx+yAJkBqmG5dPPDh1aPOGR/eugW0G4vlIoTIfyFcA+QekhhHJhPdQxbiAIguMBTQZrPD7108M6roWYDFQiIAAv6Aow/1bFwXgis+f2LUAynwoIaNcz8XNx3Dl7MEJUDGQpx9gtQ8YCueB+D26OECAAQDadt7e46D42QAAAABJRU5ErkJggg==) 2px 1px no-repeat;height:7px;width:8px}.dg li.save-row .button:hover{background-color:#bab19e;box-shadow:0 -1px 0 #b0a58f}.dg li.folder{border-bottom:0}.dg li.title{padding-left:16px;background:#000 url(data:image/gif;base64,R0lGODlhBQAFAJEAAP////Pz8////////yH5BAEAAAIALAAAAAAFAAUAAAIIlI+hKgFxoCgAOw==) 6px 10px no-repeat;cursor:pointer;border-bottom:1px solid rgba(255,255,255,0.2)}.dg .closed li.title{background-image:url(data:image/gif;base64,R0lGODlhBQAFAJEAAP////Pz8////////yH5BAEAAAIALAAAAAAFAAUAAAIIlGIWqMCbWAEAOw==)}.dg .cr.boolean{border-left:3px solid #806787}.dg .cr.function{border-left:3px solid #e61d5f}.dg .cr.number{border-left:3px solid #2fa1d6}.dg .cr.number input[type=text]{color:#2fa1d6}.dg .cr.string{border-left:3px solid #1ed36f}.dg .cr.string input[type=text]{color:#1ed36f}.dg .cr.function:hover,.dg .cr.boolean:hover{background:#111}.dg .c input[type=text]{background:#303030;outline:none}.dg .c input[type=text]:hover{background:#3c3c3c}.dg .c input[type=text]:focus{background:#494949;color:#fff}.dg .c .slider{background:#303030;cursor:ew-resize}.dg .c .slider-fg{background:#2fa1d6}.dg .c .slider:hover{background:#3c3c3c}.dg .c .slider:hover .slider-fg{background:#44abda}
	</style>
	<script src="../javascript/jsfeat/webfont.js" type="text/javascript" async=""></script>

</head>



<body <%if (request.getParameter("noscript") == null) {%>
  xonload="initialize()" <%}%>>
  
   <script type="text/javascript" src="../javascript/jsfeat/jsfeat-min.js"></script>
    <script type="text/javascript" src="../javascript/jsfeat/compatibility.js"></script>
    <script type="text/javascript" src="../javascript/jsfeat/profiler.js"></script>
    <script type="text/javascript" src="../javascript/jsfeat/dat.gui.min.js"></script>
    <script type="text/javascript" src="../javascript/jsfeat/custom.jsfeat.js"></script>
  <script type="text/javascript">
      
      //var patternImage = document.getElementById("imageTools-img");
      //var patternImage2 = document.getElementById("patternImage2");
      
      function getURLParameter(name){
         if(name=(new RegExp('[?&]'+encodeURIComponent(name)+'=([^&]*)')).exec(location.search))
          return decodeURIComponent(name[1]);
      }
                 

            var gui,options,canvasWidth,canvasHeight;
            var img_u8, img_u8_smooth, screen_corners, num_corners, screen_descriptors;
            var pattern_corners, pattern_descriptors, pattern_preview;
            var matches, homo3x3, match_mask;
            var num_train_levels = 4;


      var stat = new profiler();
      
      
      
      function render_corners(corners, count, img, step) {
			//alert("render_corners");
          var pix = (0xff << 24) | (0x00 << 16) | (0xff << 8) | 0x00;
console.warn('step = %d', step);
//console.warn(corners); return;
          
          //var spotScale=itool.wCanvas.width/itool.imgEl.naturalWidth;
          var spotScale = $(itool.wCanvas).width() / itool.wCanvas.width;
          //var spotScale=1.1;
          //alert(spotScale);
//spotScale = 1;
console.warn('spotScale = %f', spotScale);
          itool.spots=[];
					itool.lCtx.clearRect(0,0,itool.lCanvas.width, itool.lCanvas.height);
          
          for(var i=0; i < count; ++i){
        	  if(i<options.maxspots){
        	  	//old spot creating with JSFeat - replace this
              
              	var x = corners[i].x;
              	var y = corners[i].y;
							var xy = itool.xyWorkToOrig([x*spotScale, y*spotScale]);
							//if (itool.isNearSpot(xy[0],xy[1])) continue;
              	console.info("(%d,%d) -> (%d,%d)",x,y, xy[0],xy[1]);
              
              //Jon - how can I create your spots here instead of those above?
             	itool.spots.push({xy: xy, type: 'spot'});
             //itool.spots.push({xy: [(x-itool.wCanvas.width), (y-itool.wCanvas.height)], type: 'spot'});
              
              
              //itool.spots.push({xy: [(x), (y)], type: 'spot'});
              
              //push({xy:  itool.xyOrigToWork([x,y]) .....})                                                        
            //push({xy: [x * this.scale, y * this.scale] ..})    		  
        	  }	  
              
          }
          itool.drawSpots();
			//alert("done render_corners");
      }
      
      
      function findSpots() {
    	  
    	 
    	  
          canvasWidth=itool.wCanvas.width;
          canvasHeight=itool.wCanvas.height;
          
          //alert("width: "+itool.wCanvas.width+" height: "+itool.wCanvas.height);

          //sctx.fillStyle = "rgb(0,255,0)";
          //sctx.strokeStyle = "rgb(0,255,0)";

          img_u8 = new jsfeat.matrix_t(canvasWidth, canvasHeight, jsfeat.U8_t | jsfeat.C1_t);
          // after blur
          img_u8_smooth = new jsfeat.matrix_t(canvasWidth, canvasHeight, jsfeat.U8_t | jsfeat.C1_t);
          // we wll limit to 500 strongest points
          screen_descriptors = new jsfeat.matrix_t(32, 500, jsfeat.U8_t | jsfeat.C1_t);
          pattern_descriptors = [];
          
          screen_corners = [];
          pattern_corners = [];
          matches = [];

          var i = canvasWidth*canvasHeight;
          while(--i >= 0) {
              screen_corners[i] = new jsfeat.keypoint_t(0,0,0,0,-1);
              matches[i] = new match_t();
          }
    	  //screen_corners = [];
          //pattern_corners = [];
          //matches = [];
              	//compatibility.requestAnimationFrame(tick);
              	//stat.new_frame();
              	//if (video.readyState === video.HAVE_ENOUGH_DATA) {
        		//alert("about to draw image!");
              	//sctx.drawImage(localPatternImage, 0, 0, 640, 480);
              	
              	var myWidth=itool.wCanvas.width;
               var myHeight=itool.wCanvas.height;
               //var myWidth=800;
               //var myHeight=600;
               //var myWidth=itool.imgEl.naturalWidth;
               //var myHeight=itool.imgEl.naturalHeight;
console.warn('myW, myH = (%d, %d)', myWidth, myHeight);
               
               //alert("width: "+itool.imgEl.naturalWidth+" height: "+itool.imgEl.naturalHeight);
              	
                    //var imageData = sctx.getImageData(0, 0, myWidth, myHeight);
                    var imageData = itool.wCtx.getImageData(0, 0, myWidth, myHeight);
       			//alert("Retrieved image data!");

                    stat.start("grayscale");
                    jsfeat.imgproc.grayscale(imageData.data, myWidth, myHeight, img_u8);
                    stat.stop("grayscale");
                    //alert("post grayscale");

                    stat.start("gauss blur");
                    jsfeat.imgproc.gaussian_blur(img_u8, img_u8_smooth, options.blur_size|0);
                    stat.stop("gauss blur");
                    //alert("post gauss lur");

                    jsfeat.yape06.laplacian_threshold = options.lap_thres|0;
                    jsfeat.yape06.min_eigen_value_threshold = options.eigen_thres|0;
                    //alert("post yape06");

                    stat.start("keypoints");
                    num_corners = detect_keypoints(img_u8_smooth, screen_corners, 5000);
                    stat.stop("keypoints");

                    stat.start("orb descriptors");
                    jsfeat.orb.describe(img_u8_smooth, screen_corners, num_corners, screen_descriptors);
                    stat.stop("orb descriptors");

                    // render result back to canvas
                    var data_u32 = new Uint32Array(imageData.data.buffer);
                    //console.log("%d,%d",itool.wCanvas.width,itool.wCanvas.height);
                    render_corners(screen_corners, num_corners, data_u32, myWidth);
        
        //alert("end render_corners!");

                    // render pattern and matches
                    var num_matches = 0;
                    var good_matches = 0;
                    //if(pattern_preview) {
                    //    render_mono_image(pattern_preview.data, data_u32, pattern_preview.cols, pattern_preview.rows, 800);
                      //alert("end render_mono_image");
                     //   stat.start("matching");
                     //   num_matches = match_pattern();
                     //   good_matches = find_transform(matches, num_matches);
                     //   stat.stop("matching");
                    //}
        
        //alert("end render pattern and matches!");
        
                    sctx.putImageData(imageData, 0, 0);

                    if(num_matches) {
                        render_matches(sctx, matches, num_matches);
                        if(good_matches > 8)
                            render_pattern_shape(sctx);
                    }

                    $('#log').html(stat.log());
                  //}
              	//itool.wCanvas.onmouseup();
            }
      
         

            // our point match structure
            var match_t = (function () {
                function match_t(screen_idx, pattern_lev, pattern_idx, distance) {
                    if (typeof screen_idx === "undefined") { screen_idx=0; }
                    if (typeof pattern_lev === "undefined") { pattern_lev=0; }
                    if (typeof pattern_idx === "undefined") { pattern_idx=0; }
                    if (typeof distance === "undefined") { distance=0; }

                    this.screen_idx = screen_idx;
                    this.pattern_lev = pattern_lev;
                    this.pattern_idx = pattern_idx;
                    this.distance = distance;
                }
                return match_t;
            })();
      
       var demo_opt = function(){
             //alert("Starting demo_opt!");
                this.blur_size = 5;
                this.lap_thres = 30;
                this.eigen_thres = 25;
                this.match_threshold = 48;
                this.maxspots=80;

                this.train_pattern = function() {
return findSpots();
                    //alert("Starting train_pattern!");
                    var lev=0, i=0;
                    var sc = 1.0;
                    var max_pattern_size = 512;
                    var max_per_level = 300;
                    var sc_inc = Math.sqrt(2.0); // magic number ;)
                    var lev0_img = new jsfeat.matrix_t(img_u8.cols, img_u8.rows, jsfeat.U8_t | jsfeat.C1_t);
                    var lev_img = new jsfeat.matrix_t(img_u8.cols, img_u8.rows, jsfeat.U8_t | jsfeat.C1_t);
                    var new_width=0, new_height=0;
                    var lev_corners, lev_descr;
                    var corners_num=0;

                    var sc0 = Math.min(max_pattern_size/img_u8.cols, max_pattern_size/img_u8.rows);
                    new_width = (img_u8.cols*sc0)|0;
                    new_height = (img_u8.rows*sc0)|0;

                    jsfeat.imgproc.resample(img_u8, lev0_img, new_width, new_height);

                    // prepare preview
                    pattern_preview = new jsfeat.matrix_t(new_width>>1, new_height>>1, jsfeat.U8_t | jsfeat.C1_t);
                    jsfeat.imgproc.pyrdown(lev0_img, pattern_preview);

                    for(lev=0; lev < num_train_levels; ++lev) {
                        pattern_corners[lev] = [];
                        lev_corners = pattern_corners[lev];

                        // preallocate corners array
                        i = (new_width*new_height) >> lev;
                        while(--i >= 0) {
                            lev_corners[i] = new jsfeat.keypoint_t(0,0,0,0,-1);
                        }

                        pattern_descriptors[lev] = new jsfeat.matrix_t(32, max_per_level, jsfeat.U8_t | jsfeat.C1_t);
                    }

                    // do the first level
                    lev_corners = pattern_corners[0];
                    lev_descr = pattern_descriptors[0];

                    jsfeat.imgproc.gaussian_blur(lev0_img, lev_img, options.blur_size|0); // this is more robust
                    corners_num = detect_keypoints(lev_img, lev_corners, max_per_level);
                    jsfeat.orb.describe(lev_img, lev_corners, corners_num, lev_descr);

                    console.log("train " + lev_img.cols + "x" + lev_img.rows + " points: " + corners_num);

                    sc /= sc_inc;

                    // lets do multiple scale levels
                    // we can use Canvas context draw method for faster resize 
                    // but its nice to demonstrate that you can do everything with jsfeat
                    for(lev = 1; lev < num_train_levels; ++lev) {
                        lev_corners = pattern_corners[lev];
                        lev_descr = pattern_descriptors[lev];

                        new_width = (lev0_img.cols*sc)|0;
                        new_height = (lev0_img.rows*sc)|0;

                        jsfeat.imgproc.resample(lev0_img, lev_img, new_width, new_height);
                        jsfeat.imgproc.gaussian_blur(lev_img, lev_img, options.blur_size|0);
                        corners_num = detect_keypoints(lev_img, lev_corners, max_per_level);
                        jsfeat.orb.describe(lev_img, lev_corners, corners_num, lev_descr);

                        // fix the coordinates due to scale level
                        for(i = 0; i < corners_num; ++i) {
                            lev_corners[i].x *= 1./sc;
                            lev_corners[i].y *= 1./sc;
                        }

                        console.log("train " + lev_img.cols + "x" + lev_img.rows + " points: " + corners_num);

                        sc /= sc_inc;
                    }
                    //alert("Ending train_pattern!");
                  //tick(patternImage2);
                };
         
            }

      
      function startJFeat() {
        //alert("Starting demo_app");
                //canvasWidth  = itool.wCanvas.width;
                //canvasHeight = itool.wCanvas.height;
                
                //canvasWidth=itool.imgEl.naturalWidth;
                //canvasHeight=itool.imgEl.naturalHeight;
                

        

                // transform matrix
                homo3x3 = new jsfeat.matrix_t(3,3,jsfeat.F32C1_t);
                match_mask = new jsfeat.matrix_t(500,1,jsfeat.U8C1_t);

                options = new demo_opt();
                //alert("completed demo_opt!");
                gui = new dat.GUI();
        		//alert("completed dat.GUI!");
        
        

                var blurController = gui.add(options, "blur_size", 3, 12).step(1);
                var lapController = gui.add(options, "lap_thres", 1, 100);
                var eigenController = gui.add(options, "eigen_thres", 1, 100);
                //var thresholdController = gui.add(options, "match_threshold", 16, 128);
                var maxspots=gui.add(options, "maxspots",1,150).name("max # spots").step(5);
                var trainer=gui.add(options, "train_pattern").name("find spots");
                
                
        		//alert("completed dat.GUI add options!");
        		blurController.onFinishChange(findSpots);
         		eigenController.onFinishChange(findSpots);
              	lapController.onFinishChange(findSpots);
              	maxspots.onFinishChange(findSpots);
              	//thresholdController.onFinishChange(findSpots);
                
        
                
        
        
                stat.add("grayscale");
                stat.add("gauss blur");
                stat.add("keypoints");
                stat.add("orb descriptors");
                stat.add("matching");
              //alert("Completed demo_app!");
            }



        </script>

	<div id="wrapper">
		<div id="page">
			<jsp:include page="../header.jsp" flush="true">
  				<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
			</jsp:include>

<script src="http://code.jquery.com/ui/1.10.2/jquery-ui.js"></script>

<link rel="stylesheet" href="//code.jquery.com/ui/1.11.1/themes/smoothness/jquery-ui.css">
 <script src="//code.jquery.com/ui/1.11.1/jquery-ui.js"></script>
 

 
<script src="../javascript/imageTools.js"></script>
<script>
window.alert = function(a) { console.debug(a); }

$(document).ready(function() {
	checkImage($('#imageTools-img'));
    "use strict";

    // lets do some fun
    //document.getElementById("patternImage").src = getURLParameter("patternImage");
  //document.getElementById("patternImage2").src = getURLParameter("patternImage2");
    
    try {
        
        
        
        
       // var onDimensionsReady = function(width, height) {
          //alert("Starting onDimensionsReady!");
            startJFeat();
          //alert("Completing onDimensionsReady!");
          //compatibility.requestAnimationFrame(tick);
     //   };
      
     // onDimensionsReady(640, 480);
      //findSpots();

        
    } catch (error) {
		alert("error!");
    }
});
</script>

			
			<div id="main">
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

	    <div class="dg ac">
      <div class="dg main a" style="width: 245px;">
        <ul style="height: auto;">
          <li class="cr number has-slider">
            <div><span class="property-name">blur_size</span>
              <div class="c">
                <div><input type="text"></div>
                <div class="slider"> </div>
              </div>
            </div>
          </li>
          <li class="cr number has-slider">
            <div><span class="property-name">lap_thres</span>
              <div class="c">
                <div><input type="text"></div>
                <div class="slider"> </div>
              </div>
            </div>
          </li>
          <li class="cr number has-slider">
            <div><span class="property-name">eigen_thres</span>
              <div class="c">
                <div><input type="text"></div>
                <div class="slider"> </div>
              </div>
            </div>
          </li>
          
          <li class="cr number has-slider">
            <div><span class="property-name">maxspots</span>
              <div class="c">
                <div><input type="text"></div>
                <div class="slider"> </div>
              </div>
            </div>
          </li>
      
          <li class="cr function">
            <div><span class="property-name">train_pattern</span>
              <div class="c"> </div>
            </div>
          </li>
        </ul>
      
      </div>


</div>

<jsp:include page="../footer.jsp" flush="true"/>

</div>
<!-- end page -->

</div>

<!--end wrapper -->



</body>
</html>


