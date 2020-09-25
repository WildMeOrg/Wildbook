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
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@page contentType="text/html; charset=iso-8859-1" language="java"
        import="org.ecocean.CommonConfiguration"
        import="org.ecocean.Shepherd"
        import="org.ecocean.Keyword"
        import="org.ecocean.servlet.BatchUpload"
        import="org.ecocean.servlet.ServletUtilities"
        import="org.ecocean.batch.BatchProcessor"
        import="java.io.File"
        import="java.io.IOException"
        import="java.text.MessageFormat"
        import="java.util.*"
        import="org.slf4j.Logger"
        import="org.slf4j.LoggerFactory"
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd = new Shepherd(context);
	response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
	response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
	response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
	response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  // --------------------------------------------------------------------------
  // Page internationalization.
  // Code is use below is a compromise to fit in with the current i18n mechanism.
  // Ideally it should use the proper ResourceBundle lookup mechanism, and when
  // not explicitly chosen by the user, find supported languages/variants from
  // the browser configuration.
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String)session.getAttribute("langCode");
  } else {
    Locale loc = request.getLocale();
    langCode = loc.getLanguage();
  }
//  Locale locale = new Locale(langCode);
//  ResourceBundle bundle = ResourceBundle.getBundle("/bundles/batchUpload", locale);
  Properties bundle = new Properties();
  bundle.load(getClass().getResourceAsStream("/bundles/batchUpload_" + langCode + ".properties"));
  // --------------------------------------------------------------------------

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
  private final String createOptionsList(int i, String langCode, String context) throws IOException {
    List<String> list = new ArrayList<String>();
    List<String> temp = new ArrayList<String>();
    TreeSet<String> set = null;
    switch (i) {
      case 0:
      case 1:
        list = CommonConfiguration.getIndexedPropertyValues("sex", context);
        break;
      case 2:
        temp = CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
        set = new TreeSet<String>();
        for (String s : temp)
          set.add(s.substring(0, s.indexOf(" ")));
        list.addAll(set);
        break;
      case 3:
        temp = CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
        set = new TreeSet<String>();
        for (String s : temp)
          set.add(s.substring(s.indexOf(" ") + 1));
        list.addAll(set);
        break;
      case 4:
        list = CommonConfiguration.getIndexedPropertyValues("locationID", context);
        break;
      case 5:
        list = CommonConfiguration.getIndexedPropertyValues("livingStatus", context);
        break;
      case 6:
        list = CommonConfiguration.getIndexedPropertyValues("lifeStage", context);
        break;
      case 7:
        list = CommonConfiguration.getIndexedPropertyValues("patterningCode", context);
        break;
      case 8:
        Properties props = new Properties();
        props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));
        list.add(props.getProperty("unmatchedFirstEncounter"));
        list.add(props.getProperty("visualInspection"));
        list.add(props.getProperty("patternMatch"));
        break;
      case 9:
        list = CommonConfiguration.getIndexedPropertyValues("measurement", context);
        break;
      case 10:
        list = CommonConfiguration.getIndexedPropertyValues("measurementUnits", context);
        break;
      case 11:
        list = CommonConfiguration.getIndexedPropertyValues("samplingProtocol", context);
        break;
      case 12:
        Shepherd shep = new Shepherd(context);
        for (Iterator<Keyword> iter = shep.getAllKeywords(); iter.hasNext();)
          list.add(iter.next().getReadableName());
        shep.closeDBTransaction();
        shep = null;
        break;
      case 13:
        list = CommonConfiguration.getIndexedPropertyValues("tissueType", context);
        break;
      case 14: // FIXME
        list = CommonConfiguration.getIndexedPropertyValues("preservationMethod", context);
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
<html>
<head>
	<title><%=CommonConfiguration.getHTMLTitle(context) %></title>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
	<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
	<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
	<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
	<link href="<%=CommonConfiguration.getCSSURLLocation(request, context) %>" rel="stylesheet" type="text/css"/>
	<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
	<link href="<%=request.getContextPath()%>/css/batchUpload.css" rel="stylesheet" type="text/css"/>
</head>

<body>
<div id="wrapper">
	<div id="page">
		<jsp:include page="../header.jsp" flush="true">
			<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
		</jsp:include>
		<div id="main">

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
      <li><%=MessageFormat.format(bundle.getProperty("gui.step2.enums.list" + i), createOptionsList(i, langCode, context))%></li>
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
			<jsp:include page="../footer.jsp" flush="true"/>
		</div>
	</div>
	<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


