<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
         pageEncoding="ISO-8859-1"
         import="org.ecocean.CommonConfiguration, org.ecocean.Shepherd, java.util.Properties" %>
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

<%


  String overview_language = "Select Language";
  String langCode = "en";
  if (request.getParameter("langCode") != null) {
    langCode = request.getParameter("langCode");
  } else if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  session.setAttribute("langCode", langCode);

  //set up the file input stream
  Properties props = new Properties();

  try {
    props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));
    overview_language = props.getProperty("overview_language");
  } catch (Exception langEx) {
    langEx.printStackTrace();
  }

  Shepherd myShepherd = new Shepherd();

%>
<a name="lang"/>

<div class="module">
  <h3><%=overview_language%>
  </h3>
  <a href="?langCode=en#lang"><img src="images/flag_en.gif" width="19"
                                   height="12" border="0" title="English" alt="English"/></a> <a
  href="?langCode=de" title="Auf Deutsch"><img
  src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/flag_de.gif"
  width="19" height="12" border="0" title="Deutsch" alt="Deutsch"/></a> <a
  href="?langCode=fr#lang" title="En fran&ccedil;ais"><img
  src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/flag_fr.gif"
  width="19" height="12" border="0" title="Fran&ccedil;ais"
  alt="Fran&cedil;ais"/></a> <a href="?langCode=es#lang"
                                title="En espa&ntilde;ol"><img
  src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/flag_es.gif"
  width="19" height="12" border="0" title="Espa&ntilde;ol"
  alt="Espa&ntilde;ol"/></a></div>