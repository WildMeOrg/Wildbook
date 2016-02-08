<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  // Setup our Properties object to hold all properties
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("logout.properties", langCode, context);
%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">

  <h1 class="intro"><%=props.getProperty("title")%></h1>

  <p><%=props.getProperty("text")%></p>
        
  <%
  Logger log = LoggerFactory.getLogger(getClass());
  log.info(request.getRemoteUser()+" logged out.");
  %>

  <p><a href="welcome.jsp"><%=props.getProperty("loginText")%></a></p>

  <p>&nbsp;</p>
</div>
<jsp:include page="footer.jsp" flush="true"/>
<%
  session.invalidate();
%>

