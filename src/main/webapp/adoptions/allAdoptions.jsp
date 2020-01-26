<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
         		org.ecocean.Adoption,
         		org.ecocean.CommonConfiguration,
         		org.ecocean.Shepherd,
         		org.ecocean.ShepherdProperties,
         		org.ecocean.servlet.ServletUtilities, 
         		org.joda.time.DateTime, 
         		org.joda.time.format.DateTimeFormatter, 
         		org.joda.time.format.ISODateTimeFormat, 
         		javax.jdo.Extent,
         		javax.jdo.Query,
         		java.util.Iterator, 
         		org.datanucleus.api.rest.orgjson.JSONArray, 
         		org.json.JSONObject,
         		java.util.List,
         		java.util.Collection,
         		java.util.ArrayList,
         		java.util.Properties" %>


<%
String context="context0";
context=ServletUtilities.getContext(request);



  String langCode=ServletUtilities.getLanguageCode(request);
  
  Properties props = new Properties();
  props = ShepherdProperties.getProperties("adoption.properties", langCode,context);
  

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("allAdoptions.jsp");
  List<Adoption> adoptions = new ArrayList<Adoption>();

  String filter="SELECT FROM org.ecocean.Adoption where id!=null ORDER BY adoptionStartDate";
  Query q=myShepherd.getPM().newQuery(filter);
  Collection results = (Collection) q.execute();
  adoptions=new ArrayList<Adoption>(results);
  q.closeAll();
  
  int count = 0;
  JSONArray jsonobj=new JSONArray();
  for(Adoption adopt:adoptions){
  	jsonobj.put(adopt.uiJson(request, false));
  }
  String indsJson = jsonobj.toString();
  System.out.println(indsJson);

%>

    <jsp:include page="../header.jsp" flush="true" />
    
    
    <script src="../javascript/underscore-min.js"></script>
<script src="../javascript/backbone-min.js"></script>
<script src="../javascript/core.js"></script>
<script src="../javascript/classes/Base.js"></script>
    
    	<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
	
	<link rel="stylesheet" href="../css/pageableTable.css" />
	<script src="../javascript/tsrt.js"></script>
	

	
	
	

        <div class="container maincontent">

      <h1 class="intro"><%=props.getProperty("title")%></h1>
        
<script type="text/javascript">
	
	var searchResults = <%=indsJson%>;
	console.log(searchResults);
	var resultsTable;
	
	
	$(document).keydown(function(k) {
		if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
		if (k.which == 38) return tableDn();
		if (k.which == 40) return tableUp();
		if (k.which == 33) return nudge(-howMany);
		if (k.which == 34) return nudge(howMany);
	});
	
	
	
	var colDefn = [

		
		{
			key: 'id',
			label: '<%=props.getProperty("id")%>',
			value: _colId,
			sortValue: function(o) { return o.id; },
		},
		{
			key: 'stripeCustomerId',
			label: '<%=props.getProperty("stripeCustomerId")%>',
			value: _colStripeCustomerId,
			sortValue: function(o) { return o.stripeCustomerId; },
		},
	
		{
			key: 'adopterName',
			label: '<%=props.getProperty("adopterName")%>',
			value: _colAdopterName
		},
		{
			key: 'adopterEmail',
			label: '<%=props.getProperty("adopterEmail")%>',
			value: _colAdopterEmail
		},
		{
			key: 'adoptionStartDate',
			label: '<%=props.getProperty("adoptionStartDate")%>',
			value: _colAdoptionStartDate
		}
		,
		{
			key: 'individual',
			label: '<%=props.getProperty("individual")%>',
			value: _colIndividual
		}
		,
		{
			key: 'encounter',
			label: '<%=props.getProperty("encounter")%>',
			value: _colEncounter
		}
		,
		{
			key: 'adoptionType',
			label: '<%=props.getProperty("adoptionType")%>',
			value: _colAdoptionType
		}
	
	];
	
	
	var howMany = 10;
	var start = 0;
	var results = [];
	
	var sortCol = 4;
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
		nudge(-1);
	
	}
	
	function rowClick(el) {
		console.log(el);
		var w = window.open('adoption.jsp?isEdit=true&number=' + el.getAttribute('data-id'), '_self');
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
			//$('#results-table tbody tr')[i].title = 'Encounter ' + searchResults[results[i]].id;
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
	

	
	
	function _colId(o) {
		if (o.id == undefined) return '';
		var i = '<b>' + o.id + '</b>';
		return i;
	}	
	
	function _colStripeCustomerId(o) {
		if (o.stripeCustomerId == undefined) return '';
		return o.stripeCustomerId;
	}
	
	function _colAdopterName(o) {
		if (o.adopterName == undefined) return '';
		return o.adopterName;
	}
	
	function _colAdopterEmail(o) {
		if (o.adopterEmail == undefined) return '';
		return o.adopterEmail;
	}
	
	function _colIndividual(o) {
		if (o.individual == undefined) return '';
		return o.individual;
	}
	
	function _colEncounter(o) {
		if (o.encounter == undefined) return '';
		return o.encounter;
	}

	function _colAdoptionType(o) {
		if (o.adoptionType == undefined) return '';
		return o.adoptionType;
	}


	
	
	function _colRowNum(o) {
		return o._rowNum;
	}
	


	function _colAdoptionStartDate(o) {
		if (!o.adoptionStartDate) return '';
		return o.adoptionStartDate;
	}
	
	function _colLastLoginSort(o) {
		var m = o.lastLogin;
		if (!m) return '';
		return m;
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
		<input placeholder="<%=props.getProperty("filterByText") %>" id="filter-text" onChange="return applyFilter()" />
		<input type="button" value="<%=props.getProperty("filter") %>" />
		<input type="button" value="<%=props.getProperty("clear") %>" onClick="$('#filter-text').val(''); applyFilter(); return true;" />
		<span style="margin-left: 40px; color: #888; font-size: 0.8em;" id="table-info"></span>
	</p>
	
	<div class="pageableTable-wrapper">
		<div id="progress">loading...</div>
		<table id="results-table"></table>
		<div id="results-slider"></div>
	</div>
        


          <%


            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            myShepherd = null;
          %>
        

        </div>

    <jsp:include page="../footer.jsp" flush="true"/>
