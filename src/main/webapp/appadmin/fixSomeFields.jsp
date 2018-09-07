<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Data Sheet. How do they work?</title>

</head>

<body>
<ul>
<%

try{

	Encounter enc = new Encounter("mortality", context, myShepherd);

	DataSheet ds = new DataSheet(Util.generateUUID());
	
	System.out.println("++++++ New Enc: "+enc.toString());

	System.out.println("++++++ New DataSheet: "+ds.toString());

	System.out.println("++++++++++ Recording DataSheet???");
	enc.record(ds);

	System.out.println("++++++++++ Add Config DataSheet???");
	enc.addConfigDataSheet(context);

	System.out.println("++++++++++++++++++++++ Can I persist them independantly?");

	myShepherd.storeNewEncounter(enc);
	myShepherd.storeNewDataSheet(ds);
	
}
catch(Exception e){
	e.printStackTrace();
	//myShepherd.rollbackDBTransaction();
}

%>

</ul>

</body>
</html>
