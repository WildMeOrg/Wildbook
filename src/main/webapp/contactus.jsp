<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = "context0";
  context = ServletUtilities.getContext(request);

  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("contactus.properties", langCode, context);

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">

  <h1 class="intro"><%=props.getProperty("title")%></h1>

  <p><%=props.getProperty("text1")%></p>

  <p><%=props.getProperty("text2")%></p>

<!-- end maintext -->
</div>

<jsp:include page="footer.jsp" flush="true"/>
