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
         import="java.io.PrintWriter"
         import="java.util.*"
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
	Shepherd myShepherd = new Shepherd();
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

  BatchProcessor proc = (BatchProcessor)session.getAttribute(BatchUpload.SESSION_KEY_TASK);
  Throwable throwable = (proc == null) ? null : proc.getThrown();
  if (throwable == null)
    throwable = (Throwable)request.getAttribute("thrown");
  List<String> errors = (List<String>)session.getAttribute(BatchUpload.SESSION_KEY_ERRORS);
  List<String> warnings = (List<String>)session.getAttribute(BatchUpload.SESSION_KEY_WARNINGS);
  boolean hasErrors = errors != null && !errors.isEmpty();
  boolean hasWarnings = warnings != null && !warnings.isEmpty();
%>
<html>
<head>
	<title><%=CommonConfiguration.getHTMLTitle() %></title>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
	<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>"/>
	<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
	<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
	<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css"/>
	<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>
	<link href="../css/batchUpload.css" rel="stylesheet" type="text/css"/>
</head>

<body>
<div id="wrapper">
	<div id="page">
		<jsp:include page="../header.jsp" flush="true">
			<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
		</jsp:include>
		<div id="main">

			<h1>Error Page</h1>

      <p>The application encountered a problem.</p>

<%  if (hasErrors) { %>
			<h2><%=bundle.getProperty("gui.errors.title")%></h2>
        <ul id="errorList">
<%    for (String msg : errors) { %>
          <li><%=ServletUtilities.preventCrossSiteScriptingAttacks(msg)%></li>
<%    } %>
        </ul>
<%  } %>

<%  if (throwable != null) { %>
      <p>The following problem occurred:</p>
      <pre>
<%    throwable.printStackTrace(new PrintWriter(out)); %>
      </pre>
<%  } %>

			<jsp:include page="../footer.jsp" flush="true"/>
		</div>
	</div>
	<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


