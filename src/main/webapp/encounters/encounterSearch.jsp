<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,javax.jdo.Extent, javax.jdo.Query, java.util.ArrayList, com.reijns.I3S.Point2D" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Properties" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>         
<%
String context="context0";
context=ServletUtilities.getContext(request);

String langCode=ServletUtilities.getLanguageCode(request);


%>

<jsp:include page="../header.jsp" flush="true"/>

  <!-- Sliding div content: STEP1 Place inside the head section -->
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
    animatedcollapse.addDiv('patternrecognition', 'fade=1')

    animatedcollapse.ontoggle = function($, divobj, state) { //fires each time a DIV is expanded/contracted
      //$: Access to jQuery
      //divobj: DOM reference to DIV being expanded/ collapsed. Use "divobj.id" to get its ID
      //state: "block" or "none", depending on state
    }
    animatedcollapse.init()
  </script>
  <!-- /STEP2 Place inside the head section -->

<script src="//maps.google.com/maps/api/js?sensor=false&language=<%=langCode %>"></script>
<script src="visual_files/keydragzoom.js" type="text/javascript"></script>
<script type="text/javascript" src="../javascript/geoxml3.js"></script>
<script type="text/javascript" src="../javascript/ProjectedOverlay.js"></script>
 <script type="text/javascript" src="../javascript/markerclusterer/markerclusterer.js"></script>
<script src="../javascript/timepicker/jquery-ui-timepicker-addon.js"></script>

 <%
 if(!langCode.equals("en")){
 %>

<script src="javascript/timepicker/datepicker-<%=langCode %>.js"></script>
<script src="javascript/timepicker/jquery-ui-timepicker-<%=langCode %>.js"></script>

 <%
 }
 %>


</head>

<style type="text/css">v\:* {
  behavior: url(#default#VML);  
   .ui-timepicker-div .ui-widget-header { margin-bottom: 8px; }
.ui-timepicker-div dl { text-align: left; }
.ui-timepicker-div dl dt { float: left; clear:left; padding: 0 0 0 5px; }
.ui-timepicker-div dl dd { margin: 0 10px 10px 40%; }
.ui-timepicker-div td { font-size: 90%; }
.ui-tpicker-grid-label { background: none; border: none; margin: 0; padding: 0; }
.ui-timepicker-div .ui_tpicker_unit_hide{ display: none; }

.ui-timepicker-rtl{ direction: rtl; }
.ui-timepicker-rtl dl { text-align: right; padding: 0 5px 0 0; }
.ui-timepicker-rtl dl dt{ float: right; clear: right; }
.ui-timepicker-rtl dl dd { margin: 0 40% 10px 10px; }

/* Shortened version style */
.ui-timepicker-div.ui-timepicker-oneLine { padding-right: 2px; }
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_time,
.ui-timepicker-div.ui-timepicker-oneLine dt { display: none; }
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_time_label { display: block; padding-top: 2px; }
.ui-timepicker-div.ui-timepicker-oneLine dl { text-align: right; }
.ui-timepicker-div.ui-timepicker-oneLine dl dd,
.ui-timepicker-div.ui-timepicker-oneLine dl dd > div { display:inline-block; margin:0; }
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_minute:before,
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_second:before { content:':'; display:inline-block; }
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_millisec:before,
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_microsec:before { content:'.'; display:inline-block; }
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_unit_hide,
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_unit_hide:before{ display: none; }
    /*customizations*/
    .ui_tpicker_hour_label {margin-bottom:5px !important;}
    .ui_tpicker_minute_label {margin-bottom:5px !important;}
}
</style>
<link type='text/css' rel='stylesheet' href='../javascript/timepicker/jquery-ui-timepicker-addon.css' />


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

  }
  
  $( function() {
	  $( "#datepicker1" ).datetimepicker({
	      changeMonth: true,
	      changeYear: true,
	      dateFormat: 'yy-mm-dd',
	      maxDate: '+1d',
	      controlType: 'select',
	      alwaysSetTime: false,
	      showTimepicker: false,
	      showSecond:false,
	      showMillisec:false,
	      showMicrosec:false,
	      showTimezone:false
	    });
	    $( "#datepicker1" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );
	
	    $( "#datepicker2" ).datetimepicker({
	        changeMonth: true,
	        changeYear: true,
	        dateFormat: 'yy-mm-dd',
	        maxDate: '+1d',
	        controlType: 'select',
	        alwaysSetTime: false,
	        showTimepicker: false,
	        showSecond:false,
	        showMillisec:false,
	        showMicrosec:false,
	        showTimezone:false
	      });
	      $( "#datepicker2" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );
		
	      //date added pickers
	      $( "#dateaddedpicker1" ).datetimepicker({
		      changeMonth: true,
		      changeYear: true,
		      dateFormat: 'yy-mm-dd',
		      maxDate: '+1d',
		      controlType: 'select',
		      alwaysSetTime: false,
		      showTimepicker: false,
		      showSecond:false,
		      showMillisec:false,
		      showMicrosec:false,
		      showTimezone:false
		    });
		    $( "#dateaddedpicker1" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );
		
		    $( "#dateaddedpicker2" ).datetimepicker({
		        changeMonth: true,
		        changeYear: true,
		        dateFormat: 'yy-mm-dd',
		        maxDate: '+1d',
		        controlType: 'select',
		        alwaysSetTime: false,
		        showTimepicker: false,
		        showSecond:false,
		        showMillisec:false,
		        showMicrosec:false,
		        showTimezone:false
		      });
		      $( "#dateaddedpicker2" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );

  } );
</script>

<body onload="resetMap()" onunload="resetMap()">

<%


  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("encounterSearch.jsp");
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
  myShepherd.beginDBTransaction();


  Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounterSearch.properties"));
  encprops=ShepherdProperties.getProperties("encounterSearch.properties", langCode, context);

  
%>

<div class="container maincontent">
<table width="810">
<tr>
<td>
<p>

<h1 class="intro"><img src="../images/Crystal_Clear_action_find.png" width="50px" height="50px" align="absmiddle"> <%=encprops.getProperty("title")%>
  <a href="<%=CommonConfiguration.getWikiLocation(context)%>searching#encounter_search" target="_blank">
    <img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/>
  </a>
</h1>
</p>
<p><em><%=encprops.getProperty("instructions")%>
</em></p>

<form action="searchResults.jsp" method="get" name="search" id="search">

  <%
		if(request.getParameter("referenceImageName")!=null){
			
			if(myShepherd.isSinglePhotoVideo(request.getParameter("referenceImageName"))){
				SinglePhotoVideo mySPV=myShepherd.getSinglePhotoVideo(request.getParameter("referenceImageName"));
				//int slashPosition=request.getParameter("referenceImageName").indexOf("/");
				String encNum=mySPV.getCorrespondingEncounterNumber();
				Encounter thisEnc = myShepherd.getEncounter(encNum);
				
				
		%>
<p><strong><%=encprops.getProperty("referenceImage") %></strong></p>

<p><%=encprops.getProperty("selectedReference") %></p>
<input name="referenceImageName" type="hidden"
       value="<%=request.getParameter("referenceImageName") %>"/>

<p><img width="810px" src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=thisEnc.subdir(thisEnc.getCatalogNumber()) %>/<%=mySPV.getFilename() %>"/></p>
<table>
											<tr>
												<td align="left" valign="top">
										
												<table>
										<%
										
										//prep the params
										if(thisEnc.getLocation()!=null){
										%>
										<tr><td><span class="caption"><%=encprops.getProperty("location") %> <%=thisEnc.getLocation() %></span></td></tr>
										<%
										}
										if(thisEnc.getLocationID()!=null){
										%>
										<tr><td><span class="caption"><%=encprops.getProperty("locationID") %> <%=thisEnc.getLocationID() %></span></td></tr>
										<%
										}
										%>
										<tr><td><span class="caption"><%=encprops.getProperty("date") %> <%=thisEnc.getDate() %></span></td></tr>
										<%
										if(thisEnc.getIndividualID()!=null){
										%>
											<tr><td><span class="caption"><%=encprops.getProperty("identifiedAs") %> 
											<%
											if(!thisEnc.getIndividualID().equals("Unassigned")){
											%>
												<a href="../individuals.jsp?number=<%=thisEnc.getIndividualID() %>" target="_blank">
											<%
											}
											%>
											<%=thisEnc.getIndividualID() %>
											<%
											if(!thisEnc.getIndividualID().equals("Unassigned")){
											%>
												</a>
											<%
											}
											%>
											</span></td></tr>
										<%
										}
										%>
										<tr><td><span class="caption"><%=encprops.getProperty("encounter") %> <a href="encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" target="_blank"><%=thisEnc.getCatalogNumber() %></a></span></td></tr>
										

										
										
<%
										if(thisEnc.getVerbatimEventDate()!=null){
										%>
											<tr>
											
											<td><span class="caption"><%=encprops.getProperty("verbatimEventDate") %> <%=thisEnc.getVerbatimEventDate() %></span></td></tr>
										<%
										}
										%>


										</table>
  <%
		}
}
		%>

<table>

<tr>
  <td width="810px">

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/></a>
      <a href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><font
        color="#000000"><%=encprops.getProperty("locationFilter") %></font></a></h4>


    
<script type="text/javascript">
//alert("Prepping map functions.");
var center = new google.maps.LatLng(0, 0);

var map;

var markers = [];
var overlays = [];


var overlaysSet=false;
 
var geoXml = null;
var geoXmlDoc = null;
var kml = null;
var filename="//<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?encounterSearchUse=true&barebones=true";
 

  function initialize() {
	//alert("initializing map!");
	//overlaysSet=false;
	var mapZoom = 1;
	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}

	  map = new google.maps.Map(document.getElementById('map_canvas'), {
		  zoom: mapZoom,
		  center: center,
		  mapTypeId: google.maps.MapTypeId.HYBRID,
		  fullscreenControl: true
		});

   map.enableKeyDragZoom({
          visualEnabled: true,
          visualPosition: google.maps.ControlPosition.LEFT,
          visualPositionOffset: new google.maps.Size(35, 0),
          visualPositionIndex: null,
          visualSprite: "//maps.gstatic.com/mapfiles/ftr/controls/dragzoom_btn.png",
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
        });

        //alert("Finished initialize method!");

          
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
    		content:'<%=encprops.getProperty("loadingMapData") %>',
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



  google.maps.event.addDomListener(window, 'load', initialize);
  
  
    </script>

    <div id="map">
      <p><%=encprops.get("useTheArrow") %></p>

      <div id="map_canvas" style="width: 770px; height: 510px; "></div>
      
      <div id="map_overlay_buttons">
 
          <input type="button" value="<%=encprops.getProperty("loadMarkers") %>" onclick="setOverlays();" />&nbsp;
 

      </div>
      <p><%=encprops.getProperty("northeastCorner") %> <%=encprops.getProperty("latitude") %> <input type="text" id="ne_lat" name="ne_lat"></input> <%=encprops.getProperty("longitude") %>
        <input type="text" id="ne_long" name="ne_long"></input><br/><br/>
        <%=encprops.getProperty("southwestCorner") %> <%=encprops.getProperty("latitude") %> <input type="text" id="sw_lat" name="sw_lat"></input> <%=encprops.getProperty("longitude") %>
        <input type="text" id="sw_long" name="sw_long"></input></p>
    </div>

  </td>
</tr>
<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('location')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000"><%=encprops.get("locationFilterText") %></font></a></h4>

    <div id="location" style="display:none; ">
      <p><%=encprops.getProperty("locationInstructions") %></p>

      <p><strong><%=encprops.getProperty("locationNameContains")%>:</strong>
        <input name="locationField" type="text" size="60"> <br>
        <em><%=encprops.getProperty("leaveBlank")%>
        </em>
      </p>

      <p><strong><%=encprops.getProperty("locationID")%></strong> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation(context)%>locationID"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span> <br>
        (<em><%=encprops.getProperty("locationIDExample")%>
        </em>)</p>

      <%
        List<String> locIDs = myShepherd.getAllLocationIDs();
        int totalLocIDs = locIDs.size();

        if (totalLocIDs >= 1) {
      %>

      <select multiple name="locationCodeField" id="locationCodeField" size="10">
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
      
      
      <%

if(CommonConfiguration.showProperty("showCountry",context)){

%>
<table><tr><td valign="top">
<strong><%=encprops.getProperty("country")%>:</strong><br />
<em><%=encprops.getProperty("leaveBlank")%>
        </em>

</td></tr><tr><td>
  
  <select name="country" id="country" multiple="multiple" size="5">
  	<option value="None" selected="selected"></option>
  <%
  			       boolean hasMoreCountries=true;
  			       int stageNum=0;
  			       
  			       while(hasMoreCountries){
  			       	  String currentCountry = "country"+stageNum;
  			       	  if(CommonConfiguration.getProperty(currentCountry,context)!=null){
  			       	  	%>
  			       	  	 
  			       	  	  <option value="<%=CommonConfiguration.getProperty(currentCountry,context)%>"><%=CommonConfiguration.getProperty(currentCountry,context)%></option>
  			       	  	<%
  			       		stageNum++;
  			          }
  			          else{
  			        	hasMoreCountries=false;
  			          }
  			          
			       }
			       if(stageNum==0){%>
			    	   <em><%=encprops.getProperty("noCountries")%></em>
			       <% 
			       }
			       %>
			       

  </select>
  </td></tr></table>
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
      <font color="#000000"><%=encprops.getProperty("dateFilters") %></font></a></h4>
  </td>
</tr>


<tr>
  <td>
    <div id="date" style="display:none;">
      <p><%=encprops.getProperty("dateInstructions") %></p>
      <strong><%=encprops.getProperty("sightingDates")%></strong><br/>
      

      <table width="720">
        <tr>
          <td width="720"> 
	          <%=encprops.get("start") %>&nbsp;
	          <input  class="form-control" type="text" style="position: relative; z-index: 101;width: 200px;" id="datepicker1" name="datepicker1" size="20" />
	           &nbsp;<%=encprops.get("end") %>&nbsp;
	          <input class="form-control" type="text" style="position: relative; z-index: 101;width: 200px;" id="datepicker2" name="datepicker2" size="20" />
	          
          </td>
        </tr>
      </table>

      <p><strong><%=encprops.getProperty("verbatimEventDate")%></strong> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation(context)%>verbatimEventDate"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span></p>

      <%
        List<String> vbds = myShepherd.getAllVerbatimEventDates();
        int totalVBDs = vbds.size();


        if (totalVBDs > 1) {
      %>

      <select multiple name="verbatimEventDateField" id="verbatimEventDateField" size="5">
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
   
     
      <p><strong><%=encprops.getProperty("addedsightingDates")%></strong></p>

       <table width="720">
        <tr>
          <td width="720"> 
	          <%=encprops.get("start") %>&nbsp;
	          <input  class="form-control" type="text" style="position: relative; z-index: 101;width: 200px;" id="dateaddedpicker1" name="dateaddedpicker1" size="20" />
	           &nbsp;<%=encprops.get("end") %>&nbsp;
	          <input class="form-control" type="text" style="position: relative; z-index: 101;width: 200px;" id="dateaddedpicker2" name="dateaddedpicker2" size="20" />
	          
          </td>
        </tr>
      </table>
		</div>
		</td>
</tr>


<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('observation')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000"><%=encprops.getProperty("observationFilters") %></font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="observation" style="display:none; ">
      <p><%=encprops.getProperty("observationInstructions") %></p>

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
				List<String> behavs = myShepherd.getAllBehaviors();
				int totalBehavs=behavs.size();

				
				if(totalBehavs>1){
				%>

            <select multiple name="behaviorField" id="behaviorField" style="width: 500px">
              <option value="None"></option>
              <%
                for (int f = 0; f < totalBehavs; f++) {
                  String word = behavs.get(f);
                  if ((word != null)&&(!word.trim().equals(""))) {
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


if(CommonConfiguration.showProperty("showPatterningCode",context)){

%>
<tr valign="top">
  <td><strong><%=encprops.getProperty("patterningCode")%></strong>
  
  <select name="patterningCodeField" id="patterningCodeField">
  	<option value="None" selected="selected"></option>
  <%
  			       boolean hasMorePatterningCodes=true;
  			       int stageNum=0;
  			       
  			       while(hasMorePatterningCodes){
  			       	  String currentLifeStage = "patterningCode"+stageNum;
  			       	  if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
  			       	  	%>
  			       	  	 
  			       	  	  <option value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></option>
  			       	  	<%
  			       		stageNum++;
  			          }
  			          else{
  			        	hasMorePatterningCodes=false;
  			          }
  			          
			       }
			       if(stageNum==0){%>
			    	   <p><em><%=encprops.getProperty("noPatterningCodes")%></em></p>
			       <% }
			       
 %>
  </select></td>
</tr>
<%
}

  pageContext.setAttribute("showMeasurement", CommonConfiguration.showMeasurements(context));
%>
<c:if test="${showMeasurement}">
<%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
%>
<tr><td></td></tr>
<tr><td><strong><%=encprops.getProperty("measurements") %></strong></td></tr>
<c:forEach items="${items}" var="item">
<tr valign="top">
<td>${item.label}
<select name="measurement${item.type}(operator)">
<option value="gteq">&gt;=</option>
<option value="lteq">&lt;=</option>
  <option value="gt">&gt;</option>
  <option value="lt">&lt;</option>
  <option value="eq">=</option>
</select>
<input name="measurement${item.type}(value)"/>(<c:out value="${item.unitsLabel})"/>
</td>
</tr>
</c:forEach>
<tr><td></td></tr>
</c:if>
<tr><td>
      <p><strong><%=encprops.getProperty("hasPhoto")%> </strong>
            <label> 
            	<input name="hasPhoto" type="checkbox" id="hasPhoto" value="hasPhoto" />
            </label>
      </p>
      </td></tr>
<%
  int totalKeywords = myShepherd.getNumKeywords();
%>
<tr>
  <td valign="top"><%=encprops.getProperty("hasKeywordPhotos")%><br/>
    <%

      if (totalKeywords > 0) {
    %>

    <select multiple size="10" name="keyword" id="keyword" >
      <option value="None"></option>
      <%


        Iterator<Keyword> keys = myShepherd.getAllKeywords(kwQuery);
        for (int n = 0; n < totalKeywords; n++) {
          Keyword word = keys.next();
      %>
      <option value="<%=word.getIndexname()%>"><%=word.getReadableName()%>
      </option>
      <%
        }

      %>

    </select>
    </td>
    </tr>
           <tr><td>
      <p>
            <label> 
            	<input name="photoKeywordOperator" type="checkbox" id="photoKeywordOperator" value="_OR_" />
            </label> <%=encprops.getProperty("orPhotoKeywords")%> 
      </p>
      </td></tr>
    <%
    } else {
    %>

    <p><em><%=encprops.getProperty("noKeywords")%>
    </em>
</td>
</tr>
        <%
					
				}
				%>
  


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
      <font color="#000000"><%=encprops.getProperty("identityFilters") %></font></a></h4>
  </td>
</tr>
<tr>
  <td>
    <div id="identity" style="display:none; ">
      <p><%=encprops.getProperty("identityInstructions") %></p>
      <input name="resightOnly" type="checkbox" id="resightOnly" value="true" /> <%=encprops.getProperty("include")%> 
   
   <select name="numResights" id="numResights">
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

<br /><input name="unassigned" type="checkbox" id="unassigned" value="true" /> <%=encprops.getProperty("unassignedEncounter")%>

      <p><strong><%=encprops.getProperty("alternateID")%>:</strong> <em> <input
        name="alternateIDField" type="text" id="alternateIDField" size="10"
        maxlength="35"> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation(context)%>alternateID"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" width="15" height="15" border="0"
                             align="absmiddle"/></a></span>
        <br></em></p>
        
        
            
            <p><strong><%=encprops.getProperty("individualID")%></strong> <em> <input
              name="individualID" type="text" id="individualID" size="25"
              maxlength="100"> <span class="para"><a
              href="<%=CommonConfiguration.getWikiLocation(context)%>individualID"
              target="_blank"><img src="../images/information_icon_svg.gif"
                                   alt="Help" width="15" height="15" border="0" align="absmiddle"/></a></span>
              <br />
              
              <%=encprops.getProperty("multipleIndividualID")%></em></p>
        
      
        
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
       <font color="#000000"><%=encprops.getProperty("tagsTitle") %></font></a></h4>
     </td>
 </tr>
 <tr>
    <td>
        <div id="tags" style="display:none;">
        <p><%=encprops.getProperty("tagsInstructions") %></p>
        <c:if test="${showMetalTags}">
            <% 
              pageContext.setAttribute("metalTagDescs", Util.findMetalTagDescs(langCode,context)); 
            %>
            <h5><%=encprops.getProperty("metalTags") %></h5>
            <table>
            <c:forEach items="${metalTagDescs}" var="metalTagDesc">
                <tr>
                    <td><c:out value="${metalTagDesc.locationLabel}:"/></td><td><input name="metalTag(${metalTagDesc.location})"/></td>
                </tr>
            </c:forEach>
            </table>
        </c:if>
        <c:if test="${showAcousticTag}">
          <h5><%=encprops.getProperty("acousticTags") %></h5>
          <table>
          <tr><td><%=encprops.getProperty("serialNumber") %></td><td><input name="acousticTagSerial"/></td></tr>
          <tr><td>ID:</td><td><input name="acousticTagId"/></td></tr>
          </table>
        </c:if>
        <c:if test="${showSatelliteTag}">
          <%
            pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
           %>
          <h5><%=encprops.getProperty("satelliteTag") %></h5>
          <table>
          <tr><td><%=encprops.getProperty("name") %></td><td>
            <select name="satelliteTagName">
                <option value="None"><%=encprops.getProperty("none") %></option>
                <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
                    <option value="${satelliteTagName}">${satelliteTagName}</option>
                </c:forEach>
            </select>
          </td></tr>
          <tr><td><%=encprops.getProperty("serialNumber") %></td><td><input name="satelliteTagSerial"/></td></tr>
          <tr><td><%=encprops.getProperty("argosPTT") %></td><td><input name="satelliteTagArgosPttNumber"/></td></tr>
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
      <font color="#000000"><%=encprops.getProperty("biologicalSamples") %></font></a></h4>
  </td>
</tr>
<tr>
  <td>
    <div id="genetics" style="display:none; ">
      <p><%=encprops.getProperty("biologicalInstructions") %></p>
      
      <p><strong><%=encprops.getProperty("hasTissueSample")%>: </strong>
            <label> 
            	<input name="hasTissueSample" type="checkbox" id="hasTissueSample" value="hasTissueSample" />
            </label>
      </p>
      <p><strong><%=encprops.getProperty("tissueSampleID")%>:</strong>
        <input name="tissueSampleID" type="text" size="50">    
      </p>
      <p><strong><%=encprops.getProperty("haplotype")%>:</strong> <span class="para">
      <a href="<%=CommonConfiguration.getWikiLocation(context)%>haplotype"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span> <br />
                             (<em><%=encprops.getProperty("locationIDExample")%></em>)
   </p>

      <%
        List<String> haplos = myShepherd.getAllHaplotypes();
        int totalHaplos = haplos.size();
		System.out.println(haplos.toString());

        if (totalHaplos >= 1) {
      %>

      <select multiple size="10" name="haplotypeField" id="haplotypeField">
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
        List<String> genSexes = myShepherd.getAllGeneticSexes();
        int totalSexes = genSexes.size();
		//System.out.println(haplos.toString());

        if (totalSexes >= 1) {
      %>

      <select multiple size="10" name="geneticSexField" id="geneticSexField">
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
      
      
      <%
    pageContext.setAttribute("items", Util.findBiologicalMeasurementDescs(langCode,context));
%>

<table>
<tr><td></td></tr>
<tr><td><strong><%=encprops.getProperty("biomeasurements") %></strong></td></tr>
<c:forEach items="${items}" var="item">
<tr valign="top">
<td>${item.label}
<select name="biomeasurement${item.type}(operator)">
<option value="gteq">&gt;=</option>
<option value="lteq">&lt;=</option>
  <option value="gt">&gt;</option>
  <option value="lt">&lt;</option>
  <option value="eq">=</option>
</select>
<input name="biomeasurement${item.type}(value)"/>(<c:out value="${item.unitsLabel})"/>
</td>
</tr>
</c:forEach>
<tr><td></td></tr>
</table>
    
      <p><strong><%=encprops.getProperty("msmarker")%>:</strong> 
      <span class="para">
      	<a href="<%=CommonConfiguration.getWikiLocation(context)%>loci" target="_blank">
      		<img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/>
      	</a>
      </span> 
   </p>
<p>

      <%
        List<String> loci = myShepherd.getAllLoci();
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
      <font color="#000000"><%=encprops.getProperty("metadataFilters") %></font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="metadata" style="display:none; ">
      <p><%=encprops.getProperty("metadataInstructions") %></p>
      <table width="720px" align="left">
        <tr>
          <td width="154">
          <p><strong><%=encprops.getProperty("types2search")%></strong></p>
     		<%
     		List<String> values=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
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
		
		<tr>
  <td><br /><strong><%=encprops.getProperty("submitterName")%></strong>
    <input name="nameField" type="text" size="60"> <br> <em><%=encprops.getProperty("namesBlank")%>
    </em>
  </td>
</tr>



<tr>
  <td><br /><strong><%=encprops.getProperty("additionalComments")%></strong>
    <input name="additionalCommentsField" type="text" size="60"> <br> <em><%=encprops.getProperty("commentsBlank")%>
    </em>
  </td>
</tr>

<tr>
<td>

      <%
      	Shepherd inShepherd=new Shepherd("context0");
      inShepherd.setAction("encounterSearch.jsp2");
      myShepherd.beginDBTransaction();
        List<User> users = inShepherd.getAllUsers();
        int numUsers = users.size();

      %>
	<br /><strong><%=encprops.getProperty("username")%></strong><br />
      <select multiple size="5" name="username" id="username">
        <option value="None"></option>
        <%
          for (int n = 0; n < numUsers; n++) {
            String username = users.get(n).getUsername();
            String userFullName=username;
            if(users.get(n).getFullName()!=null){
            	userFullName=users.get(n).getFullName();
            }
            
        	%>
        	<option value="<%=username%>"><%=userFullName%></option>
        	<%
          }
        %>
      </select>
<%
inShepherd.rollbackDBTransaction();
inShepherd.closeDBTransaction();

%>

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

    <p><em> <input name="submitSearch" type="submit"
                   id="submitSearch" value="<%=encprops.getProperty("goSearch")%>"></em>

  </td>
</tr>
</table>
</form>
</td>
</tr>
</table>
<br />
</div>
<jsp:include page="../footer.jsp" flush="true"/>



