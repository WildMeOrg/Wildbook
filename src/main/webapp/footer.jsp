<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration, java.util.Properties" %>

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

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";


%>
<div id="footer">
 

  
  <p class="credit">This software is distributed under the <a
    href="http://www.gnu.org/licenses/gpl-3.0.html" target="_blank">GPL v3 license</a> and is
    intended to support mark-recapture field studies. Open source and commercially licensed products
    used in this framework are listed <a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/thirdparty.jsp">here</a>. This is version <%=CommonConfiguration.getVersion()%>.</p>

  <p><a href="http://www.ecoceanusa.org/shepherd" target="_blank">
    <img border="0" src="http://<%=CommonConfiguration.getURLLocation(request)%>/images/lib_bottom.gif"
                                                                       alt="Powered by ECOCEAN USA"
    /></a>
  </p>


</div>
<!-- end footer -->
