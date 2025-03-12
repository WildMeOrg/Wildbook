<%@ page contentType="text/html; charset=utf-8" 
		language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,java.util.Enumeration, java.util.Vector" %>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>


<%

  String context="context0";
  context=ServletUtilities.getContext(request);
    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResultsExport.properties"));
    props = ShepherdProperties.getProperties("individualSearchResultsExport.properties", langCode,context);




    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("individualSearchResultsExport.jsp");
    myShepherd.beginDBTransaction();
    
    try{




    String order = "";

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);


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

  <jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

<h1 class="intro"><%=props.getProperty("title")%></h1>


<ul id="tabmenu">

<%
String queryString="";
if(request.getQueryString()!=null){
	queryString=request.getQueryString();

	/*
	Enumeration params=request.getParameterNames();
	while(params.hasMoreElements()){

		String name=(String)params.nextElement();
		String value=request.getParameter(name);
		
		queryString+=("&"+name+"="+value);
		
	}
	*/
	
	
}

%>

  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("matchingImages")%>
  </a></li>
   <li><a href="individualMappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("mappedResults")%>
  </a></li>
  <li><a href="individualSearchResultsAnalysis.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("analysis")%>
  </a></li>
    <li><a class="active"><%=props.getProperty("export")%>
  </a></li>

</ul>

<p>&nbsp;</p>


<p>
<table border="1" bordercolor="black" cellspacing="0">
  <tr><td bgcolor="#CCCCCC"><strong><%=props.getProperty("pictureBook")%></strong><br/><%=props.getProperty("generatePictureBook") %></td></tr>
  <tr><td bgcolor="#FFFFFF"><a href="//<%=CommonConfiguration.getURLLocation(request)%>/pictureBook.jsp?<%=queryString%>">
    <%=props.getProperty("clickHere") %></a>
        </td></tr>
</table>
</p>

<!--
<p>
<table border="1" bordercolor="black" cellspacing="0">
  <tr><td bgcolor="#CCCCCC"><strong>Picture Book</strong><br/><%=props.getProperty("generatePictureBook") %></td></tr>
  <tr>
  	<td bgcolor="#FFFFFF">
  	
		<form name="pictureBook" action="//<%=CommonConfiguration.getURLLocation(request)%>/pictureBook.jsp?<%=queryString%>" method="get">
		  	<%=props.getProperty("maxPagesToDisplay") %> <input type="text" name="maxPages" size="5" maxLength="10" value="50"/>
		    	
		  <input type="submit" value="<%=props.getProperty("clickHere") %>">
	    </form>
    
    </td>
  </tr>
</table>
</p>
-->

<p>
<table border="1" bordercolor="black" cellspacing="0">
	<tr><td bgcolor="#CCCCCC"><strong>CAPTURE with annual seasons (example only)</strong><br/>For use with the web version available <a href="http://www.mbr-pwrc.usgs.gov/software/capture.html">here.</a></td></tr>
	<tr><td bgcolor="#FFFFFF"><a href="//<%=CommonConfiguration.getURLLocation(request)%>/IndividualSearchExportCapture?<%=queryString%>">
		Click here</a>
        </td></tr>
</table>
	</p>

	<p>	<table border="1" bordercolor="black" cellspacing="0">
			<tr><td bgcolor="#CCCCCC"><strong>SOCPROG Excel</strong></td></tr>
			<tr><td bgcolor="#FFFFFF">
		<a href="//<%=CommonConfiguration.getURLLocation(request)%>/SOCPROGExport?<%=queryString%>">
Click here</a>
</td></tr>
</table>
</p>

	<p>	<table border="1" bordercolor="black" cellspacing="0">
			<tr><td bgcolor="#CCCCCC"><strong>Genetics Export CSV File</strong></td></tr>
			<tr><td bgcolor="#FFFFFF">
		<a href="//<%=CommonConfiguration.getURLLocation(request)%>/KinalyzerExport?<%=queryString%>">
Click here</a>
</td></tr>
</table>
</p>

<p>
<form name="simpleCMR" action="//<%=CommonConfiguration.getURLLocation(request)%>/SimpleCMRSpecifySessions.jsp?<%=queryString%>" method="get">
		<table border="1" bordercolor="black" cellspacing="0">
			<tr>
			  <td bgcolor="#CCCCCC"><strong>Simple Mark-Recapture History File Export (single site, single state)</strong></td></tr>
			
			<tr><td bgcolor="#FFFFFF"><em>This output file (an .inp file) is designed for use with <a href="http://www.phidot.org/software/mark/index.html" target="_blank">Program MARK</a>, <a href="http://www.phidot.org/software/mark/rmark/" target="_blank">RMARK</a>, U-CARE, and other mark-recapture analysis packages using individual capture history file formats.
			This is a single state, single site format. If you have specified one or more location IDs in the search, the first one specified in the list will be used to determine the capture history
			for each individual animal. The options below also allow you to include details of this search within the .inp file. These comments in the /* ... */ format are acceptable within Program MARK but may not be readable by other applications.</em></td></tr>
			
			
			<tr><td bgcolor="#FFFFFF">Number of capture sessions: <input type="text" name="numberSessions" size="3" maxLength="3" value="3"/></td></tr>
			<tr><td bgcolor="#FFFFFF">Include marked individual ID as a comment at the end of each line (Program MARK only): <input type="checkbox" name="includeIndividualID" /></td></tr>
            <tr><td bgcolor="#FFFFFF">Include search query summary as a comment and URL at the start of the file (Program MARK only): <input type="checkbox" name="includeQueryComments" /></td></tr>
            
            <tr><td bgcolor="#FFFFFF"><input type="submit" value="Next"></td></tr>
		</table>
		<%
Enumeration params=request.getParameterNames();
while(params.hasMoreElements()){

	String name=(String)params.nextElement();
	String value=request.getParameter(name);
%>
	<input type="hidden" id="<%=name %>" name="<%=name %>" value="<%=value %>" />
<%
}
%>
	<form>
</p>



<p>

    <%
  	if (request.getParameter("noQuery") == null) {
	%>
		<table>
		  <tr>
		    <td align="left">
		
		      <p><strong><%=props.getProperty("queryDetails")%>
		      </strong></p>
		
		      <p class="caption"><strong><%=props.getProperty("prettyPrintResults") %>
		      </strong><br/>
		        <%=result.getQueryPrettyPrint().replaceAll("locationField", props.getProperty("location")).replaceAll("locationCodeField", props.getProperty("locationID")).replaceAll("verbatimEventDateField", props.getProperty("verbatimEventDate")).replaceAll("Sex", props.getProperty("sex")).replaceAll("Keywords", props.getProperty("keywords")).replaceAll("alternateIDField", (props.getProperty("alternateID"))).replaceAll("alternateIDField", (props.getProperty("size")))%>
		      </p>
		
		      <p class="caption"><strong><%=props.getProperty("jdoql")%>
		      </strong><br/>
		        <%=result.getJDOQLRepresentation()%>
		      </p>
		
		    </td>
		  </tr>
		</table>
	<%
	  }
	%>
	
	<%
    }
    catch(Exception e){
    	e.printStackTrace();
    }
    finally{
  		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
    }

%>
	



</div>

<jsp:include page="footer.jsp" flush="true"/>



