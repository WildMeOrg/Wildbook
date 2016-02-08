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
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  // Setup context.
  String context = ServletUtilities.getContext(request);
  // Page internationalization.
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties bundle = ShepherdProperties.getProperties("actionResults.properties", langCode, context);
  // Find ActionResult.
  ActionResult actRes = (ActionResult)request.getSession().getAttribute(ActionResult.SESSION_KEY);
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:include page="header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>

<style type="text/css">
  #actionResultMessage {
    font-size: 1.2em;
  }
  #actionResultMessage .prefix {
    font-weight: bold;
  }
  #actionResultComment .content {
    font-size: 0.9em;
  }
  #actionResultDetail {
    margin-top: 0.5em;
  }
  #actionResultDetail .content {
    font-size: 0.75em;
  }
  #actionResultLink p {
    font-weight: bold;
  }
  div.expando .toggler {
    color: #00c0f7;
    cursor: pointer;
  }
  div.expando .toggler.active {
    display: none;
  }
  div.expander {
    position: relative;
    height: 0;
    overflow: hidden;
    padding: 1em;
    margin-bottom: 1em;
    background: #DDD;
  }
  div.expander .pre {
    display: block;
    unicode-bidi: embed;
    font-family: monospace;
    white-space: pre;
    overflow: auto;
  }
</style>
<script>
  jQuery(document).ready(function($) {
    $('div.expando .toggler').each(function() {
      var elem = $(this), state = !elem.hasClass('active');
      var expando = elem.parents('div.expando');
      var expander = expando.find('div.expander').hide().css('height','auto').slideUp();
      elem.click(function() {
        state = !state;
        expander.slideToggle(state);
        elem.toggleClass('active', state);
        expando.find('.toggler').toggle();
      });
    });
  });
</script>

<div class="container maincontent">

  <!------------------------------------------------------------------------->

  <h1 class="intro"><%=bundle.getProperty("page.title")%></h1>

<%
  String messageText = actRes.getMessage(bundle);
  String messagePrefix = bundle.getProperty(String.format("action.title.messagePrefix.%s", actRes.isSucceeded() ? "success" : "failure"));
  if (messageText != null) {
%>
  <div id="actionResultMessage">
<%  if (messagePrefix != null && messageText != null) { %>
    <p><span class="prefix"><%=messagePrefix%></span> <span class="content"><%=messageText%></span></p>
<%  } else { %>
    <p class="content"><%=messageText%></p>
<%  } %>
  </div>
<%
  }
%>

<%
  // Comment is a regular text section which is shown if set in the ActionResult instance.
  String commentText = actRes.getComment(bundle);
  String commentPrefix = bundle.getProperty(String.format("%s.commentPrefix.%s", actRes.getActionKey(), actRes.isSucceeded() ? "success" : "failure"));
  if (commentPrefix == null)
    commentPrefix = bundle.getProperty(String.format("action.title.commentPrefix.%s", actRes.isSucceeded() ? "success" : "failure"));
  if (commentText != null && !"".equals(commentText)) {
%>
  <div id="actionResultComment">
<%  if (commentPrefix != null) { %>
    <p class="prefix"><strong><%=commentPrefix%></strong></p>
<%  } %>
    <div class="content"><%=commentText%></div>
  </div>
<%
  }
%>

<%
  // DetailText is a collapsible text section which is shown if set in the ActionResult instance.
  // It may optionally be set to use preformatted text (e.g. for console output).
  String detailText = actRes.getDetailText(bundle);
  String detailPrefix = bundle.getProperty(String.format("%s.detailPrefix.%s", actRes.getActionKey(), actRes.isSucceeded() ? "success" : "failure"));
  if (detailPrefix == null)
    detailPrefix = bundle.getProperty(String.format("action.title.detailPrefix.%s", actRes.isSucceeded() ? "success" : "failure"));
  String expand = bundle.getProperty("action.title.detailPrefix.expand");
  String collapse = bundle.getProperty("action.title.detailPrefix.collapse");
  if (detailText != null && !"".equals(detailText)) {
%>
  <div id="actionResultDetail" class="expando">
    <p class="prefix"><strong><%=detailPrefix%></strong> <span class="toggler"><%=expand%></span><span class="toggler active"><%=collapse%></span></p>
    <div class="expander">
<%  if (actRes.isDetailTextPreformatted()) { %>
      <div class="content pre"><%=detailText%></div>
<%  } else { %>
      <div class="content"><%=detailText%></div>
<%  } %>
    </div>
  </div>
<%
  }
%>

<%
  if (actRes.getLink() != null) {
%>
  <div id="actionResultLink">
    <p><a href="<%=actRes.getLink()%>"><%=actRes.getLinkText(bundle)%></a></p>
  </div>
<%
  }
%>


  <!------------------------------------------------------------------------->


</div>

<jsp:include page="footer.jsp" flush="true"/>
