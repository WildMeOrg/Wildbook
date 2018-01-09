<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.ArrayList,java.util.*,org.ecocean.movement.*" %>

<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2017 Jason Holmberg
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

<%
String context=ServletUtilities.getContext(request);
Properties props = new Properties();
String langCode=ServletUtilities.getLanguageCode(request);

props = ShepherdProperties.getProperties("survey.properties", langCode, context);

Properties locationProps = new Properties();
locationProps = ShepherdProperties.getProperties("locationIDGPS.properties", "",context);

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("surveyMapEmbed.jsp");
myShepherd.beginDBTransaction();

ArrayList<Occurrence> occsWithGps = new ArrayList<Occurrence>();
ArrayList<SurveyTrack> trks = new ArrayList<>();
String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String number = request.getParameter("number").trim();
Survey sv = myShepherd.getSurvey(number);  
trks = sv.getAllSurveyTracks();
%>

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script type="text/javascript" src="javascript/markerclusterer/markerclusterer.js"></script>
<script type="text/javascript" src="https://cdn.rawgit.com/googlemaps/js-marker-clusterer/gh-pages/src/markerclusterer.js"></script> 
<script src="javascript/oms.min.js"></script>
<p><strong><%=props.getProperty("surveyMap") %></strong></p>
<div id="map"></div>
<script>

  // This example creates a 2-pixel-wide red polyline showing the path of William
  // Kingsford Smith's first trans-Pacific flight between Oakland, CA, and
  // Brisbane, Australia.

  function initMap() {
    var map = new google.maps.Map(document.getElementById('map'), {
      zoom: 3,
      center: {lat: 35.216399, lng: -75.688132},
      mapTypeId: 'terrain'
    });

    var surveyCoordinates = [
      {lat: 37.772, lng: -122.214},
      {lat: 21.291, lng: -157.821},
      {lat: -18.142, lng: 178.431},
      {lat: -27.467, lng: 153.027}
    ];
    var flightPath = new google.maps.Polyline({
      path: flightPlanCoordinates,
      geodesic: true,
      strokeColor: '#FF0000',
      strokeOpacity: 1.0,
      strokeWeight: 2
    });

    flightPath.setMap(map);
  }
</script>


