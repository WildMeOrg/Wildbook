<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.movement.Path,
org.json.JSONObject, org.json.JSONArray,
org.joda.time.DateTime,
java.util.Properties" %>
<%

String context = ServletUtilities.getContext(request);
String mapKey = CommonConfiguration.getGoogleMapsKey(context);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

if (request.getParameter("save") != null) {
    JSONObject rtn = new JSONObject();
    JSONObject jsonIn = ServletUtilities.jsonFromHttpServletRequest(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("routeCreator-save");
    myShepherd.beginDBTransaction();
    Route route = new Route();
    route.setStartTime(new DateTime(jsonIn.optString("startTime", null)));
    route.setEndTime(new DateTime(jsonIn.optString("endTime", null)));
    route.setLocationId(jsonIn.optString("locationId", null));
    route.setPath(Path.fromJSONArray(jsonIn.optJSONArray("path")));
    myShepherd.getPM().makePersistent(route);
    myShepherd.commitDBTransaction();
    rtn.put("success", true);
    rtn.put("routeId", route.getId());
    out.println(rtn.toString(4));
    return;
}


    request.setAttribute("pageTitle", "Route Creator");

%>
<jsp:include page="../header.jsp" flush="true"/>

<script type="text/javascript"
  src="https://maps.googleapis.com/maps/api/js?key=<%=mapKey%>&libraries=drawing">
</script>
<script>
var map;
var drawingManager;

var data;

function mapSetup() {
	map = new google.maps.Map(document.getElementById('map'), {
    center: {lat: 47.609722, lng: -122.333056 },  //TODO center based on pulldown of locationId
    zoom: 8
  });

drawingManager = new google.maps.drawing.DrawingManager({
	drawingMode: google.maps.drawing.OverlayType.POLYLINE,
	drawingControlOptions: {
      position: google.maps.ControlPosition.TOP_CENTER,
      drawingModes: ['polyline'],
    },
});
drawingManager.setMap(map);

google.maps.event.addListener(drawingManager, 'polylinecomplete', function(ev) {
	data = ev;
	console.log(ev);
	var s = [];
	ev.latLngs.forEach(function(arr) {
		arr.forEach(function(ll) {
			console.log(ll);
			s.push( [ll.lat(), ll.lng()] );
		});
	});
	updatePoints(s);
});

}

$(document).ready(function() {
    mapSetup();
    $('#startTime').val(new Date().toISOString());
    $('#endTime').val(new Date(new Date().getTime() + 600000).toISOString());
});

function updatePoints(pts) {
    $('#pt-data').val(JSON.stringify(pts));
    $('#pt-info').html('<b>' + pts.length + '</b> points');
}

function saveData() {
    var data = {
        locationId: $('#locationId').val(),
        startTime: $('#startTime').val(),
        endTime: $('#endTime').val(),
        path: JSON.parse($('#pt-data').val())
    };
console.log('data=%o', data);
    $.ajax({
        url: 'routeCreator.jsp?save',
        data: JSON.stringify(data),
        contentType: 'application/javascript',
        dataType: 'json',
        type: 'POST',
        complete: function(x) {
            console.log(x);
        }
    });
}

</script>

<div class="container maincontent">

<div style="
padding: 10px;
	border: solid 3px blue;
width: 75%;
height: 400px;
margin: 10px;
" id="map"></div>


<div>
<select id="locationId">
<option>fix</option>
<option>me</option>
<option>please</option>
</select>
</div>

<div>
<input id="startTime" /> start time <br />
<input id="endTime" /> end time
</div>
<div id="pt-info"></div>

<input type="button" value="save" onclick="saveData()" />

<div>
<textarea id="pt-data" style="padding-top: 20px; width: 700px; height: 20em;"></textarea>
</div>

</body>
</html>


</div>

<jsp:include page="../footer.jsp" flush="true"/>
