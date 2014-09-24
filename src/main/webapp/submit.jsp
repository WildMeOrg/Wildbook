<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2008-2014 Jason Holmberg
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
<%@ page contentType="text/html; charset=utf-8" pageEncoding="UTF-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,org.ecocean.*, org.ecocean.Util, java.util.GregorianCalendar, java.util.Properties, java.util.List" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>         
<%

boolean isIE = request.getHeader("user-agent").contains("MSIE ");
String context="context0";
context=ServletUtilities.getContext(request);

  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);
//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("submit.properties", langCode,context);


	long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
	long maxSizeBytes = maxSizeMB * 1048576;

  

%>

<html>
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

        
  <script language="javascript" type="text/javascript">
    <!--

    function validate() {
      var requiredfields = "";

      if (document.encounter_submission.submitterName.value.length == 0) {
        /*
         * the value.length returns the length of the information entered
         * in the Submitter's Name field.
         */
        requiredfields += "\n   *  <%=props.getProperty("submit_name") %>";
      }

        /*         
        if ((document.encounter_submission.submitterEmail.value.length == 0) ||
          (document.encounter_submission.submitterEmail.value.indexOf('@') == -1) ||
          (document.encounter_submission.submitterEmail.value.indexOf('.') == -1)) {
      
             requiredfields += "\n   *  valid Email address";
        }
        if ((document.encounter_submission.location.value.length == 0)) {
            requiredfields += "\n   *  valid sighting location";
        }
        */

      if (requiredfields != "") {
        requiredfields = "<%=props.getProperty("pleaseFillIn") %>\n" + requiredfields;
        alert(requiredfields);
// the alert function will popup the alert window
        return false;
      }
      else return true;
    }

    //-->
  </script>

</head>


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


/* css for timepicker */
.ui-timepicker-div .ui-widget-header { margin-bottom: 8px; }
.ui-timepicker-div dl { text-align: left; padding: 0 5px 0 0;}
.ui-timepicker-div dl dt { float: left; clear:left; padding: 0 0 0 5px; }
.ui-timepicker-div dl dd { margin: 0 10px 10px 45%; }
.ui-timepicker-div td { font-size: 90%; }
.ui-tpicker-grid-label { background: none; border: none; margin: 0; padding: 0; }

.ui-timepicker-rtl{ direction: rtl; }
.ui-timepicker-rtl dl { text-align: right; padding: 0 5px 0 0; }
.ui-timepicker-rtl dl dt{ float: right; clear: right; }
.ui-timepicker-rtl dl dd { margin: 0 45% 10px 10px; }

/*customizations*/
.ui_tpicker_hour_label {margin-bottom:5px !important;}
.ui_tpicker_minute_label {margin-bottom:5px !important;}



</style>

<script>
  function resetMap() {
    var ne_lat_element = document.getElementById('lat');
    var ne_long_element = document.getElementById('longitude');


    ne_lat_element.value = "";
    ne_long_element.value = "";

  }

</script>


<body onload="resetMap()" onunload="resetMap()">
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>

 <script type="text/javascript" src="http://geoxml3.googlecode.com/svn/branches/polys/geoxml3.js"></script>
 <script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode%>"></script>
  <link rel="stylesheet" href="//code.jquery.com/ui/1.11.1/themes/smoothness/jquery-ui.css">
 <script src="//code.jquery.com/ui/1.11.1/jquery-ui.js"></script>
 
  <script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>
 
 <%
 if(!langCode.equals("en")){
 %>
 <script src="javascript/timepicker/datepicker-<%=langCode %>.js"></script>
  <script src="javascript/timepicker/jquery-ui-timepicker-<%=langCode %>.js"></script>
 
  
  
  
 <%
 }
 %>
 
  <script type="text/javascript">
  $(function() {
    $( "#datepicker" ).datetimepicker({
      changeMonth: true,
      changeYear: true,
      dateFormat: 'yy-mm-dd',
      maxDate: '+1d',
      controlType: 'select',
      alwaysSetTime: false
      
    });
    $( "#datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );


    
  });
  </script>
  
   <script type="text/javascript">
  $(function() {
    $( "#releasedatepicker" ).datepicker({
      changeMonth: true,
      changeYear: true,
      dateFormat: 'yy-mm-dd'
      
    });
    $( "#releasedatepicker" ).datepicker( $.datepicker.regional[ "<%=langCode %>" ] );
    $( "#releasedatepicker" ).datepicker( "option", "maxDate", "+1d" );
  });
  </script>
 
 
<script type="text/javascript">
//alert("Prepping map functions.");
var center = new google.maps.LatLng(10.8, 160.8);

var map;



var marker;

function placeMarker(location) {
	if(marker!=null){marker.setMap(null);}  
	marker = new google.maps.Marker({
	      position: location,
	      map: map
	  });

	  //map.setCenter(location);
	  
	    var ne_lat_element = document.getElementById('lat');
	    var ne_long_element = document.getElementById('longitude');


	    ne_lat_element.value = location.lat();
	    ne_long_element.value = location.lng();
	}

  function initialize() {
	//alert("initializing map!");
	
	var mapZoom = 3;
	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}


	if(marker!=null){
		center = new google.maps.LatLng(10.8, 160.8);
	}
	
	map = new google.maps.Map(document.getElementById('map_canvas'), {
		  zoom: mapZoom,
		  center: center,
		  mapTypeId: google.maps.MapTypeId.HYBRID
		});
	
	if(marker!=null){
		marker.setMap(map);    
	}

	  //adding the fullscreen control to exit fullscreen
	  var fsControlDiv = document.createElement('DIV');
	  var fsControl = new FSControl(fsControlDiv, map);
	  fsControlDiv.index = 1;
	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

	  google.maps.event.addListener(map, 'click', function(event) {
		    placeMarker(event.latLng);
		  });
 }
  
 

 


function fullScreen(){
	$("#map_canvas").addClass('full_screen_map');
	$('html, body').animate({scrollTop:0}, 'slow');
	initialize();
	
	//hide header
	$("#header_menu").hide();
	
	//if(overlaysSet){overlaysSet=false;setOverlays();}
	//alert("Trying to execute fullscreen!");
}


function exitFullScreen() {
	$("#header_menu").show();
	$("#map_canvas").removeClass('full_screen_map');

	initialize();
	//if(overlaysSet){overlaysSet=false;setOverlays();}
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
      controlText.innerHTML = '<%=props.getProperty("exitFullscreen")%>';
    } else {
      controlText.innerHTML = '<%=props.getProperty("fullscreen")%>';
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
 
 
<div id="main">

<div id="maincol-wide-solo">

<div id="maintext">
  <h1 class="intro"><%=props.getProperty("submit_report")%>
  </h1>
</div>
<form xclass="dropzone" id="encounterForm" action="EncounterForm" method="post" enctype="multipart/form-data"
      name="encounter_submission" target="_self" dir="ltr" lang="en"
      onsubmit="return validate();">
<div class="dz-message"></div>

<p><%=props.getProperty("submit_overview")%>
</p>

<p><%=props.getProperty("submit_note_red")%>
</p>
<table id="encounter_report" border="0" width="100%">
<tr class="form_row">
  <td class="form_label"><strong><font color="#CC0000"><%=props.getProperty("submit_date")%></font></strong>
  </td>
  <td colspan="2">
  
     <input type="text" style="position: relative; z-index: 101;" id="datepicker" name="datepicker" size="20" /> yyyy-MM-dd HH:mm

    </td>
</tr>

<%
  pageContext.setAttribute("showReleaseDate", CommonConfiguration.showReleaseDate(context));
%>
<c:if test="${showReleaseDate}">
    <tr class="form_row">
    <td class="form_label"><strong><%=props.getProperty("submit_releasedate") %>:</strong></td>
    <td colspan="2">  
    	<input type="text" style="position: relative; z-index: 101;" id="releasedatepicker" name="releaseDate" size="20" />
	</td>
    </tr>
</c:if>





<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_sex")%>:</strong></td>
  <td colspan="2" class="form_label"><label> <input type="radio" name="sex"
                                 value="male"/> <%=props.getProperty("submit_male")%>
  </label> <label>
    <input type="radio" name="sex" value="female"/> <%=props.getProperty("submit_female")%>
  </label>

    <label> <input name="sex" type="radio" value="unknown"
                   checked="checked"/> <%=props.getProperty("submit_unknown")%>
    </label></td>
</tr>
<%

if(CommonConfiguration.showProperty("showTaxonomy",context)){

%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("species")%>:</strong></td>
  <td colspan="2">
  <select name="genusSpecies" id="genusSpecies">
  	<option value="" selected="selected"><%=props.getProperty("submit_unsure")%></option>
  <%
  			       boolean hasMoreTax=true;
  			       int taxNum=0;
  			       if(CommonConfiguration.showProperty("showTaxonomy",context)){
  			       while(hasMoreTax){
  			       	  String currentGenuSpecies = "genusSpecies"+taxNum;
  			       	  if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){
  			       	  	%>
  			       	  	 
  			       	  	  <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>"><%=CommonConfiguration.getProperty(currentGenuSpecies,context).replaceAll("_"," ")%></option>
  			       	  	<%
  			       		taxNum++;
  			          }
  			          else{
  			             hasMoreTax=false;
  			          }
  			          
			       }
			       }
 %>
  </select></td>
</tr>
<%
}
//test comment
%>

<tr class="form_row">
  <td class="form_label" rowspan="5"><strong><font
    color="#CC0000"><%=props.getProperty("submit_location")%>:</font></strong></td>
  <td colspan="2"><input name="location" type="text" id="location" size="40"/></td>
</tr>
<%
//add locationID to fields selectable


if(CommonConfiguration.getSequentialPropertyValues("locationID", context).size()>0){
%>
<tr class="form_row">
			<td class="form_label1"><strong><%=props.getProperty("locationID")%>:</strong></td>
		<td>
	  		<select name="locationID" id="locationID">
	  			<option value="" selected="selected"></option>
	  			<%
	  			       boolean hasMoreLocationsIDs=true;
	  			       int locNum=0;
	  			       
	  			       while(hasMoreLocationsIDs){
	  			       	  String currentLocationID = "locationID"+locNum;
	  			       	  if(CommonConfiguration.getProperty(currentLocationID,context)!=null){
	  			       	  	%>
	  			       	  	 
	  			       	  	  <option value="<%=CommonConfiguration.getProperty(currentLocationID,context)%>"><%=CommonConfiguration.getProperty(currentLocationID,context)%></option>
	  			       	  	<%
	  			       		locNum++;
	  			          }
	  			          else{
	  			             hasMoreLocationsIDs=false;
	  			          }
	  			          
				       }
				       
	 %>
	  </select>
	
</td>
	</tr>
<%
}

if(CommonConfiguration.showProperty("showCountry",context)){

%>

		<tr class="form_row">
			<td class="form_label1"><strong><%=props.getProperty("country")%>:</strong></td>
		<td>
	  		<select name="country" id="country">
	  			<option value="" selected="selected"></option>
	  			<%
	  			       boolean hasMoreCountries=true;
	  			       int taxNum=0;
	  			       
	  			       while(hasMoreCountries){
	  			       	  String currentCountry = "country"+taxNum;
	  			       	  if(CommonConfiguration.getProperty(currentCountry,context)!=null){
	  			       	  	%>
	  			       	  	 
	  			       	  	  <option value="<%=CommonConfiguration.getProperty(currentCountry,context)%>"><%=CommonConfiguration.getProperty(currentCountry,context)%></option>
	  			       	  	<%
	  			       		taxNum++;
	  			          }
	  			          else{
	  			             hasMoreCountries=false;
	  			          }
	  			          
				       }
				       
	 %>
	  </select>
	
</td>
	</tr>
	
	
	

<%
}  //end if showCountry

%>

<tr class="form_row"><td colspan="2">
    <p id="map">
    
    <!--  
      <p>Use the arrow and +/- keys to navigate to a portion of the globe,, then click
        a point to set the sighting location. You can also use the text boxes below the map to specify exact
        latitude and longitude.</p>
    -->

      	<p id="map_canvas" style="width: 578px; height: 383px; "></p>
      		<p id="map_overlay_buttons"></p>
    </p>
</td>
</tr>

<tr class="form_row">
		<td class="form_label1"><strong><%=props.getProperty("submit_gpslatitude")%>:</strong></td>
		<td>
		<input name="lat" type="text" id="lat" size="10" />
		&deg;
		</td>
	</tr>
	
	<tr class="form_row">
		<td class="form_label1"><strong><%=props.getProperty("submit_gpslongitude")%>:</strong></td>
		<td>
			<input name="longitude" type="text" id="longitude" size="10" />
	
		&deg;
		<br/>
		<br/> <%=props.getProperty("gpsConverter") %>
		</td>
	</tr>
	
	
	      <%



if(CommonConfiguration.showProperty("maximumDepthInMeters",context)){
%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_depth")%>:</strong></td>
  <td colspan="2">
<input name="depth" type="text" id="depth" size="10" />
  &nbsp;<%=props.getProperty("submit_meters")%>
    </td>
</tr>
<%
}
%>

<%
if(CommonConfiguration.showProperty("maximumElevationInMeters",context)){
%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_elevation")%>:</strong></td>
  <td colspan="2">
<input name="elevation" type="text" id="elevation" size="10" />
  &nbsp;<%=props.getProperty("submit_meters")%>
    </td>
</tr>
<%
}
%>

<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("status") %></strong></td>
  <td colspan="2"><select name="livingStatus" id="livingStatus">
    <option value="alive" selected="selected">Alive</option>
    <option value="dead">Dead</option>
  </select></td>
</tr>

<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_behavior")%></strong></td>
  <td colspan="2">
    <input name="behavior" type="text" id="scars" size="75"/></td>
</tr>
<%

if(CommonConfiguration.showProperty("showLifestage",context)){

%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("lifeStage")%></strong></td>
  <td colspan="2">
  <select name="lifeStage" id="lifeStage">
  	<option value="" selected="selected"></option>
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
			       
 %>
  </select></td>
</tr>
<%
}
%>
<%
    pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements(context));
%>
<c:if test="${showMeasurements}">
<%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
    pageContext.setAttribute("samplingProtocols", Util.findSamplingProtocols(langCode,context));
%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("measurements")%>:</strong></td>
  <td colspan="2">
  <table class="measurements">
  <tr>
  <th><%=props.getProperty("type") %></th><th><%=props.getProperty("size") %></th><th><%=props.getProperty("units") %></th><c:if test="${!empty samplingProtocols}"><th><%=props.getProperty("samplingProtocol") %></th></c:if>
  </tr>
  <c:forEach items="${items}" var="item">
    <tr>
    <td>${item.label}</td>
    <td><input name="measurement(${item.type})" id="${item.type}"/><input type="hidden" name="measurement(${item.type}units)" value="${item.units}"/></td>
    <td><c:out value="${item.unitsLabel}"/></td>
    <c:if test="${!empty samplingProtocols}">
      <td>
        <select name="measurement(${item.type}samplingProtocol)">
        <c:forEach items="${samplingProtocols}" var="optionDesc">
          <option value="${optionDesc.name}"><c:out value="${optionDesc.display}"/></option>
        </c:forEach>
        </select>
      </td>
    </c:if>
    </tr>
  </c:forEach>
  </table>
  </td>
</tr>
</c:if>
<%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode,context));
%>

<c:if test="${showMetalTags and !empty metalTags}">
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("physicalTags") %></strong></td>
  <td colspan="2">
    <table class="metalTags">
    <tr>
      <th><%=props.getProperty("location") %></th><th><%=props.getProperty("tagNumber") %></th>
    </tr>
    <c:forEach items="${metalTags}" var="metalTagDesc">
      <tr>
        <td><c:out value="${metalTagDesc.locationLabel}:"/></td>
        <td><input name="metalTag(${metalTagDesc.location})"/></td>
      </tr>
    </c:forEach>
    </table>
  </td>
</tr>
</c:if>

<c:if test="${showAcousticTag}">
<tr class="form_row">
    <td class="form_label"><strong><%=props.getProperty("acousticTag") %></strong></td>
    <td colspan="2">
      <table class="acousticTag">
      <tr>
      <td><%=props.getProperty("serialNumber") %></td>
      <td><input name="acousticTagSerial"/></td>
      </tr>
      <tr>
        <td><%=props.getProperty("id") %></td>
        <td><input name="acousticTagId"/></td>
      </tr>
      </table>
    </td>
</tr>
</c:if>

<c:if test="${showSatelliteTag}">
<%
  pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
%>
<tr class="form_row">
    <td class="form_label"><strong><%=props.getProperty("satelliteTag") %></strong></td>
    <td colspan="2">
      <table class="satelliteTag">
      <tr>
        <td><%=props.getProperty("name") %></td>
        <td>
            <select name="satelliteTagName">
              <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
                <option value="${satelliteTagName}">${satelliteTagName}</option>
              </c:forEach>
            </select>
        </td>
      </tr>
      <tr>
        <td><%=props.getProperty("serialNumber") %></td>
        <td><input name="satelliteTagSerial"/></td>
      </tr>
      <tr>
        <td><%=props.getProperty("argosNumber") %></td>
        <td><input name="satelliteTagArgosPttNumber"/></td>
      </tr>
      </table>
    </td>
</tr>
</c:if>

<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_scars")%>:</strong></td>
  <td colspan="2">
    <input name="scars" type="text" id="scars" size="75"/></td>
</tr>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_comments")%>:</strong></td>
  <td colspan="2"><textarea name="comments" cols="40" id="comments"
                            rows="10"></textarea></td>
</tr>
<tr>
  <td></td>
  <td></td>
  <td></td>
</tr>
</table>
<table id="encounter_contact">
  <tr>
    <td class="you" colspan="2"><strong><%=props.getProperty("submit_contactinfo")%>*</strong></td>
    <td class="photo" colspan="2"><strong><%=props.getProperty("submit_contactphoto")%>
    </strong><br/><%=props.getProperty("submit_ifyou")%>
    </td>
  </tr>
    <%
    //let's pre-populate important info for logged in users
    String submitterName="";
    String submitterEmail="";
    String affiliation="";
    String project="";
    if(request.getRemoteUser()!=null){
    	submitterName=request.getRemoteUser();
    	Shepherd myShepherd=new Shepherd(context);
    	if(myShepherd.getUser(submitterName)!=null){
    		User user=myShepherd.getUser(submitterName);
    		if(user.getFullName()!=null){submitterName=user.getFullName();}
    		if(user.getEmailAddress()!=null){submitterEmail=user.getEmailAddress();}
    		if(user.getAffiliation()!=null){affiliation=user.getAffiliation();}
    		if(user.getUserProject()!=null){project=user.getUserProject();}
    	}
    }
    %>
  <tr>
    <td><font color="#CC0000"><%=props.getProperty("submit_name")%>:</font></td>
    <td><input name="submitterName" type="text" id="submitterName" size="24" value="<%=submitterName%>"/></td>
    <td><%=props.getProperty("submit_name")%>:</td>
    <td><input name="photographerName" type="text" id="photographerName" size="24"/></td>
  </tr>
  <tr>
    <td><font color="#CC0000"><%=props.getProperty("submit_email")%>:</font></td>

    <td><input name="submitterEmail" type="text" id="submitterEmail" size="24" value="<%=submitterEmail %>"/></td>
    <td><%=props.getProperty("submit_email")%>:</td>
    <td><input name="photographerEmail" type="text" id="photographerEmail" size="24"/></td>
  </tr>

  <tr>
    <td><%=props.getProperty("submit_address")%>:</td>
    <td><input name="submitterAddress" type="text" id="submitterAddress" size="24"/></td>
    <td><%=props.getProperty("submit_address")%>:</td>
    <td><input name="photographerAddress" type="text" id="photographerAddress" size="24"/></td>
  </tr>
  <tr>
    <td><%=props.getProperty("submit_telephone")%>:</td>
    <td><input name="submitterPhone" type="text" id="submitterPhone" size="24"/></td>
    <td><%=props.getProperty("submit_telephone")%>:</td>
    <td><input name="photographerPhone" type="text" id="photographerPhone" size="24"/></td>
  </tr>

  <tr>
    <td colspan="4"><br /><strong><%=props.getProperty("submitterOrganization")%></strong><br />
    <input name="submitterOrganization" type="text" id="submitterOrganization" size="75" value="<%=affiliation%>"/>
    </td>
  </tr>
  
    <tr>
      <td colspan="4"><br /><strong><%=props.getProperty("submitterProject")%></strong><br />
      <input name="submitterProject" type="text" id="submitterProject" size="75" value="<%=project%>"/>
      </td>
  </tr>

  <tr>
    <td colspan="4"><br /><strong><%=props.getProperty("otherEmails")%></strong><br />
    <input name="informothers" type="text" id="informothers" size="75"/>
    </td>
  </tr>
  
</table>
<p><em><%=props.getProperty("multipleEmailNote")%></em>.</p>
<hr>

<p><%=props.getProperty("submit_pleaseadd")%>
</p>

<p>&nbsp;</p>

<p align="center"><strong><%=props.getProperty("submit_image")%></strong>

<div id="xdropzone-previews" class="dropzone-previews" style="display: none;">
	<div style="text-align: center;" ><b>drop</b> image/video files here, or <b>click</b> for file dialog</div>
</div>

<script>
function updateList(inp) {
	var f = '';
	if (inp.files && inp.files.length) {
		var all = [];
		for (var i = 0 ; i < inp.files.length ; i++) {
			if (inp.files[i].size > <%=maxSizeBytes%>) {
				all.push('<span class="error">' + inp.files[i].name + ' (' + Math.round(inp.files[i].size / (1024*1024)) + 'MB is too big, <%=maxSizeMB%>MB max)</span>');
			} else {
				all.push(inp.files[i].name + ' (' + Math.round(inp.files[i].size / 1024) + 'k)');
			}
		}
		f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
	} else {
		f = inp.value;
	}
	document.getElementById('input-file-list').innerHTML = f;
}
</script>

	<div class="input-file-drop" xonClick="return fileClick();">
<% if (isIE) { %>
		<div><%=props.getProperty("dragInstructionsIE")%></div>
		<input class="ie" name="theFiles" type="file" accept=".jpg, .jpeg, .png, .bmp, .gif, .mov, .wmv, .avi, .mp4, .mpg" multiple size="30" onChange="updateList(this);" />
<% } else { %>
		<input class="nonIE" name="theFiles" type="file" accept=".jpg, .jpeg, .png, .bmp, .gif, .mov, .wmv, .avi, .mp4, .mpg" multiple size="30" onChange="updateList(this);" />
		<div><%=props.getProperty("dragInstructions")%></div>
<% } %>
		<div id="input-file-list"></div>
	</div>

</p>


<p>&nbsp;</p>
<%if (request.getRemoteUser() != null) {%> <input name="submitterID"
                                                  type="hidden"
                                                  value="<%=request.getRemoteUser()%>"/> <%} else {%>
<input
  name="submitterID" type="hidden" value="N/A"/> <%}%>
<p align="center"><input type="submit" name="Submit" value="<%=props.getProperty("submit_send")%>"/>
</p>

<p>&nbsp;</p>
</form>
</div>
<!-- end maintext --></div>
<!-- end maincol -->
<jsp:include page="footer.jsp" flush="true"/>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>
