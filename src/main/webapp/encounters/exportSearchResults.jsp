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
  


    
  </head>
 <body onunload="GUnload()">
 <div id="wrapper">
 <div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
 <div id="main">
 
  <table width="810px" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <p>

      <h1 class="intro"><%=map_props.getProperty("title")%>
      </h1>
    </p>
    </td>
  </tr>
</table>
 
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
