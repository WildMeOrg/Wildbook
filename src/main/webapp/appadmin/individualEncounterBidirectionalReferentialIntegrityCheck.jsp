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


String filter="SELECT FROM org.ecocean.Encounter WHERE individual == indy && !indy.encounters.contains(this)  VARIABLES org.ecocean.MarkedIndividual indy";

String filter2="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc1) && enc1.individual != this  VARIABLES org.ecocean.Encounter enc1";

%>

<p>Encounters Pointing to Individuals but Individuals NOT Pointing Back</p>
<ol>
<%

try {
	
	Query q=myShepherd.getPM().newQuery(filter);
    Collection results = (Collection) q.execute();
    ArrayList<Encounter> encs = new ArrayList(results);

    for(Encounter enc:encs){
    	%>
    	<li><a href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber() %>"><%=enc.getCatalogNumber() %></a></li>
    	<%
    }
    
%>
</ol>    
<p>Individuals Pointing at Encounters that Don't Point Back</p>
<ol>
<%
Query q2=myShepherd.getPM().newQuery(filter2);
Collection results2 = (Collection) q2.execute();
ArrayList<MarkedIndividual> encs2 = new ArrayList(results2);

for(MarkedIndividual enc:encs2){
	%>
	<li><a href="../individuals.jsp?number=<%=enc.getIndividualID() %>"><%=enc.getIndividualID() %></a></li>
	<%
}

%>
</ol> 
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
