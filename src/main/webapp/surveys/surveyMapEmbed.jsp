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

ArrayList<SurveyTrack> trks = new ArrayList<SurveyTrack>();
ArrayList<String> polyLines = new ArrayList<String>();
String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String occID = request.getParameter("occID").trim();
System.out.println("Got this occ: "+occID);
String number = null;
Occurrence occ = null;
Survey sv = null;
try {
	
	System.out.println("Got this occID from params: "+occID);
	occ = myShepherd.getOccurrence(occID);
	System.out.println("Got this occ from DB: "+occ.getOccurrenceID());
	if (occ.getSurvey(myShepherd)!=null) {
		number = occ.getSurvey(myShepherd).getID();		
	} else {
		System.out.println("No Survey was found for this Occurrence.");
	}
	System.out.println("Got this survey number from the occ: "+number);
	sv = myShepherd.getSurvey(number);
	System.out.println("Retreived this survey: "+sv.getID());
	
} catch (Exception e) {
	e.printStackTrace();
	System.out.println("Could not retreive survey and occurrence for this number.");
}

ArrayList<Survey> svs = new ArrayList<Survey>();

try {
	trks = sv.getAllSurveyTracks();	
	System.out.println("Current survey: "+sv.toString());
} catch (Exception e) {
	e.printStackTrace();
}
ArrayList<String> polyLineSets = new ArrayList<String>();
for (SurveyTrack trk : trks ) {
	System.out.println("Current track: "+trk.getID());
	String lineSet = "";
	ArrayList<Occurrence> occsWithGps = trk.getAllOccurrences();
	for (Occurrence trackOcc : occsWithGps) {
		String lat = String.valueOf(trackOcc.getDecimalLatitude());
		String lon = String.valueOf(trackOcc.getDecimalLongitude());
		lineSet += "{lat: "+lat+", lng: "+lon+"},";
		System.out.println(lineSet);
	}
	lineSet = lineSet.substring(0,lineSet.length()-1);
	polyLineSets.add(lineSet);
}
%>
<p><strong><%=props.getProperty("surveyMap") %></strong></p>
<ul>
<%


for (Survey srvy : svs) {
	if (srvy.getAllSurveyTracks()!=null) {
		
%>
	<li>
		<%= srvy.getID() %>
		<%
		String svyID = "SVID : ";
		if (myShepherd.getOccurrenceForSurvey(srvy)!=null) {
			svyID += myShepherd.getOccurrenceForSurvey(srvy).getOccurrenceID();
		} else {
			svyID += "None.";
		}
		%>
		
		<%=svyID%>
	</li>
</ul>
<%
	}
}
if (sv!=null) {
%>
	<p>Survey: <%=sv.getID()%> LineSet:<%=polyLineSets.toString() %></p>
<%
}
%>
<div id="map">
</div>
<script>
alert('Test');
$(document).ready(function() {
  function initMap() {
    var map = new google.maps.Map(document.getElementById('map'), {
      zoom: 7,
      center: {lat: 35.216399, lng: -75.688132},
      mapTypeId: 'terrain'
    });

    var polyLines = [];
    var path = null;
    
    <% 
    for (String set : polyLineSets) {
    %>
	    var surveyCoordinates = [
	      <%=set%>
	    ];    	
	    path = new google.maps.Polyline({
	      path: surveyCoordinates,
	      geodesic: true,
	      strokeColor: '#FF0000',
	      strokeOpacity: 1.0,
	      strokeWeight: 2
	    });
	
    <%
  	}
    %>
	path.setMap(map);
	initMap();	 
 }
}); 
  
</script>

<%
myShepherd.closeDBTransaction();
%>


