<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.json.JSONObject,
org.ecocean.media.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,
org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>

<%

String context = ServletUtilities.getContext(request);

if (request.getParameter("annotId") != null) {
	String annId = request.getParameter("annotId");
	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction("matchResults.jsp1");
	myShepherd.beginDBTransaction();
       	Annotation ann = null;
	JSONObject rtn = new JSONObject("{\"success\": false}");
	//Encounter enc = null;
	try {
        	ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annId), true)));
	} catch (Exception ex) {}
	if (ann == null) {
		rtn.put("error", "unknown error");
	} else {
		rtn.put("success", true);
		rtn.put("annId", annId);
		MediaAsset ma = ann.getMediaAsset();
		if (ma != null) {
			rtn.put("asset", Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject())));
		}
	}
/*
	if ((qann != null) && (qann.getMediaAsset() != null)) {
		qMediaAssetJson = qann.getMediaAsset().sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject()).toString();
        	enc = Encounter.findByAnnotation(qann, myShepherd2);
		num = enc.getCatalogNumber();
	}
*/
	myShepherd.rollbackDBTransaction();
	out.println(rtn.toString());
	return;
}


//TODO security for this stuff, obvs?
//quick hack to set id & approve
if ((request.getParameter("number") != null) && (request.getParameter("individualID") != null)) {
	JSONObject res = new JSONObject("{\"success\": false}");
	res.put("encounterId", request.getParameter("number"));
	res.put("encounterId2", request.getParameter("enc2"));
	res.put("individualId", request.getParameter("individualID"));
	//note: short circuiting for now!  needs more testing

	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction("matchResults.jsp1");
	myShepherd.beginDBTransaction();

	Encounter enc = myShepherd.getEncounter(request.getParameter("number"));
	if (enc == null) {
		res.put("error", "no such encounter: " + request.getParameter("number"));
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}

	Encounter enc2 = null;
	if (request.getParameter("enc2") != null) {
		enc2 = myShepherd.getEncounter(request.getParameter("enc2"));
		if (enc == null) {
			res.put("error", "no such encounter: " + request.getParameter("enc2"));
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
	}

	/* now, making an assumption here (and the UI does as well):
	   basically, we only allow a NEW INDIVIDUAL when both encounters are unnamed;
	   otherwise, we are assuming we are naming one based on the other.  thus, we MUST
	   use an *existing* indiv in those cases (but allow a new one in the other)
	*/

	MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(request.getParameter("individualID"));
	if ((indiv == null) && (enc != null) && (enc2 != null)) {
//TODO make actual individual yo!!!!
//indiv.addComment(????)
		res.put("error", "Creating a new MarkedIndividual currently not supported. YET! Sorry.");
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}

	if (indiv == null) {
		res.put("error", "Unknown individual " + request.getParameter("individualID"));
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}

// TODO enc.setMatchedBy() + comments + etc?????
	enc.setIndividualID(indiv.getIndividualID());
	enc.setState("approved");
	indiv.addEncounter(enc, context);
	if (enc2 != null) {
		enc2.setIndividualID(indiv.getIndividualID());
		enc2.setState("approved");
		indiv.addEncounter(enc2, context);
	}
	myShepherd.getPM().makePersistent(indiv);
	
	myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	res.put("success", true);
	out.println(res.toString());
	return;
}



  //session.setMaxInactiveInterval(6000);
  //String taskId = request.getParameter("taskId");

%>


<style type="text/css">

#encounter-info {
	padding: 2px 10px;
	background-color: rgba(255,255,0,0.7);
}

#encounter-info .enc-title {
	color: #333;
	font-size: 1.5em;
}

#encounter-info .enc-link {
	color: #777;
}

#enc-action {
	position: relative;
	top: -5px;
	left: 20px;
	display: inline-block;
	font-size: 0.7em;
}

#enc-action input[type="button"] {
	line-height: 1em;
	padding: auto;
	margin: 0;
}

.throbbing {
	background: url(../images/throbber.gif) no-repeat left top !important;
}

.task-title {
	background-color: #FAA;
	margin: 10px 0;
	padding: 8px;
}
.task-title-id {
	color: #555;
}

.task-content {
	height: 520px;
}
.annot-wrapper {
	width: 48%;
}

.summary-column {
	display: inline-block;
	width: 32%;
	vertical-align: top;
}


.annot-summary-checked {
	background-color: #FE8;
}

.annot-summary {
	padding: 1px 10px;
	margin: 4px 0;
}

.annot-summary:hover {
	background-color: #8E8;
}

.annot-wrapper {
	position: relative;
}
.annot-wrapper img {
	width: 100%;
}

.annot-action-checkbox-active, .annot-action-checkbox-inactive {
	margin-right: 10px !important;
	float: right;
	transform: scale(1.2);
}

.annot-action-checkbox-inactive {
	display: none;
}

.annot-summary:hover .annot-action-checkbox-inactive {
	cursor: pointer;
	display: inline-block;
}

.annot-wrapper .img-info {
	position: absolute;
	top: 4px;
	left: 4px;
	font-size: 0.8em;
	background-color: rgba(255,255,255,0.7);
	padding: 1px 3px;
	border-radius: 2px;
}
.annot-wrapper .img-info-type {
	background-color: #4F8;
	color: #222;
	margin-right: 8px;
	padding: 1px 4px 2px 4px;
}

.annot-wrapper-query .annot-info {
	display: none;
}
.annot-info-num {
	font-size: 0.8em;
	color: #666;
	display: inline-block;
	margin-right: 10px;
	text-align: right;
	width: 1.3em;
}

.annot-wrapper-query {
	float: left;
}
.annot-wrapper-dict {
	float: right;
	display: none;
}

.annot-info {
	display: inline-block;
}

.enc-link, .indiv-link {
	vertical-align: middle;
	font-size: 0.8em;
	padding: 0 3px;
	border-radius: 4px;
	margin: 0 3px 0 15px;
	background-color: #DDD;
	overflow: hidden;
	display: inline-block;
	white-space: nowrap;
	max-width: 120px;
}
.enc-link:hover, .indiv-link:hover {
	background-color: #DD4;
	text-decoration: none;
}

.task-title .enc-link, .task-title .indiv-link {
	max-width: none !important;
}

#approval-buttons {
	height: 5em;
}

#link {
	clear: both;
}

#results {
	display: inline-block;
}

#result-images {
	margin-bottom: 100px;
}

td.ptcol-overall_score,
td.ptcol-score_holmbergIntersection,
td.ptcol-score_fastDTW,
td.ptcol-score_I3S,
td.ptcol-score_proportion {
	text-align: right;
}

.ptcol-adaboost_match {
        display: none !important;
}

/*
td.ptcol-encounterID:hover, td.ptcol-individualID:hover {
	background-color: #FF0 !important;
	outline: solid black 2px;
}
*/

td.ptcol-encounterID, td.ptcol-individualID {
	position: relative !important;
}
tr.clickable:hover .link-button {
	display: inline-block;
}

.indiv-button {
	display: none;
}
.enc-button {
	display: inline-block;
}
.link-button, .link-button:hover {
	position: absolute;
	right: 2px;
	bottom: 2px;
	background-color: #FFA;
	padding: 1px 4px;
	border: solid #444 1px;
	border-radius: 4px;
	margin: 0 3px;
	color: #444;
	text-decoration: none;
}
.link-button:hover {
	color: #000;
	background-color: #FF0;
}

#result-images {
	height: 300px;
	position: relative;
}

#image-main {
	background-color: #02F;
}
#image-compare {
	background-color: #FAFA00;
}
.result-image-wrapper {
	padding: 9px;
	border-radius: 6px;
	width: 47%;
	margin: 4px;
	float: left;
	top: 0;
}

.result-image-wrapper img {
	top: 0;
	left: 0;
	width: 100%;
}

.result-image-wrapper .note, #chart .note {
	background-color: rgba(0,0,0,0.5);
	border-radius: 10px;
	padding: 5px;
	margin: 50px 10px 0 10px;
	text-align: center;
	color: #FFF;
	font-size: 0.9em;
}


.image-info {
	padding: 5px;
	margin: 8px;
	margin-bottom: -75px;
	width: 43%;
	background-color: rgba(255,255,255,0.7);
	font-size: 0.8em;
	position: absolute;
	bottom: 0;
}


#image-meta {
	width: 100%;
	text-align:center;
}
#image-meta #score {
	display: inline-block;
	padding: 3px 15px;
	border-radius: 12px;
	background-color: rgba(0,0,0,0.7);
	color: #FFF;
	z-index: 9999 !important;
	position: relative;
	margin-bottom: -25px;
}


/* makes up for nudging of chart */
#chart .note {
	width: 80%;
}

#chart {
	margin: 75px 0 -30px 70px;
	height: 400px;
}


</style>




<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">
</div>

<jsp:include page="../footer.jsp" flush="true"/>



<script src="../javascript/underscore-min.js"></script>
<script src="../javascript/backbone-min.js"></script>
<script src="../javascript/core.js"></script>
<script src="../javascript/classes/Base.js"></script>

<script src="../javascript/tablesorter/jquery.tablesorter.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
<link rel="stylesheet" href="../css/pageableTable.css" />
<script src="../javascript/tsrt.js"></script>
<script src="../javascript/flukeScanEnd.js"></script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript">
/*
	google.load('visualization', '1.1', {packages: ['line', 'corechart']});
    	google.setOnLoadCallback(initChart);
*/
</script>


<script>
var serverTimestamp = <%= System.currentTimeMillis() %>;
var pageStartTimestamp = new Date().getTime();
var taskIds = [];
var jobIdMap = {};
var timers = {};
var matchInstructions = 'Select <b>correct match</b> from results below by <i>hovering</i> over result and checking the <i>checkbox</i>.';
var queryAnnotation = {};

function init2() {   //called from wildbook.init() when finished
	$('.nav-bar-wrapper').append('<div id="encounter-info"><div class="enc-title" /></div>');
	parseTaskIds();
	for (var i = 0 ; i < taskIds.length ; i++) {
		grabTaskResult(taskIds[i]);
	}
}


function parseTaskIds() {
	var a = window.location.search.substring(1).split('&');
	for (var i = 0 ; i < a.length ; i++) {
		if (a[i].indexOf('taskId=') == 0) taskIds.push(a[i].substring(7));
	}
}

function grabTaskResult(tid) {
	if (!$('#task-' + tid).length) {
		$('.maincontent').append('<div class="task-content" id="task-' + tid + '"><div class="task-title"><span class="task-title-id" title="task id ' + tid + '"></span></div><div class="task-summary"><div class="summary-column col0" /><div class="summary-column col1" /><div class="summary-column col2" /></div></div>');
	}
	var mostRecent = false;
	var gotResult = false;
console.warn('------------------- %s', tid);
	$.ajax({
		url: '../iaLogs.jsp?taskId=' + tid,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
			$('#wait-message-' + tid).remove();  //in case it already exists from previous
			for (var i = 0 ; i < d.length ; i++) {
				if (d[i].serviceJobId && (d[i].serviceJobId != '-1')) {
					if (!jobIdMap[tid]) jobIdMap[tid] = { timestamp: d[i].timestamp, jobId: d[i].serviceJobId, manualAttempts: 0 };
				}
				//console.log('d[i].status._action --> %o', d[i].status._action);
				if (d[i].status && d[i].status._action == 'getJobResult') {
					showTaskResult(d[i]);
					i = d.length;
					gotResult = true;
				} else {
					if (!mostRecent && d[i].status && d[i].status._action) mostRecent = d[i].status._action;
				}
			}
			if (!gotResult) {
				//$('#task-' + tid).append('<p id="wait-message-' + tid + '" title="' + (mostRecent? mostRecent : '[unknown status]') + '" class="waiting throbbing">waiting for results <span onClick="manualCallback(\'' + tid + '\')" style="float: right">*</span></p>');
				$('#task-' + tid).append('<p id="wait-message-' + tid + '" title="' + (mostRecent? mostRecent : '[unknown status]') + '" class="waiting throbbing">waiting for results</p>');
				if (jobIdMap[tid]) {
					var tooLong = 15 * 60 * 1000;
					var elapsed = approxServerTime() - jobIdMap[tid].timestamp;
					console.warn("elapsed = %.1f min", elapsed / 60000);
					if (elapsed > tooLong) {
						if (timers[tid] && timers[tid].timeout) clearTimeout(timers[tid].timeout);
						$('#wait-message-' + tid).removeClass('throbbing').html('attempting to fetch results');
						manualCallback(tid);
					} else {
						if (!timers[tid]) timers[tid] = { attempts: 0 };
						if (timers[tid].attempts > 1000) {
							if (timers[tid] && timers[tid].timeout) clearTimeout(timers[tid].timeout);
							$('#wait-message-' + tid).html('gave up trying to obtain results').removeClass('throbbing');;
						} else {
							timers[tid].attempts++;
							timers[tid].timeout = setTimeout(function() { console.info('ANOTHER %s!', tid); grabTaskResult(tid); }, 1700);
						}
					}
				} else {
					if (!timers[tid]) timers[tid] = { attempts: 0 };
					timers[tid].attempts++;
					timers[tid].timeout = setTimeout(function() { console.info('ANOTHER %s!', tid); grabTaskResult(tid); }, 1700);
				}
			} else {
				if (timers[tid] && timers[tid].timeout) clearTimeout(timers[tid].timeout);
			}
console.info('>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> got %o on tid=%s', d, tid);
		},
		error: function(a,b,c) {
console.info('!!>> got %o', d);
			console.error(a, b, c);
			$('#task-' + tid).append('<p class="error">there was an error with task ' + tid + '</p>');
		}
	});
}


function approxServerTime() {
	return serverTimestamp + (new Date().getTime() - pageStartTimestamp);
}

function manualCallback(tid) {
	var m = jobIdMap[tid];
	if (!m || !m.jobId) return alert('Could not find jobid for ' + tid);
	if (jobIdMap[tid].manualAttempts > 3) {
		//$('#wait-message-' + tid).html('failed to obtain results').removeClass('throbbing');
		$('#wait-message-' + tid).html('Still waiting on results. Please try again later. IA is most likely ingesting a large amount of data.').removeClass('throbbing');
		return;
	}
	jobIdMap[tid].manualAttempts++;
	$('#wait-message-' + tid).html('<i>attempting to manually query IA</i>').removeClass('throbbing');;
	console.log(m);

	$.ajax({
		url: '../IBEISIAGetJobStatus.jsp?jobid=' + m.jobId,
		type: 'GET',
		dataType: 'json',
		complete: function(x, stat) {
			console.log('status = %o; xhr=%o', stat, x);
/*
			var msg = '<i>unknown results</i>';
			if (x.responseJSON && x.responseJSON.continue) {
				msg = '<b>tried to get results (<i>reload</i> to check)</b>';
			} else if (x.responseJSON && !x.responseJSON.continue) {
				msg = '<b>disallowed getting results (already tried and failed)</b>';
			}
			$('#wait-message-' + tid).html(msg + ' [returned status=<i title="' + x.responseText + '">' + stat + '</i>]');
*/
		}
	});
	$('#wait-message-' + tid).remove();
	grabTaskResult(tid);

	//$('#wait-message-' + tid).html(m.jobId);
	//alert(m.jobId);
}

var RESMAX = 12;
function showTaskResult(res) {
	console.log("RRRRRRRRRRRRRRRRRRRRRRRRRRESULT showTaskResult() %o on %s", res, res.taskId);
	if (res.status && res.status._response && res.status._response.response && res.status._response.response.json_result &&
			res.status._response.response.json_result.cm_dict) {
		var isEdgeMatching = (res.status._response.response.json_result.query_config_dict &&
			(res.status._response.response.json_result.query_config_dict.pipeline_root == 'OC_WDTW'));
		var qannotId = res.status._response.response.json_result.query_annot_uuid_list[0]['__UUID__'];
		//$('#task-' + res.taskId).append('<p>' + JSON.stringify(res.status._response.response.json_result) + '</p>');
		console.warn('json_result --> %o %o', qannotId, res.status._response.response.json_result['cm_dict'][qannotId]);

		var h = 'Matches based on <b>' + (isEdgeMatching ? 'trailing edge' : 'pattern') + '</b>';
		if (res.timestamp) {
			var d = new Date(res.timestamp);
			h += '<span style="color: #FFF; margin: 0 11px; font-size: 0.7em;">' + d.toLocaleString() + '</span>';
		}

		h += '<span style="margin-left: 30px; font-size: 0.8em; color: #777;">Hover mouse over listings below to <b>compare results</b> to target. Links to <b>encounters</b> and <b>individuals</b> given next to match score.</span>';
		$('#task-' + res.taskId + ' .task-title-id').html(h);
		displayAnnot(res.taskId, qannotId, -1, -1);

		var sorted = score_sort(res.status._response.response.json_result['cm_dict'][qannotId]);
		if (!sorted || (sorted.length < 1)) {
			//$('#task-' + res.taskId + ' .waiting').remove();  //shouldnt be here (cuz got result)
			//$('#task-' + res.taskId + ' .task-summary').append('<p class="xerror">results list was empty.</p>');
			$('#task-' + res.taskId + ' .task-summary').append('<p class="xerror">Image Analysis has returned and no match was found.</p>');
			return;
		}
		var max = sorted.length;
		if (max > RESMAX) max = RESMAX;
		for (var i = 0 ; i < max ; i++) {
			var d = sorted[i].split(/\s/);
			displayAnnot(res.taskId, d[1], i, d[0]);
		}
		$('.annot-summary').on('mouseover', function(ev) { annotClick(ev); });
		$('#task-' + res.taskId + ' .annot-wrapper-dict:first').show();

	} else {
		$('#task-' + res.taskId).append('<p class="error">there was an error parsing results for task ' + res.taskId + '</p>');
	}
}


function displayAnnot(taskId, annId, num, score) {
console.info('%d ===> %s', num, annId);
	var h = '<div data-annid="' + annId + '" class="annot-summary annot-summary-' + annId + '">';
	h += '<div class="annot-info"><span class="annot-info-num">' + (num + 1) + '</span> <b>' + score.toString().substring(0,6) + '</b></div></div>';
	var perCol = Math.ceil(RESMAX / 3);
	if (num >= 0) $('#task-' + taskId + ' .task-summary .col' + Math.floor(num / perCol)).append(h);
	//now the image guts
	h = '<div class="annot-wrapper annot-wrapper-' + ((num < 0) ? 'query' : 'dict') + ' annot-' + annId + '">';
	//h += '<div class="annot-info">' + (num + 1) + ': <b>' + score + '</b></div></div>';
	$('#task-' + taskId).append(h);
	$.ajax({
		url: 'matchResultsMulti.jsp?annotId=' + annId,
		type: 'GET',
		dataType: 'json',
		complete: function(d) { displayAnnotDetails(taskId, d, num); }
	});
}

function displayAnnotDetails(taskId, res, num) {
	var isQueryAnnot = (num < 0);
console.warn('+++++++ isQueryAnnot %o', isQueryAnnot);
	if (!res || !res.responseJSON || !res.responseJSON.success || res.responseJSON.error) {
		console.warn('error on (task %s) res = %o', taskId, res);
		return;
	}
	var encId = false;
	var indivId = false;
	var imgInfo = '';
	if (res.responseJSON.asset) {
		if (res.responseJSON.asset.url) {
			$('#task-' + taskId + ' .annot-' + res.responseJSON.annId).append('<img src="' + res.responseJSON.asset.url + '" />');
		} else {
			$('#task-' + taskId + ' .annot-' + res.responseJSON.annId).append('<img src="../images/no_images.jpg" style="padding: 10%" />');
		}
		if (res.responseJSON.asset.dateTime) {
			imgInfo += ' <b>' + res.responseJSON.asset.dateTime.substring(0,16) + '</b> ';
		}
		if (res.responseJSON.asset.filename) {
			var fn = res.responseJSON.asset.filename;
			var i = fn.lastIndexOf('/');
			if (i > -1) fn = fn.substring(i + 1);
			imgInfo += ' ' + fn + ' ';
		}
		if (res.responseJSON.asset.features && (res.responseJSON.asset.features.length > 0)) {
			encId = res.responseJSON.asset.features[0].encounterId;
			indivId = res.responseJSON.asset.features[0].individualId;
			var h = 'Matching results';
			if (encId) {
				h += ' for <a style="margin-top: -6px;" class="enc-link" target="_new" href="encounter.jsp?number=' + encId + '" title="open encounter ' + encId + '">Encounter ' + encId.substring(0,6) + '</a>';
				$('#task-' + taskId + ' .annot-summary-' + res.responseJSON.annId).append('<a class="enc-link" target="_new" href="encounter.jsp?number=' + encId + '" title="encounter ' + encId + '">enc ' + encId + '</a>');
			}
			if (indivId) {
				h += ' of <a class="indiv-link" title="open individual page" target="_new" href="../individuals.jsp?number=' + indivId + '">' + indivId + '</a>';
				$('#task-' + taskId + ' .annot-summary-' + res.responseJSON.annId).append('<a class="indiv-link" target="_new" href="../individuals.jsp?number=' + indivId + '">' + indivId + '</a>');
			}
			if (encId || indivId) {
				$('#task-' + taskId + ' .annot-summary-' + res.responseJSON.annId).append('<input title="use this encounter" type="checkbox" class="annot-action-checkbox-inactive" id="annot-action-checkbox-' + res.responseJSON.annId +'" data-encid="' + (encId || '') + '" data-individ="' + (indivId || '') + '" onClick="return annotCheckbox(this);" />');
			}

			h += '<div id="enc-action">' + matchInstructions + '</div>';
			//if (isQueryAnnot && h) $('#task-' + taskId + ' .task-title').append(h);
			if (isQueryAnnot) {
				queryAnnotation = {
					id: res.responseJSON.annId,
					encId: encId,
					indivId: indivId
				};
				if (h) $('#encounter-info .enc-title').html(h);
				if (imgInfo) imgInfo = '<span class="img-info-type">TARGET</span> ' + imgInfo;
			} else {
				if (imgInfo) imgInfo = '<span class="img-info-type">#' + (num+1) + '</span> ' + imgInfo;
			}
		}
	}
	if (imgInfo) $('#task-' + taskId + ' .annot-' + res.responseJSON.annId).append('<div class="img-info">' + imgInfo + '</div>');
}


function annotCheckbox(el) {
	var jel = $(el);
	annotCheckboxReset();
	if (!el.checked) return;
	jel.removeClass('annot-action-checkbox-inactive').addClass('annot-action-checkbox-active');
	jel.parent().addClass('annot-summary-checked');

	var h;
	if (!queryAnnotation.encId || !jel.data('encid')) {
		h = '<i>Insuffient encounter data for any actions</i>';
	} else if (jel.data('individ')==queryAnnotation.indivId) {
		h = 'The target and candidate are already assigned to the <b>same individual ID</b>. No further action is needed to confirm this match.'
	} else if (jel.data('individ') && queryAnnotation.indivId) {
		h = 'The two encounters have <b>different individuals</b> already assigned and must be handled manually.';
	} else if (jel.data('individ')) {
		h = '<b>Confirm</b> action: &nbsp; <input onClick="approvalButtonClick(\'' + queryAnnotation.encId + '\', \'' + jel.data('individ') + '\');" type="button" value="Set to individual ' + jel.data('individ') + '" />';
	} else if (queryAnnotation.indivId) {
		h = '<b>Confirm</b> action: &nbsp; <input onClick="approvalButtonClick(\'' + jel.data('encid') + '\', \'' + queryAnnotation.indivId + '\');" type="button" value="Use individual ' + jel.data('individ') + ' for unnamed match below" />';
	} else {
		h = '<input onChange="approveNewIndividual(this);" size="20" placeholder="Type new or existing name" ';
		h += ' data-query-enc-id="' + queryAnnotation.encId + '" ';
		h += ' data-match-enc-id="' + jel.data('encid') + '" ';
		h += ' /> <input type="button" value="Set individual on both encounters" />'
	}
	$('#enc-action').html(h);
	return true;
}

function annotCheckboxReset() {
	$('.annot-action-checkbox-active').removeClass('annot-action-checkbox-active').addClass('annot-action-checkbox-inactive').prop('checked', false);
	$('.annot-summary-checked').removeClass('annot-summary-checked');
	$('#enc-action').html(matchInstructions);
}

function annotClick(ev) {
	//console.log(ev);
	var annId = ev.currentTarget.getAttribute('data-annid');
	var taskId = $(ev.currentTarget).closest('.task-content').attr('id').substring(5);
	//console.warn('%o | %o', taskId, annId);
	$('#task-' + taskId + ' .annot-wrapper-dict').hide();
	$('#task-' + taskId + ' .annot-' + annId).show();
}

function score_sort(cm_dict, topn) {
console.warn('score_sort() cm_dict %o', cm_dict);
//.score_list vs .annot_score_list ??? TODO are these the same? seem to be same values
	if (!cm_dict.score_list || !cm_dict.dname_uuid_list) return;
	var sorta = [];
	if (cm_dict.score_list.length < 1) return;
	//for (var i = 0 ; i < cm_dict.score_list.length ; i++) {
	for (var i = 0 ; i < cm_dict.score_list.length ; i++) {
		if (cm_dict.score_list[i] < 0) continue;
		sorta.push(cm_dict.score_list[i] + ' ' + cm_dict.dannot_uuid_list[i]['__UUID__']);
	}
	sorta.sort().reverse();
	return sorta;
}

function foo() {
    	$('#result-images').append('<div class="result-image-wrapper" id="image-main" />');
    	$('#result-images').append('<div class="result-image-wrapper" id="image-compare" />');
	//if (qMediaAsset) addImage(fakeEncounter({}, qMediaAsset),jQuery('#image-main'));
	if (qMediaAsset) jQuery('#image-main').append('<img src="' + wildbook.cleanUrl(qMediaAsset.url) + '" />');
	jQuery('#image-compare').append('<img style="height: 11px; width: 50%; margin: 40px 25%;" src="../images/image-processing.gif" />');
	checkForResults();
}

function checkForResults() {
	jQuery.ajax({
		url: '../ia?getJobResultFromTaskID=' + taskId,
		success: function(d) {
			console.info(d);
			processResults(d);
		},
		error: function() {
			alert('error fetching results');
		},
		dataType: 'json'
	});
}

var countdown = 100;
function processResults(res) {
	if (!res || !res.queryAnnotation) {
console.info('waiting to try again...');
		$('#results').html('Waiting for results. You may leave this page.  [countdown=' + countdown + ']');
		countdown--;
		if (countdown < 0) {
			$('#results').html('Gave up waiting for results, sorry.  Reload to wait longer.');
			return;
		}
		setTimeout(function() { checkForResults(); }, 3000);
		return;
	}
	if (res.queryAnnotation.encounter && res.queryAnnotation.mediaAsset) {
		jQuery('#image-main').html('');
		addImage(fakeEncounter(res.queryAnnotation.encounter, res.queryAnnotation.mediaAsset),
			 jQuery('#image-main'));
	}
	if (!res.matchAnnotations || (res.matchAnnotations.length < 1)) {
		jQuery('#image-compare').html('<img style="width: 225px; margin: 20px 30%;" src="../images/image-not-found.jpg" />');
		$('#results').html('No matches found.');
		return;
	}
	if (res.matchAnnotations.length == 1) {
		var altIDString = res.matchAnnotations[0].encounter.otherCatalogNumbers || '';
		if (altIDString && altIDString.length > 0) {
      			altIDString = ', altID '+altIDString;
    		}

		$('#results').html('One match found (<a target="_new" href="encounter.jsp?number=' +
			res.matchAnnotations[0].encounter.catalogNumber +
			'">' + res.matchAnnotations[0].encounter.catalogNumber +
			'</a> id ' + (res.matchAnnotations[0].encounter.individualID || 'unknown') + altIDString +
			') - score ' + res.matchAnnotations[0].score + approvalButtons(res.queryAnnotation, res.matchAnnotations));
		updateMatch(res.matchAnnotations[0]);
		return;
	}
	// more than one match
	res.matchAnnotations.sort(function(a,b) {
		if (!a.score || !b.score) return 0;
		return b.score - a.score;
	});
	updateMatch(res.matchAnnotations[0]);
	var h = '<p><b>' + res.matchAnnotations.length + ' matches</b></p><ul>';
	for (var i = 0 ; i < res.matchAnnotations.length ; i++) {
      // a little handling of the alternate ID
      var altIDString = res.matchAnnotations[i].encounter.otherCatalogNumbers || '';
	if (altIDString && altIDString.length > 0) {
		altIDString = ' (altID: '+altIDString+')';
	}
		h += '<li data-i="' + i + '"><a target="_new" href="encounter.jsp?number=' +
			res.matchAnnotations[i].encounter.catalogNumber + '">' +
			res.matchAnnotations[i].encounter.catalogNumber + altIDString + '</a> (' +
			(res.matchAnnotations[i].encounter.individualID || 'unidentified') + '), score = ' +
			res.matchAnnotations[i].score + '</li>';
	}
	h += '</ul><div>' + approvalButtons(res.queryAnnotation, res.matchAnnotations) + '</div>';

	$('#results').html(h);
	$('#results li').on('mouseover', function(ev) {
		var i = ev.currentTarget.getAttribute('data-i');
		updateMatch(res.matchAnnotations[i]);
	});
}

function updateMatch(m) {
		jQuery('#image-compare').html('');
		addImage(fakeEncounter(m.encounter, m.mediaAsset),jQuery('#image-compare'));
}

function fakeEncounter(e, ma) {
	var enc = new wildbook.Model.Encounter(e);
	enc.set('annotations', [{mediaAsset: ma}]);
	return enc;
}


/////// these are disabled now, as matching results "bar" handles actions
function approvalButtons(qann, manns) {
	return '';
	if (!manns || (manns.length < 1) || !qann || !qann.encounter) return '';
console.info(qann);
	var inds = [];
	for (var i = 0 ; i < manns.length ; i++) {
		if (!manns[i].encounter || !manns[i].encounter.individualID) continue;
		if (inds.indexOf(manns[i].encounter.individualID) > -1) continue;
		if (manns[i].encounter.individualID == qann.encounter.individualID) continue;
		inds.push(manns[i].encounter.individualID);
	}
console.warn(inds);
	if (inds.length < 1) return '';
	var h = ' <div id="approval-buttons">';
	for (var i = 0 ; i < inds.length ; i++) {
		h += '<input type="button" onClick="approvalButtonClick(\'' + qann.encounter.catalogNumber + '\', \'' +
		     inds[i] + '\');" value="Approve as assigned to ' + inds[i] + '" />';
	}
	return h + '</div>';
}


function approvalButtonClick(encID, indivID, encID2) {
	var msgTarget = '#enc-action';  //'#approval-buttons';
	console.info('approvalButtonClick: id(%s) => %s %s', indivID, encID, encID2);
	if (!indivID || !encID) {
		jQuery(msgTarget).html('Argument errors');
		return;
	}
	jQuery(msgTarget).html('<i>saving changes...</i>');
	var url = 'matchResultsMulti.jsp?number=' + encID + '&individualID=' + indivID;
	if (encID2) url += '&enc2=' + encID2;
	jQuery.ajax({
		url: url,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
console.warn(d);
			if (d.success) {
				jQuery(msgTarget).html('<i><b>Update successful</b> - please wait....</i>');
				//////window.location.href = 'encounter.jsp?number=' + encID;
			} else {
				console.warn('error returned: %o', d);
				jQuery(msgTarget).html('Error updating encounter: <b>' + d.error + '</b>');
			}
		},
		error: function(x,y,z) {
			console.warn('%o %o %o', x, y, z);
			jQuery(msgTarget).html('<b>Error updating encounter</b>');
		}
	});
	return true;
}


function approveNewIndividual(el) {
	var jel = $(el);
	console.info('name=%s; qe=%s, me=%s', jel.val(), jel.data('query-enc-id'), jel.data('match-enc-id'));
	return approvalButtonClick(jel.data('query-enc-id'), jel.val(), jel.data('match-enc-id'));
}


</script>
