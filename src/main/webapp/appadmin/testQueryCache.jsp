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
	
	if(qc.getQueryByName("numMarkedIndividuals", context)==null){
		StoredQuery sq=new StoredQuery("numMarkedIndividuals", "SELECT FROM org.ecocean.MarkedIndividual WHERE individualID != null");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries(context);
	}
	if(qc.getQueryByName("numEncounters", context)==null){
		StoredQuery sq=new StoredQuery("numEncounters", "SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries(context);

	}
	if(qc.getQueryByName("numUsersWithRoles", context)==null){
		StoredQuery sq=new StoredQuery("numUsersWithRoles", "SELECT DISTINCT username FROM org.ecocean.Role");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries(context);
	}
	if(qc.getQueryByName("numUsers", context)==null){
		StoredQuery sq=new StoredQuery("numUsers", "SELECT FROM org.ecocean.User WHERE uuid != null");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries(context);
	}
	if(qc.getQueryByName("top3Encounters", context)==null){
		StoredQuery sq=new StoredQuery("top3Encounters", "SELECT FROM org.ecocean.Encounter WHERE individualID != null ORDER BY dwcDateAddedLong descending RANGE 1,4");
		sq.setExpirationTimeoutDuration(600000);
		myShepherd.getPM().makePersistent(sq);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		qc.loadQueries(context);
	}
	
	

	Map<String,CachedQuery> queries=qc.cachedQueries();
	Set<String> keys=queries.keySet();
	
	%>
	<h2>Round 1: Uncached</h2>
	<ul>
	<%
	//qc.loadQueries(context);
	Iterator<String> iter=keys.iterator();
	int numQueries=queries.size();

	long start1=System.currentTimeMillis();
	while(iter.hasNext()){
		String keyName=iter.next();
		CachedQuery cquery=queries.get(keyName);
		
		%>
		
		<li><%=cquery.getName() %>:<%=cquery.getQueryString() %>:<%=cquery.executeCountQuery(myShepherd) %></li>
		
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
		//cquery.executeCountQuery(myShepherd);
		%>	
		<li><%=cquery.getName() %>:<%=cquery.getQueryString() %>::<%=cquery.executeCountQuery(myShepherd) %></li>
		<%

	}
	long end2=System.currentTimeMillis();
	%>
	</ul>
	<p>Round 2 took: <%=(end2-start2) %>
	
	
	<h2>Testing specific calls</h2>
	<ul>
	
	<%
	long start3=System.currentTimeMillis();
    int numMarkedIndividuals=qc.getQueryByName("numMarkedIndividuals", context).executeCountQuery(myShepherd).intValue();
    int numEncounters=qc.getQueryByName("numEncounters", context).executeCountQuery(myShepherd).intValue();
    int numDataContributors=qc.getQueryByName("numUsersWithRoles", context).executeCountQuery(myShepherd).intValue();
    int numUsersWithRoles = qc.getQueryByName("numUsers", context).executeCountQuery(myShepherd).intValue()-numDataContributors;
    long end3=System.currentTimeMillis();
	%>
		<li>numMarkedIndividuals: <%=numMarkedIndividuals %></li>
		<li>numEncounters: <%=numEncounters %></li>
		<li>numDataContributors: <%=numDataContributors %></li>
		<li>numUsersWithRoles: <%=numUsersWithRoles %></li>
	
	</ul>
	<p>Result 3 time: <%=(end3-start3) %></p>
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
