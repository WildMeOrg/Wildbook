<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.Adoption,org.ecocean.CommonConfiguration,org.ecocean.Shepherd,java.util.ArrayList" %>

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
  Shepherd adoptShepherd = new Shepherd();
  String num = request.getParameter("num");

  try {


%>


<h3 style="width: 250px">Adopters</h3>

<%
  ArrayList adoptions = adoptShepherd.getAllAdoptionsForEncounter(num);
  int numAdoptions = adoptions.size();

  for (int ia = 0; ia < numAdoptions; ia++) {
    Adoption ad = (Adoption) adoptions.get(ia);
%>
<table class="adopter" width="250px">
  <tr>
    <td class="image"><img
      src="../adoptions/<%=ad.getID()%>/thumb.jpg" width="250px"></td>
  </tr>

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
    if ((ad.getAdopterQuote() != null) && (!ad.getAdopterQuote().equals(""))) {
  %>

  <tr>
    <td>Why are research and conservation important for this
      species?
    </td>
  </tr>
  <tr>
    <td width="250px"><em>"<%=ad.getAdopterQuote()%>"</em></td>
  </tr>

  <%
    }

    if (request.isUserInRole("admin")) {
  %>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <tr>
    <td><em>Adoption type:</em><br><%=ad.getAdoptionType()%>
    </td>
  </tr>
  <tr>
    <td><em>Adoption start:</em><br><%=ad.getAdoptionStartDate()%>
    </td>
  </tr>
  <tr>
    <td><em>Adoption end:</em><br><%=ad.getAdoptionEndDate()%>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <tr>
    <td align="left"><a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?number=<%=ad.getID()%>#create">[edit
      this adoption]</a></td>
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

  //add adoption
  if (request.isUserInRole("admin")) {
%>
<p><a
  href="http://<%=CommonConfiguration.getURLLocation(request) %>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?encounter=<%=num%>#create">[+]
  Add adoption</a></p>
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