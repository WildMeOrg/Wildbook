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
                <th>Path</th>
                <th>Action</th>
            </tr>
        </thead>
</table>
</div>
<script type="text/javascript"
  src="https://maps.googleapis.com/maps/api/js?key=<%=mapKey%>&libraries=drawing">
</script>
<script src="https://cdn.datatables.net/1.10.24/js/jquery.dataTables.min.js"></script>
<script>
//delete a record
function deleteRoute(id){
	if(confirm("Are you sure you want Delete ?")){
	 $.ajax({
   	  url: "../RouteList?action=delete&id="+id,
   	  cache: false,
   	  success: function(html){
   	    $("#results").append(html);
   	  }
   	});
	}
}

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
	
	
	
	$("#route-list").DataTable({
		"processing" : true,
		"serverside" : false,
		dom: 'Bfrtip',
		 "buttons": [
		            {
		                text: 'Add new button',
		                action: function ( e, dt, node, config ) {
		                    dt.button().add( 1, {
		                        text: 'Button '+(counter++),
		                        action: function () {
		                            this.remove();
		                        }
		                    } );
		                }
		            }
		        ],
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
					return '';
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
        path: pathArr,
        action : "save"
    };
console.log('data=%o', data);
    $.ajax({
        url: "../RouteList",
        data: JSON.stringify(data),
        type: 'POST',
        dataType: "text",
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
