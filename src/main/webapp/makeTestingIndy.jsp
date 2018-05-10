<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);

%>

<html>
<head>
<title>Add Some Indy's</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries
String result = "";

for (int i=0;i<10;i++) {
    Encounter enc = new Encounter();
    myShepherd.storeNewEncounter(enc, Util.generateUUID());
    MarkedIndividual mi = new MarkedIndividual(Util.generateUUID(),enc);
    myShepherd.storeNewMarkedIndividual(mi);
    result += "NEW INDY "+i;

}

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>

<h2><%=result%></h2>



</body>
</html>
