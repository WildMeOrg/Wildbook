<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.ArrayList,java.util.List,
java.util.Properties,
org.json.JSONObject,
org.json.JSONArray,
org.ecocean.movement.*" %>


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

String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String center = CommonConfiguration.getDefaultGoogleMapsCenter(context);
String number = null;
Survey sv = null;
JSONObject data = new JSONObject();
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

JSONArray trkArr = new JSONArray();
for (SurveyTrack trk : sv.getSurveyTracks()) {
    JSONObject jt = new JSONObject();
    Path path = trk.getPath();
    if (path != null) {
        int np = path.getPointLocations().size();
        if (np > 100) np = Math.round(np / 10);
        jt.put("pathPoints", Path.toJSONArray(path.getPointLocationsSubsampled(Math.round(np)))); 
    }
    JSONArray joccs = new JSONArray();
    if (!Util.collectionIsEmptyOrNull(trk.getOccurrences())) for (Occurrence occ : trk.getOccurrences()) {
        JSONObject jocc = new JSONObject();
        jocc.put("id", occ.getOccurrenceID());
        jocc.put("lat", occ.getDecimalLatitude());
        jocc.put("lon", occ.getDecimalLongitude());
        jocc.put("time", occ.getMillisFromEncounterAvg());
        jocc.put("dateTimeCreated", occ.getDateTimeCreated());
        jocc.put("numEncounters", occ.getNumberEncounters());
        joccs.put(jocc);
    }
    jt.put("occurrences", joccs);
    trkArr.put(jt);
}
data.put("surveyTracks", trkArr);
%>
<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>

<div style="height:500px;" id="map"></div>

<script defer>
var data = <%=data.toString(4)%>;
var map;

$(document).ready(function() {
    initMap();
});

function initMap() {
    map = new google.maps.Map(document.getElementById('map'), {
      zoom: 11,
      center: findCenter(),
      mapTypeId: 'terrain',
      gestureHandling: 'greedy'
    });

    google.maps.event.addListenerOnce(map, 'idle', function() {
        drawSurvey();
    });
}


function findCenter() {
    for (var i = 0 ; i < data.surveyTracks.length ; i++) {
        for (var j = 0 ; j < data.surveyTracks[i].occurrences.length ; j++) {
            var occ = data.surveyTracks[i].occurrences[j];
                if (occ.lat && occ.lon) return { lat: occ.lat, lng: occ.lon };
        }
    }
}

function drawSurvey() {
    for (var i = 0 ; i < data.surveyTracks.length ; i++) {
        drawSurveyTrack(data.surveyTracks[i]);
    }
}

function drawSurveyTrack(trk) {
    for (var i = 0 ; i < trk.occurrences.length ; i++) {
        drawOccurrence(trk.occurrences[i], i);
    }
    drawPath(trk.pathPoints);
}

function drawPath(pathPoints) {
    var gpath = [];
    for (var i = 0 ; i < pathPoints.length ; i++) {
        gpath.push({ lat: pathPoints[i].latitude, lng: pathPoints[i].longitude });
    }
    var line = new google.maps.Polyline({
        path: gpath,
        geodesic: true,
        strokeColor: '#55A',
        strokeOpacity: 1.0,
        strokeWeight: 2
    });
    line.setMap(map);
}

function drawOccurrence(occ, occI) {
    if (!occ.lon || !occ.lat) {
        console.info("skipping %s - no geo! %o", occ.id, occ);
    } else {
        var marker = new google.maps.Marker({
            position: new google.maps.LatLng(occ.lat, occ.lon),
            title: '(' + String.fromCharCode(65 + occI) + ') ' + (occ.dateTimeCreated ? occ.dateTimeCreated.substring(11,16) : '') + ' [' + occ.id.substring(0,8) + ']',
            map: map,
        });
        marker.setLabel(String.fromCharCode(65 + occI));
        google.maps.event.addListener(marker, 'click', function() {
            var content = '<p><b><a href="../occurrence.jsp?number=' + occ.id + '">Sight ' + occ.id.substring(0,8) + '</a></b><br />';
            content += '<b>' + occ.dateTimeCreated + '</b></p>';
            content += '<p>Lat: <b>' + occ.lat + '</b><br />Lon: <b>' + occ.lon + '</b></p>';
            (new google.maps.InfoWindow({content: content })).open(map, this);
        });
    }
}

 
 function generateColor() {
	 //console.log("Generating...");
	 var randomColor = "#";
	 var hexArr = ['0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'];
	 for (i=0;i<6;i++) {
		 var index = Math.floor(Math.random()*16);
		 //console.log("Index... : "+index);
		 randomColor+= hexArr[index];
	 }
	 return randomColor;
 }

</script>
<%


myShepherd.closeDBTransaction();
%>


