<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,java.util.Collection,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
    String qstr = "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \"" + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
    Query query = myShepherd.getPM().newQuery(qstr);
    query.setIgnoreCache(true);
    query.setOrdering("created");
    Collection c=(Collection)query.execute();
    ArrayList<Task> listy=new ArrayList<Task>(c);
    query.closeAll();
    return listy;
}

%>

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
<ol>
<%

myShepherd.beginDBTransaction();

String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && encounters.contains(enc1515) &&( enc1515.submitterID == 'globice' ) VARIABLES org.ecocean.Encounter enc;org.ecocean.Encounter enc1515";
Query q=myShepherd.getPM().newQuery(filter);

HashMap<String,MarkedIndividual> idMap=new HashMap<String, MarkedIndividual>();

try {
	
	Collection c = (Collection) (q.execute());
	ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
	q.closeAll();
	
	for(MarkedIndividual indy:indies){
		String displayName=indy.getDisplayName();
		if(!idMap.containsKey(displayName)){
			idMap.put(displayName, indy);
		}
		else{
			
			MarkedIndividual consolidatedIndy=idMap.get(displayName);
			
			List<Encounter> listy=indy.getEncounterList();
			for(Encounter enc:listy){
				indy.removeEncounter(enc);
				enc.setIndividual(consolidatedIndy);
				consolidatedIndy.addEncounter(enc);
				myShepherd.updateDBTransaction();
			}
			
			
			
			%>
			<li><%=displayName %> was fixed!</li>
			<%
		}
	}

	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>


</ol>
</body>
</html>
