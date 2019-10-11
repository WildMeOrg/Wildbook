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

String genus="";


%>

<html>
<head>
<title>Individual Species Consistency Check</title>

</head>


<body>

<%
if(request.getParameter("genus")==null){
	%>
	<p>You must at least include ?genus=</p>
	<%
}
else{

	genus=request.getParameter("genus");
	myShepherd.beginDBTransaction();
	
	String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && ( enc.genus == \""+genus+"\" )  VARIABLES org.ecocean.Encounter enc";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c = (Collection) (q.execute());
	ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
	q.closeAll();
	
	
	
	try {
	
		int numIndies=indies.size();
		%>
		
		<p>Checking <%=numIndies %> individuals for consistency with genus <em><%=genus %></em>. Listed below will be any inconsistencies. No results are a good thing!</p>
		<ol>
		<%
	
		
		for(int i=0;i<numIndies;i++){
			MarkedIndividual indy=indies.get(i);
			String speciesString="";
			boolean hasOtherSpecies=false;
			List<Encounter> encs=indy.getEncounterList();
			for(Encounter enc:encs){
				if(enc.getGenus()!=null && !enc.getGenus().equals("Megaptera")){
					hasOtherSpecies=true;
					speciesString+=enc.getGenus();
				}
			}
			if(hasOtherSpecies){
				%>
				<li><%=indy.getDefaultName() %>:<%=indy.getIndividualID() %>: <%=speciesString %></li>
				<%
			}
		}
	}
	catch(Exception e){
		%>
		Error!
		<%
		e.printStackTrace();
	}
	finally{
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
	
	}
	%>
	</ol>
	<%


} //end else

%>

</body>
</html>
