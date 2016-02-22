<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.Vector" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Properties map_props = ShepherdProperties.getProperties("exportSearchResults.properties", langCode, context);
    Properties propsShared = ShepherdProperties.getProperties("searchResults_shared.properties", langCode, context);
		Properties collabProps = ShepherdProperties.getProperties("collaboration.properties", langCode, context);
    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);

    //set up the vector for matching encounters
    Vector rEncounters = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    //request.setAttribute("gpsOnly", "yes");
    EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    rEncounters = queryResult.getResult();
    
		Vector blocked = Encounter.blocked(rEncounters, request);
    		
  %>

    <style type="text/css">




      #map {
        width: 600px;
        height: 400px;
      }

    </style>
  

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
    font: 0.5em "Arial, sans-serif;
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
  
    <jsp:include page="../header.jsp" flush="true"/>

    <div class="container maincontent">
    

      <h1 class="intro"><%=map_props.getProperty("title")%>
      </h1>
   
   
 
 <ul id="tabmenu">
 
  <li><a href="searchResults.jsp?<%=request.getQueryString() %>"><%=propsShared.getProperty("table")%></a></li>
  <li><a href="thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=propsShared.getProperty("matchingImages")%></a></li>
  <li><a href="mappedSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("mappedResults")%></a></li>
  <li><a href="../xcalendar/calendar2.jsp?<%=request.getQueryString() %>"><%=propsShared.getProperty("resultsCalendar")%></a></li>
  <li><a href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=propsShared.getProperty("analysis")%></a></li>
  <li><a class="active"><%=propsShared.getProperty("export")%></a></li>
 
 </ul>
 
<% if (blocked.size() < 1) { %>
 
 <p><strong><%=map_props.getProperty("exportOptions")%></strong></p>
<p><%=map_props.getProperty("exportedOBIS")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportExcelFile?<%=request.getQueryString()%>"><%=propsShared.getProperty("clickHere")%></a><br />
<%=map_props.getProperty("exportedOBISLocales")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportExcelFile?<%=request.getQueryString()%>&locales=trues"><%=propsShared.getProperty("clickHere")%></a>
</p>

<p><%=map_props.getProperty("exportedEmail")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportEmailAddresses?<%=request.getQueryString()%>"><%=propsShared.getProperty("clickHere")%>
</a>
</p>

<p><%=map_props.getProperty("exportedGeneGIS")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportGeneGISFormat?<%=request.getQueryString()%>">
<%=propsShared.getProperty("clickHere")%></a>
</p>
 
  <p><strong><%=map_props.getProperty("gisExportOptions")%></strong></p>

<p><%=map_props.getProperty("exportedKML")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?<%=request.getQueryString() %>"><%=propsShared.getProperty("clickHere")%></a><br />
  <%=map_props.getProperty("exportedKMLTimeline")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?<%=request.getQueryString() %>&addTimeStamp=true"><%=propsShared.getProperty("clickHere")%></a>
</p>

<p><%=map_props.getProperty("exportedShapefile")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportShapefile?<%=request.getQueryString() %>"><%=propsShared.getProperty("clickHere")%></a>
</p>

<% } else { // dont have access to ALL records, so:  %>

<p><%=collabProps.getProperty("functionalityBlockedMessage")%></p>

<% } %>

 <table>
  <tr>
    <td align="left">

      <p><strong><%=propsShared.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=propsShared.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", propsShared.getProperty("location")).replaceAll("locationCodeField", propsShared.getProperty("locationID")).replaceAll("verbatimEventDateField", propsShared.getProperty("verbatimEventDate")).replaceAll("alternateIDField", propsShared.getProperty("alternateID")).replaceAll("behaviorField", propsShared.getProperty("behavior")).replaceAll("Sex", propsShared.getProperty("sex")).replaceAll("nameField", propsShared.getProperty("nameField")).replaceAll("selectLength", propsShared.getProperty("selectLength")).replaceAll("numResights", propsShared.getProperty("numResights")).replaceAll("vesselField", propsShared.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=propsShared.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>

</div>

 
 <jsp:include page="../footer.jsp" flush="true"/>

