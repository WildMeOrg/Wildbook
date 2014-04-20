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
         import="org.ecocean.servlet.ServletUtilities,java.io.File, java.io.FileInputStream,java.util.Properties,org.ecocean.*" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  //setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode=ServletUtilities.getLanguageCode(request);
  

  

%>

<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context)%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context)%>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context)%>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context)%>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context)%>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context)%>"/>

  <style type="text/css">
    <!--
    .style1 {
      font-size: larger
    }

    -->
  </style>
</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../../header.jsp" flush="true">
      <jsp:param name="isResearcher"
                 value="<%=request.isUserInRole("researcher")%>"/>
      <jsp:param name="isManager"
                 value="<%=request.isUserInRole("manager")%>"/>
      <jsp:param name="isReviewer"
                 value="<%=request.isUserInRole("reviewer")%>"/>
      <jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>"/>
    </jsp:include>
    <div id="main"><!-- end leftcol -->
      <div id="maincol-calendar">

        <div id="maintext">
          <h1 class="intro">Spot! Online Help</h1>
          <table cellpadding="3">
            <tr>
              <td><img src="cool_screenshot.gif" width="150" hspace="10"
                       vspace="10" border="1" align="left"/></td>
              <td>
                <p>The spot pattern recognition algorithms used in whale shark
                  mark-recapture (Modified Groth and I3S) assume a flat, two-dimensional
                  surface when analyzing the relationships between spots. While each
                  algorithm has some tolerance for skew in an image, both quickly
                  degrade in their ability to match identical patterns as the angles
                  between those patterns increases.</p>

                <p><strong>Spot!</strong> allows you to map a skewed 2D image to a
                  3D whale shark model and obtain a properly-oriented left- or
                  right-side pattern for use with the Modified Groth and I3S
                  algorithms.This perspective correction has been used in the ECOCEAN
                  Library to match images taken from very extreme angles to previously
                  tagged whale sharks. For example, Spot! was used to make this match
                  for shark <a
                    href="http://www.whaleshark.org/individuals.jsp?number=M-025">M-025</a>
                  in the ECOCEAN Library.</p>
              </td>
            </tr>
          </table>

          <p align="left"><img src="example1.jpg" width="658" height="617"/>
          </p>

          <p align="center">&nbsp;</p>

          <p class="style1"><strong>Spot! Requirements </strong></p>

          <p>Spot! requires Java 5 or Java 6 on your computer. The Java
            Runtime Environment (JRE) can be downloaded from Sun Microsystems at:</p>

          <p><a href="http://www.java.com">http://www.java.com</a></p>

          <p class="style1"><strong>Loading Spot!</strong></p>

          <p>Spot! can be loaded from within the <a
            href="http://www.whaleshark.org/software.jsp">Client Software page</a>
            of the ECOCEAN Library or directly from this link:</p>

          <p><a href="http://www.whaleshark.org/spot/spot.jnlp">http://www.whaleshark.org/spot/spot.jnlp</a>
          </p>

          <p class="style1"><strong>Basic Spot! Instructions </strong></p>

          <p>There are just a few basic steps to using Spot!.</p>
          <ol>
            <li>Load the skewed image into Spot! using the <strong>Open</strong>
              button. <img src="step1.gif" width="39" height="45" hspace="3"
                           vspace="3" align="middle"/></li>
            <li>Scale the image to the 3D model by holding down the middle
              mouse button and moving the mouse forward and backward.
            </li>
            <li>Move the loaded image left and right by holding down the right
              mouse button.
            </li>
            <li>Manipulate the 3D model by holding down the left mouse button
              on its edges and moving the mouse up, down, left, and right.
            </li>
            <li>Repeat steps 2-4 to align the guidelines on the 3D to these
              features on the 2D image: 5th gill, lowest lateral line, and vertebral
              column.
            </li>
            <li>Once aligned, click the <strong>Map</strong> button to map the
              2D image to the 3D model. <img src="step2.gif" width="33" height="45"
                                             align="middle"/></li>
            <li>Click the Export button to save the properly aligned image for
              spot mapping with ECOCEAN Interconnect. <img src="step3.gif" width="45"
                                                           height="45" align="middle"/></li>
          </ol>
          <p>The <a href="quickstart.jsp">Quickstart video</a> demonstrates
            these steps.</p>

          <p class="style1"><strong>Spot! FAQ</strong></p>

          <p><em><strong>1. How accurate is Spot?</strong></em></p>

          <p>We're still testing it. So far, we've made a lot of very strong
            matches in our test cases using real-world data. Spot! has exceeded our
            expectations in its ability to match patterns from skewed images to
            properly-oriented patterns. As with any software tool, the result is as
            good as the user, and Spot! requires patient, careful alignment between
            3D model and 2D image. At this time, we're using Spot! to go back
            through encounter reports and identify sharks from images that were
            previously unsuitable for existing pattern recognition algorithms.
            Statistical tests of Spot!'s accuracy will be performed in the near
            future. But again...it's always going to be only as good as its user.</p>

          <p><em><strong>2. Can I use Spot! to identify new
            whale sharks from skewed images?</strong></em></p>

          <p>That's a longer discussion about misidentification in
            mark-recapture population modeling. At this point, Spot! is NOT being
            used to identify new whale sharks in the ECOCEAN Library. However, we
            may choose to do so in the future. We currently use it only to identify
            previously marked whale sharks. In general, we HIGHLY recommend the
            tandem use of both the <a
              href="http://www.blackwell-synergy.com/doi/pdf/10.1111/j.1365-2664.2005.01117.x?cookieSet=1">Modified
              Groth</a> and <a
              href="http://www.blackwell-synergy.com/doi/abs/10.1111/j.1365-2664.2006.01273.x?journalCode=jpe">I3S</a>
            pattern recognition algorithms in addition to rigorous peer review to
            reduce misidentification.</p>

          <p><em><strong>3. Can Spot! be used for other species?</strong></em></p>

          <p>Yes! Spot! can load other 3D models (.obj file format) and map
            images to them. This allows researchers for other spotted species to use
            image catalogs and pattern recognition algorithms where the observer
            cannot orient the camera correctly to the animal, such as when camera
            traps are used. If you are interested in using Spot! and the dual
            pattern recognition algorithms of the ECOCEAN Library, please contact <em>webmaster
              at whaleshark dot org</em>.</p>

          <p><em><strong>4. Is Spot! open source?</strong></em></p>

          <p>It will be as soon as our initial tests are complete. For now, it
            is freeware under license. Use it as you like.</p>

          <p><em><strong>5. Is a single whale shark model
            representative of the long-term morphology of an individual? </strong></em></p>

          <p>Probably not for very small (rare) and very large whale sharks,
            such as those found in the Galapagos Islands. The truncated 3D whale
            shark model used in Spot! is based on the proportions of average sized
            whale sharks, generally about 7 meters in total length (TL). We assume
            changes are proportional in all three dimensions for smaller and larger
            sharks (generally 5 m to 10 m in TL) within the range of most ecotourism
            activity. As our understanding of whale shark biology increases, we may
            use multiple models for different stages of growth.</p>

          <p><em><strong>6. Why is this model truncated at the
            front and back? Where are the fins?</strong></em></p>

          <p>The model focuses only on the fiducial region used for spot
            pattern recognition of whale sharks: left- and right-side patterning
            behind the gills. This area does not distort as much as many other
            regions of the shark during normal motion. By using a truncated model,
            we focus the eye on only the region of interest and consider only he
            region of interest.</p>
        </div>
      </div>
    </div>
    <jsp:include page="../../footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>
