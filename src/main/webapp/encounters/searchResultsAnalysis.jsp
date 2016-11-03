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

   
    


      <h1 class="intro"><%=encprops.getProperty("title")%></h1>
    
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
    //Shepherd myShepherd = new Shepherd(context);
    //myShepherd.setAction("searchResultsAnalysis.jsp");


    //kick off the transaction
    //myShepherd.beginDBTransaction();

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
    
</div>
 
  <jsp:include page="../footer.jsp" flush="true"/>


