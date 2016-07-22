<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;


try{

	Iterator allEncs=myShepherd.getAllEncounters();
	


	while(allEncs.hasNext()){
		
		Encounter enc=(Encounter)allEncs.next();
		if((enc.getLocationID()!=null)&&( (enc.getLocationID().trim().toLowerCase().equals("no data"))||(enc.getLocationID().trim().toLowerCase().equals("none")) )){
			enc.setLocationID(null);
			myShepherd.commitDBTransaction();
			myShepherd.beginDBTransaction();
		}

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
