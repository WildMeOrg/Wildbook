<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String message="Successfuly reloaded JSON locationID graph for file: ";

if(request.getParameter("qualifier")!=null){
	message+=request.getParameter("qualifier").trim();
	LocationID.reloadJSON(request.getParameter("qualifier").trim());
	message+="<br>"+LocationID.getLocationIDStructure(request.getParameter("qualifier")).toString();
}
else{
	LocationID.reloadJSON(null);
	message+="<br>"+LocationID.getLocationIDStructure().toString();
}





%>

<html>
<head>
<title>Reload a LocationID.json File</title>

</head>


<body>
<h1>Reload a LocationID.json File</h1>
<p><%=message %></p>

</html>
