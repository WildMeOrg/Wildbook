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
         import="org.ecocean.servlet.ServletUtilities,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*" %>



<html>
<head>



  <%
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load encounterSearch.properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    Properties map_props = new Properties();
    //map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/exportSearchResults.properties"));
    map_props=ShepherdProperties.getProperties("exportSearchResults.properties", langCode, context);

		Properties collabProps = new Properties();
 		collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
    
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



      #map {
        width: 600px;
        height: 400px;
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
  


    
  </head>
 <body onunload="GUnload()">
 <div id="wrapper">
 <div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
 <div id="main">
 
 <ul id="tabmenu">
 
   <li><a href="searchResults.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("table")%>
   </a></li>
   <li><a
     href="thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("matchingImages")%>
   </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=map_props.getProperty("mappedResults")%>
  </a></li>
   <li><a
     href="../xcalendar/calendar2.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("resultsCalendar")%>
   </a></li>
      <li><a
     href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=map_props.getProperty("analysis")%>
   </a></li>
    <li><a class="active"><%=map_props.getProperty("export")%>
   </a></li>
 
 </ul>
 
<% if (blocked.size() < 1) { %>
 
 <p><strong><%=map_props.getProperty("exportOptions")%></strong></p>
<p><%=map_props.getProperty("exportedOBIS")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportExcelFile?<%=request.getQueryString()%>"><%=map_props.getProperty("clickHere")%></a><br />
<%=map_props.getProperty("exportedOBISLocales")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportExcelFile?<%=request.getQueryString()%>&locales=trues"><%=map_props.getProperty("clickHere")%></a>
</p>

<p><%=map_props.getProperty("exportedEmail")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportEmailAddresses?<%=request.getQueryString()%>"><%=map_props.getProperty("clickHere")%>
</a>
</p>

<p><%=map_props.getProperty("exportedGeneGIS")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportGeneGISFormat?<%=request.getQueryString()%>">
<%=map_props.getProperty("clickHere")%></a>
</p>
 
  <p><strong><%=map_props.getProperty("gisExportOptions")%></strong></p>

<p><%=map_props.getProperty("exportedKML")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?<%=request.getQueryString() %>"><%=map_props.getProperty("clickHere")%></a><br />
  <%=map_props.getProperty("exportedKMLTimeline")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportKML?<%=request.getQueryString() %>&addTimeStamp=true"><%=map_props.getProperty("clickHere")%></a>
</p>

<p><%=map_props.getProperty("exportedShapefile")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/EncounterSearchExportShapefile?<%=request.getQueryString() %>"><%=map_props.getProperty("clickHere")%></a>
</p>

<% } else { // dont have access to ALL records, so:  %>

<p><%=collabProps.getProperty("functionalityBlockedMessage")%></p>

<% } %>

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
 
 <jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
