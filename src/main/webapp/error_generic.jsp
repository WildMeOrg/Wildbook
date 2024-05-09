

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" isErrorPage="true" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.CommonConfiguration" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("error.properties", langCode,context);
%>


  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>

<div class="container maincontent">


          <h1 class="intro"><%=props.getProperty("title")%></h1>

          <p><%=props.getProperty("error.prefix") %></p>

          <c:set var="exception" value="${requestScope['javax.servlet.error.exception']}"/>
          <pre><%
				
          		exception.printStackTrace(new java.io.PrintWriter(out)); 
				
				%>
		</pre>




      <!-- end maincol --></div>

      <jsp:include page="footer.jsp" flush="true"/>

