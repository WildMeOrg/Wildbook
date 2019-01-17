<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

int numFixes=0;

%>

<html>
<head>
<title>Test Query Cache</title>

</head>


<body>
<h1>Testing Query Cache</h1>

<ul>
<%

myShepherd.beginDBTransaction();

QueryCache qc=QueryCacheFactory.getQueryCache(context);
if(qc.getQueryByName("numIndividualsTotal", context)==null){
	StoredQuery sq=new StoredQuery("numIndividualsTotal", "SELECT FROM org.ecocean.MarkedIndividual WHERE individualID != null");
	myShepherd.getPM().makePersistent(sq);
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	qc.loadQueries(context);
}

if(qc.getQueryByName("numEncountersTotal", context)==null){
	StoredQuery sq=new StoredQuery("numEncountersTotal", "SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null");
	sq.setExpirationTimeoutDuration(180000);
	myShepherd.getPM().makePersistent(sq);
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	qc.loadQueries(context);
}

try{

	Map<String,CachedQuery> queries=qc.cachedQueries();
	Set<String> keys=queries.keySet();
	Iterator<String> iter=keys.iterator();
	int numQueries=queries.size();


	while(iter.hasNext()){
		String keyName=iter.next();
		CachedQuery cquery=queries.get(keyName);
		%>
		
		<li><%=cquery.getName() %>:<%=cquery.getQueryString() %>:<%=cquery.executeCountQuery(myShepherd).intValue() %></li>
		
		<%


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

</ul>


</body>
</html>
