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
<p>Humpbacks with multiple photos but requiring  trailing edge mapping</p>
<ol>
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



	
allEncs=myShepherd.getAllEncountersForSpeciesWithSpots("Megaptera", "novaeangliae").iterator();
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

while(allSharks.hasNext()){
	
	MarkedIndividual indy=(MarkedIndividual)allSharks.next();
	ArrayList<SinglePhotoVideo> allP=indy.getAllSinglePhotoVideo();
	if((indy.getGenusSpecies().equals("Megaptera novaeangliae"))&&(allP.size()>1)&&(indy.getNumberTrainableEncounters()==0)&&(indy.getEncounters().size()>1)){
		//for(int i=0;i<allP.size();i++){
			%>
			<li><a target="_blank" href="individuals.jsp?number=<%=indy.getIndividualID() %>"><%=indy.getIndividualID() %></a></li>
			<%
		//}
		
	}
}

%>

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

</ol>
</body>
</html>
