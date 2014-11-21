<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.io.File, java.io.FileOutputStream, java.io.OutputStreamWriter, java.util.*, org.json.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager " %>


<html>
<head>


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

  int numResults = 0;


  Vector rEncounters = new Vector();

  myShepherd.beginDBTransaction();

  EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
  rEncounters = queryResult.getResult();


//--let's estimate the number of results that might be unique

  int numUniqueEncounters = 0;
  int numUnidentifiedEncounters = 0;
  int numDuplicateEncounters = 0;
  ArrayList uniqueEncounters = new ArrayList();
  for (int q = 0; q < rEncounters.size(); q++) {
    Encounter rEnc = (Encounter) rEncounters.get(q);
    if ((rEnc.getIndividualID()!=null)&&(!rEnc.getIndividualID().equals("Unassigned"))) {
      String assemblage = rEnc.getIndividualID() + ":" + rEnc.getYear() + ":" + rEnc.getMonth() + ":" + rEnc.getDay();
      if (!uniqueEncounters.contains(assemblage)) {
        numUniqueEncounters++;
        uniqueEncounters.add(assemblage);
      } else {
        numDuplicateEncounters++;
      }
    } else {
      numUnidentifiedEncounters++;
    }

  }

//--end unique counting------------------------------------------

%>
<title><%=CommonConfiguration.getHTMLTitle(context)%>
</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta name="Description"
      content="<%=CommonConfiguration.getHTMLDescription(context)%>"/>
<meta name="Keywords"
      content="<%=CommonConfiguration.getHTMLKeywords(context)%>"/>
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context)%>"/>
<link href="<%=CommonConfiguration.getCSSURLLocation(request,context)%>"
      rel="stylesheet" type="text/css"/>
<link rel="shortcut icon"
      href="<%=CommonConfiguration.getHTMLShortcutIcon(context)%>"/>
</head>

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 2px solid black;
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
    color: #DEDECF;
    background: #000;
    font: bold 1em "Trebuchet MS", Arial, sans-serif;
    border: 2px solid black;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #FFFFFF;
    color: #000000;
    border-bottom: 2px solid #FFFFFF;
  }

  #tabmenu a:hover {
    color: #ffffff;
    background: #7484ad;
  }

  #tabmenu a:visited {
    color: #E8E9BE;
  }

  #tabmenu a.active:hover {
    background: #7484ad;
    color: #DEDECF;
    border-bottom: 2px solid #000000;
  }
</style>


<body onload="initialize()" onunload="GUnload()">
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>


<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">

<script src="../javascript/tablesorter/jquery.tablesorter.js"></script>

<script src="../javascript/underscore-min.js"></script>
<script src="../javascript/backbone-min.js"></script>
<script src="../javascript/core.js"></script>
<script src="../javascript/classes/Base.js"></script>

<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

<link rel="stylesheet" href="../css/pageableTable.css" />
<script src="../javascript/pageableTable.js"></script>



<div id="main">

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


<table width="810px" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <p>

      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>
      </p>    <p><%=encprops.getProperty("belowMatches")%>
    </p>
    </td>
  </tr>
</table>


<script type="text/javascript">

/*



    <strong><%=encprops.getProperty("markedIndividual")%>
    <strong><%=encprops.getProperty("number")%>
  if (CommonConfiguration.showProperty("showTaxonomy",context)) {
    <strong><%=encprops.getProperty("taxonomy")%>
    <strong><%=encprops.getProperty("submitterName")%>
    <strong><%=encprops.getProperty("date")%>
    <strong><%=encprops.getProperty("location")%>
    <strong><%=encprops.getProperty("locationID")%>
    <strong><%=encprops.getProperty("occurrenceID")%>
*/

<%
	JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
	String encsJson = "";
	if (rEncounters instanceof Collection) {
		JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)rEncounters, jdopm.getExecutionContext());
		//JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)rEncounters, ((JDOPersistenceManager)pm).getExecutionContext());
		encsJson = jsonobj.toString();
	} else {
		JSONObject jsonobj = RESTUtils.getJSONObjectFromPOJO(rEncounters, jdopm.getExecutionContext());
		encsJson = jsonobj.toString();
	}

%>

var searchResults = <%=encsJson%>;

var testColumns = {
	rowNum: { label: '#', val: _colRowNum },
	thumb: { label: 'Thumb', val: _colThumb },
	//catalogNumber: { label: 'Number' },
	//dataTypes: { label: 'Data types', val: dataTypes },
	individualID: { label: 'ID', val: _colIndLink },
	taxonomy: { label: 'Taxonomy', val: _colTaxonomy },
	submitterID: { label: 'Submitter' },
	//sex: { label: 'Sex', val: cleanValue },
	date: { label: 'Date', val: _colEncDate },
	modified: { label: 'Edit Date', val: _colModified },
	verbatimLocality: { label: 'Location' },
	locationID: { label: 'Location ID' },
	//occurrenceID: { label: 'Occurrence ID' },
};

var encs;
var resultsTable;

$(document).ready( function() {
	wildbook.init(function() { doTable(); });
});



function doTable() {
	resultsTable = new pageableTable({
		columns: testColumns,
		tableElement: $('#results-table'),
		sliderElement: $('#results-slider'),
		tablesorterOpts: {
			headers: { 1: {sorter: false} },
			textExtraction: _textExtraction,
		},
	});

	resultsTable.tableInit();

	encs = new wildbook.Collection.Encounters();
	encs.on('add', function(o) {
		var row = resultsTable.tableAddRow(o);
		row.click(function() { window.location.href = 'encounter.jsp?number=' + row.data('id'); });
		row.addClass('clickable');
for (var i = 0 ; i < 5 ; i++) {
		row = resultsTable.tableAddRow(o);
		row.click(function() { window.location.href = 'encounter.jsp?number=' + row.data('id'); });
		row.addClass('clickable');
}

	});

	_.each(searchResults, function(o) {
console.log(o);
		encs.add(new wildbook.Model.Encounter(o));
	});
	$('#table-status').remove();
	resultsTable.tableShow();

/*
	encs.fetch({
		//fields: { individualID: 'newMatch' },
		success: function() {
			$('#table-status').remove();
			resultsTable.tableShow();
		}
	});
*/

}


function _colIndLink(o) {
	var iid = o.get('individualID');
	if (!iid || (iid == 'Unknown') || (iid == 'Unassigned')) return 'Unassigned';

	return '<a title="Individual ID: ' + iid + '" href="../individuals.jsp?number=' + iid + '">' + iid + '</a>';
}


function _colEncDate(o) {
	var d = o.date();
	if (!d) return '';
	return d.toLocaleDateString();
}

function _colTaxonomy(o) {
	if (!o.get('genus') || !o.get('specificEpithet')) return 'n/a';
	return o.get('genus') + ' ' + o.get('specificEpithet');
}


function _colRowNum(o) {
	return o._rowNum;
}


function _colThumb(o) {
	var url = o.thumbUrl();
	if (!url) return '';
	return '<div style="background-image: url(' + url + ');"><img src="' + url + '" /></div>';
	return '<div style="background-image: url(' + url + ');"></div>';
	return '<img src="' + url + '" />';
}


function _colModified(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = new Date(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}


function _textExtraction(n) {
	var s = $(n).text();
	var skip = new RegExp('^(none|unassigned|)$', 'i');
	if (skip.test(s)) return 'zzzzz';
	return s;
}

</script>

<div class="pageableTable-wrapper">
	<div style="padding: 30px; background-color: #CCC; text-align: center;" id="table-status">loading...</div>
	<table id="results-table"></table>
	<div id="results-slider"></div>
</div>


<p>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td align="left">
      <p><strong><%=encprops.getProperty("matchingEncounters")%>
      </strong>: <%=numResults%>
        <%
          if (request.getUserPrincipal()!=null) {
        %>
        <br/>
        <%=numUniqueEncounters%> <%=encprops.getProperty("identifiedUnique")%><br/>
        <%=numUnidentifiedEncounters%> <%=encprops.getProperty("unidentified")%><br/>
        <%=(numDuplicateEncounters)%> <%=encprops.getProperty("dailyDuplicates")%>
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
    <%
      myShepherd.rollbackDBTransaction();
    %>
  </tr>
</table>

<table>
  <tr>
    <td align="left">

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>


</p>
<br>

<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
  rEncounters = null;

%>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>




