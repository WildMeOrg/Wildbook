<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

int numFixes=0;

%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>


<%

myShepherd.beginDBTransaction();


try{

	String filter="select from org.ecocean.Annotation where iaClass == 'mantaCR'";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c= (Collection)q.execute();
	ArrayList<Annotation> annots=new ArrayList<Annotation>(c);
	q.closeAll();
	
	%>
	<p>Matches: <%=annots.size() %></p>
	<ul>
	<%
	
	for(Annotation annot:annots){
		
		MediaAsset ma=annot.getMediaAsset();
		%>
		
		<li><%=ma.getDetectionStatus() %></li>
		<%
		ma.setDetectionStatus("complete");
		myShepherd.updateDBTransaction();

	}
	myShepherd.rollbackDBTransaction();
	

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>

<p>Done successfully: <%=numFixes %></p>

</body>
</html>
