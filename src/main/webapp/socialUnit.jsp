<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, org.ecocean.security.HiddenIndividualReporter, java.util.Properties, java.util.Collection, java.util.Vector,java.util.ArrayList, org.datanucleus.api.rest.orgjson.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, 
         org.datanucleus.api.jdo.JDOPersistenceManager,
         org.ecocean.social.*,
         org.datanucleus.FetchGroup,javax.jdo.*" %>



  <%

  String context="context0";
  context=ServletUtilities.getContext(request);

  
    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);

    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResults.properties"));
    props = ShepherdProperties.getProperties("socialunit.properties", langCode,context);


    %>
    
    	<jsp:include page="header.jsp" flush="true"/>
    	<script src="javascript/underscore-min.js"></script>
		<script src="javascript/backbone-min.js"></script>
		<script src="javascript/core.js"></script>
		<script src="javascript/classes/Base.js"></script>
		
		<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
		
		<link rel="stylesheet" href="css/pageableTable.css" />
		<script src="javascript/tsrt.js"></script>
		
		
		<div class="container maincontent">
    <%

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("socialUnit.jsp");



    int numResults = 0;


    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    

    myShepherd.beginDBTransaction();
    
    try{
    	
    	
    	if(request.getParameter("name")!=null && myShepherd.getSocialUnit(request.getParameter("name").trim())!=null){
    		
	    	
	    	
		    String order ="";
		
		    SocialUnit su=myShepherd.getSocialUnit(request.getParameter("name"));
		    rIndividuals = new Vector<MarkedIndividual>(su.getMarkedIndividuals());
		
			// viewOnly=true arg means this hiddenData relates to viewing the summary results
			HiddenIndividualReporter hiddenData = new HiddenIndividualReporter(rIndividuals, request, true,myShepherd);
			rIndividuals = hiddenData.viewableResults(rIndividuals, true, myShepherd);
	
		  %>
		

		
		
		      <h1 class="intro">
		        <%=props.getProperty("community")%> <%=su.getSocialUnitName() %>
		      </h1>
		
		
		
		<table width="810" border="0" cellspacing="0" cellpadding="0">
		  <tr>
		    <td>
		
		
		      <p><%=props.getProperty("instructions")%></p>
		      
		      <p><a href="individualSearchResults.jsp?community=<%=request.getParameter("name") %>"><%=props.getProperty("clickHere")%></a></p>
		      
		    </td>
		  </tr>
		</table>
		
		
		  <%
		
		    //set up the statistics counters
		
		
		    Vector histories = new Vector();
		    int rIndividualsSize=rIndividuals.size();
		
		    int count = 0;
		    int numNewlyMarked = 0;
		
		
		
		
			JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
			//JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)rIndividuals, jdopm.getExecutionContext());
			JSONArray jsonobj = new JSONArray();
			System.out.println("Starting to iterate over individuals");
			for (MarkedIndividual mark: rIndividuals) {
				org.datanucleus.api.rest.orgjson.JSONObject jobj=mark.uiJson(request, false);
				Membership ms=su.getMembershipForMarkedIndividual(mark);
				if(ms.getRole()!=null)jobj.put("role", ms.getRole());
				if(ms.getStartDate()!=null)jobj.put("startDate", ms.getStartDate());
				if(ms.getEndDate()!=null)jobj.put("endDate", ms.getEndDate());
				jsonobj.put(jobj);
			}
			System.out.println("Done iterating over individuals");
	
			String indsJson = jsonobj.toString();
		
		%>
		
		<style>
		.ptcol-maxYearsBetweenResightings {
			width: 100px;
		}
		.ptcol-numberLocations {
			width: 100px;
		}
		
		</style>
		<script type="text/javascript">
		
		var searchResults = <%=indsJson%>;
	
		
		var resultsTable;
		
		
		$(document).keydown(function(k) {
			if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
			if (k.which == 38) return tableDn();
			if (k.which == 40) return tableUp();
			if (k.which == 33) return nudge(-howMany);
			if (k.which == 34) return nudge(howMany);
		});
		
		var colDefn = [
		/*
			{
				key: 'rowNum',
				label: '#',
				value: _colRowNum,
			},
		*/
			{
				key: 'thumb',
				label: '<%=props.getProperty("thumbnail") %>',
				value: _colThumb,
				nosort: true,
			},
			{
				key: 'individual',
				label: '<%=props.getProperty("markedIndividual")%>',
				value: _colIndividual,
				sortValue: function(o) { return o.displayName; },
				//sortFunction: function(a,b) {},
			},
		
			{
				key: 'nickName',
				label: '<%=props.getProperty("nickNameCol")%>',
				value: _colNickname,
				sortValue: function(o) { 
					if(o.nickname !=null) {return o.nickname.toLowerCase();} 
					else{return "";}
				},
			},
		
			{
				key: 'numberEncounters',
				label: '<%=props.getProperty("numEncounters")%>',
				value: _colNumberEncounters,
				sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
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
			},
			{
				key: 'role',
				label: '<%=props.getProperty("role")%>',//'Role',
				value: _colRole
			},	
			{
				key: 'startDate',
				label: '<%=props.getProperty("startDate")%>',//'Start Date',
				value: _colStartDate
			},	
			{
				key: 'endDate',
				label: '<%=props.getProperty("endDate")%>',//'End Date',
				value: _colEndDate
			},	
		
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
		//var searchResultsObjects = [];
		
		function doTable() {
		/*
			for (var i = 0 ; i < searchResults.length ; i++) {
				searchResultsObjects[i] = new wildbook.Model.MarkedIndividual(searchResults[i]);
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
			var w = window.open('individuals.jsp?number=' + el.getAttribute('data-id'), '_blank');
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
				$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].individualID);
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
				$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].individualID);
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
		
		
		
		function _colIndividual(o) {
			var i = '<b>' + o.displayName + '</b>';
			var fi = o.dateFirstIdentified;
			if (fi) i += '<br /><%=props.getProperty("firstIdentified") %> ' + fi;
			return i;
		}
	
		function _colNickname(o) {
			if (o.nickname == undefined) return '';
			return o.nickname;
		}
		
		function _colRole(o) {
			//console.log("role: "+o.role);
			if (o.role == undefined) return '';
			return o.role;
		}
		
		function _colStartDate(o) {
			//console.log("role: "+o.role);
			if (o.startDate == undefined) return '';
			return o.startDate;
		}
		
		function _colEndDate(o) {
			//console.log("role: "+o.role);
			if (o.endDate == undefined) return '';
			return o.endDate;
		}
		
		
		function _colNumberEncounters(o) {
			if (o.numberEncounters == undefined) return '';
			return o.numberEncounters;
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
		    boolean includeZeroYears = true;
		
		    boolean subsampleMonths = false;
		    if (request.getParameter("subsampleMonths") != null) {
		      subsampleMonths = true;
		    }
		    numResults = count;
		  %>
		</table>
	<%	
	}
	else{
	%>
	
	<p>No corresponding social unit found.</p>
	
		
	<%
	}
	


    }
    catch(Exception e){
    	System.out.println("Exception on IndividualSearchResults!");
    	e.printStackTrace();
    %>
    
    <p>Exception on page!</p>
    <p><%=e.getMessage() %></p>
    
    <%	
    }
    finally{
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    
    

%>

</div>
<jsp:include page="footer.jsp" flush="true"/>
