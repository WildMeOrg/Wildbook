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
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("submit.properties", langCode,context);



%>

<html>
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
</head>

<link rel="shortcut icon" href="images/favicon.ico"/>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide-solo">

        <div id="maintext">
          <h1 class="intro">Third Party Software</h1>
        </div>

        <p>The following third party software is used with permission and/or under an appropriate
          license within the Shepherd Project Framework.</p>

        <p><strong>Third Party Commercial Licenses (used with permission and/or under non-commercial
          guidelines)</strong></p>
        <ul>
          <li>Dynamic Image as included in this software is used with the permission of its
            developer Guang Yang and licensed only for use with the Shepherd Project Framework for
            non-commercial, mark-recapture studies.
          </li>
          <li>CSSPlay for dropdown navigation menus is used under non-commercial guidelines. <a
            href="http://www.cssplay.co.uk/menus/simple_vertical.html">http://www.cssplay.co.uk/menus/simple_vertical.html</a>
          </li>
          <li>Highslide JS 4.1.9 is used under non-commercial guidelines. <a
            href="http://highslide.com/">http://highslide.com/</a></li>
        </ul>
        <p><strong>Open Source Components </strong></p>
        <ul>
          <li>DataNucleus Access Platform 3.0.4 is used for object-relational mapping and persistence
            under the Apache 2 License.<a href="http://www.datanucleus.org%20">http://www.datanucleus.org</a>
          </li>
          <li>Joda Time for date manipulation and standardization is used under the Apache 2
            License.<a
              href="http://joda-time.sourceforge.net/">http://joda-time.sourceforge.net/</a></li>
          <li>Crystal Clear icons are used under the GNU Lesser General Public License. <a
            href="http://commons.wikimedia.org/wiki/Crystal_Clear">http://commons.wikimedia.org/wiki/Crystal_Clear</a>
          </li>
          <li>A Derby database is embedded for default usage and is available under the Apache 2
            License. <a href="http://db.apache.org/derby/">http://db.apache.org/derby/</a></li>
          <li>Project Rome is used for writing out Atom feeds. <a href="https://rome.dev.java.net/">https://rome.dev.java.net/</a>
          </li>
          <li>A Java version of the <a href="http://www.reijns.com/i3s/">I3S 1.0 algorithm</a> is
            used under the GPL v2 license as one of two spot pattern recognition algorithms.
          </li>
          <li>Metadata Extractor 2.3.1 for EXIF image data extraction. <a
            href="http://www.drewnoakes.com/code/exif/">http://www.drewnoakes.com/code/exif/</a>
          </li>
          <li>Sanselan 0.97 for image size detection. <a
            href="http://incubator.apache.org/sanselan/site/index.html">http://incubator.apache.org/sanselan/site/index.html</a>
          </li>

          <li>KeyDragZoom 2.0.5 for Google Maps area selection. <a
            href="http://code.google.com/p/gmaps-utility-library-dev/">GMaps Utility Library</a>
          </li>


        </ul>

      </div>
      <!-- end maintext -->

    </div>
    <!-- end maincol -->

    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page -->
</div>
<!--end wrapper -->
</body>
</html>
