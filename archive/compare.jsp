<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,
org.json.JSONArray,
org.json.JSONObject,
javax.jdo.*,
java.util.Vector,

org.ecocean.media.MediaAsset,
org.ecocean.media.MediaAssetFactory,
org.ecocean.*,java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory,org.apache.commons.lang3.StringEscapeUtils" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("compare.jsp");


  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


  //setup our Properties object to hold all properties
  //String langCode = "en";
  //String langCode=ServletUtilities.getLanguageCode(request);

	String res = request.getParameter("results");
	System.out.println("=============>\n" + res);
	if (res != null) {
		out.println("{\"success\": true}");
		return;
	}


/*
  session = request.getSession(true);
  session.putValue("logged", "true");
  if ((request.getParameter("reflect") != null)) {
    response.sendRedirect(request.getParameter("reflect"));
  }
  ;
*/

	myShepherd.beginDBTransaction();
	Vector all = myShepherd.getAllEncountersNoFilterAsVector();
	JSONArray jall = new JSONArray();
	for (Object obj : all) {
		Encounter enc = (Encounter)obj;
		if ((enc.getAnnotations() == null) || (enc.getAnnotations().size() < 1)) continue;
		MediaAsset ma = enc.getAnnotations().get(0).getMediaAsset();
		if (ma == null) continue;
		JSONObject j = new JSONObject();
		j.put("encId", enc.getCatalogNumber());
		j.put("individualId", enc.getIndividualID());
		j.put("asset", Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject())));
		jall.put(j);
	}
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

%>
<jsp:include page="header.jsp" flush="true"/>
<script src="tools/panzoom/jquery.panzoom.min.js"></script>
<script src="tools/jquery-mousewheel/jquery.mousewheel.min.js"></script>
<script src="javascript/panzoom.js"></script>

<script>
var refKeyword = 'ReferenceImage';
var deck = [];
var assetRefs = [];
var assetTests = [];
var quizResults = { completed: [] };
var startSize = 0;
var currentOffset = -1;
var results = [];

var assets = <%= jall.toString() %>;
$(document).ready(function() {
	$(document).on('keydown', function(ev) {
		if (ev.keyCode == 89) {  //y
			answerClick('yes');
		} else if (ev.keyCode == 78) {  //n
			answerClick('no');
		} else if (ev.keyCode == 83) {  //s
			answerClick('skip');
		}
	});

	buildDeck();
	startSize = deck.length;
	setupForm();
});

function answerClick(a) {
	$('#image-test').prop('src', '');
	$('#image-ref').prop('src', '');
	if (a == 'skip') {
		setupForm();
	} else {
		storeResult(a, currentOffset);
		if (deck.length > 0) {
			deck.splice(currentOffset, 1);
			setupForm();
		} else {
			//will never get here cuz setupFrom kills us
			updateStatus();
			console.log('finished %o', results);
		}
	}
	return true;
}

function storeResult(ans, i) {
	var r = assets[deck[i][0]];
	var t = assets[deck[i][1]];
	results.push({
		response: ans,
		t: new Date().getTime(),
		ref: {
			assetId: r.asset.id,
			encId: r.encId,
			indivId: r.individualId
		},
		test: {
			assetId: t.asset.id,
			encId: t.encId,
			indivId: t.individualId
		}
	});
}

function buildDeck() {
	for (var i = 0 ; i < assets.length ; i++) {
		if (hasKeyword(assets[i].asset, refKeyword)) {
			assetRefs.push(i);
		} else {
			assetTests.push(i);
		}
	}
	for (var r = 0 ; r < assetRefs.length ; r++) {
		for (var t = 0 ; t < assetTests.length ; t++) {
			deck.push([assetRefs[r], assetTests[t]]);
		}
	}
}

function setupForm() {
	if (deck.length <= 0) {
		$('.compare-image-wrapper').html('<h1 style="text-align: center; padding: 20px;">completed.... saving.... </h1>');
		$.ajax({
			url: 'compare.jsp',
			type: 'POST',
			data: 'results=' + JSON.stringify(results),
			success: function(d) {
				console.log(d);
				$('.compare-image-wrapper').html('<h1 style="text-align: center; padding: 20px;">results recorded.</h1>');
			},
			dataType: 'json'
		});
		return;
	}
	currentOffset = Math.floor(Math.random() * deck.length);
	$('#image-ref').prop('src', assets[deck[currentOffset][0]].asset.url);
	$('#image-test').prop('src', assets[deck[currentOffset][1]].asset.url);
	updateStatus();
}

function hasKeyword(asset, kw) {
	if (!asset || !asset.keywords || (asset.keywords.length < 1)) return false;
	for (var i = 0 ; i < asset.keywords.length ; i++) {
		if (asset.keywords[i].readableName == kw) return true;
	}
	return false;
}


function updateStatus() {
	if (deck.length < 1) {
		$('#deck-status').html("<i>complete</i>");
	} else {
		$('#deck-status').html("<b>" + deck.length + " remaining</b> (of " + startSize + ")");
	}
}

</script>

<style>
#compare-wrapper {
	position: absolute;
	width: 100%;
	left: 0;
	top: 175px;
}

.compare-image-wrapper {
	width: 100%;
}
.compare-image-div {
	min-height: 100px;
	position: relative;
	width: 44%;
	margin-right: 2%;
	margin-left: 2%;
	display: inline-block;
}
.compare-image {
	width: 100%;
	heigth: auto;
}
.compare-image-label {
	position: absolute;
	z-index: 100;
	left: 0;
	top: 0;
	border-radius: 4px;
	font-weight: bold;
	padding: 0px 8px;
	background-color: rgba(100,100,100,0.7);
	color: #FFF;
}

.click-mode {
	outline: 3px solid #BFB;
}


#deck-status {
	font-size: 0.8em;
	color: #888;
	text-align: center;
}

#match-question {
	text-align: center;
}

</style>

<div style="height: 700px;" class="container maincontent">
	<div id="compare-wrapper">
		<div class="compare-image-wrapper">
			<div class="compare-image-div" id="image-ref-div">
				<div class="compare-image-label">reference</div>
				<img id="image-ref" class="compare-image" />
			</div>
			<div class="compare-image-div" id="image-test-div">
				<div class="compare-image-label">test</div>
				<img id="image-test" class="compare-image" />
			</div>
		</div>
		<div class="compare-ui">
			<div style="text-align: center;"><b id="click-mode-shift-false" class="click-mode">CLICK</b> to zoom in,
				<b id="click-mode-shift-true">SHIFT-CLICK</b> to zoom out, <b>DRAG</b> to move/pan</div>

			<div id="match-question">
				Do these images represent the same cat?
				<input type="button" value="[y]es" onClick="return answerClick('yes');" />
				<input type="button" value="[n]o" onClick="return answerClick('no');" />
				<input type="button" value="[s]kip" onClick="return answerClick('skip');" />
			</div>
			<div id="deck-status"></div>
		</div>
	</div>
</div>

      <jsp:include page="footer.jsp" flush="true"/>

