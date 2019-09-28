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

<%

myShepherd.beginDBTransaction();


String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && encounters.contains(enc1515) &&( enc1515.submitterID == \"SDRP\" ) && ((enc.dwcDateAddedLong >= 1356998400000) && (enc.dwcDateAddedLong <= 1640995140000)) VARIABLES org.ecocean.Encounter enc;org.ecocean.Encounter enc1515";

PersistenceManager pm=myShepherd.getPM();
PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();
FetchPlan fp=pm.getFetchPlan();

//Create a FetchGroup on the PMF called "TestGroup" for MyClass
//FetchGroup grp = pm.getPersistenceManagerFactory().getFetchGroup(MarkedIndividual.class, "TestGroup");
//grp.addMember("field1").addMember("field2");


try {
	
	
	//default fetch group query
	long q1StartTime=System.currentTimeMillis();
	Query q1=pm.newQuery(filter);
	Collection c = (Collection) (q1.execute());
	%>
	<p>Completed default fetch group query in: <%=System.currentTimeMillis()-q1StartTime %></p>
	<%
	
	//count (empty) fetch group
	long q2StartTime=System.currentTimeMillis();
	pm.getFetchPlan().setGroup("count");
	Query q2=pm.newQuery(filter);
	Collection c2 = (Collection) (q2.execute());
	%>
	<p>Completed 'count' fetch group query in: <%=System.currentTimeMillis()-q2StartTime %></p>
	<%
	
	
	//custom fetch group for individualSearchResults.jsp
	// Create a FetchGroup on the PMF called "TestGroup" for MyClass
	FetchGroup grp = pmf.getFetchGroup(MarkedIndividual.class, "individualSearchResults");
	grp.addMember("individualID").addMember("sex").addMember("names").addMember("numberEncounters").addMember("numberLocations").addMember("maxYearsBetweenResightings").addMember("thumbnailUrl");
			
	long q3StartTime=System.currentTimeMillis();
	pm.getFetchPlan().setGroup("individualSearchResults");
	Query q3=pm.newQuery(filter);
	Collection c3 = (Collection) (q3.execute());
	%>
	<p>Completed 'individualSearchResults' fetch group query in: <%=System.currentTimeMillis()-q3StartTime %></p>
	<%		

	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>



</body>
</html>
