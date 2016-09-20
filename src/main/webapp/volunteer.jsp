<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="java.util.*" %>
<%
	String context = ServletUtilities.getContext(request);
	String langCode = ServletUtilities.getLanguageCode(request);
	Properties props = ShepherdProperties.getProperties("volunteer.properties", langCode, context);
%>

<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

	<h1><%=props.getProperty("title")%></h1>

	<p><%=props.getProperty("text1")%></p>
	<p><%=props.getProperty("text2")%></p>
	<p><%=props.getProperty("text3")%></p>
	<p><%=props.getProperty("text4")%></p>
	<ul>
		<li><%=props.getProperty("include1")%></li>
		<li><%=props.getProperty("include2")%></li>
		<li><%=props.getProperty("include3")%></li>
		<li><%=props.getProperty("include4")%></li>
		<li><%=props.getProperty("include5")%></li>
	</ul>
	<p><%=props.getProperty("text5")%></p>
	</div>
	
<jsp:include page="footer.jsp" flush="true" />

