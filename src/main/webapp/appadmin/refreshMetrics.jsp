<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,org.ecocean.servlet.importer.*,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,org.ecocean.servlet.importer.ImportTask,
java.io.*,java.util.*, java.io.FileInputStream, 
java.text.SimpleDateFormat,
java.util.Date,org.ecocean.ia.*,
org.ecocean.identity.IBEISIA,org.ecocean.social.*,org.ecocean.ia.Task,
org.apache.poi.ss.usermodel.DateUtil,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, 
java.util.Iterator, java.lang.NumberFormatException"%>



<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Standard Children</title>

</head>


<body>


<ol>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numOrphanAssets=0;	
int numDatalessAssets=0;	
int numDatasFixed=0;	
int numAssetsFixed=0;	
int numAssetsWithoutStore=0;	

boolean committing=true;


try{

	MetricsBot.refreshMetrics(context);
	

}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ol>


</body>
</html>
