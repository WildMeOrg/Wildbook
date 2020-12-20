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
	~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
	~ GNU General Public License for more details.
	~
	~ You should have received a copy of the GNU General Public License
	~ along with this program; if not, write to the Free Software
	~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA	02110-1301, USA.
--%>
<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.text.MessageFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="org.ecocean.batch.BatchData" %>
<%@ page import="org.ecocean.CommonConfiguration" %>
<%@ page import="org.ecocean.servlet.BatchUpload" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>
<link href="<%=request.getContextPath()%>/css/batchUpload.css" rel="stylesheet" type="text/css"/>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties bundle = new Properties();
  bundle.load(getClass().getResourceAsStream("/bundles/batchUpload_" + langCode + ".properties"));

	response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
	response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
	response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
	response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  List<String> warnings = (List<String>)session.getAttribute(BatchUpload.SESSION_KEY_WARNINGS);
  boolean hasWarnings = warnings != null && !warnings.isEmpty();

  // Check that batch data has been assigned to session.
  BatchData data = (BatchData)session.getAttribute(BatchUpload.SESSION_KEY_DATA);
  if (data == null) {
    getServletContext().getRequestDispatcher(request.getContextPath() + "/BatchUpload/start").forward(request, response);
    return;
  }
%>

<div class="container maincontent">


<h1><%=bundle.getProperty("gui.summary.title")%></h1>
			<p><%=bundle.getProperty("gui.summary.overview")%></p>

      <table id="dataSummary">
        <thead></thead>
        <tbody>
          <tr>
            <th><%=bundle.getProperty("gui.summary.label.ind")%></th>
            <td><%=MessageFormat.format(bundle.getProperty("gui.summary.value.ind"), data.listInd.size())%></td>
          </tr>
          <tr>
            <th><%=bundle.getProperty("gui.summary.label.enc")%></th>
            <td><%=MessageFormat.format(bundle.getProperty("gui.summary.value.enc"), data.listEnc.size(), data.getUnassignedEncounterCount(), data.getAssignedToExistingIndividualCount())%></td>
          </tr>
          <tr>
            <th><%=bundle.getProperty("gui.summary.label.mea")%></th>
            <td><%=MessageFormat.format(bundle.getProperty("gui.summary.value.mea"), data.listMea == null ? 0 : data.listMea.size())%></td>
          </tr>
          <tr>
            <th><%=bundle.getProperty("gui.summary.label.med")%></th>
            <td><%=MessageFormat.format(bundle.getProperty("gui.summary.value.med"), data.listMed == null ? 0 : data.listMed.size())%></td>
          </tr>
          <tr>
            <th><%=bundle.getProperty("gui.summary.label.sam")%></th>
            <td><%=MessageFormat.format(bundle.getProperty("gui.summary.value.sam"), data.listSam == null ? 0 : data.listSam.size())%></td>
          </tr>
        </tbody>
      </table>
			<p><%=bundle.getProperty("gui.summary.text")%></p>

			<form name="batchSummary" method="post" accept-charset="utf-8" action="<%=request.getContextPath()%>/BatchUpload/confirmBatchDataUpload">
			<table id="batchTable">
				<tr>
					<td>
            <input type="submit" id="confirm" value="<%=bundle.getProperty("gui.summary.form.confirm")%>" />
            <input type="button" id="cancel" value="<%=bundle.getProperty("gui.summary.form.cancel")%>" onclick="window.location.href='<%=request.getContextPath()%>/BatchUpload/start';return true;" />
          </td>
				</tr>
			</table>
			</form>

<%  if (hasWarnings) { %>
      <div id="warnings">
  			<h2><%=bundle.getProperty("gui.warnings.title")%></h2>
        <ul id="warningList">
<%    for (String msg : warnings) { %>
          <li><%=ServletUtilities.preventCrossSiteScriptingAttacks(msg)%></li>
<%    } %>
        </ul>
      </div>
<%  } %>

    </div>
      <jsp:include page="../footer.jsp" flush="true"/>
