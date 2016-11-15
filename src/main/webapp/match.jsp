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
<link type='text/css' rel='stylesheet' href='javascript/timepicker/jquery-ui-timepicker-addon.css' />

<jsp:include page="header.jsp" flush="true"/>

<script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>

<style>
.no, .yes {
	position: absolute;
	right: -10px;
	bottom: -10px;
	width: 75px;
	height: 75px;
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

.error-input {
	outline: solid red 3px;
	background-color: rgba(255,255,0,0.7);
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

.ident-img-info {
	font-size: 0.8em;
	position: absolute;
	left: 5px;
	top: 5px;
	background-color: #FFF;
	padding: 6px;
	opacity: 0.7;
	border-radius: 5px;
}
.ident-img-info:hover {
	opacity: 1.0;
}

#ident-controls {
	height: 2.5em;
}

#ident-workarea {
}


#ident-main-form {
	display: none;
}

#ident-main-form input {
	margin-top: 10px;
}

.ident-date {
	width: 9em;
}

#ident-datetime-wrapper input[type="button"] {
	padding: 0 4px;
	border-radius: 5px;
	margin: -5px 0 0 10px;
}

#ident-message {
	margin: 0 0 0 40px;
}

#recaptcha-wrapper {
	margin: 60px 0;
}

</style>
<script type="text/javascript">
var imageData = [];
var defaultSpecies = 'Megaptera novaeangliae';
var accessKey = wildbook.uuid();
var recaptchaValue = false;

function beginProcess() {
	$('.captcha-error').remove();
	if (recaptchaCompleted()) {
		recaptchaValue = recaptchaCompleted();
		$('#recaptcha-wrapper').hide();
	} else {
		$('#ident-controls').append('<p class="captcha-error error">Please confirm you are not a robot below.</p>');
		return;
	}
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
	var img = $('<img title="' + srcUrl + '" class="ident-img" id="ident-img-' + id + '" src="' + srcUrl + '" />');
	img.on('load', function(d) {
		var id = d.target.id.substring(10);
		console.warn('loaded[%o]: %o', id, d);
		$(d.target).parent().css('background-image', 'none').addClass('ident-toggle').append('<div class="yes-no-icon yes" />');
		imageData[id].complete = true;
		imageData[id].success = true;
		testingComplete();
	});
	img.on('error', function(d) {
		var id = d.target.id.substring(10);
		console.warn('failed[%o]: %o', id, d);
		$(d.target).hide().parent().css('background-color', '#FAA').css('background-image', 'none').append('<div class="yes-no-icon no" />');
		imageData[id].complete = true;
		var badUrl = $('#ident-img-wrapper-' + id + ' img').prop('src');
		$('#ident-img-wrapper-' + id).prepend('<p style="padding: 10px; width: 200px; overflow-wrap: break-word;">error on url <b>' + badUrl + '</b></p>');
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
console.log(ev);
console.log(ev.currentTarget);
			var jel = $(ev.currentTarget).find('.yes-no-icon');
console.log('jel = %o', jel);
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

	$('#ident-message').html('<p>Verified ' + ok + ' image URL' + ((ok == 1) ? '' : 's') + '.' + ((ok > 1) ? ' <b>Select desired images to identify</b> by clicking the images.' : '') + '</p>');
	$('#ident-controls').append('<input id="ident-begin-button" value="use images" type="button" onClick="return beginIdentify()" /> <span id="ident-begin-note"></span>');
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
	$('.ident-toggle').removeClass('ident-toggle').unbind('click');
	var assets = [];
	selected.each(function(i,el) {
		var img = $(el).parent().find('img');
		if (!img.length) return;
		//var id= img.prop('id').substr(10);
		assets.push({
			accessKey: accessKey,
			url: img.prop('src')
		});
	});
	if (assets.length < 1) return;
	$('#ident-begin-button').hide();
	var data = { MediaAssetCreate: [ { assets: assets } ], recaptchaValue: recaptchaValue };
console.log('data = %o', data);
	$.ajax({
		url: 'MediaAssetCreate',
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
				promptUserForMore(x.responseJSON);
			}
		},
		type: 'POST'
	});
}


var foundDates = [];
function promptUserForMore(d) {
	$('#ident-message').html('');
//console.warn('prompt 4 more %o', d);
	$('#ident-controls').hide();
	$('.yes').closest('.ident-img-wrapper').each(function(i,el) {
		if (!d.withoutSet || !d.withoutSet[i]) return;
		var id = el.getAttribute('id').substring(18);
		var h = '<div title="' + d.withoutSet[i].id + '" class="ident-img-info">successfully saved<br />';
		h += '<input class="ident-date" placeholder="sighting date" id="user-date-' + id + '"';
		if (d.withoutSet[i].dateTime) {
			var dt = new Date(d.withoutSet[i].dateTime);
			h += ' value="' + dt.toISOString().substring(0,16).replace('T', ' ') + '" ';
		} else {
			var dt = new Date();
			h += ' value="' + dt.toISOString().substring(0,10) + '" ';
		}
		h += ' /></div>';
		$(el).append(h).find('img').data('media-asset-id', d.withoutSet[i].id);
	});
/*
	for (var i = 0 ; i < foundDates.length ; i++) {
		$('#ident-datetime-wrapper').append('<input type="button" value="use ' + foundDates[i].toLocaleDateString() +
			'" onClick="return identSetDate(' + i + ');" />');
	}
*/
	$('#ident-main-form').show();

	$('.ident-date').datetimepicker({
      		changeMonth: true,
      		changeYear: true,
      		dateFormat: 'yy-mm-dd',
      		maxDate: '+1d',
      		controlType: 'select',
      		alwaysSetTime: false,
      		showSecond:false,
      		showMillisec:false,
      		showMicrosec:false,
      		showTimezone:false
    	});
    	//$( "#datepicker" ).datetimepicker( $.timepicker.regional[ "en" ] );
}

/*
function identSetDate(i) {
	if (!foundDates[i]) return;
	$('#ident-datetime').val(foundDates[i].getFullYear() + '-' + (foundDates[i].getMonth()+1) + '-' + foundDates[i].getDate());
}
*/

//now we consider each image to be its own Encounter
var numEncsLeft = 0;
var numIdentsLeft = 0;
function createEncounters() {
	$('.tmp-error').remove();
	$('.error-input').removeClass('error-input');
	var email = $('#ident-email').val();
	var fail = false;
	if (!wildbook.isValidEmailAddress(email)) {
		$('#ident-main-form').append('<div class="error tmp-error">invalid email address</div>');
		$('#ident-email').addClass('error-input');
		fail = true;
	}
	$('.yes').closest('.ident-img-wrapper').find('img').each(function(i, el) {
		var id = el.id.substring(10);
		if (!$('#user-date-' + id).val()) {
			$('#user-date-' + id).addClass('error-input');
			$('#ident-main-form').append('<div class="error tmp-error">date required</span>');
			fail = true;
		}
	});
	if (fail) return;

	$('#ident-main-form').hide();
	$('#ident-controls').show();
	numEncsLeft = $('.yes').length;
	numIdentsLeft = numEncsLeft;
	$('.yes').closest('.ident-img-wrapper').find('img').each(function(i, el) {
		var id = el.id.substring(10);
		var srcs = [{
			maLabels: ['matching only'],
			mediaAssetId: $(el).data('media-asset-id')
		}];
		createEncounter({
			accessKey: accessKey,
			sources: srcs,
			dateString: $('#user-date-' + id).val(),
			email: email,
			species: $('#ident-species').text()
		}, id);
	});
}

function createEncounter(data, id) {
	$.ajax({
		url: 'EncounterCreate',
		contentType: 'application/javascript',
		data: JSON.stringify(data),
		dataType: 'json',
		complete: function(x) {
			numEncsLeft--;
			console.warn('%d) [%o] response: %o', numEncsLeft, id, x);
			if ((x.status != 200) || !x.responseJSON || !x.responseJSON.success) {
				var msg = 'unknown error';
				if (x.status != 200) msg = 'server error: ' + x.status + ' ' + x.statusText;
				if (x.responseJSON && x.responseJSON.error) msg = x.responseJSON.error;
				$('#ident-img-wrapper-' + id + ' .ident-img-info').append('<div class="error">' + msg + '</div>');
				numIdentsLeft--;
			} else {
				processEncounter(x.responseJSON, id);
			}
		},
		type: 'POST'
	});
}

// .annotations .assets .encounterId
function processEncounter(data, id) {
	$('#ident-img-wrapper-' + id + ' .ident-img-info').append('<div>created <b><a target="_new" title="' + data.encounterId +
		'" href="encounters/encounter.jsp?number=' + data.encounterId + '">new encounter</a></b>.</div>');
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
			numIdentsLeft--;
			console.warn('[%d] response: %o', numIdentsLeft, x);
			if (numIdentsLeft < 1) $('#ident-begin-note').html('<h2>Thank you!</h2>Please check your email for follow-up information regarding your submissions.');
			if ((x.status != 200) || !x.responseJSON || !x.responseJSON.success || !x.responseJSON.tasks || !x.responseJSON.tasks.length) {
				var msg = 'unknown error';
				if (x.status != 200) msg = 'server error: ' + x.status + ' ' + x.statusText;
				if (x.responseJSON && x.responseJSON.error) msg = x.responseJSON.error;
				$('#ident-img-wrapper-' + id + ' .ident-img-info').append('<div class="error">' + msg + '</div>');
			} else {
				var h = '<div>Started task(s):<ul>';
				for (var i = 0 ; i < x.responseJSON.tasks.length ; i++) {
					h += '<li><a target="_new" href="encounters/matchResults.jsp?taskId=' + x.responseJSON.tasks[i].taskId + '">' + x.responseJSON.tasks[i].taskId + '</a></li>';
				}
				h += '</ul></div>';
				$('#ident-img-wrapper-' + id + ' .ident-img-info').append(h);
			}
		},
		dataType: 'json',
		type: 'post'
	});
}

function parseUrls(txt) {
	var urls = [];
	var regex = /^https*:\/\/\S+/img;
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
'https://upload.wikimedia.org/wikipedia/commons/5/5c/Humpback_whale_fluke_(2).jpg\n' +
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

<h1>Enter URLs of fluke images to identify</h1>
<p style="color: #AAA; margin-bottom: 20px;">
<b style="background-color: #99C; color: #FFF; padding: 2px 8px; border-radius: 4px; margin-right: 15px;">Beta testing</b> Currently supporting only <b>humpback flukes</b> (<i>Megaptera novaeangliae</i>)
</p>

<div id="ident-message">
	<%= ((sources == null) ? "" : "<p class=\"error\">there was an error parsing sources</p>") %>
</div>

<div id="ident-workarea"></div>
<div style="clear: both;"></div>

<textarea id="ident-sources"><%= ((sources == null) ? "" : StringUtils.join(sources, "\n")) %></textarea>

<div id="ident-main-form">
	<p>Enter <b>email address</b> below and <b>set dates</b> for sightings above.</p>
	<input id="ident-email" type="email" placeholder="email address (required)" style="width: 18em;" />
	<p>
		<input type="button" value="start identification" onClick="return createEncounters();" />
	</p>
</div>

<div id="ident-controls">
	<input type="button" value="continue" onClick="return beginProcess();" />
</div>


<!--
	<a target="_new" href="EncounterCreate?species=Megaptera%20novaeangliae&source=https://pacificwhale.files.wordpress.com/2014/03/whale_fluke_prebranding_img.jpg?w%3D1200">direct test of EncounterCreate</a>
-->


<%
}
%>

<div id="recaptcha-wrapper">
<%= ServletUtilities.captchaWidget(request) %>
</div>

<div style="clear: both; margin-bottom: 100px;"></div>

<jsp:include page="footer.jsp" flush="true"/>

