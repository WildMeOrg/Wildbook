<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="java.util.Properties" %>
<%@ page import="java.util.Vector" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("individualSearchResultsExport.properties", langCode, context);
  Properties propsShared = ShepherdProperties.getProperties("searchResults_shared.properties", langCode, context);

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
    int listNum = endNum;

    int day1 = 1, day2 = 31, month1 = 1, month2 = 12, year1 = 0, year2 = 3000;
    try {
      month1 = (new Integer(request.getParameter("month1"))).intValue();
    } catch (NumberFormatException nfe) {
    }
    try {
      month2 = (new Integer(request.getParameter("month2"))).intValue();
    } catch (NumberFormatException nfe) {
    }
    try {
      year1 = (new Integer(request.getParameter("year1"))).intValue();
    } catch (NumberFormatException nfe) {
    }
    try {
      year2 = (new Integer(request.getParameter("year2"))).intValue();
    } catch (NumberFormatException nfe) {
    }


    Shepherd myShepherd = new Shepherd(context);



    int numResults = 0;


    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    myShepherd.beginDBTransaction();
    String order = "";

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = result.getResult();


    if (rIndividuals.size() < listNum) {
      listNum = rIndividuals.size();
    }
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
  table td {
    padding: 5px !important;
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


	Enumeration params=request.getParameterNames();
	while(params.hasMoreElements()){

		String name=(String)params.nextElement();
		String value=request.getParameter(name);
		
		queryString+=("&"+name+"="+value);
		
	}
	
	
}

%>

  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=propsShared.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=propsShared.getProperty("matchingImages")%>
  </a></li>
   <li><a href="individualMappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=propsShared.getProperty("mappedResults")%>
  </a></li>
  <li><a href="individualSearchResultsAnalysis.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=propsShared.getProperty("analysis")%>
  </a></li>
    <li><a class="active"><%=propsShared.getProperty("export")%>
  </a></li>

</ul>

<p>&nbsp;</p>

<p>
<table border="1" bordercolor="black" cellspacing="0">
	<tr><td bgcolor="#CCCCCC"><strong><%=props.getProperty("capture_title")%></strong><br/><%=props.getProperty("capture_subtitle")%></td></tr>
	<tr><td bgcolor="#FFFFFF"><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/IndividualSearchExportCapture?<%=queryString%>"><%=props.getProperty("click")%></a>
        </td></tr>
</table>
	</p>

	<p>	<table border="1" bordercolor="black" cellspacing="0">
			<tr><td bgcolor="#CCCCCC"><strong><%=props.getProperty("socprog_title")%></strong></td></tr>
			<tr><td bgcolor="#FFFFFF">
		<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/SOCPROGExport?<%=queryString%>"><%=props.getProperty("click")%></a>
</td></tr>
</table>
</p>

	<p>	<table border="1" bordercolor="black" cellspacing="0">
			<tr><td bgcolor="#CCCCCC"><strong><%=props.getProperty("kinalyzer_title")%></strong></td></tr>
			<tr><td bgcolor="#FFFFFF"><%=props.getProperty("link")%>: <a href="http://kinalyzer.cs.uic.edu">http://kinalyzer.cs.uic.edu</a></td></tr>
			<tr><td bgcolor="#FFFFFF">
		<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/KinalyzerExport?<%=queryString%>"><%=props.getProperty("click")%></a>
</td></tr>
</table>
</p>

<p>
<form name="simpleCMR" action="http://<%=CommonConfiguration.getURLLocation(request)%>/SimpleCMRSpecifySessions.jsp?<%=queryString%>" method="get">
		<table border="1" bordercolor="black" cellspacing="0">
			<tr><td bgcolor="#CCCCCC"><strong><%=props.getProperty("simple_title")%></strong></td></tr>
			<tr><td bgcolor="#FFFFFF"><em><%=props.getProperty("simple_text1")%></em></td></tr>
			<tr><td bgcolor="#FFFFFF"><%=props.getProperty("simple_text2")%>: <input type="text" name="numberSessions" size="3" maxLength="3" value="3"/></td></tr>
			<tr><td bgcolor="#FFFFFF"><%=props.getProperty("simple_text3")%>: <input type="checkbox" name="includeIndividualID" /></td></tr>
      <tr><td bgcolor="#FFFFFF"><%=props.getProperty("simple_text4")%>: <input type="checkbox" name="includeQueryComments" /></td></tr>
      <tr><td bgcolor="#FFFFFF"><input type="submit" value="<%=props.getProperty("next")%>"></td></tr>
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

<%
  myShepherd.rollbackDBTransaction();
  startNum += 10;
  endNum += 10;
  if (endNum > numResults) {
    endNum = numResults;
  }


%>
<table width="810px">
  <tr>
    <%
      if ((startNum - 10) > 1) {%>
    <td align="left">
      <p>
        <a
          href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>&sort=<%=request.getParameter("sort")%>"><img
          src="images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
          title="<%=props.getProperty("seePreviousResults")%>"/></a> <a
        href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>&sort=<%=request.getParameter("sort")%>"><%=(startNum - 20)%>
        - <%=(startNum - 11)%>
      </a>
      </p>
    </td>
    <%
      }

      if (startNum < numResults) {
    %>
    <td align="right">
      <p>
        <a
          href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=startNum%>&endNum=<%=endNum%>&sort=<%=request.getParameter("sort")%>"><%=startNum%>
          - <%=endNum%>
        </a> <a
        href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=startNum%>&endNum=<%=endNum%>&sort=<%=request.getParameter("sort")%>"><img
        src="images/Black_Arrow_right.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=props.getProperty("seeNextResults")%>"/></a>
      </p>
    </td>
    <%
      }
    %>
  </tr>
</table>

<p>

    <%
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();


  if (request.getParameter("noQuery") == null) {
%>
<table>
  <tr>
    <td align="left">

      <p><strong><%=propsShared.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=propsShared.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=result.getQueryPrettyPrint().replaceAll("locationField", propsShared.getProperty("location")).replaceAll("locationCodeField", propsShared.getProperty("locationID")).replaceAll("verbatimEventDateField", propsShared.getProperty("verbatimEventDate")).replaceAll("Sex", propsShared.getProperty("sex")).replaceAll("Keywords", propsShared.getProperty("keywords")).replaceAll("alternateIDField", (propsShared.getProperty("alternateID"))).replaceAll("alternateIDField", (propsShared.getProperty("size")))%>
      </p>

      <p class="caption"><strong><%=propsShared.getProperty("jdoql")%>
      </strong><br/>
        <%=result.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<%
  }
%>
</p>


</div>

<jsp:include page="footer.jsp" flush="true"/>



