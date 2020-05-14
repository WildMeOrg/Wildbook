<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
org.ecocean.media.*,org.ecocean.servlet.importer.ImportTask,
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

int pending=0;
int duped=0;


try {
	
	Query q=myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null && ( submitterID == 'FeliciaVachon' ) && ( state == 'unapproved' ) && dwcDateAddedLong < 1589079679000");
	Collection c=(Collection)q.execute();
	ArrayList<Encounter> al=new ArrayList<Encounter>(c);
	
	for(Encounter enc2trash:al){
		
        Occurrence occ = myShepherd.getOccurrenceForEncounter(enc2trash.getID());
        if (occ==null&&(enc2trash.getOccurrenceID()!=null)&&(myShepherd.isOccurrence(enc2trash.getOccurrenceID()))) {
          occ = myShepherd.getOccurrence(enc2trash.getOccurrenceID());
        }
        
        if(occ!=null) {
          occ.removeEncounter(enc2trash);
          enc2trash.setOccurrenceID(null);
          
          //delete Occurrence if it's last encounter has been removed.
          if(occ.getNumberEncounters()==0){
            myShepherd.throwAwayOccurrence(occ);
          }
          
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
   
        }
        
        //Remove it from an ImportTask if needed
        ImportTask task=myShepherd.getImportTaskForEncounter(enc2trash.getCatalogNumber());
        if(task!=null) {
          task.removeEncounter(enc2trash);
          task.addLog("Servlet EncounterDelete removed Encounter: "+enc2trash.getCatalogNumber());
          myShepherd.updateDBTransaction();
        }
        
        

        if (myShepherd.getImportTaskForEncounter(enc2trash)!=null) {
          ImportTask itask = myShepherd.getImportTaskForEncounter(enc2trash);
          itask.removeEncounter(enc2trash);
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
        }
		myShepherd.getPM().deletePersistent(enc2trash);
		myShepherd.updateDBTransaction();
		
	}
	%>
	<p>Duped: <%=duped %></p>
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
