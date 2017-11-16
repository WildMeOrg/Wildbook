<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities, org.ecocean.*, java.util.Properties, java.util.Collection, java.util.Vector,java.util.ArrayList, org.datanucleus.api.rest.orgjson.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager" %>



  <%

  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load out properties
    Properties props = new Properties();
    Properties svyProps = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);

    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResults.properties"));
    props = ShepherdProperties.getProperties("individualSearchResults.properties", langCode,context);
    svyProps = ShepherdProperties.getProperties("survey.properties");


    int startNum = 1;
    int endNum = 10;


    try {

      if (request.getParameter("startNum") != null) {
        startNum = (new Integer(request.getParameter("startNum"))).intValue();
      }
      if (request.getParameter("endNum") != null) {
        endNum = (new Integer(request.getParameter("endNum"))).intValue();
      }

    } catch (NumberFormatException nfe) {
      startNum = 1;
      endNum = 10;
    }
    int listNum = endNum;

    int day1 = 1, day2 = 31, month1 = 1, month2 = 12, year1 = 0, year2 = 3000;
    try {
      month1 = (new Integer(request.getParameter("month1"))).intValue();
    } catch (Exception nfe) {
    }
    try {
      month2 = (new Integer(request.getParameter("month2"))).intValue();
    } catch (Exception nfe) {
    }
    try {
      year1 = (new Integer(request.getParameter("year1"))).intValue();
    } catch (Exception nfe) {
    }
    try {
      year2 = (new Integer(request.getParameter("year2"))).intValue();
    } catch (Exception nfe) {
    }

    Shepherd myShepherd = new Shepherd(context);
    //myShepherd.setAction("occurrenceSearchResults.jsp");

    int numResults = 0;


    Vector<Survey> numSurveys = new Vector<Survey>();
    myShepherd.beginDBTransaction();
    String order ="";

    SurveyQueryResult result = SurveyQueryProcessor.processQuery(myShepherd, request, order);
    numSurveys = result.getResult();


    if (numSurveys.size() < listNum) {
      listNum = numSurveys.size();
    }
  %>

<style type="text/css">
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


</style>


<jsp:include page="../header.jsp" flush="true"/>

<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>
<script src="javascript/core.js"></script>
<script src="javascript/classes/Base.js"></script>

<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

<link rel="stylesheet" href="css/pageableTable.css" />
<script src="javascript/tsrt.js"></script>


<div class="container maincontent">


      <h1 class="intro">
        <%=svyProps.getProperty("OccurrenceSearchResults")%>
      </h1>

      <ul id="tabmenu">

        <li><a class="active"><%=svyProps.getProperty("table")%>
        </a></li>
            <li><a
           href="surveyExportSearchResults.jsp?<%=request.getQueryString() %>"><%=svyProps.getProperty("export")%>
         </a></li>

      </ul>


<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>


      <!--<p><%=svyProps.getProperty("searchResultsInstructions")%>-->
      </p>
    </td>
  </tr>
</table>


  <%

    //set up the statistics counters


    Vector histories = new Vector();
    int numSurveysSize=numSurveys.size();

    int count = 0;
    int numNewlyMarked = 0;




	JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
	JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)numSurveys, jdopm.getExecutionContext());
	String indsJson = jsonobj.toString();

%>

<style>
.ptcol-maxYearsBetweenResightings {
	width: 100px;
}
.ptcol-numberLocations {
	width: 100px;
}

.searchResultsRow {
  min-height: 1em;
}

</style>
<script type="text/javascript">

var searchResults = <%=indsJson%>;

/*
var testColumns = {
	//rowNum: { label: '#', val: _colRowNum },
	thumb: { label: 'Thumb', val: _colThumb },
	individual: { label: 'Individual', val: _colIndividual },
	numberEncounters: { label: 'Encounters', val: _colNumberEncounters },
	maxYearsBetweenResightings: { label: 'Max yrs between resights' },
	sex: { label: 'Sex' },
	numberLocations: { label: 'No. Locations sighted', val: _colNumberLocations },
};

var inds;
*/

var resultsTable;


$(document).keydown(function(k) {
	if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
	if (k.which == 38) return tableDn();
	if (k.which == 40) return tableUp();
	if (k.which == 33) return nudge(-howMany);
	if (k.which == 34) return nudge(howMany);
});

// functor!
function _notUndefined(fieldName) {
  function _helperFunc(o) {	
    if (o[fieldName] == undefined) return '';
    return o[fieldName];
  }
  return _helperFunc;
}

// Split up some collections before we try to display them. 

var colDefn = [
/*
	{
		key: 'rowNum',
		label: '#',
		value: _colRowNum,
	},

  {
    key: 'imageSet',
    label: '<%=svyProps.getProperty("imageSet")%>',
    value: _notUndefined('imageSet'),
  },
  
*/
 
  {
    key: 'ID',
    label: 'ID',
    value: _notUndefined('ID'),
  },
  {
    key: 'dateTimeCreated',
    label: 'Date Created',
    value: _notUndefined('dateTimeCreated'),
  },
  {
	    key: 'correspondingSurveyID',
	    label: 'Corresponding Survey',
	    value: _notUndefined('correspondingSurveyID'),
  }, 	
  {
	key: 'numberEncounters',
	label: '<%=props.getProperty("numEncounters")%>',
	value: _colNumberEncounters,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
  },
  {
		key: 'decimalLatitude',
		label: 'latitude',
    value: _notUndefined('decimalLatitude'),
    sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
	},
  {
		key: 'decimalLongitude',
		label: 'longitude',
    value: _notUndefined('decimalLongitude'),
    sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
	},
  /*
  {
    key: 'individualCount',
    label: 'Encounters',
    value: _notUndefined('individualCount'),
    sortFunction: function(a,b) { return parseInt(a) - parseInt(b); }
  },
	{
		key: 'individual',
		label: '<%=props.getProperty("markedIndividual")%>',
		value: _colIndividual,
		sortValue: function(o) { return o.individualID.toLowerCase(); },
		//sortFunction: function(a,b) {},
	},

	{
		key: 'maxYearsBetweenResightings',
		label: '<%=props.getProperty("maxYearsBetweenResights")%>',
		sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
	},
	{
		key: 'sex',
		label: '<%=props.getProperty("sex")%>',//'Sex',
	},
	{
		key: 'numberLocations',
		label: '<%=props.getProperty("numLocationsSighted")%>',
		value: _colNumberLocations,
		sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
	}*/

];

var howMany = 30;
var start = 0;
var results = [];

var sortCol = -1;
var sortReverse = true;


var counts = {
	total: 0,
	ided: 0,
	unid: 0,
	dailydup: 0,
};

var sTable = false;
//var searchResultsObjects = [];

function doTable() {
/*
	for (var i = 0 ; i < searchResults.length ; i++) {
		searchResultsObjects[i] = new wildbook.Model.Occurrence(searchResults[i]);
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
		var r = '<tr onClick="return rowClick(this);" class="clickable pageableTable-visible searchResultsRow">';
		for (var c = 0 ; c < colDefn.length ; c++) {
			r += '<td class="ptcol-' + colDefn[c].key + '"></td>';
		}
		r += '</tr>';
		$('#results-table').append(r);
	}

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

function rowClick(el) {
	console.log(el);
	var w = window.open('occurrence.jsp?number=' + el.getAttribute('data-id'), '_blank');
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


function xxxshow() {
	$('#results-table td').html('');
	for (var i = 0 ; i < results.length ; i++) {
		//$('#results-table tbody tr')[i].title = searchResults[results[i]].individualID;
		$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].ID);
		for (var c = 0 ; c < colDefn.length ; c++) {
			$('#results-table tbody tr')[i].children[c].innerHTML = sTable.values[results[i]][c];
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

	sTable.sliderSet(100 - (start / (searchResults.length - howMany)) * 100);
}



function show() {
	$('#results-table td').html('');
	$('#results-table tbody tr').show();
	for (var i = 0 ; i < results.length ; i++) {
		//$('#results-table tbody tr')[i].title = 'Encounter ' + searchResults[results[i]].id;
		$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].ID);
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
	return;  //none of the below applies here! (cruft from encounters for prosperity)
	counts.unid = 0;
	counts.ided = 0;
	counts.dailydup = 0;
	var uniq = {};

	for (var i = 0 ; i < counts.total ; i++) {
		console.log('>>>>> what up? %o', searchResults[sTable.matchesFilter[i]]);
		var iid = searchResults[sTable.matchesFilter[i]].individualID;
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
$(document).ready( function() {
	wildbook.init(function() { doTable(); });
});


var tableContents = document.createDocumentFragment();

/*
function doTable() {
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

	inds = new wildbook.Collection.Occurrences();
	var addedCount = 0;
	inds.on('add', function(o) {
		var row = resultsTable.tableCreateRow(o);
		row.click(function() { var w = window.open('individuals.jsp?number=' + row.data('id'), '_blank'); w.focus(); });
		row.addClass('clickable');
		row.appendTo(tableContents);
		addedCount++;
var percentage = Math.floor(addedCount / searchResults.length * 100);
if (percentage % 3 == 0) console.log(percentage);
		if (addedCount >= searchResults.length) {
			$('#results-table').append(tableContents);
		}
	});

	_.each(searchResults, function(o) {
		inds.add(new wildbook.Model.Occurrence(o));
	});
	$('#progress').remove();
	resultsTable.tableShow();


}
*/


function _colIndividual(o) {
	var i = '<b>' + o.individualID + '</b>';
	var fi = o.dateFirstIdentified;
	if (fi) i += '<br /><%=props.getProperty("firstIdentified") %> ' + fi;
	return i;
}


function _colNumberEncounters(o) {
	if (o.encounters == undefined) return '';
	//console.log("Here's the encs: "+JSON.stringify(o.encounters));
	//console.log("Here's th length: "+o.encounters.length);
	return o.encounters.length;
}


/*
function _colYearsBetween(o) {
	return o.get('maxYearsBetweenResightings');
}
*/

function _colNumberLocations(o) {
	if (o.numberLocations == undefined) return '';
	return o.numberLocations;
}

function _colLatitude(o) {
	if (o.latitude == undefined) return '';
	return o.latitude;
}
function _colLongitude(o) {
	if (o.longitude == undefined) return '';
	return o.longitude;
}
function _colID(o) {
  if (o.ID == undefined) {
    if (o.ID == undefined) return '';
    return o.DataCollectionEventID;
  }
  return o.ID;
}

function _colSamplingProtocol(o) {
  if (o.samplingProtocol == undefined) return '';
  return o.samplingProtocol;
}

function _colTaxonomy(o) {
	if (!o.get('genus') || !o.get('specificEpithet')) return 'n/a';
	return o.get('genus') + ' ' + o.get('specificEpithet');
}


function _colRowNum(o) {
	return o._rowNum;
}


function _colThumb(o) {
	var url = o.thumbnailUrl;
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

function _colOccDate(o) {
	var dateMillis = o.get('millis');
	if (!dateMillis) return '';
	var dateString = new Date(dateMillis).toString();
	return dateString;
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
	newSlice(1);
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
	<div id="progress">loading...</div>
	<table id="results-table"></table>
	<div id="results-slider"></div>
</div>


<%
    boolean includeZeroYears = true;

    boolean subsampleMonths = false;
    if (request.getParameter("subsampleMonths") != null) {
      subsampleMonths = true;
    }
    numResults = count;
  %>
</table>


<%
  myShepherd.rollbackDBTransaction();
  startNum += 10;
  endNum += 10;
  if (endNum > numResults) {
    endNum = numResults;
  }


%>

<p>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td align="left">
      <p><strong><%=svyProps.getProperty("matchingOccurrences")%>
      </strong>: <span id="count-total"></span>
      </p>
      <%myShepherd.beginDBTransaction();%>
      <p><strong><%=svyProps.getProperty("totalOccurrences")%>
    </strong>: <%=(myShepherd.getNumOccurrences())%>
      </p>
    </td>
    <%
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();

    %>
  </tr>
</table>
<%
  if (request.getParameter("noQuery") == null) {
%>
<table>
  <tr>
    <td align="left">

      <p><strong><%=props.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=props.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=result.getQueryPrettyPrint().replaceAll("locationField", props.getProperty("location")).replaceAll("locationCodeField", props.getProperty("locationID")).replaceAll("verbatimEventDateField", props.getProperty("verbatimEventDate")).replaceAll("Sex", props.getProperty("sex")).replaceAll("Keywords", props.getProperty("keywords")).replaceAll("alternateIDField", (props.getProperty("alternateID"))).replaceAll("alternateIDField", (props.getProperty("size")))%>
      </p>

      <p class="caption"><strong><%=props.getProperty("jdoql")%>
      </strong><br/>
        <%=result.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<%
  }
%>
</p>


</div>
<jsp:include page="../footer.jsp" flush="true"/>
