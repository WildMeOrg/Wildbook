<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.servlet.importer.ImportTask,
javax.jdo.Query,
org.json.JSONArray,
org.json.JSONObject,
java.util.ArrayList,
java.util.Collection,
java.util.List,
java.util.Map" %>

<%@ page import="org.ecocean.shepherd.core.Shepherd" %>

<%-- Batch count methods are in ImportTask.java --%>

<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("imports.jsp");
myShepherd.beginDBTransaction();
User user = AccessControl.getUser(request, myShepherd);
if (user == null) {
    response.sendError(401, "access denied");
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    return;
}
boolean adminMode = request.isUserInRole("admin");

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


%>
<jsp:include page="header.jsp" flush="true"/>



<style>
.bootstrap-table {
    height: min-content;
}
.dim, .ct0 {
    color: #AAA;
}

.yes {
    color: #0F5;
}
.no {
    color: #F20;
}

a.button {
    font-weight: bold;
    font-size: 0.9em;
    background-color: #AAA;
    border-radius: 4px;
    padding: 0 6px;
    text-decoration: none;
    cursor: pointer;
}
a.button:hover {
    background-color: #DDA;
    text-decoration: none;
}
</style>


	<script src="javascript/underscore-min.js"></script>
	<script src="javascript/backbone-min.js"></script>
	<script src="javascript/core.js"></script>
	<script src="javascript/classes/Base.js"></script>

	<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

	<link rel="stylesheet" href="css/pageableTable.css" />
	<script src="javascript/tsrt.js"></script>


<div class="container maincontent">
<h2>Import Tasks</h2>

	<p>The following is a list of your bulk imports as well as those of your collaborators.</p>
	<%


try{
    // Batch-load all counts in 3 queries (instead of 3 per task)
    Map<String,Integer> encCounts = ImportTask.getAllEncounterCounts(myShepherd);
    Map<String,Integer> indivCounts = ImportTask.getAllIndividualCounts(myShepherd);
    Map<String,Integer> mediaCounts = ImportTask.getAllMediaAssetCounts(myShepherd);

    String jdoql = "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE id != null";
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("created desc");
    Collection c = (Collection) (query.execute());
    List<ImportTask> tasks = new ArrayList<ImportTask>(c);
    query.closeAll();

    JSONArray jsonobj = new JSONArray();

    for (ImportTask task : tasks) {
    	if(adminMode || ServletUtilities.isUserAuthorizedForImportTask(task,request,myShepherd)){
			String taskID = task.getId();
	            User tu = task.getCreator();
	            String uname = "(guest)";
	            if (tu != null) {
	                uname = tu.getFullName();
	                if (uname == null) uname = tu.getUsername();
	                if (uname == null) uname = tu.getUUID();
	                if (uname == null) uname = Long.toString(tu.getUserID());
	            }

	        int numEncs = encCounts.getOrDefault(taskID, 0);
	        int indivCount = indivCounts.getOrDefault(taskID, 0);
	        int numMediaAssets = mediaCounts.getOrDefault(taskID, 0);
	        String created=task.getCreated().toString().substring(0,10);

	        String iaStatusString="";
	        if (task.getIATask() !=null) {
	            if(!task.iaTaskRequestedIdentification())iaStatusString="detection";
	            else{iaStatusString="identification";}
	        }
	        String status=task.getStatus();
	        if(status!=null && status.equals("processing-detection")) status="complete";

	        JSONObject jobj = new JSONObject();
	        jobj.put("iaStatus", iaStatusString);
	        jobj.put("numMediaAssets", numMediaAssets);
	        jobj.put("numEncs", numEncs);
	        jobj.put("created", created);
	        jobj.put("uname", uname);
	        jobj.put("taskID", taskID);
	        jobj.put("indivCount", indivCount);
	        jobj.put("status", status);
                String name = task.getSourceName();
                jobj.put("filename", (name == null) ? "-" : name);
	        jsonobj.put(jobj);

    	}
    } //end for loop of tasks
    
    
    %>
    
    	<script type="text/javascript">

			var searchResults = <%=jsonobj.toString() %>;
			var resultsTable;

			$(document).keydown(function(k) {
				if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
				if (k.which == 38) return tableDn();
				if (k.which == 40) return tableUp();
				if (k.which == 33) return nudge(-howMany);
				if (k.which == 34) return nudge(howMany);
			});
			
			var colDefn = [
			           		//{
			           		//	key: 'taskID',
			           		//	label: 'Import ID',
			           		//	value: _colTask,
			           		//	sortValue: function(o) { return o.taskID; },
			           		//},
			           		{
				           		key: 'filename',
				           		label: 'Filename',
				           		value: _colFilename,
				           		sortValue: function(o) { return o.filename; },
			           		},
			           		{
			           			key: 'uname',
			           			label: 'User',
			           			value: _colUser,
			           			sortValue: function(o) { return o.uname; },
			           		},

			           		{
			           			key: 'date',
			           			label: 'Date',
			           			value: _colCreated,
			           			sortValue: _colCreatedSort
			           		},

			           		{
			           			key: 'numberEncounters',
			           			label: 'Encounters',
			           			value: _colNumberEncounters,
			           			sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
			           		},
			           		{
			           			key: 'numberIndividuals',
			           			label: 'Individuals',
			           			value: _colNumberIndividuals,
			           			sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
			           		},
			           		{
			           			key: 'numberImages',
			           			label: 'Images',
			           			value: _colNumberImages,
			           			sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
			           		},
			           		{
			           			key: 'status',
			           			label: 'Import Status',
			           			value: _colStatus,
			           			sortValue: function(o) { return o.status; },
	
			           		},
			           		{
			           			key: 'iaStatus',
			           			label: 'IA?',
			           			value: _colIA,
			           			sortValue: function(o) { return o.iaStatus; },
			     
			           		}



			];
			
			var howMany = 10;
			var start = 0;
			var results = [];

			var sortCol = 2;
			var sortReverse = true;
			
			
			var sTable = false;
			
			function _colCreated(o) {
				if (!o.created) return '';
				if (o.created === "-1") return '';
				return o.created;
			}

			function _colCreatedSort(o) {
				var m = o.created;
				if (!m) return '';
				//var d = wildbook.parseDate(m);
				//if (!wildbook.isValidDate(d)) return '';
				//return d.getTime();
				return m;
			}

			
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
					var r = '<tr onClick="return rowClick(this);" onAuxClick="return rowClick(this);" class="clickable pageableTable-visible">';
					for (var c = 0 ; c < colDefn.length ; c++) {
						r += '<td class="ptcol-' + colDefn[c].key + '"></td>';
					}
					r += '</tr>';
					$('#results-table').append(r);
				}

				sTable.initSort();
				sTable.initValues();


				newSlice(sortCol, sortReverse);

				$('#progress').hide();
				sTable.sliderInit();
				show();

				$('#results-table').on('wheel', function(ev) {  //firefox? DOMMouseScroll
					if (!sTable.opts.sliderElement) return;
					ev.preventDefault();
					var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
					if (delta != 0) nudge(-delta);
				});
				

			} //end doTable
			
			function rowClick(el) {
				console.log(el);
				var target = (event.metaKey && event.button == 0) || (event.button == 1) ? '_blank' : '_self';
				var w = window.open('react/bulk-import-task?id=' + el.getAttribute('data-id'), target);
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

		      var title = 'Individual ' + searchResults[results[i]].id;
		  		
		  			$($('#results-table tbody tr')[i]).removeClass('collab-private');
		  		
		  		$('#results-table tbody tr')[i].title = title;
					//$('#results-table tbody tr')[i].title = 'Encounter ' + searchResults[results[i]].id;
					$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].taskID);
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
			}
			
			function _colTask(o) {
				if (o.taskID == undefined) return '';
				return o.taskID;
			}
			function _colFilename(o) {
				if (o.filename == undefined) return '';
				return o.filename.replaceAll("[","").replaceAll("]","").replaceAll('"','');
			}
			
			function _colUser(o) {
				if (o.uname == undefined) return '';
				return o.uname;
			}
			
			function _colDate(o) {
				if (o.created == undefined) return '';
				return o.created;
			}
			
			function _colNumberEncounters(o) {
				if (o.numEncs == undefined) return '';
				return o.numEncs;
			}
			
			function _colNumberIndividuals(o) {
				if (o.indivCount == undefined) return '';
				return o.indivCount;
			}
			
			function _colNumberImages(o) {
				if (o.numMediaAssets == undefined) return '';
				return o.numMediaAssets;
			}
			
			function _colIA(o) {
				if (o.iaStatus == undefined) return '';
				return o.iaStatus;
			}
			
			function _colStatus(o) {
				if (o.status == undefined) return '';
				return o.status;
			}
			

			
		</script>
		
		<p class="table-filter-text">
			<input placeholder="Filter by text" id="filter-text" onChange="return applyFilter()" />
			<input type="button" value="Filter" />
			<input type="button" value="Clear" onClick="$('#filter-text').val(''); applyFilter(); return true;" />
			<span style="margin-left: 40px; color: #888; font-size: 0.8em;" id="table-info"></span>
		</p>

		<div class="pageableTable-wrapper">
			<div id="progress">loading...</div>
			<table id="results-table"></table>
			<div id="results-slider"></div>
		</div>

<%
}
catch(Exception n){
	n.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
}
%>



    	
    </div>




<jsp:include page="footer.jsp" flush="true"/>



