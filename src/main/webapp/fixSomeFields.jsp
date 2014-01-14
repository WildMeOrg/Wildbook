<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
	Shepherd myShepherd=new Shepherd();
%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries




Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator allSharks;



try{

allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);


while(allSharks.hasNext()){

	ArrayList<Encounter> originals=new ArrayList<Encounter>();
	
	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	%>
	<p>Indie is: <%=sharky.getIndividualID() %>
	<br />Start encounters: <%=sharky.getEncounters().size() %>
	
	<%
	Vector encs=sharky.getEncounters();
	int numEncs=encs.size();	
	for(int i=0;i<numEncs;i++){
		Encounter enc=(Encounter)encs.get(i);
		
		if(!originals.contains(enc)){
			originals.add(enc);
		}
		
	}
	%>
	<br />Num originals: <%=originals.size() %></p>
	<%
	for(int i=0;i<originals.size();i++){
		sharky.removeEncounter(originals.get(i));
		sharky.addEncounter(originals.get(i));
	}
	
	
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	%>
	<br />End encounters: <%=sharky.getEncounters().size() %></p>
	<%
	
}


myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page allEncounters.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");

	//sharkQuery.closeAll();
	//sharkQuery=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>


</body>
</html>