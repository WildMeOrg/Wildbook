<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<%

String individualID="ff83d036-44cc-4b63-8bf3-f4d98d1ffef8";

%>

<body>

<h2>Analysis for <%=individualID %></h2>

<ul>
<%



//Modified Groth algorithm parameters
String epsilon = "0.008";
String R = "49.8";
String Sizelim = "0.998";
String maxTriangleRotation = "12.33";
String C = "0.998";
String secondRun = "true";


java.util.Properties props2 = new java.util.Properties();

props2.setProperty("rightScan", "false");


//Modified Groth algorithm parameters
//pulled from the gridManager
props2.setProperty("epsilon", epsilon);
props2.setProperty("R", R);
props2.setProperty("Sizelim", Sizelim);
props2.setProperty("maxTriangleRotation",maxTriangleRotation);
props2.setProperty("C", C);
props2.setProperty("secondRun", secondRun);

myShepherd.beginDBTransaction();
try{
	
	ArrayList<Encounter> leftEncs=new ArrayList<Encounter>();
	ArrayList<Encounter> rightEncs=new ArrayList<Encounter>();
	
	MarkedIndividual indy=myShepherd.getMarkedIndividual(individualID);
	int numEncs=indy.getEncounterList().size();
	List<Encounter> encs=indy.getEncounterList();
	for(int i=0;i<numEncs;i++){
		Encounter enc=encs.get(i);
		if(enc.getSpots()!=null && enc.getSpots().size()>3){
			leftEncs.add(enc);
		}
		if(enc.getRightSpots()!=null && enc.getRightSpots().size()>3){
			rightEncs.add(enc);
		}
	}
	
	if(leftEncs.size()>2){
		for(int i=0;i<leftEncs.size();i++){
			for(int j=i+1;j<leftEncs.size();j++){
				
				ScanWorkItem swi=new ScanWorkItem(leftEncs.get(i), leftEncs.get(j), (individualID+"_"+i), individualID, props2);
				MatchObject mo=swi.execute();    
				String finalscore = (new Double(mo.getMatchValue() * mo.getAdjustedMatchValue())).toString();
	            
				%>
				<li><strong><%=finalscore %></strong>: <a href="../encounters/encounter.jsp?number=<%=leftEncs.get(i).getCatalogNumber() %>" target="_blank"><%=leftEncs.get(i).getCatalogNumber() %></a> vs. <a href="../encounters/encounter.jsp?number=<%=leftEncs.get(j).getCatalogNumber() %>" target="_blank"><%=leftEncs.get(j).getCatalogNumber() %></a></li>
				<%
				
			}
		}
	}
	
	

}
catch(Exception e){
	
	%>
	<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
	<%
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ul>

</body>
</html>
