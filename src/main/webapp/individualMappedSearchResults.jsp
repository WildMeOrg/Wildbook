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

<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*,java.util.Random" %>



<html>
<head>



  <%


    //let's load encounterSearch.properties
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
      langCode = (String) session.getAttribute("langCode");
    }
    Properties map_props = new Properties();
    map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualMappedSearchResults.properties"));

    Properties haploprops = new Properties();
    haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));

    Properties localeprops = new Properties();
   localeprops.load(getClass().getResourceAsStream("/bundles/locales.properties"));

    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd();

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


    //set up paging of results
    int startNum = 1;
    int endNum = 10;
    try {

      if (request.getParameter("startNum") != null) {
        startNum = (new Integer(request.getParameter("startNum"))).intValue();
      }
      if (request.getParameter("endNum") != null) {
      
        endNum = (new Integer(request.getParameter("endNum"))).intValue();
      }

    } catch (NumberFormatException nfe) {
      startNum = 1;
      endNum = 10;
    }
    int numResults = 0;

    //set up the vector for matching encounters
    Vector rIndividuals = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    request.setAttribute("gpsOnly", "yes");
    MarkedIndividualQueryResult queryResult = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = queryResult.getResult();
    

    		
    		
  %>

  <title><%=CommonConfiguration.getHTMLTitle()%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription()%>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords()%>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor()%>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request)%>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon()%>"/>


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
  
      <script>
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
	for(int uu=0;uu<rIndividuals.size();uu++){
	%>
  	var movePathCoordinates<%=uu%> = [];	
  	<%
  	}
  	
 
int rIndividualsSize=rIndividuals.size();
        int count = 0;

	    ArrayList<String> allHaplos2=new ArrayList<String>(); 
	    int numHaplos2 = 0;
	    
	    if((request.getParameter("showBy")!=null)&&(request.getParameter("showBy").trim().equals("haplotype"))){
	    	allHaplos2=myShepherd.getAllHaplotypes(); 
	    	numHaplos2=allHaplos2.size();
	    }
        
      
if(rIndividualsSize>0){
	//int havegpsSize=rIndividuals.size();
 for(int y=0;y<rIndividualsSize;y++){
	 MarkedIndividual indie=(MarkedIndividual)rIndividuals.get(y);

	 //Encounter thisEnc=
	 Vector rEncounters=indie.returnEncountersWithGPSData(true,true); 
	 int numEncs=rEncounters.size();
	 boolean showMovePath=false;
	for(int yh=0;yh<numEncs;yh++){
		Encounter thisEnc=(Encounter)rEncounters.get(yh);
		Double thisEncLat=null;
		Double thisEncLong=null;
		
		//first check if the Encounter object has lat and long values
		if((thisEnc.getLatitudeAsDouble()!=null)&&(thisEnc.getLongitudeAsDouble()!=null)){
			thisEncLat=thisEnc.getLatitudeAsDouble();
			thisEncLong=thisEnc.getLongitudeAsDouble();
		}
		//let's see if locales.properties has a location we can use
		else{
	           try {
	                String lc = thisEnc.getLocationCode();
	                if (localeprops.getProperty(lc) != null) {
	                  String gps = localeprops.getProperty(lc);
	                  StringTokenizer st = new StringTokenizer(gps, ",");
	                  thisEncLat=(new Double(st.nextToken()))+ran.nextDouble()*0.02;
	                  thisEncLong=(new Double(st.nextToken()))+ran.nextDouble()*0.02;;

	                }
	              } catch (Exception e) {
	                e.printStackTrace();
	                System.out.println("     I hit an error getting locales in individualMappedSearchResults.jsp.");
	              }
		}
		
		String haploColor="CC0000";
		
        if((map_props.getProperty("defaultMarkerColor")!=null)&&(!map_props.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=map_props.getProperty("defaultMarkerColor");
        }
		
        
        //now check if we should show by sex
		if((request.getParameter("showBy")!=null)&&(request.getParameter("showBy").trim().equals("sex"))){
			if(indie.getSex().equals("male")){
				haploColor="0000FF";
			}
			else if(indie.getSex().equals("female")){
				haploColor="FF00FF";
			}
		}
		else if((request.getParameter("showBy")!=null)&&(request.getParameter("showBy").trim().equals("haplotype"))){
			

	           if((indie.getHaplotype()!=null)&&(haploprops.getProperty(indie.getHaplotype())!=null)){
	         	  if(!haploprops.getProperty(indie.getHaplotype()).trim().equals("")){ haploColor = haploprops.getProperty(indie.getHaplotype());}
	            }
		
		}	
		
		if((thisEncLat!=null)&&(thisEncLong!=null)){
		
		showMovePath=true;
 %>
          
          var latLng = new google.maps.LatLng(<%=thisEncLat.toString()%>, <%=thisEncLong.toString()%>);
          bounds.extend(latLng);
          movePathCoordinates<%=y%>.push(latLng);
           <%
           
           
           //currently unused programatically
           String markerText="";
           
           //another comment
           
           


           %>
           var marker = new google.maps.Marker({
					        	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=haploColor%>',
					        	   position:latLng,
					        	   map:map
			});

            google.maps.event.addListener(marker,'click', function() {
                 (new google.maps.InfoWindow({content: '<strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=thisEnc.isAssignedToMarkedIndividual()%>\"><%=thisEnc.isAssignedToMarkedIndividual()%></a></strong><br /><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName()%>/encounters/<%=thisEnc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=thisEnc.getDate()%><br />Sex: <%=thisEnc.getSex()%><%if(thisEnc.getSizeAsDouble()!=null){%><br />Size: <%=thisEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=thisEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>'})).open(map, this);
             });
 
	
          markers.push(marker);
 		  map.fitBounds(bounds);       
 
 <%
 
	 }
	 	 %>
	 	 
	 	//test comment
	 	 	    
	 	var movePath<%=y%> = new google.maps.Polyline({
	 				       path: movePathCoordinates<%=y%>,
	 				       geodesic: true,
	 				       strokeOpacity: 0.0,
	 				       strokeColor: '<%=haploColor%>',
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
} 

myShepherd.rollbackDBTransaction();
 %>
 

 
   
  
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
    


    
  </head>
 <body onunload="GUnload()">
 <div id="wrapper">
 <div id="page">

<jsp:include page="header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>

 <div id="main">
 
<ul id="tabmenu">


  <li><a href="individualSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("matchingImages")%>
  </a></li>
  <li><a class="active"><%=map_props.getProperty("mappedResults")%>
  </a></li>
     
  <li><a href="individualSearchResultsAnalysis.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("analysis")%>
  </a></li>
    <li><a href="individualSearchResultsExport.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("export")%>
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
 
 //read from the map_props property file the value determining how many entries to map. Thousands can cause map delay or failure from Google.
 int numberResultsToMap = -1;

 %>

 
 
 <%
   if (rIndividuals.size() > 0) {
     myShepherd.beginDBTransaction();
     try {
 %>
 <p><%=map_props.getProperty("resultsNote")%></p>
 
 <p>
 <%=map_props.getProperty("aspects")%>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="<%=generalStyle %>" href="individualMappedSearchResults.jsp?<%=request.getQueryString().replaceAll("showBy=sex","").replaceAll("showBy=haplotype","") %>"><%=map_props.getProperty("displayAspectName0") %></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="<%=sexStyle%>" href="individualMappedSearchResults.jsp?<%=request.getQueryString().replaceAll("showBy=sex","").replaceAll("showBy=haplotype","") %>&showBy=sex"><%=map_props.getProperty("displayAspectName2") %></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a style="<%=haplotypeStyle %>" href="individualMappedSearchResults.jsp?<%=request.getQueryString().replaceAll("showBy=sex","").replaceAll("showBy=haplotype","") %>&showBy=haplotype"><%=map_props.getProperty("displayAspectName1") %></a>
 </p>
 
<p><%=map_props.getProperty("mapNote")%></p>
 
 <div id="map-container">
 
 
<table cellpadding="3">
 <tr>
 <td valign="top">
<div id="map_canvas" style="width: 770px; height: 510px; "> </div>
 </td>
 
 <%
 if((request.getParameter("showBy")!=null)&&(request.getParameter("showBy").trim().equals("haplotype"))){
 %>
 <td valign="top">
 <table>
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
 
   }
 else {
 %>
 <p><%=map_props.getProperty("noGPS")%></p>
 <%
 }  

 
 
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   rIndividuals = null;
   //haveGPSData = null;
 
%>
 <table>
  <tr>
    <td align="left">

      <p><strong><%=map_props.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=map_props.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", map_props.getProperty("location")).replaceAll("locationCodeField", map_props.getProperty("locationID")).replaceAll("verbatimEventDateField", map_props.getProperty("verbatimEventDate")).replaceAll("alternateIDField", map_props.getProperty("alternateID")).replaceAll("behaviorField", map_props.getProperty("behavior")).replaceAll("Sex", map_props.getProperty("sex")).replaceAll("nameField", map_props.getProperty("nameField")).replaceAll("selectLength", map_props.getProperty("selectLength")).replaceAll("numResights", map_props.getProperty("numResights")).replaceAll("vesselField", map_props.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=map_props.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
