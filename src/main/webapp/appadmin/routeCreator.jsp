<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.movement.Path,
org.json.JSONObject, org.json.JSONArray,
org.joda.time.DateTime,
java.util.Properties" %>
<link rel="stylesheet" href="https://cdn.datatables.net/1.10.24/css/jquery.dataTables.min.css">
<style>
#table_div_id.dataTables_filter {
  float: left;
  text-align: left;
}
div.dt-buttons {
    position: relative;
    float: left;
}
</style>
<%
String context = ServletUtilities.getContext(request);
String mapKey = CommonConfiguration.getGoogleMapsKey(context);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

    request.setAttribute("pageTitle", "Route Creator");

%>
<jsp:include page="../header.jsp" flush="true"/>
<div class="container maincontent">
<table id="route-list" class="display" style="width:100%">
        <thead>
            <tr>
                <th>Name</th>
                <th>Location Id</th>
                <th>Start Time</th>
                <th>End Time</th>
                <th>Preview</th>
                <th>Action</th>
            </tr>
        </thead>
</table>
</div>
<script type="text/javascript"
  src="https://maps.googleapis.com/maps/api/js?key=<%=mapKey%>&libraries=drawing">
</script>
<script src="https://cdn.datatables.net/1.10.24/js/jquery.dataTables.min.js"></script>
<script type="text/javascript" src="cust/preview-image.js"></script>
<script>
var drawingManager;
var selectedShape;
var map;
var data;
var s = [];
var dataTable = "";
function clearSelection() {
  if (selectedShape) {
    selectedShape.setEditable(false);
    selectedShape = null;
  }
}
function setSelection(shape) {
  clearSelection();
  selectedShape = shape;
  shape.setEditable(true);

}
function deleteSelectedShape() {
	$("#pt-data").val("");
  if (selectedShape) {
    selectedShape.setMap(null);
  }
}
function initialize() {
	var map = new google.maps.Map(document.getElementById('map'), {
		zoom: 8,
		center: new google.maps.LatLng(47.609722, -122.333056)
	});

	// Creates a drawing manager attached to the map that allows the user to draw
	// markers, lines, and shapes.
	drawingManager = new google.maps.drawing.DrawingManager({
		drawingMode: google.maps.drawing.OverlayType.POLYLINE,
		map: map
	});
	drawingManager.setOptions({
		drawingControlOptions: {
			position: google.maps.ControlPosition.TOP_CENTER,
			drawingModes: ['polyline'],
		}
	});

  	google.maps.event.addListener(drawingManager, 'overlaycomplete', function(e) {
		if (e.type != google.maps.drawing.OverlayType.MARKER) {
			// Switch back to non-drawing mode after drawing a shape.
			drawingManager.setDrawingMode(null);
	
			// Add an event listener that selects the newly-drawn shape when the user
			// mouses down on it.
			var newShape = e.overlay;
			newShape.type = e.type;
		      google.maps.event.addListener(newShape, 'click', function() {
		        setSelection(newShape);
		      });
			setSelection(newShape);
    	}
  	});

  	// Clear the current selection when the drawing mode is changed, or when the
  	// map is clicked.
  	google.maps.event.addListener(drawingManager, 'drawingmode_changed', clearSelection);
  	google.maps.event.addListener(map, 'click', clearSelection);
  	google.maps.event.addDomListener(document.getElementById('clear-routes'), 'click', deleteSelectedShape);
  	google.maps.event.addListener(drawingManager, 'polylinecomplete', function(ev) {
  		data = ev;
  		console.log(ev);
  		ev.latLngs.forEach(function(arr) {
  			arr.forEach(function(ll) {
  				console.log(ll);
  				s.push( [ll.lat(), ll.lng()] );
  			});
  		});
  		updatePoints(s);
  	});
}

function deleteRoute(id){
	if(confirm("Are you sure you want Delete ?")){
	 $.ajax({
   	  url: "../RouteList?action=delete&id="+id,
   	  cache: false,
   	  success: function(html){
   	    $("#results").append(html);
   	 	getRouteList();
   	  }
   	});
	}
}

function showPreview(e){
	$(e).parent().find("span").show();
}

function hidePreview(e){
	$(e).parent().find("span").hide();
}

function getRouteList(){
	if(dataTable !== ""){
		dataTable.destroy();
	}
	dataTable = $("#route-list").DataTable({
		"processing" : true,
		"serverside" : false,
		dom: 'Bfrtip',
		"ajax" : "../RouteList?action=getList",
		"columns" : [
			{
				data : "name"
			},
			{
				data : "locationId"
			},
			{
				data : "startTime"
			},
			{
				data : "endTime"
			},
			{
				data : "path", 
				render : function ( data, type, row, meta ) {
					var a = data;
					var previewURL = 'https://maps.googleapis.com/maps/api/staticmap?size=400x400&key=AIzaSyDTXMFUMTbIRCo905mxcPGPP1RNBnfCkQw&path=47.678969535962956,-122.28155758691406|47.51969336834902,-122.27057125878906|47.471444639572866,-122.29803707910156';
						return '<span style="position: absolute; display:none; z-index:1" ><img src="'+previewURL+'" /></span> <a href="#" data-preview-image="'+previewURL+'"><i class="el el-picture"></i></a>';
				}
			},
			{
				data : "id", 
				render : function ( data, type, row, meta ) {
					var a = data;
					return '<a href = "#" onClick = "deleteRoute(\''+ data +'\');"><i class="el el-remove-sign"></i></a>';
				}
			},
		]
	});
}


$(document).ready(function() {
	google.maps.event.addDomListener(window, 'load', initialize);
    $('#startTime').val(new Date().toISOString());
    $('#endTime').val(new Date(new Date().getTime() + 600000).toISOString());
    getRouteList();
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
        url: "../RouteList?action=save",
        data: JSON.stringify(data),
        type: 'POST',
        dataType: "json",
        contentType: 'application/json',
        success : function(data) {
            console.log(data);
            if (data && data.success && data.routeId) {
            	$("#pt-data,#name ").val("");
                $('#save-status').html('saved Route id=<b>' + data.routeId + '</b>');
                getRouteList();
                document.getElementById('clear-routes').click();
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

<p id="save-status"></p>
<input type="button" value="save" onclick="saveData()" />
<input type="button" value="clear" id="clear-routes">
</body>
</html>


</div>

<jsp:include page="../footer.jsp" flush="true"/>
