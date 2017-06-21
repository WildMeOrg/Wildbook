<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, javax.jdo.Extent, javax.jdo.Query, java.util.ArrayList, java.util.List, java.util.GregorianCalendar, java.util.Iterator, java.util.Properties, java.io.IOException" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%!
// here I'll define some methods that will end up in classEditTemplate

public static void printStringFieldSearchRow(String fieldName, javax.servlet.jsp.JspWriter out, Properties nameLookup) throws IOException, IllegalAccessException {
  // note how fieldName is variously manipulated in this method to make element ids and contents
  String displayName = getDisplayName(fieldName, nameLookup);
  out.println("<tr id=\""+fieldName+"Row\">");
  out.println("  <td id=\""+fieldName+"Title\">"+displayName+"</td>");
  out.println("  <td><input name=\""+fieldName+"\"/></td>");
  out.println("</tr>");

}

public static String getDisplayName(String fieldName, Properties nameLookup) throws IOException, IllegalAccessException {
  // Tries to lookup a translation and defaults to some string manipulation
  return (nameLookup.getProperty(fieldName, ClassEditTemplate.prettyFieldName(fieldName)));
}
%>



<%
String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  //myShepherd.setAction("individualSearch.jsp");
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);

  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);

  int firstYear = 1980;
  myShepherd.beginDBTransaction();
  try {
    firstYear = myShepherd.getEarliestSightingYear();
    nowYear = myShepherd.getLastSightingYear();
  } catch (Exception e) {
    e.printStackTrace();
  }

//let's load out properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearch.properties"));
  // Properties occProps = new Properties();
  // occProps = ShepherdProperties.getProperties("occurrence.properties", langCode,context);
  Properties occProps = ShepherdProperties.getProperties("occurrence.properties", langCode,context);

  props = ShepherdProperties.getProperties("individualSearch.properties", langCode,context);

%>


<jsp:include page="header.jsp" flush="true"/>

    <!-- Sliding div content: STEP1 Place inside the head section -->
  <script type="text/javascript" src="javascript/animatedcollapse.js"></script>

  <script type="text/javascript">
    //animatedcollapse.addDiv('location', 'fade=1')
    animatedcollapse.addDiv('map', 'fade=1')
    animatedcollapse.addDiv('date', 'fade=1')
    animatedcollapse.addDiv('observation', 'fade=1')
    animatedcollapse.addDiv('tags', 'fade=1')
    animatedcollapse.addDiv('identity', 'fade=1')
    animatedcollapse.addDiv('metadata', 'fade=1')
    animatedcollapse.addDiv('export', 'fade=1')
    animatedcollapse.addDiv('genetics', 'fade=1')
	animatedcollapse.addDiv('social', 'fade=1')
	animatedcollapse.addDiv('patternrecognition', 'fade=1')

    animatedcollapse.ontoggle = function($, divobj, state) { //fires each time a DIV is expanded/contracted
      //$: Access to jQuery
      //divobj: DOM reference to DIV being expanded/ collapsed. Use "divobj.id" to get its ID
      //state: "block" or "none", depending on state
    }
    animatedcollapse.init()
  </script>
  <!-- /STEP2 Place inside the head section -->

<script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode %>"></script>
<script src="encounters/visual_files/keydragzoom.js" type="text/javascript"></script>
<script type="text/javascript" src="javascript/geoxml3.js"></script>
<script type="text/javascript" src="javascript/ProjectedOverlay.js"></script>

  <!-- /STEP2 Place inside the head section -->




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

  }
</script>

<div class="container maincontent">
<table width="720">
<tr>
<td>
<p>
<%
String titleString=occProps.getProperty("OccurrenceSearch");
String formAction="occurrenceSearchResults.jsp";
%>


<h1 class="intro"><strong><span class="para">
		<img src="images/wild-me-logo-only-100-100.png" width="50" align="absmiddle"/></span></strong>
  <%=titleString%>
</h1>
</p>

<%
if((request.getParameter("individualDistanceSearch")!=null)||(request.getParameter("encounterNumber")!=null)){
	MarkedIndividual compareAgainst=new MarkedIndividual();
	if((request.getParameter("individualDistanceSearch")!=null)&&(myShepherd.isMarkedIndividual(request.getParameter("individualDistanceSearch")))){
		compareAgainst=myShepherd.getMarkedIndividual(request.getParameter("individualDistanceSearch"));
	}
	else if((request.getParameter("encounterNumber")!=null)&&(myShepherd.isEncounter(request.getParameter("encounterNumber")))){
		Encounter enc=myShepherd.getEncounter(request.getParameter("encounterNumber"));
		if((enc.getIndividualID()!=null)&&(myShepherd.isMarkedIndividual(enc.getIndividualID()))){
			compareAgainst=myShepherd.getMarkedIndividual(enc.getIndividualID());
		}
	}

    List<String> loci=myShepherd.getAllLoci();
    int numLoci=loci.size();
    String[] theLoci=new String[numLoci];
    for(int q=0;q<numLoci;q++){
    	theLoci[q]=loci.get(q);
    }

    String compareAgainstAllelesString=compareAgainst.getFomattedMSMarkersString(theLoci);


%>

<p>Reference Individual ID: <%=compareAgainst.getIndividualID() %>
<%
String compareAgainstHaplotype="";
if(compareAgainst.getHaplotype()!=null){
	compareAgainstHaplotype=compareAgainst.getHaplotype();
}
String compareAgainstGeneticSex="";
if(compareAgainst.getGeneticSex()!=null){
	compareAgainstGeneticSex=compareAgainst.getGeneticSex();
}
%>
<br/>Haplotype: <%=compareAgainstHaplotype %>
<br/>Genetic sex: <%=compareAgainstGeneticSex %>
		<table>
			<tr><td colspan="<%=(numLoci*2)%>">Microsatellite markers</td></tr>
				<tr>
					<%
					for(int y=0;y<numLoci;y++){
					%>
						<td><span style="font-style: italic"><%=theLoci[y] %></span></td><td><span style="font-style: italic"><%=theLoci[y] %></span></td>
					<%
					}
					%>
				</tr>


				<tr>
					<td><span style="color: #909090"><%=compareAgainstAllelesString.replaceAll(" ", "</span></td><td><span style=\"color: #909090\">") %></span></td>
				</tr>

			</table>

</p>
<%
}

%>
<p><em><strong><%=occProps.getProperty("searchInstructions")%>
</strong></em></p>


<form action="<%=formAction %>" method="get" name="search" id="search">
    <%
	if(request.getParameter("individualDistanceSearch")!=null){
	%>
		<input type="hidden" name="individualDistanceSearch" value="<%=request.getParameter("individualDistanceSearch") %>" />
	<%
	}
    %>
<table width="810px">

<tr>
  <td width="810px">

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><img
      src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/></a> <a
      href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><font
      color="#000000"><%=props.getProperty("locationFilter") %></font></a></h4>
  </td>
</tr>
<tr>
  <td width="810px">

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
    		content:'<%=props.getProperty("loadingMapData") %>',
    		position:center});

    	iw.open(map);

    	google.maps.event.addListener(map, 'center_changed', function(){iw.close();});



		  overlaysSet=true;
      }

   }

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
      controlText.innerHTML = '<%=props.getProperty("exitFullscreen") %>';
    } else {
      controlText.innerHTML = '<%=props.getProperty("fullscreen") %>';
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
      <p><%=props.getProperty("useTheArrow") %></p>

      <div id="map_canvas" style="width: 770px; height: 510px; ">
      		<div style="padding-top: 5px; padding-right: 5px; padding-bottom: 5px; padding-left: 5px; z-index: 0; position: absolute; right: 95px; top: 0px; " >

      		</div>
      </div>

      <div id="map_overlay_buttons">

          <input type="button" value="<%=props.getProperty("loadMarkers") %>" onclick="setOverlays();" />&nbsp;


      </div>
      <p><%=props.getProperty("northeastCorner") %> <%=props.getProperty("latitude") %> <input type="text" id="ne_lat" name="ne_lat"></input> <%=props.getProperty("longitude") %>
        <input type="text" id="ne_long" name="ne_long"></input><br/><br/>
        <%=props.getProperty("southwestCorner") %> <%=props.getProperty("latitude") %> <input type="text" id="sw_lat" name="sw_lat"></input> <%=props.getProperty("longitude") %>
        <input type="text" id="sw_long" name="sw_long"></input></p>
    </div>

  </td>
</tr>

<tr>

</tr>


<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('date')" style="text-decoration:none"><img
      src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/> <font
      color="#000000"><%=props.getProperty("dateFilters") %></font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="date" style="display:none;">
      <p><%=props.getProperty("dateInstructions") %></p>

<!--  date of birth and death -->
      <p><strong><%=occProps.getProperty("dateStart")+" "+occProps.getProperty("range")%>:</strong></p>
      <table>
      	<tr>
      		<td><%=occProps.getProperty("start") %> <input type="text" id="eventStartDate-From" name="eventStartDate-From" class="addDatePicker"/></td>
      		<td><%=occProps.getProperty("end") %> <input type="text" id="eventStartDate-To" name="eventStartDate-To" class="addDatePicker"/></td>
      	</tr>
      </table>

      <p><strong><%=occProps.getProperty("dateEnd")+" "+occProps.getProperty("range")%>:</strong></p>
      <table>
      	<tr>
      		<td><%=occProps.getProperty("start") %> <input type="text" id="eventEndDate-From" name="eventEndDate-From" class="addDatePicker"/></td>
      		<td><%=occProps.getProperty("end") %> <input type="text" id="eventEndDate-To" name="eventEndDate-To" class="addDatePicker"/></td>
      	</tr>
      </table>

      <script>
      $(function() {
        $('.addDatePicker').datepicker();
        console.log("Done setting datepickers!");
      });
      </script>

    </div>
  </td>
</tr>







<%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
%>



  <tr id="FieldsTitleRow">
    <td>
      <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
        href="javascript:animatedcollapse.toggle('tags')" style="text-decoration:none"><img
        src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/> <font
        color="#000000"><%=occProps.getProperty("fieldsTitle") %></font></a></h4>
    </td>
  </tr>

  <tr id="fieldsContentRow">
    <td>
        <div id="tags" style="display:none;">
            <p><%=occProps.getProperty("fieldsInstructions") %></p>


            <%
            // here we'll programatically create divs that allow for searching through metadata fields

            %>

              <h5>Zebra Group Fields</h5>
              <table>

              <%
              for (String fieldName : OccurrenceQueryProcessor.SIMPLE_STRING_FIELDS) {
                printStringFieldSearchRow(fieldName, out, occProps);
              }
              %>
              </table>
              </table>
           </div>
           </td>
           </tr>

<tr>
  <td>

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('metadata')" style="text-decoration:none"><img
      src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000"><%=props.getProperty("metadataFilters") %></font></a></h4>
  </td>
</tr>

<tr>
<td>
  <div id="metadata" style="display:none; ">
  <p><%=props.getProperty("metadataInstructions") %></p>

	<strong><%=props.getProperty("username")%></strong><br />
      <%
      	Shepherd inShepherd=new Shepherd("context0");
      //inShepherd.setAction("individualSearch.jsp2");
        List<User> users = inShepherd.getAllUsers();
        int numUsers = users.size();

      %>

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
</div>
</td>
</tr>




<%
  myShepherd.rollbackDBTransaction();
%>


<tr>
  <td>


  </td>
</tr>
</table>
<br />
<input name="submitSearch" type="submit" id="submitSearch"
                   value="<%=props.getProperty("goSearch")%>" />
</form>
</td>
</tr>
</table>
<br>
</div>

<script>
/* the below function removes any blank-valued params from the form just before submitting, making the searchResults.jsp url MUCH cleaner and more readable */
$('#submitSearch').submit(function() {
  $(this)
    .find('input[name]')
    .filter(function () {
        return !this.value;
    })
    .prop('name', '');
  });
</script>



<jsp:include page="footer.jsp" flush="true"/>


<%
  kwQuery.closeAll();
  myShepherd.closeDBTransaction();
  kwQuery = null;
  myShepherd = null;
%>
