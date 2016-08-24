<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.datacollection.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<html>

<head>
  <title>Test Some Stuff</title>
</head>

<body>

<p>Creating a DataSheet from a config file.</p>

<ul>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
myShepherd.beginDBTransaction();
int numFixes=0;

try{

  DataSheet fromConfig = DataSheet.fromCommonConfig("nest", context);
  System.out.println("I made the DataSheet, now to persist it");
  boolean dSheetSaved = myShepherd.storeNewDataSheet(fromConfig);
  System.out.println("DataSheet persisted!");


  Nest nest = new Nest(fromConfig);

  boolean saved = myShepherd.storeNewNest(nest);
  System.out.println("I made a nest, saved="+saved+", and its DataSheet.toLabeledString() = "+nest.getDataSheets().get(0).toLabeledString());
  out.println("I made a nest, saved="+saved+", and its DataSheet.toLabeledString() = "+nest.getDataSheets().get(0).toLabeledString());
  //out.println("I made a nest and its DataSheet.toLabeledString() = "+nest.getDataSheets().get(0).toLabeledString());
  //System.out.println("I made a nest and its DataSheet.toLabeledString() = "+nest.getDataSheets().get(0).toLabeledString());

}
catch(Exception e){
  e.printStackTrace();
}

finally{
  myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %></p>

</body>
</html>
