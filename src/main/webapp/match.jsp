<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.Dimension, java.io.File, java.util.*, java.util.concurrent.ThreadPoolExecutor,
org.apache.commons.lang3.StringUtils,
org.ecocean.servlet.ReCAPTCHA,
javax.servlet.http.HttpSession" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%!

public boolean validateSources(String[] sources) {
	return false;
}

%>

<%
    boolean amHuman = ReCAPTCHA.sessionIsHuman(request);
%>
<link type='text/css' rel='stylesheet' href='javascript/timepicker/jquery-ui-timepicker-addon.css' />

<jsp:include page="header.jsp" flush="true"/>

<script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>
<script src="https://sdk.amazonaws.com/js/aws-sdk-2.2.33.min.js"></script>
<script src="tools/flow.min.js"></script>

<style>
<% if (!amHuman) { %>
#entire-form {
	display: none;
}
<% } %>

#disclaimer {
	margin: 15px;
	padding: 20px;
	border: solid 1px #999;
	font-size: 0.8em;
	color: #888;
}

#file-input {
	width: 80%;
	margin: 10%;
	background-color: #CFF;
	padding: 20px;
	border-radius: 10px;
	margin-top: 20px;
	text-align: center;
}

#upcontrols {
	display: none;
}

#steps {
	padding: 15px;
}

.step {
	display: inline-block;
	background-color: #DDD;
	border-radius: 5px;
	padding: 5px 10px;
	color: #FFF;
	font-size: 1.3em;
	margin: 0 8px;
}

.step-active {
	background-color: #386;
	color: #EEE;
}

.step-num {
	color: #CCC;
	font-weight: bold;
	margin-right: 10px;
	padding: 0 8px;
	height: 1.8em;
	font-size: 1.5em;
	width: 1.8em;
	background-color: #FFF;
	border-radius: 0.9em;
}

.step-active .step-num {
	color: #555;
}

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
	max-height: 250px;
	max-width: 500px;
}
.ident-img-wrapper {
	position: relative;
	background: #EEE url(images/throbber.gif) 50% 50% no-repeat;
	display: inline-block;
	margin: 10px;
	float: left;
	min-height: 300px;
	min-width: 300px;
}

.ident-img-info {
	font-size: 0.8em;
	position: absolute;
	left: 5px;
	top: 5px;
	background-color: #FFF;
	padding: 6px;
	opacity: 0.7;
	width: 94%;
	word-wrap: break-word;
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

.ident-date, .ident-location {
	display: block;
	margin: 5px;
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
	padding: 10px 50px;
}


/* uploader stuff */

div#file-activity {
	display:  none;
	font-family: sans;
	border: solid 2px black;
	padding: 8px;
	margin: 20px;
	min-height: 100px;
}
div.file-item {
	position: relative;
	background-color: #DDD;
	border-radius: 3px;
	margin: 2px;
}

div.file-item div {
	display: inline-block;
	padding: 3px 7px;
	overflow: hidden;
}
.file-name {
	width: 50%;
	font-size: 0.9em;
	white-space: nowrap;
}
.file-size {
	width: 8%;
}

.file-bar {
	position: absolute;
	width: 0;
	height: 100%;
	padding: 0 !important;
	left: 0;
	border-radius: 3px;
	background-color: rgba(100,100,100,0.3);
}

#method-control {
	display: none;  /* disabling url method for now */
	margin-bottom: 10px;
}

#method-control a {
	background-color: #DDD;
	padding: 2px 10px;
	border-radius: 3px;
	cursor: pointer;
	font-size: 0.8em;
	margin-left: 30px;
}
#method-control a:hover {
	text-decoration: none;
	background-color: #BBB;
}

#wait-block {
	z-index: 20;
	border-radius: 15px;
	opacity: 0.9;
	text-align: center;
	position: fixed;
	left: 30%;
	top: 40%;
	height: 100px;
	width: 40%;
	background-color: #EFA;
	box-shadow: 10px 10px 5px #888888;
	display: none;
}
#wait-block p {
	margin-top: 20px;
	font-size: 1.3em;
}

</style>
<script type="text/javascript">
var amHuman = <%=amHuman%>;
var currentMethod = 'upload';
var imageData = [];
var defaultSpecies = 'Megaptera novaeangliae';
var accessKey = wildbook.uuid();

function activateStep(num) {
	$('.step-active').removeClass('step-active');
	$('#step-' + num).addClass('step-active');
}

function waitOn(txt) {
	if (!txt) txt = 'Please wait...';
	$('#wait-block p').text(txt);
	$('#wait-block').show();
}

function waitOff() {
	$('#wait-block').hide();
}

function switchMethod() {
	$('#method-' + currentMethod).hide();
	if (currentMethod == 'upload') {
		currentMethod = 'url';
		$('#method-control b').text('Enter image URLs');
		$('#method-control a').text('Upload images instead');
	} else {
		currentMethod = 'upload';
		$('#method-control b').text('Upload image files');
		$('#method-control a').text('Use image URLs instead');
	}
	$('#method-' + currentMethod).show();
	return false;
}

function beginProcess() {
	$('.captcha-error').remove();
	if (recaptchaCompleted()) {
		$('#recaptcha-wrapper').hide();
	} else {
		$('#ident-controls').append('<p class="captcha-error error">Please confirm you are not a robot below.</p>');
		return;
	}
	//recaptchaVerify();
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
	var h = '<b>' + selected.length + ' of ' + ok + '</b> to identify (species <i xxxxxid="ident-species">' + defaultSpecies + '</i>)';
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
	var data = { MediaAssetCreate: [ { assets: assets } ] };
console.log('data = %o', data);
	waitOn('Saving images...');
	$.ajax({
		url: 'MediaAssetCreate',
		contentType: 'application/javascript',
		data: JSON.stringify(data),
		dataType: 'json',
		complete: function(x) {
			waitOff();
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
		var fname = 'successfully saved';
		if (d.withoutSet[i].filename) {
			fname = d.withoutSet[i].filename;
			var l = fname.lastIndexOf('/');
			if (l > -1) fname = fname.substring(l + 1);
		}
		var h = '<div title="' + d.withoutSet[i].id + '" class="ident-img-info">' + fname + '<br />';
		h += '<input class="ident-date" placeholder="sighting date" id="user-date-' + id + '"';
		if (d.withoutSet[i].dateTime) {
			var dt = new Date(d.withoutSet[i].dateTime);
			h += ' value="' + dt.toISOString().substring(0,16).replace('T', ' ') + '" ';
		} else {
			var dt = new Date();
			h += ' value="' + dt.toISOString().substring(0,10) + '" ';
		}
		h += ' />';
		h += '<input class="ident-location" placeholder="location (optional)" title="location description (optional)" id="user-location-' + id + '" />';
		h += '</div>';
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
	activateStep(3);
	$('.no').closest('.ident-img-wrapper').hide();
	$('.tmp-error').remove();
	$('.error-input').removeClass('error-input');
	var email = $('#ident-email').val();
	var fail = false;
	if (!wildbook.isValidEmailAddress(email)) {
		$('#ident-main-form').append('<div class="error tmp-error">invalid email address</div>');
		$('#ident-email').addClass('error-input');
		fail = true;
	}
	$('.ident-toggle').removeClass('ident-toggle');
	$('#click-instructions').remove();
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
	$('.ident-img-info input').hide();
	numEncsLeft = $('.yes').length;
	numIdentsLeft = numEncsLeft;
	var usable = $('.yes').closest('.ident-img-wrapper').find('img');
	window.setTimeout(function() { gaveUp(); }, 17000 * usable.length);
	usable.each(function(i, el) {
		var id = el.id.substring(10);
		var srcs = [{
			maLabels: ['matching only'],
			mediaAssetId: $(el).data('media-asset-id')
		}];
		createEncounter({
			accessKey: accessKey,
			sources: srcs,
			dateString: $('#user-date-' + id).val(),
			locationString: $('#user-location-' + id).val(),
			email: email,
			species: defaultSpecies
		}, id);
	});
}

var encIds = {};
function createEncounter(data, id) {
	waitOn('Saving data...');
	$.ajax({
		url: 'EncounterCreate',
		contentType: 'application/javascript',
		data: JSON.stringify(data),
		dataType: 'json',
		complete: function(x) {
			waitOff();
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
	encIds[id] = data.encounterId;
	$('#ident-img-wrapper-' + id + ' .ident-img-info').append('<div>created <b><a target="_new" title="' + data.encounterId +
		'" href="encounters/encounter.jsp?number=' + data.encounterId + '&accessKey=' + accessKey + '">new encounter</a></b>.</div>');
	waitOn('Starting identification...');
	startIdentify(data.annotations, id);
return;
//////////////////////////// the rest is historic cruft (now in _identCallback() )
	$.ajax({
		url: 'ia',
		data: JSON.stringify(iaData),
		contentType: 'application/javascript',
		complete: function(x) {
			waitOff();
			//this allows us the singular case of just a .taskId being returned
			if ((x.status == 200) && x.responseJSON && x.responseJSON.taskId) {
				console.info("converting taskId %s to .tasks array", x.responseJSON.taskId);
				x.responseJSON.tasks = [ { taskId: x.responseJSON.taskId } ];
			}
			if ((x.status != 200) || !x.responseJSON || !x.responseJSON.success || !x.responseJSON.tasks || !x.responseJSON.tasks.length) {
				var msg = 'unknown error';
				if (x.status != 200) msg = 'server error: ' + x.status + ' ' + x.statusText;
				if (x.responseJSON && x.responseJSON.error) msg = x.responseJSON.error;
				$('#ident-img-wrapper-' + id + ' .ident-img-info').append('<div class="error">' + msg + '</div>');
			} else {
				var h = '<div>Started task';
				if (x.responseJSON.tasks.length > 1) {
					h += 's: <ul>';
				} else {
					h += ' ';
				}
				for (var i = 0 ; i < x.responseJSON.tasks.length ; i++) {
					taskIds.push(x.responseJSON.tasks[i].taskId);
					h += '<li><a target="_new" href="encounters/matchResults.jsp?taskId=' + x.responseJSON.tasks[i].taskId + '">' + x.responseJSON.tasks[i].taskId + '</a></li>';
				}
				h += ((x.responseJSON.tasks.length > 1) ? '</ul>' : '') + '</div>';
				$('#ident-img-wrapper-' + id + ' .ident-img-info').append(h);
			}
			numIdentsLeft--;
			console.warn('[%d] response: %o', numIdentsLeft, x);
			if (numIdentsLeft < 1) wrapThingsUp();
		},
		dataType: 'json',
		type: 'post'
	});
}


var identTasks = {};
function startIdentify(annotIds, id) {
	//TODO this is tailored for flukebook basically.  see: Great Future Where Multiple IA Plugins Are Seemlessly Supported
	_identAjax(id, annotIds);  //default; pattern-match
	_identAjax(id, annotIds, { OC_WDTW: true });  //will do trailing edge match
}

function _identAjax(id, annotIds, opt) {
	if (!annotIds) return _identCallback(id, { success: false, error: '_identAjax called without annotation IDs' });
	var jdata = { identify: { annotationIds: annotIds }, enqueue: true };
	//var jdata = { identify: { annotationIds: annotIds, limitTargetSize: 10 }, enqueue: true };  //debugging (small set to compare against)
	if (opt) jdata.identify.opt = opt;
	jQuery.ajax({
		url: 'ia',
		type: 'POST',
		dataType: 'json',
		contentType: 'application/javascript',
		success: function(d) { _identCallback(id, d); },
		error: function(x,y,z) {
			console.warn('_identAjax error on %o: %o %o %o', annotIds, x, y, z);
			_identCallback(id, { success: false, error: 'error ' + x});
		},
		data: JSON.stringify(jdata)
	});
}

function _identCallback(id, res) {
console.log("====> _identCallback id=%s got %o", id, res);
	if (!identTasks[id]) identTasks[id] = [];
	identTasks[id].push(res);
	if (identTasks[id].length > 1) {
console.info('completed _identAjax calls with %o', identTasks[id]);
		waitOff();
		var successful = [];
		for (var i = 0 ; i < identTasks[id].length ; i++) {
			if (identTasks[id][i].success) successful.push(identTasks[id][i].taskId);
		}
		if (successful.length > 0) {
			$('#ident-img-wrapper-' + id + ' .ident-img-info').append('<div>created <a target="_new" href="encounters/matchResultsMulti.jsp?taskId=' + successful.join('&taskId=') + '"><b>ident tasks</b></a></div>');
		}
		numIdentsLeft--;
		console.warn('[%d] response: %o', numIdentsLeft, res);
		if (numIdentsLeft < 1) wrapThingsUp();
	}
}


function gaveUp() {
	console.warn('oops, timer gave up on waiting for createEncounters()');
	wrapThingsUp();
}

var allCompleted = false;
function wrapThingsUp() {
	if (allCompleted) return;
	allCompleted = true;
	waitOff();
	$('#ident-begin-note').remove();
	$('#disclaimer').before('<div id="ident-begin-note"><h2>Thank you!</h2><p>Please check your email for follow-up information regarding your submissions.</p></div>');
	sendEmail();
}

function sendEmail() {
	waitOn('Finishing...');
	var data = {
		accessKey: accessKey,
		created: {}
	};
	for (var id in identTasks) {
		if (!encIds[id]) continue;
		data.created[encIds[id]] = [];
		if (!identTasks[id].length) continue;
		for (var i = 0 ; i < identTasks[id].length ; i++) {
			if (!identTasks[id][i].success) continue;
			data.created[encIds[id]].push(identTasks[id][i].taskId);
		}
	}
console.warn('sendEmail() data = %o', data);
	$.ajax({
		url: 'EncounterCreate',
		contentType: 'application/javascript',
		data: JSON.stringify(data),
		dataType: 'json',
		complete: function(x) {
			console.info('sendEmail() -> %o', x);
			waitOff();
			$('#ident-begin-note').append('<input type="button" onClick="document.location.reload()" value="Do more" />');
		},
		type: 'POST'
	});
}


//called by recaptcha widget when user has success
function recaptchaSuccess(response) {
	console.warn('callback from ReCAPTCHA -- sending to server for backend validation');
	recaptchaVerify(function(d) {
		if (d && d.responseJSON && d.responseJSON.success && d.responseJSON.valid) {
			$('#recaptcha-wrapper').remove();
			$('#entire-form').show();
			//this is kinda hacky, but lets us make sure this behaves as expected
			window.recaptchaCompleted = function() { return true; }
		} else {
			$('#recaptcha-wrapper').html('<p class="error">There was a problem verifying.  Please reload and try again.</p>');
		}
	});
}

//note: now this should(!) only be called once when not amHuman known... and as 0th step.  thus we can NOT do synchronous call and instead use callback
function recaptchaVerify(callback) {
<% if (amHuman) out.println("return true; //amHuman short-circuit\n"); %>
	if (amHuman) return true;
	var cvalue = recaptchaCompleted();
	console.info('recaptchaVerify() using %s', cvalue);
	waitOn('Validating...');
	$.ajax({
		type: 'GET',
		url: 'ReCAPTCHA?recaptchaValue=' + cvalue,
		complete: function(d) {
			console.info('recaptchaVerify() got %o', d);
			//we dont really do much on the client side, cuz we most care about session on server... but we could do more here,
			//  especially handle errors etc.   :/    TODO
			waitOff();
			amHuman = true;
			if (callback) callback(d);
		},
		dataType: 'json'
	});
console.warn('fell out of recaptchaVerify() !!!!!!!!!');
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



/* ===============[ all uploader-related below ]==============

https://docs.aws.amazon.com/AWSJavaScriptSDK/guide/browser-examples.html#Amazon_S3
"Uploading a local file using the File API"

*/


function uploadFinished() {
	$('#method-control').hide();
	$('#method-upload').hide();
  	//document.getElementById('updone').innerHTML = '<i>Upload complete. Refresh page to see new image.</i>';
	console.log('upload finished. Files added: '+filenames);
	if (filenames.length < 1) return;  //fail
	var assetsArr = [];
	for (var i = 0 ; i < filenames.length ; i++) {
		assetsArr.push({filename: filenames[i], accessKey: accessKey});
	}


console.warn('assetsArr = %o', assetsArr);
      console.log("creating mediaAsset for filename "+filenames[0]);
	waitOn('Saving data...');
      $.ajax({
        url: 'MediaAssetCreate',
        type: 'POST',
        dataType: 'json',
        contentType: 'application/javascript',
        data: JSON.stringify({
          "MediaAssetCreate": [
            {"assets": assetsArr}
           ]
        }),
		complete: function(x) {
			waitOff();
			console.warn('MediaAssetCreate response: %o', x);
			if ((x.status != 200) || !x.responseJSON || !x.responseJSON.success || !x.responseJSON.withoutSet || !x.responseJSON.withoutSet.length) {
				var msg = 'unknown error';
				if (x.status != 200) msg = 'server error: ' + x.status + ' ' + x.statusText;
				if (x.responseJSON && x.responseJSON.error) msg = x.responseJSON.error;
				$('#ident-workarea').html('<p class="error">' + msg + '</p>');
			} else {
				for (var i = 0 ; i < x.responseJSON.withoutSet.length ; i++) {
					var h = '<div class="ident-img-wrapper ident-toggle" id="ident-img-wrapper-' + i + '">';
					h += '<img class="ident-img" id="ident-img-' + i + '" src="' + x.responseJSON.withoutSet[i].url + '" />';
					h += '<div class="yes-no-icon yes" title="de-select this image"></div>';
					h += '</div>';
					$('#ident-workarea').append(h);
					imageData.push({ success: true });  //hacktacular!
				}
				activateStep(2);
				$('#ident-main-form').before('<p id="click-instructions">Click checkmark to (de-)select images. <span style="margin-left: 15px;" id="ident-begin-note"></span></p>');
				updateBeginNote();
				$('.ident-img-wrapper').css('background-image', 'none');
				$('.ident-toggle .yes-no-icon').bind('click', function(ev) {
console.log(ev);
console.log(ev.currentTarget);
					var jel = $(ev.currentTarget);
console.log('jel = %o', jel);
					if (jel.hasClass('yes')) {
						jel.removeClass('yes');
						jel.addClass('no');
						jel.attr('title', 'select this image');
						jel.parent().find('img').css('opacity', 0.2);
					} else {
						jel.removeClass('no');
						jel.addClass('yes');
						jel.attr('title', 'de-select this image');
						jel.parent().find('img').css('opacity', 1);
					}
					updateBeginNote();
				});
				promptUserForMore(x.responseJSON);
			}
		},
/*
        success: function(d) {
          console.info('Success! Got back '+JSON.stringify(d));
          var maId = d.withoutSet[0].id;
          console.info('parsed id = '+maId);

          var ajaxData = {"attach":"true","EncounterID":"","MediaAssetID":maId};
          var ajaxDataString = JSON.stringify(ajaxData);
          console.info("ajaxDataString="+ajaxDataString);



          $.ajax({
            url: '../MediaAssetAttach',
            type: 'POST',
            dataType: 'json',
            contentType: "application/json",
            data: ajaxDataString,
            success: function(d) {
              console.info("I attached MediaAsset "+maId+" to encounter ");
            },
            error: function(x,y,z) {
              console.warn("failed to MediaAssetAttach");
              console.warn('%o %o %o', x, y, z);
            }
          });


        },
        error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
        }
*/

      });
}


var uploaderFlow;
var uploaderS3Bucket;
var forceLocal = (document.location.search == '?forceLocal');
var mediaAssetSetId = false;
var randomPrefix = Math.floor(Math.random() * 100000);  //this is only used for filenames when we dont get a mediaAssetSetId -- which is hopefully never
var keyToFilename = {};
var pendingUpload = -1;
var filenames = [];

//TODO we should make this more generic wrt elements and events
function uploaderInit(completionCallback) {

    if (useS3Direct()) {
        $('#uptype').html('S3-direct');
        console.info("uploader is using direct-to-s3 uploading to bucket %s", wildbookGlobals.uploader.s3_bucket);
		AWS.config.credentials = {
			accessKeyId: wildbookGlobals.uploader.s3_accessKeyId,
			secretAccessKey: wildbookGlobals.uploader.s3_secretAccessKey
  		};
  		AWS.config.region = wildbookGlobals.uploader.s3_region;
		uploaderS3Bucket = new AWS.S3({params: {Bucket: wildbookGlobals.uploader.s3_bucket}});

		document.getElementById('upload-button').addEventListener('click', function(ev) {
			if (!amHuman && !recaptchaCompleted()) {
				$('#upload-button-message').append('<p class="captcha-error error">Please confirm you are not a robot below.</p>');
				return;
			}
/*
			recaptchaVerify();
			$('#recaptcha-wrapper').hide();
*/
                        document.getElementById('upcontrols').style.display = 'none';
			var files = document.getElementById('file-chooser').files;
                        pendingUpload = files.length;
			for (var i = 0 ; i < files.length ; i++) {
				var params = {
					Key: filenameToKey(files[i].name),
					ContentType: files[i].type,
					Body: files[i]
				};
				var mgr = uploaderS3Bucket.upload(params, function(err, data) {
                                        var dkey = data.key || data.Key;  //weirdly the case changes on the K for multipart! grrr
					var el = findElement(dkey, -1);
console.info('complete? err=%o data=%o', err, data);
					if (err) {
						updateProgress(el, -1, err, 'rgba(250,120,100,0.3)');
                                                pendingUpload--;
                                                if (pendingUpload == 0) completionCallback();
					} else {
						updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
                                                pendingUpload--;
                                                if (pendingUpload == 0) completionCallback();
					}
				});
				mgr.on('httpUploadProgress', function(data) {
//console.info('progress? %o', data);
//console.log('%o %o', data.key, data.size);
					var el = findElement(data.key, data.total);
					var p = ((data.loaded / data.total) * 100) + '%';
					updateProgress(el, p, 'uploading');
				}, false);
			}
  		}, false);


	} else {
        $('#uptype').html('server local');
            console.info("uploader is using uploading direct to host (not S3)");
		flow = new Flow({
  			target:'ResumableUpload',
			forceChunkSize: true,
  			query: { mediaAssetSetId: mediaAssetSetId },
			testChunks: false,
		});
		document.getElementById('upload-button').addEventListener('click', function(ev) {
			$('#upload-button-message').html('');
			if (!recaptchaCompleted()) {
				$('#upload-button-message').append('<p class="captcha-error error">Please confirm you are not a robot below.</p>');
				return;
			}
/*
			recaptchaVerify();
			$('#recaptcha-wrapper').hide();
*/
			var files = flow.files;
//console.log('files --> %o', files);
                        pendingUpload = files.length;
                        for (var i = 0 ; i < files.length ; i++) {
//console.log('%d %o', i, files[i]);
                            filenameToKey(files[i].name);
                        }
                        document.getElementById('upcontrols').style.display = 'none';
console.warn('pendingUpload -> %o', pendingUpload);
			flow.upload();
		}, false);

		flow.assignBrowse(document.getElementById('file-chooser'));
		//flow.assignDrop(document.getElementById('dropTarget'));

		flow.on('fileAdded', function(file, event){
    			console.log('added %o %o', file, event);
			pendingUpload++;
			$('#file-input').hide();
			$('#file-activity').show();
			$('#upcontrols').show();
		});
		flow.on('fileProgress', function(file, chunk){
			var el = findElement(file.name, file.size);
			var p = ((file._prevUploadedSize / file.size) * 100) + '%';
			updateProgress(el, p, 'uploading');
    			console.log('progress %o %o', file._prevUploadedSize, file);
		});
		flow.on('fileSuccess', function(file,message){
			filenames.push(file.name);
			var el = findElement(file.name, file.size);
			updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
    			console.log('success %o %o', file, message);
                        pendingUpload--;
                        if (pendingUpload == 0) completionCallback();
		});
		flow.on('fileError', function(file, message){
    			console.log('error %o %o', file, message);
                        pendingUpload--;
                        if (pendingUpload == 0) completionCallback();
		});

	}
}


function useS3Direct() {
	return false;  //for these purposes i dont think we can go right to S3 since we need to confirm botlessness
    return (!forceLocal && wildbookGlobals && wildbookGlobals.uploader && (wildbookGlobals.uploader.type == 's3direct'));
}


function requestMediaAssetSet(callback) {
    $.ajax({
        url: 'MediaAssetCreate?requestMediaAssetSet',
        type: 'GET',
        dataType: 'json',
        success: function(d) {
            console.info('success got MediaAssetSet: %o -> %s', d, d.mediaAssetSetId);
            mediaAssetSetId = d.mediaAssetSetId;
            callback(d);
        },
        error: function(a,b,c) {
            console.log('error getting MediaAssetSet: %o %o %o', a,b,c);
            alert('error getting Set ID');
        },
    });
}

/*
{
"MediaAssetCreate": [
	{
    	"setId":"567d00b5-b44e-485a-9d77-10987f6dd3e6",
      "assets": [
        {"bucket": "flukebook-dev-upload-tmp", "key": "567d00b5-b44e-485a-9d77-10987f6dd3e6/11854-r043-4f25.jpg"},
        {"bucket": "abc", "key": "xyz"}
        ]
    }
]
}*/

function createMediaAssets(setId, bucket, keys, callback) {
    var assetData = [];
    for (var i = 0 ; i < keys.length ; i++) {
        assetData.push({bucket: bucket, key: keys[i]});
    }
    $.ajax({
        url: 'MediaAssetCreate',
        type: 'POST',
        data: JSON.stringify({
            MediaAssetCreate: [{
                setId: setId,
                assets: assetData
            }]
        }),
        dataType: 'json',
        success: function(d) {
            if (d.success && d.sets) {
                console.info('successfully created MediaAssets: %o', d.sets);
                callback(d);
            } else {
                console.log('error creating MediaAssets: %o', d);
                alert('error saving on server');
                callback(d);
            }
        },
        error: function(a,b,c) {
            console.log('error creating MediaAssets: %o %o %o', a,b,c);
            alert('error saving on server');
            callback({error: a});
        },
    });
}


function filenameToKey(fname) {
    var key = fname;
    if (useS3Direct()) key = (mediaAssetSetId || randomPrefix) + '/' + fname;
    keyToFilename[key] = fname;
console.info('key = %s', key);
    return key;
}

function findElement(key, size) {
        var name = keyToFilename[key];
        if (!name) {
            console.warn('could not find filename for key %o; bailing!', key);
            return false;
        }
	var items = document.getElementsByClassName('file-item');
	for (var i = 0 ; i < items.length ; i++) {
		if ((name == items[i].getAttribute('data-name')) && ((size < 0) || (size == items[i].getAttribute('data-size')))) return items[i];
	}
	return false;
}

function getOffset(name, size) {
	var files = document.getElementById('file-chooser').files;
	for (var i = 0 ; i < files.length ; i++) {
console.warn('%o %o', size, files[i].size);
console.warn('%o %o', name, files[i].name);
		if ((size == files[i].size) &&  (name == files[i].name)) return i;
	}
	return -1;
}


function finfo(o) {
	console.info('%o', o);
}

function filesChanged(f) {
	var h = '';
	for (var i = 0 ; i < f.files.length ; i++) {
		h += '<div class="file-item" id="file-item-' + i + '" data-i="' + i + '" data-name="' + f.files[i].name + '" data-size="' + f.files[i].size + '"><div class="file-name">' + f.files[i].name + '</div><div class="file-size">' + niceSize(f.files[i].size) + '</div><div class="file-status"></div><div class="file-bar"></div></div>';
	}
	document.getElementById('file-activity').innerHTML = h;
}

function updateProgress(el, width, status, bg) {
	if (!el) return;
	var els = el.children;
	if (width < 0) {  //special, means 100%
		els[3].style.width = '100%';
	} else if (width) {
		els[3].style.width = width;
	}
	if (status) els[2].innerHTML = status;
	if (bg) els[3].style.backgroundColor = bg;
}

function niceSize(s) {
	if (s < 1024) return s + 'b';
	if (s < 1024*1024) return Math.floor(s/1024) + 'k';
	return Math.floor(s/(1024*1024) * 10) / 10 + 'M';
}



$(document).ready(function() {
	uploaderInit(uploadFinished);
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

<div id="wait-block"><p>Please wait....</p><img src="images/image-processing.gif" /></div>

<h1>Identify humpback fluke images</h1>

<div id="steps">
	<div id="step-1" class="step step-active">
		<span class="step-num">1</span>
		Upload images
	</div>
	<div id="step-2" class="step">
		<span class="step-num">2</span>
		Add metadata
	</div>
	<div id="step-3" class="step">
		<span class="step-num">3</span>
		Start matching
	</div>
</div>


<div id="entire-form">
<div id="method-control">
	<b>Upload image files</b>
	<a onClick="return switchMethod()">Use image URLs instead</a>
</div>

<div id="ident-message">
	<%= ((sources == null) ? "" : "<p class=\"error\">there was an error parsing sources</p>") %>
</div>

<div id="ident-workarea"></div>
<div style="clear: both;"></div>

<div id="method-upload">


	<div style="margin-top: 100px; padding: 5px; display: none;" >upload method being used: <b><span id="uptype"></span></b></div>

	<div id="file-input">
		<input style="display: inline-block;" type="file" id="file-chooser" multiple accept="audio/*,video/*,image/*" onChange="return filesChanged(this)" /> 
	</div>
	<div id="file-activity">
	</div>

	<div id="updone"></div>

	<div id="upcontrols" style="padding: 20px;">
		<button id="upload-button">begin upload</button>
		<div id="upload-button-message" style="display: inline-block;"></div>
	</div>

</div>

<div id="method-url" style="display: none;">
	<textarea placeholder="https://www.example.com/images/fluke.jpg" id="ident-sources"><%= ((sources == null) ? "" : StringUtils.join(sources, "\n")) %></textarea>

	<div id="ident-controls">
		<input type="button" value="continue" onClick="return beginProcess();" />
	</div>
</div>

<%
	String emailVal = "";
	if (AccessControl.isAnonymous(request)) {
		emailVal = (String)request.getSession().getAttribute("USER_EMAIL");
	} else {
		String username = request.getUserPrincipal().getName();
		User user = null;
		if (username != null) user = myShepherd.getUser(username);
		if (user != null) emailVal = user.getEmailAddress();
	}
	if (emailVal == null) emailVal = "";
%>
<div id="ident-main-form">
	<p>Enter <b>email address</b> below and <b>set dates and locations</b> for sightings above.</p>
	<input id="ident-email" value="<%=emailVal%>" type="email" placeholder="email address (required)" style="width: 18em;" />
	<p>
		<input type="button" id="ident-begin-button" value="start identification" onClick="return createEncounters();" />
	</p>
</div>


<!--
	<a target="_new" href="EncounterCreate?species=Megaptera%20novaeangliae&source=https://pacificwhale.files.wordpress.com/2014/03/whale_fluke_prebranding_img.jpg?w%3D1200">direct test of EncounterCreate</a>
-->


<%
}
%>

</div> <!-- entire-form -->

<% if (amHuman) { %>
	<script>
		function recaptchaCompleted() { return true; } //amHuman-certified
	</script>

<% } else { //unknown human! %>
	<div id="recaptcha-wrapper">
	<p>Let's verify that you are human...</p>
	<%= ReCAPTCHA.captchaWidget(request, null, "data-callback=\"recaptchaSuccess\"") %>
	</div>
<% } %>

<div id="disclaimer">
<b>DISCLAIMER:</b>
Images are for non-profit scientific research purposes only.
Data will be removed after 7 days unless you take further action to incorporate it into Flukebook.org.
</div>
<div style="clear: both; margin-bottom: 100px;"></div>

<jsp:include page="footer.jsp" flush="true"/>

