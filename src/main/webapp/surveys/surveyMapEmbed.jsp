<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.ArrayList,java.util.*,org.ecocean.movement.*" %>


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
	if (occsWithGps!=null) {
		for (Occurrence trackOcc : occsWithGps) {
			String lat = String.valueOf(trackOcc.getDecimalLatitude());
			String lon = String.valueOf(trackOcc.getDecimalLongitude());
			lineSet += "{lat: "+lat+", lng: "+lon+"},";
			System.out.println(lineSet);
		}		
		center = lineSet.split(",")[0]+","+lineSet.split(",")[1];
		lineSet = lineSet.substring(0,lineSet.length()-1);
		polyLineSets.add(lineSet);
	} else {
		center = "{lat: 35.2195, lng: -75.6903}";
	}
}
%>
<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js"></script>
<script src="<%=urlLoc %>/tools/bootstrap/js/bootstrap.min.js"></script>

<p><strong><%=props.getProperty("surveyMap") %></strong></p>
<%
if (sv!=null) {
%>
	<p>Survey: <%=sv.getID()%></p>
<%
}
%>

<div style="height:500px;" id="map"></div>

<script defer>
$(document).ready(function() {
  function initMap() {
	console.log("Center : "+"<%=center%>");
	
    var map = new google.maps.Map(document.getElementById('map'), {
      zoom: 10,
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


