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
         import="org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.CommonConfiguration, org.ecocean.Shepherd, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector" %>
<html>
<%
  session.setMaxInactiveInterval(6000);
  String num = request.getParameter("number");
  Shepherd myShepherd = new Shepherd();
  if (request.getParameter("writeThis") == null) {
    myShepherd = (Shepherd) session.getAttribute(request.getParameter("number"));
  }
  Shepherd altShepherd = new Shepherd();
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
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isResearcher"
             value="<%=request.isUserInRole("researcher")%>"/>
  <jsp:param name="isManager"
             value="<%=request.isUserInRole("manager")%>"/>
  <jsp:param name="isReviewer"
             value="<%=request.isUserInRole("reviewer")%>"/>
  <jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>"/>
</jsp:include>
<div id="page">


<div id="main">

<ul id="tabmenu">
  <li><a
    href="encounter.jsp?number=<%=request.getParameter("number")%>">Encounter
    <%=request.getParameter("number")%>
  </a></li>
  <li><a class="active">Modified Groth</a></li>

  <%
    String fileSider = "";
    File finalXMLFile;
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      //finalXMLFile=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightI3SScan.xml");
      finalXMLFile = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFullRightI3SScan.xml")));


      side2 = "right";
      fileSider = "&rightSide=true";
    } else {
      //finalXMLFile=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullI3SScan.xml");
      finalXMLFile = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFullI3SScan.xml")));
    }
    if (finalXMLFile.exists()) {
  %>

  <li><a
    href="i3sScanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%>&I3S=true<%=fileSider%>">I3S</a>
  </li>
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
        file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFullRightScan.xml")));


        side = "right";
      } else {
        //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullScan.xml");
        file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFullScan.xml")));

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
    } catch (java.io.IOException ioe) {
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
  href="<%=CommonConfiguration.getWikiLocation()%>scan_results"
  target="_blank"><img src="../images/information_icon_svg.gif"
                       alt="Help" border="0" align="absmiddle"></a></h2>
</p>
<p><strong>The following encounter(s) received the highest
  match values against a <%=side%>-side scan of encounter# <a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=num%>"><%=num%>
  </a>.</strong></p>


<%
  if (xmlOK) {%>
<p><img src="../alert.gif" hspace="2" vspace="2" align="absmiddle"><strong>Saved
  scan data may be old and invalid. Check the date below and run a fresh
  scan for the latest results.</strong></p>

<p><em>Date of scan: <%=scanDate%>
</em></p>
<%}%>
<table width="524" border="1" cellspacing="0" cellpadding="5">
  <tr>
    <td width="143" align="left" valign="top">
      <%if ((request.getParameter("epsilon") != null) && (request.getParameter("R") != null)) {%>
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
    </td>
    <td width="355" align="left" valign="top">
      <table width="100%" border="1" align="left" cellpadding="3">
        <tr align="left" valign="top">
          <td><strong>Shark</strong></td>
          <td><strong> Encounter</strong></td>
          <td><strong>Fraction Matched Triangles </strong></td>
          <td><strong>Match Score </strong></td>
          <%//  <td><strong>Triangle logM Breakdown</strong></td> %>
          <td><strong>logM std. dev.</strong></td>
          <td><strong>Confidence</strong></td>
          <td><strong>Matched Keywords</strong></td>

        </tr>
        <%
          if (!xmlOK) {

            MatchObject[] results = new MatchObject[1];
            results = matches;
            Arrays.sort(results, new MatchComparator());
            for (int p = 0; p < results.length; p++) {
              if ((results[p].matchValue != 0) || (request.getAttribute("singleComparison") != null)) {%>
        <tr align="left" valign="top">
          <td>
            <table width="62">

              <tr>
                <td width="60" align="left"><a
                  href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=results[p].getIndividualName()%>"><%=results[p].getIndividualName()%>
                </a></td>
              </tr>
            </table>
          </td>
          <%if (results[p].encounterNumber.equals("N/A")) {%>
          <td>N/A</td>
          <%} else {%>
          <td><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=results[p].encounterNumber%>"><%=results[p].encounterNumber%>
          </a></td>
          <%
            }
            String adjustedMatchValueString = (new Double(results[p].adjustedMatchValue)).toString();
            if (adjustedMatchValueString.length() > 5) {
              adjustedMatchValueString = adjustedMatchValueString.substring(0, 5);
            }
          %>
          <td><%=(adjustedMatchValueString)%><br>
          </td>
          <%
            String finalscore2 = (new Double(results[p].matchValue * results[p].adjustedMatchValue)).toString();

            //trim the length of finalscore
            if (finalscore2.length() > 7) {
              finalscore2 = finalscore2.substring(0, 6);
            }
          %>
          <td><%=finalscore2%>
          </td>

          <td><font size="-2"><%=results[p].getLogMStdDev()%>
          </font></td>
          <td><font size="-2"><%=results[p].getEvaluation()%>
          </font></td>

        </tr>

        <%
              //end if matchValue!=0 loop
            }
            //end for loop
          }

//or use XML output here	
        } else {
          doc = xmlReader.read(file);
          root = doc.getRootElement();

          Iterator matchsets = root.elementIterator("match");
          while (matchsets.hasNext()) {
            Element match = (Element) matchsets.next();
            List encounters = match.elements("encounter");
            Element enc1 = (Element) encounters.get(0);
            Element enc2 = (Element) encounters.get(1);
        %>
        <tr align="left" valign="top">
          <td>
            <table width="62">

              <tr>
                <td width="60" align="left"><a
                  href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=enc1.attributeValue("assignedToShark")%>"><%=enc1.attributeValue("assignedToShark")%>
                </a></td>
              </tr>
            </table>
          </td>
          <%if (enc1.attributeValue("number").equals("N/A")) {%>
          <td>N/A</td>
          <%} else {%>
          <td><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc1.attributeValue("number")%>"><%=enc1.attributeValue("number")%>
          </a></td>
          <%
            }
            String adjustedpoints = "No Adj.";
            adjustedpoints = match.attributeValue("adjustedpoints");
            if (adjustedpoints == null) {
              adjustedpoints = "&nbsp;";
            }
            if (adjustedpoints.length() > 5) {
              adjustedpoints = adjustedpoints.substring(0, 5);
            } else {
              adjustedpoints = adjustedpoints + "<br>";
            }
          %>
          <td><%=adjustedpoints%>
          </td>
          <%
            String finalscore = "&nbsp;";
            try {
              if (match.attributeValue("finalscore") != null) {
                finalscore = match.attributeValue("finalscore");
              }
            } catch (NullPointerException npe) {
            }

            //trim the length of finalscore
            if (finalscore.length() > 7) {
              finalscore = finalscore.substring(0, 6);
            }

          %>
          <td><%=finalscore%>
          </td>


          <td><font size="-2"><%=match.attributeValue("logMStdDev")%>
          </font></td>
          <%
            String evaluation = "No Adj.";
            evaluation = match.attributeValue("evaluation");
            if (evaluation == null) {
              evaluation = "&nbsp;";
            }

          %>
          <td><font size="-2"><%=evaluation%>
          </font></td>
          <td>
            <%
              String keywords = "";
              for (Iterator i = match.elementIterator("keywords"); i.hasNext();) {
                Element kws = (Element) i.next();
                // iterate the keywords themselves
                for (Iterator j = kws.elementIterator("keyword"); j.hasNext();) {
                  Element kws2 = (Element) j.next();
                  keywords = keywords + "<li>" + kws2.attributeValue("name") + "</li>";
                }
              }
              if (keywords.length() <= 1) {
                keywords = "&nbsp;";
              }
            %> <font size="-2">
            <ul><%=keywords%>
            </ul>
          </font></td>
        </tr>

        <%


            }
          }

        %>

      </table>
    </td>
  </tr>
</table>
</tr>
</table>

<p><font size="+1">Visualizations for Potential Matches (as
  scored above)</font></p>

<p>

<p>
  <%
    String feedURL = "http://" + CommonConfiguration.getURLLocation(request) + "/TrackerFeed?number=" + num;
    String baseURL = "http://" + CommonConfiguration.getURLLocation(request) + "/encounters/";


//myShepherd.rollbackDBTransaction();
    myShepherd = null;
    doc = null;
    root = null;
    initresults = null;
    file = null;
    xmlReader = null;

    System.out.println("Base URL is: " + baseURL);
    if (xmlOK) {
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        feedURL = baseURL + num + "/lastFullRightScan.xml?";
      } else {
        feedURL = baseURL + num + "/lastFullScan.xml?";
      }
    }
    String rightSA = "";
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      rightSA = "&filePrefix=extractRight";
    }
    System.out.println("I made it to the Flash without exception.");
  %>
  <OBJECT id=sharkflash
          codeBase=http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=6,0,0,0
          height=450 width=800 classid=clsid:D27CDB6E-AE6D-11cf-96B8-444553540000>
    <PARAM NAME="movie"
           VALUE="tracker.swf?sessionId=<%=sessionId%>&rootURL=<%=CommonConfiguration.getURLLocation(request)%>&baseURL=<%=baseURL%>&feedurl=<%=feedURL%><%=rightSA%>">
    <PARAM NAME="qualidty" VALUE="high">
    <PARAM NAME="scale" VALUE="exactfit">
    <PARAM NAME="bgcolor" VALUE="#ddddff">
    <EMBED
      src="tracker.swf?sessionId=<%=sessionId%>&rootURL=<%=CommonConfiguration.getURLLocation(request)%>&baseURL=<%=baseURL%>&feedurl=<%=feedURL%>&time=<%=System.currentTimeMillis()%><%=rightSA%>"
      quality=high scale=exactfit bgcolor=#ddddff swLiveConnect=TRUE
      WIDTH="800" HEIGHT="450" NAME="sharkflash" ALIGN=""
      TYPE="application/x-shockwave-flash"
      PLUGINSPAGE="http://www.macromedia.com/go/getflashplayer"></EMBED>
  </OBJECT>
</p>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>
