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
org.ecocean.ai.nlp.*,
org.ecocean.ai.nmt.azure.*,
org.ecocean.ai.ocr.azure.*,
org.ecocean.ai.ocr.google.*"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

%>

<html>
<head>
<title>Azure OCR Testing</title>

</head>


<body>

<h3>Azure OCR Testing</h3>

<%
ArrayList<MediaAsset> results = new ArrayList<>();
String resultString = "";
try {        
    String hasTextUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/Atomist_quote_from_Democritus.png/338px-Atomist_quote_from_Democritus.png";
    String noTextURL = "https://www.whaleshark.org/wildbook_data_dir/5/a/5a1e8ee7-8e30-4913-8998-2b89368d1b2d/e38c2895-fecc-46e3-9e94-c4a3cf29da63-mid.jpg";
    String another = "https://imgs.mongabay.com/wp-content/uploads/sites/22/2016/02/03165949/Splash-tag-on-shark.jpg";
    String lotsaWords = "https://static.seattletimes.com/wp-content/uploads/2017/07/WEB-largest-smallest-shark-1020x680.jpg";

        resultString = "<img src=\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/Atomist_quote_from_Democritus.png/338px-Atomist_quote_from_Democritus.png\">";
        resultString += "<p>Pic with words: "+AzureOcr.postSingleAsset(hasTextUrl, "en")+"</p><br>";

        resultString += "<img src=\"https://www.whaleshark.org/wildbook_data_dir/5/a/5a1e8ee7-8e30-4913-8998-2b89368d1b2d/e38c2895-fecc-46e3-9e94-c4a3cf29da63-mid.jpg\">";
        resultString += "<p>Pic with nothing: "+AzureOcr.postSingleAsset(noTextURL, "en")+"</p><br>"; 

        resultString += "<img src=\"https://imgs.mongabay.com/wp-content/uploads/sites/22/2016/02/03165949/Splash-tag-on-shark.jpg\">";
        resultString += "<p>Pic with a date: "+AzureOcr.postSingleAsset(another, "en")+"</p><br>";

        resultString += "<img src=\"https://static.seattletimes.com/wp-content/uploads/2017/07/WEB-largest-smallest-shark-1020x680.jpg\">";
        resultString += "<p>Pic with disorganized words: "+AzureOcr.postSingleAsset(lotsaWords, "en")+"</p><br>";
		
} catch (Exception e) {

%>

    <p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>

<%

	e.printStackTrace();

} 
//	myShepherd.rollbackDBTransaction();
//myShepherd.closeDBTransaction();

%>

<!--Assuming all goes well... -->
<p><%=resultString%></p>

</ul>

</body>
</html>
