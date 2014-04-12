<%--gen
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2012 Jason Holmberg
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
         import="org.ecocean.servlet.ServletUtilities,org.springframework.mock.web.MockHttpServletRequest,org.ecocean.*,javax.jdo.Extent, javax.jdo.Query, java.util.ArrayList, com.reijns.I3S.Point2D" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Properties" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>         

<html xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml">
<%
String context="context0";
context=ServletUtilities.getContext(request);
%>
<head>
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

  <!-- Sliding div content: STEP1 Place inside the head section -->
  <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js"></script>
  <script type="text/javascript" src="../javascript/animatedcollapse.js"></script>
  
  <!-- /STEP1 Place inside the head section -->
  <!-- STEP2 Place inside the head section -->
  <script type="text/javascript">
    animatedcollapse.addDiv('location', 'fade=1')
    animatedcollapse.addDiv('map', 'fade=1')
    animatedcollapse.addDiv('date', 'fade=1')
    animatedcollapse.addDiv('observation', 'fade=1')
    animatedcollapse.addDiv('tags', 'fade=1')
    animatedcollapse.addDiv('identity', 'fade=1')
    animatedcollapse.addDiv('metadata', 'fade=1')
    animatedcollapse.addDiv('export', 'fade=1')
    animatedcollapse.addDiv('genetics', 'fade=1')

    animatedcollapse.ontoggle = function($, divobj, state) { //fires each time a DIV is expanded/contracted
      //$: Access to jQuery
      //divobj: DOM reference to DIV being expanded/ collapsed. Use "divobj.id" to get its ID
      //state: "block" or "none", depending on state
    }
    animatedcollapse.init()
  </script>
  <!-- /STEP2 Place inside the head section -->

<script src="http://maps.google.com/maps/api/js?sensor=false"></script>
<script src="visual_files/keydragzoom.js" type="text/javascript"></script>
<script type="text/javascript" src="http://geoxml3.googlecode.com/svn/branches/polys/geoxml3.js"></script>
<script type="text/javascript" src="http://geoxml3.googlecode.com/svn/trunk/ProjectedOverlay.js"></script>

</head>

<style type="text/css">v\:* {
  behavior: url(#default#VML);
  
}</style>

<style type="text/css">
.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;
</style>

<script>
  function resetMap() {
    var ne_lat_element = document.getElementById('ne_lat');
    var ne_long_element = document.getElementById('ne_long');
    var sw_lat_element = document.getElementById('sw_lat');
    var sw_long_element = document.getElementById('sw_long');

    ne_lat_element.value = "";
    ne_long_element.value = "";
    sw_lat_element.value = "";
    sw_long_element.value = "";
    selectedRectangle.setVisible(false);

  }
</script>

<body onload="resetMap()" onunload="resetMap()">

<%
  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);
  int firstYear = 1980;

  Shepherd myShepherd = new Shepherd(context);
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
  myShepherd.beginDBTransaction();
  try {
    firstYear = myShepherd.getEarliestSightingYear();
    nowYear = myShepherd.getLastSightingYear();
  } catch (Exception e) {
    e.printStackTrace();
  }

//let's load encounterSearch.properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/searchComparison.properties"));
  encprops=ShepherdProperties.getProperties("searchComparison.properties", langCode);

  
  //let's determine if this is location 1 or 2
  
  String part="1";
  if(request.getParameter("day1")!=null){
	
	  //this is then part 2
	  part="2";
	  
	  MockHttpServletRequest mrew=new MockHttpServletRequest(getServletContext(), request.getRequestURI(), request.getMethod());
	  //mrew.setRequestURI(request.getRequestURI());
	  mrew.setQueryString(request.getQueryString());
	  mrew.setParameters(request.getParameterMap());
	  
	  //we also need to store the part 1 parameter
	  session.setAttribute("locationSearch1", mrew);
  
  }
  
%>


<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">
<table width="810">
<tr>
<td>
<p>

<h1 class="intro"><%=encprops.getProperty("title")%> (<%=encprops.getProperty("part") %> <%=part %>)
  <a href="<%=CommonConfiguration.getWikiLocation(context)%>searching#encounter_search" target="_blank">
    <img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/>
  </a>
</h1>
</p>
<p><em><%=encprops.getProperty("instructions")%>
</em></p>
<%
String gotoPage="searchComparisonResults.jsp";
if(request.getParameter("day1")==null){gotoPage="searchComparison.jsp";}
%>
<form action="<%=gotoPage %>" method="get" name="search" id="search">

<p>Name this search: <input name="searchNameField" type="text" size="60" /></p>

<table>

<tr>
  <td width="810px">

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/></a>
      <a href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><font
        color="#000000">Location filter (map)</font></a></h4>


    
<script type="text/javascript">
//alert("Prepping map functions.");
var center = new google.maps.LatLng(0, 0);

var map;

var markers = [];
var overlays = [];


var selectedRectangle;

var overlaysSet=false;
 
var geoXml = null;
var geoXmlDoc = null;
var kml = null;
var filename="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?encounterSearchUse=true&barebones=true";
 

  function initialize() {
	//alert("initializing map!");
	//overlaysSet=false;
	var mapZoom = 1;
	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}

	  map = new google.maps.Map(document.getElementById('map_canvas'), {
		  zoom: mapZoom,
		  center: center,
		  mapTypeId: google.maps.MapTypeId.HYBRID
		});

	  //adding the fullscreen control to exit fullscreen
	  var fsControlDiv = document.createElement('DIV');
	  var fsControl = new FSControl(fsControlDiv, map);
	  fsControlDiv.index = 1;
	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

	//create the selection response rectangle
	  selectedRectangle = new google.maps.Rectangle({
	  	map: map,
	  	visible: false,
	      strokeColor: "#0000FF",
	      fillColor: "#0000FF"
	  });


   map.enableKeyDragZoom({
          visualEnabled: true,
          visualPosition: google.maps.ControlPosition.LEFT,
          visualPositionOffset: new google.maps.Size(35, 0),
          visualPositionIndex: null,
          visualSprite: "http://maps.gstatic.com/mapfiles/ftr/controls/dragzoom_btn.png",
          visualSize: new google.maps.Size(20, 20),
          visualTips: {
            off: "Turn on",
            on: "Turn off"
          }
        });


        var dz = map.getDragZoomObject();
        google.maps.event.addListener(dz, 'dragend', function (bnds) {
          var ne_lat_element = document.getElementById('ne_lat');
          var ne_long_element = document.getElementById('ne_long');
          var sw_lat_element = document.getElementById('sw_lat');
          var sw_long_element = document.getElementById('sw_long');

          ne_lat_element.value = bnds.getNorthEast().lat();
          ne_long_element.value = bnds.getNorthEast().lng();
          sw_lat_element.value = bnds.getSouthWest().lat();
          sw_long_element.value = bnds.getSouthWest().lng();
          
      	  var neRecBound=new google.maps.LatLng(bnds.getNorthEast().lat(),bnds.getNorthEast().lng());
    	  var swRecBound=new google.maps.LatLng(bnds.getSouthWest().lat(),bnds.getSouthWest().lng());
    	  var selectedBounds = new google.maps.LatLngBounds(
    		swRecBound,
    		neRecBound
    	  );
    	  selectedRectangle.setBounds(selectedBounds);
          selectedRectangle.setVisible(true);
          
        });
        
		<%
        //set the previous maps search box if set
        if((request.getParameter("ne_lat")!=null)&&(request.getParameter("ne_long")!=null)&&(request.getParameter("sw_lat")!=null)&&(request.getParameter("ne_long")!=null)&&(!request.getParameter("ne_lat").trim().equals("")) &&(!request.getParameter("ne_long").trim().equals("")) && (!request.getParameter("sw_lat").trim().equals("")) &&(!request.getParameter("sw_long").trim().equals(""))){
        %>    
        	
        	//create the coordinates
        	var neCoord=new google.maps.LatLng(<%=request.getParameter("ne_lat")%>,<%=request.getParameter("ne_long")%>);
        	var swCoord=new google.maps.LatLng(<%=request.getParameter("sw_lat")%>,<%=request.getParameter("sw_long")%>);
        	//alert("neCoord: "+neCoord.lat()+","+neCoord.long());
        	//alert("swCoord: "+swCoord.lat()+","+swCoord.long());
        	//add the previous bounding box
        	var search1Bounds = new google.maps.LatLngBounds(
        		swCoord,
        		neCoord
        	);

        	//create the rectangle
        	var search1Rectangle = new google.maps.Rectangle({
        		bounds:search1Bounds,
        		map: map,
        	    strokeColor: "#ff0000",
        	    fillColor: "#ff0000"
        	});
        	
 
        	
        <%    
        }		
		%>

          
 }
  
 
  function setOverlays() {
	  //alert("In setOverlays!");
	  if(!overlaysSet){
		//read in the KML
		 geoXml = new geoXML3.parser({
                    map: map,
                    markerOptions: {flat:true,clickable:false},

         });

		
	
        geoXml.parse(filename);
        
    	var iw = new google.maps.InfoWindow({
    		content:'Loading and rendering map data...',
    		position:center});
         
    	iw.open(map);
    	
    	google.maps.event.addListener(map, 'center_changed', function(){iw.close();});
         
         
         
		  overlaysSet=true;
      }
	    
   }
 
//not using this function right now. kept because it might be useful later  
function useData(doc){	
	geoXmlDoc = doc;
	kml = geoXmlDoc[0];
    if (kml.markers) {
	 for (var i = 0; i < kml.markers.length; i++) {
	     //if(i==0){alert(kml.markers[i].getVisible());
	 }
   } 
}

function fullScreen(){
	$("#map_canvas").addClass('full_screen_map');
	$('html, body').animate({scrollTop:0}, 'slow');
	initialize();
	
	//hide header
	$("#header_menu").hide();
	
	if(overlaysSet){overlaysSet=false;setOverlays();}
	//alert("Trying to execute fullscreen!");
}


function exitFullScreen() {
	$("#header_menu").show();
	$("#map_canvas").removeClass('full_screen_map');

	initialize();
	if(overlaysSet){overlaysSet=false;setOverlays();}
	//alert("Trying to execute exitFullScreen!");
}


//making the exit fullscreen button
function FSControl(controlDiv, map) {

  // Set CSS styles for the DIV containing the control
  // Setting padding to 5 px will offset the control
  // from the edge of the map
  controlDiv.style.padding = '5px';

  // Set CSS for the control border
  var controlUI = document.createElement('DIV');
  controlUI.style.backgroundColor = '#f8f8f8';
  controlUI.style.borderStyle = 'solid';
  controlUI.style.borderWidth = '1px';
  controlUI.style.borderColor = '#a9bbdf';;
  controlUI.style.boxShadow = '0 1px 3px rgba(0,0,0,0.5)';
  controlUI.style.cursor = 'pointer';
  controlUI.style.textAlign = 'center';
  controlUI.title = 'Toggle the fullscreen mode';
  controlDiv.appendChild(controlUI);

  // Set CSS for the control interior
  var controlText = document.createElement('DIV');
  controlText.style.fontSize = '12px';
  controlText.style.fontWeight = 'bold';
  controlText.style.color = '#000000';
  controlText.style.paddingLeft = '4px';
  controlText.style.paddingRight = '4px';
  controlText.style.paddingTop = '3px';
  controlText.style.paddingBottom = '2px';
  controlUI.appendChild(controlText);
  //toggle the text of the button
   if($("#map_canvas").hasClass("full_screen_map")){
      controlText.innerHTML = 'Exit Fullscreen';
    } else {
      controlText.innerHTML = 'Fullscreen';
    }

  // Setup the click event listeners: toggle the full screen

  google.maps.event.addDomListener(controlUI, 'click', function() {

   if($("#map_canvas").hasClass("full_screen_map")){
    exitFullScreen();
    } else {
    fullScreen();
    }
  });

}


  google.maps.event.addDomListener(window, 'load', initialize);
  
  
    </script>

    <div id="map">
      <p>Use the arrow and +/- keys to navigate to a portion of the globe of interest, then click
        and drag the <img src="../javascript/zoomin.gif" align="absmiddle"/> icon to select the
        specific search boundaries. You can also use the text boxes below the map to specify exact
        boundaries.</p>
        <%
        if(request.getParameter("year1")!=null){
        %>
        <p>The red box below indicates the geographic region of your first comparison selection, if specified.</p>
		<%
        }
		%>
      <div id="map_canvas" style="width: 770px; height: 510px; "></div>
      
      <div id="map_overlay_buttons">
 
          <input type="button" value="Load Markers" onclick="setOverlays();" />&nbsp;
 

      </div>
      <p>Northeast corner latitude: <input type="text" id="ne_lat" name="ne_lat"></input> longitude:
        <input type="text" id="ne_long" name="ne_long"></input><br/><br/>
        Southwest corner latitude: <input type="text" id="sw_lat" name="sw_lat"></input> longitude:
        <input type="text" id="sw_long" name="sw_long"></input></p>
    </div>

  </td>
</tr>
<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('location')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Location filters (text)</font></a></h4>

    <div id="location" style="display:none; ">
      <p>Use the fields below to filter the search by a location string (e.g. "Mexico") or to a
        specific, pre-defined location identifier.</p>

      <p><strong><%=encprops.getProperty("locationNameContains")%>:</strong>
        <input name="locationField" type="text" size="60"> <br />
        <em><%=encprops.getProperty("leaveBlank")%>
        </em>
      </p>

      <p><strong><%=encprops.getProperty("locationID")%>:</strong> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation(context)%>locationID"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span> <br />
                             
                              <input name="andLocationIDs" type="checkbox" id="andLocationIDs" value="andLocationIDs" /> <%=encprops.getProperty("andLocationID")%>
                             
                             <br />
                             
        (<em><%=encprops.getProperty("locationIDExample")%>
        </em>)</p>

      <%
        ArrayList<String> locIDs = myShepherd.getAllLocationIDs();
        int totalLocIDs = locIDs.size();

        if (totalLocIDs >= 1) {
      %>

      <select multiple size="<%=(totalLocIDs+1) %>" name="locationCodeField" id="locationCodeField">
        <option value="None"></option>
        <%
          for (int n = 0; n < totalLocIDs; n++) {
            String word = locIDs.get(n);
            if (!word.equals("")&&(!word.equals("None"))) {
        %>
        <option value="<%=word%>"><%=word%></option>
        <%
            }
          }
        %>
      </select>
      <%
      } else {
      %>
      <p><em><%=encprops.getProperty("noLocationIDs")%>
      </em></p>
      <%
        }
      %>
    </div>
  </td>

</tr>


<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('date')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Date filters</font></a></h4>
  </td>
</tr>


<tr>
  <td>
    <div id="date" style="display:none;">
      <p>Use the fields below to limit the timeframe of your search.</p>
      <strong><%=encprops.getProperty("sightingDates")%>:</strong><br/>
      <table width="720">
        <tr>
          <td width="670"><label><em>
            &nbsp;<%=encprops.getProperty("day")%>
          </em> <em> <select name="day1" id="day1">
            <option value="1" selected>1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12">12</option>
            <option value="13">13</option>
            <option value="14">14</option>
            <option value="15">15</option>
            <option value="16">16</option>
            <option value="17">17</option>
            <option value="18">18</option>
            <option value="19">19</option>
            <option value="20">20</option>
            <option value="21">21</option>
            <option value="22">22</option>
            <option value="23">23</option>
            <option value="24">24</option>
            <option value="25">25</option>
            <option value="26">26</option>
            <option value="27">27</option>
            <option value="28">28</option>
            <option value="29">29</option>
            <option value="30">30</option>
            <option value="31">31</option>
          </select> <%=encprops.getProperty("month")%>
          </em> <em> <select name="month1" id="month1">
            <option value="1" selected>1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12">12</option>
          </select> <%=encprops.getProperty("year")%>
          </em> <select name="year1" id="year1">
            <% for (int q = firstYear; q <= nowYear; q++) { %>
            <option value="<%=q%>"

              <%
                if (q == firstYear) {
              %>
                    selected
              <%
                }
              %>
              ><%=q%>
            </option>

            <% } %>
          </select> &nbsp;to <em>&nbsp;<%=encprops.getProperty("day")%>
          </em> <em> <select name="day2"
                             id="day2">
            <option value="1">1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12">12</option>
            <option value="13">13</option>
            <option value="14">14</option>
            <option value="15">15</option>
            <option value="16">16</option>
            <option value="17">17</option>
            <option value="18">18</option>
            <option value="19">19</option>
            <option value="20">20</option>
            <option value="21">21</option>
            <option value="22">22</option>
            <option value="23">23</option>
            <option value="24">24</option>
            <option value="25">25</option>
            <option value="26">26</option>
            <option value="27">27</option>
            <option value="28">28</option>
            <option value="29">29</option>
            <option value="30">30</option>
            <option value="31" selected>31</option>
          </select> <%=encprops.getProperty("month")%>
          </em> <em> <select name="month2" id="month2">
            <option value="1">1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12" selected>12</option>
          </select> <%=encprops.getProperty("year")%>
          </em>
            <select name="year2" id="year2">
              <% for (int q = nowYear; q >= firstYear; q--) { %>
              <option value="<%=q%>"

                <%
                  if (q == nowYear) {
                %>
                      selected
                <%
                  }
                %>
                ><%=q%>
              </option>

              <% } %>
            </select>
          </label></td>
        </tr>
      </table>

      <p><strong><%=encprops.getProperty("verbatimEventDate")%>:</strong> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation(context)%>verbatimEventDate"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span></p>

      <%
        ArrayList<String> vbds = myShepherd.getAllVerbatimEventDates();
        int totalVBDs = vbds.size();


        if (totalVBDs > 1) {
      %>

      <select multiple size="<%=(totalVBDs+1) %>" name="verbatimEventDateField"
              id="verbatimEventDateField">
        <option value="None"></option>
        <%
          for (int f = 0; f < totalVBDs; f++) {
            String word = vbds.get(f);
            if (word != null) {
        %>
        <option value="<%=word%>"><%=word%>
        </option>
        <%

            }

          }
        %>
      </select>
      <%

      } else {
      %>
      <p><em><%=encprops.getProperty("noVBDs")%>
      </em></p>
      <%
        }
      %>
      <%
        pageContext.setAttribute("showReleaseDate", CommonConfiguration.showReleaseDate(context));
      %>
      <c:if test="${showReleaseDate}">
        <p><strong><%= encprops.getProperty("releaseDate") %></strong></p>
        <p>From: <input name="releaseDateFrom"/> to <input name="releaseDateTo"/> <%=encprops.getProperty("releaseDateFormat") %></p>
      </c:if>
    </div>
  </td>
</tr>

<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('observation')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Observation attribute filters</font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="observation" style="display:none; ">
      <p>Use the fields below to filter your search based on observed attributes.</p>

      <p>
      <table align="left">
        <tr>
          <td><strong><%=encprops.getProperty("sex")%>: </strong>
            <label> <input name="male"
                           type="checkbox" id="male" value="male"
                           checked> <%=encprops.getProperty("male")%>
            </label>

            <label> <input name="female"
                           type="checkbox" id="female" value="female" checked>
              <%=encprops.getProperty("female")%>
            </label>
            <label> <input name="unknown"
                           type="checkbox" id="unknown" value="unknown" checked>
              <%=encprops.getProperty("unknown")%>
            </label></td>
        </tr>
        <%
        if(CommonConfiguration.showProperty("showTaxonomy",context)){
        %>
        <tr>
        <td>
         <strong><%=encprops.getProperty("genusSpecies")%></strong>: <select name="genusField" id="genusField">
		<option value=""></option>
				       
				       <%
				       boolean hasMoreTax=true;
				       int taxNum=0;
				       while(hasMoreTax){
				       	  String currentGenuSpecies = "genusSpecies"+taxNum;
				       	  if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){
				       	  	%>
				       	  	 
				       	  	  <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>"><%=CommonConfiguration.getProperty(currentGenuSpecies,context)%></option>
				       	  	<%
				       		taxNum++;
				          }
				          else{
				             hasMoreTax=false;
				          }
				          
				       }
				       %>
				       
				       
			      </select>
        </td>
	</tr>
	<%
	}
	%>

        <tr>
          <td><strong><%=encprops.getProperty("status")%>: </strong><label>
            <input name="alive" type="checkbox" id="alive" value="alive"
                   checked> <%=encprops.getProperty("alive")%>
          </label><label>
            <input name="dead" type="checkbox" id="dead" value="dead"
                   checked> <%=encprops.getProperty("dead")%>
          </label>
          </td>
        </tr>
        


        <tr>
          <td valign="top"><strong><%=encprops.getProperty("behavior")%>:</strong>
            <em> <span class="para">
								<a href="<%=CommonConfiguration.getWikiLocation(context)%>behavior" target="_blank">
                  <img src="../images/information_icon_svg.gif" alt="Help" border="0"
                       align="absmiddle"/>
                </a>
							</span>
            </em><br/>
              <%
				ArrayList<String> behavs = myShepherd.getAllBehaviors();
				int totalBehavs=behavs.size();

				
				if(totalBehavs>1){
				%>

            <select multiple name="behaviorField" id="behaviorField">
              <option value="None"></option>
              <%
                for (int f = 0; f < totalBehavs; f++) {
                  String word = behavs.get(f);
                  if (word != null) {
              %>
              <option value="<%=word%>"><%=word%>
              </option>
              <%

                  }

                }
              %>
            </select>
              <%

				}
				else{
					%>
            <p><em><%=encprops.getProperty("noBehaviors")%>
            </em></p>
              <%
				}
				%>

      </p>
  </td>
</tr>
<%

if(CommonConfiguration.showProperty("showLifestage",context)){

%>
<tr valign="top">
  <td><strong><%=encprops.getProperty("lifeStage")%>:</strong>
  
  <select name="lifeStageField" id="lifeStageField">
  	<option value="None" selected="selected"></option>
  <%
  			       boolean hasMoreStages=true;
  			       int stageNum=0;
  			       
  			       while(hasMoreStages){
  			       	  String currentLifeStage = "lifeStage"+stageNum;
  			       	  if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
  			       	  	%>
  			       	  	 
  			       	  	  <option value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></option>
  			       	  	<%
  			       		stageNum++;
  			          }
  			          else{
  			        	hasMoreStages=false;
  			          }
  			          
			       }
			       if(stageNum==0){%>
			    	   <p><em><%=encprops.getProperty("noStages")%></em></p>
			       <% }
			       
 %>
  </select></td>
</tr>
<%
}
%>

<%
  pageContext.setAttribute("showMeasurement", CommonConfiguration.showMeasurements(context));
%>
<c:if test="${showMeasurement}">
<%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
%>
<tr><td><strong><%=encprops.getProperty("measurements") %></strong></td></tr>
<c:forEach items="${items}" var="item">
<tr valign="top">
<td>${item.label}
<select name="measurement${item.type}(operator)">
  <option value="gt">&gt;</option>
  <option value="lt">&lt;</option>
  <option value="eq">=</option>
</select>
<input name="measurement${item.type}(value)"/>(<c:out value="${item.unitsLabel})"/>
</td>
</tr>
</c:forEach>
</c:if>
      <p><strong><%=encprops.getProperty("hasPhoto")%>: </strong>
            <label> 
            	<input name="hasPhoto" type="checkbox" id="hasPhoto" value="hasPhoto" />
            </label>
      </p>
<%
  int totalKeywords = myShepherd.getNumKeywords();
%>
<tr>
  <td valign="top"><%=encprops.getProperty("hasKeywordPhotos")%><br/>
    <%

      if (totalKeywords > 0) {
    %>

    <select multiple size="<%=(totalKeywords+1) %>" name="keyword" id="keyword">
      <option value="None"></option>
      <%


        Iterator keys = myShepherd.getAllKeywords(kwQuery);
        for (int n = 0; n < totalKeywords; n++) {
          Keyword word = (Keyword) keys.next();
      %>
      <option value="<%=word.getIndexname()%>"><%=word.getReadableName()%>
      </option>
      <%
        }

      %>

    </select>
    <%
    } else {
    %>

    <p><em><%=encprops.getProperty("noKeywords")%>
    </em>

        <%
					
				}
				%>
  </td>
</tr>
<tr>
  <td><strong><%=encprops.getProperty("submitterName")%>:</strong>
    <input name="nameField" type="text" size="60"> <br> <em><%=encprops.getProperty("namesBlank")%>
    </em>
  </td>
</tr>

<tr>
  <td><strong><%=encprops.getProperty("filenameField")%>:</strong>
    <input name="filenameField" type="text" size="60"> <br> <em><%=encprops.getProperty("filenamesBlank")%>
    </em>
  </td>
</tr>

</table>
</p>
</div>
</td>
</tr>

<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('identity')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Identity filters</font></a></h4>
  </td>
</tr>
<tr>
  <td>
    <div id="identity" style="display:none; ">
      <p>Use the fields below to limit your search to marked individuals with the following
        properties.</p>
      <input name="resightOnly" type="checkbox" id="resightOnly"
             value="true"> <%=encprops.getProperty("include")%> <select
      name="numResights" id="numResights">
      <option value="1" selected>1</option>
      <option value="2">2</option>
      <option value="3">3</option>
      <option value="4">4</option>
      <option value="5">5</option>
      <option value="6">6</option>
      <option value="7">7</option>
      <option value="8">8</option>
      <option value="9">9</option>
      <option value="10">10</option>
      <option value="11">11</option>
      <option value="12">12</option>
      <option value="13">13</option>
      <option value="14">14</option>
      <option value="15">15</option>
    </select> <%=encprops.getProperty("times")%>

      <p><strong><%=encprops.getProperty("alternateID")%>:</strong> <em> <input
        name="alternateIDField" type="text" id="alternateIDField" size="10"
        maxlength="35"> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation(context)%>alternateID"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" width="15" height="15" border="0"
                             align="absmiddle"/></a></span>
        <br></em></p>
    </div>
  </td>
</tr>

<%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
%>
<c:if test="${showMetalTags or showAcousticTag or showSatelliteTag}">
 <tr>
     <td>
     <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
       href="javascript:animatedcollapse.toggle('tags')" style="text-decoration:none"><img
       src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
       <font color="#000000">Tags</font></a></h4>
     </td>
 </tr>
 <tr>
    <td>
        <div id="tags" style="display:none;">
        <p>Use the fields below to limit your search to specific tags.</p>
        <c:if test="${showMetalTags}">
            <% 
              pageContext.setAttribute("metalTagDescs", Util.findMetalTagDescs(langCode,context)); 
            %>
            <h5>Metal Tags</h5>
            <table>
            <c:forEach items="${metalTagDescs}" var="metalTagDesc">
                <tr>
                    <td><c:out value="${metalTagDesc.locationLabel}:"/></td><td><input name="metalTag(${metalTagDesc.location})"/></td>
                </tr>
            </c:forEach>
            </table>
        </c:if>
        <c:if test="${showAcousticTag}">
          <h5>Acoustic Tag</h5>
          <table>
          <tr><td>Serial number:</td><td><input name="acousticTagSerial"/></td></tr>
          <tr><td>ID:</td><td><input name="acousticTagId"/></td></tr>
          </table>
        </c:if>
        <c:if test="${showSatelliteTag}">
          <%
            pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
           %>
          <h5>Satellite Tag</h5>
          <table>
          <tr><td>Name:</td><td>
            <select name="satelliteTagName">
                <option value="None">None</option>
                <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
                    <option value="${satelliteTagName}">${satelliteTagName}</option>
                </c:forEach>
            </select>
          </td></tr>
          <tr><td>Serial Number:</td><td><input name="satelliteTagSerial"/></td></tr>
          <tr><td>Argos PTT Number</td><td><input name="satelliteTagArgosPttNumber"/></td></tr>
          </table>
        </c:if>
        </div>
    </td>
 </tr>
</c:if>

<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('genetics')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Genetics filters</font></a></h4>
  </td>
</tr>
<tr>
  <td>
    <div id="genetics" style="display:none; ">
      <p>Use the fields below to limit your search to encounters with available genetic data.</p>
      <br /><p><em><%=encprops.getProperty("fastOptions") %></em></p>
      <p><strong><%=encprops.getProperty("hasTissueSample")%>: </strong>
            <label> 
            	<input name="hasTissueSample" type="checkbox" id="hasTissueSample" value="hasTissueSample" />
            </label>
      </p>
            <p><strong><%=encprops.getProperty("hasHaplotype")%>: </strong>
            <label> 
            	<input name="hasHaplotype" type="checkbox" id="hasHaplotype" value="hasHaplotype" />
            </label>
      </p>
            </p>
            <p><strong><%=encprops.getProperty("hasMSMarkers")%>: </strong>
            <label> 
            	<input name="hasMSMarkers" type="checkbox" id="hasMSMarkers" value="hasMSMarkers" />
            </label>
      </p>
<br /><p><em><%=encprops.getProperty("slowOptions") %></em></p>
      <p><strong><%=encprops.getProperty("haplotype")%>:</strong> <span class="para">
      <a href="<%=CommonConfiguration.getWikiLocation(context)%>haplotype"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span> <br />
                             (<em><%=encprops.getProperty("locationIDExample")%></em>)
   </p>

      <%
        ArrayList<String> haplos = myShepherd.getAllHaplotypes();
        int totalHaplos = haplos.size();
		System.out.println(haplos.toString());

        if (totalHaplos >= 1) {
      %>

      <select multiple size="<%=(totalHaplos+1) %>" name="haplotypeField" id="haplotypeField">
        <option value="None" ></option>
        <%
          for (int n = 0; n < totalHaplos; n++) {
            String word = haplos.get(n);
            if (!word.equals("")) {
        	%>
        		<option value="<%=word%>"><%=word%></option>
        	<%
            }
          }
        %>
      </select>
      <%
      } else {
      %>
      <p><em><%=encprops.getProperty("noHaplotypes")%>
      </em></p>
      <%
        }
      %>
      
      
    <p><strong><%=encprops.getProperty("geneticSex")%>:</strong> <span class="para">
      <a href="<%=CommonConfiguration.getWikiLocation(context)%>geneticSex"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span> <br />
                             (<em><%=encprops.getProperty("locationIDExample")%></em>)
   </p>

      <%
        ArrayList<String> genSexes = myShepherd.getAllGeneticSexes();
        int totalSexes = genSexes.size();
		//System.out.println(haplos.toString());

        if (totalSexes >= 1) {
      %>

      <select multiple size="<%=(totalSexes+1) %>" name="geneticSexField" id="geneticSexField">
        <option value="None" ></option>
        <%
          for (int n = 0; n < totalSexes; n++) {
            String word = genSexes.get(n);
            if (!word.equals("")) {
        	%>
        		<option value="<%=word%>"><%=word%></option>
        	<%
            }
          }
        %>
      </select>
      <%
      } else {
      %>
      <p><em><%=encprops.getProperty("noGeneticSexes")%>
      </em></p>
      <%
        }
      %>
      
      
      <p><strong><%=encprops.getProperty("msmarker")%>:</strong> 
      <span class="para">
      	<a href="<%=CommonConfiguration.getWikiLocation(context)%>loci" target="_blank">
      		<img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/>
      	</a>
      </span> 
   </p>
<p>

      <%
        ArrayList<String> loci = myShepherd.getAllLoci();
        int totalLoci = loci.size();
		
        if (totalLoci >= 1) {
			%>
            <table border="0">
            <%

          for (int n = 0; n < totalLoci; n++) {
            String word = loci.get(n);
            if (!word.equals("")) {
        	%>
        	
        	<tr><td width="100px"><input name="<%=word%>" type="checkbox" value="<%=word%>"><%=word%></input></td><td><%=encprops.getProperty("allele")%> 1: <input name="<%=word%>_alleleValue0" type="text" size="5" maxlength="10" />&nbsp;&nbsp;</td><td><%=encprops.getProperty("allele")%> 2: <input name="<%=word%>_alleleValue1" type="text" size="5" maxlength="10" /></td></tr>
        		
        	<%
            }
          }
%>
<tr><td colspan="3">

<%=encprops.getProperty("alleleRelaxValue")%>: +/- 
<%
int alleleRelaxMaxValue=0;
try{
	alleleRelaxMaxValue=(new Integer(CommonConfiguration.getProperty("alleleRelaxMaxValue",context))).intValue();
}
catch(Exception d){}
%>
<select name="alleleRelaxValue" size="1">
<%
for(int k=0;k<alleleRelaxMaxValue;k++){
%>
	<option value="<%=k%>"><%=k%></option>	
<%
}
%>
</select>
</td></tr>
</table>
<%
      } 
else {
      %>
      <p><em><%=encprops.getProperty("noLoci")%>
      </em></p>
      <%
        }
      %>
   
</p>

    </div>
  </td>
</tr>



<tr>
  <td>

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('metadata')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Metadata filters</font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="metadata" style="display:none; ">
      <p>Use the fields below to limit your search by catalog metadata fields.</p>
      <table width="720px" align="left">
        <tr>
          <td width="154">
          <p><strong><%=encprops.getProperty("types2search")%></strong>:</p>
     		<%
     		ArrayList<String> values=CommonConfiguration.getSequentialPropertyValues("encounterState",context);
     		int numProps=values.size();
     		%>
     		<p><select size="<%=(numProps+1) %>" multiple="multiple" name="state" id="state">
     		<option value="None"></option>
     		<%
     		
     		for(int y=0;y<numProps;y++){
     		%>
     			<option value="<%=values.get(y) %>"><%=values.get(y) %></option>
     		<%
     		}
     		%>
     		</select>
			</p>
		</td>
        </tr>
      </table>
    </div>
  </td>
</tr>


<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>

<tr>
  <td>

    <p>
    <em> 
    <%
    String submitText=encprops.getProperty("submitText1");
    if(request.getParameter("year1")!=null){submitText=encprops.getProperty("submitText2");}
    
    %>
    <input name="submitSearch" type="submit" id="submitSearch" value="<%=submitText%>">
    </em>
    </p>

  </td>
</tr>
</table>
</form>
</td>
</tr>
</table>
<br />
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page -->
</div>
<!--end wrapper -->



</body>
</html>


