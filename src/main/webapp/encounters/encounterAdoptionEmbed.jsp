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
<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.Adoption" %>
<%@ page import="org.ecocean.CommonConfiguration" %>
<%@ page import="org.ecocean.Shepherd" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="java.util.*" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

  Shepherd adoptShepherd = new Shepherd(context);
  String num = request.getParameter("encounterNumber");

  try {
%>
  <style type="text/css">
<!--

.style2 {
  color: #000000;
  font-size: small;
}

.style3 {
  font-weight: bold
}

.style4 {
  color: #000000
}

table.adopter {
  border-width: 1px 1px 1px 1px;
  border-spacing: 0px;
  border-style: solid solid solid solid;
  border-color: black black black black;
  border-collapse: separate;
  background-color: white;
}

table.adopter td {
  border-width: 1px 1px 1px 1px;
  padding: 3px 3px 3px 3px;
  border-style: none none none none;
  border-color: gray gray gray gray;
  background-color: white;
  -moz-border-radius: 0px 0px 0px 0px;
  font-size: 12px;
  color: #330099;
}

table.adopter td.name {
  font-size: 12px;
  text-align: center;
}

table.adopter td.image {
  padding: 0px 0px 0px 0px;
}

div.scroll {
  height: 200px;
  overflow: auto;
  border: 1px solid #666;
  background-color: #ccc;
  padding: 8px;
}

-->
</style>
<hr width="100%"/>
  <h2><%=encprops.getProperty("adoption.section.title")%></h2>


<%
  ArrayList adoptions = adoptShepherd.getAllAdoptionsForEncounter(num);
  int numAdoptions = adoptions.size();
if(numAdoptions>0){
  for (int ia = 0; ia < numAdoptions; ia++) {
    Adoption ad = (Adoption) adoptions.get(ia);
%>
<table class="adopter" width="250px">
  <%
    if ((ad.getAdopterImage() != null) && (!ad.getAdopterImage().trim().equals(""))) {
  %>
  <tr>
    <td class="image"><img
      src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=ad.getID()%>/thumb.jpg" width="250px"></td>
  </tr>
  <%
    }
  %>

  <tr>
    <td class="name">
      <table>
        <tr>
          <td><strong><%=ad.getAdopterName()%>
          </strong></td>
        </tr>
      </table>
    </td>
  </tr>
  <%
    if ((ad.getAdopterQuote() != null) && (!ad.getAdopterQuote().trim().equals(""))) {
  %>

  <tr>
    <td><%=encprops.getProperty("adoption.quote")%></td>
  </tr>
  <tr>
    <td width="250px"><em>"<%=ad.getAdopterQuote()%>"</em></td>
  </tr>

  <%
    }

    if (request.getUserPrincipal()!=null) {
  %>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <tr>
    <td><em><%=encprops.getProperty("adoption.type")%>:</em><br><%=ad.getAdoptionType()%>
    </td>
  </tr>
  <tr>
    <td><em><%=encprops.getProperty("adoption.startDate")%>:</em><br><%=ad.getAdoptionStartDate()%>
    </td>
  </tr>
  <tr>
    <td><em><%=encprops.getProperty("adoption.endDate")%>:</em><br><%=ad.getAdoptionEndDate()%>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <tr>
    <td align="left"><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/adoption.jsp?number=<%=ad.getID()%>#create">[<%=encprops.getProperty("adoption.edit")%>]</a></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <%
    }
  %>
</table>
<p>&nbsp;</p>
<%
  }
  }
  else {
	%>
	<p><%=encprops.getProperty("adoption.none")%></p>
<%	  
  }

  //add adoption
  if (request.getUserPrincipal()!=null) {
%>
<p><a href="../adoptions/adoption.jsp?encounter=<%=num%>#create">[+] <%=encprops.getProperty("adoption.add")%></a></p>
<%
  }
%>


<%
  } catch (Exception e) {
  }
  adoptShepherd.rollbackDBTransaction();
  adoptShepherd.closeDBTransaction();
  adoptShepherd = null;

%>