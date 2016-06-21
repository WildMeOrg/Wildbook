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
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties, java.util.Collection, java.util.Map, java.util.HashMap,java.util.Vector,java.util.ArrayList, org.datanucleus.api.rest.orgjson.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager" %>
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
    StringBuffer prettyPrint=new StringBuffer();
    Map<String,Object> paramMap = new HashMap<String, Object>();
    String queryString = IndividualQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);


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


    
  
  %>
  
  <script>
      var jdoql = '<%= queryString.replaceAll("'", "\\\\'") %>';
  </script>
  
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
        
<link rel="stylesheet" href="graph_files/forced_graph.css">
<link rel="stylesheet" href="http://code.jquery.com/ui/1.11.1/themes/smoothness/jquery-ui.css">
<link href="http://fonts.googleapis.com/css?family=Crimson+Text" rel="stylesheet" type="text/css">
<link href="http://fonts.googleapis.com/css?family=Allerta" rel="stylesheet" type="text/css">
<script src="http://code.jquery.com/jquery-1.10.2.js"></script>
<script src="http://code.jquery.com/ui/1.11.1/jquery-ui.js"></script>
<script src="http://d3js.org/d3.v3.min.js" charset="utf-8"></script>
<script src="graph_files/configuration.js" charset="utf-8"></script>
        
        

</head>
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
<body>
<div id="wrapper">
<div id="page">




<div id="main">

<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <br/>

      <h1 class="intro">
        <%=props.getProperty("title")%>
      </h1>

    </td>
  </tr>
</table>

<ul id="tabmenu">


  <li><a class="active"><%=props.getProperty("table")%>
  </a></li>

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


      <p><%=props.getProperty("instructions")%>
      </p>
    </td>
  </tr>
</table>


<div id="display_div">
	<!-- makes the data pop-up display in the top right corner -->
	<div id="force_fluke_right" class="noselect">
		<!-- the content of this dis is generated via javascript and is based on data from the flukebook data -->
		<div id="fluke_data_display"></div>
		<div id="launch_config" style="display:none">
			<div style="padding:5px;">Reconfigure</div>
		</div>
	</div>
	<!-- allows users to configure what the graph displays -->
	<div id="configuration" class="noselect" >
		<button id="configuration_close" style="margin-left:110px; margin-top:30px;">Load Configuration</button>
	</div>
	<svg id="display_svg"></svg>
</div>
<script src="graph_files/graph.js"></script>
<script src="graph_files/displayFlukeData.js"></script>
<script type="text/javascript" >
var config = $('#configuration');
/**
	Creates a new header jQuery object, also setting 
	the header. It's a separate function to clean up the 
	loops below.
*/
globals.make_header = function(header_name){
	var new_header = $("<span>");
	new_header.attr("id", header_name.toLowerCase());
	// add the display text
	var header_label = $('<div class="config_header">');
	// we're assuming the header name is appropriately capitalized
	header_label.append(header_name.replace("_", " ") + " Attributes");
	// put the header text on the header object and return to 
	new_header.append(header_label);

	return new_header;
}

/**
	Create a new attribute jquery object 
	and sets the name value. Does not create the 
	option list directly, only the shell
*/
globals.make_row = function(config_label){
	return $('<div class="config_row">'+
				'<div class="config_cell config_label">' + config_label.replace("_", " ") + '</div>' +
					'<div class="config_cell config_dropdown">' +
						'<select name="' + config_label +'" ></select>' +
					'</div>' +
				'</div>' );
}

// set up the options using the defined functions in the configuration file
for (var span_key in attribute_data_mappings){
	// create the attribute associate to the span key
	var new_header = globals.make_header(span_key);
	
	// create a row for each attribute key and generate the option lists
	var graph_attribute = attribute_data_mappings[span_key];
	for (var attribute_key in graph_attribute){
		// make the row object 
		var attribute_row = globals.make_row(attribute_key);
		var select = attribute_row.find('select[name='+attribute_key+']');
		for (var data_attributes in attribute_data_mappings[span_key][attribute_key]){
			$(select).append($('<option>').text(data_attributes));
		}
		// finally add the new attribute row back to the header div 
		new_header.append(attribute_row);
	}
	
	// now randomly select an option
	/*new_header.find('select').each(function(i, s){
		var names = $(s).find('option').map(function(i,a){ return $(a).html() })
		$(s).val(names[Math.floor(Math.random() * names.length)] );
	});*/
	
	// append the new header to the configuration object 
	config.prepend(new_header);
}

// set parent for the config div centering 
if (parent)
	parent = config.parent();
else
	parent = window;
	
// center the configuration div (only needs to run once) 
config.css({
	"position": "absolute",
	"top": ((($(parent).height() - 100 - config.outerHeight()) / 2) + $(parent).scrollTop() + "px"),
	"left": ((($(parent).width() - config.outerWidth()) / 2) + $(parent).scrollLeft() + "px")
});

// Add the close action 
$('#configuration_close').button().click(function(){
	// hide the div
	config.hide();
	// show the edit div in the right/left corner
	var launch = $('#launch_config');
	launch.show();
	launch.click(function(){
		// hide the reconfig
		launch.hide();
		// show the configuration div
		config.show();
		// destroy the graph (in graph.js)
		transitionAndRemove();  
	});
	// do some data load/filter stuff
	var attribute_functions = getAttributeFunctions();
	run(attribute_functions,jdoql);
});

// This function gets all of the selected settings from the configuration div and returns them in an object
function getAttributeFunctions(){
	// get all of the functions based on the configuration, allowing the graph generation to be cleaner
	var attribute_functions = {};
	for (var name in attribute_data_mappings){
		attribute_functions[name] = {};
		// get all of the associated functions for the node attribute and data selected
		$.each( $('#'+name.toLowerCase()).find("select"), function(i,select){
			attribute_functions[name][select.name] = attribute_data_mappings[name][select.name][$(select).val()]
		});
	}

	return attribute_functions;
}
</script>



<p></p>

</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


