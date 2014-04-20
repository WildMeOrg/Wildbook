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
  //String langCode = "en";
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

</head>

<script type="text/javascript" src="spot_real_media/realembed.js"></script>

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
          <h1 class="intro">Spot! Quickstart Guide</h1>
        </div>
        <p>The following video demonstrates basic usage of <strong>Spot!</strong>.
          The video requires the Free version of the <a
            href="http://www.real.com/">RealPlayer</a>. More information about
          Spot! can be found <a
            href="http://www.whaleshark.org/onlinehelp/spot/index.jsp">here</a>.</p>

        <p>
        <table border="0" cellpadding="0"
               style="text-align: center; margin: auto" width="640" align="center">
          <tr>
            <td>
              <div id="media">
                <div id="cs_noJSorReal">
                  <p>The Camtasia Studio video content presented here requires
                    JavaScript to be enabled and the latest version of the Real Player. If
                    you are you using a browser with JavaScript disabled please enable it
                    now. Otherwise, please update your version of the free Real Player by
                    <a href="http://www.real.com/player">downloading here</a>.</p>
                </div>
              </div>
              <div id="player"></div>
              <script type="text/javascript">
                if (isRealInstalled()) {
                  var real = new RealEmbed("spot_real_media/spot_real.rm", "realplayer", "640", "480");
                  real.addParam("controls", "imagewindow");
                  real.addParam("console", "One");
                  real.addParam("autoStart", "true");
                  real.write("media");
                  var con = new RealEmbed("spot_real_media/spot_real.rm", "realControls", "640", "36");
                  con.addParam("controls", "ControlPanel");
                  con.addParam("console", "One");
                  con.addParam("autoStart", "true");
                  con.write("player");
                }
              </script>
            </td>
          </tr>
        </table>
        </p>
      </div>
    </div>
    <jsp:include page="../../footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>
