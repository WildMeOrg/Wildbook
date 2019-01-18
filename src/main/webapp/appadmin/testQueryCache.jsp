<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,
org.json.*,
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

<%

myShepherd.beginDBTransaction();

QueryCache qc=QueryCacheFactory.getQueryCache(context);



%>

<p>OK, delete everything old...</p>

<%
if(request.getParameter("delete")!=null){
	List<StoredQuery> st=myShepherd.getAllStoredQueries();
	for(int i=0;i<st.size();i++){
		
		StoredQuery s=st.get(i);
		myShepherd.getPM().deletePersistent(s);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		
	}
	
	
	//remove cache files
	String writePath=ShepherdProperties.getProperties("cache.properties","").getProperty("cacheRootDirectory");
	File cacheDir=new File(writePath);
	File[] files=cacheDir.listFiles();
	for(int i=0;i<files.length;i++){
		File f=files[i];
		f.delete();
	}
}	
     
	
	
	
	qc.loadQueries(context);


//add a query
//if(qc.getQueryByName("numIndividualsTotal", context)==null){
	StoredQuery sq=new StoredQuery("numIndividualsTotal", "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && ((enc.dwcDateAddedLong >= 1041379200000) && (enc.dwcDateAddedLong <= 1577836740000)) && ((enc.dateInMilliseconds >= -189388800000) && (enc.dateInMilliseconds <= 1577836740000)) && ( maxYearsBetweenResightings >= 20 ) VARIABLES org.ecocean.Encounter enc");
	myShepherd.getPM().makePersistent(sq);
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	qc.loadQueries(context);
//}

JSONObject jsonobj=new JSONObject();
jsonobj.put("name", "Bob Dobaleena");
qc.addCachedQuery(jsonobj, "exampleQuery", true, myShepherd);



/*
if(qc.getQueryByName("numEncountersTotal", context)==null){
	StoredQuery sq=new StoredQuery("numEncountersTotal", "SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null");
	sq.setExpirationTimeoutDuration(180000);
	myShepherd.getPM().makePersistent(sq);
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	qc.loadQueries(context);
}*/

try{

	Map<String,CachedQuery> queries=qc.cachedQueries();
	Set<String> keys=queries.keySet();
	
	%>
	<h2>Round 1: Uncached</h2>
	<ul>
	<%
	Iterator<String> iter=keys.iterator();
	int numQueries=queries.size();

	long start1=System.currentTimeMillis();
	while(iter.hasNext()){
		String keyName=iter.next();
		CachedQuery cquery=queries.get(keyName);
		cquery.executeCollectionQuery(myShepherd, true);
		%>
		
		<li><%=cquery.getName() %>:<%=cquery.getQueryString() %></li>
		
		<%

	}
	long end1=System.currentTimeMillis();
	%>
	</ul>
	<p>Round 1 took: <%=(end1-start1) %>
	
	<h2>Round 2: Cached</h2>
	<ul>
	<%
	iter=keys.iterator();
	//int numQueries=queries.size();

	long start2=System.currentTimeMillis();
	while(iter.hasNext()){
		String keyName=iter.next();
		CachedQuery cquery=queries.get(keyName);
		cquery.executeCollectionQuery(myShepherd, true);
		%>	
		<li><%=cquery.getName() %>:<%=cquery.getQueryString() %></li>
		<%

	}
	long end2=System.currentTimeMillis();
	%>
	</ul>
	<p>Round 2 took: <%=(end2-start2) %>
	
	
	<%
	myShepherd.rollbackDBTransaction();
	

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}


%>




</body>
</html>
