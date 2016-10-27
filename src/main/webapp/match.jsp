<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.Dimension, java.io.File, java.util.*, java.util.concurrent.ThreadPoolExecutor,
org.apache.commons.lang3.StringUtils,
javax.servlet.http.HttpSession" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%!

public boolean validateSources(String[] sources) {
	return false;
}

%>

<jsp:include page="header.jsp" flush="true"/>
<style>
.no, .yes {
	position: absolute;
	right: -10px;
	bottom: -10px;
	width: 40%;
	height: 40%;
	background-size: 100% 100%;
	mask-size: 100% 100%;
	-webkit-mask-size: 100% 100%;
}
.no {
	background-color: #F20;
	-webkit-mask-image: url(images/ic_not_interested_black_24px.svg);
	mask-image: url(images/ic_not_interested_black_24px.svg);
/*
	background-image: url(images/ic_not_interested_black_24px.svg);
*/
}
.yes {
	background-color: #0F2;
	-webkit-mask-image: url(images/ic_check_circle_black_24px.svg);
	mask-image: url(images/ic_check_circle_black_24px.svg);
/*
	background-image: url(images/ic_check_circle_black_24px.svg);
*/
}

.ident-toggle {
	cursor: pointer;
}

textarea {
	width: 50%;
	height: 200px;
}
.error {
	color: #D11;
	font-weight: bold;
}
img.ident-img {
	max-height: 150px;
	max-width: 500px;
}
.ident-img-wrapper {
	position: relative;
	background: #EEE url(images/throbber.gif) 50% 50% no-repeat;
	display: inline-block;
	margin: 10px;
	float: left;
	min-height: 200px;
	min-width: 200px;
}

#ident-controls {
	height: 2.5em;
}

#ident-workarea {
}

</style>
<script type="text/javascript">
var imageData = [];
var defaultSpecies = 'Megaptera novaeangliae';

function beginProcess() {
	$('#ident-controls input').hide();
	var validUrls = parseUrls($('#ident-sources').val());
	if (!validUrls) {
		$('#ident-message').html('<p class="error">could not parse any valid urls</p>');
		return;
	}
	$('#ident-message').html('<p>found ' + validUrls.length + ' URL' + ((validUrls.length == 1) ? '' : 's') + '</p>');
	$('#ident-sources').hide();
	for (var i = 0 ; i < validUrls.length ; i++) {
		testSource(validUrls[i]);
	}
	return true;
}

function testSource(srcUrl) {
	imageData.push({ url: srcUrl, complete: false, success: false });
	var id = imageData.length - 1;
	imageData[id].id = id;
	var img = $('<img class="ident-img" id="ident-img-' + id + '" src="' + srcUrl + '" />');
	img.on('load', function(d) {
		var id = d.target.id.substring(10);
		console.warn('loaded[%o]: %o', id, d);
		$(d.target).parent().css('background-image', 'none').append('<div class="yes ident-toggle" />');
		imageData[id].complete = true;
		imageData[id].success = true;
		testingComplete();
	});
	img.on('error', function(d) {
		var id = d.target.id.substring(10);
		console.warn('failed[%o]: %o', id, d);
		$(d.target).hide().parent().css('background-color', '#FAA').css('background-image', 'none').append('<div class="no" />');
		imageData[id].complete = true;
		testingComplete();
	});
	var el = $('<div class="ident-img-wrapper" id="ident-img-wrapper-' + id + '"></div>');
	el.append(img);
	$('#ident-workarea').append(el);
	//$('#ident-img-' + id).prop('src', srcUrl);
}

function testingComplete() {
	var allDone = true;
	var ok = 0;
	for (var i = 0 ; i < imageData.length ; i++) {
console.info('i=%d complete[%o] success[%o]', i, imageData[i].complete, imageData[i].success);
		if (!imageData[i].complete) allDone = false;
		if (imageData[i].success) ok++;
	}
	if (!allDone) return;

	if (ok > 1) {
		$('.ident-toggle').bind('click', function(ev) {
			var jel = $(ev.target);
			if (jel.hasClass('yes')) {
				jel.removeClass('yes');
				jel.addClass('no');
				jel.parent().find('img').css('opacity', 0.2);
			} else {
				jel.removeClass('no');
				jel.addClass('yes');
				jel.parent().find('img').css('opacity', 1);
			}
			updateBeginNote();
		});
	}

	$('#ident-message').html('<p>verified ' + ok + ' image URL' + ((ok == 1) ? '' : 's') + '.' + ((ok > 1) ? ' select desired images to identify.' : '') + '</p>');
	$('#ident-controls').append('<input id="ident-begin-button" value="identify" type="button" onClick="return beginIdentify()" /> <span id="ident-begin-note"></span>');
	updateBeginNote();
}


function updateBeginNote() {
	var ok = 0;
	for (var i = 0 ; i < imageData.length ; i++) {
//console.info('i=%d complete[%o] success[%o]', i, imageData[i].complete, imageData[i].success);
		if (imageData[i].success) ok++;
	}
	var selected = $('.yes');
	var h = '<b>' + selected.length + ' of ' + ok + '</b> to identify (species <i id="ident-species">' + defaultSpecies + '</i>)';
	if (selected.length < 1) {
		$('#ident-begin-button').hide();
	} else {
		$('#ident-begin-button').show();
	}
	$('#ident-begin-note').html(h);
}


function beginIdentify() {
	var selected = $('.yes');
	if (selected.length < 1) return;
	selected.removeClass('ident-toggle').unbind('click');
	var srcs = [];
	selected.each(function(i,el) {
		var img = $(el).parent().find('img');
		if (!img.length) return;
		//var id= img.prop('id').substr(10);
		srcs.push({
			maLabels: ['matching only'],
			imgSrc: img.prop('src')
		});
	});
	if (srcs.length < 1) return;
	$('#ident-begin-button').hide();
	var data = {
		sources: srcs,
		species: $('#ident-species').text()
	};
	$.ajax({
		url: 'EncounterCreate',
		contentType: 'application/javascript',
		data: JSON.stringify(data),
		dataType: 'json',
		complete: function(x) {
			console.warn('response: %o', x);
			if ((x.status != 200) || !x.responseJSON || !x.responseJSON.success) {
				var msg = 'unknown error';
				if (x.status != 200) msg = 'server error: ' + x.status + ' ' + x.statusText;
				if (x.responseJSON && x.responseJSON.error) msg = x.responseJSON.error;
				$('#ident-begin-note').html('<p class="error">' + msg + '</p>');
			} else {
				processEncounter(x.responseJSON);
			}
		},
		type: 'POST'
	});
}

// .annotations .assets .encounterId
var xx;
function processEncounter(data) {
xx=data;
	$('#ident-begin-note').html('<p>created <b><a target="_new" href="encounters/encounter.jsp?number=' + data.encounterId + '">new encounter</a></b>.</p>');
	var iaData = {
		identify: {
			annotationIds: data.annotations
		}
	};
	$.ajax({
		url: 'ia',
		data: JSON.stringify(iaData),
		contentType: 'application/javascript',
		complete: function(x) {
			console.warn('response: %o', x);
			if ((x.status != 200) || !x.responseJSON || !x.responseJSON.success || !x.responseJSON.tasks || !x.responseJSON.tasks.length) {
				var msg = 'unknown error';
				if (x.status != 200) msg = 'server error: ' + x.status + ' ' + x.statusText;
				if (x.responseJSON && x.responseJSON.error) msg = x.responseJSON.error;
				$('#ident-begin-note').append('<p class="error">' + msg + '</p>');
			} else {
				var h = '<div>Started task(s):<ul>';
				for (var i = 0 ; i < x.responseJSON.tasks.length ; i++) {
					h += '<li><a target="_new" href="encounters/matchResults.jsp?taskId=' + x.responseJSON.tasks[i].taskId + '">' + x.responseJSON.tasks[i].taskId + '</a></li>';
				}
				h += '</ul></div>';
				$('#ident-begin-note').append(h);
			}
		},
		dataType: 'json',
		type: 'post'
	});
}

function parseUrls(txt) {
	var urls = [];
	var regex = new RegExp(/^https*:\/\/\S+/, "img");
	var matches;
	while ((matches = regex.exec(txt)) !== null) {
//console.info('%d %o', count, matches);
		urls.push(matches[0]);
	}
	if (urls.length < 1) return false;
	return urls;
}

$(document).ready(function() {
	$('#ident-sources').val(
'https://pacificwhale.files.wordpress.com/2014/03/whale_fluke_prebranding_img.jpg?w=1200\n' +
'https://media-cdn.tripadvisor.com/media/photo-s/03/91/a7/6c/pacific-whale-foundation.jpg'
	);
});
</script>

<div class="container maincontent">

<%
String context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("match.jsp");

// TODO maybe have some config to disable this!

String[] sources = request.getParameterValues("sources");
boolean valid = validateSources(sources);
if ((sources != null) && valid) {
  
} else {  //show a form

%>

<div id="ident-message">
	<%= ((sources == null) ? "" : "<p class=\"error\">there was an error parsing sources</p>") %>
</div>

<div id="ident-workarea"></div>
<div style="clear: both;"></div>

<textarea id="ident-sources"><%= ((sources == null) ? "" : StringUtils.join(sources, "\n")) %></textarea>

<div id="ident-controls">
	<input type="button" value="continue" onClick="return beginProcess();" />
</div>


<!--
	<a target="_new" href="EncounterCreate?species=Megaptera%20novaeangliae&source=https://pacificwhale.files.wordpress.com/2014/03/whale_fluke_prebranding_img.jpg?w%3D1200">direct test of EncounterCreate</a>
-->


<%
}
%>



</div>

<jsp:include page="footer.jsp" flush="true"/>

