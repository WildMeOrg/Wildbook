<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*, org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

boolean committing = false;
%>

<html>
<head>
<title>Fix Individuals</title>

</head>


<body>
<h1>Fixing some Individuals</h1>
<h2>Committing? <%=committing%>!</h2>
<ul>
<%

String context="context0";
Shepherd myShepherd=new Shepherd(context);


%><p><em>in context <code><%=context%>:</code></em>
<%

myShepherd.beginDBTransaction();

int numFixes=0;

try {

  Iterator allIndividuals=myShepherd.getAllMarkedIndividuals();


  %>
  <ul>
  <%



  while(allIndividuals.hasNext()/* && numFixes < 20*/){

    MarkedIndividual ind=(MarkedIndividual)allIndividuals.next();
    if (ind.getMaxNumYearsBetweenSightings()==0) {
    	ind.resetMaxNumYearsBetweenSightings();
    	if (ind.getMaxNumYearsBetweenSightings()!=0) {
    		numFixes++;
   			%><li>
    			<ul>ind <%=ind.getIndividualID()%>
    			  <li>set maxnumyearsbetweenSightings: <%=ind.getMaxNumYearsBetweenSightings()%></li>
    			</ul>
    		</li><%

    		if (committing) {
    		  myShepherd.commitDBTransaction();
    		  myShepherd.beginDBTransaction();
    		}
    	}
    }


    if (ind.getNumberLocations()==0) {
    	ind.refreshNumberLocations();
    	if (ind.getNumberLocations()!=0) {
    		numFixes++;
   			%><li>
    			<ul>ind <%=ind.getIndividualID()%>
    			  <li>set getNumberLocations: <%=ind.getNumberLocations()%></li>
    			</ul>
    		</li><%

    		if (committing) {
    		  myShepherd.commitDBTransaction();
    		  myShepherd.beginDBTransaction();
    		}
    	}
    }


  }
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}
%>
</ul>
Done successfully: <%=numFixes %></p>


</body>
</html>
