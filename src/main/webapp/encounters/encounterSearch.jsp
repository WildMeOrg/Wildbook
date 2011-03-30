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
         import="org.ecocean.*,javax.jdo.Extent, javax.jdo.Query, java.util.ArrayList" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Properties" %>

<html xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml">

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

  <!-- Sliding div content: STEP1 Place inside the head section -->
  <script type="text/javascript"
          src="http://ajax.googleapis.com/ajax/libs/jquery/1.3.2/jquery.min.js"></script>
  <script type="text/javascript" src="../javascript/animatedcollapse.js"></script>
  <!-- /STEP1 Place inside the head section -->
  <!-- STEP2 Place inside the head section -->
  <script type="text/javascript">
    animatedcollapse.addDiv('location', 'fade=1')
    animatedcollapse.addDiv('map', 'fade=1')
    animatedcollapse.addDiv('date', 'fade=1')
    animatedcollapse.addDiv('observation', 'fade=1')
    animatedcollapse.addDiv('identity', 'fade=1')
    animatedcollapse.addDiv('metadata', 'fade=1')
    animatedcollapse.addDiv('export', 'fade=1')

    animatedcollapse.ontoggle = function($, divobj, state) { //fires each time a DIV is expanded/contracted
      //$: Access to jQuery
      //divobj: DOM reference to DIV being expanded/ collapsed. Use "divobj.id" to get its ID
      //state: "block" or "none", depending on state
    }
    animatedcollapse.init()
  </script>
  <!-- /STEP2 Place inside the head section -->


</head>

<style type="text/css">v\:* {
  behavior: url(#default#VML);
}</style>

<script>
  function resetMap() {
    var ne_lat_element = document.getElementById('ne_lat');
    var ne_long_element = document.getElementById('ne_long');
    var sw_lat_element = document.getElementById('sw_lat');
    var sw_long_element = document.getElementById('sw_long');

    ne_lat_element.value = "";
    ne_long_element.value = "";
    sw_lat_element.value = "";
    sw_long_element.value = "";

  }
</script>

<body onload="initialize();resetMap()" onunload="GUnload();resetMap()">

<%
  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);
  int firstYear = 1980;

  Shepherd myShepherd = new Shepherd();
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
  myShepherd.beginDBTransaction();
  try {
    firstYear = myShepherd.getEarliestSightingYear();
    nowYear = myShepherd.getLastSightingYear();
  } catch (Exception e) {
    e.printStackTrace();
  }

//let's load encounterSearch.properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Properties encprops = new Properties();
  encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounterSearch.properties"));


%>


<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">
<table width="810">
<tr>
<td>
<p>

<h1 class="intro"><%=encprops.getProperty("title")%>
  <a href="<%=CommonConfiguration.getWikiLocation()%>searching#encounter_search" target="_blank">
    <img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/>
  </a>
</h1>
</p>
<p><em><%=encprops.getProperty("instructions")%>
</em></p>

<form action="thumbnailSearchResults.jsp" method="get" name="search" id="search">

  <%
		if(request.getParameter("referenceImageName")!=null){
		%>
<p><strong>Reference Image</strong></p>

<p>You have selected this image as a reference for comparison with the results of this search</p>
<input name="referenceImageName" type="hidden"
       value="<%=request.getParameter("referenceImageName") %>"/>

<p><img width="810px" src="<%=request.getParameter("referenceImageName") %>"/></p>
<table>
											<tr>
												<td align="left" valign="top">
										
												<table>
										<%
										int slashPosition=request.getParameter("referenceImageName").indexOf("/");
										String encNum=request.getParameter("referenceImageName").substring(0,slashPosition);
										Encounter thisEnc = myShepherd.getEncounter(encNum);
										%>
								
										<tr><td><span class="caption">Location: <%=thisEnc.getLocation() %></span></td></tr>
										<tr><td><span class="caption">Location ID: <%=thisEnc.getLocationID() %></span></td></tr>
										<tr><td><span class="caption">Date: <%=thisEnc.getDate() %></span></td></tr>
										<%
										if(thisEnc.getIndividualID()!=null){
										%>
											<tr><td><span class="caption">Identified as: 
											<%
											if(!thisEnc.getIndividualID().equals("Unassigned")){
											%>
												<a href="../individuals.jsp?number=<%=thisEnc.getIndividualID() %>" target="_blank">
											<%
											}
											%>
											<%=thisEnc.getIndividualID() %>
											<%
											if(!thisEnc.getIndividualID().equals("Unassigned")){
											%>
												</a>
											<%
											}
											%>
											</span></td></tr>
										<%
										}
										%>
										<tr><td><span class="caption">Encounter: <a href="encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" target="_blank"><%=thisEnc.getCatalogNumber() %></a></span></td></tr>
										

										
										
<%
										if(thisEnc.getVerbatimEventDate()!=null){
										%>
											<tr>
											
											<td><span class="caption"><%=encprops.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span></td></tr>
										<%
										}
										%>


										</table>
  <%
		}
		%>

<table>

<tr>
  <td width="810px">

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/></a>
      <a href="javascript:animatedcollapse.toggle('map')" style="text-decoration:none"><font
        color="#000000">Location filter (map)</font></a></h4>

    <script
      src="http://maps.google.com/maps?file=api&amp;v=2&amp;key=<%=CommonConfiguration.getGoogleMapsKey() %>"
      type="text/javascript">
    </script>

    <script src="visual_files/keydragzoom.js" type="text/javascript"></script>

    <script type="text/javascript">
      function initialize() {
        if (GBrowserIsCompatible()) {
          var map = new GMap2(document.getElementById("map_canvas"));
          map.setMapType(G_HYBRID_MAP);
          map.addControl(new GSmallMapControl());
          map.setCenter(new GLatLng(0, 180), 1);


          map.enableKeyDragZoom({
            visualEnabled: true,
            visualPosition: new GControlPosition(G_ANCHOR_TOP_LEFT, new GSize(16, 103)),
            visualSprite: "http://maps.gstatic.com/mapfiles/ftr/controls/dragzoom_btn.png",
            visualSize: new google.maps.Size(20, 20),
            visualTips: {
              off: "Turn on",
              on: "Turn off"
            }
          });

          var dz = map.getDragZoomObject();
          GEvent.addListener(dz, 'dragend', function (bnds) {
            var ne_lat_element = document.getElementById('ne_lat');
            var ne_long_element = document.getElementById('ne_long');
            var sw_lat_element = document.getElementById('sw_lat');
            var sw_long_element = document.getElementById('sw_long');

            //GLog.write('KeyDragZoom Ended: ' + bnds.getNorthEast().lat());

            ne_lat_element.value = bnds.getNorthEast().lat();
            ne_long_element.value = bnds.getNorthEast().lng();
            sw_lat_element.value = bnds.getSouthWest().lat();
            sw_long_element.value = bnds.getSouthWest().lng();
          });

          //map.addControl(new GMapTypeControl());


        }
      }
    </script>

    <div id="map">
      <p>Use the arrow and +/- keys to navigate to a portion of the globe of interest, then click
        and drag the <img src="../javascript/zoomin.gif" align="absmiddle"/> icon to select the
        specific search boundaries. You can also use the text boxes below the map to specify exact
        boundaries.</p>

      <div id="map_canvas" style="width: 510px; height: 340px; "></div>
      <p>Northeast corner latitude: <input type="text" id="ne_lat" name="ne_lat"></input> longitude:
        <input type="text" id="ne_long" name="ne_long"></input><br/><br/>
        Southwest corner latitude: <input type="text" id="sw_lat" name="sw_lat"></input> longitude:
        <input type="text" id="sw_long" name="sw_long"></input></p>
    </div>

  </td>
</tr>
<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('location')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Location filters (text)</font></a></h4>

    <div id="location" style="display:none; ">
      <p>Use the fields below to filter the search by a location string (e.g. "Mexico") or to a
        specific, pre-defined location identifier.</p>

      <p><strong><%=encprops.getProperty("locationNameContains")%>:</strong>
        <input name="locationField" type="text" size="60"> <br>
        <em><%=encprops.getProperty("leaveBlank")%>
        </em>
      </p>

      <p><strong><%=encprops.getProperty("locationID")%>:</strong> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation()%>locationID"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span> <br>
        (<em><%=encprops.getProperty("locationIDExample")%>
        </em>)</p>

      <%
        ArrayList<String> locIDs = myShepherd.getAllLocationIDs();
        int totalLocIDs = locIDs.size();


        if (totalLocIDs > 1) {
      %>

      <select multiple size="<%=(totalLocIDs+1) %>" name="locationCodeField" id="locationCodeField">
        <option value="None"></option>
        <%
          for (int n = 0; n < totalLocIDs; n++) {
            String word = locIDs.get(n);
            if (!word.equals("")) {
        %>
        <option value="<%=word%>"><%=word%>
        </option>
        <%
            }
          }
        %>
      </select>
      <%
      } else {
      %>
      <p><em><%=encprops.getProperty("noLocationIDs")%>
      </em></p>
      <%
        }
      %>
    </div>
  </td>

</tr>


<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('date')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Date filters</font></a></h4>
  </td>
</tr>


<tr>
  <td>
    <div id="date" style="display:none;">
      <p>Use the fields below to limit the timeframe of your search.</p>
      <strong><%=encprops.getProperty("sightingDates")%>:</strong><br/>
      <table width="720">
        <tr>
          <td width="670"><label><em>
            &nbsp;<%=encprops.getProperty("day")%>
          </em> <em> <select name="day1" id="day1">
            <option value="1" selected>1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12">12</option>
            <option value="13">13</option>
            <option value="14">14</option>
            <option value="15">15</option>
            <option value="16">16</option>
            <option value="17">17</option>
            <option value="18">18</option>
            <option value="19">19</option>
            <option value="20">20</option>
            <option value="21">21</option>
            <option value="22">22</option>
            <option value="23">23</option>
            <option value="24">24</option>
            <option value="25">25</option>
            <option value="26">26</option>
            <option value="27">27</option>
            <option value="28">28</option>
            <option value="29">29</option>
            <option value="30">30</option>
            <option value="31">31</option>
          </select> <%=encprops.getProperty("month")%>
          </em> <em> <select name="month1" id="month1">
            <option value="1" selected>1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12">12</option>
          </select> <%=encprops.getProperty("year")%>
          </em> <select name="year1" id="year1">
            <% for (int q = firstYear; q <= nowYear; q++) { %>
            <option value="<%=q%>"

              <%
                if (q == firstYear) {
              %>
                    selected
              <%
                }
              %>
              ><%=q%>
            </option>

            <% } %>
          </select> &nbsp;to <em>&nbsp;<%=encprops.getProperty("day")%>
          </em> <em> <select name="day2"
                             id="day2">
            <option value="1">1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12">12</option>
            <option value="13">13</option>
            <option value="14">14</option>
            <option value="15">15</option>
            <option value="16">16</option>
            <option value="17">17</option>
            <option value="18">18</option>
            <option value="19">19</option>
            <option value="20">20</option>
            <option value="21">21</option>
            <option value="22">22</option>
            <option value="23">23</option>
            <option value="24">24</option>
            <option value="25">25</option>
            <option value="26">26</option>
            <option value="27">27</option>
            <option value="28">28</option>
            <option value="29">29</option>
            <option value="30">30</option>
            <option value="31" selected>31</option>
          </select> <%=encprops.getProperty("month")%>
          </em> <em> <select name="month2" id="month2">
            <option value="1">1</option>
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
            <option value="5">5</option>
            <option value="6">6</option>
            <option value="7">7</option>
            <option value="8">8</option>
            <option value="9">9</option>
            <option value="10">10</option>
            <option value="11">11</option>
            <option value="12" selected>12</option>
          </select> <%=encprops.getProperty("year")%>
          </em>
            <select name="year2" id="year2">
              <% for (int q = nowYear; q >= firstYear; q--) { %>
              <option value="<%=q%>"

                <%
                  if (q == nowYear) {
                %>
                      selected
                <%
                  }
                %>
                ><%=q%>
              </option>

              <% } %>
            </select>
          </label></td>
        </tr>
      </table>

      <p><strong><%=encprops.getProperty("verbatimEventDate")%>:</strong> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation()%>verbatimEventDate"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></span></p>

      <%
        ArrayList<String> vbds = myShepherd.getAllVerbatimEventDates();
        int totalVBDs = vbds.size();


        if (totalVBDs > 1) {
      %>

      <select multiple size="<%=(totalVBDs+1) %>" name="verbatimEventDateField"
              id="verbatimEventDateField">
        <option value="None"></option>
        <%
          for (int f = 0; f < totalVBDs; f++) {
            String word = vbds.get(f);
            if (word != null) {
        %>
        <option value="<%=word%>"><%=word%>
        </option>
        <%

            }

          }
        %>
      </select>
      <%

      } else {
      %>
      <p><em><%=encprops.getProperty("noVBDs")%>
      </em></p>
      <%
        }
      %>
    </div>
  </td>
</tr>

<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('observation')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Observation attribute filters</font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="observation" style="display:none; ">
      <p>Use the fields below to filter your search based on observed attributes.</p>

      <p>
      <table align="left">
        <tr>
          <td><strong><%=encprops.getProperty("sex")%>: </strong>
            <label> <input name="male"
                           type="checkbox" id="male" value="male"
                           checked> <%=encprops.getProperty("male")%>
            </label>

            <label> <input name="female"
                           type="checkbox" id="female" value="female" checked>
              <%=encprops.getProperty("female")%>
            </label>
            <label> <input name="unknown"
                           type="checkbox" id="unknown" value="unknown" checked>
              <%=encprops.getProperty("unknown")%>
            </label></td>
        </tr>


        <tr>
          <td><strong><%=encprops.getProperty("status")%>: </strong><label>
            <input name="alive" type="checkbox" id="alive" value="alive"
                   checked> <%=encprops.getProperty("alive")%>
          </label><label>
            <input name="dead" type="checkbox" id="dead" value="dead"
                   checked> <%=encprops.getProperty("dead")%>
          </label>
          </td>
        </tr>
        <tr>
          <td><strong><%=encprops.getProperty("lengthIs")%>: </strong> <select name="selectLength"
                                                                               size="1">
            <option value="gt">&gt;</option>
            <option value="lt">&lt;</option>
            <option value="eq">=</option>
          </select> <select name="lengthField" id="lengthField">
            <option value="skip" selected><%=encprops.getProperty("none")%>
            </option>
            <option value="1.0">1</option>
            <option value="2.0">2</option>
            <option value="3.0">3</option>
            <option value="4.0">4</option>
            <option value="5.0">5</option>
            <option value="6.0">6</option>
            <option value="7.0">7</option>
            <option value="8.0">8</option>
            <option value="9.0">9</option>
            <option value="10.0">10</option>
            <option value="11.0">11</option>
            <option value="12.0">12</option>
            <option value="13.0">13</option>
            <option value="14.0">14</option>
            <option value="15.0">15</option>
            <option value="16.0">16</option>
            <option value="17.0">17</option>
            <option value="18.0">18</option>
            <option value="19.0">19</option>
            <option value="20.0">20</option>
          </select> <%=encprops.getProperty("meters")%>
          </td>
        </tr>


        <tr>
          <td valign="top"><strong><%=encprops.getProperty("behavior")%>:</strong>
            <em> <span class="para">
								<a href="<%=CommonConfiguration.getWikiLocation()%>behavior" target="_blank">
                  <img src="../images/information_icon_svg.gif" alt="Help" border="0"
                       align="absmiddle"/>
                </a>
							</span>
            </em><br/>
              <%
				ArrayList<String> behavs = myShepherd.getAllBehaviors();
				int totalBehavs=behavs.size();

				
				if(totalBehavs>1){
				%>

            <select multiple name="behaviorField" id="behaviorField">
              <option value="None"></option>
              <%
                for (int f = 0; f < totalBehavs; f++) {
                  String word = behavs.get(f);
                  if (word != null) {
              %>
              <option value="<%=word%>"><%=word%>
              </option>
              <%

                  }

                }
              %>
            </select>
              <%

				}
				else{
					%>
            <p><em><%=encprops.getProperty("noBehaviors")%>
            </em></p>
              <%
				}
				%>

      </p>
  </td>
</tr>
<%
  int totalKeywords = myShepherd.getNumKeywords();
%>
<tr>
  <td valign="top"><%=encprops.getProperty("hasKeywordPhotos")%><br/>
    <%

      if (totalKeywords > 0) {
    %>

    <select multiple size="<%=(totalKeywords+1) %>" name="keyword" id="keyword">
      <option value="None"></option>
      <%


        Iterator keys = myShepherd.getAllKeywords(kwQuery);
        for (int n = 0; n < totalKeywords; n++) {
          Keyword word = (Keyword) keys.next();
      %>
      <option value="<%=word.getIndexname()%>"><%=word.getReadableName()%>
      </option>
      <%
        }

      %>

    </select>
    <%
    } else {
    %>

    <p><em><%=encprops.getProperty("noKeywords")%>
    </em>

        <%
					
				}
				%>
  </td>
</tr>
<tr>
  <td><strong><%=encprops.getProperty("submitterName")%>:</strong>
    <input name="nameField" type="text" size="60"> <br> <em><%=encprops.getProperty("namesBlank")%>
    </em>
  </td>
</tr>
</table>
</p>
</div>
</td>
</tr>

<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('identity')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Identity filters</font></a></h4>
  </td>
</tr>
<tr>
  <td>
    <div id="identity" style="display:none; ">
      <p>Use the fields below to limit your search to marked individuals with the following
        properties.</p>
      <input name="resightOnly" type="checkbox" id="resightOnly"
             value="true"> <%=encprops.getProperty("include")%> <select
      name="numResights" id="numResights">
      <option value="1" selected>1</option>
      <option value="2">2</option>
      <option value="3">3</option>
      <option value="4">4</option>
      <option value="5">5</option>
      <option value="6">6</option>
      <option value="7">7</option>
      <option value="8">8</option>
      <option value="9">9</option>
      <option value="10">10</option>
      <option value="11">11</option>
      <option value="12">12</option>
      <option value="13">13</option>
      <option value="14">14</option>
      <option value="15">15</option>
    </select> <%=encprops.getProperty("times")%>

      <p><strong><%=encprops.getProperty("alternateID")%>:</strong> <em> <input
        name="alternateIDField" type="text" id="alternateIDField" size="10"
        maxlength="35"> <span class="para"><a
        href="<%=CommonConfiguration.getWikiLocation()%>alternateID"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" width="15" height="15" border="0"
                             align="absmiddle"/></a></span>
        <br></em></p>
    </div>
  </td>
</tr>
<tr>
  <td>

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('metadata')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Metadata filters</font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="metadata" style="display:none; ">
      <p>Use the fields below to limit your search by catalog metadata fields.</p>
      <table width="720px" align="left">
        <tr>
          <td width="154"><strong><%=encprops.getProperty("types2search")%>
          </strong>:
          </td>
          <td width="208"><label>
            <input type="checkbox" name="approved" value="acceptedEncounters"
                   checked><%=encprops.getProperty("approved")%>
            </input></label>
          </td>


          <td width="188"><label>
            <input name="unapproved" type="checkbox" value="allEncounters"
                   checked><%=encprops.getProperty("unapproved")%>
            </input></label>
          </td>

          <td width="145"><label>
            <input name="unidentifiable" type="checkbox" value="allEncounters"
                   checked><%=encprops.getProperty("unidentifiable")%>
            </input></label>
          </td>


        </tr>
      </table>
    </div>
  </td>
</tr>


<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>

<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('export')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000">Export options</font></a></h4>

    <div id="export" style="display:none; ">
      <p>Use the fields below to specify data export options.</p>

      <p><input name="export" type="checkbox" id="export" value="true">
        <strong><%=encprops.getProperty("generateExportFile")%>
        </strong><br>
        &nbsp;&nbsp;&nbsp;&nbsp;<input name="locales" type="checkbox"
                                       id="locales"
                                       value="true"> <%=encprops.getProperty("localeExport")%>
      </p>
      </p>

      <p><input name="addTimeStamp" type="checkbox" id="addTimeStamp" value="true">
        <strong><%=encprops.getProperty("addTimestamp2KML")%>
        </strong></p>

      <p><input name="generateEmails" type="checkbox"
                id="generateEmails" value="true">
        <strong><%=encprops.getProperty("generateEmailList")%>
        </strong></p>
      </p>
    </div>
    <p><em> <input name="submitSearch" type="submit"
                   id="submitSearch" value="<%=encprops.getProperty("goSearch")%>"></em>

  </td>
</tr>
</table>
</form>
</td>
</tr>
</table>
<br>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>


