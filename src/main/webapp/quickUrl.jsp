<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="java.util.ArrayList" %>
<%@ page import="org.ecocean.*,
org.ecocean.media.*,
org.json.JSONObject, org.json.JSONArray
" %>


<%


String urlIn = request.getParameter("url");

if (urlIn != null) {
	response.setHeader("Content-type", "text/plain");
	String[] urls = urlIn.split("[\\s,]+");

	String context="context0";
	Shepherd myShepherd = new Shepherd(context);
	myShepherd.beginDBTransaction();
	URLAssetStore as = ((URLAssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(URLAssetStore.class, 7), true)));

	JSONObject jout = new JSONObject();
	JSONArray jurls = new JSONArray();
	JSONArray mas = new JSONArray();
	for (int i = 0 ; i < urls.length ; i++) {
		jurls.put(urls[i]);
		JSONObject p = new JSONObject();
		p.put("url", urls[i]);
		MediaAsset ma = as.create(p);
		if (ma.cacheLocal()) {
			ma.updateMinimalMetadata();
			Annotation ann = new Annotation("Megaptera novaeangliae", ma);  //this should not(?) be used to match against as it is not an exemplar!
			myShepherd.getPM().makePersistent(ann);
			org.datanucleus.api.rest.orgjson.JSONObject aj = ann.sanitizeJson(request, true);
			mas.put(new JSONObject(aj.toString()));
		} else {
			mas.put(false);
		}
	}
	jout.put("urls", jurls);
	jout.put("annotations", mas);
	out.println(jout.toString());
	myShepherd.commitDBTransaction();
	return;
}

%>
<html>
<!--
///95c2365f-5e19-488e-ab63-ea7fddf28c67
///6b7ba32a-9ed2-4698-990d-15862078bad5
-->
<head><title>batch matching</title>
<style>
	.no-match {
		padding: 30px;
		color: #800;
		font-size: 2em;
		text-align: center;
	}
	body {
		font-family: arial;
	}

	.awrapper {
		width: 30%;
		position: relative;
		display: inline-block;
	}
	.awrapper img {
		width: 100%;
	}
	.ainfo {
		position: absolute;
		background-color: rgba(255,255,255,0.6);
		padding: 4px;
		font-size: 0.8em;
		bottom: 0;
		right: 0;
	}

	.mannot .ainfo {
		opacity: 0.4;
	}
	.mannot:hover .ainfo {
		opacity: 1.0;
	}

	#meta {
		position: fixed;
		z-index: 20;
		padding: 6px;
		font-size: 0.8em;
		border-radius: 5px;
		right: 20px;
		background-color: #BFF;
		top: 20px;
		display: none;
		opacity: 0.7;
		width: 180px;
	}
	#meta:hover {
		opacity: 1.0;
	}

	.meta {
		padding: 2px 4px;
		background-color: #CCC;
		margin: 2px;
		cursor: pointer;
		position: relative;
	}
	.meta-active {
		background-color: #444;
		color: #EEE;
	}

	.meta b {
		position: absolute;
		right: 4px;
	}

	#meta textarea {
		height: 200px;
	}

	#ids {
		width: 50%;
		height: 200px;
	}

	div.encounter {
		padding: 2px 5px;
		background-color: #DDD;
		border-radius: 3px;
		margin: 15px;
	}

	div.annots {
		padding: 4px;
		background-color: #BBB;
		margin: 3px;
	}

	.annot {
		padding: 4px;
		background-color: #FFF;
		font-size: 0.9em;
		margin: 4px;
		border-radius: 4px;
		position: relative;
	}
	.annot-img {
		height: 20px;
	}

	.annot.start-up {
		background-color: #47F;
	}
	.annot.waiting {
		background-image: url(images/image-processing.gif);
		background-size: 250px;
	}
	.annot.complete {
		background-color: #3F5;
	}

	.annot .date {
		font-size: 0.85em;
		margin-left: 15px;
		color: #888;
	}

	.ctdiv {
		position: absolute;
		right: 6px;
		top: 5px;
		font-size: 0.8em;
		color: #444;
	}

	.error {
		background-color: #F55;
	}

	.task {
		margin: 8px;
		background-color: #EEE;
		padding: 5px;
	}

	.task a {
		color: #000;
		font-weight: bold;
		text-decoration: none;
	}

	.match-thumb {
		position: relative;
		margin: 5px;
		display: inline-block;
	}
	.match-thumb img {
		border-radius: 7px;
		max-width: 125px;
		max-height: 200px;
	}

	.score {
		position: absolute;
		bottom: 0;
		left: 0;
		padding: 1px 4px;
		margin: 0;
		overflow: hidden;
		background-color: rgba(255,255,255,0.6);
	}
</style>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/2.2.2/jquery.min.js"></script>

<script>

var encs = {};

$(document).ready(function() {
	if (window.location.search == '?all') {
		$('#ids').val($('#all-the-ids').val());
	}
});

var taskId = false;

function start() {
	$('#controls').hide();
	//$('#meta').show();
	var ids = $('#ids').val().split(/[\s,]+/);
console.log(ids);
	$.ajax({
		url: 'quickUrl.jsp?url=' + ids.join(','),
		type: 'POST',
		dataType: 'json',
		success: function(d) {
			$('#controls').hide();
			$('#results').append('<div class="awrapper" id="qannot"><img src="' + ids[0] + '" /><div class="ainfo">' + ids[0] + '</div></div><div id="matching">matching... please wait...</div>');
			console.info('created annotations: %o', d);
			if (!d || !d.annotations || !d.annotations.length) return;
			for (var i = 0 ; i < d.annotations.length ; i++) {
				startIdentify(d.annotations[i].id,
					function(d) { taskId = d.taskID; watchTask(); },
					function(d) { console.error('error startIdentify'); } );
			}
		},
		error: function() {
			console.error('problem creating annotations');
		}
	});
}


function watchTask() {
	$.ajax({
		url: 'ia?getJobResultFromTaskID=' + taskId,
		type: 'GET',
		success: function(d) {
			if (d.status && d.status.success) return showResults(d);
			console.warn('did not get results, trying again %o', d);
			setTimeout(function() { watchTask(); }, 1500);
		},
		error: function() {
			console.error('could not get results for task');
			setTimeout(function() { watchTask(); }, 1500);
		},
		dataType: 'json'
	});
}


function showResults(d) {
	console.info('hey i got %o', d);
	$('#matching').remove();

	if (!d.matchAnnotations || !d.matchAnnotations.length) {
		$('#results').append('<div class="no-match awrapper">no match found :(</div>');
	} else {
		for (var i = 0 ; i < d.matchAnnotations.length ; i++) {
			var ikeys = ['date', 'locationID', 'verbatimLocality', 'individualID'];
			var inf = [];
			for (var j = 0 ; j < ikeys.length ; j++) {
				if (d.matchAnnotations[i].encounter[ikeys[j]])
					inf.push(ikeys[j] + ': <b>' + d.matchAnnotations[i].encounter[ikeys[j]] + '</b>');
			}
			$('#results').append('<div class="awrapper mannot"><img src="' + d.matchAnnotations[i].mediaAsset.url + '" /><div class="ainfo">' + inf.join('<br />') + '</div></div>');
		}
	}
}


function getResults() {
	for (var eid in encs) {
		if (!encs[eid] || !encs[eid].obj || !encs[eid].obj.annotations || (encs[eid].obj.annotations.length < 1)) continue;
		for (var i = 0 ; i < encs[eid].obj.annotations.length ; i++) {
			annMap[encs[eid].obj.annotations[i].id] = encs[eid].obj.annotations[i];
			encs[eid].obj.annotations[i]._matchTask = { start: new Date().getTime(), count: 0 };
			getTaskId(encs[eid].obj.annotations[i].id, function(aid, rtn) {
console.info('got rtn=%o for aid=%o', rtn, aid);
				if (!rtn.success) {
					$('#' + aid).removeClass('start-up').addClass('error').append((rtn.error || 'unknown error') + ' finding taskids');
				} else {
					if (!rtn.taskIds || (rtn.taskIds.length < 1)) {
						$('#' + aid).removeClass('start-up').addClass('complete').append(' no matching tasks found');
					} else {
						var tid = rtn.taskIds[rtn.taskIds.length - 1];
						annMap[aid]._matchTask.taskId = tid;
						$('#' + aid).removeClass('start-up').addClass('waiting').append('<div class="task" id="' + tid + '">task <a target="_new" href="encounters/matchResults.jsp?taskId=' + tid + '">' + tid + '</a><div class="task-results"></div>');
						checkStatus(aid, 10);
					}
				}
			});
		}
	}
}

/*
			console.info('aid=%s success initiating task %s', d.aid, d.taskID);
						annMap[d.aid]._matchTask.taskId = d.taskID;
						$('#' + d.aid).removeClass('start-up').addClass('waiting').append('<div class="task" id="' + d.taskID + '"><a target="_new" href="encounters/matchResults.jsp?taskId=' + d.taskID + '">' + d.taskID + '</a><div class="task-results"></div>');
						checkStatus(d.aid);
						startMatches();
*/


function getTaskId(aid, callback) {
	$.ajax({
		url: 'ia',
		type: 'POST',
      		contentType: 'application/javascript',
		data: JSON.stringify({taskIds: aid}),
		error: function(x,y,z) { callback(aid, { success: false, error: "error: " + y }); },
		success: function(d) { callback(aid, d); },
		dataType: 'json'
	});
}

function startMatches() {
	if (!firstMatchRun && runningMatches) return;
	if (runningMatches >= maxRunningMatches) {
		setTimeout(function() { startMatches(); }, 2000);
		return;
	}

	var someLeft = false;
	for (var eid in encs) {
		if (!encs[eid].obj || !encs[eid].obj.annotations || (encs[eid].obj.annotations.length < 1)) continue;
		for (var i = 0 ; i < encs[eid].obj.annotations.length ; i++) {
			if (!encs[eid].obj.annotations[i]._matchTask) {
				if ((runningMatches < maxRunningMatches) && (firstMatchRun || !runningMatches)) {
					runningMatches++;
					encs[eid].obj.annotations[i]._matchTask = { start: new Date().getTime(), count: 0 };
					annMap[encs[eid].obj.annotations[i].id] = encs[eid].obj.annotations[i];
console.warn('running on %s', encs[eid].obj.annotations[i].id);
					$('#' + encs[eid].obj.annotations[i].id).addClass('start-up');
					startIdentify(encs[eid].obj.annotations[i].id,
					function(d) {
						console.info('aid=%s success initiating task %s', d.aid, d.taskID);
						annMap[d.aid]._matchTask.taskId = d.taskID;
						$('#' + d.aid).removeClass('start-up').addClass('waiting').append('<div class="task" id="' + d.taskID + '"><a target="_new" href="encounters/matchResults.jsp?taskId=' + d.taskID + '">' + d.taskID + '</a><div class="task-results"></div>');
						checkStatus(d.aid);
						startMatches();
					},
					function(err) {
console.log('error --------> %o', err);
						$('#' + err.aid).removeClass('start-up').addClass('error').append('<span> error initiating task: ' + err.error + '</span>');
						startMatches();
					});

				} else {
					someLeft = true;
				}
			}
		}
	}

	if (someLeft) {
console.info('trying once more...');
		startMatches();
	} else if (runningMatches < 1) {
console.warn('really!! done');
	} else {
console.info('not quite done?');
	}
}


function toggleDivStatus(aid, newStatus) {
	var encDiv = $('#' + aid).closest('.encounter');
	encDiv.removeClass('status-error');
	encDiv.removeClass('status-other');
	//encDiv.removeClass('status-ok');
	encDiv.removeClass('status-matches-found');
	encDiv.removeClass('status-no-matches');
	encDiv.removeClass('status-pending');
	encDiv.addClass(newStatus);
	updateMeta();
}

var fakecount = {};
function checkStatus(aid, maxTries) {
	toggleDivStatus(aid, 'status-pending');

	firstMatchRun = true;
	console.log('aid=%o => %o', aid, annMap[aid]);
//http://cascadia.wildbook.org/ia?getJobResultFromTaskID=e6a2323f-8cbe-4215-95d9-e218005ef2c1
//return;
	if (!aid || !annMap[aid] || !annMap[aid]._matchTask || !annMap[aid]._matchTask.taskId) {
		console.error('failed to find taskId aid=%o annMap[aid]=%o', aid, annMap[aid]);
		return;
	}
	if ($('#' + aid + ' .ctdiv').length) {
		$('#' + aid + ' .ctdiv').html(niceCount(annMap[aid]._matchTask));
	} else {
		$('#' + aid).append('<span class="ctdiv">' + niceCount(annMap[aid]._matchTask) + '</span>');
	}
	
	annMap[aid]._matchTask.count++;
	$.ajax({
		url: 'ia?getJobResultFromTaskID=' + annMap[aid]._matchTask.taskId,
		type: 'GET',
		success: function(d) {
			console.info('aid %s task %s returned %o', aid, annMap[aid]._matchTask.taskId, d);
			if (!d.status || !d.status.success) {
				console.warn('failed to find success %s (%d/%o)', annMap[aid]._matchTask.taskId, annMap[aid]._matchTask.count, maxTries);
				if (maxTries && (annMap[aid]._matchTask.count > maxTries)) {
					$('#' + aid).removeClass('waiting').addClass('error');
					$('#' + annMap[aid]._matchTask.taskId).append(' gave up finding results');
					toggleDivStatus(aid, 'status-error');
				} else {
					setTimeout(function() { checkStatus(aid, maxTries); }, 3000 + Math.random() * 1100);
				}
			} else { //////success
				gotResults(aid, d);
				console.info('done with aid=%s', aid);
				runningMatches--;
				startMatches();
			}
		},
		error: function(a,b) {
			console.warn('failed %s task %s: %o %o', aid, annMap[aid]._matchTask.taskId, a, b);
			setTimeout(function() { checkStatus(aid); }, 3000 + Math.random() * 1100);
		},
		dataType: 'json'
	});
}
/*
fakecount[tid] = ((fakecount[tid] == undefined) ? 0 : fakecount[tid] + 1);
console.log('fake check status on %s: %d [running %d]', tid, fakecount[tid], runningMatches);

var aid = tid;
if (fakecount[tid] > 3) $('#' + aid).removeClass('start-up').addClass('waiting');

	if (fakecount[tid] > 10) {
		firstMatchRun = true;
		$('#' + aid).removeClass('waiting').addClass('complete');
		console.info('done with aid=%s', aid);
		runningMatches--;
		startMatches();
	} else {
		setTimeout(function() { checkStatus(tid); }, 100 + Math.random() * 700);
	}
}
*/


function gotResults(aid, d) {
console.warn('d=%o',d);
	var tid = annMap[aid]._matchTask.taskId;
	$('#' + aid).removeClass('waiting').addClass('complete');
	if (d.timestamp) {
		var dt = new Date();
		dt.setTime(d.timestamp);
console.warn(dt);
		$('#' + tid + ' .task-results').before('<span class="date">' + dt.toLocaleDateString() + '</span>');
	}
	if (!d.matchAnnotations || (d.matchAnnotations.length < 1)) {
		$('#' + tid + ' .task-results').html('<i>no matches found</i>');
		toggleDivStatus(aid, 'status-no-matches');
	} else {
		toggleDivStatus(aid, 'status-matches-found');
		for (var i = 0 ; i < d.matchAnnotations.length ; i++) {
			var h = '<div class="match-thumb"><a href="encounters/encounter.jsp?number=';
			h += d.matchAnnotations[i].encounter.catalogNumber + '"><img src="';
			h += cleanUrl(d.matchAnnotations[i].mediaAsset.url) + '" /><span class="score">';
			h += (Math.floor(d.matchAnnotations[i].score * 10000) / 10000) + '</span></a></div>';
			$('#' + tid + ' .task-results').append(h);
		}
	}
}


var clickActive = 'ALL';
function updateMeta() {
	var s = [ 'error', 'other', 'matches-found', 'no-matches', 'pending' ];
	var h = '';
	for (var i = 0 ; i < s.length ; i++) {
		h += '<div onClick="metaClick(this);" class="meta" id="meta-' + s[i] + '">' + s[i] + ' <b>' + $('.status-' + s[i]).length + '</b></div>';
	}
	h += '<div onClick="metaClick(this);" class="meta" id="meta-ALL">show all</div>';
	$('#meta-buttons').html(h);
	$('#meta-' + clickActive).addClass('meta-active');
}


function metaClick(el) {
	var id = el.id.substr(5);
	$('.meta-active').removeClass('meta-active');
console.warn(id);
	var matches;
	$('#meta-' + id).addClass('meta-active');
	if (id == 'ALL') {
		matches = $('.encounter');
	} else {
		$('.encounter').hide();
		matches = $('.encounter.status-' + id);
	}
	clickActive = id;
	matches.show();
	var list = '';
	matches.each(function(i, el) {
		list += el.id + '\n';
	});
	$('#meta textarea').val(list);
}


function niceCount(d) {
	var ms = new Date().getTime() - d.start;
	var n;
	if (ms < 60000) {
		n = Math.floor(ms / 1000) + 's';
	} else if (ms < 60 * 60000) {
		n = Math.floor(ms / 60000) + ':';
		var s = Math.floor(ms % 60000 / 1000);
		if (s < 10) n += '0';
		n += s;
	} else {
		n = ms / (60 * 60 * 60000) + 'h';
	}
	return n + ' [' + d.count + ']';
}

function startIdentify(aid, success, failure) {
	if (!aid) return;
    	jQuery.ajax({
      		url: 'ia',
      		type: 'POST',
      		dataType: 'json',
      		contentType: 'application/javascript',
      		success: function(d) {
			d.aid = aid;
        		console.info('identify returned %o', d);
        		if (d.taskID) {
				success(d);
        		} else {
				if (!d.error) d.error = 'unknown error';
				failure(d);
        		}
      		},
      		error: function(x,y,z) {
        		console.warn('%o %o %o', x, y, z);
			failure({error: z, aid: aid});
      		},
      		data: JSON.stringify({
        		identify: { annotationIds: [ aid ] }
      		})
    	});
}


///95c2365f-5e19-488e-ab63-ea7fddf28c67
///6b7ba32a-9ed2-4698-990d-15862078bad5
function NOTstartIdentify(aid, success, failure) {
	success({
		aid: aid,
		taskID: '6b7ba32a-9ed2-4698-990d-15862078bad5'
	});
}


function cleanUrl(url) {
        return encodeURI(url).replace(new RegExp('#', 'g'), '%23');
}

</script>


</head>
<body>
<div id="meta">
	<div id="meta-buttons"></div>
</div>

<div id="controls">
<div>
<textarea id="ids" placeholder="enter encounter ids here">
</textarea>
</div>

enter url(s) above.  they should all be flukes from the same whale.
<input id="submit-button" type="submit" value="start" onClick="start()" />
</div>

<div id="results"></div>

</body>
</html>
