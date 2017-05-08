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

//quick hack to set id & approve

/*
//quick hack to set id & approve
if ((request.getParameter("number") != null) && (request.getParameter("individualID") != null)) {
	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction("matchResults.jsp1");
	myShepherd.beginDBTransaction();
	Encounter enc = myShepherd.getEncounter(request.getParameter("number"));
	if (enc == null) {
		out.println("{\"success\": false, \"error\": \"no such encounter\"}");
		myShepherd.rollbackDBTransaction();
	} else {
		enc.setIndividualID(request.getParameter("individualID"));
		enc.setState("approved");
		myShepherd.commitDBTransaction();
		out.println("{\"success\": true}");
	}
	myShepherd.closeDBTransaction();
	return;
}
*/


  //session.setMaxInactiveInterval(6000);
  //String taskId = request.getParameter("taskId");

%>


<style type="text/css">

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
var taskIds = [];
function init2() {   //called from wildbook.init() when finished
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
	$('.maincontent').append('<div class="task-content" id="task-' + tid + '" />');
	$.ajax({
		url: '../iaLogs.jsp?taskId=' + tid,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
			for (var i = 0 ; i < d.length ; i++) {
				//console.log('d[i].status._action --> %o', d[i].status._action);
				if (d[i].status && d[i].status._action == 'getJobResult') {
					showTaskResult(d[i]);
					i = d.length;
				}
			}
//console.info('>>>> got %o', d);
		},
		error: function(a,b,c) {
			console.error(a, b, c);
			$('#task-' + tid).append('<p class="error">there was an error with task ' + tid + '</p>');
		}
	});
}

function showTaskResult(res) {
	console.log(res);
	if (res.status && res.status._response && res.status._response.response && res.status._response.response.json_result &&
			res.status._response.response.json_result.cm_dict) {
		var qannotId = res.status._response.response.json_result.query_annot_uuid_list[0]['__UUID__'];
		//$('#task-' + res.taskId).append('<p>' + JSON.stringify(res.status._response.response.json_result) + '</p>');
		console.warn('json_result --> %o %o', qannotId, res.status._response.response.json_result['cm_dict'][qannotId]);
		var sorted = score_sort(res.status._response.response.json_result['cm_dict'][qannotId]);
		var max = sorted.length;
		if (max > 10) max = 10;
		displayAnnot(qannotId, -1, -1);
		for (var i = 0 ; i < max ; i++) {
			var d = sorted[i].split(/\s/);
			displayAnnot(res.taskId, d[1], i, d[0]);
		}

	} else {
		$('#task-' + res.taskId).append('<p class="error">there was an error parsing results for task ' + res.taskId + '</p>');
	}
}


function displayAnnot(taskId, annId, num, score) {
	$('#task-' + taskId).append('<div>(' + num + ')<b>' + annId + ':</b> ' + score + '</p>');
}

function score_sort(cm_dict, topn) {
console.warn(cm_dict);
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


function approvalButtons(qann, manns) {
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


function approvalButtonClick(encID, indivID) {
	console.info('approvalButtonClick(%s, %s)', encID, indivID);
	jQuery('#approval-buttons').html('<i>sending request...</i>');
	jQuery.ajax({
		url: 'matchResults.jsp?number=' + encID + '&individualID=' + indivID,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
			if (d.success) {
				window.location.href = 'encounter.jsp?number=' + encID;
			} else {
				console.warn(d);
				jQuery('#approval-buttons').html('error');
				alert('Error updating encounter: ' + d.error);
			}
		},
		error: function(x,y,z) {
			console.warn('%o %o %o', x, y, z);
			jQuery('#approval-buttons').html('error');
			alert('Error updating encounter');
		}
	});
	return true;
}


</script>
