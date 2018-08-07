<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.json.JSONObject,
org.ecocean.media.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,
org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>

<%

String context = ServletUtilities.getContext(request);

//this is a quick hack to produce a useful set of info about an Annotation (as json) ... poor mans api?  :(
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


<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">
</div>

<jsp:include page="footer.jsp" flush="true"/>



<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>
<script src="javascript/core.js"></script>
<script src="javascript/classes/Base.js"></script>
<script src="javascript/ia.js"></script>
<script src="javascript/ia.IBEIS.js"></script>

<script src="javascript/tablesorter/jquery.tablesorter.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
<link rel="stylesheet" href="css/pageableTable.css" />
<link rel="stylesheet" href="css/ia.css" type="text/css" />
<script src="javascript/tsrt.js"></script>
<script src="javascript/flukeScanEnd.js"></script>

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
		tryTaskId(taskIds[i]);
	}
}


function parseTaskIds() {
	var a = window.location.search.substring(1).split('&');
	for (var i = 0 ; i < a.length ; i++) {
		if (a[i].indexOf('taskId=') == 0) taskIds.push(a[i].substring(7));
	}
}

function tryTaskId(tid) {
    IA.fetchTaskResponse(tid, function(x) {
        if ((x.status == 200) && x.responseJSON && x.responseJSON.success && x.responseJSON.task) {
            processTask(x.responseJSON.task);
        } else {
            alert('Error fetching task id=' + tid);
            console.error('tryTaskId(%s) failed: %o', tid, x);
        }
    });
return;///////

}


function processTask(task) {
    IA.processTask(task, function(t, res) {
        $('.maincontent').append(res);
        getTaskResult(t);
    });
}


function getTaskResult(task) {
	var mostRecent = false;
	var gotResult = false;
console.warn('------------------- getTaskResult(%s)', task.id);
	$.ajax({
		url: 'iaLogs.jsp?taskId=' + task.id,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
console.info('>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> got %o on task.id=%s', d, task.id);
                    $('#task-debug-' + task.id).append('<b>iaLogs returned:</b>\n\n' + JSON.stringify(d, null, 4));
			for (var i = 0 ; i < d.length ; i++) {
				//console.log('d[i].status._action --> %o', d[i].status._action);
				if (d[i].status && d[i].status._action == 'getJobResult') {
					showTaskResult(d[i]);
					i = d.length;
					gotResult = true;
				} else {
					if (!mostRecent && d[i].status && d[i].status._action) mostRecent = d[i].status._action;
				}
			}
			if (!gotResult) $('#task-' + task.id).append('<p title="' + (mostRecent? mostRecent : '[unknown status]') + '" class="waiting throbbing">waiting for results</p>');
		},
		error: function(a,b,c) {
console.info('!!>> got %o', d);
			console.error(a, b, c);
			$('#task-' + task.id).append('<p class="error">there was an error with task ' + task.id + '</p>');
		}
	});
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

		$('#task-' + res.taskId + ' .task-title-id').append(' (' + (isEdgeMatching ? 'edge matching' : 'pattern matching') + ')');
		displayAnnot(res.taskId, qannotId, -1, -1);

		var sorted = score_sort(res.status._response.response.json_result['cm_dict'][qannotId]);
		if (!sorted || (sorted.length < 1)) {
			//$('#task-' + res.taskId + ' .waiting').remove();  //shouldnt be here (cuz got result)
			$('#task-' + res.taskId + ' .task-summary').append('<p class="xerror">results list was empty.</p>');
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
		url: 'iaResults.jsp?annotId=' + annId,  //hacktacular!
		type: 'GET',
		dataType: 'json',
		complete: function(d) { displayAnnotDetails(taskId, d, (num < 0)); }
	});
}

function displayAnnotDetails(taskId, res, isQueryAnnot) {
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
			$('#task-' + taskId + ' .annot-' + res.responseJSON.annId).append('<img src="images/no_images.jpg" style="padding: 10%" />');
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
			var h = '';
			if (encId) {
				h += '<a class="enc-link" target="_new" href="encounter.jsp?number=' + encId + '" title="encounter ' + encId + '">enc ' + encId + '</a>';
				$('#task-' + taskId + ' .annot-summary-' + res.responseJSON.annId).append('<a class="enc-link" target="_new" href="encounter.jsp?number=' + encId + '" title="encounter ' + encId + '">enc ' + encId + '</a>');
			}
			if (indivId) {
				h += '<a class="indiv-link" target="_new" href="individuals.jsp?number=' + indivId + '">' + indivId + '</a>';
				$('#task-' + taskId + ' .annot-summary-' + res.responseJSON.annId).append('<a class="indiv-link" target="_new" href="individuals.jsp?number=' + indivId + '">' + indivId + '</a>');
			}
			if (isQueryAnnot && h) $('#task-' + taskId + ' .task-title').append(h);
		}
	}
	if (imgInfo) $('#task-' + taskId + ' .annot-' + res.responseJSON.annId).append('<div class="img-info">' + imgInfo + '</div>');
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
	jQuery('#image-compare').append('<img style="height: 11px; width: 50%; margin: 40px 25%;" src="images/image-processing.gif" />');
	checkForResults();
}

function checkForResults() {
	jQuery.ajax({
		url: 'ia?getJobResultFromTaskID=' + taskId,
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
		jQuery('#image-compare').html('<img style="width: 225px; margin: 20px 30%;" src="images/image-not-found.jpg" />');
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
