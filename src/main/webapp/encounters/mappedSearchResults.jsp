<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*" %>



  <%
  String context="context0";
  context=ServletUtilities.getContext(request);

    String langCode=ServletUtilities.getLanguageCode(request);
    
    Properties map_props = new Properties();
    //map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/mappedSearchResults.properties"));
    map_props=ShepherdProperties.getProperties("mappedSearchResults.properties", langCode, context);

    
    

    Properties haploprops = new Properties();
    //haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));
	haploprops=ShepherdProperties.getProperties("haplotypeColorCodes.properties", "",context);
    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("mappedSearchResults.jsp");





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
    Vector rEncounters = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    request.setAttribute("gpsOnly", "yes");
    EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    rEncounters = queryResult.getResult();
    

    		
    		
  %>



<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
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
    color: #000;
    background: #E6EEEE;
     
    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {
    
  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
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
  
  <jsp:include page="../header.jsp" flush="true"/>

<script src="//maps.google.com/maps/api/js?sensor=false"></script>
 <script type="text/javascript" src="../javascript/markerclusterer/markerclusterer.js"></script>
 


    <script type="text/javascript">
      function initialize() {
        var center = new google.maps.LatLng(0,0);
        var mapZoom = 3;
    	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
    	var bounds = new google.maps.LatLngBounds();

        var map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID,
          fullscreenControl: true
        });
        
  	 
        var markers = [];
 
 
        
        <%

//now remove encounters this user cannot see
for (int i = rEncounters.size() - 1 ; i >= 0 ; i--) {
	Encounter enc = (Encounter)rEncounters.get(i);
	if (!enc.canUserAccess(request)) rEncounters.remove(i);
}

int rEncountersSize=rEncounters.size();
        int count = 0;

      
        
      
if(rEncounters.size()>0){
	int havegpsSize=rEncounters.size();
 for(int y=0;y<havegpsSize;y++){
	 Encounter thisEnc=(Encounter)rEncounters.get(y);
		String encSubdir = thisEnc.subdir();

 %>
          
          var latLng = new google.maps.LatLng(<%=thisEnc.getDecimalLatitude()%>, <%=thisEnc.getDecimalLongitude()%>);
          bounds.extend(latLng);
           <%
           
           
           //currently unused programatically
           String markerText="";
           
           String haploColor="CC0000";
           if((map_props.getProperty("defaultMarkerColor")!=null)&&(!map_props.getProperty("defaultMarkerColor").trim().equals(""))){
        	   haploColor=map_props.getProperty("defaultMarkerColor");
           }

           
           %>
           var marker = new google.maps.Marker({
        	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=haploColor%>',
        	   position:latLng,
        	   map:map
        	});
    	   		
google.maps.event.addListener(marker,'click', function() {
	
	<%
	String individualLinkString="";
	//if this is a MarkedIndividual, provide a link to it
	if(thisEnc.getIndividualID()!=null){
		individualLinkString="<strong><a target=\"_blank\" href=\"//"+CommonConfiguration.getURLLocation(request)+"/individuals.jsp?number="+thisEnc.getIndividualID()+"\">"+thisEnc.getIndividualID()+"</a></strong><br />";
	}
	%>
	(new google.maps.InfoWindow({content: '<%=individualLinkString %><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName(context)%>/encounters/<%=encSubdir%>/thumb.jpg\"></td><td>Date: <%=thisEnc.getDate()%><%if(thisEnc.getSex()!=null){%><br />Sex: <%=thisEnc.getSex()%><%}%><%if(thisEnc.getSizeAsDouble()!=null){%><br />Size: <%=thisEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=thisEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>'})).open(map, this);

});
 
	
          markers.push(marker);
 		  map.fitBounds(bounds);       
 
 <%
 
	 }
} 

myShepherd.rollbackDBTransaction();
 %>
 
 var options = {
         imagePath: '../javascript/markerclusterer/m',
         maxZoom: 10
     };

var markerCluster = new MarkerClusterer(map, markers, options);
 
 //markerClusterer = new MarkerClusterer(map, markers, {gridSize: 10});

      }
      

      google.maps.event.addDomListener(window, 'load', initialize);
    </script>
    
<div class="container maincontent">
 

 
       <h1 class="intro"><%=map_props.getProperty("title")%></h1>

 
 <ul id="tabmenu">
 
   <li><a href="searchResults.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("table")%>
   </a></li>
   <li><a
     href="thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("matchingImages")%>
   </a></li>
   <li><a class="active"><%=map_props.getProperty("mappedResults") %>
   </a></li>
   <li><a
     href="../xcalendar/calendar2.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("resultsCalendar")%>
   </a></li>
      <li><a
     href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("analysis")%>
   </a></li>
         <li><a
     href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("export")%>
   </a></li>
 
 </ul>

 
 
 
 
 <br />
 
 

 
 <%
 
 //read from the map_props property file the value determining how many entries to map. Thousands can cause map delay or failure from Google.
 int numberResultsToMap = -1;

 %>

 
  <p><%=map_props.getProperty("aspects") %>:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <%
  boolean hasMoreProps=true;
  int propsNum=0;
  while(hasMoreProps){
	if((map_props.getProperty("displayAspectName"+propsNum)!=null)&&(map_props.getProperty("displayAspectFile"+propsNum)!=null)){
		%>
		<a href="<%=map_props.getProperty("displayAspectFile"+propsNum)%>?<%=request.getQueryString()%>"><%=map_props.getProperty("displayAspectName"+propsNum) %></a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
		
		<%
		propsNum++;
	}
	else{hasMoreProps=false;}
  }
  %>
</p>
 
 <%
   if (rEncounters.size() > 0) {
     myShepherd.beginDBTransaction();
     try {
 %>
 
<p><%=map_props.getProperty("mapNote")%></p>

 <div id="map_canvas" style="width: 100%; height: 500px;"></div>
 

 
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
   rEncounters = null;
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
 </div>
 <jsp:include page="../footer.jsp" flush="true"/>



