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

String urlLoc = "//" + CommonConfiguration.getURLLocation(request);

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
	System.out.println("Number of svy-tracks: "+trks.size());
} catch (Exception e) {
	e.printStackTrace();
}
ArrayList<String> polyLineSets = new ArrayList<String>();
String center = "";
for (SurveyTrack trk : trks ) {
	String lineSet = "";
	System.out.println("Current track: "+trk.getID());
	ArrayList<Occurrence> occsWithGps = trk.getAllOccurrences();
	System.out.println("Number of occs for this track: "+occsWithGps.size());
	for (Occurrence trackOcc : occsWithGps) {
		String lat = String.valueOf(trackOcc.getDecimalLatitude());
		String lon = String.valueOf(trackOcc.getDecimalLongitude());
		lineSet += "{lat: "+lat+", lng: "+lon+"},";
		System.out.println(lineSet);
	}
	center = lineSet.split(",")[0]+","+lineSet.split(",")[1];
	lineSet = lineSet.substring(0,lineSet.length()-1);
	polyLineSets.add(lineSet);
}
%>
<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js"></script>
<script src="<%=urlLoc %>/tools/bootstrap/js/bootstrap.min.js"></script>

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
	<p>Survey: <%=sv.getID()%></p>
<%
}
%>

<div style="height:500px;" id="map"></div>

<script defer>
alert('Here\'s The map and stuff. Mapkey:'+'<%=mapKey%>');
$(document).ready(function() {
  function initMap() {
	console.log("Center : "+"<%=center%>");
    var map = new google.maps.Map(document.getElementById('map'), {
      zoom: 12,
      center: <%=center%>,
      mapTypeId: 'terrain',
      gestureHandling: 'greedy'
    });
	console.log('Initializing map...');
    var polyLines = [];
    <% 
    int currentPathNum = 0;
    for (String set : polyLineSets) {	
    	currentPathNum++;
    	String currentPath = "path"+currentPathNum;
    %>
	    var surveyCoordinates = [
				<%=set%>
	    ];  
    	console.log('Another coord set...'+'<%=set%>');
    	
    	var newColor = generateColor();
	    console.log(newColor);
	    
	    var <%=currentPath%> = new google.maps.Polyline({
	      path: surveyCoordinates,
	      geodesic: true,
	      strokeColor: newColor,
	      strokeOpacity: 1.0,
	      strokeWeight: 2
	    });
		<%=currentPath%>.setMap(map);
	
    <%
  	}
    %>
 }
 initMap();	
 
 function generateColor() {
	 console.log("Generating...");
	 var randomColor = "#";
	 var hexArr = ['1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'];
	 for (i=0;i<6;i++) {
		 var index = Math.floor(Math.random()*15);
		 console.log("Index... : "+index);
		 randomColor+= hexArr[index];
	 }
	 return randomColor;
 }
});  
</script>

<%
myShepherd.closeDBTransaction();
%>


