<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.ArrayList,java.util.*" %>

<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
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
String context="context0";
context=ServletUtilities.getContext(request);
//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  //load our variables for the submit page
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individuals.properties"));
  props = ShepherdProperties.getProperties("individuals.properties", langCode, context);
  Properties localesProps = new Properties();
  localesProps = ShepherdProperties.getProperties("locationIDGPS.properties", "",context);
  String markedIndividualTypeCaps = props.getProperty("markedIndividualTypeCaps");
  String nickname = props.getProperty("nickname");
  String nicknamer = props.getProperty("nicknamer");
  String alternateID = props.getProperty("alternateID");
  String sex = props.getProperty("sex");
  String setsex = props.getProperty("setsex");
  String numencounters = props.getProperty("numencounters");
  String encnumber = props.getProperty("number");
  String dataTypes = props.getProperty("dataTypes");
  String date = props.getProperty("date");
  String size = props.getProperty("size");
  String spots = props.getProperty("spots");
  String location = props.getProperty("location");
  String mapping = props.getProperty("mapping");
  String mappingnote = props.getProperty("mappingnote");
  String setAlternateID = props.getProperty("setAlternateID");
  String setNickname = props.getProperty("setNickname");
  String unknown = props.getProperty("unknown");
  String noGPS = props.getProperty("noGPS");
  String update = props.getProperty("update");
  String additionalDataFiles = props.getProperty("additionalDataFiles");
  String delete = props.getProperty("delete");
  String none = props.getProperty("none");
  String addDataFile = props.getProperty("addDataFile");
  String sendFile = props.getProperty("sendFile");
  String researcherComments = props.getProperty("researcherComments");
  String edit = props.getProperty("edit");
  String matchingRecord = props.getProperty("matchingRecord");
  String tryAgain = props.getProperty("tryAgain");
  String addComments = props.getProperty("addComments");
  String record = props.getProperty("record");
  String getRecord = props.getProperty("getRecord");
  String allEncounters = props.getProperty("allEncounters");
  String allIndividuals = props.getProperty("allIndividuals");
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("individualMapEmbed.jsp");
  myShepherd.beginDBTransaction();
  Vector haveGPSData = new Vector();
  String mapKey = CommonConfiguration.getGoogleMapsKey(context);
  if(request.getParameter("name")!=null){
	  String name = request.getParameter("name");
	  MarkedIndividual sharky=myShepherd.getMarkedIndividual(name);
	  haveGPSData = sharky.returnEncountersWithGPSData(true, true,context);
  }
  else if(request.getParameter("occurrence_number")!=null){
	  String name = request.getParameter("occurrence_number");
	  Occurrence sharky=myShepherd.getOccurrence(name);
	  haveGPSData = sharky.returnEncountersWithGPSData(false, false,context);
  }
  try {
%>

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script type="text/javascript" src="javascript/markerclusterer/markerclusterer.js"></script>
<script type="text/javascript" src="https://cdn.rawgit.com/googlemaps/js-marker-clusterer/gh-pages/src/markerclusterer.js"></script> 
<script src="javascript/oms.min.js"></script>
<p><strong><%=mapping %></strong></p>
<%
  int havegpsSize=haveGPSData.size();
  if (havegpsSize > 0) {
%>


    <script type="text/javascript">
      function initialize() {
        var center = new google.maps.LatLng(0,0);
        var mapZoom = 1;
    	  var bounds = new google.maps.LatLngBounds();
        var map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          fullscreenControl: true,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID,
          zoomControl: true,
          scaleControl: false,
          scrollwheel: false,
          disableDoubleClickZoom: true
        });
        var markers = [];
 	var movePathCoordinates = [];
	var oms = new OverlappingMarkerSpiderfier(map, {nearbyDistance: 15, legWeight: 1});
        <%
        String haploColor="CC0000";
        if((props.getProperty("defaultMarkerColor")!=null)&&(!props.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=props.getProperty("defaultMarkerColor");
        }
String lastLatLong="";
//for an occurrence, we should also map where else its marked individuals have been spotted
 if(request.getParameter("occurrence_number")!=null){
	 String name = request.getParameter("occurrence_number");
	  Occurrence sharky=myShepherd.getOccurrence(name);
	  ArrayList<String> occurIndies=sharky.getMarkedIndividualNamesForThisOccurrence();
	  int numParticipatingIndies=occurIndies.size();
	  //set up movePath line holders
		for(int uu=0;uu<numParticipatingIndies;uu++){
		%>
	  	var movePathCoordinates<%=uu%> = [];
	  	<%
	  	}
	  for(int g=0;g<numParticipatingIndies;g++){
		  MarkedIndividual indie=myShepherd.getMarkedIndividual(occurIndies.get(g));
		  if(indie.returnEncountersWithGPSData(true,false,context).size()>0){
			  Vector encsWithGPS=indie.returnEncountersWithGPSData(true,true,context);
			  int numEncsWithGPS=encsWithGPS.size();
			  for(int j=0;j<numEncsWithGPS;j++){
				  //if(!haveGPSData.contains(encsWithGPS.get(j))){
					  Encounter indieEnc=(Encounter)encsWithGPS.get(j);
					  //we now have an Encounter that is external to this occurrence but part of a MarkedIndividual participating in this occurrence
					  String thisLatLong="999,999";
					  if(((indieEnc.getDecimalLatitude())!=null)&&(indieEnc.getDecimalLongitude()!=null)){
							 thisLatLong=indieEnc.getDecimalLatitude()+","+indieEnc.getDecimalLongitude();
					  }
					  //let's try to get this from locationIDGPS.properties
					  else if(localesProps.getProperty(indieEnc.getLocationID())!=null){
								 thisLatLong=localesProps.getProperty(indieEnc.getLocationID());
					  }
					 %>
					          var latLng = new google.maps.LatLng(<%=thisLatLong%>);
					          bounds.extend(latLng);
					          movePathCoordinates<%=g%>.push(latLng);
					           <%
					           //currently unused programatically
					           String markerText="";
					           String zIndexString="";
					           String markerColor="C0C0C0";
					           if(haveGPSData.contains(encsWithGPS.get(j))){
					        	   markerColor="00FF00";
					        	   zIndexString=",zIndex: 10000";
					           }
					           %>
					           var marker = new google.maps.Marker({
					        	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=markerColor%>',
					        	   position:latLng,
					        	   map:map<%=zIndexString%>
					        	});
					           <%
											String encSubdir = indieEnc.subdir();
					           %>
					              var encDate1 = '<br/><table><tr><td>' + '<%=props.getProperty("date")%><%=indieEnc.getDate()%>';

					              var encSex2 = '<%if(indieEnc.getSex()!=null){%>'+'<br/>' + '<%=props.getProperty("sex")%><%=indieEnc.getSex()%><%}%>';
					              var encSize3 = '<%if(indieEnc.getSizeAsDouble()!=null){%>'+'<br/>Size:'+<%=indieEnc.getSize()%>+'m'+'<%}%><br/>';
					              var encURL4 = '<br/><a target=\"_blank\" href=\"http:\/\/'+'<%=CommonConfiguration.getURLLocation(request)%>'+'/encounters/encounter.jsp?number='+'<%=indieEnc.getEncounterNumber()%>'+'\" >'+'<%=props.getProperty("gotoEncounter")%>'+'</a></td></tr></table>';
					              var indyURL5 = '<strong><a target=\"_blank\" href=\"\/\/'+'<%=CommonConfiguration.getURLLocation(request)%>'+'/individuals.jsp?number='+'<%=indieEnc.getIndividualID()%>'+'\">'+'<%=indieEnc.getIndividualID()%>'+'</a></strong>';
					             
					           	  var popWindow = encDate1 + encSex2 + encSize3 + encURL4 + indyURL5; 
					           
					           
					              google.maps.event.addListener(marker,'click', function() {
					                 (new google.maps.InfoWindow({content: popWindow })).open(map, this);
					              });
					              markers.push(marker);
					              map.fitBounds(bounds);
							<%
							lastLatLong=indieEnc.getDecimalLatitude()+","+indieEnc.getDecimalLongitude();
				  //}  //end if
							if(haveGPSData.contains(encsWithGPS.get(j))){haveGPSData.remove(encsWithGPS.get(j));havegpsSize=haveGPSData.size();}
			  }
		  }
		  %>
		  var movePath<%=g%> = new google.maps.Polyline({
			     path: movePathCoordinates<%=g%>,
			     geodesic: true,
			     strokeOpacity: 0.0,
			     strokeColor: 'white',
			     icons: [{
			       icon: {
			         path: 'M -1,1 0,0 1,1',
			         strokeOpacity: 1,
			         strokeWeight: 1.5,
			         scale: 6
			       },
			       repeat: '20px'
			     }
			     ],
			     map: map
			   });
			  <%
	  }
 }
 for(int y=0;y<havegpsSize;y++){
	 Encounter thisEnc=(Encounter)haveGPSData.get(y);
	 String thisLatLong="999,999";
	 if(((thisEnc.getDecimalLatitude())!=null)&&(thisEnc.getDecimalLongitude()!=null)){
		 thisLatLong=thisEnc.getDecimalLatitude()+","+thisEnc.getDecimalLongitude();
	 }
	 //let's try to get this from locationIDGPS.properties
	 else{
		 if(localesProps.getProperty(thisEnc.getLocationID())!=null){
			 thisLatLong=localesProps.getProperty(thisEnc.getLocationID());
		 }
	 }
 %>
          var latLng = new google.maps.LatLng(<%=thisLatLong%>);
          bounds.extend(latLng);
          movePathCoordinates.push(latLng);
           <%
           //currently unused programatically
           String markerText="";
           String colorToUseForMarker=haploColor;
           String zIndexString="";
           if((y==0)&&(havegpsSize>0)){
        	   colorToUseForMarker="00FF00";
        	   zIndexString=",zIndex: 10000";
           }
           %>
			var marker = new google.maps.Marker({
        	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=colorToUseForMarker%>',
        	   position:latLng,
        	   map:map<%=zIndexString%>
        	});
			<%
						String encSubdir = thisEnc.subdir();
			%>
            google.maps.event.addListener(marker,'click', function() {
                 (new google.maps.InfoWindow({content: '<strong><a target=\"_blank\" href=\"//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=thisEnc.getIndividualID()%>\"><%=thisEnc.getIndividualID()%></a></strong><br /><table><tr><td><%=props.getProperty("date") %> <%=thisEnc.getDate()%><%if(thisEnc.getSex()!=null){%><br /><%=props.getProperty("sex") %> <%=thisEnc.getSex()%><%}%><%if(thisEnc.getSizeAsDouble()!=null){%><br />Size: <%=thisEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=thisEnc.getEncounterNumber()%>\" ><%=props.getProperty("gotoEncounter") %></a></td></tr></table>'})).open(map, this);
             });
          markers.push(marker);
          map.fitBounds(bounds);
 <%
 }
 %>
 var movePath = new google.maps.Polyline({
     path: movePathCoordinates,
     geodesic: true,
     strokeOpacity: 0.0,
     strokeColor: 'white',
     icons: [{
       icon: {
         path: 'M -1,1 0,0 1,1',
         strokeOpacity: 1,
         strokeWeight: 1.5,
         scale: 6
       },
       repeat: '20px'
     }
     ],
     map: map
   });
 zoomChangeBoundsListener =
	    google.maps.event.addListenerOnce(map, 'bounds_changed', function(event) {
	        if ((this.getZoom())&&(this.getZoom()>5)){
	            this.setZoom(5);
	        }
	});

var spiderWindow = new google.maps.InfoWindow();
oms.addListener('click', function(marker, event) {
	spiderWindow.setContent(marker.desc);
	// spiderWindow.open(map, marker);
});

oms.addListener('spiderfy', function(markers) {
  spiderWindow.close();
});

for (var i = 0; i < markers.length; i ++) {
  oms.addMarker(markers[i]);
  console.log(markers[i])
}
 
setTimeout(function(){google.maps.event.removeListener(zoomChangeBoundsListener)}, 2000);
var options = {
        imagePath: 'https://cdn.rawgit.com/googlemaps/js-marker-clusterer/gh-pages/images/m',
        maxZoom: 8
    };
var markerCluster = new MarkerClusterer(map, markers, options)
 } // end initialize function
      google.maps.event.addDomListener(window, 'load', initialize);
    </script>


<%
if(request.getParameter("occurrence_number")!=null){
%>
	<p><%=props.getProperty("occurrenceAdditionalMappingNote") %></p>
<%
} else {
  %><p><%=mappingnote %></p><%
}
%>

 <div id="map_canvas" style="width: 100%; height: 500px;"></div>

<%
}
else {
%>
<p><%=noGPS %></p>
<br />
<%
  }
%>
<p>&nbsp;</p>


<%
  }
  catch (Exception e) {e.printStackTrace();}
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
  myShepherd = null;
%>
