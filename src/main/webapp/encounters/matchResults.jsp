<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");



  session.setMaxInactiveInterval(6000);
  String taskId = request.getParameter("taskId");
String num="xxx";
	//String encSubdir = Encounter.subdir(num);
  //Shepherd myShepherd = new Shepherd(context);
  //if (request.getParameter("writeThis") == null) {
  //  myShepherd = (Shepherd) session.getAttribute(request.getParameter("number"));
  //}
  //Shepherd altShepherd = new Shepherd(context);
  String sessionId = session.getId();
  boolean xmlOK = false;
  SAXReader xmlReader = new SAXReader();
  File file = new File("foo");
  String scanDate = "";
  String C = "";
  String R = "";
  String epsilon = "";
  String Sizelim = "";
  String maxTriangleRotation = "";
  String side2 = "";
%>

 <link href="../css/pageableTable.css" rel="stylesheet" type="text/css"/>
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />


<style type="text/css">

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

<script>
var taskId = '<%=taskId%>';
</script>



<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">




<h1>Matching Results <a href="<%=CommonConfiguration.getWikiLocation(context)%>scan_results"
  target="_blank"><img src="../images/information_icon_svg.gif"
                       alt="Help" border="0" align="absmiddle">
   </a>
</h1>


<%
  if (xmlOK) {%>
<p><img src="../images/Crystal_Clear_action_flag.png" width="28px" height="28px" hspace="2" vspace="2" align="absmiddle">&nbsp;<strong>Saved
  scan data may be old and invalid. Check the date below and run a fresh
  scan for the latest results.</strong></p>

<p><em>Date of scan: <%=scanDate%>
</em></p>
<%}%>


<p>


  


<div id="result-images"></div>


<div id="results">Waiting for results....</div>



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
function init2() {   //called from wildbook.init() when finished
	checkForResults();
    	$('#result-images').append('<div class="result-image-wrapper" id="image-main" />');
    	$('#result-images').append('<div class="result-image-wrapper" id="image-compare" />');
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
		$('#results').html('waiting for results, countdown=' + countdown);
		countdown--;
		if (countdown < 0) {
			$('#results').html('gave up waiting for results, sorry.  reload to wait longer.');
			return;
		}
		setTimeout(function() { checkForResults(); }, 3000);
		return;
	}
	if (res.queryAnnotation.encounter && res.queryAnnotation.mediaAsset) {
		addImage(fakeEncounter(res.queryAnnotation.encounter, res.queryAnnotation.mediaAsset),
			 jQuery('#image-main'));
	}
	if (!res.matchAnnotations || (res.matchAnnotations.length < 1)) {
		$('#results').html('No matches found.');
		return;
	}
	if (res.matchAnnotations.length == 1) {
		$('#results').html('One match found (<a target="_new" href="' +
			res.matchAnnotations[0].encounter.catalogNumber +
			'">' + res.matchAnnotations[0].encounter.catalogNumber +
			'</a>) - score ' + res.matchAnnotations[0].score);
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
		h += '<li data-i="' + i + '"><a target="_new" href="' +
			res.matchAnnotations[i].encounter.catalogNumber + '">' +
			res.matchAnnotations[i].encounter.catalogNumber + '</a> (' +
			res.matchAnnotations[i].encounter.individualID + '), score = ' +
			res.matchAnnotations[i].score + '</li>';
	}
	h += '</ul>';
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


</script>



