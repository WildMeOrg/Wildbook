<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, 
java.io.File, java.io.FileNotFoundException, org.ecocean.*,
org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, 
java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
org.ecocean.ai.nmt.azure.*"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Testing Azure Machine Translation...</title>

</head>



<body>

<ul>
<%

String testStringEN = "Hello there, I was on vacation and took this rad picture of a whaleshark. My family and I were eating ham sandwiches for lunch, and it just swam by.";
String testStringES = "Hola, estaba de vacaciones y tomé esta foto de un tiburón ballena. Mi familia y yo estábamos comiendo sandwiches de jamón para el almuerzo, y simplemente nadamos.";
String testStringCH = "那你好，我正在度假，拍了一张鲸鱼的照片。我和我的家人正在吃火腿三明治作为午餐，它只是在游泳";
%>
<p>English String: <%=testStringEN%></p>
<p>Spanish String: <%=testStringES%></p>
<p>Chinese String: <%=testStringCH%></p>
<br>
<hr>
<%
try{
	myShepherd.beginDBTransaction();
%>


	<p>EN String lang code result: <%=DetectTranslate.detectLanguage(testStringEN)%></p>
	<p>ES String lang code result: <%=DetectTranslate.detectLanguage(testStringES)%></p>
    <p>CH String lang code result: <%=DetectTranslate.detectLanguage(testStringCH)%></p>
    <br>
    <hr>
    <p>EN String --> ES: <%=DetectTranslate.translateToLanguage(testStringEN,"es")%> </p>
    <p>EN String --> CH: <%=DetectTranslate.translateToLanguage(testStringEN,"zh-Hans")%> </p>
    <p>ES String --> EN: <%=DetectTranslate.translateToLanguage(testStringES,"en")%> </p>
    <p>ES String --> CH  <%=DetectTranslate.translateToLanguage(testStringES,"zh-Hans")%> </p>
    <p>CH String --> ES  <%=DetectTranslate.translateToLanguage(testStringCH,"es")%> </p>
    <p>CH String --> EN  <%=DetectTranslate.translateToLanguage(testStringCH,"en")%> </p>
    <br>
    <hr>
    <h5>translateIfNotEnglish() method results....</h5>
    <p>ES String: <%=DetectTranslate.translateIfNotEnglish(testStringES)%></p>
    <p>CH String: <%=DetectTranslate.translateIfNotEnglish(testStringCH)%></p>

<%
}
catch(Exception e){

	%>
	<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
	<%
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ul>

</body>
</html>
