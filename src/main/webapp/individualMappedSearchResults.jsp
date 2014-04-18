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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*,java.util.Random" %>



<html>
<head>



  <%
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load encounterSearch.properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    Properties map_props = new Properties();
    //map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualMappedSearchResults.properties"));
    map_props = ShepherdProperties.getProperties("individualMappedSearchResults.properties", langCode);
	  

    
    Properties haploprops = new Properties();
    //haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));
	//haploprops=ShepherdProperties.getProperties("haplotypeColorCodes.properties", "");
	haploprops = ShepherdProperties.getProperties("haplotypeColorCodes.properties", "",context);
		

    Properties localeprops = new Properties();
    localeprops = ShepherdProperties.getProperties("locationIDGPS.properties", "");
	
    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);

	Random ran= new Random();

	//set up the aspect styles
	String haplotypeStyle="";
	String sexStyle="";
	String generalStyle="";
    if((request.getParameter("showBy")!=null)&&(request.getParameter("showBy").trim().equals("haplotype"))){
    	haplotypeStyle="background-color:#D8D8D8";
    }
    else if((request.getParameter("showBy")!=null)&&(request.getParameter("showBy").trim().equals("sex"))){
    	sexStyle="background-color:#D8D8D8";
    }
    else{
    	generalStyle="background-color:#D8D8D8";
    	//general comment
    }




    ArrayList<String> allHaplos2=new ArrayList<String>(); 
    int numHaplos2 = 0;
    allHaplos2=myShepherd.getAllHaplotypes(); 
    numHaplos2=allHaplos2.size();
    
    List<String> allSpecies=CommonConfiguration.getIndexedValues("genusSpecies",context);
    int numSpecies=allSpecies.size();
   
    List<String> allSpeciesColors=CommonConfiguration.getIndexedValues("genusSpeciesColor",context);
    int numSpeciesColors=allSpeciesColors.size();
%>

  <title><%=CommonConfiguration.getHTMLTitle(context)%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context)%>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context)%>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context)%>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context)%>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context)%>"/>


    <style type="text/css">

      body {
        margin: 0;
        padding: 10px 20px 20px;
        font-family: Arial;
        font-size: 16px;
      }


.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;
}
</style>

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 2px solid black;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #DEDECF;
    background: #000;
    font: bold 1em "Trebuchet MS", Arial, sans-serif;
    border: 2px solid black;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #FFFFFF;
    color: #000000;
    border-bottom: 2px solid #FFFFFF;
  }

  #tabmenu a:hover {
    color: #ffffff;
    background: #7484ad;
  }

  #tabmenu a:visited {
    color: #E8E9BE;
  }

  #tabmenu a.active:hover {
    background: #7484ad;
    color: #DEDECF;
    border-bottom: 2px solid #000000;
  }
  
  
</style>
  
      <script type="text/javascript">
        function getQueryParameter(name) {
          name = name.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
          var regexS = "[\\?&]" + name + "=([^&#]*)";
          var regex = new RegExp(regexS);
          var results = regex.exec(window.location.href);
          if (results == null)
            return "";
          else
            return results[1];
        }
        
        //test comment
  </script>
  
  

<script src="http://maps.google.com/maps/api/js?sensor=false&v=3.9"></script>
<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js"></script>
<script type="text/javascript" src="javascript/GeoJSON.js"></script>



    <script type="text/javascript">
    
    var center = new google.maps.LatLng(0,0);
    var mapZoom = 1;
    if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
    var bounds = new google.maps.LatLngBounds();

    var iw = new google.maps.InfoWindow({
		content:'Loading and rendering map data...',
		position:center
	});
     
    var map;
    var bounds = new google.maps.LatLngBounds();
    var currentFeature_or_Features;
    var geoJSONResults;
    //aspect options: sex, haplotype, none
    var aspect="none";
    
    var filename = "http://<%=CommonConfiguration.getURLLocation(request)%>/GetIndividualSearchGoogleMapsPoints?<%=request.getQueryString()%>";
    var overlays = [];
    var overlaysSet=false;
    

    
      function initialize() {
    	  
    	  map = new google.maps.Map(document.getElementById('map_canvas'), {
    	      zoom: mapZoom,
    	      center: center,
    	      mapTypeId: google.maps.MapTypeId.HYBRID
    	    });
    	  

    	  iw.open(map);
    	  
  	  //adding the fullscreen control to exit fullscreen
  	  var fsControlDiv = document.createElement('DIV');
  	  var fsControl = new FSControl(fsControlDiv, map);
  	  fsControlDiv.index = 1;
  	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

        var markers = [];
	var movePathCoordinates = [];

  
 	var maxZoomService = new google.maps.MaxZoomService();
 	maxZoomService.getMaxZoomAtLatLng(map.getCenter(), function(response) {
 		    if (response.status == google.maps.MaxZoomStatus.OK) {
 		    	if(response.zoom < map.getZoom()){
 		    		map.setZoom(response.zoom);
 		    	}
 		    }
 		    
	});

 	

 	
      }
      
      
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
    
    <script type="text/javascript">



function loadIndividualMapData(localResults,aspect){
	
	//alert("Entering function loadIndividualMapData");
	
	  //for (var i = 0; i < results.length; i++) {
		//    var geoJsonObject = results.features[i];
		//    var geometry = geoJsonObject.geometry;
	  //}
	  
	  //alert("Done iterating...");
	  var googleOptions = {
			  strokeColor: '#CCC',
			  strokeWeight: 1
			};
	  //alert("Results: "+localResults);
	  //alert("Aspect is: "+aspect);
	  currentFeature_or_Features = new GeoJSON(jQuery.parseJSON(localResults), googleOptions, map, bounds,aspect);
	  	if (currentFeature_or_Features.type && currentFeature_or_Features.type == "Error"){
			alert("GeoJSON read error: "+ currentFeature_or_Features.message);
			//return;
		}
	  	//alert("No error");
		if (currentFeature_or_Features.length){
			//alert("Iterating through detected features: "  +currentFeature_or_Features.length);
			for (var i = 0; i < currentFeature_or_Features.length; i++){
				if(currentFeature_or_Features[i].length){
					for(var j = 0; j < currentFeature_or_Features[i].length; j++){
						currentFeature_or_Features[i][j].setMap(map);
						if(currentFeature_or_Features[i][j].geojsonProperties) {
							setInfoWindow(currentFeature_or_Features[i][j]);
						}
					}
				}
				else{
					
					currentFeature_or_Features[i].setMap(map);
				}
				if (currentFeature_or_Features[i].geojsonProperties) {
					setInfoWindow(currentFeature_or_Features[i]);
				}
			}
			
			currentFeature_or_Features.setMap(map);
		}else{
			//alert("In the else statement...");
			currentFeature_or_Features.setMap(map);
			if (currentFeature_or_Features.geojsonProperties) {
				setInfoWindow(currentFeature_or_Features);
			}
		}
}


function clearMap(){
	if (!currentFeature_or_Features) {
		//alert("There is nothing to clear!");
		return;
	}
	if (currentFeature_or_Features.length){
		//alert("Iterating and clearing map...");
		for (var i = 0; i < currentFeature_or_Features.length; i++){
			if(currentFeature_or_Features[i].length){
				for(var j = 0; j < currentFeature_or_Features[i].length; j++){
					currentFeature_or_Features[i][j].setMap(null);
				}
			}
			else{
				currentFeature_or_Features[i].setMap(null);
			}
		}
	}else{
		//alert("Clearing map...");
		currentFeature_or_Features.setMap(null);
	}
	//if (infowindow.getMap()){
	//	infowindow.close();
	//}
}

function hideTable(myID) {
    var lTable = document.getElementById(myID);
    lTable.style.display = "none";
}

function showTable(myID) {
    var lTable = document.getElementById(myID);
    lTable.style.display = "table";
}
function useNoAspect(){
	//alert("In useNoAspect");
	if(aspect != "none"){
		aspect="none";
		hideTable("haplotable");
		 <%
		 if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
		 %>
		hideTable("speciestable");
		<%
		 }
		 %>
		clearMap();
		loadIndividualMapData(geoJSONResults,aspect);

	}
}
function useSexAspect(){
	//alert("In useSexAspect");
	hideTable("haplotable");
	 <%
	 if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
	 %>
	hideTable("speciestable");
	<%
	 }
	%>
	if(aspect != "sex"){
		aspect="sex";
		
		
		clearMap();
		loadIndividualMapData(geoJSONResults,aspect);
	
	}
}
function useHaplotypeAspect(){
	//alert("In useHaplotypeAspect");
	 <%
 	if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
 	%>
	hideTable("speciestable");
	<%
 	}
	%>
	showTable("haplotable");
	if(aspect != "haplotype"){
		aspect="haplotype";

		clearMap();
		loadIndividualMapData(geoJSONResults,aspect);
		
		
	}
}

function useSpeciesAspect(){
	//alert("In useHaplotypeAspect");
	 <%
 if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
 %>
	hideTable("speciestable");
	<%
 }
	%>
	hideTable("haplotable");
	 <%
	 if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
	 %>
	showTable("speciestable");
	<%
	 }
	 %>
	if(aspect != "species"){
		aspect="species";
		clearMap();
		loadIndividualMapData(geoJSONResults,aspect);
	}
}




function setInfoWindow (feature) {
	google.maps.event.addListener(feature, "click", function(event) {
		var content = "<div id='infoBox'><strong>GeoJSON Feature Properties</strong><br />";
		for (var j in this.geojsonProperties) {
			content += j + ": " + this.geojsonProperties[j] + "<br />";
		}
		content += "</div>";
		infowindow.setContent(content);
		infowindow.position = event.latLng;
		infowindow.open(map);
	});
}

function setOverlays() {
	  
	  if(!overlaysSet){

    	if(!geoJSONResults){
			//read in the GeoJSON 
			//alert("Reading GeoJSON...");
			var xhr = new XMLHttpRequest();
			//alert("Filename is: "+filename);
			xhr.open('GET', filename, true);
			//alert("xhr is open...");
			xhr.onload = function() {
				//alert(this.responseText);
				iw.close();
				geoJSONResults=this.responseText;
				loadIndividualMapData(geoJSONResults,aspect);
				
				
				
  				
			};
			xhr.send();
	  	}
    	else{
    		loadIndividualMapData(geoJSONResults,aspect);
    	}
    	
		
		//alert("done loading!!!");
    	
    	//google.maps.event.addListener(map, 'center_changed', function(){iw.close();});
         
         
		  overlaysSet=true;
      }
	    
   }



</script>

    
  </head>
 <body>
 <div id="wrapper">
 <div id="page">

<jsp:include page="header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>

 <div id="main">
 
<ul id="tabmenu">
<%
String queryString = "";
if (request.getQueryString() != null) {
  queryString = request.getQueryString();
}
%>

  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("matchingImages")%>
  </a></li>
  <li><a class="active"><%=map_props.getProperty("mappedResults")%>
  </a></li>
     
  <li><a href="individualSearchResultsAnalysis.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("analysis")%>
  </a></li>
    <li><a href="individualSearchResultsExport.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("export")%>
  </a></li>
</ul>
 <table width="810px" border="0" cellspacing="0" cellpadding="0">
   <tr>
     <td>
       <br/>
 
       <h1 class="intro"><%=map_props.getProperty("title")%>
       </h1>
     </td>
   </tr>
</table>

 <br />



 
 
 <%
   //if (rIndividuals.size() > 0) {
     //myShepherd.beginDBTransaction();
     try {
 %>
 <p><%=map_props.getProperty("resultsNote")%></p>
 
 <p>
 <%=map_props.getProperty("aspects")%>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="cursor:pointer; color:blue" onClick="useNoAspect(); return false;"><%=map_props.getProperty("displayAspectName0") %></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="cursor:pointer;color:blue" onClick="useSexAspect(); return false;"><%=map_props.getProperty("displayAspectName2") %></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="cursor:pointer;color:blue" onClick="useHaplotypeAspect(); return false;"><%=map_props.getProperty("displayAspectName1") %></a>
  <%
 if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
 %>
 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="cursor:pointer;color:blue" onClick="useSpeciesAspect(); return false;"><%=map_props.getProperty("displayAspectName3") %></a>
 <%
 }
 %>
 </p>
 
<p><%=map_props.getProperty("mapNote")%></p>
 
 <div id="map-container">
 
 
<table cellpadding="3">
 <tr>
 <td valign="top">
<div id="map_canvas" style="width: 770px; height: 510px; "> </div>
 </td>
 

 <td valign="top">
 <table id="haplotable" style="display:none">
 <tr><th>Haplotype Color Key</th></tr>
                    <%
                    String haploColor="CC0000";
                   if((map_props.getProperty("defaultMarkerColor")!=null)&&(!map_props.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=map_props.getProperty("defaultMarkerColor");
                   }   
                   for(int yy=0;yy<numHaplos2;yy++){
                       String haplo=allHaplos2.get(yy);
                       if((haploprops.getProperty(haplo)!=null)&&(!haploprops.getProperty(haplo).trim().equals(""))){
                     	  haploColor = haploprops.getProperty(haplo);
                        }
					%>
					<tr bgcolor="#<%=haploColor%>"><td><strong><%=haplo %></strong></td></tr>
					<%
                   }
                   if((map_props.getProperty("defaultMarkerColor")!=null)&&(!map_props.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=map_props.getProperty("defaultMarkerColor");
                	   %>
                	   <tr bgcolor="#<%=haploColor%>"><td><strong>Unknown</strong></td></tr>
                	   <%
                   }  
                   
                   %>
 </table>
 </td>
 <%
 if((CommonConfiguration.getProperty("showTaxonomy",context)!=null)&&(!CommonConfiguration.getProperty("showTaxonomy",context).equals("false"))){
 %>
  <td valign="top">
 <table id="speciestable" style="display:none">
 <tr><th>Species Color Key</th></tr>
                    <%
                    String speciesColor="CC0000";
                   if((map_props.getProperty("defaultMarkerColor")!=null)&&(!map_props.getProperty("defaultMarkerColor").trim().equals(""))){
                	  speciesColor=map_props.getProperty("defaultMarkerColor");
                   }   
                   for(int yy=0;yy<numSpecies;yy++){
                       String specie=allSpecies.get(yy);
                       if(numSpeciesColors>yy){
                     	  speciesColor = allSpeciesColors.get(yy);
                        }
					%>
					<tr bgcolor="#<%=speciesColor%>"><td><strong><%=specie %></strong></td></tr>
					<%
                   }
                   if((map_props.getProperty("defaultMarkerColor")!=null)&&(!map_props.getProperty("defaultMarkerColor").trim().equals(""))){
                	   speciesColor=map_props.getProperty("defaultMarkerColor");
                	   %>
                	   <tr bgcolor="#<%=speciesColor%>"><td><strong>Unknown</strong></td></tr>
                	   <%
                   }  
                   
                   %>
 </table>
 </td>
<%
 }
%>
 
 
 </tr>
 </table>
 

 </div>
 

 
 <%
 
     } 
     catch (Exception e) {
       e.printStackTrace();
     }
 


 
 
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   //rIndividuals = null;
   //haveGPSData = null;
 
%>

<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

<script>


$( window ).load(function() {
	setTimeout(function () {
        setOverlays();  
    }, 1000);
});

</script>

</body>
</html>
