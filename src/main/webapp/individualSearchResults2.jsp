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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties, java.util.Collection, java.util.Vector,java.util.ArrayList, org.json.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


<html>
<head>
  <%

  String context="context0";
  context=ServletUtilities.getContext(request);
  
    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResults.properties"));
    props = ShepherdProperties.getProperties("individualSearchResults.properties", langCode,context);


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



    int numResults = 0;


    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    myShepherd.beginDBTransaction();
    String order ="";

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = result.getResult();


    if (rIndividuals.size() < listNum) {
      listNum = rIndividuals.size();
    }
  %>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

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
<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

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


  <li><a class="active"><%=props.getProperty("table")%>
  </a></li>
  <%
  String queryString="";
  if(request.getQueryString()!=null){queryString=("?"+request.getQueryString());}
  %>
  <li><a href="individualThumbnailSearchResults.jsp<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("matchingImages")%>
  </a></li>
   <li><a href="individualMappedSearchResults.jsp<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("mappedResults")%>
  </a></li>
  <li><a href="individualSearchResultsAnalysis.jsp<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("analysis")%>
  </a></li>
    <li><a href="individualSearchResultsExport.jsp<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("export")%>
  </a></li>

</ul>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <br/>

      <h1 class="intro"><span class="para"><img src="images/wild-me-logo-only-100-100.png" width="35"
                                                align="absmiddle"/>
        <%=props.getProperty("title")%>
      </h1>

      <p><%=props.getProperty("instructions")%>
      </p>
    </td>
  </tr>
</table>



  <%

    //set up the statistics counters
    

    Vector histories = new Vector();
    int rIndividualsSize=rIndividuals.size();
    
    int count = 0;
    int numNewlyMarked = 0;
	String extra = "var extra = {";

    for (int f = 0; f < rIndividualsSize; f++) {
     
      count++;

      /*
      //check if this individual was newly marked in this period
      Encounter[] dateSortedEncs = indie.getDateSortedEncounters();
      int sortedLength = dateSortedEncs.length - 1;
      Encounter temp = dateSortedEncs[sortedLength];


      if ((temp.getYear() == year1) && (temp.getYear() < year2) && (temp.getMonth() >= month1)) {
        numNewlyMarked++;
      } else if ((temp.getYear() > year1) && (temp.getYear() == year2) && (temp.getMonth() <= month2)) {
        numNewlyMarked++;
      } else if ((temp.getYear() >= year1) && (temp.getYear() <= year2) && (temp.getMonth() >= month1) && (temp.getMonth() <= month2)) {
        numNewlyMarked++;
      }
      */



      if (true) {
        
        MarkedIndividual indie = (MarkedIndividual) rIndividuals.get(f);
        //check if this individual was newly marked in this period
	String thumbUrl = "";
	Encounter[] dateSortedEncs = indie.getDateSortedEncounters();
	int sortedLength = dateSortedEncs.length - 1;
	Encounter temp = dateSortedEncs[sortedLength];
	ArrayList<SinglePhotoVideo> photos=indie.getAllSinglePhotoVideo();
	if (photos.size() > 0) {
		SinglePhotoVideo t = photos.get(0);
		thumbUrl = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/" + temp.subdir() + "/thumb.jpg";
	}

	String firstIdent = "";
	if (temp.getYear() > 0) firstIdent = temp.getMonth() + "/" + temp.getYear();

	extra += "'" + indie.getIndividualID() + "': { locations: " + indie.participatesInTheseLocationIDs().size() + ", genusSpecies: '" + indie.getGenusSpecies() + "', thumbUrl: '" + thumbUrl + "', numberEncounters: " + indie.totalEncounters() + ", firstIdent: '" + firstIdent + "' },\n";

      } //end if to control number displayed



    } //end for


/////START


	JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
	JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)rIndividuals, jdopm.getExecutionContext());
	String indsJson = jsonobj.toString();

	extra += "};";
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
<%=extra%>

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
var resultsTable;

$(document).ready( function() {
	wildbook.init(function() { doTable(); });
});


var tableContents = document.createDocumentFragment();

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

	inds = new wildbook.Collection.MarkedIndividuals();
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
		inds.add(new wildbook.Model.MarkedIndividual(o));
	});
	$('#progress').remove();
	resultsTable.tableShow();


}


function _colIndividual(o) {
	//var i = '<b><a target="_new" href="individuals.jsp?number=' + o.id + '">' + o.id + '</a></b> ';
	var i = '<b>' + o.id + '</b> ';
	if (!extra[o.id]) return i;
	i += (extra[o.id].firstIdent || '') + ' <i>';
	i += (extra[o.id].genusSpecies || '') + '</i>';
	return i;
}


function _colNumberEncounters(o) {
	if (!extra[o.id]) return '';
	var n = extra[o.id].numberEncounters;
	if (n == undefined) return '';
	return n;
}

/*
function _colYearsBetween(o) {
	return o.maxYearsBetweenResightings;
}
*/

function _colNumberLocations(o) {
	if (!extra[o.id]) return '';
	var n = extra[o.id].locations;
	if (n == undefined) return '';
	return n;
}


function _colTaxonomy(o) {
	if (!o.get('genus') || !o.get('specificEpithet')) return 'n/a';
	return o.get('genus') + ' ' + o.get('specificEpithet');
}


function _colRowNum(o) {
	return o._rowNum;
}


function _colThumb(o) {
	if (!extra[o.id]) return '';
	var url = extra[o.id].thumbUrl;
	if (!url) return '';
	return '<div style="background-image: url(' + url + ');"><img src="' + url + '" /></div>';
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
      <p><strong><%=props.getProperty("matchingMarkedIndividuals")%>
      </strong>: <%=count%>
      </p>
      <%myShepherd.beginDBTransaction();%>
      <p><strong><%=props.getProperty("totalMarkedIndividuals")%>
      </strong>: <%=(myShepherd.getNumMarkedIndividuals())%>
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



<p></p>
<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


