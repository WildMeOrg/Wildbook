<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%


	Shepherd myShepherd=new Shepherd();

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />

<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />
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

//empty comment



try{


allEncs=myShepherd.getAllEncounters(encQuery);
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numLogEncounters=0;




while(allSharks.hasNext()){

	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	
	sharky.resetMaxNumYearsBetweenSightings();
	
}


myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>
<p>numLogEncounters: <%=numLogEncounters %></p>

<%
} 
catch(Exception ex) {

	ex.printStackTrace();
	%>
	!!!An error occurred on page allEncounters.jsp.
	<%
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");
	encQuery.closeAll();
	encQuery=null;
	sharkQuery.closeAll();
	sharkQuery=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>


</body>
</html>