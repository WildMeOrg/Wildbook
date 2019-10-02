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
<title>Merge SDRP</title>

</head>


<body>
<ol>
<%

myShepherd.beginDBTransaction();

String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && ( enc.submitterID == \"SDRP\" )  VARIABLES org.ecocean.Encounter enc";
Query q=myShepherd.getPM().newQuery(filter);
Collection c = (Collection) (q.execute());
ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
q.closeAll();

HashMap<String, MarkedIndividual> names=new HashMap<String, MarkedIndividual>();


try {

	int numIndies=indies.size();
	for(int i=0;i<numIndies;i++){
		MarkedIndividual indy=indies.get(i);
		names.put(indy.getDefaultName(), indy);
	}
	
	for(int i=0;i<numIndies;i++){
		MarkedIndividual indy=indies.get(i);
		if(!indy.getDefaultName().startsWith("SDRP")){
			if(names.containsKey("SDRP-"+indy.getDefaultName())){
				%>
				<li><%=indy.getDefaultName() %></li>
				<%
				
				MarkedIndividual other=names.get("SDRP-"+indy.getDefaultName());
				
				indy.mergeAndThrowawayIndividual(other, request, myShepherd);
				myShepherd.updateDBTransaction();
				names.remove("SDRP-"+indy.getDefaultName());
				
			}
		}
	}
	
	//new let's correct for all Encounters to ensure that they have the correct genus and species

	 List<Encounter> allEncs=myShepherd.getEncountersByField("submitterID","SDRP");

		int count = 0;
		int printPeriod = 10;
		for (Encounter enc: allEncs) {
			if(enc.getGenus()==null || !enc.getGenus().equals("Tursiops")){
				enc.setGenus("Tursiops");
				enc.setSpecificEpithet("truncatus");
				myShepherd.updateDBTransaction();
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

</ol>

</body>
</html>
