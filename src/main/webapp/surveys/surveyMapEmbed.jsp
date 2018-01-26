<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.ArrayList,java.util.*,org.ecocean.movement.*" %>


<%

String context=ServletUtilities.getContext(request);
Properties props = new Properties();
String langCode=ServletUtilities.getLanguageCode(request);

props = ShepherdProperties.getProperties("survey.properties", langCode, context);

Properties locationProps = new Properties();
locationProps = ShepherdProperties.getProperties("locationIDGPS.properties", "",context);

String mapUrlLoc = "//" + CommonConfiguration.getURLLocation(request);
String occLocation = mapUrlLoc + "/occurrence.jsp?number=";
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("surveyMapEmbed.jsp");
myShepherd.beginDBTransaction();

ArrayList<SurveyTrack> trks = new ArrayList<SurveyTrack>();
ArrayList<String> polyLines = new ArrayList<String>();
String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String number = null;
Survey sv = null;
try {	
	if (request.getParameter("surveyID")!=null) {
		number = request.getParameter("surveyID");		
	} 
	sv = myShepherd.getSurvey(number);
	System.out.println("Retreived this survey: "+sv.getID());
} catch (Exception e) {
	e.printStackTrace();
	System.out.println("Could not retreive survey and occurrence for this number.");
}
try {
	if (sv.getAllSurveyTracks()!=null) {
		trks = sv.getAllSurveyTracks();	
	}
	System.out.println("Number of svy-tracks: "+trks.size());
} catch (Exception e) {
	e.printStackTrace();
}
ArrayList<String> polyLineSets = new ArrayList<String>();
ArrayList<String> allMarkerSets = new ArrayList<String>();
ArrayList<String> infoWindowSets = new ArrayList<String>();
String center = "{lat: 35.2195, lng: -75.6903}";
for (SurveyTrack trk : trks ) {
	String lineSet = "";
	String markerSet = "";
	String infoWindowSet = "";
	System.out.println("Current track: "+trk.getID());
	ArrayList<Occurrence> occsWithGps = trk.getAllOccurrences();
	if (occsWithGps!=null) {
		for (Occurrence trackOcc : occsWithGps) {
			String startTime = null;
			String endTime = null;
			try {
				Encounter firstEnc = trackOcc.getEncounters().get(0);
				startTime = firstEnc.getStartDateTime();
				endTime = firstEnc.getEndDateTime();
			} catch (Exception e) {
				e.printStackTrace();
			}

			String lat = String.valueOf(trackOcc.getDecimalLatitude());
			String lon = String.valueOf(trackOcc.getDecimalLongitude());
			lineSet += "{lat: "+lat+", lng: "+lon+"},";
			markerSet += "["+lat+","+lon+"],";
			String link =  occLocation + trackOcc.getOccurrenceID();
			infoWindowSet += "<p><small><a href='"+link+"'>"+trackOcc.getOccurrenceID()+"</a></small</p>";
			infoWindowSet += "<p><small>Location ID: "+trackOcc.getLocationID()+"</small></p>";
			infoWindowSet += "<p><small>Lat/Lon: ["+lat+","+lon+"]</small></p>";
			if (startTime!=null&&endTime!=null) {
				if (startTime.equals(endTime)) {
					infoWindowSet += "<p><small>Start Time: "+startTime+"</small></p>";		
					infoWindowSet += "<p><small>End Time: None Recorded</small></p>";				
				} else {
					infoWindowSet += "<p><small>Start Time: "+startTime+"</small></p>";		
					infoWindowSet += "<p><small>End Time: "+endTime+"</small></p>";	
				}
			}
			infoWindowSets.add(infoWindowSet);
			infoWindowSet = "";
			System.out.println(lineSet);
			System.out.println(markerSet);
		}		
		center = lineSet.split(",")[0]+","+lineSet.split(",")[1];
		lineSet = lineSet.substring(0,lineSet.length()-1);
		markerSet = markerSet.substring(0,markerSet.length()-1);
		polyLineSets.add(lineSet);
		allMarkerSets.add(markerSet);
	} 
}
%>
<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>


<div style="height:500px;" id="map"></div>

<script defer>
$(document).ready(function() {
  function initMap() {
	console.log("Center : "+"<%=center%>");
	
    var map = new google.maps.Map(document.getElementById('map'), {
      zoom: 13,
      center: <%=center%>,
      mapTypeId: 'terrain',
      gestureHandling: 'greedy'
    });
	console.log('Initializing map...');
    var polyLines = [];
    <% 
    int currentPathNum = 0;
    for (int i=0; i<polyLineSets.size(); i++) {
    	String set = polyLineSets.get(i);
    	String markerSet = allMarkerSets.get(i);
    	currentPathNum++;
    	String currentPath = "path"+currentPathNum;
    %>
	    var surveyCoordinates = [
				<%=set%>
	    ];
	    var markerCoordinates = [
	    		<%=markerSet%>
	    ];
    	//console.log('Another coord set...'+'<%=set%>');
    	
    	var newColor = generateColor();
	    console.log(newColor);
	    
	    var <%=currentPath%> = new google.maps.Polyline({
	      path: surveyCoordinates,
	      geodesic: true,
	      strokeColor: newColor,
	      strokeOpacity: 1.0,
	      strokeWeight: 2
	    });
	    
	    var marker, i;	
	    var infWindows = [];

		<% 
		for (int j=0;j<infoWindowSets.size();j++) {
			String text = infoWindowSets.get(j);
			String index = String.valueOf(j);
		%>
			infWindows.push("<%=text%>");
			console.log(infWindows);
		<%
		} 
		%>
	    
	    for (i=0; i<markerCoordinates.length; i++) {  

			var iconColor = 'http://maps.google.com/mapfiles/ms/icons/red-dot.png';
			if (i===0) {
				iconColor = 'http://maps.google.com/mapfiles/ms/icons/green-dot.png';
			}

		    var marker = new google.maps.Marker({
		    	position: new google.maps.LatLng(markerCoordinates[i][i-i], markerCoordinates[i][1]),
				icon: iconColor,
		        map: map,
		    });
	        
		    //marker.setLabel(infWindows[i]);
	        //Careful with this i. The java and js are both running a for loop. 
	        console.log("I : "+i);
	        let infoWindowContent = infWindows[i];
	        console.log(infWindows[i]);
		    
            google.maps.event.addListener(marker,'click', function() {
            	console.log("I : "+i);
                (new google.maps.InfoWindow({content: infoWindowContent })).open(map, this);
            });
	    }
	    	    
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


