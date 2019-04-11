<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.json.JSONObject, org.json.JSONArray,
org.ecocean.media.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,
org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>

<%

String context = ServletUtilities.getContext(request);
org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evictAll();

//this is a quick hack to produce a useful set of info about an Annotation (as json) ... poor mans api?  :(
if (request.getParameter("acmId") != null) {
	String acmId = request.getParameter("acmId");
	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction("matchResults.jsp1");
	myShepherd.beginDBTransaction();
       	ArrayList<Annotation> anns = null;
	JSONObject rtn = new JSONObject("{\"success\": false}");
	//Encounter enc = null;
	try {
        	anns = myShepherd.getAnnotationsWithACMId(acmId);
	} catch (Exception ex) {}
	if ((anns == null) || (anns.size() < 1)) {
		rtn.put("error", "unknown error");
	} else {
            JSONArray janns = new JSONArray();
            for (Annotation ann : anns) {
		if (ann.getMatchAgainst()==true) {
                    JSONObject jann = new JSONObject();
                    jann.put("id", ann.getId());
                    jann.put("acmId", ann.getAcmId());
		    MediaAsset ma = ann.getMediaAsset();
		    if (ma != null) {
			jann.put("asset", Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject())));
		    }
                    janns.put(jann);
                }
            }
	    rtn.put("success", true);
            rtn.put("annotations", janns);
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
		if (request.getParameter("individualID")!=null&&!"".equals(request.getParameter("individualID").trim())) {
			try {
				MarkedIndividual newIndiv = new MarkedIndividual(request.getParameter("individualID"), enc);
				myShepherd.storeNewMarkedIndividual(newIndiv);
				enc.setIndividualID(newIndiv.getIndividualID());
				enc2.setIndividualID(newIndiv.getIndividualID());
				newIndiv.addEncounter(enc2, context);
				res.put("success", true);
			} catch (Exception e) {
				e.printStackTrace();
				res.put("error", "Please enter a different Individual ID.");
			}
		} else {
			res.put("error", "Please enter a new Individual ID.");
		}
		//indiv.addComment(????)
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


<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">
</div>

<jsp:include page="footer.jsp" flush="true"/>



<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>

<link rel="stylesheet" href="css/ia.css" type="text/css" />



<script>
var serverTimestamp = <%= System.currentTimeMillis() %>;
var pageStartTimestamp = new Date().getTime();
var taskIds = [];
var tasks = {};
var jobIdMap = {};
var timers = {};
var matchInstructions = 'Select <b>correct match</b> from results below by <i>hovering</i> over result and checking the <i>checkbox</i>.';
function init2() {   //called from wildbook.init() when finished
	$('.nav-bar-wrapper').append('<div id="encounter-info"><div class="enc-title" /></div>');
	parseTaskIds();
	for (var i = 0 ; i < taskIds.length ; i++) {
		var tid = taskIds[i];
		tryTaskId(tid);
	}
	// If we don't have any ID task elements, it's reasonable to assume we are waiting for something.
	// If we don't have anything but null task types after a while, lets just reload the page and get updated info. 
	// We get to this condition when the page loads too fast and you have only __NULL__ type tasks, 
	// and no children to traverse.
	$('.maincontent').html("<div id=\"initial-waiter\" class=\"waiting throbbing\"><p>processing request</p></div>");
	var reloadTimeout = setTimeout(function(){
		var onlyNullTaskType = true;
		for (var i = 0 ; i < taskIds.length ; i++) {
			var processedTask = tasks[tid];
			console.log("Processed Task: "+JSON.stringify(processedTask));
			var type = wildbook.IA.getPluginType(processedTask);
			console.log("TYPE : "+type);
			if (type!="__NULL__"||processedTask.children) {
				onlyNullTaskType = false;
				$('#initial-waiter').remove();
			}
		}
		console.log("-- >> What are the current tasks? : "+JSON.stringify(tasks));
		if (onlyNullTaskType==true) {
			console.log("RELOADING!");
			clearTimeout(reloadTimeout);
			location.reload(true);
		} else {
			clearTimeout(reloadTimeout);
			console.log("NOT RELOADING!!!!!");
		}
	},4000);
}
$(document).ready(function() { wildbook.init(function() { init2(); }); });
function parseTaskIds() {
	var a = window.location.search.substring(1).split('&');
	for (var i = 0 ; i < a.length ; i++) {
		if (a[i].indexOf('taskId=') == 0) taskIds.push(a[i].substring(7));
	}
}
function tryTaskId(tid) {
    wildbook.IA.fetchTaskResponse(tid, function(x) {
        if ((x.status == 200) && x.responseJSON && x.responseJSON.success && x.responseJSON.task) {
            processTask(x.responseJSON.task); //this will be json task (w/children)
	    console.log("TRY TASK RESPONSE!!!!                "+JSON.stringify(x.responseJSON.task));
        } else {
            alert('Error fetching task id=' + tid);
            console.error('tryTaskId(%s) failed: %o', tid, x);
        }
    });
}
function getCachedTask(tid) {
    return tasks[tid];
}
function cacheTaskAndChildren(task) {
    if (!task || !task.id || tasks[task.id]) return;
    tasks[task.id] = task;
    if (!wildbook.IA.hasChildren(task)) return;
    for (var i = 0 ; i < task.children.length ; i++) {
        cacheTaskAndChildren(task.children[i]);
    }
}

function processTask(task) {
    //first we populate tasks hash (for use later via getCachedTask()
    cacheTaskAndChildren(task);

    //now we get the DOM element
    //  note: this recurses, so our "one" element should have nested children element for child task(s)
    wildbook.IA.getDomResult(task, function(t, res) {
        $('.maincontent').append(res);
        grabTaskResultsAll(task);
    });
}



//calls grabTaskResult() on all appropriate nodes in tree
//  note there is no callback -- that is because this ultimately is expecting to put contents in
//  appropriate divs (e.g. id="task-XXXX")
var alreadyGrabbed = {};
function grabTaskResultsAll(task) {
console.info("grabTaskResultsAll %s", task.id);
    if (!task || !task.id || alreadyGrabbed[task.id]) return;
    if (wildbook.IA.needTaskResults(task)) {  //this magic decides "do we even have/need results for this task?"
console.info("grabTaskResultsAll %s TRYING.....", task.id);
        grabTaskResult(task.id);  //ajax, backgrounds
    }
    if (!wildbook.IA.hasChildren(task)) return;
    //now we recurse thru children....
    for (var i = 0 ; i < task.children.length ; i++) {
        grabTaskResultsAll(task.children[i]);
    }
}

function grabTaskResult(tid) {
        alreadyGrabbed[tid] = true;
	var mostRecent = false;
	var gotResult = false;
//console.warn('------------------- grabTaskResult(%s)', tid);
	$.ajax({
		url: 'iaLogs.jsp?taskId=' + tid,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
		    $('#wait-message-' + tid).remove();  //in case it already exists from previous
console.info('>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> got %o on task.id=%s', d, tid);
                    $('#task-debug-' + tid).append('<br /><b>iaLogs returned:</b>\n\n' + JSON.stringify(d, null, 4));
			for (var i = 0 ; i < d.length ; i++) {
				if (d[i].serviceJobId && (d[i].serviceJobId != '-1')) {
					if (!jobIdMap[tid]) jobIdMap[tid] = { timestamp: d[i].timestamp, jobId: d[i].serviceJobId, manualAttempts: 0 };
				}
				//console.log('d[i].status._action --> %o', d[i].status._action);
				if (d[i].status && d[i].status._action == 'getJobResult') {
					showTaskResult(d[i], tid);
					i = d.length;
					gotResult = true;
				} else {
					if (!mostRecent && d[i].status && d[i].status._action) mostRecent = d[i].status._action;
				}
			}
			if (!gotResult) {
				//console.log("Element length: "+$('#task-' + tid).length+" Element contents: "+document.getElementsByClassName("elementa")[0].innerHTML);
				if ($('#task-' + tid).length) {
					$('#initial-waiter').remove();
				}
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
                                        var latest = -1;
                                        if (d && d[0] && d[0].timestamp) latest = d[0].timestamp;
                                        var gaveUp = false;
                                        if (latest > 0) {
                                            var age = serverTimeDiff(latest);
console.info('age = %.2fmin', age / (60*1000));
                                            if (age > (12 * 60 * 1000)) {
                                                console.log('giving up on old task latest=%d -> %.2fmin', latest, age / (60*1000));
                                                if (timers && timers[tid] && timers[tid].timeout) clearTimeout(timers[tid].timeout);
                                                timers[tid] = { attempts: 9999999 };
						$('#wait-message-' + tid).html('error initiating IA job').removeClass('throbbing');;
                                                gaveUp = true;
                                            }
                                        }
                                        if (!gaveUp) {
					    if (!timers[tid]) timers[tid] = { attempts: 0 };
					    timers[tid].attempts++;
					    timers[tid].timeout = setTimeout(function() { console.info('ANOTHER %s!', tid); grabTaskResult(tid); }, 1700);
                                        }
				}
			} else {
				if (timers[tid] && timers[tid].timeout) clearTimeout(timers[tid].timeout);
				$('#initial-waiter').remove();
			}
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

//st is a server timestamp (i.e. using its clock, like data fields set on server)
//  this returns millisec different from (approx) server time... basically its age
function serverTimeDiff(st) {
    return approxServerTime() - st;
}

function manualCallback(tid) {
console.warn('manualCallback disabled currently (tid=%s)', tid); return;
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
		url: wildbookGlobals.baseUrl + '/ia?callback&jobid=' + m.jobId,
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
function showTaskResult(res, taskId) {
	console.log("RRRRRRRRRRRRRRRRRRRRRRRRRRESULT showTaskResult() %o on %s", res, res.taskId);
	if (res.status && res.status._response && res.status._response.response && res.status._response.response.json_result &&
			res.status._response.response.json_result.cm_dict) {
		var algoInfo = (res.status._response.response.json_result.query_config_dict &&
			res.status._response.response.json_result.query_config_dict.pipeline_root);
		var qannotId = res.status._response.response.json_result.query_annot_uuid_list[0]['__UUID__'];
		//$('#task-' + res.taskId).append('<p>' + JSON.stringify(res.status._response.response.json_result) + '</p>');
		console.warn('json_result --> %o %o', qannotId, res.status._response.response.json_result['cm_dict'][qannotId]);

		//$('#task-' + res.taskId + ' .task-title-id').append(' (' + (isEdgeMatching ? 'edge matching' : 'pattern matching') + ')');
                var algoDesc = '<span title="' + algoInfo + '">pattern</span>';
                if (algoInfo == 'CurvRankFluke') {
                    algoDesc = 'trailing edge (CurvRank)';
                } else if (algoInfo == 'OC_WDTW') {
                    algoDesc = 'trailing edge (OC/WDTW)';
                }
console.log('algoDesc %o %s %s', res.status._response.response.json_result.query_config_dict, algoInfo, algoDesc);
		var h = 'Matches based on <b>' + algoDesc + '</b>';
		if (res.timestamp) {
			var d = new Date(res.timestamp);
			h += '<span style="color: #FFF; margin: 0 11px; font-size: 0.7em;">' + d.toLocaleString() + '</span>';
		}

		h += '<span title="taskId=' + taskId + ' : qannotId=' + qannotId + '" style="margin-left: 30px; font-size: 0.8em; color: #777;">Hover mouse over listings below to <b>compare results</b> to target. Links to <b>encounters</b> and <b>individuals</b> given next to match score.</span>';
		$('#task-' + res.taskId + ' .task-title-id').html(h);
		displayAnnot(res.taskId, qannotId, -1, -1, -1);

		var sorted = score_sort(res.status._response.response.json_result['cm_dict'][qannotId]);
		if (!sorted || (sorted.length < 1)) {
			//$('#task-' + res.taskId + ' .waiting').remove();  //shouldnt be here (cuz got result)
			//$('#task-' + res.taskId + ' .task-summary').append('<p class="xerror">results list was empty.</p>');
			$('#task-' + res.taskId + ' .task-summary').append('<p class="xerror">Image Analysis has returned and no match was found.</p>');
			return;
		}
		var max = sorted.length;
		if (max > RESMAX) max = RESMAX;
		// ----- BEGIN Hotspotter IA Illustration: here we construct the illustration link URLs for each dannot -----
		// these URLs are passed-along and rendered in html by displayAnnotDetails
		var resJSON = res.status._response.response.json_result['cm_dict'][qannotId];
		// names conforming to IA args
		var extern_reference = resJSON.dannot_extern_reference;
		var query_annot_uuid = qannotId;
		var version = "heatmask";

		for (var i = 0 ; i < max ; i++) {
			var d = sorted[i].split(/\s/);
			var acmId = d[0];
			var database_annot_uuid = d[1];

			var illustUrl = "api/query/graph/match/thumb/?extern_reference="+extern_reference;
			illustUrl += "&query_annot_uuid="+query_annot_uuid;
			illustUrl += "&database_annot_uuid="+database_annot_uuid;
			illustUrl += "&version="+version;
			console.log("ILLUSTRATION "+i+" "+illustUrl);

			displayAnnot(res.taskId, d[1], i, d[0] / 1000, illustUrl);
			// ----- END Hotspotter IA Illustration-----
		}
		$('.annot-summary').on('mouseover', function(ev) { annotClick(ev); });
		$('#task-' + res.taskId + ' .annot-wrapper-dict:first').show();

	} else {
		$('#task-' + res.taskId).append('<p class="error">there was an error parsing results for task ' + res.taskId + '</p>');
	}
}

// Fix the acmId ---> annotID situation here. 

function displayAnnot(taskId, acmId, num, score, illustrationUrl) {
console.info('%d ===> %s', num, acmId);
	var h = '<div data-acmid="' + acmId + '" class="annot-summary annot-summary-' + acmId + '">';
	h += '<div class="annot-info"><span class="annot-info-num">' + (num + 1) + '</span> <b>' + score.toString().substring(0,6) + '</b></div></div>';
	var perCol = Math.ceil(RESMAX / 3);
	if (num >= 0) $('#task-' + taskId + ' .task-summary .col' + Math.floor(num / perCol)).append(h);


	//now the image guts
	h = '<div title="acmId=' + acmId + '" class="annot-wrapper annot-wrapper-' + ((num < 0) ? 'query' : 'dict') + ' annot-' + acmId + '">';
	//h += '<div class="annot-info">' + (num + 1) + ': <b>' + score + '</b></div></div>';
	$('#task-' + taskId).append(h);
	$.ajax({
		url: 'iaResults.jsp?acmId=' + acmId,  //hacktacular!
		type: 'GET',
		dataType: 'json',
		complete: function(d) { displayAnnotDetails(taskId, d, num, illustrationUrl); }
	});
}

function displayAnnotDetails(taskId, res, num, illustrationUrl) {
	var isQueryAnnot = (num < 0);
	if (!res || !res.responseJSON || !res.responseJSON.success || res.responseJSON.error || !res.responseJSON.annotations || !tasks[taskId] || !tasks[taskId].annotationIds) {
		console.warn('error on (task %s) res = %o', taskId, res);
		return;
	}
        /*
            we may have gotten more than one annot back from the acmId, so we have to account for them all.  currently we handle
            this as follows:
            (a) if it is the query annot, we *should* be able to find the id of the annot by looking at task.annotationIds.
            (b) if we cannot, or if target/dict annots, then we collect data about *all* possibly annots and show that
        */

	//var encId = false;
	//var indivId = false;
	var imgInfo = '';
        var mainAnnId = false; //this is basically to set as data('annid') on our div (cuz we need one?)
        var mainAsset = false;
        var otherAnnots = [];
        var h = 'Matching results';
        var acmId;

        for (var i = 0 ; i < res.responseJSON.annotations.length ; i++) {
            acmId = res.responseJSON.annotations[i].acmId;  //should be same for all, so lets just set it
            console.info('[%d/%d] annot id=%s, acmId=%s', i, res.responseJSON.annotations.length, res.responseJSON.annotations[i].id, res.responseJSON.annotations[i].acmId);
            if (tasks[taskId].annotationIds.indexOf(res.responseJSON.annotations[i].id) >= 0) {  //got it (usually query annot)
                console.info(' -- looks like we got a hit on %s', res.responseJSON.annotations[i].id);
                mainAnnId = res.responseJSON.annotations[i].id;
            }
            //we "should" only need the first asset we find -- as they "should" all be identical!
            if (!res.responseJSON.annotations[i].asset) continue;  //no asset, meh continue
            if (mainAsset) {
                otherAnnots.push(res.responseJSON.annotations[i]);
            } else {
                mainAsset = res.responseJSON.annotations[i].asset;
		$('#initial-waiter').remove();
            }
        }
        if (mainAnnId) $('#task-' + taskId + ' .annot-summary-' + acmId).data('annid', mainAnnId);  //TODO what if this fails?
        if (mainAsset) {
console.info('mainAsset -> %o', mainAsset);
console.info('illustrationUrl '+illustrationUrl);
            if (mainAsset.url) {
                $('#task-' + taskId + ' .annot-' + acmId).append('<img src="' + mainAsset.url + '" />');
            } else {
                $('#task-' + taskId + ' .annot-' + acmId).append('<img src="images/no_images.jpg" style="padding: 10%" />');
            }
            if (mainAsset.dateTime) {
                imgInfo += ' <b>' + mainAsset.dateTime.substring(0,16) + '</b> ';
            }
            if (mainAsset.filename) {
                var fn = mainAsset.filename;
                var j = fn.lastIndexOf('/');
                if (j > -1) fn = fn.substring(j + 1);
                imgInfo += ' ' + fn + ' ';
            }
            var ft = findMyFeature(acmId, mainAsset);
            if (ft) {
                var encId = ft.encounterId;
                var indivId = ft.individualId;
                if (encId) {
                    h += ' for <a style="margin-top: -6px;" class="enc-link" target="_new" href="encounters/encounter.jsp?number=' + encId + '" title="open encounter ' + encId + '">Encounter ' + encId.substring(0,6) + '</a>';
                    $('#task-' + taskId + ' .annot-summary-' + acmId).append('<a class="enc-link" target="_new" href="encounters/encounter.jsp?number=' + encId + '" title="encounter ' + encId + '">enc ' + encId + '</a>');
                    
		    if (!indivId) {
				$('#task-' + taskId + ' .annot-summary-' + acmId).append('<span class="indiv-link-target" id="encnum'+encId+'"></span>');			
		    }

                }
                if (indivId) {
                    h += ' of <a class="indiv-link" title="open individual page" target="_new" href="individuals.jsp?number=' + indivId + '">' + indivId + '</a>';
                    $('#task-' + taskId + ' .annot-summary-' + acmId).append('<a class="indiv-link" target="_new" href="individuals.jsp?number=' + indivId + '">' + indivId + '</a>');
                }

                if (encId || indivId) {
                    $('#task-' + taskId + ' .annot-summary-' + acmId).append('<input title="use this encounter" type="checkbox" class="annot-action-checkbox-inactive" id="annot-action-checkbox-' + mainAnnId +'" data-encid="' + (encId || '') + '" data-individ="' + (indivId || '') + '" onClick="return annotCheckbox(this);" />');
                }
                h += '<div id="enc-action">' + matchInstructions + '</div>';
                if (isQueryAnnot) {
                    if (h) $('#encounter-info .enc-title').html(h);
                    if (imgInfo) imgInfo = '<span class="img-info-type">TARGET</span> ' + imgInfo;
                    var qdata = {
                        annotId: mainAnnId,
                        encId: encId,
                        indivId: indivId
                    }
console.info('qdata[%s] = %o', taskId, qdata);
                        $('#task-' + taskId).data(qdata);
                } else {
                    if (imgInfo) imgInfo = '<span class="img-info-type">#' + (num+1) + '</span> ' + imgInfo;
                }
            }  //end if (ft) ....
            // Illustration
            if (illustrationUrl) {
            	var selector = '#task-' + taskId + ' .annot-summary-' + acmId;
            	// TODO: generify
            	var iaBase = wildbookGlobals.iaStatus.map.iaURL;
            	illustrationUrl = iaBase+illustrationUrl
            	var illustrationHtml = '<span class="illustrationLink" style="float:right;"><a href="'+illustrationUrl+'" target="_blank">inspect match</a></span>';
            	console.log("trying to attach illustrationHtml "+illustrationHtml+" with selector "+selector);
            	$(selector).append(illustrationHtml);
            }

        }  //end if (mainAsset)

    if (otherAnnots.length > 0) {
        imgInfo += '<div><i>Alternate references:</i><ul>';
        for (var i = 0 ; i < otherAnnots.length ; i++) {
            imgInfo += '<li title="Annot ' + otherAnnots[i].id + '"><b>Annot ' + otherAnnots[i].id.substring(0,12) + '</b>';
            var ft = findMyFeature(acmId, otherAnnots[i].asset);  //TODO is acmId correct here???
            if (ft) {
                var encId = ft.encounterId;
                var indivId = ft.individualId;
                if (encId) imgInfo += ' <a xstyle="margin-top: -6px;" class="enc-link" target="_new" href="encounters/encounter.jsp?number=' + encId + '" title="open encounter ' + encId + '">Encounter ' + encId.substring(0,6) + '</a>';
                if (indivId) imgInfo += ' <a class="indiv-link" title="open individual page" target="_new" href="individuals.jsp?number=' + indivId + '">' + indivId + '</a>';
            }
            imgInfo += '</li>';
        }
        imgInfo += '</ul></div>';
    }

    if (imgInfo) $('#task-' + taskId + ' .annot-' + acmId).append('<div class="img-info">' + imgInfo + '</div>');
}


function annotCheckbox(el) {
	var jel = $(el);
	var taskId = jel.closest('.task-content').attr('id').substring(5);
        var task = getCachedTask(taskId);
        var queryAnnotation = jel.closest('.task-content').data();
console.info('taskId %s => %o .... queryAnnotation => %o', taskId, task, queryAnnotation);
	annotCheckboxReset();
        if (!taskId || !task) return;
	if (!el.checked) return;
	jel.removeClass('annot-action-checkbox-inactive').addClass('annot-action-checkbox-active');
	jel.parent().addClass('annot-summary-checked');

	var h;
	if (!queryAnnotation.encId || !jel.data('encid')) {
		h = '<i>Insufficient encounter data for any actions</i>';
	} else if (jel.data('individ')==queryAnnotation.indivId) {
		h = 'The target and candidate are already assigned to the <b>same individual ID</b>. No further action is needed to confirm this match.'
	} else if (jel.data('individ') && queryAnnotation.indivId) {
		h = 'The two encounters have <b>different individuals</b> already assigned and must be handled manually.';
	} else if (jel.data('individ')) {
		h = '<b>Confirm</b> action: &nbsp; <input onClick="approvalButtonClick(\'' + queryAnnotation.encId + '\', \'' + jel.data('individ') + '\');" type="button" value="Set to individual ' + jel.data('individ') + '" />';
	} else if (queryAnnotation.indivId) {
		h = '<b>Confirm</b> action: &nbsp; <input onClick="approvalButtonClick(\'' + jel.data('encid') + '\', \'' + queryAnnotation.indivId + '\');" type="button" value="Use individual ' + jel.data('individ') + ' for unnamed match below" />';
	} else {
                //disable onChange for now -- as autocomplete will trigger!
		h = '<input class="needs-autocomplete" xonChange="approveNewIndividual(this);" size="20" placeholder="Type new or existing name" ';
		h += ' data-query-enc-id="' + queryAnnotation.encId + '" ';
		h += ' data-match-enc-id="' + jel.data('encid') + '" ';
		h += ' /> <input type="button" value="Set individual on both encounters" onClick="approveNewIndividual($(this.parentElement).find(\'.needs-autocomplete\')[0])" />'
	}
	$('#enc-action').html(h);
        setIndivAutocomplete($('#enc-action .needs-autocomplete'));
	return true;
}

function setIndivAutocomplete(el) {
    if (!el || !el.length) return;
    var args = {
        resMap: function(data) {
            var res = $.map(data, function(item) {
                if (item.type != 'individual') return null;
                var label = item.label;
                if (item.species) label += '   ( ' + item.species + ' )';
                return { label: label, type: item.type, value: item.value };
            });
            return res;
        }
    };
    wildbook.makeAutocomplete(el[0], args);
}

function annotCheckboxReset() {
	$('.annot-action-checkbox-active').removeClass('annot-action-checkbox-active').addClass('annot-action-checkbox-inactive').prop('checked', false);
	$('.annot-summary-checked').removeClass('annot-summary-checked');
	$('#enc-action').html(matchInstructions);
}

function annotClick(ev) {
	//console.log(ev);
	var acmId = ev.currentTarget.getAttribute('data-acmid');
	var taskId = $(ev.currentTarget).closest('.task-content').attr('id').substring(5);
	//console.warn('%o | %o', taskId, acmId);
	$('#task-' + taskId + ' .annot-wrapper-dict').hide();
	$('#task-' + taskId + ' .annot-' + acmId).show();
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
		sorta.push(cm_dict.score_list[i] * 1000 + ' ' + cm_dict.dannot_uuid_list[i]['__UUID__']);
	}
	sorta.sort(function(a,b) { return parseFloat(a) - parseFloat(b); }).reverse();
	return sorta;
}

function findMyFeature(annotAcmId, asset) {
console.info('findMyFeature() wanting annotAcmId %s from features %o', annotAcmId, asset.features);
    if (!asset || !Array.isArray(asset.features) || (asset.features.length < 1)) return;
    for (var i = 0 ; i < asset.features.length ; i++) {
        if (asset.features[i].annotationAcmId == annotAcmId) return asset.features[i];
    }
    return;
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

		$('#results').html('One match found (<a target="_new" href="encounters/encounter.jsp?number=' +
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
		h += '<li data-i="' + i + '"><a target="_new" href="encounters/encounter.jsp?number=' +
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
	var url = 'iaResults.jsp?number=' + encID + '&individualID=' + indivID;
	if (encID2) url += '&enc2=' + encID2;
	jQuery.ajax({
		url: url,
		type: 'GET',
		dataType: 'json',
		success: function(d) {
console.warn(d);
			if (d.success) {
				jQuery(msgTarget).html('<i><b>Update successful</b></i>');
				var indivLink = ' <a class="indiv-link" title="open individual page" target="_new" href="individuals.jsp?number=' + indivID + '">' + indivID + '</a>';
				if (encID2) {
					$(".enc-title .indiv-link").remove();
					$(".enc-title #enc-action").remove();
					$(".enc-title").append('<span> of <a class="indiv-link" title="open individual page" target="_new" href="individuals.jsp?number=' + indivID + '">' + indivID + '</a></span>');
					$(".enc-title").append('<div id="enc-action"><i><b>  Update Successful</b></i></div>');
					$("#encnum"+encID2).append(indivLink);
				}
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

