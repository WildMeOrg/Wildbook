<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.io.File, java.io.FileOutputStream, java.io.OutputStreamWriter, java.util.*, org.datanucleus.api.rest.orgjson.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager " %>


<%!

String dispToString(Integer i) {
	if (i==null) return "";
	return i.toString();
}

%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  //let's load encounterSearch.properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


  Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/searchResults.properties"));
  encprops=ShepherdProperties.getProperties("searchResults.properties", langCode, context);


  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("searchResults.jsp");



  int numResults = 0;


  //Vector rEncounters = new Vector();

  //myShepherd.beginDBTransaction();

  try{

  	//EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
 	 //rEncounters = queryResult.getResult();


//--let's estimate the number of results that might be unique

  Integer numUniqueEncounters = null;
  Integer numUnidentifiedEncounters = null;
  Integer numDuplicateEncounters = null;

%>


<style type="text/css">

#results-table thead tr {
	height: 4em;
}

.ia-ann-summary {
	margin: 0 2px;
}

.ia-success-match, .ia-success-miss, .ia-pending, .ia-error, .ia-unknown {
	padding: 0 3px;
	color: #FFF;
	font-weight: bold;
}

.ptcol-ia .ia-success-match {
	background-color: #1A0;
}
.ptcol-ia .ia-success-miss {
	background-color: #222;
}
.ptcol-ia .ia-pending {
	background-color: #42F;
}
.ptcol-ia .ia-error {
	background-color: #D20;
}
.ptcol-ia .ia-unknown {
	background-color: #888;
}


.ptcol-individualID {
	position: relative;
}
.ptcol-individualID a.pt-vm-button {
	position: absolute;
	display: none;
	left: 5px;
	top: 5px;
	border: solid 1px black;
	border-radius: 3px;
	background-color: #DDD;
	padding: 0 3px;
	color: black;
	text-decoration: none;
	cursor: pointer;
}

.ptcol-otherCatalogNumbers {
  width: 75px !important;
}

tr.clickable:hover td {
	background-color: #EFA !important;
}

tr:hover .ptcol-individualID span.unassigned {
	display:hidden;
}

tr:hover .ptcol-individualID a.pt-vm-button {
	display: inline-block;
}
a.pt-vm-button:hover {
	background-color: #FF5;
}

.ptcol-thumb {
	width: 75px !important;
}

td.tdw {
	position: relative;
}

td.tdw div {
	height: 16px;
	overflow-y: hidden;
}


td.tdw:hover div {
	position: absolute;
	z-index: 20;
	background-color: #EFA;
	outline: 3px solid #EFA;
	min-height: 16px;
	height: auto;
	overflow-y: auto;
}


  #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #000;
    background: #E6EEEE;
     
    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {

  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
  }


	.collab-private {
		background-color: #FDD;
	}

	.collab-private td {
		background-color: transparent !important;
	}

	.collab-private .collab-icon {
		position: absolute;
		left: -15px;
		z-index: -1;
		width: 13px;
		height: 13px;
		background: url(../images/lock-icon-tiny.png) no-repeat;
	}

</style>

<jsp:include page="../header.jsp" flush="true"/>

<script src="../javascript/tablesorter/jquery.tablesorter.js"></script>

<script src="../javascript/underscore-min.js"></script>
<script src="../javascript/backbone-min.js"></script>
<script src="../javascript/core.js"></script>
<script src="../javascript/classes/Base.js"></script>

<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

<link rel="stylesheet" href="../css/pageableTable.css" />
<script src="../javascript/tsrt.js"></script>



<div class="container maincontent">


      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>


<ul id="tabmenu">

  <li><a class="active"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a
    href="thumbnailSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("matchingImages")%>
  </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
  </a></li>
  <li><a
    href="../xcalendar/calendar2.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("resultsCalendar")%>
  </a></li>
        <li><a
     href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("analysis")%>
   </a></li>
      <li><a
     href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("export")%>
   </a></li>

</ul>


<p><%=encprops.getProperty("belowMatches")%></p>

<style>
.ptcol-maxYearsBetweenResightings {
	width: 100px;
}
.ptcol-numberLocations {
	width: 100px;
}

</style>

<script type="text/javascript">

	var needIAStatus = false;

/*

    <strong><%=encprops.getProperty("markedIndividual")%>
    <strong><%=encprops.getProperty("number")%>
    if (<%=CommonConfiguration.showProperty("showTaxonomy",context)%>) {
    	
	    <strong><%=encprops.getProperty("taxonomy")%>
	    <strong><%=encprops.getProperty("submitterName")%>
	    <strong><%=encprops.getProperty("date")%>
	    <strong><%=encprops.getProperty("location")%>
	    <strong><%=encprops.getProperty("locationID")%>
	    <strong><%=encprops.getProperty("occurrenceID")%>
*/


<%
	String encsJson = "false";
/*
	JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
	if (rEncounters instanceof Collection) {
		JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)rEncounters, jdopm.getExecutionContext());
		//JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)rEncounters, ((JDOPersistenceManager)pm).getExecutionContext());
		encsJson = jsonobj.toString();
	} else {
		JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(rEncounters, jdopm.getExecutionContext());
		encsJson = jsonobj.toString();
	}
*/

StringBuffer prettyPrint=new StringBuffer("");

Map<String,Object> paramMap = new HashMap<String, Object>();

String filter=EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);

%>



var searchResults = <%=encsJson%>;

var jdoql = '<%= filter.replaceAll("'", "\\\\'") %>';

var testColumns = {
	//thumb: { label: 'Thumb', val: _colThumb },
	individualID: { label: 'ID', val: _colIndLink },
	date: { label: 'Date', val: _colEncDate },
	verbatimLocality: { label: 'Location' },
	//locationID: { label: 'Location ID' },
	taxonomy: { label: 'Taxonomy', val: _colTaxonomy },
	submitterID: { label: 'Submitter' },
	creationDate: { label: 'Created', val: _colCreationDate },
	modified: { label: 'Edit Date', val: _colModified },
};







$(document).keydown(function(k) {
	if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
	if (k.which == 38) return tableDn();
	if (k.which == 40) return tableUp();
	if (k.which == 33) return nudge(-howMany);
	if (k.which == 34) return nudge(howMany);
});


var colDefn = [

	{
		key: 'individualID',
		label: 'ID',
		value: _colIndLink,
		//sortValue: function(o) { return o.individualID.toLowerCase(); },
	},
  {
    key: 'otherCatalogNumbers',
    label: '<%=encprops.getProperty("alternateID")%>'//'Alternate ID',
  },
	{
		key: 'date',
		label: '<%=encprops.getProperty("date")%>',
		value: _colEncDate,
		sortValue: _colEncDateSort,
		sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
	},
	{
		key: 'verbatimLocality',
		label: '<%=encprops.getProperty("location")%>',
	},
//	{
//		key: 'locationID',
// 		label: '<%=encprops.getProperty("locationID")%>',
//	},
	{
		key: 'taxonomy',
		label: '<%=encprops.getProperty("taxonomy")%>',
		value: _colTaxonomy,
	},
	{
		key: 'submitterID',
		label: '<%=encprops.getProperty("submitterName")%>',
		value: _submitterID,
	},
	{
		key: 'creationDate',
		label: 'Created',
		value: _colCreationDate,
		sortValue: _colCreationDateSort,
	},
	{
		key: 'modified',
		label: 'Edit Date',
		value: _colModified,
		sortValue: _colModifiedSort,
	}

];


var howMany = 10;
var start = 0;
var results = [];

var sortCol = -1;
var sortReverse = false;

var counts = {
	total: 0,
	ided: 0,
	unid: 0,
	dailydup: 0,
};

var sTable = false;


var iaResults;
function doTable() {
	iaResults = {};
	if (needIAStatus) {
		for (var i = 0 ; i < searchResults.length ; i++) {
			if (searchResults[i].get('individualID') && (searchResults[i].get('individualID') != 'Unassigned')) continue;
			var anns = searchResults[i].get('annotations');
			if (!anns || (anns.length < 1)) continue;
			searchResults[i].set('_iaResults', {});
			iaResults[i] = {};
			for (var a = 0 ; a < anns.length ; a++) {
				iaResults[i][anns[a].id] = [];
			}
		}
		colDefn.splice(1, 0, {
			key: 'ia',
			label: 'ID match',
			value: _colIA,
			sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); },
			sortValue: _colIASort
		});

		var allIds = [];
		for (var i in iaResults) {
			for (var annId in iaResults[i]) {
				allIds.push(annId);
			}
		}

		jQuery.ajax({
			url: '../ia',
			type: 'POST',
			contentType: 'application/javascript',
			dataType: 'json',
			data: JSON.stringify({ taskSummary: allIds }),
			success: function(d) { updateIAResults(d); },
			error: function(a,b,c) { console.warn('%o %o %o', a, b, c); alert('error finding IA results'); },
		});
	}
/*
	for (var i = 0 ; i < searchResults.length ; i++) {
		searchResults[i] = new wildbook.Model.Encounter(searchResults[i]);
	}
*/

	sTable = new SortTable({
		data: searchResults,
		perPage: howMany,
		sliderElement: $('#results-slider'),
		columns: colDefn,
	});

	$('#results-table').addClass('tablesorter').addClass('pageableTable');
	var th = '<thead><tr>';
		for (var c = 0 ; c < colDefn.length ; c++) {
			var cls = 'ptcol-' + colDefn[c].key;
			if (!colDefn[c].nosort) {
				if (sortCol < 0) { //init
					sortCol = c;
					cls += ' headerSortUp';
				}
				cls += ' header" onClick="return headerClick(event, ' + c + ');';
			}
			th += '<th class="' + cls + '">' + colDefn[c].label + '</th>';
		}
	$('#results-table').append(th + '</tr></thead>');
	for (var i = 0 ; i < howMany ; i++) {
		var r = '<tr onClick="return rowClick(this);" class="clickable pageableTable-visible">';
		for (var c = 0 ; c < colDefn.length ; c++) {
			r += '<td class="ptcol-' + colDefn[c].key + ' tdw"><div></div></td>';
		}
		r += '</tr>';
		$('#results-table').append(r);
	}

	$('.ptcol-thumb.tdw').removeClass('tdw');

	sTable.initSort();
	sTable.initValues();


	newSlice(sortCol);

	$('#progress').hide();
	sTable.sliderInit();
	show();
	computeCounts();
	displayCounts();

	$('#results-table').on('mousewheel', function(ev) {  //firefox? DOMMouseScroll
		if (!sTable.opts.sliderElement) return;
		ev.preventDefault();
		var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
		if (delta != 0) nudge(-delta);
	});

}



function updateIAResults(d) {
	console.info('iaresults -> %o', d);
	if (d.error || !d.success || !d.taskSummary) {
		if (!d.error) d.error = 'unknown';
		alert('error getting IA results: ' + d.error);
		return;
	}
	var needUpdating = [];
	var foundAnns = [];
	for (var i in iaResults) {
		var updated = false;
		for (var annId in iaResults[i]) {
			if (d.taskSummary[annId]) {
				var r = searchResults[i].get('_iaResults');
				if (!r) {
					console.error('searchResults[%s] did not have _iaResults!?', i);
					continue;
				}
				r[annId] = d.taskSummary[annId];
				updated = true;
				foundAnns.push(annId);
			}
		}
		if (updated) needUpdating.push(i);
	}
	for (var annId in d.taskSummary) {
		if (foundAnns.indexOf(annId) < 0) {
			console.warn('taskSummary reported an annotation we dont care about: %s', annId);
		}
	}
	console.log('needUpdating -> %o', needUpdating);
	if (needUpdating.length < 1) return;

	//refresh the values where needed, then the sorting for the IA summary column
	for (var i = 0 ; i < needUpdating.length ; i++) {
		sTable.refreshValue(needUpdating[i], 1);
	}
	sTable.refreshSort(1);
	newSlice(sortCol);
	show();  //update table to show changes
}


function rowClick(el) {
	console.log(el);
	var w = window.open('encounter.jsp?number=' + el.getAttribute('data-id'), '_blank');
	w.focus();
	return false;
}

function headerClick(ev, c) {
	start = 0;
	ev.preventDefault();
	console.log(c);
	if (sortCol == c) {
		sortReverse = !sortReverse;
	} else {
		sortReverse = false;
	}
	sortCol = c;

	$('#results-table th.headerSortDown').removeClass('headerSortDown');
	$('#results-table th.headerSortUp').removeClass('headerSortUp');
	if (sortReverse) {
		$('#results-table th.ptcol-' + colDefn[c].key).addClass('headerSortUp');
	} else {
		$('#results-table th.ptcol-' + colDefn[c].key).addClass('headerSortDown');
	}
console.log('sortCol=%d sortReverse=%o', sortCol, sortReverse);
	newSlice(sortCol, sortReverse);
	show();
}


function show() {
	$('#results-table td').html('');
	$('#results-table tbody tr').show();
	for (var i = 0 ; i < results.length ; i++) {
		var privateResults = searchResults[results[i]].get('_sanitized') || false;
		var title = 'Encounter ' + searchResults[results[i]].id;
		if (privateResults) {
			title += ' [private]';
			$($('#results-table tbody tr')[i]).addClass('collab-private');
		} else {
			$($('#results-table tbody tr')[i]).removeClass('collab-private');
		}
		$('#results-table tbody tr')[i].title = title;
		$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].id);
		for (var c = 0 ; c < colDefn.length ; c++) {
			$('#results-table tbody tr')[i].children[c].innerHTML = '<div>' + sTable.values[results[i]][c] + '</div>';
		}
	}
	if (results.length < howMany) {
		$('#results-slider').hide();
		for (var i = 0 ; i < (howMany - results.length) ; i++) {
			$('#results-table tbody tr')[i + results.length].style.display = 'none';
		}
	} else {
		$('#results-slider').show();
	}

	//if (sTable.opts.sliderElement) sTable.opts.sliderElement.slider('option', 'value', 100 - (start / (searchResults.length - howMany)) * 100);
	sTable.sliderSet(100 - (start / (sTable.matchesFilter.length - howMany)) * 100);
	displayPagePosition();
}

function computeCounts() {
	counts.total = sTable.matchesFilter.length;
	counts.unid = 0;
	counts.ided = 0;
	counts.dailydup = 0;
	var uniq = {};

	for (var i = 0 ; i < counts.total ; i++) {
		var iid = searchResults[sTable.matchesFilter[i]].get('individualID');
		if (iid == 'Unassigned') {
			counts.unid++;
		} else {
			var k = iid + ':' + searchResults[sTable.matchesFilter[i]].get('year') + ':' + searchResults[sTable.matchesFilter[i]].get('month') + ':' + searchResults[sTable.matchesFilter[i]].get('day');
			if (!uniq[k]) {
				uniq[k] = true;
				counts.ided++;
			} else {
				counts.dailydup++;
			}
		}
	}
/*
	var k = Object.keys(uniq);
	counts.ided = k.length;
*/
}


function displayCounts() {
	for (var w in counts) {
		$('#count-' + w).html(counts[w]);
	}
}


function displayPagePosition() {
	if (sTable.matchesFilter.length < 1) {
		$('#table-info').html('<b>no matches found</b>');
		return;
	}

	var max = start + howMany;
	if (sTable.matchesFilter.length < max) max = sTable.matchesFilter.length;
	$('#table-info').html((start+1) + ' - ' + max + ' of ' + sTable.matchesFilter.length);
}


function newSlice(col, reverse) {
	results = sTable.slice(col, start, start + howMany, reverse);
}


function nudge(n) {
	start += n;
	if ((start + howMany) > sTable.matchesFilter.length) start = sTable.matchesFilter.length - howMany;
	if (start < 0) start = 0;
console.log('start -> %d', start);
	newSlice(sortCol, sortReverse);
	show();
}

function tableDn() {
	return nudge(-1);
	start--;
	if (start < 0) start = 0;
	newSlice(sortCol, sortReverse);
	show();
}

function tableUp() {
	return nudge(1);
	start++;
	if (start > sTable.matchesFilter.length - 1) start = sTable.matchesFilter.length - 1;
	newSlice(sortCol, sortReverse);
	show();
}



////////
var encs;
$(document).ready( function() {
	wildbook.init(function() {
		encs = new wildbook.Collection.Encounters();
		encs.fetch({
/*
			// h/t http://stackoverflow.com/questions/9797970/backbone-js-progress-bar-while-fetching-collection
			xhr: function() {
				var xhr = $.ajaxSettings.xhr();
				xhr.onprogress = fetchProgress;
				return xhr;
			},
*/
			jdoql: jdoql,
			success: function() { searchResults = encs.models; doTable(); },
		});
	});
});


function fetchProgress(ev) {
	if (!ev.lengthComputable) return;
	var percent = ev.loaded / ev.total;
console.info(percent);
}


function _colIndividual(o) {
	//var i = '<b><a target="_new" href="individuals.jsp?number=' + o.individualID + '">' + o.individualID + '</a></b> ';
	var i = '<b>' + o.individualID + '</b> ';
	if (!extra[o.individualID]) return i;
	i += (extra[o.individualID].firstIdent || '') + ' <i>';
	i += (extra[o.individualID].genusSpecies || '') + '</i>';
	return i;
}


function _colNumberEncounters(o) {
	if (!extra[o.individualID]) return '';
	var n = extra[o.individualID].numberEncounters;
	if (n == undefined) return '';
	return n;
}

/*
function _colYearsBetween(o) {
	return o.get('maxYearsBetweenResightings');
}
*/

function _colNumberLocations(o) {
	if (!extra[o.individualID]) return '';
	var n = extra[o.individualID].locations;
	if (n == undefined) return '';
	return n;
}


function _colTaxonomy(o) {
	var animal = 'n/a';
	if (o.get('genus')) return o.get('genus');
	if (o.get('specificEpithet')) return o.get('specificEpithet');
	return 'n/a';
}


function _colRowNum(o) {
	return o._rowNum;
}


function _xxxcolThumb(o) {
	if (!extra[o.individualID]) return '';
	var url = extra[o.individualID].thumbUrl;
	if (!url) return '';
	return '<div style="background-image: url(' + url + ');"><img src="' + url + '" /></div>';
}


function _colModified(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}

function _submitterID(o) {
	var m = o.get('submitterName');
	if (!m) m = o.get('submitterProject');
	return m;
}

function _textExtraction(n) {
	var s = $(n).text();
	var skip = new RegExp('^(none|unassigned|)$', 'i');
	if (skip.test(s)) return 'zzzzz';
	return s;
}






var tableContents = document.createDocumentFragment();

function xdoTable() {
	resultsTable = new pageableTable({
		columns: testColumns,
		tableElement: $('#results-table'),
		sliderElement: $('#results-slider'),
		tablesorterOpts: {
			headers: { 0: {sorter: false} },
			textExtraction: _textExtraction,
		},
	});

	resultsTable.tableInit();

	encs = new wildbook.Collection.Encounters();
	var addedCount = 0;
	encs.on('add', function(o) {
		var row = resultsTable.tableCreateRow(o);
		row.click(function() { var w = window.open('encounter.jsp?number=' + row.data('id'), '_blank'); w.focus(); });
		row.addClass('clickable');
		row.appendTo(tableContents);
		addedCount++;
/*
		var percentage = Math.floor(addedCount / searchResults.length * 100);
console.log(percentage);
$('#progress').html(percentage);
*/
		if (addedCount >= searchResults.length) {
			$('#results-table').append(tableContents);
		}
	});

	_.each(searchResults, function(o) {
//console.log(o);
		encs.add(new wildbook.Model.Encounter(o));
	});
	$('#progress').remove();
	resultsTable.tableShow();

/*
	encs.fetch({
		//fields: { individualID: 'newMatch' },
		success: function() {
			$('#progress').remove();
			resultsTable.tableShow();
		}
	});
*/

}


function _colIndLink(o) {
	var iid = o.get('individualID');
	if (!iid || (iid == 'Unknown') || (iid == 'Unassigned')) return 'Unassigned';
	//if (!iid || (iid == 'Unknown') || (iid == 'Unassigned')) return '<a onClick="return justA(event);" class="pt-vm-button" target="_blank" href="encounterVM.jsp?number=' + o.id + '">Visual Matcher</a><span class="unassigned">Unassigned</span>';
//
//
	return '<a target="_blank" onClick="return justA(event);" title="Individual ID: ' + iid + '" href="../individuals.jsp?number=' + iid + '">' + iid + '</a>';
}


//stops propagation of click to enclosing <TR> which wants click too
function justA(ev) {
	ev.stopPropagation();
	return true;
}


//new way

function _colEncDate(o) {
	return o.dateAsString();
}

function _colEncDateSort(o) {
	var d = o.date();
	if (!d) return 0;
	return d.getTime();
}

//old way
//function _colEncDate(o) {
//	var d = o.date();
//	if (!d) return '';
//	return d.toLocaleDateString();
//}

//function _colEncDateSort(o) {
//	var d = o.date();
//	if (!d) return '';
//	return d.getTime();
//}

function _colTaxonomy(o) {
	if (!o.get('genus') || !o.get('specificEpithet')) return 'n/a';
	return o.get('genus') + ' ' + o.get('specificEpithet');
}

function _colFileName(o) {
  if (!o.get('annotations')) return 'none';
  var outStrings = [];
  for (id in o.get('annotations')) {
    var ann = o.get('annotations')[id];
    //note: assuming 0th feature "may be bad" ?   TODO
    if (ann.features && ann.features.length && ann.features[0].mediaAsset && ann.features[0].mediaAsset.filename) {
      outStrings.push(ann.features[0].mediaAsset.filename);
    }
/*
    if (ann.mediaAsset != undefined) {
      var urlString = ann.mediaAsset.url;
      var pieces = urlString.split('/');
      var betweenLastSlashAndJpg = pieces[pieces.length-1].split('.')[0];
      outStrings[outStrings.length] = betweenLastSlashAndJpg;
      //console.log('\t added url string: '+ann.mediaAsset.url);
    }
    console.log('\t no mediaAsset found in annotation '+JSON.stringify(ann));
*/
  }
  return outStrings.join(',\n');
}
function _colAlternateID(o) {
  if (!o.get('otherCatalogNumbers')) return '';
}

function _colRowNum(o) {
	return o._rowNum;
}


function _colIA(o) {
	if (!o.get('_iaResults')) return '';
	//for sorting.  not it is asc numeric, so smaller appears at top
	var sortWeights = {
		pending: 0,
		'success-match': 3,
		'success-miss': 5,
		error: 7,
		unknown: 9,
	};
	var res = [];
	var total = {};
	var mostRecent = 0;
	var mostRecentNice = '';
	for (var annId in o.get('_iaResults')) {
		var sum = _colAnnIASummary(annId, o.get('_iaResults')[annId]);
		if (sum.mostRecent > mostRecent) {
			mostRecent = sum.mostRecent;
			mostRecentNice = sum.mostRecentNice;
		}
		res.push(sum.html);
		for (var flav in sum.data) {
			if (sum.data[flav] < 1) continue;
			if (!total[flav]) total[flav] = 0;
			total[flav] += sum.data[flav];
		}
	}
	if (res.length < 1) return '<span class="ia-ann-summary"><span class="ia-unknown">?</span></span>';
	if (Object.keys(total).length == 1) {
		var flav = Object.keys(total)[0];
		o.set('_sortWeight', sortWeights[flav] + '.' + (10000000000000 - mostRecent));
		return '<span class="ia-ann-summary" title="' + total[flav] + ' ' + flav + ' on ' + res.length + ' imgs; most recent run ' + sum.mostRecentNice + '"><span class="ia-' + flav + '">' + total[flav] + '</span></span>';
	}

	//for sortWeight, we pick the lowest value
	var sw = 500;
	for (var flav in total) {
		if (sortWeights[flav] < sw) sw = sortWeights[flav];
	}
	o.set('_sortWeight', sw + '.' + (10000000000000 - mostRecent));
	return res.join('');
}

function _colAnnIASummary(annId, sum) {
	console.log('%s ------> %o', annId, sum);
		var mostRecent = 0;
	var mostRecentTaskId = false;
	var flav = ['success-match', 'success-miss', 'pending', 'error', 'unknown'];
	var r = {};
	for (var i = 0 ; i < flav.length ; i++) {
		r[flav[i]] = 0;
	}
	for (var taskId in sum) {
		if (!sum[taskId].timestamp || !sum[taskId].status || !sum[taskId].status._response) {
			console.warn('unknown summary on annId=%s, taskId=%s -> %o', annId, taskId, sum[taskId]);
			r.unknown++;
			continue;
		}

		if (sum[taskId].timestamp > mostRecent) {
			mostRecent = sum[taskId].timestamp;
			mostRecentTaskId = taskId;
		}
	}

	if (mostRecentTaskId) {
		if (sum[mostRecentTaskId].status && sum[mostRecentTaskId].status._response && sum[mostRecentTaskId].status._response.status && sum[mostRecentTaskId].status._response.response && sum[mostRecentTaskId].status._response.status.success) {
			if (sum[mostRecentTaskId].status._response.response && sum[mostRecentTaskId].status._response.response.json_result &&
			    (sum[mostRecentTaskId].status._response.response.json_result.length > 0)) {
				var numMatches = 0;
//console.warn(sum[mostRecentTaskId].status._response.response.json_result);
				for (var m = 0 ; m < sum[mostRecentTaskId].status._response.response.json_result.length ; m++) {
					if (!sum[mostRecentTaskId].status._response.response.json_result[m].daid_list) continue;
					numMatches += sum[mostRecentTaskId].status._response.response.json_result[m].daid_list.length;
				}
				if (numMatches > 0) {
					r['success-match']++;
				} else {
					r['success-miss']++;
				}
			} else {
				console.warn('got IA results, but could not parse on annId=%s, taskId=%s -> %s', annId, mostRecentTaskId, sum[mostRecentTaskId].status);
				r.error++;
			}
		} else if (!sum[mostRecentTaskId].status._response.success || sum[mostRecentTaskId].status._response.error) {
			console.warn('error on annId=%s, taskId=%s -> %s', annId, mostRecentTaskId, sum[mostRecentTaskId].status._response.error || 'non-success');
			r.error++;
		} else {  //guess this means we are waiting on results?
			console.warn('reporting pending on annId=%s, taskId=%s -> %o', annId, mostRecentTaskId, sum[mostRecentTaskId].status._response);
			r.pending++;
		}


/* old way, to show *all* results
		if (sum[taskId].timestamp > mostRecent) mostRecent = sum[taskId].timestamp;

		//wtf, gimme a break, nested json!
		if (sum[taskId].status && sum[taskId].status._response && sum[taskId].status._response.status && sum[taskId].status._response.response && sum[taskId].status._response.status.success) {
			if (sum[taskId].status._response.response && sum[taskId].status._response.response.json_result &&
			    (sum[taskId].status._response.response.json_result.length > 0)) {
				var numMatches = 0;
//console.warn(sum[taskId].status._response.response.json_result);
				for (var m = 0 ; m < sum[taskId].status._response.response.json_result.length ; m++) {
					if (!sum[taskId].status._response.response.json_result[m].daid_list) continue;
					numMatches += sum[taskId].status._response.response.json_result[m].daid_list.length;
				}
				if (numMatches > 0) {
					r['success-match']++;
				} else {
					r['success-miss']++;
				}
			} else {
				console.warn('got IA results, but could not parse on annId=%s, taskId=%s -> %s', annId, taskId, sum[taskId].status);
				r.error++;
			}
		} else if (!sum[taskId].status._response.success || sum[taskId].status._response.error) {
			console.warn('error on annId=%s, taskId=%s -> %s', annId, taskId, sum[taskId].status._response.error || 'non-success');
			r.error++;
		} else {  //guess this means we are waiting on results?
			console.warn('reporting pending on annId=%s, taskId=%s -> %o', annId, taskId, sum[taskId].status._response);
			r.pending++;
		}
*/
	}

	var rtn = '';
	var expl = '';
	for (var i = 0 ; i < flav.length ; i++) {
		if (r[flav[i]] < 1) continue;
		rtn += '<span class="ia-' + flav[i] + '">' + r[flav[i]] + '</span>';
		expl += ' ' + flav[i] + ':' + r[flav[i]];
	}
	if (!rtn) return '<span class="ia-error">!</span>';
	var d = new Date(mostRecent);
	return {
		html: '<span class="ia-ann-summary" title="annot ' + annId + '; most recent run ' + d.toLocaleString() + ';' + expl + '">' + rtn + '</span>',
		mostRecent: mostRecent,
		mostRecentNice: d.toLocaleString(),
		data: r
	};
}


function _colIASort(o) {
//console.info('[%s] weight=%o | has _iaResults %o', o.id, o.get('_sortWeight'), !(!o.get('_iaResults')));
	if (o.get('_sortWeight')) return o.get('_sortWeight');
	if (!o.get('_iaResults')) return 1000;
	return 0;
}

function _colThumb(o) {
	var url = wildbook.cleanUrl(o.thumbUrl());
	if (!url) return '';
	return '<div style="background-image: url(' + url + ');"><img src="' + url + '" /><span class="collab-icon"></span></div>';
	return '<img src="' + url + '" />';
}


function _colModified(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}

function _colModifiedSort(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.getTime();
}

function _colCreationDate(o) {
	var m = o.get('dwcDateAdded');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}

function _colCreationDateSort(o) {
	var m = o.get('dwcDateAdded');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return 0;
	return d.getTime();
}



function _textExtraction(n) {
	var s = $(n).text();
	var skip = new RegExp('^(none|unassigned|)$', 'i');
	if (skip.test(s)) return 'zzzzz';
	return s;
}


function applyFilter() {
	var t = $('#filter-text').val();
console.log(t);
	sTable.filter(t);
	start = 0;
	newSlice(0);
	show();
	computeCounts();
	displayCounts();
}

</script>

<p class="table-filter-text">
<input placeholder="filter by text" id="filter-text" onChange="return applyFilter()" />
<input type="button" value="filter" />
<input type="button" value="clear" onClick="$('#filter-text').val(''); applyFilter(); return true;" />
<span style="margin-left: 40px; color: #888; font-size: 0.8em;" id="table-info"></span>
</p>
<div class="pageableTable-wrapper">
	<div id="progress">Loading results table...</div>
	<table id="results-table"></table>
	<div id="results-slider"></div>
</div>


<p>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td align="left">
      <p><strong><%=encprops.getProperty("matchingEncounters")%>
      </strong>: <span id="count-total"></span>
        <%
          if (request.getUserPrincipal()!=null) {
        %>
        <br/>
        <span id="count-ided"><%=dispToString(numUniqueEncounters)%></span> <%=encprops.getProperty("identifiedUnique")%><br/>
        <span id="count-unid"><%=dispToString(numUnidentifiedEncounters)%></span> <%=encprops.getProperty("unidentified")%><br/>
        <span id="count-dailydup"><%=dispToString(numDuplicateEncounters)%></span> <%=encprops.getProperty("dailyDuplicates")%>
        <%
          }
        %>
      </p>
      <%
        myShepherd.beginDBTransaction();
      %>
      <p><strong><%=encprops.getProperty("totalEncounters")%>
      </strong>: <%=(myShepherd.getNumEncounters() + (myShepherd.getNumUnidentifiableEncounters()))%>
      </p>
    </td>

  </tr>
</table>

<table>
  <tr>
    <td align="left">

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=prettyPrint.toString().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=filter %>
      </p>

    </td>
  </tr>
</table>
</div>

<%
  }
  catch(Exception e){e.printStackTrace();}
  finally{
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
  }
  //rEncounters = null;

%>



<jsp:include page="../footer.jsp" flush="true"/>
