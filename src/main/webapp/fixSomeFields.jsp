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

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator allEncs;





Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator allSharks;



try{



	
allEncs=myShepherd.getAllEncountersForSpeciesWithSpots("Tursiops", "truncatus").iterator();
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;

DateTimeFormatter fmt = ISODateTimeFormat.date();
DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();

%>
<ol>
<%

while(allEncs.hasNext()){
	

	Encounter sharky=(Encounter)allEncs.next();

	
	if((sharky.getLeftReferenceSpots()==null)||(sharky.getLeftReferenceSpots().size()<10)){
		
		
		%>
		
		<li><a target="_blank" href="encounters/encounter.jsp?number=<%=sharky.getCatalogNumber() %>">This Encounter does not have all 10 reference points.</a></li>
		
		<%
			
	}
	else{
	
		//check orientation
		EncounterLite encLite=new EncounterLite(sharky);
		SuperSpot[] refSpots=encLite.getLeftReferenceSpots();
		if(refSpots[0].getCentroidX()<refSpots[2].getCentroidX()){
			%>
			<li><a target="_blank" href="encounters/encounter.jsp?number=<%=sharky.getCatalogNumber() %>">Ref spots 0 and 2 are inverted.</a></li>
			<%
		}
		if(refSpots[0].getCentroidY()>refSpots[1].getCentroidY()){
			%>
			<li><a target="_blank" href="encounters/encounter.jsp?number=<%=sharky.getCatalogNumber() %>">Ref spots 0 and 1 are inverted.</a></li>
			<%
		}
		if(encLite.getRightSpots()!=null){
			%>
			<li><a target="_blank" href="encounters/encounter.jsp?number=<%=sharky.getCatalogNumber() %>">Has right spots.</a></li>
			<%
		}
	
	}
	
	


}
	

%>
</ol>

<p>Done successfully!</p>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");
	encQuery.closeAll();
	encQuery=null;
	//sharkQuery.closeAll();
	//sharkQuery=null;


}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>


</body>
</html>
