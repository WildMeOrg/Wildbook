<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>



<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("deleteOrphanedIndividuals.jsp");

boolean commit=false;
if(request.getParameter("commit")!=null && request.getParameter("commit").equals("true")){
	commit=true;
}
%>

<html>
<head>
<title>Delete Orphaned Individuals</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();



try {
	
	int count=0;
	Iterator<MarkedIndividual> allSharks = myShepherd.getAllMarkedIndividuals();
	while(allSharks.hasNext()){
		MarkedIndividual indy=(MarkedIndividual)allSharks.next();
		if(indy.getEncounters()==null || indy.getEncounters().size()==0){
			count++;
			%>
			<p><a href="../individuals.jsp?number=<%=indy.getIndividualID() %>"><%=indy.getDisplayName() %></p>
			<%
			if(commit){
				myShepherd.getPM().deletePersistent(indy);
				myShepherd.updateDBTransaction();
			}
		}
	}

	%>
	<%=count %>
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
