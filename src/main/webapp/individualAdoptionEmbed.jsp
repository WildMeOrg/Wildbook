<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.Adoption,org.ecocean.CommonConfiguration,org.ecocean.Shepherd,java.util.List" %>

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
String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd adoptShepherd = new Shepherd(context);
  adoptShepherd.setAction("individualAdoptionEmbed.jsp");
  String name = request.getParameter("name");
  adoptShepherd.beginDBTransaction();

  try {
%>

<style type="text/css">
  <!--
  .style1 {
    font-weight: bold
  }

  table.adopter {
    border-width: 0px 0px 0px 0px;
    border-spacing: 0px;
    border-style: solid solid solid solid;
    border-color: black black black black;
    border-collapse: separate;

  }

  table.adopter td {
    border-width: 1px 1px 1px 1px;
    padding: 3px 3px 3px 3px;
    border-style: none none none none;
    border-color: gray gray gray gray;
    background-color: #D7E0ED;
    -moz-border-radius: 0px 0px 0px 0px;
    font-size: 12px;
    color: #330099;
  }

  table.adopter td.name {
    font-size: 12px;
    text-align: center;
    background-color: #D7E0ED;

  }

  table.adopter td.image {
    padding: 0px 0px 0px 0px;

  }

  .style2 {
    font-size: x-small;
    color: #000000;
  }

  -->
</style>


<div style="width: 100%;">
  <%
    List<Adoption> adoptions = adoptShepherd.getAllAdoptionsForMarkedIndividual(name,context);
    int numAdoptions = adoptions.size();
    int ia = 0;
    for (ia = 0; ia < numAdoptions; ia++) {
      Adoption ad = adoptions.get(ia);
  %>
  <div style="float:left; margin: 5px;">
  <table style="background-color:#D7E0Ed " width="190px">
  
  <tr>
    <td class="image"><img border="0" src="images/meet-adopter-frame.gif"/></td>
  </tr>

  <%
    if ((ad.getAdopterImage() != null) && (!ad.getAdopterImage().trim().equals(""))) {
  %>
  <tr>
    <td class="image" style="padding-top: 0px;">
      <center><img width="188px"
                   src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=ad.getID()%>/adopter.jpg"/></center>
    </td>
  </tr>
  <%
    }
  %>


  <tr>
    <td class="name">
      <center><strong><font color="#282460" size="+1"><%=ad.getAdopterName()%>
      </font></strong></center>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <%
    if ((ad.getAdopterQuote() != null) && (!ad.getAdopterQuote().trim().equals(""))) {
  %>

  <tr>
    <td>Why are research and conservation for this species important?</td>
  </tr>
  <tr>
    <td><em>"<%=ad.getAdopterQuote()%>"</em></td>
  </tr>

  <%
    }

    if (request.getUserPrincipal()!=null) {
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
      href="//<%=CommonConfiguration.getURLLocation(request)%>/adoptions/adoption.jsp?number=<%=ad.getID()%>#create">[edit
      this adoption]</a></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <%
    }
  %>
  <tr>
    <td>&nbsp;</td>
  </tr>
  <%
      if (ia > 0) {
  %>


  <tr>
    <td class="image"><img border="0" src="images/adopter-frame-bottom.gif"/></td>
  </tr>

  <%
    }
  %>
</table>
</div>

  <%
    }
%>
</div>
<div style="float:left; margin: 5px;">
<a href="createadoption.jsp?number=<%=name%>"><button class="btn btn-md">Adopt Me<span class="button-icon" aria-hidden="true"></button></a>
</div>
<p>&nbsp;</p>


<%
  }
  catch (Exception e) {
  }
  adoptShepherd.rollbackDBTransaction();
  adoptShepherd.closeDBTransaction();
  adoptShepherd = null;

%>
