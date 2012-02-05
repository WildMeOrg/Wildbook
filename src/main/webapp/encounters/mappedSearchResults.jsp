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

<%@ page contentType="text/html; charset=utf-8" language="java"
         import="java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.List,java.net.URI, org.ecocean.*" %>



<html>
<head>



  <%


    //let's load encounterSearch.properties
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
      langCode = (String) session.getAttribute("langCode");
    }
    Properties encprops = new Properties();
    encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/mappedSearchResults.properties"));

    //get our Shepherd
    Shepherd myShepherd = new Shepherd();





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
    EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    rEncounters = queryResult.getResult();
 
  %>

  <title><%=CommonConfiguration.getHTMLTitle()%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription()%>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords()%>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor()%>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request)%>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon()%>"/>

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
  </script>


</head>


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


<body onload="initialize()" onunload="GUnload()">
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">

<ul id="tabmenu">

  <li><a href="searchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a
    href="thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("matchingImages")%>
  </a></li>
  <li><a class="active"><%=encprops.getProperty("mappedResults") %>
  </a></li>
  <li><a
    href="../xcalendar/calendar2.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("resultsCalendar")%>
  </a></li>

</ul>
<table width="810px" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <br/>

      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>
    </td>
  </tr>
</table>




<%
  Vector haveGPSData = new Vector();
  int count = 0;

  for (int f = 0; f < rEncounters.size(); f++) {

    Encounter enc = (Encounter) rEncounters.get(f);
    count++;
    numResults++;
    if ((enc.getDWCDecimalLatitude() != null) && (enc.getDWCDecimalLongitude() != null)) {
      haveGPSData.add(enc);
      
     
      
    }
  }
    

  myShepherd.rollbackDBTransaction();

  startNum = startNum + 10;
  endNum = endNum + 10;

  if (endNum > numResults) {
    endNum = numResults;
  }
  String numberResights = "";
  if (request.getParameter("numResights") != null) {
    numberResights = "&numResights=" + request.getParameter("numResights");
  }
  String qString = request.getQueryString();
  int startNumIndex = qString.indexOf("&startNum");
  if (startNumIndex > -1) {
    qString = qString.substring(0, startNumIndex);
  }


%>


<br />




<p><strong>
	<img src="../images/2globe_128.gif" width="64" height="64" align="absmiddle"/> <%=encprops.getProperty("mappedResults")%>
</strong>
<%

//read from the encprops property file the value determining how many entries to map. Thousands can cause map delay or failure from Google.
int numberResultsToMap = -1;
try{numberResultsToMap=Integer.parseInt(encprops.getProperty("numberResultsToMap"));}
catch(Exception e){}

if(numberResultsToMap>-1){
%>
<%=encprops.getProperty("mappedMatchResults").replaceAll("%numberResultsToMap%",encprops.getProperty("numberResultsToMap"))%>
<%
}
%>
</p>
<%
  if (haveGPSData.size() > 0) {
    myShepherd.beginDBTransaction();
    try {
%>

<p><%=encprops.getProperty("mapNote")%></p>
<script src="http://maps.google.com/maps?file=api&amp;v=3.2&amp;key=<%=CommonConfiguration.getGoogleMapsKey() %>" type="text/javascript"></script> <script type="text/javascript">
    function initialize() {
      if (GBrowserIsCompatible()) {
          

        var map = new GMap2(document.getElementById("map_canvas"));
        var bounds = new GLatLngBounds();
		
        
  		var ne_lat = parseFloat(getQueryParameter("ne_lat"));
		var ne_long = parseFloat(getQueryParameter('ne_long'));
		var sw_lat = parseFloat(getQueryParameter('sw_lat'));
		var sw_long = parseFloat(getQueryParameter('sw_long'));
        
		
		<%
			double centroidX=0;
			int countPoints=0;
			double centroidY=0;
			for(int c=0;c<haveGPSData.size();c++) {
				Encounter mapEnc=(Encounter)haveGPSData.get(c);
				countPoints++;
				centroidX=centroidX+Double.parseDouble(mapEnc.getDWCDecimalLatitude());
				centroidY=centroidY+Double.parseDouble(mapEnc.getDWCDecimalLongitude());
			}
			centroidX=centroidX/countPoints;
			centroidY=centroidY/countPoints;
		%>
			
			//map.setCenter(new GLatLng(<%=centroidX%>, <%=centroidY%>), 1);
			map.addControl(new GSmallMapControl());
        	map.addControl(new GMapTypeControl());
			map.setMapType(G_HYBRID_MAP);
			<%
			
			
			
			for(int t=0;t<haveGPSData.size();t++) {
				if((numberResultsToMap==-1) || (t<numberResultsToMap)){
					Encounter mapEnc=(Encounter)haveGPSData.get(t);
					double myLat=(new Double(mapEnc.getDWCDecimalLatitude())).doubleValue();
					double myLong=(new Double(mapEnc.getDWCDecimalLongitude())).doubleValue();
					%>
				          var point<%=t%> = new GLatLng(<%=myLat%>,<%=myLong%>, false);
				          bounds.extend(point<%=t%>);
				          
						  var marker<%=t%> = new GMarker(point<%=t%>);
						  GEvent.addListener(marker<%=t%>, "click", function(){
						  	window.location="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=mapEnc.getEncounterNumber()%>";
						  });
						  GEvent.addListener(marker<%=t%>, "mouseover", function(){
						  	marker<%=t%>.openInfoWindowHtml("<%=encprops.getProperty("markedIndividual")%>: <strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=mapEnc.isAssignedToMarkedIndividual()%>\"><%=mapEnc.isAssignedToMarkedIndividual()%></a></strong><br><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName()%>/encounters/<%=mapEnc.getEncounterNumber()%>/thumb.jpg\"></td><td><%=encprops.getProperty("date")%>: <%=mapEnc.getDate()%><br><%=encprops.getProperty("sex")%>: <%=mapEnc.getSex()%><br><br><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=mapEnc.getEncounterNumber()%>\" ><%=encprops.getProperty("go2encounter")%></a></td></tr></table>");
						  });

						  
						  map.addOverlay(marker<%=t%>);
			
		<%	
			}	
		}
		%>		
		if(!bounds.isEmpty()){	
			//map.setZoom();
			map.setCenter(bounds.getCenter(), map.getBoundsZoomLevel(bounds));
		}
		else{
			map.setCenter(new GLatLng(<%=centroidX%>, <%=centroidY%>), 1);
		}
      }
    }
    </script>



<div id="map_canvas" style="width: 510px; height: 340px"></div>

<p><strong><%=encprops.getProperty("exportOptions")%></strong></p>
<p><%=encprops.getProperty("exportedKML")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?<%=request.getQueryString() %>"><%=encprops.getProperty("clickHere")%></a><br />
  <%=encprops.getProperty("exportedKMLTimeline")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?<%=request.getQueryString() %>&addTimeStamp=true"><%=encprops.getProperty("clickHere")%></a>
</p>

<p><%=encprops.getProperty("exportedShapefile")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportShapefile?<%=request.getQueryString() %>"><%=encprops.getProperty("clickHere")%></a>
</p>

<%

    } 
    catch (Exception e) {
      e.printStackTrace();
    }

  }
else {
%>
<p><%=encprops.getProperty("noGPS")%></p>
<%
}  
%>

<table>
  <tr>
    <td align="left">

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>

<%


  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
  rEncounters = null;
  haveGPSData = null;

%>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>




