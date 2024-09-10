<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.ArrayList,java.util.*,org.ecocean.security.Collaboration" %>



<%
String context="context0";
context=ServletUtilities.getContext(request);
Properties props = new Properties();
String langCode=ServletUtilities.getLanguageCode(request);
props = ShepherdProperties.getProperties("individuals.properties", langCode, context);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("individualMapEmbed.jsp");
myShepherd.beginDBTransaction();
Vector haveGPSData = new Vector();
String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String noGPS = props.getProperty("noGPS");
String mapping = props.getProperty("mapping");

Double lat = null;
Double lon = null;
boolean hasData = false;
String latLonStr = null;
String occID = null;
boolean visible =false;

if(request.getParameter("occurrence_number")!=null){
  occID = request.getParameter("occurrence_number");
  Occurrence occur=myShepherd.getOccurrence(occID);
  lat = occur.getDecimalLatitude();
  lon = occur.getDecimalLongitude();
  visible = Collaboration.canUserAccessOccurrence(occur, request);
}
hasData = (lat!=null & lon!=null);
if (hasData) latLonStr = lat.toString()+","+lon.toString();


//System.out.println("occurrenceMapEmbed begun for occ "+occID+": ("+lat+","+lon+")");
try {
%>

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script type="text/javascript" src="javascript/markerclusterer/markerclusterer.js"></script>
<script type="text/javascript" src="https://cdn.rawgit.com/googlemaps/js-marker-clusterer/gh-pages/src/markerclusterer.js"></script> 
<script src="javascript/oms.min.js"></script>
<p><strong><%=mapping %></strong></p>

<script type="text/javascript">

  var map;
  var center = new google.maps.LatLng(<%=lat%>,<%=lon%>);
  var marker;


  function placeMarker(location) {
    if(marker!=null){marker.setMap(null);}
      marker = new google.maps.Marker({
        position: location,
        map: map,
        <%
        if(visible){
        %>
        visible: true
        <%
      	}
        else{
        %>
        visible: false
        <%
        }
        %>
        
      });

              //map.setCenter(location);

    var ne_lat_element = document.getElementById('lat');
    var ne_long_element = document.getElementById('longitude');


    ne_lat_element.value = location.lat();
    ne_long_element.value = location.lng();
  }
  function initialize() {
    var mapZoom = 2;
    var center = new google.maps.LatLng(<%=lat%>,<%=lon%>);
    map = new google.maps.Map(document.getElementById('map_canvas'), {
      zoom: mapZoom,
      center: center,
      mapTypeId: google.maps.MapTypeId.HYBRID,
      zoomControl: true,
      scaleControl: false,
      scrollwheel: false,
      disableDoubleClickZoom: true,
    });

    marker = new google.maps.Marker({
      position:center,
      map:map
    });
    //console.log("initialize google maps ending with marker = "+marker);

    // google.maps.event.addListener(map, 'click', function(event) {
    //   placeMarker(event.latLng);
    // });
  }
  google.maps.event.addDomListener(window, 'load', initialize);

</script>


<%
if(request.getParameter("occurrence_number")!=null && hasData){
  %>
	<p><%=props.getProperty("occurrenceAdditionalMappingNote") %></p>
  <div id="map_canvas" style="width: 100%; height: 500px;"></div>
  <%
} else {
%>
<p><%=noGPS %></p>
<br />
<%
}
%>
<p>&nbsp;</p>


<%
  }
  catch (Exception e) {e.printStackTrace();}
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
  myShepherd = null;
%>
