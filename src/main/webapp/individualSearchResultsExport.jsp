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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, java.util.Properties, java.util.Vector" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


<html>
<head>
  <%

    //let's load out properties
    Properties props = new Properties();
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
      langCode = (String) session.getAttribute("langCode");
    }
    props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResultsExport.properties"));


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


    Shepherd myShepherd = new Shepherd();



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
  <title><%=CommonConfiguration.getHTMLTitle() %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>

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
<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">
<ul id="tabmenu">


  <li><a href="individualSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("matchingImages")%>
  </a></li>
   <li><a href="individualMappedSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("mappedResults")%>
  </a></li>
  <li><a href="individualSearchResultsAnalysis.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("analysis")%>
  </a></li>
    <li><a class="active"><%=props.getProperty("export")%>
  </a></li>

</ul>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <br/>

      <h1 class="intro">
        <%=props.getProperty("title")%>
      </h1>

    </td>
  </tr>
</table>


<p>Example output: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/IndividualSearchExportCapture?<%=request.getQueryString()%>">
CAPTURE with annual seasons</a>
</p>

<p><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/SOCPROGExport?<%=request.getQueryString()%>">
SOCPROG Excel export file</a>
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
          href="individualSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>&sort=<%=request.getParameter("sort")%>"><img
          src="images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
          title="<%=props.getProperty("seePreviousResults")%>"/></a> <a
        href="individualSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>&sort=<%=request.getParameter("sort")%>"><%=(startNum - 20)%>
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
          href="individualSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=startNum%>&endNum=<%=endNum%>&sort=<%=request.getParameter("sort")%>"><%=startNum%>
          - <%=endNum%>
        </a> <a
        href="individualSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>&startNum=<%=startNum%>&endNum=<%=endNum%>&sort=<%=request.getParameter("sort")%>"><img
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
</p>



<p></p>
<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


