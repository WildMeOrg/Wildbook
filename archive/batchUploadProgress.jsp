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
        import="org.ecocean.batch.BatchProcessor"
        import="org.ecocean.servlet.BatchUpload"
        import="org.ecocean.servlet.ServletUtilities"
        import="java.io.File"
        import="java.io.PrintWriter"
        import="java.text.MessageFormat"
        import="java.util.*"
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jstl/fmt" prefix="fmt" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  // Page internationalization.
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

  BatchProcessor proc = (BatchProcessor)session.getAttribute(BatchUpload.SESSION_KEY_TASK);
  if (proc == null) {
    BatchUpload.log.trace("No BatchProcessor found");
    BatchUpload.flushSessionInfo(request);
    getServletContext().getRequestDispatcher(request.getContextPath() + "/BatchUpload/start").forward(request, response);
    return;
  }

  List<String> errors = proc.getErrors();
  List<String> warnings = proc.getWarnings();
  boolean hasErrors = (errors != null) && !errors.isEmpty();
  boolean hasWarnings = warnings != null && !warnings.isEmpty();

  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
	response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
	response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
	response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
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

  <%-- NOTE: This page needs both JQuery & JQueryUI. --%>
  <%-- JQuery in included by header.jsp, and JQueryUI script must be added after that. --%>
  <link rel="stylesheet" href="//ajax.googleapis.com/ajax/libs/jqueryui/1.11.1/themes/start/jquery-ui.css"/>
  <!--<script type="text/javascript" src="//ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>-->

</head>

<body>
<div id="wrapper">
	<div id="page">
		<jsp:include page="../header.jsp" flush="true">
			<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
		</jsp:include>
    <%-- NOTE: JQueryUI script placed here due to JQuery inclusion in header.jsp --%>
    <script type="text/javascript" src="//ajax.googleapis.com/ajax/libs/jqueryui/1.11.1/jquery-ui.min.js"></script>
		<div id="main">

  <%  if (!proc.isTerminated() && !hasErrors) { %>
  <script language="javascript" type="text/javascript">
    var INTERVAL = 1000 * <%=CommonConfiguration.getBatchUploadProgressRefresh(context)%>;
    var PHASE_NONE = "<%=bundle.getProperty("gui.progress.status.phase.NONE")%>";
    var PHASE_MEDIA_DOWNLOAD = "<%=bundle.getProperty("gui.progress.status.phase.MEDIA_DOWNLOAD")%>";
    var PHASE_PERSISTENCE = "<%=bundle.getProperty("gui.progress.status.phase.PERSISTENCE")%>";
    var PHASE_THUMBNAILS = "<%=bundle.getProperty("gui.progress.status.phase.THUMBNAILS")%>";
    var PHASE_PLUGIN = "<%=(proc == null) ? "" : proc.getPluginPhaseMessage()%>";
    var PHASE_DONE = "<%=bundle.getProperty("gui.progress.status.phase.DONE")%>";

    function refreshProgress() {
      $.ajax({
        url:'<%=request.getContextPath()%>/BatchUpload/getBatchProgress',
        cache:false,
        dataType:'json',
        success:function(data) {
          if (data.error == undefined) {
            if (data.status == 'FINISHED' || data.status == 'ERROR') {
              window.location.replace('<%=request.getContextPath() + BatchUpload.JSP_PROGRESS%>');
              return;
            }
            if (!$('#ajaxProblem').hasClass('hidden'))
              $('#ajaxProblem').addClass('hidden');
            // Update progress display & phase text.
            $('#progressMeter').progressbar({ value: data.progress });
            $('#percent').text(data.progress + '%');
            $('#phase').text(eval('PHASE_' + data.phase));
            // Ensure progress displays are visible.
            $('#progress, #progressMeter, #phase').css('visibility', 'visible');
            setTimeout(refreshProgress, INTERVAL);
          } else {
            window.location.replace('<%=request.getContextPath() + BatchUpload.JSP_PROGRESS%>');
            return;
          }
        },
        error:function(jqXHR, status, err) {
          console.log("AJAX response: " + jqXHR.responseText);
          console.log("AJAX error   : " + status + " / " + err);
          $("#ajaxProblem").removeClass('hidden');
          setTimeout(refreshProgress, INTERVAL);
        }
      });
    }

    $(document).ready(function() {
      $('#progressMeter').progressbar({ value: false });
      window.setTimeout(refreshProgress, INTERVAL);
    });
  </script>
  <noscript>
    <meta http-equiv="refresh" content="<%=CommonConfiguration.getBatchUploadProgressRefresh(context) * 2%>"/>
  </noscript>
<%  } else { %>
  <script language="javascript" type="text/javascript">
    $(document).ready(function() {
      $('#ajaxProblem, #progress, #progressMeter, #phase').css('visibility', 'hidden');
    });
  </script>
<%  } %>

      <h1><%=bundle.getProperty("gui.progress.title")%></h1>

<%  if (hasErrors) { %>
      <p><%=bundle.getProperty("gui.progress.text.error")%></p>
<%    switch (proc.getPhase()) {
        case MEDIA_DOWNLOAD:
%>
      <p><%=bundle.getProperty("gui.progress.text.errorIntegrityMediaDownload")%></p>
<%          break;
        case PERSISTENCE:
%>
      <p><%=bundle.getProperty("gui.progress.text.errorIntegrityPersistence")%></p>
<%          break;
        case PLUGIN:
%>
      <p><%=bundle.getProperty("gui.progress.text.errorIntegrityPlugin")%></p>
<%          break;
        case THUMBNAILS:
%>
      <p><%=bundle.getProperty("gui.progress.text.errorIntegrityThumbnails")%></p>
<%          break;
        default:
%>
      <p><%=bundle.getProperty("gui.progress.text.errorIntegrityOk")%></p>
<%          break;
      }
%>
<%  } else { %>
<%
      switch(proc.getStatus()) {
        case FINISHED: %>
      <p><%=bundle.getProperty("gui.progress.text.finished")%></p>
<%        break;
        case ERROR: %>
      <p><%=bundle.getProperty("gui.progress.text.error")%></p>
<%        break;
        default: %>
      <p><%=bundle.getProperty("gui.progress.text.running")%></p>
      <p id="progress"><%=MessageFormat.format(bundle.getProperty("gui.progress.text.tracker"), proc.getProgress())%></p>
      <!-- Progress meter. -->
      <div id="progressMeter"></div>
<%    } %>
      <p id="phase" class="hidden"><%=bundle.getProperty("gui.progress.status.phase.NONE")%></p>
      <p id="ajaxProblem" class="hidden"><%=bundle.getProperty("gui.progress.problem")%></p>
<%  } %>

<%  if (hasErrors) { %>
      <div id="errors">
  			<h2><%=bundle.getProperty("gui.errors.title")%></h2>
        <ul id="errorList">
<%    for (String msg : errors) { %>
          <li><%=ServletUtilities.preventCrossSiteScriptingAttacks(msg)%></li>
<%    } %>
        </ul>
<%    if (proc.getThrown() != null) { %>
          <p><%=bundle.getProperty("gui.errors.thrown")%></p>
          <pre><%proc.getThrown().printStackTrace(new PrintWriter(out));%><pre>
<%    } %>
      </div>
<%  } %>
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

      <jsp:include page="../footer.jsp" flush="true"/>
		</div>
	</div>
	<!-- end page --></div>
<!--end wrapper -->
</body>
</html>