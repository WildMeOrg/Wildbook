<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2015 \
  
  
  Jason Holmberg
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
         import="org.ecocean.servlet.ServletUtilities,java.text.DecimalFormat,org.ecocean.Util.MeasurementDesc,org.apache.commons.math.stat.descriptive.SummaryStatistics,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*, org.ecocean.security.Collaboration" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties encprops = new Properties();
//encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/searchResultsAnalysis.properties"));
encprops=ShepherdProperties.getProperties("searchResultsAnalysis.properties", langCode, context);


%>

<html>
<head>

<title><%=CommonConfiguration.getHTMLTitle(context)%>
</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta name="Description"
      content="<%=CommonConfiguration.getHTMLDescription(context)%>"/>
<meta name="Keywords"
      content="<%=CommonConfiguration.getHTMLKeywords(context)%>"/>
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context)%>"/>
<link href="<%=CommonConfiguration.getCSSURLLocation(request,context)%>"
      rel="stylesheet" type="text/css"/>
<link rel="shortcut icon"
      href="<%=CommonConfiguration.getHTMLShortcutIcon(context)%>"/>


<style type="text/css">

.ptcol-individualID {
	position: relative;
}
.ptcol-individualID a.pt-vm-button {
	position: absolute;
	display: none;
	left: 5px;
	top: 5px;
	border: solid 1px black;
	border-radius: 3px;
	background-color: #DDD;
	padding: 0 3px;
	color: black;
	text-decoration: none;
	cursor: pointer;
}

tr:hover .ptcol-individualID span.unassigned {
	display:hidden;
}

tr:hover .ptcol-individualID a.pt-vm-button {
	display: inline-block;
}
a.pt-vm-button:hover {
	background-color: #FF5;
}

.ptcol-thumb {
	width: 75px !important;
}

td.tdw {
	position: relative;
}

td.tdw div {
	height: 16px;
	overflow-y: hidden;
}


td.tdw:hover div {
	position: absolute;
	z-index: 20;
	background-color: #EFA;
	outline: 3px solid #EFA;
	min-height: 16px;
	height: auto;
	overflow-y: auto;
}


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

<body>
<div id="wrapper">
<div id="page">
    <jsp:include page="../header.jsp" flush="true">
   
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide-solo">

        <div id="maintext">
    
     <table width="810px" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <p>

      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>
    </p>
    </td>
  </tr>
</table>
 
 <ul id="tabmenu">
 
   <li><a href="searchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("table")%>
   </a></li>
   <li><a
     href="thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("matchingImages")%>
   </a></li>
   <li><a
     href="mappedSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("mappedResults") %>
   </a></li>
   <li><a
     href="../xcalendar/calendar2.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("resultsCalendar")%>
   </a></li>
   <li><a class="active"><%=encprops.getProperty("analysis")%>
   </a></li>
      <li><a
     href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("export")%>
   </a></li>
 
 </ul>
    
<%
  
  DecimalFormat df = new DecimalFormat("#.##");


    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);


    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    StringBuffer prettyPrint=new StringBuffer("");
  	Map<String,Object> paramMap = new HashMap<String, Object>();
    String jdoqlString = EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);
    //System.out.println("jdoQLstring is: "+jdoqlString);
  %>
   
    <jsp:include page="encounterSearchResultsAnalysisEmbed.jsp" flush="true">
    	<jsp:param name="jdoqlString" value="<%=jdoqlString%>" />
    </jsp:include>
    
 
  <jsp:include page="../footer.jsp" flush="true"/>

</div>
</div>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
