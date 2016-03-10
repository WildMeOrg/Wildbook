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
<%@ page import="java.io.IOException" %>
<%@ page import="java.text.MessageFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="org.ecocean.servlet.BatchUpload" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.*" %>
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
  Properties cciProps = ShepherdProperties.getProperties("commonCoreInternational.properties", langCode, context);

	Shepherd myShepherd = new Shepherd(context);
	response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
	response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
	response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
	response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  List<String> errors = (List<String>)session.getAttribute(BatchUpload.SESSION_KEY_ERRORS);
  boolean hasErrors = errors != null && !errors.isEmpty();
  List<String> warnings = (List<String>)session.getAttribute(BatchUpload.SESSION_KEY_WARNINGS);
  boolean hasWarnings = warnings != null && !warnings.isEmpty();

  // If landed directly on page without forwarding, reset ready for use.
  String uri = (String)request.getAttribute("javax.servlet.forward.request_uri");
  if (uri == null || "".equals(uri))
    BatchUpload.flushSessionInfo(request);

  // Define template/data types.
  final String[] TYPES = {"Ind", "Enc", "Mea", "Med", "Sam"};
%>
<%!
  /*
   * This method populates the option descriptions for the BatchUpload JSP page.
   * Each indexed item refers to the same indexed item in the properties file
   * (e.g. gui.step2.enums.list0), and the {0} parameter is filled with the
   * resulting string from this method.
   */
  private final String createOptionsList(int i, Properties cciProps, String langCode, String context) throws IOException {
    Collection<String> list = new ArrayList<String>();
    List<String> temp = null;
    Set<String> set = null;
    switch (i) {
      case 0:
      case 1:
        list = Util.getIndexedValuesMap(cciProps, "sex").values();
        break;
      case 2:
        temp = CommonConfiguration.getSequentialPropertyValues("genusSpecies", context);
        set = new LinkedHashSet<String>();
        for (String s : temp)
          set.add(s.substring(0, s.indexOf(" ")));
        list.addAll(set);
        break;
      case 3:
        temp = CommonConfiguration.getSequentialPropertyValues("genusSpecies", context);
        set = new LinkedHashSet<String>();
        for (String s : temp)
          set.add(s.substring(s.indexOf(" ") + 1));
        list.addAll(set);
        break;
      case 4:
        list = Util.getIndexedValuesMap(cciProps, "locationID").values();
        break;
      case 5:
        list = Util.getIndexedValuesMap(cciProps, "livingStatus").values();
        break;
      case 6:
        list = Util.getIndexedValuesMap(cciProps, "lifeStage").values();
        break;
      case 7:
        list = Util.getIndexedValuesMap(cciProps, "patterningCode").values();
        break;
      case 8:
        Properties props = new Properties();
        props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));
        list.add(props.getProperty("unmatchedFirstEncounter"));
        list.add(props.getProperty("visualInspection"));
        list.add(props.getProperty("patternMatch"));
        break;
      case 9:
        list = Util.getIndexedValuesMap(cciProps, "measurement").values();
        break;
      case 10:
        list = Util.getIndexedValuesMap(cciProps, "measurementUnits").values();
        break;
      case 11:
        list = Util.getIndexedValuesMap(cciProps, "samplingProtocol").values();
        break;
      case 12:
        Shepherd shep = new Shepherd(context);
        for (Iterator iter = shep.getAllKeywords(); iter.hasNext();)
          list.add(((Keyword)iter.next()).getReadableName());
        shep.closeDBTransaction();
        shep = null;
        break;
      case 13:
        list = Util.getIndexedValuesMap(cciProps, "tissueType").values();
        break;
      case 14: // FIXME
        list = Util.getIndexedValuesMap(cciProps, "preservationMethod").values();
        break;
      default:
    }
    StringBuilder sb = new StringBuilder();
    if (list.size() < 6) {
      sb.append("<ul><li>");
      for (String s : list)
        sb.append("<span class=\"example\">&quot;").append(s).append("&quot;</span>, ");
      sb.setLength(sb.length() - 2);
      sb.append("</li></ul>");
    } else {
      sb.append("<ul>");
      for (String s : list)
        sb.append("<li class=\"example\">").append(s).append("</li>");
      sb.append("</ul>\n");
    }
    return sb.toString();
  }
%>

  <div class="container maincontent">


      <h1><%=bundle.getProperty("gui.title")%></h1>
			<p><%=bundle.getProperty("gui.overview")%></p>

      <% if (hasErrors) { %>
      <div id="errors">
        <hr />
  			<h2><%=bundle.getProperty("gui.errors.title")%></h2>
        <ul id="errorList">
        <% for (String msg : errors) { %>
          <li><%=ServletUtilities.preventCrossSiteScriptingAttacks(msg)%></li>
        <% } %>
        </ul>
        <hr />
      </div>
      <% } %>
      <% if (hasWarnings) { %>
      <hr />
      <div id="warnings">
  			<h2><%=bundle.getProperty("gui.warnings.title")%></h2>
        <ul id="warningList">
        <% for (String msg : warnings) { %>
          <li><%=ServletUtilities.preventCrossSiteScriptingAttacks(msg)%></li>
        <% } %>
        </ul>
        <hr />
      </div>
      <% } %>

			<h2><%=bundle.getProperty("gui.step1.title")%></h2>
			<p><%=bundle.getProperty("gui.step1.text")%></p>

      <ul id="templateList">
        <% for (String type : TYPES) { %>
          <% if (type.equals("Ind") || type.equals("Enc")) { %>
        <li class="required"><a href="../BatchUpload/template<%=type%>"><%=bundle.getProperty("gui.step1.template." + type.toLowerCase(Locale.US))%></a></li>
          <% } else { %>
        <li><a href="../BatchUpload/template<%=type%>"><%=bundle.getProperty("gui.step1.template." + type.toLowerCase(Locale.US))%></a></li>
          <% } %>
        <% } %>
      </ul>

			<h2><%=bundle.getProperty("gui.step2.title")%></h2>
			<p><%=bundle.getProperty("gui.step2.text")%></p>
      <ul id="rules">
      <% for (int i = 0; i <= 15; i++) { %>
        <li><%=bundle.getProperty("gui.step2.list" + i)%></li>
      <% } %>
      <li><%=MessageFormat.format(bundle.getProperty("gui.step2.maxMediaSize"), CommonConfiguration.getMaxMediaSizeInMegabytes(context))%></li>
      <% for (int i = 0; i <= 14; i++) { %>
      <li><%=MessageFormat.format(bundle.getProperty("gui.step2.enums.list" + i), createOptionsList(i, cciProps, langCode, context))%></li>
      <% } %>
      </ul>
			<p><%=bundle.getProperty("gui.step2.text2")%></p>

<%
  String batchPlugin = CommonConfiguration.getBatchUploadPlugin(context);
  if (batchPlugin != null) {
%>
      <p class="pluginText"><%=bundle.getProperty("gui.step2.pluginText")%></p>
<%
  }
%>

			<h2><%=bundle.getProperty("gui.step3.title")%></h2>
			<p><%=bundle.getProperty("gui.step3.text")%></p>
			<p class="notice"><%=bundle.getProperty("gui.step3.text2")%></p>
      <form name="batchUpload" method="post" enctype="multipart/form-data" accept-charset="utf-8" action="<%=request.getContextPath()%>/BatchUpload/uploadBatchData">
			<table id="batchTable">
        <% for (String type : TYPES) { %>
				<tr>
					<td class="required"><%=bundle.getProperty("gui.step3.form.text." + type.toLowerCase(Locale.US))%></td>
					<td><input name="csv<%=type%>" type="file" id="csv<%=type%>" size="20" maxlength="255"></td>
				</tr>
        <% } %>
				<tr>
					<td colspan="3">
            <input type="submit" id="upload" value="<%=bundle.getProperty("gui.step3.form.submit")%>">
            <input type="reset" id="reset" value="<%=bundle.getProperty("gui.step3.form.reset")%>">
          </td>
				</tr>
			</table>
			</form>

			<h2><%=bundle.getProperty("gui.step4.title")%></h2>
			<p><%=bundle.getProperty("gui.step4.text")%></p>

			<h2><%=bundle.getProperty("gui.step5.title")%></h2>
			<p><%=bundle.getProperty("gui.step5.text")%></p>

<%
  // Clean up page resources.
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>

  </div>
<jsp:include page="../footer.jsp" flush="true"/>
