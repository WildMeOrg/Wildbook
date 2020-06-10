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
    route.setName(jsonIn.optString("name", null));
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
    var pathArr = null;
    try {
        pathArr = JSON.parse($('#pt-data').val());
    } catch(err) {
        alert('error in path value: ' + err);
        return;
    }
    if (!pathArr || !Array.isArray(pathArr)) {
        alert('error in path value - bad array');
        return;
    }

    $('#save-status input').hide();
    var data = {
        locationId: $('#locationId').val(),
        name: $('#name').val(),
        startTime: $('#startTime').val(),
        endTime: $('#endTime').val(),
        path: pathArr
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
            if (x && x.responseJSON && x.responseJSON.success && x.responseJSON.routeId) {
                $('#save-status').html('saved Route id=<b>' + x.responseJSON.routeId + '</b>');
            } else {
                $('#save-status').html('<div class="error">error saving</div>');
            }
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
<%
    JSONObject lj = LocationID.getLocationIDStructure();
    JSONArray jarr = lj.optJSONArray("locationID");
    if (jarr != null) for (int i = 0 ; i < jarr.length() ; i++) {
        JSONObject jloc = jarr.optJSONObject(i);
        if (jloc == null) continue;
        String lid = jloc.optString("id", null);
        if (lid == null) continue;
        String name = jloc.optString("name", lid);
        out.println("<option value=\"" + lid + "\">" + name + "</option>");
    }
%>
</select>
</div>

<div>
<input id="startTime" /> start time <br />
<input id="endTime" /> end time <br />
<input id="name" /> name
</div>

<div>
<p><div id="pt-info"></div></p>
<textarea id="pt-data" style="padding-top: 20px; width: 700px; height: 20em;"></textarea>
</div>

<p id="save-status">
<input type="button" value="save" onclick="saveData()" />
</p>

</body>
</html>


</div>

<jsp:include page="../footer.jsp" flush="true"/>
