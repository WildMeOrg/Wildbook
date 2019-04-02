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

if(request.getParameter("delete")!=null){
	%>

	<p>OK, delete everything old...</p>

	<%
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
     
	




try{
	
	if(qc.getQueryByName("numMarkedIndividuals")==null){
		StoredQuery sq=new StoredQuery("numMarkedIndividuals", "SELECT FROM org.ecocean.MarkedIndividual WHERE individualID != null");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries();
	}
	if(qc.getQueryByName("numEncounters")==null){
		StoredQuery sq=new StoredQuery("numEncounters", "SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries();

	}
	
	if(qc.getQueryByName("numUsersWithRoles")==null){
		StoredQuery sq=new StoredQuery("numUsersWithRoles", "SELECT DISTINCT username FROM org.ecocean.Role");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries();
	}
	
	if(qc.getQueryByName("numUsers")==null){
		StoredQuery sq=new StoredQuery("numUsers", "SELECT FROM org.ecocean.User WHERE uuid != null");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries();
	}
	if(qc.getQueryByName("top3Encounters")==null){
		StoredQuery sq=new StoredQuery("top3Encounters", "SELECT FROM org.ecocean.Encounter WHERE individualID != null ORDER BY dwcDateAddedLong descending RANGE 1,4");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries();
	}
	
	

	Map<String,CachedQuery> queries=qc.cachedQueries();
	Set<String> keys=queries.keySet();
	
	%>
	<h2>Round 1: Cached?</h2>
	<ul>
	<%
	//qc.loadQueries();
	Iterator<String> iter=keys.iterator();
	int numQueries=queries.size();

	long start1=System.currentTimeMillis();
	while(iter.hasNext()){
		String keyName=iter.next();
		CachedQuery cquery=queries.get(keyName);
		cquery.executeCollectionQuery(myShepherd,true);
		%>
		
		<li><%=cquery.getName() %>:<%=cquery.getQueryString() %></li>
		
		<%

	}
	long end1=System.currentTimeMillis();
	%>
	</ul>
	<p>Round 1 took: <%=(end1-start1) %>
	

	
	

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
