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

  boolean hasNickName = true;
	String nick = "";
	try {
		if (sessionShark != null) {
			MarkedIndividual mi = myShepherd.getMarkedIndividual(sessionShark);
			nick = mi.getNickName();
			if ((nick.equals("Unassigned"))||(nick.equals(""))) {
				hasNickName = false;
			}
		}
	} catch (Exception e) {
		System.out.println("Error looking up nickname!!");
		e.printStackTrace();
	}

  try {
%>

<link rel="stylesheet" href="css/createadoption.css">

<article class="adopter-feature-gallery">
  <div class="adopter">
    <div class="adopter-header" >
      <p>
        Whale Shark Adopter
      </p>
    </div>
    <%
      List<Adoption> adoptions = adoptShepherd.getAllAdoptionsForMarkedIndividual(name,context);
      int numAdoptions = adoptions.size();
      int ia = 0;
      for (ia = 0; ia < numAdoptions; ia++) {
        Adoption ad = adoptions.get(ia);
    %>
        <%
        if ((ad.getAdopterImage() != null) && (!ad.getAdopterImage().trim().equals(""))) {
        %>
          <img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=ad.getID()%>/thumb.jpg" alt="adopters picture" />
        <%
          }
        %>
        <div class="adopter-details">
          <p>
            "<%=ad.getAdopterName()%>"
          </p>
          <%
            if ((ind.getNickName() != null) && (!ind.getNickName().trim().equals(""))) {
          %>
            <p>
              Adopted "<%=ind.getNickName()%>"
            </p>
          <%
            }
          %>
          <%
            if ((ad.getAdopterQuote() != null) && (!ad.getAdopterQuote().trim().equals(""))) {
          %>
            <p>
              "<%=ad.getAdopterQuote()%>"
            </p>
          <%
            }
          %>
        </div>
  </div>
</article>
<%
    }

  if (ia > 0) {
%>
<%
  }
%>
<%
  }
  catch (Exception e) {
  }
  adoptShepherd.rollbackDBTransaction();
  adoptShepherd.closeDBTransaction();
  adoptShepherd = null;

%>
