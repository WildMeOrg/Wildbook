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
  String langCode = "en";

  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


  //load our variables for the submit page

  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individuals.properties"));
  props = ShepherdProperties.getProperties("individuals.properties", langCode);


		  
  Properties localesProps = new Properties();
  //localesProps.load(getClass().getResourceAsStream("/bundles/locales.properties"));
  localesProps = ShepherdProperties.getProperties("locales.properties", "");
	  
		  
		  
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
  
  
 
  
  Vector haveGPSData = new Vector();
  
  if(request.getParameter("name")!=null){
	  String name = request.getParameter("name");
	  MarkedIndividual sharky=myShepherd.getMarkedIndividual(name);
	  haveGPSData = sharky.returnEncountersWithGPSData(true, true);
  }
  else if(request.getParameter("occurrence_number")!=null){
	  String name = request.getParameter("occurrence_number");
	  Occurrence sharky=myShepherd.getOccurrence(name);
	  haveGPSData = sharky.returnEncountersWithGPSData(false, false);
  }
  
  

  try {
%>

<style type="text/css">
.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;
</style>

<script src="http://maps.google.com/maps/api/js?sensor=false"></script>


<p><strong><img src="images/2globe_128.gif" width="64" height="64" align="absmiddle"/><%=mapping %></strong></p>
<%
  
  int havegpsSize=haveGPSData.size();
  if (havegpsSize > 0) {
%>


    <script type="text/javascript">
      function initialize() {
        var center = new google.maps.LatLng(0,0);
        var mapZoom = 1;
    	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
    	var bounds = new google.maps.LatLngBounds();
        
        var map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID
        });

    	  //adding the fullscreen control to exit fullscreen
    	  var fsControlDiv = document.createElement('DIV');
    	  var fsControl = new FSControl(fsControlDiv, map);
    	  fsControlDiv.index = 1;
    	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

        
        var markers = [];
 	    var movePathCoordinates = [];
        
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
		  if(indie.returnEncountersWithGPSData(true,false).size()>0){
			  Vector encsWithGPS=indie.returnEncountersWithGPSData(true,true);
			  int numEncsWithGPS=encsWithGPS.size();
			  for(int j=0;j<numEncsWithGPS;j++){
				  //if(!haveGPSData.contains(encsWithGPS.get(j))){
					  Encounter indieEnc=(Encounter)encsWithGPS.get(j);
					  
					  //we now have an Encounter that is external to this occurrence but part of a MarkedIndividual participating in this occurrence
					  String thisLatLong="999,999";
					  if(((indieEnc.getDecimalLatitude())!=null)&&(indieEnc.getDecimalLongitude()!=null)){
							 thisLatLong=indieEnc.getDecimalLatitude()+","+indieEnc.getDecimalLongitude();
					  }
					  //let's try to get this from locales.properties
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

					             google.maps.event.addListener(marker,'click', function() {
					                 (new google.maps.InfoWindow({content: '<strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=indieEnc.isAssignedToMarkedIndividual()%>\"><%=indieEnc.isAssignedToMarkedIndividual()%></a></strong><br /><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName(context)%>/encounters/<%=indieEnc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=indieEnc.getDate()%><br />Sex: <%=indieEnc.getSex()%><%if(indieEnc.getSizeAsDouble()!=null){%><br />Size: <%=indieEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=indieEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>'})).open(map, this);
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
	 //let's try to get this from locales.properties
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

            google.maps.event.addListener(marker,'click', function() {
                 (new google.maps.InfoWindow({content: '<strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=thisEnc.isAssignedToMarkedIndividual()%>\"><%=thisEnc.isAssignedToMarkedIndividual()%></a></strong><br /><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName(context)%>/encounters/<%=thisEnc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=thisEnc.getDate()%><br />Sex: <%=thisEnc.getSex()%><%if(thisEnc.getSizeAsDouble()!=null){%><br />Size: <%=thisEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=thisEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>'})).open(map, this);
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

  
 
	var maxZoomService = new google.maps.MaxZoomService();
	maxZoomService.getMaxZoomAtLatLng(map.getCenter(), function(response) {
		    if (response.status == google.maps.MaxZoomStatus.OK) {
		    	if(response.zoom < map.getZoom()){
		    		map.setZoom(response.zoom);
		    	}
		    }
		    
	});
 
 

 } // end initialize function
      
 


	
      

      function fullScreen(){
    		$("#map_canvas").addClass('full_screen_map');
    		$('html, body').animate({scrollTop:0}, 'slow');
    		initialize();
    		
    		//hide header
    		$("#header_menu").hide();
    		
    		if(overlaysSet){overlaysSet=false;setOverlays();}
    		//alert("Trying to execute fullscreen!");
    	}


    	function exitFullScreen() {
    		$("#header_menu").show();
    		$("#map_canvas").removeClass('full_screen_map');

    		initialize();
    		if(overlaysSet){overlaysSet=false;setOverlays();}
    		//alert("Trying to execute exitFullScreen!");
    	}


    	//making the exit fullscreen button
    	function FSControl(controlDiv, map) {

    	  // Set CSS styles for the DIV containing the control
    	  // Setting padding to 5 px will offset the control
    	  // from the edge of the map
    	  controlDiv.style.padding = '5px';

    	  // Set CSS for the control border
    	  var controlUI = document.createElement('DIV');
    	  controlUI.style.backgroundColor = '#f8f8f8';
    	  controlUI.style.borderStyle = 'solid';
    	  controlUI.style.borderWidth = '1px';
    	  controlUI.style.borderColor = '#a9bbdf';;
    	  controlUI.style.boxShadow = '0 1px 3px rgba(0,0,0,0.5)';
    	  controlUI.style.cursor = 'pointer';
    	  controlUI.style.textAlign = 'center';
    	  controlUI.title = 'Toggle the fullscreen mode';
    	  controlDiv.appendChild(controlUI);

    	  // Set CSS for the control interior
    	  var controlText = document.createElement('DIV');
    	  controlText.style.fontSize = '12px';
    	  controlText.style.fontWeight = 'bold';
    	  controlText.style.color = '#000000';
    	  controlText.style.paddingLeft = '4px';
    	  controlText.style.paddingRight = '4px';
    	  controlText.style.paddingTop = '3px';
    	  controlText.style.paddingBottom = '2px';
    	  controlUI.appendChild(controlText);
    	  //toggle the text of the button
    	   if($("#map_canvas").hasClass("full_screen_map")){
    	      controlText.innerHTML = 'Exit Fullscreen';
    	    } else {
    	      controlText.innerHTML = 'Fullscreen';
    	    }

    	  // Setup the click event listeners: toggle the full screen

    	  google.maps.event.addDomListener(controlUI, 'click', function() {

    	   if($("#map_canvas").hasClass("full_screen_map")){
    	    exitFullScreen();
    	    } else {
    	    fullScreen();
    	    }
    	  });

    	}

      

    	
      
      google.maps.event.addDomListener(window, 'load', initialize);
    </script>


<p><%=mappingnote %></p>
<%
if(request.getParameter("occurrence_number")!=null){
%>
	<p><%=props.getProperty("occurrenceAdditionalMappingNote") %></p>
<%
}
%>

 <div id="map_canvas" style="width: 770px; height: 510px; "></div>

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