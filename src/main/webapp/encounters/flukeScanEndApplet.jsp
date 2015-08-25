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
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>
<html>
<%

String context="context0";
context=ServletUtilities.getContext(request);

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");



  session.setMaxInactiveInterval(6000);
  String num = request.getParameter("number");
	String encSubdir = Encounter.subdir(num);
  Shepherd myShepherd = new Shepherd(context);
  if (request.getParameter("writeThis") == null) {
    myShepherd = (Shepherd) session.getAttribute(request.getParameter("number"));
  }
  //Shepherd altShepherd = new Shepherd(context);
  String sessionId = session.getId();
  boolean xmlOK = false;
  SAXReader xmlReader = new SAXReader();
  File file = new File("foo");
  String scanDate = "";
  String C = "";
  String R = "";
  String epsilon = "";
  String Sizelim = "";
  String maxTriangleRotation = "";
  String side2 = "";
%>

<head>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
          <link href="../css/pageableTable.css" rel="stylesheet" type="text/css"/>
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
      
</head>

<style type="text/css">
 
td.ptcol-overall_score,
td.ptcol-score_holmbergIntersection,
td.ptcol-score_fastDTW,
td.ptcol-score_I3S,
td.ptcol-score_proportion {
	text-align: right;
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

<%
	//Path json = Paths.get(encountersDir.getAbsolutePath()+"/" + encSubdir + "/flukeMatching.json");
	String json = new String(Files.readAllBytes(Paths.get(encountersDir.getAbsolutePath()+"/" + encSubdir + "/flukeMatching.json")));
%>
<script>
var flukeMatchingData = <%=json%>;
</script>


<body>
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="page">


<div id="main">

<ul id="tabmenu">
  <li><a
    href="encounter.jsp?number=<%=request.getParameter("number")%>">Encounter
    
  </a></li>
  <%
    String fileSider = "";
    File finalXMLFile;
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      finalXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullRightScan.xml");

      side2 = "right";
      fileSider = "&rightSide=true";
    } else {
      finalXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullScan.xml");

    }
    if (finalXMLFile.exists()) {
    	if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("ModifiedGroth")!=-1)){
			  %>
			  <li><a class="active" >Modified Groth</a></li>
			
			  <%
    	}
    }
    
    if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("I3S")!=-1)){
		 
  %>
    <li><a href="i3sScanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%>&I3S=true<%=fileSider%>">I3S</a>
  </li>
  <%
    }
    if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("FastDTW")!=-1)){
		 
  %>
  
    <li><a href="fastDTWScanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%>&I3S=true<%=fileSider%>">FastDTW</a></li>
  
    <%
    }
    
    if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("Whitehead")!=-1)){
		 
    %>
  
    <li><a href="geroScanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%>&I3S=true<%=fileSider%>">Gero</a></li>
  
  
  <%
    }
    if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("HolmbergIntersection")!=-1)){
		 
  %>
  <li><a href="intersectionScanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%>&I3S=true<%=fileSider%>">Intersection</a>
  
  
<%
    }
%>

</ul>

<%
  Vector initresults = new Vector();
  Document doc;
  Element root;
  String side = "left";

  if (request.getParameter("writeThis") == null) {
    initresults = myShepherd.matches;
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      side = "right";
    }
  } else {

//read from the written XML here if flagged
    try {
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightScan.xml");
        file = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullRightScan.xml");


        side = "right";
      } else {
        //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullScan.xml");
        file = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullScan.xml");

      }
      doc = xmlReader.read(file);
      root = doc.getRootElement();
      scanDate = root.attributeValue("scanDate");
      xmlOK = true;
      R = root.attributeValue("R");
      C = root.attributeValue("C");
      maxTriangleRotation = root.attributeValue("maxTriangleRotation");
      Sizelim = root.attributeValue("Sizelim");
      epsilon = root.attributeValue("epsilon");
    } catch (Exception ioe) {
      System.out.println("Error accessing the stored scan XML data for encounter: " + num);
      ioe.printStackTrace();
      initresults = myShepherd.matches;
      xmlOK = false;
    }

  }
  MatchObject[] matches = new MatchObject[0];
  if (!xmlOK) {
    int resultsSize = initresults.size();
    System.out.println(resultsSize);
    matches = new MatchObject[resultsSize];
    for (int a = 0; a < resultsSize; a++) {
      matches[a] = (MatchObject) initresults.get(a);
    }
    R = request.getParameter("R");
    C = request.getParameter("C");
    maxTriangleRotation = request.getParameter("maxTriangleRotation");
    Sizelim = request.getParameter("Sizelim");
    epsilon = request.getParameter("epsilon");
  }
%>

<p>

<h2>Modified Groth Scan Results <a
  href="<%=CommonConfiguration.getWikiLocation(context)%>scan_results"
  target="_blank"><img src="../images/information_icon_svg.gif"
                       alt="Help" border="0" align="absmiddle"></a></h2>
</p>
<p>The following encounter(s) received the highest
  match values against a <%=side%>-side scan of encounter <a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=num%>"><%=num%></a>.</p>


<%
  if (xmlOK) {%>
<p><img src="../images/Crystal_Clear_action_flag.png" width="28px" height="28px" hspace="2" vspace="2" align="absmiddle">&nbsp;<strong>Saved
  scan data may be old and invalid. Check the date below and run a fresh
  scan for the latest results.</strong></p>

<p><em>Date of scan: <%=scanDate%>
</em></p>
<%}%>

<p><a href="#resultstable">See the table below for score breakdowns.</a></p>

<p>

<%
/*
    String feedURL = "http://" + CommonConfiguration.getURLLocation(request) + "/TrackerFeed?number=" + num;
    String baseURL = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/";

    System.out.println("Base URL is: " + baseURL);
    if (xmlOK) {
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        feedURL = baseURL + encSubdir + "/lastFullRightScan.xml?";
      } else {
        feedURL = baseURL + encSubdir + "/lastFullScan.xml?";
      }
    }
    String rightSA = "";
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      rightSA = "&filePrefix=extractRight";
    }
    System.out.println("I made it to the Flash without exception.");
*/
  %>
  
      <a name="resultstable"/>


<div class="pageableTable-wrapper">
	<div id="progress">loading...</div>
	<table id="results-table"></table>
	<div id="results-slider"></div>
</div>

  <%



//myShepherd.rollbackDBTransaction();
    myShepherd = null;
    doc = null;
    root = null;
    initresults = null;
    file = null;
    xmlReader = null;
    
if ((request.getParameter("epsilon") != null) && (request.getParameter("R") != null)) {%>
      <p><font size="+1">Custom Scan</font></p>
      <%} else {%>
      <p><font size="+1">Standard Scan</font></p>
      <%}%>
      <p>For this scan, the following variables were used:</p>
      <ul>


        <li>epsilon (<%=epsilon%>)</li>
        <li>R (<%=R%>)</li>
        <li>Sizelim (<%=Sizelim%>)</li>
        <li>C (<%=C%>)</li>
        <li>Max. Triangle Rotation (<%=maxTriangleRotation%>)</li>

      </ul>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

<script src="../javascript/tablesorter/jquery.tablesorter.js"></script>
<script src="//code.jquery.com/ui/1.11.2/jquery-ui.js"></script>
<link rel="stylesheet" href="//code.jquery.com/ui/1.11.2/themes/smoothness/jquery-ui.css">
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
<link rel="stylesheet" href="../css/pageableTable.css" />
<script src="../javascript/tsrt.js"></script>
<script src="../javascript/flukeScanEnd.js"></script>

</body>
</html>
