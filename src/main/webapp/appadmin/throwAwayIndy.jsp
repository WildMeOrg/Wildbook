
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

String indyName = request.getParameter("name");
PrintWriter out = response.getWriter();

%>

<html>
<head>
<title>Remove Test Encounters...</title>

</head>

<body>
<ul>
<%

try{

    out.println("Removing individual "+indyName);
	MarkedIndividual mi = myShepherd.getMarkedIndividual(indyName);
	
    Encounter[] encs = mi.getEncounters().toArray();
    for (Encounter enc : encs) {
        mi.removeEncounter(enc, context);
    }
    myShepherd.throwAwayMarkedIndividual(mi);
    out.println("Success?!?!?!?");
}
catch(Exception e){
	e.printStackTrace();
	//myShepherd.rollbackDBTransaction();
}

%>

</ul>

</body>
</html>

