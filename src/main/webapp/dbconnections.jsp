<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,java.util.concurrent.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%@ page import="org.ecocean.shepherd.core.ShepherdPMF" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);



%>

<html>
<head>
<title>Shepherd Persistence Manager States</title>

</head>


<body>

<h1>Database Connections</h1>
<ol>
<%
ConcurrentHashMap<String,String> map= ShepherdPMF.getAllShepherdStates();
Enumeration<String> keys=map.keys();
while(keys.hasMoreElements()){
	String key=keys.nextElement();
	String status=ShepherdPMF.getShepherdState(key);
	//display any non-closes
	//if(status.indexOf("close")==-1){
	%>
		<li><%=key %>:<%=status %></li>
	<%
	//}
}
%>
</ol>
</body>
</html>
